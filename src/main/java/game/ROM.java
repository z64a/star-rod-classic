package game;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import app.StarRodException;
import app.input.IOUtils;
import app.input.InvalidInputException;
import game.shared.StructTypes;
import game.shared.lib.LibEntry;
import game.shared.lib.Library;
import game.shared.lib.LibraryFile;
import game.shared.struct.TypeMap;
import util.Logger;

public abstract class ROM
{
	public static enum RomVersion
	{
		US,
		JP,
		PAL,
		CN
	}

	public static enum LibScope
	{
		// @formatter:off
		None		(StructTypes.sharedTypes),
		Common		(StructTypes.sharedTypes),
		World		(StructTypes.mapTypes),
		Battle		(StructTypes.battleTypes),
		Pause		(StructTypes.sharedTypes),
		MainMenu	(StructTypes.sharedTypes);
		// @formatter:on

		public final TypeMap typeMap;

		private LibScope(TypeMap typeMap)
		{
			this.typeMap = typeMap;
		}
	}

	public static enum EPointer
	{
		ITEM_TABLE,
		MOVE_TABLE,
		WORLD_MAP_BG_START,
		WORLD_MAP_BG_LIMIT,
		WORLD_MAP_SHAPE_START,
		WORLD_MAP_SHAPE_LIMIT,
		WORLD_MAP_DATA_START,
		WORLD_MAP_DATA_LIMIT,
		WORLD_LIB_START,
		WORLD_PARTNER_START,
		WORLD_PARTNER_LIMIT,
		BATTLE_DATA_START,
		BATTLE_DATA_LIMIT,
		BATTLE_PARTNER_START,
		BATTLE_PARTNER_LIMIT,
		BATTLE_LIB_START,
		BATTLE_LIB_END,
	}

	public static enum EOffset
	{
		MAP_CONFIG_TABLE,
		MAP_ASSET_TABLE,

		EFFECT_TABLE,
		ITEM_TABLE,
		MENU_ICON_TABLE,
		ITEM_ICON_TABLE,
		ITEM_NTT_SCRIPT_END,
		MOVE_TABLE,

		ACTION_TABLE,
		ITEM_SCRIPT_LIST,
		ITEM_SCRIPT_TABLE,
		MOVE_SCRIPT_TABLE,

		PAUSE_ICON_TABLE,
		PAUSE_ICON_LIMIT,

		ICON_BASE,

		SHADING_TABLE,
		SHADING_DATA_START,
		SHADING_DATA_END,

		PARTNER_WORLD_START,
		PARTNER_WORLD_END,

		WORLD_SCRIPTS_START,
		WORLD_SCRIPTS_END
	}

	public static enum EConstant
	{
		MAP_DATA_SIZE,
		WORLD_PARTNER_SIZE,
		BATTLE_DATA_SIZE,
		BATTLE_PARTNER_SIZE,

		ICON_TABLE_COUNT
	}

	public static enum EngineComponent
	{
		SYSTEM, // nusys, file io, heaps, math, game states, low-level audio, much more...
		CHARACTER, // npc, player, inventory, collision, status menu, popup menus
		ENGINE, // entities, models, messages, windows, higher level audio
		SCRIPT, // script interpreter, most API functions, virtual entities
		SPRITE, // sprite rendering and animation
		ENTITY, // generic entities available to all game areas
		WORLD_LIB,
		BATTLE_LIB,
		BATTLE_MENU,
		ENTITY_DEFAULT,
		ENTITY_JANIWA,
		ENTITY_SBKOMO
	}

	public final RomVersion version;
	private final List<MemoryRegion> allRegions;
	private final LinkedHashMap<LibScope, RamContent> ramMaps;

	public LinkedHashMap<Integer, LibEntry> libOffsetMap;

	public abstract int getConstant(EConstant constant);

	public abstract int getAddress(EPointer pointer);

	public abstract int getOffset(EOffset offset);

	public abstract MemoryRegion getMemoryRegion(EngineComponent region);

	public MemoryRegion[] getActionOverlays()
	{
		throw new StarRodException("Action overlays not defined for " + version.name());
	}

	public MemoryRegion[] getMoveOverlays()
	{
		throw new StarRodException("Action overlays not defined for " + version.name());
	}

	public MemoryRegion[] getItemOverlays()
	{
		throw new StarRodException("Action overlays not defined for " + version.name());
	}

	private static class RamContent
	{
		private List<MemoryRegion> regions = new ArrayList<>();
		private Library library;

		private RamContent(LibScope context)
		{
			try {
				this.library = new Library(context);
			}
			catch (IOException e) {
				throw new StarRodException("Exception occurred while loading library for %s: %n%s", context, e.getMessage());
			}
		}
	}

