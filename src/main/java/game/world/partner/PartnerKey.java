package game.world.partner;

import util.xml.XmlKey;

public enum PartnerKey implements XmlKey
{
	// @formatter:off
	TAG_ROOT		("Partners"),
	TAG_PARTNER		("Partner"),

	TAG_WORLD		("World"),
	ATTR_NAME		("name"),
	ATTR_IDLE		("idleAnim"),
	ATTR_FLYING		("isFlying"),
	ATTR_INIT		("initFunction"),

	ATTR_FULL_DESC		("fullDesc"),
	ATTR_ABILITY_DESC	("abilityDesc"),
	ATTR_BATTLE_DESC	("battleDesc"),

	TAG_MENU		("PauseMenu"),
	ATTR_PORTRAIT		("portraitName"),

	TAG_SCRIPTS				("Scripts"),
	ATTR_SCRIPT_TAKE_OUT	("onTakeOut"),
	ATTR_SCRIPT_USE_ABILITY	("useAbility"),
	ATTR_SCRIPT_UPDATE		("onUpdate"),
	ATTR_SCRIPT_PUT_AWAY	("onPutAway"),
	ATTR_SCRIPT_RIDE		("whileRiding"),

	TAG_CALLBACKS				("Callbacks"),
	ATTR_FUNC_TEST_COLLISION	("testCollision"),
	ATTR_FUNC_CAN_USE			("canUseAbility"),
	ATTR_FUNC_CAN_PAUSE			("canPause"),
	ATTR_FUNC_BEFORE_BATTLE		("beforeBattle"),
	ATTR_FUNC_AFTER_BATTLE		("afterBattle"),

	TAG_ANIMS				("Anims"),
	ATTR_ANIM_DEFAULT		("default"),
	ATTR_ANIM_IDLE			("idle"),
	ATTR_ANIM_SPEAK			("speak"),
	ATTR_ANIM_WALK			("walk"),
	ATTR_ANIM_RUN			("run"),
	ATTR_ANIM_FLY			("fly"),
	ATTR_ANIM_FALL			("fall"),
	ATTR_ANIM_JUMP			("jump"),
	ATTR_ANIM_HURT			("hurt");
	// @formatter:on

	private final String key;

	private PartnerKey(String key)
	{
		this.key = key;
	}

	@Override
	public String toString()
	{
		return key;
	}
}
