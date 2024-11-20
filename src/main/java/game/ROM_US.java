package game;

import java.io.File;

import app.StarRodException;

public class ROM_US extends ROM
{
	public static final String MD5 = "A722F8161FF489943191330BF8416496";

	public ROM_US(File databaseDir)
	{
		super(Version.US, RAM_BLOCKS, databaseDir);
	}

	@Override
	public int getConstant(EConstant constant)
	{
		// @formatter:off
		switch(constant)
		{
			case WORLD_PARTNER_SIZE:	return 0x2F00; // 2E00 perhaps?
			case BATTLE_DATA_SIZE:		return 0x20000;
			case BATTLE_PARTNER_SIZE:	return 0x6000;

			case ICON_TABLE_COUNT:		return 0x181;

			default:
				throw new StarRodException("%s ROM does not have a defined value for %s", version, constant);
		}
		// @formatter:on
	}

	@Override
	public int getAddress(EPointer pointer)
	{
		// @formatter:off
		switch(pointer)
		{
			case ITEM_TABLE:				return 0x800878E0;
			case MOVE_TABLE:				return 0x8008F060;

			case WORLD_MAP_BG_START:		return 0x80200000;
			case WORLD_MAP_BG_LIMIT:		return 0x80210000;
			case WORLD_MAP_SHAPE_START:		return 0x80210000;
			case WORLD_MAP_SHAPE_LIMIT:		return 0x80240000;
			case WORLD_MAP_DATA_START:		return 0x80240000;
			case WORLD_MAP_DATA_LIMIT:		return 0x80268000;
			case WORLD_LIB_START:			return 0x80280000;
			case WORLD_PARTNER_START:		return 0x802BD100;
			case WORLD_PARTNER_LIMIT:		return 0x802C0000;

			case BATTLE_DATA_START :		return 0x80218000;
			case BATTLE_DATA_LIMIT:			return 0x80238000;
			case BATTLE_PARTNER_START :		return 0x80238000;
			case BATTLE_PARTNER_LIMIT:		return 0x8023E000;
			case BATTLE_LIB_START :			return 0x8023E000;
			case BATTLE_LIB_END  :			return 0x8029DA30;

			default:
				throw new StarRodException("%s ROM does not have a defined value for %s", version, pointer);
		}
		// @formatter:on
	}

	@Override
	public int getOffset(EOffset offset)
	{
		// @formatter:off
		switch(offset)
		{
			case MAP_CONFIG_TABLE:		return 0x06E8F0;
			case MAP_ASSET_TABLE:		return 0x1E40000;

			case EFFECT_TABLE:			return 0x05A610;
			case ITEM_TABLE:			return 0x062CE0;
			case MENU_ICON_TABLE:		return 0x065A80;
			case ITEM_ICON_TABLE:		return 0x0691D4;
			case ITEM_NTT_SCRIPT_END:	return 0x0697D8;
			case MOVE_TABLE:			return 0x06A460;

			case ACTION_TABLE:			return 0x09113C;
			case ITEM_SCRIPT_LIST:		return 0x1C2460;
			case ITEM_SCRIPT_TABLE:		return 0x1C24E4;
			case MOVE_SCRIPT_TABLE:		return 0x1C2760;

			case PAUSE_ICON_TABLE:		return 0x135140;
			case PAUSE_ICON_LIMIT:		return 0x135EE0;

			case ICON_BASE:				return 0x1CC310;

			case SHADING_TABLE:			return 0x315B80;
			case SHADING_DATA_START:	return 0x315D50;
			case SHADING_DATA_END:		return 0x3169F0;

			case PARTNER_WORLD_START:	return 0x317020;
			case PARTNER_WORLD_END:		return 0x3251D0;

			case WORLD_SCRIPTS_START:	return 0x7E0E80;
			case WORLD_SCRIPTS_END:		return 0x7E73A0;

			default:
				throw new StarRodException("%s ROM does not have a defined value for %s", version, offset);
		}
		// @formatter:on
	}

	@Override
	public MemoryRegion getMemoryRegion(EngineComponent comp)
	{
		for (MemoryRegion r : RAM_BLOCKS) {
			if (r.engineComp == comp)
				return r;
		}
		return null;
	}

