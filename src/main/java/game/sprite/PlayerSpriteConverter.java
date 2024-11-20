package game.sprite;

import static app.Directories.*;
import static game.texture.TileFormat.CI_4;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map.Entry;
import java.util.TreeMap;

import org.apache.commons.io.FileUtils;

import app.Directories;
import app.Environment;
import app.input.IOUtils;
import game.sprite.SpriteLoader.SpriteSet;
import game.texture.Palette;
import game.texture.Tile;
import util.Logger;
import util.Priority;
import util.xml.XmlWrapper.XmlWriter;

public class PlayerSpriteConverter
{
	private static final int UNKNOWN_RASTER = 0x1F880;

	public static void main(String args[]) throws IOException
	{
		Environment.initialize();
		new PlayerSpriteConverter();
		Environment.exit();
	}

	// byte[] rasterBytes = new byte[0x19E0970 - 0x1943020];

	private final TreeMap<Integer, RasterTableEntry> rasters;
	private final List<SpriteListEntry> sections;
	private final ByteBuffer rastersBuffer;

	public PlayerSpriteConverter() throws IOException
	{
		rastersBuffer = IOUtils.getDirectBuffer(new File(DUMP_SPR_PLR_RAW + "rasters"));
		rasters = new TreeMap<>();
		sections = new ArrayList<>(13);

		readSpriteSections();
		readRasterTable();

		readSprites();

		dumpShared();
		dumpSprites();
	}

	private void readSprites() throws IOException
	{
		for (int i = 0; i < sections.size(); i++) {
			String spriteName = String.format("%02X", (i + 1));
			File source = new File(DUMP_SPR_PLR_RAW + spriteName);

			Logger.log("Converting sprite " + spriteName + ".", Priority.MILESTONE);
			readBinaryPlayer(source, sections.get(i));
		}
	}

	private void dumpShared() throws IOException
	{
		FileUtils.forceMkdir(DUMP_SPR_PLR_SHARED.toFile());

		for (Entry<Integer, RasterTableEntry> e : rasters.entrySet()) {
			int offset = e.getKey();
			if (offset == UNKNOWN_RASTER)
				continue;

			RasterTableEntry pr = e.getValue();
			pr.dumpPNG(rastersBuffer, DUMP_SPR_PLR_SHARED);
		}
	}

	private void dumpSprites() throws IOException
	{
		for (int i = 0; i < sections.size(); i++) {
			String spriteName = String.format("%02X", (i + 1));
			String spriteDir = DUMP_SPR_PLR_SRC + spriteName + "/";
			File xmlFile = new File(spriteDir + "SpriteSheet.xml");

			FileUtils.forceMkdir(new File(spriteDir));

			Sprite spr = sections.get(i).sprite;

			for (int j = 0; j < spr.rasters.size(); j++) {
				SpriteRaster sr = spr.rasters.get(j);

				if (sr.isSpecial)
					continue;

				sr.img = sr.tableEntry.img;
				sr.filename = sr.tableEntry.filename;
			}

			File refDir = new File(spriteDir + "reference/");
			FileUtils.forceMkdir(refDir);
			spr.dumpRasters(refDir, false);
			spr.dumpPalettes(new File(spriteDir));

			XmlWriter xmw = new XmlWriter(xmlFile);
			spr.toXML(xmw);
			xmw.save();
		}
	}

	private void readSpriteSections()
	{
		rastersBuffer.position(0x10);
		int[] offsets = new int[14];
		for (int i = 0; i <= 13; i++)
			offsets[i] = rastersBuffer.getInt();

		for (int i = 0; i < 13; i++)
			sections.add(new SpriteListEntry(offsets[i], offsets[i + 1] - offsets[i]));
	}

	private void readRasterTable()
	{
		rastersBuffer.position(0x48);

		int currentSectionID = 0;
		int currentSectionPos = 0;
		SpriteListEntry currentSection = sections.get(currentSectionID);

		// last raster is not legitimate
		for (int i = 0; i < 0x2CC; i++) {
			int v = rastersBuffer.getInt();
			int size = (v >>> 20) << 4;
			int offset = v & 0xFFFFF;

			RasterTableEntry pr = rasters.get(offset);

			if (pr == null) {
				pr = new RasterTableEntry(offset, size);
				rasters.put(offset, pr);
			}
			else
				assert (pr.size == size);

			pr.count++;

			// add raster to sprite
			currentSection.add(pr);

			if (++currentSectionPos == currentSection.count) {
				currentSectionID++;
				currentSection = (currentSectionID < 13) ? sections.get(currentSectionID) : null;
				currentSectionPos = 0;
			}
		}

		/*
		for(Entry<Integer,RasterTableEntry> e : rasters.entrySet())
		{
			int offset = e.getKey();
			RasterTableEntry pr = e.getValue();
			System.out.printf("%05X -- %05X %d%n", offset, offset + pr.size, pr.count);
		}
		*/
	}

