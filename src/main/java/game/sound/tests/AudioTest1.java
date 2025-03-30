package game.sound.tests;

import static app.Directories.DUMP_AUDIO_BANK;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;

import app.Environment;

public class AudioTest1
{
	private static final int TARGET_FPS = 60;
	private static final long FRAME_TIME_MS = 1000 / TARGET_FPS;
	private static float pitch = 1.0f;
	private static float volume = 1.0f;

	public static void main(String[] args) throws Exception
	{
		Environment.initialize();

		File audioFile = DUMP_AUDIO_BANK.getFile("STG5/STG5_06.wav");
		Sound wavData = new Sound(audioFile);

		AudioFormat format = new AudioFormat(wavData.sampleRate, 16, 1, true, false);
		SourceDataLine line = AudioSystem.getSourceDataLine(format);
		line.open();
		line.start();

		final int chunkSize = 1024;
		byte[] buffer = new byte[chunkSize * 2];
		float[] output = new float[chunkSize];

		float readPos = 0.0f;
		long lastTime = System.nanoTime();

		while ((int) readPos + 1 < wavData.samples.size()) {
			long now = System.nanoTime();
			float deltaTime = (now - lastTime) / 1_000_000_000f;
			lastTime = now;

			float pitch = 1.0f + (float) Math.sin(now / 1e9) * 0.1f;
			float volume = 1.0f;// 0.5f + (float) Math.sin(now / 2e9) * 0.5f;

			// clamp pitch ratio to MAX_RATIO
			if (pitch > 1.99996f) {
				pitch = 1.99996f;
			}

			// n64 mircocode uses linear resampling, so that's what we'll use
			int outputIndex = 0;
			while (outputIndex < chunkSize && (int) readPos + 1 < wavData.samples.size()) {
				int i0 = (int) readPos;
				int i1 = i0 + 1;
				float frac = readPos - i0;

				float s0 = wavData.samples.get(i0);
				float s1 = wavData.samples.get(i1);
				float interp = (1 - frac) * s0 + frac * s1;

				output[outputIndex++] = interp * volume;
				readPos += pitch;
			}

			// convert to PCM and output
			for (int i = 0; i < outputIndex; i++) {
				short s = (short) (output[i] * 32767.0f);
				buffer[2 * i] = (byte) s;
				buffer[2 * i + 1] = (byte) (s >>> 8);
			}
			line.write(buffer, 0, outputIndex * 2);

			// 60 hz frame limiter
			long frameTime = 1000 / 60;
			long sleep = frameTime - (System.nanoTime() - now) / 1_000_000;
			if (sleep > 0)
				Thread.sleep(sleep);
		}

		line.drain();
		line.stop();
		line.close();

		Environment.exit();
	}

	private static class Sound
	{
		public final List<Float> samples;
		public final int sampleRate;

		public Sound(File wavFile) throws Exception
		{
			try (AudioInputStream audioInputStream = AudioSystem.getAudioInputStream(wavFile)) {

				AudioFormat format = audioInputStream.getFormat();

				// require 16-bit PCM samples
				if (format.getEncoding() != AudioFormat.Encoding.PCM_SIGNED ||
					format.getSampleSizeInBits() != 16) {
					throw new IllegalArgumentException("Only 16-bit signed PCM WAV files are supported.");
				}

				sampleRate = Math.round(format.getSampleRate());

				byte[] audioBytes = audioInputStream.readAllBytes();
				samples = new ArrayList<>(audioBytes.length / 2);

				// WAV files are conventionally little-endian, but we can support flexible endianness
				boolean bigEndian = format.isBigEndian();

				for (int i = 0; i < audioBytes.length; i += 2) {
					int low = audioBytes[i] & 0xFF;
					int high = audioBytes[i + 1] & 0xFF;
					short sample = bigEndian
						? (short) ((high << 8) | low)
						: (short) ((low) | (high << 8));
					samples.add((float) sample / Short.MAX_VALUE);
				}
			}
		}
	}
}
