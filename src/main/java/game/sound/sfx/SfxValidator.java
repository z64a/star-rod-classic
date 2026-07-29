package game.sound.sfx;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import game.sound.engine.EnvelopeCommand;
import game.sound.engine.EnvelopeOp;
import game.sound.sfx.SfxArchive.Allocation;
import game.sound.sfx.SfxArchive.Command;
import game.sound.sfx.SfxArchive.Definition;
import game.sound.sfx.SfxArchive.Empty;
import game.sound.sfx.SfxArchive.Envelope;
import game.sound.sfx.SfxArchive.Label;
import game.sound.sfx.SfxArchive.Node;
import game.sound.sfx.SfxArchive.OneShot;
import game.sound.sfx.SfxArchive.Op;
import game.sound.sfx.SfxArchive.Routing;
import game.sound.sfx.SfxArchive.Sequence;
import game.sound.sfx.SfxArchive.Sound;
import game.sound.sfx.SfxArchive.SpawnedEffect;
import game.sound.sfx.SfxArchive.Track;

public final class SfxValidator
{
	private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_.-]*");
	private static final int MAX_FILE_SIZE_WITH_16_BIT_OFFSETS = 0x10000;
	private static final int ENVELOPE_INTERVAL_COUNT = 95;

	/**
	 * Validates an archive and returns non-fatal diagnostics.
	 *
	 * @throws SfxFormatException if one or more errors are found
	 */
	public static List<String> validate(SfxArchive archive)
	{
		return new Validation(archive, null).run();
	}

	/** Validates semantic data and requires the supplied ID/name authority. */
	public static List<String> validate(SfxArchive archive, SfxNames authoritativeNames)
	{
		if (authoritativeNames == null)
			throw new IllegalArgumentException("authoritativeNames is null");
		return new Validation(archive, authoritativeNames).run();
	}

	/** Returns whether an ID uses a pointer/routing entry in the normal tables. */
	public static boolean isPointerBackedID(int id)
	{
		if (id < 0x0001 || id > 0x0400)
			return false;

		int index = id & 0xFF;
		return index >= 0x01 && index <= 0xC0;
	}

	/** Returns whether an ID's four-byte table slot contains the effect itself. */
	public static boolean isDirectID(int id)
	{
		return SfxNames.isRawSoundID(id) && !isPointerBackedID(id);
	}

	/** Tests exact representability by the engine's four-byte COMPACT mode. */
	public static boolean isCompactRepresentable(OneShot oneShot)
	{
		return oneShot != null
			&& inRange(oneShot.bank, 0, 0xFF)
			&& inRange(oneShot.patch, 0, 0xFF)
			&& oneShot.pan == 64
			&& oneShot.reverb == 0
			&& oneShot.pitch == 48
			&& inRange(oneShot.volume, 3, 127)
			&& (oneShot.volume & 3) == 3
			&& inRange(oneShot.randomPitch, 0, 56)
			&& (oneShot.randomPitch & 7) == 0;
	}

	private static boolean inRange(int value, int min, int max)
	{
		return value >= min && value <= max;
	}

	private static final class Validation
	{
		private final SfxArchive archive;
		private final SfxNames authoritativeNames;
		private final List<String> errors = new ArrayList<>();
		private final List<String> warnings = new ArrayList<>();
		private final Set<String> envelopeNames = new HashSet<>();

		private Validation(SfxArchive archive, SfxNames authoritativeNames)
		{
			this.archive = archive;
			this.authoritativeNames = authoritativeNames;
		}

		private List<String> run()
		{
			if (archive == null)
				throw new SfxFormatException("SFX archive is null");

			validateArchiveProperties();
			validateEnvelopes();
			validateSounds();

			if (!errors.isEmpty()) {
				StringBuilder message = new StringBuilder("Invalid SFX archive:");
				for (String error : errors)
					message.append(System.lineSeparator()).append(" - ").append(error);
				throw new SfxFormatException(message.toString());
			}

			return List.copyOf(warnings);
		}

		private void validateArchiveProperties()
		{
			if (archive.name == null || archive.name.length() != 4) {
				error("Archive name must contain exactly four ASCII characters");
			}
			else {
				for (int i = 0; i < archive.name.length(); i++) {
					char c = archive.name.charAt(i);
					if (c < 0x20 || c > 0x7E) {
						error("Archive name must contain exactly four printable ASCII characters");
						break;
					}
				}
			}

			if (!inRange(archive.maxBinarySize, 0x22, MAX_FILE_SIZE_WITH_16_BIT_OFFSETS))
				error("maxBinarySize must be between 0022 and 10000");
			else if (archive.maxBinarySize > SfxArchive.DEFAULT_MAX_BINARY_SIZE)
				warn(String.format("maxBinarySize %04X exceeds the vanilla DAT1 allocation of %04X",
					archive.maxBinarySize, SfxArchive.DEFAULT_MAX_BINARY_SIZE));
		}

		private void validateEnvelopes()
		{
			for (int i = 0; i < archive.envelopes.size(); i++) {
				Envelope envelope = archive.envelopes.get(i);
				String context = "Envelope #" + i;
				if (envelope == null) {
					error(context + " is null");
					continue;
				}

				context = "Envelope " + printable(envelope.name);
				validateIdentifier(envelope.name, context + " name");
				if (envelope.name != null && !envelopeNames.add(envelope.name))
					error(context + " is defined more than once");

				validateEnvelopeCommands(envelope, context);
			}
		}

		private void validateEnvelopeCommands(Envelope envelope, String context)
		{
			if (envelope.commands.isEmpty()) {
				error(context + " has no commands");
				return;
			}

			boolean loopActive = false;
			boolean foundEnd = false;
			for (int i = 0; i < envelope.commands.size(); i++) {
				EnvelopeCommand command = envelope.commands.get(i);
				String commandContext = context + " command " + i;
				if (command == null || command.op == null) {
					error(commandContext + " has no operation");
					continue;
				}

				switch (command.op) {
					case POINT:
						range(command.value, 0, 127, commandContext + " value");
						range(command.durationIndex, 0, ENVELOPE_INTERVAL_COUNT - 1,
							commandContext + " duration index");
						break;
					case SET_SCALE:
						range(command.value, 0, 127, commandContext + " value");
						break;
					case ADD_SCALE:
						range(command.value, -128, 127, commandContext + " value");
						break;
					case START_LOOP:
						range(command.value, 0, 255, commandContext + " count");
						if (loopActive)
							error(commandContext + " starts a nested loop, but the engine has one envelope loop register");
						loopActive = true;
						break;
					case END_LOOP:
						if (!loopActive)
							error(commandContext + " ends a loop before one is started");
						loopActive = false;
						break;
					case END:
						if (i != envelope.commands.size() - 1)
							error(commandContext + " is not the final envelope command");
						foundEnd = true;
						break;
				}
			}

			if (loopActive)
				error(context + " has a StartLoop without a matching EndLoop");
			if (!foundEnd)
				error(context + " must end with End");
		}

		private void validateSounds()
		{
			Set<Integer> ids = new HashSet<>();
			Map<String, Integer> names = new HashMap<>();

			for (Map.Entry<Integer, Sound> entry : archive.sounds.entrySet()) {
				Integer mapID = entry.getKey();
				Sound sound = entry.getValue();
				if (mapID == null) {
					error("Sound map contains a null ID");
					continue;
				}
				if (sound == null) {
					error(String.format("Sound %04X is null", mapID));
					continue;
				}

				String context = String.format("Sound %04X (%s)", sound.id, printable(sound.name));
				if (mapID != sound.id)
					error(String.format("Sound map key %04X disagrees with its sound ID %04X", mapID, sound.id));
				if (!ids.add(sound.id))
					error(String.format("Sound ID %04X is defined more than once", sound.id));
				if (!SfxNames.isRawSoundID(sound.id))
					error(String.format("Sound ID %04X is outside 0001-0400 and 2001-2140", sound.id));

				validateIdentifier(sound.name, context + " primary name");
				registerSoundName(names, sound.name, sound.id, "primary name");

				for (String alias : sound.aliases) {
					validateIdentifier(alias, context + " alias");
					registerSoundName(names, alias, sound.id, "alias");
				}
				validateAuthoritativeNames(sound, context);

				validateSourcePath(sound.source, context);
				validateSound(sound, context);
			}
		}

		private void validateAuthoritativeNames(Sound sound, String context)
		{
			if (authoritativeNames == null)
				return;

			List<String> expected = authoritativeNames.get(sound.id);
			if (expected.isEmpty() || authoritativeNames.hasGeneratedName(sound.id)) {
				String expectedName = authoritativeNames.preferredName(sound.id);
				if (!sound.generatedName)
					error(context + " must be marked as using a generated name");
				if (!expectedName.equals(sound.name))
					error(context + " must use generated name " + expectedName);
				if (!sound.aliases.isEmpty())
					error(context + " cannot have aliases because its generated name table row has none");
				return;
			}

			if (sound.generatedName)
				error(context + " is marked generated even though DX names this ID " + expected.get(0));
			if (!expected.get(0).equals(sound.name))
				error(context + " must use authoritative DX primary name " + expected.get(0));
			List<String> expectedAliases = expected.subList(1, expected.size());
			if (!expectedAliases.equals(sound.aliases))
				error(context + " must use authoritative DX aliases " + expectedAliases);
		}

		private void registerSoundName(Map<String, Integer> names, String name, int id, String kind)
		{
			if (name == null)
				return;
			Integer previous = names.putIfAbsent(name, id);
			if (previous != null)
				error(String.format("Sound %04X %s %s duplicates a name already owned by %04X",
					id, kind, name, previous));
		}

		private void validateSourcePath(String source, String context)
		{
			if (source == null)
				return;
			if (source.isBlank()) {
				error(context + " has an empty source path");
				return;
			}
			if (source.indexOf('\\') >= 0)
				error(context + " source path must use forward slashes: " + source);
			if (source.startsWith("/") || source.matches("^[A-Za-z]:.*"))
				error(context + " source path must be relative: " + source);
			for (String component : source.split("/", -1)) {
				if (component.equals(".."))
					error(context + " source path may not traverse outside the asset root: " + source);
				else if (component.isEmpty() || component.equals("."))
					error(context + " source path is not normalized: " + source);
			}
		}

		private void validateSound(Sound sound, String context)
		{
			if (sound.isEmpty()) {
				if (sound.routing != null)
					error(context + " is empty and must not have routing");
				if (!sound.spawnedEffects.isEmpty())
					error(context + " is empty and must not contain spawned effects");
				return;
			}

			Set<String> spawnedNames = collectSpawnedNames(sound, context);

			if (isDirectID(sound.id)) {
				if (sound.routing != null)
					error(context + " is in a direct table and must not have routing");
				if (!sound.spawnedEffects.isEmpty())
					error(context + " is in a direct table and cannot contain spawned effects");
				if (!sound.canInlineOneShot()) {
					error(context + " is in a direct table and must contain exactly one OneShot track");
				}
				else {
					OneShot oneShot = (OneShot) sound.tracks.get(0).definition;
					if (!isCompactRepresentable(oneShot))
						error(context + " is not exactly representable by four-byte COMPACT mode");
				}
			}
			else if (sound.routing == null) {
				error(context + " is pointer-backed and requires routing");
			}

			validateTrackSet(sound.tracks, sound.routing, context, spawnedNames);

			for (int i = 0; i < sound.spawnedEffects.size(); i++) {
				SpawnedEffect spawned = sound.spawnedEffects.get(i);
				if (spawned == null)
					continue;
				String spawnedContext = context + " spawned effect " + printable(spawned.name);
				if (spawned.routing == null)
					error(spawnedContext + " requires routing");
				validateTrackSet(spawned.tracks, spawned.routing, spawnedContext, spawnedNames);
			}
		}

		private Set<String> collectSpawnedNames(Sound sound, String context)
		{
			Set<String> result = new HashSet<>();
			for (int i = 0; i < sound.spawnedEffects.size(); i++) {
				SpawnedEffect spawned = sound.spawnedEffects.get(i);
				if (spawned == null) {
					error(context + " spawned effect #" + i + " is null");
					continue;
				}
				validateIdentifier(spawned.name, context + " spawned effect name");
				if (spawned.name != null && !result.add(spawned.name))
					error(context + " defines spawned effect " + spawned.name + " more than once");
			}
			return result;
		}

		private void validateTrackSet(List<Track> tracks, Routing routing, String context, Set<String> spawnedNames)
		{
			int count = tracks.size();
			if (count != 1 && count != 2 && count != 4 && count != 8) {
				error(context + " must contain exactly 1, 2, 4, or 8 track slots, found " + count);
			}

			boolean anyDefinition = false;
			for (int i = 0; i < tracks.size(); i++) {
				Track track = tracks.get(i);
				if (track == null) {
					error(context + " track slot " + i + " is null");
					continue;
				}
				if (track.slot != i)
					error(context + " track slots must be contiguous and ordered; expected " + i + " but found " + track.slot);
				if (track.definition == null) {
					error(context + " track " + track.slot + " has no definition");
					continue;
				}
				if (!(track.definition instanceof Empty))
					anyDefinition = true;
			}
			if (!tracks.isEmpty() && !anyDefinition)
				error(context + " has only empty tracks; use an empty Sound definition instead");

			validateRouting(routing, tracks, context);
			for (Track track : tracks) {
				if (track != null && track.definition != null)
					validateDefinition(track.definition, context + " track " + track.slot, spawnedNames);
			}
		}

		private void validateRouting(Routing routing, List<Track> tracks, String context)
		{
			if (routing == null)
				return;
			if (routing.allocation == null) {
				error(context + " routing has no allocation mode");
				return;
			}

			range(routing.exclusiveGroup, 0, 3, context + " routing exclusiveGroup");
			if (routing.allocation == Allocation.DYNAMIC) {
				range(routing.maxPlayer, 0, 7, context + " routing maxPlayer");
				range(routing.priority, 0, 3, context + " routing priority");
				if (tracks.size() > 1 && routing.maxPlayer != 7)
					error(context + " uses dynamic polyphony and requires maxPlayer=7");
				for (Track track : tracks) {
					if (track != null && (track.player != null || track.priority != null))
						error(context + " uses dynamic allocation, so track-level player and priority must be omitted");
				}
				return;
			}

			if (tracks.size() <= 1) {
				range(routing.player, 0, 7, context + " routing player");
				range(routing.priority, 0, 3, context + " routing priority");
				for (Track track : tracks) {
					if (track != null && (track.player != null || track.priority != null))
						error(context + " is fixed mono, so player and priority belong on Routing, not Track");
				}
				return;
			}

			boolean foundEmpty = false;
			for (Track track : tracks) {
				if (track == null || track.definition == null)
					continue;
				if (track.definition instanceof Empty) {
					foundEmpty = true;
					if (track.player != null || track.priority != null)
						error(context + " empty fixed-poly track " + track.slot + " must omit player and priority");
				}
				else {
					if (foundEmpty)
						error(context + " fixed-poly empty tracks must be trailing");
					if (track.player == null)
						error(context + " fixed-poly track " + track.slot + " requires player");
					else
						range(track.player, 0, 7, context + " track " + track.slot + " player");
					if (track.priority == null)
						error(context + " fixed-poly track " + track.slot + " requires priority");
					else
						range(track.priority, 0, 3, context + " track " + track.slot + " priority");
				}
			}
		}

		private void validateDefinition(Definition definition, String context, Set<String> spawnedNames)
		{
			if (definition instanceof Empty)
				return;
			if (definition instanceof OneShot oneShot) {
				validateOneShot(oneShot, context);
				return;
			}
			if (definition instanceof Sequence sequence) {
				validateSequence(sequence, context, spawnedNames);
				return;
			}
			error(context + " has an unknown definition type");
		}

		private void validateOneShot(OneShot oneShot, String context)
		{
			range(oneShot.bank, 0, 255, context + " OneShot bank");
			range(oneShot.patch, 0, 255, context + " OneShot patch");
			range(oneShot.volume, 0, 255, context + " OneShot volume");
			range(oneShot.pan, 0, 255, context + " OneShot pan");
			range(oneShot.reverb, 0, 255, context + " OneShot reverb");
			range(oneShot.pitch, 0, 127, context + " OneShot pitch");
			if (!inRange(oneShot.randomPitch, 0, 120) || (oneShot.randomPitch & 7) != 0)
				error(context + " OneShot randomPitch must be a multiple of 8 from 0 through 120");
			if (inRange(oneShot.pan, 128, 255))
				warn(context + " OneShot pan exceeds the normal authored range of 0-127");
		}

		private void validateSequence(Sequence sequence, String context, Set<String> spawnedNames)
		{
			validateIdentifier(sequence.entry, context + " Sequence entry");
			Set<String> labels = new HashSet<>();
			for (int i = 0; i < sequence.nodes.size(); i++) {
				Node node = sequence.nodes.get(i);
				if (node == null) {
					error(context + " Sequence node " + i + " is null");
					continue;
				}
				if (node instanceof Label label) {
					validateIdentifier(label.name(), context + " label");
					if (label.name() != null && !labels.add(label.name()))
						error(context + " defines label " + label.name() + " more than once");
				}
			}

			if (sequence.entry != null && !labels.contains(sequence.entry))
				error(context + " Sequence entry label does not exist: " + sequence.entry);

			for (int i = 0; i < sequence.nodes.size(); i++) {
				Node node = sequence.nodes.get(i);
				if (node instanceof Command command)
					validateCommand(command, context + " command " + i, labels, spawnedNames);
			}
		}

		private void validateCommand(Command command, String context, Set<String> labels, Set<String> spawnedNames)
		{
			if (command.op == null) {
				error(context + " has no operation");
				return;
			}

			switch (command.op) {
				case END, WAIT_FOR_END, END_LOOP, WAIT_FOR_RELEASE, STOP, RESTART:
					break;
				case NOP:
					warn(context + " contains behaviorally inert Nop");
					break;
				case DELAY:
					range(command.a, 1, 2167, context + " ticks");
					break;
				case PLAY:
					range(command.a, 0, 87, context + " pitch");
					range(command.b, 0, 127, context + " velocity");
					range(command.c, 0, 16575, context + " length");
					break;
				case SET_VOLUME, SET_REVERB, SET_ENVELOPE, FINE_TUNE, SET_CURRENT_VOLUME,
						SET_RANDOM_PITCH, SET_RANDOM_VELOCITY, SET_ALTERNATIVE_VOLUME:
					range(command.a, 0, 255, context + " value");
					break;
				case SET_RANDOM_UNUSED:
					range(command.a, 0, 255, context + " value");
					warn(context + " uses SetRandomUnused, which is behaviorally unused by the current engine");
					break;
				case SET_PAN:
					range(command.a, 0, 255, context + " value");
					if (inRange(command.a, 128, 255))
						warn(context + " pan exceeds the normal authored range of 0-127");
					break;
				case SET_INSTRUMENT:
					range(command.a, 0, 255, context + " bank");
					range(command.b, 0, 255, context + " patch");
					break;
				case COARSE_TUNE:
					range(command.a, -128, 127, context + " semitones");
					break;
				case PITCH_SWEEP:
					range(command.a, 0, 65535, context + " ticks");
					range(command.b, 0, 127, context + " pitch");
					break;
				case START_LOOP:
					range(command.a, 0, 255, context + " count");
					break;
				case VOLUME_RAMP:
					range(command.a, 0, 65535, context + " ticks");
					range(command.b, 0, 255, context + " value");
					break;
				case SET_ALTERNATIVE:
					range(command.a, 1, 3, context + " type");
					validateLabelReference(command.ref, context + " target", labels);
					break;
				case JUMP:
					validateLabelReference(command.ref, context + " target", labels);
					break;
				case SET_PRESS_ENVELOPE:
					validateEnvelopeReference(command.ref, context);
					break;
				case SPAWN:
					validateSpawnReference(command.ref, context, spawnedNames);
					break;
			}
		}

		private void validateLabelReference(String reference, String context, Set<String> labels)
		{
			if (reference == null || reference.isBlank()) {
				error(context + " is missing");
				return;
			}
			if (reference.startsWith("local:")) {
				String name = reference.substring("local:".length());
				validateIdentifier(name, context);
				if (!labels.contains(name))
					error(context + " does not exist: " + reference);
			}
			else if (reference.startsWith("shared:")) {
				String name = reference.substring("shared:".length());
				validateIdentifier(name, context);
				error(context + " uses unsupported shared reference " + reference);
			}
			else {
				error(context + " must use local:name or shared:name syntax: " + reference);
			}
		}

		private void validateEnvelopeReference(String reference, String context)
		{
			if (reference == null)
				return; // clearing the custom envelope is meaningful
			validateIdentifier(reference, context + " envelope reference");
			if (!envelopeNames.contains(reference))
				error(context + " references missing envelope " + reference);
		}

		private void validateSpawnReference(String reference, String context, Set<String> spawnedNames)
		{
			if (reference == null || reference.isBlank()) {
				error(context + " spawn reference is missing");
				return;
			}
			if (reference.startsWith("shared:")) {
				String name = reference.substring("shared:".length());
				validateIdentifier(name, context + " spawn reference");
				error(context + " uses unsupported shared reference " + reference);
				return;
			}

			String name = reference.startsWith("local:")
				? reference.substring("local:".length())
				: reference;
			validateIdentifier(name, context + " spawn reference");
			if (!spawnedNames.contains(name))
				error(context + " references missing spawned effect " + reference);
		}

		private void validateIdentifier(String value, String context)
		{
			if (value == null || !IDENTIFIER.matcher(value).matches())
				error(context + " must match [A-Za-z_][A-Za-z0-9_.-]*: " + printable(value));
		}

		private void range(int value, int min, int max, String context)
		{
			if (!inRange(value, min, max))
				error(context + " must be in " + min + "-" + max + ", found " + value);
		}

		private void error(String message)
		{
			errors.add(message);
		}

		private void warn(String message)
		{
			warnings.add(message);
		}

		private static String printable(String value)
		{
			return value == null ? "<null>" : value;
		}
	}

	private SfxValidator()
	{}
}
