package game.globals;

import static game.globals.MoveRecordKey.*;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;

import org.w3c.dom.Element;

import app.StarRodException;
import app.input.InvalidInputException;
import game.globals.editor.GlobalsRecord;
import patcher.IGlobalDatabase;
import util.StringUtil;
import util.ui.FlagEditorPanel.Flag;
import util.xml.XmlWrapper.XmlReader;
import util.xml.XmlWrapper.XmlTag;
import util.xml.XmlWrapper.XmlWriter;

public class MoveRecord extends GlobalsRecord
{
	/*
	Example Entry (Power Bounce)
	002A0009 00054881 00230061 00250061 02030200
	00	002A0009	(String ID) Move name
	04	00054881	Flags (written to battle[184])
	08	00230061	(String ID) World description
	08	00250061	(String ID) Menu description
	10	02	Battle submenu ID
	11	03	FP Cost
	12	02	BP Cost
	13	00	Action command instruction
	 */

	public static final String NONE = "None";

	public static final Flag[] FLAGS = new Flag[] {
			new Flag(0x00000001, "Single Target", ""),
			new Flag(0x00000008, "Target Player", ""),
			new Flag(0x00001000, "Target Partner", "")
	};

	public String displayName;
	public String identifier;
	public String desc;

	public String abilityName;

	public String msgName;
	public String msgShortDesc;
	public String msgFullDesc;

	public int flags;

	public byte inputPopupIndex;
	public byte category;

	public byte fpCost;
	public byte bpCost;

	public MoveRecord(int index)
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

	public void copyFrom(MoveRecord other)
	{
		this.setName(other.identifier);
		this.desc = other.desc;
		this.abilityName = other.abilityName;

		this.msgName = other.msgName;
		this.msgShortDesc = other.msgShortDesc;
		this.msgFullDesc = other.msgFullDesc;

		this.flags = other.flags;

		this.inputPopupIndex = other.inputPopupIndex;
		this.category = other.category;

		this.fpCost = other.fpCost;
		this.bpCost = other.bpCost;
	}

	public static MoveRecord load(int index, String line)
	{
		MoveRecord rec = new MoveRecord(index);

		String[] tokens = line.trim().split("\\s*,\\s*");
		if (tokens.length < 10)
			throw new StarRodException("Invalid line in item table: %n%s", line);

		if (index != Integer.parseInt(tokens[0], 16))
			throw new StarRodException("Index does not match line number for move table: %n%s", line);

		// Index,Name,Flags,Menu Desc,World Desc,Slot,FP,BP,Type,Ability,Name,Desc
		//   3, 1D-38,13005,    23-A3,     00-00,   1, 0, 0,   1,Hammer Lv1,,

		rec.msgName = tokens[1];

		rec.flags = (int) Long.parseLong(tokens[2], 16);

		rec.msgShortDesc = tokens[3];
		rec.msgFullDesc = tokens[4];

		rec.category = (byte) Integer.parseInt(tokens[5], 16);
		rec.fpCost = (byte) Integer.parseInt(tokens[6], 16);
		rec.bpCost = (byte) Integer.parseInt(tokens[7], 16);
		rec.inputPopupIndex = (byte) Integer.parseInt(tokens[8], 16);

		rec.abilityName = tokens[9];

		if (tokens.length > 10)
			rec.setName(tokens[10]);

		if (tokens.length > 11)
			rec.desc = tokens[11];

		return rec;
	}

