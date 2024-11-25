package game.battle.starpowers;

import static app.Directories.*;
import static game.battle.BattleConstants.*;
import static game.shared.StructTypes.UseScriptT;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;

import app.input.IOUtils;
import app.input.InputFileException;
import game.battle.formations.BattleSectionEncoder;
import game.shared.struct.Struct;
import patcher.Patcher;
import patcher.RomPatcher;
import util.Logger;

public class StarPowerPatcher
{
	private static final class StarPowerConfig
	{
		public File source;

		public int mainAddress;
		public int entryOffset;
	}

	private final Patcher patcher;
	private final RomPatcher rp;
	private List<StarPowerConfig> configs;

	public StarPowerPatcher(Patcher patcher)
	{
		this.patcher = patcher;
		rp = patcher.getRomPatcher();
	}

	public void patchStarPowerData() throws IOException
	{
		FileUtils.forceMkdir(MOD_STARS_TEMP.toFile());
		FileUtils.cleanDirectory(MOD_STARS_TEMP.toFile());

		Collection<File> patchFiles = IOUtils.getFilesWithExtension(MOD_STARS_PATCH, "bpat", true);

		for (File f : patchFiles) {
			Logger.log("Executing patch: " + f.getName());
			String name = FilenameUtils.removeExtension(f.getName());
			StarPowerEncoder encoder = new StarPowerEncoder(patcher);
			encoder.encode(name);
		}
	}

	public void generateConfigs() throws IOException
	{
		configs = new LinkedList<>();

		for (int i = 0; i < NUM_STAR_POWERS; i++) {
			StarPowerConfig config = new StarPowerConfig();
			configs.add(config);

			String scriptName = String.format("%02X %s", i, STAR_POWER_NAME[i]);

			File patchFile = new File(MOD_STARS_PATCH + scriptName + ".bpat");
			File indexFile;

			if (patchFile.exists()) {
				indexFile = new File(MOD_STARS_TEMP + scriptName + ".bidx");
				config.source = new File(MOD_STARS_TEMP + scriptName + ".bin");
			}
			else {
				indexFile = new File(DUMP_STARS_SRC + scriptName + ".bidx");
				config.source = new File(DUMP_STARS_RAW + scriptName + ".bin");
			}

			HashMap<String, Struct> structMap = new HashMap<>();
			BattleSectionEncoder tempEncoder = new BattleSectionEncoder(patcher);
			tempEncoder.loadIndexFile(structMap, indexFile);

			boolean foundMain = false;
			for (Struct str : structMap.values()) {
				if (str.isTypeOf(UseScriptT) && str.name.equals("$Script_UsePower")) {
					if (foundMain)
						throw new InputFileException(indexFile, "Found duplicate UsePower script for " + scriptName);

					config.mainAddress = str.originalAddress;
					foundMain = true;
				}
			}

			if (!foundMain)
				throw new InputFileException(indexFile, "Could not find UsePower script for " + scriptName);
		}
	}

	public void writeStarPowerTable() throws IOException
	{
		rp.seek("Star Power Table", 0x1CB0B0);
		for (StarPowerConfig cfg : configs) {
			cfg.entryOffset = rp.getCurrentOffset();
			rp.skip(8); // ROM offsets
			rp.writeInt(STARS_BASE);
			rp.writeInt(cfg.mainAddress);
		}

		Logger.log("Wrote star powers script table.");
	}

	public void writeStarPowerData() throws IOException
	{
		for (StarPowerConfig cfg : configs) {
			byte[] data = FileUtils.readFileToByteArray(cfg.source);
			int start = patcher.getBattleDataPos(data.length);
			int end = start + data.length;

			rp.seek(FilenameUtils.getBaseName(cfg.source.getName()) + " Data", start);
			rp.write(data);
			Logger.log(String.format("Wrote %s to %X", cfg.source.getName(), start));

			// update table
			rp.seek("Star Power Table", cfg.entryOffset);
			rp.writeInt(start);
			rp.writeInt(end);
		}
	}
}
