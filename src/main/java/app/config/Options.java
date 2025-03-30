package app.config;

import java.util.HashMap;

import app.Environment;

/**
 * These options are used with {@link Config} objects to set user options for the map editor,
 * the dumping process, the patching process, etc. The {@link Scope} describes which of these
 * domains the Option pertains to and the {@link Type} defines acceptable values for it to hold.
 * Type checking is enforced by getter/setter methods in the Config class.
 *
 * Options with Main or Dump scope belong to /cfg/main.cfg
 * Options with Editor or Patch scope belong to {mod.dir}/mod.cfg
 */
public enum Options
{
	// @formatter:off
	// options for main.cfg, mostly to keep track of user directories
	ModPath				(true, Scope.Main, Type.String, "ModPath", null),
	RomPath				(true, Scope.Main, Type.String, "RomPath", null),
//	Dumped				(true, Scope.Main, Type.Boolean, "Dumped", "False"),

	LogDetails			(true, Scope.Main, Type.Boolean, "LogDetails", "false"),
	Theme				(true, Scope.Main, Type.String, "Theme", "FlatLight"),
	ExitToMenu			(true, Scope.Main, Type.Boolean, "ExitToMenu", "true"),
	CheckForUpdates		(true, Scope.Main, Type.Boolean, "CheckForUpdates", "true"),

	// options for dumping assets
	CleanDump			(true, Scope.Main, Type.Boolean, "FullDump", "False", "Dump everything (clears existing dump)",
			"Clears the existing dump directory and dumps all assets."),
	DumpReports			(true, Scope.Main, Type.Boolean, "DumpReports", "False", "Create reports on dumped content",
			"Enables gathering and writing reports on metadata tracked during the dumping process."),
	DumpProfiling		(true, Scope.Main, Type.Boolean, "DumpProfiling", "False", "Profile map dumping performance",
			"Prints performance profiles for map dumping."),
	DumpMessages		(true, Scope.Main, Type.Boolean, "DumpMessages", "True", "Messages and Message Graphics", ""),
	DumpTables			(true, Scope.Main, Type.Boolean, "DumpTables", "True", "Items, Moves, Hud Elements", ""),
	DumpMaps			(true, Scope.Main, Type.Boolean, "DumpMaps", "True", "Map Data",
			"Includes configuration files, scripts, and maps."),
	RecompressMaps		(true, Scope.Main, Type.Boolean, "RecompressMapAssets", "False", "Recompress Map Data",
			"Attempt to compress map data more thoroughly than the originals."),
	DumpBattles			(true, Scope.Main, Type.Boolean, "DumpBattles", "True", "Battle Scripts",
			"Includes configuration files and scripts."),
	DumpMoves			(true, Scope.Main, Type.Boolean, "DumpMoves", "True", "Move and Item Scripts",
			"Also includes Star Powers, Partner modes, and item scripts."),
	DumpPartners		(true, Scope.Main, Type.Boolean, "DumpPartners", "True", "Partners",
			"Partner overworld actions."),
	DumpWorld			(true, Scope.Main, Type.Boolean, "DumpWorld", "True", "World Data",
			"Scripts for entities and actions."),
	DumpTextures		(true, Scope.Main, Type.Boolean, "DumpTextures", "True", "Textures", ""),
	DumpSprites			(true, Scope.Main, Type.Boolean, "DumpSprites", "True", "Sprites", ""),
	DumpAudio			(true, Scope.Main, Type.Boolean, "DumpAudio", "True", "Audio Files", ""),
	DumpLibrary			(true, Scope.Main, Type.Boolean, "DumpLibrary", "True", "Library Data", ""),

	DumpVersion			(true, Scope.Dump, Type.String, "DumpVersion", Environment.getVersionString()),

