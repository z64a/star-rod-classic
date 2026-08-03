package game.sound;

import static app.Directories.DUMP_AUDIO;
import static app.Directories.DUMP_AUDIO_RAW;
import static app.Directories.FN_AUDIO_AMBIENTS;
import static app.Directories.FN_AUDIO_BANKS;
import static app.Directories.FN_AUDIO_SONGS;
import static app.Directories.MOD_AUDIO;
import static app.Directories.MOD_AUDIO_BUILD;
import static app.Directories.MOD_AUDIO_OVERRIDE;
import static app.Directories.MOD_AUDIO_RAW;
import static game.sound.AudioModder.BankListKey.ATTR_BANK_GROUP;
import static game.sound.AudioModder.BankListKey.ATTR_BANK_INDEX;
import static game.sound.AudioModder.BankListKey.ATTR_BANK_NAME;
import static game.sound.AudioModder.BankListKey.TAG_BANK;
import static game.sound.AudioModder.BankListKey.TAG_BANK_LIST;
import static game.sound.AudioModder.SongListKey.ATTR_BGM;
import static game.sound.AudioModder.SongListKey.ATTR_BK1;
import static game.sound.AudioModder.SongListKey.ATTR_BK2;
import static game.sound.AudioModder.SongListKey.ATTR_BK3;
import static game.sound.AudioModder.SongListKey.ATTR_ID;
import static game.sound.AudioModder.SongListKey.ATTR_OLD_BGM;
import static game.sound.AudioModder.SongListKey.ATTR_SONG_NAME;
import static game.sound.AudioModder.SongListKey.ATTR_UNUSED;
import static game.sound.AudioModder.SongListKey.ATTR_X;
import static game.sound.AudioModder.SongListKey.ATTR_Y;
import static game.sound.AudioModder.SongListKey.ATTR_Z;
import static game.sound.AudioModder.SongListKey.TAG_SONG;
import static game.sound.AudioModder.SongListKey.TAG_SONG_LIST;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.w3c.dom.Element;

import app.Environment;
import app.input.IOUtils;
import app.input.InputFileException;
import game.shared.ProjectDatabase;
import game.shared.ProjectDatabase.ConstEnum;
import game.sound.bgm.SongModder;
import game.sound.mseq.Mseq;
import game.sound.sfx.SfxFormatException;
import game.sound.sfx.SfxXml;
import patcher.Patcher;
import patcher.RomPatcher;
import util.Logger;
import util.xml.XmlKey;
import util.xml.XmlWrapper.XmlReader;
import util.xml.XmlWrapper.XmlTag;
import util.xml.XmlWrapper.XmlWriter;

public class AudioModder
{
	public static void main(String[] args) throws IOException
	{
		Environment.initialize();
		dumpAudio();
		Environment.exit();
	}

	private static final int SBN_BASE = 0xF00000;
	private static final int AUDIO_DATA_END = 0x1942C40;
	// audio heap allocations which do not depend on SBN or INIT contents
	private static final int AUDIO_HEAP_FIXED_ALLOC_SIZE = 0x3A9A0;
	private static final String FN_SFX_BINARY = "DAT1.sef";
	private static final String FN_SFX_ARCHIVE = SfxXml.FN_SOUND_EFFECTS;
	private static final String FN_LEGACY_FILE_LIST = "Files.xml";
	private static final String RESOURCE_SBN_FILES = "/audio/DefaultFileOrder.txt";
	private static boolean warnedLegacyFileList;

	private static class FileEntry
	{
		private String name;
		private File file;
		private int typeOrder;
		private int vanillaOrder = -1;
		private int fmt;
		private int romOffset;
		private int size;
		private int paddedSize;
		private String type;
	}

	private static class AudioHeapUsage
	{
		private int fileList;
		private int songList;
		private int resourceList;
		private int banks;
		private int sef;

		private int getTotal()
		{
			return fileList + songList + resourceList + banks + sef;
		}
	}

	private static class SongEntry
	{
		private String bgmName;
		private String bk1Name = "";
		private String bk2Name = "";
		private String bk3Name = "";

		private int bgmFileIndex;
		private int bk1FileIndex;
		private int bk2FileIndex;
		private int bk3FileIndex;
	}

	public enum SongListKey implements XmlKey
	{
		// @formatter:off
		TAG_SONG_LIST	("SongList"),
		TAG_SONG		("Song"),
		ATTR_ID			("id"),
		ATTR_SONG_NAME	("name"),
		ATTR_UNUSED		("unused"),
		ATTR_BGM		("bgm"),
		ATTR_BK1		("bk1"),
		ATTR_BK2		("bk2"),
		ATTR_BK3		("bk3"),
		// backward compatibility
		ATTR_OLD_BGM	("BGM"),
		ATTR_X			("x"),
		ATTR_Y			("y"),
		ATTR_Z			("z");
		// @formatter:on

