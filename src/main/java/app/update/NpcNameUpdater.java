package app.update;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map.Entry;

import org.apache.commons.io.FileUtils;

import app.Directories;
import app.input.IOUtils;
import app.input.InputFileException;
import app.input.Line;
import util.Logger;

public class NpcNameUpdater
{
	public static void updateAll() throws IOException
	{
		File crumb = new File(Directories.MOD_OUT + "hello");
		if (crumb.exists())
			return;

		File source = new File(Directories.DATABASE.toString() + "/old/npc_names.txt");

		List<String[]> entries = new ArrayList<>();
		for (Line line : IOUtils.readFormattedInputFile(source, false))
			entries.add(line.str.split("\\s+"));

		HashMap<String, HashMap<String, String>> allRules = new LinkedHashMap<>();
		for (String[] entry : entries) {
			HashMap<String, String> mapRules = allRules.get(entry[0]);
			if (mapRules == null) {
				mapRules = new HashMap<>();
				allRules.put(entry[0], mapRules);
			}
			if (mapRules.containsKey(entry[1]))
				throw new InputFileException(source, "Duplicate NPC defition for %s: %s", entry[0], entry[1]);
			mapRules.put(entry[1], entry[2]);
		}

		for (Entry<String, HashMap<String, String>> mapEntry : allRules.entrySet()) {
			String mapName = mapEntry.getKey();
			HashMap<String, String> mapRules = mapEntry.getValue();

			System.out.println("Updating map: " + mapName);
			for (Entry<String, String> rule : mapRules.entrySet())
				System.out.printf("  %s --> %s%n", rule.getKey(), rule.getValue());

			new NpcNameUpdater(mapName, mapRules.entrySet());
		}

		HashMap<String, String> isMap = new HashMap<>();
		isMap.put("RestartChance", "RandomRestart");
		Iterable<Entry<String, String>> isRules = isMap.entrySet();

		for (File f : IOUtils.getFilesWithExtension(Directories.MOD_ITEM_SCRIPTS, Directories.EXT_ITEM_SCRIPT, true))
			updateTextFile(f, isRules);

		HashMap<String, String> hsMap = new HashMap<>();
		hsMap.put("AddTexelOffset(?![XY])", "SetTexelOffset");
		Iterable<Entry<String, String>> hsRules = hsMap.entrySet();

		for (File f : IOUtils.getFilesWithExtension(Directories.MOD_HUD_SCRIPTS, Directories.EXT_HUD_SCRIPT, true))
			updateTextFile(f, hsRules);

		FileUtils.touch(crumb);
	}

	private NpcNameUpdater(String mapName, Iterable<Entry<String, String>> rules) throws IOException
	{
		for (File f : IOUtils.getFileWithin(Directories.MOD_MAP, mapName + ".mscr", true))
			updateTextFile(f, rules);

		for (File f : IOUtils.getFileWithin(Directories.MOD_MAP, mapName + ".mpat", true))
			updateTextFile(f, rules);

		for (File f : IOUtils.getFileWithin(Directories.MOD_MAP, mapName + ".xml", true))
			updateTextFile(f, rules);
	}

	private static void updateTextFile(File sourceFile, Iterable<Entry<String, String>> rules) throws IOException
	{
		Logger.log("Updating file: " + sourceFile.getName());

		List<String> linesIn = IOUtils.readPlainTextFile(sourceFile);
		List<String> linesOut = new ArrayList<>(linesIn.size());

		for (String line : linesIn) {
			for (var rule : rules)
				line = line.replaceAll(rule.getKey(), rule.getValue());
			linesOut.add(line);
		}

		sourceFile.delete();
		PrintWriter pw = IOUtils.getBufferedPrintWriter(sourceFile.getAbsolutePath());
		for (String line : linesOut)
			pw.println(line);
		pw.close();
	}
}
