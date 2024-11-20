package game.sprite;

import util.xml.XmlKey;

public enum SpriteTableKey implements XmlKey
{
	// @formatter:off
	TAG_ROOT			("SpriteTable"),
	TAG_NPC_LIST		("NpcSprites"),
	TAG_PLAYER_LIST		("PlayerSprites"),
	TAG_SPRITE			("Sprite"),
	ATTR_ID				("id"),
	ATTR_SOURCE			("src"),
	ATTR_NAME			("name");
	// @formatter:on

	private final String key;

	private SpriteTableKey(String key)
	{
		this.key = key;
	}

	@Override
	public String toString()
	{
		return key;
	}
}
