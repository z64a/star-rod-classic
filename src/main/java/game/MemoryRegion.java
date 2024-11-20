package game;

import game.ROM.EngineComponent;
import game.ROM.LibScope;

public class MemoryRegion
{
	public final LibScope context;
	public final String overlayType;

	public final int startOffset;
	public final int startAddr;
	public final int endAddr;
	public final int size;

	public final EngineComponent engineComp;
	public final String name;

	/**
	 * Region of global memory with no corresponding ROM offset -- unknown memory or BSS and the like
	 */

	public static MemoryRegion unitialized(LibScope context, int startAddr, int endAddr, String name)
	{
		return new MemoryRegion(context, "", true, startAddr, endAddr, -1, null, name);
	}

	/**
	 * Region of global memory loaded from the ROM
	 */

	public static MemoryRegion globalFromROM(LibScope context, int startAddr, int endAddr, int startOffset, String name)
	{
		return new MemoryRegion(context, "", true, startAddr, endAddr, startOffset, null, name);
	}

	public static MemoryRegion globalFromROM(LibScope context, int startAddr, int endAddr, int startOffset, EngineComponent comp)
	{
		return new MemoryRegion(context, "", true, startAddr, endAddr, startOffset, comp, comp.name());
	}

	public static MemoryRegion globalFromROM(LibScope context, boolean addressMode, int start, int end, int other, String name)
	{
		return new MemoryRegion(context, "", addressMode, start, end, other, null, name);
	}

	public static MemoryRegion globalFromROM(LibScope context, boolean addressMode, int start, int end, int other, EngineComponent comp)
	{
		return new MemoryRegion(context, "", addressMode, start, end, other, comp, comp.name());
	}

	/**
	 * Initialized overlay
	 * An overlay region loaded from the ROM
	 */

	public static MemoryRegion overlayFromROM(LibScope context, String overlayType, int startAddr, int endAddr, int startOffset, String name)
	{
		return new MemoryRegion(context, overlayType, true, startAddr, endAddr, startOffset, null, name);
	}

	public static MemoryRegion overlayFromROM(LibScope context, String overlayType, boolean addressMode, int start, int end, int other, String name)
	{
		return new MemoryRegion(context, overlayType, addressMode, start, end, other, null, name);
	}

	private MemoryRegion(LibScope context, String overlayType, boolean addressMode, int start, int end, int other, EngineComponent comp, String name)
	{
		this.context = context;
		this.overlayType = (overlayType == null) ? "" : overlayType;

		this.size = end - start;

		if (addressMode) {
			this.startOffset = other;
			this.startAddr = start;
			this.endAddr = end;
		}
		else {
			this.startOffset = start;
			this.startAddr = other;
			this.endAddr = size + other;
		}

		this.name = name;
		this.engineComp = comp;
	}

	public Integer toAddress(int offset)
	{
		if (startOffset < 0 || offset < startOffset || offset >= startOffset + size)
			return null;

		return startAddr + (offset - startOffset);
	}
}
