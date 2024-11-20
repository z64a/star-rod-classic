package game.sound;

import static app.Directories.*;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import app.Environment;
import app.StarRodClassic;
import app.input.IOUtils;
import app.input.InputFileException;
import game.shared.ProjectDatabase;
import game.shared.ProjectDatabase.ConstEnum;
import patcher.Patcher;
import patcher.RomPatcher;
import util.Logger;

public class AudioEditor
{
	public static void main(String[] args) throws IOException
	{
		Environment.initialize();
		//	new AudioEditor();

		//	dumpAudio();

		HashMap<String, Integer> sbnLookup = new HashMap<>(250);
		List<AudioFile> fileList = new ArrayList<>(250);
		List<Song> songList = new ArrayList<>(100);

		readXMLFileList(new File(MOD_AUDIO + FN_AUDIO_FILES), sbnLookup, fileList);
		readXMLSongList(new File(MOD_AUDIO + FN_AUDIO_SONGS), sbnLookup, songList);

		int i = 0;
		for (Song s : songList) {
			ByteBuffer bb = IOUtils.getDirectBuffer(new File(MOD_AUDIO + s.bgmName));
			bb.position(0x14);
			int count = 0;
			if (bb.getShort() != 0)
				count++;
			if (bb.getShort() != 0)
				count++;
			if (bb.getShort() != 0)
				count++;
			if (bb.getShort() != 0)
				count++;

			System.out.printf("%02X\t%d\t%s%n", i++, count, s.bgmName);
		}

		Environment.exit();
	}

	private static class Song
	{
		String bgmName;
		String x;
		String y;
		String z;

		int bgmIndex;
		int xIndex;
		int yIndex;
		int zIndex;
	}

	private static class AudioFile
	{
		File file;
		int fmt;
		int romOffset;
		int size;
	}

	private static final int TABLE_BASE = 0xF00000;
	private static final int AUDIO_DATA_END = 0x1942C40;

	// keep the SBN table in place, move other files wherever they can fit
	// users supply their own files and must update the INIT file
	// we need one source files:

	// Song_List.xml
	//	<SongList>
	//		<Song BGM="xxx.BGM" X="" (optional) Y="" (optional) Z="" (optional)>
	//	</SongList>

	// check file names for uniqueness

	private static final String FILE_LIST_TAG = "FileList";
	private static final String FILE_TAG = "File";
	private static final String FILE_NAME_ATTR = "name";

	private static final String SONG_LIST_TAG = "SongList";
	private static final String SONG_TAG = "Song";
	private static final String SONG_ID_ATTR = "id";
	private static final String SONG_BGM_ATTR = "BGM";

	private static int dumpSBN(RandomAccessFile raf, List<String> dumpedFilenames) throws IOException
	{
		File xmlFile = new File(DUMP_AUDIO + FN_AUDIO_FILES);
		PrintWriter pw = IOUtils.getBufferedPrintWriter(xmlFile);
		pw.println("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>");
		pw.println("<!-- Add raw audio files here -->");
		pw.println("<" + FILE_LIST_TAG + ">");

		raf.seek(TABLE_BASE);
		raf.skipBytes(16);

		int tableStart = raf.readInt();
		int numEntries = raf.readInt();
		raf.skipBytes(12);
		int initOffset = raf.readInt();

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

			String dir;
			switch (ext) {
				case "bgm":
					dir = "bgm";
					break;
				case "bk":
					dir = "bk";
					break;
				case "mseq":
					dir = "mseq";
					break;
				default:
					dir = "misc";
			}

			String fileName = String.format("%02X_%s.%s", i, name.trim(), ext);
			String fullName = dir + "/" + fileName;
			dumpedFilenames.add(fullName);

			byte[] fileBytes = new byte[len + 0xF & 0xFFFFFFF0];

			raf.seek(TABLE_BASE + offset);
			raf.read(fileBytes);
			File out = new File(DUMP_AUDIO + fullName);
			FileUtils.writeByteArrayToFile(out, fileBytes);

			pw.printf("\t<%s %s=\"%s\"/>\n", FILE_TAG, FILE_NAME_ATTR, fullName);

			Logger.logf("Dumped %s from %X", fileName, TABLE_BASE + offset);
		}

		pw.println("</" + FILE_LIST_TAG + ">");
		pw.close();

