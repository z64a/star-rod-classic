package game.sprite;

import static app.Directories.*;
import static game.texture.TileFormat.CI_4;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.TreeMap;

import org.apache.commons.io.FileUtils;

import app.Directories;
import app.StarRodException;
import app.input.IOUtils;
import app.input.InvalidInputException;
import game.shared.encoder.GlobalPatchManager;
import game.sprite.PlayerSpriteConverter.RasterTableEntry;
import game.sprite.SpriteLoader.SpriteMetadata;
import game.sprite.SpriteLoader.SpriteSet;
import game.sprite.Yay0Cache.CacheResult;
import game.texture.Tile;
import patcher.Patcher;
import patcher.RomPatcher;
import util.KeyValuePair;
import util.Logger;
import util.Priority;

public class SpritePatcher
{
	private static final String ANIM_SEPARATOR = "#";
	private static final String PAL_SEPARATOR = ":";

	private static final int SPRITE_TABLE_BASE = 0x1943010;
	private static final int SPRITE_DATA_LIMIT = 0x1B82208;

	private final Patcher patcher;
	private final RomPatcher rp;
	private final SpriteLoader spriteLoader;

	private final TreeMap<String, Integer> npcSpriteNameMap;
	private final TreeMap<String, Integer> playerSpriteNameMap;

	private final HashMap<String, RasterTableEntry> rasterEntryLookup;
	private final List<Sprite> playerSprites;
	private int specialRasterOffset;
	private int numPlayerSprites = 0;
	private int numPlayerSpriteRasters = 0;

	private int romptrPlayerRasters;
	private int romptrPlayerYay0;
	private int romptrNpcYay0;

	public SpritePatcher(Patcher patcher) throws IOException
	{
		this.patcher = patcher;
		rp = patcher.getRomPatcher();

		FileUtils.forceMkdir(MOD_SPR_PLR_TEMP.toFile());
		FileUtils.forceMkdir(MOD_SPR_NPC_TEMP.toFile());

		spriteLoader = new SpriteLoader();

		npcSpriteNameMap = new TreeMap<>();
		playerSpriteNameMap = new TreeMap<>();

		rasterEntryLookup = new HashMap<>();
		playerSprites = new ArrayList<>();
	}

	public void indexAnimations(boolean allowDuplicateNames) throws IOException
	{
		try {
			indexAnimations(SpriteSet.Npc, allowDuplicateNames);
			indexAnimations(SpriteSet.Player, allowDuplicateNames);
		}
		catch (InvalidInputException e) {
			throw new StarRodException(e);
		}
	}

	private static class SpriteIndex
	{
		private long lastModified;
		private String name;
		private List<KeyValuePair<String, Integer>> anims;
		private List<KeyValuePair<String, Integer>> palettes;
	}

	private void indexAnimations(SpriteSet set, boolean allowDuplicateNames) throws InvalidInputException, IOException
	{
		Collection<SpriteMetadata> sprites = SpriteLoader.getValidSprites(set);

		File cacheFile;
		TreeMap<String, Integer> spriteNameMap;
		String setName;

		switch (set) {
			case Npc:
				setName = "NPC";
				spriteNameMap = npcSpriteNameMap;
				cacheFile = new File(Directories.MOD_SPR_NPC_CACHE + "cache.bin");
				break;
			case Player:
				setName = "Player";
				spriteNameMap = playerSpriteNameMap;
				cacheFile = new File(Directories.MOD_SPR_PLR_CACHE + "cache.bin");
				break;
			default:
				throw new InvalidInputException("Invalid sprite set: %s");
		}

		HashMap<String, SpriteIndex> cache = readCache(cacheFile);
		HashMap<String, SpriteIndex> newCache = new HashMap<>();

		for (SpriteMetadata data : sprites) {
			String dataName = data.name.replaceAll("\\s+", "");
			SpriteIndex index = cache.get(dataName);

			// valid cache entry, use values from cache instead of loading sprite
			if (index != null && index.lastModified == data.lastModified()) {
				addSpriteName(spriteNameMap, setName, index.name, data.id);

				for (KeyValuePair<String, Integer> e : index.anims)
					addAnimName(spriteNameMap, setName, data.name, e.key, e.value, allowDuplicateNames);

				for (KeyValuePair<String, Integer> e : index.palettes)
					addPaletteName(spriteNameMap, setName, data.name, e.key, e.value, allowDuplicateNames);

				newCache.put(data.name, index);
				continue;
			}

			Sprite sprite = spriteLoader.getSprite(set, data.id, true);
			sprite.recalculateIndices();

			String spriteName = sprite.toString().replaceAll("\\s+", "");
			addSpriteName(spriteNameMap, setName, spriteName, data.id);

			index = new SpriteIndex();
			index.lastModified = data.lastModified();
			index.name = spriteName;
			newCache.put(index.name, index);

			index.anims = new ArrayList<>(sprite.animations.size());
			for (int i = 0; i < sprite.animations.size(); i++) {
				SpriteAnimation anim = sprite.animations.get(i);
				String animName = anim.toString().replaceAll("\\s+", "");

				if (addAnimName(spriteNameMap, setName, spriteName, animName, i, allowDuplicateNames))
					index.anims.add(new KeyValuePair<>(animName, i));
			}

			index.palettes = new ArrayList<>(sprite.palettes.size());
			for (int i = 0; i < sprite.palettes.size(); i++) {
				SpritePalette pal = sprite.palettes.get(i);
				String palName = pal.toString().replaceAll("\\s+", "");

				if (addPaletteName(spriteNameMap, setName, spriteName, palName, i, allowDuplicateNames))
					index.palettes.add(new KeyValuePair<>(palName, i));
			}
		}

		writeCache(cacheFile, newCache);
	}

