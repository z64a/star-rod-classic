package game.sound.bgm;

import java.util.ArrayList;

import game.sound.bgm.Track.TrackCommand;
import util.DynamicByteBuffer;

public class CommandStream extends ArrayList<TrackCommand>
{
	public static enum StreamType
	{
		TRACK,
		BRANCH,
		DETOUR
	}

	public final StreamType type;
	public boolean isDrum;
	public int duration;

	public transient int filePos;
	public transient int fileLen;

	public CommandStream(StreamType type)
	{
		this.type = type;
	}

	public void build(DynamicByteBuffer dbb, boolean terminate)
	{
		filePos = dbb.position();

		/*
		int time = 0;
		for (TrackCommand cmd : this) {
			if (time < cmd.time) {
				addDelay(dbb, cmd.time - time);
			}
			cmd.build(dbb);
		}
		if (time < duration) {
			addDelay(dbb, duration - time);
		}
		*/

		for (TrackCommand cmd : this) {
			cmd.build(dbb);
		}

		if (terminate) {
			dbb.putByte(0);
		}

		fileLen = dbb.position() - filePos;
	}

	private static void addDelay(DynamicByteBuffer dbb, int ticks)
	{
		int maxDelay = ((0x7F & 7) << 8) + 0xFF + 0x78; // equals 0x877 (2167)

		while (ticks > maxDelay) {
			addDelay(dbb, maxDelay);
			ticks -= maxDelay;
		}

		if (ticks >= 0x78) {
			// two byte encoding
			int amt = ticks - 0x78;
			int low = amt & 0xFF;
			int high = (amt >> 8) & 0x7;

			dbb.putByte(high | 0x78);
			dbb.putByte(low);
		}
		else {
			dbb.putByte(ticks);
		}
	}
}
