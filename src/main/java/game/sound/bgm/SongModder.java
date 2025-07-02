package game.sound.bgm;

import static app.Directories.*;

import java.io.File;
import java.io.IOException;
import java.util.Collection;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;

import app.Directories;
import app.Environment;
import app.input.IOUtils;
import util.Logger;
import util.xml.XmlWrapper.XmlReader;
import util.xml.XmlWrapper.XmlWriter;

public abstract class SongModder
{
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
		Collection<File> files = IOUtils.getFilesWithExtension(Directories.DUMP_AUDIO_RAW, "bgm", false);
		for (File f : files) {
			Logger.log("Extracting " + f.getName());
			Song song = new Song(f);

			String name = FilenameUtils.getBaseName(f.getName());

			try (XmlWriter xmw = new XmlWriter(Directories.DUMP_AUDIO_BGM.getFile(name + ".xml"))) {
				song.toXML(xmw);
			}
		}
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
		Collection<File> files = IOUtils.getFilesWithExtension(MOD_AUDIO_BGM, "xml", false);
		for (File f : files) {
			Logger.log("Building " + f.getName());

			Song song = new Song();

			XmlReader xmr = new XmlReader(f);
			song.fromXML(xmr, xmr.getRootElement());

			String filename = FilenameUtils.getBaseName(f.getName());

			File outFile = MOD_AUDIO_BUILD.getFile(filename + ".bgm");
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
