package game.texture;

import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

import app.Directories;
import app.Environment;
import app.input.IOUtils;
import util.Logger;
import util.Priority;

public class AsepriteImporter
{
	public static void main(String[] args) throws IOException
	{
		Environment.initialize();
		Logger.setDefaultOuputPriority(Priority.DETAIL);
		new AsepriteImporter(new AsepriteImportSettings(
			new File(Directories.getModPath() + "/sample.aseprite"),
			TileFormat.CI_4));
		Environment.exit();
	}

	public static class AsepriteImportSettings
	{
		public final File file;
		public final TileFormat fmt;

		public AsepriteImportSettings(File file, TileFormat fmt)
		{
			this.file = file;
			this.fmt = fmt;
		}
	}

	public static class ImportedTile
	{
		public final Tile tile;
		public final String name;

		public ImportedTile(Tile tile, String name)
		{
			this.tile = tile;
			this.name = name;
		}
	}

	private final AsepriteImportSettings settings;

	private int paletteSize;
	private int transPalIndex;
	private Color[] colors;

	private final int bpp;
	private final int sizeX;
	private final int sizeY;

	private List<AseLayer> layers = new ArrayList<>();
	private Set<String> layerNames = new HashSet<>();

	private List<ImportedTile> tiles = new ArrayList<>();

	public AsepriteImporter(AsepriteImportSettings settings) throws ImportException
	{
		this.settings = settings;
		ByteBuffer bb;
		try {
			bb = IOUtils.getDirectBuffer(settings.file);
		}
		catch (IOException e) {
			Logger.printStackTrace(e);
			throw new ImportException(e.getMessage());
		}

		bb.position(4);
		int magicNumber = bb.getShort() & 0xFFFF;

		if (magicNumber == 0xE0A5) {
			bb.order(ByteOrder.LITTLE_ENDIAN);
			bb.position(4);
			magicNumber = bb.getShort() & 0xFFFF;
		}

		if (magicNumber != 0xA5E0)
			throw new ImportException("File could not be opened as an aseprite image file.");

		bb.position(0);
		int fileSize = bb.getInt();
		bb.getShort(); // magic number
		int numFrames = bb.getShort() & 0xFFFF;
		sizeX = bb.getShort() & 0xFFFF;
		sizeY = bb.getShort() & 0xFFFF;
		bpp = bb.getShort() & 0xFFFF;
		int flags = bb.getInt();
		int speed = bb.getShort() & 0xFFFF;
		bb.getInt(); // zero
		bb.getInt(); // zero
		transPalIndex = bb.get();
		bb.get(); // skip
		bb.get(); // skip
		bb.get(); // skip
		int numColors = bb.getShort() & 0xFFFF;
		byte pixelW = bb.get();
		byte pixelH = bb.get();
		bb.position(128); // rest of file header is blank

		if (settings.fmt.type == TileFormat.TYPE_CI) {
			if (bpp != 8)
				throw new ImportException("File is not color-indexed.");

			if (settings.fmt == TileFormat.CI_4)
				paletteSize = 16;
			else if (settings.fmt == TileFormat.CI_8)
				paletteSize = 256;

			if (numColors != paletteSize)
				throw new ImportException("Palette mismatch, expected %d colors, found %d", paletteSize, numColors);

			colors = new Color[paletteSize];
		}

		/*
		DWORD       Bytes in this frame
		WORD        Magic number (always 0xF1FA)
		WORD        Old field which specifies the number of "chunks"
		            in this frame. If this value is 0xFFFF, we might
		            have more chunks to read in this frame
		            (so we have to use the new field)
		WORD        Frame duration (in milliseconds)
		BYTE[2]     For future (set to zero)
		DWORD       New field which specifies the number of "chunks"
		            in this frame (if this is 0, use the old field)
		 */
		for (int f = 0; f < numFrames; f++) {
			AseFrame frame = new AseFrame(bb);

			if (frame.magicNumber != 0xF1FA)
				throw new ImportException("Could not read frame %d of %d", (f + 1), numFrames);

			Logger.logfDetail("Reading frame %d / %d :: %d chunks :: %X bytes",
				(f + 1), numFrames, frame.numChunks, frame.frameSize);

			for (int c = 0; c < frame.numChunks; c++)
				readChunk(frame, bb);

			for (AseCel cel : frame.cels) {
				AseLayer layer = layers.get(cel.layerIndex);
				if ((layer.flags & 64) != 0) // reference layer
					continue;
				if ((layer.flags & 8) != 0) // background layer
					continue;
				if (layer.type != 0) // non-image layer
					continue;

				String name;
				if (layer.validName.isEmpty())
					name = String.format("Frame_%02X_Layer_%02X", f, cel.layerIndex);
				else
					name = String.format("Frame_%02X_%s", f, layer.validName);

				if (settings.fmt.type == TileFormat.TYPE_CI)
					tiles.add(new ImportedTile(convertCI(cel), name));

				/*
				try {
					tile.savePNG(Directories.MOD_OUT + name);
				} catch (IOException e) {
					Logger.printStackTrace(e);
					throw new ImportException(e.getMessage());
				}
				 */
			}
		}
	}

