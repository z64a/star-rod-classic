package patcher;

import static app.Directories.*;
import static app.config.Options.CaptureThumbnails;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;

import app.Directories;
import app.Environment;
import app.StarRodException;
import app.config.Config;
import app.config.Options;
import app.config.WatchListEntry;
import app.input.IOUtils;
import app.input.InvalidInputException;
import game.battle.ActorTypesEditor;
import game.battle.AuxBattlePatcher;
import game.battle.BattlePatcher;
import game.globals.ItemModder;
import game.globals.MoveModder;
import game.map.Map;
import game.map.MapIndex;
import game.map.config.MapConfigTable;
import game.map.config.MapConfigTable.AreaConfig;
import game.map.config.MapConfigTable.MapConfig;
import game.map.editor.MapEditor;
import game.map.patching.MapPatcher;
import game.map.shading.SpriteShadingEditor;
import game.shared.DataUtils;
import game.shared.ProjectDatabase;
import game.shared.ProjectDatabase.ConstEnum.EnumPair;
import game.shared.encoder.GlobalPatchManager;
import game.shared.struct.script.ScriptVariable;
import game.sound.AudioEditor;
import game.sprite.SpritePatcher;
import game.sprite.SpriteLoader.SpriteSet;
import game.string.MessageBoxes;
import game.string.StringPatcher;
import game.string.font.FontManager;
import game.texture.CompressedImagePatcher;
import game.texture.images.ImageScriptModder;
import game.world.partner.PartnerWorldPatcher;
import game.worldmap.WorldMapModder;
import game.yay0.Yay0Helper;
import shared.Globals;
import util.CaseInsensitiveMap;
import util.Logger;
import util.Pair;
import util.Priority;

public class Patcher implements IGlobalDatabase
{
	private static final int OUT_BUFFER_SIZE_MB = 128; //XXX read this from config

	public static final int ROM_BASE = 0x02800000;
	public static final int RAM_BASE = 0x80400000;
	private List<Region> emptyRegions = new ArrayList<>();
	private LinkedHashMap<String, Timer> timerLookup;

	private RomPatcher rp = null;
	private int nextBattleDataPos = 0x4219F0;

	private HashMap<String, Integer> globalPointerMap; // globally accessible pointers $Name -> address
	private HashMap<String, String> globalConstantMap; // globally accessible constants .Name -> value
	private HashMap<String, Integer> stringIDMap;
	private HashMap<String, MapIndex> indexedMaps;

	private boolean addingCompileGlobals = false;
	private boolean addingUserGlobals = false;

	private SpritePatcher spritePatcher;

	public Patcher() throws IOException
	{
		ProjectDatabase.loadModGlobals();
		patchROM();
		ProjectDatabase.clearModGlobals();
	}

