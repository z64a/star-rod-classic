package game.sound;

import static app.Directories.*;
import static game.sound.InstrumentPreset.InstrumentKey.TAG_LIST;

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

//interface for the SET1.prg instrument program(?) file
public class InstrumentsModder
{
	private static final String FN_BIN = "SET1.prg";

	public static void main(String[] args) throws IOException
	{
		Environment.initialize();
		dump();
		FileUtils.copyFile(DUMP_AUDIO.getFile(FN_AUDIO_PRESETS), MOD_AUDIO.getFile(FN_AUDIO_PRESETS));
		build();
		Environment.exit();
	}

	public static void dump() throws IOException
	{
		List<InstrumentPreset> instruments = decode(DUMP_AUDIO_RAW.getFile(FN_BIN));
		save(instruments, DUMP_AUDIO.getFile(FN_AUDIO_PRESETS));
		Logger.log("Dumped presets from SET1");
	}

	public static void build() throws IOException
	{
		ArrayList<InstrumentPreset> instruments = load(MOD_AUDIO.getFile(FN_AUDIO_PRESETS));
		encode(instruments, MOD_AUDIO_BUILD.getFile(FN_BIN));
		Logger.log("Built SET1 from presets");
	}

	private static ArrayList<InstrumentPreset> decode(File binFile) throws IOException
	{
		ArrayList<InstrumentPreset> instruments = new ArrayList<>();
		ByteBuffer bb = IOUtils.getDirectBuffer(binFile);

		bb.position(0x10);
		for (int i = 0; i < 7; i++) {
			instruments.add(new InstrumentPreset(bb));
		}

		return instruments;
	}

	private static void encode(List<InstrumentPreset> instruments, File outFile) throws IOException
	{
		DynamicByteBuffer dbb = new DynamicByteBuffer();

		dbb.position(0x10);

		for (InstrumentPreset ins : instruments) {
			ins.put(dbb);
		}

		int endOffset = dbb.position();
		dbb.align(16);

		// write header
		dbb.position(0);

		dbb.putUTF8("PRG ", false);
		dbb.putInt(endOffset);
		dbb.putUTF8("SET1", false);

		IOUtils.writeBufferToFile(dbb.getFixedBuffer(), outFile);
	}

	public static void save(List<InstrumentPreset> instruments, File xmlFile) throws IOException
	{
		try (XmlWriter xmw = new XmlWriter(xmlFile)) {
			XmlTag rootTag = xmw.createTag(TAG_LIST, false);
			xmw.openTag(rootTag);

			for (InstrumentPreset ins : instruments) {
				ins.toXML(xmw);
			}

			xmw.closeTag(rootTag);
			xmw.save();
		}
	}

	public static ArrayList<InstrumentPreset> load(File xmlFile) throws IOException
	{
		ArrayList<InstrumentPreset> instruments = new ArrayList<>();

		XmlReader xmr = new XmlReader(xmlFile);
		Element rootElem = xmr.getRootElement();

		for (Element insElem : xmr.getTags(rootElem, TAG_LIST)) {
			instruments.add(new InstrumentPreset(xmr, insElem));
		}

		return instruments;
	}
}
