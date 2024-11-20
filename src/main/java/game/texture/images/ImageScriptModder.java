package game.texture.images;

import static app.Directories.*;
import static game.shared.StructTypes.HudElementScriptT;
import static game.shared.StructTypes.ItemEntityScriptT;
import static game.texture.images.ImageAssetKey.*;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import org.w3c.dom.Element;

import app.Directories;
import app.Environment;
import app.Resource;
import app.Resource.ResourceType;
import app.StarRodException;
import app.input.IOUtils;
import app.input.InputFileException;
import app.input.Line;
import game.MemoryRegion;
import game.ROM.EOffset;
import game.ROM.EngineComponent;
import game.ROM.LibScope;
import game.globals.ItemRecord;
import game.globals.editor.GlobalsData;
import game.shared.DataUtils;
import game.shared.ProjectDatabase;
import game.shared.struct.miniscript.HudElementScript;
import game.shared.struct.miniscript.ItemEntityScript;
import game.shared.struct.miniscript.Miniscript;
import game.texture.images.ImageDatabase.EncodedImageAsset;
import patcher.DefaultGlobals;
import patcher.IGlobalDatabase;
import patcher.RomPatcher;
import util.IterableListModel;
import util.Logger;
import util.xml.XmlWrapper.XmlReader;
import util.xml.XmlWrapper.XmlTag;
import util.xml.XmlWrapper.XmlWriter;

public class ImageScriptModder
{
	/*
	RAM layout:
	8007F210	8007FEE8	effect_table_entry[137`]
	8007FEF0	800878E0	HudElementScripts
	800878E0	8008A680	Item Table
	8008A680	8008B108	Item HudElementScript Table
	8008B108	8008DDD4	ItemEntityScripts
	8008DDD4	8008E3D8	ItemEntityScript Table
	8008E3D8	8008E94C	Item Icon Raster Offsets
	8008E94C	8008EEC0	Item Icon Palette Offsets
	 */

	public static void dumpAll() throws IOException
	{
		ByteBuffer romBuffer = Environment.getBaseRomBuffer();
		HashSet<String> usedNames = new HashSet<>();

		ImageScriptDecoder globalDumper = new ImageScriptDecoder(LibScope.Common);
		ImageScriptDecoder battleDumper = new ImageScriptDecoder(LibScope.Battle);
		ImageScriptDecoder menuDumper = new ImageScriptDecoder(LibScope.Pause);

		XmlWriter hudXMW = new XmlWriter(new File(Directories.DUMP_IMG + FN_HUD_SCRIPTS));
		XmlTag hudRoot = hudXMW.createTag(TAG_HUD_ELEMS, false);
		hudXMW.openTag(hudRoot);

		XmlTag hudGroup = hudXMW.createTag(TAG_HUD_ITEM, false);
		hudXMW.openTag(hudGroup);
		for (String s : Resource.getText(ResourceType.Miniscripts, "HudScripts_Item"))
			dumpHudScripts(globalDumper, romBuffer, s, usedNames, hudXMW);
		hudXMW.closeTag(hudGroup);

		hudGroup = hudXMW.createTag(TAG_HUD_GLOBAL, false);
		hudXMW.openTag(hudGroup);
		for (String s : Resource.getText(ResourceType.Miniscripts, "HudScripts_Global"))
			dumpHudScripts(globalDumper, romBuffer, s, usedNames, hudXMW);
		hudXMW.closeTag(hudGroup);

		hudGroup = hudXMW.createTag(TAG_HUD_BATTLE, false);
		hudXMW.openTag(hudGroup);
		for (String s : Resource.getText(ResourceType.Miniscripts, "HudScripts_Battle"))
			dumpHudScripts(battleDumper, romBuffer, s, usedNames, hudXMW);
		hudXMW.closeTag(hudGroup);

		hudGroup = hudXMW.createTag(TAG_HUD_MENU, false);
		hudXMW.openTag(hudGroup);
		for (String s : Resource.getText(ResourceType.Miniscripts, "HudScripts_Menu"))
			dumpHudScripts(menuDumper, romBuffer, s, usedNames, hudXMW);
		hudXMW.closeTag(hudGroup);

		hudXMW.closeTag(hudRoot);
		hudXMW.save();

		XmlWriter nttXMW = new XmlWriter(new File(Directories.DUMP_IMG + FN_ITEM_SCRIPTS));
		XmlTag nttRoot = nttXMW.createTag(TAG_ITEM_NTTS, false);
		nttXMW.openTag(nttRoot);

		for (String s : Resource.getText(ResourceType.Miniscripts, "ItemScripts"))
			dumpItemScripts(battleDumper, romBuffer, s, new HashSet<>(), nttXMW);

		nttXMW.closeTag(nttRoot);
		nttXMW.save();
	}

