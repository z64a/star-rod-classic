package game.battle.moves;

import static app.Directories.*;
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
import game.battle.BattleConstants;
import game.battle.formations.BattleSectionEncoder;
import game.shared.struct.Struct;
import patcher.Patcher;
import patcher.Region;
import patcher.RomPatcher;
import util.Logger;

public class MovePatcher
{
	private static final class MoveConfig
	{
		public File source;
		public int entryOffset;

		public int mainAddress;
	}

	private final Patcher patcher;
	private final RomPatcher rp;
	private List<MoveConfig> configs;

	public MovePatcher(Patcher patcher)
	{
		this.patcher = patcher;
		rp = patcher.getRomPatcher();
	}

	public void patchMoveData() throws IOException
	{
		FileUtils.forceMkdir(MOD_MOVE_TEMP.toFile());
		FileUtils.cleanDirectory(MOD_MOVE_TEMP.toFile());

		Collection<File> patchFiles = IOUtils.getFilesWithExtension(MOD_MOVE_PATCH, "bpat", true);

		for (File f : patchFiles) {
			Logger.log("Executing patch: " + f.getName());
			String name = FilenameUtils.removeExtension(f.getName());
			MoveEncoder encoder = new MoveEncoder(patcher);
			encoder.encode(name);
		}
	}

	public void generateConfigs() throws IOException
	{
		configs = new LinkedList<>();
		File tableFile = new File(MOD_MOVE + FN_BATTLE_MOVES);
		List<String> lines = IOUtils.readFormattedTextFile(tableFile, false);

		if (lines.size() != 49)
			throw new InputFileException(tableFile, "Incorrect number of lines in move table.");

		for (int i = 0; i < 49; i++) {
			String[] tokens = lines.get(i).split("\\s+");

			int lineIndex = Integer.parseInt(tokens[0], 16);
			if (lineIndex != i)
				throw new InputFileException(tableFile, "Index %d out of order.", i);

			if (tokens[1].equals("null")) {
				configs.add(null);
				continue;
			}

			MoveConfig config = new MoveConfig();
			String scriptName = tokens[1];
			String mainName = tokens[2];

			File patchFile = new File(MOD_MOVE_PATCH + scriptName + ".bpat");
			File indexFile;

			if (patchFile.exists()) {
				indexFile = new File(MOD_MOVE_TEMP + scriptName + ".bidx");
				config.source = new File(MOD_MOVE_TEMP + scriptName + ".bin");
			}
			else {
				indexFile = new File(DUMP_MOVE_SRC + scriptName + ".bidx");
				config.source = new File(DUMP_MOVE_RAW + scriptName + ".bin");
			}

			HashMap<String, Struct> structMap = new HashMap<>();
			BattleSectionEncoder tempEncoder = new BattleSectionEncoder(patcher);
			tempEncoder.loadIndexFile(structMap, indexFile);

			boolean foundMain = false;
			for (Struct str : structMap.values()) {
				if (str.isTypeOf(UseScriptT) && str.name.equals("$" + mainName)) {
					if (foundMain)
						throw new InputFileException(indexFile, "Duplicate " + mainName + " scripts for " + scriptName);

					config.mainAddress = str.originalAddress;
					foundMain = true;
				}
			}

			if (!foundMain)
				throw new InputFileException(indexFile, "Could not find " + mainName + " script for " + scriptName);

			configs.add(config);
		}
	}

	public void writeMoveTable() throws IOException
	{
		int size = configs.size() * 0x10;
		int tableOffset;

		boolean relocated = (size > BattleConstants.MOVE_TABLE_SIZE);

		if (relocated)
			tableOffset = rp.nextAlignedOffset();
		else
			tableOffset = BattleConstants.MOVE_TABLE_OFFSET;

		rp.seek("Move Table", tableOffset);

		for (MoveConfig cfg : configs) {
			// write empty entries
			if (cfg == null) {
				rp.writeInt(0);
				rp.writeInt(0);
				rp.writeInt(0);
				rp.writeInt(0);
			}
			else {
				cfg.entryOffset = rp.getCurrentOffset();
				rp.skip(8); // ROM offsets
				rp.writeInt(BattleConstants.MOVE_BASE);
				rp.writeInt(cfg.mainAddress);
			}
		}

		Logger.log("Wrote move script table to 0x" + String.format("%X", tableOffset));

		if (relocated)
			fixPointersToMoveTable(tableOffset);
	}

	private void fixPointersToMoveTable(int offset) throws IOException
	{
		if (offset < Patcher.ROM_BASE)
			return;

		int addr = rp.toAddress(offset);
		int ins1 = 0x3C020000 | (addr >>> 16);
		int ins2 = 0x34420000 | (addr & 0x0000FFFF);

		// func_80268130 == useMove(int index)
		rp.seek("Move Table Reference", 0x196A34);
		rp.writeInt(ins1);
		rp.writeInt(ins2);
	}

	/*
	 * 7345A0 - 779C90
	 * 77CB80 - 789E60
	 */
	public void writeMoveData() throws IOException
	{
		HashMap<File, Region> sourceMap = new LinkedHashMap<>();

		for (MoveConfig cfg : configs) {
			if (cfg == null)
				continue;

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
			rp.seek("Move Table", cfg.entryOffset);
			rp.writeInt((int) r.start);
			rp.writeInt((int) r.end);
		}
	}
}
