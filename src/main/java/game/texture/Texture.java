package game.texture;

import static game.texture.TileFormat.TYPE_CI;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.ByteBuffer;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import app.input.IOUtils;
import app.input.InputFileException;
import util.Logger;

public class Texture
{
	public static final int IMG = 1;
	public static final int AUX = 0;

	public static final int WRAP_REPEAT = 0;
	public static final int WRAP_MIRROR = 1;
	public static final int WRAP_CLAMP = 2;

	public final String name;

	public Tile main;
	public Tile aux;
	public List<Tile> mipmapList;

	public int extra;
	public boolean hasAux = false;
	public boolean hasMipmaps = false;

	public int[] hWrap;
	public int[] vWrap;
	public boolean filter;
	public int auxCombine;

	private static final String IMG_KEY = "img";
	private static final String AUX_KEY = "aux";
	private static final String MIPMAP_KEY = "mipmaps";
	private static final String FILTER_KEY = "filter";
	private static final String COMBINE_KEY = "combine";
	private static final String YES = "yes";
	private static final String NO = "no";

	private Texture(String name)
	{
		this.name = name;
	}

	/*
	 * Use this constructor to load a texture from a tex asset file.
	 */
	public Texture(ByteBuffer bb) throws IOException
	{
		// STEP 1: read header
		name = IOUtils.readString(bb, 0x20);

		int[] width = new int[2];
		width[AUX] = bb.getShort();
		width[IMG] = bb.getShort();

		int[] height = new int[2];
		height[AUX] = bb.getShort();
		height[IMG] = bb.getShort();

		extra = (bb.getShort() & 0xF);

		switch (extra) {
			case 0:
				break;
			case 1:
				hasMipmaps = true;
				break;
			case 2:
			case 3:
				hasAux = true;
				break;
		}

		auxCombine = (bb.get() & 0xFF);

		int[] type = splitByte(bb.get());
		int[] depth = splitByte(bb.get());
		hWrap = splitByte(bb.get());
		vWrap = splitByte(bb.get());
		byte filterMode = bb.get();
		filter = (filterMode == 2);
		assert (filterMode == 0 || filterMode == 2);

		// STEP 2: interpret additional information

		if (extra != 3 && auxCombine != 0 && auxCombine != 8)
			System.out.printf("# %s %X %X%n", name, extra, auxCombine);

		switch (auxCombine) {
			case 0x00: // iwa_komonotif, mac_s_roadberotif, most tst_* textures
			case 0x08: // huge majority of textures
			case 0x0D: // iwa_dropwater_btif only -- waterfalls on mt rugged iwa_01 (mm = 3)
			case 0x10: // sam_yamatif only -- icy cliffs around shiver mountain (mm = 3)
				break;
		}

		TileFormat fmt = TileFormat.get(type[IMG], depth[IMG]);

		// STEP 3: read texture maps from filebuffer
		switch (extra) {

			case 0: // MAIN ONLY
				main = new Tile(fmt, height[IMG], width[IMG]);
				main.readImage(bb, true);

				assert (height[AUX] == 0);
				assert (width[AUX] == 0);
				assert (type[AUX] == 0);
				assert (depth[AUX] == 0);
				assert (vWrap[AUX] == 0);
				assert (vWrap[AUX] == 0);

				if (main.format.type == TYPE_CI)
					main.readPalette(bb);
				break;

			case 1: // MAIN + MIPMAPS
				main = new Tile(fmt, height[IMG], width[IMG]);
				main.readImage(bb, true);

				assert (height[AUX] == 0);
				assert (width[AUX] == 0);
				assert (type[AUX] == 0);
				assert (depth[AUX] == 0);
				assert (vWrap[AUX] == 0);
				assert (vWrap[AUX] == 0);

				mipmapList = new LinkedList<>();

				int divisor = 2;
				if (width[IMG] >= (32 >> depth[IMG])) {
					while (true) {
						if (width[IMG] / divisor <= 0)
							break;

						int mmHeight = height[IMG] / divisor;
						int mmWidth = width[IMG] / divisor;

						Tile mipmap = new Tile(fmt, mmHeight, mmWidth);
						mipmap.readImage(bb, true);
						mipmapList.add(mipmap);

						divisor = divisor << 1;
						if (width[IMG] / divisor < (16 >> depth[IMG]))
							break;
					}
				}

				if (main.format.type == TYPE_CI) {
					int pos = bb.position();
					main.readPalette(bb);

					for (Tile mipmap : mipmapList) {
						bb.position(pos);
						mipmap.readPalette(bb);
					}
				}
				break;

			case 2: // MAIN + SHARED MASK -- use same format, half height for each
				assert (height[AUX] == 0);
				assert (width[AUX] == 0);

				height[IMG] = height[IMG] / 2;
				width[AUX] = width[IMG];
				height[AUX] = height[IMG];

				main = new Tile(fmt, height[IMG], width[IMG]);
				main.readImage(bb, true);

				aux = new Tile(fmt, height[AUX], width[AUX]);
				aux.readImage(bb, true);

				if (main.format.type == TYPE_CI) {
					int pos = bb.position();
					main.readPalette(bb);
					bb.position(pos);
					aux.readPalette(bb);
				}
				break;

			case 3: // MAIN + SEPARATE MASK -- two images have independent attributes
				main = new Tile(fmt, height[IMG], width[IMG]);
				main.readImage(bb, true);

				if (main.format.type == TYPE_CI)
					main.readPalette(bb);

				aux = new Tile(TileFormat.get(type[AUX], depth[AUX]), height[AUX], width[AUX]);
				aux.readImage(bb, true);

				if (aux.format.type == TYPE_CI)
					aux.readPalette(bb);
				break;

			default:
				throw new RuntimeException("Invalid setting for extra data.");
		}
	}

