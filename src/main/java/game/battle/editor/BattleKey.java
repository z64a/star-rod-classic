package game.battle.editor;

import util.xml.XmlKey;

public enum BattleKey implements XmlKey
{
	// @formatter:off
	ATTR_KEY			("key"),
	ATTR_VALUE			("value"),
	ATTR_NAME			("name"),
	ATTR_FLAGS			("flags"),
	ATTR_EXCLUDE		("excludeFromTable"),

	TAG_BATTLE_SECTION	("BattleSection"),

	ATTR_BASE_ADDRESS		("baseAddress"),

	TAG_STAGE			("Stage"),
	TAG_ASSETS			("Assets"),
	TAG_SCRIPTS			("Scripts"),
	ATTR_STAGE_TEX				("tex"),
	ATTR_STAGE_BG				("bg"),
	ATTR_STAGE_SHAPE			("shape"),
	ATTR_STAGE_HIT				("hit"),
	ATTR_STAGE_FOREGROUND		("foregound"),
	ATTR_STAGE_FORMATION		("formation"),
	ATTR_STAGE_PRE_SCRIPT		("beforeBattle"),
	ATTR_STAGE_POST_SCRIPT		("afterBattle"),

	TAG_FORMATION		("Formation"),
	TAG_UNIT			("Unit"),

	ATTR_FORMATION_STAGE		("stage"),
	ATTR_FORMATION_SCRIPT		("script"),

	ATTR_UNIT_ACTOR				("actor"),
	ATTR_UNIT_PRIORITY			("priority"),
	ATTR_UNIT_HOME_INDEX		("homeIndex"),
	ATTR_UNIT_HOME_VECTOR		("homeVector"),
	ATTR_UNIT_VARS				("vars"),

	TAG_ACTOR			("Actor"),

	ATTR_ACTOR_SCRIPT			("script"),
	ATTR_ACTOR_TYPE				("type"),

	TAG_STATS					("Stats"),
	ATTR_ACTOR_LEVEL			("level"),
	ATTR_ACTOR_COINS			("coins"),
	ATTR_ACTOR_MAXHP			("maxHP"),
	ATTR_ACTOR_WEIGHT			("weight"),

	TAG_WEAKNESS				("Weakness"),
	ATTR_ACTOR_ITEM				("items"),
	ATTR_ACTOR_ESCAPE			("escape"),
	ATTR_ACTOR_AIRLIFT			("airlift"),
	ATTR_ACTOR_HURRICANE		("hurricane"),
	ATTR_ACTOR_UPANDAWAY		("upAndAway"),
	ATTR_ACTOR_POWERBOUNCE		("powerBounce"),

	TAG_LAYOUT					("Layout"),
	ATTR_ACTOR_SIZE				("size"),
	ATTR_ACTOR_HEALTHBAROFFSET	("healthBarOffset"),
	ATTR_ACTOR_COUNTEROFFSET	("statusCounterOffset"),
	ATTR_ACTOR_ICONOFFSET		("statusIconOffset"),

	TAG_PART_LIST		("Parts"),
	TAG_PART			("Part"),

	TAG_IDLE_ANIM				("IdleAnim"),

	ATTR_PART_EVENT_FLAGS		("eventFlags"),
	ATTR_PART_IMMUNE_FLAGS		("immuneFlags"),
	ATTR_PART_SPRITE			("sprite"),
	ATTR_PART_PALETTE			("palette"),
	ATTR_PART_OPACITY			("opacity"),

	ATTR_PART_OFFSET			("offset"),
	ATTR_PART_TARGET_OFFSET		("targetOffset"),
	ATTR_PART_UNKNOWN_OFFSET	("unkOffset");
	// @formatter:on

	private final String key;

	private BattleKey(String key)
	{
		this.key = key;
	}

	@Override
	public String toString()
	{
		return key;
	}
}