		private final String key;

		private SongListKey(String key)
		{
			this.key = key;
		}

		@Override
		public String toString()
		{
			return key;
		}
	}

	public static class BankEntry
	{
		public final String name;
		public final int fileIndex;

		public final int bankGroup;
		public final int bankIndex;

		public BankEntry(String name, int fileIndex, int bankGroup, int bankIndex)
		{
			this.name = name;
			this.fileIndex = fileIndex;
			this.bankGroup = bankGroup;
			this.bankIndex = bankIndex;
		}
	}

	public enum BankListKey implements XmlKey
	{
		// @formatter:off
		TAG_BANK_LIST	("BankList"),
		TAG_BANK		("Bank"),
		ATTR_BANK_NAME	("bk"),
		ATTR_BANK_GROUP	("group"),
		ATTR_BANK_INDEX	("index");
		// @formatter:on

		private final String key;

		private BankListKey(String key)
		{
			this.key = key;
		}

		@Override
		public String toString()
		{
			return key;
		}
	}

	private static int dumpSBN(RandomAccessFile raf, List<String> dumpedFilenames) throws IOException
	{
		/*
		typedef struct SBNHeader {
		 0x00  AUFileMetadata mdata; // uses identifer 'SBN '
		 0x08  char unused_08[8];
		 0x10  s32 tableOffset; // offset in the SBN file of the file table (== sizeof(SBNHeader))
		 0x14  s32 numEntries;  // number of entries in the SBN file table
		 0x18  s32 fileSize;    // full size of the SBN file (unread)
		 0x1C  s32 versionOffset;
		 0x20  char unused_04[4];
		 0x24  s32 INIToffset;
		 0x28  char reserved[24];
		 0x40  SBNFileEntry entries[0];
		} SBNHeader; // size = 0x40
		*/

		raf.seek(SBN_BASE);
		raf.skipBytes(16);

		int tableStart = raf.readInt();
		int numEntries = raf.readInt();
		raf.skipBytes(12);
		int initOffset = raf.readInt();

		for (int i = 0; i < numEntries; i++) {
			raf.seek(SBN_BASE + tableStart + 8 * i);
			int offset = raf.readInt();
			//	int word2 = raf.readInt();
			//	int fmt = word2 >>> 24;
			//	int lenX = word2 & 0x00FFFFFF; // bytes from offset (includes header)

			raf.seek(SBN_BASE + offset);
			String ext = IOUtils.readString(raf, 4).trim().toLowerCase();
			int len = raf.readInt();
			String name = IOUtils.readString(raf, 4);

			String fileName;
			switch (ext) {
				case "bgm":
				case "mseq":
					fileName = String.format("%02X_%s.%s", i, name.trim(), ext);
					break;
				default: // bk, per, prg, sef
					fileName = String.format("%s.%s", name.trim(), ext);
					break;
			}
			dumpedFilenames.add(fileName);

			byte[] fileBytes = new byte[len + 0xF & 0xFFFFFFF0];

			raf.seek(SBN_BASE + offset);
			raf.read(fileBytes);
			File out = new File(DUMP_AUDIO_RAW + fileName);
			FileUtils.writeByteArrayToFile(out, fileBytes);

			Logger.logf("Dumped %s from %X", fileName, SBN_BASE + offset);
		}

		return initOffset;
	}

