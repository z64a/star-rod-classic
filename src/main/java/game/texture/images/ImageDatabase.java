package game.texture.images;

import static app.Directories.*;
import static game.texture.TileFormat.CI_4;
import static game.texture.TileFormat.CI_8;
import static game.texture.images.ImageAssetKey.*;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.w3c.dom.Element;

import app.Environment;
import app.input.InputFileException;
import game.shared.ProjectDatabase;
import game.texture.Tile;
import game.texture.TileFormat;
import patcher.RomPatcher;
import util.Logger;
import util.xml.XmlWrapper.XmlReader;
import util.xml.XmlWrapper.XmlTag;
import util.xml.XmlWrapper.XmlWriter;

public class ImageDatabase
{
	public static final int NO_DATA = -1;

	public static void main(String[] args) throws IOException
	{
		Environment.initialize();

		ImageDatabase imgDB = new ImageDatabase();
		imgDB.loadFromDatabase();
		imgDB.dumpAll();

		Environment.exit();
	}

	// maps a unique pair of offsets to a unique asset name
	public static class DecodedImageAsset
	{
		public final int imgOffset;
		public final int palOffset;

		public final String name;

		public DecodedImageAsset(int imgOffset, int palOffset, String name)
		{
			this.imgOffset = imgOffset;
			this.palOffset = palOffset;
			this.name = name;
		}
	}

	// maps a unique asset name to a unique pair of offsets or addresses
	// each alt palette for an ImageRecord gets its own EncodedImageAsset
	public static class EncodedImageAsset
	{
		public final String name;
		public final TileFormat fmt;

		public int outImgAddress = NO_DATA;
		public int outPalAddress = NO_DATA;

		public int outImgOffset = NO_DATA;
		public int outPalOffset = NO_DATA;

		public EncodedImageAsset(String name, TileFormat fmt)
		{
			this.name = name;
			this.fmt = fmt;
		}
	}

	private final List<ImageRecord> images;

	private final LinkedHashMap<Long, DecodedImageAsset> dumpingMap;
	private final LinkedHashMap<String, EncodedImageAsset> patchingMap;

	public ImageDatabase()
	{
		images = new ArrayList<>();
		dumpingMap = new LinkedHashMap<>();
		patchingMap = new LinkedHashMap<>();
	}

	public void loadFromDatabase() throws IOException
	{
		load(readXML(new File(DATABASE + FN_IMAGE_ASSETS)));
	}

	public void loadFromProject() throws IOException
	{
		load(readXML(new File(MOD_IMG + FN_IMAGE_ASSETS)));
	}

	public void load(Iterable<ImageRecord> imageList) throws IOException
	{
		images.clear();
		for (ImageRecord image : imageList)
			images.add(image);

		for (ImageRecord rec : images) {
			if (rec.imgOffset > 0) {
				Integer imgAddr = ProjectDatabase.rom.getAddress(rec.imgOffset);
				rec.imgAddress = (imgAddr == null) ? NO_DATA : imgAddr;
			}

			if (rec.palOffset > 0) {
				Integer palAddr = ProjectDatabase.rom.getAddress(rec.palOffset);
				rec.palAddress = (palAddr == null) ? NO_DATA : palAddr;
			}
		}
	}

	public EncodedImageAsset getImage(String name)
	{
		return patchingMap.get(name);
	}

	public DecodedImageAsset getImage(int imgOffset)
	{
		return dumpingMap.get(packOffsets(imgOffset, 0));
	}

	public DecodedImageAsset getImage(int imgOffset, int palOffset)
	{
		return dumpingMap.get(packOffsets(imgOffset, palOffset));
	}

	private static long packOffsets(int imgOffset, int palOffset)
	{
		return (imgOffset & 0xFFFFFFFFL) | ((palOffset & 0xFFFFFFFFL) << 32);
	}

	public void dumpAll() throws IOException
	{
		RandomAccessFile raf = Environment.getBaseRomReader();
		dumpingMap.clear();

		for (ImageRecord rec : images) {
			Logger.log("Dumping " + rec.identifier);
			Tile img = new Tile(rec.fmt, rec.sizeH, rec.sizeW);
			img.readImage(raf, rec.imgOffset, rec.flip);

			if (rec.fmt == CI_4 || rec.fmt == CI_8) {
				int palSize = (rec.fmt == CI_4) ? 0x20 : 0x200;

				img.readPalette(raf, rec.palOffset);
				dumpImageAsset(img, rec.imgOffset, rec.palOffset, rec.identifier);

				if (rec.palCount == 2) {
					img.readPalette(raf, rec.palOffset + palSize);
					dumpImageAsset(img, rec.imgOffset, rec.palOffset + palSize, rec.identifier + "_alt");
				}
				else if (rec.palCount > 2) {
					for (int i = 1; i < rec.palCount; i++) {
						img.readPalette(raf, rec.palOffset + i * palSize);
						dumpImageAsset(img, rec.imgOffset, rec.palOffset + i * palSize, rec.identifier + "_alt" + i);
					}
				}
			}
			else
				dumpImageAsset(img, rec.imgOffset, 0, rec.identifier);
		}

		raf.close();

		File dbFile = new File(DATABASE + FN_IMAGE_ASSETS);
		File dumpFile = new File(DUMP_IMG + FN_IMAGE_ASSETS);
		FileUtils.copyFile(dbFile, dumpFile);
	}

