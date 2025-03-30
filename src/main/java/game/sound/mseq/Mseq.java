package game.sound.mseq;

import static app.Directories.*;
import static game.sound.mseq.Mseq.MseqKey.*;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import app.Environment;
import app.input.IOUtils;
import util.DynamicByteBuffer;
import util.Logger;
import util.xml.XmlKey;
import util.xml.XmlWrapper.XmlReader;
import util.xml.XmlWrapper.XmlSerializable;
import util.xml.XmlWrapper.XmlTag;
import util.xml.XmlWrapper.XmlWriter;

public class Mseq implements XmlSerializable
{
	public final List<TrackSetting> trackSettings = new ArrayList<>();
	public final List<MseqCommand> commands = new ArrayList<>();
	public String name;
	public int firstVoiceIdx;

	private static boolean matching = true;

	public static final int NUM_TRACKS = 10;
	public static final int DRUM_TRACK = 9;
	public static final float MAX_VOLUME = 127.0f;

	private static final int MSEQ_CMD_80_STOP_SOUND = 0x8;
	private static final int MSEQ_CMD_90_PLAY_SOUND = 0x9;
	private static final int MSEQ_CMD_A0_SET_VOLUME_PAN = 0xA;
	private static final int MSEQ_CMD_B0_MULTI = 0xB;

	private static final int MSEQ_CMD_SUB_66_START_LOOP = 0x66;
	private static final int MSEQ_CMD_SUB_67_END_LOOP = 0x67;
	private static final int MSEQ_CMD_SUB_68_SET_REVERB = 0x68;
	private static final int MSEQ_CMD_SUB_69_SET_RESUMABLE = 0x69;

	private static final int MSEQ_CMD_C0_SET_INSTRUMENT = 0xC;
	private static final int MSEQ_CMD_E0_TUNING = 0xE;

	public static void main(String[] args) throws IOException
	{
		Environment.initialize();

		dumpAll();
		copyAll();
		buildAll();
		validateAll();

		Environment.exit();
	}

	public static void dumpAll() throws IOException
	{
		Collection<File> files = IOUtils.getFilesWithExtension(DUMP_AUDIO_RAW, "mseq", false);
		for (File f : files) {
			Logger.log("Extracting " + f.getName());

			Mseq mseq = new Mseq();
			mseq.decode(f);

			String name = FilenameUtils.getBaseName(f.getName());

			try (XmlWriter xmw = new XmlWriter(DUMP_AUDIO_MSEQ.getFile(name + ".xml"))) {
				mseq.toXML(xmw);
			}
		}
	}

	public static void copyAll() throws IOException
	{
		Collection<File> files = IOUtils.getFilesWithExtension(DUMP_AUDIO_MSEQ, "xml", false);
		for (File dumpFile : files) {
			Logger.log("Copying " + dumpFile.getName());

			File destFile = MOD_AUDIO_MSEQ.getFile(dumpFile.getName());
			FileUtils.copyFile(dumpFile, destFile);
		}
	}

	public static void buildAll() throws IOException
	{
		Collection<File> files = IOUtils.getFilesWithExtension(MOD_AUDIO_MSEQ, "xml", false);
		for (File f : files) {
			Logger.log("Building " + f.getName());

			Mseq mseq = new Mseq();

			XmlReader xmr = new XmlReader(f);
			mseq.fromXML(xmr, xmr.getRootElement());

			String filename = FilenameUtils.getBaseName(f.getName());

			File outFile = MOD_AUDIO_BUILD.getFile(filename + ".mseq");
			mseq.build(outFile);
		}
	}

	public static void validateAll() throws IOException
	{
		Collection<File> files = IOUtils.getFilesWithExtension(MOD_AUDIO_RAW, "mseq", false);
		for (File rawFile : files) {
			Logger.log("Validating " + rawFile.getName());

			String filename = FilenameUtils.getBaseName(rawFile.getName());
			File newFile = MOD_AUDIO_BUILD.getFile(filename + ".mseq");

			byte[] rawBytes = FileUtils.readFileToByteArray(rawFile);
			byte[] newBytes = FileUtils.readFileToByteArray(newFile);

			assert (rawBytes.length == newBytes.length);

			for (int i = 0; i < rawBytes.length; i++) {
				assert (rawBytes[i] == newBytes[i]) : String.format("%2X --> %2X", rawBytes[i], newBytes[i]);
			}
		}

		Logger.log("All valid :)");
	}