	private static final MemoryRegion[] RAM_BLOCKS = {
			MemoryRegion.globalFromROM(LibScope.Common, 0x80025C00, 0x8009A5B0, 0x001000, EngineComponent.SYSTEM),
			MemoryRegion.globalFromROM(LibScope.Common, 0x800DC500, 0x8010C920, 0x0759B0, EngineComponent.CHARACTER),
			MemoryRegion.globalFromROM(LibScope.Common, 0x8010F6D0, 0x801512B0, 0x0A5DD0, EngineComponent.ENGINE),
			MemoryRegion.globalFromROM(LibScope.Common, 0x802C3000, 0x802DA480, 0x0E79B0, EngineComponent.SCRIPT),
			MemoryRegion.globalFromROM(LibScope.Common, 0x802DBD40, 0x802DF520, 0x0FEE30, EngineComponent.SPRITE),
			MemoryRegion.globalFromROM(LibScope.Common, 0x802E0D90, 0x802EB390, 0x102610, EngineComponent.ENTITY),

			MemoryRegion.globalFromROM(LibScope.Common, 0x802EB3D0, 0x802ED970, 0x10CC10, "Message Images"),
			MemoryRegion.globalFromROM(LibScope.Common, 0x802ED970, 0x802EE8D0, 0x1149B0, "Credits 1 Font Images"),
			MemoryRegion.globalFromROM(LibScope.Common, 0x802EE8D0, 0x802F39D0, 0x10F1B0, "Normal Font Images"),
			MemoryRegion.globalFromROM(LibScope.Common, 0x802F39D0, 0x802F4560, 0x115910, "Credits 2 Font Images"), // actually ends at 802F4558
			MemoryRegion.globalFromROM(LibScope.Common, 0x802F4560, 0x802F4A60, 0x1144B0, "Font Palettes"), // or 0x80 bytes from 0x116498 used when loading credits
			//	MemoryRegion.globalFromROM(LibScope.Common, 0xE0200000, 0xE0200940, 0x0325AD0, "FX common"),

			MemoryRegion.globalFromROM(LibScope.World, 0x80280000, 0x80286520, 0x7E0E80, "World Lib"),
			MemoryRegion.globalFromROM(LibScope.World, 0x802C05CC, 0x802C0ECC, 0x3251D0, "World Lib"),

			MemoryRegion.globalFromROM(LibScope.Battle, 0x8023E000, 0x8029DA30, 0x16C8E0, "Battle Lib"),
			MemoryRegion.globalFromROM(LibScope.Battle, 0x802A1000, 0x802ACC60, 0x415D90, "Battle Menu"),

			MemoryRegion.globalFromROM(LibScope.Pause, 0x8023E000, 0x80242BA0, 0x131340, "Pause Graphics"), // same as File Graphics
			MemoryRegion.globalFromROM(LibScope.Pause, 0x80242BA0, 0x802700C0, 0x135EE0, "Pause Library"),

			MemoryRegion.globalFromROM(LibScope.MainMenu, 0x8023E000, 0x80242BA0, 0x131340, "File Graphics"), // same as Pause Graphics
			MemoryRegion.globalFromROM(LibScope.MainMenu, 0x80242BA0, 0x8024C080, 0x163400, "File Select")
	};

	public static final String ENTITY_OVERLAY_NAME = "entity";
	public static final String ACTION_OVERLAY_NAME = "action";
	public static final String MOVE_OVERLAY_NAME = "move";
	public static final String ITEM_OVERLAY_NAME = "item";

	public static final MemoryRegion[] ENTITY_OVERLAYS = {
			MemoryRegion.overlayFromROM(LibScope.Common, ENTITY_OVERLAY_NAME, 0x802BAE00, 0x802BD000, 0xE2B530, "Default Area Entities"),
			MemoryRegion.overlayFromROM(LibScope.Common, ENTITY_OVERLAY_NAME, 0x802BAE00, 0x802BCE20, 0xE2D730, "JAN/IWA Only Entities"),
			MemoryRegion.overlayFromROM(LibScope.Common, ENTITY_OVERLAY_NAME, 0x802BAE00, 0x802BCBE0, 0xE2F750, "SBK/OMO Only Entities"),
	};

