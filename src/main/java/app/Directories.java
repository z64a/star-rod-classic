package app;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedList;
import java.util.List;

import org.apache.commons.io.FileUtils;

import util.Logger;

public enum Directories
{
	// @formatter:off
	DATABASE			(Root.NONE,				"/database/"),
	DATABASE_EDITOR		(Root.NONE, DATABASE,		"/editor/"),
	DATABASE_THEMES		(Root.NONE, DATABASE,		"/themes/"),
	DATABASE_TYPES		(Root.NONE, DATABASE,		"/types/"),
	DATABASE_STRUCTS	(Root.NONE, DATABASE,		"/structs/"),
	DATABASE_RAM_STRUCTS(Root.NONE, DATABASE_STRUCTS,	"/ram/"),
	DATABASE_ROM_STRUCTS(Root.NONE, DATABASE_STRUCTS,	"/rom/"),
	DATABASE_WIP_STRUCTS(Root.NONE, DATABASE_STRUCTS,	"/wip/"),
	DATABASE_HINTS		(Root.NONE, DATABASE,		"/hints/"),
	DATABASE_SYSTEM		(Root.NONE, DATABASE,		"/system/"),

	BACKUPS				(Root.NONE,				"/backups/"),
	TEMP				(Root.NONE,				"/temp/"),
	LOGS				(Root.NONE, 			"/logs/"),

	DEFAULTS			(Root.NONE, DATABASE,		"/defaults/"),
	DEFAULTS_FORM		(Root.NONE, DEFAULTS,			"/formation/"),
	DEFAULTS_MAP		(Root.NONE, DEFAULTS,			"/map/"),
	DEFAULTS_MOVE		(Root.NONE, DEFAULTS,			"/move/"),
	DEFAULTS_ALLY		(Root.NONE, DEFAULTS,			"/partner/"),
	DEFAULTS_ITEM		(Root.NONE, DEFAULTS,			"/item/"),
	DEFAULTS_STARS		(Root.NONE, DEFAULTS,			"/starpower/"),

	//=======================================================================================

	DUMP_REPORTS		(Root.DUMP,				"/reports/"),
	DUMP_REQUESTS		(Root.DUMP, DUMP_REPORTS,	"/requests/"),

	DUMP_MAP 			(Root.DUMP,				"/map/"),
	DUMP_MAP_YAY0		(Root.DUMP, DUMP_MAP,		"/yay0/"),		// stripped-down, recompressed assets
	DUMP_MAP_RAW 		(Root.DUMP, DUMP_MAP,		"/raw/"),		// binary map files: _shape, _hit, _tex, .bin
	DUMP_MAP_SRC 		(Root.DUMP, DUMP_MAP,		"/src/"),
	DUMP_MAP_NPC 		(Root.DUMP, DUMP_MAP,		"/npc/"),
	DUMP_MAP_THUMBNAIL	(Root.DUMP, DUMP_MAP,		"/thumbnail/"),

	DUMP_EFFECT 		(Root.DUMP, 			"/effect/", true),
	DUMP_EFFECT_GFX		(Root.DUMP, DUMP_EFFECT,	"/grahics/", true),
	DUMP_EFFECT_RAW		(Root.DUMP, DUMP_EFFECT,	"/raw/", true),
	DUMP_EFFECT_SRC		(Root.DUMP, DUMP_EFFECT,	"/src/", true),

	DUMP_WORLD			(Root.DUMP,				"/world/"),

	DUMP_ACTION 		(Root.DUMP, DUMP_WORLD,		"/action/"),
	DUMP_ACTION_RAW		(Root.DUMP, DUMP_ACTION,		"/raw/"),
	DUMP_ACTION_SRC		(Root.DUMP, DUMP_ACTION,		"/src/"),

	DUMP_ASSIST 		(Root.DUMP, DUMP_WORLD,		"/partner/"),
	DUMP_ASSIST_RAW		(Root.DUMP, DUMP_ASSIST,		"/raw/"),
	DUMP_ASSIST_SRC		(Root.DUMP, DUMP_ASSIST,		"/src/"),

	DUMP_ENTITY 		(Root.DUMP, DUMP_WORLD,		"/entity/"),
	DUMP_ENTITY_RAW		(Root.DUMP, DUMP_ENTITY,		"/raw/"),
	DUMP_ENTITY_SRC		(Root.DUMP, DUMP_ENTITY,		"/src/"),

