package game.sprite;

import static app.Directories.*;
import static game.sprite.SpriteTableKey.*;
import static game.texture.TileFormat.CI_4;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.List;

import org.apache.commons.io.FileUtils;

import app.Environment;
import app.input.IOUtils;
import game.shared.ProjectDatabase;
import game.sprite.SpriteLoader.SpriteSet;
import game.texture.Palette;
import game.texture.Tile;
import game.yay0.Yay0Helper;
import util.Logger;
import util.Priority;
import util.StringUtil;
import util.xml.XmlWrapper.XmlTag;
import util.xml.XmlWrapper.XmlWriter;

public class SpriteDumper
{
	public static void main(String[] args) throws IOException
	{
		Environment.initialize();
		dumpSprites();
		Environment.exit();
	}

	public static void dumpSprites() throws IOException
	{
		Logger.log("Dumping sprites...", Priority.MILESTONE);

		try (XmlWriter xmw = new XmlWriter(new File(DUMP_SPRITE + FN_SPRITE_TABLE))) {
			XmlTag root = xmw.createTag(TAG_ROOT, false);
			xmw.openTag(root);
			dumpNpcSprites(xmw);
			dumpPlayerSprites(xmw);
			xmw.closeTag(root);
			xmw.save();
		}

		Logger.log("Converting sprites...", Priority.MILESTONE);
		makeSourcesForNPCs();
		new PlayerSpriteConverter();
	}

	private static void makeSourcesForNPCs() throws IOException
	{
		for (int i = 1; i <= 0xE9; i++) {
			String spriteName = String.format("%02X", i);
			File source = new File(DUMP_SPR_NPC_RAW + spriteName);
			File spriteDir = new File(DUMP_SPR_NPC_SRC + String.format("%02X/", i));
			File xmlFile = new File(spriteDir.getPath() + "/SpriteSheet.xml");

			Logger.log("Converting sprite " + spriteName + ".", Priority.MILESTONE);
			Sprite spr = readBinaryNpc(source);

			FileUtils.forceMkdir(spriteDir);
			spr.dumpRasters(spriteDir, true);
			spr.dumpPalettes(spriteDir);

			XmlWriter xmw = new XmlWriter(xmlFile);
			spr.toXML(xmw);
			xmw.save();
		}
	}

	private static void dumpNpcSprites(XmlWriter xmw) throws IOException
	{
		XmlTag listTag = xmw.createTag(TAG_NPC_LIST, false);
		xmw.openTag(listTag);

		RandomAccessFile raf = Environment.getBaseRomReader();
		raf.seek(0x19E67B8);
		int[] offsets = new int[0xEA];

		for (int i = 0; i < offsets.length; i++)
			offsets[i] = raf.readInt();

		byte[] dumpedBytes;
		byte[] writeBytes;

		for (int i = 0; i < offsets.length - 1; i++) {
			String spriteName = String.format("%02X", i + 1);
			Logger.log("Dumping NPC sprite " + spriteName + ".", Priority.MILESTONE);

			int start = offsets[i] + 0x019E67B8;
			int end = offsets[i + 1] + 0x019E67B8;

			dumpedBytes = new byte[end - start];
			raf.seek(start);
			raf.read(dumpedBytes);
			writeBytes = Yay0Helper.decode(dumpedBytes);

			File out = new File(DUMP_SPR_NPC_RAW + spriteName);
			FileUtils.writeByteArrayToFile(out, writeBytes);

			String name = ProjectDatabase.SpriteType.getName(i + 1);

			XmlTag spriteTag = xmw.createTag(TAG_SPRITE, true);
			xmw.addHex(spriteTag, ATTR_ID, i + 1);
			xmw.addAttribute(spriteTag, ATTR_SOURCE, spriteName);
			xmw.addAttribute(spriteTag, ATTR_NAME, StringUtil.splitCamelCase(name));
			xmw.printTag(spriteTag);
		}

		raf.close();
		xmw.closeTag(listTag);
	}