	// each may contain more than one action
	public static final MemoryRegion[] ACTIONS_OVERLAYS = {
			MemoryRegion.overlayFromROM(LibScope.World, ACTION_OVERLAY_NAME, 0x802B6000, 0x802B6480, 0xE23260, "Actions_Idle"),
			MemoryRegion.overlayFromROM(LibScope.World, ACTION_OVERLAY_NAME, 0x802B6000, 0x802B6960, 0xE236E0, "Actions_Walk"),
			MemoryRegion.overlayFromROM(LibScope.World, ACTION_OVERLAY_NAME, 0x802B6000, 0x802B6590, 0xE24040, "Actions_Air"),
			MemoryRegion.overlayFromROM(LibScope.World, ACTION_OVERLAY_NAME, 0x802B6000, 0x802B6350, 0xE245D0, "Actions_StepUp"),
			MemoryRegion.overlayFromROM(LibScope.World, ACTION_OVERLAY_NAME, 0x802B6000, 0x802B65B0, 0xE24920, "Actions_Land"),
			MemoryRegion.overlayFromROM(LibScope.World, ACTION_OVERLAY_NAME, 0x802B6000, 0x802B6E90, 0xE24ED0, "Actions_Hammer"),
			MemoryRegion.overlayFromROM(LibScope.World, ACTION_OVERLAY_NAME, 0x802B6000, 0x802B69B0, 0xE25D60, "Actions_Spin"),
			MemoryRegion.overlayFromROM(LibScope.World, ACTION_OVERLAY_NAME, 0x802B6000, 0x802B66D0, 0xE26710, "Actions_UltraJump"),
			MemoryRegion.overlayFromROM(LibScope.World, ACTION_OVERLAY_NAME, 0x802B6000, 0x802B6730, 0xE26DE0, "Actions_SpinJump"),
			MemoryRegion.overlayFromROM(LibScope.World, ACTION_OVERLAY_NAME, 0x802B6000, 0x802B6780, 0xE27510, "Actions_Slide"),
			MemoryRegion.overlayFromROM(LibScope.World, ACTION_OVERLAY_NAME, 0x802B6000, 0x802B62B0, 0xE27C90, "Actions_Fire"),
			MemoryRegion.overlayFromROM(LibScope.World, ACTION_OVERLAY_NAME, 0x802B6000, 0x802B68B0, 0xE27F40, "Actions_Lava"),
			MemoryRegion.overlayFromROM(LibScope.World, ACTION_OVERLAY_NAME, 0x802B6000, 0x802B6240, 0xE287F0, "Actions_Knockback"),
			MemoryRegion.overlayFromROM(LibScope.World, ACTION_OVERLAY_NAME, 0x802B6000, 0x802B6770, 0xE28A30, "Actions_Misc"),
			MemoryRegion.overlayFromROM(LibScope.World, ACTION_OVERLAY_NAME, 0x802B6000, 0x802B62D0, 0xE291A0, "Actions_Munch"),
			MemoryRegion.overlayFromROM(LibScope.World, ACTION_OVERLAY_NAME, 0x802B6000, 0x802B6ED0, 0xE29470, "Actions_Flower"),
			MemoryRegion.overlayFromROM(LibScope.World, ACTION_OVERLAY_NAME, 0x802B6000, 0x802B6370, 0xE2A340, "Actions_Tweester"),
			MemoryRegion.overlayFromROM(LibScope.World, ACTION_OVERLAY_NAME, 0x802B6000, 0x802B6E80, 0xE2A6B0, "Actions_Parasol"),
	};

	@Override
	public MemoryRegion[] getActionOverlays()
	{
		return ACTIONS_OVERLAYS;
	}