	private static void dumpINIT(RandomAccessFile raf, int initOffset, List<String> dumpedFilenames) throws IOException
	{
		ConstEnum songEnum = ProjectDatabase.getFromLibraryName("songID");

		try (XmlWriter xmw = new XmlWriter(DUMP_AUDIO.getFile(FN_AUDIO_BANKS))) {
			XmlTag listTag = xmw.createTag(TAG_BANK_LIST, false);
			xmw.openTag(listTag);

			raf.seek(SBN_BASE + initOffset + 0x20);
			while (true) {
				int fileIndex = raf.readShort();
				int bankIndex = raf.readByte();
				int bankGroup = raf.readByte();

				if (fileIndex == -1)
					break;

				XmlTag bankTag = xmw.createTag(TAG_BANK, true);
				xmw.addAttribute(bankTag, ATTR_BANK_NAME, dumpedFilenames.get(fileIndex));
				xmw.addHex(bankTag, ATTR_BANK_GROUP, bankGroup);
				xmw.addHex(bankTag, ATTR_BANK_INDEX, bankIndex);
				xmw.printTag(bankTag);
			}

			xmw.closeTag(listTag);
			xmw.save();
		}

		try (XmlWriter xmw = new XmlWriter(DUMP_AUDIO.getFile(FN_AUDIO_SONGS))) {
			XmlTag listTag = xmw.createTag(TAG_SONG_LIST, false);
			xmw.openTag(listTag);

			raf.seek(SBN_BASE + initOffset + 0x130);
			int songID = 0;
			while (true) {
				int bgmFileIndex = raf.readShort();
				int bk1FileIndex = raf.readShort();
				int bk2FileIndex = raf.readShort();
				int bk3FileIndex = raf.readShort();

				if (bgmFileIndex == -1)
					break;

				XmlTag songTag = xmw.createTag(TAG_SONG, true);

				xmw.addHex(songTag, ATTR_ID, "%02X", songID);
				if (songEnum.has(songID))
					xmw.addAttribute(songTag, ATTR_SONG_NAME, songEnum.getName(songID));
				if (AudioCatalog.isVanillaUnusedSongID(songID))
					xmw.addBoolean(songTag, ATTR_UNUSED, true);
				xmw.addAttribute(songTag, ATTR_BGM, dumpedFilenames.get(bgmFileIndex));

				if (bk1FileIndex != 0)
					xmw.addAttribute(songTag, ATTR_BK1, dumpedFilenames.get(bk1FileIndex));
				if (bk2FileIndex != 0)
					xmw.addAttribute(songTag, ATTR_BK2, dumpedFilenames.get(bk2FileIndex));
				if (bk3FileIndex != 0)
					xmw.addAttribute(songTag, ATTR_BK3, dumpedFilenames.get(bk3FileIndex));

				xmw.printTag(songTag);

				songID++;
			}

			xmw.closeTag(listTag);
			xmw.save();
		}

		List<String> resourceFileNames = readResourceFileNames(raf, initOffset, dumpedFilenames);
		AudioCatalog.writeAmbientSounds(
			DUMP_AUDIO.getFile(FN_AUDIO_AMBIENTS), resourceFileNames);
	}

	private static List<String> readResourceFileNames(RandomAccessFile raf, int initOffset,
		List<String> dumpedFilenames) throws IOException
	{
		raf.seek(SBN_BASE + initOffset + 0x10);
		int listOffset = raf.readUnsignedShort();
		int listSize = raf.readUnsignedShort();
		if ((listSize & 1) != 0)
			throw new IOException("INIT resource list has an invalid size");

		List<String> resourceFileNames = new ArrayList<>();
		raf.seek(SBN_BASE + initOffset + listOffset);
		for (int i = 0; i < listSize / 2; i++) {
			int fileIndex = raf.readUnsignedShort();
			if (fileIndex == 0xFFFF)
				break;
			if (fileIndex >= dumpedFilenames.size())
				throw new IOException("INIT resource list references an invalid SBN file index");
			resourceFileNames.add(dumpedFilenames.get(fileIndex));
		}
		return resourceFileNames;
	}

	public static void dumpAudio() throws IOException
	{
		RandomAccessFile raf = Environment.getBaseRomReader();

		List<String> dumpedFilenames = new ArrayList<>(250);

		int initOffset = dumpSBN(raf, dumpedFilenames);
		dumpINIT(raf, initOffset, dumpedFilenames);
		BankModder.dumpAll();
		SoundBankCatalog soundBankCatalog = SoundBankCatalog.loadDump();

		File sfxBinary = DUMP_AUDIO_RAW.getFile(FN_SFX_BINARY);
		if (sfxBinary.exists()) {
			SfxModder.DumpSummary summary = SfxModder.dump(
				sfxBinary.toPath(), DUMP_AUDIO.toFile().toPath(), soundBankCatalog);
			Logger.logf("Dumped %d DAT1 sound rows to %d editable SFX files.",
				summary.sounds(), summary.effectFiles());
			for (String warning : summary.warnings())
				Logger.logWarning(warning);
		}

		SongModder.dumpAll();
		Mseq.dumpAll();
		InstrumentsModder.dump();
		DrumsModder.dump();

		raf.close();
	}

	public static void prepareBuildDirectory() throws IOException
	{
		FileUtils.forceMkdir(MOD_AUDIO_BUILD.toFile());
		FileUtils.cleanDirectory(MOD_AUDIO_BUILD.toFile());
	}

	public static void buildSoundBanks() throws IOException
	{
		BankModder.buildAll();
		DrumsModder.build();
		InstrumentsModder.build();
	}