	protected static Sprite readBinaryNpc(File binFile) throws IOException
	{
		ByteBuffer bb = IOUtils.getDirectBuffer(binFile);
		Sprite sprite = new Sprite(SpriteSet.Npc);

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

		// read palettes
		for (int i = 0; i < paletteOffsetList.size(); i++) {
			bb.position(paletteOffsetList.get(i));

			short[] colors = new short[16];
			for (int j = 0; j < 16; j++)
				colors[j] = bb.getShort();

			sprite.palettes.addElement(new SpritePalette(new Palette(colors)));
		}

		// read images
		for (int i = 0; i < imageOffsetList.size(); i++) {
			bb.position(imageOffsetList.get(i));
			int rasterOffset = bb.getInt();
			int width = bb.get() & 0xFF;
			int height = bb.get() & 0xFF;
			int paletteIndex = bb.get();
			int FF = bb.get();
			assert (FF == -1);

			bb.position(rasterOffset);

			SpriteRaster sr = new SpriteRaster();

			sr.img = new Tile(CI_4, height, width);
			sr.img.readImage(bb, false);
			sr.defaultPal = sprite.palettes.get(paletteIndex);

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

		return sprite;
	}

	private static final int NUM_PLAYER_SPRITES = 13;

	private static final String[] PLAYER_SPRITE_NAMES = {
			"Mario1",
			"Mario2",
			"Mario3",
			"Mario4",
			"Mario5",
			"Mario6",
			"Mario7",
			"Mario8",
			"Mario9",
			"Peach1",
			"Peach2",
			"Peach3",
			"Peach4"
	};

	private static void dumpPlayerSprites(XmlWriter xmw) throws IOException
	{
		XmlTag listTag = xmw.createTag(TAG_PLAYER_LIST, false);
		xmw.openTag(listTag);

		RandomAccessFile raf = Environment.getBaseRomReader();

		FileUtils.forceMkdir(DUMP_SPR_PLR_RAW.toFile());

		byte[] dumpedBytes;
		byte[] writeBytes;

		for (int i = 0; i < NUM_PLAYER_SPRITES; i++) {
			String spriteName = String.format("%02X", i + 1);
			Logger.log("Dumping player sprite " + spriteName + ".", Priority.MILESTONE);

			raf.seek(0x19E0970 + 4 * i);
			int start = raf.readInt();
			int end = raf.readInt();

			raf.seek(0x19E0970 + start);

			dumpedBytes = new byte[end - start];
			raf.read(dumpedBytes);
			writeBytes = Yay0Helper.decode(dumpedBytes);

			File out = new File(DUMP_SPR_PLR_RAW + String.format("%02X", i + 1));
			FileUtils.writeByteArrayToFile(out, writeBytes);

			XmlTag spriteTag = xmw.createTag(TAG_SPRITE, true);
			xmw.addHex(spriteTag, ATTR_ID, i + 1);
			xmw.addAttribute(spriteTag, ATTR_SOURCE, spriteName);
			xmw.addAttribute(spriteTag, ATTR_NAME, StringUtil.splitCamelCase(PLAYER_SPRITE_NAMES[i]));
			xmw.printTag(spriteTag);
		}

		byte[] rasterBytes = new byte[0x19E0970 - 0x1943020];

		raf.seek(0x1943020);
		raf.read(rasterBytes);

		File out = new File(DUMP_SPR_PLR_RAW + String.format("rasters"));
		FileUtils.writeByteArrayToFile(out, rasterBytes);

		raf.close();
		xmw.closeTag(listTag);
	}

	private static List<Integer> readOffsetList(ByteBuffer bb)
	{
		int v;
		List<Integer> offsetList = new LinkedList<>();
		while ((v = bb.getInt()) != -1)
			offsetList.add(v);
		return offsetList;
	}
}
