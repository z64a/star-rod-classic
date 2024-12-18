package game.map.patching;

import static app.Directories.*;
import static game.shared.StructTypes.HeaderT;
import static game.shared.StructTypes.InitFunctionT;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map.Entry;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;

import app.Environment;
import app.input.IOUtils;
import app.input.InputFileException;
import game.map.Map;
import game.map.MapIndex;
import game.map.compiler.CollisionCompiler;
import game.map.compiler.GeometryCompiler;
import game.map.config.MapConfigTable;
import game.map.config.MapConfigTable.AreaConfig;
import game.map.config.MapConfigTable.MapConfig;
import game.map.config.MapConfigTable.Resource;
import game.map.config.MapConfigTable.Resource.ResourceType;
import game.shared.struct.Struct;
import patcher.Patcher;
import patcher.Region;
import patcher.RomPatcher;
import util.ListingMap;
import util.Logger;
import util.Priority;

public class MapPatcher
{
	private final Patcher patcher;
	private final RomPatcher rp;

	public MapPatcher(Patcher patcher)
	{
		this.patcher = patcher;
		rp = patcher.getRomPatcher();
	}

	public void patchMapData() throws IOException
	{
		ListingMap<String, File> patches = new ListingMap<>();
		for (File f : IOUtils.getFilesWithExtension(MOD_MAP_PATCH, "mpat", true))
			patches.add(f.getName(), f);

		for (Entry<String, List<File>> e : patches.entrySet()) {
			Collections.sort(e.getValue(), (a, b) -> a.getAbsolutePath().length() - b.getAbsolutePath().length());

			String mapName = FilenameUtils.removeExtension(e.getKey());
			Logger.log("Executing patches for: " + mapName);

			MapIndex index = patcher.getMapIndex(mapName);

			MapEncoder encoder = new MapEncoder(patcher, index);
			encoder.encode(e.getValue(), mapName);
		}
	}

	public MapConfigTable readConfigs() throws IOException
	{
		File xmlFile = new File(MOD_MAP + FN_MAP_TABLE);
		MapConfigTable table = MapConfigTable.readXML(xmlFile);

		if (table.areas.size() < 1)
			throw new InputFileException(xmlFile, "No areas found.");

		for (AreaConfig area : table.areas) {
			if (area.name.length() > 3)
				throw new InputFileException(xmlFile, "Area names must not exceed three characters! " + area.name);

			for (MapConfig map : area.maps) {
				if (map.name.length() > 7)
					throw new InputFileException(xmlFile, "Map names must not exceed seven characters! " + map.name);

				if (map.bgName.length() > 7)
					throw new InputFileException(xmlFile, "Map background names must not exceed seven characters! " + map.name);
			}
		}

		class Segment
		{
			public int start;
			public int end;
		}

		// find original entries and their locations on the ROM
		HashMap<String, Segment> originalEntries = new HashMap<>(1200);
		RandomAccessFile raf = Environment.getBaseRomReader();
		for (int i = 0; i < 28; i++) {
			raf.seek(0x006E8F0 + (i * 0x10));

			int numMaps = raf.readInt();
			int mapTableOffset = raf.readInt() - 0x80024C00;

			for (int j = 0; j < numMaps; j++) {
				raf.seek(mapTableOffset + (j * 0x20));
				int mapNameAddr = raf.readInt();
				raf.skipBytes(4);
				Segment seg = new Segment();
				seg.start = raf.readInt();
				seg.end = raf.readInt();

				raf.seek(mapNameAddr - 0x80024C00);
				String mapName = IOUtils.readString(raf, 8);

				// NOTE: kkj_26 has a DUPLICATE entry (0x006D110), but since they both
				// use the same data section, there is no problem.
				originalEntries.put(mapName, seg);
			}
		}
		raf.close();

		for (AreaConfig area : table.areas) {
			for (MapConfig map : area.maps) {
				Segment seg = originalEntries.get(map.name);
				if (seg != null) {
					map.oldExists = true;
					map.startOffset = seg.start;
					map.endOffset = seg.end;
				}
			}
		}

		return table;
	}