	PrintLineOffsets	(true, Scope.Main, Type.Boolean, "PrintLineOffsets", "True", "Print line offsets",
			"Print memory offsets before each line for scripts and functions."),
	UseTabIndents		(true, Scope.Main, Type.Boolean, "UseTabIndents", "True", "Use tabs for indentation",
			"Indent scripts using tab characters rather than spaces."),
	UseTabSpacing		(true, Scope.Main, Type.Boolean, "UseTabSpacing", "False", "Use tabs for spacing",
			"Use tab characters for spacing within functions and scripts (assumes 4-space tab length)."),
	IndentPrintedData	(true, Scope.Main, Type.Boolean, "IndentPrintedData", "True", "Indent printed structs",
			"Structs other than functions and scripts will be indented."),
	NewlineOpenBrace	(true, Scope.Main, Type.Boolean, "NewlineOpenBrace", "True", "Put open brace on new line",
			"The open curly bracket at the start of a struct will start on its own line."),

	PrintRequiredBy		(true, Scope.Main, Type.Boolean, "PrintRequiredBy", "False", "Print \"RequiredBy\" Annotations", ""),
	RoundFixedVars		(true, Scope.Main, Type.Boolean, "RoundFixedVars", "True", "Round *Fixed Vars",
			"Dumped values will be less precise, but more likely to match the intended original values."),

	UseShorthandVars	(true, Scope.Main, Type.Boolean, "UseShorthandVars", "True", "Use shorthand *Var names",
			"Allows script vars to be written without brackets; *VarX instead of *Var[X]"),

	TrackScriptVarTypes	(true, Scope.Main, Type.Boolean, "TrackScriptVarTypes", "True", "Aggressive script type analysis",
			"Try to track Var types through function calls. May result in incorrect enum types in dumped scripts."),

	GenerateNpcIDs	(true, Scope.Main, Type.Boolean, "GenerateNpcIDs", "True", "Auto-generate constants for NPC IDs",
			"Create constant definitions for NPCs in dumped map scripts."),

	// options for building assets and patching the ROM
	ClearMapCache		(true, Scope.Patch, Type.Boolean, "ClearMapCache", "False", "Clear Map Index",
			"Delete the map index before building."),
	ClearSpriteCache	(true, Scope.Patch, Type.Boolean, "ClearSpriteCache", "False", "Clear Sprite Cache",
			"Delete previously-cached sprite before building."),
	ClearTextureCache	(true, Scope.Patch, Type.Boolean, "ClearTextureCache", "False", "Clear Texture Cache",
			"Delete previously-cached texture archives before building."),

	CompressBattleData	(true, Scope.Patch, Type.Boolean, "CompressBattleData", "True", "Compress Battle Data",
			"(Recommended) Save space by compressing battle data + modifying the battle loading code."),

	PackScriptOpcodes	(true, Scope.Patch, Type.Boolean, "PackScriptOpcodes", "False", "Pack Script Opcode/Length",
			"(Recommended) Reduce the size of scripts by packing length and opcode into one word, saving 4 bytes per line."),

	EnableDebugCode		(true, Scope.Patch, Type.Boolean, "EnableDebugCode", "False", "Enable Debug Information",
			"Print live debug information and enable the cheat menu."),

	EnableVarLogging	(true, Scope.Patch, Type.Boolean, "EnableVarLogging", "False", "Enable Variable Logging",
			"(Requires debug information enabled) Print a message to the screen whenever a Game/Mod Byte/Flag is written."),

	CaptureThumbnails	(true, Scope.Patch, Type.Boolean, "CaptureThumbnails", "False", "Capture Map Thumbnails",
			"Captures missing thumbnail images for each map."),

	ClearJapaneseStrings	(true, Scope.Patch, Type.Boolean, "ClearJapaneseStrings", "True", "Clear SJIS Strings",
			"(Recommended) Clear the space used by unused SJIS (Japanese) strings, making extra room for new data."),
	BuildTextures		(true, Scope.Patch, Type.Boolean, "BuildTextures",	"False", "Build Texture Archives",
			"Only use this option if you are using modified or custom textures."),
	BuildBackgrounds	(true, Scope.Patch, Type.Boolean, "BuildBackgrounds",	"False", "Build Backgrounds",
			"Only use this option if you are using modified or custom backgrounds."),
	BuildSpriteSheets	(true, Scope.Patch, Type.Boolean, "BuildSpriteSheets",	"False", "Build Sprite Sheets",
			"Only use this option if you are using modified or custom spritesheets."),

