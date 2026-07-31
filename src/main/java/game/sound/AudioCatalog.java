package game.sound;

import static game.sound.AudioModder.SongListKey.ATTR_BGM;
import static game.sound.AudioModder.SongListKey.ATTR_ID;
import static game.sound.AudioModder.SongListKey.ATTR_OLD_BGM;
import static game.sound.AudioModder.SongListKey.ATTR_UNUSED;
import static game.sound.AudioModder.SongListKey.TAG_SONG;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.apache.commons.io.FilenameUtils;
import org.w3c.dom.Element;

import app.input.InputFileException;
import game.shared.ProjectDatabase;
import game.shared.ProjectDatabase.ConstEnum;
import util.xml.XmlKey;
import util.xml.XmlWrapper.XmlReader;
import util.xml.XmlWrapper.XmlTag;
import util.xml.XmlWrapper.XmlWriter;

public final class AudioCatalog
{
	private static final int LAST_AMBIENT_ID = 0xF;
	private static final int RADIO_ID = 0x10;
	private static final int RADIO_PLAYER_COUNT = 4;
	private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");
	private static final Set<Integer> VANILLA_UNUSED_SONG_IDS = Set.of(
		0x01, 0x06, 0x23, 0x2D, 0x2E, 0x2F, 0x36, 0x43,
		0x45, 0x4D, 0x4F, 0x5E, 0x8F, 0x92, 0x93, 0x96,
		0x97, 0x98, 0x99, 0x9A, 0x9B, 0x9C, 0x9D, 0x9E, 0x9F);
	private static final Set<Integer> VANILLA_UNUSED_AMBIENT_IDS = Set.of(
		0x06, 0x0D, 0x0E, 0x0F);

	private static final int[] AMBIENT_EXTRA_INDICES = {
		0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09,
		0x0A, 0x0B, 0x0C, 0x0D, 0x0E, 0x0F, 0x10,
		0x11, 0x12
	};

	private static final String[] DEFAULT_AMBIENT_MSEQS = {
		"DB_501.mseq", "DC_502.mseq", "E5_511.mseq", "E6_512.mseq",
		"DD_503.mseq", "DE_504.mseq", "DF_505.mseq", "E0_506.mseq",
		"E1_507.mseq", "E2_508.mseq", "E3_509.mseq", "E4_510.mseq",
		"E7_513.mseq", "E5_511.mseq", "E5_511.mseq", "E5_511.mseq"
	};

	private static final int[] RADIO_SONG_IDS = { 0x2D, 0x2E, 0x2F, 0x2D };
	private static final String[] DEFAULT_RADIO_MSEQS = {
		"E8_521.mseq", "E9_522.mseq", "EA_523.mseq", "EB_521.mseq"
	};

	public enum Key implements XmlKey
	{
		// @formatter:off
		TAG_AMBIENT_SOUNDS	("AmbientSounds"),
		TAG_AMBIENT_SOUND	("AmbientSound"),
		TAG_RADIO			("Radio"),
		TAG_STATION			("Station"),
		ATTR_ID				("id"),
		ATTR_NAME			("name"),
		ATTR_UNUSED			("unused"),
		ATTR_MSEQ			("mseq"),
		ATTR_BANK			("bank"),
		ATTR_PLAYER			("player"),
		ATTR_SONG			("song");
		// @formatter:on

		private final String key;

		private Key(String key)
		{
			this.key = key;
		}

		@Override
		public String toString()
		{
			return key;
		}
	}