	private void dumpImageAsset(Tile img, int imgOffset, int palOffset, String name) throws IOException
	{
		String filename = DUMP_IMG_ASSETS + name + ".png";
		FileUtils.touch(new File(filename));
		img.savePNG(filename);

		dumpingMap.put(packOffsets(imgOffset, palOffset), new DecodedImageAsset(imgOffset, palOffset, name));
	}

	public void loadAllImageTiles() throws IOException
	{
		long t0 = System.nanoTime();

		for (ImageRecord rec : images)
			rec.loadTiles();

		long t1 = System.nanoTime();
		Logger.logf("Loaded %d image assets in %3.1f ms.", images.size(), (t1 - t0) * 1e-6);
	}

	// speed test: we can load 1082 images in 550ms and write them to the rom buffer with +30ms
	public void patchImages(RomPatcher rp) throws IOException
	{
		long t0 = System.nanoTime();
		int rasterCount = 0;
		int paletteCount = 0;

		patchingMap.clear();
		HashMap<Integer, EncodedImageAsset> writtenRasters = new HashMap<>();
		HashMap<Integer, EncodedImageAsset> writtenPalettes = new HashMap<>();

		/*
		 * Each image record connects a series of image files in the project directory to a pair of
		 * offsets on the ROM. The same image may be written to multiple offsets, and a particular
		 * offset may be connected to multiple files.
		 *
		 * The series of image files is denoted either X (trivial); X, X_alt (dual); or X, X_alt1,
		 *  X_alt2, ..., X_altN (multiple) and results in a palette for each being written consecutively.
		 *
		 * We only write the FIRST occurance of each offset.
		 */

		// at the moment, we force any image larger than the defaut to move to the tail of the ROM
		// and leave the original-sized version alone
		for (ImageRecord rec : images) {
			EncodedImageAsset baseAsset = new EncodedImageAsset(rec.source[0], rec.fmt);
			patchingMap.put(rec.source[0], baseAsset);

			boolean fixedImg = rec.hasRaster && rec.imgOffset > 0;
			boolean fixedPal = rec.hasRaster && rec.palOffset > 0;

			if (rec.hasRaster) {
				if (!fixedImg || (rec.tile[0].width * rec.tile[0].height > rec.sizeW * rec.sizeH)) {
					// vanilla does not exist OR size exceeds original -- append to end of ROM
					baseAsset.outImgOffset = rp.nextAlignedOffset();
					baseAsset.outImgAddress = rp.toAddress(baseAsset.outImgOffset);
				}
				else {
					// overwrite vanilla
					baseAsset.outImgOffset = rec.imgOffset;
					baseAsset.outImgAddress = rec.imgAddress;
				}

				if (baseAsset.outImgOffset < 0)
					throw new IllegalStateException("Negative img offset for " + rec.identifier);

				// only write to a given offset once
				if (!fixedImg || !writtenRasters.containsKey(rec.imgOffset)) {
					if (fixedImg)
						writtenRasters.put(rec.imgOffset, baseAsset);
					rp.seek(rec.identifier + " (IMG)", baseAsset.outImgOffset);
					rec.tile[0].writeRaster(rp, rec.flip);
					rasterCount++;
				}
				else {
					// this image raster has already been written, use existing properties
					EncodedImageAsset matchRec = writtenRasters.get(rec.imgOffset);
					baseAsset.outImgOffset = matchRec.outImgOffset;
					baseAsset.outImgAddress = matchRec.outImgAddress;
				}
			}

			if (rec.fmt.type == TileFormat.TYPE_CI) {
				if (!fixedPal) {
					baseAsset.outPalOffset = rp.nextAlignedOffset();
					baseAsset.outPalAddress = rp.toAddress(baseAsset.outPalOffset);
				}
				else {
					baseAsset.outPalOffset = rec.palOffset;
					baseAsset.outPalAddress = rec.palAddress;
				}

				if (baseAsset.outPalOffset < 0)
					throw new IllegalStateException("Negative pal offset for " + rec.identifier);

				// only write to a given offset once
				if (!fixedPal || !writtenPalettes.containsKey(rec.palOffset)) {
					if (fixedPal)
						writtenPalettes.put(rec.palOffset, baseAsset);

					rp.seek(rec.identifier + " (PAL)", baseAsset.outPalOffset);
					rec.tile[0].palette.write(rp);

					int size = (rec.fmt == TileFormat.CI_4) ? 0x20 : 0x200;
					for (int i = 1; i < rec.palCount; i++) {
						EncodedImageAsset ithAsset = new EncodedImageAsset(rec.source[i], rec.fmt);
						patchingMap.put(rec.source[i], ithAsset);

						ithAsset.outImgOffset = baseAsset.outImgOffset;
						ithAsset.outImgAddress = baseAsset.outImgAddress;

						ithAsset.outPalOffset = baseAsset.outPalOffset + size * i;
						ithAsset.outPalAddress = baseAsset.outPalAddress + size * i;

						rec.tile[i].palette.write(rp);
					}

					paletteCount += rec.palCount;
				}
				else {
					// this image raster has already been written, use existing properties
					EncodedImageAsset matchRec = writtenPalettes.get(rec.palOffset);
					baseAsset.outPalOffset = matchRec.outPalOffset;
					baseAsset.outPalAddress = matchRec.outPalAddress;
				}
			}
		}

		long t1 = System.nanoTime();
		Logger.logf("Wrote %d image assets and %d palettes in %3.1f ms.", rasterCount, paletteCount, (t1 - t0) * 1e-6);
	}