	DUMP_BATTLE			(Root.DUMP,				"/battle/"),

	DUMP_FORMA			(Root.DUMP, DUMP_BATTLE,	"/formation/"),
	DUMP_FORMA_RAW		(Root.DUMP, DUMP_FORMA,			"/raw/"),
	DUMP_FORMA_SRC		(Root.DUMP, DUMP_FORMA,			"/src/"),
	DUMP_FORMA_ENEMY	(Root.DUMP, DUMP_FORMA,			"/enemy/"),

	DUMP_ITEM			(Root.DUMP, DUMP_BATTLE,	"/item/"),
	DUMP_ITEM_RAW		(Root.DUMP, DUMP_ITEM,			"/raw/"),
	DUMP_ITEM_SRC		(Root.DUMP, DUMP_ITEM,			"/src/"),

	DUMP_MINIGAME		(Root.DUMP, DUMP_BATTLE,	"/command/"),
	DUMP_MINIGAME_RAW	(Root.DUMP, DUMP_MINIGAME,		"/raw/"),
	DUMP_MINIGAME_SRC	(Root.DUMP, DUMP_MINIGAME,		"/src/"),

	DUMP_MOVE			(Root.DUMP, DUMP_BATTLE,	"/move/"),
	DUMP_MOVE_RAW		(Root.DUMP, DUMP_MOVE,			"/raw/"),
	DUMP_MOVE_SRC		(Root.DUMP, DUMP_MOVE,			"/src/"),

	DUMP_ALLY			(Root.DUMP, DUMP_BATTLE,	"/partner/"),
	DUMP_ALLY_RAW		(Root.DUMP, DUMP_ALLY,			"/raw/"),
	DUMP_ALLY_SRC		(Root.DUMP, DUMP_ALLY,			"/src/"),

	DUMP_STARS			(Root.DUMP, DUMP_BATTLE,	"/starpower/"),
	DUMP_STARS_RAW		(Root.DUMP, DUMP_STARS,			"/raw/"),
	DUMP_STARS_SRC		(Root.DUMP, DUMP_STARS,			"/src/"),

	DUMP_IMG			(Root.DUMP,				"/image/"),
	DUMP_IMG_TEX		(Root.DUMP, DUMP_IMG,		"/texture/"),
	DUMP_IMG_COMP		(Root.DUMP, DUMP_IMG,		"/compressed/"),
	DUMP_IMG_BG			(Root.DUMP, DUMP_IMG,		"/bg/"),
	DUMP_IMG_ASSETS		(Root.DUMP, DUMP_IMG,		"/assets/"),
	DUMP_HUD_SCRIPTS	(Root.DUMP, DUMP_IMG,		"/hudscripts/"),
	DUMP_ITEM_SCRIPTS	(Root.DUMP, DUMP_IMG,		"/itemscripts/"),

	DUMP_SPRITE			(Root.DUMP,				"/sprite/"),
	DUMP_SPR_NPC		(Root.DUMP,	DUMP_SPRITE,	"/npc/"),
	DUMP_SPR_NPC_RAW	(Root.DUMP, DUMP_SPR_NPC,		"/raw/"),
	DUMP_SPR_NPC_SRC	(Root.DUMP, DUMP_SPR_NPC,		"/src/"),
	DUMP_SPR_PLR		(Root.DUMP,	DUMP_SPRITE,	"/player/"),
	DUMP_SPR_PLR_RAW	(Root.DUMP, DUMP_SPR_PLR,		"/raw/"),
	DUMP_SPR_PLR_SRC	(Root.DUMP, DUMP_SPR_PLR,		"/src/"),
	DUMP_SPR_PLR_SHARED	(Root.DUMP, DUMP_SPR_PLR_SRC,		"/shared/"),

	DUMP_AUDIO			(Root.DUMP,				"/audio/"),
	DUMP_AUDIO_RAW		(Root.DUMP,	DUMP_AUDIO,		"/raw/"),
	DUMP_AUDIO_BANK		(Root.DUMP,	DUMP_AUDIO,		"/bank/"),
	DUMP_AUDIO_MSEQ		(Root.DUMP,	DUMP_AUDIO,		"/mseq/"),
	DUMP_AUDIO_SFX		(Root.DUMP,	DUMP_AUDIO,		"/sfx/"),

