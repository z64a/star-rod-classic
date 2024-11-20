package game.battle;

import static app.Directories.*;
import static game.battle.BattleConstants.BLANK_SECTION;
import static game.shared.StructTypes.*;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

import org.apache.commons.io.FileUtils;

import app.input.IOUtils;
import app.input.InputFileException;
import asm.AsmUtils;
import game.battle.formations.BattleSectionEncoder;
import game.shared.struct.Struct;
import game.yay0.Yay0Helper;
import patcher.Patcher;
import patcher.RomPatcher;
import util.Logger;
import util.Priority;

public final class BattlePatcher
{
	public static class BattleConfig
	{
		public File source;
		public int tableEntryOffset;
		public byte[] data;

		public String name;
		public int startAddress = 0;
		public int ptrFormationTable = 0;
		public int ptrMapTable = 0;
		public int ptrDmaTable = 0;

		public final boolean empty;

		public BattleConfig(boolean empty)
		{
			this.empty = empty;
		}
	}

	private final Patcher patcher;
	private final RomPatcher rp;
	private List<BattleConfig> configs = null;

	public BattlePatcher(Patcher patcher)
	{
		this.patcher = patcher;
		rp = patcher.getRomPatcher();
	}

	public void readConfigs() throws IOException
	{
		configs = new LinkedList<>();

		File in = new File(MOD_FORMA + FN_BATTLE_SECTIONS);
		for (String line : IOUtils.readFormattedTextFile(in, false)) {
			if (line.equals(BLANK_SECTION)) {
				configs.add(new BattleConfig(true));
				continue;
			}

			BattleConfig config = new BattleConfig(false);

			String[] tokens = line.split(":");
			if (tokens.length != 2)
				throw new InputFileException(in, "Invalid line in " + FN_BATTLE_SECTIONS + ":\r\n" + line);

			try {
				config.startAddress = (int) Long.parseLong(tokens[0].trim(), 16);
			}
			catch (NumberFormatException e) {
				throw new InputFileException(in, "Invalid address in " + FN_BATTLE_SECTIONS + ":\r\n" + line);
			}

			config.name = tokens[1].trim();

			configs.add(config);
		}
	}

	public void patchBattleData() throws IOException
	{
		for (BattleConfig cfg : configs) {
			BattleSectionEncoder encoder = new BattleSectionEncoder(patcher);
			encoder.encode(cfg);
		}
	}

	public void updateConfigs() throws IOException
	{
		for (BattleConfig cfg : configs) {
			if (cfg.empty)
				continue;

			File patch = new File(MOD_FORMA_PATCH + cfg.name + ".bpat");
			File index;

			if (patch.exists()) {
				index = new File(MOD_FORMA_TEMP + cfg.name + ".bidx");
				cfg.source = new File(MOD_FORMA_TEMP + cfg.name + ".bin");
			}
			else {
				index = new File(DUMP_FORMA_SRC + cfg.name + ".bidx");
				cfg.source = new File(DUMP_FORMA_RAW + cfg.name + ".bin");
			}

			HashMap<String, Struct> structMap = new HashMap<>();
			new BattleSectionEncoder(patcher).loadIndexFile(structMap, index);

			boolean foundFormationTable = false;
			boolean foundMapTable = false;
			boolean foundDmaTable = false;

			for (Struct str : structMap.values()) {
				if (str.isTypeOf(FormationTableT)) {
					cfg.ptrFormationTable = str.originalAddress;
					if (foundFormationTable)
						throw new InputFileException(index, "Found more than one " + FormationTableT + " in " + cfg.name);
					foundFormationTable = true;
				}

				if (str.isTypeOf(StageTableT)) {
					cfg.ptrMapTable = str.originalAddress;
					if (foundMapTable)
						throw new InputFileException(index, "Found more than one " + StageTableT + " in " + cfg.name);
					foundMapTable = true;
				}

				if (str.isTypeOf(DmaArgTableT)) {
					cfg.ptrDmaTable = str.originalAddress;
					if (foundDmaTable)
						throw new InputFileException(index, "Found more than one " + DmaArgTableT + " in " + cfg.name);
					foundDmaTable = true;
				}
			}
		}
	}

	public void writeBattleTable()
	{
		int size = configs.size() * 0x20;
		int tableOffset;

		if (size > BattleConstants.BATTLE_TABLE_SIZE)
			tableOffset = rp.nextAlignedOffset();
		else
			tableOffset = BattleConstants.BATTLE_TABLE_OFFSET;

		rp.seek("Battle Section Table", tableOffset);
		for (BattleConfig cfg : configs) {
			cfg.tableEntryOffset = rp.getCurrentOffset();
			rp.writeInt(0); // SJIS name
			rp.skip(8); // ROM offsets
			rp.writeInt(cfg.startAddress);
			rp.writeInt(cfg.ptrFormationTable);
			rp.writeInt(cfg.ptrMapTable);
			rp.writeInt(0);
			rp.writeInt(cfg.ptrDmaTable);
		}

		Logger.log("Wrote battle section table to 0x" + String.format("%X", tableOffset));

		fixPointersToBattleTable(tableOffset);
	}

	private void fixPointersToBattleTable(int offset)
	{
		if (offset < Patcher.ROM_BASE)
			return;

		int addr = Patcher.toAddress(offset);
		String LIO = String.format("LIO    V1, %08X", addr);

		// patch 80072BEC (load battle code)
		// was 3C038009 24635A30
		rp.seek("LoadBattle Fix", 0x4DFE8);
		AsmUtils.assembleAndWrite(true, "LoadBattleFix", rp, LIO);

		// patch 80269DF8 (LoadBattleSection api function)
		rp.seek("LoadBattleSection Fix", 0x1986D8);
		AsmUtils.assembleAndWrite(true, "LoadBattleSectionFix", rp, LIO);

		// patch 8025364C (LoadFromDmaTable api function)
		rp.seek("LoadFromDmaTable Fix", 0x181F2C);
		AsmUtils.assembleAndWrite(true, "LoadFromDmaTableFix", rp, String.format("LTW    V0, A0 (%08X)", addr + 0x1C));
	}

	// 04309A0 - 0543570	Battle sections 0x00 - 0x11
	// 05573E0 - 06F0B30	Battle sections 0x12 - 0x27
	public void writeBattleData(boolean compressBattleData) throws IOException
	{
		Logger.log("Loading formation data...", Priority.MILESTONE);
		for (BattleConfig cfg : configs) {
			if (cfg.empty)
				continue;

			cfg.data = FileUtils.readFileToByteArray(cfg.source);
		}

		if (compressBattleData) {
			Logger.log("Compressing formation data...", Priority.MILESTONE);
			configs.parallelStream().forEach((cfg) -> {
				if (!cfg.empty)
					cfg.data = Yay0Helper.encode(cfg.data);
			});
		}

		Logger.log("Writing formation data...", Priority.MILESTONE);
		for (BattleConfig cfg : configs) {
			if (cfg.empty)
				continue;

			//	byte[] data = FileUtils.readFileToByteArray(cfg.source);
			//	if(compressBattleData)
			//		data = Yay0Helper.encode(data);

			int start = patcher.getBattleDataPos(cfg.data.length);

			rp.seek(cfg.name + " Data", start);
			rp.write(cfg.data);
			Logger.log(String.format("Wrote %s to %X", cfg.source.getName(), start));

			// update table
			rp.seek("Battle Section Table", cfg.tableEntryOffset);
			rp.skip(4);
			rp.writeInt(start);
			rp.writeInt(start + cfg.data.length);

			cfg.data = null;
		}
	}
}
