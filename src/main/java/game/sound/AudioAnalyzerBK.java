package game.sound;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;

import org.apache.commons.io.FilenameUtils;

import app.Directories;
import app.Environment;
import app.input.IOUtils;
import util.CountingMap;

public class AudioAnalyzerBK
{
	public static void main(String[] args) throws IOException
	{
		Environment.initialize();
		new AudioAnalyzerBK();
		Environment.exit();
	}

	private AudioAnalyzerBK() throws IOException
	{
		for (File f : IOUtils.getFilesWithExtension(Directories.DUMP_AUDIO, new String[] { "bk" }, true)) {
			System.out.println("------------------------------------");
			System.out.print(f.getName() + " ");
			ByteBuffer bb = IOUtils.getDirectBuffer(f);
			new Bank(bb, FilenameUtils.getBaseName(f.getName()));
		}

		/*
		System.out.println();
		System.out.println("Sample Rates:");
		Instrument.sampleRates.print();

		System.out.println();
		System.out.println("Key Bases:");
		Instrument.keyBases.print();
		*/
	}

	private static class Bank
	{
		final String name;

		ArrayList<BKPart> parts;
		HashMap<Integer, BKPart> partMap;

		Instrument[] instruments;
		Predictor[] predictors;

		int instrumentsLength;

		int loopPredictorsStart;
		int loopPredictorsLength;

		int predictorsStart;
		int predictorsLength;

		int envelopesStart;
		int envelopesLength;

		public Bank(ByteBuffer bb, String name)
		{
			this.name = name;

			parts = new ArrayList<>();
			partMap = new HashMap<>();

			addPart(new BKPart(0, 0x40, "Header"));

			bb.position(0xC);

			char c1 = (char) bb.get();
			char c2 = (char) bb.get();
			bb.get();
			bb.get();
			//	System.out.print((char) bb.get());
			//	System.out.print((char) bb.get());
			System.out.println();

			assert (c1 == 'C') : c1;
			assert (c2 == 'R') : c2;

			bb.position(0x10);
			assert (bb.getShort() == 0);

			bb.position(0x12);
			int[] instrumentOffsets = new int[16];
			int instrumentCount = 0;
			for (int i = 0; i < 16; i++) {
				instrumentOffsets[i] = bb.getShort();
				if (instrumentOffsets[i] != 0)
					instrumentCount++;
			}
			instrumentsLength = bb.getShort();

			loopPredictorsStart = bb.getShort();
			loopPredictorsLength = bb.getShort();

			predictorsStart = bb.getShort();
			predictorsLength = bb.getShort();

			envelopesStart = bb.getShort();
			envelopesLength = bb.getShort();

			assert (instrumentsLength == instrumentCount * 0x30);
			assert (loopPredictorsLength == ((loopPredictorsLength + 15) & -16)) : String.format("%X", loopPredictorsLength);
			assert (predictorsLength == ((predictorsLength + 31) & -32)) : String.format("%X", predictorsLength);
			assert (envelopesLength == ((envelopesLength + 1) & -2)) : String.format("%X", envelopesLength);

			// done reading header

			System.out.printf("Instrument count = %X%n", instrumentCount);

			/*
			for (int i = 0; i < 16; i++)
				System.out.printf("%02X ", instrumentOffsets[i]);
			System.out.println();
			*/

			instruments = new Instrument[instrumentCount];
			for (int i = 0; i < instrumentCount; i++) {
				Instrument ins = new Instrument(bb, instrumentOffsets[i]);
				instruments[i] = ins;
				ins.outName = String.format("%s_%02X", name, i);
				dumpInstrument(ins, bb);

				addPart(ins);
				addPart(ins.wavDataPart);
				addPart(new Envelope(bb, ins.envelopeOffset));
				if (ins.loopPredictorOffset != 0)
					addPart(new Predictor(bb, ins.loopPredictorOffset, true));
			}

			assert (predictorsLength != 0);
			assert (predictorsLength == ((predictorsLength + 31) & -32)) : String.format("%X", predictorsLength);
			int numPredictors = predictorsLength / 32;
			predictors = new Predictor[numPredictors];

			bb.position(predictorsStart);
			for (int i = 0; i < numPredictors; i++) {
				predictors[i] = new Predictor(bb, predictorsStart + 0x20 * i, false);
				addPart(predictors[i]);
			}

			int last = 0;
			BKPart prev = null;

			Collections.sort(parts);
			for (BKPart part : parts) {
				// account for padding between different segments of the file
				if (prev != null && prev.getClass() != part.getClass()) {
					int aligned = (last + 15) & -16;
					if (last != aligned) {
						String s = String.format("%-5X %-5X (%X)", last, aligned, aligned - last);
						System.out.printf("%-20s PAD%n", s, name);

						last = aligned;
					}
				}

				System.out.println(part);

				assert (last == -1 || last == part.start);
				last = part.end;
				prev = part;
			}
			System.out.printf("FILE END: %X%n", bb.capacity());
		}