	public static void buildAudioFiles() throws IOException
	{
		SongModder.buildAll();
		Mseq.buildAll();

		File sfxArchive = MOD_AUDIO.getFile(FN_SFX_ARCHIVE);
		if (sfxArchive.isFile() && hasOverride(FN_SFX_BINARY)) {
			Logger.log("Using audio override for " + FN_SFX_BINARY);
		}
		else if (sfxArchive.isFile()) {
			File output = MOD_AUDIO_BUILD.getFile(FN_SFX_BINARY);
			try {
				SoundBankCatalog soundBankCatalog = SoundBankCatalog.loadMod();
				SfxModder.BuildSummary summary = SfxModder.build(
					sfxArchive.toPath(), output.toPath(), soundBankCatalog);
				Logger.logf("Built SFX archive: %X bytes.", summary.size());
				for (String warning : summary.warnings())
					Logger.logWarning(warning);
			}
			catch (SfxFormatException e) {
				throw new InputFileException(sfxArchive, e);
			}
		}
	}

	public static boolean hasOverride(String fileName)
	{
		return MOD_AUDIO_OVERRIDE.getFile(fileName).isFile();
	}

	private static int getFileTypeOrder(String fileName)
	{
		String ext = FilenameUtils.getExtension(fileName).toLowerCase();
		switch (ext) {
			case "bgm":
				return 0;
			case "sef":
				return 1;
			case "bk":
				return 2;
			case "per":
				return 3;
			case "prg":
				return 4;
			case "mseq":
				return 5;
			default:
				return -1;
		}
	}

	private static HashMap<String, Integer> readDefaultFileOrder() throws IOException
	{
		InputStream stream = AudioModder.class.getResourceAsStream(RESOURCE_SBN_FILES);
		if (stream == null)
			throw new IOException("Missing bundled " + RESOURCE_SBN_FILES);

		List<String> fileNames = IOUtils.readFormattedTextStream(stream, false);
		HashMap<String, Integer> vanillaOrder = new HashMap<>(fileNames.size());
		int previousType = -1;

		for (int i = 0; i < fileNames.size(); i++) {
			String fileName = fileNames.get(i);
			if (!new File(fileName).getName().equals(fileName))
				throw new IOException("Bundled SBN filename contains a path: " + fileName);

			int typeOrder = getFileTypeOrder(fileName);
			if (typeOrder < 0)
				throw new IOException("Bundled SBN registry contains an unsupported file: " + fileName);
			if (typeOrder < previousType)
				throw new IOException("Bundled SBN registry is not ordered by file type: " + fileName);
			previousType = typeOrder;

			if (vanillaOrder.put(fileName.toLowerCase(), i) != null)
				throw new IOException("Bundled SBN registry contains a duplicate file: " + fileName);
		}

		return vanillaOrder;
	}

	private static void discoverAudioFiles(File directory, HashMap<String, FileEntry> discoveredFiles) throws IOException
	{
		if (!directory.exists())
			return;
		if (!directory.isDirectory())
			throw new InputFileException(directory, "Audio asset path is not a directory");

		File[] files = directory.listFiles(File::isFile);
		if (files == null)
			throw new InputFileException(directory, "Could not enumerate audio files");

		for (File file : files) {
			String fileName = file.getName();
			int typeOrder = getFileTypeOrder(fileName);
			if (typeOrder < 0)
				throw new InputFileException(file, "Unsupported file in audio member directory");

			String key = fileName.toLowerCase();
			FileEntry af = discoveredFiles.get(key);
			if (af == null) {
				af = new FileEntry();
				af.name = fileName;
				af.typeOrder = typeOrder;
				discoveredFiles.put(key, af);
			}
			else if (!af.name.equals(fileName)) {
				throw new InputFileException(file, "Audio filename differs only by case from " + af.name);
			}

			// Called in raw, build, override order so later sources take precedence.
			af.file = file;
		}
	}

	private static int compareFileEntries(FileEntry a, FileEntry b)
	{
		int result = Integer.compare(a.typeOrder, b.typeOrder);
		if (result != 0)
			return result;

		if (a.vanillaOrder >= 0 && b.vanillaOrder >= 0)
			return Integer.compare(a.vanillaOrder, b.vanillaOrder);
		if (a.vanillaOrder >= 0)
			return -1;
		if (b.vanillaOrder >= 0)
			return 1;

		result = a.name.compareToIgnoreCase(b.name);
		if (result != 0)
			return result;
		return a.name.compareTo(b.name);
	}

