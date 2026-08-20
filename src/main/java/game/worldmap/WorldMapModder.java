package game.worldmap;

import static app.Directories.*;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.w3c.dom.Element;

import app.Environment;
import app.input.InputFileException;
import game.shared.ProjectDatabase;
import patcher.RomPatcher;
import util.Logger;
import util.xml.XmlKey;
import util.xml.XmlWrapper.XmlReader;
import util.xml.XmlWrapper.XmlTag;
import util.xml.XmlWrapper.XmlWriter;

public class WorldMapModder
{
	public static final int MAP_SIZE = 320;

	private enum Key implements XmlKey
	{
		// @formatter:off
		TAG_ROOT		("WorldMap"),
		TAG_LOCATION	("Location"),
		ATTR_ID			("id"),
		ATTR_PARENT		("parent"),
		ATTR_POS_X		("posX"),
		ATTR_POS_Y		("posY"),
		ATTR_UPDATE		("update"),
		ATTR_PATH		("path");
		// @formatter:on

		private final String key;

		Key(String key)
		{
			this.key = key;
		}

		@Override
		public String toString()
		{
			return key;
		}
	}

	public static void main(String args[]) throws IOException
	{
		Environment.initialize();
		dump();
		Environment.exit();
	}

	public static void dump() throws IOException
	{
		Logger.log("Dumping world map to XML file...");
		List<WorldLocation> locations = readROM();
		writeXML(locations, new File(DUMP_GLOBALS + FN_WORLD_MAP));
	}

	public static void patch(RomPatcher rp) throws IOException
	{
		List<WorldLocation> locations = readXML(new File(MOD_GLOBALS + FN_WORLD_MAP));
		writeROM(locations, rp);
	}

	public static class WorldMarker
	{
		public boolean mouseOver;

		protected int x, y;
		public float dragX, dragY;

		public WorldMarker(int x, int y)
		{
			this.x = x;
			this.y = y;
		}

		public double getDistTo(float posX, float posY)
		{
			float dX = x - posX;
			float dY = y - posY;
			return Math.sqrt(dX * dX + dY * dY);
		}

		public int getX()
		{
			return x + Math.round(dragX);
		}

		public int getY()
		{
			return y + Math.round(dragY);
		}
	}

	public static final class WorldLocation extends WorldMarker
	{
		public transient WorldLocation parent;
		public transient String locationName;

		private int id, parentID;
		public byte descUpdate;

		public ArrayList<WorldPathElement> path = new ArrayList<>();
		public transient int _pathLength;
		public transient int _ptrPath;

		public WorldLocation(int x, int y)
		{
			super(x, y);
		}
	}

	public static final class WorldPathElement extends WorldMarker
	{
		public final WorldLocation owner;

		public WorldPathElement(WorldLocation owner, int x, int y)
		{
			super(x, y);
			this.owner = owner;
		}
	}

	private static List<WorldLocation> readROM() throws IOException
	{
		ByteBuffer bb = Environment.getBaseRomBuffer();
		bb.position(0x1435F8);

		List<WorldLocation> locations = new ArrayList<>();

		for (int i = 0; i < 0x22; i++) {
			int x = bb.getShort();
			int y = MAP_SIZE - bb.getShort();

			WorldLocation loc = new WorldLocation(x, y);
			locations.add(loc);

			loc.parentID = bb.get();
			loc._pathLength = bb.get();
			bb.getShort(); // always zero
			loc._ptrPath = bb.getInt();
			loc.descUpdate = (byte) bb.getInt();
			loc.id = bb.getInt();
		}

		// convert parent index to parent ID
		for (WorldLocation loc : locations)
			loc.parentID = locations.get(loc.parentID).id;

		// read paths
		for (WorldLocation loc : locations) {
			bb.position(loc._ptrPath - 0x8010CCC0);
			loc.path.clear();
			int x = loc.x;
			int y = loc.y;
			for (int j = 0; j < loc._pathLength; j++) {
				x += bb.get();
				y -= bb.get();
				loc.path.add(new WorldPathElement(loc, x, y));
			}
		}

		return locations;
	}

	private static void writeROM(List<WorldLocation> locations, RomPatcher rp) throws IOException
	{
		if (locations.size() > 0x22)
			throw new RuntimeException("Error: tried to write more than 34 locations for world map.");

		HashMap<Integer, Integer> indexLookup = new HashMap<>();

		int i = 0;
		for (WorldLocation loc : locations)
			indexLookup.put(loc.id, i++);

		// convert from parent ID to index
		for (WorldLocation loc : locations) {
			if (!indexLookup.containsKey(loc.parentID))
				throw new RuntimeException("Location parent ID could not be found.");
			loc.parentID = indexLookup.get(loc.parentID);
		}

		// write paths
		i = 0;
		for (WorldLocation loc : locations) {
			loc._ptrPath = 0x8024FA38 + i;
			rp.seek("World Map Paths", 0x142D78 + i);
			i += 0x40;

			if (loc.path.size() > 0x20)
				throw new RuntimeException("Error: location path length exceeds limit: (" + loc.path.size() + " / 32)");

			int lastX = loc.x;
			int lastY = loc.y;
			for (WorldPathElement marker : loc.path) {
				rp.writeByte(marker.x - lastX);
				rp.writeByte(-(marker.y - lastY));
				lastX = marker.x;
				lastY = marker.y;
			}
		}

		// write locations
		rp.seek("World Map Locations", 0x1435F8);
		for (WorldLocation loc : locations) {
			rp.writeShort(loc.x);
			rp.writeShort(MAP_SIZE - loc.y);
			rp.writeByte(loc.parentID);
			rp.writeByte(loc.path.size());
			rp.writeShort(0);
			rp.writeInt(loc._ptrPath);
			rp.writeInt(loc.descUpdate);
			rp.writeInt(loc.id);
		}
	}