	public static MoveRecord readXML(XmlReader xmr, Element elem, int index)
	{
		MoveRecord move = new MoveRecord(index);

		xmr.requiresAttribute(elem, ATTR_INDEX);
		move.listIndex = xmr.readHex(elem, ATTR_INDEX);
		if (index != move.listIndex)
			xmr.complain(String.format("Move index mismatch: %03X vs %03X", index, move.listIndex));

		xmr.requiresAttribute(elem, ATTR_NAME);
		move.setName(xmr.getAttribute(elem, ATTR_NAME));

		if (xmr.hasAttribute(elem, ATTR_MSG_NAME))
			move.msgName = xmr.getAttribute(elem, ATTR_MSG_NAME);
		if (xmr.hasAttribute(elem, ATTR_MSG_DESC_SHORT))
			move.msgShortDesc = xmr.getAttribute(elem, ATTR_MSG_DESC_SHORT);
		if (xmr.hasAttribute(elem, ATTR_MSG_DESC_FULL))
			move.msgFullDesc = xmr.getAttribute(elem, ATTR_MSG_DESC_FULL);

		xmr.requiresAttribute(elem, ATTR_FLAGS);
		move.flags = xmr.readHex(elem, ATTR_FLAGS);

		xmr.requiresAttribute(elem, ATTR_CATEGORY);
		xmr.requiresAttribute(elem, ATTR_INPUT_POPUP);
		move.category = (byte) xmr.readHex(elem, ATTR_CATEGORY);
		move.inputPopupIndex = (byte) xmr.readHex(elem, ATTR_INPUT_POPUP);

		xmr.requiresAttribute(elem, ATTR_FP_COST);
		xmr.requiresAttribute(elem, ATTR_BP_COST);
		move.fpCost = (byte) xmr.readHex(elem, ATTR_FP_COST);
		move.bpCost = (byte) xmr.readHex(elem, ATTR_BP_COST);

		if (xmr.hasAttribute(elem, ATTR_ABILITY))
			move.abilityName = xmr.getAttribute(elem, ATTR_ABILITY);

		if (xmr.hasAttribute(elem, ATTR_DESC))
			move.desc = xmr.getAttribute(elem, ATTR_DESC);

		return move;
	}

	public void writeXML(XmlWriter xmw, int index)
	{
		XmlTag moveTag = xmw.createTag(TAG_MOVE, true);

		xmw.addHex(moveTag, ATTR_INDEX, "%03X", index);

		xmw.addNonEmptyAttribute(moveTag, ATTR_MSG_NAME, msgName);
		xmw.addNonEmptyAttribute(moveTag, ATTR_MSG_DESC_SHORT, msgShortDesc);
		xmw.addNonEmptyAttribute(moveTag, ATTR_MSG_DESC_FULL, msgFullDesc);

		xmw.addHex(moveTag, ATTR_FLAGS, "%08X", flags);
		xmw.addHex(moveTag, ATTR_CATEGORY, "%02X", category & 0xFF);
		xmw.addHex(moveTag, ATTR_INPUT_POPUP, "%02X", inputPopupIndex & 0xFF);
		xmw.addHex(moveTag, ATTR_FP_COST, "%02X", fpCost & 0xFF);
		xmw.addHex(moveTag, ATTR_BP_COST, "%02X", bpCost & 0xFF);

		xmw.addAttribute(moveTag, ATTR_NAME, identifier);

		xmw.addNonEmptyAttribute(moveTag, ATTR_ABILITY, abilityName);
		xmw.addNonEmptyAttribute(moveTag, ATTR_DESC, desc);

		xmw.printTag(moveTag);
	}

	public static MoveRecord read(int index, RandomAccessFile raf) throws IOException
	{
		MoveRecord rec = new MoveRecord(index);

		rec.msgName = makeMessageID(raf.readInt());
		rec.flags = raf.readInt();

		rec.msgShortDesc = makeMessageID(raf.readInt());
		rec.msgFullDesc = makeMessageID(raf.readInt());

		rec.category = raf.readByte();
		rec.fpCost = raf.readByte();
		rec.bpCost = raf.readByte();
		rec.inputPopupIndex = raf.readByte();

		return rec;
	}

	public void put(ByteBuffer bb, IGlobalDatabase db) throws InvalidInputException
	{
		bb.putInt(db.resolveStringID(msgName));
		bb.putInt(flags);

		bb.putInt(db.resolveStringID(msgShortDesc));
		bb.putInt(db.resolveStringID(msgFullDesc));

		bb.put(category);
		bb.put(fpCost);
		bb.put(bpCost);
		bb.put(inputPopupIndex);
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
		return identifier;
	}

	@Override
	public boolean canDeleteFromList()
	{
		return listIndex >= MoveModder.NUM_MOVES;
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
}