	private void patchROM() throws IOException
	{
		timerLookup = new LinkedHashMap<>();
		long startTime = System.nanoTime();
		Logger.log(new java.util.Date().toString(), Priority.IMPORTANT);
		Logger.log("Preparing patching process.", Priority.MILESTONE);

		// get build options from the mod config
		Config cfg = Environment.project.config;

		if (cfg.getBoolean(Options.ClearMapCache) && MapIndex.getFile().exists())
			FileUtils.forceDelete(MapIndex.getFile());

		if (cfg.getBoolean(Options.ClearSpriteCache)) {
			FileUtils.deleteDirectory(Directories.MOD_SPR_NPC_CACHE.toFile());
			FileUtils.deleteDirectory(Directories.MOD_SPR_PLR_CACHE.toFile());
		}

		if (cfg.getBoolean(Options.ClearTextureCache))
			FileUtils.deleteDirectory(Directories.MOD_IMG_CACHE.toFile());

		if (cfg.getBoolean(CaptureThumbnails)) {
			Logger.log("Capturing missing map thumbnails...", Priority.MILESTONE);
			MapEditor editor = new MapEditor(false);
			Collection<File> sourceFiles = IOUtils.getFilesWithExtension(MOD_MAP_SRC, Map.EXTENSION.substring(1), true);
			try {
				for (File f : sourceFiles) {
					File thumbFile = new File(MOD_MAP_THUMBNAIL + FilenameUtils.getBaseName(f.getName()) + ".jpg");
					if (thumbFile.exists())
						continue;
					Logger.log("Capturing thumbnail for " + FilenameUtils.removeExtension(f.getName()) + "...", Priority.MILESTONE);
					editor.generateThumbnail(f, thumbFile);
				}
				cfg.setBoolean(CaptureThumbnails, false);
				cfg.saveConfigFile();
			}
			finally {
				editor.shutdownThumbnail();
			}
		}

		// ======== Phase 1: build data files from patches and run pre-processor

		rp = Environment.project.getTargetRomPatcher(OUT_BUFFER_SIZE_MB * 1024 * 1024);
		SubscriptionManager.initialize();

		FileUtils.forceMkdir(MOD_MAP_TEMP.toFile());
		FileUtils.cleanDirectory(MOD_MAP_TEMP.toFile());

		FileUtils.forceMkdir(MOD_FORMA_TEMP.toFile());
		FileUtils.cleanDirectory(MOD_FORMA_TEMP.toFile());

		Logger.log("Building string data...", Priority.MILESTONE);
		StringPatcher stringPatcher = new StringPatcher();
		stringPatcher.readAllStrings();
		stringIDMap = stringPatcher.getStringIDMap();
		recordTime("Strings Built");

		// prepare tables for maps and battles

		MapPatcher mapPatcher = new MapPatcher(this);
		BattlePatcher battlePatcher = new BattlePatcher(this);
		AuxBattlePatcher auxPatcher = new AuxBattlePatcher(this);
		spritePatcher = new SpritePatcher(this);
		CompressedImagePatcher imgPatcher = new CompressedImagePatcher();
		PartnerWorldPatcher partnerPatcher = new PartnerWorldPatcher(this);
		Logger.log("Reading map config files...", Priority.MILESTONE);
		MapConfigTable mapTable = mapPatcher.readConfigs();
		recordTime("Map Configs Read");

		if (cfg.getBoolean(Options.AutoBuildMapAssets)) {
			Logger.log("Checking for missing assets...", Priority.MILESTONE);
			mapPatcher.buildMissing(mapTable);
		}

		Logger.log("Indexing map objects...", Priority.MILESTONE);
		indexMapObjects(cfg, mapTable);
		recordTime("Maps Indexed");

		Logger.log("Indexing animations...", Priority.MILESTONE);
		boolean allowDuplicateNames = cfg.getBoolean(Options.AllowDuplicateSpriteNames);
		spritePatcher.indexAnimations(allowDuplicateNames);
		recordTime("Animations Indexed");

		Logger.log("Writing map config table...", Priority.MILESTONE);
		int mapConfigTableBase = mapPatcher.writeConfigTable(mapTable);
		recordTime("Map Configs Patched");

		Logger.log("Reading battle config files...", Priority.MILESTONE);
		battlePatcher.readConfigs();
		recordTime("Battle Configs Read");

		// add table pointers to global pointers map
		globalConstantMap = new HashMap<>();
		globalPointerMap = new LinkedHashMap<>();
		addingCompileGlobals = true;

		setGlobalPointer(DefaultGlobals.MAP_CONFIG_TABLE, rp.toAddress(mapConfigTableBase));
		setGlobalPointer(DefaultGlobals.ITEM_TABLE, DefaultGlobals.ITEM_TABLE.getDefaultValue());
		setGlobalPointer(DefaultGlobals.MOVE_TABLE, DefaultGlobals.MOVE_TABLE.getDefaultValue());

		// apply direct ROM patches

		ActorTypesEditor.patch(this, rp);
		recordTime("Actor Types Patched");

		Logger.log("Reading direct ROM patches...", Priority.MILESTONE);
		GlobalPatchManager gpm = new GlobalPatchManager(this);

		FunctionPatcher.modifyHeaps(this, cfg, gpm, rp);
		gpm.readInternalPatch("ExtendedGlobals.patch",
			cfg.getBoolean(Options.EnableDebugCode) && cfg.getBoolean(Options.EnableVarLogging) ? "LogVars" : "");
		gpm.readInternalPatch("ExtendedScripts.patch");
		gpm.readInternalPatch("ExtraMoves.patch");
		gpm.readInternalPatch("MoreStringVars.patch");

		//TODO sort out the relationships between ProjectDatabase, GlobalsData, and ImageDatabase

		Logger.log("Patching globals...", Priority.MILESTONE);
		ProjectDatabase.loadGlobals(true);
		ImageScriptModder.readAll(ProjectDatabase.globalsData);
		ProjectDatabase.images.load(ProjectDatabase.globalsData.images);
		ProjectDatabase.images.loadAllImageTiles();
		ProjectDatabase.images.patchImages(rp);
		recordTime("Image Assets Patched");

		MoveModder.patchTable(rp, this, gpm, ProjectDatabase.globalsData, cfg.getBoolean(Options.Allow10Partners));
		ItemModder.patchTable(rp, this, ProjectDatabase.globalsData);

		Logger.log("Patching asset scripts...", Priority.MILESTONE);
		ProjectDatabase.globalsData.generateAutoGraphics();
		ImageScriptModder.parseAll(ProjectDatabase.globalsData);
		ImageScriptModder.patchAll(rp, this, ProjectDatabase.globalsData);
		gpm.readInternalPatch("HudElementJumping.patch");
		gpm.readInternalPatch("RelocateItemTables.patch");

		Logger.log("Patching world map...");
		WorldMapModder.patch(rp);

		recordTime("Item/Move Data Patched");

		if (cfg.getBoolean(Options.EnableDebugCode)) {
			CaseInsensitiveMap<String> debugRules = writeDebugSettings(cfg, rp);
			gpm.readInternalPatch("DebugInfo.patch", debugRules);
		}

		if (cfg.getBoolean(Options.QuickLaunch))
			gpm.readInternalPatch("QuickLaunch.patch");

		if (cfg.getBoolean(Options.SkipIntroLogos))
			gpm.readInternalPatch("SkipIntroLogos.patch");

		if (cfg.getBoolean(Options.DisableIntroStory))
			gpm.readInternalPatch("DisableIntroStory.patch");

		if (cfg.getBoolean(Options.DisableDemoReel))
			gpm.readInternalPatch("DisableDemoReel.patch");

		if (cfg.getBoolean(Options.Allow10Partners))
			partnerPatcher.allowExtraPartners(this, gpm);
		else
			partnerPatcher.patchPause(this);

		if (cfg.getBoolean(Options.PackScriptOpcodes)) {
			gpm.readInternalPatch("CompressedScripts.patch");
			Logger.log(String.format("Compressed scripts enabled."), Priority.IMPORTANT);
		}

		boolean optCompressBattleData = cfg.getBoolean(Options.CompressBattleData);
		if (optCompressBattleData) {
			gpm.readInternalPatch("CompressedBattleSections.patch");
			Logger.log(String.format("Compressed battle data enabled."), Priority.IMPORTANT);
		}

		if (cfg.getBoolean(Options.BuildSpriteSheets))
			spritePatcher.writeTables(gpm);

		setInitialMap(gpm, cfg, mapTable);

		addingCompileGlobals = false;
		addingUserGlobals = true;

		recordTime("Built-in Patches Read");

		gpm.encodeAndWrite(this);

		addingUserGlobals = false;

		Logger.log("Finished reading global pointers:");
		for (Entry<String, Integer> e : globalPointerMap.entrySet())
			Logger.logf("%08X = %s", e.getValue(), e.getKey());

		// patch data files

		Logger.log("Building battle data...", Priority.MILESTONE);
		battlePatcher.patchBattleData();
		auxPatcher.patchData();
		recordTime("Battle Data Built");

		Logger.log("Building world data...", Priority.MILESTONE);
		mapPatcher.patchMapData();

		partnerPatcher.patchData(this);
		recordTime("World Data Built");

		// preprocessing
		battlePatcher.updateConfigs();
		auxPatcher.generateConfigs();

		recordTime("Globals Built");

		// ======== Phase 3a: add the boot-only code

		Logger.log("Writing direct ROM patches...", Priority.MILESTONE);

		FunctionPatcher.addBootCode(rp);

		// ======== Phase 3b: append things that must load during boot

		// add things to load during boot
		gpm.addNewStructs();

		SubscriptionManager.writeHooks(rp);
		FunctionPatcher.showVersionInfo(rp, rp.nextAlignedOffset());
		recordTime("Global Patches Applied");

		Logger.log("Writing strings...", Priority.MILESTONE);
		stringPatcher.writeStrings(rp);

		Logger.log("Writing config tables...", Priority.MILESTONE);
		battlePatcher.writeBattleTable();
		auxPatcher.writeTables();

		SpriteShadingEditor.patchShading(rp);

		if (cfg.getBoolean(Options.PatchFonts)) {
			Logger.log("Injecting font...", Priority.MILESTONE);
			FontManager.patch(rp);
			MessageBoxes.patch(rp);
		}

		// add padding so hook boot works nicely
		rp.seek("Boot Padding", rp.getEndOffset());
		while (rp.getCurrentOffset() != rp.nextAlignedOffset())
			rp.writeByte(0);

		FunctionPatcher.hookBoot(this, cfg, rp, rp.nextAlignedOffset() - ROM_BASE);

		/*
		// ======== Phase 4: add things that will be loaded via DMA -- these can move around!

		// clear old map table - will help to root out hard-coded map table instructions
		//clearRegion(0x6B450, 0x6EAC0); // map and area config tables
		clearRegion(0x6B860, 0x6EAC0); // map and area config tables (with extended move table)
		clearRegion(0x73DA0, 0x73E10); // area SJIS strings
		clearRegion(0x73E2C, 0x74EA0); // map and area name strings

		clearRegion(0x65A80, 0x66508);
		clearRegion(0x66508, 0x691D4);
		clearRegion(0x691D4, 0x697D8);
		clearRegion(0x5B8F0, 0x62CE0);

		 */

		imgPatcher.patchCompressedImages();

		if (cfg.getBoolean(Options.BuildTextures)) {
			Logger.log("Building texture archives...", Priority.MILESTONE);
			imgPatcher.buildTextureArchives();
		}

		if (cfg.getBoolean(Options.BuildBackgrounds)) {
			Logger.log("Building backgrounds...", Priority.MILESTONE);
			imgPatcher.buildBackgrounds();
		}

		Logger.log("Writing map assets...", Priority.MILESTONE);
		mapPatcher.writeAssetTable(mapTable);

		mapPatcher.writeMapData(mapTable);
		mapPatcher.updateConfigTable(mapTable);

		Logger.log("Writing battle data...", Priority.MILESTONE);
		battlePatcher.writeBattleData(optCompressBattleData);
		auxPatcher.writeData();
		sealBattleData();

		Logger.log("Writing partner data...", Priority.MILESTONE);
		partnerPatcher.writeData(this);

		if (cfg.getBoolean(Options.BuildSpriteSheets)) {
			Logger.log("Patching sprite sheets...", Priority.MILESTONE);
			spritePatcher.patchSpriteSheets();
			recordTime("Sprite Sheets Patched");
		}

		if (cfg.getBoolean(Options.BuildAudio)) {
			Logger.log("Writing audio data...", Priority.MILESTONE);
			AudioEditor.patchAudio(this, rp);
			recordTime("Audio Patched");
		}

		// ======== Phase 5: finishing touches

		Logger.log("Calculating new CRC values...", Priority.MILESTONE);
		recalculateCRCs(rp.getBuffer());

		System.out.println("------------- EMPTY --------------");
		for (Region r : emptyRegions) {
			Logger.logf("Empty region from %08X to %08X (%X bytes)", r.start, r.end, r.length());
			//	raf.seek(r.start);
			//	raf.write(new byte[r.length()]);
		}

		System.out.println("------------- PATCHES ------------");
		rp.print();

		System.out.println("------------- TIMING -------------");
		printTimes();

		rp.writeFile();

		cfg.setString(Options.CompileVersion, Environment.getVersionString()); // another successful compile. great job!
		cfg.setBoolean(Options.ClearMapCache, false);
		cfg.setBoolean(Options.ClearSpriteCache, false);
		cfg.setBoolean(Options.ClearTextureCache, false);
		cfg.saveConfigFile();

		Logger.log("Done editing " + Environment.project.getTargetRom(), Priority.MILESTONE);
		long endTime = System.nanoTime();
		Logger.log(String.format("Mod compilation took %.2f seconds", (endTime - startTime) / 1e9), Priority.IMPORTANT);
		Logger.log(new java.util.Date().toString(), Priority.IMPORTANT);
	}