	DUMP_GLOBALS		(Root.DUMP,				"/globals/"),

	DUMP_STRINGS 		(Root.DUMP,				"/strings/"),
	DUMP_STRINGS_FONT	(Root.DUMP, DUMP_STRINGS,	"/font/"),
	DUMP_FONT_STANDARD	(Root.DUMP, DUMP_STRINGS_FONT,	"/normal/"),
	DUMP_FONT_STD_PAL	(Root.DUMP, DUMP_FONT_STANDARD,		"/palette/"),
	DUMP_FONT_CREDITS1	(Root.DUMP, DUMP_STRINGS_FONT,	"/credits-title/"),
	DUMP_FONT_CRD_PAL1	(Root.DUMP, DUMP_FONT_CREDITS1,		"/palette/"),
	DUMP_FONT_CREDITS2	(Root.DUMP, DUMP_STRINGS_FONT,	"/credits-name/"),
	DUMP_FONT_CRD_PAL2	(Root.DUMP, DUMP_FONT_CREDITS2,		"/palette/"),
	DUMP_STRINGS_SRC	(Root.DUMP,	DUMP_STRINGS,	"/src/"),
	DUMP_TXTBOX_IMG		(Root.DUMP, DUMP_STRINGS,	"/textbox/"),
	DUMP_TXTBOX_PAL		(Root.DUMP, DUMP_TXTBOX_IMG,	"/palette/"),

	DUMP_LIB			(Root.DUMP,				"/lib/"),

	DUMP_YAY0_DECODED	(Root.DUMP,				"/yay0/decoded/", true),
	DUMP_YAY0_ENCODED	(Root.DUMP,				"/yay0/encoded/", true),

	//=======================================================================================

	MOD_MAP				(Root.MOD,				"/map/"),
	MOD_MAP_IMPORT		(Root.MOD, MOD_MAP,			"/import/"),		// reusable patch data that can be imported into map patch files
	MOD_MAP_SRC			(Root.MOD, MOD_MAP, 		"/src/"),			// sources copied from the dump directory
	MOD_MAP_GEN			(Root.MOD, MOD_MAP,	 		"/gen/"),			// auto-generated patch files
	MOD_MAP_PATCH		(Root.MOD, MOD_MAP,	 		"/patch/"),			// user patch files
	MOD_MAP_SAVE		(Root.MOD, MOD_MAP,			"/save/"),			// user .map files saved from the editor
	MOD_MAP_BUILD		(Root.MOD, MOD_MAP,			"/build/"),			// binary map files built from the editor
	MOD_MAP_CACHE		(Root.MOD, MOD_MAP, 		"/cache/", true),	// automatically generated files during patch process
	MOD_MAP_TEMP		(Root.MOD, MOD_MAP, 		"/temp/", true),	// automatically generated files during patch process
	MOD_MAP_THUMBNAIL	(Root.MOD, MOD_MAP,			"/thumbnail/"),

	MOD_WORLD			(Root.MOD,				"/world/"),

	/*
	MOD_ACTION 			(Root.MOD, MOD_WORLD,		"/action/"),
	MOD_ACTION_RAW		(Root.MOD, MOD_ACTION,			"/raw/"),
	MOD_ACTION_SRC		(Root.MOD, MOD_ACTION,			"/src/"),
	MOD_ACTION_PATCH	(Root.MOD, MOD_ACTION,			"/patch/"),
	*/

	MOD_ASSIST 			(Root.MOD, MOD_WORLD,		"/partner/"),
	MOD_ASSIST_RAW		(Root.MOD, MOD_ASSIST,			"/raw/"),
	MOD_ASSIST_SRC		(Root.MOD, MOD_ASSIST,			"/src/"),
	MOD_ASSIST_PATCH	(Root.MOD, MOD_ASSIST,			"/patch/"),
	MOD_ASSIST_TEMP		(Root.MOD, MOD_ASSIST,			"/temp/", true),