	private static void dumpHudScripts(ImageScriptDecoder dumper, ByteBuffer romBuffer, String entry,
		HashSet<String> usedNames, XmlWriter xmw) throws IOException
	{
		entry = entry.trim();
		if (entry.isEmpty())
			return;

		String[] tokens = entry.split(" ");

		int offset = Integer.parseInt(tokens[0], 16);
		String name = tokens[2];

		if (usedNames.contains(name))
			throw new StarRodException("Duplicate HudElementScript name: " + name);
		usedNames.add(name);

		File f = new File(DUMP_HUD_SCRIPTS + name + EXT_HUD_SCRIPT);
		try (PrintWriter pw = IOUtils.getBufferedPrintWriter(f)) {
			dumper.printStruct(HudElementScriptT, romBuffer, offset, pw);
		}

		cleanFile(f);

		XmlTag tag = xmw.createTag(TAG_SCRIPT, true);
		xmw.addHex(tag, ATTR_OFFSET, "%06X", offset);
		xmw.addAttribute(tag, ATTR_NAME, name);
		xmw.printTag(tag);
	}

	private static void dumpItemScripts(ImageScriptDecoder dumper, ByteBuffer romBuffer, String entry,
		HashSet<String> usedNames, XmlWriter xmw) throws IOException
	{
		entry = entry.trim();
		if (entry.isEmpty())
			return;

		String[] tokens = entry.split(" ");

		int offset = Integer.parseInt(tokens[0], 16);
		String name = tokens[2];

		if (usedNames.contains(name))
			throw new StarRodException("Duplicate ItemEntityScript name: " + name);
		usedNames.add(name);

		File f = new File(DUMP_ITEM_SCRIPTS + name + EXT_ITEM_SCRIPT);
		try (PrintWriter pw = IOUtils.getBufferedPrintWriter(f)) {
			dumper.printStruct(ItemEntityScriptT, romBuffer, offset, pw);
		}

		cleanFile(f);

		XmlTag tag = xmw.createTag(TAG_SCRIPT, true);
		xmw.addHex(tag, ATTR_OFFSET, "%06X", offset);
		xmw.addAttribute(tag, ATTR_NAME, name);
		xmw.printTag(tag);
	}

	private static void cleanFile(File f) throws IOException
	{
		ArrayList<String> lines = IOUtils.readPlainTextFile(f);
		try (PrintWriter pw = IOUtils.getBufferedPrintWriter(f)) {
			for (String line : lines) {
				if (line.contains("%"))
					line = line.substring(0, line.indexOf("%"));
				if (line.startsWith("\t"))
					line = line.substring(1);
				pw.println(line.stripTrailing());
			}
		}
	}

	public static void readHudXML(GlobalsData data, boolean fromProject)
	{
		XmlReader xmr = new XmlReader(new File((fromProject ? MOD_IMG : DUMP_IMG) + FN_HUD_SCRIPTS));
		readHudScriptElements(data.itemHudElements, xmr, xmr.getUniqueRequiredTag(xmr.getRootElement(), TAG_HUD_ITEM));
		readHudScriptElements(data.globalHudElements, xmr, xmr.getUniqueRequiredTag(xmr.getRootElement(), TAG_HUD_GLOBAL));
		readHudScriptElements(data.battleHudElements, xmr, xmr.getUniqueRequiredTag(xmr.getRootElement(), TAG_HUD_BATTLE));
		readHudScriptElements(data.menuHudElements, xmr, xmr.getUniqueRequiredTag(xmr.getRootElement(), TAG_HUD_MENU));
	}

	private static void readHudScriptElements(IterableListModel<HudElementRecord> list, XmlReader xmr, Element group)
	{
		list.clear();

		for (Element elem : xmr.getTags(group, TAG_SCRIPT)) {
			xmr.requiresAttribute(elem, ATTR_NAME);
			String name = xmr.getAttribute(elem, ATTR_NAME);
			int offset = xmr.hasAttribute(elem, ATTR_OFFSET) ? xmr.readHex(elem, ATTR_OFFSET) : -1;
			list.addElement(new HudElementRecord(name, offset));
		}
	}

