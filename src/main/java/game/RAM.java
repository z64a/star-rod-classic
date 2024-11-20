package game;

public class RAM
{
	// @formatter:off
	public static final int ITEM_TABLE				= 0x800878E0;
	public static final int MOVE_TABLE				= 0x8008F060;

	public static final int WORLD_MAP_BG_START		= 0x80200000;
	public static final int WORLD_MAP_BG_LIMIT		= 0x80210000;

	public static final int WORLD_MAP_SHAPE_START	= 0x80210000;
	public static final int WORLD_MAP_SHAPE_LIMIT	= 0x80240000;

	public static final int WORLD_MAP_DATA_START	= 0x80240000;
	public static final int WORLD_MAP_DATA_LIMIT	= 0x80268000;

	public static final int WORLD_LIB_START			= 0x80280000;

	public static final int WORLD_PARTNER_START		= 0x802BD100;
	public static final int WORLD_PARTNER_LIMIT		= 0x802C0000;
	public static final int WORLD_PARTNER_MAX_SIZE = WORLD_PARTNER_LIMIT - WORLD_PARTNER_START; // 0x2F00 -- could be 2E00 though!

	public static final int BATTLE_DATA_START 		= 0x80218000;
	public static final int BATTLE_DATA_LIMIT		= 0x80238000;
	public static final int BATTLE_DATA_MAX_SIZE = BATTLE_DATA_LIMIT - BATTLE_DATA_START; // 0x20000

	public static final int BATTLE_PARTNER_START 	= 0x80238000;
	public static final int BATTLE_PARTNER_LIMIT	= 0x8023E000;
	public static final int BATTLE_PARTNER_MAX_SIZE = BATTLE_PARTNER_START - BATTLE_PARTNER_LIMIT; // 0x6000

	public static final int BATTLE_LIB_START 		= 0x8023E000;
	public static final int BATTLE_LIB_END  		= 0x8029DA30;
	// @formatter:on

	// really, these are all 'move'

	public static final int MOVE_MAX_SIZE = 0x8000; // largest move = 0x4340

	public static final int BATTLE_MOVE_START = 0x802A1000;
	public static final int BATTLE_MOVE_LIMIT = BATTLE_MOVE_START + MOVE_MAX_SIZE;

	public static final int BATTLE_ITEM_START = 0x802A1000;
	public static final int BATTLE_ITEM_LIMIT = BATTLE_ITEM_START + MOVE_MAX_SIZE;

	public static final int BATTLE_SPIRITS_START = 0x802A1000;
	public static final int BATTLE_SPIRITS_LIMIT = BATTLE_SPIRITS_START + MOVE_MAX_SIZE;

	public static final int BATTLE_ACTION_START = 0x802A9000;
	public static final int BATTLE_ACTION_MAX_SIZE = 0x2200; // largest = 0x2200
}
