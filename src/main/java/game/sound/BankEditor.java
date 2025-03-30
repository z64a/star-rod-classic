package game.sound;

import static app.Directories.*;
import static game.sound.BankEditor.BankKey.*;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.TreeMap;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.w3c.dom.Element;

import app.Environment;
import app.StarRodException;
import app.input.IOUtils;
import game.sound.AudioModder.BankEntry;
import game.sound.engine.Envelope;
import game.sound.engine.Envelope.EnvelopePair;
import game.sound.engine.Instrument;
import util.DynamicByteBuffer;
import util.Logger;
import util.xml.XmlKey;
import util.xml.XmlWrapper.XmlReader;
import util.xml.XmlWrapper.XmlTag;
import util.xml.XmlWrapper.XmlWriter;

public class BankEditor
{
	//TODO crash at 800531D8 -- call to snd_load_BK_headers / au_load_BK_headers
	// 80055008 for B6

	public static final String EXT_BANK = ".bk";

	public enum BankKey implements XmlKey
	{
		// @formatter:off
		TAG_SOUND_BANK	("SoundBank"),
		TAG_INS_LIST	("Instruments"),
		TAG_INSTRUMENT	("Instrument"),
		TAG_ENV_LIST	("Envelopes"),
		TAG_ENVELOPE	("Envelope"),
		TAG_ENV_CMDS	("Commands"),
		ATTR_SRC		("src"),
		ATTR_LOOP		("loop"),
		ATTR_LOOP_COUNT	("loopCount"),
		ATTR_ENV_NAME	("envName"),
		ATTR_KEY_BASE	("keyBase"),
		ATTR_NUM_PRED	("numPred"),
		ATTR_PRESS		("press"),
		ATTR_RELEASE	("release");
		// @formatter:on

		private final String key;

		private BankKey(String key)
		{
			this.key = key;
		}

		@Override
		public String toString()
		{
			return key;
		}
	}

	public static void main(String[] args) throws IOException
	{
		Environment.initialize();
		buildAll();
		Environment.exit();
	}

	public static void dumpAll() throws IOException
	{
		for (File binFile : IOUtils.getFilesWithExtension(DUMP_AUDIO_RAW, new String[] { "bk" }, true))
			dumpBank(binFile);
	}

	public static void buildAll() throws IOException
	{
		for (File dir : MOD_AUDIO_BANK.toFile().listFiles(File::isDirectory)) {
			buildBank(dir);
		}
	}

	private static final EnvelopePair DEFAULT_ENVELOPE = new EnvelopePair(
		new int[] { 61, 127, 0xFF, 0 },
		new int[] { 52, 0, 0xFF, 0 }
	);

	public static class SoundBank
	{
		private HashMap<Integer, Bank> bankMap;

		public SoundBank() throws IOException
		{
			List<BankEntry> bankList = AudioModder.getBankEntries();
			bankMap = new HashMap<>();

			for (BankEntry e : bankList) {
				String bankName = FilenameUtils.getBaseName(e.name);

				File bankDir = MOD_AUDIO_BANK.getFile(bankName);
				if (!bankDir.exists() || !bankDir.isDirectory())
					throw new StarRodException("Could not find directory for bank " + bankName);

				File xmlFile = new File(bankDir, FN_SOUND_BANK);
				if (!xmlFile.exists())
					throw new StarRodException("Could not find %s for sound bank %s", xmlFile.getName(), bankName);

				Bank bank = new Bank(bankName, xmlFile);

				int key = (e.group & 0xF) << 4 | (e.index & 0xF);

				if (bankMap.containsKey(key))
					throw new StarRodException("Duplicate key for sound bank, group %X with index %X", e.group, e.index);

				bankMap.put(key, bank);

				int i = 0;
				for (Instrument ins : bank.instruments) {
					ins.load(bankDir);
					System.out.printf("INS: %X %X --> %4s %X%n", e.group, e.index, bank.name, i++);
				}
			}
		}

		public record BankQueryResult(Instrument instrument, EnvelopePair envelope)
		{}

