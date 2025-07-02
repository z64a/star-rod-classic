package game.sound.bgm;

import static game.sound.bgm.SongKey.*;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;

import org.w3c.dom.Element;
import org.w3c.dom.Node;

import app.StarRodException;
import game.sound.bgm.CommandStream.StreamType;
import game.sound.bgm.Song.BGMPart;
import util.DynamicByteBuffer;
import util.xml.XmlWrapper.XmlReader;
import util.xml.XmlWrapper.XmlSerializable;
import util.xml.XmlWrapper.XmlTag;
import util.xml.XmlWrapper.XmlWriter;

public class Track implements XmlSerializable
{
	private static final int PhraseCmdArgCounts[] = {
			2, 1, 1, 1, 4, 3, 2, 0,
			2, 1, 1, 1, 1, 1, 1, 2,
			3, 1, 1, 0, 2, 1, 3, 1,
			0, 0, 0, 0, 3, 3, 3, 3
	};

	private static final int PolyphonicVoiceCounts[] = {
			0, 1, 0, 0,
			0, 2, 3, 4
	};

	private static enum CommandType
	{
		DELAY,
		NOTE,
		CALL,
		PROPERTY,
		DETUNE_CURVE,
		VOLUME_CURVE,
		PAN_CURVE
	}

	private static final int SPECIAL_OPCODE = 0xFF;

	public static boolean debugPrint = true;
	private static final String CMD_FMT = "%5X %5d: ";

	public final Phrase phrase;
	protected CommandStream commands = new CommandStream(StreamType.TRACK);
	protected ArrayList<TrackDetour> detours = new ArrayList<>();
	protected ArrayList<TrackBranch> branches = new ArrayList<>();

	// map serialID/filePos --> TrackDetour during dump and deserialization
	private transient HashMap<Integer, TrackDetour> detourLookup = new HashMap<>();
	private transient HashMap<Integer, TrackBranch> branchLookup = new HashMap<>();

	public int index;
	public boolean enabled;
	public boolean unkFlag;

	public int linkedIndex;
	public int polyphonicIndex = 1;
	public int polyphonicVoiceCount = PolyphonicVoiceCounts[polyphonicIndex];

	// allow tracks to be a direct duplicate of another track
	// only used for one phrase in 26_210.bgm (required to match)
	public int copyOf = -1;

	public Track(Phrase phrase)
	{
		this.phrase = phrase;
	}

	public int getTrackInfo()
	{
		if (!enabled)
			return 0;

		int info = 0;

		info |= ((commands.filePos - phrase.filePos) & 0xFFFF) << 16;
		info |= (polyphonicIndex & 7) << 13;
		info |= (linkedIndex & 0xF) << 9;
		if (!unkFlag)
			info |= 1 << 8;
		if (commands.isDrum)
			info |= 1 << 7;

		return info;
	}

	public boolean hasBranch()
	{
		for (TrackCommand cmd : commands) {
			if (cmd instanceof Branch) {
				return true;
			}
		}

		return false;
	}

	public Track(Phrase phrase, int i, ByteBuffer bb, int trackInfo)
	{
		this(phrase);

		enabled = (trackInfo != 0);
		index = i;

		if (!enabled) {
			return;
		}

		// unpack track info
		int offset = (trackInfo >> 16) & 0xFFFF;
		polyphonicIndex = (trackInfo >> 13) & 0x7;
		linkedIndex = (trackInfo >> 9) & 0xF;
		unkFlag = ((trackInfo >> 8) & 1) == 0;
		commands.isDrum = ((trackInfo >> 7) & 1) != 0;

		polyphonicVoiceCount = PolyphonicVoiceCounts[polyphonicIndex];

		if (debugPrint)
			System.out.printf("Track %X: poly %d, drum %b, linked %d%n", i, polyphonicIndex, commands.isDrum, linkedIndex);

		TrackStreamReader reader = new TrackStreamReader(this, bb, phrase.filePos + offset);
		readStream(reader, commands);

		phrase.song.addPart(new BGMPart(phrase.filePos + offset, bb.position(), String.format("Track %X-%d", phrase.filePos, i)));
	}

	private static class TrackStreamReader
	{
		private final Track track;
		private final ByteBuffer bb;
		private final int startPos;

		private int commandPos;
		private int time;

		public TrackStreamReader(Track track, ByteBuffer bb, int startPos)
		{
			this.track = track;

			this.bb = bb;
			this.startPos = startPos;

			bb.position(startPos);
		}

		public ByteBuffer getBufferView()
		{
			return bb.duplicate();
		}

		public void beginNext()
		{
			commandPos = bb.position();
		}

		public int getCommandPos()
		{
			return commandPos;
		}

		public void addTime(int ticks)
		{
			time += ticks;
		}

		public int getTime()
		{
			return time;
		}

		public int getS8()
		{
			return bb.get();
		}

		public int getU8()
		{
			return bb.get() & 0xFF;
		}

		public int getS16()
		{
			return bb.getShort();
		}

		public int getU16()
		{
			return bb.getShort() & 0xFFFF;
		}

		public int getS32()
		{
			return bb.getInt();
		}
	}

	public static void readStream(TrackStreamReader reader, CommandStream commands)
	{
		readStream(reader, commands, -1);
	}

	public static void readStream(TrackStreamReader reader, CommandStream commands, int lengthLimit)
	{
		int lastNoteTime = -1;
		int polyAlloc = 0;

		while (true) {
			reader.beginNext();
			int readLen = reader.getCommandPos() - reader.startPos;

			if (lengthLimit > 0 && readLen >= lengthLimit) {
				assert (readLen == lengthLimit);
				if (debugPrint)
					System.out.printf(CMD_FMT + "End of Detour%n", reader.getCommandPos(), reader.getTime());
				break;
			}

			int op = reader.getU8();
			if (op == 0) {
				if (debugPrint)
					System.out.printf(CMD_FMT + "End of Stream%n", reader.getCommandPos(), reader.getTime());
				break;
			}

			if (op < 0x78) {
				int delay = op;
				commands.add(new Delay(reader, delay));

				if (debugPrint)
					System.out.printf(CMD_FMT + "Delay ~ %d%n", reader.getCommandPos(), reader.getTime(), delay);

				reader.addTime(delay);
			}
			else if (op < 0x80) {
				int delay = ((op & 7) << 8) + reader.getU8() + 0x78;
				commands.add(new Delay(reader, delay));

				if (debugPrint)
					System.out.printf(CMD_FMT + "Delay ~ %d%n", reader.getCommandPos(), reader.getTime(), delay);

				reader.addTime(delay);
			}
			else if (op < 0xD4) {
				int pitch = op & 0x7F;
				int velocity = reader.getU8();
				int length = reader.getU8();
				if (length >= 0xC0) {
					length = ((length & 0x3F) << 8) + reader.getU8() + 0xC0;
				}
				commands.add(new Note(reader, pitch, velocity, length));

				int curTime = reader.getTime();

				if (debugPrint)
					System.out.printf(CMD_FMT + "Note ~ %d %d %d%n", reader.getCommandPos(), curTime, pitch, velocity, length);

				if (curTime == lastNoteTime) {
					polyAlloc++;

					if (polyAlloc > reader.track.polyphonicVoiceCount) {
						System.out.println("POLYPHONY OVERFLOW!");
					}
				}
				else {
					polyAlloc = 1;
				}
				lastNoteTime = curTime;
			}
			else if (op < 0xE0) {
				throw new StarRodException("Unknown track command: %02X", op);
			}
			else {
				switch (op) {
					case SetMasterTempo.OPCODE:
						commands.add(new SetMasterTempo(reader));
						break;
					case SetMasterVolume.OPCODE:
						commands.add(new SetMasterVolume(reader));
						break;
					case SetMasterDetune.OPCODE:
						commands.add(new SetMasterDetune(reader));
						break;
					case SetBusEffect.OPCODE:
						commands.add(new SetBusEffect(reader));
						break;
					case MasterTempoLerp.OPCODE:
						commands.add(new MasterTempoLerp(reader));
						break;
					case MasterVolumeLerp.OPCODE:
						commands.add(new MasterVolumeLerp(reader));
						break;
					case SetMasterEffect.OPCODE:
						commands.add(new SetMasterEffect(reader));
						break;
					case OverridePatch.OPCODE:
						commands.add(new OverridePatch(reader));
						break;
					case InstrumentVolume.OPCODE:
						commands.add(new InstrumentVolume(reader));
						break;
					case InstrumentPan.OPCODE:
						commands.add(new InstrumentPan(reader));
						break;
					case InstrumentReverb.OPCODE:
						commands.add(new InstrumentReverb(reader));
						break;
					case TrackVolume.OPCODE:
						commands.add(new TrackVolume(reader));
						break;
					case InstrumentCoarseTune.OPCODE:
						commands.add(new InstrumentCoarseTune(reader));
						break;
					case InstrumentFineTune.OPCODE:
						commands.add(new InstrumentFineTune(reader));
						break;
					case TrackDetune.OPCODE:
						commands.add(new TrackDetune(reader));
						break;
					case TrackTremolo.OPCODE:
						commands.add(new TrackTremolo(reader));
						break;
					case TrackTremoloRate.OPCODE:
						commands.add(new TrackTremoloRate(reader));
						break;
					case TrackTremoloDepth.OPCODE:
						commands.add(new TrackTremoloDepth(reader));
						break;
					case TrackTremoloStop.OPCODE:
						commands.add(new TrackTremoloStop(reader));
						break;
					case RandomPan.OPCODE:
						commands.add(new RandomPan(reader));
						break;
					case UseInstrument.OPCODE:
						commands.add(new UseInstrument(reader));
						break;
					case InstrumentVolumeLerp.OPCODE:
						commands.add(new InstrumentVolumeLerp(reader));
						break;
					case ReverbType.OPCODE:
						commands.add(new ReverbType(reader));
						break;
					case Branch.OPCODE:
						assert (commands.type == StreamType.TRACK);
						commands.add(new Branch(reader));
						break;
					case EventTrigger.OPCODE:
						commands.add(new EventTrigger(reader));
						break;
					case Detour.OPCODE:
						assert (commands.type == StreamType.TRACK);
						commands.add(new Detour(reader));
						break;
					case SPECIAL_OPCODE:
						int type = reader.getU8();
						switch (type) {
							case SetStereoDelay.SUBOP:
								commands.add(new SetStereoDelay(reader));
								break;
							case SeekCustomEnv.SUBOP:
								commands.add(new SeekCustomEnv(reader));
								break;
							case WriteCustomEnv.SUBOP:
								commands.add(new WriteCustomEnv(reader));
								break;
							case UseCustomEnv.SUBOP:
								commands.add(new UseCustomEnv(reader));
								break;
							case TriggerSound.SUBOP:
								commands.add(new TriggerSound(reader));
								break;
							case ProxMixOverride.SUBOP:
								commands.add(new ProxMixOverride(reader));
								break;
							default:
								throw new StarRodException("Unknown special command type: %02X", type);
						}
						break;
					default:
						throw new StarRodException("Unknown track command: %02X", op);
				}
			}
		}

		commands.duration = reader.getTime();
	}

