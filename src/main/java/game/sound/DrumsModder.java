package game.sound;

import static app.Directories.*;
import static game.sound.DrumPreset.DrumKey.TAG_LIST;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.w3c.dom.Element;

import app.Environment;
import app.input.IOUtils;
import util.DynamicByteBuffer;
import util.Logger;
import util.xml.XmlWrapper.XmlReader;
import util.xml.XmlWrapper.XmlTag;
import util.xml.XmlWrapper.XmlWriter;

// interface for the SET1.per percussion file
public class DrumsModder
{
	private static final String FN_BIN = "SET1.per";

	public static void main(String[] args) throws IOException
	{
		Environment.initialize();
		dump();
		FileUtils.copyFile(DUMP_AUDIO.getFile(FN_AUDIO_DRUMS), MOD_AUDIO.getFile(FN_AUDIO_DRUMS));
		build();
		Environment.exit();
	}

	public static void dump() throws IOException
	{
		List<DrumPreset> drums = decode(DUMP_AUDIO_RAW.getFile(FN_BIN));
		save(drums, DUMP_AUDIO.getFile(FN_AUDIO_DRUMS));
		Logger.log("Dumped drums from SET1");
	}

	public static void build() throws IOException
	{
		ArrayList<DrumPreset> drums = load(MOD_AUDIO.getFile(FN_AUDIO_DRUMS));
		encode(drums, MOD_AUDIO_BUILD.getFile(FN_BIN));
		Logger.log("Built SET1 from drums");
	}

	private static ArrayList<DrumPreset> decode(File binFile) throws IOException
	{
		ArrayList<DrumPreset> drums = new ArrayList<>();
		ByteBuffer bb = IOUtils.getDirectBuffer(binFile);

		bb.position(0x10);
		for (int i = 0; i < 6; i++) {
			for (int j = 0; j < 12; j++) {
				drums.add(new DrumPreset(bb));
			}
		}

		return drums;
	}

	private static void encode(List<DrumPreset> drums, File outFile) throws IOException
	{
		DynamicByteBuffer dbb = new DynamicByteBuffer();

		dbb.position(0x10);

		for (DrumPreset drum : drums) {
			drum.put(dbb);
		}

		int endOffset = dbb.position();
		dbb.align(16);

		// write header
		dbb.position(0);

		dbb.putUTF8("PER ", false);
		dbb.putInt(endOffset);
		dbb.putUTF8("SET1", false);

		IOUtils.writeBufferToFile(dbb.getFixedBuffer(), outFile);
	}

	public static void save(List<DrumPreset> drums, File xmlFile) throws IOException
	{
		try (XmlWriter xmw = new XmlWriter(xmlFile)) {
			XmlTag rootTag = xmw.createTag(TAG_LIST, false);
			xmw.openTag(rootTag);

			for (DrumPreset drum : drums) {
				drum.toXML(xmw);
			}

			xmw.closeTag(rootTag);
			xmw.save();
		}
	}

	public static ArrayList<DrumPreset> load(File xmlFile) throws IOException
	{
		ArrayList<DrumPreset> drums = new ArrayList<>();

		XmlReader xmr = new XmlReader(xmlFile);
		Element rootElem = xmr.getRootElement();

		for (Element drumElem : xmr.getTags(rootElem, TAG_LIST)) {
			drums.add(new DrumPreset(xmr, drumElem));
		}

		return drums;
	}
}