		public BankQueryResult getInstrument(int group, int index)
		{
			// see: au_get_instrument
			switch (group >> 4) {
				case 0:
				case 7:
					// aux
					return null;
				case 1:
					group = 2;
					break;
				case 2:
					// default instrument
					return null;
				case 3:
				case 4:
				case 5:
				case 6:
					group = group >> 4;
					// no remapping
					break;
				default:
					// invalid group
					return null;
			}

			// have to split bank index from instrument index
			int bankIndex = (index >> 4) & 0xF;
			int insIndex = index & 0xF;

			int key = (group & 0xF) << 4 | (bankIndex & 0xF);

			Bank bank = bankMap.get(key);
			if (bank == null) {
				Logger.logfError("Could not find a bank with group %X and index %X", group, bankIndex);
				return null;
			}

			if (bank.instruments.size() <= insIndex) {
				Logger.logfError("Bank %s has no instrument with index %X", bank.name, insIndex);
				return null;
			}

			Instrument ins = bank.instruments.get(insIndex);
			EnvelopePair env = DEFAULT_ENVELOPE;

			int envIndex = group & 3;
			if (envIndex < ins.envelope.count()) {
				env = ins.envelope.get(envIndex);
			}

			return new BankQueryResult(ins, env);
		}
	}

	public static void dumpBank(File binFile) throws IOException
	{
		Bank bank = new Bank(binFile);
		bank.dump();
	}

	public static void buildBank(File bankDir) throws IOException
	{
		String bankName = FilenameUtils.getBaseName(bankDir.getName());

		if (!bankDir.isDirectory())
			throw new StarRodException(bankDir.getName() + " is not a directory!");

		File xmlFile = new File(bankDir, FN_SOUND_BANK);
		if (!xmlFile.exists())
			throw new StarRodException("Could not find %s for sound bank %s", xmlFile.getName(), bankName);

		File outFile = MOD_AUDIO_BUILD.getFile(bankName + EXT_BANK);

		Bank bank = new Bank(bankName, xmlFile);
		bank.build(bankDir, outFile);
	}

	private static class Bank
	{
		final String name;

		final ArrayList<Instrument> instruments = new ArrayList<>();
		final ArrayList<Envelope> envelopes = new ArrayList<>();

		public Bank(File binFile) throws IOException
		{
			this.name = FilenameUtils.getBaseName(binFile.getName());
			ByteBuffer bb = IOUtils.getDirectBuffer(binFile);

			// read header
			bb.position(0x12);
			int[] instrumentOffsets = new int[16];
			int instrumentCount = 0;
			for (int i = 0; i < 16; i++) {
				instrumentOffsets[i] = bb.getShort();
				if (instrumentOffsets[i] != 0)
					instrumentCount++;
			}

			TreeMap<Integer, Envelope> envMap = new TreeMap<>();

			// read instruments
			for (int i = 0; i < instrumentCount; i++) {
				String insName = String.format("%s_%02X", name, i);
				Instrument ins = new Instrument(bb, instrumentOffsets[i], insName);
				instruments.add(ins);

				if (!envMap.containsKey(ins.envelopeOffset))
					envMap.put(ins.envelopeOffset, new Envelope(bb, ins.envelopeOffset));
			}

			// assign envelope references
			for (Instrument ins : instruments) {
				ins.envelope = envMap.get(ins.envelopeOffset);
			}

			envelopes.addAll(envMap.values());

			// assign basic envelope names
			for (int i = 0; i < envelopes.size(); i++) {
				envelopes.get(i).name = "env" + (i + 1);
			}
		}

		public Bank(String name, File xmlFile)
		{
			this.name = name;

			if (name.length() > 4)
				throw new StarRodException("Bank names must be 4 characters or less: ", name);

			XmlReader xmr = new XmlReader(xmlFile);

			Element rootElem = xmr.getRootElement();

			Element envelopesElem = xmr.getUniqueRequiredTag(rootElem, TAG_ENV_LIST);
			HashMap<String, Envelope> envMap = new HashMap<>();

			for (Element elem : xmr.getTags(envelopesElem, TAG_ENVELOPE)) {
				Envelope env = new Envelope(xmr, elem);
				envMap.put(env.name, env);
				envelopes.add(env);
			}

			Element instrumentsElem = xmr.getUniqueRequiredTag(rootElem, TAG_INS_LIST);

			for (Element elem : xmr.getTags(instrumentsElem, TAG_INSTRUMENT)) {
				Instrument ins = new Instrument(xmr, elem);

				if (!envMap.containsKey(ins.envelopeName))
					throw new StarRodException("Instrument %s of bank %s uses unknown envelope %s", ins.name, name, ins.envelopeName);

				ins.envelope = envMap.get(ins.envelopeName);
				instruments.add(ins);
			}
		}