	public static List<ImageRecord> readXML(File xmlFile) throws IOException
	{
		XmlReader xmr = new XmlReader(xmlFile);
		List<Element> imgElems = xmr.getTags(xmr.getRootElement(), TAG_IMAGE);
		List<ImageRecord> list = new ArrayList<>(imgElems.size());

		for (Element elem : imgElems) {
			ImageRecord rec = new ImageRecord();

			xmr.requiresAttribute(elem, ATTR_NAME);
			rec.identifier = xmr.getAttribute(elem, ATTR_NAME);

			xmr.requiresAttribute(elem, ATTR_FORMAT);
			String format = xmr.getAttribute(elem, ATTR_FORMAT);
			rec.fmt = TileFormat.getFormat(format);
			if (rec.fmt == null)
				throw new InputFileException(xmlFile, "Invalid format: %s%nValid formats are:%n%s", format, TileFormat.validFormats);

			if (xmr.hasAttribute(elem, ATTR_OFFSET)) {
				xmr.requiresAttribute(elem, ATTR_WIDTH);
				xmr.requiresAttribute(elem, ATTR_HEIGHT);

				rec.fixedPos = true;
				rec.imgOffset = xmr.readHex(elem, ATTR_OFFSET);
				rec.sizeW = xmr.readInt(elem, ATTR_WIDTH);
				rec.sizeH = xmr.readInt(elem, ATTR_HEIGHT);

				if (rec.fmt.type == TileFormat.TYPE_CI) {
					xmr.requiresAttribute(elem, ATTR_PALETTE);
					rec.palOffset = xmr.readHex(elem, ATTR_PALETTE);
				}
			}
			else {
				rec.fixedPos = false;
				rec.imgOffset = NO_DATA;
				rec.palOffset = NO_DATA;
				rec.sizeW = NO_DATA;
				rec.sizeH = NO_DATA;
			}

			if (rec.fmt.type == TileFormat.TYPE_CI) {
				if (xmr.hasAttribute(elem, ATTR_PALCOUNT))
					rec.palCount = xmr.readInt(elem, ATTR_PALCOUNT);
				else
					rec.palCount = 1;

				if (rec.palCount < 1)
					xmr.complain("Palette count for " + rec.identifier + " cannot be negative!");
			}

			if (xmr.hasAttribute(elem, ATTR_FLIP))
				rec.flip = xmr.readBoolean(elem, ATTR_FLIP);

			list.add(rec);
		}

		return list;
	}

	public static void writeXML(Iterable<ImageRecord> images, File xmlFile) throws IOException
	{
		try (XmlWriter xmw = new XmlWriter(xmlFile)) {
			XmlTag rootTag = xmw.createTag(TAG_IMG_ASSETS, false);
			xmw.openTag(rootTag);

			for (ImageRecord image : images) {
				XmlTag imgTag = xmw.createTag(TAG_IMAGE, true);

				if (image.fixedPos) {
					if (image.imgOffset > 0)
						xmw.addHex(imgTag, ATTR_OFFSET, image.imgOffset);

					if (image.palOffset > 0)
						xmw.addHex(imgTag, ATTR_PALETTE, image.palOffset);
				}

				if (image.flip)
					xmw.addBoolean(imgTag, ATTR_FLIP, image.flip);

				if (image.fmt.type == TileFormat.TYPE_CI && image.palCount > 1)
					xmw.addInt(imgTag, ATTR_PALCOUNT, image.palCount);

				xmw.addAttribute(imgTag, ATTR_FORMAT, image.fmt.name);

				if (image.fixedPos) {
					xmw.addInt(imgTag, ATTR_WIDTH, image.sizeW);
					xmw.addInt(imgTag, ATTR_HEIGHT, image.sizeH);
				}

				xmw.addAttribute(imgTag, ATTR_NAME, image.identifier);
				xmw.printTag(imgTag);
			}

			xmw.closeTag(rootTag);
			xmw.save();
		}
	}
}
