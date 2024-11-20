package game.globals;

import util.xml.XmlKey;

public enum ItemRecordKey implements XmlKey
{
	// @formatter:off
	TAG_ROOT		("Items"),
	TAG_ITEM		("Item"),
	ATTR_INDEX			("index"),

	ATTR_MSG_NAME		("nameMsg"),
	ATTR_MSG_DESC_SHORT	("desc1Msg"),
	ATTR_MSG_DESC_FULL	("desc2Msg"),

	ATTR_HUD_ELEMENT	("hudElement"),
	ATTR_ITEM_ENTITY	("itemEntity"),
	ATTR_MOVE			("move"),

	ATTR_AUTO_GFX		("autoGraphics"),

	ATTR_TARGET_FLAGS	("targetFlags"),
	ATTR_TYPE_FLAGS		("typeFlags"),

	ATTR_SORT_PRIORITY	("sortValue"),
	ATTR_SELL_VALUE		("sellValue"),

	ATTR_POTENCY_A		("powA"),
	ATTR_POTENCY_B		("powB"),

	ATTR_NAME			("name"),
	ATTR_DESC			("desc");
	// @formatter:on

	private final String key;

	private ItemRecordKey(String key)
	{
		this.key = key;
	}

	@Override
	public String toString()
	{
		return key;
	}
}
