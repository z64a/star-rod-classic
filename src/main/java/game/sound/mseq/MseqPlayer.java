package game.sound.mseq;

import game.sound.DrumModder.Drum;
import game.sound.engine.AudioEngine;
import game.sound.engine.Envelope.EnvelopePair;
import game.sound.engine.Instrument;
import game.sound.engine.SoundBank;
import game.sound.engine.SoundBank.DrumQueryResult;
import game.sound.engine.SoundBank.InstrumentQueryResult;
import game.sound.engine.Voice;
import game.sound.mseq.Mseq.DelayCommand;
import game.sound.mseq.Mseq.EndLoopCommand;
import game.sound.mseq.Mseq.MseqCommand;
import game.sound.mseq.Mseq.PlayDrumCommand;
import game.sound.mseq.Mseq.PlaySoundCommand;
import game.sound.mseq.Mseq.SetInstrumentCommand;
import game.sound.mseq.Mseq.SetPanCommand;
import game.sound.mseq.Mseq.SetResumableCommand;
import game.sound.mseq.Mseq.SetReverbCommand;
import game.sound.mseq.Mseq.SetTuneCommand;
import game.sound.mseq.Mseq.SetVolCommand;
import game.sound.mseq.Mseq.StartLoopCommand;
import game.sound.mseq.Mseq.StopSoundCommand;
import game.sound.mseq.Mseq.TrackSetting;
import util.Logger;

public class MseqPlayer
{
	private static final int NUM_VOICES = 16;

	public static enum PlayerState
	{
		PLAYING,
		PAUSED,
		DONE
	}

	private static boolean debugCommands = false;

	private final AudioEngine engine;
	private final SoundBank bank;

	private PlayerState state;

	private Mseq mseq;
	private int curPos;
	private int delayTime;

	private int curTime;

	private MseqTrack[] tracks;
	private MseqVoice[] voices;

	// loop state
	private int[] loopPositions;
	private int[] loopIterations;

	public static class MseqVoice extends Voice
	{
		public final MseqTrack track;
		public final int tuneID;

		public float baseVolume;
		public int baseDetune;

		public MseqVoice(MseqTrack track, int tuneID)
		{
			this.track = track;
			this.tuneID = tuneID;
		}

		public void updateVolume()
		{
			setVolume(baseVolume * track.volumeLerp.current);
		}

		public void updatePitch()
		{
			if (track.index != Mseq.DRUM_TRACK)
				setPitch(AudioEngine.detuneToPitchRatio(baseDetune + Math.round(track.tuneLerp.current)));
		}
	}

	public static class LerpState
	{
		float current = 1.0f;
		float goal = 1.0f;
		float step;
		int time;
	}

	public static class MseqTrack
	{
		public final int index;

		public Instrument instrument;
		public EnvelopePair envelope;

		public LerpState volumeLerp = new LerpState();
		public LerpState tuneLerp = new LerpState();

		public int pan;
		public int reverb;
		public boolean isResumable;

		public MseqTrack(int index)
		{
			this.index = index;
		}

		public void reset()
		{
			volumeLerp = new LerpState();
			tuneLerp = new LerpState();

			volumeLerp.current = 1.0f;
			tuneLerp.current = 0;

			pan = 64;
			reverb = 0;
			isResumable = false;
		}
	}

	public MseqPlayer(AudioEngine engine, SoundBank bank)
	{
		this.engine = engine;
		this.bank = bank;

		engine.addClient(this::frame);
	}

	public boolean getPaused()
	{
		return state == PlayerState.PAUSED;
	}

	public void setPaused(boolean pause)
	{
		boolean changed = false;

		if (state == PlayerState.PLAYING && pause) {
			state = PlayerState.PAUSED;
			changed = true;
		}
		else if (state == PlayerState.PAUSED && !pause) {
			state = PlayerState.PLAYING;
			changed = true;
		}

		if (changed) {
			for (MseqVoice voice : voices) {
				if (voice != null) {
					voice.setPaused(pause);
				}
			}
		}
	}