	MOD_BATTLE			(Root.MOD,				"/battle/"),
	MOD_FORMA			(Root.MOD, MOD_BATTLE,		"/formation/"),
	MOD_FORMA_IMPORT	(Root.MOD, MOD_FORMA,			"/import/"),	// reusable patch data for custom imports
	MOD_FORMA_ENEMY		(Root.MOD, MOD_FORMA_IMPORT,		"/enemy/"),	// reusable patch data for importing enemies
	MOD_FORMA_SRC		(Root.MOD, MOD_FORMA,			"/src/"),		// sources copied from the dump directory
	MOD_FORMA_PATCH		(Root.MOD, MOD_FORMA,			"/patch/"),		// user patch files
	MOD_FORMA_TEMP		(Root.MOD, MOD_FORMA,			"/temp/", true),// automatically generated files during patch process

	MOD_ITEM			(Root.MOD, MOD_BATTLE,		"/item/"),
	MOD_ITEM_SRC		(Root.MOD, MOD_ITEM,			"/src/"),
	MOD_ITEM_PATCH		(Root.MOD, MOD_ITEM, 			"/patch/"),
	MOD_ITEM_TEMP		(Root.MOD, MOD_ITEM, 			"/temp/", true),

	MOD_MINIGAME		(Root.MOD, MOD_BATTLE,		"/command/"),
	MOD_MINIGAME_SRC	(Root.MOD, MOD_MINIGAME,		"/src/"),
	MOD_MINIGAME_PATCH	(Root.MOD, MOD_MINIGAME, 		"/patch/"),
	MOD_MINIGAME_TEMP	(Root.MOD, MOD_MINIGAME, 		"/temp/", true),

	MOD_MOVE			(Root.MOD, MOD_BATTLE,		"/move/"),
	MOD_MOVE_SRC		(Root.MOD, MOD_MOVE, 			"/src/"),
	MOD_MOVE_PATCH		(Root.MOD, MOD_MOVE, 			"/patch/"),
	MOD_MOVE_TEMP		(Root.MOD, MOD_MOVE, 			"/temp/", true),

	MOD_ALLY			(Root.MOD, MOD_BATTLE,		"/partner/"),
	MOD_ALLY_SRC		(Root.MOD, MOD_ALLY,			"/src/"),
	MOD_ALLY_PATCH		(Root.MOD, MOD_ALLY,			"/patch/"),
	MOD_ALLY_TEMP		(Root.MOD, MOD_ALLY,			"/temp/", true),

	MOD_STARS			(Root.MOD, MOD_BATTLE,		"/starpower/"),
	MOD_STARS_SRC		(Root.MOD, MOD_STARS,			"/src/"),
	MOD_STARS_PATCH		(Root.MOD, MOD_STARS,			"/patch/"),
	MOD_STARS_TEMP		(Root.MOD, MOD_STARS,			"/temp/", true),

	MOD_IMG				(Root.MOD, 				"/image/"),
	MOD_IMG_CACHE		(Root.MOD, MOD_IMG,			"/cache/"),
	MOD_IMG_COMP		(Root.MOD, MOD_IMG,			"/compressed/"),
	MOD_IMG_TEX			(Root.MOD, MOD_IMG,			"/texture/"),
	MOD_IMG_BG			(Root.MOD, MOD_IMG,			"/bg/"),
	MOD_IMG_ASSETS		(Root.MOD, MOD_IMG,			"/assets/"),
	MOD_HUD_SCRIPTS		(Root.MOD, MOD_IMG,			"/hudscripts/"),
	MOD_ITEM_SCRIPTS	(Root.MOD, MOD_IMG,			"/itemscripts/"),

	MOD_SPRITE			(Root.MOD,				"/sprite/"),
	MOD_SPR_NPC			(Root.MOD, MOD_SPRITE,		"/npc/"),
	MOD_SPR_NPC_SRC		(Root.MOD, MOD_SPR_NPC,			"/src/"),
	MOD_SPR_NPC_TEMP	(Root.MOD, MOD_SPR_NPC,			"/temp/"),
	MOD_SPR_NPC_CACHE	(Root.MOD, MOD_SPR_NPC,			"/cache/"),
	MOD_SPR_PLR			(Root.MOD, MOD_SPRITE,		"/player/"),
	MOD_SPR_PLR_SRC		(Root.MOD, MOD_SPR_PLR,			"/src/"),
	MOD_SPR_PLR_SHARED	(Root.MOD, MOD_SPR_PLR_SRC,			"/shared/", true),
	MOD_SPR_PLR_TEMP	(Root.MOD, MOD_SPR_PLR,			"/temp/"),
	MOD_SPR_PLR_CACHE	(Root.MOD, MOD_SPR_PLR,			"/cache/"),