	private void readBinaryPlayer(File binFile, SpriteListEntry spriteSection) throws IOException
	{
		ByteBuffer bb = IOUtils.getDirectBuffer(binFile);
		Sprite sprite = new Sprite(SpriteSet.Player);

		// header and offset lists
		int imageListOffset = bb.getInt();
		int paletteListOffset = bb.getInt();
		sprite.maxComponents = bb.getInt();
		sprite.numVariations = bb.getInt();
		List<Integer> animationOffsets = readOffsetList(bb);

		bb.position(imageListOffset);
		List<Integer> imageOffsetList = readOffsetList(bb);

		bb.position(paletteListOffset);
		List<Integer> paletteOffsetList = readOffsetList(bb);

		List<Palette> paletteList = new LinkedList<>();

		// read palettes
		for (int i = 0; i < paletteOffsetList.size(); i++) {
			bb.position(paletteOffsetList.get(i));

			short[] colors = new short[16];
			for (int j = 0; j < 16; j++)
				colors[j] = bb.getShort();

			Palette pal = new Palette(colors);
			paletteList.add(pal);
			sprite.palettes.addElement(new SpritePalette(pal));
		}

		assert (imageOffsetList.size() == spriteSection.rasterOffsets.size());

		// read images
		for (int i = 0; i < imageOffsetList.size(); i++) {
			//System.out.printf("%X %X%n", i, imageOffsetList.get(i));
			bb.position(imageOffsetList.get(i));
			int relativeOffset = bb.getInt(); // loaded offset don't worry about it
			int width = bb.get();
			int height = bb.get();
			int palID = bb.get();
			int unknown = bb.get();
			assert (unknown == -1);

			assert (relativeOffset == spriteSection.rasterPositions.get(i)) : String.format("%X %X %X", relativeOffset,
				spriteSection.rasterPositions.get(i),
				spriteSection.rasterOffsets.get(i));

			RasterTableEntry pr = spriteSection.rasters.get(i);

			SpriteRaster sr = new SpriteRaster();

			if (pr.offset == UNKNOWN_RASTER) {
				// special -- what is it? nobody knows! -- related to back sprites though, might indiciate 'use same as front'.
				// 80300210 00000200 00000001 00100000
				sr.isSpecial = true;
				sr.specialWidth = width;
				sr.specialHeight = height;
				//System.out.printf("%X %02X x %02X%n", pr.offset, (byte)width, (byte)height);
			}
			else {
				assert (width * height / 2 == pr.size) : String.format("%X vs %X", width * height / 2, pr.size);

				pr.setSize(width, height);
				pr.setPalette(paletteList.get(palID));

				sr.defaultPal = sprite.palettes.get(palID);
				sr.tableEntry = pr;

				// DONT SET sr.img = new Tile(CI_4, height, width);
			}

			sprite.rasters.addElement(sr);
		}

		// read animations
		for (int animOffset : animationOffsets) {
			SpriteAnimation anim = new SpriteAnimation(sprite);

			bb.position(animOffset);
			List<Integer> componentOffsets = readOffsetList(bb);

			for (int compOffset : componentOffsets) {
				SpriteComponent comp = new SpriteComponent(anim);
				comp.rawAnim = new RawAnimation();

				bb.position(compOffset);
				int sequenceOffset = bb.getInt();
				short sequenceLength = bb.getShort();
				comp.posx = bb.getShort();
				comp.posy = bb.getShort();
				comp.posz = bb.getShort();

				bb.position(sequenceOffset);
				for (int i = 0; i < sequenceLength / 2; i++) {
					short cmd = bb.getShort();
					comp.rawAnim.add(cmd);
				}

				anim.components.addElement(comp);
			}

			sprite.animations.addElement(anim);
		}

		sprite.recalculateIndices();
		sprite.assignDefaultAnimationNames();

		spriteSection.sprite = sprite;
	}

	private static List<Integer> readOffsetList(ByteBuffer bb)
	{
		int v;
		List<Integer> offsetList = new LinkedList<>();
		while ((v = bb.getInt()) != -1)
			offsetList.add(v);
		return offsetList;
	}

	private static class SpriteListEntry
	{
		public final int start;
		public final int count;

		private int loadedPosition;
		public List<Integer> rasterOffsets;
		public List<Integer> rasterPositions;
		public List<RasterTableEntry> rasters;

		public Sprite sprite;

		public SpriteListEntry(int start, int count)
		{
			this.start = start;
			this.count = count;
			loadedPosition = 0;
			rasterOffsets = new LinkedList<>();
			rasterPositions = new LinkedList<>();
			rasters = new LinkedList<>();
		}

		public void add(RasterTableEntry raster)
		{
			rasters.add(raster);
			rasterOffsets.add(raster.offset);
			rasterPositions.add(loadedPosition);
			loadedPosition += raster.size;
		}
	}

	public static class RasterTableEntry implements Comparable<RasterTableEntry>
	{
		public final int offset;
		public final int size;
		private int count;

		private boolean sizeSet = false;
		private int height;
		private int width;

		public Tile img = null;

		// used for dumping
		private final String filename;
		private boolean setPalette = false;
		private Palette pal;

		public RasterTableEntry(int offset, int size)
		{
			this.offset = offset;
			this.size = size;
			count = 0;

			filename = String.format("%05X.png", offset);
		}

		public void setSize(int width, int height)
		{
			if (sizeSet) {
				String msg = String.format("%d x %d vs %d x %d", this.width, this.height, width, height);
				assert (width == this.width) : msg;
				assert (height == this.height) : msg;
			}

			this.width = width;
			this.height = height;
			sizeSet = true;
		}

		public void setPalette(Palette pal)
		{
			if (setPalette)
				return;

			this.pal = pal;
			setPalette = true;
		}

		public void dumpPNG(ByteBuffer rastersBuffer, Directories dir) throws IOException
		{
			img = new Tile(CI_4, height, width);
			img.readImage(rastersBuffer, offset, false);
			img.palette = pal;
			img.savePNG(dir + filename);
		}

		/*
		public void incrementCount()
		{
			count++;
		}
		
		public int getCount()
		{
			return count;
		}
		*/

		@Override
		public boolean equals(Object obj)
		{
			if (obj == this)
				return true;

			if (!(obj instanceof RasterTableEntry other))
				return false;

			return offset == other.offset && size == other.size;
		}

		@Override
		public int hashCode()
		{
			return offset;
		}

		@Override
		public int compareTo(RasterTableEntry other)
		{
			return offset - other.offset;
		}
	}
}
