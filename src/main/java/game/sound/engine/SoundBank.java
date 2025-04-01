package game.sound.engine;

import static app.Directories.FN_SOUND_BANK;
import static app.Directories.MOD_AUDIO_BANK;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;

import org.apache.commons.io.FilenameUtils;

import app.StarRodException;
import game.sound.AudioModder;
import game.sound.AudioModder.BankEntry;
import game.sound.BankModder.Bank;
import game.sound.engine.Envelope.EnvelopePair;
import util.Logger;

public class SoundBank
{
	private static final EnvelopePair DEFAULT_ENVELOPE = new EnvelopePair(
		new int[] { 61, 127, 0xFF, 0 },
		new int[] { 52, 0, 0xFF, 0 }
	);

	private HashMap<String, Bank> bankNameMap;
	private HashMap<Integer, Bank> bankRefMap;

	public SoundBank() throws IOException
	{
		bankNameMap = new HashMap<>();

		for (File dir : MOD_AUDIO_BANK.toFile().listFiles(File::isDirectory)) {
			String bankName = FilenameUtils.getBaseName(dir.getName());

			File xmlFile = new File(dir, FN_SOUND_BANK);
			if (!xmlFile.exists()) {
				Logger.logfError("Could not find %s for sound bank %s", xmlFile.getName(), bankName);
				continue;
			}

			Bank bank = new Bank(bankName, xmlFile);
			for (Instrument ins : bank.instruments) {
				ins.load(dir);
			}
			bankNameMap.put(bank.name, bank);
			Logger.log("Loaded bank " + bank.name);
		}

		List<BankEntry> bankList = AudioModder.getBankEntries();
		bankRefMap = new HashMap<>();

		for (BankEntry e : bankList) {
			String bankName = FilenameUtils.getBaseName(e.name);

			Bank bank = bankNameMap.get(bankName);
			if (bank == null)
				throw new StarRodException("Could not find bank %s", bankName);

			int key = (e.group & 0xF) << 4 | (e.index & 0xF);

			if (bankRefMap.containsKey(key))
				throw new StarRodException("Duplicate key for sound bank, group %X with index %X", e.group, e.index);

			bankRefMap.put(key, bank);

			int i = 0;
			for (Instrument ins : bank.instruments)
				System.out.printf("INS: %X %X --> %4s %X%n", e.group, e.index, bank.name, i++);
		}
	}

	public boolean installAuxBank(String bankName, int index)
	{
		Bank bank = bankNameMap.get(bankName);
		if (bank == null) {
			Logger.logfError("Could not find bank %s", bankName);
			return false;
		}

		int key = (index & 0xF);

		bankRefMap.put(key, bank);
		return true;
	}

	public record BankQueryResult(Instrument instrument, EnvelopePair envelope)
	{}

	public BankQueryResult getInstrument(int groupEnv, int index)
	{
		int groupIndex = groupEnv >> 4;
		int envIndex = groupEnv & 3;

		// see: au_get_instrument
		switch (groupIndex) {
			case 0:
			case 7:
				// aux
				groupIndex = 0;
				break;
			case 1:
				groupIndex = 2;
				break;
			case 2:
				// default instrument
				return null;
			case 3:
			case 4:
			case 5:
			case 6:
				// no remapping
				break;
			default:
				// invalid group
				return null;
		}

		// have to split bank index from instrument index
		int bankIndex = (index >> 4) & 0xF;
		int insIndex = index & 0xF;

		int key = (groupIndex & 0xF) << 4 | (bankIndex & 0xF);

		Bank bank = bankRefMap.get(key);
		if (bank == null) {
			Logger.logfError("Could not find a bank with group %X and index %X", groupEnv, bankIndex);
			return null;
		}

		if (bank.instruments.size() <= insIndex) {
			Logger.logfError("Bank %s has no instrument with index %X", bank.name, insIndex);
			return null;
		}

		Instrument ins = bank.instruments.get(insIndex);
		EnvelopePair env = DEFAULT_ENVELOPE;

		if (envIndex < ins.envelope.count())
			env = ins.envelope.get(envIndex);

		return new BankQueryResult(ins, env);
	}
}
