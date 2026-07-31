package game.sound.sfx;

import static game.sound.sfx.SfxXmlKey.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

import app.StarRodException;
import app.input.InputFileException;
import game.sound.SoundBankCatalog;
import game.sound.engine.EnvelopeCommand;
import game.sound.engine.EnvelopeOp;
import game.sound.engine.EnvelopeTimes;
import game.sound.engine.EnvelopeXml;
import game.sound.sfx.SfxArchive.Allocation;
import game.sound.sfx.SfxArchive.Command;
import game.sound.sfx.SfxArchive.Definition;
import game.sound.sfx.SfxArchive.Empty;
import game.sound.sfx.SfxArchive.Envelope;
import game.sound.sfx.SfxArchive.Label;
import game.sound.sfx.SfxArchive.OneShot;
import game.sound.sfx.SfxArchive.Op;
import game.sound.sfx.SfxArchive.Routing;
import game.sound.sfx.SfxArchive.Sequence;
import game.sound.sfx.SfxArchive.Sound;
import game.sound.sfx.SfxArchive.SpawnedEffect;
import game.sound.sfx.SfxArchive.Track;
import util.xml.XmlKey;
import util.xml.XmlWrapper.XmlReader;
import util.xml.XmlWrapper.XmlTag;
import util.xml.XmlWrapper.XmlWriter;

public final class SfxXml
{
	public static final String FN_SOUND_EFFECTS = "SoundEffects.xml";
	public static final String FN_SOUND_ENVELOPES = "SoundEnvelopes.xml";