	private void setInitialMap(GlobalPatchManager gpm, Config cfg, MapConfigTable mapTable) throws IOException
	{
		String initialMap = cfg.getString(Options.InitialMap);
		String initialEntry = cfg.getString(Options.InitialEntry);
		Pair<Integer> ids = mapTable.getAreaMapIDs(initialMap);
		if (ids == null)
			throw new StarRodException("Could not find initial map: %s", initialMap);

		int areaID = ids.first;
		int mapID = ids.second;

		MapIndex ndx = getMapIndex(initialMap);
		int entryID = ndx.getEntry(initialEntry);
		if (entryID < 0)
			throw new StarRodException("Could not find entry \"%s\" for map: %s", initialEntry, initialMap);

		Logger.logf("Setting initial map to %s, %s --> %X-%X (%X)", initialMap, initialEntry, areaID, mapID, entryID);
		gpm.readInternalPatch("SetInitialMap.patch",
			String.format("%s=%04X", "AreaID", areaID),
			String.format("%s=%04X", "MapID", mapID),
			String.format("%s=%04X", "EntryID", entryID));
	}

	private static final Options[] debugWatchList = new Options[] {
			Options.DebugWatch0, Options.DebugWatch1,
			Options.DebugWatch2, Options.DebugWatch3,
			Options.DebugWatch4, Options.DebugWatch5,
			Options.DebugWatch6, Options.DebugWatch7 };