	public void reindex()
	{
		int detourID = 1;
		for (TrackDetour detour : detours) {
			detour.serialID = detourID++;
		}

		int branchID = 1;
		for (TrackBranch branch : branches) {
			branch.serialID = branchID++;
		}
	}

	public void build(DynamicByteBuffer dbb)
	{
		commands.build(dbb, true);

		for (TrackDetour detour : detours) {
			detour.build(dbb);
		}
	}

	public void updateRefs(DynamicByteBuffer dbb)
	{
		for (TrackCommand cmd : commands) {
			cmd.update(dbb);
		}
	}

	@Override
	public void fromXML(XmlReader xmr, Element elem)
	{
		index = xmr.readInt(elem, ATTR_INDEX);
		copyOf = xmr.readInt(elem, ATTR_COPY_OF, -1);
		linkedIndex = xmr.readInt(elem, ATTR_LINKED, 0);
		polyphonicIndex = xmr.readInt(elem, ATTR_POLYPHONY, 1);
		enabled = xmr.readBoolean(elem, ATTR_ENABLED, false);
		unkFlag = xmr.readBoolean(elem, ATTR_UNK_FLAG, true);
		commands.isDrum = xmr.readBoolean(elem, ATTR_IS_DRUM, false);

		polyphonicVoiceCount = PolyphonicVoiceCounts[polyphonicIndex];

		detourLookup.clear();
		branchLookup.clear();

		// read detours
		Element detoursElem = xmr.getUniqueTag(elem, TAG_DETOUR_LIST);
		if (detoursElem != null) {
			for (Element detourElem : xmr.getTags(detoursElem, TAG_DETOUR)) {
				TrackDetour detour = new TrackDetour(this);
				detour.fromXML(xmr, detourElem);
				detourLookup.put(detour.serialID, detour);
				detours.add(detour);
			}
		}

		// read branches
		Element branchesElem = xmr.getUniqueTag(elem, TAG_BRANCH_LIST);
		if (branchesElem != null) {
			for (Element branchElem : xmr.getTags(branchesElem, TAG_BRANCH)) {
				TrackBranch branch = new TrackBranch(this);
				branch.fromXML(xmr, branchElem);
				branchLookup.put(branch.serialID, branch);
				branches.add(branch);
			}
		}

		// read track commands last
		Element commandsElem = xmr.getUniqueRequiredTag(elem, TAG_COMMANDS);
		readCommandList(xmr, commandsElem, this, commands);
	}

	@Override
	public void toXML(XmlWriter xmw)
	{
		XmlTag tag = xmw.createTag(TAG_TRACK, false);
		xmw.addInt(tag, ATTR_INDEX, index);
		if (copyOf >= 0)
			xmw.addInt(tag, ATTR_COPY_OF, copyOf);
		xmw.addInt(tag, ATTR_LINKED, linkedIndex);
		xmw.addInt(tag, ATTR_POLYPHONY, polyphonicIndex);
		xmw.addBoolean(tag, ATTR_ENABLED, enabled);
		xmw.addBoolean(tag, ATTR_UNK_FLAG, unkFlag);
		xmw.addBoolean(tag, ATTR_IS_DRUM, commands.isDrum);

		xmw.openTag(tag);

		XmlTag commandsTag = xmw.createTag(TAG_COMMANDS, false);
		xmw.openTag(commandsTag);
		for (TrackCommand cmd : commands) {
			cmd.toXML(xmw);
		}
		xmw.closeTag(commandsTag);

		if (detours.size() > 0) {
			XmlTag listTag = xmw.createTag(TAG_DETOUR_LIST, false);
			xmw.openTag(listTag);
			for (TrackDetour detour : detours) {
				detour.toXML(xmw);
			}
			xmw.closeTag(listTag);
		}

		if (branches.size() > 0) {
			XmlTag listTag = xmw.createTag(TAG_BRANCH_LIST, false);
			xmw.openTag(listTag);
			for (TrackBranch branch : branches) {
				branch.toXML(xmw);
			}
			xmw.closeTag(listTag);
		}

		xmw.closeTag(tag);
	}

	private static void readCommandList(XmlReader xmr, Element listElem, Track track, CommandStream commands)
	{
		int time = 0;

		for (Node child = listElem.getFirstChild(); child != null; child = child.getNextSibling()) {
			if (child instanceof Element elem) {
				TrackCommand cmd = makeCommand(xmr, elem, track);
				cmd.time = time;

				if (cmd instanceof Delay delay) {
					time += delay.ticks;
					//TODO do not include delay commands in live, editable command stream
					commands.add(cmd); //TODO remove
				}
				else if (cmd instanceof Detour detour) {
					detour.detour = track.detourLookup.get(detour.serialID);
					time += detour.detour.commands.duration;
					commands.add(cmd);
				}
				else if (cmd instanceof Branch branch) {
					branch.branch = track.branchLookup.get(branch.serialID);
					time += track.phrase.song.branchMeasure;
					commands.add(cmd);
				}
				else {
					commands.add(cmd);
				}
			}
		}

		commands.duration = time;
	}

