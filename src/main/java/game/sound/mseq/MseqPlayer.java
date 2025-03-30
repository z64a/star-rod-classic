package game.sound.mseq;

import game.sound.BankEditor.SoundBank;
import game.sound.BankEditor.SoundBank.BankQueryResult;
import game.sound.engine.AudioEngine;
import game.sound.engine.Envelope.EnvelopePair;
import game.sound.engine.Instrument;
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

public class MseqPlayer
{
	private static final int NUM_VOICES = 16;

	public static enum PlayerState
	{
		PLAYING,
		PAUSED,
		DONE
	};

	private final AudioEngine engine;
	private final SoundBank bank;

	private PlayerState state;

	private Mseq mseq;
	private int curPos;
	private int delayTime;

	private MseqTrack[] tracks;
	private VoiceRef[] refs;

	// loop state
	private int[] loopPositions;
	private int[] loopIterations;

	public static class VoiceRef
	{
		Voice voice;
		MseqTrack track;
		int tuneID;

		int volume;
		int detune;

		public void updateVoiceVolume()
		{
			if (voice != null && track != null)
				voice.setVolume((volume / 127.0f) * track.volumeLerp.current);
		}

		public void updateVoicePitch()
		{
			if (voice != null && track != null && track.index != Mseq.DRUM_TRACK)
				voice.setPitch(AudioEngine.detuneToPitchRatio(detune + (track.tuneLerp.current >> 0x10)));
		}
	}

	public static class VolumeLerp
	{
		float current = 1.0f;
		float goal = 1.0f;
		float step;
		float time;
	}

	public static class TuneLerp
	{
		int current;
		int goal;
		int step;
		int time;
	}

	public static class MseqTrack
	{
		public final int index;

		public Instrument instrument;
		public EnvelopePair envelope;

		public VolumeLerp volumeLerp = new VolumeLerp();
		public TuneLerp tuneLerp = new TuneLerp();

		public int pan;
		public int reverb;
		public boolean isResumable;

		public MseqTrack(AudioEngine engine, int index)
		{
			this.index = index;
		}

		public void reset()
		{
			volumeLerp = new VolumeLerp();
			tuneLerp = new TuneLerp();

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

		tracks = new MseqTrack[Mseq.NUM_TRACKS];
		for (int i = 0; i < Mseq.NUM_TRACKS; i++) {
			tracks[i] = new MseqTrack(engine, i);
		}

		refs = new VoiceRef[NUM_VOICES];
		for (int i = 0; i < NUM_VOICES; i++) {
			refs[i] = new VoiceRef();
			refs[i].voice = new Voice();
			engine.addVoice(refs[i].voice);
		}

		// loop state
		loopPositions = new int[2];
		loopIterations = new int[2];
	}

	public void setMseq(Mseq mseq)
	{
		this.mseq = mseq;

		curPos = 0;
		delayTime = 0;

		loopPositions[0] = 0;
		loopPositions[1] = 0;
		loopIterations[0] = 0;
		loopIterations[1] = 0;

		for (int i = 0; i < tracks.length; i++) {
			tracks[i].reset();
		}

		state = PlayerState.PLAYING;
	}

