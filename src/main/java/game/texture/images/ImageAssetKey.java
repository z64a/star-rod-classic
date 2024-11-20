package game.texture.images;

import util.xml.XmlKey;

public enum ImageAssetKey implements XmlKey
{
	// @formatter:off
	TAG_IMG_ASSETS	("ImageAssets"),
	TAG_HUD_ELEMS	("HudElements"),
	TAG_HUD_ITEM	("Item"),
	TAG_HUD_GLOBAL	("Global"),
	TAG_HUD_BATTLE	("Battle"),
	TAG_HUD_MENU	("Menu"),
	TAG_ITEM_NTTS	("ItemEntities"),
	TAG_SCRIPT		("Script"),
	TAG_IMAGE		("Image"),
	ATTR_OFFSET		("offset"),
	ATTR_PALETTE	("palette"),
	ATTR_PALCOUNT	("palcount"),
	ATTR_NAME		("name"),
	ATTR_FORMAT		("fmt"),
	ATTR_WIDTH		("w"),
	ATTR_HEIGHT		("h"),
	ATTR_FLIP		("flip");
	// @formatter:on

	private final String key;

	private ImageAssetKey(String key)
	{
		this.key = key;
	}

	@Override
	public String toString()
	{
		return key;
	}
}
