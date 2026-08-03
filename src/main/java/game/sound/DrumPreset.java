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
		ATTR_WAV			("wav"),
		ATTR_ENVELOPE		("envelope"),
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
	public String wav;
	public int envelope;
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
	public void fromXML(XmlReader xmr, Element insElem)
	{
		xmr.requiresAttribute(insElem, ATTR_WAV);
		wav = xmr.getAttribute(insElem, ATTR_WAV);

		if (xmr.hasAttribute(insElem, ATTR_ENVELOPE))
			envelope = SoundXml.readInt(xmr, insElem, ATTR_ENVELOPE, 0, 3);

		keybase = SoundXml.readInt(xmr, insElem, ATTR_KEY_BASE, 0, 0xFFFF);
		volume = SoundXml.readHex(xmr, insElem, ATTR_VOLUME, 0, 255);
		pan = SoundXml.readInt(xmr, insElem, ATTR_PAN, -128, 127);
		reverb = SoundXml.readInt(xmr, insElem, ATTR_REVERB, 0, 255);

		if (xmr.hasAttribute(insElem, ATTR_RAND_TUNE))
			randTune = SoundXml.readInt(xmr, insElem, ATTR_RAND_TUNE, 0, 255);

		if (xmr.hasAttribute(insElem, ATTR_RAND_VOLUME))
			randVolume = SoundXml.readHex(xmr, insElem, ATTR_RAND_VOLUME, 0, 255);

		if (xmr.hasAttribute(insElem, ATTR_RAND_PAN))
			randPan = SoundXml.readInt(xmr, insElem, ATTR_RAND_PAN, 0, 255);

		if (xmr.hasAttribute(insElem, ATTR_RAND_REVERB))
			randReverb = SoundXml.readInt(xmr, insElem, ATTR_RAND_REVERB, 0, 255);
	}

	@Override
	public void toXML(XmlWriter xmw)
	{
		XmlTag tag = xmw.createTag(TAG_DRUM, true);

		xmw.addAttribute(tag, ATTR_WAV, wav);
		if (envelope != 0)
			xmw.addInt(tag, ATTR_ENVELOPE, envelope);

		xmw.addInt(tag, ATTR_KEY_BASE, keybase);
		SoundXml.addHex(xmw, tag, ATTR_VOLUME, 2, volume);
		xmw.addInt(tag, ATTR_PAN, pan);
		xmw.addInt(tag, ATTR_REVERB, reverb);

		if (randTune != 0)
			xmw.addInt(tag, ATTR_RAND_TUNE, randTune);

		if (randVolume != 0)
			SoundXml.addHex(xmw, tag, ATTR_RAND_VOLUME, 2, randVolume);

		if (randPan != 0)
			xmw.addInt(tag, ATTR_RAND_PAN, randPan);

		if (randReverb != 0)
			xmw.addInt(tag, ATTR_RAND_REVERB, randReverb);

		xmw.printTag(tag);
	}
}
