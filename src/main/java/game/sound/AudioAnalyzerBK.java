package game.sound;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;

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
			new Bank(bb);
		}
	}

	private static class Bank
	{
		ArrayList<BKPart> parts;
		HashMap<Integer, BKPart> partMap;

		Instrument[] instruments;
		Predictor[] predictors;

		int instrumentsLength;

		int partBStart;
		int partBLength;

		int predictorsStart;
		int predictorsLength;

		int partDStart;
		int partDLength;

		public Bank(ByteBuffer bb)
		{
			parts = new ArrayList<>();
			partMap = new HashMap<>();

			addPart(new BKPart(0, 0x40, "Header"));

			bb.position(0xC);

			char c1 = (char) bb.get();
			char c2 = (char) bb.get();
			System.out.print((char) bb.get());
			System.out.print((char) bb.get());
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

			partBStart = bb.getShort();
			partBLength = bb.getShort();

			predictorsStart = bb.getShort();
			predictorsLength = bb.getShort();

			partDStart = bb.getShort();
			partDLength = bb.getShort();

			assert (instrumentsLength == instrumentCount * 0x30);
			assert (partBLength == ((partBLength + 15) & -16)) : String.format("%X", partBLength);
			assert (predictorsLength == ((predictorsLength + 31) & -32)) : String.format("%X", predictorsLength);
			assert (partDLength == ((partDLength + 1) & -2)) : String.format("%X", partDLength);

			// done reading header

			System.out.printf("Instrument count = %X%n", instrumentCount);
			for (int i = 0; i < 16; i++)
				System.out.printf("%02X ", instrumentOffsets[i]);
			System.out.println();

			instruments = new Instrument[instrumentCount];
			for (int i = 0; i < instrumentCount; i++) {
				Instrument ins = new Instrument(bb, instrumentOffsets[i]);
				instruments[i] = ins;
				addPart(ins);
				addPart(ins.wavDataPart);
				addPart(new BKPart(ins.unk_Offset_2C, -1, "UNK 2C")); //TODO length unk!
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
			Collections.sort(parts);
			for (BKPart part : parts) {
				System.out.println(part);

				assert (last == -1 || last == part.start);
				last = part.end;
			}
			System.out.printf("FILE END: %X%n", bb.capacity());

			System.out.println();
			System.out.println("Sample Rates:");
			Instrument.sampleRates.print();
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
		static CountingMap<Integer> sampleRates = new CountingMap<>();

		WavData wavDataPart;
		int wavOffset;
		int wavLength;

		int loopPredictorOffset;
		int loopStart;
		int loopEnd;
		int loopCount;

		int predictorOffset;
		int unk_1C;

		int sampleRate;

		int unk_24;
		int unk_28;
		int unk_Offset_2C;

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
			unk_1C = bb.getInt();

			sampleRate = bb.getInt();
			unk_24 = bb.getInt(); // always zero
			unk_28 = bb.getInt(); // always zero
			unk_Offset_2C = bb.getInt();

			//TODO supposedly, byte_25 is a field, but it seems to always be zero!

			int wavEnd = wavOffset + wavLength;
			wavEnd = (wavEnd + 15) & -16;
			wavDataPart = new WavData(wavOffset, wavEnd);

			sampleRates.add(sampleRate);
			assert (sampleRate == 44100 ||
				sampleRate == 32000 ||
				sampleRate == 31752 ||
				sampleRate == 30000 ||
				sampleRate == 24000 ||
				sampleRate == 22254 ||
				sampleRate == 22050 ||
				sampleRate == 19845 ||
				sampleRate == 18000 ||
				sampleRate == 16000 ||
				sampleRate == 15876 ||
				sampleRate == 15832 ||
				sampleRate == 12000 ||
				sampleRate == 11907 ||
				sampleRate == 11025 ||
				sampleRate == 10000 ||
				sampleRate == 8000) : sampleRate;

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

			if (unk_1C != 0)
				System.out.printf("### %X", unk_1C);
			//	assert(unk_1C == 0) : String.format("%X", unk_1C);
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
				sb.append(String.format(" %6d", data[i]));

			sb.append(" )");
			return sb.toString();
		}
	}
}
