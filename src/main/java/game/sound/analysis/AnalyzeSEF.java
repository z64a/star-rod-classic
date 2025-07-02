package game.sound.analysis;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map.Entry;
import java.util.TreeMap;

import app.Directories;
import app.Environment;
import app.Resource;
import app.Resource.ResourceType;
import app.input.IOUtils;
import util.Logger;

public class AnalyzeSEF
{
	private static final int SEF_RAM = 0x801E2B10; // unused
	private static final String TAB = "\t";

	public static void main(String[] args) throws IOException
	{
		Environment.initialize();
		new AnalyzeSEF();
		Environment.exit();
	}

	// section sizes:
	// SoundList0  0x300 bytes --> 4 bytes per 0xC0 sound IDs
	// SoundList1  0x300 bytes --> 4 bytes per 0xC0 sound IDs
	// SoundList2  0x300 bytes --> 4 bytes per 0xC0 sound IDs
	// SoundList3  0x300 bytes --> 4 bytes per 0xC0 sound IDs
	// SoundList4  0x100 bytes --> 4 bytes per 0x40 sound IDs
	// SoundList5  0x100 bytes --> 4 bytes per 0x40 sound IDs
	// SoundList6  0x100 bytes --> 4 bytes per 0x40 sound IDs
	// SoundList7  0x100 bytes --> 4 bytes per 0x40 sound IDs
	// SoundList2000  0x500 bytes --> 4 bytes per max of 0x140 sound IDs

	// these sections fill in the order of 0, 4, 1, 5, 2, 6, 3, 7

	// engine has maximum of 0x140 sounds in section 2000,
	// only IDs up to 0x12E are used however

	private AnalyzeSEF() throws IOException
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
		TreeMap<Integer, SEFPart> partMap;

		int[] sections = new int[8];
		int section2000;