	MOD_AUDIO			(Root.MOD,				"/audio/"),
	MOD_AUDIO_RAW		(Root.MOD,	MOD_AUDIO,		"/raw/"),
	MOD_AUDIO_BUILD		(Root.MOD,	MOD_AUDIO,		"/build/"),
	MOD_AUDIO_BANK		(Root.MOD,	MOD_AUDIO,		"/bank/"),
	MOD_AUDIO_MSEQ		(Root.MOD,	MOD_AUDIO,		"/mseq/"),
	MOD_AUDIO_SFX		(Root.MOD,	MOD_AUDIO,		"/sfx/"),

	MOD_EDITOR			(Root.MOD,				"/editor/"),

	MOD_STRINGS			(Root.MOD,				"/strings/"),
	MOD_STRINGS_SRC		(Root.MOD, MOD_STRINGS,		"/src/"),
	MOD_STRINGS_PATCH	(Root.MOD, MOD_STRINGS,		"/patch/"),
	MOD_STRINGS_FONT	(Root.MOD, MOD_STRINGS,		"/font/"),
	MOD_STD_FONT		(Root.MOD, MOD_STRINGS_FONT,	"/normal/"),
	MOD_STD_FONT_PAL	(Root.MOD, MOD_STD_FONT,			"/palette/"),
	MOD_TXTBOX_IMG		(Root.MOD, MOD_STRINGS,		"/textbox/"),
	MOD_TXTBOX_PAL		(Root.MOD, MOD_TXTBOX_IMG,		"/palette/"),

	MOD_GLOBALS			(Root.MOD,				"/globals/"),
	MOD_ENUMS			(Root.MOD, MOD_GLOBALS,		"/enum/"),
	MOD_PATCH			(Root.MOD, MOD_GLOBALS,		"/patch/"),
	MOD_SYSTEM			(Root.MOD, MOD_GLOBALS,		"/system/"),

	MOD_RESOURCE		(Root.MOD, 				"/res/"),
	MOD_OUT 			(Root.MOD, 				"/out/");

	// @formatter:on
	//=======================================================================================

	public static final String FN_GAME_BYTES = "GameBytes.txt";
	public static final String FN_GAME_FLAGS = "GameFlags.txt";

	public static final String FN_MOD_BYTES = "ModBytes.txt";
	public static final String FN_MOD_FLAGS = "ModFlags.txt";

	public static final String FN_BATTLE_SECTIONS = "BattleSections.txt";
	public static final String FN_BATTLE_ACTORS = "ActorTypes.xml";

	public static final String FN_BATTLE_ACTIONS = "Actions.txt";
	public static final String FN_BATTLE_MOVES = "Moves.txt";
	public static final String FN_BATTLE_ITEMS = "Items.txt";

	public static final String FN_MAP_EDITOR_CONFIG = "map_editor.cfg";
	public static final String FN_EDITOR_GUIDES = "EditorGuides.xml";

	public static final String FN_STRING_EDITOR_CONFIG = "string_editor.cfg";
	public static final String FN_SPRITE_EDITOR_CONFIG = "sprite_editor.cfg";

	public static final String FN_IMAGE_ASSETS = "ImageAssets.xml";
	public static final String FN_ITEM_SCRIPTS = "ItemEntities.xml";
	public static final String FN_HUD_SCRIPTS = "HudElements.xml";
	public static final String EXT_ITEM_SCRIPT = ".is";
	public static final String EXT_HUD_SCRIPT = ".hs";

	public static final String FN_SPRITE_SHADING = "SpriteShading.xml";
	public static final String FN_SPRITE_PROFILE_NAMES = "shading_profiles.txt";
	public static final String FN_SPRITE_TABLE = "SpriteTable.xml";
	public static final String FN_SPRITESHEET = "SpriteSheet.xml";
	public static final String FN_SPRITE_CACHE = "checksums.txt";

	public static final String FN_AUDIO_FILES = "Files.xml";
	public static final String FN_AUDIO_SONGS = "Songs.xml";
	public static final String FN_AUDIO_BANKS = "Banks.xml";
	public static final String FN_AUDIO_DRUMS = "Drums.xml";
	public static final String FN_AUDIO_BGMS = "BGMs.xml";
	public static final String FN_SOUND_BANK = "SoundBank.xml";
	public static final String EXT_BANK = ".bk";

