package game.sound;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;

import org.apache.commons.io.FilenameUtils;

import app.Directories;
import app.Environment;
import app.input.IOUtils;
import game.sound.TableDesign.Table;
import util.CountingMap;
import util.Logger;

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

			assert (loopStart == 0 || loopCount == -1);

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

	private static class CodeBook
	{
		private final int numPred;
		private final int[][][] predictors;

		private CodeBook(ByteBuffer bb, int offset, int numPred)
		{
			this.numPred = numPred;
			predictors = readBook(bb, offset, numPred);
		}
	}

	private static void dumpInstrument(Instrument ins, ByteBuffer bb)
	{
		int numFrames = ins.wavLength / FRAME_LENGTH;
		//	int numPred = ins.dc_bookSize / 0x20;

		CodeBook book = new CodeBook(bb, ins.predictorOffset, ins.dc_bookSize / 0x20);

		//	int[][][] predictors = readBook(bb, ins.predictorOffset, numPred);
		int[] loopPredictors = new int[16];

		if (ins.loopStart != 0) {
			bb.position(ins.loopPredictorOffset);
			for (int i = 0; i < 16; i++)
				loopPredictors[i] = bb.getShort();
		}

		ArrayList<Integer> samples;
		ArrayList<Integer> loopSamples;
		if (ins.loopStart != 0) {
			samples = new ArrayList<>(ins.loopStart);
			loopSamples = new ArrayList<>(ins.loopEnd - ins.loopStart);
		}
		else {
			samples = new ArrayList<>(ins.wavLength * 2); // 16 samples per 9 bytes
			loopSamples = new ArrayList<>();
		}
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

				if (j == 0)
					System.arraycopy(state, 16 - ORDER, inVec, 0, ORDER);
				else
					System.arraycopy(state, 8 - ORDER, inVec, 0, ORDER);

				for (int i = 0; i < 8; i++) {
					int ind = j * 8 + i;
					inVec[ORDER + i] = ix[ind];

					if (index >= book.numPred)
						index = Math.min(index, book.numPred - 1);

					state[ind] = ix[ind] + innerProduct(ORDER + i, book.predictors[index][i], inVec);
				}
			}

			for (int i : state) {
				if (ins.loopStart > 0 && (samples.size() == ins.loopStart)) {
					loopSamples.add(i);
				}
				else {
					samples.add(i);
				}
			}
		}

		File wavFile = new File(Directories.MOD_OUT.toFile(), ins.outName + ".wav");
		writeWav(wavFile, ins, samples);

		if (loopSamples.size() > 0) {
			wavFile = new File(Directories.MOD_OUT.toFile(), ins.outName + "_loop.wav");
			writeWav(wavFile, ins, loopSamples);
		}

		if (ins.loopStart == 0) {
			// generate a new code book to test encoding
			Table tbl = TableDesign.makeTable(samples, ORDER);
			book = new CodeBook(tbl.buffer, 0, tbl.numPred);

			ByteBuffer recoded = encode(samples, book);

			ArrayList<Integer> outSamples = new ArrayList<>(samples.size());
			decode(recoded, 0, numFrames, book, outSamples);

			File wavFile2 = new File(Directories.MOD_OUT.toFile(), ins.outName + "_2.wav");
			writeWav(wavFile2, ins, outSamples);
		}
	}

	public static void decode(ByteBuffer bb, int inPos, int numFrames, CodeBook book, List<Integer> outSamples)
	{
		int[] state = new int[16];

		bb.position(inPos);
		for (int frame = 0; frame < numFrames; frame++) {
			// read frame header byte
			int header = bb.get() & 0xFF;

			// extract header byte fields
			int scale = 1 << (header >> 4);
			int bestPred = header & 0xF;

			if (bestPred >= book.numPred)
				bestPred = Math.min(bestPred, book.numPred - 1);

			int[] rawSamples = new int[16];

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

				rawSamples[2 * i] = s1;
				rawSamples[2 * i + 1] = s2;
			}

			for (int j = 0; j < 2; j++) {
				int[] inVec = new int[16];

				if (j == 0)
					System.arraycopy(state, 16 - ORDER, inVec, 0, ORDER);
				else
					System.arraycopy(state, 8 - ORDER, inVec, 0, ORDER);

				for (int i = 0; i < 8; i++) {
					int idx = j * 8 + i;
					inVec[ORDER + i] = rawSamples[idx];
					state[idx] = rawSamples[idx] + innerProduct(ORDER + i, book.predictors[bestPred][i], inVec);
				}
			}

			for (int i : state) {
				outSamples.add(i);
			}
		}
	}

	/**
	 * Encodes raw audio samples using VADPCM.
	 *
	 * @param samples Raw audio samples.
	 * @param book    Precomputed predictor codebook.
	 * @return ByteBuffer containing encoded VADPCM audio.
	 */
	public static ByteBuffer encode(List<Integer> samples, CodeBook book)
	{
		int numSamples = samples.size();
		int encodedSize = 9 * (1 + (numSamples / 16)); // 16 samples -> 9

		ByteBuffer encoded = ByteBuffer.allocateDirect(encodedSize);
		int pos = 0;

		short[] buffer = new short[16];
		int[] state = new int[16];

		while (pos < numSamples) {
			int remaining = Math.min(16, numSamples - pos);

			// load frame samples into buffer; pad with zeros if necessary
			if (numSamples - pos >= remaining) {
				for (int i = 0; i < remaining; i++) {
					buffer[i] = (short) (int) samples.get(pos);
					pos++;
				}
				for (int i = remaining; i < 16; i++) {
					buffer[i] = 0;
				}

				encodeFrame(encoded, book, buffer, state);
			}
			else {
				Logger.logError("Missed a frame!");
			}
		}

		encoded.flip();

		return encoded;
	}

	/**
	 * Encodes a single 16-sample audio frame into a compact representation using VADPCM.
	 *
	 * <p>This method selects the best LPC predictor from the provided codebook by minimizing the prediction error,
	 * quantizes the residual error, applies scaling, and writes the encoded data to the output buffer.
	 *
	 * @param out        Buffer to store the encoded frame data.
	 * @param book       Predictor codebook.
	 * @param buffer     Input audio samples for the current frame.
	 * @param state      Current encoder state.
	 */
	private static void encodeFrame(ByteBuffer out, CodeBook book, short[] buffer, int[] state)
	{
		short[] ix = new short[16];
		int[] prediction = new int[16];
		int[] inVec = new int[16];
		int[] saveState = new int[16];
		float[] error = new float[16];
		int[] ie = new int[16];

		int encBits = 4;
		int llevel = -(1 << (encBits - 1));
		int ulevel = -llevel - 1;

		int scaleFactor = 16 - encBits;

		// determine best-fitting predictor
		float sumErrSq;
		float minErrSqr = Float.MAX_VALUE;
		int bestPred = 0;

		for (int k = 0; k < book.numPred; k++) {
			do_prediction(state, buffer, inVec, error, prediction, book.predictors, k);

			sumErrSq = 0.0f;
			for (float v : error) {
				sumErrSq += v * v;
			}

			if (sumErrSq < minErrSqr) {
				minErrSqr = sumErrSq;
				bestPred = k;
			}
		}

		// run prediction with the best predictor
		do_prediction(state, buffer, inVec, error, prediction, book.predictors, bestPred);

		// clamp errors to 16-bit range
		clamp_wow(16, error, ie, 16);

		// scale down to 4-bit signed integer range

		// find the largest absolute  value
		int max = 0;
		for (int i = 0; i < 16; i++) {
			if (Math.abs(ie[i]) > Math.abs(max)) {
				max = ie[i];
			}
		}

		// choose a scale that works for all
		int scale;
		for (scale = 0; scale <= scaleFactor; scale++) {
			if (max <= ulevel && max >= llevel)
				break;
			max /= 2;
		}

		System.arraycopy(state, 0, saveState, 0, 16);

		// attempt to encode
		scale--;
		int maxClip;
		int nIter = 0;
		float err;

		do {
			nIter++;
			scale++;
			maxClip = 0;
			scale = Math.min(scale, 12);

			System.arraycopy(saveState, 16 - ORDER, inVec, 0, ORDER);

			for (int i = 0; i < 8; i++) {
				prediction[i] = innerProduct(ORDER + i, book.predictors[bestPred][i], inVec);
				err = buffer[i] - prediction[i];
				ix[i] = qSample(err, 1 << scale);
				int cV = clip(ix[i], llevel, ulevel) - ix[i];
				maxClip = Math.max(maxClip, Math.abs(cV));
				ix[i] += cV;
				inVec[i + ORDER] = ix[i] * (1 << scale);
				state[i] = prediction[i] + inVec[i + ORDER];
			}

			for (int i = 0; i < ORDER; i++) {
				inVec[i] = state[8 - ORDER + i];
			}

			for (int i = 0; i < 8; i++) {
				prediction[8 + i] = innerProduct(ORDER + i, book.predictors[bestPred][i], inVec);
				err = buffer[8 + i] - prediction[8 + i];
				ix[8 + i] = qSample(err, 1 << scale);
				int cV = clip(ix[8 + i], llevel, ulevel) - ix[8 + i];
				maxClip = Math.max(maxClip, Math.abs(cV));
				ix[8 + i] += cV;
				inVec[i + ORDER] = ix[8 + i] * (1 << scale);
				state[8 + i] = prediction[8 + i] + inVec[i + ORDER];
			}

		}
		while (maxClip >= 2 && nIter < 2);

		// write header
		out.put((byte) ((scale << 4) | (bestPred & 0xF)));

		// write encoded samples
		for (int i = 0; i < 16; i += 2) {
			out.put((byte) ((ix[i] << 4) | (ix[i + 1] & 0xF)));
		}
	}

	private static void do_prediction(int[] state, short[] buffer, int[] inVec, float[] error, int[] prediction,
		int[][][] coefTable, int k)
	{
		System.arraycopy(state, 16 - ORDER, inVec, 0, ORDER);

		for (int i = 0; i < 8; i++) {
			prediction[i] = innerProduct(i + ORDER, coefTable[k][i], inVec);
			inVec[i + ORDER] = buffer[i] - prediction[i];
			error[i] = inVec[i + ORDER];
		}

		for (int i = 0; i < ORDER; i++) {
			inVec[i] = prediction[8 - ORDER + i] + inVec[i + 8];
		}

		for (int i = 0; i < 8; i++) {
			prediction[8 + i] = innerProduct(ORDER + i, coefTable[k][i], inVec);
			inVec[i + ORDER] = buffer[i + 8] - prediction[i + 8];
			error[i + 8] = inVec[i + ORDER];
		}
	}

	private static void writeWav(File wavFile, Instrument ins, ArrayList<Integer> samples)
	{
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

			AudioSystem.write(audioInputStream, AudioFileFormat.Type.WAVE, wavFile);

			System.out.println("WAV file created: " + wavFile.getAbsolutePath());
		}
		catch (Exception e) {
			e.printStackTrace();
		}
		 */

		// hand-written output
		/*
		try {
			int sampleRate = ins.sampleRate;
			int numChannels = 1;
			int bitsPerSample = 16;

			int dataSize = samples.size() * (bitsPerSample / 8);

			int riffChunkSize = 0xC;
			int fmtChunkSize = 0x18;
			int dataChunkSize = 0x8 + dataSize;
			int smplChunkSize = (ins.loopStart != 0) ? 0x44 : 0;
			int fileSize = riffChunkSize + fmtChunkSize + smplChunkSize + dataChunkSize;

			ByteBuffer buffer = ByteBuffer.allocateDirect(fileSize);
			buffer.order(ByteOrder.LITTLE_ENDIAN);

			// Write RIFF Header
			int byteRate = sampleRate * numChannels * (bitsPerSample / 8);
			int blockAlign = numChannels * (bitsPerSample / 8);

			// RIFF Chunk
			buffer.put("RIFF".getBytes());
			buffer.putInt(fileSize - 8);
			buffer.put("WAVE".getBytes());

			// fmt Chunk
			buffer.put("fmt ".getBytes());
			buffer.putInt(16); // fmt chunk size
			buffer.putShort((short) 1); // Audio format = PCM
			buffer.putShort((short) numChannels);
			buffer.putInt(sampleRate);
			buffer.putInt(byteRate);
			buffer.putShort((short) blockAlign);
			buffer.putShort((short) bitsPerSample);

			// smpl chunk
			if (ins.loopStart != 0) {
				buffer.put("smpl".getBytes());
				buffer.putInt(0x3C); // smpl chunk size (fixed size for one loop point)

				buffer.putInt(0); // Manufacturer
				buffer.putInt(0); // Product
				buffer.putInt(1000000000 / sampleRate); // Sample period (in nanoseconds)
				buffer.putInt(60); // MIDI note (middle C)
				buffer.putInt(0); // Fine tuning
				buffer.putInt(0); // SMPTE format
				buffer.putInt(0); // SMPTE offset
				buffer.putInt(1); // Number of sample loops
				buffer.putInt(0); // Sampler data size

				// Loop definition
				buffer.putInt(0); // Cue point ID
				buffer.putInt(0); // Type (0 = forward)
				buffer.putInt(ins.loopStart); // Start sample (loop starts at beginning)
				buffer.putInt(ins.loopEnd); // End sample (loop for 1 second)
				buffer.putInt(0); // Fraction
				buffer.putInt(0); // Play count (0 = infinite)
			}

			// data Chunk
			buffer.put("data".getBytes());
			buffer.putInt(dataSize);

			for (int sample : samples) {
				buffer.putShort((short) sample);
			}

			IOUtils.writeBufferToFile(buffer, wavFile);
			System.out.println("WAV file written with loop points!");
		}
		catch (IOException e) {
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

	private static final int innerProduct(int len, int[] a, int[] b)
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

	private static short qSample(float x, int scale)
	{
		if (x > 0.0f) {
			return (short) ((x / scale) + 0.4999999f);
		}
		else {
			return (short) ((x / scale) - 0.4999999f);
		}
	}

	private static void clamp_wow(int fs, float[] e, int[] ie, int bits)
	{
		float ulevel = (1 << (bits - 1)) - 1;
		float llevel = -ulevel - 1;

		for (int i = 0; i < fs; i++) {
			// clamp to level range
			if (e[i] > ulevel) {
				e[i] = ulevel;
			}
			if (e[i] < llevel) {
				e[i] = llevel;
			}

			// apply rounding
			if (e[i] > 0.0f) {
				ie[i] = (int) (e[i] + 0.5f);
			}
			else {
				ie[i] = (int) (e[i] - 0.5f);
			}
		}
	}

	private static int clip(int ix, int llevel, int ulevel)
	{
		if (ix < llevel) {
			return llevel;
		}
		if (ix > ulevel) {
			return ulevel;
		}
		return ix;
	}
}
