package game.battle;

import static app.Directories.FN_BATTLE_ITEMS;
import static app.Directories.FN_BATTLE_MOVES;
import static game.battle.BattleConstants.*;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

import app.Directories;
import app.StarRodException;
import app.input.IOUtils;
import game.MemoryRegion;
import game.ROM.EOffset;
import game.battle.items.ItemDecoder;
import game.battle.minigame.MinigameDecoder;
import game.battle.moves.MoveDecoder;
import game.battle.moves.MoveDecoder.MoveSectionData;
import game.battle.partners.PartnerActorDecoder;
import game.battle.starpowers.StarPowerDecoder;
import game.shared.ProjectDatabase;
import game.world.partner.PartnerConfig;
import util.Logger;
import util.Priority;

public class AuxBattleDumper
{
	public static void dumpMoves(ByteBuffer fileBuffer) throws IOException
	{
		int moveTableBase = ProjectDatabase.rom.getOffset(EOffset.MOVE_SCRIPT_TABLE);

		List<Integer> startingOffsets = new LinkedList<>();
		HashMap<Integer, MoveSectionData> scriptDataMap = new HashMap<>();

		File configFile = new File(Directories.DUMP_MOVE + FN_BATTLE_MOVES);
		PrintWriter pw = IOUtils.getBufferedPrintWriter(configFile);
		pw.printf("%% Move script table dumped from 0x%X%n", moveTableBase);

		Logger.log(String.format("Dumping move data..."), Priority.MILESTONE);
		for (int i = 0; i < 49; i++) {
			fileBuffer.position(moveTableBase + i * 0x10);
			int start = fileBuffer.getInt();
			int end = fileBuffer.getInt();
			int startAddress = fileBuffer.getInt();
			int ptrMainScript = fileBuffer.getInt();

			pw.printf("%02X  ", i);
			if (start == 0) {
				pw.println("null");
				continue;
			}
			assert (startAddress == 0x802A1000);

			String scriptName = null;
			for (MemoryRegion r : ProjectDatabase.rom.getMoveOverlays()) {
				if (r.startOffset == start) {
					scriptName = r.name;
					break;
				}
			}
			if (scriptName == null)
				throw new StarRodException("Could not identify move script starting at offset %X", start);

			if (!startingOffsets.contains(start)) {
				scriptDataMap.put(start, new MoveSectionData(scriptName, start, end));
				startingOffsets.add(start);
			}

			String mainName;
			MoveSectionData section = scriptDataMap.get(start);
			if (!section.mainAddressList.contains(ptrMainScript)) {
				mainName = "Script_UseMove" + section.mainAddressList.size();
				section.mainAddressList.add(ptrMainScript);
				section.mainNameList.add(mainName);
			}
			else {
				int j = section.mainAddressList.indexOf(ptrMainScript);
				mainName = section.mainNameList.get(j);
			}

			String moveName = ProjectDatabase.getMoveName(i);
			pw.printf("%-20s %-20s %% %s%n", section.name, mainName, moveName);
		}

		pw.close();

		// Unused move script -- power bounce with no action commands, perhaps from the demo reel?
		MoveSectionData unusedScriptData = new MoveSectionData("Moves_Unused", 0x779C90, 0x77CB80);
		unusedScriptData.mainAddressList.add(BattleConstants.MOVE_BASE + 0x16E4);
		unusedScriptData.mainNameList.add("Script_UseMove0");
		scriptDataMap.put(0x779C90, unusedScriptData);

		for (int i : scriptDataMap.keySet()) {
			Logger.log("Generating source files for move: " + scriptDataMap.get(i).name, Priority.MILESTONE);
			MoveDecoder decoder = new MoveDecoder(fileBuffer, scriptDataMap.get(i));
			int unknownPointers = decoder.unknownPointers;
			int missingSections = decoder.missingSections;

			if (unknownPointers > 0)
				Logger.log(decoder.getSourceName() + " contains " + unknownPointers + " unknown pointers!");

			if (missingSections > 0)
				Logger.log(decoder.getSourceName() + " contains " + missingSections + " missing sections!");

			Logger.log("");
		}
	}

