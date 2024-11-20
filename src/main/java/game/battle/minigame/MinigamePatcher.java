package game.battle.minigame;

import static app.Directories.*;
import static game.battle.BattleConstants.ACTION_COMMANDS_BASE;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;

import app.StarRodException;
import app.input.IOUtils;
import asm.AsmUtils;
import game.shared.ProjectDatabase;
import game.shared.ProjectDatabase.ConstEnum;
import patcher.Patcher;
import patcher.RomPatcher;
import util.Logger;

public class MinigamePatcher
{
	private static final class MinigameConfig
	{
		public File source;
		public int entryOffset;
	}

	private final Patcher patcher;
	private final RomPatcher rp;
	private List<MinigameConfig> configs;

	private boolean foundPatches = false;

	public MinigamePatcher(Patcher patcher)
	{
		this.patcher = patcher;
		rp = patcher.getRomPatcher();
	}

	public void patchData() throws IOException
	{
		FileUtils.forceMkdir(MOD_MINIGAME_TEMP.toFile());
		FileUtils.cleanDirectory(MOD_MINIGAME_TEMP.toFile());

		Collection<File> patchFiles = IOUtils.getFilesWithExtension(MOD_MINIGAME_PATCH, "bpat", true);

		for (File f : patchFiles) {
			Logger.log("Executing patch: " + f.getName());
			String name = FilenameUtils.removeExtension(f.getName());
			MinigameEncoder encoder = new MinigameEncoder(patcher);
			encoder.encode(name);
			foundPatches = true;
		}
	}

	public void generateConfigs() throws IOException
	{
		if (!foundPatches)
			return;

		configs = new LinkedList<>();
		ConstEnum commandsEnum = ProjectDatabase.getFromNamespace("ActionCommand");

		for (int i = 1; i <= commandsEnum.getMaxDefinedValue(); i++) {
			MinigameConfig config = new MinigameConfig();
			configs.add(config);

			String cmdName = commandsEnum.getName(i);
			cmdName = (cmdName == null) ? "" : " " + cmdName;
			String scriptName = String.format("%02X%s", i, cmdName);

			File patchFile = new File(MOD_MINIGAME_PATCH + scriptName + ".bpat");
			File indexFile;

			if (patchFile.exists()) {
				indexFile = new File(MOD_MINIGAME_TEMP + scriptName + ".bidx");
				config.source = new File(MOD_MINIGAME_TEMP + scriptName + ".bin");
			}
			else {
				indexFile = new File(DUMP_MINIGAME_SRC + scriptName + ".bidx");
				config.source = new File(DUMP_MINIGAME_RAW + scriptName + ".bin");
			}

			if (!indexFile.exists()) {
				throw new StarRodException("Could not find sources for action command %X%n Has it been defined?", i);
			}
		}
	}

	public void writeTable() throws IOException
	{
		if (!foundPatches)
			return;

		int tableOffset = 0x1C2DA0;
		if (configs.size() <= 23) {
			rp.seek("Action Command Table", tableOffset);
		}
		else {
			tableOffset = rp.nextAlignedOffset();
			rp.seek("LoadActionCommand", 0x196AB4); // battle::802681D4
			AsmUtils.assembleAndWrite(true, "LoadActionCommand", rp,
				String.format("LA	A2, %08X", rp.toAddress(tableOffset)));
			rp.seek("Action Command Table", tableOffset);
		}

		rp.skip(0xC); // skip command 0

		for (MinigameConfig cfg : configs) {
			cfg.entryOffset = rp.getCurrentOffset();
			rp.skip(8); // ROM offsets
			rp.writeInt(ACTION_COMMANDS_BASE);
		}

		Logger.logf("Wrote action commands table to %6X", tableOffset);
	}

	public void writeData() throws IOException
	{
		if (!foundPatches)
			return;

		for (MinigameConfig cfg : configs) {
			byte[] data = FileUtils.readFileToByteArray(cfg.source);
			int start = patcher.getBattleDataPos(data.length);

			rp.seek(FilenameUtils.getBaseName(cfg.source.getName()) + " Data", start);
			rp.write(data);
			Logger.log(String.format("Wrote %s to %X", cfg.source.getName(), start));

			// update table
			rp.seek("Action Command Table", cfg.entryOffset);
			rp.writeInt(start);
			rp.writeInt(start + data.length);
		}
	}
}