	private static TrackCommand makeCommand(XmlReader xmr, Element elem, Track track)
	{
		String tagName = elem.getTagName();
		TrackCommand cmd;

		switch (tagName) {
			// @formatter:off
			case "Note":					cmd = new Note(track); break;
			case "Delay":  					cmd = new Delay(track); break;
			case "MasterTempo":				cmd = new SetMasterTempo(track); break;
			case "MasterVolume":			cmd = new SetMasterVolume(track); break;
			case "MasterDetune":			cmd = new SetMasterDetune(track); break;
			case "BusEffect":				cmd = new SetBusEffect(track); break;
			case "MasterTempoLerp":			cmd = new MasterTempoLerp(track); break;
			case "MasterVolumeLerp":		cmd = new MasterVolumeLerp(track); break;
			case "MasterEffect":			cmd = new SetMasterEffect(track); break;
			case "OverridePatch":			cmd = new OverridePatch(track); break;
			case "InstrumentVolume":		cmd = new InstrumentVolume(track); break;
			case "InstrumentPan":			cmd = new InstrumentPan(track); break;
			case "InstrumentReverb":		cmd = new InstrumentReverb(track); break;
			case "TrackVolume":				cmd = new TrackVolume(track); break;
			case "InstrumentCoarseTune":	cmd = new InstrumentCoarseTune(track); break;
			case "InstrumentFineTune":		cmd = new InstrumentFineTune(track); break;
			case "TrackDetune":				cmd = new TrackDetune(track); break;
			case "TrackTremolo":			cmd = new TrackTremolo(track); break;
			case "TrackTremoloRate":		cmd = new TrackTremoloRate(track); break;
			case "TrackTremoloDepth":		cmd = new TrackTremoloDepth(track); break;
			case "TrackTremoloStop":		cmd = new TrackTremoloStop(track); break;
			case "RandomPan":				cmd = new RandomPan(track); break;
			case "UseInstrument":			cmd = new UseInstrument(track); break;
			case "InstrumentVolumeLerp":	cmd = new InstrumentVolumeLerp(track); break;
			case "ReverbType":				cmd = new ReverbType(track); break;
			case "Branch":					cmd = new Branch(track); break;
			case "EventTrigger":			cmd = new EventTrigger(track); break;
			case "Detour":					cmd = new Detour(track); break;
			case "StereoDelay":				cmd = new SetStereoDelay(track); break;
			case "SeekCustomEnvelope":		cmd = new SeekCustomEnv(track); break;
			case "WriteCustomEnvelope":		cmd = new WriteCustomEnv(track); break;
			case "UseCustomEnvelope":		cmd = new UseCustomEnv(track); break;
			case "TriggerSound":			cmd = new TriggerSound(track); break;
			case "ProxMixOverride":			cmd = new ProxMixOverride(track); break;
			// @formatter:on
			default:
				throw new IllegalArgumentException("Unknown command tag: " + tagName);
		}

		cmd.fromXML(xmr, elem);
		return cmd;
	}

	public static class TrackDetour implements XmlSerializable
	{
		public final Track track;

		protected CommandStream commands = new CommandStream(StreamType.DETOUR);

		public transient int serialID;

		public TrackDetour(Track track)
		{
			this.track = track;
		}

		public void build(DynamicByteBuffer dbb)
		{
			commands.build(dbb, false);
		}

		@Override
		public void fromXML(XmlReader xmr, Element elem)
		{
			serialID = xmr.readInt(elem, ATTR_SERIAL_ID);

			if (xmr.hasAttribute(elem, ATTR_FILE_POS))
				commands.filePos = xmr.readHex(elem, ATTR_FILE_POS);

			Element commandsElem = xmr.getUniqueRequiredTag(elem, TAG_COMMANDS);
			readCommandList(xmr, commandsElem, track, commands);
		}

		@Override
		public void toXML(XmlWriter xmw)
		{
			XmlTag tag = xmw.createTag(TAG_DETOUR, false);
			xmw.addInt(tag, ATTR_SERIAL_ID, serialID);
			if (commands.filePos != 0)
				xmw.addHex(tag, ATTR_FILE_POS, commands.filePos);
			xmw.openTag(tag);

			XmlTag commandsTag = xmw.createTag(TAG_COMMANDS, false);
			xmw.openTag(commandsTag);
			for (TrackCommand cmd : commands) {
				cmd.toXML(xmw);
			}
			xmw.closeTag(commandsTag);

			xmw.closeTag(tag);
		}
	}

	public static class TrackBranch implements XmlSerializable
	{
		public final Track track;

		protected CommandStream[] options = new CommandStream[Song.MAX_BRANCH_OPTIONS];

		public transient int tablePos;
		public transient int serialID;

		public TrackBranch(Track track)
		{
			this.track = track;

			for (int i = 0; i < options.length; i++) {
				options[i] = new CommandStream(StreamType.BRANCH);
			}
		}

		@Override
		public void fromXML(XmlReader xmr, Element elem)
		{
			serialID = xmr.readInt(elem, ATTR_SERIAL_ID);

			if (xmr.hasAttribute(elem, ATTR_FILE_POS))
				tablePos = xmr.readHex(elem, ATTR_FILE_POS);

			for (Element optionElem : xmr.getTags(elem, TAG_OPTION)) {
				int index = xmr.readInt(optionElem, ATTR_INDEX);
				boolean isDrum = xmr.readBoolean(optionElem, ATTR_IS_DRUM, false);

				if (index > options.length)
					xmr.complain("Branch has too many options, maximum is " + options.length);

				options[index].isDrum = isDrum;
				readCommandList(xmr, optionElem, track, options[index]);

				int duration = options[index].duration;
				int expected = track.phrase.song.branchMeasure;
				if (duration != expected)
					xmr.complain(String.format("Branch has duration: %d (expected %d)", duration, expected));

				index++;
			}
		}

		@Override
		public void toXML(XmlWriter xmw)
		{
			Song song = track.phrase.song;

			XmlTag branchTag = xmw.createTag(TAG_BRANCH, false);
			xmw.addInt(branchTag, ATTR_SERIAL_ID, serialID);
			if (tablePos != 0)
				xmw.addHex(branchTag, ATTR_FILE_POS, tablePos);
			xmw.openTag(branchTag);

			for (int i = 0; i < song.branchOptions; i++) {
				XmlTag optionTag = xmw.createTag(TAG_OPTION, false);
				xmw.addInt(optionTag, ATTR_INDEX, i);
				if (options[i].isDrum)
					xmw.addBoolean(optionTag, ATTR_IS_DRUM, options[i].isDrum);
				xmw.openTag(optionTag);
				for (TrackCommand cmd : options[i]) {
					cmd.toXML(xmw);
				}
				xmw.closeTag(optionTag);
			}

			xmw.closeTag(branchTag);
		}
	}

	public static abstract class TrackCommand implements XmlSerializable
	{
		public final Track track;
		public transient int time;
		public transient int streamPos;

		private TrackCommand(Track track)
		{
			this.track = track;
		}

		public TrackCommand(TrackStreamReader reader)
		{
			this.track = reader.track;
			this.streamPos = reader.getCommandPos();
			this.time = reader.getTime();
		}

		public abstract void build(DynamicByteBuffer dbb);

		// post-build update pass
		public void update(DynamicByteBuffer dbb)
		{}
	}

	public static class Note extends TrackCommand
	{
		private Track track;

		private int pitch;
		private int velocity;
		private int length;

		private Note(Track track)
		{
			super(track); // for fromXML
		}

		public Note(TrackStreamReader reader, int pitch, int velocity, int length)
		{
			super(reader);
			this.pitch = pitch;
			this.velocity = velocity;
			this.length = length;
		}

		@Override
		public void fromXML(XmlReader xmr, Element elem)
		{
			pitch = xmr.readInt(elem, ATTR_NOTE_PITCH);
			velocity = xmr.readInt(elem, ATTR_NOTE_VELOCITY);
			length = xmr.readInt(elem, ATTR_NOTE_LENGTH);
		}

		@Override
		public void toXML(XmlWriter xmw)
		{
			XmlTag tag = xmw.createTag(TAG_CMD_NOTE, true);
			xmw.addInt(tag, ATTR_NOTE_PITCH, pitch);
			xmw.addInt(tag, ATTR_NOTE_VELOCITY, velocity);
			xmw.addInt(tag, ATTR_NOTE_LENGTH, length);
			xmw.printTag(tag);
		}

