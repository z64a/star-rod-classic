package game.sound.engine;

import static game.sound.BankEditor.BankKey.*;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;

import org.w3c.dom.Element;

import app.StarRodException;
import game.sound.TableDesign;
import game.sound.TableDesign.Table;
import game.sound.VADPCM;
import game.sound.VADPCM.CodeBook;
import game.sound.VADPCM.EncodeData;
import util.DynamicByteBuffer;
import util.Logger;
import util.xml.XmlWrapper.XmlReader;
import util.xml.XmlWrapper.XmlSerializable;
import util.xml.XmlWrapper.XmlTag;
import util.xml.XmlWrapper.XmlWriter;

public class Instrument implements XmlSerializable
{
	private static final String EXT_WAV = ".wav";

	public static final int LOOP_FOREVER = -1;

	public String name;
	public String mainFilename;
	public String loopFilename;

	public int wavOffset;
	public int wavLength;

	public int loopStateOffset;
	public int loopStart;
	public int loopEnd;
	public int loopCount;

	public int predictorOffset;

	public int envelopeOffset;

	public int keyBase;
	public int sampleRate;

	public CodeBook book;
	public ArrayList<Short> samples;

	public boolean hasLoop;

	public Envelope envelope;

	// while loading
	public transient String envelopeName;
	public transient int numPredictors;

	// during build
	private transient EncodeData buildData;

	public Instrument(ByteBuffer bb, int start, String name)
	{
		this.name = name;

		bb.position(start);

		wavOffset = bb.getInt();
		wavLength = bb.getInt();

		loopStateOffset = bb.getInt();
		loopStart = bb.getInt();
		loopEnd = bb.getInt();
		loopCount = bb.getInt();

		hasLoop = (loopStart != 0);

		mainFilename = name + EXT_WAV;
		if (hasLoop)
			loopFilename = name + "_loop" + EXT_WAV;

		predictorOffset = bb.getInt();
		int predictorBookSize = bb.getShort();
		keyBase = bb.getShort();

		sampleRate = bb.getInt();

		bb.getInt(); // always zero -- first byte is actually a 'type', but always zero means always ADPCM
		bb.getInt(); // always zero

		envelopeOffset = bb.getInt();

		int wavEnd = wavOffset + wavLength;
		wavEnd = (wavEnd + 15) & -16;

		// read codebook for this instrument
		numPredictors = predictorBookSize / 0x20;
		book = new CodeBook(bb, predictorOffset, numPredictors);

		// decode samples
		samples = VADPCM.decode(bb, book, wavOffset, wavLength);
	}

	public Instrument(XmlReader xmr, Element insElem)
	{
		fromXML(xmr, insElem);
	}

	@Override
	public void fromXML(XmlReader xmr, Element insElem)
	{
		xmr.requiresAttribute(insElem, ATTR_SRC);
		mainFilename = xmr.getAttribute(insElem, ATTR_SRC);

		if (xmr.hasAttribute(insElem, ATTR_LOOP)) {
			loopFilename = xmr.getAttribute(insElem, ATTR_LOOP);
			hasLoop = true;

			if (xmr.hasAttribute(insElem, ATTR_LOOP_COUNT)) {
				loopCount = xmr.readInt(insElem, ATTR_LOOP_COUNT);
			}
			else {
				loopCount = LOOP_FOREVER;
			}
		}

		xmr.requiresAttribute(insElem, ATTR_ENV_NAME);
		envelopeName = xmr.getAttribute(insElem, ATTR_ENV_NAME);

		xmr.requiresAttribute(insElem, ATTR_KEY_BASE);
		keyBase = xmr.readInt(insElem, ATTR_KEY_BASE);

		if (xmr.hasAttribute(insElem, ATTR_NUM_PRED)) {
			numPredictors = xmr.readInt(insElem, ATTR_NUM_PRED);
		}
		else {
			numPredictors = 1;
		}
	}

	@Override
	public void toXML(XmlWriter xmw)
	{
		XmlTag tag = xmw.createTag(TAG_INSTRUMENT, true);

		xmw.addAttribute(tag, ATTR_SRC, mainFilename);
		if (hasLoop) {
			xmw.addAttribute(tag, ATTR_LOOP, loopFilename);
			if (loopCount != LOOP_FOREVER)
				xmw.addInt(tag, ATTR_LOOP_COUNT, loopCount);
		}

		xmw.addAttribute(tag, ATTR_ENV_NAME, envelope.name);

		xmw.addInt(tag, ATTR_KEY_BASE, keyBase);

		if (numPredictors > 1)
			xmw.addInt(tag, ATTR_NUM_PRED, numPredictors);

		xmw.printTag(tag);
	}

	/**
	 * Dump the wav data from this instrument
	 * @param bankDir
	 */
	public void dump(File bankDir)
	{
		List<Short> mainSamples;
		List<Short> loopSamples;

		if (hasLoop) {
			mainSamples = samples.subList(0, loopStart);
			loopSamples = samples.subList(loopStart, loopEnd);
		}
		else {
			mainSamples = samples;
			loopSamples = new ArrayList<>();
		}

		File wavFile = new File(bankDir, mainFilename);
		writeWav(wavFile, mainSamples, sampleRate);

		if (hasLoop) {
			wavFile = new File(bankDir, loopFilename);
			writeWav(wavFile, loopSamples, sampleRate);
		}
	}

