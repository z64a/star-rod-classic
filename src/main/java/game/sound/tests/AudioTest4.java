package game.sound.tests;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;

import app.Environment;
import game.sound.engine.Instrument;
import game.sound.engine.SoundBank;
import util.Logger;

public class AudioTest4
{
	private static final int TARGET_FPS = 60;
	private static final long FRAME_TIME_MS = 1000 / TARGET_FPS;

	private static final int OUTPUT_RATE = 32000;
	private static final int NUM_CHANNELS = 2; // stereo

	public static void main(String[] args) throws Exception
	{
		Environment.initialize();

		AudioEngine engine = new AudioEngine(OUTPUT_RATE);
		SoundBank bank = new SoundBank();

		Instrument ins = bank.getInstrument(0x30, 0).instrument();
		engine.addVoice(new Voice(ins));

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

	public static class AudioEngine
	{
		private final SourceDataLine line;
		private final int outputRate;

		private final List<Voice> voices;

		private float[] mixBufferL;
		private float[] mixBufferR;
		private byte[] outBuffer;

		public AudioEngine(int outputRate) throws LineUnavailableException
		{
			this.outputRate = outputRate;

			voices = new ArrayList<>();

			AudioFormat format = new AudioFormat(outputRate, Short.SIZE, NUM_CHANNELS, true, false);
			line = AudioSystem.getSourceDataLine(format);
			line.open(format);
			line.start();

			mixBufferL = new float[256];
			mixBufferR = new float[256];
			outBuffer = new byte[256 * Short.BYTES * NUM_CHANNELS];
		}

		public void addVoice(Voice voice)
		{
			voices.add(voice);
		}

		public void renderFrame(double deltaTime)
		{
			int samplesToGenerate = (int) Math.ceil(deltaTime * outputRate);

			if (mixBufferL.length < samplesToGenerate) {
				mixBufferL = new float[samplesToGenerate];
				mixBufferR = new float[samplesToGenerate];
				outBuffer = new byte[samplesToGenerate * Short.BYTES * NUM_CHANNELS];
			}
			else {
				Arrays.fill(mixBufferL, 0, samplesToGenerate, 0.0f);
				Arrays.fill(mixBufferR, 0, samplesToGenerate, 0.0f);
			}

			// mix voices
			for (Voice v : voices)
				v.renderInto(mixBufferL, mixBufferR, samplesToGenerate, outputRate);

			voices.removeIf(v -> v.isFinished());

			// clamp mixed voices and convert to PCM
			for (int i = 0; i < samplesToGenerate; i++) {
				short left = floatToPCM(mixBufferL[i]);
				short right = floatToPCM(mixBufferR[i]);
				outBuffer[4 * i + 0] = (byte) left;
				outBuffer[4 * i + 1] = (byte) (left >> 8);
				outBuffer[4 * i + 2] = (byte) right;
				outBuffer[4 * i + 3] = (byte) (right >> 8);
			}

			line.write(outBuffer, 0, samplesToGenerate * Short.BYTES * NUM_CHANNELS);
		}

		private static short floatToPCM(float sample)
		{
			sample = Math.max(-1.0f, Math.min(1.0f, sample)); // hard clip
			return (short) (sample * Short.MAX_VALUE);
		}

		public void shutdown()
		{
			line.drain();
			line.stop();
			line.close();
		}
	}

	private static class Voice
	{
		private final Instrument ins;
		private float readPos = 0.0f;
		private float volume = 1.0f;
		private float pitch = 1.0f;
		private int pan = 64;

		public Voice(Instrument ins)
		{
			this.ins = ins;
		}

		public void setPitch(float pitch)
		{
			this.pitch = pitch;
		}

		public void setVolume(float volume)
		{
			this.volume = volume;
		}

		public void setPan(int pan)
		{
			if (pan < 0 || pan > 127) {
				Logger.logWarning("Invalid pan value: " + pan);
				pan = Math.max(0, Math.min(127, pan));
			}
			this.pan = pan;
		}

		public void renderInto(float[] bufferL, float[] bufferR, int numSamples, int outputRate)
		{
			float angle = (float) (pan / 127.0 * (Math.PI / 2));
			float panL = (float) Math.cos(angle);
			float panR = (float) Math.sin(angle);

			float resampleRatio = pitch * ((float) ins.sampleRate / outputRate);

			// clamp resample ratio to MAX_RATIO
			if (resampleRatio > 1.99996f) {
				resampleRatio = 1.99996f;
			}

			for (int i = 0; i < numSamples; i++) {
				int i0 = (int) readPos;
				int i1 = i0 + 1;

				if (i1 >= ins.samples.size()) {
					return; // done
				}

				// n64 microcode uses linear resampling, so that's what we'll use
				float frac = readPos - i0;
				float s0 = (float) ins.samples.get(i0) / Short.MAX_VALUE;
				float s1 = (float) ins.samples.get(i1) / Short.MAX_VALUE;
				float sample = (1 - frac) * s0 + frac * s1;

				bufferL[i] += sample * volume * panL;
				bufferR[i] += sample * volume * panR;

				readPos += resampleRatio;
			}
		}

		public boolean isFinished()
		{
			return readPos >= ins.samples.size() - 1;
		}
	}
}