	private CaseInsensitiveMap<String> writeDebugSettings(Config cfg, RomPatcher rp) throws IOException
	{
		rp.seek("Debug Map Name", rp.nextAlignedOffset());
		int ptrMapName = Patcher.toAddress(rp.getCurrentOffset());
		String mapName = cfg.getString(Options.DebugMapName);
		rp.write(mapName.getBytes());
		rp.writeByte(0);
		while ((rp.getCurrentOffset() & 3) != 0)
			rp.writeByte(0); // pad to 4 byte boundary

		int ptrBattleID = Patcher.toAddress(rp.getCurrentOffset());
		String battleID = cfg.getString(Options.DebugBattleID);
		if (battleID.length() > 4 || !DataUtils.isInteger(battleID))
			battleID = Options.DebugBattleID.defaultValue;
		rp.writeShort(Integer.parseInt(battleID, 16));
		rp.writeShort(0xFFFF);

		ByteBuffer watchListBuffer = ByteBuffer.allocateDirect(4 + 0x18 * debugWatchList.length);
		int watchListPos = 4;
		int numWatchEntries = 0;

		for (Options opt : debugWatchList) {
			String optValue = cfg.getString(opt);
			if (optValue != null) {
				watchListBuffer.position(watchListPos);
				WatchListEntry entry = new WatchListEntry(cfg, opt);
				entry.put(watchListBuffer);
				watchListPos += 0x18;
				numWatchEntries++;
			}
		}
		watchListBuffer.limit(watchListPos);

		watchListBuffer.position(0);
		watchListBuffer.putInt(numWatchEntries);

		CaseInsensitiveMap<String> rules = new CaseInsensitiveMap<>();

		rp.seek("Debug Watch List", rp.nextAlignedOffset());
		int ptrWatchList = Patcher.toAddress(rp.getCurrentOffset());
		rp.write(watchListBuffer);

		rules.put("DebugMapPointer", String.format("%08X", ptrMapName));
		rules.put("DebugBattlePointer", String.format("%08X", ptrBattleID));
		rules.put("WatchListPointer", String.format("%08X", ptrWatchList));

		// story progress names

		ArrayList<String> names = new ArrayList<>();
		for (EnumPair pair : ProjectDatabase.StoryType.getDecoding()) {
			int id = pair.key;
			while (names.size() < (id + 0x80))
				names.add("Unused");
			names.add(pair.value);
		}

		int[] namePointers = new int[256];
		rp.seek("StoryProgressNames", rp.nextAlignedOffset());

		int storyIndex = 0;
		for (; storyIndex < names.size(); storyIndex++) {
			namePointers[storyIndex] = rp.toAddress(rp.getCurrentOffset());
			rp.writeMessage(names.get(storyIndex));
		}
		int unusedIndex = rp.toAddress(rp.getCurrentOffset());
		rp.writeMessage("Unused");
		for (; storyIndex < namePointers.length; storyIndex++)
			namePointers[storyIndex] = unusedIndex;

		rp.seek("StoryProgressTable", rp.nextAlignedOffset());
		rules.put("StoryProgressNames", String.format("%08X", rp.toAddress(rp.getCurrentOffset())));
		for (int i : namePointers)
			rp.writeInt(i);

		// var logging

		if (cfg.getBoolean(Options.EnableVarLogging)) {
			rules.put("LogVars", "True");

			rp.seek("GameByteNames", rp.nextAlignedOffset());
			rules.put("GameByteNames", String.format("%08X", Patcher.toAddress(rp.getCurrentOffset())));
			for (int i = 0; i < ScriptVariable.GameByte.getMaxIndex(); i++)
				writeVarInfo(rp, i, ProjectDatabase.getGameByte(i), ProjectDatabase.isGameByteUnused(i));
			rp.writeInt(-1);

			rp.seek("ModByteNames", rp.nextAlignedOffset());
			rules.put("ModByteNames", String.format("%08X", Patcher.toAddress(rp.getCurrentOffset())));
			for (int i = 0; i < ScriptVariable.ModByte.getMaxIndex(); i++)
				writeVarInfo(rp, i, ProjectDatabase.getModByte(i), false);
			rp.writeInt(-1);

			rp.seek("GameFlagNames", rp.nextAlignedOffset());
			rules.put("GameFlagNames", String.format("%08X", Patcher.toAddress(rp.getCurrentOffset())));
			for (int i = 0; i < ScriptVariable.GameFlag.getMaxIndex(); i++)
				writeVarInfo(rp, i, ProjectDatabase.getGameFlag(i), ProjectDatabase.isGameFlagUnused(i));
			rp.writeInt(-1);

			rp.seek("ModFlagNames", rp.nextAlignedOffset());
			rules.put("ModFlagNames", String.format("%08X", Patcher.toAddress(rp.getCurrentOffset())));
			for (int i = 0; i < ScriptVariable.ModFlag.getMaxIndex(); i++)
				writeVarInfo(rp, i, ProjectDatabase.getModFlag(i), false);
			rp.writeInt(-1);
		}

		return rules;
	}

