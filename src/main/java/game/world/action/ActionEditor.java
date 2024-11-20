package game.world.action;

import static app.Directories.FN_BATTLE_ACTIONS;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

import app.Directories;
import app.Environment;
import app.StarRodException;
import app.input.IOUtils;
import game.MemoryRegion;
import game.ROM.EOffset;
import game.shared.ProjectDatabase;
import game.shared.ProjectDatabase.ConstEnum;
import game.shared.decoder.DumpMetadata;
import game.world.action.ActionDecoder.ActionSectionData;
import util.Logger;
import util.Priority;

public class ActionEditor
{
	public static final int RAM_START = 0x802B6000;
	public static final int SIZE_LIMIT = 0xED0; //XXX largest vanilla entry
	public static final int RAM_LIMIT = RAM_START + SIZE_LIMIT;

	public static void main(String[] args) throws IOException
	{
		Environment.initialize();
		dumpWorldActions(Environment.getBaseRomBuffer());
		Environment.exit();
	}

	public static void dumpWorldActions(ByteBuffer fileBuffer) throws IOException
	{
		int actionTableBase = ProjectDatabase.rom.getOffset(EOffset.ACTION_TABLE);

		ConstEnum actionNames = ProjectDatabase.getFromNamespace("ActionState");
		List<Integer> startingOffsets = new LinkedList<>();
		HashMap<Integer, ActionSectionData> scriptDataMap = new HashMap<>();

		File configFile = new File(Directories.DUMP_ACTION + FN_BATTLE_ACTIONS);
		PrintWriter pw = IOUtils.getBufferedPrintWriter(configFile);
		pw.printf("%% Action table dumped from 0x%X%n", actionTableBase);

		Logger.log(String.format("Dumping action data..."), Priority.MILESTONE);
		for (int i = 0; i < 39; i++) {
			String actionName;
			if (actionNames.has(i))
				actionName = actionNames.getName(i);
			else
				actionName = String.format("%02X", i);

			fileBuffer.position(actionTableBase + i * 0x10);
			int initAddr = fileBuffer.getInt();
			int romStart = fileBuffer.getInt();
			int romEnd = fileBuffer.getInt();
			int flags = fileBuffer.getInt();

			String scriptName = null;
			for (MemoryRegion r : ProjectDatabase.rom.getActionOverlays()) {
				if (r.startOffset == romStart) {
					scriptName = r.name;
					break;
				}
			}
			if (scriptName == null)
				throw new StarRodException("Could not identify action script starting at offset %X", romStart);

			if (!startingOffsets.contains(romStart)) {
				scriptDataMap.put(romStart, new ActionSectionData(scriptName, romStart, romEnd));
				startingOffsets.add(romStart);
			}

			ActionSectionData section = scriptDataMap.get(romStart);
			String mainName = "Update_" + actionName;
			section.mainAddressList.add(initAddr);
			section.mainNameList.add(mainName);

			pw.printf("%02X  %08X  %-20s %-20s %n", i, flags, section.name, mainName);
		}

		pw.close();

		for (ActionSectionData entry : scriptDataMap.values()) {
			Logger.log("Generating source files for action: " + entry.name, Priority.MILESTONE);

			DumpMetadata metadata = new DumpMetadata(entry.name,
				entry.start, entry.end,
				RAM_START, RAM_LIMIT);

			byte[] bytes = new byte[metadata.size];
			fileBuffer.position(metadata.romStart);
			fileBuffer.get(bytes);

			Logger.log("Generating source files for world action: " + entry.name);
			new ActionDecoder(fileBuffer, metadata, entry);
		}
	}
}