	public static void writeAmbientSounds(File xmlFile, List<String> extraFiles) throws IOException
	{
		if (extraFiles.size() <= 0x17)
			throw new IOException("Audio extra-file list is too short for ambience data");

		ConstEnum ambientNames = ProjectDatabase.getFromNamespace("AmbientSounds");

		try (XmlWriter xmw = new XmlWriter(xmlFile)) {
			XmlTag rootTag = xmw.createTag(Key.TAG_AMBIENT_SOUNDS, false);
			xmw.openTag(rootTag);

			for (int id = 0; id <= LAST_AMBIENT_ID; id++) {
				XmlTag ambientTag = xmw.createTag(Key.TAG_AMBIENT_SOUND, true);
				xmw.addHex(ambientTag, Key.ATTR_ID, "%02X", id);
				xmw.addAttribute(ambientTag, Key.ATTR_NAME, ambientNames.getName(id));
				if (isVanillaUnusedAmbientID(id))
					xmw.addBoolean(ambientTag, Key.ATTR_UNUSED, true);
				xmw.addAttribute(ambientTag, Key.ATTR_MSEQ,
					extraFiles.get(AMBIENT_EXTRA_INDICES[id]));
				xmw.printTag(ambientTag);
			}

			XmlTag radioTag = xmw.createTag(Key.TAG_RADIO, false);
			xmw.addHex(radioTag, Key.ATTR_ID, "%02X", RADIO_ID);
			xmw.addAttribute(radioTag, Key.ATTR_NAME, ambientNames.getName(RADIO_ID));
			xmw.addAttribute(radioTag, Key.ATTR_BANK, extraFiles.get(0x17));
			xmw.openTag(radioTag);

			for (int player = 0; player < RADIO_PLAYER_COUNT; player++) {
				XmlTag stationTag = xmw.createTag(Key.TAG_STATION, true);
				xmw.addInt(stationTag, Key.ATTR_PLAYER, player);
				xmw.addHex(stationTag, Key.ATTR_SONG, "%02X", RADIO_SONG_IDS[player]);
				if (player == RADIO_PLAYER_COUNT - 1)
					xmw.addBoolean(stationTag, Key.ATTR_UNUSED, true);
				xmw.addAttribute(stationTag, Key.ATTR_MSEQ, extraFiles.get(0x13 + player));
				xmw.printTag(stationTag);
			}

			xmw.closeTag(radioTag);
			xmw.closeTag(rootTag);
			xmw.saveOrThrow();
		}
	}

	public static Map<String, String> readSongNames(File xmlFile)
	{
		Map<String, List<String>> names = new LinkedHashMap<>();
		Map<Integer, String> songNames = readSongNameTable(xmlFile);
		XmlReader xmr = new XmlReader(xmlFile);
		List<Element> songElements = xmr.getTags(xmr.getRootElement(), TAG_SONG);

		for (int index = 0; index < songElements.size(); index++) {
			Element songElement = songElements.get(index);
			int id = index;
			if (xmr.hasAttribute(songElement, ATTR_ID))
				id = xmr.readHex(songElement, ATTR_ID);
			String name = songNames.get(id);
			if (name == null)
				continue;
			boolean unused = readUnused(xmr, songElement, ATTR_UNUSED);
			if (unused && name.equals(unusedName(id)))
				continue;

			String filename;
			if (xmr.hasAttribute(songElement, ATTR_OLD_BGM))
				filename = xmr.getAttribute(songElement, ATTR_OLD_BGM);
			else {
				xmr.requiresAttribute(songElement, ATTR_BGM);
				filename = xmr.getAttribute(songElement, ATTR_BGM);
			}
			addName(names, filename, name);
		}

		return joinNames(names);
	}

	public static Map<String, String> readMseqNames(File xmlFile, File songsXml)
	{
		if (!xmlFile.isFile())
			return createDefaultMseqNames();

		AmbientData data = readAmbientSounds(xmlFile);
		Map<Integer, String> songNames = readSongNameTable(songsXml);
		Map<String, List<String>> names = new LinkedHashMap<>();

		for (int id = 0; id <= LAST_AMBIENT_ID; id++) {
			if (!data.ambientUnused[id] || !data.ambientNames[id].equals(unusedName(id)))
				addName(names, data.ambientMseqs[id], data.ambientNames[id]);
		}

		for (int player = 0; player < RADIO_PLAYER_COUNT - 1; player++) {
			String songName = songNames.get(data.radioSongIDs[player]);
			addName(names, data.radioMseqs[player], songName);
		}

		String songName = songNames.get(data.radioSongIDs[3]);
		String unusedName = songName == null ? null : songName + "Copy (unused)";
		addName(names, data.radioMseqs[3], unusedName);
		return joinNames(names);
	}

	public static Map<Integer, String> readSongNameTable(File xmlFile)
	{
		XmlReader xmr = new XmlReader(xmlFile);
		Element root = xmr.getRootElement();
		if (!root.getTagName().equals(AudioModder.SongListKey.TAG_SONG_LIST.toString()))
			xmr.complain("Expected root tag: " + AudioModder.SongListKey.TAG_SONG_LIST);

		Map<Integer, String> names = new LinkedHashMap<>();
		Set<Integer> ids = new HashSet<>();
		Set<String> identifiers = new HashSet<>();
		for (Element element : xmr.getTags(root, TAG_SONG)) {
			xmr.requiresAttribute(element, ATTR_ID);
			xmr.requiresAttribute(element, AudioModder.SongListKey.ATTR_SONG_NAME);
			int id = xmr.readHex(element, ATTR_ID);
			if (!ids.add(id))
				throw new InputFileException(xmlFile, String.format("Song ID %X is defined more than once", id));
			readUnused(xmr, element, ATTR_UNUSED);

			String name = readIdentifier(xmr, xmlFile, element, AudioModder.SongListKey.ATTR_SONG_NAME);
			if (!identifiers.add(name))
				throw new InputFileException(xmlFile, "Song name is defined more than once: " + name);
			names.put(id, name);
		}
		return names;
	}

