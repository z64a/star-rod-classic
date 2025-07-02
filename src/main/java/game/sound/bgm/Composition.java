package game.sound.bgm;

import static game.sound.bgm.SongKey.*;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;

import org.w3c.dom.Element;
import org.w3c.dom.Node;

import app.StarRodException;
import game.sound.bgm.Song.BGMPart;
import util.DynamicByteBuffer;
import util.xml.XmlWrapper.XmlReader;
import util.xml.XmlWrapper.XmlSerializable;
import util.xml.XmlWrapper.XmlTag;
import util.xml.XmlWrapper.XmlWriter;

public class Composition implements XmlSerializable
{
	private static final int COMP_CMD_END = 0;
	private static final int COMP_CMD_PLAY = 1;
	private static final int COMP_CMD_WAIT = 4;
	private static final int COMP_CMD_START_LOOP = 3; // support up to 32 loop start points, up to a nested depth of 4
	private static final int COMP_CMD_END_LOOP = 5; // coutn of zero means 'forever'
	private static final int COMP_CMD_COND_LOOP_FALSE = 6;
	private static final int COMP_CMD_COND_LOOP_TRUE = 7;

	private final Song song;
	protected ArrayList<CompCommand> commands = new ArrayList<>();

	public boolean enabled;
	public int index; // which variation (0-3) this corresponds to

	public transient int filePos; // file offset where composition begins

	protected Composition(Song song)
	{
		this.song = song;
	}

	protected Composition(Song song, ByteBuffer bb, int index, int filePos)
	{
		this.song = song;
		this.filePos = filePos;
		this.index = index;

		int v;
		bb.position(filePos);

		int startPos = bb.position();
		while ((v = bb.getInt()) != COMP_CMD_END) {
			int type = v >> 28;
			int data = v & 0xFFFFFFF;
			int offset;
			int loopIndex;
			int loopCount;

			switch (type) {
				case COMP_CMD_PLAY:
					offset = data << 2;
					commands.add(new PlayCompCommand(this, bb, startPos + offset));
					break;

				case COMP_CMD_START_LOOP:
					loopIndex = data & 0x1F; // can have up to 32 loop start labels per composition
					commands.add(new StartLoopCompCommand(this, loopIndex));
					break;
				case COMP_CMD_END_LOOP:
					loopIndex = data & 0x1F; // 01F (bits 0-4)
					loopCount = (data >> 5) & 0x7F; // FE0 (bits 5-11)
					commands.add(new EndLoopCompCommand(this, loopIndex, loopCount));
					break;

				// unused commands
				case COMP_CMD_WAIT:
				case COMP_CMD_COND_LOOP_FALSE:
				case COMP_CMD_COND_LOOP_TRUE:
				default:
					throw new StarRodException("Unknown composition command: %08X", v);
			}
		}

		song.addPart(new BGMPart(startPos, bb.position(), "Composition " + index));
		System.out.println();
	}

	public void assignPhrases(HashMap<Integer, Phrase> phraseLookup)
	{
		for (CompCommand cmd : commands) {
			if (cmd instanceof PlayCompCommand play) {
				Phrase p = phraseLookup.get(play.phraseID);
				if (p == null)
					throw new StarRodException("Composition %d references unknown Phrase %d ", index, play.phraseID);
				play.phrase = p;
			}
		}
	}

	public void build(DynamicByteBuffer dbb)
	{
		filePos = dbb.position();

		for (CompCommand cmd : commands) {
			cmd.build(dbb);
		}

		dbb.putInt(COMP_CMD_END << 0x28);
	}

	public void updateRefs(DynamicByteBuffer dbb)
	{
		for (CompCommand cmd : commands) {
			cmd.update(dbb);
		}
	}

	@Override
	public void fromXML(XmlReader xmr, Element elem)
	{
		index = xmr.readInt(elem, ATTR_INDEX);

		for (Node child = elem.getFirstChild(); child != null; child = child.getNextSibling()) {
			if (child instanceof Element cmd) {
				commands.add(makeCommand(xmr, cmd));
			}
		}
	}

	private CompCommand makeCommand(XmlReader xmr, Element elem)
	{
		String tagName = elem.getTagName();
		CompCommand cmd;

		switch (tagName) {
			// @formatter:off
			case "Play":		cmd = new PlayCompCommand(this); break;
			case "StartLoop":  	cmd = new StartLoopCompCommand(this); break;
			case "EndLoop":		cmd = new EndLoopCompCommand(this); break;
			// @formatter:on
			default:
				throw new IllegalArgumentException("Unknown command tag: " + tagName);
		}

		cmd.fromXML(xmr, elem);
		return cmd;
	}