	private static void writeVarInfo(RomPatcher rp, int index, String name, boolean unused)
	{
		if (name == null || name.length() < 2)
			return;
		name = name.substring(1); // remove * prefix

		// the in-game debug format/print buffer is 0x40 = 64 bytes
		// leave 4 bytes for formatting, 1 for terminator char, and 1 extra for safety
		if (name.length() > 58)
			name.substring(0, 58);

		// always have one extra byte for \0 and pad to 4 bytes
		int len = (name.length() + 4) & -4;
		rp.writeShort(index);
		rp.writeByte(4 + len);
		rp.writeByte(unused ? 1 : 0);
		rp.write(name.getBytes());
		int padding = len - name.length();
		if (padding > 0)
			rp.write(new byte[padding]);
	}

	@Override
	public void setGlobalPointer(DefaultGlobals global, int addr)
	{
		if (!addingCompileGlobals)
			throw new IllegalStateException("Cannot add compiled global pointers!");

		globalPointerMap.put(global.toString(), addr);
	}

	@Override
	public void setGlobalPointer(String name, int addr)
	{
		//	think this is okay to relax this restriction
		//	if(!addingUserGlobals)
		//		throw new IllegalStateException("Cannot add global pointers after direct ROM patches are added.");

		globalPointerMap.put(name, addr);
	}

