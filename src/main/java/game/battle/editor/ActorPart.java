package game.battle.editor;

import static game.battle.editor.BattleKey.*;

import java.nio.ByteBuffer;
import java.util.function.Consumer;

import org.w3c.dom.Element;

import game.map.editor.UpdateProvider;
import game.map.editor.commands.fields.EditableArrayField;
import game.map.editor.commands.fields.EditableArrayField.EditableArrayFieldFactory;
import game.map.editor.commands.fields.EditableField;
import game.map.editor.commands.fields.EditableField.EditableFieldFactory;
import util.IterableListModel;
import util.xml.XmlKey;
import util.xml.XmlWrapper.XmlReader;
import util.xml.XmlWrapper.XmlSerializable;
import util.xml.XmlWrapper.XmlTag;
import util.xml.XmlWrapper.XmlWriter;

public class ActorPart extends UpdateProvider implements XmlSerializable
{
	private final Consumer<Object> notifyCallback = (o) -> {
		notifyListeners();
	};

	String name = "";

	public EditableField<Integer> spriteID = EditableFieldFactory.create(0)
		.setCallback(notifyCallback).setName("Set Part Sprite").build();

	public EditableField<Integer> palID = EditableFieldFactory.create(0)
		.setCallback(notifyCallback).setName("Set Part Palette").build();

	public EditableField<Integer> opacity = EditableFieldFactory.create(0)
		.setCallback(notifyCallback).setName("Set Part Opacity").build();

	public EditableField<Integer> flags = EditableFieldFactory.create(0)
		.setCallback(notifyCallback).setName("Set Part Flags").build();

	public EditableField<Integer> eventFlags = EditableFieldFactory.create(0)
		.setCallback(notifyCallback).setName("Set Part Event Flags").build();

	public EditableField<Integer> immuneFlags = EditableFieldFactory.create(0)
		.setCallback(notifyCallback).setName("Set Part Immunity Flags").build();

	public EditableArrayField<Integer> posOffset = EditableArrayFieldFactory.create(3, 0)
		.setCallback(notifyCallback).setName("Set Part Positon Offset").build();

	public EditableArrayField<Integer> targetOffset = EditableArrayFieldFactory.create(2, 0)
		.setCallback(notifyCallback).setName("Set Part Target Offset").build();

	public EditableArrayField<Integer> unkOffset = EditableArrayFieldFactory.create(2, 0)
		.setCallback(notifyCallback).setName("Set Part Unknown Offset").build();

	IterableListModel<IdleAnimTableEntry> animationTable = new IterableListModel<>();
	IterableListModel<DefenseTableEntry> defenseTable = new IterableListModel<>();

	public static class IdleAnimTableEntry
	{
		public IdleAnimTableEntry(int key, int animID)
		{
			enumValue = key;
			this.animID = animID;
		}

		// store as integers internally, display as strings in the editor
		public final int enumValue;
		public int animID;
	}

	public static class DefenseTableEntry
	{
		int enumValue;
		int defenseVal;
	}

	public void setSprite(int spriteID)
	{
		this.spriteID.set(spriteID);
	}

	public ActorPart(BattleSection section, ByteBuffer fileBuffer, int offset)
	{
		fileBuffer.position(offset);

		flags.set(fileBuffer.getInt());
		fileBuffer.get(); // part index

		posOffset.set(0, (int) fileBuffer.get());
		posOffset.set(1, (int) fileBuffer.get());
		posOffset.set(2, (int) fileBuffer.get());

		targetOffset.set(0, (int) fileBuffer.get());
		targetOffset.set(1, (int) fileBuffer.get());

		fileBuffer.get(); // always zero (?)
		opacity.set(fileBuffer.get() & 0xFF);

		int ptrIdleAnims = fileBuffer.getInt();
		int ptrDefenseTable = fileBuffer.getInt(); // can be NULL

		eventFlags.set(fileBuffer.getInt());
		immuneFlags.set(fileBuffer.getInt());

		unkOffset.set(0, (int) fileBuffer.get());
		unkOffset.set(1, (int) fileBuffer.get());
		fileBuffer.get(); // always zero (?)
		fileBuffer.get(); // always zero (?)

		fileBuffer.get(); // always zero (?)
		fileBuffer.get(); // always zero (?)
		fileBuffer.get(); // always zero (?)
		fileBuffer.get(); // always zero (?)

		if (ptrIdleAnims != 0) // can be NULL
		{
			fileBuffer.position(section.toOffset(ptrIdleAnims));
			while (true) {
				int key = fileBuffer.getInt();
				int anim = fileBuffer.getInt();
				if (key == 0)
					break;
				animationTable.addElement(new IdleAnimTableEntry(key, anim & 0xFF));

				if (spriteID.get() == 0)
					spriteID.set(anim >> 16);

				int pal = (anim >> 8) & 0xFF;
				if (pal != 0)
					palID.set(pal);
			}
		}

		//TODO defense
	}

