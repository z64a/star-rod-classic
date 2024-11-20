package game.battle;

import static app.Directories.*;
import static game.battle.ActorTypesEditor.ActorKey.*;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.TreeMap;

import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import app.Environment;
import game.shared.ProjectDatabase;
import patcher.DefaultGlobals;
import patcher.Patcher;
import patcher.RomPatcher;
import util.Logger;
import util.xml.XmlKey;
import util.xml.XmlWrapper.XmlReader;
import util.xml.XmlWrapper.XmlTag;
import util.xml.XmlWrapper.XmlWriter;

public class ActorTypesEditor
{
	private static final int ACTOR_TYPE_NUM = 0xD4;
	private static final int ACTOR_NAMES = 0x1AF9E4;
	private static final int ACTOR_TATTLES = 0x1B1478;
	private static final int ACTOR_OFFSETS = 0x1B17C8;
	private static final int ACTOR_SOUNDS = 0x1AFD48;
	private static final int ACTOR_GROUP_DATA = 0x1B28E4;
	private static final int ACTOR_GROUP_POINTERS = 0x1B2924;

	public static final class ActorType
	{
		public String desc;

		public int ID;
		public int groupID;

		public int nameStringID;
		public int tattleStringID;

		public int[] sfxWalk = new int[2];
		public int[] sfxFly = new int[2];
		public int sfxJump;
		public int sfxHurt;
		public int[] sfxIncrement = new int[2];

		public byte shadowOffset;
		public byte[] tattleCamOffset;

		private ActorType()
		{
			tattleCamOffset = new byte[3];
			groupID = 0xFF;
		}

		@Override
		public String toString()
		{
			return String.format("%02X %02X %08X %08X (%d %d %d) %d",
				ID, groupID, nameStringID, tattleStringID,
				tattleCamOffset[0], tattleCamOffset[1], tattleCamOffset[2],
				shadowOffset);
		}

		@Override
		public boolean equals(Object obj)
		{
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			ActorType other = (ActorType) obj;
			if (ID != other.ID)
				return false;
			if (groupID != other.groupID)
				return false;
			if (nameStringID != other.nameStringID)
				return false;
			if (shadowOffset != other.shadowOffset)
				return false;
			if (!Arrays.equals(tattleCamOffset, other.tattleCamOffset))
				return false;
			if (tattleStringID != other.tattleStringID)
				return false;
			return true;
		}
	}

	/*
	public static void main(String[] args) throws IOException
	{
		Globals.initialize();
		File xmlFile = new File(DUMP_BATTLE + FN_BATTLE_ACTORS);

		List<ActorType> types1 = readROM();
		writeXML(types1, xmlFile);
		List<ActorType> types2 = readXML(xmlFile);

		assert(types1.size() == types2.size());
		for(int i = 0; i < types1.size(); i++)
			assert(types1.get(i).equals(types2.get(i)));

		Globals.exit();
	}
	 */

	public static void dump() throws IOException
	{
		Logger.log("Dumping actor types to XML file...");
		List<ActorType> types = readROM();
		writeXML(types, new File(DUMP_BATTLE + FN_BATTLE_ACTORS));
	}

	public static void patch(Patcher patcher, RomPatcher fp) throws IOException
	{
		List<ActorType> types = readXML(new File(MOD_BATTLE + FN_BATTLE_ACTORS));
		writeROM(patcher, fp, types);
	}

	public static List<ActorType> load() throws IOException
	{
		return readXML(new File(MOD_BATTLE + FN_BATTLE_ACTORS));
	}

	public static void save(List<ActorType> types) throws IOException
	{
		writeXML(types, new File(MOD_BATTLE + FN_BATTLE_ACTORS));
	}