	PatchFonts			(true, Scope.Patch, Type.Boolean, "PatchFonts",	"False", "Inject Font",
			"Injects font images, making additional adjustments if they are larger than normal."),

	BuildSoundBanks		(true, Scope.Patch, Type.Boolean, "BuildSoundBanks",	"False", "Build Sound Banks",
			"Only use this option if you are adding custom music."),

	BuildAudio			(true, Scope.Patch, Type.Boolean, "BuildAudio",	"False", "Inject Audio Files",
			"Use this option if you are adding custom music or sounds."),

	SkipIntroLogos		(true, Scope.Patch, Type.Boolean, "SkipIntroLogos", "True", "Skip Intro Logos",
			"Developer logos will not appear during boot."),
	DisableDemoReel		(true, Scope.Patch, Type.Boolean, "DisableDemoReel", "True", "Disable Demo Reel",
			"Demo reel will not play from title screen."),
	DisableIntroStory	(true, Scope.Patch, Type.Boolean, "DisableIntroStory", "True", "Disable Intro Story",
			"Introduction story scenes will not play."),

	Allow10Partners		(true, Scope.Patch, Type.Boolean, "Allow10Partners", "False", "Allow 10 partners",
			"Goombaria and Goompa's partner slots become properly patchable."),

	QuickLaunch			(true, Scope.Patch, Type.Boolean, "QuickLaunch", "False", "Quick Launch",
			"The last saved file will immediately load on start up."),

	AllowDuplicateSpriteNames
						(true, Scope.Patch, Type.Boolean, "AllowDuplicateSpriteNames", "True", "Allow Duplicate Sprite Names",
								"Disabling this option will require unique names for sprite animations and palettes."),

	AllowWriteConflicts
						(true, Scope.Patch, Type.Boolean, "AllowWriteConflicts", "False", "Allow Conflicting Patches",
								"(Strongly discouraged) Allows the same part of the ROM to be patched multiple times."),

	AutoBuildMapAssets	(true, Scope.Patch, Type.Boolean, "AutoBuildMapAssets",	"True", "Automatically Build Map Assets",
								"Missing shape and hit files will be built automatically at compile time."),

	InitialMap			(true, Scope.Patch, Type.String, "InitialMap", "kmr_20"),
	InitialEntry		(true, Scope.Patch, Type.String, "InitialEntry", "Entry0"),

	DebugMapName		(true, Scope.Patch, Type.String, "DebugMapName", "machi"),
	DebugBattleID		(true, Scope.Patch, Type.String, "DebugBattleID", "0000"),

	DebugWatch0			(true, Scope.Patch, Type.String, "DebugWatch0", "GameByte[0]"),
	DebugWatch1			(true, Scope.Patch, Type.String, "DebugWatch1", "8010F07C,1,ActionState"),
	DebugWatch2			(true, Scope.Patch, Type.String, "DebugWatch2", null),
	DebugWatch3			(true, Scope.Patch, Type.String, "DebugWatch3", null),
	DebugWatch4			(true, Scope.Patch, Type.String, "DebugWatch4", null),
	DebugWatch5			(true, Scope.Patch, Type.String, "DebugWatch5", null),
	DebugWatch6			(true, Scope.Patch, Type.String, "DebugWatch6", null),
	DebugWatch7			(true, Scope.Patch, Type.String, "DebugWatch7", null),

	CheckScriptSyntax	(true, Scope.Patch, Type.Boolean, "CheckScriptSyntax", "True"),

	IncreaseHeapSizes	(true, Scope.Patch, Type.Boolean, "IncreaseHeapSizes", "True", "Increase Heap Sizes",
			"Increase the RAM space for various heaps. Enable debug information to visualize usage."),

