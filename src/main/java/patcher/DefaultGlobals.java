package patcher;

import game.RAM;

public enum DefaultGlobals
{
	// @formatter:off
	MAP_CONFIG_TABLE	("MapConfigTable"),
	MAP_TABLE			("MapAssetTable"),

	ITEM_TABLE			("ItemTable",			RAM.ITEM_TABLE),
	MOVE_TABLE			("MoveTable",			RAM.MOVE_TABLE),

	COLLISION_HEAP		("CollisionHeap",		0x80268000),
	COLLISION_HEAP_SIZE	("CollisionHeapSize",	0x18000),
	WORLD_HEAP			("WorldHeap",			0x802FB800),
	WORLD_HEAP_SIZE		("WorldHeapSize",		0x54000),
	SPRITE_HEAP			("SpriteHeap",			0x8034F800),
	SPRITE_HEAP_SIZE	("SpriteHeapSize",		0x40000),
	BATTLE_HEAP			("BattleHeap",			0x803DA800),
	BATTLE_HEAP_SIZE	("BattleHeapSize",		0x25800),
	AUDIO_HEAP			("AudioHeap",			0x801AA000),
	AUDIO_HEAP_SIZE		("AudioHeapSize",		0x56000),

	MOD_BYTES			("ModBytes",			0x80356000),
	MOD_BYTES_SIZE		("ModBytesSize",		0x1000),
	MOD_BYTES_COUNT		("ModBytesCount",		0x1000),
	MOD_FLAGS			("ModFlags",			0x80357000),
	MOD_FLAGS_SIZE		("ModFlagsSize",		0x1000),
	MOD_FLAGS_COUNT		("ModFlagsCount",		0x8000),

	ACTOR_NAME_TABLE	("ActorNameTable",		0x80281104),
	ACTOR_TATTLE_TABLE	("ActorTattleTable",	0x80282B98),
	ACTOR_OFFSETS_TABLE	("ActorOffsetsTable",	0x80282EE8),

	ITEM_HUD_SCRIPTS	("ItemHudScripts",		0x8008A680),
	ITEM_NTT_SCRIPTS	("ItemEntityScripts",	0x8008DDD4),
	ITEM_ICON_RASTERS	("ItemIconRasters",		0x8008E3D8),
	ITEM_ICON_PALETTES	("ItemIconPalettes",	0x8008E94C);
	// @formatter:on

	private static final String prefix = "$Global";
	private final String name;
	private final int defaultValue;

	private DefaultGlobals(String name)
	{
		this(name, 0);
	}

	private DefaultGlobals(String name, int value)
	{
		this.name = prefix + "_" + name;
		this.defaultValue = value;
	}

	@Override
	public String toString()
	{
		return name;
	}

	public int getDefaultValue()
	{
		return defaultValue;
	}
}
