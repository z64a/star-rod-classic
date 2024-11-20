package game.sprite;

import util.xml.XmlKey;

public enum SpriteKey implements XmlKey
{
	// @formatter:off
	ATTR_AUTHOR			("author"),
	ATTR_ID				("id"),
	ATTR_SOURCE			("src"),

	TAG_SPRITE			("SpriteSheet"),
	ATTR_SPRITE_A		("a"), //deprecated
	ATTR_SPRITE_B		("b"), //deprecated
	ATTR_SPRITE_NUM_COMPONENTS	("maxComponents"),
	ATTR_SPRITE_NUM_VARIATIONS	("paletteGroups"),

	TAG_PALETTE_LIST	("PaletteList"),
	TAG_PALETTE			("Palette"),

	TAG_RASTER_LIST		("RasterList"),
	TAG_RASTER			("Raster"),
	ATTR_PALETTE		("palette"),

	ATTR_SPECIAL_SIZE	("special"), //deprecated
	ATTR_OVERRIDE_SIZE	("backSize"),

	TAG_ANIMATION_LIST	("AnimationList"),
	TAG_ANIMATION		("Animation"),
	TAG_COMPONENT		("Component"),
	ATTR_X				("x"), //deprecated
	ATTR_Y				("y"), //deprecated
	ATTR_Z				("z"), //deprecated
	ATTR_OFFSET			("xyz"),
	ATTR_SEQ			("seq"),

	TAG_COMMAND			("Command"),
	ATTR_VAL			("val"),

	TAG_LABEL			("Label"),
	ATTR_NAME			("name"),
	ATTR_POS			("pos"),

	ATTR_KEYFRAMES		("keyframes");
	// @formatter:on

	private final String key;

	private SpriteKey(String key)
	{
		this.key = key;
	}

	@Override
	public String toString()
	{
		return key;
	}
}
