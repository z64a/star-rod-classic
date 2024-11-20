package game.globals;

import static game.globals.ItemRecordKey.*;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;

import org.w3c.dom.Element;

import app.StarRodException;
import app.input.InvalidInputException;
import game.globals.editor.GlobalsRecord;
import game.shared.ProjectDatabase;
import patcher.IGlobalDatabase;
import util.ArrayIterator;
import util.StringUtil;
import util.ui.FlagEditorPanel.Flag;
import util.xml.XmlWrapper.XmlReader;
import util.xml.XmlWrapper.XmlTag;
import util.xml.XmlWrapper.XmlWriter;

public class ItemRecord extends GlobalsRecord
{
	/*
	Example Entry (Jelly Shroom)
	00260031 002B0000 00000009 004B0000 00250031 00230031 10870005 32000000
	00	00260031	(String ID) Item name
	04	002B	Icon ID
	06	0000	Badge sort priority (badges only)
	08	00000009	Target flags
	0C	004B	Default sell value (can be overridden by shops)
	0E	0000	--
	10	00250031	(String ID) Menu description
	14	00230031	(String ID) World description
	18	1087	Type flags
	1A	0005
	1C	32	HP Bonus / Damage (Attack Items) / Duration (Status Items)
	1D	00	FP Bonus
	1E	0000	--
	 */

	public static final Flag[] TYPE_FLAGS = new Flag[] {
			new Flag(0x0001, "Usable in world"),
			new Flag(0x0002, "Usable in battle"),
			new Flag(0x0004, "Consumable"),
			new Flag(0x0008, "Key item"),
			new Flag(0x0020, "Gear", "Hammers and boots"),
			new Flag(0x0040, "Badge"),
			new Flag(0x0080, "Food or Drink"),
			new Flag(0x0100, "Use drink animation"),
			new Flag(0x0200, "Collectible entity", "No effect. Used for coins, hearts, etc"),
			new Flag(0x1000, "Full-size entity", "1 = 32x32 image, 0 = 24x24 image")
	};

	public static final Flag[] TARGET_FLAGS = new Flag[] {
			new Flag(0x0001, "Single enemy"),
			new Flag(0x0002, "???  0002", "Unknown purpose"),
			new Flag(0x0008, "Player", "Used by food, status effect items, etc"),
			new Flag(0x8000, "???  8000", "Unknown purpose")
	};

	public String hudElemName;
	public transient short hudElemID;

	public String itemEntityName;

	public boolean autoGraphics;
	public String imageAssetName;

	public String moveName = "Nothing";
	public transient byte moveID; // badges only

	public String displayName;
	public String identifier;
	public String desc;

	// msg values may be NULL, pointers, string IDs (%02X-%03X), or string names!
	public String msgName = "00-000";
	public String msgFullDesc = "00-000";
	public String msgShortDesc = "00-000";

	public short typeFlags;
	public int targetFlags;

	public short sortValue; // badges only

	public byte potencyA; // contextual: HP Bonus | Damage | Duration
	public byte potencyB; // FP bonus

	public short sellValue = -1;

	public ItemRecord(int index)
	{
		setIndex(index);
	}

	public void setName(String newName)
	{
		if (newName == null)
			newName = "NameMissing";
		newName = newName.replaceAll("\\s+", "_").replaceAll("\\W", "");
		if (newName.isBlank())
			newName = "NameMissing";
		identifier = newName;
		displayName = StringUtil.splitCamelCase(identifier).replaceAll("_", " ").replaceAll("\\s+", " ");
	}

	public void copyFrom(ItemRecord other)
	{
		this.setName(other.identifier);
		this.desc = other.desc;

		this.autoGraphics = other.autoGraphics;
		this.imageAssetName = other.imageAssetName;
		this.hudElemName = other.hudElemName;
		this.itemEntityName = other.itemEntityName;
		this.moveName = other.moveName;

		this.msgName = other.msgName;
		this.msgFullDesc = other.msgFullDesc;
		this.msgShortDesc = other.msgShortDesc;

		this.typeFlags = other.typeFlags;
		this.targetFlags = other.targetFlags;

		this.sortValue = other.sortValue;
		this.potencyA = other.potencyA;
		this.potencyB = other.potencyB;

		this.sellValue = other.sellValue;
	}