		return initOffset;
	}

	private static void dumpINIT(RandomAccessFile raf, int initOffset, List<String> dumpedFilenames) throws IOException
	{
		ConstEnum songEnum = ProjectDatabase.getFromLibraryName("songID");

		/*
		raf.seek(TABLE_BASE + initOffset + 4);
		int initLen = raf.readInt();

		byte[] initBytes = new byte[initLen + 0xF & 0xFFFFFFF0];
		raf.seek(TABLE_BASE + initOffset);
		raf.read(initBytes);
		File out = new File(Directories.DUMP_AUDIO + "INIT");
		FileUtils.writeByteArrayToFile(out, initBytes);
		 */

		// open XML file
		File xmlFile = new File(DUMP_AUDIO + FN_AUDIO_SONGS);
		PrintWriter pw = IOUtils.getBufferedPrintWriter(xmlFile);
		pw.println("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>");
		pw.println("<!-- Add raw audio files here -->");
		pw.println("<" + SONG_LIST_TAG + ">");

		System.out.printf("INIT: %X%n", TABLE_BASE + initOffset);
		int i;

		raf.seek(TABLE_BASE + initOffset + 0x20);
		i = 0;
		System.out.println("Banks:");
		while (true) {
			int sbnID = raf.readShort();
			int bankIndex = raf.readByte();
			int bankGroup = raf.readByte();

			if (sbnID == -1)
				break;

			System.out.printf("%02X %02X %X %s%n", bankGroup, bankIndex, sbnID, dumpedFilenames.get(sbnID));
		}

		raf.seek(TABLE_BASE + initOffset + 0x130);
		i = 0;
		while (true) {
			int sbnID = raf.readShort();
			int bk1 = raf.readShort();
			int bk2 = raf.readShort();
			int bk3 = raf.readShort();

			if (sbnID == -1)
				break;

			StringBuilder sb = new StringBuilder();
			sb.append(String.format("\t<%s %s=\"%02X\" %s=\"%s\"", SONG_TAG,
				SONG_ID_ATTR, i,
				SONG_BGM_ATTR, dumpedFilenames.get(sbnID)));

			if (bk1 != 0)
				sb.append(String.format(" x=\"%s\"", dumpedFilenames.get(bk1)));
			if (bk2 != 0)
				sb.append(String.format(" y=\"%s\"", dumpedFilenames.get(bk2)));
			if (bk3 != 0)
				sb.append(String.format(" z=\"%s\"", dumpedFilenames.get(bk3)));

			sb.append("/>");

			if (songEnum.has(i))
				sb.append(String.format(" <!-- %s -->", songEnum.getName(i)));

			pw.println(sb.toString());
			i++;
		}
		pw.println("</" + SONG_LIST_TAG + ">");
		pw.close();
	}

	public static void dumpAudio() throws IOException
	{
		RandomAccessFile raf = Environment.getBaseRomReader();

		List<String> dumpedFilenames = new ArrayList<>(250);

		int initOffset = dumpSBN(raf, dumpedFilenames);
		dumpINIT(raf, initOffset, dumpedFilenames);

		raf.close();
	}

	private static void readXMLFileList(File xmlFile, HashMap<String, Integer> sbnLookup, List<AudioFile> fileList) throws IOException
	{
		try {
			DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
			DocumentBuilder builder = factory.newDocumentBuilder();
			Document document = builder.parse(xmlFile);
			document.getDocumentElement().normalize();

			NodeList fileListElements = document.getElementsByTagName(FILE_LIST_TAG);
			if (fileListElements.getLength() < 1)
				throw new InputFileException(xmlFile, "Could not find <" + FILE_LIST_TAG + ">");
			if (fileListElements.getLength() > 1)
				throw new InputFileException(xmlFile, "Found multiple <" + FILE_LIST_TAG + ">");
			Element fileListElement = (Element) fileListElements.item(0);

			NodeList files = fileListElement.getElementsByTagName(FILE_TAG);
			if (files.getLength() < 1)
				throw new InputFileException(xmlFile, FILE_LIST_TAG + " is empty!");

			for (int i = 0; i < files.getLength(); i++) {
				Element fileElement = (Element) files.item(i);

				if (!fileElement.hasAttribute(FILE_NAME_ATTR))
					throw new InputFileException(xmlFile, FILE_TAG + " is missing required attribute: " + FILE_NAME_ATTR);

				String fileName = fileElement.getAttribute(FILE_NAME_ATTR);
				AudioFile af = new AudioFile();
				af.file = new File(MOD_AUDIO + fileName);

				if (!af.file.exists())
					throw new InputFileException(xmlFile, "File does not exist: " + fileName);

				if (sbnLookup.containsKey(fileName))
					throw new InputFileException(xmlFile, "File is listed multiple times: " + fileName);

				fileList.add(af);
				sbnLookup.put(fileName, i);
			}
		}
		catch (ParserConfigurationException e) {
			e.printStackTrace();
			StarRodClassic.displayStackTrace(e);
		}
		catch (SAXException e) {
			e.printStackTrace();
			StarRodClassic.displayStackTrace(e);
		}
	}

	private static void readXMLSongList(File xmlFile, HashMap<String, Integer> sbnLookup, List<Song> songList) throws IOException
	{
		try {
			DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
			DocumentBuilder builder = factory.newDocumentBuilder();
			Document document = builder.parse(xmlFile);
			document.getDocumentElement().normalize();

			NodeList songListElements = document.getElementsByTagName(SONG_LIST_TAG);
			if (songListElements.getLength() < 1)
				throw new InputFileException(xmlFile, "Could not find <" + SONG_LIST_TAG + ">");
			if (songListElements.getLength() > 1)
				throw new InputFileException(xmlFile, "Found multiple <" + SONG_LIST_TAG + ">");
			Element songListElement = (Element) songListElements.item(0);

			NodeList songElements = songListElement.getElementsByTagName(SONG_TAG);
			if (songElements.getLength() < 1)
				throw new InputFileException(xmlFile, SONG_LIST_TAG + " is empty!");

			int numSongs = songElements.getLength();

			for (int i = 0; i < numSongs; i++) {
				Element songElement = (Element) songElements.item(i);

				if (!songElement.hasAttribute(SONG_ID_ATTR))
					throw new InputFileException(xmlFile, SONG_TAG + " is missing required attribute: " + SONG_ID_ATTR);

				int id = Integer.parseInt(songElement.getAttribute(SONG_ID_ATTR), 16);

				//XXX temporary restriction
				if (id != i)
					throw new InputFileException(xmlFile, SONG_TAG + " ID is out of order! Do not skip song IDs.");

				if (!songElement.hasAttribute(SONG_BGM_ATTR))
					throw new InputFileException(xmlFile, SONG_TAG + " is missing required attribute: " + SONG_BGM_ATTR);

				Song s = new Song();
				s.bgmName = songElement.getAttribute(SONG_BGM_ATTR);
				s.x = songElement.getAttribute("x");
				s.y = songElement.getAttribute("y");
				s.z = songElement.getAttribute("z");
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
		}
		catch (ParserConfigurationException e) {
			e.printStackTrace();
			StarRodClassic.displayStackTrace(e);
		}
		catch (SAXException e) {
			e.printStackTrace();
			StarRodClassic.displayStackTrace(e);
		}
	}

	private static ByteBuffer createBufferForSBN(List<AudioFile> fileList) throws IOException
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

	private static void writeAudioFiles(RomPatcher rp, List<AudioFile> fileList) throws IOException
	{
		int tableEnd = AUDIO_DATA_END;
		int nextOffset = rp.getCurrentOffset();

		for (AudioFile af : fileList) {
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

	private static void writeINIT(RomPatcher rp, List<Song> songList) throws IOException
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
		for (Song song : songList) {
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

	public static void patchAudio(Patcher patcher, RomPatcher rp) throws IOException
	{
		HashMap<String, Integer> sbnLookup = new HashMap<>(250);
		List<AudioFile> fileList = new ArrayList<>(250);
		List<Song> songList = new ArrayList<>(100);

		readXMLFileList(new File(MOD_AUDIO + FN_AUDIO_FILES), sbnLookup, fileList);
		readXMLSongList(new File(MOD_AUDIO + FN_AUDIO_SONGS), sbnLookup, songList);

		ByteBuffer sbnBuffer = createBufferForSBN(fileList);

		rp.seek("INIT", TABLE_BASE + sbnBuffer.capacity());

		// write INIT
		writeINIT(rp, songList);

		// write other files
		writeAudioFiles(rp, fileList);

		sbnBuffer.position(0x40);
		for (AudioFile af : fileList) {
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

	private AudioEditor() throws IOException
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