	public void buildMissing(MapConfigTable table) throws IOException
	{
		for (AreaConfig area : table.areas) {
			for (MapConfig map : area.maps) {
				if (!map.hasShape && !map.hasHit)
					continue;

				File[] matches = IOUtils.getFileWithin(MOD_MAP_SAVE, map.name + Map.EXTENSION, true);

				if (matches.length == 0)
					continue;

				if (matches.length > 1) {
					Logger.log("Found multiple sources for " + map.name);
					continue;
				}

				File xmlFile = matches[0];
				File shapeFile = new File(MOD_MAP_BUILD + map.name + "_shape");
				File hitFile = new File(MOD_MAP_BUILD + map.name + "_hit");

				if (map.hasShape && (!shapeFile.exists() || shapeFile.lastModified() < xmlFile.lastModified())) {
					Logger.log("Must build missing shape file for " + map.name);
					new GeometryCompiler(Map.loadMap(xmlFile));
				}

				if (map.hasHit && (!hitFile.exists() || hitFile.lastModified() < xmlFile.lastModified())) {
					Logger.log("Must build missing hit file for " + map.name);
					new CollisionCompiler(Map.loadMap(xmlFile));
				}
			}
		}
	}

	/**
	 * Writes a skeleton map config table based on the number of areas/maps and
	 * their names. Also writes the name strings to the ROM.
	 * @return ROM offset of the area table
	 * @throws IOException
	 */
	public int writeConfigTable(MapConfigTable mapTable) throws IOException
	{
		HashMap<String, Integer> names = new LinkedHashMap<>(1200);

		for (AreaConfig area : mapTable.areas) {
			if (!names.containsKey(area.name))
				names.put(area.name, -1);

			for (MapConfig map : area.maps) {
				if (!names.containsKey(map.name))
					names.put(map.name, -1);

				if (!names.containsKey(map.bgName))
					names.put(map.bgName, -1);
			}
		}

		rp.seek("Map Names", rp.nextAlignedOffset());
		for (String name : names.keySet()) {
			if (name.isEmpty())
				continue;

			names.put(name, rp.toAddress(rp.getCurrentOffset()));

			rp.write(name.getBytes());
			for (int i = name.length(); i < 8; i++)
				rp.writeByte(0);
		}

		names.put("", 0);

		rp.seek("Map Configs", rp.nextAlignedOffset());
		for (AreaConfig area : mapTable.areas) {
			area.ptrConfigTableEntry = rp.toAddress(rp.getCurrentOffset());
			for (MapConfig map : area.maps) {
				if (!map.hasData)
					continue; //XXX added! is this correct? should be...

				map.configTableOffset = rp.getCurrentOffset();
				rp.writeInt(names.get(map.name));
				rp.skip(16);
				rp.writeInt(names.get(map.bgName));
				rp.skip(4);
				rp.writeInt(map.flags);
			}
		}

		int tableBase = rp.getCurrentOffset();
		for (AreaConfig area : mapTable.areas) {
			int numMaps = 0;
			for (MapConfig map : area.maps) {
				if (map.hasData)
					numMaps++;
			}

			rp.writeInt(numMaps);
			rp.writeInt(area.ptrConfigTableEntry);
			rp.writeInt(names.get(area.name)); // ACSII name
			rp.writeInt(0); // SJIS name
		}

		// end table with blank entry
		rp.writeInt(0);
		rp.writeInt(0);
		rp.writeInt(0);
		rp.writeInt(0);

		Logger.log("Wrote map config table to 0x" + String.format("%X", tableBase));

		fixPointersToConfigTable(tableBase);
		return tableBase;
	}

