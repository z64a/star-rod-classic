package game.battle.editor;

import static game.battle.editor.BattleKey.*;

import java.nio.ByteBuffer;

import org.w3c.dom.Element;

import util.IterableListModel;
import util.xml.XmlWrapper.XmlReader;
import util.xml.XmlWrapper.XmlSerializable;
import util.xml.XmlWrapper.XmlTag;
import util.xml.XmlWrapper.XmlWriter;

public class Stage implements XmlSerializable
{
	public String name = "";
	public String texName = "";
	public String bgName = "";
	public String shapeName = "";
	public String hitName = "";

	public IterableListModel<Integer> modelList = new IterableListModel<>();

	public String beforeBattleScript = "";
	public String afterBattleScript = "";

	public String specialFormationName = "";

	public boolean includeInStageTable = false;

	public Stage(BattleSection section, ByteBuffer fileBuffer, int offset)
	{
		fileBuffer.position(offset);

		int ptrTex = fileBuffer.getInt();
		int ptrShape = fileBuffer.getInt();
		int ptrHit = fileBuffer.getInt();
		int ptrBeforeScript = fileBuffer.getInt();
		int ptrAfterScript = fileBuffer.getInt();
		int ptrBackground = fileBuffer.getInt();
		int ptrForegroundList = fileBuffer.getInt();
		fileBuffer.getInt(); // special formation size
		int ptrSpecialFormation = fileBuffer.getInt();
		fileBuffer.getInt(); //TODO

		if (ptrTex != 0)
			texName = BattleSection.readString(fileBuffer, section.toOffset(ptrTex));

		if (ptrShape != 0)
			shapeName = BattleSection.readString(fileBuffer, section.toOffset(ptrShape));

		if (ptrHit != 0)
			hitName = BattleSection.readString(fileBuffer, section.toOffset(ptrHit));

		if (ptrBackground != 0)
			bgName = BattleSection.readString(fileBuffer, section.toOffset(ptrBackground));

		if (ptrBeforeScript != 0)
			beforeBattleScript = section.getPointerName("Script", ptrBeforeScript);

		if (ptrAfterScript != 0)
			afterBattleScript = section.getPointerName("Script", ptrAfterScript);

		if (ptrSpecialFormation != 0)
			specialFormationName = section.getPointerName("SpecialFormation", ptrSpecialFormation);

		if (ptrForegroundList != 0) {
			int v;
			fileBuffer.position(section.toOffset(ptrForegroundList));
			while ((v = fileBuffer.getInt()) != 0)
				modelList.addElement(v);
		}
	}

	public Stage(XmlReader xmr, Element elem)
	{
		fromXML(xmr, elem);
	}

	@Override
	public void fromXML(XmlReader xmr, Element elem)
	{
		if (xmr.hasAttribute(elem, ATTR_NAME))
			name = xmr.getAttribute(elem, ATTR_NAME);
		if (xmr.hasAttribute(elem, ATTR_EXCLUDE))
			includeInStageTable = !xmr.readBoolean(elem, ATTR_EXCLUDE);
		if (xmr.hasAttribute(elem, ATTR_STAGE_FORMATION))
			specialFormationName = xmr.getAttribute(elem, ATTR_STAGE_FORMATION);

		Element assetsElem = xmr.getUniqueRequiredTag(elem, TAG_ASSETS);
		if (xmr.hasAttribute(assetsElem, ATTR_STAGE_TEX))
			texName = xmr.getAttribute(assetsElem, ATTR_STAGE_TEX);
		if (xmr.hasAttribute(assetsElem, ATTR_STAGE_BG))
			bgName = xmr.getAttribute(assetsElem, ATTR_STAGE_BG);
		if (xmr.hasAttribute(assetsElem, ATTR_STAGE_SHAPE))
			shapeName = xmr.getAttribute(assetsElem, ATTR_STAGE_SHAPE);
		if (xmr.hasAttribute(assetsElem, ATTR_STAGE_HIT))
			hitName = xmr.getAttribute(assetsElem, ATTR_STAGE_HIT);

		if (xmr.hasAttribute(assetsElem, ATTR_STAGE_FOREGROUND)) {
			int[] modelIDs = xmr.readHexArray(assetsElem, ATTR_STAGE_FOREGROUND, -1);
			for (int id : modelIDs)
				modelList.addElement(id);
		}

		Element scriptsElem = xmr.getUniqueRequiredTag(elem, TAG_SCRIPTS);
		if (xmr.hasAttribute(scriptsElem, ATTR_STAGE_PRE_SCRIPT))
			beforeBattleScript = xmr.getAttribute(scriptsElem, ATTR_STAGE_PRE_SCRIPT);
		if (xmr.hasAttribute(scriptsElem, ATTR_STAGE_POST_SCRIPT))
			afterBattleScript = xmr.getAttribute(scriptsElem, ATTR_STAGE_POST_SCRIPT);
	}

	@Override
	public void toXML(XmlWriter xmw)
	{
		XmlTag stageTag = xmw.createTag(TAG_STAGE, false);
		if (!name.isEmpty())
			xmw.addAttribute(stageTag, ATTR_NAME, name);
		if (!includeInStageTable)
			xmw.addBoolean(stageTag, ATTR_EXCLUDE, !includeInStageTable);
		if (!specialFormationName.isEmpty())
			xmw.addAttribute(stageTag, ATTR_STAGE_FORMATION, specialFormationName);
		xmw.openTag(stageTag);

		XmlTag assetsTag = xmw.createTag(TAG_ASSETS, true);
		if (!texName.isEmpty())
			xmw.addAttribute(assetsTag, ATTR_STAGE_TEX, texName);
		if (!bgName.isEmpty())
			xmw.addAttribute(assetsTag, ATTR_STAGE_BG, bgName);
		if (!shapeName.isEmpty())
			xmw.addAttribute(assetsTag, ATTR_STAGE_SHAPE, shapeName);
		if (!hitName.isEmpty())
			xmw.addAttribute(assetsTag, ATTR_STAGE_HIT, hitName);

		if (modelList.size() > 0) {
			int[] modelIDs = new int[modelList.size()];
			for (int i = 0; i < modelIDs.length; i++)
				modelIDs[i] = modelList.get(i);
			xmw.addHexArray(assetsTag, ATTR_STAGE_FOREGROUND, modelIDs);
		}

		xmw.printTag(assetsTag);

		XmlTag scriptsTag = xmw.createTag(TAG_SCRIPTS, true);
		if (!beforeBattleScript.isEmpty())
			xmw.addAttribute(scriptsTag, ATTR_STAGE_PRE_SCRIPT, beforeBattleScript);
		if (!afterBattleScript.isEmpty())
			xmw.addAttribute(scriptsTag, ATTR_STAGE_POST_SCRIPT, afterBattleScript);
		xmw.printTag(scriptsTag);

		xmw.closeTag(stageTag);
	}

	@Override
	public String toString()
	{
		return name;
	}
}