	public List<ImportedTile> getTiles()
	{
		return tiles;
	}

	private void readChunk(AseFrame frame, ByteBuffer bb)
	{
		int chunkStart = bb.position();
		int chunkSize = bb.getInt();
		int chunkType = bb.getShort() & 0xFFFF;

		switch (chunkType) {
			case 0x2004: // layer
				readLayer(bb, chunkSize - 0x6);
				break;
			case 0x2005: // cel
				AseCel cel = readCel(bb, chunkSize - 0x6);
				frame.cels.add(cel);
				break;
			case 0x2019: // palette
				readPalette(bb, chunkSize - 0x6);
				break;
			case 0x0004: // oldpalette
			case 0x2007: // color profile, irrelavant for CI
			default:
				Logger.logfDetail("Skipping chunk %X", chunkType);
				bb.position(chunkStart + chunkSize);
				break;
		}
	}

	private void readPalette(ByteBuffer bb, int len)
	{
		Logger.logfDetail("Reading palette chunk");

		int numColors = bb.getInt();
		int start = bb.getInt();
		int end = bb.getInt();
		bb.getInt(); // skip
		bb.getInt(); // skip

		Logger.logfDetail("%d colors from %d to %d", numColors, start, end);
		for (int i = start; i <= end; i++) {
			int flags = bb.getShort() & 0xFFFF;
			colors[i] = new Color(bb.get() & 0xFF, bb.get() & 0xFF, bb.get() & 0xFF, bb.get() & 0xFF);

			if ((flags & 1) != 0) // hasName
				readString(bb); // color name
		}

		if (numColors != paletteSize)
			throw new ImportException("Palette mismatch, expected %d colors, found %d", paletteSize, numColors);
	}

	private void readLayer(ByteBuffer bb, int len)
	{
		Logger.logfDetail("Reading layer chunk");
		AseLayer layer = new AseLayer(bb, layers.size());
		layers.add(layer);
	}

	private AseCel readCel(ByteBuffer bb, int len)
	{
		Logger.logfDetail("Reading cel chunk");
		return new AseCel(bb, len);
	}

	private static String readString(ByteBuffer bb)
	{
		int len = bb.getShort() & 0xFFFF;
		byte[] bytes = new byte[len];
		bb.get(bytes);
		return new String(bytes);
	}

	private static class AseFrame
	{
		public final List<AseCel> cels;
		public final int frameSize;
		public final int numChunks;
		public final int duration;
		public final int magicNumber;

		private AseFrame(ByteBuffer bb)
		{
			cels = new ArrayList<>();

			frameSize = bb.getInt();
			magicNumber = bb.getShort() & 0xFFFF;
			int oldChunks = bb.getShort() & 0xFFFF;
			duration = bb.getShort() & 0xFFFF;
			bb.get(); // zero
			bb.get(); // zero
			int newChunks = bb.getInt();

			assert (oldChunks == newChunks);

			numChunks = (oldChunks == -1) ? newChunks : oldChunks;
		}
	}

	private class AseCel
	{
		public final int layerIndex;
		public final short posX;
		public final short posY;
		public final byte opacity;
		public final int type;

		public int width;
		public int height;
		public byte[] pixels;

		private AseCel(ByteBuffer bb, int len)
		{
			layerIndex = bb.getShort() & 0xFFFF;
			posX = bb.getShort();
			posY = bb.getShort();
			opacity = bb.get();
			type = bb.getShort() & 0xFFFF;
			bb.getInt(); // reserved
			bb.get(); // reserved
			bb.get(); // reserved
			bb.get(); // reserved
			if (type == 0)
				readRaw(bb);
			else if (type == 2)
				readCompressed(bb, len - 16);
			else
				throw new ImportException("Linked cels are not supported.");
		}

