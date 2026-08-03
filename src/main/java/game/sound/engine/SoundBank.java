package game.sound.engine;

import static app.Directories.FN_AUDIO_DRUMS;
import static app.Directories.FN_AUDIO_PRESETS;
import static app.Directories.FN_SOUND_BANK;
import static app.Directories.MOD_AUDIO;
import static app.Directories.MOD_AUDIO_BANK;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.apache.commons.io.FilenameUtils;

import app.StarRodException;
import game.sound.AudioModder;
import game.sound.AudioModder.BankEntry;
import game.sound.BankModder.Bank;
import game.sound.DrumPreset;
import game.sound.DrumsModder;
import game.sound.InstrumentPreset;
import game.sound.InstrumentsModder;
import game.sound.SoundBankCatalog;
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
	private HashMap<String, Instrument> instrumentNameMap;

	private ArrayList<InstrumentPreset> instrumentList;
	private ArrayList<DrumPreset> drumList;

	public SoundBank() throws IOException
	{
		bankNameMap = new HashMap<>();
		instrumentNameMap = new HashMap<>();

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
				if (instrumentNameMap.put(ins.name, ins) != null)
					throw new StarRodException("Duplicate sound bank WAV name %s", ins.name);
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

			int key = (e.bankSet & 0xF) << 4 | (e.bankIndex & 0xF);

			if (bankRefMap.containsKey(key))
				throw new StarRodException("Duplicate sound bank assignment for bank set %X index %X", e.bankSet, e.bankIndex);

			bankRefMap.put(key, bank);
		}

		SoundBankCatalog catalog = SoundBankCatalog.loadMod();
		instrumentList = InstrumentsModder.load(MOD_AUDIO.getFile(FN_AUDIO_PRESETS), catalog);
		drumList = DrumsModder.load(MOD_AUDIO.getFile(FN_AUDIO_DRUMS), catalog);
	}

	public boolean installAuxBank(String bankName, int index)
	{
		Bank bank = bankNameMap.get(bankName);
		if (bank == null) {
			Logger.logfError("Could not find bank %s", bankName);
			return false;
		}

		int key = 0x10 | (index & 0xF);

		bankRefMap.put(key, bank);
		return true;
	}

	public List<Bank> getBanks()
	{
		return List.copyOf(bankNameMap.values());
	}

	public record InstrumentQueryResult(Instrument instrument, EnvelopePair envelope)
	{}

	public record PresetQueryResult(InstrumentPreset preset, Instrument instrument, EnvelopePair envelope)
	{}

	public InstrumentQueryResult getInstrument(String wav, int envelope)
	{
		String name = FilenameUtils.getBaseName(wav);
		Instrument ins = instrumentNameMap.get(name);
		if (ins == null) {
			Logger.logfError("Could not find sound bank WAV %s", wav);
			return null;
		}

		return getInstrument(ins, envelope);
	}

	public InstrumentQueryResult getInstrument(int bank, int patch)
	{
		int bankSetIndex = (bank >> 4) & 0xF;
		int envelopeIndex = bank & 3;
		int bankSet;

		// see: au_get_instrument
		switch (bankSetIndex) {
			case 0:
			case 7:
				bankSet = 1;
				break;
			case 1:
				bankSet = 2;
				break;
			case 2:
				// default instrument
				return null;
			case 3:
			case 4:
			case 5:
			case 6:
				bankSet = bankSetIndex;
				break;
			default:
				// invalid bank set index
				return null;
		}

		// have to split bank index from instrument index
		int bankIndex = (patch >> 4) & 0xF;
		int instrumentIndex = patch & 0xF;

		int key = (bankSet & 0xF) << 4 | (bankIndex & 0xF);

		Bank soundBank = bankRefMap.get(key);
		if (soundBank == null) {
			Logger.logfError("Could not find a bank in bank set %X at index %X", bankSet, bankIndex);
			return null;
		}

		if (soundBank.instruments.size() <= instrumentIndex) {
			Logger.logfError("Bank %s has no instrument with index %X", soundBank.name, instrumentIndex);
			return null;
		}

		Instrument ins = soundBank.instruments.get(instrumentIndex);
		return getInstrument(ins, envelopeIndex);
	}

	private InstrumentQueryResult getInstrument(Instrument ins, int envIndex)
	{
		EnvelopePair env = DEFAULT_ENVELOPE;

		if (envIndex < ins.envelope.count())
			env = ins.envelope.get(envIndex);

		return new InstrumentQueryResult(ins, env);
	}

	public PresetQueryResult getPreset(int index)
	{
		if (index < 0 || index >= instrumentList.size()) {
			Logger.logfError("Instrument preset ID is out of range: %X", index);
			return null;
		}

		InstrumentPreset preset = instrumentList.get(index);
		InstrumentQueryResult result = getInstrument(preset.wav, preset.envelope);
		if (result == null)
			return null;
		return new PresetQueryResult(preset, result.instrument, result.envelope);
	}

	public record DrumQueryResult(DrumPreset drum, Instrument instrument, EnvelopePair envelope)
	{}

	public DrumQueryResult getDrum(int drumID)
	{
		if (drumID < 0 || drumID >= drumList.size()) {
			Logger.logfError("Drum ID is out of range: %X", drumID);
			return null;
		}

		DrumPreset drum = drumList.get(drumID);

		InstrumentQueryResult ins = getInstrument(drum.wav, drum.envelope);
		if (ins == null) {
			Logger.logfError("Failed to find instrument for drum %X", drumID);
			return null;
		}

		return new DrumQueryResult(drum, ins.instrument, ins.envelope);
	}
}