	public static void dumpPartnerMoves(ByteBuffer fileBuffer) throws IOException
	{
		for (int i = 0; i < 11; i++) {
			if (i == 9)
				continue;

			fileBuffer.position(0x1B2804 + i * 0x14);

			int start = fileBuffer.getInt();
			int end = fileBuffer.getInt();
			fileBuffer.getInt(); // ALLY_BASE
			int actor = fileBuffer.getInt();
			fileBuffer.getInt(); // unknown int, leave it alone

			String scriptName = String.format("%02X %s", i, PartnerConfig.DEFAULT_PARTNER_NAMES[i]);

			Logger.log("Generating source files for partner moves: " + scriptName, Priority.MILESTONE);
			PartnerActorDecoder decoder = new PartnerActorDecoder(fileBuffer, scriptName, start, end, actor);
			int unknownPointers = decoder.unknownPointers;
			int missingSections = decoder.missingSections;

			if (unknownPointers > 0)
				Logger.log(scriptName + " contains " + unknownPointers + " unknown pointers!");

			if (missingSections > 0)
				Logger.log(scriptName + " contains " + missingSections + " missing sections!");

			Logger.log("");
		}
	}

	public static void dumpStarPowers(ByteBuffer fileBuffer) throws IOException
	{
		for (int i = 0; i < NUM_STAR_POWERS; i++) {
			fileBuffer.position(0x1CB0B0 + i * 0x10);

			int start = fileBuffer.getInt();
			int end = fileBuffer.getInt();
			fileBuffer.getInt(); // STARS_BASE
			int main = fileBuffer.getInt();

			String scriptName = String.format("%02X %s", i, STAR_POWER_NAME[i]);

			Logger.log("Generating source files for star power: " + scriptName, Priority.MILESTONE);
			StarPowerDecoder decoder = new StarPowerDecoder(fileBuffer, scriptName, start, end, main);
			int unknownPointers = decoder.unknownPointers;
			int missingSections = decoder.missingSections;

			if (unknownPointers > 0)
				Logger.log(scriptName + " contains " + unknownPointers + " unknown pointers!");

			if (missingSections > 0)
				Logger.log(scriptName + " contains " + missingSections + " missing sections!");

			Logger.log("");
		}
	}

