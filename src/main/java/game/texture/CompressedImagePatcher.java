package game.texture;

import static app.Directories.*;
import static game.texture.TileFormat.*;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;

import app.AssetManager;
import app.Directories;
import game.sprite.Yay0Cache;
import game.sprite.Yay0Cache.CacheResult;
import util.Logger;

public class CompressedImagePatcher
{
	private static final int DRAW_POS = 0x000C0014;

	private final Yay0Cache cache;

	public CompressedImagePatcher() throws IOException
	{
		cache = new Yay0Cache(Directories.MOD_IMG_CACHE);
	}

	public void buildTextureArchives() throws IOException
	{
		String buildDir = AssetManager.getMapBuildDir().getAbsolutePath();
		for (File f : AssetManager.getTextureArchivesToBuild()) {
			TextureArchive ta = TextureArchive.loadText(f);
			Logger.log("Building texture archive: " + f.getName());
			ta.writeBinary(buildDir);
		}
	}

	public void buildBackgrounds() throws IOException
	{
		for (File f : MOD_IMG_BG.toFile().listFiles()) {
			String baseName = FilenameUtils.removeExtension(f.getName());

			if (baseName.endsWith("_bg")) {
				String ext = FilenameUtils.getExtension(f.getName());
				File altFile = new File(MOD_IMG_BG + baseName + "_alt." + ext);

				Tile img = Tile.load(f, CI_8);
				Tile alt = null;

				int fileSize = img.raster.limit() + 0x210;
				int imgAddress = 0x80200210;
				int palAddress = 0x80200010;

				if (altFile.exists()) {
					alt = Tile.load(altFile, CI_8);
					fileSize += 0x210;
					imgAddress += 0x210;
					palAddress += 0x10;
				}

				byte[] bytes = new byte[fileSize];
				ByteBuffer bb = ByteBuffer.wrap(bytes);

				bb.putInt(imgAddress);
				bb.putInt(palAddress);
				bb.putInt(DRAW_POS);
				bb.putShort((short) img.width);
				bb.putShort((short) img.height);

				if (altFile.exists()) {
					bb.putInt(imgAddress);
					bb.putInt(palAddress + 0x200);
					bb.putInt(DRAW_POS);
					bb.putShort((short) img.width);
					bb.putShort((short) img.height);
				}

				img.palette.put(bb);
				if (altFile.exists())
					alt.palette.put(bb);
				img.putRaster(bb, false);

				File out = new File(MOD_MAP_BUILD + baseName);

				CacheResult result = cache.get(out, bytes);
				byte[] encoded = result.data;

				if (!result.fromCache)
					Logger.logDetail("Saved background to cache: " + baseName);
				else
					Logger.logDetail("Using cached file for background: " + baseName);

				FileUtils.writeByteArrayToFile(out, encoded);
			}
		}
		cache.save();
	}

	public void patchCompressedImages() throws IOException
	{
		patchTitleImages();
		patchPartyImages();
	}

	private void patchTitleImages() throws IOException
	{
		File f1 = new File(MOD_IMG_COMP + "title_1.png");
		File f2 = new File(MOD_IMG_COMP + "title_2.png");
		File f3 = new File(MOD_IMG_COMP + "title_3.png");

		File in = new File(DUMP_MAP_RAW + "title_data");
		File out = new File(MOD_MAP_BUILD + "title_data");

		byte[] titleData = FileUtils.readFileToByteArray(in);
		ByteBuffer bb = ByteBuffer.wrap(titleData);
		Tile img;

		if (!f1.exists() && !f2.exists() && !f3.exists()) {
			FileUtils.forceDelete(out);
			return;
		}

		if (f1.exists()) {
			img = Tile.load(f1, RGBA_32);
			if (img.height != 112 || img.width != 200)
				throw new RuntimeException(f1.getName() + " must be 112x200 pixels.");

			bb.position(0x2210);
			img.putRaster(bb, false);
		}

		if (f2.exists()) {
			img = Tile.load(f2, IA_8);
			if (img.height != 32 || img.width != 144)
				throw new RuntimeException(f2.getName() + " must be 32x144 pixels.");

			bb.position(0x10);
			img.putRaster(bb, false);
		}

		if (f3.exists()) {
			img = Tile.load(f3, IA_8);
			if (img.height != 32 || img.width != 128)
				throw new RuntimeException(f3.getName() + " must be 32x128 pixels.");

			bb.position(0x1210);
			img.putRaster(bb, false);
		}

		CacheResult result = cache.get(out, titleData);
		byte[] encoded = result.data;

		if (!result.fromCache)
			Logger.logDetail("Saved to cache: " + out.getName());
		else
			Logger.logDetail("Using cached file for: " + out.getName());

		FileUtils.writeByteArrayToFile(out, encoded);
		cache.save();
	}

	private void patchPartyImages() throws IOException
	{
		patchPartyImage("party_kurio");
		patchPartyImage("party_kameki");
		patchPartyImage("party_pinki");
		patchPartyImage("party_pareta");
		patchPartyImage("party_resa");
		patchPartyImage("party_akari");
		patchPartyImage("party_opuku");
		patchPartyImage("party_pokopi");
		cache.save();
	}

	private void patchPartyImage(String name) throws IOException
	{
		File f = new File(MOD_IMG_COMP + name + ".png");

		if (f.exists()) {
			File in = new File(DUMP_MAP_RAW + name);
			File out = new File(MOD_MAP_BUILD + name);

			byte[] bytes = FileUtils.readFileToByteArray(in);
			ByteBuffer bb = ByteBuffer.wrap(bytes);

			Tile img = Tile.load(f, CI_8);
			if (img.height != 105 || img.width != 150)
				throw new RuntimeException(f.getName() + " must be 105x150 pixels.");

			bb.position(0);
			img.palette.put(bb);
			img.putRaster(bb, false);

			CacheResult result = cache.get(out, bytes);
			byte[] encoded = result.data;

			if (!result.fromCache)
				Logger.logDetail("Saved to cache: " + out.getName());
			else
				Logger.logDetail("Using cached file for: " + out.getName());

			FileUtils.writeByteArrayToFile(out, encoded);
		}
	}
}
