package game.sound.bgm;

import static game.sound.bgm.SongKey.*;

import java.nio.ByteBuffer;
import java.util.HashMap;

import org.w3c.dom.Element;

import game.sound.SoundXml;
import game.sound.bgm.Song.BGMPart;
import util.DynamicByteBuffer;
import util.xml.XmlWrapper.XmlReader;
import util.xml.XmlWrapper.XmlSerializable;
import util.xml.XmlWrapper.XmlTag;
import util.xml.XmlWrapper.XmlWriter;

public class Phrase implements XmlSerializable
{
	private static final int NUM_TRACKS = 16;

	public final Song song;
	public Track[] tracks = new Track[NUM_TRACKS];

	public transient int serialID; // for serialization only
	public transient int filePos; // file offset where phrase begins

	public Phrase(Song song)
	{
		this.song = song;

		for (int i = 0; i < NUM_TRACKS; i++) {
			tracks[i] = new Track(this);
			tracks[i].index = i;
			tracks[i].defined = false;
			tracks[i].enabled = false;
		}
	}

	public Phrase(Song song, ByteBuffer bb, int pos)
	{
		this(song);

		this.filePos = pos;

		int[] trackInfo = new int[NUM_TRACKS];
		int[] firstSeen = new int[NUM_TRACKS];
		HashMap<Integer, Integer> seenTracks = new HashMap<>(2 * NUM_TRACKS);

		bb.position(pos);
		for (int i = 0; i < NUM_TRACKS; i++) {
			int v = bb.getInt();
			trackInfo[i] = v;

			if (seenTracks.containsKey(v)) {
				firstSeen[i] = seenTracks.get(v);
			}
			else {
				firstSeen[i] = i;
				seenTracks.put(v, i);
			}
		}

		for (int i = 0; i < NUM_TRACKS; i++) {
			tracks[i] = new Track(this, i, bb, trackInfo[i]);

			if (trackInfo[i] != 0 && firstSeen[i] != i)
				tracks[i].copyOf = firstSeen[i];
		}

		song.addPart(new BGMPart(filePos, filePos + 0x40, String.format("Phrase %X", pos)));
	}

	public void reindex()
	{
		for (Track track : tracks) {
			track.reindex();
		}
	}

	public void beforeBuild()
	{
		for (Track track : tracks) {
			if (track.enabled)
				track.beforeBuild();
		}
	}

	public void build(DynamicByteBuffer dbb)
	{
		dbb.align(4);

		filePos = dbb.position();
		dbb.skip(0x40);

		// write streams for non-branching tracks
		for (Track track : tracks) {
			if (track.enabled && track.copyOf < 0 && !track.hasBranch()) {
				track.build(dbb);
			}
		}
	}

	public void buildBranchTracks(DynamicByteBuffer dbb)
	{
		// write streams for branching tracks
		for (Track track : tracks) {
			if (track.enabled && track.copyOf < 0 && track.hasBranch()) {
				track.build(dbb);
			}
		}
	}

	public void updateRefs(DynamicByteBuffer dbb)
	{
		// track table
		dbb.position(filePos);
		for (Track track : tracks) {
			if (!track.enabled)
				dbb.putInt(0);
			else if (track.copyOf < 0)
				dbb.putInt(track.getTrackInfo());
			else
				dbb.putInt(tracks[track.copyOf].getTrackInfo());
		}

		// update references in tracks
		for (Track track : tracks) {
			if (track.enabled && track.copyOf < 0) {
				track.updateRefs(dbb);
			}
		}
	}

	@Override
	public void fromXML(XmlReader xmr, Element elem)
	{
		serialID = SoundXml.readInt(xmr, elem, ATTR_SERIAL_ID, 1, 0x7FFFFFFF);

		for (Element child : xmr.getTags(elem, TAG_TRACK)) {
			Track track = new Track(this);
			track.fromXML(xmr, child);

			if (tracks[track.index].defined)
				xmr.complain("Track index is defined more than once: " + track.index);
			tracks[track.index] = track;
		}

		for (Track track : tracks) {
			if (track.enabled && track.linkedIndex >= 0 && !tracks[track.linkedIndex].enabled)
				xmr.complain("Track " + track.index + " links to missing track " + track.linkedIndex);
		}
	}

	@Override
	public void toXML(XmlWriter xmw)
	{
		XmlTag tag = xmw.createTag(TAG_PHRASE, false);

		xmw.addInt(tag, ATTR_SERIAL_ID, serialID);

		xmw.openTag(tag);

		for (int i = 0; i < NUM_TRACKS; i++) {
			Track track = tracks[i];

			if (track.defined) {
				track.index = i;
				track.toXML(xmw);
			}
		}

		xmw.closeTag(tag);
	}
}
