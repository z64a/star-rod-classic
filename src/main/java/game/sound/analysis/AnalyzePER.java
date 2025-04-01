package game.sound.analysis;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;

import app.Directories;
import app.Environment;
import app.input.IOUtils;

public class AnalyzePER
{
	public static void main(String[] args) throws IOException
	{
		Environment.initialize();

		File f = Directories.DUMP_AUDIO_RAW.getFile("SET1.per");
		new AnalyzePER(f);

		Environment.exit();
	}

	private static class Drum
	{
		int bankPatch;
		int keybase;
		int volume;
		int pan;
		int reverb;
		int randTune;
		int randVolume;
		int randPan;
		int randReverb;
	}

	private AnalyzePER(File f) throws IOException
	{
		System.out.println("------------------------------------");
		System.out.println(f.getName() + " ");

		ByteBuffer bb = IOUtils.getDirectBuffer(f);

		String signature = getUTF8(bb, 4);
		assert (signature.equals("PER "));

		int size = bb.getInt();
		String name = getUTF8(bb, 4);

		bb.position(0x10);
		for (int i = 0; i < 6; i++) {
			for (int j = 0; j < 12; j++) {
				Drum drum = new Drum();
				drum.bankPatch = bb.getShort() & 0xFFFF;
				drum.keybase = bb.getShort() & 0xFFFF;
				drum.volume = bb.get() & 0xFF;
				drum.pan = bb.get();
				drum.reverb = bb.get() & 0xFF;
				drum.randTune = bb.get() & 0xFF;
				drum.randVolume = bb.get() & 0xFF;
				drum.randPan = bb.get() & 0xFF;
				drum.randReverb = bb.get() & 0xFF;
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
}
