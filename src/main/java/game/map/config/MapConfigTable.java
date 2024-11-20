package game.map.config;

import static game.map.config.MapConfigKey.*;
import static game.map.config.MapConfigTable.Resource.ResourceType.*;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;

import org.w3c.dom.Element;

import app.input.IOUtils;
import game.MemoryRegion;
import game.ROM;
import game.ROM.EOffset;
import game.ROM.EngineComponent;
import game.map.config.MapConfigTable.Resource.ResourceType;
import util.CountingMap;
import util.Pair;
import util.xml.XmlWrapper.XmlReader;
import util.xml.XmlWrapper.XmlTag;
import util.xml.XmlWrapper.XmlWriter;

public class MapConfigTable
{
	public List<AreaConfig> areas = new ArrayList<>(1200);
	public List<Resource> resources = new ArrayList<>(200);

	public transient List<Resource> allResources = new ArrayList<>(1500);

	public transient File source;
	public transient int configTableAddr;

	public static final class AreaConfig
	{
		public String name;
		public List<MapConfig> maps;
		public List<MapConfig> stages;

		public String nickname = "";
		public String desc = "";

		public transient int ptrConfigTableEntry;

		// level editor
		public transient DefaultMutableTreeNode mapsTreeNode;
		public transient DefaultMutableTreeNode stagesTreeNode;
		public transient boolean invalidName;
		public transient int areaID = -1;

		public AreaConfig(String name)
		{
			this.name = name;
			maps = new ArrayList<>();
			stages = new ArrayList<>();
		}

		@Override
		public String toString()
		{
			return name;
		}
	}

	public static final class MapConfig
	{
		// only valid in the editor, when read from XML files
		public final boolean isStage;
		public transient int areaID = -1;
		public transient int mapID = -1;

		public String name;

		// optional attributes
		public int flags = 0;
		public String bgName = "";
		public String nickname = "";
		public String desc = "";

		public boolean hasData;
		public boolean hasShape;
		public boolean hasHit;

		// for patching
		public transient int configTableOffset;
		public transient boolean oldExists = false;
		public transient int startOffset = 0xABABABAB;
		public transient int endOffset = 0xABABABAB;

		// for dumping
		public transient int dataStartOffset;
		public transient int dataEndOffset;
		public transient int ptrHeader;
		public transient int ptrInitFunction;
		public transient int shapeOffset;
		public transient int shapeLength;
		public transient int hitOffset;
		public transient int hitLength;
		public transient int unknownPointers = 0;
		public transient int missingSections = 0;

		// level editor
		public transient DefaultMutableTreeNode treeNode;
		public transient BufferedImage thumbnail;
		public boolean invalidName;

		public MapConfig(String name, boolean isStage)
		{
			this.name = name;
			this.isStage = isStage;
		}

		public MapConfig(RandomAccessFile raf, int ptrToOffset) throws IOException
		{
			int mapNameAddr = raf.readInt();
			ptrHeader = raf.readInt();
			dataStartOffset = raf.readInt();
			dataEndOffset = raf.readInt();
			raf.skipBytes(4); // always 80240000
			int bgNameAddr = raf.readInt();
			ptrInitFunction = raf.readInt();
			flags = raf.readInt();

			raf.seek(mapNameAddr - ptrToOffset);
			name = IOUtils.readString(raf, 0x8);

			if (bgNameAddr != 0) {
				raf.seek(bgNameAddr - ptrToOffset);
				bgName = IOUtils.readString(raf, 0x8);
			}

			hasData = true;
			isStage = false;
		}

		@Override
		public String toString()
		{
			return name;
		}
	}

	public static final class Resource
	{
		public String nickname = "";
		public String name = "";
		public boolean compressed;

		// dumping
		public transient int offset;
		public transient int length;

		// patching
		public transient File source;

		// level editor
		public DefaultMutableTreeNode treeNode;
		public boolean invalidName;

		public Resource(String name, boolean compressed)
		{
			this.name = name;
			this.compressed = compressed;
		}

		public static enum ResourceType
		{
			HIT, SHAPE, TEX, BG, TITLE, PARTY, UNKNOWN
		}

		public static ResourceType resolveType(String name)
		{
			if (name.endsWith("_shape"))
				return SHAPE;
			if (name.endsWith("_hit"))
				return HIT;
			if (name.endsWith("_tex"))
				return TEX;
			if (name.endsWith("_bg"))
				return BG;
			if (name.startsWith("title_"))
				return TITLE;
			if (name.startsWith("party_"))
				return PARTY;

			return UNKNOWN;
		}