	private static List<FileEntry> buildFileList(HashMap<String, Integer> sbnFileIndices) throws IOException
	{
		if (!warnedLegacyFileList && MOD_AUDIO.getFile(FN_LEGACY_FILE_LIST).isFile()) {
			Logger.logWarning(FN_LEGACY_FILE_LIST + " is no longer used; SBN members are discovered automatically");
			warnedLegacyFileList = true;
		}

		HashMap<String, Integer> vanillaOrder = readDefaultFileOrder();
		HashMap<String, FileEntry> discoveredFiles = new HashMap<>(256);
		discoverAudioFiles(MOD_AUDIO_RAW.toFile(), discoveredFiles);
		discoverAudioFiles(MOD_AUDIO_BUILD.toFile(), discoveredFiles);
		discoverAudioFiles(MOD_AUDIO_OVERRIDE.toFile(), discoveredFiles);

		List<FileEntry> fileList = new ArrayList<>(discoveredFiles.values());
		for (FileEntry af : fileList) {
			Integer order = vanillaOrder.get(af.name.toLowerCase());
			if (order != null)
				af.vanillaOrder = order;
		}
		fileList.sort(AudioModder::compareFileEntries);

		if (fileList.size() > 0x10000)
			throw new InputFileException(MOD_AUDIO.toFile(), "SBN contains more than 65536 files");

		for (int index = 0; index < fileList.size(); index++) {
			FileEntry af = fileList.get(index);
			sbnFileIndices.put(af.name, index);
		}
		if (!sbnFileIndices.containsKey(FN_SFX_BINARY))
			throw new InputFileException(MOD_AUDIO.toFile(), "Missing mandatory audio file: " + FN_SFX_BINARY);

		return fileList;
	}

	private static List<SongEntry> readSongListXML(File xmlFile, HashMap<String, Integer> sbnFileIndices) throws IOException
	{
		List<SongEntry> songList = new ArrayList<>(256);

		XmlReader xmr = new XmlReader(xmlFile);

		Element root = xmr.getRootElement();
		List<Element> songElems = xmr.getTags(root, TAG_SONG);

		for (int index = 0; index < songElems.size(); index++) {
			Element songElem = songElems.get(index);

			xmr.requiresAttribute(songElem, ATTR_ID);
			xmr.requiresAttribute(songElem, ATTR_SONG_NAME);
			int id = SoundXml.readHex(xmr, songElem, ATTR_ID, 0, 0xFF);

			if (id != index)
				throw new InputFileException(xmlFile, TAG_SONG + " ID is out of order! Do not skip song IDs.");
			if (xmr.hasAttribute(songElem, ATTR_UNUSED)
				&& !xmr.readBoolean(songElem, ATTR_UNUSED, false))
				xmr.complain(TAG_SONG + " attribute " + ATTR_UNUSED + " must be true when present");

			SongEntry s = new SongEntry();
			if (xmr.hasAttribute(songElem, ATTR_OLD_BGM)) {
				s.bgmName = xmr.getAttribute(songElem, ATTR_OLD_BGM);

				if (xmr.hasAttribute(songElem, ATTR_X))
					s.bk1Name = xmr.getAttribute(songElem, ATTR_X);

				if (xmr.hasAttribute(songElem, ATTR_Y))
					s.bk2Name = xmr.getAttribute(songElem, ATTR_Y);

				if (xmr.hasAttribute(songElem, ATTR_Z))
					s.bk3Name = xmr.getAttribute(songElem, ATTR_Z);
			}
			else {
				xmr.requiresAttribute(songElem, ATTR_BGM);
				s.bgmName = xmr.getAttribute(songElem, ATTR_BGM);

				if (xmr.hasAttribute(songElem, ATTR_BK1))
					s.bk1Name = xmr.getAttribute(songElem, ATTR_BK1);

				if (xmr.hasAttribute(songElem, ATTR_BK2))
					s.bk2Name = xmr.getAttribute(songElem, ATTR_BK2);

				if (xmr.hasAttribute(songElem, ATTR_BK3))
					s.bk3Name = xmr.getAttribute(songElem, ATTR_BK3);
			}

			songList.add(s);

			if (!sbnFileIndices.containsKey(s.bgmName))
				throw new InputFileException(xmlFile, "Song references missing audio file: " + s.bgmName);
			s.bgmFileIndex = sbnFileIndices.get(s.bgmName);

			if (!s.bk1Name.isEmpty()) {
				if (!sbnFileIndices.containsKey(s.bk1Name))
					throw new InputFileException(xmlFile, "Song references missing audio file: " + s.bk1Name);
				s.bk1FileIndex = sbnFileIndices.get(s.bk1Name);
			}

			if (!s.bk2Name.isEmpty()) {
				if (!sbnFileIndices.containsKey(s.bk2Name))
					throw new InputFileException(xmlFile, "Song references missing audio file: " + s.bk2Name);
				s.bk2FileIndex = sbnFileIndices.get(s.bk2Name);
			}

			if (!s.bk3Name.isEmpty()) {
				if (!sbnFileIndices.containsKey(s.bk3Name))
					throw new InputFileException(xmlFile, "Song references missing audio file: " + s.bk3Name);
				s.bk3FileIndex = sbnFileIndices.get(s.bk3Name);
			}
		}

		return songList;
	}