	// each may contain more than one move
	public static final MemoryRegion[] MOVE_OVERLAYS = {
			MemoryRegion.overlayFromROM(LibScope.Battle, MOVE_OVERLAY_NAME, 0x802A1000, 0x802A3044, 0x007345A0, "Move_Hammer"),
			MemoryRegion.overlayFromROM(LibScope.Battle, MOVE_OVERLAY_NAME, 0x802A1000, 0x802A4640, 0x00737890, "Move_SpinSmash"),
			MemoryRegion.overlayFromROM(LibScope.Battle, MOVE_OVERLAY_NAME, 0x802A1000, 0x802A3990, 0x0073AED0, "Move_QuakeHammer"),
			MemoryRegion.overlayFromROM(LibScope.Battle, MOVE_OVERLAY_NAME, 0x802A1000, 0x802A4460, 0x0073D860, "Move_Jump"),
			MemoryRegion.overlayFromROM(LibScope.Battle, MOVE_OVERLAY_NAME, 0x802A1000, 0x802A4130, 0x00740CC0, "Move_Multibounce"),
			MemoryRegion.overlayFromROM(LibScope.Battle, MOVE_OVERLAY_NAME, 0x802A1000, 0x802A4020, 0x00743DF0, "Move_PowerBounce"),
			MemoryRegion.overlayFromROM(LibScope.Battle, MOVE_OVERLAY_NAME, 0x802A1000, 0x802A2FC0, 0x00746E10, "Move_SleepStomp"),
			MemoryRegion.overlayFromROM(LibScope.Battle, MOVE_OVERLAY_NAME, 0x802A1000, 0x802A2FC0, 0x00748DD0, "Move_DizzyStomp"),
			MemoryRegion.overlayFromROM(LibScope.Battle, MOVE_OVERLAY_NAME, 0x802A1000, 0x802A37E0, 0x0074AD90, "Move_D-DownPound"),
			MemoryRegion.overlayFromROM(LibScope.Battle, MOVE_OVERLAY_NAME, 0x802A1000, 0x802A2BC0, 0x0074D570, "Move_JumpCharge0"),
			MemoryRegion.overlayFromROM(LibScope.Battle, MOVE_OVERLAY_NAME, 0x802A1000, 0x802A4320, 0x0074F130, "Move_HammerCharge0"),
			MemoryRegion.overlayFromROM(LibScope.Battle, MOVE_OVERLAY_NAME, 0x802A1000, 0x802A5330, 0x00752450, "Move_HammerThrow"),
			MemoryRegion.overlayFromROM(LibScope.Battle, MOVE_OVERLAY_NAME, 0x802A1000, 0x802A3E30, 0x00756780, "Move_MegaQuake"),
			MemoryRegion.overlayFromROM(LibScope.Battle, MOVE_OVERLAY_NAME, 0x802A1000, 0x802A43D0, 0x007595B0, "Move_HammerCharge1"),
			MemoryRegion.overlayFromROM(LibScope.Battle, MOVE_OVERLAY_NAME, 0x802A1000, 0x802A2C50, 0x0075C980, "Move_JumpCharge1"),
			MemoryRegion.overlayFromROM(LibScope.Battle, MOVE_OVERLAY_NAME, 0x802A1000, 0x802A4350, 0x0075E5D0, "Move_HammerCharge2"),
			MemoryRegion.overlayFromROM(LibScope.Battle, MOVE_OVERLAY_NAME, 0x802A1000, 0x802A2C20, 0x00761920, "Move_JumpCharge2"),
			MemoryRegion.overlayFromROM(LibScope.Battle, MOVE_OVERLAY_NAME, 0x802A1000, 0x802A36E0, 0x00763540, "Move_AutoSmash"),
			MemoryRegion.overlayFromROM(LibScope.Battle, MOVE_OVERLAY_NAME, 0x802A1000, 0x802A3070, 0x00765C20, "Move_AutoJump"),
			MemoryRegion.overlayFromROM(LibScope.Battle, MOVE_OVERLAY_NAME, 0x802A1000, 0x802A3BA0, 0x00767C90, "Move_PowerQuake"),
			MemoryRegion.overlayFromROM(LibScope.Battle, MOVE_OVERLAY_NAME, 0x802A1000, 0x802A3F30, 0x0076A830, "Move_AutoMultibounce"),
			MemoryRegion.overlayFromROM(LibScope.Battle, MOVE_OVERLAY_NAME, 0x802A1000, 0x802A2BD0, 0x0076D760, "Move_PowerJump"),
			MemoryRegion.overlayFromROM(LibScope.Battle, MOVE_OVERLAY_NAME, 0x802A1000, 0x802A2C00, 0x0076F330, "Move_SuperJump"),
			MemoryRegion.overlayFromROM(LibScope.Battle, MOVE_OVERLAY_NAME, 0x802A1000, 0x802A2C70, 0x00770F30, "Move_MegaJump"),
			MemoryRegion.overlayFromROM(LibScope.Battle, MOVE_OVERLAY_NAME, 0x802A1000, 0x802A3560, 0x00772BA0, "Move_PowerSmash"),
			MemoryRegion.overlayFromROM(LibScope.Battle, MOVE_OVERLAY_NAME, 0x802A1000, 0x802A3590, 0x00775100, "Move_SuperSmash"),
			MemoryRegion.overlayFromROM(LibScope.Battle, MOVE_OVERLAY_NAME, 0x802A1000, 0x802A3600, 0x00777690, "Move_MegaSmash"),
			MemoryRegion.overlayFromROM(LibScope.Battle, MOVE_OVERLAY_NAME, 0x802A1000, 0x802A3EF0, 0x00779C90, "Move_Unused"),
			MemoryRegion.overlayFromROM(LibScope.Battle, MOVE_OVERLAY_NAME, 0x802A1000, 0x802A36C0, 0x0077CB80, "Move_ShrinkSmash"),
			MemoryRegion.overlayFromROM(LibScope.Battle, MOVE_OVERLAY_NAME, 0x802A1000, 0x802A36E0, 0x0077F240, "Move_ShellCrack"),
			MemoryRegion.overlayFromROM(LibScope.Battle, MOVE_OVERLAY_NAME, 0x802A1000, 0x802A3280, 0x00781920, "Move_D-DownJump"),
			MemoryRegion.overlayFromROM(LibScope.Battle, MOVE_OVERLAY_NAME, 0x802A1000, 0x802A2F80, 0x00783BA0, "Move_ShrinkStomp"),
			MemoryRegion.overlayFromROM(LibScope.Battle, MOVE_OVERLAY_NAME, 0x802A1000, 0x802A5340, 0x00785B20, "Move_EarthquakeJump")
	};