		private void addPart(BKPart part)
		{
			if (!partMap.containsKey(part.start)) {
				parts.add(part);
				partMap.put(part.start, part);
			}
		}

		private BKPart getPart(int offset)
		{
			return partMap.get(offset);
		}
	}

	private static class BKPart implements Comparable<BKPart>
	{
		final String name;
		int start;
		int end;

		public BKPart(int start, int end, String name)
		{
			this.start = start;
			this.end = end;
			this.name = name;
		}

		@Override
		public int compareTo(BKPart o)
		{
			return this.start - o.start;
		}

		@Override
		public String toString()
		{
			String s;
			if (end >= 0)
				s = String.format("%-5X %-5X (%X) ", start, end, end - start);
			else
				s = String.format("%-5X ???   (???) ", start);
			return String.format("%-20s %s", s, name);
		}
	}

	private static class WavData extends BKPart
	{
		public WavData(int start, int end)
		{
			super(start, end, "WavData");
		}

		@Override
		public String toString()
		{
			return super.toString() + "()";
		}
	}

	private static class Instrument extends BKPart
	{
		public String outName;

		static CountingMap<Integer> sampleRates = new CountingMap<>();
		static CountingMap<Integer> keyBases = new CountingMap<>();

		public final WavData wavDataPart;
		public final int wavOffset;
		public final int wavLength;

		public final int loopPredictorOffset;
		public final int loopStart;
		public final int loopEnd;
		public final int loopCount;

		public final int predictorOffset;
		public final int dc_bookSize;
		public final int keyBase;

		public final int sampleRate;

		public final int unk_24;
		public final int unk_28;
		public final int envelopeOffset;

		// 0100 0000 0008 000C 5E7F FF00 58DF 58BF 589F 5880 FF00
		// 0100 0000 0008 000C 5E7F FF00 5C00 FF00
		// 0200 0000 000C 0014 0010 001E 5E7F FF00 5E7F FF00 58DF 58BF 589F 5880 FF00 5C00 FF00
		// 0100 0000 0008 000C 5E7F FF00 4D00 FF00 0000 0000 0000 0000

		public Instrument(ByteBuffer bb, int start)
		{
			super(start, start + 0x30, "Instrument");

			bb.position(start);

			wavOffset = bb.getInt();
			wavLength = bb.getInt();
			loopPredictorOffset = bb.getInt();
			loopStart = bb.getInt();

			loopEnd = bb.getInt();
			loopCount = bb.getInt();
			predictorOffset = bb.getInt();
			dc_bookSize = bb.getShort();
			keyBase = bb.getShort();

			sampleRate = bb.getInt();
			unk_24 = bb.getInt(); // always zero -- first byte is actually a 'type', but always zero means always ADPCM
			unk_28 = bb.getInt(); // always zero
			envelopeOffset = bb.getInt();

			int wavEnd = wavOffset + wavLength;
			wavEnd = (wavEnd + 15) & -16;
			wavDataPart = new WavData(wavOffset, wavEnd);

			sampleRates.add(sampleRate);
			assert (sampleRate == 44100
				|| sampleRate == 32000
				|| sampleRate == 31752
				|| sampleRate == 30000
				|| sampleRate == 24000
				|| sampleRate == 22254
				|| sampleRate == 22050
				|| sampleRate == 19845
				|| sampleRate == 18000
				|| sampleRate == 16000
				|| sampleRate == 15876
				|| sampleRate == 15832
				|| sampleRate == 12000
				|| sampleRate == 11907
				|| sampleRate == 11025
				|| sampleRate == 10000
				|| sampleRate == 8000) : sampleRate;

			if (loopEnd == 0) {
				assert (loopPredictorOffset == 0);
				assert (loopStart == 0);
				assert (loopCount == 0);
			}
			else {
				// note: loopstart MAY be zero here!
				assert (loopPredictorOffset != 0);
				assert (loopEnd > loopStart);
				assert (loopCount == -1);
			}

			assert (dc_bookSize == 0x20 || dc_bookSize == 0x40) : String.format("%X", dc_bookSize);

			keyBases.add(keyBase);
			/*
			assert (keyBase == 2399
				|| keyBase == 2400
				|| keyBase == 2900
				|| keyBase == 3502
				|| keyBase == 3599
				|| keyBase == 3600
				|| keyBase == 4100
				|| keyBase == 4301
				|| keyBase == 4799
				|| keyBase == 4800
				|| keyBase == 4801
				|| keyBase == 4805
				|| keyBase == 6000
				|| keyBase == 6001
				|| keyBase == 6012
				|| keyBase == 6501
				|| keyBase == 7209) : keyBase;
				*/

			assert (unk_24 == 0) : String.format("%X", unk_24);
			assert (unk_28 == 0) : String.format("%X", unk_28);
		}

