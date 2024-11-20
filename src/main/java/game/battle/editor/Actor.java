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
import util.ui.FlagEditorPanel.Flag;
import util.xml.XmlKey;
import util.xml.XmlWrapper.XmlReader;
import util.xml.XmlWrapper.XmlSerializable;
import util.xml.XmlWrapper.XmlTag;
import util.xml.XmlWrapper.XmlWriter;

public class Actor extends UpdateProvider implements XmlSerializable
{
	private final Consumer<Object> notifyCallback = (o) -> {
		notifyListeners();
	};

	public String name = "";
	public String scriptName = "";
	public IterableListModel<ActorPart> parts = new IterableListModel<>();

	public EditableField<Integer> flags = EditableFieldFactory.create(0)
		.setCallback(notifyCallback).setName("Set Actor Flags").build();

	public EditableField<Integer> level = EditableFieldFactory.create(0)
		.setCallback(notifyCallback).setName("Set Actor Level").build();

	public EditableField<Integer> maxHP = EditableFieldFactory.create(0)
		.setCallback(notifyCallback).setName("Set Actor Max HP").build();

	public EditableField<Integer> coins = EditableFieldFactory.create(0)
		.setCallback(notifyCallback).setName("Set Coin Bonus").build();

	public EditableField<Integer> actorType = EditableFieldFactory.create(0)
		.setCallback(notifyCallback).setName("Set Actor Type").build();

	public EditableArrayField<Integer> size = EditableArrayFieldFactory.create(2, 0)
		.setCallback(notifyCallback).setName("Set Actor Size").build();

	public EditableArrayField<Integer> healthBarOffset = EditableArrayFieldFactory.create(2, 0)
		.setCallback(notifyCallback).setName("Set Health Bar Offset").build();

	public EditableArrayField<Integer> statusCounterOffset = EditableArrayFieldFactory.create(2, 0)
		.setCallback(notifyCallback).setName("Set Status Counter Offset").build();

	public EditableArrayField<Integer> statusIconOffset = EditableArrayFieldFactory.create(2, 0)
		.setCallback(notifyCallback).setName("Set Status Icon Offset").build();

	public EditableField<Integer> escape = EditableFieldFactory.create(0)
		.setCallback(notifyCallback).setName("Set Escape Effectiveness").build();

	public EditableField<Integer> airlift = EditableFieldFactory.create(0)
		.setCallback(notifyCallback).setName("Set Airlift Effectiveness").build();

	public EditableField<Integer> hurricane = EditableFieldFactory.create(0)
		.setCallback(notifyCallback).setName("Set Hurricane Effectiveness").build();

	public EditableField<Integer> item = EditableFieldFactory.create(0)
		.setCallback(notifyCallback).setName("Set Item Effectiveness").build();

	public EditableField<Integer> upAndAway = EditableFieldFactory.create(0)
		.setCallback(notifyCallback).setName("Set Up & Away Effectiveness").build();

	public EditableField<Integer> powerBounce = EditableFieldFactory.create(0)
		.setCallback(notifyCallback).setName("Set Power Bounce Effectiveness").build();

	public EditableField<Integer> weight = EditableFieldFactory.create(0)
		.setCallback(notifyCallback).setName("Set Spin Smash Weight").build();

	public static final Flag[] FLAGS = new Flag[] {
			new Flag(0x00000004, "Hide Shadow"),
			new Flag(0x00000200, "Flying"),
			new Flag(0x00000800, "Upside-Down"),
			new Flag(0x00004000, "Target-Only", "Has no AI; battle ends even if it hasn't been defeated"),
			new Flag(0x00040000, "Hide HP Bar"),
			new Flag(0x00200000, "Skip Attack Turn"),
			new Flag(0x00400000, "Cannot be targeted?"),
			new Flag(0x02000000, "Hide Damage Pop-up"),
	};

	public Actor(BattleSection section, ByteBuffer fileBuffer, int offset)
	{
		fileBuffer.position(offset);

		flags.set(fileBuffer.getInt());
		fileBuffer.get();
		actorType.set(fileBuffer.get() & 0xFF);
		level.set(fileBuffer.get() & 0xFF);
		maxHP.set(fileBuffer.get() & 0xFF);
		int numParts = fileBuffer.getShort() & 0xFFFF;
		fileBuffer.getShort();
		int ptrPartsTable = fileBuffer.getInt();
		int ptrScript = fileBuffer.getInt();
		int ptrStatusTable = fileBuffer.getInt(); //TODO

		scriptName = section.getPointerName("Script", ptrScript);

		escape.set(fileBuffer.get() & 0xFF);
		airlift.set(fileBuffer.get() & 0xFF);
		hurricane.set(fileBuffer.get() & 0xFF);
		item.set(fileBuffer.get() & 0xFF);
		upAndAway.set(fileBuffer.get() & 0xFF);
		weight.set(fileBuffer.get() & 0xFF);
		powerBounce.set(fileBuffer.get() & 0xFF);
		coins.set(fileBuffer.get() & 0xFF);

		size.set(0, fileBuffer.get() & 0xFF);
		size.set(1, fileBuffer.get() & 0xFF);

		healthBarOffset.set(0, (int) fileBuffer.get());
		healthBarOffset.set(1, (int) fileBuffer.get());

		statusCounterOffset.set(0, (int) fileBuffer.get());
		statusCounterOffset.set(1, (int) fileBuffer.get());

		statusIconOffset.set(0, (int) fileBuffer.get());
		statusIconOffset.set(1, (int) fileBuffer.get());

		if (ptrPartsTable != 0) {
			for (int i = 0; i < numParts; i++) {
				ActorPart part = new ActorPart(section, fileBuffer, section.toOffset(ptrPartsTable) + i * 0x24);
				part.name = String.format("Part_%X", i);
				parts.addElement(part);
			}
		}
	}

