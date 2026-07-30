package game.sound;

import java.io.File;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;

import game.sound.bgm.BgmPlayer;
import game.sound.bgm.Composition;
import game.sound.bgm.Composition.CompCommand;
import game.sound.bgm.Composition.EndLoopCompCommand;
import game.sound.bgm.Song;
import game.sound.engine.AudioEngine;
import game.sound.engine.SoundBank;
import game.sound.engine.WavWriter;
import game.sound.mseq.Mseq;
import game.sound.mseq.Mseq.EndLoopCommand;
import game.sound.mseq.Mseq.MseqCommand;
import game.sound.mseq.MseqPlayer;
import game.sound.sfx.SfxArchive;
import game.sound.sfx.SfxArchive.Command;
import game.sound.sfx.SfxArchive.Node;
import game.sound.sfx.SfxArchive.Op;
import game.sound.sfx.SfxArchive.Sequence;
import game.sound.sfx.SfxArchive.Sound;
import game.sound.sfx.SfxArchive.SpawnedEffect;
import game.sound.sfx.SfxArchive.Track;
import game.sound.sfx.SfxPlayer;

public class AudioExporter
{
	private static final double AUDIO_BLOCK_TIME =
		(AudioEngine.FRAME_SAMPLES - 0.5) / AudioEngine.OUTPUT_RATE;
	private static final int MAX_EXPORT_BLOCKS =
		30 * 60 * AudioEngine.OUTPUT_RATE / AudioEngine.FRAME_SAMPLES;
	private static final int MAX_TAIL_BLOCKS =
		10 * AudioEngine.OUTPUT_RATE / AudioEngine.FRAME_SAMPLES;

	private final SoundBank bank;

	public AudioExporter(SoundBank bank)
	{
		this.bank = bank;
	}

	public Result exportSfx(File outputFile, int volume, int loopRepetitions,
		SfxArchive archive, Sound sound) throws Exception
	{
		return render(outputFile, volume, loopRepetitions, (engine) -> {
			SfxPlayer player = new SfxPlayer(engine, bank);
			player.setArchive(archive);
			player.play(sound.id);
			return new ExportPlayback(player);
		});
	}

	public Result exportMseq(File outputFile, int volume, int loopRepetitions,
		Mseq mseq) throws Exception
	{
		return render(outputFile, volume, loopRepetitions, (engine) -> {
			MseqPlayer player = new MseqPlayer(engine, bank);
			player.setMseq(mseq);
			return new ExportPlayback(player);
		});
	}

	public Result exportBgm(File outputFile, int volume, int loopRepetitions,
		Song song, int composition, int proximityMixID, int proximityMixVolume,
		boolean proximityMixInstant) throws Exception
	{
		return render(outputFile, volume, loopRepetitions, (engine) -> {
			BgmPlayer player = new BgmPlayer(engine, bank);
			player.play(song, composition);
			player.setProximityMix(proximityMixID, proximityMixVolume, proximityMixInstant);
			return new ExportPlayback(player);
		});
	}

	private Result render(File outputFile, int volume, int loopRepetitions,
		PlayerFactory factory) throws Exception
	{
		try (WavWriter writer = new WavWriter(outputFile)) {
			AudioEngine engine = new AudioEngine(writer);
			engine.setMasterVolume(volume);
			ExportPlayback playback = factory.create(engine);

			int blocks = 0;
			while (playback.isPlaying() && blocks < MAX_EXPORT_BLOCKS) {
				if (loopRepetitions != 0 && playback.getTimelineLoopCount() >= loopRepetitions)
					break;
				engine.renderFrame(AUDIO_BLOCK_TIME, false);
				blocks++;
			}

			boolean truncated = blocks == MAX_EXPORT_BLOCKS && playback.isPlaying();
			if (!playback.isPlaying()) {
				int tailBlocks = 0;
				while (engine.hasActiveVoices() && tailBlocks < MAX_TAIL_BLOCKS) {
					engine.renderFrame(AUDIO_BLOCK_TIME, false);
					tailBlocks++;
				}
				if (tailBlocks == MAX_TAIL_BLOCKS && engine.hasActiveVoices())
					truncated = true;
			}

			return new Result(writer.getSampleCount(), truncated);
		}
	}

	public static boolean hasInfiniteLoop(Mseq mseq)
	{
		for (MseqCommand command : mseq.commands) {
			if (command instanceof EndLoopCommand loop && loop.count == 0)
				return true;
		}
		return false;
	}

	public static boolean hasInfiniteLoop(Song song, int compositionIndex)
	{
		Composition composition = song.getComposition(compositionIndex);
		if (composition == null)
			return false;

		for (CompCommand command : composition.getCommands()) {
			if (command instanceof EndLoopCompCommand loop && loop.getLoopCount() == 0)
				return true;
		}
		return false;
	}

	public static boolean hasInfiniteLoop(Sound sound)
	{
		if (tracksHaveInfiniteLoop(sound.tracks))
			return true;
		for (SpawnedEffect spawned : sound.spawnedEffects) {
			if (tracksHaveInfiniteLoop(spawned.tracks))
				return true;
		}
		return false;
	}

	private static boolean tracksHaveInfiniteLoop(List<Track> tracks)
	{
		for (Track track : tracks) {
			if (!(track.definition instanceof Sequence sequence))
				continue;
			for (Node node : sequence.nodes) {
				if (node instanceof Command command
					&& command.op == Op.START_LOOP && command.a == 0)
					return true;
			}
		}
		return false;
	}

	private interface PlayerFactory
	{
		ExportPlayback create(AudioEngine engine) throws Exception;
	}

	private record ExportPlayback(BooleanSupplier playing, IntSupplier timelineLoopCount)
	{
		public ExportPlayback(SfxPlayer player)
		{
			this(player::isPlaying, player::getTimelineLoopCount);
		}

		public ExportPlayback(MseqPlayer player)
		{
			this(player::isPlaying, player::getTimelineLoopCount);
		}

		public ExportPlayback(BgmPlayer player)
		{
			this(player::isPlaying, player::getTimelineLoopCount);
		}

		public boolean isPlaying()
		{
			return playing.getAsBoolean();
		}

		public int getTimelineLoopCount()
		{
			return timelineLoopCount.getAsInt();
		}
	}

	public record Result(long samples, boolean truncated)
	{}
}