		@Override
		public String toString()
		{
			StringBuilder sb = new StringBuilder();
			sb.append(super.toString());
			sb.append("( ");

			sb.append(String.format("wav=%X,%X ", wavDataPart.start, wavDataPart.end));
			if (loopCount != 0)
				sb.append(String.format("loop=%X,(%X-%X),%d ", loopPredictorOffset, loopStart, loopEnd, loopCount));
			sb.append(")");

			return sb.toString();
		}
	}

	private static class Predictor extends BKPart
	{
		final short[] data = new short[16];

		public Predictor(ByteBuffer bb, int start, boolean loop)
		{
			super(start, start + 0x20, loop ? "Loop Predictor" : "Predictor");

			bb.position(start);
			for (int i = 0; i < data.length; i++)
				data[i] = bb.getShort();
		}

		@Override
		public String toString()
		{
			StringBuilder sb = new StringBuilder();
			sb.append(super.toString());
			sb.append("(");

			for (int i = 0; i < data.length; i++)
				sb.append(String.format(" %6d", data[i] & 0xFFFF));

			sb.append(" )");
			return sb.toString();
		}
	}

	private static class Envelope extends BKPart
	{
		final int count;

		public Envelope(ByteBuffer bb, int start)
		{
			super(start, -1, "Envelope");

			bb.position(start);
			count = bb.get() & 0xFF;
			byte b1 = bb.get();
			byte b2 = bb.get();
			byte b3 = bb.get();

			assert (b1 == 0);
			assert (b2 == 0);
			assert (b3 == 0);

			int[][] cmdLists = new int[count][2];
			for (int i = 0; i < count; i++) {
				cmdLists[i][0] = start + (bb.getShort() & 0xFFFF);
				cmdLists[i][1] = start + (bb.getShort() & 0xFFFF);
			}

			end = bb.position();

			for (int i = 0; i < count; i++) {
				readCmdList(bb, cmdLists[i][0]);
				readCmdList(bb, cmdLists[i][1]);
			}
		}

		private void readCmdList(ByteBuffer bb, int start)
		{
			boolean done = false;
			bb.position(start);

			do {
				int cmd = bb.get();
				int arg = bb.get();

				switch (cmd & 0xFF) {
					default:
						break;
					case 0xFB: // ENV_CMD_END_LOOP
					case 0xFC: // ENV_CMD_START_LOOP
					case 0xFD: // ENV_CMD_ADD_MULTIPLIER
					case 0xFE: // ENV_CMD_SET_MULTIPLIER
						break;
					case 0xFF: // ENV_CMD_END
						done = true;
						break;
				}

			}
			while (!done);

			if (bb.position() > end)
				end = bb.position();
		}

		@Override
		public String toString()
		{
			return super.toString() + "()";
		}
	}

	private static final int FRAME_LENGTH = 9;
	private static final int ORDER = 2;

