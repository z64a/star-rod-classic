package game.sound;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;

import app.Directories;
import app.Environment;
import app.StarRodException;
import app.input.IOUtils;

public class AudioAnalyzerBGM
{
	public static void main(String[] args) throws IOException
	{
		Environment.initialize();
		new AudioAnalyzerBGM();
		Environment.exit();
	}

	private AudioAnalyzerBGM() throws IOException
	{
		Collection<File> files = IOUtils.getFilesWithExtension(Directories.DUMP_AUDIO, new String[] { "bgm" }, true);
		ArrayList<File> fileList = new ArrayList<>(files);
		Collections.reverse(fileList);
		for (File f : fileList) {
			System.out.println("------------------------------------");
			System.out.println(f.getName() + " ");
			ByteBuffer bb = IOUtils.getDirectBuffer(f);
			new Song(bb);
		}
	}

	private static class Song
	{
		ArrayList<BGMPart> parts;
		HashMap<Integer, BGMPart> partMap;

		Segment[] segments = new Segment[4];
		ArrayList<SubSegment> subsegments = new ArrayList<>();

		public Song(ByteBuffer bb)
		{
			parts = new ArrayList<>();
			partMap = new HashMap<>();

			addPart(new BGMPart(0, 0x24, "Header"));
			String signature = "" + (char) bb.get() + (char) bb.get() + (char) bb.get() + (char) bb.get();
			assert (signature.equals("BGM ")) : "Signature was " + signature; // 'BGM '
			int fileLength = bb.getInt();
			String songName = "" + (char) bb.get() + (char) bb.get() + (char) bb.get() + (char) bb.get();
			int unk_0C = bb.getInt();
			assert (unk_0C == 0);
			int numSegments = bb.get();
			byte unk_11 = bb.get();
			assert (unk_11 == 0);
			byte unk_12 = bb.get();
			assert (unk_11 == 0);
			byte unk_13 = bb.get();
			assert (unk_11 == 0);
			short[] segmentOffsets = new short[4];
			segmentOffsets[0] = bb.getShort();
			segmentOffsets[1] = bb.getShort();
			segmentOffsets[2] = bb.getShort();
			segmentOffsets[3] = bb.getShort();
			short drums = bb.getShort();
			short drumCount = bb.getShort();
			short instruments = bb.getShort();
			short instrumentCount = bb.getShort();

			System.out.printf("segments: %X %X %X %X%n",
				segmentOffsets[0] << 2, segmentOffsets[1] << 2,
				segmentOffsets[2] << 2, segmentOffsets[3] << 2);
			System.out.printf("drums: %X (x%d)%n", drums, drumCount);
			System.out.printf("instruments: %X (x%d)%n", instruments, instrumentCount);
			assert (numSegments == 4) : numSegments;

			if (drumCount > 0) {
				assert (drums * 4 == bb.position());
				for (int i = 0; i < drumCount; i++) {
					addPart(new BGMPart(bb.position(), bb.position() + 0xC, String.format("Drum %X", i)));
					bb.getInt();
					bb.getInt();
					bb.getInt();
				}
			}

			if (instrumentCount > 0) {
				assert (instruments * 4 == bb.position());
				for (int i = 0; i < instrumentCount; i++) {
					addPart(new BGMPart(bb.position(), bb.position() + 0x8, String.format("Instrument %X", i)));
					bb.getInt();
					bb.getInt();
				}
			}

			for (int i = 0; i < segments.length; i++) {
				if (segmentOffsets[i] == 0) {
					segments[i] = null;
					continue;
				}

				segments[i] = new Segment(this, bb, i, segmentOffsets[i] << 2);
			}

			System.out.println();

			int last = 0;
			Collections.sort(parts);
			for (BGMPart part : parts) {
				System.out.println(part);

				//	assert(last == -1 || last == part.start);
				last = part.end;
			}
			System.out.printf("FILE END: %X%n", bb.capacity());
			System.out.println();
		}

		private SubSegment readSubsegment(ByteBuffer bb, int pos)
		{
			for (SubSegment subseg : subsegments) {
				if (pos == subseg.filePos) {
					return subseg;
				}
			}

			// read new sub seg
			SubSegment subseg = new SubSegment(this, bb, pos);
			subsegments.add(subseg);
			return subseg;
		}

		private void addPart(BGMPart part)
		{
			if (!partMap.containsKey(part.start)) {
				parts.add(part);
				partMap.put(part.start, part);
			}
		}

		private BGMPart getPart(int offset)
		{
			return partMap.get(offset);
		}
	}

	private static final int SEGMENT_CMD_END = 0;
	private static final int SEGMENT_CMD_PLAY = 1;
	private static final int SEGMENT_CMD_HALT = 4;
	private static final int SEGMENT_CMD_START_LOOP = 3;
	private static final int SEGMENT_CMD_END_LOOP = 5;
	private static final int SEGMENT_CMD_6 = 6;
	private static final int SEGMENT_CMD_7 = 7;

	private static class SegmentCommand
	{
		int type;
		int loopIndex;
		int loopCount;
		SubSegment subseg;

		transient int subsegPos;