	private static List<ActorType> readROM() throws IOException
	{
		ArrayList<ActorType> types = new ArrayList<>(ACTOR_TYPE_NUM);
		for (int i = 0; i < ACTOR_TYPE_NUM; i++) {
			types.add(new ActorType());
			types.get(i).ID = i;
		}

		ByteBuffer bb = Environment.getBaseRomBuffer();

		for (int j = 0;; j += 4) {
			bb.position(ACTOR_GROUP_POINTERS + j);
			int addr = bb.getInt();
			if (addr == 0)
				break;
			bb.position(addr - 0x800D1720);
			int id = bb.get() & 0xFF;
			int groupID = id;
			do {
				types.get(id).groupID = groupID;
				id = bb.get() & 0xFF;
			}
			while (id != 0xFF);
		}

		bb.position(ACTOR_SOUNDS);
		for (int i = 0; i < types.size(); i++) {
			ActorType type = types.get(i);
			type.sfxWalk[0] = bb.getInt();
			type.sfxWalk[1] = bb.getInt();
			type.sfxFly[0] = bb.getInt();
			type.sfxFly[1] = bb.getInt();
			type.sfxJump = bb.getInt();
			type.sfxHurt = bb.getInt();
			type.sfxIncrement[0] = bb.getShort();
			type.sfxIncrement[1] = bb.getShort();
		}

		bb.position(ACTOR_NAMES);
		for (int i = 0; i < types.size(); i++)
			types.get(i).nameStringID = bb.getInt();

		bb.position(ACTOR_TATTLES);
		for (int i = 0; i < types.size(); i++)
			types.get(i).tattleStringID = bb.getInt();

		bb.position(ACTOR_OFFSETS);
		for (int i = 0; i < types.size(); i++) {
			ActorType type = types.get(i);
			type.tattleCamOffset[0] = bb.get();
			type.tattleCamOffset[1] = bb.get();
			type.tattleCamOffset[2] = bb.get();
			type.shadowOffset = bb.get();
		}

		for (ActorType type : types)
			type.desc = ProjectDatabase.getActorName(type.ID);

		return types;
	}

	private static void writeROM(Patcher patcher, RomPatcher fp, List<ActorType> types) throws IOException
	{
		if (types.size() > 255)
			throw new RuntimeException("Error: tried to write more than 255 actor types.");

		writeActorsGroups(fp, types);

		fp.clear(ACTOR_NAMES, ACTOR_NAMES + 4 * ACTOR_TYPE_NUM);
		fp.clear(ACTOR_TATTLES, ACTOR_TATTLES + 4 * ACTOR_TYPE_NUM);
		fp.clear(ACTOR_OFFSETS, ACTOR_OFFSETS + 4 * ACTOR_TYPE_NUM);

		if (types.size() <= ACTOR_TYPE_NUM)
			writeModifyActors(patcher, fp, types);
		else
			writeRelocateActors(patcher, fp, types);

		// this table is not relocated, only (possibly) expanded
		fp.seek("Actor Type Sounds", ACTOR_SOUNDS);
		for (int i = 0; i < types.size(); i++) {
			ActorType type = types.get(i);
			fp.writeInt(type.sfxWalk[0]);
			fp.writeInt(type.sfxWalk[1]);
			fp.writeInt(type.sfxFly[0]);
			fp.writeInt(type.sfxFly[1]);
			fp.writeInt(type.sfxJump);
			fp.writeInt(type.sfxHurt);
			fp.writeShort(type.sfxIncrement[0]);
			fp.writeShort(type.sfxIncrement[1]);
		}
	}

	private static final class Group
	{
		List<Integer> members;
		int pointer;

		private Group()
		{
			members = new LinkedList<>();
		}
	}