	private int[] splitByte(byte b)
	{
		int[] parts = new int[2];
		parts[AUX] = (b >> 4);
		parts[IMG] = b & 0xF;
		return parts;
	}

	public void write(ByteBuffer bb)
	{
		int start = bb.position();
		if (name.length() >= 0x20)
			throw new RuntimeException("Texture name is too long: " + name);
		bb.put(name.getBytes());

		bb.position(start + 0x20);

		bb.putShort((short) (extra == 3 ? aux.width : 0));
		bb.putShort((short) main.width);
		bb.putShort((short) (extra == 3 ? aux.height : 0));
		bb.putShort((short) (extra == 2 ? 2 * main.height : main.height));
		bb.put((byte) 0);
		bb.put((byte) extra);
		bb.put((byte) auxCombine);

		int fmt = main.format.type;
		if (extra == 3)
			fmt |= (aux.format.type << 4);
		bb.put((byte) fmt);

		int depth = main.format.depth;
		if (extra == 3)
			depth |= (aux.format.depth << 4);
		bb.put((byte) depth);

		bb.put((byte) ((hWrap[AUX] << 4) | hWrap[IMG]));
		bb.put((byte) ((vWrap[AUX] << 4) | vWrap[IMG]));
		bb.put((byte) (filter ? 2 : 0));

		main.putRaster(bb, true);

		Logger.log("Writing texture: " + name);

		switch (extra) {
			case 0:
				if (main.format.type == TYPE_CI)
					main.palette.put(bb);
				break;
			case 1: // MAIN + MIPMAPS
				for (Tile mm : mipmapList)
					mm.putRaster(bb, true);
				if (main.format.type == TYPE_CI)
					main.palette.put(bb);
				break;
			case 2: // MAIN + SHARED MASK -- use same format, half height for each
				aux.putRaster(bb, true);
				if (main.format.type == TYPE_CI)
					main.palette.put(bb);
				break;
			case 3: // MAIN + SEPARATE MASK -- two images have independent attributes
				if (main.format.type == TYPE_CI)
					main.palette.put(bb);
				aux.putRaster(bb, true);
				if (aux.format.type == TYPE_CI)
					aux.palette.put(bb);
				break;
		}
	}

	public int getFileSize()
	{
		int fileSize = 0x30 + main.raster.limit();
		switch (main.format) {
			case CI_4:
				fileSize += (2 * 16);
				break;
			case CI_8:
				fileSize += (2 * 256);
				break;
			default:
				break;
		}

		switch (extra) {
			case 1: // MAIN + MIPMAPS
				for (Tile mm : mipmapList)
					fileSize += mm.raster.limit();
				break;
			case 2: // MAIN + SHARED MASK -- use same format, half height for each
				fileSize += aux.raster.limit();
				break;
			case 3: // MAIN + SEPARATE MASK -- two images have independent attributes
				fileSize += aux.raster.limit();
				switch (aux.format) {
					case CI_4:
						fileSize += (2 * 16);
						break;
					case CI_8:
						fileSize += (2 * 256);
						break;
					default:
				}
				break;
		}

		return fileSize;
	}

