package game.map.patching;

import static app.Directories.*;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

import org.apache.commons.io.FileUtils;

import app.Directories;
import app.Environment;
import app.StarRodClassic;
import app.config.Options;
import app.input.IOUtils;
import game.ROM;
import game.ROM.LibScope;
import game.map.Map;
import game.map.compiler.CollisionDecompiler;
import game.map.compiler.GeometryDecompiler;
import game.map.config.MapConfigTable;
import game.map.config.MapConfigTable.AreaConfig;
import game.map.config.MapConfigTable.MapConfig;
import game.map.config.MapConfigTable.Resource;
import game.map.config.MapConfigTable.Resource.ResourceType;
import game.map.marker.Marker;
import game.map.marker.Marker.MarkerType;
import game.map.tree.MapObjectNode;
import game.shared.ProjectDatabase;
import game.yay0.Yay0Helper;
import reports.FunctionCallTracker;
import util.Logger;
import util.Priority;

public class MapDumper
{
	public static void dumpMaps(boolean fullDump, boolean recompress) throws IOException
	{
		dumpMaps(ProjectDatabase.rom, Environment.getBaseRomReader(), fullDump, recompress);
	}

	public static void dumpMaps(ROM rom, RandomAccessFile raf, boolean fullDump, boolean recompress) throws IOException
	{
		Logger.log("Reading map tables.", Priority.MILESTONE);
		MapConfigTable table = MapConfigTable.read(rom, raf);

		HashMap<String, String> entries = IOUtils.readKeyValueFile(
			new File(Directories.DATABASE + "/" + Directories.FN_MAP_NICKNAMES));

		for (AreaConfig area : table.areas) {
			if (entries.containsKey(area.name))
				area.nickname = entries.get(area.name);

			for (MapConfig map : area.maps) {
				if (entries.containsKey(map.name))
					map.nickname = entries.get(map.name);
			}
		}

		Logger.log("Dumping map data.", Priority.MILESTONE);
		dumpMapData(raf, table);

		if (fullDump) {
			Logger.log("Dumping map assets.", Priority.MILESTONE);
			dumpAssets(raf, table);

			if (recompress) {
				Logger.log("Stripping and recompressing assets.", Priority.MILESTONE);
				stripAssets(table);
			}
		}

		raf.close();
		table.writeXML(new File(DUMP_MAP + FN_MAP_TABLE));
		generateSources(rom, table); // create *.map *.midx *.mscr

		//	ScriptRegistry.print();
	}

	/*
	public static void dumpJPMaps(File romFile) throws IOException
	{
		Logger.log("Reading map tables.", Priority.MILESTONE);
		RandomAccessFile raf = new RandomAccessFile(romFile, "r");
		MapConfigTable table = MapConfigTable.read(new ROM_JP(Directories.DATABASE.toFile()), raf);
	
		HashMap<String,String> entries = IOUtils.readKeyValueFile(
				new File(Directories.DATABASE + "/" + Directories.FN_MAP_NICKNAMES));
	
		for(AreaConfig area : table.areas)
		{
			if(entries.containsKey(area.name))
				area.nickname = entries.get(area.name);
	
			for(MapConfig map : area.maps)
			{
				if(entries.containsKey(map.name))
					map.nickname = entries.get(map.name);
			}
		}
	
		Logger.log("Dumping map assets.", Priority.MILESTONE);
		dumpAssets(raf, table);
	
		Logger.log("Dumping resources...", Priority.MILESTONE);
		for(Resource res : table.allResources)
		{
			byte[] writeBytes;
			byte[] dumpedBytes = new byte[res.length];
			raf.seek(res.offset);
			raf.read(dumpedBytes);
	
			if(res.compressed)
			{
				File yay0 = new File(DUMP_MAP_JP_YAY0 + res.name);
				FileUtils.writeByteArrayToFile(yay0, dumpedBytes);
	
				writeBytes = Yay0Helper.decode(dumpedBytes);
			}
			else
				writeBytes = dumpedBytes;
	
			File out = new File(DUMP_MAP_JP_RAW + res.name);
			FileUtils.writeByteArrayToFile(out, writeBytes);
		}
	
		raf.close();
		table.writeXML(new File(DUMP_MAP_JP + FN_MAP_TABLE));
	
		for(AreaConfig area : table.areas)
		{
			for(MapConfig cfg : area.maps)
			{
				Logger.log("Generating source files for map: " + cfg.name, Priority.MILESTONE);
				cfg.hasData = false;
	
				Map map = generateMap(cfg);
				try {
					map.saveMapAs(DUMP_MAP_JP_SRC + map.name + ".xml", "");
				} catch (Exception e) {
					StarRodDev.displayStackTrace(e);
				}
			}
	
			for(MapConfig cfg : area.stages)
			{
				Logger.log("Generating source files for stage: " + cfg.name, Priority.MILESTONE);
				cfg.hasData = false;
	
				Map map = generateMap(cfg);
	
				MapObjectNode<Marker> rootNode = map.markerTree.getRoot();
				for(int i = 0; i < BATTLE_ENEMY_POSITIONS.length; i++)
				{
					int[] vec = BATTLE_ENEMY_POSITIONS[i];
					String name = String.format("Home Position %X", i);
					Marker m = new Marker(name, MarkerType.Position, vec[0], vec[1], vec[2], 0);
					m.getNode().parentNode = rootNode;
					m.getNode().childIndex = rootNode.getChildCount();
					rootNode.add(m.getNode());
				}
	
				try {
					map.saveMapAs(DUMP_MAP_JP_SRC + map.name + ".xml", "");
				} catch (Exception e) {
					StarRodDev.displayStackTrace(e);
				}
			}
		}
	}
	*/