	HeapSizeWorld		(true, Scope.Patch, Type.Hex, "WorldHeapSize", "C0000", 0x54000, 0x200000, 0x800),
	HeapSizeCollision	(true, Scope.Patch, Type.Hex, "CollisionHeapSize", "C0000", 0x18000, 0x200000, 0x800),
	HeapSizeSprite		(true, Scope.Patch, Type.Hex, "SpriteHeapSize", "C0000", 0x40000, 0x200000, 0x10000),
	HeapSizeBattle		(true, Scope.Patch, Type.Hex, "BattleHeapSize", "C0000", 0x25800, 0x200000, 0x800),
	HeapSizeAudio		(true, Scope.Patch, Type.Hex, "AudioHeapSize", "56000", 0x56000, 0x200000, 0x800),

	CompileVersion		(true, Scope.Patch, Type.String, "BuildVersion", Environment.getVersionString()),

	ModVersionString	(true, Scope.Patch, Type.String, "ModVersionString", "Paper Mario Mod"),

	CompressModPackage	(true, Scope.Patch, Type.Boolean, "CompressModPackage", "True", "Compress Mod Package",
			"Use Yay0 to compress the final diff file for your mod. May take several additional minutes."),

	DebugInlineScripts	(false, Scope.Patch, Type.Boolean, "DebugInlineScripts", "False"),

	// editor options
	EditorDebugMode		(false, Scope.MapEditor, Type.Boolean, "EditorDebugMode", "False"),
	Author				(false, Scope.MapEditor, Type.String, "Author", "unnamed", "Author Name", ""),
	ShowCurrentMode		(true, Scope.MapEditor, Type.Boolean, "ShowCurrentMode", "True", "Show Mode in Viewport", ""),
	UndoLimit			(true, Scope.MapEditor, Type.Integer, "UndoLimit", "32", "Undo Limit", "", 1.0),
	BackupInterval		(true, Scope.MapEditor, Type.Integer, "BackupInterval", "-1", "Backup Interval",
			"How often (in mintues) to automatically save backups. Negative values mean 'never'."),
	AngleSnap			(true, Scope.MapEditor, Type.Float,  "AngleSnap", "15.0", "Angle Snap Increment",
			"Sets the angle increment used for rotations with rotation snap enabled.", 1.0, 180.0, 1.0),
	uvScale				(true, Scope.MapEditor, Type.Float,  "UVScale", "16.0", "Default UV Scale",
			"The default scale in texels to world units to use for UV generation.", 1.0, 128.0, 1.0),
	ScrollSensitivity	(true, Scope.MapEditor, Type.Integer, "ScrollWheelSensitivity", "32", "Scroll Sensitivity",
			"Set how sensitive scroll panels are to mouse wheel scrolling", 1.0, 1024.0, 1.0),
	NormalsLength		(true, Scope.MapEditor, Type.Float,  "NormalsLength", "16.0", "Normals Draw Length",
					"The length of normal visualizations draw in the editor.", 1.0, 1024.0, 1.0),
	RecentMap0			(true, Scope.MapEditor, Type.String, "RecentMap0", null),
	RecentMap1			(true, Scope.MapEditor, Type.String, "RecentMap1", null),
	RecentMap2			(true, Scope.MapEditor, Type.String, "RecentMap2", null),
	RecentMap3			(true, Scope.MapEditor, Type.String, "RecentMap3", null),
	RecentMap4			(true, Scope.MapEditor, Type.String, "RecentMap4", null),
	RecentMap5			(true, Scope.MapEditor, Type.String, "RecentMap5", null),