	public void print(PrintWriter pw)
	{
		pw.println("tex: " + name);
		pw.println("{");

		pw.println("\timg: " + name + ".png");
		pw.println("\t{");
		pw.println("\t\tformat: " + main.format);
		pw.println("\t\thwrap: " + getWrapName(null, name, hWrap[IMG]));
		pw.println("\t\tvwrap: " + getWrapName(null, name, vWrap[IMG]));
		pw.println("\t}");

		switch (extra) {
			case 0:
				break;
			case 1: // MAIN + MIPMAPS
				pw.println("\t" + MIPMAP_KEY + ": " + YES);
				break;
			case 2: // MAIN + SHARED MASK -- use same format, half height for each
				pw.printf("\t%s: %s_AUX.png%n", AUX_KEY, name);
				pw.println("\t{");
				pw.println("\t\tformat: shared");
				pw.println("\t\thwrap: " + getWrapName(null, name, hWrap[AUX]));
				pw.println("\t\tvwrap: " + getWrapName(null, name, vWrap[AUX]));
				pw.println("\t}");
				break;
			case 3: // MAIN + SEPARATE MASK -- two images have independent attributes
				pw.printf("\t%s: %s_AUX.png%n", AUX_KEY, name);
				pw.println("\t{");
				pw.println("\t\tformat: " + aux.format);
				pw.println("\t\thwrap: " + getWrapName(null, name, hWrap[AUX]));
				pw.println("\t\tvwrap: " + getWrapName(null, name, vWrap[AUX]));
				pw.println("\t}");
				break;
		}

		pw.printf("\t%s: %s%n", FILTER_KEY, (filter ? YES : NO));
		pw.printf("\t%s: %X%n", COMBINE_KEY, auxCombine);

		pw.println("}");
		pw.println();
	}

	public static Texture parseTexture(File archiveFile, String dir, String name, List<String> lines) throws IOException
	{
		Texture tx = new Texture(name);
		String imgName = null;
		String auxName = null;
		String imgFormatName = null;
		String auxFormatName = null;
		tx.hWrap = new int[2];
		tx.vWrap = new int[2];
		boolean convertImg = false;
		boolean convertAux = false;
		ImageAttributes attr;

		Iterator<String> iter = lines.iterator();
		while (iter.hasNext()) {
			String line = iter.next();
			String[] tokens = splitLine(archiveFile, tx.name, line);

			switch (tokens[0]) {
				case IMG_KEY:
					imgName = tokens[1];
					if (!iter.hasNext())
						throw new InputFileException(archiveFile, "(%s) Incomplete texture description.", name);
					line = iter.next();
					if (!line.equals("{"))
						throw new InputFileException(archiveFile, "(%s) Invalid texture description.", name);
					List<String> imgLines = new LinkedList<>();
					while (!(line = iter.next()).equals("}")) {
						imgLines.add(line);
						if (!iter.hasNext())
							throw new InputFileException(archiveFile, "(%s) Incomplete texture description.", name);
					}
					attr = parseImage(archiveFile, tx, imgLines);
					imgFormatName = attr.format;
					if (attr.convert != null)
						convertImg = attr.convert.equals(YES);
					if (attr.hWrap != null)
						tx.hWrap[IMG] = getWrapMode(archiveFile, name, attr.hWrap);
					if (attr.vWrap != null)
						tx.vWrap[IMG] = getWrapMode(archiveFile, name, attr.vWrap);
					break;

				case AUX_KEY:
					auxName = tokens[1];
					if (!iter.hasNext())
						throw new InputFileException(archiveFile, "(%s) Incomplete texture description.", name);
					line = iter.next();
					if (!line.equals("{"))
						throw new InputFileException(archiveFile, "(%s) Invalid texture description.", name);
					List<String> auxLines = new LinkedList<>();
					while (!(line = iter.next()).equals("}")) {
						auxLines.add(line);
						if (!iter.hasNext())
							throw new InputFileException(archiveFile, "(%s) Incomplete texture description.", name);
					}
					attr = parseImage(archiveFile, tx, auxLines);
					auxFormatName = attr.format;
					if (attr.convert != null)
						convertAux = attr.convert.equals(YES);
					if (attr.hWrap != null)
						tx.hWrap[AUX] = getWrapMode(archiveFile, name, attr.hWrap);
					if (attr.vWrap != null)
						tx.vWrap[AUX] = getWrapMode(archiveFile, name, attr.vWrap);
					break;

				case MIPMAP_KEY:
					if (tokens[1].equals(YES)) {
						tx.hasMipmaps = true;
						tx.mipmapList = new LinkedList<>();
					}
					break;

				case FILTER_KEY:
					if (tokens[1].equals(YES))
						tx.filter = true;
					break;

				case COMBINE_KEY:
					tx.auxCombine = (byte) Short.parseShort(tokens[1], 16);
					break;
			}
		}

		if (imgFormatName == null)
			throw new InputFileException(archiveFile, "(%s) Texture does not specify an image.", name);

		TileFormat imgFormat = TileFormat.getFormat(imgFormatName);
		if (imgFormat == null)
			throw new InputFileException(archiveFile, "(%s) Unknown image format: %s", name, imgFormatName);

		TileFormat auxFormat = null;
		if (auxFormatName != null) {
			if (auxFormatName.equals("shared")) {
				tx.extra = 2;
				auxFormat = imgFormat;
			}
			else {
				tx.extra = 3;
				auxFormat = TileFormat.getFormat(auxFormatName);
				if (auxFormat == null)
					throw new InputFileException(archiveFile, "(%s) Unknown aux format: %s", name, auxFormatName);
			}

			tx.hasAux = true;
		}

		if (tx.hasMipmaps) {
			tx.extra = 1;
			if (tx.hasAux)
				throw new InputFileException(archiveFile, "(%s) Texture cannot have both mipmaps and aux.", name);
		}

		tx.main = Tile.load(dir + imgName, imgFormat, convertImg);

		if (tx.hasAux)
			tx.aux = Tile.load(dir + auxName, auxFormat, convertAux);

		if (tx.hasMipmaps) {
			int divisor = 2;
			if (tx.main.width >= (32 >> tx.main.format.depth)) {
				while (true) {
					if (tx.main.width / divisor <= 0)
						break;

					int mmHeight = tx.main.height / divisor;
					int mmWidth = tx.main.width / divisor;

					if (imgName.contains("."))
						imgName = imgName.substring(0, imgName.indexOf("."));

					String mmName = imgName + "_MIPMAP_" + (tx.mipmapList.size() + 1) + ".png";
					Tile mipmap = Tile.load(dir + mmName, imgFormat, convertImg);

					if (mipmap.height != mmHeight)
						throw new InputFileException(archiveFile, "%s has incorrect height: %s instead of %s", mmName, mipmap.height, mmHeight);

					if (mipmap.width != mmWidth)
						throw new InputFileException(archiveFile, "%s has incorrect width: %s instead of %s", mmName, mipmap.width, mmWidth);

					tx.mipmapList.add(mipmap);

					divisor = divisor << 1;
					if (tx.main.width / divisor < (16 >> tx.main.format.depth))
						break;
				}
			}
		}

		return tx;
	}

