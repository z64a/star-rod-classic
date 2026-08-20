package game.sound.bgm;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import game.sound.bgm.Track.Note;
import game.sound.bgm.Track.TrackCommand;
import util.DynamicByteBuffer;
import util.xml.XmlWrapper.XmlTag;
import util.xml.XmlWrapper.XmlWriter;

public class CommandStream
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

	public ArrayList<TrackCommand> all = new ArrayList<>();

	public ArrayList<TrackCommand> properties = new ArrayList<>();
	public ArrayList<TrackCommand> notes = new ArrayList<>();

	public ArrayList<TrackCommand> trackPan = new ArrayList<>();
	public ArrayList<TrackCommand> trackVol = new ArrayList<>();
	public ArrayList<TrackCommand> trackDetune = new ArrayList<>();

	public ArrayList<TrackCommand> insPan = new ArrayList<>();
	public ArrayList<TrackCommand> insVol = new ArrayList<>();
	public ArrayList<TrackCommand> insDetune = new ArrayList<>();

	public CommandStream(StreamType type)
	{
		this.type = type;
	}

	public List<TrackCommand> getCommands()
	{
		return Collections.unmodifiableList(all);
	}

	public boolean isDrum()
	{
		return isDrum;
	}

	public int getDuration()
	{
		return duration;
	}

	public void toXML(XmlWriter xmw, XmlTag tag)
	{
		xmw.openTag(tag);
		for (TrackCommand cmd : all) {
			cmd.toXML(xmw);
		}
		xmw.closeTag(tag);
	}

	public void split()
	{
		properties.clear();
		notes.clear();
		trackPan.clear();
		trackVol.clear();
		trackDetune.clear();
		insPan.clear();
		insVol.clear();
		insDetune.clear();

		for (TrackCommand cmd : all) {
			if (cmd instanceof Note note) {

			}
		}
	}

	public void collect()
	{
		// TODO rebuild the command stream from the editor representation.
	}

	public void build(DynamicByteBuffer dbb, boolean terminate)
	{
		filePos = dbb.position();

		for (TrackCommand cmd : all) {
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
