package game.worldmap;

import static app.Directories.*;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import app.Environment;
import app.StarRodClassic;
import app.input.InputFileException;
import game.shared.ProjectDatabase;
import patcher.RomPatcher;
import util.Logger;

public class WorldMapModder
{
	public static final int MAP_SIZE = 320;

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
			DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
			DocumentBuilder builder = factory.newDocumentBuilder();
			Document document = builder.parse(xmlFile);
			document.getDocumentElement().normalize();

			NodeList nodes = document.getElementsByTagName("Location");
			if (nodes.getLength() > 0x22)
				throw new InputFileException(xmlFile, "Only 34 locations may be defined for the world map.");
			if (nodes.getLength() < 1)
				throw new InputFileException(xmlFile, "No locations defined for world map.");

			for (int i = 0; i < nodes.getLength(); i++) {
				Element locationElement = (Element) nodes.item(i);

				if (!locationElement.hasAttribute("posX"))
					throw new InputFileException(xmlFile, "Location is missing attribute: posX.");
				int x = Integer.parseInt(locationElement.getAttribute("posX"));

				if (!locationElement.hasAttribute("posY"))
					throw new InputFileException(xmlFile, "Location is missing attribute: posY.");
				int y = Integer.parseInt(locationElement.getAttribute("posY"));

				WorldLocation loc = new WorldLocation(x, y);
				locations.add(loc);

				if (!locationElement.hasAttribute("id"))
					throw new InputFileException(xmlFile, "Location is missing attribute: id.");
				loc.id = Integer.parseInt(locationElement.getAttribute("id"), 16);

				if (!locationElement.hasAttribute("parent"))
					throw new InputFileException(xmlFile, "Location is missing attribute: parent.");
				loc.parentID = Integer.parseInt(locationElement.getAttribute("parent"), 16);

				if (!locationElement.hasAttribute("update"))
					throw new InputFileException(xmlFile, "Location is missing attribute: update.");
				loc.descUpdate = (byte) Integer.parseInt(locationElement.getAttribute("update"), 16);

				if (!locationElement.hasAttribute("path"))
					throw new InputFileException(xmlFile, "Location is missing attribute: path.");
				String path = locationElement.getAttribute("path").replaceAll("//s+", "");
				if (!path.isEmpty()) {
					String[] points = path.split(";");
					if (points.length > 0x20)
						throw new InputFileException(xmlFile, "Path length exceeds limit: (" + points.length + " / 32)");

					int curX = loc.x;
					int curY = loc.y;
					for (int j = 0; j < points.length; j++) {
						String[] coords = points[j].split(",");
						if (coords.length != 2)
							throw new InputFileException(xmlFile, "Path has invalid coordinate: " + points[j]);

						curX += (byte) Integer.parseInt(coords[0]);
						curY += (byte) Integer.parseInt(coords[1]);
						loc.path.add(new WorldPathElement(loc, curX, curY));
					}
				}
			}
		}
		catch (ParserConfigurationException e) {
			e.printStackTrace();
			StarRodClassic.displayStackTrace(e);
		}
		catch (SAXException e) {
			e.printStackTrace();
			StarRodClassic.displayStackTrace(e);
		}

		return locations;
	}

	private static void writeXML(List<WorldLocation> locations, File xmlFile)
	{
		try {
			DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
			DocumentBuilder builder = factory.newDocumentBuilder();
			Document doc = builder.newDocument();

			Element rootElement = doc.createElement("WorldMap");
			doc.appendChild(rootElement);

			for (WorldLocation loc : locations) {
				Element locationElement = doc.createElement("Location");
				locationElement.setAttribute("id", String.format("%02X", loc.id));
				locationElement.setAttribute("parent", String.format("%02X", loc.parentID));
				locationElement.setAttribute("posX", String.format("%d", loc.x));
				locationElement.setAttribute("posY", String.format("%d", loc.y));
				locationElement.setAttribute("update", String.format("%02X", loc.descUpdate));

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

				locationElement.setAttribute("path", sb.toString());
				rootElement.appendChild(locationElement);
			}

			TransformerFactory transformerFactory = TransformerFactory.newInstance();
			Transformer transformer = transformerFactory.newTransformer();
			transformer.setOutputProperty(OutputKeys.INDENT, "yes");
			transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");

			DOMSource source = new DOMSource(doc);
			StreamResult dest = new StreamResult(xmlFile);

			transformer.transform(source, dest);

		}
		catch (ParserConfigurationException e) {
			e.printStackTrace();
			StarRodClassic.displayStackTrace(e);
		}
		catch (TransformerException e) {
			e.printStackTrace();
			StarRodClassic.displayStackTrace(e);
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

	public static void saveLocations(List<WorldLocation> locations)
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