	@Override
	public void toXML(XmlWriter xmw)
	{
		XmlTag tag = xmw.createTag(TAG_COMPOSITION, false);
		xmw.addInt(tag, ATTR_INDEX, index);
		xmw.openTag(tag);

		for (CompCommand cmd : commands) {
			cmd.toXML(xmw);
		}

		xmw.closeTag(tag);
	}

	public static abstract class CompCommand implements XmlSerializable
	{
		public final Composition comp;

		public CompCommand(Composition comp)
		{
			this.comp = comp;
		}

		public abstract void build(DynamicByteBuffer dbb);

		// post-build update pass
		public void update(DynamicByteBuffer dbb)
		{}

		public abstract void print();
	}

	public static class PlayCompCommand extends CompCommand
	{
		private int phraseID;
		private Phrase phrase;

		private int filePos;

		public PlayCompCommand(Composition comp)
		{
			super(comp); // for fromXML
		}

		public PlayCompCommand(Composition comp, ByteBuffer bb, int phraseOffset)
		{
			super(comp);
			this.phraseID = phraseOffset;

			int nextPos = bb.position();
			phrase = comp.song.addPhrase(bb, phraseOffset);
			bb.position(nextPos);
		}

		@Override
		public void fromXML(XmlReader xmr, Element elem)
		{
			phraseID = xmr.readInt(elem, ATTR_SERIAL_ID);
		}

		@Override
		public void toXML(XmlWriter xmw)
		{
			XmlTag tag = xmw.createTag(TAG_COMP_PLAY, true);
			xmw.addInt(tag, ATTR_SERIAL_ID, phrase.serialID);
			xmw.printTag(tag);
		}

		@Override
		public void build(DynamicByteBuffer dbb)
		{
			filePos = dbb.position();
			dbb.putInt(0); // defer real write until update (after phrase IDs have been assigned)
		}

		@Override
		public void update(DynamicByteBuffer dbb)
		{
			dbb.position(filePos);
			int offset = (phrase.filePos - comp.filePos) >> 2;

			dbb.putInt((COMP_CMD_PLAY << 28) | (offset & 0xFFFFFFF));
		}

		@Override
		public void print()
		{
			System.out.printf("PLAY: %X%n", phraseID);
		}
	}

	public static class StartLoopCompCommand extends CompCommand
	{
		private int loopIndex;

		public StartLoopCompCommand(Composition comp)
		{
			super(comp); // for fromXML
		}

		public StartLoopCompCommand(Composition comp, int loopIndex)
		{
			super(comp);
			this.loopIndex = loopIndex;
		}

		@Override
		public void fromXML(XmlReader xmr, Element elem)
		{
			loopIndex = xmr.readInt(elem, ATTR_COMP_LOOP_INDEX);
		}

		@Override
		public void toXML(XmlWriter xmw)
		{
			XmlTag tag = xmw.createTag(TAG_COMP_START_LOOP, true);
			xmw.addInt(tag, ATTR_COMP_LOOP_INDEX, loopIndex);
			xmw.printTag(tag);
		}

		@Override
		public void build(DynamicByteBuffer dbb)
		{
			dbb.putInt(COMP_CMD_START_LOOP << 28 | (loopIndex & 0xFFFFFFF));
		}

		@Override
		public void print()
		{
			System.out.printf("LOOP_START: %X%n", loopIndex);
		}
	}

	public static class EndLoopCompCommand extends CompCommand
	{
		private int loopIndex;
		private int loopCount;

		public EndLoopCompCommand(Composition comp)
		{
			super(comp); // for fromXML
		}

		public EndLoopCompCommand(Composition comp, int loopIndex, int loopCount)
		{
			super(comp);
			this.loopIndex = loopIndex;
			this.loopCount = loopCount;
		}

		@Override
		public void fromXML(XmlReader xmr, Element elem)
		{
			loopIndex = xmr.readInt(elem, ATTR_COMP_LOOP_INDEX);
			loopCount = xmr.readInt(elem, ATTR_COMP_LOOP_COUNT);
		}

		@Override
		public void toXML(XmlWriter xmw)
		{
			XmlTag tag = xmw.createTag(TAG_COMP_END_LOOP, true);
			xmw.addInt(tag, ATTR_COMP_LOOP_INDEX, loopIndex);
			xmw.addInt(tag, ATTR_COMP_LOOP_COUNT, loopCount);
			xmw.printTag(tag);
		}

		@Override
		public void build(DynamicByteBuffer dbb)
		{
			int data = loopIndex & 0x1F; // (bits 0-4)
			data |= (loopCount & 0x7F) << 5; // (bits 5-11)

			dbb.putInt(COMP_CMD_END_LOOP << 28 | data);
		}

		@Override
		public void print()
		{
			if (loopCount != 0)
				System.out.printf("LOOP_END: %X (x%d)%n", loopIndex, loopCount);
			else
				System.out.printf("LOOP_END: %X (forever)%n", loopIndex);
		}
	}
}
