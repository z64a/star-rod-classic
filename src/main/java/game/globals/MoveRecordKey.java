package game.globals;

import util.xml.XmlKey;

public enum MoveRecordKey implements XmlKey
{
	// @formatter:off
	TAG_ROOT		("Moves"),
	TAG_MOVE		("Move"),
	ATTR_INDEX			("index"),

	ATTR_MSG_NAME		("nameMsg"),
	ATTR_MSG_DESC_SHORT	("desc1Msg"),
	ATTR_MSG_DESC_FULL	("desc2Msg"),

	ATTR_FLAGS			("flags"),
	ATTR_CATEGORY		("category"),
	ATTR_INPUT_POPUP	("inputPopup"),
	ATTR_ABILITY		("ability"),

	ATTR_FP_COST		("fp"),
	ATTR_BP_COST		("bp"),

	ATTR_NAME			("name"),
	ATTR_DESC			("desc");
	// @formatter:on

	private final String key;

	private MoveRecordKey(String key)
	{
		this.key = key;
	}

	@Override
	public String toString()
	{
		return key;
	}
}