		private void readRaw(ByteBuffer bb)
		{
			width = bb.getShort();
			height = bb.getShort();

			pixels = new byte[width * height * (bpp / 8)];
			bb.get(pixels);
		}

		private void readCompressed(ByteBuffer bb, int len)
		{
			width = bb.getShort();
			height = bb.getShort();
			byte[] compressed = new byte[len - 4];
			bb.get(compressed);

			Inflater decompresser = new Inflater();
			decompresser.setInput(compressed);//, 0, compressed.length);

			int bufferSize = 4096;
			int numBytes = 0;
			ArrayList<byte[]> chunks = new ArrayList<>();

			try {
				while (!decompresser.finished()) {
					byte[] buffer = new byte[bufferSize];
					numBytes += decompresser.inflate(buffer);
					chunks.add(buffer);
				}
			}
			catch (DataFormatException e) {
				throw new ImportException("Error while decompressing image!");
			}
			finally {
				decompresser.end();
			}

			assert (numBytes == width * height * (bpp / 8));

			ByteBuffer decompressed = ByteBuffer.allocateDirect(numBytes);
			for (byte[] chunk : chunks) {
				if (numBytes > bufferSize)
					decompressed.put(chunk);
				else
					decompressed.put(chunk, 0, numBytes);

				numBytes -= bufferSize;
			}

			pixels = new byte[width * height * (bpp / 8)];
			decompressed.flip();
			decompressed.get(pixels);
		}
	}

	private class AseLayer
	{
		public final int index;
		public final int childLevel;
		public final int flags;
		public final int type;
		public final int blendMode;
		public final String name;

		public String validName;

		private AseLayer(ByteBuffer bb, int index)
		{
			this.index = index;
			flags = bb.getShort() & 0xFFFF;
			type = bb.getShort() & 0xFFFF;
			childLevel = bb.getShort() & 0xFFFF;
			bb.getShort(); // default width
			bb.getShort(); // default height
			blendMode = bb.getShort() & 0xFFFF;
			bb.get(); // blend mode -- skip this
			bb.get(); // reserved
			bb.get(); // reserved
			bb.get(); // reserved
			name = readString(bb);

			validName = name.trim().replaceAll("\\s+", "_").replaceAll("[^\\w\\-]", "");

			if (!name.isEmpty() && !validName.isEmpty()) {
				int i = 0;
				while (layerNames.contains(validName))
					validName = String.format("%s_%d", name, i++);
				layerNames.add(validName);
			}
		}
	}

	public class ImportException extends RuntimeException
	{
		public ImportException(String fmt, Object ... args)
		{
			super(settings.file.toString() + "\n " + String.format(fmt, args));
		}
	}

	private Tile convertCI(AseCel cel)
	{
		Palette pal = new Palette(colors);

		// pad to multiple of 8 sizes
		int padW = (8 - (cel.width % 8)) % 8;
		int padH = (8 - (cel.height % 8)) % 8;
		int halfPadW = padW / 2;
		int halfPadH = padH / 2;

		Tile tile = new Tile(settings.fmt, cel.height + padH, cel.width + padW);
		tile.palette = pal;
		tile.raster.rewind();

		byte[] padded = new byte[(cel.height + padH) * (cel.width + padW)];

		int k = 0;
		int n = 0;
		for (int i = 0; i < (cel.height + padH); i++) {
			for (int j = 0; j < (cel.width + padW); j++) {
				// padding rows
				if ((i < halfPadH) || (i >= cel.height + halfPadH)) {
					padded[k++] = (byte) transPalIndex;
					continue;
				}

				// pad columns
				if ((j < halfPadW) || (j >= cel.width + halfPadW)) {
					padded[k++] = (byte) transPalIndex;
					continue;
				}

				padded[k++] = cel.pixels[n++];
			}
		}

		for (int i = 0; i < padded.length;) {
			byte b1 = padded[i++];
			byte b2 = padded[i++];

			if (settings.fmt == TileFormat.CI_4) {
				// pack indicies for CI_4
				tile.raster.put((byte) ((b1 << 4) | (b2 & 0xF)));
			}
			else if (settings.fmt == TileFormat.CI_8) {
				tile.raster.put(b1);
				tile.raster.put(b2);
			}
			else
				throw new IllegalStateException("Cannot write " + settings.fmt + " file as color-indexed!");
		}

		return tile;
	}
}
