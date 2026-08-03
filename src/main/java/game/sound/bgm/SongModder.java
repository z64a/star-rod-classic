package game.sound.bgm;

import static app.Directories.*;
import static game.sound.AudioModder.SongListKey.*;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.w3c.dom.Element;

import app.Directories;
import app.Environment;
import app.input.IOUtils;
import game.sound.AudioModder;
import game.sound.SoundBankCatalog;
import game.sound.SoundXml;
import util.Logger;
import util.xml.XmlWrapper.XmlReader;
import util.xml.XmlWrapper.XmlWriter;

public abstract class SongModder
{
	private static final int TOAD_TOWN_SONG_ID = 0;
	private static final List<String> TOAD_TOWN_BRANCH_NAMES = List.of(
		"Default", "Kitchen", "Merlon", "Dojo", "Tunnels",
		"FlowerGate", "Sinister", "RussT", "BadgeShop", "Toybox");

	public static void main(String[] args) throws IOException
	{
		Environment.initialize();

		dumpAll();
		copyAll();
		buildAll();
		validateAll();

		Environment.exit();
	}

	public static void dumpAll() throws IOException
	{
		SoundBankCatalog catalog = SoundBankCatalog.loadDump();
		File songListFile = DUMP_AUDIO.getFile(FN_AUDIO_SONGS);
		String toadTownBgm = getBgmForSong(songListFile, TOAD_TOWN_SONG_ID);
		Collection<File> files = IOUtils.getFilesWithExtension(Directories.DUMP_AUDIO_RAW, "bgm", false);
		for (File f : files) {
			Logger.log("Extracting " + f.getName());
			SoundBankCatalog songCatalog = catalog.withSongBanks(
				songListFile, f.getName());
			Song song = new Song(f, songCatalog);
			if (f.getName().equals(toadTownBgm))
				song.setBranchNames(TOAD_TOWN_BRANCH_NAMES);

			String name = FilenameUtils.getBaseName(f.getName());

			try (XmlWriter xmw = new XmlWriter(Directories.DUMP_AUDIO_BGM.getFile(name + ".xml"))) {
				song.toXML(xmw);
			}
		}
	}

	private static String getBgmForSong(File songListFile, int songID) throws IOException
	{
		XmlReader xmr = new XmlReader(songListFile);
		List<Element> songElements = xmr.getTags(xmr.getRootElement(), TAG_SONG);

		for (int index = 0; index < songElements.size(); index++) {
			Element songElement = songElements.get(index);
			int currentID = xmr.hasAttribute(songElement, ATTR_ID)
				? SoundXml.readHex(xmr, songElement, ATTR_ID, 0, 0xFF) : index;
			if (currentID != songID)
				continue;

			if (xmr.hasAttribute(songElement, ATTR_OLD_BGM))
				return xmr.getAttribute(songElement, ATTR_OLD_BGM);
			xmr.requiresAttribute(songElement, ATTR_BGM);
			return xmr.getAttribute(songElement, ATTR_BGM);
		}

		throw new IOException(String.format("Song list does not contain song ID %X", songID));
	}

	public static void copyAll() throws IOException
	{
		Collection<File> files = IOUtils.getFilesWithExtension(DUMP_AUDIO_BGM, "xml", false);
		for (File dumpFile : files) {
			Logger.log("Copying " + dumpFile.getName());

			File destFile = MOD_AUDIO_BGM.getFile(dumpFile.getName());
			FileUtils.copyFile(dumpFile, destFile);
		}
	}

	public static void buildAll() throws IOException
	{
		SoundBankCatalog catalog = SoundBankCatalog.loadMod();
		Collection<File> files = IOUtils.getFilesWithExtension(MOD_AUDIO_BGM, "xml", false);
		for (File f : files) {
			String filename = FilenameUtils.getBaseName(f.getName());
			String outputName = filename + ".bgm";
			if (AudioModder.hasOverride(outputName)) {
				Logger.log("Using audio override for " + outputName);
				continue;
			}

			Logger.log("Building " + f.getName());

			SoundBankCatalog songCatalog = catalog.withSongBanks(
				MOD_AUDIO.getFile(FN_AUDIO_SONGS), outputName);
			Song song = new Song();
			song.setSoundBankCatalog(songCatalog);

			XmlReader xmr = new XmlReader(f);
			song.fromXML(xmr, xmr.getRootElement());

			File outFile = MOD_AUDIO_BUILD.getFile(outputName);
			song.build(outFile);
		}
	}

	public static void validateAll() throws IOException
	{
		Collection<File> files = IOUtils.getFilesWithExtension(MOD_AUDIO_RAW, "bgm", false);
		for (File rawFile : files) {
			Logger.log("Validating " + rawFile.getName());

			String filename = FilenameUtils.getBaseName(rawFile.getName());

			File newFile = MOD_AUDIO_BUILD.getFile(filename + ".bgm");

			byte[] rawBytes = FileUtils.readFileToByteArray(rawFile);
			byte[] newBytes = FileUtils.readFileToByteArray(newFile);

			assert (rawBytes.length == newBytes.length);

			for (int i = 0; i < rawBytes.length; i++) {
				assert (rawBytes[i] == newBytes[i]) : String.format("%2X --> %2X", rawBytes[i], newBytes[i]);
			}
		}

		Logger.log("All valid :)");
	}
}
