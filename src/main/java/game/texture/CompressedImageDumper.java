package game.texture;

import static app.Directories.*;
import static game.texture.TileFormat.*;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;

import app.input.IOUtils;
import util.Logger;
import util.Priority;

public class CompressedImageDumper
{
	public static void dumpTextures() throws IOException
	{
		dumpAllTextures();
		dumpAllBackgrounds();
		dumpTitleImages();
		dumpPartyImages();
	}

	private static void dumpAllTextures() throws IOException
	{
		File[] assets = DUMP_MAP_RAW.toFile().listFiles();
		int count = 0;

		for (File f : assets)
			if (f.getName().endsWith("_tex")) {
				Logger.log("Dumping textures from " + f.getName(), Priority.MILESTONE);
				TextureArchive ta = new TextureArchive(f);
				ta.dumpToDirectory(DUMP_IMG_TEX.toFile());
				count += ta.textureList.size();
			}

		Logger.logf("Dumped %d textures.\n", count);
	}

	private static void dumpAllBackgrounds() throws IOException
	{
		File[] assets = DUMP_MAP_RAW.toFile().listFiles();

		for (File f : assets)
			if (f.getName().endsWith("_bg")) {
				dumpBackground(f, 0, f.getName());

				// this background has an extra palette
				if (f.getName().equals("sbk_bg"))
					dumpBackground(f, 0x10, "sbk_bg_alt");
			}
	}

	private static void dumpBackground(File f, int headerOffset, String name) throws IOException
	{
		Logger.log("Dumping background " + f.getName(), Priority.MILESTONE);

		ByteBuffer bb = IOUtils.getDirectBuffer(f);

		bb.position(headerOffset);
		int imageOffset = bb.getInt() - 0x80200000;
		int paletteOffset = bb.getInt() - 0x80200000;
		bb.getInt(); // 2D draw position for top left corner, always (12,20) -- 0x000C0014
		int width = bb.getShort();
		int height = bb.getShort();

		Tile img = new Tile(CI_8, height, width);
		img.readImage(bb, imageOffset, false);
		img.readPalette(bb, paletteOffset);

		img.savePNG(DUMP_IMG_BG + name);
	}

	private static void dumpTitleImages() throws IOException
	{
		RandomAccessFile raf = new RandomAccessFile(DUMP_MAP_RAW + "title_data", "r");
		Tile img;

		img = new Tile(RGBA_32, 112, 200);
		img.readImage(raf, 0x2210, false);
		img.savePNG(DUMP_IMG_COMP + "title_1");

		img = new Tile(IA_8, 32, 144);
		img.readImage(raf, 0x10, false);
		img.savePNG(DUMP_IMG_COMP + "title_2");

		img = new Tile(IA_8, 32, 128);
		img.readImage(raf, 0x1210, false);
		img.savePNG(DUMP_IMG_COMP + "title_3");

		raf.close();
	}

	private static void dumpPartyImages() throws IOException
	{
		dumpPartyImage("party_kurio");
		dumpPartyImage("party_kameki");
		dumpPartyImage("party_pinki");
		dumpPartyImage("party_pareta");
		dumpPartyImage("party_resa");
		dumpPartyImage("party_akari");
		dumpPartyImage("party_opuku");
		dumpPartyImage("party_pokopi");
	}

	private static void dumpPartyImage(String name) throws IOException
	{
		RandomAccessFile raf = new RandomAccessFile(DUMP_MAP_RAW + name, "r");

		Tile img = new Tile(CI_8, 105, 150);
		img.readImage(raf, 0x200, false);
		img.readPalette(raf, 0);
		img.savePNG(DUMP_IMG_COMP + name);

		raf.close();
	}
}
