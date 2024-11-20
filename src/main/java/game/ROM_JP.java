package game;

import java.io.File;

import app.StarRodException;

public class ROM_JP extends ROM
{
	public static final String MD5 = "DF54F17FB84FB5B5BCF6AA9AF65B0942";

	public ROM_JP(File databaseDir)
	{
		super(Version.JP, RAM_BLOCKS, databaseDir);
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
				return 0x6E8C0;
			case MAP_ASSET_TABLE:
				return 0x1E00000;

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
			MemoryRegion.globalFromROM(LibScope.Common, 0x80025C00, 0x8009A590, 0x001000, EngineComponent.SYSTEM),
			MemoryRegion.globalFromROM(LibScope.Common, 0x800DC4E0, 0x8010CAE0, 0x075990, EngineComponent.CHARACTER),
			MemoryRegion.globalFromROM(LibScope.Common, 0x801148E0, 0x80156310, 0x0A9770, EngineComponent.ENGINE),
			MemoryRegion.globalFromROM(LibScope.Common, 0x802C3000, 0x802DA480, 0x0EB1A0, EngineComponent.SCRIPT),
			MemoryRegion.globalFromROM(LibScope.Common, 0x8010F890, 0x80113070, 0x0A5F90, EngineComponent.SPRITE),
			MemoryRegion.globalFromROM(LibScope.Common, 0x802DBD40, 0x802E6340, 0x102620, EngineComponent.ENTITY),

			MemoryRegion.globalFromROM(LibScope.World, 0x80280000, 0x802864C0, 0x7E8810, "World Lib"),

			MemoryRegion.globalFromROM(LibScope.Battle, 0x8023E000, 0x8029DD30, 0x1749F0, "Battle Lib"),
			MemoryRegion.globalFromROM(LibScope.Battle, 0x802A1000, 0x802ACBE0, 0x41D750, "Battle Menu"),

			MemoryRegion.globalFromROM(LibScope.Pause, 0x8023E000, 0x80242370, 0x139DC0, "Pause Graphics"), // same as File Graphics
			MemoryRegion.globalFromROM(LibScope.Pause, 0x80242370, 0x8026F790, 0x13E130, "Pause Library"),

			MemoryRegion.globalFromROM(LibScope.MainMenu, 0x8023E000, 0x80242370, 0x139DC0, "File Graphics"), // same as Pause Graphics
			MemoryRegion.globalFromROM(LibScope.MainMenu, 0x80242370, 0x8024B810, 0x16B550, "File Select")
	};
}