	@Override
	public boolean hasGlobalPointer(String name)
	{
		return globalPointerMap.containsKey(name);
	}

	@Override
	public int getGlobalPointerAddress(String name)
	{
		return globalPointerMap.get(name);
	}

	@Override
	public void setGlobalConstant(DefaultGlobals global, String value)
	{
		if (!addingCompileGlobals)
			throw new IllegalStateException("Cannot add compiled global constants!");

		globalConstantMap.put(global.toString(), value);
	}

	@Override
	public void setGlobalConstant(String name, String value)
	{
		if (!addingUserGlobals)
			throw new IllegalStateException("Cannot add global constants after direct ROM patches are added.");

		globalConstantMap.put(name, value);
	}

	@Override
	public boolean hasGlobalConstant(String name)
	{
		return globalConstantMap.containsKey(name);
	}

	@Override
	public String getGlobalConstant(String name)
	{
		return globalConstantMap.get(name);
	}

	public static final Pattern PatternMessageID = Pattern.compile("([0-9A-F]+)-([0-9A-F]+)");
	public static final Matcher MatcherMessageID = PatternMessageID.matcher("");

	@Override
	public int resolveStringID(String s) throws InvalidInputException
	{
		if (s == null)
			return 0;

		Integer id = stringIDMap.get(s);
		if (id != null)
			return id;

		MatcherMessageID.reset(s);
		if (MatcherMessageID.matches()) {
			int hi = Integer.parseInt(MatcherMessageID.group(1), 16);
			int lo = Integer.parseInt(MatcherMessageID.group(2), 16);
			return (hi << 16 | lo);
		}

		return DataUtils.parseIntString(s);
	}

	@Override
	public boolean hasStringName(String name)
	{
		return stringIDMap.containsKey(name);
	}

	@Override
	public int getStringFromName(String name)
	{
		return stringIDMap.get(name);
	}

	/**
	 * Patches the two CRC values used by the N64 boot chip to verify the integrity of
	 * the ROM (0x10 and 0x14). Paper Mario uses the CIC-NUS-6103 boot chip, so we must
	 * use the corresponding algorithm to calculate the new CRCs. Reproducing the correct
	 * unsigned integer arithmetic is tricky and leads to this ugly, nigh-unreadable code.
	 * But it works.
	 * @throws IOException
	 */
	private void recalculateCRCs(ByteBuffer bb) throws IOException
	{
		long t1, t2, t3;
		long t4, t5, t6;
		t1 = t2 = t3 = t4 = t5 = t6 = 0xA3886759; // 6103 only

		long r, d;

		bb.position(0x1000);
		for (int i = 0x1000; i < 0x101000; i += 4) {
			d = bb.getInt() & 0xFFFFFFFFL;
			if (((t6 + d) & 0xFFFFFFFFL) < (t6 & 0xFFFFFFFFL))
				t4++;
			t6 += d;
			t3 ^= d;

			r = ((d << (d & 0x1F)) | (d >> (32L - (d & 0x1F)))) & 0xFFFFFFFFL;

			t5 += r;
			if ((t2 & 0xFFFFFFFFL) > (d & 0xFFFFFFFFL))
				t2 ^= r;
			else
				t2 ^= t6 ^ d;

			t1 += t5 ^ d;
		}

		int crc1 = (int) ((t6 ^ t4) + t3);
		int crc2 = (int) ((t5 ^ t2) + t1);

		bb.position(0x10);
		bb.putInt(crc1);
		bb.putInt(crc2);
		Logger.log(String.format("Wrote new CRCs to ROM (%08X %08X)", crc1, crc2), Priority.IMPORTANT);
	}