		@Override
		public String toString()
		{
			return name;
		}
	}

	public static MapConfigTable read(ROM rom, RandomAccessFile raf) throws IOException
	{
		MapConfigTable table = new MapConfigTable();
		int configTableBase = rom.getOffset(EOffset.MAP_CONFIG_TABLE);
		int assetTableBase = rom.getOffset(EOffset.MAP_ASSET_TABLE);
		table.readTables(rom, configTableBase, assetTableBase, raf);
		return table;
	}

	public static MapConfigTable read(ROM rom, int cfgOffset, int assetsOffset, RandomAccessFile raf) throws IOException
	{
		MapConfigTable table = new MapConfigTable();
		table.readTables(rom, cfgOffset, assetsOffset, raf);
		return table;
	}

	private void readTables(ROM rom, int cfgOffset, int assetsOffset, RandomAccessFile raf) throws IOException
	{
		MemoryRegion reg = rom.getMemoryRegion(EngineComponent.SYSTEM);

		HashMap<String, MapConfig> mapLookup = new HashMap<>(1200);
		int ptrToOffset = reg.startAddr - reg.startOffset; // 0x80025C00 - 0x1000 = 0x80024C00 -- should be same on all versions!

		// config table
		for (int i = 0; i < 28; i++) {
			raf.seek(cfgOffset + (i * 0x10));

			int numMaps = raf.readInt();
			int mapTableOffset = raf.readInt() - ptrToOffset;
			int areaNameOffset = raf.readInt() - ptrToOffset;

			raf.seek(areaNameOffset + 5); // skip "area_"
			String areaName = IOUtils.readString(raf, 16);

			AreaConfig area = new AreaConfig(areaName);

			for (int j = 0; j < numMaps; j++) {
				raf.seek(mapTableOffset + (j * 0x20));

				MapConfig map = new MapConfig(raf, ptrToOffset);

				if (mapLookup.containsKey(map.name)) {
					// kkj_26 is followed by a duplicate entry at 6D110
					assert (map.name.equals("kkj_26"));
					// ignore the duplicate
					continue;
				}
				mapLookup.put(map.name, map);
				area.maps.add(map);
			}

			areas.add(area);
		}

		// asset table
		int tableOffset = assetsOffset + 0x20;
		while (true) {
			raf.seek(tableOffset);
			tableOffset += 0x1C;

			String name = IOUtils.readString(raf, 0x10);

			int offset = raf.readInt() + assetsOffset + 0x20;
			int compressedLength = raf.readInt();
			raf.skipBytes(4); // uncompressed length

			if (name.equals("end_data"))
				break;

			ResourceType type = Resource.resolveType(name);
			boolean compressed = (type != TEX);
			Resource res = new Resource(name, compressed);
			res.offset = offset;
			res.length = compressedLength;
			allResources.add(res);

			if (type != SHAPE && type != HIT) {
				resources.add(res);
				continue;
			}

			/*
			if(compressed)
			{
				raf.seek(offset);
				assert(raf.readInt() == 0x59617930); // "Yay0"
			}
			 */

			name = name.substring(0, name.lastIndexOf("_"));
			MapConfig map = mapLookup.get(name);
			if (map == null) {
				assert (name.contains("bt") || name.equals("arn_20") || name.equals("kpa_80")) : name;

				map = new MapConfig(name, false);
				mapLookup.put(name, map);
				map.hasData = false;

				String areaName = name.substring(0, 3);
				AreaConfig area = null;
				for (AreaConfig ar : areas) {
					if (ar.name.startsWith(areaName))
						area = ar;
				}
				if (area == null)
					throw new RuntimeException("Unable to assign area to map: " + name);

				if (name.contains("bt"))
					area.stages.add(map);
				else
					area.maps.add(map);
			}

			if (type == SHAPE) {
				map.hasShape = true;
				map.shapeOffset = offset;
				map.shapeLength = compressedLength;
			}

			if (type == HIT) {
				map.hasHit = true;
				map.hitOffset = offset;
				map.hitLength = compressedLength;
			}
		}
	}

	public static MapConfigTable readXML(File xmlFile)
	{
		XmlReader xmr = new XmlReader(xmlFile);
		MapConfigTable table = new MapConfigTable();
		table.readXML(xmr, xmr.getRootElement());
		table.source = xmlFile;
		return table;
	}