		public SoundArchive(ByteBuffer bb)
		{
			this.bb = bb;
			parts = new ArrayList<>();
			partMap = new TreeMap<>();

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

			for (int i = 0; i < 8; i++) {
				int len = i < 4 ? 0x300 : 0x100;
				addPart(new SEFPart(sections[i], sections[i] + len, String.format("SoundList%X", i)));
			}
			addPart(new SEFPart(section2000, section2000 + 0x500, "SoundList2000"));

			decodeIDs();

			int last = 0;
			Collections.sort(parts);
			for (SEFPart part : parts) {
				if (last != part.start)
					System.out.printf("%-5X %-5X (%X) --- GAP ---%n", last, part.start, part.start - last);
				System.out.println(part);

				// assert(last == -1 || last == part.start);
				last = part.end;
			}
			System.out.printf("FILE END: %X%n", bb.capacity());
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

		private boolean isCovered(int offset)
		{
			Entry<Integer, SEFPart> e = partMap.floorEntry(offset);
			if (e == null)
				return false;

			SEFPart p = e.getValue();
			return (offset >= p.start && offset < p.end);
		}

		private void decodeIDs()
		{
			for (String s : Resource.getText(ResourceType.Basic, "sfx.txt")) {
				String[] line = s.split("\\s+");
				String name = line[1];
				int id = (int) Long.parseLong(line[0], 16);
				int offset, section, index;

				if ((id & 0x2000) != 0) {
					section = 9;
					index = id & 0x1FF;
					offset = section2000 + index * 4;

					// each entry is just sound bytecode
					System.out.printf("%04X --> %X-%02X --> %s%n", id, section, index, name);
					readSound(bb, offset, name);
				}
				else {
					index = (id - 1) & 0xFF;

					if (index < 0xC0) {
						section = (id >> 8) & 0x3;
						offset = sections[section] + index * 4; // 8004BC40

						bb.position(offset);
						int data = bb.getShort();
						int info = bb.getShort();

						int playerID = (info & 7); // bits 0-2
						// bits 3-4 unused
						int polyphonyMode = (info & 0x60) >> 5; // bits 5-6
						int useSpecificPlayerMode = (info & 0x80) >> 7; // bit 7
						int priority = (info & 0x300) >> 8; // bits 8-9
						// bit 10 unused
						int exclusiveID = (info & 0x1800) >> 11; // bits 11-12
						// bits 13-15 unused

						if (polyphonyMode != 0) {

							System.out.printf("%04X --> %X-%02X --> %s%n", id, section, index, name);

							int numSounds = 2 << (polyphonyMode - 1);
							for (int i = 0; i < numSounds; i++) {
								System.out.println("POLY #" + (i + 1));

								bb.position(data + 4 * i);
								int poly = bb.getShort();

								readSound(bb, poly, name);
							}
						}
						else {
							System.out.printf("%04X --> %X-%02X --> %s%n", id, section, index, name);
							readSound(bb, data, name);
						}

					}
					else {
						index -= 0xC0;
						section = 4 + (((id - 1) >> 8) & 0x3);
						offset = sections[section] + index * 4; // 8004bb94

						// each entry is just sound bytecode
						System.out.printf("%04X --> %X-%02X --> %s%n", id, section, index, name);
						readSound(bb, offset, name);
					}

					/*
					bb.position(offset);
					if (oneshot) {
					
						int b1 = bb.get() & 0xFF;
						int bank = bb.get() & 0xFF;
						int patch = bb.get() & 0xFF;
						int b4 = bb.get() & 0xFF;
					
						int mode = b1 & 3;
						int paramFlags = b1 & ~3;
					
						// use param flags to make certain params ignore api func settings
						// SFX_PARAM_FLAG_VOLUME        = 04
						// SFX_PARAM_FLAG_PAN           = 08
						// SFX_PARAM_FLAG_PITCH         = 10
						// SFX_PARAM_FLAG_FIXED_REVERB  = 20
					
						assert (mode == 2);
					
						int randVol = (b4 >> 1) | 3; // bottom 3 bits are irrelevant, maps to a range 3 to 127
						int randPitch = b4 & 7;
					
						System.out.printf("%04X  %X-%02X --> %4X (%02X %02X : %02X %02X %02X ) %s%n",
							id, section, index, offset,
							bank, patch, randVol, randPitch, paramFlags, name);
					}
					else {
						int data = bb.getShort();
						int info = bb.getShort();
					
						int playerID = (info & 7); // bits 0-2
						// bits 3-4 unused
						int polyphonyMode = (info & 0x60) >> 5; // bits 5-6
						int useSpecificPlayerMode = (info & 0x80) >> 7; // bit 7
						int priority = (info & 0x300) >> 8; // bits 8-9
						// bit 10 unused
						int exclusiveID = (info & 0x1800) >> 11; // bits 11-12
						// bits 13-15 unused
					
						readSound(bb, data, name, id, section, index);
					}
					*/
				}
			}
		}

		private void readSound(ByteBuffer bb, int start, String name)
		{
			bb.position(start);

			int b1 = bb.get() & 0xFF;
			int mode = b1 & 3;
			int paramFlags = b1 & ~3;

			// use param flags to make certain params ignore api func settings
			// SFX_PARAM_FLAG_VOLUME        = 04
			// SFX_PARAM_FLAG_PAN           = 08
			// SFX_PARAM_FLAG_PITCH         = 10
			// SFX_PARAM_FLAG_FIXED_REVERB  = 20

			if (mode == 0) { // SFX_PARAM_MODE_BASIC
				System.out.printf("BASIC @ %X%n", start);

				int bank = bb.get() & 0xFF;
				int patch = bb.get() & 0xFF;
				int volume = bb.get() & 0xFF;
				int pan = bb.get() & 0xFF;
				int reverb = bb.get() & 0xFF;

				int tuneLerp = 100 * (bb.get() & 0x7F); // semitones --> cents
				int randPitch = (bb.get() & 0xF) << 3;

				System.out.printf(TAB + "SetInstrument(%X, %X)%n", bank, patch);
				System.out.printf(TAB + "SetVolume(%d)%n", volume);
				System.out.printf(TAB + "SetPan(%d)%n", pan);
				System.out.printf(TAB + "SetReverb(%d)%n", reverb);
				System.out.printf(TAB + "TuneLerp(%d)%n", tuneLerp);
				System.out.printf(TAB + "RandPitch(%d)%n", randPitch);
			}
			else if (mode == 1) { // SFX_PARAM_MODE_SEQUENCE
				System.out.printf("SEQUENCE @ %X%n", start);
				new SoundEntry(this, bb, start, name);
			}
			else if (mode == 2) { // SFX_PARAM_MODE_COMPACT
				System.out.printf("COMPACT @ %X%n", start);

				int bank = bb.get() & 0xFF;
				int patch = bb.get() & 0xFF;
				int b4 = bb.get() & 0xFF;

				int volume = (b4 >> 1) | 3; // bottom 3 bits are irrelevant, maps to a range 3 to 127
				int randPitch = b4 & 7;

				System.out.printf(TAB + "SetInstrument(%X, %X)%n", bank, patch);
				System.out.printf(TAB + "SetVolume(%d)%n", volume);
				System.out.printf(TAB + "SetPan(%d)%n", 64); // forced
				System.out.printf(TAB + "SetReverb(%d)%n", 0); // forced
				System.out.printf(TAB + "TuneLerp(%d)%n", 48); // forced (plus 0x80 flag)
				System.out.printf(TAB + "RandPitch(%d)%n", randPitch);

				System.out.println();
			}
			else {
				Logger.logError("Invalid mode: " + mode);
			}

			System.out.println();
		}
	}

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
	}

	private static class SoundEntry
	{
		public SoundEntry(SoundArchive sef, ByteBuffer bb, int start, String name)
		{
			bb.position(start);

			int b1 = bb.get() & 0xFF;
			int mode = b1 & 3;
			int paramFlags = b1 & ~3;

			while (true) {
				int op = bb.get() & 0xFF;
				if (op == 0) {
					System.out.print(TAB + "END");
					sef.addPart(new SoundSeq(start, bb.position(), name));
					break;
				}

				if (op < 0x78) {
					System.out.printf(TAB + "DELAY: %d`%n", op);
					continue;
				}

				if (op < 0x80) {
					int arg = bb.get() & 0xFF;
					int delay = (op & 7) * 256 + arg + 0x78; // 0x78 = 120
					System.out.printf(TAB + "DELAY: %d` (long)%n", delay);
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
					System.out.printf(TAB + "PLAY[%d`, %d`, %d`]%n", tuneLerp, velocity, playLen);

					continue;
				}

				if (op < 0xE0) {
					//TODO throw new StarRodException("Invalid byte in stream: %2X", op);

					Logger.logfError("Invalid byte in stream: %2X", op);
					continue;
				}

				// CmdHandlers
				switch (op - 0xE0) {
					case 0x00: // SetVolume
						System.out.printf(TAB + "SetVolume(%X)%n", bb.get() & 0xFF);
						break;
					case 0x01: // SetPan
						System.out.printf(TAB + "SetPan(%X)%n", bb.get() & 0xFF);
						break;
					case 0x02: // SetInstrument
						System.out.printf(TAB + "SetInstrument(%X, %X)%n", bb.get() & 0xFF, bb.get() & 0xFF);
						break;
					case 0x03: // SetReverb
						System.out.printf(TAB + "SetReverb(%X)%n", bb.get() & 0xFF);
						break;
					case 0x04: // SetEnvelope
						System.out.printf(TAB + "SetEnvelope(%X)%n", bb.get() & 0xFF);
						break;
					case 0x05: // CoarseTune
						System.out.printf(TAB + "CoarseTune(%X)%n", bb.get() & 0xFF);
						break;
					case 0x06: // FineTune
						System.out.printf(TAB + "FineTune(%X)%n", bb.get() & 0xFF);
						break;
					case 0x07: // WaitForEnd
						System.out.printf(TAB + "WaitForEnd()%n");
						break;
					case 0x08: // PitchSweep
						System.out.printf(TAB + "PitchSweep(%d`, %X)%n", bb.getShort() & 0xFFFF, bb.get() & 0xFF);
						break;
					case 0x09: // StartLoop
						System.out.printf(TAB + "StartLoop(%X)%n", bb.get() & 0xFF);
						break;
					case 0x0A: // EndLoop
						System.out.printf(TAB + "EndLoop()%n");
						break;
					case 0x0B: // WaitForRelease
						System.out.printf(TAB + "WaitForRelease()%n");
						break;
					case 0x0C: // SetCurrentVolume
						System.out.printf(TAB + "SetCurrentVolume(%X)%n", bb.get() & 0xFF);
						break;
					case 0x0D: // VolumeRamp
						System.out.printf(TAB + "VolumeRamp(%d`, %X)%n", bb.getShort() & 0xFFFF, bb.get() & 0xFF);
						break;
					case 0x0E: // SetAlternativeSound
						System.out.printf(TAB + "SetAlternativeSound(%X, %X)%n", bb.get() & 0xFF, bb.getShort() & 0xFFFF);
						break;
					case 0x0F: // Stop
						System.out.printf(TAB + "Stop()%n");
						break;
					case 0x10: // Jump
						int dest = bb.getShort() & 0xFFFF;
						System.out.printf(TAB + "Jump(%X)%n", dest);
						sef.addPart(new SoundSeq(start, bb.position(), name));

						if (sef.isCovered(dest)) {
							return;
						}

						start = dest;
						bb.position(start);
						break;
					case 0x11: // Restart
						//	bb.position(start);
						System.out.printf(TAB + "Restart()%n");
						break;
					case 0x12: // NOP
						break;
					case 0x13: // SetRandomPitch
						System.out.printf(TAB + "SetRandomPitch(%X)%n", bb.get() & 0xFF);
						break;
					case 0x14: // SetRandomVelocity
						System.out.printf(TAB + "SetRandomVelocity(%X)%n", bb.get() & 0xFF);
						break;
					case 0x15: // SetUnkA3
						System.out.printf(TAB + "SetUnkA3(%X)%n", bb.get() & 0xFF);
						break;
					case 0x16: // SetEnvelopePress
						System.out.printf(TAB + "SetEnvelopePress(%X)%n", bb.getShort() & 0xFFFF);
						break;
					case 0x17: // PlaySound
						System.out.printf(TAB + "PlaySound(%X, %X)%n", bb.getShort() & 0xFFFF, bb.getShort() & 0xFFFF);
						break;
					case 0x18: // SetAlternativeVolume
						System.out.printf(TAB + "SetAlternativeVolume(%X)%n", bb.get() & 0xFF);
						break;
				}
			}
		}
	}
}