	private static void writeActorsGroups(RomPatcher rp, List<ActorType> types) throws IOException
	{
		// get actor groups
		TreeMap<Integer, Group> groupMap = new TreeMap<>();
		int totalGroupBytes = 0;
		for (int i = 0; i < types.size(); i++) {
			ActorType type = types.get(i);
			if (type.groupID != 0xFF) {
				Group g;
				if (groupMap.containsKey(type.groupID)) {
					g = groupMap.get(type.groupID);
				}
				else {
					g = new Group();
					groupMap.put(type.groupID, g);
				}
				g.members.add(type.ID);
				totalGroupBytes++;
			}
		}
		totalGroupBytes += groupMap.size();

		// clear old group data
		rp.clear(ACTOR_GROUP_DATA, ACTOR_GROUP_DATA + 0x70);

		// write groups
		int sizeLimit = (groupMap.size() > 11) ? 0x70 : 0x40;
		if (totalGroupBytes > sizeLimit) {
			rp.seek("Actor Groups", rp.nextAlignedOffset());
			for (Group g : groupMap.values()) {
				g.pointer = rp.toAddress(rp.getCurrentOffset());
				for (int i : g.members)
					rp.writeByte(i);
				rp.writeByte(0xFF);
			}
		}
		else {
			rp.seek("Actor Groups", ACTOR_GROUP_DATA);
			for (Group g : groupMap.values()) {
				g.pointer = 0x800D1720 + rp.getCurrentOffset();
				for (int i : g.members)
					rp.writeByte(i);
				rp.writeByte(0xFF);
			}
		}

		// position group pointer list
		if (groupMap.size() > 11) {
			int groupsLocation = rp.nextAlignedOffset();

			int addr = rp.toAddress(groupsLocation);
			int upper = (addr >>> 16);
			int lower = addr & 0x0000FFFF;

			rp.seek("Actor Groups Reference", 0x182614); // 80253D34
			rp.writeInt(0x3C050000 | upper);
			rp.writeInt(0x24A50000 | lower);

			rp.seek("Actor Groups Reference", 0x18277C); // 80253E9C
			rp.writeInt(0x3C050000 | upper);
			rp.writeInt(0x24A50000 | lower);

			rp.seek("Actor Group Pointers", groupsLocation);
		}
		else
			rp.seek("Actor Group Pointers", ACTOR_GROUP_POINTERS);

		// write group pointers
		for (Group g : groupMap.values())
			rp.writeInt(g.pointer);
		rp.writeInt(0);
	}

	private static void writeModifyActors(Patcher patcher, RomPatcher fp, List<ActorType> types) throws IOException
	{
		patcher.setGlobalPointer(DefaultGlobals.ACTOR_NAME_TABLE, 0x80281104);
		fp.seek("Actor Type Names", ACTOR_NAMES);
		for (int i = 0; i < types.size(); i++)
			fp.writeInt(types.get(i).nameStringID);

		patcher.setGlobalPointer(DefaultGlobals.ACTOR_TATTLE_TABLE, 0x80282B98);
		fp.seek("Actor Type Tattles", ACTOR_TATTLES);
		for (int i = 0; i < types.size(); i++)
			fp.writeInt(types.get(i).tattleStringID);

		patcher.setGlobalPointer(DefaultGlobals.ACTOR_OFFSETS_TABLE, 0x80282EE8);
		fp.seek("Actor Type Offsets", ACTOR_OFFSETS);
		for (int i = 0; i < types.size(); i++) {
			ActorType type = types.get(i);
			fp.writeByte(type.tattleCamOffset[0]);
			fp.writeByte(type.tattleCamOffset[1]);
			fp.writeByte(type.tattleCamOffset[2]);
			fp.writeByte(type.shadowOffset);
		}
	}

