package game.sound.engine;

import game.sound.engine.Envelope.EnvelopePair;
import game.sound.engine.Envelope.EnvelopePlayer;
import util.Logger;

public class Voice
{
	public enum VoiceState
	{
		INIT,
		READY,
		PLAYING,
		PAUSED,
	}

	private EnvelopePlayer envPlayer;
	private VoiceState state;

	private Instrument ins;
	private EnvelopePair env;
	private float readPos;
	private float volume;
	private float pitch;
	private int pan;
	public int reverb;

	private int loopIterations;

	public Voice()
	{
		envPlayer = new EnvelopePlayer();
		reset();
	}

	public void reset()
	{
		readPos = 0.0f;
		volume = 1.0f;
		pitch = 1.0f;
		pan = 64;

		if (ins == null)
			state = VoiceState.INIT;
		else
			state = VoiceState.READY;
	}

	public void setInstrument(Instrument ins)
	{
		this.ins = ins;
		state = VoiceState.READY;
		loopIterations = 0;
	}

	public void setEnvelope(EnvelopePair env)
	{
		this.env = env;
	}

	public void setPitch(float pitch)
	{
		this.pitch = pitch;
	}

	public void setVolume(float volume)
	{
		this.volume = volume;
	}

	public void play()
	{
		if (state == VoiceState.READY) {
			state = VoiceState.PLAYING;

			if (env != null)
				envPlayer.press(env);
		}
	}

	public void setPaused(boolean paused)
	{
		if (state == VoiceState.PLAYING && paused) {
			state = VoiceState.PAUSED;
		}
		else if (state == VoiceState.PAUSED && !paused) {
			state = VoiceState.PLAYING;
		}
	}

	public void setPan(int pan)
	{
		if (pan < 0 || pan > 127) {
			Logger.logWarning("Invalid pan value: " + pan);
			pan = Math.max(0, Math.min(127, pan));
		}
		this.pan = pan;
	}

	public void release()
	{
		if (env != null)
			envPlayer.release(env);
		else
			reset(); // immediately end playback
	}

	public boolean isReady()
	{
		return state == VoiceState.READY;
	}

	public void renderInto(float[] dryBufferL, float[] dryBufferR, float[] wetBufferL, float[] wetBufferR,
		int numSamples, int outputRate, double deltaTime)
	{
		if (state != VoiceState.PLAYING)
			return;

		if (ins == null)
			return;

		if (env != null) {
			envPlayer.update(deltaTime);

			if (envPlayer.isDone()) {
				reset();
				return; // done
			}
		}

		float panAngle = (float) ((pan / 127.0) * (Math.PI / 2));
		float panL = (float) Math.cos(panAngle);
		float panR = (float) Math.sin(panAngle);

		float dryAngle = (float) ((reverb / 127.0) * (Math.PI / 2));
		float dryAmt = (float) Math.cos(dryAngle);
		float wetAmt = (float) Math.sin(dryAngle);

		float resampleRatio = pitch * ((float) ins.sampleRate / outputRate);
		resampleRatio = Math.min(resampleRatio, 1.99996f);

		float envVolume = (env != null) ? envPlayer.getEnvelopeVolume() : 1.0f;

		for (int i = 0; i < numSamples; i++) {
			int i0 = (int) readPos;
			int i1 = i0 + 1;

			// handle looping
			if (ins.hasLoop && i1 >= ins.loopEnd) {
				if (ins.loopCount == Instrument.LOOP_FOREVER) {
					// infinite loop
					readPos = ins.loopStart + (readPos - ins.loopEnd);
					i0 = (int) readPos;
					i1 = i0 + 1;
				}
				else if (loopIterations < ins.loopCount) {
					// fixed number of iterations
					loopIterations++;
					readPos = ins.loopStart + (readPos - ins.loopEnd);
					i0 = (int) readPos;
					i1 = i0 + 1;
				}
			}

			if (i1 >= ins.samples.size()) {
				reset(); // reached end of non-looping sample
				return;
			}

			// n64 microcode uses linear resampling, so that's what we'll use
			float frac = readPos - i0;
			float s0 = (float) ins.samples.get(i0) / Short.MAX_VALUE;
			float s1 = (float) ins.samples.get(i1) / Short.MAX_VALUE;
			float sample = (1 - frac) * s0 + frac * s1;

			float scaled = sample * volume * envVolume;

			dryBufferL[i] += scaled * panL * dryAmt;
			dryBufferR[i] += scaled * panR * dryAmt;

			wetBufferL[i] += scaled * panL * wetAmt;
			wetBufferR[i] += scaled * panR * wetAmt;

			readPos += resampleRatio;
		}
	}
}