	private static void dumpMapData(RandomAccessFile raf, MapConfigTable table) throws IOException
	{
		for (AreaConfig area : table.areas) {
			for (MapConfig map : area.maps) {
				if (map.hasData) {
					byte[] mapData = new byte[map.dataEndOffset - map.dataStartOffset];
					raf.seek(map.dataStartOffset);
					raf.read(mapData);
					FileUtils.writeByteArrayToFile(new File(DUMP_MAP_RAW + map.name + ".bin"), mapData);
				}
			}
		}
	}

	private static void dumpAssets(RandomAccessFile raf, MapConfigTable table) throws IOException
	{
		Logger.log("Dumping resources...", Priority.MILESTONE);
		for (Resource res : table.allResources) {
			byte[] writeBytes;
			byte[] dumpedBytes = new byte[res.length];
			raf.seek(res.offset);
			raf.read(dumpedBytes);

			if (res.compressed) {
				File yay0 = new File(DUMP_MAP_YAY0 + res.name);
				FileUtils.writeByteArrayToFile(yay0, dumpedBytes);

				writeBytes = Yay0Helper.decode(dumpedBytes);
			}
			else
				writeBytes = dumpedBytes;

			File out = new File(DUMP_MAP_RAW + res.name);
			FileUtils.writeByteArrayToFile(out, writeBytes);
		}
	}

	/**
	 * Strips unnecessary data out of assets and recompressed them to save space.
	 * Should save ~56000 bytes
	 */
	private static void stripAssets(MapConfigTable table) throws IOException
	{
		int totalSize = 0;
		int totalSavings = 0;

		int i = 0;
		for (Resource res : table.allResources) {
			String progress = String.format("(%.1f%%)", 100.0 * ((float) i / table.allResources.size()));
			Logger.log("Stripping and recompressing assets... " + progress, Priority.UPDATE);
			i++;

			File dumpedFile = new File(DUMP_MAP_RAW + res.name);
			byte[] dumped = FileUtils.readFileToByteArray(dumpedFile);

			File out = new File(DUMP_MAP_YAY0 + res.name);

			if (!res.compressed) {
				// just copy uncompressed files
				FileUtils.writeByteArrayToFile(out, dumped);
				continue;
			}

			ResourceType type = Resource.resolveType(res.name);
			byte[] recompressed = null;

			if (type == ResourceType.SHAPE) {
				byte[] stripped = stripShape(dumped);
				recompressed = Yay0Helper.encode(stripped);
			}
			else {
				// don't bother trying to strip _hit or other files
				recompressed = Yay0Helper.encode(dumped);
			}

			if (recompressed.length < res.length) {
				Logger.logf("Stripped %05X bytes from %s",
					res.length - recompressed.length, res.name);

				totalSavings += res.length - recompressed.length;
				totalSize += res.length;

				// write the reduced yay0 file
				FileUtils.writeByteArrayToFile(out, recompressed);
			}
			else {
				Logger.logf("Could not reduce size of %s", res.name);
				totalSize += res.length;
				// keep the original yay0 file
			}
		}

		Logger.logf("Saved %08X / %08X (%2.3f%%)\n", totalSavings, totalSize, 100 * (float) totalSavings / totalSize);
	}

