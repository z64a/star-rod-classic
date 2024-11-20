package game.world.partner;

import static game.world.partner.PartnerKey.*;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import app.input.InvalidInputException;
import game.shared.DataUtils;
import patcher.IGlobalDatabase;
import util.xml.XmlWrapper.XmlReader;
import util.xml.XmlWrapper.XmlTag;
import util.xml.XmlWrapper.XmlWriter;

public class PartnerConfig
{
	public static final String[] DEFAULT_PARTNER_NAMES = {
			"Goombario",
			"Kooper",
			"Bombette",
			"Parakarry",
			"Goompa",
			"Watt",
			"Sushie",
			"Lakilester",
			"Bow",
			"Goombaria",
			"Twink"
	};

	public String name = "";
	public int romStart;
	public int romEnd;

	public int idleAnim;
	public boolean isFlying;

	public String funcInit = "";
	public String scriptOnTakeOut = "";
	public String scriptUseAbility = "";
	public String scriptUpdate = "";
	public String scriptOnPutAway = "";
	public String funcTestEnemyCollision = "";
	public String funcCanUseAbility = "";
	public String funcPlayerCanPause = "";
	public String funcBeforeBattle = "";
	public String funcAfterBattle = "";
	public String scriptWhileRiding = "";

	public String fullDescString = "";
	public String abilityDescString = "";
	public String battleDescString = "";

	public String portraitName = "";

	public int[] anims = new int[9];
	public static final int ANIM_DEFAULT = 0;
	public static final int ANIM_WALK = 1;
	public static final int ANIM_JUMP = 2;
	public static final int ANIM_FALL = 3;
	public static final int ANIM_FLY = 4;
	public static final int ANIM_IDLE = 5;
	public static final int ANIM_RUN = 6;
	public static final int ANIM_SPEAK = 7;
	public static final int ANIM_HURT = 8;

	public PartnerTableEntry tableEntry;

	protected PartnerConfig()
	{}

	protected static void writeXML(List<PartnerConfig> partners, File xmlFile) throws FileNotFoundException
	{
		try (XmlWriter xmw = new XmlWriter(xmlFile,
			"You can't add new partners, but you can modify any of the existing ones.")) {
			XmlTag root = xmw.createTag(TAG_ROOT, false);
			xmw.openTag(root);

			for (PartnerConfig partner : partners) {
				XmlTag partnerTag = xmw.createTag(TAG_PARTNER, false);

				xmw.addAttribute(partnerTag, ATTR_NAME, partner.name);
				xmw.addAttribute(partnerTag, ATTR_FULL_DESC, partner.fullDescString);
				xmw.addAttribute(partnerTag, ATTR_ABILITY_DESC, partner.abilityDescString);
				xmw.addAttribute(partnerTag, ATTR_BATTLE_DESC, partner.battleDescString);
				xmw.addAttribute(partnerTag, ATTR_PORTRAIT, partner.portraitName);
				xmw.openTag(partnerTag);

				XmlTag worldTag = xmw.createTag(TAG_WORLD, false);

				xmw.addHex(worldTag, ATTR_IDLE, "%08X", partner.idleAnim);
				xmw.addBoolean(worldTag, ATTR_FLYING, partner.isFlying);
				xmw.addAttribute(worldTag, ATTR_INIT, partner.funcInit);

				xmw.openTag(worldTag);

				XmlTag scriptsTag = xmw.createTag(TAG_SCRIPTS, true);
				if (!partner.scriptUpdate.isEmpty())
					xmw.addAttribute(scriptsTag, ATTR_SCRIPT_UPDATE, partner.scriptUpdate);
				if (!partner.scriptUseAbility.isEmpty())
					xmw.addAttribute(scriptsTag, ATTR_SCRIPT_USE_ABILITY, partner.scriptUseAbility);
				if (!partner.scriptOnTakeOut.isEmpty())
					xmw.addAttribute(scriptsTag, ATTR_SCRIPT_TAKE_OUT, partner.scriptOnTakeOut);
				if (!partner.scriptOnPutAway.isEmpty())
					xmw.addAttribute(scriptsTag, ATTR_SCRIPT_PUT_AWAY, partner.scriptOnPutAway);
				if (!partner.scriptWhileRiding.isEmpty())
					xmw.addAttribute(scriptsTag, ATTR_SCRIPT_RIDE, partner.scriptWhileRiding);
				xmw.printTag(scriptsTag);

				XmlTag callbacksTag = xmw.createTag(TAG_CALLBACKS, true);
				if (!partner.funcCanUseAbility.isEmpty())
					xmw.addAttribute(callbacksTag, ATTR_FUNC_CAN_USE, partner.funcCanUseAbility);
				if (!partner.funcPlayerCanPause.isEmpty())
					xmw.addAttribute(callbacksTag, ATTR_FUNC_CAN_PAUSE, partner.funcPlayerCanPause);
				if (!partner.funcBeforeBattle.isEmpty())
					xmw.addAttribute(callbacksTag, ATTR_FUNC_BEFORE_BATTLE, partner.funcBeforeBattle);
				if (!partner.funcAfterBattle.isEmpty())
					xmw.addAttribute(callbacksTag, ATTR_FUNC_AFTER_BATTLE, partner.funcAfterBattle);
				if (!partner.funcTestEnemyCollision.isEmpty())
					xmw.addAttribute(callbacksTag, ATTR_FUNC_TEST_COLLISION, partner.funcTestEnemyCollision);
				xmw.printTag(callbacksTag);

				XmlTag animsTag = xmw.createTag(TAG_ANIMS, true);
				xmw.addHex(animsTag, ATTR_ANIM_DEFAULT, "%08X", partner.anims[ANIM_DEFAULT]);
				xmw.addHex(animsTag, ATTR_ANIM_IDLE, "%08X", partner.anims[ANIM_IDLE]);
				xmw.addHex(animsTag, ATTR_ANIM_SPEAK, "%08X", partner.anims[ANIM_SPEAK]);
				xmw.addHex(animsTag, ATTR_ANIM_WALK, "%08X", partner.anims[ANIM_WALK]);
				xmw.addHex(animsTag, ATTR_ANIM_RUN, "%08X", partner.anims[ANIM_RUN]);
				xmw.addHex(animsTag, ATTR_ANIM_FLY, "%08X", partner.anims[ANIM_FLY]);
				xmw.addHex(animsTag, ATTR_ANIM_FALL, "%08X", partner.anims[ANIM_FALL]);
				xmw.addHex(animsTag, ATTR_ANIM_JUMP, "%08X", partner.anims[ANIM_JUMP]);
				xmw.addHex(animsTag, ATTR_ANIM_HURT, "%08X", partner.anims[ANIM_HURT]);
				xmw.printTag(animsTag);

				xmw.closeTag(worldTag);

				xmw.closeTag(partnerTag);
			}

			xmw.closeTag(root);
			xmw.save();
		}
	}