	/**
	 * Fixes several dozen hard-coded pointers to use the new area/map config table.
	 * @throws IOException
	 */
	private void fixPointersToConfigTable(int areaTableStart) throws IOException
	{
		// old table information:
		// map table	80090050	<-> 6B450
		// area table	800934F0

		int addr = rp.toAddress(areaTableStart);
		int upper = (addr >>> 16);
		int lower = addr & 0x0000FFFF;

		rp.seek("Map Config Table Reference", 0x36014);
		rp.writeInt(0x3C020000 | upper);
		rp.writeInt(0x24420000 | lower);

		rp.seek("Map Config Table Reference", 0x36054);
		rp.writeInt(0x3C020000 | upper);
		rp.writeInt(0x24420000 | lower);

		rp.seek("Map Config Table Reference", 0x360A8);
		rp.writeInt(0x3C020000 | upper);
		rp.writeInt(0x24420000 | lower);

		rp.seek("Map Config Table Reference", 0xE0B34);
		rp.writeInt(0x3C070000 | upper);
		rp.writeInt(0x24E70000 | lower);

		// These occur in battle sections FLO and FLO2.
		// Patching them here won't help, they must be updated in the
		// proper .bpat files.
		/*
			int insLoadS3[] = {
					0x5B28E8, 0x5B29A8, 0x5B2A68, 0x5B2B28, 0x5B2BE8,
					0x5B2CA8, 0x5B2D68, 0x5B3148, 0x5B3208, 0x5B32C8,
					0x5CF478, 0x5CF538, 0x5CF5F8, 0x5CF6B8, 0x5CF778,
					0x5CF838, 0x5CF8F8, 0x5CFCD8, 0x5CFD98, 0x5CFE58};

			for(int i : insLoadS3)
			{
				raf.seek(i);
				raf.writeInt(0x3C130000 | upper);
				raf.writeInt(0x26730000 | lower);
			}
		 */

		// 3C018009 00220821 8C2234F4
		rp.seek("Map Config Table Reference", 0x35BE0);
		rp.writeInt(0x3C010000 | upper);
		rp.skip(4);
		rp.writeInt(0x8C220000 | (lower + 4));

		// 3C018009 00230821 8C2334F4

		rp.seek("Map Config Table Reference", 0xF064);
		rp.writeInt(0x3C010000 | upper);
		rp.skip(4);
		rp.writeInt(0x8C230000 | (lower + 4));

		rp.seek("Map Config Table Reference", 0x7B6C4);
		rp.writeInt(0x3C010000 | upper);
		rp.skip(4);
		rp.writeInt(0x8C230000 | (lower + 4));

		// 3C068009 00C33021 8CC634F4
		rp.seek("Map Config Table Reference", 0x10130);
		rp.writeInt(0x3C060000 | upper);
		rp.skip(4);
		rp.writeInt(0x8CC60000 | (lower + 4));

		// 3C058009 00A32821 8CA534F4
		rp.seek("Map Config Table Reference", 0x10CAC);
		rp.writeInt(0x3C050000 | upper);
		rp.skip(4);
		rp.writeInt(0x8CA50000 | (lower + 4));

		// 3C048009 00922021 8C8434F4
		rp.seek("Map Config Table Reference", 0x3609C);
		rp.writeInt(0x3C040000 | upper);
		rp.skip(4);
		rp.writeInt(0x8C840000 | (lower + 4));
	}

	public void writeMapData(MapConfigTable mapTable) throws IOException
	{
		for (AreaConfig area : mapTable.areas) {
			for (MapConfig map : area.maps) {
				if (!map.hasData)
					continue;

				File customData = new File(MOD_MAP_BUILD + map.name + ".bin");
				File patchData = new File(MOD_MAP_TEMP + map.name + ".bin");

				if (!customData.exists() && !patchData.exists())
					continue;

				File newData = customData.exists() ? customData : patchData;

				if (customData.exists() && patchData.exists())
					Logger.log(String.format("CONFLICT: %s has data in both %s and %s.",
						map.name, MOD_MAP_BUILD, MOD_MAP_TEMP),
						Priority.ERROR);

				// by default, overwrite existing data
				int writeOffset = map.startOffset;

				if (!map.oldExists) {
					// brand new map, append to end
					writeOffset = rp.nextAlignedOffset();
				}
				else if (newData.length() > (map.endOffset - map.startOffset)) {
					// modified map is larger than original, append to end
					writeOffset = rp.nextAlignedOffset();
				}

				map.startOffset = writeOffset;
				map.endOffset = writeOffset + (int) newData.length();

				byte[] data = FileUtils.readFileToByteArray(newData);
				rp.seek(map.name + " Data", writeOffset);
				rp.write(data);

				Logger.log("Wrote " + newData.getName() + " to " + String.format("%X", writeOffset));
			}
		}
	}

	/**
	 * Fills in the map config table.
	 * @throws IOException
	 */
	public void updateConfigTable(MapConfigTable mapTable) throws IOException
	{
		for (AreaConfig area : mapTable.areas) {
			for (MapConfig map : area.maps) {
				if (!map.hasData)
					continue;

				MapIndex index = patcher.getMapIndex(map.name);

				File patched = new File(MOD_MAP_TEMP + map.name + ".bin");
				File structIndexFile;

				if (patched.exists())
					structIndexFile = new File(MOD_MAP_TEMP + map.name + ".midx");
				else
					structIndexFile = new File(MOD_MAP_SRC + map.name + ".midx");

				HashMap<String, Struct> structMap = new HashMap<>();
				new MapEncoder(patcher, index).loadIndexFile(structMap, structIndexFile);

				boolean foundHeader = false;
				boolean foundInitFunction = false;

				for (Struct str : structMap.values()) {
					if (str.isTypeOf(HeaderT)) {
						map.ptrHeader = str.originalAddress;
						if (foundHeader)
							throw new RuntimeException("Found more than one " + HeaderT + " in " + map.name);
						foundHeader = true;
					}

					if (str.isTypeOf(InitFunctionT)) {
						map.ptrInitFunction = str.originalAddress;
						if (foundInitFunction)
							throw new RuntimeException("Found more than one " + InitFunctionT + " in " + map.name);
						foundInitFunction = true;
					}
				}

				rp.seek("Map Configs", map.configTableOffset);
				rp.skip(4);
				rp.writeInt(map.ptrHeader);
				rp.writeInt(map.startOffset);
				rp.writeInt(map.endOffset);
				rp.writeInt(0x80240000);
				rp.skip(4);
				rp.writeInt(map.ptrInitFunction);
			}
		}
	}

