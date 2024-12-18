package game;

import java.io.File;

import app.StarRodException;

public class ROM_PAL extends ROM
{
	public static final String MD5 = "3B5C99F5E7DBA06BF8237E58F6D4196B";

	public ROM_PAL(File databaseDir)
	{
		super(RomVersion.PAL, RAM_BLOCKS, databaseDir);
	}

	@Override
	public int getConstant(EConstant constant)
	{
		switch (constant) {
			default:
				throw new StarRodException("%s ROM does not have a defined value for %s", version, constant);
		}
	}

	@Override
	public int getAddress(EPointer pointer)
	{
		switch (pointer) {
			default:
				throw new StarRodException("%s ROM does not have a defined value for %s", version, pointer);
		}
	}

	@Override
	public int getOffset(EOffset offset)
	{
		switch (offset) {
			case MAP_CONFIG_TABLE:
				return 0x6AC90;
			case MAP_ASSET_TABLE:
				return 0x2600000;

			default:
				throw new StarRodException("%s ROM does not have a defined value for %s", version, offset);
		}
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
			MemoryRegion.globalFromROM(LibScope.Common, 0x80025C00, 0x80096980, 0x001000, EngineComponent.SYSTEM),
			MemoryRegion.globalFromROM(LibScope.Common, 0x800D8C70, 0x8010B410, 0x071D80, EngineComponent.CHARACTER),
			MemoryRegion.globalFromROM(LibScope.Common, 0x802C6000, 0x802E0740, 0x0E61D0, EngineComponent.SCRIPT),
			MemoryRegion.globalFromROM(LibScope.Common, 0x802E7070, 0x802F1690, 0x1040F0, EngineComponent.ENTITY),
			MemoryRegion.globalFromROM(LibScope.Common, 0x8010E1D0, 0x8014FE80, 0x0A4520, EngineComponent.ENGINE),
			MemoryRegion.globalFromROM(LibScope.Common, 0x802E2020, 0x802E5800, 0x100910, EngineComponent.SPRITE),
			MemoryRegion.globalFromROM(LibScope.Common, 0x802F16D0, 0x802F3C70, 0x10E710, "Font Data"),

			MemoryRegion.globalFromROM(LibScope.World, 0x80280000, 0x80286530, 0x84CF10, "World Lib"),

			MemoryRegion.globalFromROM(LibScope.Battle, 0x8023E000, 0x8029E080, 0x17A160, "Battle Lib"),
			MemoryRegion.globalFromROM(LibScope.Battle, 0x802A1000, 0x802ACE90, 0x4688B0, "Battle Menu"),

			MemoryRegion.globalFromROM(LibScope.Pause, 0x8023E000, 0x80246AB0, 0x132E40, "Shared Pause Graphics"), // same as File Graphics
			MemoryRegion.globalFromROM(LibScope.Pause, 0x80246AB0, 0x80272490, 0x13B8F0, "Pause Library"),

			MemoryRegion.globalFromROM(LibScope.MainMenu, 0x8023E000, 0x80246AB0, 0x132E40, "File Graphics"), // same as Pause Graphics
			MemoryRegion.globalFromROM(LibScope.MainMenu, 0x80246AB0, 0x80251740, 0x16F4D0, "File Select")
	};
}
