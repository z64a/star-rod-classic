package app;

import static app.Directories.*;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import app.input.IOUtils;
import game.string.editor.io.StringResource;
import game.texture.TextureArchive;

public class AssetManager
{
	public static File getTextureArchive(String texName)
	{
		File f = new File(MOD_IMG_TEX + texName + "." + TextureArchive.EXT);

		if (f.exists()) {
			return f;
		}

		// Fall back to dump
		f = new File(DUMP_IMG_TEX + texName + "." + TextureArchive.EXT);

		if (f.exists()) {
			return f;
		}
		else {
			return null;
		}
	}

	public static File getMap(String mapName)
	{
		String filename = mapName + ".xml";
		File[] matches = IOUtils.getFileWithin(MOD_MAP_SAVE, filename, true);

		if (matches.length == 0)
			matches = IOUtils.getFileWithin(MOD_MAP_SRC, filename, true);

		if (matches.length == 0)
			return null;

		return matches[0];
	}

	public static File getSaveMap(String mapName)
	{
		String filename = mapName + ".xml";
		return new File(MOD_MAP_SAVE + filename);
	}

	public static File getBackground(String bgName)
	{
		return new File(MOD_IMG_BG + bgName + ".png");
	}

	public static File getNpcSprite(String spriteName)
	{
		return new File(MOD_SPR_NPC_SRC + spriteName + "/" + FN_SPRITESHEET);
	}

	public static File getPlayerSprite(String spriteName)
	{
		return new File(MOD_SPR_PLR_SRC + spriteName + "/" + FN_SPRITESHEET);
	}

	public static File getPlayerSpriteRaster(String raster)
	{
		return new File(MOD_SPR_PLR_SHARED + raster);
	}

	public static Collection<File> getTextureArchivesToBuild() throws IOException
	{
		return IOUtils.getFilesWithExtension(MOD_IMG_TEX, TextureArchive.EXT, true);
	}

	public static Collection<File> getMapsToBuild() throws IOException
	{
		return IOUtils.getFilesWithExtension(MOD_MAP_SAVE, "xml", true);
	}

	public static File getMapBuildDir()
	{
		return MOD_MAP_BUILD.toFile();
	}

	public static List<StringResource> getStringAssets() throws IOException
	{
		ArrayList<StringResource> assets = new ArrayList<>();

		for (File file : IOUtils.getFilesWithExtension(MOD_STRINGS_SRC, new String[] { "str", "msg" }, true))
			assets.add(new StringResource(file));

		for (File file : IOUtils.getFilesWithExtension(MOD_STRINGS_PATCH, new String[] { "str", "msg" }, true))
			assets.add(new StringResource(file));

		return assets;
	}
}
