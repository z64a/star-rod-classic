package game.battle.editor;

import static game.battle.editor.BattleKey.*;

import java.nio.ByteBuffer;

import org.w3c.dom.Element;

import util.IterableListModel;
import util.xml.XmlWrapper.XmlReader;
import util.xml.XmlWrapper.XmlSerializable;
import util.xml.XmlWrapper.XmlTag;
import util.xml.XmlWrapper.XmlWriter;

public class Formation implements XmlSerializable
{
	public String name = "";
	public String scriptName = "";
	public boolean includeInFormationTable = false;

	public String _stageName = "";
	public Stage stage;

	public IterableListModel<Unit> units = new IterableListModel<>();

	public Formation(XmlReader xmr, Element elem)
	{
		fromXML(xmr, elem);
	}

	public Formation(BattleSection section, ByteBuffer fileBuffer, int offset, int size)
	{
		int numUnits = size / 0x1C;
		for (int i = 0; i < numUnits; i++) {
			int unitOffset = offset + 0x1C * i;
			units.addElement(new Unit(section, fileBuffer, unitOffset));
		}
	}

	@Override
	public void fromXML(XmlReader xmr, Element elem)
	{
		name = xmr.getAttribute(elem, ATTR_NAME);
		if (xmr.hasAttribute(elem, ATTR_FORMATION_STAGE))
			_stageName = xmr.getAttribute(elem, ATTR_FORMATION_STAGE);
		if (xmr.hasAttribute(elem, ATTR_FORMATION_SCRIPT))
			scriptName = xmr.getAttribute(elem, ATTR_FORMATION_SCRIPT);
		if (xmr.hasAttribute(elem, ATTR_EXCLUDE))
			includeInFormationTable = !xmr.readBoolean(elem, ATTR_EXCLUDE);

		for (Element unitElem : xmr.getTags(elem, TAG_UNIT))
			units.addElement(new Unit(xmr, unitElem));
	}

	@Override
	public void toXML(XmlWriter xmw)
	{
		XmlTag formationTag = xmw.createTag(TAG_FORMATION, false);
		xmw.addAttribute(formationTag, ATTR_NAME, name);
		if (stage != null)
			xmw.addAttribute(formationTag, ATTR_FORMATION_STAGE, stage.name);
		if (!scriptName.isEmpty())
			xmw.addAttribute(formationTag, ATTR_FORMATION_SCRIPT, scriptName);
		if (!includeInFormationTable)
			xmw.addBoolean(formationTag, ATTR_EXCLUDE, !includeInFormationTable);

		xmw.openTag(formationTag);
		for (Unit unit : units)
			unit.toXML(xmw);
		xmw.closeTag(formationTag);
	}

	@Override
	public String toString()
	{
		return name;
	}
}