	public static Map<Integer, String> readAmbientNameTable(File xmlFile)
	{
		AmbientData data = readAmbientSounds(xmlFile);
		Map<Integer, String> names = new LinkedHashMap<>();
		Set<String> identifiers = new HashSet<>();
		for (int id = 0; id <= LAST_AMBIENT_ID; id++) {
			String name = data.ambientNames[id];
			if (!identifiers.add(name))
				throw new InputFileException(xmlFile, "Ambient sound name is defined more than once: " + name);
			names.put(id, name);
		}
		if (!identifiers.add(data.radioName))
			throw new InputFileException(xmlFile, "Ambient sound name is defined more than once: " + data.radioName);
		names.put(RADIO_ID, data.radioName);
		return names;
	}

	public static int[] readAmbientExtraFiles(File xmlFile, Map<String, Integer> sbnLookup)
	{
		AmbientData data = readAmbientSounds(xmlFile);
		int[] extraFiles = new int[0x18];

		extraFiles[0] = getFileIndex(xmlFile, sbnLookup, "DAT1.sef");
		extraFiles[1] = getFileIndex(xmlFile, sbnLookup, "SET1.per");
		extraFiles[2] = getFileIndex(xmlFile, sbnLookup, "SET1.prg");

		for (int id = 0; id <= LAST_AMBIENT_ID; id++) {
			extraFiles[AMBIENT_EXTRA_INDICES[id]] =
				getFileIndex(xmlFile, sbnLookup, data.ambientMseqs[id]);
		}

		for (int player = 0; player < RADIO_PLAYER_COUNT; player++) {
			extraFiles[0x13 + player] =
				getFileIndex(xmlFile, sbnLookup, data.radioMseqs[player]);
		}
		extraFiles[0x17] = getFileIndex(xmlFile, sbnLookup, data.radioBank);

		return extraFiles;
	}

	public static String getName(Map<String, String> names, String filename)
	{
		return names.get(fileKey(filename));
	}

	private static AmbientData readAmbientSounds(File xmlFile)
	{
		XmlReader xmr = new XmlReader(xmlFile);
		Element root = xmr.getRootElement();
		if (!root.getTagName().equals(Key.TAG_AMBIENT_SOUNDS.toString()))
			xmr.complain("Expected root tag: " + Key.TAG_AMBIENT_SOUNDS);

		AmbientData data = new AmbientData();
		List<Element> ambientElements = xmr.getTags(root, Key.TAG_AMBIENT_SOUND);
		if (ambientElements.size() != LAST_AMBIENT_ID + 1) {
			xmr.complain(String.format("Expected %d ambient sounds", LAST_AMBIENT_ID + 1));
		}

		for (int index = 0; index < ambientElements.size(); index++) {
			Element ambientElement = ambientElements.get(index);
			xmr.requiresAttribute(ambientElement, Key.ATTR_ID);
			xmr.requiresAttribute(ambientElement, Key.ATTR_NAME);
			xmr.requiresAttribute(ambientElement, Key.ATTR_MSEQ);
			int id = xmr.readHex(ambientElement, Key.ATTR_ID);
			if (id != index)
				xmr.complain(Key.TAG_AMBIENT_SOUND + " ID is out of order; do not skip IDs");
			data.ambientNames[id] = readIdentifier(xmr, xmlFile, ambientElement, Key.ATTR_NAME);
			data.ambientUnused[id] = readUnused(xmr, ambientElement, Key.ATTR_UNUSED);
			data.ambientMseqs[id] = xmr.getAttribute(ambientElement, Key.ATTR_MSEQ);
		}

		Element radioElement = xmr.getUniqueRequiredTag(root, Key.TAG_RADIO);
		xmr.requiresAttribute(radioElement, Key.ATTR_ID);
		xmr.requiresAttribute(radioElement, Key.ATTR_NAME);
		xmr.requiresAttribute(radioElement, Key.ATTR_BANK);
		if (xmr.readHex(radioElement, Key.ATTR_ID) != RADIO_ID)
			xmr.complain(Key.TAG_RADIO + " must use ambient sound ID 10");
		data.radioName = readIdentifier(xmr, xmlFile, radioElement, Key.ATTR_NAME);
		data.radioBank = xmr.getAttribute(radioElement, Key.ATTR_BANK);

		List<Element> stationElements = xmr.getTags(radioElement, Key.TAG_STATION);
		if (stationElements.size() != RADIO_PLAYER_COUNT)
			xmr.complain("Radio must define four stations");

		for (int index = 0; index < stationElements.size(); index++) {
			readRadioPlayer(xmr, stationElements.get(index), data, index);
		}
		return data;
	}