		public void dump() throws IOException
		{
			File dir = DUMP_AUDIO_BANK.getFile(name);
			File xmlFile = new File(dir, FN_SOUND_BANK);
			FileUtils.forceMkdir(dir);

			for (Instrument ins : instruments) {
				ins.dump(dir);
			}

			try (XmlWriter xmw = new XmlWriter(xmlFile)) {
				XmlTag rootTag = xmw.createTag(TAG_SOUND_BANK, false);
				xmw.openTag(rootTag);

				XmlTag instrumentsTag = xmw.createTag(TAG_INS_LIST, false);
				xmw.openTag(instrumentsTag);
				for (Instrument ins : instruments) {
					ins.toXML(xmw);
				}
				xmw.closeTag(instrumentsTag);

				XmlTag envelopesTag = xmw.createTag(TAG_ENV_LIST, false);
				xmw.openTag(envelopesTag);
				for (Envelope env : envelopes) {
					env.toXML(xmw);
				}
				xmw.closeTag(envelopesTag);

				xmw.closeTag(rootTag);
				xmw.save();
			}
		}

		public void build(File bankDir, File outFile) throws IOException
		{
			Logger.log("Building bank: " + bankDir.getName());

			for (Instrument ins : instruments) {
				ins.load(bankDir);
				ins.build();
			}

			DynamicByteBuffer dbb = new DynamicByteBuffer(4096);

			// reserve space for header and instruments
			dbb.position(0x40 + 0x30 * instruments.size());

			int loopStatesOffset = dbb.position();
			for (Instrument ins : instruments)
				ins.buildLoop(dbb);

			dbb.align(16);

			int predictorsOffset = dbb.position();
			for (Instrument ins : instruments)
				ins.buildBook(dbb);

			dbb.align(16);

			int envelopesOffset = dbb.position();
			for (Envelope env : envelopes)
				env.build(dbb);

			dbb.align(16);

			int wavDataOffset = dbb.position();
			for (Instrument ins : instruments)
				ins.buildWav(dbb);

			dbb.align(16);
			int endOffset = dbb.position();

			// write header
			dbb.position(0);

			dbb.putUTF8("BK  ", false);
			dbb.putInt(dbb.size());
			dbb.putUTF8(String.format("%-4s", name), false);
			dbb.putUTF8("CR", false);
			dbb.skip(4);

			// write instrument offsets
			for (int i = 0; i < instruments.size(); i++) {
				dbb.putShort(0x40 + 0x30 * i);
			}

			// continue header
			dbb.position(0x32);
			dbb.putShort(instruments.size() * 0x30);

			dbb.putShort(loopStatesOffset);
			dbb.putShort(predictorsOffset - loopStatesOffset);

			dbb.putShort(predictorsOffset);
			dbb.putShort(envelopesOffset - predictorsOffset);

			dbb.putShort(envelopesOffset);
			dbb.putShort(wavDataOffset - envelopesOffset);

			// write instruments
			for (int i = 0; i < instruments.size(); i++) {
				Instrument ins = instruments.get(i);
				dbb.position(0x40 + 0x30 * i);

				/* 0x00 */ dbb.putInt(ins.wavOffset);
				/* 0x04 */ dbb.putInt(ins.wavLength);

				if (ins.hasLoop) {
					/* 0x08 */ dbb.putInt(ins.loopStateOffset);
					/* 0x0C */ dbb.putInt(ins.loopStart);
					/* 0x10 */ dbb.putInt(ins.loopEnd);
					/* 0x14 */ dbb.putInt(ins.loopCount);
				}
				else {
					/* 0x08 */ dbb.skip(0x10);
				}

				/* 0x18 */ dbb.putInt(ins.predictorOffset);
				/* 0x1C */ dbb.putShort(ins.numPredictors * 0x20);

				/* 0x1E */ dbb.putShort(ins.keyBase);
				/* 0x20 */ dbb.putInt(ins.sampleRate);
				/* 0x24 */ dbb.skip(8);

				/* 0x2C */ dbb.putInt(ins.envelope.buildOffset);
			}

			IOUtils.writeBufferToFile(dbb.getFixedBuffer(16), outFile);
		}
	}
}