	public static void dumpItemScripts(ByteBuffer fileBuffer) throws IOException
	{
		int itemTableBase = ProjectDatabase.rom.getOffset(EOffset.ITEM_SCRIPT_TABLE);
		int itemScriptList = ProjectDatabase.rom.getOffset(EOffset.ITEM_SCRIPT_LIST);

		List<Integer> startingOffsets = new LinkedList<>();
		HashMap<Integer, MoveSectionData> scriptDataMap = new HashMap<>();

		File configFile = new File(Directories.DUMP_ITEM + FN_BATTLE_ITEMS);
		PrintWriter pw = IOUtils.getBufferedPrintWriter(configFile);
		pw.printf("%% Item script table dumped from 0x%X%n", itemTableBase);

		fileBuffer.position(itemScriptList);
		int numItemScripts = 32;

		int[] ids = new int[numItemScripts];
		for (int i = 0; i < numItemScripts; i++)
			ids[i] = fileBuffer.getInt();

		Logger.log(String.format("Dumping item data..."), Priority.MILESTONE);
		for (int i = 0; i < 32; i++) {
			fileBuffer.position(itemTableBase + 0x10 * i);

			int start = fileBuffer.getInt();
			int end = fileBuffer.getInt();
			int startAddress = fileBuffer.getInt();
			int ptrMainScript = fileBuffer.getInt();
			assert (startAddress == 0x802A1000);

			String scriptName = null;
			for (MemoryRegion r : ProjectDatabase.rom.getItemOverlays()) {
				if (r.startOffset == start) {
					scriptName = r.name;
					break;
				}
			}
			if (scriptName == null)
				throw new StarRodException("Could not identify item script starting at offset %X", start);

			if (!startingOffsets.contains(start)) {
				scriptDataMap.put(start, new MoveSectionData(scriptName, start, end));
				startingOffsets.add(start);
			}

			String mainName;
			MoveSectionData section = scriptDataMap.get(start);
			if (!section.mainAddressList.contains(ptrMainScript)) {
				mainName = "Script_UseItem";
				section.mainAddressList.add(ptrMainScript);
				section.mainNameList.add(mainName);
			}
			else {
				int j = section.mainAddressList.indexOf(ptrMainScript);
				mainName = section.mainNameList.get(j);
			}

			if (i == 0)
				pw.printf("%-18s %-24s %% %s%n", "*", section.name, "All other items");
			else
				pw.printf("%-18s %s%n", ProjectDatabase.getItemName(ids[i]), section.name);
		}

		pw.close();

		/*
		// Unused item scripts
		MoveSectionData unusedDriedShroom = new MoveSectionData("Item_UnusedDriedShroom", 0x71CCE0, 0x71D770);
		unusedDriedShroom.mainAddressList.add(0x802A18FC);
		unusedDriedShroom.mainNameList.add("UseItem_UnusedDriedShroom");
		scriptDataMap.put(0x71CCE0, unusedDriedShroom);

		MoveSectionData unusedUltraShroom = new MoveSectionData("Item_UnusedUltraShroom", 0x723780, 0x724CE0);
		unusedUltraShroom.mainAddressList.add(0x802A23BC);
		unusedUltraShroom.mainNameList.add("UseItem_UnusedUltraShroom");
		scriptDataMap.put(0x71CCE0, unusedDriedShroom);
		*/

		for (MoveSectionData data : scriptDataMap.values()) {
			Logger.log("Generating source files for item: " + data.name, Priority.MILESTONE);
			ItemDecoder decoder = new ItemDecoder(fileBuffer, data);
			int unknownPointers = decoder.unknownPointers;
			int missingSections = decoder.missingSections;

			if (unknownPointers > 0)
				Logger.log(decoder.getSourceName() + " contains " + unknownPointers + " unknown pointers!");

			if (missingSections > 0)
				Logger.log(decoder.getSourceName() + " contains " + missingSections + " missing sections!");

			Logger.log("");
		}

		Logger.log(String.format("Generating source files for item: Unused_DriedShroom"), Priority.MILESTONE);
		new ItemDecoder(fileBuffer, "Item_UnusedDriedShroom", 0x71CCE0, 0x71D770, 0x802A18FC);
		Logger.log("");

		Logger.log(String.format("Generating source files for item: Unused_UltraShroom"), Priority.MILESTONE);
		new ItemDecoder(fileBuffer, "Item_UnusedUltraShroom", 0x723780, 0x724CE0, 0x802A23BC, 0x802A1E00);
		Logger.log("");

		pw.close();
	}

	public static void dumpActionCommands(ByteBuffer fileBuffer) throws IOException
	{
		for (int i = 1; i <= NUM_ACTION_COMMANDS; i++) {
			fileBuffer.position(0x1C2DA0 + i * 0xC);

			int start = fileBuffer.getInt();
			int end = fileBuffer.getInt();
			int dest = fileBuffer.getInt();
			assert (dest == 0x802A9000);

			String cmdName = ProjectDatabase.getFromNamespace("ActionCommand").getName(i);
			cmdName = (cmdName == null) ? "" : " " + cmdName;
			String scriptName = String.format("%02X%s", i, cmdName);

			Logger.log("Generating source files for action command: " + scriptName, Priority.MILESTONE);
			MinigameDecoder decoder = new MinigameDecoder(fileBuffer, scriptName, start, end);
			int unknownPointers = decoder.unknownPointers;
			int missingSections = decoder.missingSections;

			if (unknownPointers > 0)
				Logger.log(scriptName + " contains " + unknownPointers + " unknown pointers!");

			if (missingSections > 0)
				Logger.log(scriptName + " contains " + missingSections + " missing sections!");

			Logger.log("");
		}
	}
}