	public static ArrayList<PartnerConfig> readXML(IGlobalDatabase globalsDatabase, File xmlFile) throws IOException
	{
		ArrayList<PartnerConfig> types = new ArrayList<>(255);

		XmlReader xmr = new XmlReader(xmlFile);

		NodeList nodes = xmr.getRootElements(TAG_PARTNER);
		if (nodes.getLength() != 11)
			xmr.complain("You must define exactly 11 partners.");

		for (int i = 0; i < nodes.getLength(); i++) {
			PartnerConfig partner = new PartnerConfig();
			types.add(partner);

			Element partnerElement = (Element) nodes.item(i);

			xmr.requiresAttribute(partnerElement, ATTR_NAME);
			partner.name = xmr.getAttribute(partnerElement, ATTR_NAME);
			xmr.requiresAttribute(partnerElement, ATTR_FULL_DESC);
			partner.fullDescString = xmr.getAttribute(partnerElement, ATTR_FULL_DESC);
			xmr.requiresAttribute(partnerElement, ATTR_ABILITY_DESC);
			partner.abilityDescString = xmr.getAttribute(partnerElement, ATTR_ABILITY_DESC);
			xmr.requiresAttribute(partnerElement, ATTR_BATTLE_DESC);
			partner.battleDescString = xmr.getAttribute(partnerElement, ATTR_BATTLE_DESC);
			xmr.requiresAttribute(partnerElement, ATTR_PORTRAIT);
			partner.portraitName = xmr.getAttribute(partnerElement, ATTR_PORTRAIT);

			Element worldElement = xmr.getUniqueRequiredTag(partnerElement, TAG_WORLD);

			xmr.requiresAttribute(worldElement, ATTR_FLYING);
			partner.isFlying = xmr.readBoolean(worldElement, ATTR_FLYING);
			xmr.requiresAttribute(worldElement, ATTR_IDLE);
			partner.idleAnim = resolveAnim(globalsDatabase, xmr, xmr.getAttribute(worldElement, ATTR_IDLE));

			xmr.requiresAttribute(worldElement, ATTR_INIT);
			partner.funcInit = xmr.getAttribute(worldElement, ATTR_INIT);

			Element scriptsElement = xmr.getUniqueRequiredTag(worldElement, TAG_SCRIPTS);
			if (xmr.hasAttribute(scriptsElement, ATTR_SCRIPT_TAKE_OUT))
				partner.scriptOnTakeOut = xmr.getAttribute(scriptsElement, ATTR_SCRIPT_TAKE_OUT);
			if (xmr.hasAttribute(scriptsElement, ATTR_SCRIPT_USE_ABILITY))
				partner.scriptUseAbility = xmr.getAttribute(scriptsElement, ATTR_SCRIPT_USE_ABILITY);
			if (xmr.hasAttribute(scriptsElement, ATTR_SCRIPT_UPDATE))
				partner.scriptUpdate = xmr.getAttribute(scriptsElement, ATTR_SCRIPT_UPDATE);
			if (xmr.hasAttribute(scriptsElement, ATTR_SCRIPT_PUT_AWAY))
				partner.scriptOnPutAway = xmr.getAttribute(scriptsElement, ATTR_SCRIPT_PUT_AWAY);
			if (xmr.hasAttribute(scriptsElement, ATTR_SCRIPT_RIDE))
				partner.scriptWhileRiding = xmr.getAttribute(scriptsElement, ATTR_SCRIPT_RIDE);

			Element callbacksElement = xmr.getUniqueRequiredTag(worldElement, TAG_CALLBACKS);
			if (xmr.hasAttribute(callbacksElement, ATTR_FUNC_CAN_USE))
				partner.funcCanUseAbility = xmr.getAttribute(callbacksElement, ATTR_FUNC_CAN_USE);
			if (xmr.hasAttribute(callbacksElement, ATTR_FUNC_CAN_PAUSE))
				partner.funcPlayerCanPause = xmr.getAttribute(callbacksElement, ATTR_FUNC_CAN_PAUSE);
			if (xmr.hasAttribute(callbacksElement, ATTR_FUNC_TEST_COLLISION))
				partner.funcTestEnemyCollision = xmr.getAttribute(callbacksElement, ATTR_FUNC_TEST_COLLISION);
			if (xmr.hasAttribute(callbacksElement, ATTR_FUNC_BEFORE_BATTLE))
				partner.funcBeforeBattle = xmr.getAttribute(callbacksElement, ATTR_FUNC_BEFORE_BATTLE);
			if (xmr.hasAttribute(callbacksElement, ATTR_FUNC_AFTER_BATTLE))
				partner.funcAfterBattle = xmr.getAttribute(callbacksElement, ATTR_FUNC_AFTER_BATTLE);

			Element animsElement = xmr.getUniqueRequiredTag(worldElement, TAG_ANIMS);

			for (int k = 0; k < partner.anims.length; k++)
				partner.anims[k] = partner.idleAnim;

			if (xmr.hasAttribute(animsElement, ATTR_ANIM_DEFAULT))
				partner.anims[ANIM_DEFAULT] = resolveAnim(globalsDatabase, xmr, xmr.getAttribute(animsElement, ATTR_ANIM_DEFAULT));
			if (xmr.hasAttribute(animsElement, ATTR_ANIM_IDLE))
				partner.anims[ANIM_IDLE] = resolveAnim(globalsDatabase, xmr, xmr.getAttribute(animsElement, ATTR_ANIM_IDLE));
			if (xmr.hasAttribute(animsElement, ATTR_ANIM_SPEAK))
				partner.anims[ANIM_SPEAK] = resolveAnim(globalsDatabase, xmr, xmr.getAttribute(animsElement, ATTR_ANIM_SPEAK));
			if (xmr.hasAttribute(animsElement, ATTR_ANIM_WALK))
				partner.anims[ANIM_WALK] = resolveAnim(globalsDatabase, xmr, xmr.getAttribute(animsElement, ATTR_ANIM_WALK));
			if (xmr.hasAttribute(animsElement, ATTR_ANIM_RUN))
				partner.anims[ANIM_RUN] = resolveAnim(globalsDatabase, xmr, xmr.getAttribute(animsElement, ATTR_ANIM_RUN));
			if (xmr.hasAttribute(animsElement, ATTR_ANIM_FLY))
				partner.anims[ANIM_FLY] = resolveAnim(globalsDatabase, xmr, xmr.getAttribute(animsElement, ATTR_ANIM_FLY));
			if (xmr.hasAttribute(animsElement, ATTR_ANIM_FALL))
				partner.anims[ANIM_FALL] = resolveAnim(globalsDatabase, xmr, xmr.getAttribute(animsElement, ATTR_ANIM_FALL));
			if (xmr.hasAttribute(animsElement, ATTR_ANIM_JUMP))
				partner.anims[ANIM_JUMP] = resolveAnim(globalsDatabase, xmr, xmr.getAttribute(animsElement, ATTR_ANIM_JUMP));
			if (xmr.hasAttribute(animsElement, ATTR_ANIM_HURT))
				partner.anims[ANIM_HURT] = resolveAnim(globalsDatabase, xmr, xmr.getAttribute(animsElement, ATTR_ANIM_HURT));
		}

		return types;
	}

	private static int resolveAnim(IGlobalDatabase globalsDatabase, XmlReader xmr, String in)
	{
		if (in.isEmpty())
			return 0;

		if (DataUtils.isInteger(in)) {
			try {
				return DataUtils.parseIntString(in);
			}
			catch (InvalidInputException e) {
				throw new IllegalStateException(); // implicitly checked by DataUtils::isInteger
			}
		}

		String[] tokens = in.split(":");

		String palName = (tokens.length == 3) ? tokens[2] : "";
		int id = globalsDatabase.getNpcAnimID(tokens[0], tokens[1], palName);
		if (id == -1)
			xmr.complain("Invalid NPC sprite name: " + tokens[0]);
		if (id == -2)
			xmr.complain("Invalid animation name for NPC sprite " + tokens[0] + ": " + tokens[1]);
		if (id == -3)
			xmr.complain("Invalid palette name for NPC sprite " + tokens[0] + ": " + tokens[2]);
		return id;
	}
}
