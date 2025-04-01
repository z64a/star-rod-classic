package game.sound;

import static app.Directories.*;
import static game.sound.AudioModder.BankListKey.*;
import static game.sound.AudioModder.FileListKey.*;
import static game.sound.AudioModder.SongListKey.*;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.w3c.dom.Element;

import app.Environment;
import app.input.IOUtils;
import app.input.InputFileException;
import game.shared.ProjectDatabase;
import game.shared.ProjectDatabase.ConstEnum;
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

	private static final int TABLE_BASE = 0xF00000;
	private static final int AUDIO_DATA_END = 0x1942C40;

	private static class FileEntry
	{
		private File file;
		private int fmt;
		private int romOffset;
		private int size;
	}

	public enum FileListKey implements XmlKey
	{
		// @formatter:off
		TAG_FILE_LIST	("FileList"),
		TAG_FILE		("File"),
		ATTR_NAME		("name");
		// @formatter:on

		private final String key;

		private FileListKey(String key)
		{
			this.key = key;
		}

		@Override
		public String toString()
		{
			return key;
		}
	}

	private static class SongEntry
	{
		private String bgmName;
		private String x = "";
		private String y = "";
		private String z = "";

		private int bgmIndex;
		private int xIndex;
		private int yIndex;
		private int zIndex;
	}

	public enum SongListKey implements XmlKey
	{
		// @formatter:off
		TAG_SONG_LIST	("SongList"),
		TAG_SONG		("Song"),
		ATTR_ID			("id"),
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

		public final int group;
		public final int index;

		public BankEntry(String name, int group, int index)
		{
			this.name = name;
			this.group = group;
			this.index = index;
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

	// keep the SBN table in place, move other files wherever they can fit
	// users supply their own files and must update the INIT file
	// we need one source file:

	// Song_List.xml
	//	<SongList>
	//		<Song BGM="xxx.BGM" X="" (optional) Y="" (optional) Z="" (optional)>
	//	</SongList>

	// check file names for uniqueness

	private static int dumpSBN(RandomAccessFile raf, List<String> dumpedFilenames) throws IOException
	{
		File xmlFile = new File(DUMP_AUDIO + FN_AUDIO_FILES);
		int initOffset = -1;

		try (XmlWriter xmw = new XmlWriter(xmlFile)) {
			XmlTag listTag = xmw.createTag(TAG_FILE_LIST, false);
			xmw.printComment("Add raw audio files here");
			xmw.openTag(listTag);

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

			raf.seek(TABLE_BASE);
			raf.skipBytes(16);

			int tableStart = raf.readInt();
			int numEntries = raf.readInt();
			raf.skipBytes(12);
			initOffset = raf.readInt();

			for (int i = 0; i < numEntries; i++) {
				raf.seek(TABLE_BASE + tableStart + 8 * i);
				int offset = raf.readInt();
				//	int word2 = raf.readInt();
				//	int fmt = word2 >>> 24;
				//	int lenX = word2 & 0x00FFFFFF; // bytes from offset (includes header)

				raf.seek(TABLE_BASE + offset);
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

				raf.seek(TABLE_BASE + offset);
				raf.read(fileBytes);
				File out = new File(DUMP_AUDIO_RAW + fileName);
				FileUtils.writeByteArrayToFile(out, fileBytes);

				XmlTag fileTag = xmw.createTag(TAG_FILE, true);
				xmw.addAttribute(fileTag, ATTR_NAME, fileName);
				xmw.printTag(fileTag);

				Logger.logf("Dumped %s from %X", fileName, TABLE_BASE + offset);
			}

			xmw.closeTag(listTag);
			xmw.save();
		}

		return initOffset;
	}

	private static void dumpINIT(RandomAccessFile raf, int initOffset, List<String> dumpedFilenames) throws IOException
	{
		ConstEnum songEnum = ProjectDatabase.getFromLibraryName("songID");

		try (XmlWriter xmw = new XmlWriter(DUMP_AUDIO.getFile(FN_AUDIO_BANKS))) {
			XmlTag listTag = xmw.createTag(TAG_BANK_LIST, false);
			xmw.openTag(listTag);

			raf.seek(TABLE_BASE + initOffset + 0x20);
			while (true) {
				int sbnID = raf.readShort();
				int bankIndex = raf.readByte();
				int bankGroup = raf.readByte();

				if (sbnID == -1)
					break;

				XmlTag bankTag = xmw.createTag(TAG_BANK, true);
				xmw.addAttribute(bankTag, ATTR_BANK_NAME, dumpedFilenames.get(sbnID));
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

			raf.seek(TABLE_BASE + initOffset + 0x130);
			int songID = 0;
			while (true) {
				int sbnID = raf.readShort();
				int bk1 = raf.readShort();
				int bk2 = raf.readShort();
				int bk3 = raf.readShort();

				if (sbnID == -1)
					break;

				XmlTag songTag = xmw.createTag(TAG_SONG, true);

				xmw.addHex(songTag, ATTR_ID, songID);
				xmw.addAttribute(songTag, ATTR_BGM, dumpedFilenames.get(sbnID));

				if (bk1 != 0)
					xmw.addAttribute(songTag, ATTR_BK1, dumpedFilenames.get(bk1));
				if (bk2 != 0)
					xmw.addAttribute(songTag, ATTR_BK2, dumpedFilenames.get(bk2));
				if (bk3 != 0)
					xmw.addAttribute(songTag, ATTR_BK3, dumpedFilenames.get(bk3));

				if (songEnum.has(songID))
					xmw.printTag(songTag, songEnum.getName(songID));
				else
					xmw.printTag(songTag);

				songID++;
			}

			xmw.closeTag(listTag);
			xmw.save();
		}
	}

	public static void dumpAudio() throws IOException
	{
		RandomAccessFile raf = Environment.getBaseRomReader();

		List<String> dumpedFilenames = new ArrayList<>(250);

		int initOffset = dumpSBN(raf, dumpedFilenames);
		dumpINIT(raf, initOffset, dumpedFilenames);

		BankModder.dumpAll();
		DrumModder.dump();

		raf.close();
	}

	private static List<FileEntry> readFileListXML(File xmlFile, HashMap<String, Integer> sbnLookup) throws IOException
	{
		List<FileEntry> fileList = new ArrayList<>(256);

		XmlReader xmr = new XmlReader(xmlFile);

		Element root = xmr.getRootElement();
		List<Element> fileElems = xmr.getTags(root, TAG_FILE);

		for (int index = 0; index < fileElems.size(); index++) {
			Element fileElem = fileElems.get(index);

			xmr.requiresAttribute(fileElem, ATTR_NAME);
			String fileName = xmr.getAttribute(fileElem, ATTR_NAME);

			FileEntry af = new FileEntry();
			af.file = MOD_AUDIO_BUILD.getFile(fileName);

			if (!af.file.exists())
				af.file = MOD_AUDIO_RAW.getFile(fileName);

			if (!af.file.exists())
				throw new InputFileException(xmlFile, "Missing audio file: " + fileName);

			if (sbnLookup.containsKey(fileName))
				throw new InputFileException(xmlFile, "File is listed multiple times: " + fileName);

			fileList.add(af);
			sbnLookup.put(fileName, index);
		}

		return fileList;
	}

	private static List<SongEntry> readSongListXML(File xmlFile, HashMap<String, Integer> sbnLookup) throws IOException
	{
		List<SongEntry> songList = new ArrayList<>(256);

		XmlReader xmr = new XmlReader(xmlFile);

		Element root = xmr.getRootElement();
		List<Element> songElems = xmr.getTags(root, TAG_SONG);

		for (int index = 0; index < songElems.size(); index++) {
			Element songElem = songElems.get(index);

			xmr.requiresAttribute(songElem, ATTR_ID);
			int id = xmr.readHex(songElem, ATTR_ID);

			if (id != index)
				throw new InputFileException(xmlFile, TAG_SONG + " ID is out of order! Do not skip song IDs.");

			SongEntry s = new SongEntry();
			if (xmr.hasAttribute(songElem, ATTR_OLD_BGM)) {
				s.bgmName = xmr.getAttribute(songElem, ATTR_OLD_BGM);

				if (xmr.hasAttribute(songElem, ATTR_X))
					s.x = xmr.getAttribute(songElem, ATTR_X);

				if (xmr.hasAttribute(songElem, ATTR_Y))
					s.y = xmr.getAttribute(songElem, ATTR_Y);

				if (xmr.hasAttribute(songElem, ATTR_Z))
					s.z = xmr.getAttribute(songElem, ATTR_Z);
			}
			else {
				xmr.requiresAttribute(songElem, ATTR_BGM);
				s.bgmName = xmr.getAttribute(songElem, ATTR_BGM);

				if (xmr.hasAttribute(songElem, ATTR_BK1))
					s.x = xmr.getAttribute(songElem, ATTR_BK1);

				if (xmr.hasAttribute(songElem, ATTR_BK2))
					s.y = xmr.getAttribute(songElem, ATTR_BK2);

				if (xmr.hasAttribute(songElem, ATTR_BK3))
					s.z = xmr.getAttribute(songElem, ATTR_BK3);
			}

			songList.add(s);

			if (!sbnLookup.containsKey(s.bgmName))
				throw new InputFileException(xmlFile, "Song references unregistered file: " + s.bgmName);
			s.bgmIndex = sbnLookup.get(s.bgmName);

			if (!s.x.isEmpty()) {
				if (!sbnLookup.containsKey(s.x))
					throw new InputFileException(xmlFile, "Song references unregistered file: " + s.x);
				s.xIndex = sbnLookup.get(s.x);
			}

			if (!s.y.isEmpty()) {
				if (!sbnLookup.containsKey(s.y))
					throw new InputFileException(xmlFile, "Song references unregistered file: " + s.y);
				s.yIndex = sbnLookup.get(s.y);
			}

			if (!s.z.isEmpty()) {
				if (!sbnLookup.containsKey(s.z))
					throw new InputFileException(xmlFile, "Song references unregistered file: " + s.z);
				s.zIndex = sbnLookup.get(s.z);
			}
		}

		return songList;
	}

	private static List<BankEntry> readBankListXML(File xmlFile, HashMap<String, Integer> sbnLookup) throws IOException
	{
		List<BankEntry> bankList = new ArrayList<>(256);

		XmlReader xmr = new XmlReader(xmlFile);

		Element root = xmr.getRootElement();
		List<Element> bankElems = xmr.getTags(root, TAG_BANK);

		for (int i = 0; i < bankElems.size(); i++) {
			Element bankElem = bankElems.get(i);

			xmr.requiresAttribute(bankElem, ATTR_BANK_NAME);
			xmr.requiresAttribute(bankElem, ATTR_BANK_GROUP);
			xmr.requiresAttribute(bankElem, ATTR_BANK_INDEX);

			String name = xmr.getAttribute(bankElem, ATTR_BANK_NAME);
			int group = xmr.readHex(bankElem, ATTR_BANK_GROUP);
			int index = xmr.readHex(bankElem, ATTR_BANK_INDEX);

			BankEntry bank = new BankEntry(name, group, index);
			bankList.add(bank);

			if (!sbnLookup.containsKey(bank.name))
				throw new InputFileException(xmlFile, "Bank not found in file list: " + bank.name);
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

		/*
		rp.write("STAR ROD".getBytes());
		rp.writeInt(0);
		rp.writeInt(0);

		int sbnSize = 8 * fileList.size();
		sbnSize = (sbnSize + 0xF) & 0xFFFFFFF0;
		sbnSize += 0x40;

		rp.writeInt(0x40);
		rp.writeInt(fileList.size());
		rp.writeInt(sbnSize);	// seems odd but okay
		rp.writeInt(sbnSize);	// seems odd but okay

		rp.writeInt(0);
		rp.writeInt(sbnSize);	// init file offset, we put it first
		rp.writeInt(0);
		rp.writeInt(0);

		rp.writeInt(0);
		rp.writeInt(0);
		rp.writeInt(0);
		rp.writeInt(0);

		// write placeholder SBN table
		for(int i = 0; i < fileList.size(); i++)
		{
			rp.writeInt(0);
			rp.writeInt(0);
		}

		rp.padOut(16);
		*/

		return sbnBuffer;
	}

	private static void writeAudioFiles(RomPatcher rp, List<FileEntry> fileList) throws IOException
	{
		int tableEnd = AUDIO_DATA_END;
		int nextOffset = rp.getCurrentOffset();

		for (FileEntry af : fileList) {
			String ext = FilenameUtils.getExtension(af.file.getName());

			byte[] fileBytes = FileUtils.readFileToByteArray(af.file);
			byte[] typeBytes = new byte[] { fileBytes[0], fileBytes[1], fileBytes[2], fileBytes[3] };

			String type = new String(typeBytes).trim();

			if (ext.isEmpty())
				ext = type;

			if (!type.equalsIgnoreCase(ext))
				throw new InputFileException(af.file, "Header of " + af.file + " does not match extension!");

			int paddedSize = (fileBytes.length + 0xF) & 0xFFFFFFF0;

			if (nextOffset + paddedSize > tableEnd)
				rp.seek(af.file.getName() + " Data", rp.nextAlignedOffset());
			else
				nextOffset += paddedSize;

			af.romOffset = rp.getCurrentOffset();

			switch (type) {
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
					af.fmt = 0x40;
					break;
				case "PRG":
					af.fmt = 0x40;
					break;
				case "MSEQ":
					af.fmt = 0x40;
					break;
			}

			Logger.logf("Writing %s to %X", af.file.getName(), af.romOffset);

			rp.seek(FilenameUtils.getBaseName(af.file.getName()), af.romOffset);
			rp.write(fileBytes);
			rp.padOut(16);

			if (type.equals("BK")) {
				ByteBuffer fileBB = IOUtils.getDirectBuffer(fileBytes);

				fileBB.position(0x32);
				short len1 = fileBB.getShort();
				fileBB.getShort();
				short len2 = fileBB.getShort();
				fileBB.getShort();
				short len3 = fileBB.getShort();
				fileBB.getShort();
				short len4 = fileBB.getShort();

				af.size = ((len1 + len2 + len3 + len4 + 0x40) + 0xF) & 0xFFFFFFF0;
			}
			else {
				af.size = rp.getCurrentOffset() - af.romOffset;
			}
		}
	}

	private static void writeINIT(RomPatcher rp, List<SongEntry> songList) throws IOException
	{
		byte[] bankListBytes = new byte[0x110];
		byte[] list3Bytes = new byte[0x40];

		//XXX take parts from the original INIT
		RandomAccessFile raf_original = Environment.getBaseRomReader();
		raf_original.seek(0x19425E0);
		raf_original.read(bankListBytes);
		raf_original.seek(0x1942C00);
		raf_original.read(list3Bytes);
		raf_original.close();
		int numBanks = 64;
		int numList3 = 24;

		int initStartPosition = rp.getCurrentOffset();
		rp.write("INIT".getBytes());
		rp.skip(0x10);
		rp.writeInt(0);
		rp.writeInt(0);
		rp.writeInt(0);

		int bankListPosition = rp.getCurrentOffset();
		rp.write(bankListBytes); // XXX

		int songListPosition = rp.getCurrentOffset();
		for (SongEntry song : songList) {
			rp.writeShort(song.bgmIndex);
			rp.writeShort(song.xIndex);
			rp.writeShort(song.yIndex);
			rp.writeShort(song.zIndex);
		}
		rp.writeInt(0xFFFF0000);
		rp.padOut(16);

		int list3Position = rp.getCurrentOffset();
		rp.write(list3Bytes); //XXX

		int initEndPosition = rp.getCurrentOffset();
		rp.padOut(16);
		int initEndPadded = rp.getCurrentOffset();

		rp.seek("INIT", initStartPosition + 4);

		rp.writeInt(initEndPosition - initStartPosition);

		int bankListSize = 4 * (numBanks + 1);
		int songListSize = 8 * (songList.size() + 1);
		int list3Size = 2 * (numList3 + 1);

		rp.writeShort(bankListPosition - initStartPosition);
		rp.writeShort(bankListSize);

		rp.writeShort(songListPosition - initStartPosition);
		rp.writeShort(songListSize);

		rp.writeShort(list3Position - initStartPosition);
		rp.writeShort(list3Size);

		rp.seek("After INIT", initEndPadded);
	}

	public static List<BankEntry> getBankEntries() throws IOException
	{
		HashMap<String, Integer> sbnLookup = new HashMap<>(250);
		List<FileEntry> fileList;
		List<BankEntry> bankList;

		fileList = readFileListXML(MOD_AUDIO.getFile(FN_AUDIO_FILES), sbnLookup);
		bankList = readBankListXML(MOD_AUDIO.getFile(FN_AUDIO_BANKS), sbnLookup);

		return bankList;
	}

	public static void patchAudio(Patcher patcher, RomPatcher rp) throws IOException
	{
		HashMap<String, Integer> sbnLookup = new HashMap<>(250);
		List<FileEntry> fileList;
		List<SongEntry> songList;
		List<BankEntry> bankList;

		fileList = readFileListXML(MOD_AUDIO.getFile(FN_AUDIO_FILES), sbnLookup);
		songList = readSongListXML(MOD_AUDIO.getFile(FN_AUDIO_SONGS), sbnLookup);
		bankList = readBankListXML(MOD_AUDIO.getFile(FN_AUDIO_BANKS), sbnLookup);

		ByteBuffer sbnBuffer = createBufferForSBN(fileList);

		rp.seek("INIT", TABLE_BASE + sbnBuffer.capacity());

		// write INIT
		writeINIT(rp, songList);

		// write other files
		writeAudioFiles(rp, fileList);

		sbnBuffer.position(0x40);
		for (FileEntry af : fileList) {
			sbnBuffer.putInt(af.romOffset - TABLE_BASE);
			sbnBuffer.putInt((af.fmt << 24) | (af.size & 0x00FFFFFF));
		}

		// update SBN
		rp.seek("SBN", TABLE_BASE);
		rp.write(sbnBuffer);

		// an instruction at 800544B8 in GetSBNEntry ANDs the offset with 00FFFFFF
		// this causes the music DMA call to go haywire and crash the game
		// alternatively, we could respect this and not place anything after 0x1EFFFFF
		// however, the upper byte is not actually used for anything, so we can simply
		// NOP out the masking
		rp.seek("SBN DMA Fix", 0x2F8B8);
		rp.writeInt(0); // NOP
	}

	// sound data 0x0F00000-0x1942C40

	/*
		 SBN Header (0x40 bytes at 0xF00000)
		 00		'SBN '
		 04		00A42C40	(offset) end of data
		 10		00000040	(offset) table start
		 14		000000EC	number of entries
		 18		000007C0	(offset) first file
		 1C		000007A0	(offset) timestamp string '20001104163016'
		 24		00A425C0	(offset) INIT file
	 */

	/*
		 INIT Header (0x20 bytes at 0x19425C0)
		 00		'INIT'
		 04		00000672	file length
		 08		0020/0104	list 1 offset/length
		 0C		0130/0508	list 2 offset/length -- A0 entries...
		 10		0640/0032	list 3 offset/length
	 */

	/*
		 BGM Header (0x20 bytes)
		 00		'BGM '
		 04		file length
		 08		ASCII name
	 */

	private AudioModder() throws IOException
	{
		RandomAccessFile raf = Environment.getBaseRomReader();

		raf.seek(TABLE_BASE);
		System.out.println(IOUtils.readString(raf, 4));
		System.out.printf("End: %X%n", TABLE_BASE + raf.readInt());
		raf.skipBytes(8);

		int tableStart = raf.readInt();
		int numEntries = raf.readInt();
		raf.skipBytes(12);
		int initOffset = raf.readInt();

		String[] types = new String[numEntries];
		String[] names = new String[numEntries];

		for (int i = 0; i < numEntries; i++) {
			raf.seek(TABLE_BASE + tableStart + 8 * i);
			int offset = raf.readInt();
			int word2 = raf.readInt();
			int fmt = word2 >>> 24;
			int lenSBN = word2 & 0x00FFFFFF; // bytes from offset (includes header)

			raf.seek(TABLE_BASE + offset);
			String type = IOUtils.readString(raf, 4);
			int len = raf.readInt();
			String name = IOUtils.readString(raf, 4);

			System.out.printf("(%2X) %7X %5X %-3s", i, TABLE_BASE + offset, len, type.trim());

			types[i] = type;
			names[i] = name;

			if (!type.equals("BK  "))
				assert (len == lenSBN);
			System.out.print(" -- '" + name + "'");

			switch (type) {
				case "BGM ": // 00 - 8E
					assert (fmt == 0x10);
					break;

				case "SEF ": // 8F
					assert (fmt == 0x20);
					break;

				case "BK  ": // 90 - D8
					assert (fmt == 0x30);

					raf.seek(TABLE_BASE + offset + 0x32);
					short len1 = raf.readShort();
					raf.skipBytes(2);
					short len2 = raf.readShort();
					raf.skipBytes(2);
					short len3 = raf.readShort();
					raf.skipBytes(2);
					short len4 = raf.readShort();

					int sumLen = ((len1 + len2 + len3 + len4 + 0x40) + 0xF) & 0xFFFFFFF0;

					assert (sumLen == lenSBN);
					assert (len1 % 0x30 == 0);

					break;

				case "PER ": // D9
					assert (fmt == 0x40);
					break;

				case "PRG ": // DA
					assert (fmt == 0x40);
					break;

				case "MSEQ": // DB - EB
					assert (fmt == 0x40);
					break;

				default:
					throw new RuntimeException("Unknown file type " + type);
			}

			System.out.println();
		}

		int initBase = TABLE_BASE + initOffset;
		raf.seek(initBase);
		String type = IOUtils.readString(raf, 4);

		ConstEnum songEnum = ProjectDatabase.getFromLibraryName("songID");
		System.out.printf("     %7X %s", initBase, type.trim());
		System.out.println();

		System.out.println("");
		System.out.println("Reading INIT file:");

		// list 1
		System.out.println("----- List 1 ----- ");
		for (int i = 0; i < 64; i++) {
			raf.seek(initBase + 0x20 + 4 * i);
			int bank = raf.readShort();
			int a = raf.readByte();
			int b = raf.readByte();

			assert (bank >= 0x90 && bank <= 0xD8); // bank is always one of the banks
			System.out.printf("[%02X] %2X (%4s) %2X %2X%n", i, bank, names[bank], a, b);
		}

		// list 2
		System.out.println("----- Song List ----- ");
		for (int i = 0; i < 0xA0; i++) {
			raf.seek(initBase + 0x130 + 8 * i);
			int bgm = raf.readShort();
			int bank1 = raf.readShort();
			int bank2 = raf.readShort();
			int bank3 = raf.readShort();
			System.out.printf("%07X [%02X] %2X %2X %2X %2X  %s%n", initBase + 0x130 + 8 * i, i, bgm, bank1, bank2, bank3, songEnum.getName(i));
			assert (bgm >= 0x00 && bgm <= 0x8E);
			assert (bank1 == 0 || (bank1 >= 0x90 && bank1 <= 0xD8));
			assert (bank2 == 0 || (bank2 >= 0x90 && bank2 <= 0xD8));
			assert (bank3 == 0 || (bank3 >= 0x90 && bank3 <= 0xD8));
		}

		// list 3
		System.out.println("----- List 3 ----- ");
		for (int i = 0; i < 24; i++) {
			raf.seek(initBase + 0x640 + 2 * i);
			int sbnEntry = raf.readShort();
			System.out.printf("[%02X] %2X  %s (%s)%n", i, sbnEntry, types[sbnEntry], names[sbnEntry]);
		}

		raf.close();
	}
}
