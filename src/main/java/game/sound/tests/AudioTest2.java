package game.sound.tests;

import static app.Directories.DUMP_AUDIO_BANK;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;

import app.Environment;

public class AudioTest2
{
	private static final int TARGET_FPS = 60;
	private static final long FRAME_TIME_MS = 1000 / TARGET_FPS;
	private static float pitch = 1.0f;
	private static float volume = 1.0f;

	private static final int OUTPUT_RATE = 32000;
	private static final int BUFFER_SIZE = 10024;

	public static void main(String[] args) throws Exception
	{
		Environment.initialize();

		File audioFile = DUMP_AUDIO_BANK.getFile("STG5/STG5_06.wav");
		Sound sound = new Sound(audioFile);

		AudioEngine engine = new AudioEngine(OUTPUT_RATE);

		engine.addVoice(new Voice(sound, OUTPUT_RATE));

		long prevTime = System.nanoTime();
		double time = 0.0f;
		double deltaTime = FRAME_TIME_MS / 1e6;

		while (!engine.voices.isEmpty()) {
			long curTime = System.nanoTime();
			deltaTime = (curTime - prevTime) / 1e9;
			prevTime = curTime;
			time += deltaTime;

			engine.renderFrame(deltaTime);

			long sleep = FRAME_TIME_MS - (long) (deltaTime / 1e6);
			if (sleep > 0)
				Thread.sleep(sleep);
		}

		engine.shutdown();
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

	public static class Voice
	{
		private final Sound sound;
		private float readPos = 0.0f;
		private float volume = 1.0f;
		private float pitch = 1.0f;

		private final float outputRatio;

		public Voice(Sound sound, float outputRate)
		{
			this.sound = sound;
			this.outputRatio = sound.sampleRate / outputRate;
		}

		public void setPitch(float pitch)
		{
			this.pitch = pitch;
		}

		public void setVolume(float volume)
		{
			this.volume = volume;
		}

		public void renderInto(float[] buffer, int numSamples)
		{
			for (int i = 0; i < numSamples; i++) {
				int i0 = (int) readPos;
				int i1 = i0 + 1;

				if (i1 >= sound.samples.size()) {
					return; // done
				}

				float resampleRatio = pitch * outputRatio;

				// clamp pitch ratio to MAX_RATIO
				if (resampleRatio > 1.99996f) {
					resampleRatio = 1.99996f;
				}

				// n64 mircocode uses linear resampling, so that's what we'll use
				float frac = readPos - i0;
				float s0 = sound.samples.get(i0);
				float s1 = sound.samples.get(i1);
				float sample = (1 - frac) * s0 + frac * s1;

				buffer[i] += sample * volume;

				readPos += resampleRatio;
			}
		}

		public boolean isFinished()
		{
			return readPos >= sound.samples.size() - 1;
		}
	}

	public static class AudioEngine
	{
		private final List<Voice> voices = new ArrayList<>();
		private final SourceDataLine line;
		private final float outputRate;

		private float[] mixBuffer;
		private byte[] outBuffer;

		public AudioEngine(float outputRate) throws LineUnavailableException
		{
			this.outputRate = outputRate;

			AudioFormat format = new AudioFormat(outputRate, 16, 1, true, false);
			line = AudioSystem.getSourceDataLine(format);
			line.open(format);
			line.start();

			mixBuffer = new float[1024];
			outBuffer = new byte[1024 * Short.BYTES];
		}

		public void addVoice(Voice voice)
		{
			voices.add(voice);
		}

		public void renderFrame(double deltaTime)
		{
			int samplesToGenerate = (int) Math.ceil(deltaTime * outputRate);

			if (mixBuffer.length < samplesToGenerate) {
				mixBuffer = new float[samplesToGenerate];
				outBuffer = new byte[samplesToGenerate * Short.BYTES];
			}
			else {
				Arrays.fill(mixBuffer, 0, samplesToGenerate, 0.0f);
			}

			// mix voices
			for (Voice v : voices)
				v.renderInto(mixBuffer, samplesToGenerate);

			voices.removeIf(v -> v.isFinished());

			// clamp mixed voices and convert to PCM
			for (int i = 0; i < samplesToGenerate; i++) {
				float sample = Math.max(-1.0f, Math.min(1.0f, mixBuffer[i]));
				short s = (short) (sample * 32767);
				outBuffer[2 * i] = (byte) s;
				outBuffer[2 * i + 1] = (byte) (s >> 8);
			}

			line.write(outBuffer, 0, samplesToGenerate * Short.BYTES);
		}

		public void shutdown()
		{
			line.drain();
			line.stop();
			line.close();
		}
	}
}