	private static List<WorldLocation> readXML(File xmlFile) throws IOException
	{
		List<WorldLocation> locations = new ArrayList<>();

		try {
			XmlReader xmr = new XmlReader(xmlFile);
			Element rootElement = xmr.getRootElement();
			if (!rootElement.getTagName().equals(Key.TAG_ROOT.toString()))
				xmr.complain("World map XML must use a " + Key.TAG_ROOT + " root element.");

			List<Element> locationElements = xmr.getTags(rootElement, Key.TAG_LOCATION);
			if (locationElements.size() > 0x22)
				xmr.complain("Only 34 locations may be defined for the world map.");
			if (locationElements.isEmpty())
				xmr.complain("No locations defined for world map.");

			for (Element locationElement : locationElements) {
				xmr.requiresAttribute(locationElement, Key.ATTR_POS_X);
				xmr.requiresAttribute(locationElement, Key.ATTR_POS_Y);
				int x = xmr.readInt(locationElement, Key.ATTR_POS_X);
				int y = xmr.readInt(locationElement, Key.ATTR_POS_Y);

				WorldLocation loc = new WorldLocation(x, y);
				locations.add(loc);

				xmr.requiresAttribute(locationElement, Key.ATTR_ID);
				xmr.requiresAttribute(locationElement, Key.ATTR_PARENT);
				xmr.requiresAttribute(locationElement, Key.ATTR_UPDATE);
				loc.id = xmr.readHex(locationElement, Key.ATTR_ID);
				loc.parentID = xmr.readHex(locationElement, Key.ATTR_PARENT);
				loc.descUpdate = (byte) xmr.readHex(locationElement, Key.ATTR_UPDATE);

				if (!locationElement.hasAttribute(Key.ATTR_PATH.toString()))
					xmr.complain(Key.TAG_LOCATION + " is missing required attribute: " + Key.ATTR_PATH);
				String path = xmr.getAttribute(locationElement, Key.ATTR_PATH).replaceAll("\\s+", "");
				if (!path.isEmpty()) {
					String[] points = path.split(";");
					if (points.length > 0x20)
						xmr.complain("Path length exceeds limit: (" + points.length + " / 32)");

					int curX = loc.x;
					int curY = loc.y;
					for (int j = 0; j < points.length; j++) {
						String[] coords = points[j].split(",");
						if (coords.length != 2)
							xmr.complain("Path has invalid coordinate: " + points[j]);

						int deltaX = 0;
						int deltaY = 0;
						try {
							deltaX = Integer.parseInt(coords[0]);
							deltaY = Integer.parseInt(coords[1]);
						}
						catch (NumberFormatException e) {
							xmr.complain("Path has invalid coordinate: " + points[j]);
						}

						curX += (byte) deltaX;
						curY += (byte) deltaY;
						loc.path.add(new WorldPathElement(loc, curX, curY));
					}
				}
			}
		}
		catch (InputFileException e) {
			throw new IOException("Could not read world map XML: " + xmlFile, e);
		}

		return locations;
	}

	private static void writeXML(List<WorldLocation> locations, File xmlFile) throws IOException
	{
		try (XmlWriter xmw = new XmlWriter(xmlFile)) {
			XmlTag rootTag = xmw.createTag(Key.TAG_ROOT, false);
			xmw.openTag(rootTag);

			for (WorldLocation loc : locations) {
				XmlTag locationTag = xmw.createTag(Key.TAG_LOCATION, true);
				xmw.addHex(locationTag, Key.ATTR_ID, "%02X", loc.id);
				xmw.addHex(locationTag, Key.ATTR_PARENT, "%02X", loc.parentID);
				xmw.addInt(locationTag, Key.ATTR_POS_X, loc.x);
				xmw.addInt(locationTag, Key.ATTR_POS_Y, loc.y);
				xmw.addHex(locationTag, Key.ATTR_UPDATE, "%02X", loc.descUpdate & 0xFF);

				int lastX = loc.x;
				int lastY = loc.y;
				StringBuilder sb = new StringBuilder();
				for (int j = 0; j < loc.path.size(); j++) {
					WorldPathElement marker = loc.path.get(j);
					sb.append(String.format("%d,%d", marker.x - lastX, marker.y - lastY));
					lastX = marker.x;
					lastY = marker.y;

					if (j < loc.path.size() - 1)
						sb.append(";");
				}

				xmw.addAttribute(locationTag, Key.ATTR_PATH, sb.toString());
				xmw.printTag(locationTag);
			}

			xmw.closeTag(rootTag);
			xmw.save();
		}
	}

	public static List<WorldLocation> loadLocations() throws IOException
	{
		List<WorldLocation> locations = readXML(new File(MOD_GLOBALS + FN_WORLD_MAP));

		for (WorldLocation loc : locations) {
			loc.locationName = ProjectDatabase.LocationType.getName(loc.id);

			for (WorldLocation otherLoc : locations) {
				if (otherLoc.id == loc.parentID)
					loc.parent = otherLoc;
			}
		}

		return locations;
	}

	public static void saveLocations(List<WorldLocation> locations) throws IOException
	{
		for (WorldLocation loc : locations) {
			loc.id = ProjectDatabase.LocationType.getID(loc.locationName);

			if (loc.parent == null)
				loc.parentID = 0;
			else
				loc.parentID = loc.parent.id;
		}

		writeXML(locations, new File(MOD_GLOBALS + FN_WORLD_MAP));
	}
}
