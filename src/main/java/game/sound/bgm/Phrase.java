package game.sound.bgm;

import static game.sound.bgm.SongKey.*;

import java.nio.ByteBuffer;
import java.util.HashMap;

import org.w3c.dom.Element;

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

			if (firstSeen[i] != i)
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
			if (track.copyOf < 0)
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
		serialID = xmr.readInt(elem, ATTR_SERIAL_ID);

		if (xmr.hasAttribute(elem, ATTR_FILE_POS))
			filePos = xmr.readHex(elem, ATTR_FILE_POS);

		for (Element child : xmr.getTags(elem, TAG_TRACK)) {
			Track track = new Track(this);
			track.fromXML(xmr, child);
			track.enabled = true;

			if (track.index >= 0 && track.index < NUM_TRACKS) {
				tracks[track.index] = track;
			}
			else {
				xmr.complain("Track has invalid index: " + track.index);
			}
		}
	}

	@Override
	public void toXML(XmlWriter xmw)
	{
		XmlTag tag = xmw.createTag(TAG_PHRASE, false);

		xmw.addInt(tag, ATTR_SERIAL_ID, serialID);
		if (filePos > 0)
			xmw.addHex(tag, ATTR_FILE_POS, filePos);

		xmw.openTag(tag);

		for (int i = 0; i < NUM_TRACKS; i++) {
			Track track = tracks[i];

			if (track.enabled) {
				track.index = i;
				track.toXML(xmw);
			}
		}

		xmw.closeTag(tag);
	}
}