	public static ItemRecord loadFromCSV(int index, String line)
	{
		ItemRecord rec = new ItemRecord(index);

		String[] tokens = line.trim().split("\\s*,\\s*");
		if (tokens.length < 12)
			throw new StarRodException("Invalid line in item table: %n%s", line);

		ArrayIterator<String> iter = new ArrayIterator<>(tokens);

		if (index != Integer.parseInt(iter.next(), 16))
			throw new StarRodException("Index does not match line number for item table: %n%s", line);

		// Index,Name,IconID,Priority,Target,Sell Value,Menu Desc,World Desc,Type,MoveID,HP,MP,Name,,
		//   2, 26-A1,   39,       0,     2,      FFFF,     00-00,     00-00,1020,     0 ,0, 0,SpinJump,,

		// V2
		// Index,Name,ItemEntity,HudElem,Priority,Target,Sell Value,Menu Desc,Item Desc,Type,Move,HP,FP,Name,Desc

		rec.msgName = iter.next();

		//	if(version > 1)
		//		rec.itemEntity = (short)Integer.parseInt(iter.next(), 16);
		rec.hudElemID = (short) Integer.parseInt(iter.next(), 16);
		rec.sortValue = (short) Integer.parseInt(iter.next(), 16);

		rec.targetFlags = (int) Long.parseLong(iter.next(), 16);
		rec.sellValue = (short) Integer.parseInt(iter.next(), 16);

		rec.msgFullDesc = iter.next();
		rec.msgShortDesc = iter.next();

		rec.typeFlags = (short) Integer.parseInt(iter.next(), 16);
		rec.moveID = (byte) Integer.parseInt(iter.next(), 16);

		rec.potencyA = (byte) Integer.parseInt(iter.next(), 16);
		rec.potencyB = (byte) Integer.parseInt(iter.next(), 16);

		String name = ProjectDatabase.getItemName(index);
		if (name != null)
			rec.setName(name);
		else if (tokens.length > 12)
			rec.setName(iter.next());

		if (tokens.length > 13)
			rec.desc = iter.next();

		return rec;
	}

	public static ItemRecord readXML(XmlReader xmr, Element elem, int index)
	{
		ItemRecord item = new ItemRecord(index);

		xmr.requiresAttribute(elem, ATTR_INDEX);
		item.listIndex = xmr.readHex(elem, ATTR_INDEX);
		if (index != item.listIndex)
			item.listIndex = index;
		//	xmr.complain(String.format("Item index mismatch: %03X vs %03X", index, item.listIndex));

		xmr.requiresAttribute(elem, ATTR_NAME);
		item.setName(xmr.getAttribute(elem, ATTR_NAME));

		if (xmr.hasAttribute(elem, ATTR_MSG_NAME))
			item.msgName = xmr.getAttribute(elem, ATTR_MSG_NAME);
		if (xmr.hasAttribute(elem, ATTR_MSG_DESC_SHORT))
			item.msgShortDesc = xmr.getAttribute(elem, ATTR_MSG_DESC_SHORT);
		if (xmr.hasAttribute(elem, ATTR_MSG_DESC_FULL))
			item.msgFullDesc = xmr.getAttribute(elem, ATTR_MSG_DESC_FULL);

		xmr.requiresAttribute(elem, ATTR_TYPE_FLAGS);
		xmr.requiresAttribute(elem, ATTR_TARGET_FLAGS);
		item.typeFlags = (short) xmr.readHex(elem, ATTR_TYPE_FLAGS);
		item.targetFlags = xmr.readHex(elem, ATTR_TARGET_FLAGS);

		if (xmr.hasAttribute(elem, ATTR_SORT_PRIORITY))
			item.sortValue = (short) xmr.readHex(elem, ATTR_SORT_PRIORITY);
		if (xmr.hasAttribute(elem, ATTR_SELL_VALUE))
			item.sellValue = (short) xmr.readInt(elem, ATTR_SELL_VALUE);
		if (xmr.hasAttribute(elem, ATTR_POTENCY_A))
			item.potencyA = (byte) xmr.readInt(elem, ATTR_POTENCY_A);
		if (xmr.hasAttribute(elem, ATTR_POTENCY_B))
			item.potencyB = (byte) xmr.readInt(elem, ATTR_POTENCY_B);

		if (xmr.hasAttribute(elem, ATTR_AUTO_GFX)) {
			item.autoGraphics = true;
			item.imageAssetName = xmr.getAttribute(elem, ATTR_AUTO_GFX);
		}
		else {
			item.autoGraphics = false;
			item.imageAssetName = "item/" + item.identifier;
		}

		if (xmr.hasAttribute(elem, ATTR_HUD_ELEMENT))
			item.hudElemName = xmr.getAttribute(elem, ATTR_HUD_ELEMENT);
		else
			item.hudElemName = item.identifier;

		if (xmr.hasAttribute(elem, ATTR_ITEM_ENTITY))
			item.itemEntityName = xmr.getAttribute(elem, ATTR_ITEM_ENTITY);
		else
			item.itemEntityName = item.identifier;

		if (xmr.hasAttribute(elem, ATTR_MOVE))
			item.moveName = xmr.getAttribute(elem, ATTR_MOVE);

		if (xmr.hasAttribute(elem, ATTR_DESC))
			item.desc = xmr.getAttribute(elem, ATTR_DESC);

		return item;
	}

