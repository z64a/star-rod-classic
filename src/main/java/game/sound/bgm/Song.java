package game.sound.bgm;

import static game.sound.DrumPreset.DrumKey.TAG_DRUM;
import static game.sound.InstrumentPreset.InstrumentKey.TAG_INSTRUMENT;
import static game.sound.bgm.SongKey.*;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import org.w3c.dom.Element;

import app.input.IOUtils;
import game.sound.DrumPreset;
import game.sound.InstrumentPreset;
import game.sound.bgm.Composition.CompCommand;
import game.sound.bgm.Track.TrackBranch;
import util.DynamicByteBuffer;
import util.Logger;
import util.MathUtil;
import util.xml.XmlWrapper.XmlReader;
import util.xml.XmlWrapper.XmlSerializable;
import util.xml.XmlWrapper.XmlTag;
import util.xml.XmlWrapper.XmlWriter;

public class Song implements XmlSerializable
{
	private static final int NUM_COMPOSITIONS = 4;

	private static final int[] TIMING_PRESET_MAP = {
			48, 24, 32, 40,
			48, 56, 64, 48,
	};

	private static final int DEFAULT_MEASURE_TICKS = 96; // 24 ticks per beat x 4 beats per measure

	public static final int MAX_BRANCH_OPTIONS = 16; // chosen for star rod, not required by the game engine

	public String name;
	private int timingPreset;

	public int branchMeasure = DEFAULT_MEASURE_TICKS;
	public int branchOptions = 1;

	private ArrayList<BGMPart> parts;
	private HashMap<Integer, BGMPart> partMap;

	private Composition[] compositions = new Composition[NUM_COMPOSITIONS];
	private ArrayList<Phrase> phrases = new ArrayList<>();

	private ArrayList<InstrumentPreset> instruments = new ArrayList<>();
	private ArrayList<DrumPreset> drums = new ArrayList<>();

	private transient int emptyBranchOffset;
	private transient int emptyBranchLength;

	public Song() throws IOException
	{
		for (int i = 0; i < NUM_COMPOSITIONS; i++) {
			compositions[i] = new Composition(this);
			compositions[i].index = i;
			compositions[i].enabled = false;
		}
	}

	public Song(File bgmFile) throws IOException
	{
		this();

		ByteBuffer bb = IOUtils.getDirectBuffer(bgmFile);

		parts = new ArrayList<>();
		partMap = new HashMap<>();

		addPart(new BGMPart(0, 0x24, "Header"));
		String signature = "" + (char) bb.get() + (char) bb.get() + (char) bb.get() + (char) bb.get();
		assert (signature.equals("BGM ")) : "Signature was " + signature; // 'BGM '
		int fileLength = bb.getInt();
		name = "" + (char) bb.get() + (char) bb.get() + (char) bb.get() + (char) bb.get();
		name = name.trim();
		int unk_0C = bb.getInt();
		assert (unk_0C == 0);

		timingPreset = bb.get() & 7;
		assert (timingPreset == 4); // --> 48

		byte unk_11 = bb.get();
		assert (unk_11 == 0);
		byte unk_12 = bb.get();
		assert (unk_12 == 0);
		byte unk_13 = bb.get();
		assert (unk_13 == 0);

		int[] compOffsets = new int[4];
		compOffsets[0] = (bb.getShort() & 0xFFFF) << 2;
		compOffsets[1] = (bb.getShort() & 0xFFFF) << 2;
		compOffsets[2] = (bb.getShort() & 0xFFFF) << 2;
		compOffsets[3] = (bb.getShort() & 0xFFFF) << 2;

		int drumOffset = (bb.getShort() & 0xFFFF) << 2;
		int drumCount = bb.getShort() & 0xFFFF;
		int insOffset = (bb.getShort() & 0xFFFF) << 2;
		int insCount = bb.getShort() & 0xFFFF;

		System.out.printf("Compositions: %X %X %X %X%n",
			compOffsets[0], compOffsets[1],
			compOffsets[2], compOffsets[3]);
		System.out.printf("Drums: %X (x%d)%n", drumOffset, drumCount);
		System.out.printf("Instruments: %X (x%d)%n", insOffset, insCount);

		if (drumCount > 0) {
			assert (drumOffset == bb.position());
			for (int i = 0; i < drumCount; i++) {
				addPart(new BGMPart(bb.position(), bb.position() + 0xC, String.format("Drum %X", i)));

				DrumPreset drum = new DrumPreset(bb);
				drums.add(drum);
			}
		}

		if (insCount > 0) {
			assert (insOffset == bb.position());
			for (int i = 0; i < insCount; i++) {
				addPart(new BGMPart(bb.position(), bb.position() + 0x8, String.format("Instrument %X", i)));

				InstrumentPreset ins = new InstrumentPreset(bb);
				instruments.add(ins);
			}
		}

		for (int i = 0; i < NUM_COMPOSITIONS; i++) {
			if (compOffsets[i] == 0)
				continue;

			compositions[i] = new Composition(this, bb, i, compOffsets[i]);
			compositions[i].index = i;
			compositions[i].enabled = true;

			System.out.println("*************************************************");
			for (CompCommand cmd : compositions[i].commands) {
				cmd.print();
			}
			System.out.println("-------------------------------------------------");
		}

		System.out.println();

		findMissingPhrases(bb);

		printBreakdown(bb.capacity());
	}