		public SegmentCommand(int v)
		{
			type = v >> 28;
			int data = v & 0xFFFFFFF;

			switch (type) {
				case SEGMENT_CMD_PLAY:
					subsegPos = data;
					assert (subsegPos <= 3817) : subsegPos;
					break;

				case SEGMENT_CMD_START_LOOP:
					loopIndex = data & 0x1F;
					assert (loopIndex <= 2) : loopIndex;
					break;
				case SEGMENT_CMD_END_LOOP:
					loopIndex = data & 0x1F; // 01F (bits 0-4)
					loopCount = (data >> 5) & 0x7F; // FE0 (bits 5-11)
					assert (loopIndex <= 2) : loopIndex;
					assert (loopCount <= 9) : loopCount;
					break;

				// unused commands
				case SEGMENT_CMD_HALT:
				case SEGMENT_CMD_6:
				case SEGMENT_CMD_7:
				default:
					throw new StarRodException("Unknown segment command: %08X", v);
			}
		}
	}

	public static class Segment
	{
		private int filePos;
		private ArrayList<SegmentCommand> segmentCommands = new ArrayList<>();

		private Segment(Song song, ByteBuffer bb, int index, int filePos)
		{
			this.filePos = filePos;

			int v;
			bb.position(filePos);
			System.out.printf("%3X Segment %X: ", filePos, index);

			int startPos = bb.position();
			while ((v = bb.getInt()) != SEGMENT_CMD_END) {
				System.out.printf("%08X ", v);
				SegmentCommand cmd = new SegmentCommand(v);
				if (cmd.type == SEGMENT_CMD_PLAY) {
					int savedPos = bb.position();
					cmd.subseg = song.readSubsegment(bb, startPos + (cmd.subsegPos << 2));
					bb.position(savedPos);
				}
			}

			song.addPart(new BGMPart(startPos, bb.position(), "Segment " + index));
			System.out.println();
		}
	}

	private static final int SeqCmdArgCounts[] = {
			2, 1, 1, 1, 4, 3, 2, 0,
			2, 1, 1, 1, 1, 1, 1, 2,
			3, 1, 1, 0, 2, 1, 3, 1,
			0, 0, 0, 0, 3, 3, 3, 3
	};

	public static class SubSegment
	{
		private Track[] tracks = new Track[16];
		int filePos;

		public SubSegment(Song song, ByteBuffer bb, int pos)
		{
			this.filePos = pos;

			for (int i = 0; i < 16; i++) {
				bb.position(filePos + (i << 2));
				int trackInfo = bb.getInt();
				if (trackInfo == 0)
					continue;

				System.out.printf("Track %2d: %X%n", i, trackInfo);
				tracks[i] = new Track(trackInfo);
				int offset = (trackInfo >> 0x10) & 0xFFFF;
				int polyphonicIndex = (trackInfo >> 13) & 0x7;
				int parentIndex = (trackInfo >> 9) & 0xF;
				int unkFlag = (trackInfo >> 8) & 1; // 100
				int isDrumTrack = (trackInfo >> 7) & 1;

				bb.position(filePos + offset);

				while (true) {
					int op = (bb.get() & 0xFF);
					if (op == 0)
						break;

					if (op < 0x78) {
						int delay = op;
						System.out.printf("Delay: %d%n", delay);
					}
					else if (op < 0x80) {
						int delay = ((op & 7) << 8) + (bb.get() & 0xFF) + 0x78;
						System.out.printf("Delay: %d%n", delay);
					}
					else if (op < 0xD4) {
						int pitch = op & 0x7F;
						int velocity = (bb.get() & 0xFF);
						int length = (bb.get() & 0xFF);
						if (length >= 0xC0) {
							length = ((length & 0x3F) << 8) + (bb.get() & 0xFF) + 0xC0;
						}
						System.out.printf("Note: %d, %d, %d%n", pitch, velocity, length);
					}
					else if (op < 0xE0) {
						throw new StarRodException("Unknown track command: %02X", op);
					}
					else {
						System.out.printf("Cmd %02X: ", op);
						for (int j = 0; j < SeqCmdArgCounts[op - 0xE0]; j++) {
							System.out.printf("%02X ", bb.get());
						}
						System.out.println();
					}
				}

				song.addPart(new BGMPart(filePos + offset, bb.position(), String.format("Track %X-%d", pos, i)));
			}

			song.addPart(new BGMPart(filePos, filePos + 0x40, String.format("Subsegment %X", pos)));
		}

	}

	private static class Track
	{
		public Track(int trackInfo)
		{

		}
	}

	private static class BGMPart implements Comparable<BGMPart>
	{
		final String name;
		int start;
		int end;

		public BGMPart(int start, int end, String name)
		{
			this.start = start;
			this.end = end;
			this.name = name;
		}

		@Override
		public int compareTo(BGMPart o)
		{
			return this.start - o.start;
		}

		@Override
		public String toString()
		{
			String s;
			if (end >= 0)
				s = String.format("%-5X %-5X (%X) ", start, end, end - start);
			else
				s = String.format("%-5X ???   (???) ", start);
			return String.format("%-20s %s", s, name);
		}
	}
}
