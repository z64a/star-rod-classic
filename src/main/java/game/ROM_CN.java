package game;

import java.io.File;

import app.StarRodException;

public class ROM_CN extends ROM
{
	public static final String MD5 = "8F8F50AB00C4089AE32C6B9FEFD69543";

	public ROM_CN(File databaseDir)
	{
		super(Version.CN, RAM_BLOCKS, databaseDir);
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
				return 0x6D550;
			case MAP_ASSET_TABLE:
				return 0x1E40000;

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
			MemoryRegion.globalFromROM(LibScope.Common, false, 0x001000, 0x074570, 0x80025C00, EngineComponent.SYSTEM),
			MemoryRegion.globalFromROM(LibScope.Common, false, 0x074570, 0x0A4990, 0x800DA8E0, EngineComponent.CHARACTER),
			MemoryRegion.globalFromROM(LibScope.Common, false, 0x0A4990, 0x0E6920, 0x8010DAB0, EngineComponent.ENGINE),
			MemoryRegion.globalFromROM(LibScope.Common, false, 0x0E6920, 0x0FDDA0, 0x802C3000, EngineComponent.SCRIPT),
			MemoryRegion.globalFromROM(LibScope.Common, false, 0x0FDDA0, 0x101580, 0x802DBD40, EngineComponent.SPRITE),
			MemoryRegion.globalFromROM(LibScope.Common, false, 0x101580, 0x10BB80, 0x802E0D90, EngineComponent.ENTITY),

			MemoryRegion.globalFromROM(LibScope.Common, false, 0x10BB80, 0x10E120, 0x802EB3D0, "Message Images"),

			MemoryRegion.globalFromROM(LibScope.World, 0x80280000, 0x80286520, 0x8298B0, "World Lib"),

			MemoryRegion.globalFromROM(LibScope.Battle, 0x8023E000, 0x8029D9F0, 0x1B59A0, "Battle Lib"),
			MemoryRegion.globalFromROM(LibScope.Battle, 0x802A1000, 0x802ACC60, 0x45E7E0, "Battle Menu"),

			MemoryRegion.globalFromROM(LibScope.Pause, 0x80242BA0, 0x80270140, 0x17EB60, "Pause Library"),

			MemoryRegion.globalFromROM(LibScope.MainMenu, 0x80242BA0, 0x8024C440, 0x1AC100, "File Select")
	};
}