	private void decode(File f) throws IOException
	{
		Logger.log("Extracting " + f.getName());

		ByteBuffer bb = IOUtils.getDirectBuffer(f);

		String signature = getUTF8(bb, 4);
		assert (signature.equals("MSEQ"));

		int size = bb.getInt();
		name = getUTF8(bb, 4).trim();

		firstVoiceIdx = bb.get() & 0xFF;
		int numSettings = bb.get() & 0xFF;
		int settingsOffset = bb.getShort() & 0xFFFF;
		int streamOffset = bb.getShort() & 0xFFFF;

		bb.position(settingsOffset);

		for (int i = 0; i < numSettings; i++)
			trackSettings.add(new TrackSetting(bb));

		bb.position(streamOffset);

		boolean done = false;
		while (!done) {
			byte op = bb.get();
			if (op >= 0) {
				if (op == 0) {
					break; // done
				}
				if (op >= 0x78) {
					int delay = ((op & 7) << 8) + (bb.get() & 0xFF) + 0x78;
					commands.add(new DelayCommand(delay));
				}
				else {
					commands.add(new DelayCommand(op));
				}
			}
			else {
				int cmd = (op >> 4) & 0xF;
				int track = op & 0xF;
				int arg = bb.get() & 0xFF;

				switch (cmd) {
					case MSEQ_CMD_80_STOP_SOUND: // (pitch)
						commands.add(new StopSoundCommand(track, arg));
						break;
					case MSEQ_CMD_90_PLAY_SOUND: // (pitch, vol) or (drumID from dataPER, vol)
						int vol = bb.get() & 0xFF;
						if (track == DRUM_TRACK)
							commands.add(new PlayDrumCommand(arg, vol));
						else
							commands.add(new PlaySoundCommand(track, arg, vol));
						break;
					case MSEQ_CMD_A0_SET_VOLUME_PAN: // (pan)
						if ((arg & 0x80) != 0)
							commands.add(new SetPanCommand(track, arg & 0x7F));
						else
							commands.add(new SetVolCommand(track, arg & 0x7F));
						break;
					case MSEQ_CMD_B0_MULTI: // (...)
						int arg2 = bb.get() & 0xFF;
						switch (arg) {
							case MSEQ_CMD_SUB_66_START_LOOP: // (loopID [0 or 1])
								commands.add(new StartLoopCommand(arg2));
								break;
							case MSEQ_CMD_SUB_67_END_LOOP: // (loopID [0 or 1], count [0 = forever])
								int count = (arg2 & 0x7C) >> 2; // bit pattern suggests up to 4 loops were considered
								commands.add(new EndLoopCommand(arg2, count));
								break;
							case MSEQ_CMD_SUB_68_SET_REVERB: // (preset)
								commands.add(new SetReverbCommand(track, arg2));
								break;
							case MSEQ_CMD_SUB_69_SET_RESUMABLE: // (enabled)
								commands.add(new SetResumableCommand(track, arg2 == 1));
								break;
						}
						break;
					case MSEQ_CMD_C0_SET_INSTRUMENT: // (bank, patch)
						int patch = bb.get() & 0xFF;
						commands.add(new SetInstrumentCommand(track, arg, patch));
						break;
					case MSEQ_CMD_E0_TUNING: // (coarse, fine)
						int fine = bb.get() & 0xFF;
						commands.add(new SetTuneCommand(track, (arg << 8 | fine)));
						break;
				}
			}
		}
	}

	public void build(File outFile) throws IOException
	{
		DynamicByteBuffer dbb = new DynamicByteBuffer();

		dbb.position(0x18);

		for (MseqCommand cmd : commands)
			cmd.build(dbb);
		dbb.putByte(0); // end

		int endOffset = dbb.position();
		int trackSettingsOffset = 0;

		if (trackSettings.size() > 0) {
			dbb.align(4);
			trackSettingsOffset = dbb.position();

			for (TrackSetting setting : trackSettings)
				setting.build(dbb);

			endOffset = dbb.position();
		}

		dbb.align(16);

		// write header
		dbb.position(0);

		dbb.putUTF8("MSEQ", false);
		dbb.putInt(endOffset);
		dbb.putUTF8(String.format("%-4s", name), false);
		dbb.putByte(firstVoiceIdx);
		dbb.putByte(trackSettings.size());
		dbb.putShort(trackSettingsOffset);
		dbb.putShort(0x18);

		IOUtils.writeBufferToFile(dbb.getFixedBuffer(), outFile);
	}

