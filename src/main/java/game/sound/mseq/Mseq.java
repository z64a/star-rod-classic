package game.sound.mseq;

import static app.Directories.*;
import static game.sound.mseq.Mseq.MseqKey.*;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import app.Environment;
import app.input.IOUtils;
import game.sound.AudioCatalog;
import game.sound.AudioModder;
import game.sound.SoundBankCatalog;
import game.sound.SoundXml;
import util.DynamicByteBuffer;
import util.Logger;
import util.xml.XmlKey;
import util.xml.XmlWrapper.XmlReader;
import util.xml.XmlWrapper.XmlSerializable;
import util.xml.XmlWrapper.XmlTag;
import util.xml.XmlWrapper.XmlWriter;

public class Mseq implements XmlSerializable
{
	public final List<TrackRamp> trackRamps = new ArrayList<>();
	public final List<MseqCommand> commands = new ArrayList<>();
	public String name;
	public String code;
	public int firstVoiceIdx;
	public int duration;
	private SoundBankCatalog soundBankCatalog;

	public static final int NUM_TRACKS = 10;
	public static final int DRUM_TRACK = 9;
	public static final float MAX_VOL_8 = 127.0f; // 0x7F
	public static final float MAX_VOL_16 = 32767.0f; // 0x7FFF

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
		SoundBankCatalog catalog = SoundBankCatalog.loadDump().withAuxiliaryBank(2, "SPC3.bk");
		Map<String, String> names = AudioCatalog.readMseqNames(
			DUMP_AUDIO.getFile(FN_AUDIO_AMBIENTS), DUMP_AUDIO.getFile(FN_AUDIO_SONGS));
		Collection<File> files = IOUtils.getFilesWithExtension(DUMP_AUDIO_RAW, "mseq", false);
		for (File f : files) {
			Logger.log("Extracting " + f.getName());

			Mseq mseq = new Mseq();
			mseq.soundBankCatalog = catalog;
			mseq.decode(f);
			String predefinedName = AudioCatalog.getName(names, f.getName());
			mseq.name = predefinedName == null ? FilenameUtils.getBaseName(f.getName()) : predefinedName;

			String fileBaseName = FilenameUtils.getBaseName(f.getName());

			try (XmlWriter xmw = new XmlWriter(DUMP_AUDIO_MSEQ.getFile(fileBaseName + ".xml"))) {
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
		SoundBankCatalog catalog = SoundBankCatalog.loadMod().withAuxiliaryBank(2, "SPC3.bk");
		Collection<File> files = IOUtils.getFilesWithExtension(MOD_AUDIO_MSEQ, "xml", false);
		for (File f : files) {
			String filename = FilenameUtils.getBaseName(f.getName());
			String outputName = filename + ".mseq";
			if (AudioModder.hasOverride(outputName)) {
				Logger.log("Using audio override for " + outputName);
				continue;
			}

			Logger.log("Building " + f.getName());

			Mseq mseq = new Mseq();
			mseq.soundBankCatalog = catalog;

			XmlReader xmr = new XmlReader(f);
			mseq.fromXML(xmr, xmr.getRootElement());

			File outFile = MOD_AUDIO_BUILD.getFile(outputName);
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

	public void calculateTiming()
	{
		int time = 0;
		for (MseqCommand cmd : commands) {
			cmd.startTime = time;

			if (cmd instanceof DelayCommand delay)
				time += delay.duration;
		}

		duration = time;
	}

	private void decode(File f) throws IOException
	{
		Logger.log("Extracting " + f.getName());

		ByteBuffer bb = IOUtils.getDirectBuffer(f);

		String signature = getUTF8(bb, 4);
		assert (signature.equals("MSEQ"));

		int size = bb.getInt();
		code = getUTF8(bb, 4).trim();
		name = code;

		firstVoiceIdx = bb.get() & 0xFF;
		int numRamps = bb.get() & 0xFF;
		int rampsOffset = bb.getShort() & 0xFFFF;
		int streamOffset = bb.getShort() & 0xFFFF;

		bb.position(rampsOffset);

		for (int i = 0; i < numRamps; i++)
			trackRamps.add(new TrackRamp(bb));

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
								commands.add(new StartLoopCommand(arg2 & 1));
								break;
							case MSEQ_CMD_SUB_67_END_LOOP: // (loopID [0 or 1], count [0 = forever])
								int count = (arg2 & 0x7C) >> 2; // bit pattern suggests up to 4 loops were considered
								commands.add(new EndLoopCommand(arg2 & 1, count));
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
						SetInstrumentCommand command = new SetInstrumentCommand(track, arg, patch);
						command.setWav(soundBankCatalog);
						commands.add(command);
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

		for (MseqCommand cmd : commands) {
			if (cmd instanceof SetInstrumentCommand setInstrument)
				setInstrument.resolveWav(soundBankCatalog);
			cmd.build(dbb);
		}
		dbb.putByte(0); // end

		int endOffset = dbb.position();
		int trackRampsOffset = 0;

		if (trackRamps.size() > 0) {
			dbb.align(4);
			trackRampsOffset = dbb.position();

			for (TrackRamp ramp : trackRamps)
				ramp.build(dbb);

			endOffset = dbb.position();
		}

		// write header
		dbb.position(0);

		dbb.putUTF8("MSEQ", false);
		dbb.putInt(endOffset);
		dbb.putUTF8(String.format("%-4s", code), false);
		dbb.putByte(firstVoiceIdx);
		dbb.putByte(trackRamps.size());
		dbb.putShort(trackRampsOffset);
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

	public class TrackRamp implements XmlSerializable
	{
		public int track;
		public TrackRampType type;
		public int time;
		public short delta;
		public short goal;

		public TrackRamp()
		{} // for fromXML

		public TrackRamp(ByteBuffer bb)
		{
			track = bb.get() & 0xFF;
			type = TrackRampType.fromBinary(bb.get() & 0xFF);
			time = bb.getShort() & 0xFFFF;
			delta = bb.getShort();
			goal = bb.getShort();
		}

		public void build(DynamicByteBuffer dbb)
		{
			dbb.putByte(track);
			dbb.putByte(type.binaryValue);
			dbb.putShort(time);
			dbb.putShort(delta);
			dbb.putShort(goal);
		}

		@Override
		public void fromXML(XmlReader xmr, Element elem)
		{
			track = SoundXml.readInt(xmr, elem, ATTR_TRACK, 0, NUM_TRACKS - 1);
			xmr.requiresAttribute(elem, ATTR_TYPE);
			type = TrackRampType.fromName(xmr.getAttribute(elem, ATTR_TYPE));
			if (type == null) {
				xmr.complain("Unknown track ramp type: " + xmr.getAttribute(elem, ATTR_TYPE));
				return;
			}
			time = SoundXml.readInt(xmr, elem, ATTR_TIME, 1, 32767);
			if (type == TrackRampType.TUNE) {
				delta = (short) SoundXml.readInt(xmr, elem, ATTR_DELTA, -32768, 32767);
				goal = (short) SoundXml.readInt(xmr, elem, ATTR_GOAL, -32768, 32767);
			}
			else {
				delta = (short) SoundXml.readHex(xmr, elem, ATTR_DELTA, -32768, 32767);
				goal = (short) SoundXml.readHex(xmr, elem, ATTR_GOAL, 0, 32767);
			}
		}

		@Override
		public void toXML(XmlWriter xmw)
		{
			XmlTag tag = xmw.createTag(TAG_RAMP, true);
			xmw.addInt(tag, ATTR_TRACK, track);
			xmw.addAttribute(tag, ATTR_TYPE, type.name);
			xmw.addInt(tag, ATTR_TIME, time);
			if (type == TrackRampType.TUNE) {
				xmw.addInt(tag, ATTR_DELTA, delta);
				xmw.addInt(tag, ATTR_GOAL, goal);
			}
			else {
				SoundXml.addHex(xmw, tag, ATTR_DELTA, 4, delta);
				SoundXml.addHex(xmw, tag, ATTR_GOAL, 4, goal);
			}
			xmw.printTag(tag);
		}
	}

	public static enum TrackRampType
	{
		TUNE(0, "tune"),
		VOLUME(1, "volume");

		private final int binaryValue;
		private final String name;

		private TrackRampType(int binaryValue, String name)
		{
			this.binaryValue = binaryValue;
			this.name = name;
		}

		private static TrackRampType fromBinary(int value)
		{
			if (value == TUNE.binaryValue)
				return TUNE;
			if (value == VOLUME.binaryValue)
				return VOLUME;
			throw new IllegalArgumentException("Unknown MSEQ track ramp type: " + value);
		}

		private static TrackRampType fromName(String name)
		{
			for (TrackRampType type : values()) {
				if (type.name.equals(name))
					return type;
			}
			return null;
		}
	}

	public static enum MseqKey implements XmlKey
	{
		// @formatter:off
		TAG_MSEQ            ("Mseq"),
		ATTR_CODE		   	("code"),
		ATTR_NAME		   	("name"),
		ATTR_FIRST_VOICE   	("firstVoice"),
		TAG_RAMP_LIST       ("TrackRamps"),
		TAG_RAMP            ("TrackRamp"),
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
		ATTR_WAV            ("wav"),
		ATTR_ENVELOPE       ("envelope"),
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
		mseq.soundBankCatalog = SoundBankCatalog.loadMod().withAuxiliaryBank(2, "SPC3.bk");

		XmlReader xmr = new XmlReader(xmlFile);
		mseq.fromXML(xmr, xmr.getRootElement());
		for (MseqCommand command : mseq.commands) {
			if (command instanceof SetInstrumentCommand setInstrument)
				setInstrument.resolveWav(mseq.soundBankCatalog);
		}

		return mseq;
	}

	private Mseq()
	{}

	@Override
	public void fromXML(XmlReader xmr, Element root)
	{
		xmr.requiresAttribute(root, ATTR_NAME);
		if (xmr.hasAttribute(root, ATTR_CODE)) {
			name = xmr.getAttribute(root, ATTR_NAME);
			code = xmr.getAttribute(root, ATTR_CODE);
		}
		else {
			// legacy MSEQ XML used name for the binary code
			code = xmr.getAttribute(root, ATTR_NAME);
			name = code;
		}
		if (name.isBlank())
			xmr.complain("MSEQ name must not be empty");
		if (code.isEmpty() || code.length() > 4)
			xmr.complain("MSEQ code must contain one through four characters: " + code);
		firstVoiceIdx = SoundXml.readInt(xmr, root, ATTR_FIRST_VOICE, 0, 23);

		Element rampsElem = xmr.getUniqueRequiredTag(root, TAG_RAMP_LIST);

		for (Element elem : xmr.getTags(rampsElem, TAG_RAMP)) {
			if (trackRamps.size() == 0xFF)
				xmr.complain("MSEQ cannot contain more than 255 track ramps");
			TrackRamp ramp = new TrackRamp();
			ramp.fromXML(xmr, elem);
			trackRamps.add(ramp);
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
		xmw.addAttribute(root, ATTR_CODE, code);
		xmw.addAttribute(root, ATTR_NAME, name);
		xmw.addInt(root, ATTR_FIRST_VOICE, firstVoiceIdx);
		xmw.openTag(root);

		XmlTag rampListTag = xmw.createTag(TAG_RAMP_LIST, false);
		xmw.openTag(rampListTag);

		for (TrackRamp ramp : trackRamps)
			ramp.toXML(xmw);

		xmw.closeTag(rampListTag);

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
		public int startTime;
		public int duration = 0;

		public abstract void build(DynamicByteBuffer dbb);
	}

	public static class DelayCommand extends MseqCommand
	{
		public DelayCommand()
		{} // for fromXML

		public DelayCommand(int duration)
		{
			this.duration = duration;
		}

		@Override
		public void fromXML(XmlReader xmr, Element elem)
		{
			duration = SoundXml.readInt(xmr, elem, ATTR_DURATION, 1, 0x877);
		}

		@Override
		public void toXML(XmlWriter xmw)
		{
			XmlTag tag = xmw.createTag(TAG_DELAY, true);
			xmw.addInt(tag, ATTR_DURATION, duration);
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
		public String wav;
		public int envelope;

		public SetInstrumentCommand()
		{} // for fromXML

		public SetInstrumentCommand(int track, int bank, int patch)
		{
			this.track = track;
			this.bank = bank;
			this.patch = patch;
		}

		public void setWav(SoundBankCatalog catalog)
		{
			SoundBankCatalog.WavReference reference = catalog.getWav(bank, patch);
			wav = reference.wav;
			envelope = reference.envelope;
		}

		public void resolveWav(SoundBankCatalog catalog)
		{
			SoundBankCatalog.InstrumentAddress address = catalog.getAddress(wav, envelope);
			bank = address.bank;
			patch = address.patch;
		}

		@Override
		public void fromXML(XmlReader xmr, Element elem)
		{
			track = SoundXml.readInt(xmr, elem, ATTR_TRACK, 0, DRUM_TRACK - 1);
			xmr.requiresAttribute(elem, ATTR_WAV);
			wav = xmr.getAttribute(elem, ATTR_WAV);
			if (xmr.hasAttribute(elem, ATTR_ENVELOPE))
				envelope = SoundXml.readInt(xmr, elem, ATTR_ENVELOPE, 0, 3);
		}

		@Override
		public void toXML(XmlWriter xmw)
		{
			XmlTag tag = xmw.createTag(TAG_SET_INSTRUMENT, true);
			xmw.addInt(tag, ATTR_TRACK, track);
			xmw.addAttribute(tag, ATTR_WAV, wav);
			if (envelope != 0)
				xmw.addInt(tag, ATTR_ENVELOPE, envelope);
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
			drumID = SoundXml.readInt(xmr, elem, ATTR_DRUM, 0, 127);
			volume = SoundXml.readHex(xmr, elem, ATTR_VOLUME, 0, 127);
		}

		@Override
		public void toXML(XmlWriter xmw)
		{
			XmlTag tag = xmw.createTag(TAG_PLAY_DRUM, true);
			xmw.addInt(tag, ATTR_DRUM, drumID);
			SoundXml.addHex(xmw, tag, ATTR_VOLUME, 2, volume);
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
			track = SoundXml.readInt(xmr, elem, ATTR_TRACK, 0, DRUM_TRACK - 1);
			pitch = SoundXml.readInt(xmr, elem, ATTR_PITCH, 0, 255);
			volume = SoundXml.readHex(xmr, elem, ATTR_VOLUME, 0, 127);
		}

		@Override
		public void toXML(XmlWriter xmw)
		{
			XmlTag tag = xmw.createTag(TAG_PLAY_SOUND, true);
			xmw.addInt(tag, ATTR_TRACK, track);
			xmw.addInt(tag, ATTR_PITCH, pitch);
			SoundXml.addHex(xmw, tag, ATTR_VOLUME, 2, volume);
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
			track = SoundXml.readInt(xmr, elem, ATTR_TRACK, 0, NUM_TRACKS - 1);
			pitch = SoundXml.readInt(xmr, elem, ATTR_PITCH, 0, 255);
		}

		@Override
		public void toXML(XmlWriter xmw)
		{
			XmlTag tag = xmw.createTag(TAG_STOP_SOUND, true);
			xmw.addInt(tag, ATTR_TRACK, track);
			xmw.addInt(tag, ATTR_PITCH, pitch);
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
			track = SoundXml.readInt(xmr, elem, ATTR_TRACK, 0, NUM_TRACKS - 1);
			volume = SoundXml.readHex(xmr, elem, ATTR_VOLUME, 0, 127);
		}

		@Override
		public void toXML(XmlWriter xmw)
		{
			XmlTag tag = xmw.createTag(TAG_SET_VOL, true);
			xmw.addInt(tag, ATTR_TRACK, track);
			SoundXml.addHex(xmw, tag, ATTR_VOLUME, 2, volume);
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
			track = SoundXml.readInt(xmr, elem, ATTR_TRACK, 0, NUM_TRACKS - 1);
			pan = SoundXml.readInt(xmr, elem, ATTR_PAN, 0, 127);
		}

		@Override
		public void toXML(XmlWriter xmw)
		{
			XmlTag tag = xmw.createTag(TAG_SET_PAN, true);
			xmw.addInt(tag, ATTR_TRACK, track);
			xmw.addInt(tag, ATTR_PAN, pan);
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
		public int value; // signed cents

		public SetTuneCommand()
		{} // for fromXML

		public SetTuneCommand(int track, int value)
		{
			this.track = track;
			this.value = (short) value;
		}

		@Override
		public void fromXML(XmlReader xmr, Element elem)
		{
			track = SoundXml.readInt(xmr, elem, ATTR_TRACK, 0, DRUM_TRACK - 1);
			value = SoundXml.readInt(xmr, elem, ATTR_TUNE, -32768, 32767);
		}

		@Override
		public void toXML(XmlWriter xmw)
		{
			XmlTag tag = xmw.createTag(TAG_SET_TUNE, true);
			xmw.addInt(tag, ATTR_TRACK, track);
			xmw.addInt(tag, ATTR_TUNE, value);
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
			track = SoundXml.readInt(xmr, elem, ATTR_TRACK, 0, NUM_TRACKS - 1);
			reverb = SoundXml.readInt(xmr, elem, ATTR_REVERB, 0, 255);
		}

		@Override
		public void toXML(XmlWriter xmw)
		{
			XmlTag tag = xmw.createTag(TAG_SET_REVERB, true);
			xmw.addInt(tag, ATTR_TRACK, track);
			xmw.addInt(tag, ATTR_REVERB, reverb);
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
			track = SoundXml.readInt(xmr, elem, ATTR_TRACK, 0, NUM_TRACKS - 1);
			resumable = xmr.readBoolean(elem, ATTR_RESUMABLE);
		}

		@Override
		public void toXML(XmlWriter xmw)
		{
			XmlTag tag = xmw.createTag(TAG_SET_RESUMABLE, true);
			xmw.addInt(tag, ATTR_TRACK, track);
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
			loopID = SoundXml.readInt(xmr, elem, ATTR_LOOP_ID, 0, 1);
		}

		@Override
		public void toXML(XmlWriter xmw)
		{
			XmlTag tag = xmw.createTag(TAG_START_LOOP, true);
			xmw.addInt(tag, ATTR_LOOP_ID, loopID);
			xmw.printTag(tag);
		}

		@Override
		public void build(DynamicByteBuffer dbb)
		{
			dbb.putByte(MSEQ_CMD_B0_MULTI << 4);
			dbb.putByte(MSEQ_CMD_SUB_66_START_LOOP);

			dbb.putByte(loopID);
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
			loopID = SoundXml.readInt(xmr, elem, ATTR_LOOP_ID, 0, 1);
			count = SoundXml.readInt(xmr, elem, ATTR_LOOP_COUNT, 0, 31);
		}

		@Override
		public void toXML(XmlWriter xmw)
		{
			XmlTag tag = xmw.createTag(TAG_END_LOOP, true);
			xmw.addInt(tag, ATTR_LOOP_ID, loopID);
			xmw.addInt(tag, ATTR_LOOP_COUNT, count);
			xmw.printTag(tag);
		}

		@Override
		public void build(DynamicByteBuffer dbb)
		{
			dbb.putByte(MSEQ_CMD_B0_MULTI << 4);
			dbb.putByte(MSEQ_CMD_SUB_67_END_LOOP);

			dbb.putByte((count << 2) | loopID);
		}
	}
}