	private static String readIdentifier(XmlReader xmr, File xmlFile, Element element, XmlKey key)
	{
		String name = xmr.getAttribute(element, key);
		if (!IDENTIFIER.matcher(name).matches())
			throw new InputFileException(xmlFile, element.getTagName() + " has invalid name: " + name);
		return name;
	}

	private static boolean readUnused(XmlReader xmr, Element element, XmlKey key)
	{
		if (!xmr.hasAttribute(element, key))
			return false;
		if (!xmr.readBoolean(element, key, false))
			xmr.complain(element.getTagName() + " attribute " + key + " must be true when present");
		return true;
	}

	private static void readRadioPlayer(XmlReader xmr, Element element, AmbientData data, int index)
	{
		xmr.requiresAttribute(element, Key.ATTR_PLAYER);
		xmr.requiresAttribute(element, Key.ATTR_SONG);
		xmr.requiresAttribute(element, Key.ATTR_MSEQ);
		readUnused(xmr, element, Key.ATTR_UNUSED);

		int player = xmr.readInt(element, Key.ATTR_PLAYER);
		if (player != index)
			xmr.complain(element.getTagName() + " player is out of order; do not skip players");
		data.radioSongIDs[player] = xmr.readHex(element, Key.ATTR_SONG);
		data.radioMseqs[player] = xmr.getAttribute(element, Key.ATTR_MSEQ);
	}

	private static Map<String, String> createDefaultMseqNames()
	{
		Map<String, List<String>> names = new LinkedHashMap<>();
		ConstEnum ambientNames = ProjectDatabase.getFromNamespace("AmbientSounds");
		ConstEnum songNames = ProjectDatabase.getFromNamespace("Song");

		for (int id = 0; id <= LAST_AMBIENT_ID; id++) {
			String name = ambientNames.getName(id);
			if (!isVanillaUnusedAmbientID(id) || !name.equals(unusedName(id)))
				addName(names, DEFAULT_AMBIENT_MSEQS[id], name);
		}
		for (int player = 0; player < RADIO_PLAYER_COUNT - 1; player++)
			addName(names, DEFAULT_RADIO_MSEQS[player], songNames.getName(RADIO_SONG_IDS[player]));
		addName(names, DEFAULT_RADIO_MSEQS[3], songNames.getName(RADIO_SONG_IDS[3]) + "Copy (unused)");
		return joinNames(names);
	}

	private static int getFileIndex(File xmlFile, Map<String, Integer> sbnLookup, String filename)
	{
		Integer index = sbnLookup.get(filename);
		if (index == null)
			throw new InputFileException(xmlFile, "Ambient sound references unregistered file: " + filename);
		return index;
	}

	private static void addName(Map<String, List<String>> names, String filename, String name)
	{
		if (name == null || name.isBlank())
			return;

		String key = fileKey(filename);
		List<String> fileNames = names.get(key);
		if (fileNames == null) {
			fileNames = new ArrayList<>();
			names.put(key, fileNames);
		}
		if (!fileNames.contains(name))
			fileNames.add(name);
	}

	private static Map<String, String> joinNames(Map<String, List<String>> names)
	{
		Map<String, String> joined = new HashMap<>();
		for (Map.Entry<String, List<String>> entry : names.entrySet())
			joined.put(entry.getKey(), String.join(" / ", entry.getValue()));
		return joined;
	}

	private static String fileKey(String filename)
	{
		return FilenameUtils.getBaseName(filename).toUpperCase(Locale.ROOT);
	}

	public static boolean isVanillaUnusedSongID(int id)
	{
		return VANILLA_UNUSED_SONG_IDS.contains(id);
	}

	private static boolean isVanillaUnusedAmbientID(int id)
	{
		return VANILLA_UNUSED_AMBIENT_IDS.contains(id);
	}

	private static String unusedName(int id)
	{
		return String.format("Unused_%02X", id);
	}

	private static class AmbientData
	{
		private final String[] ambientNames = new String[LAST_AMBIENT_ID + 1];
		private final boolean[] ambientUnused = new boolean[LAST_AMBIENT_ID + 1];
		private final String[] ambientMseqs = new String[LAST_AMBIENT_ID + 1];
		private final String[] radioMseqs = new String[RADIO_PLAYER_COUNT];
		private final int[] radioSongIDs = new int[RADIO_PLAYER_COUNT];
		private String radioName;
		private String radioBank;
	}

	private AudioCatalog()
	{}
}
