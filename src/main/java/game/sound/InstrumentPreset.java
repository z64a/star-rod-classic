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

		ATTR_BANK			("bank"),
		ATTR_PATCH			("patch"),
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

	@Override
	public void fromXML(XmlReader xmr, Element elem)
	{
		xmr.requiresAttribute(elem, ATTR_BANK);
		bank = xmr.readHex(elem, ATTR_BANK);

		xmr.requiresAttribute(elem, ATTR_PATCH);
		patch = xmr.readHex(elem, ATTR_PATCH);

		xmr.requiresAttribute(elem, ATTR_VOLUME);
		volume = xmr.readInt(elem, ATTR_VOLUME);

		xmr.requiresAttribute(elem, ATTR_PAN);
		pan = xmr.readInt(elem, ATTR_PAN);

		xmr.requiresAttribute(elem, ATTR_REVERB);
		reverb = xmr.readInt(elem, ATTR_REVERB);

		xmr.requiresAttribute(elem, ATTR_COARSE_TUNE);
		coarseTune = xmr.readInt(elem, ATTR_COARSE_TUNE);

		xmr.requiresAttribute(elem, ATTR_FINE_TUNE);
		fineTune = xmr.readInt(elem, ATTR_FINE_TUNE);
	}

	@Override
	public void toXML(XmlWriter xmw)
	{
		XmlTag tag = xmw.createTag(TAG_INSTRUMENT, true);

		xmw.addHex(tag, ATTR_BANK, bank);
		xmw.addHex(tag, ATTR_PATCH, patch);
		xmw.addInt(tag, ATTR_VOLUME, volume);
		xmw.addInt(tag, ATTR_PAN, pan);
		xmw.addInt(tag, ATTR_REVERB, reverb);
		xmw.addInt(tag, ATTR_COARSE_TUNE, coarseTune);
		xmw.addInt(tag, ATTR_FINE_TUNE, fineTune);

		xmw.printTag(tag);
	}
}
