package game.sound;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;

import app.Directories;
import app.Environment;
import app.Resource;
import app.Resource.ResourceType;
import app.StarRodException;
import app.input.IOUtils;

public class AudioAnalyzerSEF
{
	public static void main(String[] args) throws IOException
	{
		Environment.initialize();
		new AudioAnalyzerSEF();
		Environment.exit();
	}

	private AudioAnalyzerSEF() throws IOException
	{
		for (File f : IOUtils.getFilesWithExtension(Directories.DUMP_AUDIO, new String[] { "sef" }, true)) {
			System.out.println("------------------------------------");
			System.out.println(f.getName() + " ");
			ByteBuffer bb = IOUtils.getDirectBuffer(f);
			new SoundArchive(bb);
		}
	}

	private static class SoundArchive
	{
		ByteBuffer bb;
		ArrayList<SEFPart> parts;
		HashMap<Integer, SEFPart> partMap;

		int[] sections = new int[8];
		int section2000;

		public SoundArchive(ByteBuffer bb)
		{
			this.bb = bb;
			parts = new ArrayList<>();
			partMap = new HashMap<>();

			addPart(new SEFPart(0, 0x22, "Header"));

			bb.position(0xC);
			assert (bb.get() == 0);
			assert (bb.get() == 0);
			assert (bb.get() == 1);
			assert (bb.get() == 0);

			for (int i = 0; i < 8; i++)
				sections[i] = bb.getShort();
			section2000 = bb.getShort();
			assert (section2000 != 0);

			for (int i = 0; i < 7; i++) {
				System.out.printf("%d : %X - %X = %X (%X)%n", i, sections[i], sections[i + 1],
					sections[i + 1] - sections[i], (sections[i + 1] - sections[i]) / 4);
			}
			System.out.printf("%d : %X - %X = %X%n", 7, sections[7], section2000, section2000 - sections[7]);

			for (int i = 0; i < 8; i++)
				addPart(new SEFPart(sections[i], -1, String.format("SoundList%X", i)));
			addPart(new SEFPart(section2000, -1, "SoundList2000"));

			decodeIDs();

			int last = 0;
			Collections.sort(parts);
			for (SEFPart part : parts) {
				System.out.println(part);

				// assert(last == -1 || last == part.start);
				last = part.end;
			}
			System.out.printf("FILE END: %X%n", bb.capacity());
			System.out.println();

			addPart(new SoundSeq(0x628, -1, "Test"));
			((SoundSeq) partMap.get(0x628)).print(bb);
			// ((SoundSeq)partMap.get(0x3240)).print(bb);
		}

		private void addPart(SEFPart part)
		{
			if (!partMap.containsKey(part.start)) {
				parts.add(part);
				partMap.put(part.start, part);
			}
		}

		private SEFPart getPart(int offset)
		{
			return partMap.get(offset);
		}

		private void decodeIDs()
		{
			for (String s : Resource.getText(ResourceType.Basic, "sfx.txt")) {
				String[] line = s.split("\\s+");
				String name = line[1];
				int id = (int) Long.parseLong(line[0], 16);

				if ((id & 0x2000) != 0) {

				}
				else {
					int section;
					int index = (id - 1) & 0xFF;

					int offset;
					if (index < 0xC0) {
						section = (id >> 8) & 0x3;
						offset = sections[section] + index * 4; // 8004BC40
					}
					else {
						index -= 0xC0;
						section = 4 + (((id - 1) >> 8) & 0x3);
						offset = sections[section] + index * 4; // 8004bb94
					}

					bb.position(offset);
					int data = bb.getShort();
					int flags = bb.getShort();

					// read at 8004c6a4

					addPart(new SoundSeq(data, -1, name));

					bb.position(data);
					int bank = bb.get() & 0xFF;
					int ins = bb.get() & 0xFF;
					int volume = bb.get() & 0xFF;
					int pan = bb.get() & 0xFF;
					int reverb = bb.get() & 0xFF;
					int A1 = bb.get() & 0xFF;

					System.out.printf("%04X  %X-%02X --> %4X %4X (%02X %02X : %02X %02X %02X : %02X ) %s%n",
						id, section, index, offset, data,
						bank, ins, volume, pan, reverb, A1, name);
				}
			}
		}
	}

	private static final int SEF_RAM = 0x801E2B10;

	private static class SEFPart implements Comparable<SEFPart>
	{
		final String name;
		int start;
		int end;

		public SEFPart(int start, int end, String name)
		{
			this.start = start;
			this.end = end;
			this.name = name;
		}

		@Override
		public int compareTo(SEFPart o)
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

	private static class SoundList extends SEFPart
	{
		public SoundList(int start, int end, String name)
		{
			super(start, end, name);
		}
	}

	private static class SoundSeq extends SEFPart
	{
		public SoundSeq(int start, int end, String name)
		{
			super(start, end, "Seq:" + name);
		}