	public void writeXML(XmlWriter xmw, int index)
	{
		XmlTag itemTag = xmw.createTag(TAG_ITEM, true);

		xmw.addHex(itemTag, ATTR_INDEX, "%03X", index);

		xmw.addNonEmptyAttribute(itemTag, ATTR_MSG_NAME, msgName);
		xmw.addNonEmptyAttribute(itemTag, ATTR_MSG_DESC_SHORT, msgShortDesc);
		xmw.addNonEmptyAttribute(itemTag, ATTR_MSG_DESC_FULL, msgFullDesc);

		xmw.addHex(itemTag, ATTR_TYPE_FLAGS, "%04X", typeFlags & 0xFFFF);
		xmw.addHex(itemTag, ATTR_TARGET_FLAGS, "%04X", targetFlags & 0xFFFF);

		if (sortValue != 0)
			xmw.addHex(itemTag, ATTR_SORT_PRIORITY, "%02X", sortValue & 0xFF);

		xmw.addAttribute(itemTag, ATTR_NAME, identifier);

		if (autoGraphics)
			xmw.addNonEmptyAttribute(itemTag, ATTR_AUTO_GFX, imageAssetName);

		if (!hudElemName.equals(identifier))
			xmw.addNonEmptyAttribute(itemTag, ATTR_HUD_ELEMENT, hudElemName);
		if (!itemEntityName.equals(identifier))
			xmw.addNonEmptyAttribute(itemTag, ATTR_ITEM_ENTITY, itemEntityName);

		if (!moveName.equals("Nothing"))
			xmw.addNonEmptyAttribute(itemTag, ATTR_MOVE, moveName);

		if (sellValue != -1)
			xmw.addInt(itemTag, ATTR_SELL_VALUE, sellValue);
		if (potencyA != 0)
			xmw.addInt(itemTag, ATTR_POTENCY_A, potencyA);
		if (potencyB != 0)
			xmw.addInt(itemTag, ATTR_POTENCY_B, potencyB);

		xmw.addNonEmptyAttribute(itemTag, ATTR_DESC, desc);

		xmw.printTag(itemTag);
	}

	public static ItemRecord read(int index, RandomAccessFile raf) throws IOException
	{
		ItemRecord rec = new ItemRecord(index);

		rec.msgName = makeMessageID(raf.readInt());
		rec.hudElemID = raf.readShort();
		rec.sortValue = raf.readShort();

		rec.targetFlags = raf.readInt();
		rec.sellValue = raf.readShort();
		short s = raf.readShort();
		assert (s == 0);

		rec.msgFullDesc = makeMessageID(raf.readInt());
		rec.msgShortDesc = makeMessageID(raf.readInt());

		rec.typeFlags = raf.readShort();
		rec.moveID = raf.readByte();
		rec.potencyA = raf.readByte();
		rec.potencyB = raf.readByte();
		byte b = raf.readByte();
		assert (b == 0);
		b = raf.readByte();
		assert (b == 0);
		b = raf.readByte();
		assert (b == 0);

		boolean isWeapon = (rec.typeFlags & 0x2) != 0;
		boolean isBadge = (rec.typeFlags & 0x40) != 0;
		boolean isFood = (rec.typeFlags & 0x80) != 0;

		if (rec.sortValue != 0)
			assert (isBadge);

		if (rec.moveID != 0)
			assert (isBadge);

		if (rec.potencyA != 0)
			assert (isWeapon || isFood);

		if (rec.potencyB != 0)
			assert (isFood);

		return rec;
	}

	public void put(ByteBuffer bb, IGlobalDatabase db) throws InvalidInputException
	{
		bb.putInt(db.resolveStringID(msgName));
		bb.putShort(hudElemID);
		bb.putShort(sortValue);

		bb.putInt(targetFlags);
		bb.putShort(sellValue);
		bb.putShort((short) 0);

		bb.putInt(db.resolveStringID(msgFullDesc));
		bb.putInt(db.resolveStringID(msgShortDesc));

		bb.putShort(typeFlags);
		bb.put(moveID);
		bb.put(potencyA);
		bb.put(potencyB);
		bb.put((byte) 0);
		bb.put((byte) 0);
		bb.put((byte) 0);
	}

	private static String makeMessageID(int v)
	{
		if (v >>> 24 != 0) // check uppermost byte
			return String.format("%08X", v); // pointer
		else
			return String.format("%02X-%03X", (v >>> 16), (v & 0xFFFF));
	}

	@Override
	public String getFilterableString()
	{
		return displayName + " " + identifier;
	}

	@Override
	public boolean canDeleteFromList()
	{
		return listIndex >= ItemModder.NUM_ITEMS;
	}

	@Override
	public String getIdentifier()
	{
		return identifier;
	}

	@Override
	public void setIdentifier(String newValue)
	{
		identifier = newValue;
	}

	@Override
	public String toString()
	{
		return identifier;
	}

	public String getInfo()
	{
		// Index,Name,ItemEntity,HudElem,Priority,Target,Sell Value,Menu Desc,Item Desc,Type,Move,HP,FP,Name,Desc
		return String.format("%03X %-20s %-20s", listIndex, identifier, moveName);
	}
}