	private void readXML(XmlReader xmr, Element tableElem)
	{
		for (Element areaElem : xmr.getTags(tableElem, TAG_AREA)) {
			xmr.requiresAttribute(areaElem, ATTR_NAME);
			String areaName = xmr.getAttribute(areaElem, ATTR_NAME);

			AreaConfig area = new AreaConfig(areaName);

			if (xmr.hasAttribute(areaElem, ATTR_NICKNAME))
				area.nickname = xmr.getAttribute(areaElem, ATTR_NICKNAME);

			if (xmr.hasAttribute(areaElem, ATTR_DESC))
				area.desc = xmr.getAttribute(areaElem, ATTR_DESC);

			for (Element mapElem : xmr.getTags(areaElem, TAG_MAP)) {
				MapConfig map = readMap(xmr, mapElem);
				area.maps.add(map);
			}

			for (Element mapElem : xmr.getTags(areaElem, TAG_STAGE)) {
				MapConfig map = readStage(xmr, mapElem);
				area.stages.add(map);
			}

			areas.add(area);
		}

		for (Element resElem : xmr.getTags(tableElem, TAG_RESOURCE)) {
			xmr.requiresAttribute(resElem, ATTR_NAME);
			String resName = xmr.getAttribute(resElem, ATTR_NAME);

			xmr.requiresAttribute(resElem, ATTR_COMPRESSED);
			boolean compressed = xmr.readBoolean(resElem, ATTR_COMPRESSED);

			Resource res = new Resource(resName, compressed);
			resources.add(res);

			if (xmr.hasAttribute(resElem, ATTR_NICKNAME))
				res.nickname = xmr.getAttribute(resElem, ATTR_NICKNAME);
		}
	}

	public void writeXML(File f)
	{
		try (XmlWriter xmw = new XmlWriter(f)) {
			XmlTag root = xmw.createTag(TAG_ROOT, false);
			xmw.openTag(root);

			for (AreaConfig area : areas) {
				XmlTag areaTag = xmw.createTag(TAG_AREA, false);
				xmw.addAttribute(areaTag, ATTR_NAME, area.name);

				if (!area.nickname.isEmpty())
					xmw.addAttribute(areaTag, ATTR_NICKNAME, area.nickname);

				if (!area.desc.isEmpty())
					xmw.addAttribute(areaTag, ATTR_DESC, area.desc);

				xmw.openTag(areaTag);

				for (MapConfig map : area.maps) {
					XmlTag mapTag = xmw.createTag(TAG_MAP, true);
					writeMap(xmw, mapTag, map);
					xmw.printTag(mapTag);
				}

				for (MapConfig map : area.stages) {
					XmlTag mapTag = xmw.createTag(TAG_STAGE, true);
					writeStage(xmw, mapTag, map);
					xmw.printTag(mapTag);
				}

				xmw.closeTag(areaTag);
			}

			for (Resource res : resources) {
				XmlTag resTag = xmw.createTag(TAG_RESOURCE, true);
				xmw.addAttribute(resTag, ATTR_NAME, res.name);
				if (!res.nickname.isEmpty())
					xmw.addAttribute(resTag, ATTR_NICKNAME, res.nickname);
				xmw.addBoolean(resTag, ATTR_COMPRESSED, res.compressed);
				xmw.printTag(resTag);
			}

			xmw.closeTag(root);
			xmw.save();
		}
		catch (FileNotFoundException e) {
			// throw it back up to the main menu
			throw new RuntimeException(e);
		}
	}

	private static MapConfig readMap(XmlReader xmr, Element mapElem)
	{
		xmr.requiresAttribute(mapElem, ATTR_NAME);
		String mapName = xmr.getAttribute(mapElem, ATTR_NAME);

		MapConfig map = new MapConfig(mapName, false);

		if (xmr.hasAttribute(mapElem, ATTR_MAP_FLAGS))
			map.flags = xmr.readHex(mapElem, ATTR_MAP_FLAGS);

		if (xmr.hasAttribute(mapElem, ATTR_NICKNAME))
			map.nickname = xmr.getAttribute(mapElem, ATTR_NICKNAME);

		if (xmr.hasAttribute(mapElem, ATTR_DESC))
			map.desc = xmr.getAttribute(mapElem, ATTR_DESC);

		if (xmr.hasAttribute(mapElem, ATTR_MAP_DATA))
			map.hasData = xmr.readBoolean(mapElem, ATTR_MAP_DATA);
		else
			map.hasData = true;

		if (xmr.hasAttribute(mapElem, ATTR_MAP_SHAPE))
			map.hasShape = xmr.readBoolean(mapElem, ATTR_MAP_SHAPE);
		else
			map.hasShape = true;

		if (xmr.hasAttribute(mapElem, ATTR_MAP_HIT))
			map.hasHit = xmr.readBoolean(mapElem, ATTR_MAP_HIT);
		else
			map.hasHit = true;

		return map;
	}

