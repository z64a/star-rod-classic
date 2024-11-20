package game.globals;

import static app.Directories.*;
import static game.globals.ItemRecordKey.TAG_ITEM;
import static game.globals.ItemRecordKey.TAG_ROOT;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

import org.w3c.dom.Element;

import app.Environment;
import app.Resource;
import app.Resource.ResourceType;
import app.StarRodException;
import app.input.IOUtils;
import app.input.InputFileException;
import app.input.InvalidInputException;
import game.ROM.EOffset;
import game.globals.editor.GlobalsData;
import game.shared.ProjectDatabase;
import game.texture.images.HudElementRecord;
import patcher.IGlobalDatabase;
import patcher.RomPatcher;
import util.Logger;
import util.Priority;
import util.xml.XmlWrapper.XmlReader;
import util.xml.XmlWrapper.XmlTag;
import util.xml.XmlWrapper.XmlWriter;

public class ItemModder
{
	public static final int NUM_ITEMS = 0x16D;
	public static final int NUM_HUD_ELEMS = 0x151;

	public static ArrayList<ItemRecord> dumpTable(ArrayList<MoveRecord> moves) throws IOException
	{
		Logger.log("Dumping item table.", Priority.MILESTONE);

		RandomAccessFile raf = Environment.getBaseRomReader();
		String[] itemNames = getItemNames();
		String[] itemEntityNames = getItemEntityNames(raf);
		String[][] hudElemNames = getHudElemNames(raf);

		ArrayList<ItemRecord> items = new ArrayList<>();
		raf.seek(ProjectDatabase.rom.getOffset(EOffset.ITEM_TABLE));
		for (int i = 0; i < NUM_ITEMS; i++) {
			ItemRecord item = ItemRecord.read(i, raf);
			item.setName(itemNames[i]);
			item.itemEntityName = itemEntityNames[i];
			item.hudElemName = hudElemNames[item.hudElemID][0];
			item.moveName = moves.get(item.moveID).identifier;
			item.desc = "";

			items.add(item);
		}
		raf.close();

		writeXML(items, new File(DUMP_GLOBALS + FN_ITEMS));
		return items;
	}

	private static String[] getItemNames() throws IOException
	{
		File f = new File(DATABASE + "default_item_names.txt");
		String[] itemNames = new String[NUM_ITEMS];

		List<String> lines = IOUtils.readFormattedTextFile(f);
		for (int i = 0; i < lines.size(); i++) {
			String line = lines.get(i);

			if (line.isEmpty())
				continue;

			String[] tokens = IOUtils.getKeyValuePair(f, line, i);

			assert (itemNames[i] == null) : tokens[1];
			assert (i == Integer.parseInt(tokens[0], 16)) : tokens[0];
			itemNames[i] = tokens[1];
		}

		for (int i = 0; i < itemNames.length; i++)
			assert (itemNames[i] != null) : String.format("%03X", i);

		return itemNames;
	}

	private static String[][] getHudElemNames(RandomAccessFile raf) throws IOException
	{
		HashMap<Integer, String> hudElemNameMap = new HashMap<>();
		for (String s : Resource.getText(ResourceType.Miniscripts, "HudScripts_Global")) {
			String[] tokens = s.split("\\s+");
			hudElemNameMap.put((int) Long.parseLong(tokens[1], 16), tokens[2]);
		}
		for (String s : Resource.getText(ResourceType.Miniscripts, "HudScripts_Item")) {
			String[] tokens = s.split("\\s+");
			hudElemNameMap.put((int) Long.parseLong(tokens[1], 16), tokens[2]);
		}

		String[][] hudElemNames = new String[NUM_HUD_ELEMS][2];
		raf.seek(ProjectDatabase.rom.getOffset(EOffset.MENU_ICON_TABLE) + 8);
		for (int i = 1; i < NUM_HUD_ELEMS; i++) // skip item 0
		{
			hudElemNames[i][0] = hudElemNameMap.get(raf.readInt());
			hudElemNames[i][1] = hudElemNameMap.get(raf.readInt());
		}

		return hudElemNames;
	}

