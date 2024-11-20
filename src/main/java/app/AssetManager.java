package app;

import static app.Directories.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
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
		if (Environment.project.isDecomp) {
			for (File dir : Environment.project.decompConfig.assetDirectories) {
				File f = new File(dir, "mapfs/" + texName + "." + TextureArchive.EXT);

				if (f.exists())
					return f;
			}
		}
		else {
			File f = new File(MOD_IMG_TEX + texName + "." + TextureArchive.EXT);

			if (f.exists()) {
				return f;
			}
		}

		// Fall back to dump
		File f = new File(DUMP_IMG_TEX + texName + "." + TextureArchive.EXT);

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
		if (Environment.project.isDecomp) {
			for (File dir : Environment.project.decompConfig.assetDirectories) {
				File f = new File(dir, "mapfs/" + filename);
				if (f.exists())
					return f;
			}

			return null;
		}
		else {
			File[] matches = IOUtils.getFileWithin(MOD_MAP_SAVE, filename, true);

			if (matches.length == 0)
				matches = IOUtils.getFileWithin(MOD_MAP_SRC, filename, true);

			if (matches.length == 0)
				return null;

			return matches[0];
		}
	}

	public static File getSaveMap(String mapName)
	{
		String filename = mapName + ".xml";
		if (Environment.project.isDecomp) {
			for (File dir : Environment.project.decompConfig.assetDirectories) {
				File f = new File(dir, "map/" + filename);
				if (f.exists())
					return f;
			}

			// Default to first in asset stack
			File defaultSaveDir = Environment.project.decompConfig.assetDirectories.get(0);
			return new File(defaultSaveDir, "mapfs/" + filename);
		}
		else {
			return new File(MOD_MAP_SAVE + filename);
		}
	}

	public static File getBackground(String bgName)
	{
		if (Environment.project.isDecomp) {
			for (File dir : Environment.project.decompConfig.assetDirectories) {
				File f = new File(dir, "mapfs/" + bgName + ".png");

				if (f.exists())
					return f;
			}

			return new File(DUMP_IMG_BG + bgName + ".png");
		}
		else {
			return new File(MOD_IMG_BG + bgName + ".png");
		}
	}

	public static File getNpcSprite(String spriteName)
	{
		if (Environment.project.isDecomp) {
			for (File dir : Environment.project.decompConfig.assetDirectories) {
				File f = new File(dir, "sprite/npc/" + spriteName + "/" + FN_SPRITESHEET);

				if (f.exists())
					return f;
			}
		}

		return new File(MOD_SPR_NPC_SRC + spriteName + "/" + FN_SPRITESHEET);
	}

	public static File getPlayerSprite(String spriteName)
	{
		if (Environment.project.isDecomp) {
			// Decomp doesn't support player sprites yet
			// Fall back to dump
			return new File(DUMP_SPR_PLR_SRC + spriteName + "/" + FN_SPRITESHEET);
		}
		else {
			return new File(MOD_SPR_PLR_SRC + spriteName + "/" + FN_SPRITESHEET);
		}
	}

	public static File getPlayerSpriteRaster(String raster)
	{
		if (Environment.project.isDecomp) {
			// Decomp doesn't support player sprites yet
			// Fall back to dump
			return new File(DUMP_SPR_PLR_SHARED + raster);
		}
		else {
			return new File(MOD_SPR_PLR_SHARED + raster);
		}
	}

	public static Collection<File> getTextureArchivesToBuild() throws IOException
	{
		if (Environment.project.isDecomp) {
			ArrayList<File> archives = new ArrayList<>();

			for (String archiveName : Environment.project.decompConfig.textureArchiveNames) {
				File archive = getTextureArchive(archiveName);

				if (archive != null) {
					archives.add(archive);
				}
			}

			return archives;
		}
		else {
			return IOUtils.getFilesWithExtension(MOD_IMG_TEX, TextureArchive.EXT, true);
		}
	}

	public static Collection<File> getMapsToBuild() throws IOException
	{
		if (Environment.project.isDecomp) {
			ArrayList<File> maps = new ArrayList<>();

			for (String mapName : Environment.project.decompConfig.mapNames) {
				File map = getMap(mapName);

				if (map != null) {
					maps.add(map);
				}
			}

			return maps;
		}
		else {
			return IOUtils.getFilesWithExtension(MOD_MAP_SAVE, "xml", true);
		}
	}

	public static File getMapBuildDir()
	{
		if (Environment.project.isDecomp)
			return Environment.project.decompConfig.mapBuildDir;
		else
			return MOD_MAP_BUILD.toFile();
	}

	public static List<StringResource> getStringAssets() throws IOException
	{
		ArrayList<StringResource> assets = new ArrayList<>();

		if (Environment.project.isDecomp) {
			ArrayList<String> relativePaths = new ArrayList<>();

			for (File assetDir : Environment.project.decompConfig.assetDirectories) {
				File dir = new File(assetDir, "msg");

				if (!dir.exists())
					continue;

				Path dirPath = dir.toPath();

				for (File file : IOUtils.getFilesWithExtension(dir, "msg", true)) {
					String relativePath = dirPath.relativize(file.toPath()).toString();

					// Files with the same name override old ones
					int idx = relativePaths.indexOf(relativePath);
					if (idx != -1) {
						assets.remove(idx);
						relativePaths.remove(idx);
					}

					assets.add(new StringResource(file));
					relativePaths.add(relativePath);
				}
			}
		}
		else {
			for (File file : IOUtils.getFilesWithExtension(MOD_STRINGS_SRC, new String[] { "str", "msg" }, true))
				assets.add(new StringResource(file));

			for (File file : IOUtils.getFilesWithExtension(MOD_STRINGS_PATCH, new String[] { "str", "msg" }, true))
				assets.add(new StringResource(file));
		}

		return assets;
	}
}