	public void build(File outFile) throws IOException
	{
		reindex();

		DynamicByteBuffer dbb = new DynamicByteBuffer();

		// skip header -- we will do it at the very end
		dbb.position(0x24);

		int drumsOffset = dbb.position();
		for (DrumPreset drum : drums) {
			drum.put(dbb);
		}

		int insOffset = dbb.position();
		for (InstrumentPreset ins : instruments) {
			ins.put(dbb);
		}

		for (int i = 0; i < NUM_COMPOSITIONS; i++) {
			Composition comp = compositions[i];
			if (comp.enabled) {
				comp.build(dbb);
			}
			else {
				comp.filePos = 0;
			}
		}

		// phrase tables and non-branching tracks go next
		for (Phrase p : phrases) {
			p.build(dbb);
		}

		// branching tracks occur after all other phrases
		for (Phrase p : phrases) {
			p.buildBranchTracks(dbb);
		}

		// actual branch jump tables and command streams for each option
		buildBranches(dbb);

		// align end of file
		int endOffset = dbb.position();
		dbb.align(16);

		for (int i = 0; i < NUM_COMPOSITIONS; i++) {
			compositions[i].updateRefs(dbb);
		}

		for (Phrase p : phrases) {
			p.updateRefs(dbb);
		}

		// write header
		dbb.position(0);

		// 0x0
		dbb.putUTF8("BGM ", false);
		dbb.putInt(endOffset);
		dbb.putUTF8(String.format("%-4s", name), false);
		dbb.skip(4);

		// 0x10
		dbb.putByte(timingPreset & 7);
		dbb.skip(3);

		// 0x14
		for (int i = 0; i < NUM_COMPOSITIONS; i++) {
			dbb.putShort(compositions[i].filePos >> 2);
		}

		// 0x1C
		dbb.putShort(drums.size() > 0 ? (drumsOffset >> 2) : 0);
		dbb.putShort(drums.size());

		// 0x20
		dbb.putShort(instruments.size() > 0 ? (insOffset >> 2) : 0);
		dbb.putShort(instruments.size());

		IOUtils.writeBufferToFile(dbb.getFixedBuffer(), outFile);
	}

	private void buildBranches(DynamicByteBuffer dbb)
	{
		List<TrackBranch> branches = new ArrayList<>();

		for (Phrase phrase : phrases) {
			for (Track track : phrase.tracks) {
				branches.addAll(track.branches);
			}
		}

		if (branches.isEmpty()) {
			return;
		}

		// reserve room for branch tables
		for (TrackBranch branch : branches) {
			branch.tablePos = dbb.position();
			dbb.skip(3 * branchOptions);
		}

		// write empty branch (will be reused as needed)
		emptyBranchOffset = dbb.position();

		//TODO support measures which require 2-byte delay length
		dbb.putByte(branchMeasure);
		dbb.putByte(0);
		emptyBranchLength = 2;

		for (TrackBranch branch : branches) {
			branch.options[0].filePos = emptyBranchOffset;
			branch.options[0].fileLen = emptyBranchLength;
		}

		// we have to write streams in this option-major ordering to match
		for (int i = 1; i < branchOptions; i++) {
			for (TrackBranch branch : branches) {
				CommandStream stream = branch.options[i];
				stream.build(dbb, true);
			}
		}

		dbb.pushPosition();

		// write the branch tables
		for (TrackBranch branch : branches) {
			dbb.position(branch.tablePos);
			for (int i = 0; i < branchOptions; i++) {
				CommandStream stream = branch.options[i];
				dbb.putShort(stream.filePos);
				dbb.putByte(stream.isDrum ? 1 : 0);
			}
		}

		dbb.popPosition();
	}