	private void addSpriteName(TreeMap<String, Integer> spriteNameMap, String setName, String spriteName, int id) throws InvalidInputException
	{
		if (spriteNameMap.containsKey(spriteName))
			throw new InvalidInputException("%s sprite $X: Name \"%s\" is not unique!", setName, id, spriteName);
		if (!spriteName.matches("[\\w\\-]+"))
			throw new InvalidInputException("%s sprite $X: Name contains illegal characters: \"%s\"", setName, id, spriteName);

		spriteNameMap.put(spriteName, id);
	}

	private boolean addAnimName(TreeMap<String, Integer> spriteNameMap, String setName,
		String spriteName, String animName, int id, boolean allowDuplicateNames) throws InvalidInputException
	{
		String key = spriteName + ANIM_SEPARATOR + animName;

		if (spriteNameMap.containsKey(key)) {
			String warning = "Anim name \'" + animName + "\' for " + setName + " sprite \'" + spriteName + "\' is not unique!";
			if (allowDuplicateNames) {
				Logger.logWarning(warning);
				return false;
			}
			throw new InvalidInputException(warning);
		}
		if (!animName.matches("[\\w\\-]+"))
			throw new IllegalStateException(
				"Anim name \'" + animName + "\' for " + setName + " sprite \'" + spriteName + "\' contains illegal characters!");

		spriteNameMap.put(key, id);
		return true;
	}

	private boolean addPaletteName(TreeMap<String, Integer> spriteNameMap, String setName,
		String spriteName, String palName, int id, boolean allowDuplicateNames) throws InvalidInputException
	{
		String key = spriteName + PAL_SEPARATOR + palName;

		if (spriteNameMap.containsKey(key)) {
			String warning = "Palette name \'" + palName + "\' for " + setName + " sprite \'" + spriteName + "\' is not unique!";
			if (allowDuplicateNames) {
				Logger.logWarning(warning);
				return false;
			}
			throw new InvalidInputException(warning);
		}
		if (!palName.matches("[\\w\\-]+"))
			throw new InvalidInputException(
				"Palette name \'" + palName + "\' for " + setName + " sprite \'" + spriteName + "\' contains illegal characters!");

		spriteNameMap.put(key, id);
		return true;
	}

	private HashMap<String, SpriteIndex> readCache(File in) throws IOException
	{
		HashMap<String, SpriteIndex> cache = new HashMap<>();
		if (!in.exists())
			return cache;

		try (DataInputStream stream = new DataInputStream(new BufferedInputStream(new FileInputStream(in)))) {
			int count = stream.readInt();
			for (int i = 0; i < count; i++) {
				SpriteIndex index = new SpriteIndex();
				index.lastModified = stream.readLong();
				int numAnims = stream.readInt();
				int numPalettes = stream.readInt();
				index.name = stream.readUTF();
				cache.put(index.name, index);

				index.anims = new ArrayList<>(numAnims);
				for (int j = 0; j < numAnims; j++) {
					String key = stream.readUTF();
					int value = stream.readInt();
					index.anims.add(new KeyValuePair<>(key, value));
				}

				index.palettes = new ArrayList<>(numPalettes);
				for (int j = 0; j < numPalettes; j++) {
					String key = stream.readUTF();
					int value = stream.readInt();
					index.palettes.add(new KeyValuePair<>(key, value));
				}
			}
		}

		return cache;
	}

