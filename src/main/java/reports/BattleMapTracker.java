package reports;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.TreeMap;

import app.Directories;
import app.input.IOUtils;

public class BattleMapTracker
{
	private static final HashMap<String, List<Integer>> mapMap;
	private static final TreeMap<Integer, List<String>> battleMap;
	private static boolean enabled = false;

	private static final HashSet<Integer> battleIDSet;

	static {
		mapMap = new LinkedHashMap<>();
		battleMap = new TreeMap<>();
		battleIDSet = new HashSet<>();
	}

	public static void enable()
	{
		enabled = true;
	}

	public static boolean isEnabled()
	{
		return enabled;
	}

	public static boolean hasBattleID(int id)
	{
		return battleIDSet.contains(id & 0xFFFF0000);
	}

	public static List<String> getMaps(int id)
	{
		return battleMap.get(id & 0xFFFF0000);
	}

	public static void add(String mapName, int battleID)
	{
		if (!enabled)
			return;

		battleIDSet.add(battleID & 0xFFFF0000);

		List<Integer> battleList;
		if (mapMap.containsKey(mapName)) {
			battleList = mapMap.get(mapName);
		}
		else {
			battleList = new LinkedList<>();
			mapMap.put(mapName, battleList);
		}
		battleList.add(battleID);

		List<String> mapList;
		if (battleMap.containsKey(battleID)) {
			mapList = battleMap.get(battleID);
		}
		else {
			mapList = new LinkedList<>();
			battleMap.put(battleID, mapList);
		}
		mapList.add(mapName);
	}

	public static void printBattles() throws FileNotFoundException
	{
		if (!enabled)
			return;

		File out = new File(Directories.DUMP_REPORTS + "BattleList.txt");
		PrintWriter pw = IOUtils.getBufferedPrintWriter(out);
		pw.println("Maps featuring battles:");
		for (int battleID : battleMap.keySet()) {
			pw.printf("%08X : ", battleID);

			for (String mapName : battleMap.get(battleID))
				pw.printf("%-8s ", mapName);

			pw.println();
		}
		pw.close();
	}

	public static void printMaps() throws FileNotFoundException
	{
		if (!enabled)
			return;

		File out = new File(Directories.DUMP_REPORTS + "BattleMapIndex.txt");
		PrintWriter pw = IOUtils.getBufferedPrintWriter(out);
		pw.println("Battles found in maps:");
		for (String mapName : mapMap.keySet()) {
			pw.printf("%-8s : ", mapName);

			for (int battleID : mapMap.get(mapName))
				pw.printf("%08X ", battleID);

			pw.println();
		}
		pw.close();
	}
}
