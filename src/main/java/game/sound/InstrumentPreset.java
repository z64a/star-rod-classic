package game.sound;

import static game.sound.InstrumentPreset.InstrumentKey.*;

import java.nio.ByteBuffer;

import org.w3c.dom.Element;

import util.DynamicByteBuffer;
import util.xml.XmlKey;
import util.xml.XmlWrapper.XmlReader;
import util.xml.XmlWrapper.XmlSerializable;
import util.xml.XmlWrapper.XmlTag;
import util.xml.XmlWrapper.XmlWriter;

public class InstrumentPreset implements XmlSerializable
{
	public enum InstrumentKey implements XmlKey
	{
		// @formatter:off
		TAG_LIST		("Instruments"),
		TAG_INSTRUMENT	("Instrument"),

		ATTR_WAV			("wav"),
		ATTR_ENVELOPE		("envelope"),
		ATTR_VOLUME			("volume"),
		ATTR_PAN			("pan"),
		ATTR_REVERB			("reverb"),
		ATTR_COARSE_TUNE	("coarseTune"),
		ATTR_FINE_TUNE		("fineTune");
		// @formatter:on

		private final String key;

		private InstrumentKey(String key)
		{
			this.key = key;
		}

		@Override
		public String toString()
		{
			return key;
		}
	}

	public int bank;
	public int patch;
	public String wav;
	public int envelope;
	public int volume;
	public int pan;
	public int reverb;
	public int coarseTune;
	public int fineTune;

	public InstrumentPreset(XmlReader xmr, Element elem)
	{
		fromXML(xmr, elem);
	}

	public InstrumentPreset(ByteBuffer bb)
	{
		bank = bb.get() & 0xFF;
		patch = bb.get() & 0xFF;
		volume = bb.get() & 0xFF;
		pan = bb.get(); // signed

		reverb = bb.get() & 0xFF;
		coarseTune = bb.get(); // signed
		fineTune = bb.get(); // signed
		int unused = bb.get(); // padding byte

		assert (unused == 0) : unused;
	}

	public void put(DynamicByteBuffer dbb)
	{
		dbb.putByte(bank);
		dbb.putByte(patch);
		dbb.putByte(volume);
		dbb.putByte(pan);
		dbb.putByte(reverb);
		dbb.putByte(coarseTune);
		dbb.putByte(fineTune);
		dbb.putByte(0); // pad
	}

	public void setWav(SoundBankCatalog catalog)
	{
		SoundBankCatalog.WavReference reference = catalog.getWav(bank, patch);
		wav = reference.wav;
		envelope = reference.envelope;
	}

	public void resolveWav(SoundBankCatalog catalog)
	{
		SoundBankCatalog.InstrumentAddress address = catalog.getAddress(wav, envelope);
		bank = address.bank;
		patch = address.patch;
	}

	@Override
	public void fromXML(XmlReader xmr, Element elem)
	{
		xmr.requiresAttribute(elem, ATTR_WAV);
		wav = xmr.getAttribute(elem, ATTR_WAV);

		if (xmr.hasAttribute(elem, ATTR_ENVELOPE))
			envelope = SoundXml.readInt(xmr, elem, ATTR_ENVELOPE, 0, 3);

		volume = SoundXml.readHex(xmr, elem, ATTR_VOLUME, 0, 255);
		pan = SoundXml.readInt(xmr, elem, ATTR_PAN, -128, 127);
		reverb = SoundXml.readInt(xmr, elem, ATTR_REVERB, 0, 255);
		coarseTune = SoundXml.readInt(xmr, elem, ATTR_COARSE_TUNE, -128, 127);
		fineTune = SoundXml.readInt(xmr, elem, ATTR_FINE_TUNE, -128, 127);
	}

	@Override
	public void toXML(XmlWriter xmw)
	{
		XmlTag tag = xmw.createTag(TAG_INSTRUMENT, true);

		xmw.addAttribute(tag, ATTR_WAV, wav);
		if (envelope != 0)
			xmw.addInt(tag, ATTR_ENVELOPE, envelope);
		SoundXml.addHex(xmw, tag, ATTR_VOLUME, 2, volume);
		xmw.addInt(tag, ATTR_PAN, pan);
		xmw.addInt(tag, ATTR_REVERB, reverb);
		xmw.addInt(tag, ATTR_COARSE_TUNE, coarseTune);
		xmw.addInt(tag, ATTR_FINE_TUNE, fineTune);

		xmw.printTag(tag);
	}
}
