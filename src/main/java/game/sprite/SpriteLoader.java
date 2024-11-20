package game.sprite;

import static app.Directories.*;
import static game.sprite.SpriteTableKey.*;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.TreeMap;

import org.apache.commons.io.FileUtils;
import org.w3c.dom.Element;

import app.AssetManager;
import app.Environment;
import game.texture.Palette;
import util.Logger;
import util.xml.XmlWrapper.XmlReader;
import util.xml.XmlWrapper.XmlWriter;

public class SpriteLoader
{
	// represents an object with an ID
	public static interface Indexable<T>
	{
		public T getObject();

		public int getIndex();
	}

	public static class SpriteMetadata implements Indexable<String>
	{
		public final int id;
		public final String name;
		private final File xml;

		private SpriteMetadata(int id, String name, File xml)
		{
			this.id = id;
			this.name = name;
			this.xml = xml;
		}

		@Override
		public String getObject()
		{
			return name;
		}

		@Override
		public int getIndex()
		{
			return id;
		}

		public long lastModified()
		{
			return xml.lastModified();
		}
	}

	public static enum SpriteSet
	{
		Player, Npc
	}

	private static boolean loaded = false;

	private static TreeMap<Integer, SpriteMetadata> playerSpriteData = null;
	private static TreeMap<Integer, SpriteMetadata> npcSpriteData = null;

	private HashMap<Integer, Sprite> playerSpriteCache = new HashMap<>();
	private HashMap<Integer, Sprite> npcSpriteCache = new HashMap<>();

	public static void initialize()
	{
		if (!loaded) {
			readSpriteTable();
			loaded = true;
		}
	}

	public SpriteLoader()
	{
		initialize();
	}

	private static TreeMap<Integer, SpriteMetadata> getMap(SpriteSet set)
	{
		switch (set) {
			case Npc:
				return npcSpriteData;
			case Player:
				return playerSpriteData;
		}
		throw new IllegalArgumentException("Unknown sprite set: " + set);
	}

	public static Integer getSpriteBefore(SpriteSet set, int id)
	{
		if (!loaded)
			throw new IllegalStateException("getSpriteBefore invoked before initializing SpriteLoader!");

		TreeMap<Integer, SpriteMetadata> spriteMap = getMap(set);
		return spriteMap.floorKey(id - 1);
	}

	public static Integer getSpriteAfter(SpriteSet set, int id)
	{
		if (!loaded)
			throw new IllegalStateException("getSpriteAfter invoked before initializing SpriteLoader!");

		TreeMap<Integer, SpriteMetadata> spriteMap = getMap(set);
		return spriteMap.ceilingKey(id + 1);
	}

	public static int getMaximumID(SpriteSet set)
	{
		if (!loaded)
			throw new IllegalStateException("getMaximumID invoked before initializing SpriteLoader!");

		TreeMap<Integer, SpriteMetadata> spriteMap = getMap(set);
		return (spriteMap.isEmpty()) ? 0 : spriteMap.lastKey();
	}

	public static Collection<SpriteMetadata> getValidSprites(SpriteSet set)
	{
		if (!loaded)
			throw new IllegalStateException("getValidSprites invoked before initializing SpriteLoader!");

		TreeMap<Integer, SpriteMetadata> spriteMap = getMap(set);
		return spriteMap.values();
	}

	public Sprite getSprite(SpriteSet set, int id)
	{
		return getSprite(set, id, false);
	}

	public Sprite getSprite(SpriteSet set, int id, boolean forceReload)
	{
		if (!loaded)
			throw new IllegalStateException("getSprite invoked before initializing SpriteLoader!");

		switch (set) {
			case Npc:
				return getNpcSprite(id, forceReload);
			case Player:
				return getPlayerSprite(id, forceReload);
		}
		throw new IllegalArgumentException("Unknown sprite set: " + set);
	}

	private Sprite getNpcSprite(int id, boolean forceReload)
	{
		if (!forceReload && npcSpriteCache.containsKey(id))
			return npcSpriteCache.get(id);

		if (!npcSpriteData.containsKey(id))
			return null;

		SpriteMetadata md = npcSpriteData.get(id);
		File xmlFile = md.xml;
		Sprite npcSprite = null;

		try {
			npcSprite = Sprite.read(xmlFile, SpriteSet.Npc);
			npcSprite.name = md.name;
			npcSpriteCache.put(id, npcSprite);
		}
		catch (Throwable e) {
			Logger.logWarning("Error while loading NPC sprite! " + e.getMessage());
			e.printStackTrace();
			return null;
			//			npcSpriteData.remove(id); // file is invalid
		}

		return npcSprite;
	}