	public int getTime()
	{
		return curTime;
	}

	public void seekTime(int seekTime)
	{
		if (mseq == null || mseq.commands.isEmpty())
			return;

		if (seekTime < 0 || seekTime > mseq.duration)
			return;

		if (seekTime > curTime)
			seek(curTime, seekTime);
		else if (seekTime < curTime)
			seek(0, seekTime);
	}

	private void seek(int startTime, int seekTime)
	{
		// kill any active voices
		for (int i = 0; i < voices.length; i++) {
			MseqVoice voice = voices[i];
			if (voice != null) {
				voice.kill();
			}
		}

		tracks = new MseqTrack[Mseq.NUM_TRACKS];
		for (int i = 0; i < Mseq.NUM_TRACKS; i++) {
			tracks[i] = new MseqTrack(i);
		}

		voices = new MseqVoice[NUM_VOICES];

		// loop state
		loopPositions = new int[2];
		loopIterations = new int[2];

		for (int i = 0; i < tracks.length; i++) {
			tracks[i].reset();
		}

		curPos = 0;
		curTime = 0;
		delayTime = 0;

		MseqCommand curCommand = mseq.commands.get(0);
		while (curPos < mseq.commands.size() && curTime > curCommand.time) {
			//TODO
		}

		state = PlayerState.PLAYING;
	}

	public void setMseq(Mseq mseq)
	{
		if (this.mseq != null) {
			// kill any voices from previous MSEQ
			for (int i = 0; i < voices.length; i++) {
				MseqVoice voice = voices[i];
				if (voice != null) {
					voice.kill();
				}
			}
		}

		this.mseq = mseq;
		mseq.calculateTiming();

		tracks = new MseqTrack[Mseq.NUM_TRACKS];
		for (int i = 0; i < Mseq.NUM_TRACKS; i++) {
			tracks[i] = new MseqTrack(i);
		}

		voices = new MseqVoice[NUM_VOICES];

		// loop state
		loopPositions = new int[2];
		loopIterations = new int[2];

		curPos = 0;
		curTime = 0;
		delayTime = 0;

		for (int i = 0; i < tracks.length; i++) {
			tracks[i].reset();
		}

		for (TrackSetting settings : mseq.trackSettings) {

			MseqTrack track = tracks[settings.track];
			if (settings.type == 0) {
				track.tuneLerp.time = settings.time;
				track.tuneLerp.goal = settings.goal;
				track.tuneLerp.step = ((float) settings.delta) / settings.time;
			}
			else {
				track.volumeLerp.time = settings.time;
				track.volumeLerp.goal = settings.goal / Mseq.MAX_VOL_16;
				track.volumeLerp.step = (settings.delta / Mseq.MAX_VOL_16) / settings.time;
			}
		}

		state = PlayerState.PLAYING;
	}

	int updateCounter = 2;
	int updateInverval = 2;

	private void frame()
	{
		updateCounter--;
		if (updateCounter <= 0) {
			updateCounter += updateInverval;
			update();
		}
	}