	private static record Region(int start, int end)
	{
		public int size()
		{
			return end - start;
		}
	}

	private static void checkCandidate(ByteBuffer bb, int start, int end, ArrayList<Region> candidates)
	{
		if (end - start > 3) {
			int padStart = (start + 3) & -4;
			boolean padded = true;

			bb.position(start);
			for (int i = start; i < padStart; i++) {
				if (bb.get() != 0)
					padded = false;
			}

			if (padded) {
				candidates.add(new Region(padStart, end));
			}
		}
	}

	private static List<Region> findCandidates(ByteBuffer bb, Iterable<BGMPart> parts)
	{
		ArrayList<Region> candidates = new ArrayList<>();

		BGMPart prev = null;

		for (BGMPart part : parts) {
			if (prev != null) {
				if (prev.end != part.start) {
					checkCandidate(bb, prev.end, part.start, candidates);
				}
			}
			prev = part;
		}

		if (prev != null) {
			if (prev.end != bb.capacity()) {
				checkCandidate(bb, prev.end, bb.capacity(), candidates);
			}
		}

		return candidates;
	}

	// some phrases are not referenced in any compositions, we need to find them by
	// looking for gaps between known sections of the file. several passes are needed
	private void findMissingPhrases(ByteBuffer bb)
	{
		boolean tryAgain = true;
		int found = 0;

		while (tryAgain) {
			Collections.sort(parts);
			tryAgain = false;

			List<Region> candidates = findCandidates(bb, parts);
			if (candidates.isEmpty()) {
				break;
			}

			for (Region r : candidates) {
				bb.position(r.start);
				// phrase tables almost always begin with 0x40 for the offset of track 0
				// BUT there is also a single blank one which we identify by size
				if (r.size() >= 0x40 && (bb.getShort() == 0x40 || r.size() == 0x40)) {
					Phrase p = new Phrase(this, bb, r.start);
					phrases.add(p);
					found++;
					tryAgain = true;
				}
			}
		}

		// sort phrases
		phrases.sort((p1, p2) -> Integer.compare(p1.filePos, p2.filePos));
	}

	private void printBreakdown(int fileLen)
	{
		// iterate again to print
		Collections.sort(parts);

		int last = 0;
		BGMPart prev = null;
		System.out.println("BREAKDOWN: " + name);

		for (BGMPart part : parts) {
			if (prev != null) {
				if (prev.end != part.start) {
					String s = String.format("%-5X %-5X (%X) ", prev.end, part.start, part.start - prev.end);
					System.out.printf("%-20s -- PAD --%n", s);
					if (part.start - prev.end > 3)
						System.out.println("!?! MISSING DATA !?!");
				}
			}

			System.out.println(part);

			last = part.end;
			prev = part;
		}
		if (prev != null) {
			if (prev.end != fileLen) {
				String s = String.format("%-5X %-5X (%X) ", prev.end, fileLen, fileLen - prev.end);
				System.out.printf("%-20s -- PAD --%n", s);
			}
		}

		System.out.printf("FILE END: %X%n", fileLen);
		System.out.println();
	}

	private void reindex()
	{
		for (int i = 0; i < NUM_COMPOSITIONS; i++) {
			compositions[i].index = i;
		}

		int phraseID = 1;

		for (Phrase p : phrases) {
			p.serialID = phraseID++;
			p.reindex();
		}
	}

	public Phrase addPhrase(ByteBuffer bb, int pos)
	{
		for (Phrase p : phrases) {
			if (pos == p.filePos) {
				return p;
			}
		}

		Phrase p = new Phrase(this, bb, pos);
		phrases.add(p);
		return p;
	}