	protected ROM(RomVersion version, MemoryRegion[] memoryMap, File databaseDir)
	{
		this.version = version;

		allRegions = new ArrayList<>();
		ramMaps = new LinkedHashMap<>();
		for (LibScope scope : LibScope.values())
			ramMaps.put(scope, new RamContent(scope));

		for (MemoryRegion r : memoryMap) {
			switch (r.context) {
				case None:
					break;

				case Common:
					allRegions.add(r);
					for (RamContent ram : ramMaps.values())
						ram.regions.add(r);
					break;

				default:
					RamContent ram = ramMaps.get(r.context);
					ram.regions.add(r);
					allRegions.add(r);
					break;
			}
		}

		// read all library files matching the ROM version
		List<LibraryFile> libFiles = new ArrayList<>();
		try {
			int added = 0;
			for (File f : IOUtils.getFilesWithExtension(databaseDir, "lib", true)) {
				LibraryFile lib = new LibraryFile(version, f);
				if (lib.version == version) {
					libFiles.add(lib);
					added += lib.count();
				}
			}
			Logger.logf("Loaded %d %s library definitions.", added, version);
		}
		catch (IOException e) {
			throw new StarRodException("Exception occurred while reading %s library files: %n%s", version, e.getMessage());
		}

		// read all common lib entries
		for (LibraryFile lib : libFiles) {
			try {
				if (lib.scope == LibScope.Common) {
					for (RamContent ram : ramMaps.values())
						ram.library.addEntries(lib, false);
				}
			}
			catch (InvalidInputException e) {
				throw new StarRodException("Exception occurred while reading entries from %s: %n%s", lib.source.getName(), e.getMessage());
			}
		}

		// read all (overriding) context-specific entries
		for (LibraryFile lib : libFiles) {
			try {
				switch (lib.scope) {
					case None:
						break;

					case Common:
						break;

					default:
						RamContent ram = ramMaps.get(lib.scope);
						ram.library.addEntries(lib, true);
						break;
				}
			}
			catch (InvalidInputException e) {
				throw new StarRodException("Exception occurred while reading entries from %s: %n%s", lib.source.getName(), e.getMessage());
			}
		}

		// build map of rom offsets to lib entries
		libOffsetMap = new LinkedHashMap<>();

		for (RamContent ram : ramMaps.values()) {
			for (LibEntry e : ram.library) {
				if (e.offset > 0) {
					libOffsetMap.put(e.offset, e);
				}
			}
		}
	}

	public final Integer getOffset(LibScope context, int address)
	{
		RamContent ram = ramMaps.get(context);
		if (ram == null)
			throw new StarRodException("Unknown context: " + context);

		for (MemoryRegion r : ram.regions) {
			if (r.startAddr <= address && r.endAddr > address)
				return r.startOffset + (address - r.startAddr);
		}

		return null;
	}

	public final Integer getAddress(int offset)
	{
		for (RamContent ram : ramMaps.values()) {
			for (MemoryRegion r : ram.regions) {
				if (r.startOffset <= offset && (r.startOffset + r.size) > offset)
					return r.startAddr + (offset - r.startOffset);
			}
		}

		return null;
	}

	public final Integer getAddress(LibScope context, int offset)
	{
		RamContent ram = ramMaps.get(context);
		if (ram == null)
			throw new StarRodException("Unknown context: " + context);

		for (MemoryRegion r : ram.regions) {
			if (r.startOffset <= offset && (r.startOffset + r.size) > offset)
				return r.startAddr + (offset - r.startOffset);
		}

		return null;
	}

	public final Library getLibrary(LibScope context)
	{
		RamContent ram = ramMaps.get(context);
		if (ram == null)
			throw new StarRodException("Unknown context: " + context);

		return ram.library;
	}

	public static List<MemoryRegion> getAllMemoryRegions(Class<? extends ROM> romClass)
	{
		List<MemoryRegion> regions = new ArrayList<>();

		Field[] declaredFields = romClass.getDeclaredFields();
		for (Field field : declaredFields) {
			if (Modifier.isStatic(field.getModifiers()) && field.getType().isArray()) {
				if (field.getType().getComponentType().isAssignableFrom(MemoryRegion.class)) {
					try {
						for (MemoryRegion r : (MemoryRegion[]) field.get(null)) {
							if (r.context != LibScope.None)
								regions.add(r);
						}
					}
					catch (Exception e) {
						throw new StarRodException(e);
					}
				}
			}
		}

		return regions;
	}
}