	private static void writeMap(XmlWriter xmw, XmlTag mapTag, MapConfig map)
	{
		xmw.addAttribute(mapTag, ATTR_NAME, map.name);

		if (!map.nickname.isEmpty())
			xmw.addAttribute(mapTag, ATTR_NICKNAME, map.nickname);

		if (!map.desc.isEmpty())
			xmw.addAttribute(mapTag, ATTR_DESC, map.desc);

		if (map.flags != 0)
			xmw.addHex(mapTag, ATTR_MAP_FLAGS, map.flags);

		if (!map.hasData)
			xmw.addBoolean(mapTag, ATTR_MAP_DATA, false);

		if (!map.hasShape)
			xmw.addBoolean(mapTag, ATTR_MAP_SHAPE, false);

		if (!map.hasHit)
			xmw.addBoolean(mapTag, ATTR_MAP_HIT, false);
	}

	private static MapConfig readStage(XmlReader xmr, Element mapElem)
	{
		xmr.requiresAttribute(mapElem, ATTR_NAME);
		String mapName = xmr.getAttribute(mapElem, ATTR_NAME);

		MapConfig stage = new MapConfig(mapName, true);

		if (xmr.hasAttribute(mapElem, ATTR_NICKNAME))
			stage.nickname = xmr.getAttribute(mapElem, ATTR_NICKNAME);

		if (xmr.hasAttribute(mapElem, ATTR_DESC))
			stage.desc = xmr.getAttribute(mapElem, ATTR_DESC);

		if (xmr.hasAttribute(mapElem, ATTR_MAP_SHAPE))
			stage.hasShape = xmr.readBoolean(mapElem, ATTR_MAP_SHAPE);
		else
			stage.hasShape = true;

		if (xmr.hasAttribute(mapElem, ATTR_MAP_HIT))
			stage.hasHit = xmr.readBoolean(mapElem, ATTR_MAP_HIT);
		else
			stage.hasHit = true;

		return stage;
	}

	private static void writeStage(XmlWriter xmw, XmlTag mapTag, MapConfig map)
	{
		xmw.addAttribute(mapTag, ATTR_NAME, map.name);

		if (!map.nickname.isEmpty())
			xmw.addAttribute(mapTag, ATTR_NICKNAME, map.nickname);

		if (!map.desc.isEmpty())
			xmw.addAttribute(mapTag, ATTR_DESC, map.desc);

		if (!map.hasShape)
			xmw.addBoolean(mapTag, ATTR_MAP_SHAPE, false);

		if (!map.hasHit)
			xmw.addBoolean(mapTag, ATTR_MAP_HIT, false);
	}

	public void calculateRequiredResources()
	{
		allResources.clear();

		for (AreaConfig area : areas) {
			for (MapConfig map : area.maps) {
				if (map.hasShape)
					allResources.add(new Resource(map.name + "_shape", true));

				if (map.hasHit)
					allResources.add(new Resource(map.name + "_hit", true));
			}

			for (MapConfig map : area.stages) {
				if (map.hasShape)
					allResources.add(new Resource(map.name + "_shape", true));

				if (map.hasHit)
					allResources.add(new Resource(map.name + "_hit", true));
			}
		}

		allResources.addAll(resources);
	}

	// =============================================================================

	public DefaultTreeModel mapsModel = null;
	public DefaultTreeModel stagesModel = null;
	public DefaultTreeModel resourcesModel = null;

	public void createTreeModels()
	{
		mapsModel = getMapTreeModel();
		stagesModel = getStageTreeModel();
		resourcesModel = getResourceTreeModel();
	}