	public void addPart(BGMPart part)
	{
		if (!partMap.containsKey(part.start)) {
			parts.add(part);
			partMap.put(part.start, part);
		}
	}

	public BGMPart getPart(int offset)
	{
		return partMap.get(offset);
	}

	@Override
	public void fromXML(XmlReader xmr, Element root)
	{
		name = xmr.getAttribute(root, ATTR_NAME);
		if (name.length() > 4) {
			Logger.logfWarning("Invalid name for BGM file will be truncated: " + name);
			name = name.substring(0, 4);
		}

		timingPreset = xmr.readInt(root, ATTR_TIMING);
		branchMeasure = xmr.readInt(root, ATTR_MEASURE);
		branchOptions = xmr.readInt(root, ATTR_BRANCHES);

		branchOptions = MathUtil.clamp(branchOptions, 0, MAX_BRANCH_OPTIONS);

		Element insListElem = xmr.getUniqueRequiredTag(root, TAG_INS_LIST);

		for (Element elem : xmr.getTags(insListElem, TAG_INSTRUMENT)) {
			InstrumentPreset ins = new InstrumentPreset(xmr, elem);
			instruments.add(ins);
		}

		Element drumListElem = xmr.getUniqueRequiredTag(root, TAG_DRUM_LIST);

		for (Element elem : xmr.getTags(drumListElem, TAG_DRUM)) {
			DrumPreset drum = new DrumPreset(xmr, elem);
			drums.add(drum);
		}

		Element compListElem = xmr.getUniqueRequiredTag(root, TAG_COMP_LIST);

		for (Element elem : xmr.getTags(compListElem, TAG_COMPOSITION)) {
			Composition comp = new Composition(this);
			comp.fromXML(xmr, elem);
			comp.enabled = true;

			if (comp.index >= 0 && comp.index < NUM_COMPOSITIONS) {
				compositions[comp.index] = comp;
			}
			else {
				xmr.complain("Composition has invalid index: " + comp.index);
			}
		}

		Element phraseListElem = xmr.getUniqueRequiredTag(root, TAG_PHRASE_LIST);

		HashMap<Integer, Phrase> phraseLookup = new HashMap<>();

		for (Element elem : xmr.getTags(phraseListElem, TAG_PHRASE)) {
			Phrase p = new Phrase(this);
			p.fromXML(xmr, elem);
			phrases.add(p);
			phraseLookup.put(p.serialID, p);
		}

		for (Composition comp : compositions) {
			comp.assignPhrases(phraseLookup);
		}
	}

	@Override
	public void toXML(XmlWriter xmw)
	{
		reindex();

		XmlTag root = xmw.createTag(TAG_SONG, false);
		xmw.addAttribute(root, ATTR_NAME, name);
		xmw.addInt(root, ATTR_TIMING, timingPreset);
		xmw.addInt(root, ATTR_MEASURE, branchMeasure);
		xmw.addInt(root, ATTR_BRANCHES, branchOptions);
		xmw.openTag(root);

		XmlTag insListTag = xmw.createTag(TAG_INS_LIST, false);
		xmw.openTag(insListTag);
		for (InstrumentPreset ins : instruments) {
			ins.toXML(xmw);
		}
		xmw.closeTag(insListTag);

		XmlTag drumListTag = xmw.createTag(TAG_DRUM_LIST, false);
		xmw.openTag(drumListTag);
		for (DrumPreset drum : drums) {
			drum.toXML(xmw);
		}
		xmw.closeTag(drumListTag);

		XmlTag compListTag = xmw.createTag(TAG_COMP_LIST, false);
		xmw.openTag(compListTag);

		for (int i = 0; i < NUM_COMPOSITIONS; i++) {
			if (compositions[i].enabled)
				compositions[i].toXML(xmw);
		}

		xmw.closeTag(compListTag);

		XmlTag phraseListTag = xmw.createTag(TAG_PHRASE_LIST, false);
		xmw.openTag(phraseListTag);

		for (Phrase p : phrases)
			p.toXML(xmw);

		xmw.closeTag(phraseListTag);

		xmw.closeTag(root);
		xmw.save();
	}

	public static class BGMPart implements Comparable<BGMPart>
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