	private static int getWrapMode(File archiveFile, String texname, String wrap)
	{
		switch (wrap) {
			case "repeat":
				return 0;
			case "mirror":
				return 1;
			case "clamp":
				return 2;
			default:
				throw new InputFileException(archiveFile, "(%s) has invalid wrap mode: %s", texname, wrap);
		}
	}

	private static String getWrapName(File archiveFile, String texname, int id)
	{
		switch (id) {
			case 0:
				return "repeat";
			case 1:
				return "mirror";
			case 2:
				return "clamp";
			default:
				throw new InputFileException(archiveFile, "(%s) has invalid wrap mode: %s", texname, id);
		}
	}

	private static class ImageAttributes
	{
		public String format = null;
		public String convert = null;
		public String hWrap = null;
		public String vWrap = null;
	}

	private static ImageAttributes parseImage(File archiveFile, Texture tx, List<String> lines)
	{
		ImageAttributes attr = new ImageAttributes();

		for (String line : lines) {
			String[] tokens = splitLine(archiveFile, tx.name, line);
			switch (tokens[0]) {
				case "format":
					if (attr.format != null)
						throw new InputFileException(archiveFile, "Format specified more than once (%s)", tx.name);
					attr.format = tokens[1];
					break;
				case "convert":
					if (attr.convert != null)
						throw new InputFileException(archiveFile, "Convert specified more than once (%s)", tx.name);
					attr.convert = tokens[1];
					break;
				case "hwrap":
					if (attr.hWrap != null)
						throw new InputFileException(archiveFile, "hWrap specified more than once (%s)", tx.name);
					attr.hWrap = tokens[1];
					break;
				case "vwrap":
					if (attr.vWrap != null)
						throw new InputFileException(archiveFile, "vWrap specified more than once (%s)", tx.name);
					attr.vWrap = tokens[1];
					break;
			}
		}

		if (attr.format == null)
			throw new InputFileException(archiveFile, "Format was not specified (%s)", tx.name);

		return attr;
	}

	private static String[] splitLine(File archiveFile, String texName, String line)
	{
		String[] tokens = line.split(":\\s*");
		if (tokens.length != 2)
			throw new InputFileException(archiveFile, "Invalid line in texture file: %s (%s)", line, texName);
		return tokens;
	}

	public static float getScale(int shift)
	{
		switch (shift) {
			default:
				return 1.0f;
			case 1:
				return 1.0f / 2.0f;
			case 2:
				return 1.0f / 4.0f;
			case 3:
				return 1.0f / 8.0f;
			case 4:
				return 1.0f / 16.0f;
			case 5:
				return 1.0f / 32.0f;
			case 6:
				return 1.0f / 64.0f;
			case 7:
				return 1.0f / 128.0f;
			case 8:
				return 1.0f / 256.0f;
			case 9:
				return 1.0f / 512.0f;
			case 10:
				return 1.0f / 1024.0f;
			case 11:
				return 32.0f;
			case 12:
				return 16.0f;
			case 13:
				return 8.0f;
			case 14:
				return 4.0f;
			case 15:
				return 2.0f;
		}
	}
}
