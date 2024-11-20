package game.map.config;

import util.xml.XmlKey;

public enum MapConfigKey implements XmlKey
{
	// @formatter:off
	TAG_ROOT			("MapTable"),

	TAG_RESOURCE		("Resource"),
	ATTR_COMPRESSED		("compressed"),

	ATTR_NAME			("name"),
	ATTR_NICKNAME		("nickname"),
	ATTR_DESC			("desc"),

	TAG_AREA			("Area"),

	TAG_MAP				("Map"),
	ATTR_MAP_FLAGS		("flags"),

	TAG_STAGE			("Stage"),

	// not a real map, doesn't exist in the config table, but does
	// have resources associated with it
	ATTR_MAP_DATA			("hasData"),

	ATTR_MAP_SHAPE			("hasShape"),
	ATTR_MAP_HIT			("hasHit");
	// @formatter:on

	private final String key;

	private MapConfigKey(String key)
	{
		this.key = key;
	}

	@Override
	public String toString()
	{
		return key;
	}
}