	private static byte[] stripShape(byte[] decoded) throws IOException
	{
		// find all 0000005E 00000002 8021XXXX to get texture pointers
		// collect all texture names and then clear the buffer
		HashMap<Integer, String> textureNameMap = new HashMap<>();
		HashMap<String, List<Integer>> texturePointerMap = new HashMap<>();

		ByteBuffer decodedBuffer = IOUtils.getDirectBuffer(decoded);

		// find all texture pointers in the file
		decodedBuffer.position(32);
		while (decodedBuffer.hasRemaining()) {
			if (decodedBuffer.getInt() == 0x0000005E && decodedBuffer.getInt() == 0x00000002) {
				int textureAddr = decodedBuffer.getInt();
				if ((textureAddr & 0xFF000000) == 0x80000000) {
					if (textureNameMap.containsKey(textureAddr)) {
						String textureName = textureNameMap.get(textureAddr);
						List<Integer> pointerList = texturePointerMap.get(textureName);
						pointerList.add(decodedBuffer.position() - 4);
					}
					else {
						LinkedList<Integer> pointerList = new LinkedList<>();
						pointerList.add(decodedBuffer.position() - 4);
						int pos = decodedBuffer.position();

						decodedBuffer.position(textureAddr - 0x80210000);
						String textureName = IOUtils.readString(decodedBuffer);

						textureNameMap.put(textureAddr, textureName);
						texturePointerMap.put(textureName, pointerList);
						decodedBuffer.position(pos);
					}
				}
			}
		}

		decodedBuffer.position(8);
		int objectNamesOffset = decodedBuffer.getInt() - 0x80210000;

		// zero out pointers in the header (more compression)
		decodedBuffer.position(8);
		decodedBuffer.putInt(0);
		decodedBuffer.putInt(0);
		decodedBuffer.putInt(0);

		HashMap<String, Integer> revisedTextureNameMap = new HashMap<>();

		// move the strings to the revised end of file
		decodedBuffer.position(objectNamesOffset);
		for (String s : textureNameMap.values()) {
			revisedTextureNameMap.put(s, decodedBuffer.position() + 0x80210000);

			decodedBuffer.put(s.getBytes());
			int paddedSize = ((s.length() + 4) & 0xFFFFFFFC);
			for (int j = s.length(); j < paddedSize; j++)
				decodedBuffer.put((byte) 0);
		}
		int end = decodedBuffer.position();

		// now update all the old pointers
		for (String s : texturePointerMap.keySet()) {
			int newPointer = revisedTextureNameMap.get(s);
			List<Integer> pointers = texturePointerMap.get(s);

			for (int p : pointers) {
				decodedBuffer.position(p);
				decodedBuffer.putInt(newPointer);
			}
		}

		decodedBuffer.rewind();

		byte[] trimmed = new byte[end];
		decodedBuffer.get(trimmed);

		return trimmed;
	}

	private static byte[] stripHit(byte[] decoded) throws IOException
	{
		ByteBuffer decodedBuffer = IOUtils.getDirectBuffer(decoded);

		// colliders
		decodedBuffer.position(0);
		decodedBuffer.position(decodedBuffer.getInt());

		short count = decodedBuffer.getShort();
		decodedBuffer.getShort();
		decodedBuffer.position(decodedBuffer.getInt());

		for (short s = 0; s < count; s++) {
			decodedBuffer.getShort();
			decodedBuffer.putInt(0xFFFFFFFF);
			decodedBuffer.getShort();
			decodedBuffer.getInt();
		}

		// areas
		decodedBuffer.position(4);
		decodedBuffer.position(decodedBuffer.getInt());

		count = decodedBuffer.getShort();
		decodedBuffer.getShort();
		decodedBuffer.position(decodedBuffer.getInt());

		for (short s = 0; s < count; s++) {
			decodedBuffer.getShort();
			decodedBuffer.putInt(0xFFFFFFFF);
			decodedBuffer.getShort();
			decodedBuffer.getInt();
		}

		decodedBuffer.rewind();

		return decodedBuffer.array();
	}