	public void load(File bankDir)
	{
		ReadWavData mainWav = readWav(new File(bankDir, mainFilename));
		ReadWavData loopWav = new ReadWavData(mainWav.sampleRate);

		if (hasLoop) {
			loopWav = readWav(new File(bankDir, loopFilename));
			loopStart = mainWav.samples.size();
			loopEnd = mainWav.samples.size() + loopWav.samples.size();
		}

		if (loopWav.sampleRate != mainWav.sampleRate) {
			throw new StarRodException("%s and %s have different sample rates: %f vs %f",
				mainFilename, loopFilename, mainWav.sampleRate, loopWav.sampleRate);
		}

		sampleRate = mainWav.sampleRate;

		samples = new ArrayList<>(mainWav.samples.size() + loopWav.samples.size());
		samples.addAll(mainWav.samples);
		samples.addAll(loopWav.samples);
	}

	public void build()
	{
		// choose power for a requested number of predictors
		int pow;
		switch (numPredictors) {
			case 1:
				pow = 0;
				break;
			case 2:
				pow = 1;
				break;
			case 4:
				pow = 2;
				break;
			default:
				throw new StarRodException("Unsupported number of predictors: %d. Use only 1, 2, or 4.", numPredictors);
		}

		Table tbl = TableDesign.makeTable(samples, VADPCM.ORDER, pow);
		book = new CodeBook(tbl.buffer, 0, tbl.numPred);

		buildData = VADPCM.encode(samples, book, loopStart);
	}

	public void buildLoop(DynamicByteBuffer dbb)
	{
		if (hasLoop) {
			loopStateOffset = dbb.position();
			for (Short s : buildData.loopState) {
				dbb.putShort(s);
			}
		}
	}

	public void buildBook(DynamicByteBuffer dbb)
	{
		predictorOffset = dbb.position();
		book.write(dbb);
	}

	public void buildWav(DynamicByteBuffer dbb)
	{
		wavOffset = dbb.position();

		wavLength = buildData.buffer.remaining();

		dbb.put(buildData.buffer);
	}

	/**
	 * Outputs a 16-bit wav file from a list of samples
	 * @param wavFile  The file to output.
	 * @param samples  List of samples to write.
	 * @param sampleRate
	 */
	private static void writeWav(File wavFile, List<Short> samples, float sampleRate)
	{
		// 16-bit output
		try {
			int numSamples = samples.size();

			byte[] rawData = new byte[numSamples * Short.BYTES];

			for (int i = 0; i < numSamples; i++) {
				short sample = samples.get(i);
				// WAV is conventionally little endian, so store lowest byte first
				rawData[i * 2] = (byte) (sample & 0xFF);
				rawData[i * 2 + 1] = (byte) ((sample >> 8) & 0xFF);
			}

			AudioFormat format = new AudioFormat(sampleRate, Short.SIZE, 1, true, false);

			ByteArrayInputStream bais = new ByteArrayInputStream(rawData);
			AudioInputStream audioInputStream = new AudioInputStream(bais, format, numSamples);

			AudioSystem.write(audioInputStream, AudioFileFormat.Type.WAVE, wavFile);
			Logger.log("Wrote WAV file: " + wavFile.getName());
		}
		catch (Exception e) {
			Logger.printStackTrace(e);
		}
	}

	private static class ReadWavData
	{
		public final List<Short> samples;
		public final int sampleRate;

		public ReadWavData(int sampleRate)
		{
			this.samples = new ArrayList<>();
			this.sampleRate = sampleRate;
		}

		public ReadWavData(List<Short> samples, int sampleRate)
		{
			this.samples = samples;
			this.sampleRate = sampleRate;
		}
	}

	private static ReadWavData readWav(File wavFile)
	{
		try (AudioInputStream audioInputStream = AudioSystem.getAudioInputStream(wavFile)) {

			AudioFormat format = audioInputStream.getFormat();

			// require 16-bit PCM samples
			if (format.getEncoding() != AudioFormat.Encoding.PCM_SIGNED ||
				format.getSampleSizeInBits() != 16) {
				throw new IllegalArgumentException("Only 16-bit signed PCM WAV files are supported.");
			}

			float sampleRate = format.getSampleRate();

			byte[] audioBytes = audioInputStream.readAllBytes();
			List<Short> samples = new ArrayList<>(audioBytes.length / 2);

			// WAV files are conventionally little-endian, but we can support flexible endianness
			boolean bigEndian = format.isBigEndian();

			for (int i = 0; i < audioBytes.length; i += 2) {
				int low = audioBytes[i] & 0xFF;
				int high = audioBytes[i + 1] & 0xFF;
				short sample = bigEndian
					? (short) ((high << 8) | low)
					: (short) ((low) | (high << 8));
				samples.add(sample);
			}

			return new ReadWavData(samples, Math.round(sampleRate));
		}
		catch (IOException | UnsupportedAudioFileException e) {
			throw new StarRodException(e);
		}
	}
}