	public static void writeHudXML(GlobalsData data) throws IOException
	{
		try (XmlWriter xmw = new XmlWriter(new File(MOD_IMG + FN_HUD_SCRIPTS))) {
			XmlTag rootTag = xmw.createTag(TAG_HUD_ELEMS, false);
			xmw.openTag(rootTag);

			XmlTag itemTag = xmw.createTag(TAG_HUD_ITEM, false);
			xmw.openTag(itemTag);
			writeHudScriptElements(xmw, data.itemHudElements);
			xmw.closeTag(itemTag);

			XmlTag generalTag = xmw.createTag(TAG_HUD_GLOBAL, false);
			xmw.openTag(generalTag);
			writeHudScriptElements(xmw, data.globalHudElements);
			xmw.closeTag(generalTag);

			XmlTag battleTag = xmw.createTag(TAG_HUD_BATTLE, false);
			xmw.openTag(battleTag);
			writeHudScriptElements(xmw, data.battleHudElements);
			xmw.closeTag(battleTag);

			XmlTag menuTag = xmw.createTag(TAG_HUD_MENU, false);
			xmw.openTag(menuTag);
			writeHudScriptElements(xmw, data.menuHudElements);
			xmw.closeTag(menuTag);

			xmw.closeTag(rootTag);
			xmw.save();
		}
	}

	private static void writeHudScriptElements(XmlWriter xmw, Iterable<HudElementRecord> hudElements)
	{
		for (HudElementRecord hudElem : hudElements) {
			XmlTag elemTag = xmw.createTag(TAG_SCRIPT, true);

			if (hudElem.offset >= 0)
				xmw.addHex(elemTag, ATTR_OFFSET, hudElem.offset);
			xmw.addAttribute(elemTag, ATTR_NAME, hudElem.identifier);

			xmw.printTag(elemTag);
		}
	}

	public static void readItemXML(GlobalsData data, boolean fromProject)
	{
		XmlReader xmr = new XmlReader(new File((fromProject ? MOD_IMG : DUMP_IMG) + FN_ITEM_SCRIPTS));
		List<Element> itemElems = xmr.getTags(xmr.getRootElement(), TAG_SCRIPT);
		data.itemEntities.clear();

		for (Element elem : itemElems) {
			xmr.requiresAttribute(elem, ATTR_NAME);
			String name = xmr.getAttribute(elem, ATTR_NAME);
			//	int offset = xmr.hasAttribute(elem, ATTR_OFFSET) ? xmr.readHex(elem, ATTR_OFFSET) : -1;
			data.itemEntities.addElement(new ItemEntityRecord(name, -1));
		}
	}

	public static void writeItemXML(GlobalsData data) throws IOException
	{
		try (XmlWriter xmw = new XmlWriter(new File(MOD_IMG + FN_ITEM_SCRIPTS))) {
			XmlTag rootTag = xmw.createTag(TAG_ITEM_NTTS, false);
			xmw.openTag(rootTag);

			for (ItemEntityRecord itemEntity : data.itemEntities) {
				XmlTag elemTag = xmw.createTag(TAG_SCRIPT, true);

				//		if(itemEntity.offset >= 0)
				//			xmw.addHex(elemTag, ATTR_OFFSET, itemEntity.offset);
				xmw.addAttribute(elemTag, ATTR_NAME, itemEntity.identifier);

				xmw.printTag(elemTag);
			}

			xmw.closeTag(rootTag);
			xmw.save();
		}
	}

	public static void readAll(GlobalsData globals) throws IOException
	{
		readItemEntityFiles(globals.itemEntities);
		Logger.logf("Read %d item entity scripts.", globals.itemEntities.size());

		readHudElementFiles(globals.itemHudElements);
		readHudElementFiles(globals.globalHudElements);
		readHudElementFiles(globals.battleHudElements);
		readHudElementFiles(globals.menuHudElements);
		Logger.logf("Read %d HUD element scripts.",
			globals.itemHudElements.size() + globals.globalHudElements.size() + globals.battleHudElements.size() + globals.menuHudElements.size());
	}

