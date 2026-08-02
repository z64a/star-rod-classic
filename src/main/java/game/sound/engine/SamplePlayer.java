package game.sound.engine;

import game.sound.engine.Envelope.EnvelopePair;

public class SamplePlayer implements PlaybackSession
{
	private final AudioEngine engine;

	private Voice voice;
	private Instrument instrument;
	private EnvelopePair envelope;
	private float pitch = 1.0f;
	private boolean looping = true;
	private boolean paused;
	private boolean releasing;

	public SamplePlayer(AudioEngine engine)
	{
		this.engine = engine;
	}

	public void play(Instrument instrument, EnvelopePair envelope)
	{
		stop();
		this.instrument = instrument;
		this.envelope = envelope;
		restart();
	}

	@Override
	public void restart()
	{
		stop();
		if (instrument == null || envelope == null)
			return;

		voice = new Voice();
		voice.setInstrument(instrument);
		voice.setEnvelope(envelope);
		voice.setPitch(pitch);
		voice.setLoopingAllowed(looping);
		engine.addVoice(voice);
		voice.play();
		paused = false;
		releasing = false;
		engine.resetRenderState();
	}

	@Override
	public void stop()
	{
		if (voice != null) {
			voice.kill();
			voice = null;
		}
		paused = false;
		releasing = false;
	}

	public void release()
	{
		if (voice != null && !voice.isDone()) {
			voice.release();
			releasing = true;
		}
	}

	public boolean isReleasing()
	{
		return releasing && isPlaying();
	}

	@Override
	public boolean isPlaying()
	{
		return voice != null && !voice.isDone();
	}

	@Override
	public boolean isPaused()
	{
		return paused && isPlaying();
	}

	@Override
	public int getTime()
	{
		if (voice == null)
			return 0;
		return Math.min(voice.getRenderedSamples(), getDuration());
	}

	@Override
	public int getDuration()
	{
		if (instrument == null)
			return 0;
		if (voice != null)
			return voice.getOutputDuration();
		return Voice.getOutputDuration(instrument, pitch);
	}

	@Override
	public void seekTime(int time)
	{
		int duration = getDuration();
		if (instrument == null || envelope == null || time < 0 || time >= duration)
			return;

		if (time == getTime())
			return;

		boolean wasPaused = isPaused();
		restart();
		engine.prepareForSeek();
		while (isPlaying() && getTime() < time)
			engine.renderFrame(AudioEngine.MIXER_BLOCK_TIME, true);
		if (wasPaused)
			setPaused(true);
		engine.finishSeek();
	}

	@Override
	public void setPaused(boolean paused)
	{
		if (!isPlaying())
			return;
		this.paused = paused;
		voice.setPaused(paused);
	}

	public void setPitch(float pitch)
	{
		this.pitch = pitch;
		if (isPlaying())
			voice.setPitch(pitch);
	}

	public void setLooping(boolean looping)
	{
		this.looping = looping;
		if (isPlaying())
			voice.setLoopingAllowed(looping);
	}

}