	private static List<BankEntry> readBankListXML(File xmlFile, HashMap<String, Integer> sbnFileIndices) throws IOException
	{
		List<BankEntry> bankList = new ArrayList<>(256);
		Set<Integer> bankSlots = new HashSet<>();

		XmlReader xmr = new XmlReader(xmlFile);

		Element root = xmr.getRootElement();
		List<Element> bankElems = xmr.getTags(root, TAG_BANK);

		for (int i = 0; i < bankElems.size(); i++) {
			Element bankElem = bankElems.get(i);

			xmr.requiresAttribute(bankElem, ATTR_BANK_NAME);
			xmr.requiresAttribute(bankElem, ATTR_BANK_GROUP);
			xmr.requiresAttribute(bankElem, ATTR_BANK_INDEX);

			String name = xmr.getAttribute(bankElem, ATTR_BANK_NAME);
			int bankGroup = SoundXml.readHex(xmr, bankElem, ATTR_BANK_GROUP, 1, 6);
			int bankIndex = SoundXml.readHex(xmr, bankElem, ATTR_BANK_INDEX, 0, 0xF);

			if (!sbnFileIndices.containsKey(name))
				throw new InputFileException(xmlFile, "Bank references missing audio file: " + name);

			int slot = bankGroup << 4 | bankIndex;
			if (!bankSlots.add(slot))
				throw new InputFileException(xmlFile, String.format("Bank group %X index %X is listed multiple times", bankGroup, bankIndex));

			bankList.add(new BankEntry(name, sbnFileIndices.get(name), bankGroup, bankIndex));
		}

		return bankList;
	}

	private static ByteBuffer createBufferForSBN(List<FileEntry> fileList) throws IOException
	{
		int sbnSize = 0x40 + 8 * fileList.size();
		sbnSize = (sbnSize + 15) & -16;

		ByteBuffer sbnBuffer = ByteBuffer.allocateDirect(sbnSize);
		sbnBuffer.put("STAR ROD".getBytes());

		sbnBuffer.position(0x10);
		sbnBuffer.putInt(0x40);
		sbnBuffer.putInt(fileList.size());
		sbnBuffer.putInt(sbnSize); // seems odd but okay
		sbnBuffer.putInt(sbnSize); // seems odd but okay

		sbnBuffer.putInt(0);
		sbnBuffer.putInt(sbnSize); // init file offset, we put it first

		return sbnBuffer;
	}

	private static int align16(int size)
	{
		return (size + 0xF) & -0x10;
	}

	private static byte[] inspectAudioFile(FileEntry af) throws IOException
	{
		byte[] fileBytes = FileUtils.readFileToByteArray(af.file);
		if (fileBytes.length < 8)
			throw new InputFileException(af.file, "Audio file is too short to contain a valid header");

		int declaredSize = ByteBuffer.wrap(fileBytes).getInt(4);
		if (declaredSize < 8 || declaredSize > fileBytes.length)
			throw new InputFileException(af.file, String.format("Invalid declared audio file size: %X", declaredSize));

		byte[] typeBytes = new byte[] { fileBytes[0], fileBytes[1], fileBytes[2], fileBytes[3] };
		af.type = new String(typeBytes).trim();
		String ext = FilenameUtils.getExtension(af.name);
		if (ext.isEmpty())
			ext = af.type;

		if (!af.type.equalsIgnoreCase(ext))
			throw new InputFileException(af.file, "Header of " + af.file + " does not match extension!");

		af.paddedSize = align16(fileBytes.length);
		switch (af.type) {
			case "BGM":
				af.fmt = 0x10;
				break;
			case "SEF":
				af.fmt = 0x20;
				break;
			case "BK":
				af.fmt = 0x30;
				break;
			case "PER":
			case "PRG":
			case "MSEQ":
				af.fmt = 0x40;
				break;
			default:
				throw new InputFileException(af.file, "Unsupported audio file type: " + af.type);
		}

		if (af.type.equals("BK")) {
			if (fileBytes.length < 0x40)
				throw new InputFileException(af.file, "Sound bank is too short to contain a valid header");
			ByteBuffer fileBB = IOUtils.getDirectBuffer(fileBytes);

			fileBB.position(0x32);
			int instrumentsSize = fileBB.getShort() & 0xFFFF;
			fileBB.getShort();
			int loopsSize = fileBB.getShort() & 0xFFFF;
			fileBB.getShort();
			int predictorsSize = fileBB.getShort() & 0xFFFF;
			fileBB.getShort();
			int envelopesSize = fileBB.getShort() & 0xFFFF;

			af.size = 0x40 + align16(instrumentsSize) + align16(loopsSize) + align16(predictorsSize) + align16(envelopesSize);
			if (af.size > fileBytes.length)
				throw new InputFileException(af.file, "Sound bank header sections exceed the file size");
		}
		else {
			af.size = af.paddedSize;
		}

		if (af.size > 0xFFFFFF)
			throw new InputFileException(af.file, "Audio file is too large for the SBN file table");

		return fileBytes;
	}