		public void print(ByteBuffer bb)
		{
			bb.position(start);

			/*
			 * System.out.println("Init:"); System.out.printf("SetInstrument(%X, %X)%n", bb.get() & 0xFF, bb.get() & 0xFF);
			 * System.out.printf("SetVolume(%X)%n", bb.get() & 0xFF); System.out.printf("SetPan(%X)%n", bb.get() & 0xFF);
			 * System.out.printf("SetReverb(%X)%n", bb.get() & 0xFF); System.out.printf("????(%X, %X)%n", bb.get() & 0xFF, bb.get()
			 * & 0xFF); // System.out.printf("????(%X)%n", bb.get() & 0xFF);
			 */

			/*
			 * Commands: PLAY[5900`, 47`, 120`] DELAY: 80` PitchSweep(225`, AE) DELAY: 225` (long) EndLoop() VolumeRamp(150`, F)
			 * PitchSweep(150`, AC) DELAY: 150` (long) END
			 */

			System.out.println("Commands:");
			while (true) {
				int op = bb.get() & 0xFF;
				if (op == 0) {
					System.out.print("END");
					break;
				}

				if (op < 0x78) {
					System.out.printf("DELAY: %d`%n", op);
					continue;
				}

				if (op < 0x80) {
					int arg = bb.get() & 0xFF;
					int delay = (op & 7) * 256 + arg + 0x78; // 0x78 = 120
					System.out.printf("DELAY: %d` (long)%n", delay);
					continue;
				}

				if (op < 0xD8) {
					// set params...

					int tuneLerp = (op & 0x7F) * 100;

					int velocity = bb.get() & 0x7F;

					int playLen = bb.get() & 0xFF;
					if (playLen >= 0xC0) {
						int lenExt = bb.get() & 0xFF;
						playLen = ((playLen & 0x3F) << 8) + 0xC0 + lenExt;
					}
					System.out.printf("PLAY[%d`, %d`, %d`]%n", tuneLerp, velocity, playLen);

					continue;
				}

				if (op < 0xE0) {
					throw new StarRodException("Invalid byte in stream: %2X", op);
				}

				// CmdHandlers
				switch (op - 0xE0) {
					case 0x00: // SetVolume
						System.out.printf("SetVolume(%X)%n", bb.get() & 0xFF);
						break;
					case 0x01: // SetPan
						System.out.printf("SetPan(%X)%n", bb.get() & 0xFF);
						break;
					case 0x02: // SetInstrument
						System.out.printf("SetInstrument(%X, %X)%n", bb.get() & 0xFF, bb.get() & 0xFF);
						break;
					case 0x03: // SetReverb
						System.out.printf("SetReverb(%X)%n", bb.get() & 0xFF);
						break;
					case 0x04: // SetEnvelope
						System.out.printf("SetEnvelope(%X)%n", bb.get() & 0xFF);
						break;
					case 0x05: // CoarseTune
						System.out.printf("CoarseTune(%X)%n", bb.get() & 0xFF);
						break;
					case 0x06: // FineTune
						System.out.printf("FineTune(%X)%n", bb.get() & 0xFF);
						break;
					case 0x07: // WaitForEnd
						System.out.printf("WaitForEnd()%n");
						break;
					case 0x08: // PitchSweep
						System.out.printf("PitchSweep(%d`, %X)%n", bb.getShort() & 0xFFFF, bb.get() & 0xFF);
						break;
					case 0x09: // StartLoop
						System.out.printf("StartLoop(%X)%n", bb.get() & 0xFF);
						break;
					case 0x0A: // EndLoop
						System.out.printf("EndLoop()%n");
						break;
					case 0x0B: // WaitForRelease
						System.out.printf("WaitForRelease()%n");
						break;
					case 0x0C: // SetCurrentVolume
						System.out.printf("SetCurrentVolume(%X)%n", bb.get() & 0xFF);
						break;
					case 0x0D: // VolumeRamp
						System.out.printf("VolumeRamp(%d`, %X)%n", bb.getShort() & 0xFFFF, bb.get() & 0xFF);
						break;
					case 0x0E: // SetAlternativeSound
						System.out.printf("SetAlternativeSound(%X, %X)%n", bb.get() & 0xFF, bb.getShort() & 0xFFFF);
						break;
					case 0x0F: // Stop
						System.out.printf("Stop()%n");
						break;
					case 0x10: // Jump
						System.out.printf("Jump(%X)%n", bb.getShort() & 0xFFFF);
						break;
					case 0x11: // Restart
						System.out.printf("Jump()%n");
						break;
					case 0x12: // NOP
						break;
					case 0x13: // SetRandomPitch
						System.out.printf("SetRandomPitch(%X)%n", bb.get() & 0xFF);
						break;
					case 0x14: // SetRandomVelocity
						System.out.printf("SetRandomVelocity(%X)%n", bb.get() & 0xFF);
						break;
					case 0x15: // SetUnkA3
						System.out.printf("SetUnkA3(%X)%n", bb.get() & 0xFF);
						break;
					case 0x16: // SetEnvelopePress
						System.out.printf("SetEnvelopePress(%X)%n", bb.getShort() & 0xFFFF);
						break;
					case 0x17: // PlaySound
						System.out.printf("PlaySound(%X, %X)%n", bb.getShort() & 0xFFFF, bb.getShort() & 0xFFFF);
						break;
					case 0x18: // SetAlternativeVolume
						System.out.printf("SetAlternativeVolume(%X)%n", bb.get() & 0xFF);
						break;
				}
			}
		}
	}
}