	private static void writeRelocateActors(Patcher patcher, RomPatcher rp, List<ActorType> types) throws IOException
	{
		int actorNameTableBase = rp.nextAlignedOffset();
		patcher.setGlobalPointer(DefaultGlobals.ACTOR_NAME_TABLE, rp.toAddress(actorNameTableBase));
		rp.seek("Actor Type Names", actorNameTableBase);
		for (int i = 0; i < types.size(); i++)
			rp.writeInt(types.get(i).nameStringID);

		int actorTattleTableBase = rp.nextAlignedOffset();
		patcher.setGlobalPointer(DefaultGlobals.ACTOR_TATTLE_TABLE, rp.toAddress(actorTattleTableBase));
		rp.seek("Actor Type Tattles", actorTattleTableBase);
		for (int i = 0; i < types.size(); i++)
			rp.writeInt(types.get(i).tattleStringID);

		int actorOffsetsTableBase = rp.nextAlignedOffset();
		patcher.setGlobalPointer(DefaultGlobals.ACTOR_OFFSETS_TABLE, rp.toAddress(actorOffsetsTableBase));
		rp.seek("Actor Type Offsets", actorOffsetsTableBase);
		for (int i = 0; i < types.size(); i++) {
			ActorType type = types.get(i);
			rp.writeByte(type.tattleCamOffset[0]);
			rp.writeByte(type.tattleCamOffset[1]);
			rp.writeByte(type.tattleCamOffset[2]);
			rp.writeByte(type.shadowOffset);
		}

		int addr, upper, lower;

		// name string table
		addr = rp.toAddress(actorNameTableBase);
		upper = (addr >>> 16);
		lower = addr & 0x0000FFFF;

		rp.seek("Actor Names Reference", 0x41FE14); // 802AB084
		rp.writeInt(0x3C040000 | upper);
		rp.skip(4);
		rp.writeInt(0x8C840000 | lower);

		rp.seek("Actor Names Reference", 0x41FFCC); // 802AB23C
		rp.writeInt(0x3C040000 | upper);
		rp.skip(4);
		rp.writeInt(0x8C840000 | lower);

		// position offset table
		addr = rp.toAddress(actorOffsetsTableBase);
		upper = (addr >>> 16);
		lower = addr & 0x0000FFFF;

		// 80282EEB --> 80282EE8 + 3
		rp.seek("Actor Offsets Reference", 0x1847AC); // 80255ECC
		rp.writeInt(0x3C010000 | upper);
		rp.skip(4);
		rp.writeInt(0x80220000 | (lower + 3));
	}

	protected static enum ActorKey implements XmlKey
	{
		// @formatter:off
		TAG_ROOT		("ActorTypes"),
		TAG_ACTOR		("ActorType"),
		ATTR_DESC		("desc"),
		ATTR_ID			("id"),
		ATTR_GROUP		("group"),
		ATTR_NAME		("name"),
		ATTR_TATTLE		("tattle"),
		ATTR_TATTLE_POS	("tattleCamOffset"),
		ATTR_SHADOW_POS	("shadowOffset"),
		ATTR_SFX_WALK	("sfxWalk"),
		ATTR_SFX_FLY	("sfxFly"),
		ATTR_SFX_JUMP	("sfxJump"),
		ATTR_SFX_HURT	("sfxHurt"),
		ATTR_INC		("sfxIncrement");
		// @formatter:on

		private final String key;

		private ActorKey(String key)
		{
			this.key = key;
		}

		@Override
		public String toString()
		{
			return key;
		}
	}

