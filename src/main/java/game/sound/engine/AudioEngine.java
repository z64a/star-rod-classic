package game.sound.engine;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;

public class AudioEngine
{
	public static final int OUTPUT_RATE = 32000;
	public static final int FRAME_SAMPLES = 184;

	private static final int NUM_CHANNELS = 2; // stereo
	private static final int FRAME_BYTES = FRAME_SAMPLES * NUM_CHANNELS * Short.BYTES;

	private int overflowSamples = 0;

	private final SourceDataLine line;
	private final EffectBus effectBus;
	private final List<Voice> voices;

	private float[] dryBufferL;
	private float[] dryBufferR;

	private float[] wetBufferL;
	private float[] wetBufferR;

	private float[] mixedBufferL;
	private float[] mixedBufferR;

	private byte[] outBuffer;

	private int masterVolume = 256;

	public AudioEngine() throws LineUnavailableException
	{
		effectBus = new EffectBus();
		voices = new ArrayList<>();

		AudioFormat format = new AudioFormat(OUTPUT_RATE, Short.SIZE, NUM_CHANNELS, true, false);
		line = AudioSystem.getSourceDataLine(format);
		line.open(format);
		line.start();

		dryBufferL = new float[FRAME_SAMPLES];
		dryBufferR = new float[FRAME_SAMPLES];

		wetBufferL = new float[FRAME_SAMPLES];
		wetBufferR = new float[FRAME_SAMPLES];

		mixedBufferL = new float[FRAME_SAMPLES];
		mixedBufferR = new float[FRAME_SAMPLES];

		outBuffer = new byte[FRAME_SAMPLES * Short.BYTES * NUM_CHANNELS];
	}

	public void addVoice(Voice voice)
	{
		voices.add(voice);
	}

	public void removeVoice(Voice voice)
	{
		voices.remove(voice);
	}

	public void setMasterVolume(int volume)
	{
		masterVolume = volume;
	}

	public int getMasterVolume()
	{
		return masterVolume;
	}

	public void renderFrame(double deltaTime)
	{
		float masterVolumeRatio = (float) (masterVolume / 256.0);

		int totalSamples = (int) Math.ceil(deltaTime * OUTPUT_RATE);
		int processed = 0;

		if (overflowSamples > 0) {
			writeSamples(line, mixedBufferL, mixedBufferR, outBuffer, FRAME_SAMPLES - overflowSamples, FRAME_SAMPLES);
			processed += overflowSamples;
			overflowSamples = 0;
		}

		while (processed < totalSamples) {
			// clear buffers
			Arrays.fill(dryBufferL, 0, FRAME_SAMPLES, 0.0f);
			Arrays.fill(dryBufferR, 0, FRAME_SAMPLES, 0.0f);

			Arrays.fill(wetBufferL, 0, FRAME_SAMPLES, 0.0f);
			Arrays.fill(wetBufferR, 0, FRAME_SAMPLES, 0.0f);

			// mix voices
			for (Voice v : voices)
				v.renderInto(dryBufferL, dryBufferR, wetBufferL, wetBufferR, FRAME_SAMPLES, OUTPUT_RATE, deltaTime);

			// process effects
			effectBus.renderInto(wetBufferL, wetBufferR, FRAME_SAMPLES);

			// final mixdown for output samples
			for (int i = 0; i < FRAME_SAMPLES; i++) {
				mixedBufferL[i] = (dryBufferL[i] + wetBufferL[i]) * masterVolumeRatio;
				mixedBufferR[i] = (dryBufferR[i] + wetBufferR[i]) * masterVolumeRatio;
			}

			processed += FRAME_SAMPLES;

			int writeSamples;

			if (processed > totalSamples) {
				overflowSamples = processed - totalSamples;
				writeSamples = FRAME_SAMPLES - overflowSamples;
			}
			else {
				overflowSamples = 0;
				writeSamples = FRAME_SAMPLES;
			}

			writeSamples(line, mixedBufferL, mixedBufferR, outBuffer, 0, writeSamples);
		}
	}

	private static void writeSamples(SourceDataLine line, float[] mixedL, float[] mixedR, byte[] outBuffer, int start, int end)
	{
		int sampleCount = (end - start);

		for (int i = 0; i < sampleCount; i++) {
			short left = floatToPCM(mixedL[start + i]);
			short right = floatToPCM(mixedR[start + i]);
			outBuffer[4 * i + 0] = (byte) left;
			outBuffer[4 * i + 1] = (byte) (left >> 8);
			outBuffer[4 * i + 2] = (byte) right;
			outBuffer[4 * i + 3] = (byte) (right >> 8);
		}

		line.write(outBuffer, 0, sampleCount * NUM_CHANNELS * Short.BYTES);
	}

	// clamp and convert to PCM
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

	public static float detuneToPitchRatio(int detune)
	{
		detune = Math.max(-0x3FFF, Math.min(0xFFF, detune));
		return (float) Math.pow(2, detune / 1200.0); // 1200 = CENTS_PER_OCTAVE
	}
}