	public static int getMinimumAudioHeapSize() throws IOException
	{
		HashMap<String, Integer> sbnFileIndices = new HashMap<>(250);
		List<FileEntry> fileList = buildFileList(sbnFileIndices);
		List<SongEntry> songList = readSongListXML(MOD_AUDIO.getFile(FN_AUDIO_SONGS), sbnFileIndices);
		List<BankEntry> bankList = readBankListXML(MOD_AUDIO.getFile(FN_AUDIO_BANKS), sbnFileIndices);
		AudioHeapUsage modUsage = new AudioHeapUsage();

		modUsage.fileList = align16(fileList.size() * 8);
		modUsage.songList = align16((songList.size() + 1) * 8);

		File ambientSounds = MOD_AUDIO.getFile(FN_AUDIO_AMBIENTS);
		int[] resourceFileIndices = AudioCatalog.buildInitResourceList(ambientSounds, sbnFileIndices);
		modUsage.resourceList = align16((resourceFileIndices.length + 1) * 2);

		for (BankEntry bank : bankList) {
			FileEntry af = fileList.get(bank.fileIndex);
			inspectAudioFile(af);
			if (!af.type.equals("BK"))
				throw new InputFileException(af.file, "Bank list entry is not a sound bank");
			modUsage.banks += af.size;
		}

		for (FileEntry af : fileList) {
			if (!af.name.equals(FN_SFX_BINARY))
				continue;

			inspectAudioFile(af);
			if (!af.type.equals("SEF"))
				throw new InputFileException(af.file, FN_SFX_BINARY + " is not a sound effect archive");
			if (af.paddedSize > 0xFFFF)
				throw new InputFileException(af.file, "DAT1 is too large for its allocation instruction");
			modUsage.sef = af.paddedSize;
			break;
		}

		if (modUsage.sef == 0)
			throw new InputFileException(MOD_AUDIO.toFile(), "Missing mandatory audio file: " + FN_SFX_BINARY);

		int requiredSize = AUDIO_HEAP_FIXED_ALLOC_SIZE + modUsage.getTotal();
		int minimumSize = (requiredSize + 0x7FF) & -0x800;
		Logger.logf("Audio heap requires %X bytes; minimum heap size is %X.", requiredSize, minimumSize);
		return minimumSize;
	}

	private static void writeAudioFiles(RomPatcher rp, List<FileEntry> fileList) throws IOException
	{
		int regionEnd = AUDIO_DATA_END;
		int nextOffset = rp.getCurrentOffset();
		int sefAllocSize = 0;

		for (FileEntry af : fileList) {
			byte[] fileBytes = inspectAudioFile(af);
			if (af.name.equals(FN_SFX_BINARY)) {
				if (af.paddedSize > 0xFFFF)
					throw new InputFileException(af.file, "DAT1 is too large for its allocation instruction");
				sefAllocSize = af.paddedSize;
			}

			if (nextOffset + af.paddedSize > regionEnd)
				rp.seek(af.name + " Data", rp.nextAlignedOffset());
			else
				nextOffset += af.paddedSize;

			af.romOffset = rp.getCurrentOffset();
			Logger.logf("Writing %s to %X", af.name, af.romOffset);

			rp.seek(FilenameUtils.getBaseName(af.name), af.romOffset);
			rp.write(fileBytes);
			rp.padOut(16);
		}

		if (sefAllocSize == 0)
			throw new InputFileException(MOD_AUDIO.toFile(), "Missing mandatory audio file: " + FN_SFX_BINARY);

		rp.seek("SEF Allocation Size", 0x2E3A0);
		rp.writeInt(0x34060000 | sefAllocSize); // ORI A2, R0, size
	}