	/*
	public void recalculateMapIDs()
	{
		DefaultMutableTreeNode root = (DefaultMutableTreeNode)mapsModel.getRoot();
		for(int i = 0; i < root.getChildCount(); i++)
		{
			DefaultMutableTreeNode areaNode = (DefaultMutableTreeNode)root.getChildAt(i);
			AreaConfig area = (AreaConfig)areaNode.getUserObject();
			area.areaID = i;

			int mapID = 0;
			for(int j = 0; j < areaNode.getChildCount(); j++)
			{
				DefaultMutableTreeNode mapNode = (DefaultMutableTreeNode)areaNode.getChildAt(j);
				MapConfig map = (MapConfig)mapNode.getUserObject();
				map.areaID = i;
				map.mapID = (map.hasData) ? mapID++ : -1;
			}
		}
	}
	 */

	private DefaultTreeModel getMapTreeModel()
	{
		DefaultMutableTreeNode root = new DefaultMutableTreeNode("Maps");
		for (AreaConfig area : areas) {
			DefaultMutableTreeNode areaNode = new DefaultMutableTreeNode(area);
			area.mapsTreeNode = areaNode;
			root.add(areaNode);

			for (MapConfig map : area.maps) {
				DefaultMutableTreeNode mapNode = new DefaultMutableTreeNode(map);
				map.treeNode = mapNode;
				areaNode.add(mapNode);
			}
		}

		return new DefaultTreeModel(root);
	}

	private DefaultTreeModel getStageTreeModel()
	{
		DefaultMutableTreeNode root = new DefaultMutableTreeNode("Stages");
		for (AreaConfig area : areas) {
			DefaultMutableTreeNode areaNode = new DefaultMutableTreeNode(area);
			area.stagesTreeNode = areaNode;
			root.add(areaNode);

			for (MapConfig stage : area.stages) {
				DefaultMutableTreeNode stageNode = new DefaultMutableTreeNode(stage);
				stage.treeNode = stageNode;
				areaNode.add(stageNode);
			}
		}

		return new DefaultTreeModel(root);
	}

	private DefaultTreeModel getResourceTreeModel()
	{
		DefaultMutableTreeNode root = new DefaultMutableTreeNode("Resources");

		for (Resource res : resources) {
			DefaultMutableTreeNode resNode = new DefaultMutableTreeNode(res);
			res.treeNode = resNode;
			root.add(resNode);
		}

		return new DefaultTreeModel(root);
	}

	/**
	 * Use this to add areas from the level editor
	 */
	public void addArea(AreaConfig area)
	{
		DefaultMutableTreeNode areaMapNode = new DefaultMutableTreeNode(area);
		DefaultMutableTreeNode areaStagesNode = new DefaultMutableTreeNode(area);
		area.mapsTreeNode = areaMapNode;
		area.stagesTreeNode = areaStagesNode;

		DefaultMutableTreeNode rootMaps = (DefaultMutableTreeNode) mapsModel.getRoot();
		DefaultMutableTreeNode rootStages = (DefaultMutableTreeNode) stagesModel.getRoot();

		mapsModel.insertNodeInto(areaMapNode, rootMaps, rootMaps.getChildCount());
		stagesModel.insertNodeInto(areaStagesNode, rootStages, rootStages.getChildCount());
		areas.add(area);
		validateNames();
	}

	public void removeArea(AreaConfig area)
	{
		mapsModel.removeNodeFromParent(area.mapsTreeNode);
		stagesModel.removeNodeFromParent(area.stagesTreeNode);
		areas.remove(area);
		validateNames();
	}

	/**
	 * Use this to add maps from the level editor
	 */
	public void addMap(AreaConfig area, MapConfig map)
	{
		DefaultMutableTreeNode mapNode = new DefaultMutableTreeNode(map);
		map.treeNode = mapNode;

		mapsModel.insertNodeInto(mapNode, area.mapsTreeNode, area.mapsTreeNode.getChildCount());
		area.maps.add(map);
		validateNames();
	}

	public void removeMap(MapConfig map)
	{
		DefaultMutableTreeNode areaNode = (DefaultMutableTreeNode) map.treeNode.getParent();
		AreaConfig area = (AreaConfig) areaNode.getUserObject();

		mapsModel.removeNodeFromParent(map.treeNode);
		area.maps.remove(map);
		validateNames();
	}