	public void update()
	{
		if (mseq == null)
			return;

		if (state != PlayerState.PLAYING)
			return;

		// free up voices which have finished playing/releasing
		for (VoiceRef ref : refs) {
			if (ref.track != null && ref.voice.isReady()) {
				ref.track = null;
				ref.tuneID = 0;
			}
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
		for (VoiceRef ref : refs) {
			if (ref.track != null) {
				ref.updateVoiceVolume();
				ref.updateVoicePitch();
			}
		}

		if (delayTime > 0)
			delayTime--;

		// consume commands
		while (delayTime == 0) {
			if (mseq.commands.size() == curPos) {
				state = PlayerState.DONE;
				return;
			}

			MseqCommand abs = mseq.commands.get(curPos);
			curPos++;

			// could have a method in the command classes, but id rather have them only store state
			// and keep all the playback related code in this class
			if (abs instanceof DelayCommand cmd) {
				System.out.println("    Delay " + cmd.duration);
				delayTime = cmd.duration;
			}
			else if (abs instanceof StopSoundCommand cmd) {
				System.out.printf("[%X] Stop Sound %X%n", cmd.track, cmd.pitch);
				for (VoiceRef ref : refs) {
					if (ref.track == tracks[cmd.track]) {
						if (ref.tuneID == cmd.pitch) {
							ref.voice.release();
						}
					}
				}
			}
			else if (abs instanceof PlaySoundCommand cmd) {
				System.out.printf("[%X] Play Sound %X%n", cmd.track, cmd.pitch);
				VoiceRef ref = null;

				// find unassigned voice ref
				for (VoiceRef curRef : refs) {
					if (curRef.track == null) {
						ref = curRef;
						break;
					}
				}

				// try stealing the first voice -- an odd choice, but OK
				if (ref == null) {
					ref = refs[0];
					ref.voice.reset();
				}

				if (ref != null) {
					ref.track = tracks[cmd.track];
					Instrument ins = ref.track.instrument;
					EnvelopePair envelope = ref.track.envelope;

					ref.tuneID = cmd.pitch;
					ref.volume = cmd.volume;
					ref.detune = ((cmd.pitch & 0x7F) * 100) - ins.keyBase;
					ref.voice.setInstrument(ins);
					ref.voice.setEnvelope(envelope);
					ref.voice.reverb = ref.track.reverb;

					ref.updateVoiceVolume();
					ref.updateVoicePitch();

					ref.voice.play();
				}
			}
			else if (abs instanceof PlayDrumCommand cmd) {
				//TODO
			}
			else if (abs instanceof SetVolCommand cmd) {
				System.out.printf("[%X] Set Volume: %X%n", cmd.track, cmd.volume);
				MseqTrack track = tracks[cmd.track];
				track.volumeLerp.current = (cmd.volume / Mseq.MAX_VOLUME);

				for (VoiceRef ref : refs) {
					if (ref.track == track) {
						ref.updateVoiceVolume();
					}
				}
			}
			else if (abs instanceof SetTuneCommand cmd) {
				System.out.printf("[%X] Set Tune: %X%n", cmd.track, cmd.value);
				MseqTrack track = tracks[cmd.track];
				track.tuneLerp.current = cmd.value;

				for (VoiceRef ref : refs) {
					if (ref.track == track) {
						ref.updateVoicePitch();
					}
				}
			}
			else if (abs instanceof SetPanCommand cmd) {
				System.out.printf("[%X] SetPan %X%n", cmd.track, cmd.pan);
				if (cmd.track != Mseq.DRUM_TRACK) {
					MseqTrack track = tracks[cmd.track];

					for (VoiceRef ref : refs) {
						if (ref.track == track) {
							ref.voice.setPan(cmd.pan);
						}
					}
				}
			}
			else if (abs instanceof SetInstrumentCommand cmd) {
				System.out.printf("[%X] Set Instrument: %2X %2X%n", cmd.track, cmd.bank, cmd.patch);
				MseqTrack track = tracks[cmd.track];
				BankQueryResult res = bank.getInstrument(cmd.bank, cmd.patch);
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
				System.out.printf("[%X] Set Reverb: %X%n", cmd.track, cmd.reverb);
				MseqTrack track = tracks[cmd.track];
				track.reverb = cmd.reverb;
			}
			else if (abs instanceof SetResumableCommand cmd) {
				System.out.printf("[%X] Set Resumable: %b%n", cmd.track, cmd.resumable);
				MseqTrack track = tracks[cmd.track];
				track.isResumable = cmd.resumable;
			}
			else if (abs instanceof StartLoopCommand cmd) {
				loopPositions[cmd.loopID & 1] = curPos + 1;
			}
			else if (abs instanceof EndLoopCommand cmd) {
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
}