	@Override
	public MemoryRegion[] getMoveOverlays()
	{
		return MOVE_OVERLAYS;
	}

	// each may contain more than one item
	public static final MemoryRegion[] ITEM_OVERLAYS = {
			MemoryRegion.overlayFromROM(LibScope.Battle, ITEM_OVERLAY_NAME, 0x802A1000, 0x802A2410, 0x00715850, "Item_Shroom"),
			MemoryRegion.overlayFromROM(LibScope.Battle, ITEM_OVERLAY_NAME, 0x802A1000, 0x802A1D70, 0x00716C60, "Item_FireFlower"),
			MemoryRegion.overlayFromROM(LibScope.Battle, ITEM_OVERLAY_NAME, 0x802A1000, 0x802A1E80, 0x007179D0, "Item_DustyHammer"),
			MemoryRegion.overlayFromROM(LibScope.Battle, ITEM_OVERLAY_NAME, 0x802A1000, 0x802A2120, 0x00718850, "Item_POWBlock"),
			MemoryRegion.overlayFromROM(LibScope.Battle, ITEM_OVERLAY_NAME, 0x802A1000, 0x802A1E80, 0x00719970, "Item_Pebble"),
			MemoryRegion.overlayFromROM(LibScope.Battle, ITEM_OVERLAY_NAME, 0x802A1000, 0x802A19B0, 0x0071A7F0, "Item_VoltShroom"),
			MemoryRegion.overlayFromROM(LibScope.Battle, ITEM_OVERLAY_NAME, 0x802A1000, 0x802A1C90, 0x0071B1A0, "Item_ThunderRage"),
			MemoryRegion.overlayFromROM(LibScope.Battle, ITEM_OVERLAY_NAME, 0x802A1000, 0x802A1EB0, 0x0071BE30, "Item_SnowmanDoll"),
			MemoryRegion.overlayFromROM(LibScope.Battle, ITEM_OVERLAY_NAME, 0x802A1000, 0x802A1A90, 0x0071CCE0, "Item_UnusedDriedShroom"),
			MemoryRegion.overlayFromROM(LibScope.Battle, ITEM_OVERLAY_NAME, 0x802A1000, 0x802A1EE0, 0x0071D770, "Item_ShootingStar"),
			MemoryRegion.overlayFromROM(LibScope.Battle, ITEM_OVERLAY_NAME, 0x802A1000, 0x802A3F50, 0x0071E650, "Item_SleepySheep"),
			MemoryRegion.overlayFromROM(LibScope.Battle, ITEM_OVERLAY_NAME, 0x802A1000, 0x802A1A60, 0x007215A0, "Item_StoneCap"),
			MemoryRegion.overlayFromROM(LibScope.Battle, ITEM_OVERLAY_NAME, 0x802A1000, 0x802A1B40, 0x00722000, "Item_TastyTonic"),
			MemoryRegion.overlayFromROM(LibScope.Battle, ITEM_OVERLAY_NAME, 0x802A1000, 0x802A1C40, 0x00722B40, "Item_ThunderBolt"),
			MemoryRegion.overlayFromROM(LibScope.Battle, ITEM_OVERLAY_NAME, 0x802A1000, 0x802A2560, 0x00723780, "Item_UnusedUltraShroom"),
			MemoryRegion.overlayFromROM(LibScope.Battle, ITEM_OVERLAY_NAME, 0x802A1000, 0x802A2280, 0x00724CE0, "Item_SuperSoda"),
			MemoryRegion.overlayFromROM(LibScope.Battle, ITEM_OVERLAY_NAME, 0x802A1000, 0x802A1E70, 0x00725F60, "Item_HustleDrink"),
			MemoryRegion.overlayFromROM(LibScope.Battle, ITEM_OVERLAY_NAME, 0x802A1000, 0x802A1B40, 0x00726DD0, "Item_StopWatch"),
			MemoryRegion.overlayFromROM(LibScope.Battle, ITEM_OVERLAY_NAME, 0x802A1000, 0x802A1CD0, 0x00727910, "Item_DizzyDial"),
			MemoryRegion.overlayFromROM(LibScope.Battle, ITEM_OVERLAY_NAME, 0x802A1000, 0x802A18E0, 0x007285E0, "Item_PleaseComeBack"),
			MemoryRegion.overlayFromROM(LibScope.Battle, ITEM_OVERLAY_NAME, 0x802A1000, 0x802A2890, 0x00728EC0, "Item_EggMissile"),
			MemoryRegion.overlayFromROM(LibScope.Battle, ITEM_OVERLAY_NAME, 0x802A1000, 0x802A21C0, 0x0072A750, "Item_InsecticideHerb"),
			MemoryRegion.overlayFromROM(LibScope.Battle, ITEM_OVERLAY_NAME, 0x802A1000, 0x802A1CA0, 0x0072B910, "Item_FrightJar"),
			MemoryRegion.overlayFromROM(LibScope.Battle, ITEM_OVERLAY_NAME, 0x802A1000, 0x802A25C0, 0x0072C5B0, "Item_Mystery"),
			MemoryRegion.overlayFromROM(LibScope.Battle, ITEM_OVERLAY_NAME, 0x802A1000, 0x802A19C0, 0x0072DB70, "Item_RepelGel"),
			MemoryRegion.overlayFromROM(LibScope.Battle, ITEM_OVERLAY_NAME, 0x802A1000, 0x802A21F0, 0x0072E530, "Item_LifeShroom"),
			MemoryRegion.overlayFromROM(LibScope.Battle, ITEM_OVERLAY_NAME, 0x802A1000, 0x802A1E80, 0x0072F720, "Item_Coconut"),
			MemoryRegion.overlayFromROM(LibScope.Battle, ITEM_OVERLAY_NAME, 0x802A1000, 0x802A1D60, 0x007305A0, "Item_ElectroPop"),
			MemoryRegion.overlayFromROM(LibScope.Battle, ITEM_OVERLAY_NAME, 0x802A1000, 0x802A2DD0, 0x00731300, "Item_StrangeCake"),
			MemoryRegion.overlayFromROM(LibScope.Battle, ITEM_OVERLAY_NAME, 0x802A1000, 0x802A24D0, 0x007330D0, "Item_Food"),
	};

	@Override
	public MemoryRegion[] getItemOverlays()
	{
		return ITEM_OVERLAYS;
	}
}