	public void addStage(AreaConfig area, MapConfig stage)
	{
		DefaultMutableTreeNode stageNode = new DefaultMutableTreeNode(stage);
		stage.treeNode = stageNode;

		stagesModel.insertNodeInto(stageNode, area.stagesTreeNode, area.stagesTreeNode.getChildCount());
		area.stages.add(stage);
		validateNames();
	}

	public void removeStage(MapConfig stage)
	{
		DefaultMutableTreeNode areaNode = (DefaultMutableTreeNode) stage.treeNode.getParent();
		AreaConfig area = (AreaConfig) areaNode.getUserObject();

		stagesModel.removeNodeFromParent(stage.treeNode);
		area.stages.remove(stage);
		validateNames();
	}

	public void addResource(Resource res)
	{
		DefaultMutableTreeNode resourcesRoot = (DefaultMutableTreeNode) resourcesModel.getRoot();
		DefaultMutableTreeNode resNode = new DefaultMutableTreeNode(res);
		res.treeNode = resNode;

		resourcesModel.insertNodeInto(resNode, resourcesRoot, resourcesRoot.getChildCount());
		resources.add(res);
		validateNames();
	}

	public void removeResource(Resource res)
	{
		resourcesModel.removeNodeFromParent(res.treeNode);
		resources.remove(res);
		validateNames();
	}

	public void validateNames()
	{
		CountingMap<String> areaNames = new CountingMap<>();
		CountingMap<String> mapNames = new CountingMap<>();
		CountingMap<String> stageNames = new CountingMap<>();
		CountingMap<String> resourceNames = new CountingMap<>();

		// initialize and gather names
		for (AreaConfig area : areas) {
			for (MapConfig map : area.maps) {
				map.invalidName = false;
				mapNames.add(map.name);
				resourceNames.add(map.name + "_shape");
				resourceNames.add(map.name + "_hit");
			}

			for (MapConfig stage : area.stages) {
				stage.invalidName = false;
				stageNames.add(stage.name);
				resourceNames.add(stage.name + "_shape");
				resourceNames.add(stage.name + "_hit");
			}

			area.invalidName = false;
			areaNames.add(area.name);
		}
		for (Resource res : resources) {
			res.invalidName = false;
			resourceNames.add(res.name);
		}

		// mark duplicates as invalid
		for (AreaConfig area : areas) {
			for (MapConfig map : area.maps) {
				if (mapNames.getCount(map.name) > 1)
					map.invalidName = true;
			}

			for (MapConfig stage : area.stages) {
				if (stageNames.getCount(stage.name) > 1)
					stage.invalidName = true;
			}

			if (areaNames.getCount(area.name) > 1)
				area.invalidName = true;
		}
		for (Resource res : resources) {
			if (resourceNames.getCount(res.name) > 1)
				res.invalidName = true;

			if (res.name.equals("end_data"))
				res.invalidName = true;
		}
	}

	public Pair<Integer> getAreaMapIDs(String name)
	{
		if (name == null || name.length() < 4)
			return null;

		String areaName = name.substring(0, 3);
		if (name.contains("_"))
			areaName = name.substring(0, name.indexOf("_"));

		AreaConfig areaCfg = null;
		int areaID = 0;
		for (AreaConfig area : areas) {
			if (area.name.equals(areaName)) {
				areaCfg = area;
				break;
			}
			areaID++;
		}
		if (areaCfg == null)
			return null;

		MapConfig mapCfg = null;
		int mapID = 0;
		for (MapConfig map : areaCfg.maps) {
			if (!map.hasData)
				continue; //XXX changed

			if (map.name.equals(name)) {
				mapCfg = map;
				break;
			}
			mapID++;
		}
		if (mapCfg == null)
			return null;

		return new Pair<>(areaID, mapID);
	}

	public AreaConfig getAreaForMap(String name)
	{
		if (name == null || name.length() < 4)
			return null;

		String areaName = name.substring(0, 3);
		if (name.contains("_"))
			areaName = name.substring(0, name.indexOf("_"));

		for (AreaConfig area : areas) {
			if (area.name.equals(areaName))
				return area;
		}

		return null;
	}

	public MapConfig getMap(String name)
	{
		if (name == null || name.length() < 4)
			return null;

		AreaConfig area = getAreaForMap(name);
		if (area == null)
			return null;

		for (MapConfig map : area.maps) {
			if (map.name.equals(name))
				return map;
		}
		for (MapConfig map : area.stages) {
			if (map.name.equals(name))
				return map;
		}
		return null;
	}
}