	private Sprite getPlayerSprite(int id, boolean forceReload)
	{
		if (!forceReload && playerSpriteCache.containsKey(id))
			return playerSpriteCache.get(id);

		if (!playerSpriteData.containsKey(id))
			return null;

		SpriteMetadata md = playerSpriteData.get(id);
		File xmlFile = md.xml;
		Sprite playerSprite = null;

		try {
			playerSprite = Sprite.read(xmlFile, SpriteSet.Player);
			playerSprite.name = md.name;
			playerSpriteCache.put(id, playerSprite);
		}
		catch (Throwable e) {
			Logger.logWarning("Error while loading player sprite " + md.id + "! " + e.getMessage());
			e.printStackTrace();
			playerSpriteData.remove(id); // file is invalid
		}

		return playerSprite;
	}

	private static void readSpriteTable()
	{
		npcSpriteData = new TreeMap<>();
		playerSpriteData = new TreeMap<>();

		if (Environment.project.isDecomp) {
			for (int i = 0; i < Environment.project.decompConfig.npcSpriteNames.size(); i++) {
				String name = Environment.project.decompConfig.npcSpriteNames.get(i);
				File xml = AssetManager.getNpcSprite(name);

				if (xml == null) {
					Logger.logWarning("Cannot find npc sprite '" + name + "'!");
					continue;
				}

				npcSpriteData.put(i + 1, new SpriteMetadata(i + 1, name, xml));
			}
		}
		else {
			File in = new File(MOD_SPRITE + FN_SPRITE_TABLE);
			XmlReader xmr = new XmlReader(in);
			Element rootElem = xmr.getRootElement();

			Element npcListElem = xmr.getUniqueRequiredTag(rootElem, TAG_NPC_LIST);
			List<Element> npcElems = xmr.getTags(npcListElem, TAG_SPRITE);
			for (Element npcElem : npcElems) {
				xmr.requiresAttribute(npcElem, ATTR_ID);
				xmr.requiresAttribute(npcElem, ATTR_NAME);
				xmr.requiresAttribute(npcElem, ATTR_SOURCE);

				int id = xmr.readHex(npcElem, ATTR_ID);
				String name = xmr.getAttribute(npcElem, ATTR_NAME);
				String dir = MOD_SPR_NPC_SRC + "/" + xmr.getAttribute(npcElem, ATTR_SOURCE) + "/";
				File xml = new File(dir + FN_SPRITESHEET);
				if (!xml.exists()) {
					Logger.logWarning(dir + FN_SPRITESHEET + " does not exist!");
					continue;
				}

				//	System.out.printf("NPC %2X %s%n", id, name);
				npcSpriteData.put(id, new SpriteMetadata(id, name, xml));
			}

			Element playerListElem = xmr.getUniqueRequiredTag(rootElem, TAG_PLAYER_LIST);
			List<Element> playerElems = xmr.getTags(playerListElem, TAG_SPRITE);
			for (Element playerElem : playerElems) {
				xmr.requiresAttribute(playerElem, ATTR_ID);
				xmr.requiresAttribute(playerElem, ATTR_NAME);
				xmr.requiresAttribute(playerElem, ATTR_SOURCE);

				int id = xmr.readHex(playerElem, ATTR_ID);
				String name = xmr.getAttribute(playerElem, ATTR_NAME);
				String dir = MOD_SPR_PLR_SRC + "/" + xmr.getAttribute(playerElem, ATTR_SOURCE) + "/";
				File xml = new File(dir + FN_SPRITESHEET);
				if (!xml.exists()) {
					Logger.logWarning(dir + FN_SPRITESHEET + " does not exist!");
					continue;
				}

				//	System.out.printf("PLR %2X %s%n", id, name);
				playerSpriteData.put(id, new SpriteMetadata(id, name, xml));
			}
		}
	}

	public static Sprite create(SpriteSet set, int id) throws IOException
	{
		String spriteName = String.format("%02X", id);
		String dirName;
		switch (set) {
			case Npc:
				dirName = MOD_SPR_NPC_SRC + "/" + spriteName + "/";
				break;
			case Player:
				dirName = MOD_SPR_PLR_SRC + "/" + spriteName + "/";
				break;
			default:
				throw new IllegalArgumentException("Unknown sprite set: " + set);
		}

		File xml = new File(dirName + FN_SPRITESHEET);
		FileUtils.touch(xml);

		Sprite spr = new Sprite(set);
		spr.source = xml;
		spr.palettes.addElement(new SpritePalette(Palette.createDefaultForSprite()));
		spr.recalculateIndices();

		try (XmlWriter xmw = new XmlWriter(xml)) {
			File spriteDir = xml.getParentFile();
			if (!spr.isPlayerSprite())
				spr.dumpRasters(spriteDir, true);
			spr.dumpPalettes(spriteDir);
			spr.toXML(xmw);
			xmw.save();
			Logger.log("Saved sprite to /" + dirName + "/");
		}

		SpriteMetadata data = new SpriteMetadata(id, spriteName, xml);
		switch (set) {
			case Npc:
				npcSpriteData.put(id, data);
				break;
			case Player:
				playerSpriteData.put(id, data);
				break;
			default:
				throw new IllegalArgumentException("Unknown sprite set: " + set);
		}

		return spr;
	}
}