	private void writeCache(File out, HashMap<String, SpriteIndex> cache) throws IOException
	{
		FileUtils.touch(out);
		try (DataOutputStream stream = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(out, false)))) {
			stream.writeInt(cache.size());

			for (Entry<String, SpriteIndex> e : cache.entrySet()) {
				SpriteIndex index = e.getValue();
				stream.writeLong(index.lastModified);
				stream.writeInt(index.anims.size());
				stream.writeInt(index.palettes.size());
				stream.writeUTF(index.name);

				for (KeyValuePair<String, Integer> pair : index.anims) {
					stream.writeUTF(pair.key);
					stream.writeInt(pair.value);
				}

				for (KeyValuePair<String, Integer> pair : index.palettes) {
					stream.writeUTF(pair.key);
					stream.writeInt(pair.value);
				}
			}
		}
	}

	public int getAnimationID(SpriteSet set, String spriteName, String animName, String palette)
	{
		TreeMap<String, Integer> spriteNameMap;
		switch (set) {
			case Npc:
				spriteNameMap = npcSpriteNameMap;
				break;
			case Player:
				spriteNameMap = playerSpriteNameMap;
				break;
			default:
				throw new IllegalArgumentException("Invalid sprite set: " + set);
		}

		Integer spriteID = spriteNameMap.get(spriteName);
		Integer animID = spriteNameMap.get(spriteName + ANIM_SEPARATOR + animName);

		if (spriteID == null)
			return -1;

		if (animID == null)
			return -2;

		if (palette.isEmpty())
			return spriteID << 16 | animID;

		Integer palID = spriteNameMap.get(spriteName + PAL_SEPARATOR + animName);

		if (palID == null)
			return -3;

		return spriteID << 16 | palID << 8 | animID;
	}

	public void patchSpriteSheets() throws IOException
	{
		loadPlayerSprites();

		rp.seek("Sprite Table", SPRITE_TABLE_BASE + 0x10);
		romptrPlayerRasters = rp.getCurrentOffset();
		writePlayerRasters();

		romptrPlayerYay0 = rp.getCurrentOffset();
		writePlayerYay0();

		romptrNpcYay0 = rp.getCurrentOffset();
		writeNpcYay0();

		// header
		rp.seek("Sprite Table", SPRITE_TABLE_BASE);
		rp.writeInt(romptrPlayerRasters - SPRITE_TABLE_BASE);
		rp.writeInt(romptrPlayerYay0 - SPRITE_TABLE_BASE);
		rp.writeInt(romptrNpcYay0 - SPRITE_TABLE_BASE);
		rp.writeInt(0x0023F1F8);

		byte[] copy = new byte[0x40000 - 0x10];
		rp.seek("Sprite Table", SPRITE_TABLE_BASE);
		rp.read(copy);
		rp.seek("Sprite Table Copy", 0x1E00000 + 0x10);
		rp.write(copy); //XXX BRX 802DCFF0
	}

	// read all the player sprites, build their yay0 files
	// in the process, build a list of required sprites
	// write the raster table
	// write the player sprites

	private void loadPlayerSprites() throws IOException
	{
		Logger.log(String.format("Loading player sprites..."), Priority.MILESTONE);
		int highestID = SpriteLoader.getMaximumID(SpriteSet.Player);

		// load player sprites, get lists of the rasters
		for (int i = 1; i <= highestID; i++) {
			Sprite spr = spriteLoader.getSprite(SpriteSet.Player, i);
			playerSprites.add(spr);
			numPlayerSprites++;
			numPlayerSpriteRasters += spr.rasters.size();
		}
	}

	private void writePlayerRasters() throws IOException
	{
		int sizeSections = 4 * (numPlayerSprites + 1);
		int sizeRasterList = 4 * numPlayerSpriteRasters;

		// write player sprites header
		int position = 0x10;
		rp.writeInt(position);
		position += sizeSections;
		rp.writeInt(position);
		position += sizeRasterList;
		int offsetRasters = (position + 15) & -16; //pad to 0x10;
		rp.writeInt(offsetRasters);
		rp.writeInt(0);

		// write sections list
		int count = 0;
		rp.writeInt(count);
		for (Sprite spr : playerSprites) {
			count += spr.rasters.getSize();
			rp.writeInt(count);
		}

		// come back for this later
		int romptrRasterList = rp.getCurrentOffset();
		rp.seek("Player Rasters", SPRITE_TABLE_BASE + 0x10 + offsetRasters);

		// write special raster
		specialRasterOffset = offsetRasters;
		rp.writeInt(0x80300210);
		rp.writeInt(0x00000200);
		rp.writeInt(0x00000001);
		rp.writeInt(0x00100000);
		position = specialRasterOffset + 0x10;

		// write all other rasters
		for (File rasterFile : IOUtils.getFilesWithExtension(MOD_SPR_PLR_SHARED, "png", false)) {
			Tile img = Tile.load(rasterFile, CI_4);
			int size = img.raster.limit();

			RasterTableEntry entry = new RasterTableEntry(position, size);
			entry.setSize(img.width, img.height);
			rasterEntryLookup.put(rasterFile.getName(), entry);

			img.writeRaster(rp, false);
			position += size;
			rp.skip(((position + 15) & -16) - position);
		}

		int endOfRasters = rp.getCurrentOffset();

		rp.seek("Player Raster List", romptrRasterList);
		for (Sprite spr : playerSprites) {
			for (int i = 0; i < spr.rasters.size(); i++) {
				SpriteRaster sr = spr.rasters.getElementAt(i);
				int packed = 0;

				if (sr.isSpecial) {
					packed |= (0x10 >> 4) << 20;
					packed |= (specialRasterOffset >> 4) & 0xFFFFF;
				}
				else {
					RasterTableEntry entry = rasterEntryLookup.get(sr.filename);
					if (entry == null)
						throw new StarRodException("Could not find raster file for %s: %s", spr.name, sr.filename);
					packed |= (entry.size >> 4) << 20;
					packed |= (entry.offset >> 4) & 0xFFFFF;
				}

				rp.writeInt(packed);
			}
		}

		rp.seek("Player Cache Info", 0x1025B8);
		for (int i = 0; i < 7; i++) {
			rp.writeInt(0x2000);
			rp.skip(8);
		}

		// return to end
		rp.seek("Player Animations", endOfRasters);
	}

	private void writePlayerYay0() throws IOException
	{
		// come back for this later
		int romptrStart = rp.getCurrentOffset();
		rp.skip(8 * numPlayerSprites);

		Yay0Cache cache = new Yay0Cache(MOD_SPR_PLR_CACHE);

		int[][] offsets = new int[numPlayerSprites][2];
		for (int i = 0; i < playerSprites.size(); i++) {
			String spriteSheetIDName = String.format("%02X", i + 1);

			File out = new File(MOD_SPR_PLR_TEMP + spriteSheetIDName);
			Logger.log(String.format("Writing player sprite %02X of %02X...", i + 1, playerSprites.size()), Priority.MILESTONE);
			Sprite spr = playerSprites.get(i);
			writeBinaryPlayer(spr, out);

			CacheResult result = cache.get(out);
			byte[] encoded = result.data;

			if (!result.fromCache)
				Logger.logDetail("Saved new player sprite to cache: " + spriteSheetIDName);
			else
				Logger.logDetail("Using cached file for player sprite: " + spriteSheetIDName);

			offsets[i][0] = rp.getCurrentOffset();
			rp.write(encoded);
			offsets[i][1] = rp.getCurrentOffset();
			Logger.log(String.format("Wrote player sprite %02X to %X", i, offsets[i][0]));
		}

		cache.save();

		// align after yay0 data
		rp.padOut(16);
		int romptrEndData = rp.getCurrentOffset();

		rp.seek("Player Sprite List", romptrStart);
		//	for(int v : offsets)
		//		rp.writeInt(v - romptrStart);
		//	rp.writeInt(romptrEndData - romptrStart);

		for (int i = 0; i < offsets.length; i++) {
			rp.writeInt(offsets[i][0] - romptrStart);
			rp.writeInt(offsets[i][1] - romptrStart);
		}

		// return to end
		rp.seek("After Player Sprites", romptrEndData);
		//	FileUtils.deleteDirectory(MOD_SPR_PLR_TEMP.toFile());
	}

	private void writeBinaryPlayer(Sprite spr, File binFile) throws IOException
	{
		spr.recalculateIndices();

		if (binFile.exists())
			binFile.delete();

		RandomAccessFile raf = new RandomAccessFile(binFile, "rw");
		raf.setLength(0x40000); // pre-allocate 256 Kb
		raf.seek(0x10); // reserve space for header

		// leave space for animation offsets
		raf.skipBytes(4 * (spr.animations.size() + 1));
		int[] animationOffsets = new int[spr.animations.size()];

		spr.maxComponents = 0;

		// write animations
		for (int i = 0; i < spr.animations.size(); i++) {
			animationOffsets[i] = (int) raf.getFilePointer();
			SpriteAnimation anim = spr.animations.get(i);

			// component offset list
			raf.skipBytes(4 * (anim.components.size() + 1));

			int[] componentOffsets = new int[anim.components.size()];

			if (anim.components.size() > spr.maxComponents)
				spr.maxComponents = anim.components.size();

			for (int j = 0; j < anim.components.size(); j++) {
				SpriteComponent comp = anim.components.get(j);

				int commandListOffset = (int) raf.getFilePointer();
				List<Short> cmdList = comp.rawAnim;
				for (short s : cmdList)
					raf.writeShort(s);
				if (cmdList.size() % 2 == 1)
					raf.writeShort(0);

				componentOffsets[j] = (int) raf.getFilePointer();
				raf.writeInt(commandListOffset);
				raf.writeShort(2 * cmdList.size());
				raf.writeShort(comp.posx);
				raf.writeShort(comp.posy);
				raf.writeShort(comp.posz);
			}

			int nextAnimation = (int) raf.getFilePointer();
			raf.seek(animationOffsets[i]);
			for (int v : componentOffsets)
				raf.writeInt(v);
			raf.writeInt(0xFFFFFFFF);
			raf.seek(nextAnimation);
		}

		// palettes start 8 byte aligned
		if ((raf.getFilePointer() & 0x7) == 4)
			raf.writeInt(0);

		// write palettes
		for (int i = 0; i < spr.palettes.size(); i++) {
			SpritePalette sp = spr.palettes.elementAt(i);
			sp.writeOffset = (int) raf.getFilePointer();
			sp.pal.write(raf);
		}

		// write rasters
		int rasterOffset = 0;
		int[] imageOffsets = new int[spr.rasters.size()];
		for (int i = 0; i < spr.rasters.size(); i++) {
			SpriteRaster sr = spr.rasters.get(i);

			imageOffsets[i] = (int) raf.getFilePointer();

			raf.writeInt(rasterOffset);
			raf.write(sr.img.width);
			raf.write(sr.img.height);
			raf.write(sr.defaultPal.getIndex());
			raf.write(-1);

			if (!sr.isSpecial) {
				RasterTableEntry entry = rasterEntryLookup.get(sr.filename);
				if (entry == null)
					throw new StarRodException("Could not find raster file for %s: %s", spr.name, sr.filename);
				rasterOffset += rasterEntryLookup.get(sr.filename).size;
			}
			else
				rasterOffset += 0x10;
		}

		// write image offset list
		int rasterOffsetListOffset = (int) raf.getFilePointer();
		for (int i : imageOffsets)
			raf.writeInt(i);
		raf.writeInt(0xFFFFFFFF);

		// write palette offset list
		int paletteOffsetListOffset = (int) raf.getFilePointer();
		for (int i = 0; i < spr.palettes.size(); i++) {
			SpritePalette sp = spr.palettes.elementAt(i);
			raf.writeInt(sp.writeOffset);
		}
		raf.writeInt(0xFFFFFFFF);

		raf.writeUTF(spr.toString());

		// finish header and close file
		raf.setLength(raf.getFilePointer());

		raf.seek(0);
		raf.writeInt(rasterOffsetListOffset);
		raf.writeInt(paletteOffsetListOffset);
		raf.writeInt(spr.maxComponents);
		raf.writeInt(spr.numVariations);

		for (int i : animationOffsets)
			raf.writeInt(i);
		raf.writeInt(0xFFFFFFFF);

		raf.close();
	}

	private void writeNpcYay0() throws IOException
	{
		int highestID = SpriteLoader.getMaximumID(SpriteSet.Npc);
		int[][] offsets = new int[highestID][2];

		int romptrStart = rp.getCurrentOffset();
		rp.skip(8 * highestID);

		int roomLeft = SPRITE_DATA_LIMIT - rp.getCurrentOffset();
		boolean reusingRoom = true;

		Yay0Cache cache = new Yay0Cache(MOD_SPR_NPC_CACHE);

		// build sprite sheets and write to ROM
		for (int i = 1; i <= highestID; i++) {
			String spriteSheetIDName = String.format("%02X", i);

			File out = new File(MOD_SPR_NPC_TEMP + spriteSheetIDName);
			Logger.log(String.format("Writing NPC sprite %02X of %02X...", i, highestID), Priority.MILESTONE);

			Sprite spr = spriteLoader.getSprite(SpriteSet.Npc, i);
			writeBinaryNpc(spr, out);

			CacheResult result = cache.get(out);
			byte[] encoded = result.data;

			if (!result.fromCache)
				Logger.logDetail("Saved new NPC sprite to cache: " + spriteSheetIDName);
			else
				Logger.logDetail("Using cached file for NPC sprite: " + spriteSheetIDName);

			if (reusingRoom && roomLeft < encoded.length) {
				rp.clear(rp.getCurrentOffset(), SPRITE_DATA_LIMIT);
				rp.seek("NPC Sprites", rp.nextAlignedOffset());
				reusingRoom = false;
			}

			offsets[i - 1][0] = rp.getCurrentOffset();
			rp.write(encoded);
			offsets[i - 1][1] = rp.getCurrentOffset();
			roomLeft -= encoded.length;

			Logger.log(String.format("Wrote NPC sprite %02X to %X", i, offsets[i - 1][0]));
		}

		if (reusingRoom)
			rp.clear(rp.getCurrentOffset(), SPRITE_DATA_LIMIT);

		rp.seek("NPC Sprite List", romptrStart);
		for (int i = 0; i < offsets.length; i++) {
			rp.writeInt(offsets[i][0] - romptrStart);
			rp.writeInt(offsets[i][1] - romptrStart);
		}

		FileUtils.deleteDirectory(MOD_SPR_NPC_TEMP.toFile());
		cache.save();
	}

	protected static void writeBinaryNpc(Sprite spr, File binFile) throws IOException
	{
		spr.recalculateIndices();

		if (binFile.exists())
			binFile.delete();

		RandomAccessFile raf = new RandomAccessFile(binFile, "rw");
		raf.setLength(0x40000); // pre-allocate 256 Kb
		raf.seek(0x10); // reserve space for header

		// leave space for animation offsets
		raf.skipBytes(4 * (spr.animations.size() + 1));
		int[] animationOffsets = new int[spr.animations.size()];

		spr.maxComponents = 0;

		// write animations
		for (int i = 0; i < spr.animations.size(); i++) {
			animationOffsets[i] = (int) raf.getFilePointer();
			SpriteAnimation anim = spr.animations.get(i);

			// component offset list
			raf.skipBytes(4 * (anim.components.size() + 1));

			int[] componentOffsets = new int[anim.components.size()];

			if (anim.components.size() > spr.maxComponents)
				spr.maxComponents = anim.components.size();

			for (int j = 0; j < anim.components.size(); j++) {
				SpriteComponent comp = anim.components.get(j);

				int commandListOffset = (int) raf.getFilePointer();
				List<Short> cmdList = comp.rawAnim;
				for (short s : cmdList)
					raf.writeShort(s);
				if (cmdList.size() % 2 == 1)
					raf.writeShort(0);

				componentOffsets[j] = (int) raf.getFilePointer();
				raf.writeInt(commandListOffset);
				raf.writeShort(2 * cmdList.size());
				raf.writeShort(comp.posx);
				raf.writeShort(comp.posy);
				raf.writeShort(comp.posz);
			}

			int nextAnimation = (int) raf.getFilePointer();
			raf.seek(animationOffsets[i]);
			for (int v : componentOffsets)
				raf.writeInt(v);
			raf.writeInt(0xFFFFFFFF);
			raf.seek(nextAnimation);
		}

		// palettes start 8 byte aligned
		if ((raf.getFilePointer() & 0x7) == 4)
			raf.writeInt(0);

		// write palettes
		for (int i = 0; i < spr.palettes.size(); i++) {
			SpritePalette sp = spr.palettes.elementAt(i);
			sp.writeOffset = (int) raf.getFilePointer();
			sp.pal.write(raf);
		}

		// write rasters
		int[] imageOffsets = new int[spr.rasters.size()];
		for (int i = 0; i < spr.rasters.size(); i++) {
			SpriteRaster sr = spr.rasters.get(i);

			if (sr.isSpecial)
				throw new IllegalStateException("NPC sprites cannot use special rasters!");

			int rasterOffset = (int) raf.getFilePointer();
			sr.img.writeRaster(raf, false);

			imageOffsets[i] = (int) raf.getFilePointer();
			raf.writeInt(rasterOffset);
			raf.write(sr.img.width);
			raf.write(sr.img.height);
			raf.write(sr.defaultPal.getIndex());
			raf.write(-1);
		}

		// write image offset list
		int rasterOffsetListOffset = (int) raf.getFilePointer();
		for (int i : imageOffsets)
			raf.writeInt(i);
		raf.writeInt(0xFFFFFFFF);

		// write palette offset list
		int paletteOffsetListOffset = (int) raf.getFilePointer();
		for (int i = 0; i < spr.palettes.size(); i++) {
			SpritePalette sp = spr.palettes.elementAt(i);
			raf.writeInt(sp.writeOffset);
		}
		raf.writeInt(0xFFFFFFFF);

		// finish header and close file
		raf.setLength(raf.getFilePointer());

		raf.seek(0);
		raf.writeInt(rasterOffsetListOffset);
		raf.writeInt(paletteOffsetListOffset);
		raf.writeInt(spr.maxComponents);
		raf.writeInt(spr.numVariations);

		for (int i : animationOffsets)
			raf.writeInt(i);
		raf.writeInt(0xFFFFFFFF);

		raf.close();
	}

	public void reserveTables() throws IOException
	{

	}

	public void writeTables(GlobalPatchManager gpm) throws IOException
	{
		/*
		 * When an enemy is removed, their sprite may be unloaded. If their spriteID >= EA, this will fail
		 * and the game will hang. For example, when an enemy is defeated:
		 *
		 * DoDeath (script_8029AEC0) calls RemoveActor (func_8027C7B0) at 8029AEE8
		 * This calls KillActor (func_80240BBC)
		 * [80240C94] provide some sprite ID pointer to func_802DE5E8
		 * func_802DE5E8 unloads the sprites? it checks if spriteID < EA and that's where it fails
		 * Solution: change [802DE628] <101718> 2A2200EA ==> 2A2200XX
		 */

		int highestID = SpriteLoader.getMaximumID(SpriteSet.Npc);

		rp.seek("Sprites Fix", 0x101718);
		rp.writeInt(0x2A220000 | ((highestID + 1) & 0xFFFF));

		/*
		 * When custom sprites are loaded in the overworld, pointers to some important data
		 * structure are stored in a table at 802DF5B0. This table assumes the number of sprites
		 * is EA, so it must be relocated and expanded if more are added.
		 */
		// 802DF5B0 - 802DF958	pointer table
		// 802DF958 - ??		byte table
		// 802DFA48 - 802DFE40	5x int table (often referenced by 802DFA4C, what about 802DFA50?)

		rp.seek("Sprites Fix", 0x100AF4); // 802DDA04
		rp.writeInt(0x2A020000 | ((highestID + 1) & 0xFFFF));

		/*
		rp.seek("Sprites List", rp.nextAlignedOffset());
		int ptrTableAddr = rp.toAddress(rp.getCurrentOffset());

		for(int i = 0; i <= highestID; i++)
			rp.writeInt(0);
		int countTableAddr = rp.toAddress(rp.getCurrentOffset());
		for(int i = 0; i < ((highestID + 4) & 0xFFFFFFFC) ; i++)
			rp.writeByte(0);
		*/

		int countsSize = (highestID + 1);
		int ptrsSize = (highestID + 1) * 4;

		gpm.readInternalPatch("ExtendedSprites.patch", String.format("CountsSize=%X", countsSize), String.format("PointersSize=%X", ptrsSize));

		/*
		int upperPtr = (ptrTableAddr >> 16) & 0xFFFF;
		int lowerPtr = ptrTableAddr & 0xFFFF;

		if((lowerPtr & 8000) != 0)
			upperPtr += 1;

		int upperCount = (countTableAddr >> 16) & 0xFFFF;
		int lowerCount = countTableAddr & 0xFFFF;

		if((lowerCount & 8000) != 0)
			upperCount += 1;

		System.out.printf("@@@ COUNT = %X%n", countTableAddr);
		System.out.printf("@@@ PTRS  = %X%n", ptrTableAddr);

		// <100AD4> 802DD9E4
		rp.seek("Sprites Fix", 0x100AD4);
		rp.writeInt(0x3C040000 | upperCount);
		rp.writeInt(0x24840000 | lowerCount);
		rp.writeInt(0x3C030000 | upperPtr);
		rp.writeInt(0x24630000 | lowerPtr);

		// <101264> 802DE174
		rp.seek("Sprites Fix", 0x101264);
		rp.writeInt(0x3C020000 | upperPtr);
		rp.writeInt(0x24420000 | lowerPtr);

		// <10127C> 802DE18C
		rp.seek("Sprites Fix", 0x10127C);
		rp.writeInt(0x3C030000 | upperCount);
		rp.writeInt(0x24630000 | lowerCount);

		// <1012B8> 802DE1C8
		rp.seek("Sprites Fix", 0x1012B8);
		rp.writeInt(0x3C010000 | upperCount);
		rp.skip(4);
		rp.writeInt(0xA0220000 | lowerCount);

		// <10172C> 802DE63C
		rp.seek("Sprites Fix", 0x10172C);
		rp.writeInt(0x3C030000 | upperCount);
		rp.writeInt(0x24630000 | lowerCount);

		// <101784> 802DE694
		rp.seek("Sprites Fix", 0x101784);
		rp.writeInt(0x3C030000 | upperCount);
		rp.skip(4);
		rp.writeInt(0x90630000 | lowerCount);

		// <1017A4> 802DE6B4
		rp.seek("Sprites Fix", 0x1017A4);
		rp.writeInt(0x3C010000 | upperPtr);
		rp.skip(4);
		rp.writeInt(0xAC200000 | lowerPtr);

		// <101AD0> 802DE9E0
		rp.seek("Sprites Fix", 0x101AD0);
		rp.writeInt(0x3C040000 | upperPtr);
		rp.skip(4);
		rp.writeInt(0x8C840000 | lowerPtr);

		// <101B34> 802DEA44
		rp.seek("Sprites Fix", 0x101B34);
		rp.writeInt(0x3C020000 | upperPtr);
		rp.skip(4);
		rp.writeInt(0x8C420000 | lowerPtr);

		// <101B60> 802DEA70
		rp.seek("Sprites Fix", 0x101B60);
		rp.writeInt(0x3C020000 | upperPtr);
		rp.skip(4);
		rp.writeInt(0x8C420000 | lowerPtr);

		// have sprites use pairs of offsets

		// <101C3C> 802deb4c
		rp.seek("Sprites Fix", 0x101C3C);
		AsmUtils.assembleAndWrite("SetInitialMap", rp, "SLL  A0, S5, 3");

		// <101C48> 802deb58
		rp.seek("Sprites Fix", 0x101C48);
		AsmUtils.assembleAndWrite("SetInitialMap", rp, "SLL  A0, S5, 3");
		*/
	}

	/*
	//TODO
	public void compileNpcSprites() throws IOException
	{
		File cache = new File(MOD_SPR_NPC_CACHE + FN_SPRITE_CACHE);
		if(cache.exists())
			loadCache(cache);

		int highestID = SpriteLoader.getMaximumID(SpriteSet.Npc);

		raf.seek(SNPCPRITE_TABLE_BASE + (highestID + 1) * 4);
		int roomLeft = SPRITE_DATA_LIMIT - (int)raf.getFilePointer();
		boolean reusingRoom = true;

		int[] offsets = new int[highestID + 2];

		// build sprite sheets and write to ROM
		for(int i = 1; i <= highestID; i++)
		{
			String spriteSheetIDName = String.format("%02X", i);

			File out = new File(MOD_SPR_NPC_TEMP + spriteSheetIDName);
			Logger.log(String.format("Writing sprite sheet %02X of %02X...", i, highestID), Priority.MILESTONE);

			Sprite spr = spriteLoader.getSprite(SpriteSet.Npc, i);
			SpriteIO.writeBinaryNpc(spr, out);

			byte[] decoded = FileUtils.readFileToByteArray(out);

			Checksum checksum = new CRC32();
			checksum.update(decoded, 0, decoded.length);

			long newCRC = checksum.getValue();
			Long cacheCRC = cachedChecksums.get(i);

			byte[] encoded;
			File cachedFile = new File(MOD_SPR_NPC_CACHE + spriteSheetIDName);

			if(cacheCRC != null && newCRC == cacheCRC)
			{
				if(cachedFile.exists())
				{
					Logger.logDetail("Using cached file for sprite sheet " + spriteSheetIDName);
					encoded = FileUtils.readFileToByteArray(cachedFile);
				}
				else
				{
					Logger.logWarning("Sprite sheet " + spriteSheetIDName + " matches cached checksum, but cached file is missing!");
					encoded = Yay0Helper.encode(decoded);

					FileUtils.writeByteArrayToFile(cachedFile, encoded);
					cachedChecksums.put(i, newCRC);
				}
			}
			else
			{
				encoded = Yay0Helper.encode(decoded);

				FileUtils.writeByteArrayToFile(cachedFile, encoded);
				cachedChecksums.put(i, newCRC);
			}
		}
	}
	 */
}