	private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_.-]*");
	private static final Pattern SOUND_ID = Pattern.compile("[0-9A-F]{4}");

	public static final List<String> ENVELOPE_DURATION_TOKENS = EnvelopeTimes.tokens();

	private SfxXml()
	{}

	public static SfxArchive read(Path archiveXml, SoundBankCatalog catalog)
	{
		try {
			return readArchive(archiveXml, catalog);
		}
		catch (InputFileException e) {
			throw new SfxFormatException(e.getOrigin() + ": " + e.getMessage(), e);
		}
	}

	private static SfxArchive readArchive(Path archiveXml, SoundBankCatalog catalog)
	{
		Path manifest = requireInputFile(archiveXml, "SFX archive manifest");
		Path assetRoot = manifest.getParent();
		XmlReader reader = parseDocument(manifest);
		Element root = requireRoot(reader, manifest, TAG_SOUNDS);
		checkAttributes(manifest, root);

		SfxArchive archive = new SfxArchive();
		for (Element child : childElements(manifest, root)) {
			if (!child.getTagName().equals(TAG_SOUND.toString()))
				throw unknownElement(manifest, root, child);
			Sound sound = readSound(reader, manifest, assetRoot, child, catalog);
			if (archive.sounds.putIfAbsent(sound.id, sound) != null)
				throw error(manifest, child, String.format("duplicate sound ID %04X", sound.id));
		}

		Path envelopeXml = assetRoot.resolve(FN_SOUND_ENVELOPES);
		if (Files.isRegularFile(envelopeXml)) {
			checkExactPathCase(assetRoot, FN_SOUND_ENVELOPES, manifest, root);
			readEnvelopes(archive, envelopeXml);
		}

		rejectOrphanEffects(assetRoot, archive, manifest);
		validateArchive(archive, manifest);
		return archive;
	}

	public static void write(SfxArchive archive, Path audioDirectory, SoundBankCatalog catalog)
	{
		if (audioDirectory == null)
			throw new IllegalArgumentException("audioDirectory is null");
		writeArchive(archive, audioDirectory.resolve(FN_SOUND_EFFECTS), catalog);
	}

	public static SfxNames readNames(Path archiveXml)
	{
		Path manifest = requireInputFile(archiveXml, "SFX archive manifest");
		XmlReader reader = parseDocument(manifest);
		Element root = requireRoot(reader, manifest, TAG_SOUNDS);
		checkAttributes(manifest, root);

		SfxNames names = new SfxNames();
		Set<Integer> ids = new HashSet<>();
		Set<String> identifiers = new HashSet<>();
		for (Element element : childElements(manifest, root)) {
			if (!element.getTagName().equals(TAG_SOUND.toString()))
				throw unknownElement(manifest, root, element);
			checkAttributes(manifest, element, ATTR_ID, ATTR_NAME, ATTR_SRC, ATTR_EMPTY, ATTR_UNUSED,
				ATTR_DESC, ATTR_TAGS);

			reader.requiresAttribute(element, ATTR_ID);
			String idText = reader.getAttribute(element, ATTR_ID);
			if (!SOUND_ID.matcher(idText).matches())
				throw error(manifest, element, "id must be four uppercase hexadecimal digits");
			int id = reader.readHex(element, ATTR_ID);
			if (!SfxNames.isRawSoundID(id))
				throw error(manifest, element, String.format("%04X is not a raw DAT1 sound ID", id));
			if (!ids.add(id))
				throw error(manifest, element, String.format("duplicate sound ID %04X", id));

			String name = readIdentifier(reader, manifest, element, ATTR_NAME, true);
			if (!identifiers.add(name))
				throw error(manifest, element, "duplicate sound name: " + name);
			names.add(id, name);
			for (Element child : childElements(manifest, element)) {
				if (SfxXmlKey.forTag(child.getTagName()) == null)
					throw unknownElement(manifest, element, child);
			}

			boolean unused = readTrueFlag(reader, manifest, element, ATTR_UNUSED);
			boolean empty = readTrueFlag(reader, manifest, element, ATTR_EMPTY);
			if (unused && empty)
				throw error(manifest, element, "empty sounds must not be marked unused");
			String desc = reader.hasAttribute(element, ATTR_DESC)
				? reader.getAttribute(element, ATTR_DESC) : "";
			List<String> tags = readTags(reader, manifest, element);
			names.setMetadata(id, unused, empty, desc, tags);
		}
		return names;
	}

	public static void writeArchive(SfxArchive archive, Path archiveXml, SoundBankCatalog catalog)
	{
		if (archive == null)
			throw new IllegalArgumentException("archive is null");
		if (archiveXml == null)
			throw new IllegalArgumentException("archiveXml is null");

		Path manifest = archiveXml.toAbsolutePath().normalize();
		Path assetRoot = manifest.getParent();
		if (assetRoot == null)
			throw new SfxFormatException("Archive manifest has no parent directory: " + archiveXml);

		validateArchive(archive, manifest);

		Map<Sound, String> effectSources = chooseEffectSources(archive, assetRoot, manifest);
		try {
			Files.createDirectories(assetRoot);
			for (Map.Entry<Sound, String> entry : effectSources.entrySet()) {
				Path output = resolveOutput(assetRoot, entry.getValue(), manifest);
				Files.createDirectories(output.getParent());
				writeEffectXml(output, entry.getKey(), catalog);
			}
			deleteStaleEffectFiles(assetRoot, effectSources.values());

			Path envelopeXml = assetRoot.resolve(FN_SOUND_ENVELOPES);
			if (!archive.envelopes.isEmpty())
				writeEnvelopesXml(envelopeXml, archive);
			else
				Files.deleteIfExists(envelopeXml);

			writeArchiveXml(manifest, archive, effectSources, catalog);
		}
		catch (IOException e) {
			throw new SfxFormatException("Could not write SFX assets under " + assetRoot + ": " + e.getMessage(), e);
		}
	}

	public static int envelopeDurationIndex(String token)
	{
		try {
			return EnvelopeTimes.indexForToken(token);
		}
		catch (IllegalArgumentException e) {
			throw new SfxFormatException(e.getMessage(), e);
		}
	}

	public static String envelopeDurationToken(int index)
	{
		try {
			return EnvelopeTimes.tokenForIndex(index);
		}
		catch (IllegalArgumentException e) {
			throw new SfxFormatException(e.getMessage(), e);
		}
	}

	private static Sound readSound(XmlReader reader, Path manifest, Path assetRoot, Element element,
		SoundBankCatalog catalog)
	{
		checkAttributes(manifest, element, ATTR_ID, ATTR_NAME, ATTR_SRC, ATTR_EMPTY, ATTR_UNUSED,
			ATTR_DESC, ATTR_TAGS);
		reader.requiresAttribute(element, ATTR_ID);
		String idText = reader.getAttribute(element, ATTR_ID);
		if (!SOUND_ID.matcher(idText).matches())
			throw error(manifest, element, "id must be four uppercase hexadecimal digits");
		int id = reader.readHex(element, ATTR_ID);
		if (!SfxNames.isRawSoundID(id))
			throw error(manifest, element, String.format("%04X is not a raw DAT1 sound ID", id));

		String name = readIdentifier(reader, manifest, element, ATTR_NAME, true);
		Sound sound = new Sound(id, name);
		boolean hasUnused = readTrueFlag(reader, manifest, element, ATTR_UNUSED);
		sound.unused = hasUnused;
		if (reader.hasAttribute(element, ATTR_DESC))
			sound.desc = reader.getAttribute(element, ATTR_DESC);
		sound.tags.addAll(readTags(reader, manifest, element));

		Element routingElement = null;
		Element oneShotElement = null;
		for (Element child : childElements(manifest, element)) {
			switch (tagKey(manifest, element, child)) {
				case TAG_ROUTING:
					if (routingElement != null)
						throw error(manifest, child, "a sound cannot contain more than one Routing element");
					routingElement = child;
					break;
				case TAG_ONE_SHOT:
					if (oneShotElement != null)
						throw error(manifest, child, "a sound cannot contain more than one OneShot element");
					oneShotElement = child;
					break;
				default:
					throw unknownElement(manifest, element, child);
			}
		}

		boolean hasSource = reader.hasAttribute(element, ATTR_SRC);
		boolean hasEmpty = readTrueFlag(reader, manifest, element, ATTR_EMPTY);
		int choices = (hasSource ? 1 : 0) + (hasEmpty ? 1 : 0) + (oneShotElement != null ? 1 : 0);
		if (choices != 1)
			throw error(manifest, element, "Sound must choose exactly one of src, empty=true, or OneShot");
		if (hasEmpty && hasUnused)
			throw error(manifest, element, "empty sounds must not be marked unused");

		if (hasSource) {
			sound.source = reader.getAttribute(element, ATTR_SRC);
			Path effectXml = resolveInput(assetRoot, sound.source, manifest, element, ATTR_SRC);
			readEffect(sound, effectXml, catalog);
		}
		else if (oneShotElement != null) {
			sound.tracks.add(new Track(0, readOneShot(reader, manifest, oneShotElement, catalog)));
		}

		if (routingElement != null)
			sound.routing = readRouting(reader, manifest, routingElement, sound.tracks.size());

		validateSoundRouting(sound, manifest, element);
		return sound;
	}

	private static boolean readTrueFlag(XmlReader reader, Path source, Element element, SfxXmlKey key)
	{
		if (!reader.hasAttribute(element, key))
			return false;
		if (!reader.readBoolean(element, key, false))
			throw error(source, element, key + ", when present, must be true");
		return true;
	}

	private static List<String> readTags(XmlReader reader, Path source, Element element)
	{
		if (!reader.hasAttribute(element, ATTR_TAGS))
			return List.of();
		List<String> tags = reader.readStringList(element, ATTR_TAGS);
		Set<String> unique = new LinkedHashSet<>();
		for (String tag : tags) {
			if (tag.isBlank())
				throw error(source, element, "tags must not contain a blank value");
			if (!unique.add(tag))
				throw error(source, element, "duplicate tag: " + tag);
		}
		return tags;
	}

	private static void readEffect(Sound sound, Path effectXml, SoundBankCatalog catalog)
	{
		XmlReader reader = parseDocument(effectXml);
		Element root = requireRoot(reader, effectXml, TAG_EFFECT);
		checkAttributes(effectXml, root);

		Element tracksElement = null;
		Element spawnedElement = null;
		for (Element child : childElements(effectXml, root)) {
			switch (tagKey(effectXml, root, child)) {
				case TAG_TRACKS:
					if (tracksElement != null)
						throw error(effectXml, child, "SoundEffect cannot contain more than one Tracks element");
					tracksElement = child;
					break;
				case TAG_SPAWNED_EFFECTS:
					if (spawnedElement != null)
						throw error(effectXml, child, "SoundEffect cannot contain more than one SpawnedEffects element");
					spawnedElement = child;
					break;
				case TAG_ROUTING:
					throw error(effectXml, child, "logical sound routing belongs in SoundEffects.xml, not the effect file");
				default:
					throw unknownElement(effectXml, root, child);
			}
		}
		if (tracksElement == null)
			throw error(effectXml, root, "SoundEffect is missing required Tracks element");
		sound.tracks.addAll(readTracks(reader, effectXml, tracksElement, catalog));

		if (spawnedElement != null) {
			checkAttributes(effectXml, spawnedElement);
			for (Element child : childElements(effectXml, spawnedElement)) {
				if (!child.getTagName().equals(TAG_SPAWNED_EFFECT.toString()))
					throw unknownElement(effectXml, spawnedElement, child);
				sound.spawnedEffects.add(readSpawnedEffect(reader, effectXml, child, catalog));
			}
		}
	}

	private static SpawnedEffect readSpawnedEffect(XmlReader reader, Path source, Element element,
		SoundBankCatalog catalog)
	{
		checkAttributes(source, element, ATTR_NAME);
		SpawnedEffect spawned = new SpawnedEffect(readIdentifier(reader, source, element, ATTR_NAME, true));
		Element routingElement = null;
		Element tracksElement = null;
		for (Element child : childElements(source, element)) {
			switch (tagKey(source, element, child)) {
				case TAG_ROUTING:
					if (routingElement != null)
						throw error(source, child, "SpawnedEffect cannot contain more than one Routing element");
					routingElement = child;
					break;
				case TAG_TRACKS:
					if (tracksElement != null)
						throw error(source, child, "SpawnedEffect cannot contain more than one Tracks element");
					tracksElement = child;
					break;
				default:
					throw unknownElement(source, element, child);
			}
		}
		if (tracksElement == null || routingElement == null)
			throw error(source, element, "SpawnedEffect requires exactly one Routing and one Tracks element");
		spawned.tracks.addAll(readTracks(reader, source, tracksElement, catalog));
		spawned.routing = readRouting(reader, source, routingElement, spawned.tracks.size());
		validateTrackRouting(spawned.tracks, spawned.routing, source, element);
		return spawned;
	}

	private static List<Track> readTracks(XmlReader reader, Path source, Element element,
		SoundBankCatalog catalog)
	{
		checkAttributes(source, element);
		List<Track> tracks = new ArrayList<>();
		for (Element child : childElements(source, element)) {
			if (!child.getTagName().equals(TAG_TRACK.toString()))
				throw unknownElement(source, element, child);
			tracks.add(readTrack(reader, source, child, catalog));
		}
		validateTrackSlots(tracks, source, element);
		return tracks;
	}

	private static Track readTrack(XmlReader reader, Path source, Element element,
		SoundBankCatalog catalog)
	{
		checkAttributes(source, element, ATTR_SLOT, ATTR_PLAYER, ATTR_PRIORITY, ATTR_EMPTY);
		int slot = readRangedInt(reader, source, element, ATTR_SLOT, 0, 7, false);
		Integer player = reader.hasAttribute(element, ATTR_PLAYER)
			? readRangedInt(reader, source, element, ATTR_PLAYER, 0, 7, false) : null;
		Integer priority = reader.hasAttribute(element, ATTR_PRIORITY)
			? readRangedInt(reader, source, element, ATTR_PRIORITY, 0, 3, false) : null;

		List<Element> children = childElements(source, element);
		boolean empty = reader.hasAttribute(element, ATTR_EMPTY);
		if (empty && !reader.readBoolean(element, ATTR_EMPTY, false))
			throw error(source, element, "empty, when present, must be true");

		Definition definition;
		if (empty) {
			if (!children.isEmpty())
				throw error(source, element, "an empty track cannot contain a definition");
			definition = Empty.INSTANCE;
		}
		else {
			if (children.size() != 1)
				throw error(source, element, "Track must contain exactly one OneShot or Sequence definition");
			Element child = children.get(0);
			switch (tagKey(source, element, child)) {
				case TAG_ONE_SHOT:
					definition = readOneShot(reader, source, child, catalog);
					break;
				case TAG_SEQUENCE:
					definition = readSequence(reader, source, child, catalog);
					break;
				case TAG_SHARED_SEQUENCE:
					throw error(source, child,
						"Shared.xml and SharedSequence are not supported by the prototype");
				default:
					throw unknownElement(source, element, child);
			}
		}

		Track track = new Track(slot, definition);
		track.player = player;
		track.priority = priority;
		return track;
	}

	private static OneShot readOneShot(XmlReader reader, Path source, Element element,
		SoundBankCatalog catalog)
	{
		checkAttributes(source, element,
			ATTR_WAV, ATTR_ENVELOPE, ATTR_VOLUME, ATTR_PAN, ATTR_REVERB, ATTR_PITCH, ATTR_RANDOM_PITCH,
			ATTR_LOCK_VOLUME, ATTR_LOCK_PAN, ATTR_LOCK_PITCH, ATTR_LOCK_REVERB);
		requireNoChildren(source, element);
		OneShot oneShot = new OneShot();
		SoundBankCatalog.InstrumentAddress address = readWavAddress(reader, source, element, catalog);
		oneShot.bank = address.bank;
		oneShot.patch = address.patch;
		oneShot.volume = readRangedInt(reader, source, element, ATTR_VOLUME, 0, 255, false);
		oneShot.pan = readRangedInt(reader, source, element, ATTR_PAN, 0, 255, true, 64);
		oneShot.reverb = readRangedInt(reader, source, element, ATTR_REVERB, 0, 255, true, 0);
		oneShot.pitch = readRangedInt(reader, source, element, ATTR_PITCH, 0, 127, true, 48);
		oneShot.randomPitch = readRangedInt(reader, source, element, ATTR_RANDOM_PITCH, 0, 120, true, 0);
		if ((oneShot.randomPitch & 7) != 0)
			throw error(source, element, "randomPitch must be a multiple of 8");
		oneShot.lockVolume = reader.readBoolean(element, ATTR_LOCK_VOLUME, false);
		oneShot.lockPan = reader.readBoolean(element, ATTR_LOCK_PAN, false);
		oneShot.lockPitch = reader.readBoolean(element, ATTR_LOCK_PITCH, false);
		oneShot.lockReverb = reader.readBoolean(element, ATTR_LOCK_REVERB, false);
		return oneShot;
	}

	private static Sequence readSequence(XmlReader reader, Path source, Element element,
		SoundBankCatalog catalog)
	{
		checkAttributes(source, element,
			ATTR_LOCK_VOLUME, ATTR_LOCK_PAN, ATTR_LOCK_PITCH, ATTR_LOCK_REVERB);
		Sequence sequence = new Sequence();
		sequence.lockVolume = reader.readBoolean(element, ATTR_LOCK_VOLUME, false);
		sequence.lockPan = reader.readBoolean(element, ATTR_LOCK_PAN, false);
		sequence.lockPitch = reader.readBoolean(element, ATTR_LOCK_PITCH, false);
		sequence.lockReverb = reader.readBoolean(element, ATTR_LOCK_REVERB, false);

		Set<String> labels = new LinkedHashSet<>();
		labels.add(Sequence.START_LABEL);
		sequence.nodes.add(new Label(Sequence.START_LABEL));
		for (Element child : childElements(source, element)) {
			if (child.getTagName().equals(TAG_LABEL.toString())) {
				checkAttributes(source, child, ATTR_NAME);
				requireNoChildren(source, child);
				String name = readIdentifier(reader, source, child, ATTR_NAME, true);
				if (!labels.add(name))
					throw error(source, child, "duplicate label: " + name);
				sequence.nodes.add(new Label(name));
			}
			else {
				sequence.nodes.add(readCommand(reader, source, child, catalog));
			}
		}

		for (SfxArchive.Node node : sequence.nodes) {
			if (node instanceof Command command && (command.op == Op.JUMP || command.op == Op.SET_ALTERNATIVE)) {
				String label = labelReferenceName(source, element, command.ref);
				if (!labels.contains(label))
					throw error(source, element, "missing label referenced by " + command.op + ": " + label);
			}
		}
		return sequence;
	}

	private static Command readCommand(XmlReader reader, Path source, Element element,
		SoundBankCatalog catalog)
	{
		SfxXmlKey tag = SfxXmlKey.forTag(element.getTagName());
		if (tag == null)
			throw error(source, element, "unknown sequence command element: " + element.getTagName());
		switch (tag) {
			case TAG_END:
				return noArgCommand(reader, source, element, Op.END);
			case TAG_DELAY:
				return new Command(Op.DELAY,
					decimalAttribute(reader, source, element, ATTR_TICKS, 1, 2167));
			case TAG_PLAY:
				checkAttributes(source, element, ATTR_PITCH, ATTR_VELOCITY, ATTR_LENGTH);
				requireNoChildren(source, element);
				return new Command(Op.PLAY,
					readRangedInt(reader, source, element, ATTR_PITCH, 0, 87, false),
					readRangedInt(reader, source, element, ATTR_VELOCITY, 0, 127, false),
					readRangedInt(reader, source, element, ATTR_LENGTH, 0, 16575, false));
			case TAG_SET_VOLUME:
				return new Command(Op.SET_VOLUME,
					decimalAttribute(reader, source, element, ATTR_VALUE, 0, 255));
			case TAG_SET_PAN:
				return new Command(Op.SET_PAN,
					decimalAttribute(reader, source, element, ATTR_VALUE, 0, 255));
			case TAG_SET_INSTRUMENT:
				checkAttributes(source, element, ATTR_WAV, ATTR_ENVELOPE);
				requireNoChildren(source, element);
				SoundBankCatalog.InstrumentAddress instrument =
					readWavAddress(reader, source, element, catalog);
				return new Command(Op.SET_INSTRUMENT, instrument.bank, instrument.patch);
			case TAG_SET_REVERB:
				return new Command(Op.SET_REVERB,
					decimalAttribute(reader, source, element, ATTR_VALUE, 0, 255));
			case TAG_SET_ENVELOPE:
				return new Command(Op.SET_ENVELOPE,
					decimalAttribute(reader, source, element, ATTR_PRESET, 0, 255));
			case TAG_COARSE_TUNE:
				return new Command(Op.COARSE_TUNE,
					decimalAttribute(reader, source, element, ATTR_SEMITONES, -128, 127));
			case TAG_FINE_TUNE:
				return new Command(Op.FINE_TUNE,
					decimalAttribute(reader, source, element, ATTR_CENTS, 0, 255));
			case TAG_WAIT_FOR_END:
				return noArgCommand(reader, source, element, Op.WAIT_FOR_END);
			case TAG_PITCH_SWEEP:
				checkAttributes(source, element, ATTR_TICKS, ATTR_PITCH);
				requireNoChildren(source, element);
				return new Command(Op.PITCH_SWEEP,
					readRangedInt(reader, source, element, ATTR_TICKS, 0, 65535, false),
					readRangedInt(reader, source, element, ATTR_PITCH, 0, 127, false));
			case TAG_START_LOOP:
				return new Command(Op.START_LOOP,
					decimalAttribute(reader, source, element, ATTR_COUNT, 0, 255));
			case TAG_END_LOOP:
				return noArgCommand(reader, source, element, Op.END_LOOP);
			case TAG_WAIT_FOR_RELEASE:
				return noArgCommand(reader, source, element, Op.WAIT_FOR_RELEASE);
			case TAG_SET_CURRENT_VOLUME:
				return new Command(Op.SET_CURRENT_VOLUME,
					decimalAttribute(reader, source, element, ATTR_VALUE, 0, 255));
			case TAG_VOLUME_RAMP:
				checkAttributes(source, element, ATTR_TICKS, ATTR_VALUE);
				requireNoChildren(source, element);
				return new Command(Op.VOLUME_RAMP,
					readRangedInt(reader, source, element, ATTR_TICKS, 0, 65535, false),
					readRangedInt(reader, source, element, ATTR_VALUE, 0, 255, false));
			case TAG_SET_ALTERNATIVE:
				checkAttributes(source, element, ATTR_TYPE, ATTR_TARGET);
				requireNoChildren(source, element);
				Command command = Command.reference(Op.SET_ALTERNATIVE,
					readIdentifier(reader, source, element, ATTR_TARGET, true));
				command.a = readRangedInt(reader, source, element, ATTR_TYPE, 1, 3, false);
				return command;
			case TAG_STOP:
				return noArgCommand(reader, source, element, Op.STOP);
			case TAG_JUMP:
				checkAttributes(source, element, ATTR_TARGET);
				requireNoChildren(source, element);
				return Command.reference(Op.JUMP,
					readIdentifier(reader, source, element, ATTR_TARGET, true));
			case TAG_RESTART:
				return noArgCommand(reader, source, element, Op.RESTART);
			case TAG_NOP:
				return noArgCommand(reader, source, element, Op.NOP);
			case TAG_SET_RANDOM_PITCH:
				return new Command(Op.SET_RANDOM_PITCH,
					decimalAttribute(reader, source, element, ATTR_AMOUNT, 0, 255));
			case TAG_SET_RANDOM_VELOCITY:
				return new Command(Op.SET_RANDOM_VELOCITY,
					decimalAttribute(reader, source, element, ATTR_AMOUNT, 0, 255));
			case TAG_SET_RANDOM_UNUSED:
				return new Command(Op.SET_RANDOM_UNUSED,
					decimalAttribute(reader, source, element, ATTR_AMOUNT, 0, 255));
			case TAG_SET_PRESS_ENVELOPE:
				checkAttributes(source, element, ATTR_REF);
				requireNoChildren(source, element);
				Command envelopeCommand = new Command(Op.SET_PRESS_ENVELOPE);
				if (reader.hasAttribute(element, ATTR_REF))
					envelopeCommand.ref = readIdentifier(reader, source, element, ATTR_REF, true);
				return envelopeCommand;
			case TAG_SPAWN:
				checkAttributes(source, element, ATTR_REF);
				requireNoChildren(source, element);
				reader.requiresAttribute(element, ATTR_REF);
				String ref = reader.getAttribute(element, ATTR_REF);
				if (ref.startsWith("shared:"))
					throw error(source, element, "shared spawned effects are not supported by the prototype");
				if (!IDENTIFIER.matcher(ref).matches())
					throw error(source, element, "Spawn ref is not a valid local spawned-effect name: " + ref);
				return Command.reference(Op.SPAWN, ref);
			case TAG_SET_ALTERNATIVE_VOLUME:
				return new Command(Op.SET_ALTERNATIVE_VOLUME,
					decimalAttribute(reader, source, element, ATTR_VALUE, 0, 255));
			case TAG_SHARED_SEQUENCE:
				throw error(source, element,
					"Shared.xml and SharedSequence are not supported by the prototype");
			default:
				throw error(source, element, "unknown sequence command element: " + element.getTagName());
		}
	}

	private static Command noArgCommand(XmlReader reader, Path source, Element element, Op op)
	{
		checkAttributes(source, element);
		requireNoChildren(source, element);
		return new Command(op);
	}

	private static int decimalAttribute(XmlReader reader, Path source, Element element,
		SfxXmlKey name, int min, int max)
	{
		checkAttributes(source, element, name);
		requireNoChildren(source, element);
		return readRangedInt(reader, source, element, name, min, max, false);
	}

	private static Routing readRouting(XmlReader reader, Path source, Element element, int trackCount)
	{
		checkAttributes(source, element,
			ATTR_ALLOCATION, ATTR_MAX_PLAYER, ATTR_PLAYER, ATTR_PRIORITY, ATTR_EXCLUSIVE_GROUP);
		requireNoChildren(source, element);
		reader.requiresAttribute(element, ATTR_ALLOCATION);
		String allocationText = reader.getAttribute(element, ATTR_ALLOCATION);
		Allocation allocation;
		try {
			allocation = Allocation.fromXml(allocationText);
		}
		catch (IllegalArgumentException e) {
			throw error(source, element, e.getMessage());
		}

		Routing routing = new Routing(allocation);
		routing.exclusiveGroup = readRangedInt(reader, source, element, ATTR_EXCLUSIVE_GROUP, 0, 3, true, 0);
		if (allocation == Allocation.DYNAMIC) {
			if (reader.hasAttribute(element, ATTR_PLAYER))
				throw error(source, element, "dynamic routing cannot specify player");
			routing.maxPlayer = readRangedInt(reader, source, element, ATTR_MAX_PLAYER, 0, 7, false);
			routing.priority = readRangedInt(reader, source, element, ATTR_PRIORITY, 0, 3, false);
		}
		else {
			if (reader.hasAttribute(element, ATTR_MAX_PLAYER))
				throw error(source, element, "fixed routing cannot specify maxPlayer");
			if (trackCount == 1) {
				routing.player = readRangedInt(reader, source, element, ATTR_PLAYER, 0, 7, false);
				routing.priority = readRangedInt(reader, source, element, ATTR_PRIORITY, 0, 3, false);
			}
			else if (reader.hasAttribute(element, ATTR_PLAYER) || reader.hasAttribute(element, ATTR_PRIORITY)) {
				throw error(source, element,
					"fixed polyphonic routing carries player and priority on nonempty Track elements");
			}
		}
		return routing;
	}

	private static void readEnvelopes(SfxArchive archive, Path envelopeXml)
	{
		XmlReader reader = parseDocument(envelopeXml);
		Element root = requireRoot(reader, envelopeXml, TAG_ENVELOPES);
		checkAttributes(envelopeXml, root);
		Set<String> names = new HashSet<>();

		for (Element element : childElements(envelopeXml, root)) {
			if (!element.getTagName().equals(TAG_ENVELOPE.toString()))
				throw unknownElement(envelopeXml, root, element);
			checkAttributes(envelopeXml, element, ATTR_NAME);
			String name = readIdentifier(reader, envelopeXml, element, ATTR_NAME, true);
			if (!names.add(name))
				throw error(envelopeXml, element, "duplicate envelope name: " + name);
			Envelope envelope = new Envelope(name);

			envelope.commands.addAll(EnvelopeXml.readCommands(reader, element));
			archive.envelopes.add(envelope);
		}
	}

	private static void validateArchive(SfxArchive archive, Path source)
	{
		Set<String> allSoundNames = new HashSet<>();
		Set<String> sources = new HashSet<>();
		for (Map.Entry<Integer, Sound> mapEntry : archive.sounds.entrySet()) {
			Sound sound = mapEntry.getValue();
			if (sound == null)
				throw modelError(source, "archive contains a null Sound");
			if (mapEntry.getKey() != sound.id)
				throw modelError(source, String.format("sound map key %04X disagrees with Sound ID %04X",
					mapEntry.getKey(), sound.id));
			if (!SfxNames.isRawSoundID(sound.id))
				throw modelError(source, String.format("%04X is not a raw DAT1 sound ID", sound.id));
			validateIdentifier(sound.name, "sound name", source);
			if (!allSoundNames.add(sound.name))
				throw modelError(source, "duplicate sound name: " + sound.name);
			if (sound.desc == null)
				throw modelError(source, String.format("sound %04X has a null description", sound.id));
			Set<String> uniqueTags = new LinkedHashSet<>();
			for (String tag : sound.tags) {
				if (tag == null || tag.isBlank())
					throw modelError(source, String.format("sound %04X has a blank tag", sound.id));
				if (!uniqueTags.add(tag))
					throw modelError(source, String.format("sound %04X has duplicate tag %s", sound.id, tag));
			}

			if (sound.isEmpty()) {
				if (sound.unused)
					throw modelError(source, String.format("empty sound %04X cannot be marked unused", sound.id));
				if (sound.routing != null)
					throw modelError(source, String.format("empty sound %04X cannot have routing", sound.id));
				if (!sound.spawnedEffects.isEmpty())
					throw modelError(source, String.format("empty sound %04X cannot own spawned effects", sound.id));
				continue;
			}

			validateTrackSlots(sound.tracks, source, null);
			boolean pointerBacked = isPointerBacked(sound.id);
			if (pointerBacked && sound.routing == null)
				throw modelError(source, String.format("pointer-backed sound %04X requires routing", sound.id));
			if (!pointerBacked && sound.routing != null)
				throw modelError(source, String.format("direct sound %04X cannot have routing", sound.id));
			if (!pointerBacked && !sound.canInlineOneShot())
				throw modelError(source, String.format(
					"direct sound %04X must contain exactly one OneShot and no spawned effects", sound.id));
			if (sound.routing != null)
				validateTrackRouting(sound.tracks, sound.routing, source, null);

			if (!pointerBacked)
				validateCompactOneShot((OneShot) sound.tracks.get(0).definition, source, sound.id);

			Set<String> spawnedNames = new HashSet<>();
			for (SpawnedEffect spawned : sound.spawnedEffects) {
				if (spawned == null)
					throw modelError(source, String.format("sound %04X contains a null spawned effect", sound.id));
				validateIdentifier(spawned.name, "spawned-effect name", source);
				if (!spawnedNames.add(spawned.name))
					throw modelError(source, String.format("sound %04X has duplicate spawned effect %s",
						sound.id, spawned.name));
				if (spawned.routing == null)
					throw modelError(source, "spawned effect requires routing: " + spawned.name);
				validateTrackSlots(spawned.tracks, source, null);
				validateTrackRouting(spawned.tracks, spawned.routing, source, null);
			}

			for (Track track : sound.tracks)
				validateDefinition(track.definition, spawnedNames, source);
			for (SpawnedEffect spawned : sound.spawnedEffects) {
				for (Track track : spawned.tracks)
					validateDefinition(track.definition, spawnedNames, source);
			}

			if (sound.source != null) {
				String key = sound.source.toLowerCase(Locale.ROOT);
				if (!sources.add(key))
					throw modelError(source, "effect source path is reused: " + sound.source);
			}
		}

		Set<String> envelopeNames = new HashSet<>();
		for (Envelope envelope : archive.envelopes) {
			if (envelope == null)
				throw modelError(source, "archive contains a null Envelope");
			validateIdentifier(envelope.name, "envelope name", source);
			if (!envelopeNames.add(envelope.name))
				throw modelError(source, "duplicate envelope name: " + envelope.name);
			validateEnvelope(envelope, source);
		}

		for (Sound sound : archive.sounds.values()) {
			for (Track track : sound.tracks)
				validateEnvelopeReferences(track.definition, envelopeNames, source);
			for (SpawnedEffect spawned : sound.spawnedEffects) {
				for (Track track : spawned.tracks)
					validateEnvelopeReferences(track.definition, envelopeNames, source);
			}
		}

		// Keep XML programmatic writes subject to the same semantic contract as
		// binary linking. XML-specific checks above retain source-file context.
		SfxValidator.validate(archive);
	}

	private static void validateSoundRouting(Sound sound, Path source, Element element)
	{
		if (sound.isEmpty()) {
			if (sound.routing != null)
				throw error(source, element, "empty sounds cannot contain Routing");
			return;
		}
		if (isPointerBacked(sound.id)) {
			if (sound.routing == null)
				throw error(source, element, "pointer-backed sounds require Routing");
			validateTrackRouting(sound.tracks, sound.routing, source, element);
		}
		else {
			if (sound.routing != null)
				throw error(source, element, "direct high/extra sounds omit Routing");
			if (!sound.canInlineOneShot())
				throw error(source, element,
					"direct high/extra sounds require exactly one OneShot and no spawned effects");
			validateCompactOneShot((OneShot) sound.tracks.get(0).definition, source, sound.id);
		}
	}

	private static void validateTrackSlots(List<Track> tracks, Path source, Element element)
	{
		if (!(tracks.size() == 1 || tracks.size() == 2 || tracks.size() == 4 || tracks.size() == 8))
			throw locationError(source, element, "track count must be exactly 1, 2, 4, or 8");
		Set<Integer> slots = new HashSet<>();
		boolean anyDefinition = false;
		for (Track track : tracks) {
			if (track == null)
				throw locationError(source, element, "track list contains null");
			if (!slots.add(track.slot))
				throw locationError(source, element, "duplicate track slot: " + track.slot);
			if (track.definition == null)
				throw locationError(source, element, "track " + track.slot + " has no definition");
			if (track.definition != Empty.INSTANCE)
				anyDefinition = true;
		}
		if (!tracks.isEmpty() && !anyDefinition)
			throw locationError(source, element, "track set contains only empty tracks; use an empty Sound instead");
		for (int slot = 0; slot < tracks.size(); slot++) {
			if (!slots.contains(slot))
				throw locationError(source, element, "track slots must be contiguous from zero");
		}
		for (int i = 1; i < tracks.size(); i++) {
			if (tracks.get(i - 1).slot > tracks.get(i).slot)
				throw locationError(source, element, "Track elements must appear in ascending slot order");
		}
	}

	private static void validateTrackRouting(List<Track> tracks, Routing routing, Path source, Element element)
	{
		if (routing == null || routing.allocation == null)
			throw locationError(source, element, "routing allocation is missing");
		if (routing.exclusiveGroup < 0 || routing.exclusiveGroup > 3)
			throw locationError(source, element, "exclusiveGroup must be 0 through 3");

		if (routing.allocation == Allocation.DYNAMIC) {
			if (routing.maxPlayer < 0 || routing.maxPlayer > 7 || routing.priority < 0 || routing.priority > 3)
				throw locationError(source, element, "dynamic routing values are out of range");
			if (tracks.size() > 1 && routing.maxPlayer != 7)
				throw locationError(source, element, "dynamic polyphonic routing requires maxPlayer=7");
			for (Track track : tracks) {
				if (track.player != null || track.priority != null)
					throw locationError(source, element,
						"dynamic allocation cannot place player or priority on Track");
			}
		}
		else if (tracks.size() == 1) {
			if (routing.player < 0 || routing.player > 7 || routing.priority < 0 || routing.priority > 3)
				throw locationError(source, element, "fixed mono routing values are out of range");
			Track track = tracks.get(0);
			if (track.player != null || track.priority != null)
				throw locationError(source, element,
					"fixed mono player and priority belong on Routing, not Track");
		}
		else {
			boolean foundEmpty = false;
			for (Track track : tracks) {
				if (track.definition == Empty.INSTANCE) {
					foundEmpty = true;
					if (track.player != null || track.priority != null)
						throw locationError(source, element, "empty fixed-poly tracks omit player and priority");
				}
				else {
					if (foundEmpty)
						throw locationError(source, element, "fixed-poly empty tracks must be trailing");
					if (track.player == null || track.priority == null
						|| track.player < 0 || track.player > 7 || track.priority < 0 || track.priority > 3) {
						throw locationError(source, element,
							"nonempty fixed-poly tracks require player 0-7 and priority 0-3");
					}
				}
			}
		}
	}

	private static void validateCompactOneShot(OneShot oneShot, Path source, int soundID)
	{
		if (oneShot.pan != 64 || oneShot.reverb != 0 || oneShot.pitch != 48
			|| oneShot.volume < 3 || oneShot.volume > 127 || (oneShot.volume & 3) != 3
			|| oneShot.randomPitch < 0 || oneShot.randomPitch > 56 || (oneShot.randomPitch & 7) != 0) {
			throw modelError(source, String.format(
				"direct sound %04X is not representable by the four-byte compact encoding", soundID));
		}
	}

	private static void validateDefinition(Definition definition, Set<String> spawnedNames, Path source)
	{
		if (definition == Empty.INSTANCE)
			return;
		if (definition instanceof OneShot oneShot) {
			if (!inRange(oneShot.bank, 0, 255) || !inRange(oneShot.patch, 0, 255)
				|| !inRange(oneShot.volume, 0, 255) || !inRange(oneShot.pan, 0, 255)
				|| !inRange(oneShot.reverb, 0, 255) || !inRange(oneShot.pitch, 0, 127)
				|| !inRange(oneShot.randomPitch, 0, 120) || (oneShot.randomPitch & 7) != 0)
				throw modelError(source, "OneShot contains an out-of-range value");
			return;
		}
		if (!(definition instanceof Sequence sequence))
			throw modelError(source, "unsupported track definition: " + definition.getClass().getName());

		if (sequence.nodes.isEmpty()
			|| !(sequence.nodes.get(0) instanceof Label entryLabel)
			|| !entryLabel.name().equals(Sequence.START_LABEL)) {
			throw modelError(source, "sequence must begin with its implicit start label");
		}
		Set<String> labels = new HashSet<>();
		for (SfxArchive.Node node : sequence.nodes) {
			if (node == null)
				throw modelError(source, "sequence contains a null node");
			if (node instanceof Label label) {
				validateIdentifier(label.name(), "label", source);
				if (!labels.add(label.name()))
					throw modelError(source, "duplicate sequence label: " + label.name());
			}
			else if (node instanceof Command command) {
				validateCommand(command, source);
			}
		}
		for (SfxArchive.Node node : sequence.nodes) {
			if (!(node instanceof Command command))
				continue;
			if (command.op == Op.JUMP || command.op == Op.SET_ALTERNATIVE) {
				String label = labelReferenceName(source, null, command.ref);
				if (!labels.contains(label))
					throw modelError(source, "missing label: " + label);
			}
			else if (command.op == Op.SPAWN && !spawnedNames.contains(command.ref)) {
				throw modelError(source, "missing local spawned effect: " + command.ref);
			}
		}
	}

	private static void validateCommand(Command command, Path source)
	{
		if (command.op == null)
			throw modelError(source, "sequence command has no operation");
		switch (command.op) {
			case END:
			case WAIT_FOR_END:
			case END_LOOP:
			case WAIT_FOR_RELEASE:
			case STOP:
			case RESTART:
			case NOP:
				break;
			case DELAY:
				requireRange(command.a, 1, 2167, command.op, "ticks", source);
				break;
			case PLAY:
				requireRange(command.a, 0, 87, command.op, "pitch", source);
				requireRange(command.b, 0, 127, command.op, "velocity", source);
				requireRange(command.c, 0, 16575, command.op, "length", source);
				break;
			case SET_VOLUME:
			case SET_PAN:
			case SET_REVERB:
			case SET_ENVELOPE:
			case FINE_TUNE:
			case SET_CURRENT_VOLUME:
			case SET_RANDOM_PITCH:
			case SET_RANDOM_VELOCITY:
			case SET_RANDOM_UNUSED:
			case SET_ALTERNATIVE_VOLUME:
				requireRange(command.a, 0, 255, command.op, "value", source);
				break;
			case SET_INSTRUMENT:
				requireRange(command.a, 0, 255, command.op, "bank", source);
				requireRange(command.b, 0, 255, command.op, "patch", source);
				break;
			case COARSE_TUNE:
				requireRange(command.a, -128, 127, command.op, "semitones", source);
				break;
			case PITCH_SWEEP:
				requireRange(command.a, 0, 65535, command.op, "ticks", source);
				requireRange(command.b, 0, 127, command.op, "pitch", source);
				break;
			case START_LOOP:
				requireRange(command.a, 0, 255, command.op, "count", source);
				break;
			case VOLUME_RAMP:
				requireRange(command.a, 0, 65535, command.op, "ticks", source);
				requireRange(command.b, 0, 255, command.op, "value", source);
				break;
			case SET_ALTERNATIVE:
				requireRange(command.a, 1, 3, command.op, "type", source);
				labelReferenceName(source, null, command.ref);
				break;
			case JUMP:
				labelReferenceName(source, null, command.ref);
				break;
			case SET_PRESS_ENVELOPE:
				if (command.ref != null)
					validateIdentifier(command.ref, "press-envelope reference", source);
				break;
			case SPAWN:
				validateIdentifier(command.ref, "spawned-effect reference", source);
				break;
		}
	}

	private static void validateEnvelopeReferences(Definition definition, Set<String> envelopeNames, Path source)
	{
		if (!(definition instanceof Sequence sequence))
			return;
		for (SfxArchive.Node node : sequence.nodes) {
			if (node instanceof Command command && command.op == Op.SET_PRESS_ENVELOPE
				&& command.ref != null && !envelopeNames.contains(command.ref)) {
				throw modelError(source, "missing custom press envelope: " + command.ref);
			}
		}
	}

	private static void validateEnvelope(Envelope envelope, Path source)
	{
		if (envelope.commands.isEmpty()
			|| envelope.commands.get(envelope.commands.size() - 1).op != EnvelopeOp.END)
			throw modelError(source, "envelope must end with End: " + envelope.name);
		for (int index = 0; index < envelope.commands.size(); index++) {
			EnvelopeCommand command = envelope.commands.get(index);
			if (command == null || command.op == null)
				throw modelError(source, "envelope contains a null command: " + envelope.name);
			if (command.op == EnvelopeOp.END && index + 1 != envelope.commands.size())
				throw modelError(source, "End must be the final envelope command: " + envelope.name);
			switch (command.op) {
				case POINT:
					requireRange(command.durationIndex, 0, 94, null, "durationIndex", source);
					requireRange(command.value, 0, 127, null, "point value", source);
					break;
				case SET_SCALE:
					requireRange(command.value, 0, 127, null, "scale", source);
					break;
				case ADD_SCALE:
					requireRange(command.value, -128, 127, null, "scale delta", source);
					break;
				case START_LOOP:
					requireRange(command.value, 0, 255, null, "loop count", source);
					break;
				case END_LOOP:
				case END:
					break;
			}
		}
	}

	private static Map<Sound, String> chooseEffectSources(SfxArchive archive, Path assetRoot, Path manifest)
	{
		Map<Sound, String> result = new LinkedHashMap<>();
		Set<String> used = new HashSet<>();
		for (Sound sound : new TreeMap<>(archive.sounds).values()) {
			if (sound.isEmpty() || sound.canInlineOneShot())
				continue;
			String relative = sound.source;
			if (relative == null || relative.isBlank())
				relative = String.format("sfx/%04X_%s.xml", sound.id, sound.name);
			validateRelativePath(relative, manifest, null, ATTR_SRC);
			if (!relative.endsWith(".xml"))
				throw modelError(manifest, "effect source must end with lowercase .xml: " + relative);
			String key = relative.toLowerCase(Locale.ROOT);
			String manifestRelative = assetRoot.relativize(manifest).toString().replace('\\', '/');
			if (key.equals(manifestRelative.toLowerCase(Locale.ROOT))
				|| key.equals(FN_SOUND_ENVELOPES.toLowerCase(Locale.ROOT)))
				throw modelError(manifest, "effect source uses a reserved output path: " + relative);
			if (!used.add(key))
				throw modelError(manifest, "effect source path is reused: " + relative);
			resolveOutput(assetRoot, relative, manifest);
			result.put(sound, relative);
		}
		return result;
	}

	private static void rejectOrphanEffects(Path assetRoot, SfxArchive archive, Path manifest)
	{
		Path effectsDirectory = assetRoot.resolve("sfx");
		if (!Files.isDirectory(effectsDirectory))
			return;

		Set<Path> referenced = new HashSet<>();
		for (Sound sound : archive.sounds.values()) {
			if (sound.source != null)
				referenced.add(assetRoot.resolve(sound.source).normalize());
		}

		try (Stream<Path> paths = Files.walk(effectsDirectory)) {
			Path orphan = paths
				.filter(Files::isRegularFile)
				.filter(path -> path.getFileName().toString().endsWith(".xml"))
				.filter(path -> !referenced.contains(path.toAbsolutePath().normalize()))
				.findFirst()
				.orElse(null);
			if (orphan != null) {
				String relative = assetRoot.relativize(orphan).toString().replace('\\', '/');
				throw modelError(manifest, "orphan effect file is not referenced by SoundEffects.xml: " + relative);
			}
		}
		catch (IOException e) {
			throw new SfxFormatException(manifest + ": could not inspect effects directory: " + e.getMessage(), e);
		}
	}

	private static void deleteStaleEffectFiles(Path assetRoot, Iterable<String> effectSources)
		throws IOException
	{
		Path effectsDirectory = assetRoot.resolve("sfx");
		if (!Files.isDirectory(effectsDirectory))
			return;
		if (Files.isSymbolicLink(effectsDirectory))
			throw new IOException("Refusing to prune symbolic-link effects directory: " + effectsDirectory);

		Set<Path> referenced = new HashSet<>();
		for (String source : effectSources)
			referenced.add(assetRoot.resolve(source).normalize());

		List<Path> stale;
		try (Stream<Path> paths = Files.walk(effectsDirectory)) {
			stale = paths
				.filter(Files::isRegularFile)
				.filter(path -> path.getFileName().toString().endsWith(".xml"))
				.filter(path -> !referenced.contains(path.toAbsolutePath().normalize()))
				.toList();
		}
		for (Path path : stale)
			Files.delete(path);
	}

	private static void writeArchiveXml(Path output, SfxArchive archive, Map<Sound, String> effectSources,
		SoundBankCatalog catalog)
		throws IOException
	{
		try (XmlWriter writer = new XmlWriter(output.toFile())) {
			XmlTag rootTag = openTag(writer, TAG_SOUNDS, Map.of());

			List<Sound> sounds = new ArrayList<>(archive.sounds.values());
			sounds.sort(Comparator.comparingInt(sound -> sound.id));
			for (Sound sound : sounds) {
				Map<SfxXmlKey, String> soundAttributes = attributes(
					ATTR_ID, String.format("%04X", sound.id), ATTR_NAME, sound.name);
				if (sound.unused)
					soundAttributes.put(ATTR_UNUSED, "true");
				if (sound.isEmpty())
					soundAttributes.put(ATTR_EMPTY, "true");
				if (!sound.desc.isBlank())
					soundAttributes.put(ATTR_DESC, sound.desc);
				if (!sound.tags.isEmpty())
					soundAttributes.put(ATTR_TAGS, String.join(", ", sound.tags));

				if (sound.isEmpty()) {
					printTag(writer, TAG_SOUND, soundAttributes);
				}
				else {
					String effectSource = effectSources.get(sound);
					if (!sound.isEmpty() && effectSource != null)
						soundAttributes.put(ATTR_SRC, effectSource);
					XmlTag soundTag = openTag(writer, TAG_SOUND, soundAttributes);
					if (!sound.isEmpty() && sound.routing != null)
						writeRouting(writer, sound.routing, sound.tracks.size());
					if (!sound.isEmpty() && effectSource == null)
						writeOneShot(writer, (OneShot) sound.tracks.get(0).definition, catalog);
					writer.closeTag(soundTag);
				}
			}

			writer.closeTag(rootTag);
			writer.saveOrThrow();
		}
	}

	private static void writeEffectXml(Path output, Sound sound, SoundBankCatalog catalog) throws IOException
	{
		try (XmlWriter writer = new XmlWriter(output.toFile())) {
			XmlTag rootTag = openTag(writer, TAG_EFFECT, Map.of());
			writeTracks(writer, sound.tracks, catalog);

			if (!sound.spawnedEffects.isEmpty()) {
				XmlTag spawnedEffectsTag = openTag(writer, TAG_SPAWNED_EFFECTS, Map.of());
				for (int index = 0; index < sound.spawnedEffects.size(); index++) {
					SpawnedEffect spawned = sound.spawnedEffects.get(index);
					XmlTag spawnedTag = openTag(writer, TAG_SPAWNED_EFFECT,
						attributes(ATTR_NAME, spawned.name));
					writeRouting(writer, spawned.routing, spawned.tracks.size());
					writeTracks(writer, spawned.tracks, catalog);
					writer.closeTag(spawnedTag);
				}
				writer.closeTag(spawnedEffectsTag);
			}

			writer.closeTag(rootTag);
			writer.saveOrThrow();
		}
	}

	private static void writeEnvelopesXml(Path output, SfxArchive archive) throws IOException
	{
		try (XmlWriter writer = new XmlWriter(output.toFile())) {
			XmlTag rootTag = openTag(writer, TAG_ENVELOPES, Map.of());
			for (Envelope envelope : archive.envelopes) {
				XmlTag envelopeTag = openTag(writer, TAG_ENVELOPE, attributes(ATTR_NAME, envelope.name));
				EnvelopeXml.writeCommands(writer, envelope.commands);
				writer.closeTag(envelopeTag);
			}

			writer.closeTag(rootTag);
			writer.saveOrThrow();
		}
	}

	private static void writeRouting(XmlWriter writer, Routing routing, int trackCount)
	{
		Map<SfxXmlKey, String> attributes = attributes(ATTR_ALLOCATION, routing.allocation.xmlName());
		if (routing.allocation == Allocation.DYNAMIC) {
			attributes.put(ATTR_MAX_PLAYER, Integer.toString(routing.maxPlayer));
			attributes.put(ATTR_PRIORITY, Integer.toString(routing.priority));
		}
		else if (trackCount == 1) {
			attributes.put(ATTR_PLAYER, Integer.toString(routing.player));
			attributes.put(ATTR_PRIORITY, Integer.toString(routing.priority));
		}
		if (routing.exclusiveGroup != 0)
			attributes.put(ATTR_EXCLUSIVE_GROUP, Integer.toString(routing.exclusiveGroup));
		printTag(writer, TAG_ROUTING, attributes);
	}

	private static void writeTracks(XmlWriter writer, List<Track> tracks, SoundBankCatalog catalog)
	{
		XmlTag tracksTag = openTag(writer, TAG_TRACKS, Map.of());
		List<Track> ordered = new ArrayList<>(tracks);
		ordered.sort(Comparator.comparingInt(track -> track.slot));
		for (int index = 0; index < ordered.size(); index++) {
			Track track = ordered.get(index);
			Map<SfxXmlKey, String> trackAttributes = attributes(ATTR_SLOT, Integer.toString(track.slot));
			if (track.player != null)
				trackAttributes.put(ATTR_PLAYER, Integer.toString(track.player));
			if (track.priority != null)
				trackAttributes.put(ATTR_PRIORITY, Integer.toString(track.priority));

			if (track.definition == Empty.INSTANCE) {
				trackAttributes.put(ATTR_EMPTY, "true");
				printTag(writer, TAG_TRACK, trackAttributes);
			}
			else {
				XmlTag trackTag = openTag(writer, TAG_TRACK, trackAttributes);
				if (track.definition instanceof OneShot oneShot)
					writeOneShot(writer, oneShot, catalog);
				else
					writeSequence(writer, (Sequence) track.definition, catalog);
				writer.closeTag(trackTag);
			}
		}
		writer.closeTag(tracksTag);
	}

	private static void writeOneShot(XmlWriter writer, OneShot oneShot, SoundBankCatalog catalog)
	{
		Map<SfxXmlKey, String> attributes = wavAttributes(catalog, oneShot.bank, oneShot.patch);
		attributes.put(ATTR_VOLUME, Integer.toString(oneShot.volume));
		putNonDefault(attributes, ATTR_PAN, oneShot.pan, 64);
		putNonDefault(attributes, ATTR_REVERB, oneShot.reverb, 0);
		putNonDefault(attributes, ATTR_PITCH, oneShot.pitch, 48);
		putNonDefault(attributes, ATTR_RANDOM_PITCH, oneShot.randomPitch, 0);
		putTrue(attributes, ATTR_LOCK_VOLUME, oneShot.lockVolume);
		putTrue(attributes, ATTR_LOCK_PAN, oneShot.lockPan);
		putTrue(attributes, ATTR_LOCK_PITCH, oneShot.lockPitch);
		putTrue(attributes, ATTR_LOCK_REVERB, oneShot.lockReverb);
		printTag(writer, TAG_ONE_SHOT, attributes);
	}

	private static void writeSequence(XmlWriter writer, Sequence sequence, SoundBankCatalog catalog)
	{
		Map<SfxXmlKey, String> attributes = attributes();
		putTrue(attributes, ATTR_LOCK_VOLUME, sequence.lockVolume);
		putTrue(attributes, ATTR_LOCK_PAN, sequence.lockPan);
		putTrue(attributes, ATTR_LOCK_PITCH, sequence.lockPitch);
		putTrue(attributes, ATTR_LOCK_REVERB, sequence.lockReverb);
		XmlTag sequenceTag = openTag(writer, TAG_SEQUENCE, attributes);
		int loopDepth = 0;
		for (int i = 1; i < sequence.nodes.size(); i++) {
			SfxArchive.Node node = sequence.nodes.get(i);
			if (node instanceof Label label) {
				printTag(writer, TAG_LABEL, attributes(ATTR_NAME, label.name()));
			}
			else {
				Command command = (Command) node;
				if (command.op == Op.END_LOOP && loopDepth > 0) {
					writer.decreaseIndent();
					loopDepth--;
				}
				writeCommand(writer, command, catalog);
				if (command.op == Op.START_LOOP) {
					writer.increaseIndent();
					loopDepth++;
				}
			}
		}
		while (loopDepth > 0) {
			writer.decreaseIndent();
			loopDepth--;
		}
		writer.closeTag(sequenceTag);
	}

	private static void writeCommand(XmlWriter writer, Command command, SoundBankCatalog catalog)
	{
		switch (command.op) {
			case END:
				printTag(writer, TAG_END, Map.of());
				break;
			case DELAY:
				printTag(writer, TAG_DELAY, attributes(ATTR_TICKS, Integer.toString(command.a)));
				break;
			case PLAY:
				printTag(writer, TAG_PLAY, attributes(
					ATTR_PITCH, Integer.toString(command.a), ATTR_VELOCITY, Integer.toString(command.b),
					ATTR_LENGTH, Integer.toString(command.c)));
				break;
			case SET_VOLUME:
				printTag(writer, TAG_SET_VOLUME, attributes(ATTR_VALUE, Integer.toString(command.a)));
				break;
			case SET_PAN:
				printTag(writer, TAG_SET_PAN, attributes(ATTR_VALUE, Integer.toString(command.a)));
				break;
			case SET_INSTRUMENT:
				printTag(writer, TAG_SET_INSTRUMENT, wavAttributes(catalog, command.a, command.b));
				break;
			case SET_REVERB:
				printTag(writer, TAG_SET_REVERB, attributes(ATTR_VALUE, Integer.toString(command.a)));
				break;
			case SET_ENVELOPE:
				printTag(writer, TAG_SET_ENVELOPE, attributes(ATTR_PRESET, Integer.toString(command.a)));
				break;
			case COARSE_TUNE:
				printTag(writer, TAG_COARSE_TUNE,
					attributes(ATTR_SEMITONES, Integer.toString(command.a)));
				break;
			case FINE_TUNE:
				printTag(writer, TAG_FINE_TUNE, attributes(ATTR_CENTS, Integer.toString(command.a)));
				break;
			case WAIT_FOR_END:
				printTag(writer, TAG_WAIT_FOR_END, Map.of());
				break;
			case PITCH_SWEEP:
				printTag(writer, TAG_PITCH_SWEEP, attributes(
					ATTR_TICKS, Integer.toString(command.a), ATTR_PITCH, Integer.toString(command.b)));
				break;
			case START_LOOP:
				printTag(writer, TAG_START_LOOP, attributes(ATTR_COUNT, Integer.toString(command.a)));
				break;
			case END_LOOP:
				printTag(writer, TAG_END_LOOP, Map.of());
				break;
			case WAIT_FOR_RELEASE:
				printTag(writer, TAG_WAIT_FOR_RELEASE, Map.of());
				break;
			case SET_CURRENT_VOLUME:
				printTag(writer, TAG_SET_CURRENT_VOLUME,
					attributes(ATTR_VALUE, Integer.toString(command.a)));
				break;
			case VOLUME_RAMP:
				printTag(writer, TAG_VOLUME_RAMP, attributes(
					ATTR_TICKS, Integer.toString(command.a), ATTR_VALUE, Integer.toString(command.b)));
				break;
			case SET_ALTERNATIVE:
				printTag(writer, TAG_SET_ALTERNATIVE, attributes(
					ATTR_TYPE, Integer.toString(command.a), ATTR_TARGET, command.ref));
				break;
			case STOP:
				printTag(writer, TAG_STOP, Map.of());
				break;
			case JUMP:
				printTag(writer, TAG_JUMP, attributes(ATTR_TARGET, command.ref));
				break;
			case RESTART:
				printTag(writer, TAG_RESTART, Map.of());
				break;
			case NOP:
				printTag(writer, TAG_NOP, Map.of());
				break;
			case SET_RANDOM_PITCH:
				printTag(writer, TAG_SET_RANDOM_PITCH,
					attributes(ATTR_AMOUNT, Integer.toString(command.a)));
				break;
			case SET_RANDOM_VELOCITY:
				printTag(writer, TAG_SET_RANDOM_VELOCITY,
					attributes(ATTR_AMOUNT, Integer.toString(command.a)));
				break;
			case SET_RANDOM_UNUSED:
				printTag(writer, TAG_SET_RANDOM_UNUSED,
					attributes(ATTR_AMOUNT, Integer.toString(command.a)));
				break;
			case SET_PRESS_ENVELOPE:
				printTag(writer, TAG_SET_PRESS_ENVELOPE,
					command.ref == null ? Map.of() : attributes(ATTR_REF, command.ref));
				break;
			case SPAWN:
				printTag(writer, TAG_SPAWN, attributes(ATTR_REF, command.ref));
				break;
			case SET_ALTERNATIVE_VOLUME:
				printTag(writer, TAG_SET_ALTERNATIVE_VOLUME,
					attributes(ATTR_VALUE, Integer.toString(command.a)));
				break;
		}
	}

	private static XmlReader parseDocument(Path source)
	{
		try {
			return new XmlReader(source.toFile());
		}
		catch (InputFileException e) {
			throw new SfxFormatException(source + ": malformed XML: " + e.getMessage(), e);
		}
	}

	private static Element requireRoot(XmlReader reader, Path source, SfxXmlKey expected)
	{
		Element root = reader.getRootElement();
		if (root == null || !root.getTagName().equals(expected.toString()))
			throw new SfxFormatException(source + ": expected root element <" + expected + ">");
		return root;
	}

	private static Element uniqueRequiredChild(Path source, Element parent, SfxXmlKey tagName)
	{
		Element found = null;
		for (Element child : childElements(source, parent)) {
			if (!child.getTagName().equals(tagName.toString()))
				throw unknownElement(source, parent, child);
			if (found != null)
				throw error(source, child, parent.getTagName() + " cannot contain more than one " + tagName);
			found = child;
		}
		if (found == null)
			throw error(source, parent, parent.getTagName() + " is missing required " + tagName);
		return found;
	}

	private static List<Element> childElements(Path source, Element parent)
	{
		List<Element> children = new ArrayList<>();
		for (Node child = parent.getFirstChild(); child != null; child = child.getNextSibling()) {
			if (child instanceof Element element)
				children.add(element);
			else if ((child.getNodeType() == Node.TEXT_NODE || child.getNodeType() == Node.CDATA_SECTION_NODE)
				&& !child.getTextContent().isBlank()) {
				throw error(source, parent, "text content is not allowed here");
			}
		}
		return children;
	}

	private static void requireNoChildren(Path source, Element element)
	{
		if (!childElements(source, element).isEmpty())
			throw error(source, element, element.getTagName() + " must be empty");
	}

	private static void checkAttributes(Path source, Element element, XmlKey ... allowed)
	{
		Set<String> allowedSet = new HashSet<>();
		for (XmlKey key : allowed)
			allowedSet.add(key.toString());
		NamedNodeMap attributes = element.getAttributes();
		for (int i = 0; i < attributes.getLength(); i++) {
			String name = attributes.item(i).getNodeName();
			if (!allowedSet.contains(name))
				throw error(source, element, "unknown attribute: " + name);
		}
	}

	private static String readIdentifier(XmlReader reader, Path source, Element element,
		SfxXmlKey name, boolean required)
	{
		String value = null;
		if (required) {
			reader.requiresAttribute(element, name);
			value = reader.getAttribute(element, name);
		}
		else if (reader.hasAttribute(element, name)) {
			value = reader.getAttribute(element, name);
		}
		if (value != null && !IDENTIFIER.matcher(value).matches())
			throw error(source, element, name + " is not a valid identifier: " + value);
		return value;
	}

	private static String labelReferenceName(Path source, Element element, String reference)
	{
		if (reference == null || !IDENTIFIER.matcher(reference).matches())
			throw locationError(source, element, "invalid label reference: " + reference);
		return reference;
	}

	private static int readHexByte(XmlReader reader, Path source, Element element,
		SfxXmlKey name, boolean optional)
	{
		if (!optional || reader.hasAttribute(element, name)) {
			reader.requiresAttribute(element, name);
			if (!reader.getAttribute(element, name).matches("[0-9A-F]{2}"))
				throw error(source, element, name + " must be exactly two uppercase hexadecimal digits");
		}
		return readRangedHex(reader, source, element, name, 0, 255, optional);
	}

	private static SoundBankCatalog.InstrumentAddress readWavAddress(XmlReader reader,
		Path source, Element element, SoundBankCatalog catalog)
	{
		reader.requiresAttribute(element, ATTR_WAV);
		String wav = reader.getAttribute(element, ATTR_WAV);
		int envelope = readRangedHex(reader, source, element, ATTR_ENVELOPE, 0, 3, true);
		try {
			return catalog.getAddress(wav, envelope);
		}
		catch (StarRodException e) {
			throw error(source, element, e.getMessage());
		}
	}

	private static int readRangedHex(XmlReader reader, Path source, Element element,
		SfxXmlKey name, int min, int max, boolean optional)
	{
		if (optional && !reader.hasAttribute(element, name))
			return min;
		reader.requiresAttribute(element, name);
		int parsed = reader.readHex(element, name);
		if (parsed < min || parsed > max)
			throw error(source, element, name + " must be " + min + " through " + max);
		return parsed;
	}

	private static int readRangedInt(XmlReader reader, Path source, Element element,
		SfxXmlKey name, int min, int max, boolean optional)
	{
		return readRangedInt(reader, source, element, name, min, max, optional, min);
	}

	private static int readRangedInt(XmlReader reader, Path source, Element element,
		SfxXmlKey name, int min, int max, boolean optional, int defaultValue)
	{
		int parsed;
		if (optional)
			parsed = reader.readInt(element, name, defaultValue);
		else {
			reader.requiresAttribute(element, name);
			parsed = reader.readInt(element, name);
		}
		if (parsed < min || parsed > max)
			throw error(source, element, name + " must be " + min + " through " + max);
		return parsed;
	}

	private static Path requireInputFile(Path path, String description)
	{
		if (path == null)
			throw new IllegalArgumentException(description + " path is null");
		Path absolute = path.toAbsolutePath().normalize();
		if (!Files.isRegularFile(absolute))
			throw new SfxFormatException(description + " does not exist: " + absolute);
		return absolute;
	}

	private static Path resolveInput(Path root, String relative, Path source, Element element, SfxXmlKey attribute)
	{
		validateRelativePath(relative, source, element, attribute);
		Path resolved;
		try {
			resolved = root.resolve(relative).normalize();
		}
		catch (InvalidPathException e) {
			throw error(source, element, "invalid " + attribute + " path: " + relative);
		}
		if (!resolved.startsWith(root))
			throw error(source, element, attribute + " escapes the SFX asset root: " + relative);
		if (!Files.isRegularFile(resolved))
			throw error(source, element, attribute + " file does not exist: " + relative);

		checkExactPathCase(root, relative, source, element);
		try {
			Path rootReal = root.toRealPath();
			Path resolvedReal = resolved.toRealPath();
			if (!resolvedReal.startsWith(rootReal))
				throw error(source, element, attribute + " resolves outside the SFX asset root: " + relative);
		}
		catch (IOException e) {
			// Some sandboxed Windows environments deny toRealPath even when the
			// file is readable. Exact segment walking above also rejects symlink
			// components, so lexical containment remains sufficient in that case.
		}
		return resolved;
	}

	private static Path resolveOutput(Path root, String relative, Path source)
	{
		validateRelativePath(relative, source, null, ATTR_SRC);
		Path resolved;
		try {
			resolved = root.resolve(relative).normalize();
		}
		catch (InvalidPathException e) {
			throw modelError(source, "invalid output path: " + relative);
		}
		if (!resolved.startsWith(root))
			throw modelError(source, "output path escapes the SFX asset root: " + relative);
		return resolved;
	}

	private static void validateRelativePath(String relative, Path source, Element element, XmlKey attribute)
	{
		if (relative == null || relative.isBlank())
			throw locationError(source, element, attribute + " path is blank");
		if (relative.contains("\\") || relative.startsWith("/") || relative.startsWith("//")
			|| relative.matches("^[A-Za-z]:.*"))
			throw locationError(source, element,
				attribute + " must be a forward-slash relative path: " + relative);
		for (int index = 0; index < relative.length(); index++) {
			if (relative.charAt(index) < 0x20)
				throw locationError(source, element, attribute + " contains a control character");
		}
		for (String segment : relative.split("/", -1)) {
			if (segment.isEmpty() || segment.equals(".") || segment.equals(".."))
				throw locationError(source, element,
					attribute + " contains an invalid path segment: " + relative);
		}
	}

	private static void checkExactPathCase(Path root, String relative, Path source, Element element)
	{
		Path current = root;
		for (String segment : relative.split("/")) {
			boolean exact;
			try (Stream<Path> children = Files.list(current)) {
				exact = children.anyMatch(path -> path.getFileName().toString().equals(segment));
			}
			catch (IOException e) {
				throw error(source, element, "could not inspect path casing for: " + relative);
			}
			if (!exact)
				throw error(source, element, "path casing does not match the filesystem: " + relative);
			current = current.resolve(segment);
			if (Files.isSymbolicLink(current))
				throw error(source, element, "symbolic links are not allowed in SFX source paths: " + relative);
		}
	}

	private static boolean isPointerBacked(int soundID)
	{
		return soundID >= 0x0001 && soundID <= 0x03FF && ((soundID - 1) & 0xFF) < 0xC0;
	}

	private static boolean inRange(int value, int min, int max)
	{
		return value >= min && value <= max;
	}

	private static void requireRange(int value, int min, int max, Op op, String field, Path source)
	{
		if (!inRange(value, min, max)) {
			String prefix = op == null ? "" : op + " ";
			throw modelError(source, prefix + field + " must be " + min + " through " + max);
		}
	}

	private static void validateIdentifier(String value, String description, Path source)
	{
		if (value == null || !IDENTIFIER.matcher(value).matches())
			throw modelError(source, description + " is not a valid identifier: " + value);
	}

	private static SfxFormatException unknownElement(Path source, Element parent, Element child)
	{
		return error(source, child,
			"unknown element <" + child.getTagName() + "> inside <" + parent.getTagName() + ">");
	}

	private static SfxXmlKey tagKey(Path source, Element parent, Element child)
	{
		SfxXmlKey key = SfxXmlKey.forTag(child.getTagName());
		if (key == null)
			throw unknownElement(source, parent, child);
		return key;
	}

	private static SfxFormatException error(Path source, Element element, String message)
	{
		return locationError(source, element, message);
	}

	private static SfxFormatException modelError(Path source, String message)
	{
		return locationError(source, null, message);
	}

	private static SfxFormatException locationError(Path source, Element element, String message)
	{
		String location = source == null ? "SFX asset" : source.toString();
		if (element != null)
			location += " <" + element.getTagName() + ">";
		return new SfxFormatException(location + ": " + message);
	}

	private static Map<SfxXmlKey, String> attributes(Object ... pairs)
	{
		if ((pairs.length & 1) != 0)
			throw new IllegalArgumentException("Attribute arguments must be name/value pairs");
		Map<SfxXmlKey, String> attributes = new LinkedHashMap<>();
		for (int index = 0; index < pairs.length; index += 2) {
			if (!(pairs[index] instanceof SfxXmlKey key) || !(pairs[index + 1] instanceof String value))
				throw new IllegalArgumentException("XML attributes must be SfxXmlKey/String pairs");
			attributes.put(key, value);
		}
		return attributes;
	}

	private static Map<SfxXmlKey, String> wavAttributes(SoundBankCatalog catalog,
		int bank, int patch)
	{
		try {
			SoundBankCatalog.WavReference reference = catalog.getWav(bank, patch);
			Map<SfxXmlKey, String> attributes = attributes(ATTR_WAV, reference.wav);
			if (reference.envelope != 0)
				attributes.put(ATTR_ENVELOPE, Integer.toHexString(reference.envelope).toUpperCase(Locale.ROOT));
			return attributes;
		}
		catch (StarRodException e) {
			throw new SfxFormatException(e.getMessage(), e);
		}
	}

	private static void putNonDefault(Map<SfxXmlKey, String> attributes, SfxXmlKey name,
		int value, int defaultValue)
	{
		if (value != defaultValue)
			attributes.put(name, Integer.toString(value));
	}

	private static void putTrue(Map<SfxXmlKey, String> attributes, SfxXmlKey name, boolean value)
	{
		if (value)
			attributes.put(name, "true");
	}

	private static XmlTag createTag(XmlWriter writer, SfxXmlKey name, boolean selfClosing,
		Map<SfxXmlKey, String> attributes)
	{
		XmlTag tag = writer.createTag(name, selfClosing);
		for (Map.Entry<SfxXmlKey, String> attribute : attributes.entrySet())
			writer.addAttribute(tag, attribute.getKey(), attribute.getValue());
		return tag;
	}

	private static XmlTag openTag(XmlWriter writer, SfxXmlKey name, Map<SfxXmlKey, String> attributes)
	{
		XmlTag tag = createTag(writer, name, false, attributes);
		writer.openTag(tag);
		return tag;
	}

	private static void printTag(XmlWriter writer, SfxXmlKey name, Map<SfxXmlKey, String> attributes)
	{
		writer.printTag(createTag(writer, name, true, attributes));
	}
}