	private static final int[][] BATTLE_ENEMY_POSITIONS = {
			{ 5, 0, -20 }, { 45, 0, -5 }, { 85, 0, 10 }, { 125, 0, 25 },
			{ 10, 50, -20 }, { 50, 45, -5 }, { 90, 50, 10 }, { 130, 55, 25 },
			{ 15, 85, -20 }, { 55, 80, -5 }, { 95, 85, 10 }, { 135, 90, 25 },
			{ 15, 125, -20 }, { 55, 120, -5 }, { 95, 125, 10 }, { 135, 130, 25 },
			{ 105, 0, 0 } };

	private static void generateSources(ROM rom, MapConfigTable table) throws IOException
	{
		long t0 = System.nanoTime();

		int totalUnknownPointers = 0;
		int totalMissingSections = 0;

		FunctionCallTracker.clear();
		boolean dumpReports = Environment.mainConfig.getBoolean(Options.DumpReports);

		for (AreaConfig area : table.areas) {
			for (MapConfig cfg : area.maps) {
				Logger.log("Generating source files for map: " + cfg.name, Priority.MILESTONE);

				Map map = generateMap(rom, cfg);
				try {
					map.saveMapAs(DUMP_MAP_SRC + map.name + ".xml", "");
				}
				catch (Exception e) {
					StarRodClassic.displayStackTrace(e);
				}

				totalUnknownPointers += cfg.unknownPointers;
				totalMissingSections += cfg.missingSections;
				Logger.log("", Priority.IMPORTANT);
			}

			for (MapConfig cfg : area.stages) {
				Logger.log("Generating source files for stage: " + cfg.name, Priority.MILESTONE);
				Map map = generateMap(rom, cfg);
				map.isStage = cfg.isStage;

				MapObjectNode<Marker> rootNode = map.markerTree.getRoot();
				for (int i = 0; i < BATTLE_ENEMY_POSITIONS.length; i++) {
					int[] vec = BATTLE_ENEMY_POSITIONS[i];
					String name = String.format("Home Position %X", i);
					Marker m = new Marker(name, MarkerType.Position, vec[0], vec[1], vec[2], 0);
					m.getNode().parentNode = rootNode;
					m.getNode().childIndex = rootNode.getChildCount();
					rootNode.add(m.getNode());
				}

				try {
					map.saveMapAs(DUMP_MAP_SRC + map.name + ".xml", "");
				}
				catch (Exception e) {
					StarRodClassic.displayStackTrace(e);
				}

				totalUnknownPointers += cfg.unknownPointers;
				totalMissingSections += cfg.missingSections;
				Logger.log("", Priority.IMPORTANT);
			}
		}

		Logger.log(totalUnknownPointers + " total unknown pointers.", Priority.IMPORTANT);
		Logger.log(totalMissingSections + " total missing sections.", Priority.IMPORTANT);

		if (dumpReports)
			FunctionCallTracker.printCalls(
				rom.getLibrary(LibScope.World),
				new PrintWriter(DUMP_REPORTS + "map_func_list.txt"));

		long t1 = System.nanoTime();
		Logger.logf("TOTAL TIME: %8.2f ms\n", (t1 - t0) / 1000000.0);
	}

	private static Map generateMap(ROM rom, MapConfig cfg) throws IOException
	{
		Map map = new Map("", cfg.name);

		map.hasBackground = !cfg.bgName.isEmpty();
		map.bgName = cfg.bgName;
		map.texName = map.getExpectedTexFilename();

		File shapeFile = new File(DUMP_MAP_RAW + cfg.name + "_shape");
		File hitFile = new File(DUMP_MAP_RAW + cfg.name + "_hit");
		File dataFile = new File(DUMP_MAP_RAW + cfg.name + ".bin");

		if (cfg.hasShape) {
			Logger.log("Analyzing geometry...");
			new GeometryDecompiler(map, shapeFile);
		}

		if (cfg.hasHit) {
			Logger.log("Analyzing collision...");
			new CollisionDecompiler(map, hitFile);
		}

		if (cfg.hasData) {
			Logger.log("Analyzing scripts...");
			new MapDecoder(rom, map, cfg, dataFile);
		}

		return map;
	}
}