	public static final String FN_STRING_CONSTANTS = "StringConstants.xml";

	public static final String FN_MAP_TABLE = "MapTable.xml";
	public static final String FN_MOVE_TABLE = "MoveTable.csv";
	public static final String FN_ITEM_TABLE = "ItemTable.csv";
	public static final String FN_ITEMS = "Items.xml";
	public static final String FN_MOVES = "Moves.xml";
	public static final String FN_WORLD_MAP = "WorldMap.xml";

	public static final String FN_PARTNERS = "Partners.xml";

	public static final String FN_MAP_NICKNAMES = "default_map_names.txt";

	private final Root root;
	private final String path;
	private final boolean optional;

	private Directories(Root root, String path)
	{
		this(root, path, false);
	}

	private Directories(Root root, Directories parent, String path)
	{
		this(root, parent, path, false);
	}

	private Directories(Root root, Directories parent, String path, boolean optional)
	{
		this.root = root;
		String fullPath = parent.path + "/" + path;
		this.path = fullPath.replaceAll("/+", "/");
		this.optional = optional;
	}

	private Directories(Root root, String path, boolean optional)
	{
		this.root = root;
		this.path = path;
		this.optional = optional;
	}

	public static final List<Directories> requiredDumpDirectories;
	public static final List<Directories> requiredModDirectories;

	static {
		requiredDumpDirectories = new LinkedList<>();
		requiredModDirectories = new LinkedList<>();

		for (Directories dir : Directories.values()) {
			if (!dir.optional) {
				switch (dir.root) {
					case DUMP:
						requiredDumpDirectories.add(dir);
						break;
					case MOD:
						requiredModDirectories.add(dir);
						break;
					default:
				}
			}
		}
	}

	@Override
	public String toString()
	{
		return getRootPath(root) + path;
	}

	public String toPath()
	{
		switch (root) {
			case MOD:
				return "$mod" + path;
			case DUMP:
				return "$dump" + path;
			case NONE:
				return path;
		}

		return path;
	}

	public File toFile()
	{
		return new File(this.toString());
	}

	public String getRelativeName(File file)
	{
		String name = file.getName();
		try {
			Path dirPath = Paths.get(toFile().getCanonicalPath());
			Path filePath = Paths.get(file.getCanonicalPath());
			name = dirPath.relativize(filePath).toString();
			name = "/" + name.replaceAll("\\\\", "/");
		}
		catch (IOException e) {
			Logger.printStackTrace(e);
		}
		return name;
	}

	public File getFile(String name)
	{
		return new File(this.toFile(), name);
	}

	private enum Root
	{
		NONE, DUMP, MOD
	}

	private static String getRootPath(Root root)
	{
		switch (root) {
			case NONE:
				return Environment.getWorkingDirectory().getAbsolutePath();
			case DUMP:
				return dumpPath;
			case MOD:
				return modPath;
		}
		return null;
	}

	private static String dumpPath = null;
	private static String modPath = null;

	public static void setDumpDirectory(String path)
	{
		if (path.contains("\\"))
			path = path.replaceAll("\\\\", "/");
		if (path.endsWith("/"))
			path = path.substring(0, path.length() - 1);

		dumpPath = path;
		Logger.log("Using dump: " + dumpPath);
	}

	public static String getDumpPath()
	{
		return dumpPath;
	}

	public static void setProjectDirectory(String path)
	{
		if (path.contains("\\"))
			path = path.replaceAll("\\\\", "/");
		if (path.endsWith("/"))
			path = path.substring(0, path.length() - 1);
		modPath = path;

		Logger.log("Using Mod Directory: " + modPath);
	}

	public static String getModPath()
	{
		return modPath;
	}

	public static void createDumpDirectories() throws IOException
	{
		if (dumpPath == null)
			throw new IOException("Dump directory is not set.");

		for (Directories dir : Directories.values()) {
			if (dir.root == Root.DUMP && !dir.optional)
				FileUtils.forceMkdir(dir.toFile());
		}
	}

	public static void createModDirectories() throws IOException
	{
		if (modPath == null)
			throw new IOException("Mod directory is not set.");

		for (Directories dir : Directories.values()) {
			if (dir.root == Root.MOD && !dir.optional)
				FileUtils.forceMkdir(dir.toFile());
		}
	}

