package game.sound.analysis;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Collection;

import app.Directories;
import app.Environment;
import app.input.IOUtils;

public class AnalyzeMSEQ
{
	public static void main(String[] args) throws IOException
	{
		Environment.initialize();

		Collection<File> files = IOUtils.getFilesWithExtension(Directories.DUMP_AUDIO, new String[] { "mseq" }, true);
		for (File f : files) {
			new AnalyzeMSEQ(f);
			// break; // only do the first
		}
		Environment.exit();
	}

	private static class TrackSettings
	{
		private final int trackIdx;
		private final int type; // 0 = tune, 1 = volume
		private final int time;
		private final int delta;
		private final int goal;

		public TrackSettings(ByteBuffer bb)
		{
			trackIdx = bb.get() & 0xFF;
			type = bb.get() & 0xFF;
			time = bb.getShort();
			delta = bb.getShort();
			goal = bb.getShort();
		}
	}

	private AnalyzeMSEQ(File f) throws IOException
	{
		System.out.println("------------------------------------");
		System.out.println(f.getName() + " ");

		ByteBuffer bb = IOUtils.getDirectBuffer(f);

		String signature = getUTF8(bb, 4);
		assert (signature.equals("MSEQ"));

		int size = bb.getInt();
		String name = getUTF8(bb, 4);

		int firstVoice = bb.get() & 0xFF;
		int numSettings = bb.get() & 0xFF;
		int settingsOffset = bb.getShort() & 0xFFFF;
		int streamOffset = bb.getShort() & 0xFFFF;

		System.out.printf("SETTINGS: %X%n", settingsOffset);
		bb.position(settingsOffset);
		TrackSettings[] settings = new TrackSettings[numSettings];

		for (int i = 0; i < numSettings; i++) {
			settings[i] = new TrackSettings(bb);

			switch (settings[i].type) {
				case 0:
					System.out.printf("[%d] PITCH LERP:  %3d %6d %5X%n", settings[i].trackIdx,
						settings[i].time, settings[i].delta, settings[i].goal);
					break;
				case 1:
					System.out.printf("[%d] VOLUME LERP: %3d %6d %5X%n", settings[i].trackIdx,
						settings[i].time, settings[i].delta, settings[i].goal);
					break;
			}
		}

		System.out.printf("STREAM: %X%n", streamOffset);
		bb.position(streamOffset);

		boolean done = false;
		while (!done) {
			byte op = bb.get();
			if (op >= 0) {
				if (op == 0) {
					System.out.println("STREAM END");
					// DONE
					break;
				}
				if (op >= 0x78) {
					int delay = ((op & 7) << 8) + bb.get() + 0x78;
					System.out.printf("|||||||||| DELAY: %d (L)%n", delay);
				}
				else {
					System.out.printf("|||||||||| DELAY: %d%n", op);
				}
			}
			else {
				int cmd = (op >> 4) & 0xF;
				int trackIdx = op & 0xF;
				int arg = bb.get() & 0xFF;

				// track 9 is DRUM

				final int DRUM_TRACK = 9;

			// @formatter:off
				String trackInfo = "xxxxxxxxx";
				switch (trackIdx) {
					case 0: trackInfo = "#|||||||||"; break;
					case 1: trackInfo = "|#||||||||"; break;
					case 2: trackInfo = "||#|||||||"; break;
					case 3: trackInfo = "|||#||||||"; break;
					case 4: trackInfo = "||||#|||||"; break;
					case 5: trackInfo = "|||||#||||"; break;
					case 6: trackInfo = "||||||#|||"; break;
					case 7: trackInfo = "|||||||#||"; break;
					case 8: trackInfo = "||||||||#|"; break;
					case 9: trackInfo = "|||||||||#"; break;
				}
				// @formatter:on

				switch (cmd) {
					case 0x8: // MSEQ_CMD_80_STOP_SOUND (pitch)
						System.out.printf("%s STOP SOUND: %X%n", trackInfo, arg);
						break;
					case 0x9: // MSEQ_CMD_90_PLAY_SOUND (pitch, vol) or (drumID from dataPER, vol)
						int vol = bb.get() & 0xFF;
						if (trackIdx == DRUM_TRACK)
							System.out.printf("%s PLAY DRUM: %X @ %X%n", trackInfo, arg, vol);
						else
							System.out.printf("%s PLAY SOUND: %X @ %X%n", trackInfo, arg, vol);
						break;
					case 0xA: // MSEQ_CMD_A0_SET_VOLUME_PAN
						if ((arg & 0x80) != 0) {
							System.out.printf("%s SET PAN: %X%n", trackInfo, arg & 0x7F);
						}
						else {
							System.out.printf("%s SET VOL: %X%n", trackInfo, arg);
						}
						break;
					case 0xB: // MSEQ_CMD_B0_MULTI
						int arg2 = bb.get() & 0xFF;
						switch (arg) {
							case 0x66: // MSEQ_CMD_SUB_66_START_LOOP
								System.out.printf("---------- START LOOP %d%n", arg2 & 1);
								break;
							case 0x67: // MSEQ_CMD_SUB_67_END_LOOP
								int count = (arg2 & 0x7C) >> 2;
								if (count == 0)
									System.out.printf("---------- END LOOP %d (forever)%n", arg2 & 1);
								else
									System.out.printf("---------- END LOOP %d (x %d)%n", arg2 & 1, count);
								break;
							case 0x68: // MSEQ_CMD_SUB_68_SET_REVERB
								System.out.printf("%s SET REVERB: %X%n", trackInfo, arg2);
								break;
							case 0x69: // MSEQ_CMD_SUB_69_SET_RESUMABLE
								System.out.printf("%s SET RESUMABLE: %X%n", trackInfo, arg2 & 1);
								break;
						}
						break;
					case 0xC: // MSEQ_CMD_C0_SET_INSTRUMENT (bank, patch)
						int patch = bb.get() & 0xFF;
						System.out.printf("%s SET INS: %X : %X%n", trackInfo, arg, patch);
						break;
					case 0xE: // MSEQ_CMD_E0_TUNING (coarse, fine)
						int fine = bb.get() & 0xFF;
						System.out.printf("%s TUNE: %X%n", trackInfo, (arg << 8 | fine));
						break;
				}
				/*
				MSEQ_CMD_SUB_66_START_LOOP      = 0x66,
				MSEQ_CMD_SUB_67_END_LOOP        = 0x67,
				MSEQ_CMD_SUB_68_SET_REVERB      = 0x68,
				MSEQ_CMD_SUB_69_SET_RESUMABLE   = 0x69,
				*/
			}
		}

	}

	private String getUTF8(ByteBuffer bb, int len)
	{
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < len; i++)
			sb.append((char) bb.get());
		return sb.toString();
	}

	/*
	  typedef struct MSEQHeader {
	/ 0x00 / s32 signature; // 'MSEQ '
	/ 0x04 / s32 size; // including header
	/ 0x08 / s32 name;
	/ 0x0C / u8 firstVoiceIdx;
	/ 0x0D / u8 trackSettingsCount;
	/ 0x0E / u16 trackSettingsOffset;
	/ 0x10 / u16 dataStart;
	} MSEQHeader; // size variable
	 */

}
