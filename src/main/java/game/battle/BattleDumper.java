package game.battle;

import static app.Directories.*;
import static game.battle.BattleConstants.*;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.ByteBuffer;
import java.util.HashMap;

import app.input.IOUtils;
import game.battle.formations.BattleSectionDecoder;
import game.map.Map;
import game.map.MapIndex;
import game.map.config.MapConfigTable;
import game.map.config.MapConfigTable.AreaConfig;
import game.map.config.MapConfigTable.MapConfig;
import util.Logger;
import util.Priority;

public class BattleDumper
{
	public static void dumpBattles(ByteBuffer fileBuffer) throws IOException
	{
		Logger.log("Loading stage data...", Priority.MILESTONE);
		HashMap<String, MapIndex> stageIndexLookup = new HashMap<>();
		MapConfigTable mapTable = MapConfigTable.readXML(new File(DUMP_MAP + FN_MAP_TABLE));
		for (AreaConfig area : mapTable.areas) {
			for (MapConfig map : area.stages) {
				File f = new File(DUMP_MAP_SRC + map.name + Map.EXTENSION);
				if (!f.exists()) {
					Logger.logError("Cannot find map for stage: " + map.name + Map.EXTENSION);
				}

				Map stage = Map.loadMap(f);
				stageIndexLookup.put(map.name, new MapIndex(stage));
			}
		}

		PrintWriter pw = IOUtils.getBufferedPrintWriter(DUMP_BATTLE + FN_BATTLE_SECTIONS);

		int totalUnknownPointers = 0;
		int totalMissingSections = 0;

		for (int section = 0; section < NUM_SECTIONS; section++) {
			if (section == 0x28 || section == 0x2F) {
				pw.println(BLANK_SECTION);
				continue;
			}

			Logger.log("Generating source files for battles: " + String.format(SECTION_NAMES[section]), Priority.MILESTONE);

			BattleSectionDecoder decoder = new BattleSectionDecoder(fileBuffer, section, stageIndexLookup);
			pw.printf("%08X : %s%n", decoder.getStartAddress(), SECTION_NAMES[section]);

			int unknownPointers = decoder.unknownPointers;
			int missingSections = decoder.missingSections;
			totalUnknownPointers += unknownPointers;
			totalMissingSections += missingSections;

			if (unknownPointers > 0)
				Logger.log("Found " + unknownPointers + " unknown pointers.");

			if (missingSections > 0)
				Logger.log("Missing " + missingSections + " sections!");

			Logger.log("");
		}

		Logger.log(totalUnknownPointers + " total unknown pointers!", Priority.IMPORTANT);
		Logger.log(totalMissingSections + " total missing sections!", Priority.IMPORTANT);
		pw.close();
	}
}
