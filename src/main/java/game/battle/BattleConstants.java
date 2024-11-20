package game.battle;

public class BattleConstants
{
	public static final String BLANK_SECTION = "empty";

	public static final int NUM_SECTIONS = 0x30;

	//	public static final int BATTLE_RAM_BASE = 0x80218000;
	//	public static final int SIZE_LIMIT = 0x20000;
	public static final int BATTLE_RAM_LIMIT = 0x80238000;

	public static final int BATTLE_TABLE_OFFSET = 0x70E30;
	public static final int BATTLE_TABLE_SIZE = 0x20 * NUM_SECTIONS;

	public static final int MOVE_BASE = 0x802A1000;
	public static final int MOVE_SIZE_LIMIT = 0x4340; // largest move size = 0x4340
	public static final int MOVE_RAM_LIMIT = MOVE_BASE + MOVE_SIZE_LIMIT;

	public static final int MOVE_TABLE_OFFSET = 0x1C2760;
	public static final int MOVE_TABLE_SIZE = 0x310;

	public static final int ALLY_BASE = 0x80238000;
	public static final int ALLY_SIZE_LIMIT = 0x6000;
	public static final int ALLY_RAM_LIMIT = ALLY_BASE + ALLY_SIZE_LIMIT;

	public static final int ITEM_BASE = 0x802A1000;
	public static final int ITEM_SIZE_LIMIT = 0x4000;
	public static final int ITEM_RAM_LIMIT = ITEM_BASE + ITEM_SIZE_LIMIT;

	public static final int STARS_BASE = 0x802A1000;
	public static final int STARS_SIZE_LIMIT = 0x4000;
	public static final int STARS_RAM_LIMIT = STARS_BASE + STARS_SIZE_LIMIT;
	public static final int NUM_STAR_POWERS = 12;

	public static final int ACTION_COMMANDS_BASE = 0x802A9000;
	public static final int ACTION_COMMANDS_SIZE_LIMIT = 0x3800; // actual size unk -- maximum possible 0x3C60?
	public static final int ACTION_COMMANDS_RAM_LIMIT = ACTION_COMMANDS_BASE + ACTION_COMMANDS_SIZE_LIMIT;
	public static final int NUM_ACTION_COMMANDS = 23;

	public static final String[] SECTION_NAMES = {
			"00 Area KMR Part 1",
			"01 Area KMR Part 2",
			"02 Area KMR Part 3",
			"03 Area MAC",
			"04 Area HOS",
			"05 Area NOK",
			"06 Area TRD Part 1",
			"07 Area TRD Part 2",
			"08 Area TRD Part 3",
			"09 Area IWA",
			"0A Area SBK",
			"0B Area ISK Part 1",
			"0C Area ISK Part 2",
			"0D Area MIM",
			"0E Area ARN",
			"0F Area DGB",
			"10 Area OMO",
			"11 Area OMO2",
			"12 Area OMO3",
			"13 Area KGR",
			"14 Area JAN",
			"15 Area JAN2",
			"16 Area KZN",
			"17 Area KZN2",
			"18 Area FLO",
			"19 Area FLO2",
			"1A Area TIK",
			"1B Area TIK2",
			"1C Area TIK3",
			"1D Area SAM",
			"1E Area SAM2",
			"1F Area PRA",
			"20 Area PRA2",
			"21 Area PRA3",
			"22 Area KPA",
			"23 Area KPA2",
			"24 Area KPA3",
			"25 Area KPA4",
			"26 Area KKJ",
			"27 Area DIG",
			"28",
			"29 Area OMO2_1",
			"2A Area OMO2_2",
			"2B Area OMO2_3",
			"2C Area OMO2_4",
			"2D Area OMO2_5",
			"2E Area OMO2_6",
			"2F"
	};

	public static final String[] STAR_POWER_NAME = {
			"Focus",
			"Refresh",
			"Lullaby",
			"StarStorm",
			"ChillOut",
			"Smooch",
			"TimeOut",
			"UpAndAway",
			"StarBeam",
			"PeachBeam",
			"PeachFocus",
			"PeachDash"
	};
}