	private static void writeINIT(RomPatcher rp, List<BankEntry> bankList, List<SongEntry> songList,
		int[] resourceFileIndices) throws IOException
	{
		int initStartPosition = rp.getCurrentOffset();
		rp.write("INIT".getBytes());
		rp.skip(0x10);
		rp.writeInt(0);
		rp.writeInt(0);
		rp.writeInt(0);

		int bankListPosition = rp.getCurrentOffset();
		for (BankEntry bank : bankList) {
			rp.writeShort(bank.fileIndex);
			rp.writeByte(bank.bankIndex);
			rp.writeByte(bank.bankGroup);
		}
		rp.writeShort(0xFFFF);
		rp.writeShort(0);
		rp.padOut(16);

		int songListPosition = rp.getCurrentOffset();
		for (SongEntry song : songList) {
			rp.writeShort(song.bgmFileIndex);
			rp.writeShort(song.bk1FileIndex);
			rp.writeShort(song.bk2FileIndex);
			rp.writeShort(song.bk3FileIndex);
		}
		rp.writeInt(0xFFFF0000);
		rp.writeInt(0);
		rp.padOut(16);

		int resourceListPosition = rp.getCurrentOffset();
		for (int fileIndex : resourceFileIndices)
			rp.writeShort(fileIndex);
		rp.writeShort(0xFFFF);
		rp.padOut(16);

		int initEndPosition = rp.getCurrentOffset();
		rp.padOut(16);
		int initEndPadded = rp.getCurrentOffset();

		rp.seek("INIT", initStartPosition + 4);

		rp.writeInt(initEndPosition - initStartPosition);

		int bankListSize = 4 * (bankList.size() + 1);
		int songListSize = 8 * (songList.size() + 1);
		int resourceListSize = 2 * (resourceFileIndices.length + 1);

		rp.writeShort(bankListPosition - initStartPosition);
		rp.writeShort(bankListSize);

		rp.writeShort(songListPosition - initStartPosition);
		rp.writeShort(songListSize);

		rp.writeShort(resourceListPosition - initStartPosition);
		rp.writeShort(resourceListSize);

		rp.seek("After INIT", initEndPadded);
	}

	public static List<BankEntry> getBankEntries() throws IOException
	{
		HashMap<String, Integer> sbnFileIndices = new HashMap<>(250);
		List<FileEntry> fileList;
		List<BankEntry> bankList;

		fileList = buildFileList(sbnFileIndices);
		bankList = readBankListXML(MOD_AUDIO.getFile(FN_AUDIO_BANKS), sbnFileIndices);

		return bankList;
	}

	public static void patchAudio(Patcher patcher, RomPatcher rp) throws IOException
	{
		HashMap<String, Integer> sbnFileIndices = new HashMap<>(250);
		List<FileEntry> fileList;
		List<SongEntry> songList;
		List<BankEntry> bankList;
		int[] resourceFileIndices;

		fileList = buildFileList(sbnFileIndices);
		songList = readSongListXML(MOD_AUDIO.getFile(FN_AUDIO_SONGS), sbnFileIndices);
		bankList = readBankListXML(MOD_AUDIO.getFile(FN_AUDIO_BANKS), sbnFileIndices);
		File ambientSounds = MOD_AUDIO.getFile(FN_AUDIO_AMBIENTS);
		resourceFileIndices = AudioCatalog.buildInitResourceList(ambientSounds, sbnFileIndices);

		ByteBuffer sbnBuffer = createBufferForSBN(fileList);

		rp.seek("INIT", SBN_BASE + sbnBuffer.capacity());

		// write INIT
		writeINIT(rp, bankList, songList, resourceFileIndices);

		// write other files
		writeAudioFiles(rp, fileList);

		sbnBuffer.position(0x40);
		for (FileEntry af : fileList) {
			sbnBuffer.putInt(af.romOffset - SBN_BASE);
			sbnBuffer.putInt((af.fmt << 24) | (af.size & 0x00FFFFFF));
		}

		// update SBN
		rp.seek("SBN", SBN_BASE);
		rp.write(sbnBuffer);

		// an instruction at 800544B8 in GetSBNEntry ANDs the offset with 00FFFFFF
		// this causes the music DMA call to go haywire and crash the game
		// alternatively, we could respect this and not place anything after 0x1EFFFFF
		// however, the upper byte is not actually used for anything, so we can simply
		// NOP out the masking
		rp.seek("SBN DMA Fix", 0x2F8B8);
		rp.writeInt(0); // NOP
	}

}