	public static void packageMod(File rom) throws IOException
	{
		LinkedList<Integer> diffStarts = new LinkedList<>();
		LinkedList<Integer> diffLengths = new LinkedList<>();

		Config cfg = Environment.project.config;

		String modName = cfg.getString(Options.ModVersionString);
		if (modName.isEmpty())
			modName = Options.ModVersionString.defaultValue;

		byte[] base = Environment.getBaseRomBytes();
		byte[] patched = FileUtils.readFileToByteArray(rom);

		/*
		Delta delta = new Delta();
		byte[] diff = delta.compute(base, patched);
		File outXDelta = new File(MOD_OUT + modName + ".xdelta");
		FileUtils.writeByteArrayToFile(outXDelta, diff);
		Logger.log("Wrote XDELTA file to " + outXDelta, Priority.IMPORTANT);
		 */

		if (patched.length < base.length)
			throw new RuntimeException("Patched ROM should not be smaller than base ROM!");

		Logger.log("Starting mod packaging: " + new java.util.Date().toString(), Priority.IMPORTANT);

		boolean mismatching = false;
		int mismatchStart = -1;
		int lastEnd = Integer.MIN_VALUE;

		Logger.log("Finding differences between base ROM and patched ROM...", Priority.MILESTONE);

		for (int pos = 0; pos < base.length; pos++) {
			if (base[pos] != patched[pos]) {
				if (!mismatching) {
					if (pos < lastEnd + 8) {
						diffLengths.removeLast();
						mismatchStart = diffStarts.removeLast();
					}
					else {
						mismatchStart = pos;
					}

					mismatching = true;
				}
			}
			else {
				if (mismatching) {
					diffStarts.add(mismatchStart);
					diffLengths.add(pos - mismatchStart);
					lastEnd = pos;
					mismatching = false;
				}
			}
		}

		if (patched.length > base.length) {
			if (mismatching) {
				diffStarts.add(mismatchStart);
				diffLengths.add(patched.length - mismatchStart);
			}
			else {
				diffStarts.add(base.length);
				diffLengths.add(patched.length - base.length);
			}
		}

		Logger.log("Found " + diffStarts.size() + " different byte sequences.", Priority.MILESTONE);

		int totalSize = 8 + 8 * diffStarts.size();
		for (int len : diffLengths)
			totalSize += len;

		Logger.log("Copying differences to diff file...", Priority.MILESTONE);

		ByteBuffer buf = ByteBuffer.allocate(totalSize);
		buf.putInt(Globals.MOD_HEADER_IDENTIFIER);
		buf.putInt(diffStarts.size());

		for (int i = 0; i < diffStarts.size(); i++) {
			buf.putInt(diffStarts.get(i));
			buf.putInt(diffLengths.get(i));
			buf.put(patched, diffStarts.get(i), diffLengths.get(i));
		}

		byte[] diffBytes = buf.array();

		if (cfg.getBoolean(Options.CompressModPackage)) {
			Logger.log("Compressing diff file...", Priority.MILESTONE);
			diffBytes = Yay0Helper.encode(diffBytes, true);
			Logger.logf("Compressed %08X -> %08X (%04.2f%%)",
				totalSize,
				diffBytes.length,
				100 * (float) diffBytes.length / totalSize);
		}

		File outMod = new File(MOD_OUT + modName + ".mod");
		FileUtils.writeByteArrayToFile(outMod, diffBytes);
		Logger.log("Wrote MOD file to " + outMod, Priority.IMPORTANT);
		Logger.log("Mod package complete. " + new java.util.Date().toString(), Priority.IMPORTANT);
	}

	/*
	public void clearRegion(int start, int end) throws IOException
	{
		raf.seek(start);
		raf.write(new byte[end - start]);
		emptyRegions.add(new Region(start, end));
	}
	 */

	public void addEmptyRegion(Region r)
	{
		//TODO
		emptyRegions.add(r);
	}

	@Override
	public RomPatcher getRomPatcher()
	{
		return rp;
	}

	public int getBattleDataPos(int sizeNeeded)
	{
		if (nextBattleDataPos == -1)
			throw new IllegalStateException();

		int offset = nextBattleDataPos;
		if (offset + sizeNeeded > 0x79EF40) {
			addEmptyRegion(new Region(offset, 0x79EF40));
			offset = rp.nextAlignedOffset();
		}
		nextBattleDataPos = offset + sizeNeeded;

		return offset;
	}

	private void sealBattleData()
	{
		if (nextBattleDataPos < 0x79EF40) {
			addEmptyRegion(new Region(nextBattleDataPos, 0x79EF40));
		}
		nextBattleDataPos = -1;
	}

	/**
	 * use method from RomPatcher instead
	 */
	@Deprecated
	public static int toAddress(int offset)
	{
		return RAM_BASE + (offset - ROM_BASE);
	}

	public static int toAddress(long filePointer)
	{
		return toAddress((int) filePointer);
	}

	private static final class Timer
	{
		public final String name;
		public final long time;

		public Timer(String name)
		{
			this.name = name;
			this.time = System.nanoTime();
		}
	}

