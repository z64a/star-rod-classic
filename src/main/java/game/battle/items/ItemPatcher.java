package game.battle.items;

import static app.Directories.*;
import static game.battle.BattleConstants.ITEM_BASE;
import static game.shared.StructTypes.UseScriptT;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;

import app.input.IOUtils;
import app.input.InputFileException;
import asm.AsmUtils;
import game.ROM.EOffset;
import game.battle.formations.BattleSectionEncoder;
import game.shared.ProjectDatabase;
import game.shared.struct.Struct;
import patcher.Patcher;
import patcher.Region;
import patcher.RomPatcher;
import util.Logger;

public class ItemPatcher
{
	private static final class ItemConfig
	{
		public File source;
		public int itemID;

		public int mainAddress;
		public int entryOffset;
	}

	private final Patcher patcher;
	private final RomPatcher rp;
	private List<ItemConfig> configs;

	public ItemPatcher(Patcher patcher)
	{
		this.patcher = patcher;
		rp = patcher.getRomPatcher();
	}

	public void patchItemData() throws IOException
	{
		FileUtils.forceMkdir(MOD_ITEM_TEMP.toFile());
		FileUtils.cleanDirectory(MOD_ITEM_TEMP.toFile());

		Collection<File> patchFiles = IOUtils.getFilesWithExtension(MOD_ITEM_PATCH, "bpat", true);

		for (File f : patchFiles) {
			Logger.log("Executing patch: " + f.getName());
			String name = FilenameUtils.removeExtension(f.getName());
			ItemEncoder encoder = new ItemEncoder(patcher);
			encoder.encode(name);
		}
	}

	public void generateConfigs() throws IOException
	{
		configs = new LinkedList<>();
		File table = new File(MOD_ITEM + FN_BATTLE_ITEMS);
		List<String> lines = IOUtils.readFormattedTextFile(table, false);

		for (int i = 0; i < lines.size(); i++) {
			ItemConfig config = new ItemConfig();
			configs.add(config);

			String[] tokens = lines.get(i).split("\\s+");

			Integer id;
			if (tokens[0].equals("*"))
				id = -1;
			else
				id = ProjectDatabase.getItemID(tokens[0]);

			if (id == null && tokens[0].matches("[0-9A-Fa-f]+"))
				id = (int) Long.parseLong(tokens[0], 16);

			if (id == null)
				throw new InputFileException(table, "Can't find item for " + tokens[0]);

			config.itemID = id;
			String scriptName = tokens[1];

			File patchFile = new File(MOD_ITEM_PATCH + scriptName + ".bpat");
			File indexFile;

			if (patchFile.exists()) {
				indexFile = new File(MOD_ITEM_TEMP + scriptName + ".bidx");
				config.source = new File(MOD_ITEM_TEMP + scriptName + ".bin");
			}
			else {
				indexFile = new File(DUMP_ITEM_SRC + scriptName + ".bidx");
				config.source = new File(DUMP_ITEM_RAW + scriptName + ".bin");
			}

			HashMap<String, Struct> structMap = new HashMap<>();
			BattleSectionEncoder tempEncoder = new BattleSectionEncoder(patcher);
			tempEncoder.loadIndexFile(structMap, indexFile);

			boolean foundMain = false;
			for (Struct str : structMap.values()) {
				if (str.isTypeOf(UseScriptT) && str.name.equals("$Script_UseItem")) {
					if (foundMain)
						throw new InputFileException(indexFile, "Found duplicate UseItem script for " + scriptName);

					config.mainAddress = str.originalAddress;
					foundMain = true;
				}
			}

			if (!foundMain)
				throw new InputFileException(indexFile, "Could not find UseItem script for " + scriptName);
		}
	}

	public void writeItemTable() throws IOException
	{
		boolean relocate = configs.size() > 32;
		int useItemIDs = ProjectDatabase.rom.getOffset(EOffset.ITEM_SCRIPT_LIST);
		int useItemTable = ProjectDatabase.rom.getOffset(EOffset.ITEM_SCRIPT_TABLE);

		if (relocate)
			useItemIDs = rp.nextAlignedOffset();

		rp.seek("UseItem Script IDs", useItemIDs);
		for (ItemConfig cfg : configs)
			rp.writeInt(cfg.itemID);
		rp.writeInt(0);

		if (relocate)
			useItemTable = rp.nextAlignedOffset();

		rp.seek("UseItem Script Table", useItemTable);
		for (ItemConfig cfg : configs) {
			cfg.entryOffset = rp.getCurrentOffset();
			rp.skip(8); // ROM offsets
			rp.writeInt(ITEM_BASE);
			rp.writeInt(cfg.mainAddress);
		}

		if (relocate) {
			/*
			% 001967B0 --> 80267ED0
			#new:Function $Function_LoadItemScript
			{
			    [B8]    LA      V1, $ItemScriptItemIDs
			    [100]   LA      V0, $ItemScriptTable
			}

			% 001968FC --> 8026801C
			#new:Function $Function_LoadFreeItemScript
			{
			    [78]    LA      V1, $ItemScriptItemIDs
			    [C4]    LA      V0, $ItemScriptTable
			}
			 */

			// note: this only occurs when relocating -- so these offsets are always ROM-appended
			String ptrUseItemIDs = String.format("%08X", rp.toAddress(useItemIDs));
			String ptrUseItemTable = String.format("%08X", rp.toAddress(useItemTable));

			// patch refs to 80293B80
			AsmUtils.assembleAndWrite("Relocate UseItemIDs", (0x1967B0 + 0xB8), rp, "LA  V1, " + ptrUseItemIDs);
			AsmUtils.assembleAndWrite("Relocate UseItemIDs", (0x1968FC + 0x78), rp, "LA  V1, " + ptrUseItemIDs);

			// patch refs to 80293C04
			AsmUtils.assembleAndWrite("Relocate UseItemScripts", (0x1967B0 + 0x100), rp, "LA  V0, " + ptrUseItemTable);
			AsmUtils.assembleAndWrite("Relocate UseItemScripts", (0x1968FC + 0xC4), rp, "LA  V0, " + ptrUseItemTable);
		}

		Logger.log("Wrote item script table.");
	}

	public void writeItemData() throws IOException
	{
		HashMap<File, Region> sourceMap = new LinkedHashMap<>();

		for (ItemConfig cfg : configs) {
			if (!sourceMap.containsKey(cfg.source)) {
				byte[] data = FileUtils.readFileToByteArray(cfg.source);
				int start = patcher.getBattleDataPos(data.length);
				int end = start + data.length;

				sourceMap.put(cfg.source, new Region(start, end));

				rp.seek(FilenameUtils.getBaseName(cfg.source.getName()) + " Data", start);
				rp.write(data);
				Logger.log(String.format("Wrote %s to %X", cfg.source.getName(), start));
			}

			Region r = sourceMap.get(cfg.source);

			// update table
			rp.seek("Item Script Table", cfg.entryOffset);
			rp.writeInt((int) r.start);
			rp.writeInt((int) r.end);
		}
	}
}
