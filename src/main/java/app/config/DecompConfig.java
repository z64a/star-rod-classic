package app.config;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.yaml.snakeyaml.Yaml;

import util.Logger;

public class DecompConfig
{
	public File buildDir;
	public File mapBuildDir;
	public List<File> assetDirectories;

	public List<String> textureArchiveNames;
	public List<String> mapNames;
	public List<String> backgroundNames;
	public List<String> npcSpriteNames;

	@SuppressWarnings("unchecked")
	public DecompConfig(File directory, File splatFile, File spriteFile) throws IOException
	{
		Map<String, Object> map = new Yaml().load(new FileInputStream(splatFile));
		Map<String, Object> sprite_map = new Yaml().load(new FileInputStream(spriteFile));

		List<String> assetDirNames = (List<String>) map.get("asset_stack");
		List<Object> segments = (List<Object>) map.get("segments");

		npcSpriteNames = new ArrayList<>();

		npcSpriteNames.addAll(sprite_map.keySet());

		Logger.log("Found " + npcSpriteNames.size() + " NPC sprites from decomp config");

		buildDir = new File(directory, "assets/star_rod_build");
		buildDir.mkdir();

		mapBuildDir = new File(buildDir, "mapfs");
		mapBuildDir.mkdir();

		File assetsDir = new File(directory, "assets");

		assetDirectories = new ArrayList<>();
		for (String dirName : assetDirNames) {
			assetDirectories.add(new File(assetsDir, dirName));
		}

		for (Object segment : segments) {
			if (segment instanceof Map) {
				Map<String, Object> segmentMap = (Map<String, Object>) segment;

				String name = (String) segmentMap.get("name");

				if (name == null) {
					continue;
				}

				if (name.equals("mapfs")) {
					List<String> fileNames = (List<String>) segmentMap.get("files");

					mapNames = new ArrayList<>();
					backgroundNames = new ArrayList<>();
					textureArchiveNames = new ArrayList<>();

					for (String fileName : fileNames) {
						if (fileName.endsWith("_shape")) {
							mapNames.add(fileName.substring(0, fileName.lastIndexOf('_')));
						}
						else if (fileName.endsWith("_bg")) {
							backgroundNames.add(fileName.substring(0, fileName.lastIndexOf('_')));
						}
						else if (fileName.endsWith("_tex")) {
							textureArchiveNames.add(fileName);
						}
					}

					Logger.log("Found " + npcSpriteNames.size() + " maps in decomp config");
					Logger.log("Found " + backgroundNames.size() + " backgrounds in decomp config");
					Logger.log("Found " + textureArchiveNames.size() + " texture archives in decomp config");
				}
			}
		}
	}
}