	public void recordTime(String name)
	{
		if (timerLookup.containsKey(name))
			throw new IllegalStateException();

		timerLookup.put(name, new Timer(name));
	}

	private void printTimes()
	{
		ArrayList<Timer> timers = new ArrayList<>(timerLookup.values());
		timers.add(new Timer("Done"));

		for (int i = 0; i < timers.size() - 1; i++) {
			Timer cur = timers.get(i);
			Timer next = timers.get(i + 1);
			System.out.printf("%11.3f ms   %s%n", 1e-6 * (next.time - cur.time), cur.name);
		}
	}

	private void indexMapObjects(Config cfg, MapConfigTable mapTable) throws IOException
	{
		indexedMaps = new LinkedHashMap<>();
		HashMap<String, MapIndex> cache = loadMapCache();
		buildMapIndex(cache, mapTable);

		for (AreaConfig area : mapTable.areas) {
			for (MapConfig mapCfg : area.maps) {
				MapIndex index = indexedMaps.get(mapCfg.name);
				if (index == null)
					throw new StarRodException("Map " + mapCfg.name + " was not found in cache!");

				mapCfg.bgName = index.getBackgroundName();
			}

			for (MapConfig mapCfg : area.stages) {
				MapIndex index = indexedMaps.get(mapCfg.name);
				if (index == null)
					throw new StarRodException("Stage " + mapCfg.name + " was not found in cache!");

				mapCfg.bgName = index.getBackgroundName();
			}
		}

		Logger.log("Loaded map index.");
	}

	private HashMap<String, MapIndex> loadMapCache()
	{
		HashMap<String, MapIndex> cache = new HashMap<>();

		File indexFile = MapIndex.getFile();
		if (!indexFile.exists())
			return cache;

		try (ObjectInputStream in = new ObjectInputStream(
			new BufferedInputStream(new FileInputStream(indexFile)))) {
			String versionString = in.readUTF();
			if (!versionString.equals(Environment.getVersionString()))
				return cache;

			int numMaps = in.readInt();

			for (int i = 0; i < numMaps; i++) {
				MapIndex index = (MapIndex) in.readObject();
				String name = index.getMapName();
				if (!name.isEmpty())
					cache.put(name, index);
			}

		}
		catch (IOException | ClassNotFoundException e) {
			Logger.printStackTrace(e);
			return cache;
		}
		return cache;
	}

	private void buildMapIndex(HashMap<String, MapIndex> cache, MapConfigTable mapTable) throws IOException
	{
		for (AreaConfig area : mapTable.areas) {
			for (MapConfig mapCfg : area.maps)
				indexMap(cache, mapCfg);

			for (MapConfig mapCfg : area.stages)
				indexMap(cache, mapCfg);
		}

		try (ObjectOutputStream out = new ObjectOutputStream(
			new BufferedOutputStream(new FileOutputStream(MapIndex.getFile())))) {
			Collection<MapIndex> indices = indexedMaps.values();
			out.writeUTF(Environment.getVersionString());
			out.writeInt(indices.size());
			for (MapIndex index : indices)
				out.writeObject(index);
		}
	}

	private void indexMap(HashMap<String, MapIndex> cache, MapConfig cfg)
	{
		File[] matches = IOUtils.getFileWithin(MOD_MAP_SAVE, cfg.name + Map.EXTENSION, true);
		if (matches.length > 1)
			throw new StarRodException("Found multiple files named " + cfg.name + Map.EXTENSION + " in " + MOD_MAP_SAVE);

		File mapFile;
		if (matches.length == 0)
			mapFile = new File(MOD_MAP_SRC + cfg.name + Map.EXTENSION);
		else
			mapFile = matches[0];

		if (!mapFile.exists())
			return;

		MapIndex cached = cache.get(cfg.name);
		if (cached != null && cached.sourceLastModified() == mapFile.lastModified()) {
			indexedMaps.put(cfg.name, cached);
			return;
		}

		Map map = Map.loadMap(mapFile);

		if (indexedMaps.containsKey(cfg.name)) {
			Logger.logWarning("Map index already contains entry for " + cfg.name + "! Ignoring duplicate entry.");
			return;
		}

		indexedMaps.put(cfg.name, new MapIndex(map));
	}

	@Override
	public boolean hasMapIndex(String name)
	{
		return indexedMaps.containsKey(name);
	}

	@Override
	public MapIndex getMapIndex(String name)
	{
		return indexedMaps.get(name);
	}

	@Override
	public int getNpcAnimID(String spriteName, String animName, String palName)
	{
		return spritePatcher.getAnimationID(SpriteSet.Npc, spriteName, animName, palName);
	}

	@Override
	public int getPlayerAnimID(String spriteName, String animName, String palName)
	{
		return spritePatcher.getAnimationID(SpriteSet.Npc, spriteName, animName, palName);
	}
}
