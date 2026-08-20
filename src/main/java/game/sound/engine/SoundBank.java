package game.sound.engine;

import static app.Directories.*;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
	private static final Instrument DEFAULT_INSTRUMENT = Instrument.createFallbackInstrument();

	private final boolean tolerateLoadFailures;
	private final HashMap<String, Bank> bankNameMap;
	private final HashMap<Integer, Bank> bankRefMap;
	private final HashMap<String, Instrument> instrumentNameMap;
	private final Set<String> fallbackWarnings;

	private ArrayList<InstrumentPreset> instrumentList;
	private ArrayList<DrumPreset> drumList;
	private int loadFailureCount;

	public SoundBank() throws IOException
	{
		this(false);
	}

	public SoundBank(boolean tolerateLoadFailures) throws IOException
	{
		this.tolerateLoadFailures = tolerateLoadFailures;
		bankNameMap = new HashMap<>();
		bankRefMap = new HashMap<>();
		instrumentNameMap = new HashMap<>();
		fallbackWarnings = new HashSet<>();
		instrumentList = new ArrayList<>();
		drumList = new ArrayList<>();

		File bankDirectory = MOD_AUDIO_BANK.toFile();
		File[] bankDirectories = bankDirectory.listFiles(File::isDirectory);
		if (bankDirectories == null) {
			if (!tolerateLoadFailures)
				throw new StarRodException("Could not enumerate sound banks in %s", bankDirectory);
			recordLoadFailure("Could not enumerate sound banks in " + bankDirectory, null);
			bankDirectories = new File[0];
		}

		for (File dir : bankDirectories) {
			String bankName = FilenameUtils.getBaseName(dir.getName());

			File xmlFile = new File(dir, FN_SOUND_BANK);
			if (!xmlFile.exists()) {
				if (tolerateLoadFailures)
					recordLoadFailure(String.format("Could not find %s for sound bank %s", xmlFile.getName(), bankName), null);
				else
					Logger.logfError("Could not find %s for sound bank %s", xmlFile.getName(), bankName);
				continue;
			}

			Bank bank;
			try {
				bank = new Bank(bankName, xmlFile);
			}
			catch (Exception e) {
				if (!tolerateLoadFailures)
					throw e;
				recordLoadFailure("Could not load sound bank " + bankName, e);
				continue;
			}

			for (Instrument ins : bank.instruments) {
				try {
					ins.load(dir);
				}
				catch (Exception e) {
					if (!tolerateLoadFailures)
						throw e;
					ins.useFallbackSample(e);
					recordLoadFailure(String.format("Could not load sample %s from bank %s; using the silent fallback instrument",
						ins.mainFilename, bankName), e);
				}

				if (instrumentNameMap.putIfAbsent(ins.name, ins) != null) {
					if (!tolerateLoadFailures)
						throw new StarRodException("Duplicate sound bank WAV name %s", ins.name);
					recordLoadFailure("Duplicate sound bank WAV name " + ins.name + "; using the first loaded instrument", null);
				}
			}
			bankNameMap.put(bank.name, bank);
			Logger.log("Loaded bank " + bank.name);
		}

		List<BankEntry> bankList;
		try {
			bankList = AudioModder.getBankEntries();
		}
		catch (Exception e) {
			if (!tolerateLoadFailures) {
				if (e instanceof IOException)
					throw (IOException) e;
				throw e;
			}
			recordLoadFailure("Could not load sound bank assignments", e);
			bankList = List.of();
		}

		for (BankEntry e : bankList) {
			String bankName = FilenameUtils.getBaseName(e.name);

			Bank bank = bankNameMap.get(bankName);
			if (bank == null) {
				if (!tolerateLoadFailures)
					throw new StarRodException("Could not find bank %s", bankName);
				recordLoadFailure("Sound bank assignment refers to unavailable bank " + bankName, null);
				continue;
			}

			int key = (e.bankGroup & 0xF) << 4 | (e.bankIndex & 0xF);

			if (bankRefMap.putIfAbsent(key, bank) != null) {
				if (!tolerateLoadFailures)
					throw new StarRodException("Duplicate sound bank assignment for bank group %X index %X", e.bankGroup, e.bankIndex);
				recordLoadFailure(
					String.format("Duplicate sound bank assignment for bank group %X index %X; using the first assignment", e.bankGroup, e.bankIndex), null);
			}
		}

		if (tolerateLoadFailures) {
			try {
				instrumentList = InstrumentsModder.load(MOD_AUDIO.getFile(FN_AUDIO_PRESETS));
			}
			catch (Exception e) {
				recordLoadFailure("Could not load global BGM instrument presets", e);
			}

			try {
				drumList = DrumsModder.load(MOD_AUDIO.getFile(FN_AUDIO_DRUMS));
			}
			catch (Exception e) {
				recordLoadFailure("Could not load global BGM drum presets", e);
			}
		}
		else {
			SoundBankCatalog catalog = SoundBankCatalog.loadMod();
			instrumentList = InstrumentsModder.load(MOD_AUDIO.getFile(FN_AUDIO_PRESETS), catalog);
			drumList = DrumsModder.load(MOD_AUDIO.getFile(FN_AUDIO_DRUMS), catalog);
		}
	}

	public int getLoadFailureCount()
	{
		return loadFailureCount;
	}

	private void recordLoadFailure(String message, Exception e)
	{
		loadFailureCount++;
		if (e == null || e.getMessage() == null || e.getMessage().isEmpty())
			Logger.logWarning(message);
		else
			Logger.logfWarning("%s: %s", message, e.getMessage());
	}

	public boolean installAuxBank(String bankName, int index)
	{
		Bank bank = bankNameMap.get(bankName);
		if (bank == null) {
			if (tolerateLoadFailures) {
				recordLoadFailure("Could not find auxiliary bank " + bankName, null);
			}
			else {
				Logger.logfError("Could not find bank %s", bankName);
			}
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
			return missingInstrument(String.format("Could not find sound bank WAV %s", wav));
		}

		return getInstrument(ins, envelope);
	}

	public InstrumentQueryResult getInstrument(int bank, int patch)
	{
		int bankSetIndex = (bank >> 4) & 0xF;
		int envelopeIndex = bank & 3;
		int bankGroup;

		// see: au_get_instrument
		switch (bankSetIndex) {
			case 0:
			case 7:
				bankGroup = 1;
				break;
			case 1:
				bankGroup = 2;
				break;
			case 2:
				// default instrument
				return getInstrument(DEFAULT_INSTRUMENT, 0);
			case 3:
			case 4:
			case 5:
			case 6:
				bankGroup = bankSetIndex;
				break;
			default:
				// invalid bank set index
				return null;
		}

		// have to split bank index from instrument index
		int bankIndex = (patch >> 4) & 0xF;
		int instrumentIndex = patch & 0xF;

		int key = (bankGroup & 0xF) << 4 | (bankIndex & 0xF);

		Bank soundBank = bankRefMap.get(key);
		if (soundBank == null) {
			return missingInstrument(String.format("Could not find a bank in bank group %X at index %X", bankGroup, bankIndex));
		}

		if (soundBank.instruments.size() <= instrumentIndex) {
			return missingInstrument(String.format("Bank %s has no instrument with index %X", soundBank.name, instrumentIndex));
		}

		Instrument ins = soundBank.instruments.get(instrumentIndex);
		return getInstrument(ins, envelopeIndex);
	}

	private InstrumentQueryResult getInstrument(Instrument ins, int envIndex)
	{
		EnvelopePair env = DEFAULT_ENVELOPE;

		if (!ins.usingFallbackSample && ins.envelope != null && envIndex >= 0 && envIndex < ins.envelope.count())
			env = ins.envelope.get(envIndex);

		return new InstrumentQueryResult(ins, env);
	}

	private InstrumentQueryResult missingInstrument(String warning)
	{
		if (!tolerateLoadFailures) {
			Logger.logError(warning);
			return null;
		}

		if (fallbackWarnings.add(warning))
			Logger.logWarning(warning + "; using the silent fallback instrument");
		return getInstrument(DEFAULT_INSTRUMENT, 0);
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