	private static void dumpInstrument(Instrument ins, ByteBuffer bb)
	{
		int numFrames = ins.wavLength / FRAME_LENGTH;
		int numPred = ins.dc_bookSize / 0x20;

		int[][][] predictors = readBook(bb, ins.predictorOffset, numPred);
		int[][] loopPredictors = new int[8][2]; //TODO bookSize used for loop predictors??

		ArrayList<Integer> samples = new ArrayList<>(ins.wavLength * 2);
		int[] state = new int[16];

		bb.position(ins.wavOffset);
		for (int frame = 0; frame < numFrames; frame++) {
			// read frame header byte
			int header = bb.get() & 0xFF;

			// extract header byte fields
			int scale = 1 << (header >> 4);
			int index = header & 0xF;

			int[] ix = new int[16];

			// read frame sample bytes
			for (int i = 0; i < 8; i++) {
				int v = bb.get() & 0xFF;

				// extract 4-bit sample pair
				int s1 = v >> 4;
				int s2 = v & 0xF;

				// 4-bit sign extension
				s1 = (s1 << 28) >> 28;
				s2 = (s2 << 28) >> 28;

				// apply scale factor
				s1 *= scale;
				s2 *= scale;

				ix[2 * i] = s1;
				ix[2 * i + 1] = s2;
			}

			for (int j = 0; j < 2; j++) {

				int[] inVec = new int[16];

				if (j == 0) {
					for (int i = 0; i < ORDER; i++) {
						inVec[i] = state[16 - ORDER + i];
					}
				}
				else {
					for (int i = 0; i < ORDER; i++) {
						inVec[i] = state[8 - ORDER + i];
					}
				}

				for (int i = 0; i < 8; i++) {
					int ind = j * 8 + i;
					inVec[ORDER + i] = ix[ind];

					if (index >= numPred)
						index = Math.min(index, numPred - 1);

					state[ind] = inner_prod(ORDER + i, predictors[index][i], inVec);
				}
			}

			for (int i : state) {
				samples.add(i);
			}
		}

		if (ins.loopStart != 0)
			return; //TODO unsupported

		// 16-bit output
		try {
			float sampleRate = ins.sampleRate;
			int sampleSizeInBits = 16;
			int channels = 1;
			boolean signed = true;
			boolean bigEndian = false;

			int numSamples = samples.size();
			byte[] rawData = new byte[numSamples * 2];

			for (int i = 0; i < numSamples; i++) {
				int sample = samples.get(i);
				rawData[i * 2] = (byte) (sample & 0xFF);
				rawData[i * 2 + 1] = (byte) ((sample >> 8) & 0xFF);
			}

			AudioFormat format = new AudioFormat(sampleRate, sampleSizeInBits, channels, signed, bigEndian);

			ByteArrayInputStream bais = new ByteArrayInputStream(rawData);
			AudioInputStream audioInputStream = new AudioInputStream(bais, format, numSamples);

			File wavFile = new File(Directories.MOD_OUT.toFile(), ins.outName + ".wav");
			AudioSystem.write(audioInputStream, AudioFileFormat.Type.WAVE, wavFile);

			System.out.println("WAV file created: " + wavFile.getAbsolutePath());
		}
		catch (Exception e) {
			e.printStackTrace();
		}

		// 32-bit output
		/*
		try {
			float sampleRate = ins.sampleRate;
			int sampleSizeInBits = 32;
			int channels = 1;
			boolean signed = true;
			boolean bigEndian = false;
		
			int numSamples = samples.size();
			byte[] rawData = new byte[numSamples * 4];
		
			for (int i = 0; i < numSamples; i++) {
				int sample = samples.get(i);
				rawData[i * 4] = (byte) (sample & 0xFF);
				rawData[i * 4 + 1] = (byte) ((sample >> 8) & 0xFF);
				rawData[i * 4 + 2] = (byte) ((sample >> 16) & 0xFF);
				rawData[i * 4 + 3] = (byte) ((sample >> 24) & 0xFF);
			}
		
			// Define audio format
			AudioFormat format = new AudioFormat(sampleRate, sampleSizeInBits, channels, signed, bigEndian);
		
			// Write to WAV file
			ByteArrayInputStream bais = new ByteArrayInputStream(rawData);
			AudioInputStream audioInputStream = new AudioInputStream(bais, format, numSamples);
		
			File wavFile = new File(Directories.MOD_OUT.toFile(), ins.outName + ".wav");
			AudioSystem.write(audioInputStream, AudioFileFormat.Type.WAVE, wavFile);
		
			System.out.println("WAV file created: " + wavFile.getAbsolutePath());
		}
		catch (Exception e) {
			e.printStackTrace();
		}
		 */
	}

	private static final int[][][] readBook(ByteBuffer bb, int startPos, int numPred)
	{
		int[][][] predictors = new int[numPred][8][ORDER + 8];

		bb.position(startPos);

		for (int i = 0; i < numPred; i++) {
			for (int j = 0; j < ORDER; j++) {
				for (int k = 0; k < 8; k++) {
					predictors[i][k][j] = bb.getShort();
				}
			}

			for (int k = 1; k < 8; k++) {
				predictors[i][k][ORDER] = predictors[i][k - 1][ORDER - 1];
			}

			predictors[i][0][ORDER] = 1 << 11;

			for (int k = 1; k < 8; k++) {
				for (int j = 0; j < k; j++) {
					predictors[i][j][k + ORDER] = 0;
				}

				for (int j = k; j < 8; j++) {
					predictors[i][j][k + ORDER] = predictors[i][j - k][ORDER];
				}
			}
		}

		return predictors;
	}

	private static final int inner_prod(int len, int[] a, int[] b)
	{
		int out = 0;

		for (int k = 0; k < len; k++) {
			out += a[k] * b[k];
		}

		int dout = out / (1 << 11);
		int fiout = dout * (1 << 11);

		if ((out - fiout) < 0)
			return dout - 1;
		else
			return dout;
	}
}