	public Actor(XmlReader xmr, Element actorElem)
	{
		fromXML(xmr, actorElem);
	}

	@Override
	public void toXML(XmlWriter xmw)
	{
		XmlTag actorTag = xmw.createTag(TAG_ACTOR, false);

		xmw.addAttribute(actorTag, ATTR_NAME, name);
		xmw.addHex(actorTag, ATTR_ACTOR_TYPE, actorType.get());
		xmw.addHex(actorTag, ATTR_FLAGS, flags.get());
		xmw.addAttribute(actorTag, ATTR_ACTOR_SCRIPT, scriptName);
		xmw.openTag(actorTag);

		XmlTag statsTag = xmw.createTag(TAG_STATS, true);
		xmw.addInt(statsTag, ATTR_ACTOR_MAXHP, maxHP.get());
		xmw.addInt(statsTag, ATTR_ACTOR_WEIGHT, weight.get());
		xmw.addInt(statsTag, ATTR_ACTOR_LEVEL, level.get());
		xmw.addInt(statsTag, ATTR_ACTOR_COINS, coins.get());
		xmw.printTag(statsTag);

		XmlTag layoutTag = xmw.createTag(TAG_LAYOUT, true);
		xmw.addIntArray(layoutTag, ATTR_ACTOR_SIZE, size.get(0), size.get(1));
		xmw.addIntArray(layoutTag, ATTR_ACTOR_HEALTHBAROFFSET, healthBarOffset.get(0), healthBarOffset.get(1));
		xmw.addIntArray(layoutTag, ATTR_ACTOR_COUNTEROFFSET, statusCounterOffset.get(0), statusCounterOffset.get(1));
		xmw.addIntArray(layoutTag, ATTR_ACTOR_ICONOFFSET, statusIconOffset.get(0), statusIconOffset.get(1));
		xmw.printTag(layoutTag);

		XmlTag weaknessTag = xmw.createTag(TAG_WEAKNESS, true);
		xmw.addInt(weaknessTag, ATTR_ACTOR_ESCAPE, escape.get());
		xmw.addInt(weaknessTag, ATTR_ACTOR_ITEM, item.get());
		xmw.addInt(weaknessTag, ATTR_ACTOR_POWERBOUNCE, powerBounce.get());
		xmw.addInt(weaknessTag, ATTR_ACTOR_AIRLIFT, airlift.get());
		xmw.addInt(weaknessTag, ATTR_ACTOR_HURRICANE, hurricane.get());
		xmw.addInt(weaknessTag, ATTR_ACTOR_UPANDAWAY, upAndAway.get());
		xmw.printTag(weaknessTag);

		//TODO status table

		XmlTag partsTag = xmw.createTag(TAG_PART_LIST, false);
		xmw.openTag(partsTag);
		for (ActorPart part : parts)
			part.toXML(xmw);
		xmw.closeTag(partsTag);

		xmw.closeTag(actorTag);
	}

	@Override
	public void fromXML(XmlReader xmr, Element elem)
	{
		name = xmr.getAttribute(elem, ATTR_NAME);
		actorType.set(xmr.readHex(elem, ATTR_ACTOR_TYPE));
		flags.set(xmr.readHex(elem, ATTR_FLAGS));
		scriptName = xmr.getAttribute(elem, ATTR_ACTOR_SCRIPT);

		Element statsElem = xmr.getUniqueRequiredTag(elem, TAG_STATS);
		maxHP.set(xmr.readInt(statsElem, ATTR_ACTOR_MAXHP));
		weight.set(xmr.readInt(statsElem, ATTR_ACTOR_WEIGHT));
		level.set(xmr.readInt(statsElem, ATTR_ACTOR_LEVEL));
		coins.set(xmr.readInt(statsElem, ATTR_ACTOR_COINS));

		Element layoutElem = xmr.getUniqueRequiredTag(elem, TAG_LAYOUT);
		readIntArrayField(xmr, layoutElem, ATTR_ACTOR_SIZE, size);
		readIntArrayField(xmr, layoutElem, ATTR_ACTOR_HEALTHBAROFFSET, healthBarOffset);
		readIntArrayField(xmr, layoutElem, ATTR_ACTOR_COUNTEROFFSET, statusCounterOffset);
		readIntArrayField(xmr, layoutElem, ATTR_ACTOR_ICONOFFSET, statusIconOffset);

		Element weaknessElem = xmr.getUniqueRequiredTag(elem, TAG_WEAKNESS);
		escape.set(xmr.readInt(weaknessElem, ATTR_ACTOR_ESCAPE));
		item.set(xmr.readInt(weaknessElem, ATTR_ACTOR_ITEM));
		powerBounce.set(xmr.readInt(weaknessElem, ATTR_ACTOR_POWERBOUNCE));
		airlift.set(xmr.readInt(weaknessElem, ATTR_ACTOR_AIRLIFT));
		hurricane.set(xmr.readInt(weaknessElem, ATTR_ACTOR_HURRICANE));
		upAndAway.set(xmr.readInt(weaknessElem, ATTR_ACTOR_UPANDAWAY));

		Element partsElem = xmr.getUniqueRequiredTag(elem, TAG_PART_LIST);
		for (Element unitElem : xmr.getTags(partsElem, TAG_PART))
			parts.addElement(new ActorPart(xmr, unitElem));
	}

	private static void readIntArrayField(XmlReader xmr, Element elem, XmlKey key, EditableArrayField<Integer> field)
	{
		int[] values = xmr.readIntArray(elem, key, field.length());
		for (int i = 0; i < values.length; i++)
			field.set(i, values[i]);
	}

	@Override
	public String toString()
	{
		return name;
	}
}