	public void writeAssetTable(MapConfigTable mapTable) throws IOException
	{
		mapTable.calculateRequiredResources();

		Deque<Resource> resourceList = new LinkedList<>();
		int tableSize = 0x1C; // end_data

		for (Resource res : mapTable.allResources) {
			if (res.name.length() > 15)
				throw new InputFileException(mapTable.source, "Resource names must not exceed 15 characters! " + res.name);

			File f = new File(MOD_MAP_BUILD + res.name);
			if (!f.exists()) {
				if (res.compressed)
					f = new File(DUMP_MAP_YAY0 + res.name);
				else
					f = new File(DUMP_MAP_RAW + res.name);

				if (!f.exists())
					throw new RuntimeException("Could not find default map asset: " + f);
			}

			Logger.log("Writing resource: " + f.getName());
			res.source = f;
			resourceList.add(res);
			tableSize += 0x1C;
		}

		int entryWritePosition = 0x1E40020 + tableSize;
		int spaceLeft = 0x27FEE22 - entryWritePosition;
		int numWritten = 0;

		while (!resourceList.isEmpty()) {
			Resource res = resourceList.pollFirst();

			// check if its compressed
			ResourceType type = Resource.resolveType(res.name);
			if (type != ResourceType.UNKNOWN) {
				boolean shouldBeCompressed = (type != ResourceType.TEX);
				if (res.compressed && !shouldBeCompressed)
					throw new RuntimeException("Resource " + res.name + " of type " + type + " is not expected to be compressed!");
				if (!res.compressed && shouldBeCompressed)
					throw new RuntimeException("Resource " + res.name + " of type " + type + " is expected to be compressed!");
			}

			int compressedLength = (int) res.source.length();
			int decompressedLength = compressedLength;

			// read file header
			RandomAccessFile fraf = new RandomAccessFile(res.source, "r");
			boolean isFileYay0 = (fraf.readInt() == 0x59617930); // 'Yay0'
			if (isFileYay0)
				decompressedLength = fraf.readInt();
			fraf.close();

			// ensure file header matches res.compressed
			if (res.compressed && !isFileYay0)
				throw new RuntimeException("Resource " + res.name + " must be compressed!");
			if (!res.compressed && isFileYay0)
				throw new RuntimeException("Resource " + res.name + " should not be compressed!");

			// determine where to write the resource
			int writeOffset;
			if (compressedLength > spaceLeft) {
				writeOffset = rp.nextAlignedOffset();
			}
			else {
				writeOffset = entryWritePosition;
				entryWritePosition += compressedLength;
				spaceLeft -= compressedLength;
			}

			// write the resource
			rp.seek(res.name, writeOffset);
			rp.write(FileUtils.readFileToByteArray(res.source));

			// write the table entry
			rp.seek("Map Assets", 0x1E40020 + 0x1C * numWritten);
			rp.write(res.name.getBytes());
			for (int i = res.name.length(); i < 16; i++)
				rp.writeByte(0);
			rp.writeInt(writeOffset - 0x1E40020);
			rp.writeInt(compressedLength);
			rp.writeInt(decompressedLength);
			numWritten++;
		}

		if (spaceLeft > 0) {
			rp.clear(entryWritePosition, 0x27FEE22);
			patcher.addEmptyRegion(new Region(entryWritePosition, 0x27FEE22));
		}

		// finish the table
		rp.seek("Map Assets", 0x1E40020 + 0x1C * numWritten);
		rp.write("end_data".getBytes());
		for (int i = "end_data".length(); i < 16; i++)
			rp.writeByte(0);
		rp.writeInt(0);
		rp.writeInt(0);
		rp.writeInt(0);
	}
}