	public ActorPart(XmlReader xmr, Element elem)
	{
		fromXML(xmr, elem);
	}

	@Override
	public void toXML(XmlWriter xmw)
	{
		XmlTag partTag = xmw.createTag(TAG_PART, false);
		xmw.addAttribute(partTag, ATTR_NAME, name);

		xmw.addHex(partTag, ATTR_PART_SPRITE, spriteID.get());
		if (palID.get() > 0)
			xmw.addHex(partTag, ATTR_PART_PALETTE, palID.get());
		xmw.addInt(partTag, ATTR_PART_OPACITY, opacity.get());

		xmw.addHex(partTag, ATTR_FLAGS, flags.get());
		xmw.addHex(partTag, ATTR_PART_EVENT_FLAGS, eventFlags.get());
		xmw.addHex(partTag, ATTR_PART_IMMUNE_FLAGS, immuneFlags.get());

		xmw.openTag(partTag);

		XmlTag layoutTag = xmw.createTag(TAG_LAYOUT, true);
		xmw.addIntArray(layoutTag, ATTR_PART_OFFSET, posOffset.get(0), posOffset.get(1), posOffset.get(2));
		xmw.addIntArray(layoutTag, ATTR_PART_TARGET_OFFSET, targetOffset.get(0), targetOffset.get(1));
		xmw.addIntArray(layoutTag, ATTR_PART_UNKNOWN_OFFSET, unkOffset.get(0), unkOffset.get(1));
		xmw.printTag(layoutTag);

		for (IdleAnimTableEntry e : animationTable) {
			XmlTag animTag = xmw.createTag(TAG_IDLE_ANIM, true);
			xmw.addHex(animTag, ATTR_KEY, e.enumValue);
			xmw.addHex(animTag, ATTR_VALUE, e.animID);
			xmw.printTag(animTag);
		}

		xmw.closeTag(partTag);

		//TODO defense
	}

	@Override
	public void fromXML(XmlReader xmr, Element elem)
	{
		name = xmr.getAttribute(elem, ATTR_NAME);
		flags.set(xmr.readHex(elem, ATTR_FLAGS));
		eventFlags.set(xmr.readHex(elem, ATTR_PART_EVENT_FLAGS));
		immuneFlags.set(xmr.readHex(elem, ATTR_PART_IMMUNE_FLAGS));
		spriteID.set(xmr.readHex(elem, ATTR_PART_SPRITE));
		if (xmr.hasAttribute(elem, ATTR_PART_PALETTE))
			palID.set(xmr.readHex(elem, ATTR_PART_PALETTE));
		opacity.set(xmr.readInt(elem, ATTR_PART_OPACITY));

		Element layoutElem = xmr.getUniqueRequiredTag(elem, TAG_LAYOUT);
		readIntArrayField(xmr, layoutElem, ATTR_PART_OFFSET, posOffset);
		readIntArrayField(xmr, layoutElem, ATTR_PART_TARGET_OFFSET, targetOffset);
		readIntArrayField(xmr, layoutElem, ATTR_PART_UNKNOWN_OFFSET, unkOffset);

		for (Element animElem : xmr.getTags(elem, TAG_IDLE_ANIM)) {
			int key = xmr.readHex(animElem, ATTR_KEY);
			int anim = xmr.readHex(animElem, ATTR_VALUE);
			animationTable.addElement(new IdleAnimTableEntry(key, anim));
		}

		//TODO defense
	}

	private static void readIntArrayField(XmlReader xmr, Element elem, XmlKey key, EditableArrayField<Integer> field)
	{
		int[] values = xmr.readIntArray(elem, key, field.length());
		for (int i = 0; i < values.length; i++)
			field.set(i, values[i]);
	}
}