	private static List<ActorType> readXML(File xmlFile) throws IOException
	{
		ArrayList<ActorType> types = new ArrayList<>(255);

		XmlReader xmr = new XmlReader(xmlFile);

		NodeList nodes = xmr.getRootElements(TAG_ACTOR);
		if (nodes.getLength() > 255)
			xmr.complain("You cannot define more than 255 ActorTypes.");
		if (nodes.getLength() < 1)
			xmr.complain("No ActorTypes are defined.");

		for (int i = 0; i < nodes.getLength(); i++) {
			ActorType type = new ActorType();
			types.add(type);

			Element actorElement = (Element) nodes.item(i);

			xmr.requiresAttribute(actorElement, ATTR_ID);
			xmr.requiresAttribute(actorElement, ATTR_NAME);
			xmr.requiresAttribute(actorElement, ATTR_TATTLE);

			if (xmr.hasAttribute(actorElement, ATTR_DESC))
				type.desc = xmr.getAttribute(actorElement, ATTR_DESC);

			type.ID = xmr.readHex(actorElement, ATTR_ID);
			type.nameStringID = xmr.readHex(actorElement, ATTR_NAME);
			type.tattleStringID = xmr.readHex(actorElement, ATTR_TATTLE);

			if (xmr.hasAttribute(actorElement, ATTR_GROUP))
				type.groupID = xmr.readHex(actorElement, ATTR_GROUP);

			if (xmr.hasAttribute(actorElement, ATTR_TATTLE_POS))
				type.tattleCamOffset = xmr.readByteArray(actorElement, ATTR_TATTLE_POS, 3);

			if (xmr.hasAttribute(actorElement, ATTR_SHADOW_POS))
				type.shadowOffset = (byte) xmr.readInt(actorElement, ATTR_SHADOW_POS);

			if (xmr.hasAttribute(actorElement, ATTR_SFX_WALK))
				type.sfxWalk = xmr.readHexArray(actorElement, ATTR_SFX_WALK, 2);

			if (xmr.hasAttribute(actorElement, ATTR_SFX_FLY))
				type.sfxFly = xmr.readHexArray(actorElement, ATTR_SFX_FLY, 2);

			if (xmr.hasAttribute(actorElement, ATTR_SFX_JUMP))
				type.sfxJump = xmr.readHex(actorElement, ATTR_SFX_JUMP);

			if (xmr.hasAttribute(actorElement, ATTR_SFX_HURT))
				type.sfxHurt = xmr.readHex(actorElement, ATTR_SFX_HURT);

			if (xmr.hasAttribute(actorElement, ATTR_INC))
				type.sfxIncrement = xmr.readIntArray(actorElement, ATTR_INC, 2);
		}

		return types;
	}

	private static void writeXML(List<ActorType> types, File xmlFile) throws FileNotFoundException
	{
		try (XmlWriter xmw = new XmlWriter(xmlFile,
			"You can define up to 255 actor types (0-FE). Read the docs for more information.")) {
			XmlTag root = xmw.createTag(TAG_ROOT, false);
			xmw.openTag(root);

			for (ActorType type : types) {
				XmlTag actor = xmw.createTag(TAG_ACTOR, true);

				xmw.addAttribute(actor, ATTR_DESC, type.desc);
				xmw.addAttribute(actor, ATTR_ID, "%02X", type.ID);

				if (type.groupID != 0xFF)
					xmw.addAttribute(actor, ATTR_GROUP, "%02X", type.groupID);

				xmw.addAttribute(actor, ATTR_NAME, "%08X", type.nameStringID);
				xmw.addAttribute(actor, ATTR_TATTLE, "%08X", type.tattleStringID);

				if (type.tattleCamOffset[0] != 0 || type.tattleCamOffset[1] != 0 || type.tattleCamOffset[2] != 0)
					xmw.addByteArray(actor, ATTR_TATTLE_POS, type.tattleCamOffset);

				if (type.shadowOffset != 0)
					xmw.addAttribute(actor, ATTR_SHADOW_POS, "%d", type.shadowOffset);

				if (type.sfxWalk[0] != 0 || type.sfxWalk[1] != 0)
					xmw.addHexArray(actor, ATTR_SFX_WALK, type.sfxWalk);

				if (type.sfxFly[0] != 0 || type.sfxFly[1] != 0)
					xmw.addHexArray(actor, ATTR_SFX_FLY, type.sfxFly);

				if (type.sfxJump != 0)
					xmw.addHex(actor, ATTR_SFX_JUMP, type.sfxJump);

				if (type.sfxHurt != 0)
					xmw.addHex(actor, ATTR_SFX_HURT, type.sfxHurt);

				if (type.sfxIncrement[0] != 0 || type.sfxIncrement[1] != 0)
					xmw.addIntArray(actor, ATTR_INC, type.sfxIncrement);

				xmw.printTag(actor);
			}

			xmw.closeTag(root);
			xmw.save();
		}
	}
}