	private static void readHudElementFiles(IterableListModel<HudElementRecord> hudElements) throws IOException
	{
		for (HudElementRecord rec : hudElements) {
			File f = new File(MOD_HUD_SCRIPTS + rec.identifier + EXT_HUD_SCRIPT);
			rec.lines = IOUtils.readFormattedInputFile(f, false);
		}
	}

	private static void readItemEntityFiles(IterableListModel<ItemEntityRecord> itemEntities) throws IOException
	{
		for (ItemEntityRecord rec : itemEntities) {
			File f = new File(MOD_ITEM_SCRIPTS + rec.identifier + EXT_ITEM_SCRIPT);
			rec.load(); // needed to search for preview image names
			rec.lines = IOUtils.readFormattedInputFile(f, false);
		}
	}

	public static void parseAll(GlobalsData globals) throws IOException
	{
		for (ItemEntityRecord rec : globals.itemEntities)
			rec.out = parse(ItemEntityScript.instance, rec.lines);

		for (HudElementRecord rec : globals.itemHudElements)
			rec.out = parse(HudElementScript.instance, rec.lines);
		for (HudElementRecord rec : globals.globalHudElements)
			rec.out = parse(HudElementScript.instance, rec.lines);
		for (HudElementRecord rec : globals.battleHudElements)
			rec.out = parse(HudElementScript.instance, rec.lines);
		for (HudElementRecord rec : globals.menuHudElements)
			rec.out = parse(HudElementScript.instance, rec.lines);
	}

	private static ByteBuffer parse(Miniscript script, List<Line> lines) throws IOException
	{
		ArrayList<Integer> words = new ArrayList<>(32);

		for (Line line : lines) {
			try {
				String tokens[] = script.parseCommand(null, line);

				for (String s : tokens) {
					// replace constants
					if (s.matches("\\.\\S+"))
						s = ProjectDatabase.resolve(s.substring(1), true);

					int x = DataUtils.parseIntString(s);
					words.add(x);
				}

			}
			catch (Exception e) {
				Logger.printStackTrace(e);
				throw new InputFileException(line, "Error while parsing line: %n%s %n%s", line.str, e.getMessage());
			}
		}

		ByteBuffer outBuf = ByteBuffer.allocateDirect(4 * words.size());
		for (int i : words)
			outBuf.putInt(i);
		outBuf.rewind();

		return outBuf;
	}

	protected static void validate(ByteBuffer original, int offset, String name, ByteBuffer parsed)
	{
		original.position(offset);
		while (parsed.hasRemaining()) {
			int pos = original.position();
			if (original.getInt() != parsed.getInt())
				throw new StarRodException("%s has mismatch at offset %X", name, pos);
		}
	}

