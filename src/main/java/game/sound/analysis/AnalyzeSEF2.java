package game.sound.analysis;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map.Entry;
import java.util.TreeMap;

import app.Directories;
import app.Environment;
import app.Resource;
import app.Resource.ResourceType;
import app.StarRodException;
import app.input.IOUtils;
import game.sound.engine.SoundBank;
import game.sound.engine.SoundBank.InstrumentQueryResult;
import util.Logger;

public class AnalyzeSEF2
{
	private static final int SEF_RAM = 0x801E2B10; // unused
	private static final String TAB1 = "   ";
	private static final String TAB2 = "      ";

	private static final int SECTION_2K = 8;

	public static boolean unwind = true;

	private static SoundBank soundBank;

	public static void main(String[] args) throws IOException
	{
		Environment.initialize();

		soundBank = new SoundBank();
		new AnalyzeSEF2();

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

	private AnalyzeSEF2() throws IOException
	{
		for (File f : IOUtils.getFilesWithExtension(Directories.DUMP_AUDIO, new String[] { "sef" }, true)) {
			System.out.println("------------------------------------");
			System.out.println(f.getName() + " ");
			ByteBuffer bb = IOUtils.getDirectBuffer(f);
			new SoundArchive(bb);
		}
	}

	private static String getInstrumentName(int bank, int patch)
	{
		InstrumentQueryResult result = soundBank.getInstrument(bank, patch);

		if (result == null)
			return String.format("!!MISSING!! %X, %X", bank, patch); //TODO
		else
			return result.instrument().name;
	}

	private static class SoundArchive
	{
		private ByteBuffer bb;
		private ArrayList<SEFPart> parts;
		private TreeMap<Integer, SEFPart> partMap;
		private HashMap<Integer, String> soundNameMap;

		int[] sections = new int[8];
		int section2000;

		public SoundArchive(ByteBuffer bb) throws IOException
		{
			this.bb = bb;

			soundNameMap = new HashMap<>();

			for (String s : Resource.getText(ResourceType.Basic, "sfx.txt")) {
				String[] line = s.split("\\s+");
				String name = line[1];
				int id = (int) Long.parseLong(line[0], 16);
				soundNameMap.put(id, name);
			}

			parts = new ArrayList<>();
			partMap = new TreeMap<>();

			// read header and section lists

			addPart(new SEFPart(0, 0x22, "Header"));

			bb.position(0x10);

			for (int i = 0; i < 8; i++)
				sections[i] = bb.getShort();
			section2000 = bb.getShort();

			for (int i = 0; i < 8; i++) {
				int len = i < 4 ? 0x300 : 0x100;
				addPart(new SEFPart(sections[i], sections[i] + len, String.format("SoundList%X", i)));
			}
			addPart(new SEFPart(section2000, section2000 + 0x500, "SoundList2000"));

			// decode sounds

			for (int i = 0; i < 4; i++)
				scanLowSection(i, sections[i]);

			for (int i = 4; i < 8; i++)
				scanHighSection(i, sections[i]);

			scanSpecialSection(section2000);

			// print breakdown of SEF file

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

		/**
		 * These can support polyphony and other controls over playback
		 * @param section
		 * @param start
		 */
		private void scanLowSection(int sectionID, int sectionPos)
		{
			for (int index = 0; index < 0xC0; index++) {
				int start = sectionPos + 4 * index;
				int soundID = (sectionID << 8) + (index + 1);

				String name = soundNameMap.get(soundID);
				String id = String.format("[%04X] %X-%02X --> %-3X :", start, sectionID, index, soundID);

				// odd soundIDs:
				// 10E is empty, but not unused. used many times for SOUND_HIT_SILENT!
				// 164 is also empty, but assigned to an unused sound: SOUND_LRAW_NOTHING_26
				// no other empty sounds have definitions assigned

				bb.position(start);
				int code = bb.getShort() & 0xFFFF;
				int info = bb.getShort() & 0xFFFF;

				System.out.println();
				if (code == 0) {
					System.out.printf("%s empty (%s)%n", id, name);
					// NOTE: in many cases info is NOT zero here -- often 87
					continue;
				}
				else if (name == null) {
					System.out.printf("%s NOT NAMED%n", id);
				}
				else {
					System.out.printf("%s (%s)%n", id, name);
				}

				int playerID = (info & 7); // bits 0-2
				// bits 3-4 unused
				int polyphonyMode = (info & 0x60) >> 5; // bits 5-6
				int useSpecificPlayerMode = (info & 0x80) >> 7; // bit 7
				int priority = (info & 0x300) >> 8; // bits 8-9
				// bit 10 unused
				int exclusiveID = (info & 0x1800) >> 11; // bits 11-12
				// bits 13-15 unused

				if (polyphonyMode == 0) {
					readSound(bb, code, name);
				}
				else {
					int numSounds = 2 << (polyphonyMode - 1);
					if (useSpecificPlayerMode == 1) {
						System.out.println("FIXED PLAYER: " + playerID);
					}

					//TODO
					System.out.println("EXC: " + exclusiveID);

					if (useSpecificPlayerMode != 0) {
						for (int j = 0; j < numSounds; j++) {
							System.out.println(TAB1 + "POLY #" + (j + 1));

							bb.position(code + 4 * j);
							int poly = bb.getShort() & 0xFFFF;

							if (poly != 0)
								readSound(bb, poly, name);

							if (start == 0x02F2) {
								System.out.println(":(");
								break; //TODO only sound with problem -- this sound (B5) actually CRASHES the game!
							}
						}
					}
					else {
						for (int j = 0; j < numSounds; j++) {
							System.out.println(TAB1 + "POLY #" + (j + 1));

							bb.position(code + 4 * j);
							int poly = bb.getShort() & 0xFFFF;
							if (poly != 0)
								break; // might be a bug?

							readSound(bb, poly, name);
						}
					}
				}
			}
		}

		private void scanHighSection(int sectionID, int sectionPos)
		{
			for (int index = 0; index < 0x40; index++) {
				int start = sectionPos + 4 * index;

				// must use + instead of | for carry bit when i = 0x3F
				int soundID = ((sectionID - 4) << 8) + (index + 0xC0 + 1);

				String name = soundNameMap.get(soundID);
				String id = String.format("[%04X] %X-%02X --> %-3X :", start, sectionID, index, soundID);

				bb.position(start);
				int a = bb.getShort() & 0xFFFF;
				int b = bb.getShort() & 0xFFFF;

				System.out.println();
				if (a == 0) {
					System.out.printf("%s empty (%s)%n", id, name);
					assert (b == 0);
					continue;
				}
				else if (name == null) {
					System.out.printf("%s NOT NAMED%n", id);
				}
				else {
					System.out.printf("%s (%s)%n", id, name);
				}

				readSound(bb, start, name);
			}
		}

		private void scanSpecialSection(int sectionPos)
		{
			for (int index = 0; index < 0x140; index++) {
				int start = sectionPos + 4 * index;
				int soundID = 0x2000 + (index + 1);

				String name = soundNameMap.get(soundID);
				String id = String.format("[%04X] X-%03X --> %-3X :", start, index, soundID);

				bb.position(start);
				int a = bb.getShort() & 0xFFFF;
				int b = bb.getShort() & 0xFFFF;

				System.out.println();
				if (a == 0) {
					System.out.printf("%s empty (%s)%n", id, name);
					assert (b == 0);
					continue;
				}
				else if (name == null) {
					System.out.printf("%s NOT NAMED%n", id, name);
				}

				int mode = (a >> 8) & 3;
				assert (mode == 2);

				readSound(bb, start, name);
			}
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

			if (paramFlags != 0) {
				System.out.print(TAB1 + "FIXED:");

				if ((paramFlags & 4) != 0)
					System.out.print(" VOLUME");

				if ((paramFlags & 8) != 0)
					System.out.print(" PAN");

				if ((paramFlags & 0x10) != 0)
					System.out.print(" PITCH");

				if ((paramFlags & 0x20) != 0)
					System.out.print(" REVERB");

				System.out.println();
			}

			if (mode == 0) { // SFX_PARAM_MODE_BASIC
				System.out.printf(TAB1 + "BASIC @ %X%n", start);

				int bank = bb.get() & 0xFF;
				int patch = bb.get() & 0xFF;
				int volume = bb.get() & 0xFF;
				int pan = bb.get() & 0xFF;
				int reverb = bb.get() & 0xFF;

				int tuneLerp = 100 * (bb.get() & 0x7F); // semitones --> cents
				int randPitch = (bb.get() & 0xF) << 3;

				System.out.printf(TAB2 + "SetInstrument(%s)%n", getInstrumentName(bank, patch));
				System.out.printf(TAB2 + "SetVolume(%d)%n", volume);
				System.out.printf(TAB2 + "SetPan(%d)%n", pan);
				System.out.printf(TAB2 + "SetReverb(%d)%n", reverb);
				System.out.printf(TAB2 + "TuneLerp(%d)%n", tuneLerp);
				System.out.printf(TAB2 + "RandPitch(%d)%n", randPitch);
			}
			else if (mode == 1) { // SFX_PARAM_MODE_SEQUENCE
				System.out.printf(TAB1 + "SEQUENCE @ %X%n", start);
				new SoundEntry(this, bb, start, name);
				System.out.println();
			}
			else if (mode == 2) { // SFX_PARAM_MODE_COMPACT
				System.out.printf(TAB1 + "COMPACT @ %X%n", start);

				int bank = bb.get() & 0xFF;
				int patch = bb.get() & 0xFF;
				int b4 = bb.get() & 0xFF;

				int volume = (b4 >> 1) | 3; // bottom 3 bits are irrelevant, maps to a range 3 to 127
				int randPitch = b4 & 7;

				System.out.printf(TAB2 + "SetInstrument(%s)%n", getInstrumentName(bank, patch));
				System.out.printf(TAB2 + "SetVolume(%d)%n", volume);
				System.out.printf(TAB2 + "SetPan(%d)%n", 64); // forced
				System.out.printf(TAB2 + "SetReverb(%d)%n", 0); // forced
				System.out.printf(TAB2 + "TuneLerp(%d)%n", 48); // forced (plus 0x80 flag)
				System.out.printf(TAB2 + "RandPitch(%d)%n", randPitch);
			}
			else {
				Logger.logError("Invalid mode: " + mode);
			}
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

			// first byte is params: mode + ignore flags
			int b1 = bb.get() & 0xFF;
			int mode = b1 & 3;
			int paramFlags = b1 & ~3;

			while (true) {
				String prefix = String.format(TAB2 + "[%4X] ", bb.position());
				int op = bb.get() & 0xFF;

				if (op == 0) {
					System.out.print(prefix + "END");
					sef.addPart(new SoundSeq(start, bb.position(), name));
					break;
				}

				if (op < 0x78) {
					System.out.printf(prefix + "DELAY: %d`%n", op);
					continue;
				}

				if (op < 0x80) {
					int arg = bb.get() & 0xFF;
					int delay = (op & 7) * 256 + arg + 0x78; // 0x78 = 120
					System.out.printf(prefix + "DELAY: %d` (long)%n", delay);
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
					System.out.printf(prefix + "PLAY[%d`, %d`, %d`]%n", tuneLerp, velocity, playLen);

					continue;
				}

				if (op < 0xE0) {
					throw new StarRodException("Invalid byte in stream: %2X", op);
				}

				// CmdHandlers
				switch (op - 0xE0) {
					case 0x00: // SetVolume
						System.out.printf(prefix + "SetVolume(%X)%n", bb.get() & 0xFF);
						break;
					case 0x01: // SetPan
						System.out.printf(prefix + "SetPan(%X)%n", bb.get() & 0xFF);
						break;
					case 0x02: // SetInstrument
						System.out.printf(prefix + "SetInstrument(%s)%n", getInstrumentName(bb.get() & 0xFF, bb.get() & 0xFF));
						break;
					case 0x03: // SetReverb
						System.out.printf(prefix + "SetReverb(%X)%n", bb.get() & 0xFF);
						break;
					case 0x04: // SetEnvelope
						System.out.printf(prefix + "SetEnvelope(%X)%n", bb.get() & 0xFF);
						break;
					case 0x05: // CoarseTune
						System.out.printf(prefix + "CoarseTune(%X)%n", bb.get() & 0xFF);
						break;
					case 0x06: // FineTune
						System.out.printf(prefix + "FineTune(%X)%n", bb.get() & 0xFF);
						break;
					case 0x07: // WaitForEnd
						System.out.printf(prefix + "WaitForEnd()%n");
						break;
					case 0x08: // PitchSweep
						System.out.printf(prefix + "PitchSweep(%d`, %X)%n", bb.getShort() & 0xFFFF, bb.get() & 0xFF);
						break;
					case 0x09: // StartLoop
						System.out.printf(prefix + "StartLoop(%X)%n", bb.get() & 0xFF);
						break;
					case 0x0A: // EndLoop
						System.out.printf(prefix + "EndLoop()%n");
						break;
					case 0x0B: // WaitForRelease
						System.out.printf(prefix + "WaitForRelease()%n");
						break;
					case 0x0C: // SetCurrentVolume
						System.out.printf(prefix + "SetCurrentVolume(%X)%n", bb.get() & 0xFF);
						break;
					case 0x0D: // VolumeRamp
						System.out.printf(prefix + "VolumeRamp(%d`, %X)%n", bb.getShort() & 0xFFFF, bb.get() & 0xFF);
						break;
					case 0x0E: // SetAlternativeSound
						System.out.printf(prefix + "SetAlternativeSound(%X, %X)%n", bb.get() & 0xFF, bb.getShort() & 0xFFFF);
						break;
					case 0x0F: // Stop
						System.out.printf(prefix + "Stop()%n");
						break;
					case 0x10: // Jump
						int dest = bb.getShort() & 0xFFFF;
						System.out.printf(prefix + "Jump(%X)%n", dest);
						sef.addPart(new SoundSeq(start, bb.position(), name));

						if (!unwind && sef.isCovered(dest)) {
							return;
						}

						start = dest;
						bb.position(start);
						break;
					case 0x11: // Restart
						//	bb.position(start);
						System.out.printf(prefix + "Restart()%n");
						return; // also terminates?
					//break;
					case 0x12: // NOP
						System.out.println(prefix + "NOP()");
						break;
					case 0x13: // SetRandomPitch
						System.out.printf(prefix + "SetRandomPitch(%X)%n", bb.get() & 0xFF);
						break;
					case 0x14: // SetRandomVelocity
						System.out.printf(prefix + "SetRandomVelocity(%X)%n", bb.get() & 0xFF);
						break;
					case 0x15: // SetUnkA3
						System.out.printf(prefix + "SetUnkA3(%X)%n", bb.get() & 0xFF);
						break;
					case 0x16: // SetEnvelopePress
						System.out.printf(prefix + "SetEnvelopePress(%X)%n", bb.getShort() & 0xFFFF);
						break;
					case 0x17: // PlaySound
						System.out.printf(prefix + "PlaySound(%X, %X)%n", bb.getShort() & 0xFFFF, bb.getShort() & 0xFFFF);
						break;
					case 0x18: // SetAlternativeVolume
						System.out.printf(prefix + "SetAlternativeVolume(%X)%n", bb.get() & 0xFF);
						break;
				}
			}
		}
	}
}