	SprLastNpcSprite		(true, Scope.SpriteEditor, Type.Integer, "LastNpcSprite", "129"),
	SprLastPlayerSprite		(true, Scope.SpriteEditor, Type.Integer, "SprLastPlayerSprite", "9"),
	SprUseFiltering			(true, Scope.SpriteEditor, Type.Boolean, "UseFiltering", "false"),
	SprFlipHorizontal		(true, Scope.SpriteEditor, Type.Boolean, "FlipHorizontal", "false"),
	SprShowScaleReference	(true, Scope.SpriteEditor, Type.Boolean, "ShowScaleReference", "false"),
	SprHighlightCommand		(true, Scope.SpriteEditor, Type.Boolean, "HighlightCommand", "true"),
	SprHighlightSelected	(true, Scope.SpriteEditor, Type.Boolean, "HighlightSelected", "true"),
	SprEnableBackground		(true, Scope.SpriteEditor, Type.Boolean, "EnableBackground", "false"),

	StrPrintDelay			(true, Scope.StringEditor, Type.Boolean, "PrintDelay", "true"),
	StrViewportGuides		(true, Scope.StringEditor, Type.Boolean, "ViewportGuides", "true"),
	StrUseCulling			(true, Scope.StringEditor, Type.Boolean, "UseCulling", "true");
	// @formatter:on

	public final Scope scope;
	public final boolean required;
	public final Type type;
	public final String key;
	public final String defaultValue;
	public final String guiName;
	public final String guiDesc;
	public final double min;
	public final double max;
	public final double step;

	// most general option
	private Options(boolean required, Scope scope, Type type, String key, String defaultValue,
		String guiName, String guiDesc, double min, double max, double step)
	{
		this.key = key;
		this.required = required;
		this.defaultValue = defaultValue;
		this.guiName = guiName;
		this.guiDesc = guiDesc;
		this.scope = scope;
		this.type = type;
		this.min = min;
		this.max = max;
		this.step = step;
	}

	// for options without maximum limits
	private Options(boolean required, Scope scope, Type type, String key, String defaultValue,
		String checkBoxLabel, String checkBoxDesc, double min)
	{
		this(required, scope, type, key, defaultValue, checkBoxLabel, checkBoxDesc, min, Double.POSITIVE_INFINITY, 1.0);
	}

	// for options without limits
	private Options(boolean required, Scope scope, Type type, String key, String defaultValue,
		String checkBoxLabel, String checkBoxDesc)
	{
		this(required, scope, type, key, defaultValue, checkBoxLabel, checkBoxDesc, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, 1.0);
	}

	// for bounded numeric options with no UI representation
	private Options(boolean required, Scope scope, Type type, String key, String defaultValue, double min, double max, double step)
	{
		this(required, scope, type, key, defaultValue, "", "", min, max, step);
	}

	// for lower-bounded numeric options with no UI representation
	private Options(boolean required, Scope scope, Type type, String key, String defaultValue, double min)
	{
		this(required, scope, type, key, defaultValue, "", "", min, Double.POSITIVE_INFINITY, 1.0);
	}

	// for options with no UI representation
	private Options(boolean required, Scope scope, Type type, String key, String defaultValue)
	{
		this(required, scope, type, key, defaultValue, "", "", Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, 1.0);
	}

	public void setToDefault(Config cfg)
	{
		switch (type) {
			case Boolean:
				cfg.setBoolean(this, this.defaultValue);
				break;
			case Integer:
				cfg.setInteger(this, this.defaultValue);
				break;
			case Hex:
				cfg.setHex(this, this.defaultValue);
				break;
			case Float:
				cfg.setFloat(this, this.defaultValue);
				break;
			case String:
				cfg.setString(this, this.defaultValue);
				break;
		}
	}

	public static enum Scope
	{
		Main,
		MapEditor,
		SpriteEditor,
		StringEditor,
		Dump,
		Patch
	}

	public static enum Type
	{
		Boolean,
		Integer,
		Hex,
		Float,
		String
	}

	public static interface ConfigOptionEditor
	{
		public void read(Config cfg);

		public boolean write(Config cfg);
	}

	private static HashMap<String, Options> optNameMap;

	static {
		optNameMap = new HashMap<>();
		for (Options opt : Options.values())
			optNameMap.put(opt.key, opt);
	}

	public static Options getOption(String key)
	{
		return optNameMap.get(key);
	}
}