	/**
	 * Copies (without replacing) a file
	 */
	public static void copyIfMissing(Directories src, Directories dest, String filename) throws IOException
	{
		File srcFile = new File(src + filename);
		if (!srcFile.exists())
			throw new RuntimeException("Missing source file: " + srcFile.getCanonicalPath());

		File destFile = new File(dest + filename);
		if (!destFile.exists())
			FileUtils.copyFile(srcFile, destFile);
	}

	/**
	 * Copies the contents of a directory only if the destination is empty
	 */
	public static void copyIfEmpty(Directories src, Directories dest) throws IOException
	{
		copyIfEmpty(src, dest, false);
	}

	/**
	 * Copies the contents of a directory only if the destination is empty
	 */
	public static void copyIfEmpty(Directories src, Directories dest, boolean ignoreMissingSource) throws IOException
	{
		File srcDir = src.toFile();
		if (!srcDir.exists()) {
			if (ignoreMissingSource)
				return;
			else
				throw new RuntimeException("Missing source directory: " + srcDir.getCanonicalPath());
		}

		File destDir = dest.toFile();
		if (!destDir.exists())
			throw new RuntimeException("Missing target directory: " + destDir.getCanonicalPath());

		if (destDir.list().length == 0)
			FileUtils.copyDirectory(srcDir, destDir);
	}

	/**
	 * Copies (recursive) all contents of a directory which are missing in another directory
	 */
	public static void copyAllMissing(Directories src, Directories dest) throws IOException
	{
		copyAllMissing(src, dest, false);
	}

	/**
	 * Copies (recursive) all contents of a directory which are missing in another directory
	 */
	public static void copyAllMissing(Directories src, Directories dest, boolean ignoreMissingSource) throws IOException
	{
		File srcDir = src.toFile();
		if (!srcDir.exists()) {
			if (ignoreMissingSource)
				return;
			else
				throw new RuntimeException("Missing source directory: " + srcDir.getCanonicalPath());
		}

		File destDir = dest.toFile();
		if (!destDir.exists())
			throw new RuntimeException("Missing target directory: " + destDir.getCanonicalPath());

		copyAllMissing(srcDir, destDir);
	}

	private static void copyAllMissing(File srcDir, File destDir) throws IOException
	{
		for (File srcFile : srcDir.listFiles()) {
			File destFile = new File(destDir.getAbsolutePath() + File.separator + srcFile.getName());

			if (srcFile.isDirectory()) {
				if (!destFile.exists())
					FileUtils.forceMkdir(destFile);
				copyAllMissing(srcFile, destFile);
			}
			else {
				if (!destFile.exists())
					FileUtils.copyFile(srcFile, destFile);
			}
		}
	}

	/**
	 * Copies (recursive) all contents of a directory which are missing in another directory
	 */
	public static void copyAllMissing(Directories src, Directories dest, String ... ext) throws IOException
	{
		copyAllMissing(src, dest, false, ext);
	}

	/**
	 * Copies (recursive) all contents of a directory which are missing in another directory
	 */
	public static void copyAllMissing(Directories src, Directories dest, boolean ignoreMissingSource, String ... ext) throws IOException
	{
		File srcDir = src.toFile();
		if (!srcDir.exists()) {
			if (ignoreMissingSource)
				return;
			else
				throw new RuntimeException("Missing source directory: " + srcDir.getCanonicalPath());
		}

		File destDir = dest.toFile();
		if (!destDir.exists())
			throw new RuntimeException("Missing target directory: " + destDir.getCanonicalPath());

		copyAllMissing(srcDir, destDir, ext);
	}

	private static void copyAllMissing(File srcDir, File destDir, String ... ext) throws IOException
	{
		for (File srcFile : FileUtils.listFiles(srcDir, ext, false)) {
			File destFile = new File(destDir.getAbsolutePath() + File.separator + srcFile.getName());

			if (srcFile.isDirectory()) {
				if (!destFile.exists())
					FileUtils.forceMkdir(destFile);
				copyAllMissing(srcFile, destFile, ext);
			}
			else {
				if (!destFile.exists())
					FileUtils.copyFile(srcFile, destFile);
			}
		}
	}
}