	private void update()
	{
		if (mseq == null)
			return;

		if (state != PlayerState.PLAYING)
			return;

		// clear voices which have finished playing/releasing
		for (int i = 0; i < voices.length; i++) {
			MseqVoice voice = voices[i];
			if (voice != null && voice.isDone())
				voices[i] = null;
		}

		// update fade in lerps
		for (MseqTrack track : tracks) {
			if (track.volumeLerp.time != 0) {
				track.volumeLerp.time--;
				if (track.volumeLerp.time != 0)
					track.volumeLerp.current += track.volumeLerp.step;
				else
					track.volumeLerp.current = track.volumeLerp.goal;
			}

			if (track.tuneLerp.time != 0) {
				track.tuneLerp.time--;
				if (track.tuneLerp.time != 0)
					track.tuneLerp.current += track.tuneLerp.step;
				else
					track.tuneLerp.current = track.tuneLerp.goal;
			}
		}

		// update client params for voices
		for (int i = 0; i < voices.length; i++) {
			MseqVoice voice = voices[i];
			if (voice == null)
				continue;

			voice.updateVolume();
			voice.updatePitch();
		}

		if (delayTime > 0)
			delayTime--;

		// consume commands
		while (delayTime == 0) {
			if (mseq.commands.size() == curPos) {
				state = PlayerState.DONE;
				curTime = mseq.duration;
				return;
			}

			MseqCommand abs = mseq.commands.get(curPos);
			curTime = abs.time;
			curPos++;

			// could have a method in the command classes, but id rather have them only store state
			// and keep all the playback related code in this class
			if (abs instanceof DelayCommand cmd) {
				logCommand("    Delay " + cmd.duration);

				delayTime = cmd.duration;
			}
			else if (abs instanceof StopSoundCommand cmd) {
				logCommand("[%X] Stop Sound %X", cmd.track, cmd.pitch);

				for (int i = 0; i < voices.length; i++) {
					MseqVoice voice = voices[i];
					if (voice == null)
						continue;

					if (voice.track == tracks[cmd.track]) {
						if (voice.tuneID == cmd.pitch) {
							voice.release();
						}
					}
				}
			}
			else if (abs instanceof PlaySoundCommand cmd) {
				logCommand("[%X] Play Sound %X @ %X (detune = %d)", cmd.track, cmd.pitch, cmd.volume,
					((cmd.pitch & 0x7F) * 100) - tracks[cmd.track].instrument.keyBase);

				int index = -1;

				// find unassigned voice ref
				for (int i = 0; i < voices.length; i++) {
					if (voices[i] == null) {
						index = i;
						break;
					}
				}

				// try stealing the first voice -- an odd choice, but OK
				if (index == -1) {
					index = 0;
					voices[0].kill();
					voices[0] = null;
				}

				if (index != -1) {
					MseqTrack track = tracks[cmd.track];

					if (track.instrument != null) {
						Instrument ins = track.instrument;
						EnvelopePair envelope = track.envelope;

						MseqVoice voice = new MseqVoice(track, cmd.pitch);
						voices[index] = voice;
						engine.addVoice(voice);

						voice.baseVolume = (cmd.volume & 0x7F) / Mseq.MAX_VOL_8;
						voice.baseDetune = ((cmd.pitch & 0x7F) * 100) - ins.keyBase;
						voice.setInstrument(ins);
						voice.setEnvelope(envelope);
						voice.setReverb(track.reverb);

						voice.updateVolume();
						voice.updatePitch();

						voice.play();
					}
					else {
						Logger.logfWarning("[%X] Play Sound: Instrument is null!", cmd.track);
					}
				}
			}
			else if (abs instanceof PlayDrumCommand cmd) {
				logCommand("[%X] Play Drum %X @ %X", Mseq.DRUM_TRACK, cmd.drumID, cmd.volume);

				MseqTrack track = tracks[Mseq.DRUM_TRACK];
				DrumQueryResult res = bank.getDrum(cmd.drumID & 0x7F);
				if (res == null) {
					track.instrument = null;
					track.envelope = null;
				}
				else {
					Drum drum = res.drum();
					Instrument ins = track.instrument = res.instrument();
					EnvelopePair envelope = track.envelope = res.envelope();

					MseqVoice voice = new MseqVoice(track, cmd.drumID);
					voices[Mseq.DRUM_TRACK] = voice;
					engine.addVoice(voice);

					voice.baseVolume = (drum.volume / Mseq.MAX_VOL_8) * (cmd.volume & 0x7F) / Mseq.MAX_VOL_8;
					voice.baseDetune = drum.keybase - ins.keyBase;
					voice.setInstrument(ins);
					voice.setEnvelope(envelope);
					voice.setReverb(drum.reverb);
					voice.setPan(drum.pan);

					voice.updateVolume();
					voice.updatePitch();

					voice.play();
				}
			}
			else if (abs instanceof SetVolCommand cmd) {
				logCommand("[%X] Set Volume: %X", cmd.track, cmd.volume);

				MseqTrack track = tracks[cmd.track];
				track.volumeLerp.current = cmd.volume / Mseq.MAX_VOL_8;

				for (int i = 0; i < voices.length; i++) {
					MseqVoice voice = voices[i];
					if (voice == null)
						continue;

					if (voice.track == track) {
						voice.updateVolume();
					}
				}
			}
			else if (abs instanceof SetTuneCommand cmd) {
				logCommand("[%X] Set Tune: %X", cmd.track, cmd.value);

				MseqTrack track = tracks[cmd.track];
				track.tuneLerp.current = (short) cmd.value;

				for (int i = 0; i < voices.length; i++) {
					MseqVoice voice = voices[i];
					if (voice == null)
						continue;

					if (voice.track == track) {
						voice.updatePitch();
					}
				}
			}
			else if (abs instanceof SetPanCommand cmd) {
				logCommand("[%X] SetPan %X", cmd.track, cmd.pan);

				if (cmd.track != Mseq.DRUM_TRACK) {
					MseqTrack track = tracks[cmd.track];

					for (int i = 0; i < voices.length; i++) {
						MseqVoice voice = voices[i];
						if (voice == null)
							continue;

						if (voice.track == track) {
							voice.setPan(cmd.pan);
						}
					}
				}
			}
			else if (abs instanceof SetInstrumentCommand cmd) {
				logCommand("[%X] Set Instrument: %2X %2X", cmd.track, cmd.bank, cmd.patch);

				MseqTrack track = tracks[cmd.track];
				InstrumentQueryResult res = bank.getInstrument(cmd.bank, cmd.patch);
				if (res == null) {
					track.instrument = null;
					track.envelope = null;
				}
				else {
					track.instrument = res.instrument();
					track.envelope = res.envelope();
				}
			}
			else if (abs instanceof SetReverbCommand cmd) {
				logCommand("[%X] Set Reverb: %X", cmd.track, cmd.reverb);

				MseqTrack track = tracks[cmd.track];
				track.reverb = cmd.reverb;
			}
			else if (abs instanceof SetResumableCommand cmd) {
				logCommand("[%X] Set Resumable: %b", cmd.track, cmd.resumable);

				MseqTrack track = tracks[cmd.track];
				track.isResumable = cmd.resumable;
			}
			else if (abs instanceof StartLoopCommand cmd) {
				logCommand("--- Start Loop %X", cmd.loopID & 1);

				loopPositions[cmd.loopID & 1] = curPos + 1;
			}
			else if (abs instanceof EndLoopCommand cmd) {
				logCommand("--- End Loop %X (%d/%d)", cmd.loopID & 1, loopIterations[cmd.loopID & 1], cmd.count);

				int loopID = cmd.loopID & 1;
				int startPos = loopPositions[loopID];

				if (cmd.count == 0) {
					// infinite loop, jump to loop start
					loopIterations[loopID] = 0;
					curPos = startPos;
				}
				else {
					if (loopIterations[loopID] != 0) {
						loopIterations[loopID]--;
						if (loopIterations[loopID] != 0) {
							// not the last iteration, jump to loop start
							curPos = startPos;
						}
					}
					else {
						// first iteration, jump to loop start
						loopIterations[loopID] = cmd.count;
						curPos = startPos;
					}
				}
			}
		}
	}

	private static void logCommand(String string, Object ... args)
	{
		if (debugCommands) {
			System.out.printf(string, args);
			System.out.println();
		}
	}
}
