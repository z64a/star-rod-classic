package game.sound;

import static app.Directories.*;
import static game.sound.AudioModder.BankListKey.*;
import static game.sound.AudioModder.SongListKey.*;
import static game.sound.BankModder.BankKey.*;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.FilenameUtils;
import org.w3c.dom.Element;

import app.StarRodException;
import app.input.InputFileException;
import util.xml.XmlWrapper.XmlReader;

public final class SoundBankCatalog
{
	private static final int MAX_ENVELOPE = 3;

	private final Map<String, BankInfo> banks;
	private final Map<Integer, BankInfo> permanentBanks;
	private final Map<Integer, BankInfo> auxiliaryBanks;

	public SoundBankCatalog(File bankDirectory, File bankListFile)
	{
		banks = readBanks(bankDirectory);
		permanentBanks = readBankList(bankListFile);
		auxiliaryBanks = new HashMap<>();
	}

	private SoundBankCatalog(SoundBankCatalog other)
	{
		banks = other.banks;
		permanentBanks = other.permanentBanks;
		auxiliaryBanks = new HashMap<>(other.auxiliaryBanks);
	}

	public static SoundBankCatalog loadDump()
	{
		return new SoundBankCatalog(DUMP_AUDIO_BANK.toFile(), DUMP_AUDIO.getFile(FN_AUDIO_BANKS));
	}

	public static SoundBankCatalog loadMod()
	{
		return new SoundBankCatalog(MOD_AUDIO_BANK.toFile(), MOD_AUDIO.getFile(FN_AUDIO_BANKS));
	}

	public SoundBankCatalog withAuxiliaryBank(int index, String bankFilename)
	{
		if (index < 0 || index > 0xF)
			throw new IllegalArgumentException("Auxiliary bank index is out of range: " + index);

		SoundBankCatalog context = new SoundBankCatalog(this);
		BankInfo bank = getBank(bankFilename);
		context.auxiliaryBanks.put(index, bank);
		return context;
	}

	public SoundBankCatalog withSongBanks(File songListFile, String songFilename)
	{
		XmlReader xmr = new XmlReader(songListFile);
		Element root = xmr.getRootElement();
		String[] bankNames = new String[3];

		for (Element elem : xmr.getTags(root, TAG_SONG)) {
			String bgmName;
			if (xmr.hasAttribute(elem, ATTR_OLD_BGM))
				bgmName = xmr.getAttribute(elem, ATTR_OLD_BGM);
			else if (xmr.hasAttribute(elem, ATTR_BGM))
				bgmName = xmr.getAttribute(elem, ATTR_BGM);
			else
				continue;

			if (!bgmName.equals(songFilename))
				continue;

			String[] current = new String[3];
			if (xmr.hasAttribute(elem, ATTR_OLD_BGM)) {
				current[0] = readOptional(xmr, elem, ATTR_X);
				current[1] = readOptional(xmr, elem, ATTR_Y);
				current[2] = readOptional(xmr, elem, ATTR_Z);
			}
			else {
				current[0] = readOptional(xmr, elem, ATTR_BK1);
				current[1] = readOptional(xmr, elem, ATTR_BK2);
				current[2] = readOptional(xmr, elem, ATTR_BK3);
			}

			for (int i = 0; i < current.length; i++) {
				if (current[i] == null)
					continue;
				if (bankNames[i] != null && !bankNames[i].equals(current[i])) {
					throw new InputFileException(songListFile,
						"Song %s is used with different auxiliary banks in slot %X", songFilename, i);
				}
				bankNames[i] = current[i];
			}
		}

		SoundBankCatalog context = new SoundBankCatalog(this);
		for (int i = 0; i < bankNames.length; i++) {
			if (bankNames[i] != null)
				context.auxiliaryBanks.put(i, getBank(bankNames[i]));
		}
		return context;
	}

	public WavReference getWav(int bank, int patch)
	{
		int bankSet = (bank >> 4) & 0xF;
		int envelope = bank & MAX_ENVELOPE;
		int bankIndex = (patch >> 4) & 0xF;
		int instrumentIndex = patch & 0xF;

		if (bankSet == 7)
			bankSet = 0;

		BankInfo bankInfo;
		if (bankSet == 0)
			bankInfo = auxiliaryBanks.get(bankIndex);
		else if (bankSet == 2)
			throw new StarRodException("The default instrument at %02X-%02X has no WAV name", bank, patch);
		else
			bankInfo = permanentBanks.get(bankKey(bankSet, bankIndex));

		if (bankInfo == null) {
			throw new StarRodException("No sound bank is assigned to bank/patch %02X-%02X", bank, patch);
		}
		if (instrumentIndex >= bankInfo.wavs.size()) {
			throw new StarRodException("Sound bank %s has no instrument %X for bank/patch %02X-%02X",
				bankInfo.name, instrumentIndex, bank, patch);
		}

		return new WavReference(bankInfo.wavs.get(instrumentIndex), envelope);
	}