	private static String[] getItemEntityNames(RandomAccessFile raf) throws IOException
	{
		HashMap<Integer, String> itemScriptNameMap = new HashMap<>();
		for (String s : Resource.getText(ResourceType.Miniscripts, "ItemScripts")) {
			if (s.isBlank())
				continue;
			String[] tokens = s.split("\\s+");
			itemScriptNameMap.put((int) Long.parseLong(tokens[1], 16), tokens[2]);
		}

		String[] itemScriptNames = new String[NUM_ITEMS];
		raf.seek(ProjectDatabase.rom.getOffset(EOffset.ITEM_ICON_TABLE));
		for (int i = 0; i < NUM_ITEMS; i++)
			itemScriptNames[i] = itemScriptNameMap.get(raf.readInt());

		return itemScriptNames;
	}

	public static void patchTable(RomPatcher rp, IGlobalDatabase globalsDB, GlobalsData globals) throws IOException
	{
		int itemTableBase = ProjectDatabase.rom.getOffset(EOffset.ITEM_TABLE);
		List<HudElementRecord> hudElemTable = new ArrayList<>();

		for (ItemRecord item : globals.items) {
			item.moveID = 0;
			if (item.moveName != null && !item.moveName.isBlank() && !item.moveName.equals(MoveRecord.NONE)) {
				MoveRecord move = globals.moves.getElement(item.moveName);
				if (move == null)
					throw new StarRodException("Item %s has unknown move: %s", item.getIdentifier(), item.moveName);
				item.moveID = (byte) move.getIndex();
			}

			item.hudElemID = 0;
			if (item.hudElemName != null && !item.hudElemName.isBlank()) {
				HudElementRecord hudElem = globals.itemHudElements.getElement(item.hudElemName);
				if (hudElem == null)
					hudElem = globals.globalHudElements.getElement(item.hudElemName);
				if (hudElem == null)
					throw new StarRodException("Item %s has unknown item HUD element: %s", item.getIdentifier(), item.hudElemName);
				item.hudElemID = (short) hudElemTable.size();
				hudElemTable.add(hudElem);
			}
		}

		ByteBuffer bb = ByteBuffer.allocate(0x20 * globals.items.size());
		for (ItemRecord item : globals.items) {
			try {
				item.put(bb, globalsDB);
			}
			catch (InvalidInputException e) {
				throw new StarRodException(e);
			}
		}
		rp.seek("Item Table", itemTableBase);
		rp.write(bb);
	}

	public static List<ItemRecord> loadItems(boolean fromProject) throws IOException
	{
		return readXML(new File((fromProject ? MOD_GLOBALS : DUMP_GLOBALS) + FN_ITEMS));
	}

	public static void saveItems(List<ItemRecord> items) throws IOException
	{
		writeXML(items, new File(MOD_GLOBALS + FN_ITEMS));
	}

	@Deprecated
	public static List<ItemRecord> readCSV(File itemTableFile) throws IOException
	{
		List<ItemRecord> items = new LinkedList<>();

		BufferedReader in = new BufferedReader(new FileReader(itemTableFile));
		String line = in.readLine(); // skip title line

		try {
			int i = 1;
			while ((line = in.readLine()) != null) {
				if (line.isEmpty())
					continue;

				items.add(ItemRecord.loadFromCSV(i++, line));
			}
			in.close();
		}
		catch (StarRodException e) {
			throw new InputFileException(itemTableFile, e.getMessage());
		}

		return items;
	}

	private static void writeXML(List<ItemRecord> items, File itemTableFile) throws IOException
	{
		try (XmlWriter xmw = new XmlWriter(itemTableFile)) {
			XmlTag rootTag = xmw.createTag(TAG_ROOT, false);
			xmw.openTag(rootTag);

			int i = 0;
			for (ItemRecord item : items)
				item.writeXML(xmw, i++);

			xmw.closeTag(rootTag);
			xmw.save();
		}
	}

	private static List<ItemRecord> readXML(File itemTableFile) throws IOException
	{
		List<ItemRecord> items = new ArrayList<>();
		XmlReader xmr = new XmlReader(itemTableFile);
		List<Element> itemElements = xmr.getTags(xmr.getRootElement(), TAG_ITEM);
		for (int i = 0; i < itemElements.size(); i++)
			items.add(ItemRecord.readXML(xmr, itemElements.get(i), i));
		return items;
	}
}
