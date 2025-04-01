package game.sound;

import static app.Directories.*;
import static game.sound.BankModder.BankKey.ATTR_KEY_BASE;
import static game.sound.DrumModder.DrumKey.*;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import org.w3c.dom.Element;

import app.Environment;
import app.input.IOUtils;
import util.DynamicByteBuffer;
import util.Logger;
import util.xml.XmlKey;
import util.xml.XmlWrapper.XmlReader;
import util.xml.XmlWrapper.XmlSerializable;
import util.xml.XmlWrapper.XmlTag;
import util.xml.XmlWrapper.XmlWriter;

public class DrumModder
{
	public static void main(String[] args) throws IOException
	{
		Environment.initialize();
		dump();
		Environment.exit();
	}

	public static enum DrumKey implements XmlKey
	{
		// @formatter:off
		TAG_ROOT		("DrumList"),
		TAG_DRUM		("Drum"),
		ATTR_BANK			("bank"),
		ATTR_PATCH			("patch"),
		ATTR_KEY_BASE		("keyBase"),
		ATTR_VOLUME			("volume"),
		ATTR_PAN			("pan"),
		ATTR_REVERB			("reverb"),
		ATTR_RAND_TUNE		("randTune"),
		ATTR_RAND_VOLUME	("randVolume"),
		ATTR_RAND_PAN		("randPan"),
		ATTR_RAND_REVERB	("randReverb");
		// @formatter:on

		private final String key;

		private DrumKey(String key)
		{
			this.key = key;
		}

		@Override
		public String toString()
		{
			return key;
		}
	}

	public static class Drum implements XmlSerializable
	{
		public int bank;
		public int patch;
		public int keybase;
		public int volume;
		public int pan;
		public int reverb;
		public int randTune;
		public int randVolume;
		public int randPan;
		public int randReverb;

		private Drum()
		{}

		public Drum(XmlReader xmr, Element insElem)
		{
			fromXML(xmr, insElem);
		}

		@Override
		public void fromXML(XmlReader xmr, Element insElem)
		{
			xmr.requiresAttribute(insElem, ATTR_BANK);
			bank = xmr.readHex(insElem, ATTR_BANK);

			xmr.requiresAttribute(insElem, ATTR_PATCH);
			patch = xmr.readHex(insElem, ATTR_PATCH);

			xmr.requiresAttribute(insElem, ATTR_KEY_BASE);
			keybase = xmr.readInt(insElem, ATTR_KEY_BASE);

			xmr.requiresAttribute(insElem, ATTR_VOLUME);
			volume = xmr.readHex(insElem, ATTR_VOLUME);

			xmr.requiresAttribute(insElem, ATTR_PAN);
			pan = xmr.readHex(insElem, ATTR_PAN);

			xmr.requiresAttribute(insElem, ATTR_REVERB);
			reverb = xmr.readHex(insElem, ATTR_REVERB);

			if (xmr.hasAttribute(insElem, ATTR_RAND_TUNE))
				randTune = xmr.readHex(insElem, ATTR_RAND_TUNE);

			if (xmr.hasAttribute(insElem, ATTR_RAND_VOLUME))
				randVolume = xmr.readHex(insElem, ATTR_RAND_VOLUME);

			if (xmr.hasAttribute(insElem, ATTR_RAND_PAN))
				randPan = xmr.readHex(insElem, ATTR_RAND_PAN);

			if (xmr.hasAttribute(insElem, ATTR_RAND_REVERB))
				randReverb = xmr.readHex(insElem, ATTR_RAND_REVERB);
		}

		@Override
		public void toXML(XmlWriter xmw)
		{
			XmlTag tag = xmw.createTag(TAG_DRUM, true);

			xmw.addHex(tag, ATTR_BANK, bank);
			xmw.addHex(tag, ATTR_PATCH, patch);

			xmw.addInt(tag, ATTR_KEY_BASE, keybase);
			xmw.addHex(tag, ATTR_VOLUME, volume);
			xmw.addHex(tag, ATTR_PAN, pan);
			xmw.addHex(tag, ATTR_REVERB, reverb);

			if (randTune != 0)
				xmw.addHex(tag, ATTR_RAND_TUNE, randTune);

			if (randVolume != 0)
				xmw.addHex(tag, ATTR_RAND_VOLUME, randVolume);

			if (randPan != 0)
				xmw.addHex(tag, ATTR_RAND_PAN, randPan);

			if (randReverb != 0)
				xmw.addHex(tag, ATTR_RAND_REVERB, randReverb);

			xmw.printTag(tag);
		}
	}

	public static void dump() throws IOException
	{
		List<Drum> drums = decode(DUMP_AUDIO_RAW.getFile("SET1.per"));
		save(drums, DUMP_AUDIO.getFile(FN_AUDIO_DRUMS));
		Logger.log("Dumped drums from SET1");
	}

	public static void build() throws IOException
	{
		ArrayList<Drum> drums = load(MOD_AUDIO.getFile(FN_AUDIO_DRUMS));
		encode(drums, MOD_AUDIO_BUILD.getFile("SET1.per"));
		Logger.log("Built SET1 from drums");
	}

	private static ArrayList<Drum> decode(File binFile) throws IOException
	{
		ArrayList<Drum> drums = new ArrayList<>();
		ByteBuffer bb = IOUtils.getDirectBuffer(binFile);

		bb.position(0x10);
		for (int i = 0; i < 6; i++) {
			for (int j = 0; j < 12; j++) {
				Drum drum = new Drum();
				drum.bank = bb.get() & 0xFF;
				drum.patch = bb.get() & 0xFF;
				drum.keybase = bb.getShort() & 0xFFFF;

				drum.volume = bb.get() & 0xFF;
				drum.pan = bb.get();
				drum.reverb = bb.get() & 0xFF;
				drum.randTune = bb.get() & 0xFF;

				drum.randVolume = bb.get() & 0xFF;
				drum.randPan = bb.get() & 0xFF;
				drum.randReverb = bb.get() & 0xFF;
				int unused = bb.get(); // unused

				assert (unused == 0);

				drums.add(drum);
			}
		}

		return drums;
	}

	private static void encode(List<Drum> drums, File outFile) throws IOException
	{
		DynamicByteBuffer dbb = new DynamicByteBuffer();

		dbb.position(0x10);

		for (Drum drum : drums) {
			dbb.putByte(drum.bank);
			dbb.putByte(drum.patch);
			dbb.putShort(drum.keybase);

			dbb.putByte(drum.volume);
			dbb.putByte(drum.pan);
			dbb.putByte(drum.reverb);
			dbb.putByte(drum.randTune);

			dbb.putByte(drum.randVolume);
			dbb.putByte(drum.randPan);
			dbb.putByte(drum.randReverb);
			dbb.putByte(0);
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

	public static void save(List<Drum> drums, File xmlFile) throws IOException
	{
		try (XmlWriter xmw = new XmlWriter(xmlFile)) {
			XmlTag rootTag = xmw.createTag(TAG_ROOT, false);
			xmw.openTag(rootTag);

			for (Drum drum : drums) {
				drum.toXML(xmw);
			}

			xmw.closeTag(rootTag);
			xmw.save();
		}
	}

	public static ArrayList<Drum> load(File xmlFile) throws IOException
	{
		ArrayList<Drum> drums = new ArrayList<>();

		XmlReader xmr = new XmlReader(xmlFile);
		Element rootElem = xmr.getRootElement();

		for (Element drumElem : xmr.getTags(rootElem, TAG_DRUM)) {
			drums.add(new Drum(xmr, drumElem));
		}

		return drums;
	}
}