		@Override
		public void build(DynamicByteBuffer dbb)
		{
			dbb.putByte(0x80 | (pitch & 0x7F));
			dbb.putByte(velocity);

			if (length < 0xC0) {
				// single byte encoding
				dbb.putByte(length);
			}
			else {
				// two-byte encoding for longer notes
				int tmp = length - 0xC0;
				int hi = (tmp >> 8) & 0x3F;
				int lo = (tmp & 0xFF);
				dbb.putByte(0xC0 | hi);
				dbb.putByte(lo);
			}
		}
	}

	public static class Delay extends TrackCommand
	{
		private Track track;

		public int ticks;

		private Delay(Track track)
		{
			super(track); // for fromXML
		}

		public Delay(TrackStreamReader reader, int length)
		{
			super(reader);
			this.ticks = length;
		}

		@Override
		public void fromXML(XmlReader xmr, Element elem)
		{
			ticks = xmr.readInt(elem, ATTR_TICKS);
		}

		@Override
		public void toXML(XmlWriter xmw)
		{
			XmlTag tag = xmw.createTag(TAG_CMD_DELAY, true);
			xmw.addInt(tag, ATTR_TICKS, ticks);
			xmw.printTag(tag);
		}

		@Override
		public void build(DynamicByteBuffer dbb)
		{
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

	public static class SetMasterTempo extends TrackCommand
	{
		public static final int OPCODE = 0xE0;

		public int bpm;

		private SetMasterTempo(Track track)
		{
			super(track); // for fromXML
		}

		public SetMasterTempo(TrackStreamReader reader)
		{
			super(reader);

			bpm = reader.getU16();

			if (debugPrint)
				System.out.printf(CMD_FMT + "Master Tempo ~ %d%n", streamPos, time, bpm);
		}

		@Override
		public void fromXML(XmlReader xmr, Element elem)
		{
			bpm = xmr.readInt(elem, ATTR_BPM);
		}

		@Override
		public void toXML(XmlWriter xmw)
		{
			XmlTag tag = xmw.createTag(TAG_CMD_MASTER_TEMPO, true);
			xmw.addInt(tag, ATTR_BPM, bpm);
			xmw.printTag(tag);
		}

		@Override
		public void build(DynamicByteBuffer dbb)
		{
			dbb.putByte(OPCODE);
			dbb.putShort(bpm & 0xFFFF);
		}
	}

	public static class SetMasterVolume extends TrackCommand
	{
		public static final int OPCODE = 0xE1;

		public int value;

		private SetMasterVolume(Track track)
		{
			super(track); // for fromXML
		}

		public SetMasterVolume(TrackStreamReader reader)
		{
			super(reader);

			value = reader.getU8() & 0x7F;

			if (debugPrint)
				System.out.printf(CMD_FMT + "Master Volume ~ %d%n", streamPos, time, value);
		}

		@Override
		public void fromXML(XmlReader xmr, Element elem)
		{
			value = xmr.readInt(elem, ATTR_VALUE);
		}

		@Override
		public void toXML(XmlWriter xmw)
		{
			XmlTag tag = xmw.createTag(TAG_CMD_MASTER_VOLUME, true);
			xmw.addInt(tag, ATTR_VALUE, value);
			xmw.printTag(tag);
		}

		@Override
		public void build(DynamicByteBuffer dbb)
		{
			dbb.putByte(OPCODE);
			dbb.putByte(value & 0x7F);
		}
	}

	public static class SetMasterDetune extends TrackCommand
	{
		public static final int OPCODE = 0xE2;

		public int cents;

		private SetMasterDetune(Track track)
		{
			super(track); // for fromXML
		}

		public SetMasterDetune(TrackStreamReader reader)
		{
			super(reader);

			cents = reader.getS8();

			if (debugPrint)
				System.out.printf(CMD_FMT + "Master Detune ~ %d%n", streamPos, time, cents);
		}

		@Override
		public void fromXML(XmlReader xmr, Element elem)
		{
			cents = xmr.readInt(elem, ATTR_CENTS);
		}

		@Override
		public void toXML(XmlWriter xmw)
		{
			XmlTag tag = xmw.createTag(TAG_CMD_MASTER_DETUNE, true);
			xmw.addInt(tag, ATTR_CENTS, cents);
			xmw.printTag(tag);
		}

		@Override
		public void build(DynamicByteBuffer dbb)
		{
			dbb.putByte(OPCODE);
			dbb.putByte(cents);
		}
	}

	public static class SetBusEffect extends TrackCommand
	{
		public static final int OPCODE = 0xE3;
		public int effectType;

		private SetBusEffect(Track track)
		{
			super(track); // for fromXML
		}

		public SetBusEffect(TrackStreamReader reader)
		{
			super(reader);

			effectType = reader.getU8();

			if (debugPrint)
				System.out.printf(CMD_FMT + "Bus Effect ~ %d%n", streamPos, time, effectType);
		}

		@Override
		public void fromXML(XmlReader x, Element e)
		{
			effectType = x.readInt(e, ATTR_EFFECT_TYPE);
		}

		@Override
		public void toXML(XmlWriter w)
		{
			XmlTag tag = w.createTag(TAG_CMD_BUS_EFFECT, true);
			w.addInt(tag, ATTR_EFFECT_TYPE, effectType);
			w.printTag(tag);
		}

		@Override
		public void build(DynamicByteBuffer dbb)
		{
			dbb.putByte(OPCODE);
			dbb.putByte(effectType);
		}
	}

	public static class MasterTempoLerp extends TrackCommand
	{
		public static final int OPCODE = 0xE4;

		public int time;
		public int bpm;

		private MasterTempoLerp(Track track)
		{
			super(track); // for fromXML
		}

		public MasterTempoLerp(TrackStreamReader reader)
		{
			super(reader);

			time = reader.getU16();
			bpm = reader.getU16();

			if (debugPrint)
				System.out.printf(CMD_FMT + "Master Tempo Lerp ~ %d over %d%n", streamPos, time, bpm, time);
		}

		@Override
		public void fromXML(XmlReader xmr, Element elem)
		{
			time = xmr.readInt(elem, ATTR_TICKS);
			bpm = xmr.readInt(elem, ATTR_BPM);
		}

		@Override
		public void toXML(XmlWriter xmw)
		{
			XmlTag tag = xmw.createTag(TAG_CMD_MASTER_TEMPO_LERP, true);
			xmw.addInt(tag, ATTR_TICKS, time);
			xmw.addInt(tag, ATTR_BPM, bpm);
			xmw.printTag(tag);
		}

		@Override
		public void build(DynamicByteBuffer dbb)
		{
			dbb.putByte(OPCODE);
			dbb.putShort(time & 0xFFFF);
			dbb.putShort(bpm & 0xFFFF);
		}
	}

	public static class MasterVolumeLerp extends TrackCommand
	{
		public static final int OPCODE = 0xE5;

		public int time;
		public int value;

		private MasterVolumeLerp(Track track)
		{
			super(track); // for fromXML
		}

		public MasterVolumeLerp(TrackStreamReader reader)
		{
			super(reader);

			time = reader.getU16();
			value = reader.getU8() & 0x7F;

			if (debugPrint)
				System.out.printf(CMD_FMT + "Master Volume Lerp ~ %d over %d%n", streamPos, time, value, time);
		}

		@Override
		public void fromXML(XmlReader xmr, Element elem)
		{
			time = xmr.readInt(elem, ATTR_TICKS);
			value = xmr.readInt(elem, ATTR_VALUE);
		}

		@Override
		public void toXML(XmlWriter xmw)
		{
			XmlTag tag = xmw.createTag(TAG_CMD_MASTER_VOLUME_LERP, true);
			xmw.addInt(tag, ATTR_TICKS, time);
			xmw.addInt(tag, ATTR_VALUE, value);
			xmw.printTag(tag);
		}

		@Override
		public void build(DynamicByteBuffer dbb)
		{
			dbb.putByte(OPCODE);
			dbb.putShort(time & 0xFFFF);
			dbb.putByte(value & 0x7F);
		}
	}

	public static class SetMasterEffect extends TrackCommand
	{
		public static final int OPCODE = 0xE6;

		public int index;
		public int value;

		private SetMasterEffect(Track track)
		{
			super(track); // for fromXML
		}

		public SetMasterEffect(TrackStreamReader reader)
		{
			super(reader);

			index = reader.getU8();
			value = reader.getU8();

			if (debugPrint)
				System.out.printf(CMD_FMT + "Master Effect ~ %d at %d%n", streamPos, time, value, index);
		}

		@Override
		public void fromXML(XmlReader xmr, Element elem)
		{
			index = xmr.readInt(elem, ATTR_INDEX);
			value = xmr.readInt(elem, ATTR_VALUE);
		}

		@Override
		public void toXML(XmlWriter xmw)
		{
			XmlTag tag = xmw.createTag(TAG_CMD_MASTER_EFFECT, true);
			xmw.addInt(tag, ATTR_INDEX, index);
			xmw.addInt(tag, ATTR_VALUE, value);
			xmw.printTag(tag);
		}

		@Override
		public void build(DynamicByteBuffer dbb)
		{
			dbb.putByte(OPCODE);
			dbb.putByte(index);
			dbb.putByte(value);
		}
	}

	public static class OverridePatch extends TrackCommand
	{
		public static final int OPCODE = 0xE8;

		public int bank;
		public int patch;

		private OverridePatch(Track track)
		{
			super(track); // for fromXML
		}

		public OverridePatch(TrackStreamReader reader)
		{
			super(reader);

			bank = reader.getU8();
			patch = reader.getU8();

			if (debugPrint)
				System.out.printf(CMD_FMT + "Override Patch ~ %2X-%2X%n", streamPos, time, bank, patch);
		}

		@Override
		public void fromXML(XmlReader xmr, Element elem)
		{
			bank = xmr.readHex(elem, ATTR_BANK);
			patch = xmr.readHex(elem, ATTR_PATCH);
		}

		@Override
		public void toXML(XmlWriter xmw)
		{
			XmlTag tag = xmw.createTag(TAG_CMD_OVERRIDE_PATCH, true);
			xmw.addHex(tag, ATTR_BANK, bank);
			xmw.addHex(tag, ATTR_PATCH, patch);
			xmw.printTag(tag);
		}

		@Override
		public void build(DynamicByteBuffer dbb)
		{
			dbb.putByte(OPCODE);
			dbb.putByte(bank);
			dbb.putByte(patch);
		}
	}

	public static class InstrumentVolume extends TrackCommand
	{
		public static final int OPCODE = 0xE9;
		public int value;

		private InstrumentVolume(Track track)
		{
			super(track); // for fromXML
		}

		public InstrumentVolume(TrackStreamReader reader)
		{
			super(reader);

			value = reader.getU8() & 0x7F;

			if (debugPrint)
				System.out.printf(CMD_FMT + "Instrument Volume ~ %d%n", streamPos, time, value);
		}

		@Override
		public void fromXML(XmlReader xmr, Element elem)
		{
			value = xmr.readInt(elem, ATTR_VALUE);
		}

		@Override
		public void toXML(XmlWriter xmw)
		{
			XmlTag tag = xmw.createTag(TAG_CMD_INSTRUMENT_VOLUME, true);
			xmw.addInt(tag, ATTR_VALUE, value);
			xmw.printTag(tag);
		}

		@Override
		public void build(DynamicByteBuffer dbb)
		{
			dbb.putByte(OPCODE);
			dbb.putByte(value & 0x7F);
		}
	}

	public static class InstrumentPan extends TrackCommand
	{
		public static final int OPCODE = 0xEA;
		public int value;

		private InstrumentPan(Track track)
		{
			super(track); // for fromXML
		}

		public InstrumentPan(TrackStreamReader reader)
		{
			super(reader);

			value = reader.getU8() & 0x7F;

			if (debugPrint)
				System.out.printf(CMD_FMT + "Instrument Pan ~ %d%n", streamPos, time, value);
		}

		@Override
		public void fromXML(XmlReader xmr, Element elem)
		{
			value = xmr.readInt(elem, ATTR_VALUE);
		}

		@Override
		public void toXML(XmlWriter xmw)
		{
			XmlTag tag = xmw.createTag(TAG_CMD_INSTRUMENT_PAN, true);
			xmw.addInt(tag, ATTR_VALUE, value);
			xmw.printTag(tag);
		}

		@Override
		public void build(DynamicByteBuffer dbb)
		{
			dbb.putByte(OPCODE);
			dbb.putByte(value & 0x7F);
		}
	}

	public static class InstrumentReverb extends TrackCommand
	{
		public static final int OPCODE = 0xEB;
		public int value;

		private InstrumentReverb(Track track)
		{
			super(track); // for fromXML
		}

		public InstrumentReverb(TrackStreamReader reader)
		{
			super(reader);

			value = reader.getU8() & 0x7F;

			if (debugPrint)
				System.out.printf(CMD_FMT + "Instrument Reverb ~ %d%n", streamPos, time, value);
		}

		@Override
		public void fromXML(XmlReader xmr, Element elem)
		{
			value = xmr.readInt(elem, ATTR_VALUE);
		}

		@Override
		public void toXML(XmlWriter xmw)
		{
			XmlTag tag = xmw.createTag(TAG_CMD_INSTRUMENT_REVERB, true);
			xmw.addInt(tag, ATTR_VALUE, value);
			xmw.printTag(tag);
		}

		@Override
		public void build(DynamicByteBuffer dbb)
		{
			dbb.putByte(OPCODE);
			dbb.putByte(value & 0x7F);
		}
	}

	public static class TrackVolume extends TrackCommand
	{
		public static final int OPCODE = 0xEC;
		public int value;

		private TrackVolume(Track track)
		{
			super(track); // for fromXML
		}

		public TrackVolume(TrackStreamReader reader)
		{
			super(reader);

			value = reader.getU8() & 0x7F;

			if (debugPrint)
				System.out.printf(CMD_FMT + "Track Volume ~ %d%n", streamPos, time, value);
		}

		@Override
		public void fromXML(XmlReader xmr, Element elem)
		{
			value = xmr.readInt(elem, ATTR_VALUE);
		}

		@Override
		public void toXML(XmlWriter xmw)
		{
			XmlTag tag = xmw.createTag(TAG_CMD_TRACK_VOLUME, true);
			xmw.addInt(tag, ATTR_VALUE, value);
			xmw.printTag(tag);
		}

		@Override
		public void build(DynamicByteBuffer dbb)
		{
			dbb.putByte(OPCODE);
			dbb.putByte(value & 0x7F);
		}
	}

	public static class InstrumentCoarseTune extends TrackCommand
	{
		public static final int OPCODE = 0xED;
		public int semitones;

		private InstrumentCoarseTune(Track track)
		{
			super(track); // for fromXML
		}

		public InstrumentCoarseTune(TrackStreamReader reader)
		{
			super(reader);

			semitones = reader.getS8();

			if (debugPrint)
				System.out.printf(CMD_FMT + "Instrument Coarse Tune ~ %d%n", streamPos, time, semitones);
		}

		@Override
		public void fromXML(XmlReader xmr, Element elem)
		{
			semitones = xmr.readInt(elem, ATTR_SEMITONES);
		}

		@Override
		public void toXML(XmlWriter xmw)
		{
			XmlTag tag = xmw.createTag(TAG_CMD_INSTR_COARSE_TUNE, true);
			xmw.addInt(tag, ATTR_SEMITONES, semitones);
			xmw.printTag(tag);
		}

		@Override
		public void build(DynamicByteBuffer dbb)
		{
			dbb.putByte(OPCODE);
			dbb.putByte(semitones);
		}
	}

	public static class InstrumentFineTune extends TrackCommand
	{
		public static final int OPCODE = 0xEE;
		public int cents;

		private InstrumentFineTune(Track track)
		{
			super(track); // for fromXML
		}

		public InstrumentFineTune(TrackStreamReader reader)
		{
			super(reader);

			cents = reader.getS8();

			if (debugPrint)
				System.out.printf(CMD_FMT + "Instrument Fine Tune ~ %d%n", streamPos, time, cents);
		}

		@Override
		public void fromXML(XmlReader xmr, Element elem)
		{
			cents = xmr.readInt(elem, ATTR_CENTS);
		}

		@Override
		public void toXML(XmlWriter xmw)
		{
			XmlTag tag = xmw.createTag(TAG_CMD_INSTR_FINE_TUNE, true);
			xmw.addInt(tag, ATTR_CENTS, cents);
			xmw.printTag(tag);
		}

		@Override
		public void build(DynamicByteBuffer dbb)
		{
			dbb.putByte(OPCODE);
			dbb.putByte(cents);
		}
	}

	public static class TrackDetune extends TrackCommand
	{
		public static final int OPCODE = 0xEF;
		public int cents;

		private TrackDetune(Track track)
		{
			super(track); // for fromXML
		}

		public TrackDetune(TrackStreamReader reader)
		{
			super(reader);

			cents = reader.getS16();

			if (debugPrint)
				System.out.printf(CMD_FMT + "Track Detune ~ %d%n", streamPos, time, cents);
		}

		@Override
		public void fromXML(XmlReader xmr, Element elem)
		{
			cents = xmr.readInt(elem, ATTR_CENTS);
		}

		@Override
		public void toXML(XmlWriter xmw)
		{
			XmlTag tag = xmw.createTag(TAG_CMD_TRACK_DETUNE, true);
			xmw.addInt(tag, ATTR_CENTS, cents);
			xmw.printTag(tag);
		}

		@Override
		public void build(DynamicByteBuffer dbb)
		{
			dbb.putByte(OPCODE);
			dbb.putShort(cents);
		}
	}

	public static class TrackTremolo extends TrackCommand
	{
		public static final int OPCODE = 0xF0;

		public int delay;
		public int speed;
		public int depth;

		private TrackTremolo(Track track)
		{
			super(track); // for fromXML
		}

		public TrackTremolo(TrackStreamReader reader)
		{
			super(reader);

			delay = reader.getU8();
			speed = reader.getU8();
			depth = reader.getU8();

			if (debugPrint)
				System.out.printf(CMD_FMT + "Tremolo ~ %d %d %d%n", streamPos, time, delay, speed, depth);
		}

		@Override
		public void fromXML(XmlReader xmr, Element elem)
		{
			delay = xmr.readInt(elem, ATTR_DELAY);
			speed = xmr.readInt(elem, ATTR_SPEED);
			depth = xmr.readInt(elem, ATTR_DEPTH);
		}

		@Override
		public void toXML(XmlWriter xmw)
		{
			XmlTag tag = xmw.createTag(TAG_CMD_TRACK_TREMOLO, true);
			xmw.addInt(tag, ATTR_DELAY, delay);
			xmw.addInt(tag, ATTR_SPEED, speed);
			xmw.addInt(tag, ATTR_DEPTH, depth);
			xmw.printTag(tag);
		}

		@Override
		public void build(DynamicByteBuffer dbb)
		{
			dbb.putByte(OPCODE);
			dbb.putByte(delay);
			dbb.putByte(speed);
			dbb.putByte(depth);
		}
	}

	public static class TrackTremoloRate extends TrackCommand
	{
		public static final int OPCODE = 0xF1;

		public int value;

		private TrackTremoloRate(Track track)
		{
			super(track); // for fromXML
		}

		public TrackTremoloRate(TrackStreamReader reader)
		{
			super(reader);

			value = reader.getU8();

			if (debugPrint)
				System.out.printf(CMD_FMT + "Tremolo ~ %d%n", streamPos, time, value);
		}

		@Override
		public void fromXML(XmlReader xmr, Element elem)
		{
			value = xmr.readInt(elem, ATTR_SPEED);
		}

		@Override
		public void toXML(XmlWriter xmw)
		{
			XmlTag tag = xmw.createTag(TAG_CMD_TREMOLO_RATE, true);
			xmw.addInt(tag, ATTR_SPEED, value);
			xmw.printTag(tag);
		}

		@Override
		public void build(DynamicByteBuffer dbb)
		{
			dbb.putByte(OPCODE);
			dbb.putByte(value);
		}
	}

	public static class TrackTremoloDepth extends TrackCommand
	{
		public static final int OPCODE = 0xF2;
		public int value;

		private TrackTremoloDepth(Track track)
		{
			super(track); // for fromXML
		}

		public TrackTremoloDepth(TrackStreamReader reader)
		{
			super(reader);

			value = reader.getU8();

			if (debugPrint)
				System.out.printf(CMD_FMT + "Tremolo Depth ~ %d%n", streamPos, time, value);
		}

		@Override
		public void fromXML(XmlReader xmw, Element elem)
		{
			value = xmw.readInt(elem, ATTR_DEPTH);
		}

		@Override
		public void toXML(XmlWriter xmw)
		{
			XmlTag tag = xmw.createTag(TAG_CMD_TREMOLO_DEPTH, true);
			xmw.addInt(tag, ATTR_DEPTH, value);
			xmw.printTag(tag);
		}

		@Override
		public void build(DynamicByteBuffer dbb)
		{
			dbb.putByte(OPCODE);
			dbb.putByte(value);
		}
	}

	public static class TrackTremoloStop extends TrackCommand
	{
		public static final int OPCODE = 0xF3;

		private TrackTremoloStop(Track track)
		{
			super(track); // for fromXML
		}

		public TrackTremoloStop(TrackStreamReader reader)
		{
			super(reader);

			if (debugPrint)
				System.out.printf(CMD_FMT + "Tremolo Stop%n", streamPos, time);
		}

		@Override
		public void fromXML(XmlReader xmr, Element elem)
		{}

		@Override
		public void toXML(XmlWriter xmw)
		{
			xmw.printTag(xmw.createTag(TAG_CMD_TREMOLO_STOP, true));
		}

		@Override
		public void build(DynamicByteBuffer dbb)
		{
			dbb.putByte(OPCODE);
		}
	}

	public static class RandomPan extends TrackCommand
	{
		public static final int OPCODE = 0xF4;

		public int pan1;
		public int pan2;

		private RandomPan(Track track)
		{
			super(track); // for fromXML
		}

		public RandomPan(TrackStreamReader reader)
		{
			super(reader);

			pan1 = reader.getU8() & 0x7F;
			pan2 = reader.getU8() & 0x7F;

			if (debugPrint)
				System.out.printf(CMD_FMT + "Random Pan ~ %d %d%n", streamPos, time, pan1, pan2);
		}

		@Override
		public void fromXML(XmlReader xmr, Element elem)
		{
			pan1 = xmr.readInt(elem, ATTR_PAN1);
			pan2 = xmr.readInt(elem, ATTR_PAN2);
		}

		@Override
		public void toXML(XmlWriter xmw)
		{
			XmlTag tag = xmw.createTag(TAG_CMD_RANDOM_PAN, true);
			xmw.addInt(tag, ATTR_PAN1, pan1);
			xmw.addInt(tag, ATTR_PAN2, pan2);
			xmw.printTag(tag);
		}

		@Override
		public void build(DynamicByteBuffer dbb)
		{
			dbb.putByte(OPCODE);
			dbb.putByte(pan1 & 0x7F);
			dbb.putByte(pan2 & 0x7F);
		}
	}

	public static class UseInstrument extends TrackCommand
	{
		public static final int OPCODE = 0xF5;

		//TODO not final -- map to named entries in song/PRG
		public int index;
		public boolean useGlobal;

		private UseInstrument(Track track)
		{
			super(track); // for fromXML
		}

		public UseInstrument(TrackStreamReader reader)
		{
			super(reader);

			index = reader.getU8();
			useGlobal = (index >= 0x80);

			if (useGlobal) {
				index -= 0x80; // use entries from PRG
			}

			if (debugPrint)
				System.out.printf(CMD_FMT + "Use Instrument ~ %d (%s)%n", streamPos, time, index, useGlobal ? "global" : "local");
		}

		@Override
		public void fromXML(XmlReader xmr, Element elem)
		{
			index = xmr.readInt(elem, ATTR_INDEX);
			useGlobal = xmr.readBoolean(elem, ATTR_USE_GLOBAL);
		}

		@Override
		public void toXML(XmlWriter xmw)
		{
			XmlTag tag = xmw.createTag(TAG_CMD_USE_INSTRUMENT, true);
			xmw.addInt(tag, ATTR_INDEX, index);
			xmw.addBoolean(tag, ATTR_USE_GLOBAL, useGlobal);
			xmw.printTag(tag);
		}

		@Override
		public void build(DynamicByteBuffer dbb)
		{
			dbb.putByte(OPCODE);

			if (useGlobal)
				dbb.putByte(index + 0x80);
			else
				dbb.putByte(index);
		}
	}

	public static class InstrumentVolumeLerp extends TrackCommand
	{
		public static final int OPCODE = 0xF6;

		public int time;
		public int value;

		private InstrumentVolumeLerp(Track track)
		{
			super(track); // for fromXML
		}

		public InstrumentVolumeLerp(TrackStreamReader reader)
		{
			super(reader);

			time = reader.getU16();
			value = reader.getU8() & 0x7F;

			if (debugPrint)
				System.out.printf(CMD_FMT + "Instrument Volume Lerp ~ %d over %d%n", streamPos, time, value, time);
		}

		@Override
		public void fromXML(XmlReader xmr, Element elem)
		{
			time = xmr.readInt(elem, ATTR_TICKS);
			value = xmr.readInt(elem, ATTR_VALUE);
		}

		@Override
		public void toXML(XmlWriter xmw)
		{
			XmlTag tag = xmw.createTag(TAG_CMD_INSTR_VOL_LERP, true);
			xmw.addInt(tag, ATTR_TICKS, time);
			xmw.addInt(tag, ATTR_VALUE, value);
			xmw.printTag(tag);
		}

		@Override
		public void build(DynamicByteBuffer dbb)
		{
			dbb.putByte(OPCODE);
			dbb.putShort(time & 0xFFFF);
			dbb.putByte(value & 0x7F);
		}
	}

	public static class ReverbType extends TrackCommand
	{
		public static final int OPCODE = 0xF7;

		public int index;

		private ReverbType(Track track)
		{
			super(track); // for fromXML
		}

		public ReverbType(TrackStreamReader reader)
		{
			super(reader);

			index = reader.getU8();

			if (debugPrint)
				System.out.printf(CMD_FMT + "Reverb Type ~ %d%n", streamPos, time, index);
		}

		@Override
		public void fromXML(XmlReader xmw, Element elem)
		{
			index = xmw.readInt(elem, ATTR_INDEX);
		}

		@Override
		public void toXML(XmlWriter xmw)
		{
			XmlTag tag = xmw.createTag(TAG_CMD_REVERB_TYPE, true);
			xmw.addInt(tag, ATTR_INDEX, index);
			xmw.printTag(tag);
		}

		@Override
		public void build(DynamicByteBuffer dbb)
		{
			dbb.putByte(OPCODE);
			dbb.putByte(index);
		}
	}

	public static class Branch extends TrackCommand
	{
		public static final int OPCODE = 0xFC;

		public TrackBranch branch;

		public transient int serialID;
		private transient int refPos; // position of reference to table in stream

		private Branch(Track track)
		{
			super(track); // for fromXML
		}

		public Branch(TrackStreamReader reader)
		{
			super(reader);

			int tablePos = reader.getU16();
			int count = reader.getU8();

			Song song = track.phrase.song;
			song.branchOptions = count;

			assert (count == 10);

			branch = track.branchLookup.get(tablePos);

			if (branch != null) {
				if (debugPrint)
					System.out.println("--- REUSED BRANCH ---");
			}
			else {
				ByteBuffer bb = reader.getBufferView();

				branch = new TrackBranch(track);
				branch.tablePos = tablePos;

				track.branchLookup.put(tablePos, branch);
				track.branches.add(branch);

				// read the branch table
				int[] offsets = new int[count];
				boolean[] isDrum = new boolean[count];

				bb.position(tablePos);
				for (int i = 0; i < count; i++) {
					offsets[i] = bb.getShort() & 0xFFFF;
					isDrum[i] = bb.get() != 0;
				}
				song.addPart(new BGMPart(tablePos, bb.position(), "Jump Table (x" + count + ")"));

				if (debugPrint)
					System.out.printf(CMD_FMT + "Begin branch x%d%n", streamPos, time, count);

				bb.position(offsets[0]);
				int length = bb.get() & 0xFF;
				assert (bb.get() == 0);
				assert (length == song.branchMeasure);

				for (int i = 0; i < count; i++) {
					if (debugPrint)
						System.out.printf(CMD_FMT + "Branch %X --> %X%n", streamPos, time, i, offsets[i]);
					TrackStreamReader branchReader = new TrackStreamReader(track, bb, offsets[i]);
					CommandStream stream = new CommandStream(StreamType.BRANCH);
					stream.isDrum = isDrum[i];
					branch.options[i] = stream;

					readStream(branchReader, stream);
					assert (stream.duration == song.branchMeasure);

					song.addPart(new BGMPart(offsets[i], bb.position(), "Branch Option " + i));
				}

				if (debugPrint)
					System.out.printf(CMD_FMT + "End of branch%n", streamPos, time);
			}
		}

		@Override
		public void fromXML(XmlReader xmr, Element elem)
		{
			serialID = xmr.readInt(elem, ATTR_SERIAL_ID);
		}

		@Override
		public void toXML(XmlWriter xmw)
		{
			XmlTag tag = xmw.createTag(TAG_CMD_BRANCH, true);
			xmw.addInt(tag, ATTR_SERIAL_ID, branch.serialID);
			xmw.printTag(tag);
		}

		@Override
		public void build(DynamicByteBuffer dbb)
		{
			dbb.putByte(OPCODE);
			refPos = dbb.position();

			// consume space to reserve offset and count for later
			dbb.putShort(0);
			dbb.putByte(0);
		}

		@Override
		public void update(DynamicByteBuffer dbb)
		{
			Song song = track.phrase.song;

			dbb.position(refPos);
			dbb.putShort(branch.tablePos);
			dbb.putByte(song.branchOptions);
		}
	}

	public static class EventTrigger extends TrackCommand
	{
		public static final int OPCODE = 0xFD;

		public int eventInfo;

		private EventTrigger(Track track)
		{
			super(track); // for fromXML
		}

		public EventTrigger(TrackStreamReader reader)
		{
			super(reader);

			eventInfo = reader.getS32() & 0xFFFFFF;

			if (debugPrint)
				System.out.printf(CMD_FMT + "Event Trigger ~ %X%n", streamPos, time, eventInfo);
		}

		@Override
		public void fromXML(XmlReader xmr, Element elem)
		{
			eventInfo = xmr.readHex(elem, ATTR_EVENT_INFO);
		}

		@Override
		public void toXML(XmlWriter xmw)
		{
			XmlTag tag = xmw.createTag(TAG_CMD_EVENT_TRIGGER, true);
			xmw.addHex(tag, ATTR_EVENT_INFO, eventInfo);
			xmw.printTag(tag);
		}

		@Override
		public void build(DynamicByteBuffer dbb)
		{
			dbb.putByte(OPCODE);
			dbb.putInt(eventInfo & 0xFFFFFF);
		}
	}

	public static class Detour extends TrackCommand
	{
		public static final int OPCODE = 0xFE;

		public TrackDetour detour;

		public transient int serialID;
		private transient int refPos;

		private Detour(Track track)
		{
			super(track); // for fromXML
		}

		public Detour(TrackStreamReader reader)
		{
			super(reader);

			int offset = reader.getU16();
			int length = reader.getU8();

			// a single detour command is broken in song 58_170.bgm, the command has length = 0, but points
			// to a detour of length 0x100. this is due to overflow. the detour command in the stream stores
			// the length as a u8, so 0x100 overflows and nothing is played.
			boolean bugged = (length == 0);

			if (bugged) {
				length = 0x100;
			}

			if (debugPrint)
				System.out.printf(CMD_FMT + "Detour ~ %X --> %X%n", streamPos, time, offset, offset + length);

			detour = track.detourLookup.get(offset);

			if (detour != null) {
				if (debugPrint)
					System.out.println("--- REUSED DETOUR ---");
			}
			else {
				track.phrase.song.addPart(new BGMPart(offset, offset + length, "Detour"));

				detour = new TrackDetour(reader.track);
				TrackStreamReader detourReader = new TrackStreamReader(track, reader.getBufferView(), offset);

				track.detourLookup.put(offset, detour);
				track.detours.add(detour);

				if (debugPrint)
					System.out.println("--- BEGIN DETOUR ---");

				readStream(detourReader, detour.commands, length);

				if (!bugged)
					reader.addTime(detourReader.time);

				if (debugPrint)
					System.out.println("---- END DETOUR ----");
			}
		}

		@Override
		public void fromXML(XmlReader xmr, Element elem)
		{
			serialID = xmr.readInt(elem, ATTR_SERIAL_ID);
		}

		@Override
		public void toXML(XmlWriter xmw)
		{
			XmlTag tag = xmw.createTag(TAG_CMD_DETOUR, true);
			xmw.addInt(tag, ATTR_SERIAL_ID, detour.serialID);
			xmw.printTag(tag);
		}

		@Override
		public void build(DynamicByteBuffer dbb)
		{
			dbb.putByte(OPCODE);

			refPos = dbb.position();

			// consume space to reserve offset and size for later
			dbb.putShort(0);
			dbb.putByte(0);
		}

		@Override
		public void update(DynamicByteBuffer dbb)
		{
			dbb.position(refPos);

			dbb.putShort(detour.commands.filePos & 0xFFFF);
			dbb.putByte(detour.commands.fileLen);
		}
	}

	public static class SetStereoDelay extends TrackCommand
	{
		public static final int SUBOP = 1;

		public int index;
		public int length;
		public int side;

		private SetStereoDelay(Track track)
		{
			super(track); // for fromXML
		}

		public SetStereoDelay(TrackStreamReader reader)
		{
			super(reader);

			index = reader.getU8();
			int v = reader.getU8();
			length = v & 0xF;
			side = ((v >> 4) & 1) + 1;

			if (debugPrint)
				System.out.printf(CMD_FMT + "Set Stereo Delay ~ %d by %d @ %d%n", streamPos, time, index, length, side);
		}

		@Override
		public void fromXML(XmlReader xmr, Element elem)
		{
			index = xmr.readInt(elem, ATTR_INDEX);
			length = xmr.readInt(elem, ATTR_TIME);
			side = xmr.readInt(elem, ATTR_SIDE);
		}

		@Override
		public void toXML(XmlWriter xmw)
		{
			XmlTag tag = xmw.createTag(TAG_CMD_STEREO_DELAY, true);
			xmw.addInt(tag, ATTR_INDEX, index);
			xmw.addInt(tag, ATTR_TIME, length);
			xmw.addInt(tag, ATTR_SIDE, side);
			xmw.printTag(tag);
		}

		@Override
		public void build(DynamicByteBuffer dbb)
		{
			dbb.putByte(SPECIAL_OPCODE);
			dbb.putByte(SUBOP);
			dbb.putByte(index);

			int upper = ((side - 1) & 1);
			int lower = length & 0xF;
			dbb.putByte((upper << 4) | lower);
		}
	}

	public static class SeekCustomEnv extends TrackCommand
	{
		public static final int SUBOP = 2;

		public int index;

		private SeekCustomEnv(Track track)
		{
			super(track); // for fromXML
		}

		public SeekCustomEnv(TrackStreamReader reader)
		{
			super(reader);

			index = reader.getU8();
			reader.getU8(); // unused

			if (debugPrint)
				System.out.printf(CMD_FMT + "Seek Custom Env ~ %d%n", streamPos, time, index);
		}

		@Override
		public void fromXML(XmlReader xmr, Element elem)
		{
			index = xmr.readInt(elem, ATTR_INDEX);
		}

		@Override
		public void toXML(XmlWriter xmw)
		{
			XmlTag tag = xmw.createTag(TAG_CMD_SEEK_CUSTOM_ENV, true);
			xmw.addInt(tag, ATTR_INDEX, index);
			xmw.printTag(tag);
		}

		@Override
		public void build(DynamicByteBuffer dbb)
		{
			dbb.putByte(SPECIAL_OPCODE);
			dbb.putByte(SUBOP);
			dbb.putByte(index);
			dbb.putByte(0); // unused
		}
	}

	public static class WriteCustomEnv extends TrackCommand
	{
		public static final int SUBOP = 3;

		public int value;

		private WriteCustomEnv(Track track)
		{
			super(track); // for fromXML
		}

		public WriteCustomEnv(TrackStreamReader reader)
		{
			super(reader);

			value = reader.getU16();

			if (debugPrint)
				System.out.printf(CMD_FMT + "Write Custom Env ~ %04X%n", streamPos, time, value);
		}

		@Override
		public void fromXML(XmlReader xmr, Element elem)
		{
			value = xmr.readHex(elem, ATTR_VALUE);
		}

		@Override
		public void toXML(XmlWriter xmw)
		{
			XmlTag tag = xmw.createTag(TAG_CMD_WRITE_CUSTOM_ENV, true);
			xmw.addHex(tag, ATTR_VALUE, value);
			xmw.printTag(tag);
		}

		@Override
		public void build(DynamicByteBuffer dbb)
		{
			dbb.putByte(SPECIAL_OPCODE);
			dbb.putByte(SUBOP);
			dbb.putShort(value);
		}
	}

	public static class UseCustomEnv extends TrackCommand
	{
		public static final int SUBOP = 4;

		public int index;

		private UseCustomEnv(Track track)
		{
			super(track); // for fromXML
		}

		public UseCustomEnv(TrackStreamReader reader)
		{
			super(reader);

			index = reader.getU8();
			reader.getU8(); //unused

			if (debugPrint)
				System.out.printf(CMD_FMT + "Use Custom Env ~ %d%n", streamPos, time, index);
		}

		@Override
		public void fromXML(XmlReader xmr, Element elem)
		{
			index = xmr.readInt(elem, ATTR_INDEX);
		}

		@Override
		public void toXML(XmlWriter xmw)
		{
			XmlTag tag = xmw.createTag(TAG_CMD_USE_CUSTOM_ENV, true);
			xmw.addInt(tag, ATTR_INDEX, index);
			xmw.printTag(tag);
		}

		@Override
		public void build(DynamicByteBuffer dbb)
		{
			dbb.putByte(SPECIAL_OPCODE);
			dbb.putByte(SUBOP);
			dbb.putByte(index);
			dbb.putByte(0); // unused
		}
	}

	public static class TriggerSound extends TrackCommand
	{
		public static final int SUBOP = 5;

		public int index;

		private TriggerSound(Track track)
		{
			super(track); // for fromXML
		}

		public TriggerSound(TrackStreamReader reader)
		{
			super(reader);

			index = reader.getU8();
			reader.getU8(); //unused

			if (debugPrint)
				System.out.printf(CMD_FMT + "Trigger Sound ~ %d%n", streamPos, time, index);
		}

		@Override
		public void fromXML(XmlReader xmr, Element elem)
		{
			index = xmr.readInt(elem, ATTR_INDEX);
		}

		@Override
		public void toXML(XmlWriter xmw)
		{
			XmlTag tag = xmw.createTag(TAG_CMD_TRIGGER_SOUND, true);
			xmw.addInt(tag, ATTR_INDEX, index);
			xmw.printTag(tag);
		}

		@Override
		public void build(DynamicByteBuffer dbb)
		{
			dbb.putByte(SPECIAL_OPCODE);
			dbb.putByte(SUBOP);
			dbb.putByte(index);
			dbb.putByte(0); // unused
		}
	}

	public static class ProxMixOverride extends TrackCommand
	{
		public static final int SUBOP = 6;

		public int vol1;
		public int vol2;

		private ProxMixOverride(Track track)
		{
			super(track); // for fromXML
		}

		public ProxMixOverride(TrackStreamReader reader)
		{
			super(reader);

			vol1 = reader.getU8();
			vol2 = reader.getU8();

			if (debugPrint)
				System.out.printf(CMD_FMT + "Prox Mix Override ~ %d and %d%n", streamPos, time, vol1, vol2);
		}

		@Override
		public void fromXML(XmlReader xmr, Element elem)
		{
			vol1 = xmr.readInt(elem, ATTR_VOL1);
			vol2 = xmr.readInt(elem, ATTR_VOL2);
		}

		@Override
		public void toXML(XmlWriter xmw)
		{
			XmlTag tag = xmw.createTag(TAG_CMD_PROX_MIX_OVERRIDE, true);
			xmw.addInt(tag, ATTR_VOL1, vol1);
			xmw.addInt(tag, ATTR_VOL2, vol2);
			xmw.printTag(tag);
		}

		@Override
		public void build(DynamicByteBuffer dbb)
		{
			dbb.putByte(SPECIAL_OPCODE);
			dbb.putByte(SUBOP);
			dbb.putByte(vol1);
			dbb.putByte(vol2);
		}
	}
}
