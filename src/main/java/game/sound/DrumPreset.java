package game.sound;

import static game.sound.DrumPreset.DrumKey.*;

import java.nio.ByteBuffer;

import org.w3c.dom.Element;

import util.DynamicByteBuffer;
import util.xml.XmlKey;
import util.xml.XmlWrapper.XmlReader;
import util.xml.XmlWrapper.XmlSerializable;
import util.xml.XmlWrapper.XmlTag;
import util.xml.XmlWrapper.XmlWriter;

public class DrumPreset implements XmlSerializable
{
	public static enum DrumKey implements XmlKey
	{
		// @formatter:off
		TAG_LIST		("Drums"),
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

	public DrumPreset(XmlReader xmr, Element insElem)
	{
		fromXML(xmr, insElem);
	}

	public DrumPreset(ByteBuffer bb)
	{
		bank = bb.get() & 0xFF;
		patch = bb.get() & 0xFF;
		keybase = bb.getShort() & 0xFFFF;

		volume = bb.get() & 0xFF;
		pan = bb.get(); // signed
		reverb = bb.get() & 0xFF;
		randTune = bb.get() & 0xFF;

		randVolume = bb.get() & 0xFF;
		randPan = bb.get() & 0xFF;
		randReverb = bb.get() & 0xFF;
		int unused = bb.get(); // unused

		assert (unused == 0);
	}

	public void put(DynamicByteBuffer dbb)
	{
		dbb.putByte(bank);
		dbb.putByte(patch);
		dbb.putShort(keybase);

		dbb.putByte(volume);
		dbb.putByte(pan);
		dbb.putByte(reverb);
		dbb.putByte(randTune);

		dbb.putByte(randVolume);
		dbb.putByte(randPan);
		dbb.putByte(randReverb);
		dbb.putByte(0); // pad
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
		volume = xmr.readInt(insElem, ATTR_VOLUME);

		xmr.requiresAttribute(insElem, ATTR_PAN);
		pan = xmr.readInt(insElem, ATTR_PAN);

		xmr.requiresAttribute(insElem, ATTR_REVERB);
		reverb = xmr.readInt(insElem, ATTR_REVERB);

		if (xmr.hasAttribute(insElem, ATTR_RAND_TUNE))
			randTune = xmr.readInt(insElem, ATTR_RAND_TUNE);

		if (xmr.hasAttribute(insElem, ATTR_RAND_VOLUME))
			randVolume = xmr.readInt(insElem, ATTR_RAND_VOLUME);

		if (xmr.hasAttribute(insElem, ATTR_RAND_PAN))
			randPan = xmr.readInt(insElem, ATTR_RAND_PAN);

		if (xmr.hasAttribute(insElem, ATTR_RAND_REVERB))
			randReverb = xmr.readInt(insElem, ATTR_RAND_REVERB);
	}

	@Override
	public void toXML(XmlWriter xmw)
	{
		XmlTag tag = xmw.createTag(TAG_DRUM, true);

		xmw.addHex(tag, ATTR_BANK, bank);
		xmw.addHex(tag, ATTR_PATCH, patch);

		xmw.addInt(tag, ATTR_KEY_BASE, keybase);
		xmw.addInt(tag, ATTR_VOLUME, volume);
		xmw.addInt(tag, ATTR_PAN, pan);
		xmw.addInt(tag, ATTR_REVERB, reverb);

		if (randTune != 0)
			xmw.addInt(tag, ATTR_RAND_TUNE, randTune);

		if (randVolume != 0)
			xmw.addInt(tag, ATTR_RAND_VOLUME, randVolume);

		if (randPan != 0)
			xmw.addInt(tag, ATTR_RAND_PAN, randPan);

		if (randReverb != 0)
			xmw.addInt(tag, ATTR_RAND_REVERB, randReverb);

		xmw.printTag(tag);
	}
}