	private String getUTF8(ByteBuffer bb, int len)
	{
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < len; i++)
			sb.append((char) bb.get());
		return sb.toString();
	}

	public class TrackSetting implements XmlSerializable
	{
		public int track;
		public int type;
		public int time;
		public int delta;
		public int goal;

		public TrackSetting()
		{} // for fromXML

		public TrackSetting(ByteBuffer bb)
		{
			track = bb.get() & 0xFF;
			type = bb.get() & 0xFF;
			time = bb.getShort() & 0xFFFF;
			delta = bb.getShort() & 0xFFFF;
			goal = bb.getShort() & 0xFFFF;
		}

		public void build(DynamicByteBuffer dbb)
		{
			dbb.putByte(track);
			dbb.putByte(type);
			dbb.putShort(time);
			dbb.putShort(delta);
			dbb.putShort(goal);
		}

		@Override
		public void fromXML(XmlReader xmr, Element elem)
		{
			track = xmr.readHex(elem, ATTR_TRACK);
			type = xmr.readHex(elem, ATTR_TYPE);
			time = xmr.readHex(elem, ATTR_TIME);
			delta = xmr.readHex(elem, ATTR_DELTA);
			goal = xmr.readHex(elem, ATTR_GOAL);
		}

		@Override
		public void toXML(XmlWriter xmw)
		{
			XmlTag tag = xmw.createTag(TAG_SETTINGS, true);
			xmw.addHex(tag, ATTR_TRACK, track);
			xmw.addHex(tag, ATTR_TYPE, type);
			xmw.addHex(tag, ATTR_TIME, time);
			xmw.addHex(tag, ATTR_DELTA, delta);
			xmw.addHex(tag, ATTR_GOAL, goal);
			xmw.printTag(tag);
		}
	}

	public static enum MseqKey implements XmlKey
	{
		// @formatter:off
		TAG_MSEQ            ("Mseq"),
		ATTR_NAME		   	("name"),
		ATTR_FIRST_VOICE   	("firstVoice"),
		TAG_SETTINGS_LIST   ("TrackSettings"),
		TAG_SETTINGS        ("TrackSetting"),
		ATTR_TYPE           ("type"),
		ATTR_TIME           ("time"),
		ATTR_DELTA          ("delta"),
		ATTR_GOAL           ("goal"),
		TAG_COMMAND_LIST    ("Commands"),
		TAG_DELAY           ("Delay"),
		TAG_SET_VOL         ("SetVolume"),
		TAG_SET_TUNE        ("SetTune"),
		TAG_SET_PAN         ("SetPan"),
		TAG_SET_REVERB      ("SetReverb"),
		TAG_SET_INSTRUMENT  ("SetInstrument"),
		TAG_STOP_SOUND      ("StopSound"),
		TAG_PLAY_SOUND      ("PlaySound"),
		TAG_PLAY_DRUM       ("PlayDrum"),
		TAG_START_LOOP      ("StartLoop"),
		TAG_END_LOOP        ("EndLoop"),
		TAG_SET_RESUMABLE   ("SetResumable"),
		ATTR_TRACK          ("track"),
		ATTR_VOLUME         ("volume"),
		ATTR_PITCH          ("pitch"),
		ATTR_TUNE           ("tune"),
		ATTR_PAN            ("pan"),
		ATTR_REVERB         ("reverb"),
		ATTR_DRUM           ("drum"),
		ATTR_BANK           ("bank"),
		ATTR_PATCH          ("patch"),
		ATTR_DURATION       ("duration"),
		ATTR_LOOP_ID        ("loopID"),
		ATTR_LOOP_COUNT     ("count"),
		ATTR_RESUMABLE    	("resumable");
		// @formatter:on

		private final String key;

		MseqKey(String key)
		{
			this.key = key;
		}

		@Override
		public String toString()
		{
			return key;
		}
	}

	public static Mseq load(File xmlFile)
	{
		Mseq mseq = new Mseq();

		XmlReader xmr = new XmlReader(xmlFile);
		mseq.fromXML(xmr, xmr.getRootElement());

		return mseq;
	}

	private Mseq()
	{}

	@Override
	public void fromXML(XmlReader xmr, Element root)
	{
		name = xmr.getAttribute(root, ATTR_NAME);
		if (name.length() > 4) {
			Logger.logfWarning("Invalid name for MSEQ file will be truncated: " + name);
			name = name.substring(0, 4);
		}
		firstVoiceIdx = xmr.readHex(root, ATTR_FIRST_VOICE);

		Element settingsElem = xmr.getUniqueRequiredTag(root, TAG_SETTINGS_LIST);

		for (Element elem : xmr.getTags(settingsElem, TAG_SETTINGS)) {
			TrackSetting setting = new TrackSetting();
			setting.fromXML(xmr, elem);
			trackSettings.add(setting);
		}

		Element commandsElem = xmr.getUniqueRequiredTag(root, TAG_COMMAND_LIST);

		for (Node child = commandsElem.getFirstChild(); child != null; child = child.getNextSibling()) {
			if (child instanceof Element elem) {
				commands.add(makeCommand(xmr, elem));
			}
		}
	}

	@Override
	public void toXML(XmlWriter xmw)
	{
		XmlTag root = xmw.createTag(TAG_MSEQ, false);
		xmw.addAttribute(root, ATTR_NAME, name);
		xmw.addHex(root, ATTR_FIRST_VOICE, firstVoiceIdx);
		xmw.openTag(root);

		XmlTag settingsListTag = xmw.createTag(TAG_SETTINGS_LIST, false);
		xmw.openTag(settingsListTag);

		for (TrackSetting s : trackSettings)
			s.toXML(xmw);

		xmw.closeTag(settingsListTag);

		XmlTag commandListTag = xmw.createTag(TAG_COMMAND_LIST, false);
		xmw.openTag(commandListTag);

		for (MseqCommand c : commands)
			c.toXML(xmw);

		xmw.closeTag(commandListTag);

		xmw.closeTag(root);
		xmw.save();
	}

	private static MseqCommand makeCommand(XmlReader xmr, Element elem)
	{
		String tagName = elem.getTagName();
		MseqCommand cmd;

		switch (tagName) {
			// @formatter:off
			case "Delay":           cmd = new DelayCommand(); break;
			case "SetVolume":  		cmd = new SetVolCommand(); break;
			case "SetPan":    	    cmd = new SetPanCommand(); break;
			case "SetReverb":       cmd = new SetReverbCommand(); break;
			case "SetInstrument":   cmd = new SetInstrumentCommand(); break;
			case "StopSound":       cmd = new StopSoundCommand(); break;
			case "PlaySound":       cmd = new PlaySoundCommand(); break;
			case "PlayDrum":        cmd = new PlayDrumCommand(); break;
			case "StartLoop":       cmd = new StartLoopCommand(); break;
			case "EndLoop":         cmd = new EndLoopCommand(); break;
			case "SetTune":         cmd = new SetTuneCommand(); break;
			case "SetResumable":    cmd = new SetResumableCommand(); break;
			// @formatter:on
			default:
				throw new IllegalArgumentException("Unknown command tag: " + tagName);
		}

		cmd.fromXML(xmr, elem);
		return cmd;
	}

	public static abstract class MseqCommand implements XmlSerializable
	{
		public abstract void build(DynamicByteBuffer dbb);
	}

	public static class DelayCommand extends MseqCommand
	{
		public int duration;

		public DelayCommand()
		{} // for fromXML

		public DelayCommand(int duration)
		{
			this.duration = duration;
		}

		@Override
		public void fromXML(XmlReader xmr, Element elem)
		{
			duration = xmr.readHex(elem, ATTR_DURATION);
		}

		@Override
		public void toXML(XmlWriter xmw)
		{
			XmlTag tag = xmw.createTag(TAG_DELAY, true);
			xmw.addHex(tag, ATTR_DURATION, duration);
			xmw.printTag(tag);
		}

		@Override
		public void build(DynamicByteBuffer dbb)
		{
			if (duration >= 0x78) {
				// two byte encoding
				int amt = duration - 0x78;
				int low = amt & 0xFF;
				int high = (amt >> 8) & 0x7;

				dbb.putByte(high | 0x78);
				dbb.putByte(low);
			}
			else {
				dbb.putByte(duration);
			}
		}
	}

	public static class SetInstrumentCommand extends MseqCommand
	{
		public int track;
		public int bank;
		public int patch;

		//TODO by name?

		public SetInstrumentCommand()
		{} // for fromXML

		public SetInstrumentCommand(int track, int bank, int patch)
		{
			this.track = track;
			this.bank = bank;
			this.patch = patch;
		}

		@Override
		public void fromXML(XmlReader xmr, Element elem)
		{
			track = xmr.readHex(elem, ATTR_TRACK);
			bank = xmr.readHex(elem, ATTR_BANK);
			patch = xmr.readHex(elem, ATTR_PATCH);
		}

		@Override
		public void toXML(XmlWriter xmw)
		{
			XmlTag tag = xmw.createTag(TAG_SET_INSTRUMENT, true);
			xmw.addHex(tag, ATTR_TRACK, track);
			xmw.addHex(tag, ATTR_BANK, bank);
			xmw.addHex(tag, ATTR_PATCH, patch);
			xmw.printTag(tag);
		}

		@Override
		public void build(DynamicByteBuffer dbb)
		{
			dbb.putByte((MSEQ_CMD_C0_SET_INSTRUMENT << 4) | (track & 0xF));
			dbb.putByte(bank);
			dbb.putByte(patch);
		}
	}

	public static class PlayDrumCommand extends MseqCommand
	{
		// track is always DRUM_TRACK
		public int drumID;
		public int volume;

		public PlayDrumCommand()
		{} // for fromXML

		public PlayDrumCommand(int drumID, int volume)
		{
			this.drumID = drumID;
			this.volume = volume;
		}

		@Override
		public void fromXML(XmlReader xmr, Element elem)
		{
			drumID = xmr.readHex(elem, ATTR_DRUM);
			volume = xmr.readHex(elem, ATTR_VOLUME);
		}

		@Override
		public void toXML(XmlWriter xmw)
		{
			XmlTag tag = xmw.createTag(TAG_PLAY_DRUM, true);
			xmw.addHex(tag, ATTR_DRUM, drumID);
			xmw.addHex(tag, ATTR_VOLUME, volume);
			xmw.printTag(tag);
		}

		@Override
		public void build(DynamicByteBuffer dbb)
		{
			dbb.putByte((MSEQ_CMD_90_PLAY_SOUND << 4) | DRUM_TRACK);
			dbb.putByte(drumID);
			dbb.putByte(volume);
		}
	}

	public static class PlaySoundCommand extends MseqCommand
	{
		public int track;
		public int pitch;
		public int volume;

		public PlaySoundCommand()
		{} // for fromXML

		public PlaySoundCommand(int track, int pitch, int volume)
		{
			this.track = track;
			this.pitch = pitch;
			this.volume = volume;
		}

		@Override
		public void fromXML(XmlReader xmr, Element elem)
		{
			track = xmr.readHex(elem, ATTR_TRACK);
			pitch = xmr.readHex(elem, ATTR_PITCH);
			volume = xmr.readHex(elem, ATTR_VOLUME);
		}

		@Override
		public void toXML(XmlWriter xmw)
		{
			XmlTag tag = xmw.createTag(TAG_PLAY_SOUND, true);
			xmw.addHex(tag, ATTR_TRACK, track);
			xmw.addHex(tag, ATTR_PITCH, pitch);
			xmw.addHex(tag, ATTR_VOLUME, volume);
			xmw.printTag(tag);
		}

		@Override
		public void build(DynamicByteBuffer dbb)
		{
			dbb.putByte((MSEQ_CMD_90_PLAY_SOUND << 4) | (track & 0xF));
			dbb.putByte(pitch);
			dbb.putByte(volume);
		}
	}

	public static class StopSoundCommand extends MseqCommand
	{
		public int track;
		public int pitch;

		public StopSoundCommand()
		{} // for fromXML

		public StopSoundCommand(int track, int pitch)
		{
			this.track = track;
			this.pitch = pitch;
		}

		@Override
		public void fromXML(XmlReader xmr, Element elem)
		{
			track = xmr.readHex(elem, ATTR_TRACK);
			pitch = xmr.readHex(elem, ATTR_PITCH);
		}

		@Override
		public void toXML(XmlWriter xmw)
		{
			XmlTag tag = xmw.createTag(TAG_STOP_SOUND, true);
			xmw.addHex(tag, ATTR_TRACK, track);
			xmw.addHex(tag, ATTR_PITCH, pitch);
			xmw.printTag(tag);
		}

		@Override
		public void build(DynamicByteBuffer dbb)
		{
			dbb.putByte((MSEQ_CMD_80_STOP_SOUND << 4) | (track & 0xF));
			dbb.putByte(pitch);
		}
	}

	public static class SetVolCommand extends MseqCommand
	{
		public int track;
		public int volume;

		public SetVolCommand()
		{} // for fromXML

		public SetVolCommand(int track, int volume)
		{
			this.track = track;
			this.volume = volume;
		}

		@Override
		public void fromXML(XmlReader xmr, Element elem)
		{
			track = xmr.readHex(elem, ATTR_TRACK);
			volume = xmr.readHex(elem, ATTR_VOLUME);
		}

		@Override
		public void toXML(XmlWriter xmw)
		{
			XmlTag tag = xmw.createTag(TAG_SET_VOL, true);
			xmw.addHex(tag, ATTR_TRACK, track);
			xmw.addHex(tag, ATTR_VOLUME, volume);
			xmw.printTag(tag);
		}

		@Override
		public void build(DynamicByteBuffer dbb)
		{
			dbb.putByte((MSEQ_CMD_A0_SET_VOLUME_PAN << 4) | (track & 0xF));
			dbb.putByte(volume & 0x7F);
		}
	}

	public static class SetPanCommand extends MseqCommand
	{
		public int track;
		public int pan;

		public SetPanCommand()
		{} // for fromXML

		public SetPanCommand(int track, int pan)
		{
			this.track = track;
			this.pan = pan;
		}

		@Override
		public void fromXML(XmlReader xmr, Element elem)
		{
			track = xmr.readHex(elem, ATTR_TRACK);
			pan = xmr.readHex(elem, ATTR_PAN);
		}

		@Override
		public void toXML(XmlWriter xmw)
		{
			XmlTag tag = xmw.createTag(TAG_SET_PAN, true);
			xmw.addHex(tag, ATTR_TRACK, track);
			xmw.addHex(tag, ATTR_PAN, pan);
			xmw.printTag(tag);
		}

		@Override
		public void build(DynamicByteBuffer dbb)
		{
			dbb.putByte((MSEQ_CMD_A0_SET_VOLUME_PAN << 4) | (track & 0xF));
			dbb.putByte((pan & 0x7F) | 0x80);
		}
	}

	public static class SetTuneCommand extends Mseq.MseqCommand
	{
		public int track;
		public int value; // 16-bit coarse/fine packed

		public SetTuneCommand()
		{} // for fromXML

		public SetTuneCommand(int track, int value)
		{
			this.track = track;
			this.value = value;
		}

		@Override
		public void fromXML(XmlReader xmr, Element elem)
		{
			track = xmr.readHex(elem, ATTR_TRACK);
			value = xmr.readHex(elem, ATTR_TUNE);
		}

		@Override
		public void toXML(XmlWriter xmw)
		{
			XmlTag tag = xmw.createTag(TAG_SET_TUNE, true);
			xmw.addHex(tag, ATTR_TRACK, track);
			xmw.addHex(tag, ATTR_TUNE, value);
			xmw.printTag(tag);
		}

		@Override
		public void build(DynamicByteBuffer dbb)
		{
			dbb.putByte((MSEQ_CMD_E0_TUNING << 4) | (track & 0xF));
			dbb.putByte(value >> 8);
			dbb.putByte(value & 0xFF);
		}
	}

	public static class SetReverbCommand extends Mseq.MseqCommand
	{
		public int track;
		public int reverb;

		public SetReverbCommand()
		{} // for fromXML

		public SetReverbCommand(int track, int reverb)
		{
			this.track = track;
			this.reverb = reverb;
		}

		@Override
		public void fromXML(XmlReader xmr, Element elem)
		{
			track = xmr.readHex(elem, ATTR_TRACK);
			reverb = xmr.readHex(elem, ATTR_REVERB);
		}

		@Override
		public void toXML(XmlWriter xmw)
		{
			XmlTag tag = xmw.createTag(TAG_SET_REVERB, true);
			xmw.addHex(tag, ATTR_TRACK, track);
			xmw.addHex(tag, ATTR_REVERB, reverb);
			xmw.printTag(tag);
		}

		@Override
		public void build(DynamicByteBuffer dbb)
		{
			dbb.putByte((MSEQ_CMD_B0_MULTI << 4) | (track & 0xF));
			dbb.putByte(MSEQ_CMD_SUB_68_SET_REVERB);
			dbb.putByte(reverb);
		}
	}

	public static class SetResumableCommand extends Mseq.MseqCommand
	{
		public int track;
		public boolean resumable;

		public SetResumableCommand()
		{} // for fromXML

		public SetResumableCommand(int track, boolean resumable)
		{
			this.track = track;
			this.resumable = resumable;
		}

		@Override
		public void fromXML(XmlReader xmr, Element elem)
		{
			track = xmr.readHex(elem, ATTR_TRACK);
			resumable = xmr.readBoolean(elem, ATTR_RESUMABLE);
		}

		@Override
		public void toXML(XmlWriter xmw)
		{
			XmlTag tag = xmw.createTag(TAG_SET_RESUMABLE, true);
			xmw.addHex(tag, ATTR_TRACK, track);
			xmw.addBoolean(tag, ATTR_RESUMABLE, resumable);
			xmw.printTag(tag);
		}

		@Override
		public void build(DynamicByteBuffer dbb)
		{
			dbb.putByte((MSEQ_CMD_B0_MULTI << 4) | (track & 0xF));
			dbb.putByte(MSEQ_CMD_SUB_69_SET_RESUMABLE);
			dbb.putByte(resumable ? 1 : 0);
		}
	}

	public static class StartLoopCommand extends MseqCommand
	{
		public int loopID;

		public StartLoopCommand()
		{} // for fromXML

		public StartLoopCommand(int loopID)
		{
			this.loopID = loopID;
		}

		@Override
		public void fromXML(XmlReader xmr, Element elem)
		{
			loopID = xmr.readHex(elem, ATTR_LOOP_ID);
		}

		@Override
		public void toXML(XmlWriter xmw)
		{
			XmlTag tag = xmw.createTag(TAG_START_LOOP, true);
			xmw.addHex(tag, ATTR_LOOP_ID, loopID);
			xmw.printTag(tag);
		}

		@Override
		public void build(DynamicByteBuffer dbb)
		{
			dbb.putByte(MSEQ_CMD_B0_MULTI << 4);
			dbb.putByte(MSEQ_CMD_SUB_66_START_LOOP);

			// E8_521 uses loopID of 7F, which is way out of range and the engine will treat as 1
			if (matching)
				dbb.putByte(loopID);
			else
				dbb.putByte(loopID & 1);
		}
	}

	public static class EndLoopCommand extends MseqCommand
	{
		public int loopID;
		public int count; // 0 = forever

		public EndLoopCommand()
		{} // for fromXML

		public EndLoopCommand(int loopID, int count)
		{
			this.loopID = loopID;
			this.count = count;
		}

		@Override
		public void fromXML(XmlReader xmr, Element elem)
		{
			loopID = xmr.readHex(elem, ATTR_LOOP_ID);
			count = xmr.readHex(elem, ATTR_LOOP_COUNT);
		}

		@Override
		public void toXML(XmlWriter xmw)
		{
			XmlTag tag = xmw.createTag(TAG_END_LOOP, true);
			xmw.addHex(tag, ATTR_LOOP_ID, loopID);
			xmw.addHex(tag, ATTR_LOOP_COUNT, count);
			xmw.printTag(tag);
		}

		@Override
		public void build(DynamicByteBuffer dbb)
		{
			dbb.putByte(MSEQ_CMD_B0_MULTI << 4);
			dbb.putByte(MSEQ_CMD_SUB_67_END_LOOP);

			// E8_521 uses loopID of 3, which is out of range and the engine will treat as 1
			if (matching)
				dbb.putByte(((count & 0x1F) << 2) | (loopID & 3));
			else
				dbb.putByte(((count & 0x1F) << 2) | (loopID & 1));
		}
	}
}