	public InstrumentAddress getAddress(String wav, int envelope)
	{
		if (wav == null || wav.isEmpty())
			throw new StarRodException("WAV name is empty");
		if (envelope < 0 || envelope > MAX_ENVELOPE)
			throw new StarRodException("Envelope index for %s is out of range: %X", wav, envelope);

		List<InstrumentAddress> matches = new ArrayList<>();
		for (Map.Entry<Integer, BankInfo> entry : permanentBanks.entrySet()) {
			int key = entry.getKey();
			findAddresses(matches, entry.getValue(), (key >> 4) & 0xF, key & 0xF, wav, envelope);
		}
		for (Map.Entry<Integer, BankInfo> entry : auxiliaryBanks.entrySet())
			findAddresses(matches, entry.getValue(), 0, entry.getKey(), wav, envelope);

		if (matches.isEmpty())
			throw new StarRodException("No bank instrument uses WAV name %s", wav);
		if (matches.size() > 1)
			throw new StarRodException("WAV name %s is used by multiple bank instruments", wav);
		return matches.get(0);
	}

	private void findAddresses(List<InstrumentAddress> matches, BankInfo bank, int bankSet,
		int bankIndex, String wav, int envelope)
	{
		for (int i = 0; i < bank.wavs.size(); i++) {
			if (!bank.wavs.get(i).equals(wav))
				continue;
			matches.add(new InstrumentAddress((bankSet << 4) | envelope, (bankIndex << 4) | i));
		}
	}

	private Map<String, BankInfo> readBanks(File bankDirectory)
	{
		if (!bankDirectory.isDirectory())
			throw new InputFileException(bankDirectory, "Sound bank directory does not exist");

		Map<String, BankInfo> bankMap = new HashMap<>();
		File[] directories = bankDirectory.listFiles(File::isDirectory);
		if (directories == null)
			throw new InputFileException(bankDirectory, "Could not enumerate sound banks");

		for (File directory : directories) {
			File xmlFile = new File(directory, FN_SOUND_BANK);
			if (!xmlFile.exists())
				continue;

			String name = FilenameUtils.getBaseName(directory.getName());
			XmlReader xmr = new XmlReader(xmlFile);
			Element root = xmr.getRootElement();
			Element instruments = xmr.getUniqueRequiredTag(root, TAG_INS_LIST);
			BankInfo bank = new BankInfo(name);

			for (Element elem : xmr.getTags(instruments, TAG_INSTRUMENT)) {
				xmr.requiresAttribute(elem, ATTR_WAV);
				bank.wavs.add(xmr.getAttribute(elem, ATTR_WAV));
			}

			if (bank.wavs.size() > 16)
				throw new InputFileException(xmlFile, "Sound bank has more than 16 instruments");
			if (bankMap.put(name, bank) != null)
				throw new InputFileException(xmlFile, "Duplicate sound bank name: " + name);
		}

		return bankMap;
	}

	private Map<Integer, BankInfo> readBankList(File bankListFile)
	{
		Map<Integer, BankInfo> bankMap = new HashMap<>();
		XmlReader xmr = new XmlReader(bankListFile);
		Element root = xmr.getRootElement();

		for (Element elem : xmr.getTags(root, TAG_BANK)) {
			xmr.requiresAttribute(elem, ATTR_BANK_NAME);
			xmr.requiresAttribute(elem, ATTR_BANK_GROUP);
			xmr.requiresAttribute(elem, ATTR_BANK_INDEX);

			String filename = xmr.getAttribute(elem, ATTR_BANK_NAME);
			int group = xmr.readHex(elem, ATTR_BANK_GROUP);
			int index = xmr.readHex(elem, ATTR_BANK_INDEX);
			int bankSet = group == 2 ? 1 : group;

			if (bankSet < 1 || bankSet > 6 || bankSet == 2)
				throw new InputFileException(bankListFile, "Unsupported sound bank group: " + group);
			if (index < 0 || index > 0xF)
				throw new InputFileException(bankListFile, "Sound bank index is out of range: " + index);

			BankInfo bank = getBank(filename);
			int key = bankKey(bankSet, index);
			if (bankMap.put(key, bank) != null) {
				throw new InputFileException(bankListFile,
					"Multiple sound banks use group %X index %X", group, index);
			}
		}

		return bankMap;
	}

	private BankInfo getBank(String filename)
	{
		String name = FilenameUtils.getBaseName(filename);
		BankInfo bank = banks.get(name);
		if (bank == null)
			throw new StarRodException("Could not find SoundBank.xml for bank %s", filename);
		return bank;
	}

	private static int bankKey(int bankSet, int bankIndex)
	{
		return (bankSet << 4) | bankIndex;
	}

	private static String readOptional(XmlReader xmr, Element elem, AudioModder.SongListKey key)
	{
		if (!xmr.hasAttribute(elem, key))
			return null;
		String value = xmr.getAttribute(elem, key);
		return value.isEmpty() ? null : value;
	}

	private static final class BankInfo
	{
		private final String name;
		private final List<String> wavs = new ArrayList<>();

		private BankInfo(String name)
		{
			this.name = name;
		}
	}

	public static final class WavReference
	{
		public final String wav;
		public final int envelope;

		private WavReference(String wav, int envelope)
		{
			this.wav = wav;
			this.envelope = envelope;
		}
	}

	public static final class InstrumentAddress
	{
		public final int bank;
		public final int patch;

		private InstrumentAddress(int bank, int patch)
		{
			this.bank = bank;
			this.patch = patch;
		}
	}
}