	public static void patchAll(RomPatcher rp, IGlobalDatabase db, GlobalsData globals)
	{
		//	rp.seek("ItemEntityScripts", itemNttScriptsStart);
		patchItemEntityScripts(rp, globals.itemEntities);
		patchHudScripts(rp, globals.itemHudElements);
		patchHudScripts(rp, globals.globalHudElements);
		patchHudScripts(rp, globals.battleHudElements);
		patchHudScripts(rp, globals.menuHudElements);

		// relocate tables to end of ROM
		int blockSize = 4 * globals.items.size();
		//		int itemNttTable = (rp.getCurrentOffset() + 15) & -16;
		int itemNttTable = rp.nextAlignedOffset();
		int itemHudTable = itemNttTable + blockSize;
		int itemIconRasterTable = itemHudTable + 2 * blockSize;
		int itemIconPaletteTable = itemIconRasterTable + blockSize;
		//		int itemNttScriptsStart = itemIconPaletteTable + blockSize;

		db.setGlobalPointer(DefaultGlobals.ITEM_NTT_SCRIPTS, rp.toAddress(itemNttTable));
		db.setGlobalPointer(DefaultGlobals.ITEM_HUD_SCRIPTS, rp.toAddress(itemHudTable));
		db.setGlobalPointer(DefaultGlobals.ITEM_ICON_RASTERS, rp.toAddress(itemIconRasterTable));
		db.setGlobalPointer(DefaultGlobals.ITEM_ICON_PALETTES, rp.toAddress(itemIconPaletteTable));

		rp.seek("ItemEntityScript Table", itemNttTable);
		for (ItemRecord item : globals.items) {
			ItemEntityRecord rec = globals.itemEntities.getElement(item.itemEntityName);
			rp.writeInt(rec.finalAddress);
		}

		rp.seek("Item HudElementScript Table", itemHudTable);
		for (ItemRecord item : globals.items) {
			HudElementRecord recA = globals.itemHudElements.getElement(item.hudElemName);
			HudElementRecord recB = globals.itemHudElements.getElement(item.hudElemName + "_disabled");

			// items may also use graphics from global list
			if (recA == null) {
				recA = globals.globalHudElements.getElement(item.hudElemName);
				recB = globals.globalHudElements.getElement(item.hudElemName + "_disabled");
			}

			if (recA == null)
				throw new StarRodException("Item %s references unknown HUD element: %s", item.getIdentifier(), item.hudElemName);

			rp.writeInt(recA.finalAddress);
			rp.writeInt(recB == null ? recA.finalAddress : recB.finalAddress);
		}

		rp.seek("Item HudElementScript Table", itemIconRasterTable);
		for (ItemRecord item : globals.items) {
			ItemEntityRecord rec = globals.itemEntities.getElement(item.itemEntityName);

			EncodedImageAsset encoded = ProjectDatabase.images.getImage(rec.previewImageName);
			if (encoded == null)
				throw new StarRodException("HudElementScript for %s has unknown item icon: %s",
					item.getIdentifier(), rec.previewImageName);
			if (encoded.outImgOffset < 0)
				throw new StarRodException("HudElementScript for %s has item icon with missing raster: %s",
					item.getIdentifier(), rec.previewImageName);
			rp.writeInt(encoded.outImgOffset - 0x1CC310);
		}

		rp.seek("Item HudElementScript Table", itemIconPaletteTable);
		for (ItemRecord item : globals.items) {
			ItemEntityRecord rec = globals.itemEntities.getElement(item.itemEntityName);
			EncodedImageAsset encoded = ProjectDatabase.images.getImage(rec.previewImageName);
			if (encoded == null)
				throw new StarRodException("HudElementScript for %s has unknown item icon: %s",
					item.getIdentifier(), rec.previewImageName);
			if (encoded.outPalOffset < 0)
				throw new StarRodException("HudElementScript for %s has item icon with missing palette: %s",
					item.getIdentifier(), rec.previewImageName);
			rp.writeInt(encoded.outPalOffset - 0x1CC310);
		}
	}

	private static void patchItemEntityScripts(RomPatcher rp, IterableListModel<ItemEntityRecord> itemScripts)
	{
		MemoryRegion region = ProjectDatabase.rom.getMemoryRegion(EngineComponent.SYSTEM);
		int end = ProjectDatabase.rom.getOffset(EOffset.ITEM_NTT_SCRIPT_END);
		boolean overflow = false;

		for (ItemEntityRecord rec : itemScripts) {
			if (rp.getCurrentOffset() + rec.out.limit() > end) {
				rp.seek("Additional ItemEntityScripts", rp.nextAlignedOffset());
				end = Integer.MAX_VALUE;
				overflow = true;
			}

			if (overflow)
				rec.finalAddress = rp.toAddress(rp.getCurrentOffset());
			else
				rec.finalAddress = region.toAddress(rp.getCurrentOffset());

			rp.write(rec.out);
		}
	}

	private static void patchHudScripts(RomPatcher rp, IterableListModel<HudElementRecord> hudElems)
	{
		for (HudElementRecord rec : hudElems) {
			if (rec.offset > 0) // patching existing script
			{
				// get original script length
				rp.seek("Default " + rec.identifier, rec.offset);
				int length = HudElementScript.instance.getLength(rp);

				if (rec.out.limit() > length) {
					// new script is longer than original, add hook to new
					int scriptOffset = rp.nextAlignedOffset();
					rp.seek(rec.identifier, scriptOffset);
					rp.write(rec.out);

					rp.seek(rec.identifier + " Hook", rec.offset);
					rp.writeInt(0x16);
					rp.writeInt(rp.toAddress(scriptOffset));
				}
				else {
					// overwrite original
					rp.seek(rec.identifier, rec.offset);
					rp.write(rec.out);
				}

				// reference should still point to original script location
				rec.finalAddress = ProjectDatabase.rom.getAddress(rec.offset);
			}
			else {
				// new script
				int scriptOffset = rp.nextAlignedOffset();
				rp.seek(rec.identifier, scriptOffset);
				rp.write(rec.out);

				rec.finalAddress = rp.toAddress(scriptOffset);
			}
		}
	}
}
