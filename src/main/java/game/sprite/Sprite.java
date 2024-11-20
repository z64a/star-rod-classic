package game.sprite;

import static app.Directories.*;
import static game.sprite.SpriteKey.*;
import static game.texture.TileFormat.CI_4;
import static org.lwjgl.opengl.GL13.GL_CLAMP_TO_BORDER;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

import javax.swing.DefaultListModel;

import org.w3c.dom.Element;

import app.AssetManager;
import app.input.IOUtils;
import app.input.InputFileException;
import common.Vector3f;
import game.map.BoundingBox;
import game.map.shading.ShadingProfile;
import game.sprite.SpriteLoader.SpriteSet;
import game.sprite.editor.SpriteCamera;
import game.sprite.editor.SpriteCamera.BasicTraceHit;
import game.sprite.editor.SpriteCamera.BasicTraceRay;
import game.texture.Tile;
import renderer.shaders.ShaderManager;
import renderer.shaders.scene.SpriteShader;
import util.Logger;
import util.xml.XmlWrapper.XmlReader;
import util.xml.XmlWrapper.XmlSerializable;
import util.xml.XmlWrapper.XmlTag;
import util.xml.XmlWrapper.XmlWriter;

public class Sprite implements XmlSerializable
{
	// const equal to (float)5/7, set at [800E1360]
	// see component matrix located in S0 at [802DC958]
	public static final float WORLD_SCALE = 0.714286f;

	public final DefaultListModel<SpriteAnimation> animations = new DefaultListModel<>();
	public final DefaultListModel<SpriteRaster> rasters = new DefaultListModel<>();
	public final DefaultListModel<SpritePalette> palettes = new DefaultListModel<>();

	private transient boolean texturesLoaded = false;
	private transient boolean readyForEditor = false;
	public transient boolean enableStencilBuffer = false;

	// create the list models and have the animators generate their animation commands
	public void prepareForEditor()
	{
		if (readyForEditor)
			return;

		for (int i = 0; i < animations.size(); i++) {
			SpriteAnimation anim = animations.elementAt(i);
			for (int j = 0; j < anim.components.size(); j++) {
				SpriteComponent comp = anim.components.elementAt(j);
				comp.generate();
			}
		}

		recalculateIndices();

		readyForEditor = true;
	}

	public void recalculateIndices()
	{
		for (int i = 0; i < rasters.size(); i++)
			rasters.get(i).listIndex = i;

		for (int i = 0; i < palettes.size(); i++)
			palettes.get(i).listIndex = i;

		for (int i = 0; i < animations.size(); i++) {
			SpriteAnimation anim = animations.get(i);
			anim.listIndex = i;
			for (int j = 0; j < anim.components.size(); j++)
				anim.components.get(j).listIndex = j;
		}
	}

	public void assignDefaultAnimationNames()
	{
		for (int i = 0; i < animations.size(); i++) {
			SpriteAnimation anim = animations.get(i);
			anim.name = String.format("Anim_%02X", i);
			for (int j = 0; j < anim.components.size(); j++)
				anim.components.get(j).name = String.format("Comp_%02X", j);
		}
	}

	public File source;
	private final boolean isPlayerSprite;

	public String name = "";
	public int numVariations;
	public int maxComponents;

	private int atlasH, atlasW;

	public transient BoundingBox aabb = new BoundingBox();

	protected Sprite(SpriteSet set)
	{
		this.isPlayerSprite = (set == SpriteSet.Player);
	}

	@Override
	public String toString()
	{
		return name.isEmpty() ? "Unnamed" : name;
	}

	public String getDirectoryName()
	{
		return source.getParentFile() + "/";
	}

	public boolean isPlayerSprite()
	{
		return isPlayerSprite;
	}

	public static Sprite read(File xmlFile, SpriteSet set)
	{
		XmlReader xmr = new XmlReader(xmlFile);
		Sprite spr = new Sprite(set);
		spr.fromXML(xmr, xmr.getRootElement());
		return spr;
	}

	@Override
	public void fromXML(XmlReader xmr, Element spriteElem)
	{
		source = xmr.getSourceFile();
		List<String> paletteFilenames, paletteNames;

		if (xmr.hasAttribute(spriteElem, ATTR_SPRITE_NUM_COMPONENTS)) {
			maxComponents = xmr.readHex(spriteElem, ATTR_SPRITE_NUM_COMPONENTS);
		}
		else {
			xmr.requiresAttribute(spriteElem, ATTR_SPRITE_A);
			maxComponents = xmr.readHex(spriteElem, ATTR_SPRITE_A);
		}

		if (xmr.hasAttribute(spriteElem, ATTR_SPRITE_NUM_VARIATIONS)) {
			numVariations = xmr.readHex(spriteElem, ATTR_SPRITE_NUM_VARIATIONS);
		}
		else {
			xmr.requiresAttribute(spriteElem, ATTR_SPRITE_B);
			numVariations = xmr.readHex(spriteElem, ATTR_SPRITE_B);
		}

		Element palettesElem = xmr.getUniqueRequiredTag(spriteElem, TAG_PALETTE_LIST);
		List<Element> paletteElems = xmr.getRequiredTags(palettesElem, TAG_PALETTE);
		paletteFilenames = new ArrayList<>();
		paletteNames = new ArrayList<>();
		for (int i = 0; i < paletteElems.size(); i++) {
			Element paletteElem = paletteElems.get(i);

			xmr.requiresAttribute(paletteElem, ATTR_ID);
			int id = xmr.readHex(paletteElem, ATTR_ID);

			if (id != i)
				throw new InputFileException(source, "Palettes are out of order!");

			if (xmr.hasAttribute(paletteElem, ATTR_NAME))
				paletteNames.add(xmr.getAttribute(paletteElem, ATTR_NAME));
			else
				paletteNames.add("");

			xmr.requiresAttribute(paletteElem, ATTR_SOURCE);
			paletteFilenames.add(xmr.getAttribute(paletteElem, ATTR_SOURCE));
		}

		Element rastersElem = xmr.getUniqueRequiredTag(spriteElem, TAG_RASTER_LIST);
		List<Element> rasterElems = xmr.getTags(rastersElem, TAG_RASTER);
		for (int i = 0; i < rasterElems.size(); i++) {
			Element rasterElem = rasterElems.get(i);
			SpriteRaster sr = new SpriteRaster();

			xmr.requiresAttribute(rasterElem, ATTR_ID);
			int id = xmr.readHex(rasterElem, ATTR_ID);

			if (id != i)
				throw new InputFileException(source, "Rasters are out of order!");

			if (xmr.hasAttribute(rasterElem, ATTR_NAME))
				sr.name = xmr.getAttribute(rasterElem, ATTR_NAME);

			if (!xmr.hasAttribute(rasterElem, ATTR_SOURCE)) {
				xmr.requiresAttribute(rasterElem, ATTR_SPECIAL_SIZE);
				sr.isSpecial = true;
				int[] size = xmr.readHexArray(rasterElem, ATTR_SPECIAL_SIZE, 2);
				sr.specialWidth = size[0];
				sr.specialHeight = size[1];
				sr.name = "special";
			}
			else {
				sr.filename = xmr.getAttribute(rasterElem, ATTR_SOURCE);

				xmr.requiresAttribute(rasterElem, ATTR_PALETTE);
				sr.palIndex = xmr.readHex(rasterElem, ATTR_PALETTE);
			}

			rasters.addElement(sr);
		}

		Element animationsElem = xmr.getUniqueRequiredTag(spriteElem, TAG_ANIMATION_LIST);
		List<Element> animationElems = xmr.getTags(animationsElem, TAG_ANIMATION);
		for (Element animationElem : animationElems) {
			SpriteAnimation anim = new SpriteAnimation(this);
			animations.addElement(anim);

			if (xmr.hasAttribute(animationElem, ATTR_NAME))
				anim.name = xmr.getAttribute(animationElem, ATTR_NAME);

			List<Element> componentElems = xmr.getRequiredTags(animationElem, TAG_COMPONENT);
			for (Element componentElem : componentElems) {
				SpriteComponent comp = new SpriteComponent(anim);
				anim.components.addElement(comp);
				comp.fromXML(xmr, componentElem);
			}
		}

		// palettes are located in the same directory as the spritesheet
		String xmlDir = source.getParent() + "/";
		for (int i = 0; i < paletteFilenames.size(); i++) {
			String filename = paletteFilenames.get(i);
			String name = paletteNames.get(i);
			try {
				File paletteFile = new File(xmlDir + filename);
				if (!paletteFile.exists())
					throw new InputFileException(source, "Can't find palette: " + filename);

				Tile palImg = Tile.load(paletteFile, CI_4);
				SpritePalette pal = new SpritePalette(palImg.palette);
				pal.filename = filename;
				pal.name = name;
				palettes.addElement(pal);
			}
			catch (Exception e) {
				xmr.complain(e.getMessage());
			}
		}

		// raster locations depends on whether this is an Npc or Player sprite
		try {
			loadRasters();
		}
		catch (Exception e) {
			xmr.complain(e.getMessage());
		}

		recalculateIndices();
	}

	protected void loadRasters() throws IOException
	{
		for (int i = 0; i < rasters.size(); i++) {
			SpriteRaster sr = rasters.elementAt(i);
			if (sr.palIndex >= palettes.size())
				throw new InputFileException(source, "Palette is out of range for raster %02X: %X", i, sr.defaultPal);
			sr.defaultPal = palettes.get(sr.palIndex);

			String imgFilename;
			if (sr.isSpecial)
				imgFilename = DUMP_IMG_ASSETS + "item/battle/XBandage.png";
			else if (isPlayerSprite)
				imgFilename = AssetManager.getPlayerSpriteRaster(sr.filename).toString();
			else
				imgFilename = getDirectoryName() + sr.filename;

			File rasterFile = new File(imgFilename);
			if (!rasterFile.exists())
				throw new InputFileException(source, "Can't find raster: " + imgFilename);

			sr.img = Tile.load(rasterFile, CI_4);
		}
	}

	@Override
	public void toXML(XmlWriter xmw)
	{
		recalculateIndices();

		XmlTag root = xmw.createTag(TAG_SPRITE, false);
		xmw.addHex(root, ATTR_SPRITE_NUM_COMPONENTS, maxComponents);
		xmw.addHex(root, ATTR_SPRITE_NUM_VARIATIONS, numVariations);
		xmw.openTag(root);

		XmlTag palettesTag = xmw.createTag(TAG_PALETTE_LIST, false);
		xmw.openTag(palettesTag);
		for (int i = 0; i < palettes.size(); i++) {
			SpritePalette sp = palettes.get(i);
			XmlTag paletteTag = xmw.createTag(TAG_PALETTE, true);
			xmw.addHex(paletteTag, ATTR_ID, i);

			if (!sp.name.isEmpty())
				xmw.addAttribute(paletteTag, ATTR_NAME, sp.name);

			xmw.addAttribute(paletteTag, ATTR_SOURCE, sp.filename);
			xmw.printTag(paletteTag);
		}
		xmw.closeTag(palettesTag);

		XmlTag rastersTag = xmw.createTag(TAG_RASTER_LIST, false);
		xmw.openTag(rastersTag);
		for (int i = 0; i < rasters.size(); i++) {
			SpriteRaster sr = rasters.get(i);
			XmlTag rasterTag = xmw.createTag(TAG_RASTER, true);
			xmw.addHex(rasterTag, ATTR_ID, i);

			if (sr.isSpecial) {
				xmw.addHexArray(rasterTag, ATTR_SPECIAL_SIZE, (sr.specialWidth & 0xFF), (sr.specialHeight & 0xFF));
			}
			else {
				if (!sr.name.isEmpty())
					xmw.addAttribute(rasterTag, ATTR_NAME, sr.name);

				xmw.addHex(rasterTag, ATTR_PALETTE, sr.defaultPal.getIndex());
				xmw.addAttribute(rasterTag, ATTR_SOURCE, sr.filename);
			}
			xmw.printTag(rasterTag);
		}
		xmw.closeTag(rastersTag);

		XmlTag animationsTag = xmw.createTag(TAG_ANIMATION_LIST, false);
		xmw.openTag(animationsTag);
		for (int i = 0; i < animations.size(); i++) {
			SpriteAnimation anim = animations.elementAt(i);
			XmlTag animationTag = xmw.createTag(TAG_ANIMATION, false);
			if (!anim.name.isEmpty())
				xmw.addAttribute(animationTag, ATTR_NAME, anim.name);
			xmw.openTag(animationTag);

			for (int j = 0; j < anim.components.size(); j++) {
				SpriteComponent component = anim.components.elementAt(j);
				component.toXML(xmw);
			}

			xmw.closeTag(animationTag);
		}
		xmw.closeTag(animationsTag);

		xmw.closeTag(root);
	}

	public void dumpRasters(File dir, boolean useNewFilesForSource) throws IOException
	{
		for (int i = 0; i < rasters.size(); i++) {
			SpriteRaster sr = rasters.get(i);

			if (sr.isSpecial)
				continue;

			//		System.out.printf("Converting raster %02X / %02X\n", (i + 1), rasters.size());

			SpritePalette sp = sr.defaultPal;
			String saveFilename = String.format("Raster_%02X.png", i);

			if (useNewFilesForSource)
				sr.filename = saveFilename;

			sr.img.palette = sp.pal;
			sr.img.savePNG(dir.toString() + "/" + saveFilename);
		}
	}

	public void resaveRasters(File dir) throws IOException
	{
		for (int i = 0; i < rasters.size(); i++) {
			SpriteRaster sr = rasters.get(i);

			if (sr.isSpecial)
				continue;

			SpritePalette sp = sr.defaultPal;
			sr.img.palette = sp.pal;
			sr.img.savePNG(dir.toString() + "/" + sr.filename);
		}
	}

	public void dumpPalettes(File dir) throws IOException
	{
		Tile defaultImage = null;

		for (int i = 0; i < rasters.size(); i++) {
			SpriteRaster sr = rasters.get(i);

			if (sr.isSpecial)
				continue;

			// get default image for palettes
			if (defaultImage == null)
				defaultImage = sr.img;

			// if a raster using a palette exists, use it for the palette image
			SpritePalette sp = sr.defaultPal;
			if (!sp.dumped) {
				if (sp.filename == null || sp.filename.isEmpty())
					sp.filename = String.format("Palette_%02X.png", sr.defaultPal.getIndex());

				sr.img.palette = sr.defaultPal.pal;
				sr.img.savePNG(dir.toString() + "/" + sp.filename);

				sp.dumped = true;
			}
		}

		if (defaultImage == null)
			defaultImage = Tile.getSpritePaletteImage();

		for (int i = 0; i < palettes.size(); i++) {
			SpritePalette sp = palettes.get(i);

			if (!sp.dumped) {
				if (sp.filename == null || sp.filename.isEmpty())
					sp.filename = String.format("Palette_%02X.png", i);
				defaultImage.palette = sp.pal;
				defaultImage.savePNG(dir.toString() + "/" + sp.filename);
			}

			// reset
			sp.dumped = false;
		}
	}

	public void saveChanges()
	{
		for (int i = 0; i < animations.size(); i++) {
			SpriteAnimation anim = animations.get(i);
			for (int j = 0; j < anim.components.size(); j++) {
				SpriteComponent comp = anim.components.get(j);
				comp.saveChanges();
			}
		}
	}

	public void convertToKeyframes()
	{
		for (int i = 0; i < animations.size(); i++) {
			SpriteAnimation anim = animations.get(i);
			for (int j = 0; j < anim.components.size(); j++) {
				SpriteComponent comp = anim.components.get(j);
				comp.convertToKeyframes();
			}
		}
	}

	public void convertToCommands()
	{
		for (int i = 0; i < animations.size(); i++) {
			SpriteAnimation anim = animations.get(i);
			for (int j = 0; j < anim.components.size(); j++) {
				SpriteComponent comp = anim.components.get(j);
				comp.convertToCommands();
			}
		}
	}

	public boolean areTexturesLoaded()
	{
		return texturesLoaded;
	}

	public void loadTextures()
	{
		for (int i = 0; i < rasters.size(); i++) {
			SpriteRaster sr = rasters.get(i);
			sr.img.glLoad(GL_CLAMP_TO_BORDER, GL_CLAMP_TO_BORDER, false);
			sr.loadEditorImages();
		}

		for (int i = 0; i < palettes.size(); i++) {
			SpritePalette sp = palettes.get(i);
			sp.pal.glLoad();
		}

		texturesLoaded = true;
	}

	public void unloadTextures()
	{
		for (int i = 0; i < rasters.size(); i++) {
			SpriteRaster sr = rasters.elementAt(i);
			sr.img.glDelete();
		}

		for (int i = 0; i < palettes.size(); i++) {
			SpritePalette sp = palettes.get(i);
			sp.pal.glDelete();
		}
	}

	public void glRefreshRasters()
	{
		for (int i = 0; i < rasters.getSize(); i++) {
			SpriteRaster sr = rasters.get(i);
			sr.img.glLoad(GL_CLAMP_TO_BORDER, GL_CLAMP_TO_BORDER, false);
		}
	}

	public void glRefreshPalettes()
	{
		for (int i = 0; i < palettes.getSize(); i++) {
			SpritePalette sp = palettes.get(i);
			sp.pal.glReload();
		}
	}

	public int getPaletteCount()
	{
		return palettes.size();
	}

	public int lastValidPaletteID()
	{
		return palettes.size() - 1;
	}

	public int lastValidAnimationID()
	{
		return animations.size() - 1;
	}

	// update anim based on ID
	public void resetAnimation(int animationID)
	{
		if (animationID >= animations.size())
			throw new IllegalArgumentException(String.format(
				"Animation ID is out of range: %X of %X", animationID, animations.size()));

		animations.get(animationID).reset();
	}

	// update anim based on ID
	public void updateAnimation(int animationID)
	{
		if (animationID >= animations.size())
			throw new IllegalArgumentException(String.format(
				"Animation ID is out of range: %X of %X", animationID, animations.size()));

		animations.get(animationID).step();
	}

	// render based on IDs -- these are used by the map editor
	public void render(ShadingProfile spriteShading, int animationID, int paletteOverride, boolean useSelectShading, boolean useFiltering)
	{
		if (animationID >= animations.size())
			throw new IllegalArgumentException(String.format(
				"Animation ID is out of range: %X of %X", animationID, animations.size()));

		if (paletteOverride >= palettes.size())
			throw new IllegalArgumentException(String.format(
				"Palette ID is out of range: %X of %X", paletteOverride, palettes.size()));

		render(spriteShading, animations.get(animationID), palettes.get(paletteOverride), true, useFiltering, useSelectShading);
	}

	// render based on reference
	public void render(ShadingProfile spriteShading, SpriteAnimation anim, SpritePalette paletteOverride,
		boolean enableSelectedHighlight, boolean useSelectShading, boolean useFiltering)
	{
		if (!animations.contains(anim))
			throw new IllegalArgumentException(anim + " does not belong to " + toString());

		aabb.clear();

		for (int i = 0; i < anim.components.size(); i++) {
			SpriteComponent comp = anim.components.get(i);
			comp.render(spriteShading, paletteOverride, enableStencilBuffer, enableSelectedHighlight, useSelectShading, false, useFiltering);
			comp.addCorners(aabb);
		}
	}

	// render single component based on references
	public void render(ShadingProfile spriteShading, SpriteAnimation anim, SpriteComponent comp, SpritePalette paletteOverride,
		boolean enableSelectedHighlight, boolean useSelectShading, boolean useFiltering)
	{
		if (!animations.contains(anim))
			throw new IllegalArgumentException(anim + " does not belong to " + toString());

		if (!anim.components.contains(comp))
			throw new IllegalArgumentException(comp + " does not belong to " + anim);

		aabb.clear();

		comp.render(spriteShading, paletteOverride, enableStencilBuffer, enableSelectedHighlight, useSelectShading, false, useFiltering);
		comp.addCorners(aabb);
	}

	private static final int ATLAS_TILE_PADDING = 8;
	private static final int ATLAS_SELECT_PADDING = 1;

	public void makeAtlas()
	{
		int totalWidth = ATLAS_TILE_PADDING;
		int totalHeight = ATLAS_TILE_PADDING;
		int validRasterCount = 0;

		for (int i = 0; i < rasters.size(); i++) {
			SpriteRaster sr = rasters.elementAt(i);
			if (sr.isSpecial)
				continue;

			totalWidth += ATLAS_TILE_PADDING + sr.img.width;
			totalHeight += ATLAS_TILE_PADDING + sr.img.height;
			validRasterCount++;
		}

		float aspectRatio = 1.0f; // H/W
		int maxWidth = (int) Math.sqrt(totalWidth * totalHeight / (aspectRatio * validRasterCount));
		maxWidth = (maxWidth + 7) & 0xFFFFFFF8; // pad to multiple of 8

		int currentX = ATLAS_TILE_PADDING;
		int currentY = -ATLAS_TILE_PADDING;

		ArrayList<Integer> rowPosY = new ArrayList<>();
		ArrayList<Integer> rowTallest = new ArrayList<>();
		int currentRow = 0;
		rowTallest.add(0);

		for (int i = 0; i < rasters.size(); i++) {
			SpriteRaster sr = rasters.elementAt(i);
			if (sr.isSpecial)
				continue;

			if (currentX + sr.img.width + ATLAS_TILE_PADDING > maxWidth) {
				// start new row
				currentY -= rowTallest.get(currentRow);
				rowPosY.add(currentY);
				rowTallest.add(0);

				// next row
				currentX = ATLAS_TILE_PADDING;
				currentY -= ATLAS_TILE_PADDING;
				currentRow++;
			}

			sr.atlasX = currentX;
			sr.atlasRow = currentRow;

			// move forward for next in the row
			currentX += sr.img.width;
			currentX += ATLAS_TILE_PADDING;

			if (sr.img.height > rowTallest.get(currentRow))
				rowTallest.set(currentRow, sr.img.height);
		}

		// finish row
		currentY -= rowTallest.get(currentRow);
		rowPosY.add(currentY);
		currentY -= ATLAS_TILE_PADDING;

		atlasW = maxWidth;
		atlasH = currentY;

		for (int i = 0; i < rasters.size(); i++) {
			SpriteRaster sr = rasters.elementAt(i);
			if (sr.isSpecial)
				continue;

			sr.atlasY = rowPosY.get(sr.atlasRow) + sr.img.height;
		}

		// center the atlas
		for (int i = 0; i < rasters.size(); i++) {
			SpriteRaster sr = rasters.elementAt(i);
			if (sr.isSpecial)
				continue;

			sr.atlasX -= atlasW / 2.0f;
			sr.atlasY -= atlasH / 2.0f;
		}

		// negative -> positive
		atlasH = Math.abs(atlasH);
	}

	public void centerAtlas(SpriteCamera sheetCamera, int canvasW, int canvasH)
	{
		sheetCamera.centerOn(canvasW, canvasH, 0, 0, 0, atlasW, atlasH, 0);
		sheetCamera.setMaxPos(Math.round(atlasW / 2.0f), Math.round(atlasH / 2.0f));
	}

	public void tryAtlasHighlight(BasicTraceRay trace)
	{
		for (int i = 0; i < rasters.size(); i++) {
			SpriteRaster sr = rasters.get(i);

			if (sr.isSpecial)
				continue;

			Vector3f min = new Vector3f(
				sr.atlasX - ATLAS_SELECT_PADDING,
				sr.atlasY - sr.img.height - ATLAS_SELECT_PADDING,
				0);

			Vector3f max = new Vector3f(
				sr.atlasX + sr.img.width + ATLAS_SELECT_PADDING,
				sr.atlasY + ATLAS_SELECT_PADDING,
				0);

			BasicTraceHit hit = BasicTraceRay.getIntersection(trace, min, max);

			sr.highlighted = !hit.missed();
		}
	}

	public SpriteRaster tryAtlasSelection(BasicTraceRay trace)
	{
		SpriteRaster selected = null;

		for (int i = 0; i < rasters.size(); i++) {
			SpriteRaster sr = rasters.get(i);

			if (sr.isSpecial)
				continue;

			Vector3f min = new Vector3f(
				sr.atlasX - ATLAS_SELECT_PADDING,
				sr.atlasY - sr.img.height - ATLAS_SELECT_PADDING,
				0);

			Vector3f max = new Vector3f(
				sr.atlasX + sr.img.width + ATLAS_SELECT_PADDING,
				sr.atlasY + ATLAS_SELECT_PADDING,
				0);

			BasicTraceHit hit = BasicTraceRay.getIntersection(trace, min, max);

			if (!hit.missed()) {
				selected = sr;
				sr.selected = true;
			}
			else
				sr.selected = false;
		}

		return selected;
	}

	public void renderAtlas(SpritePalette overridePalette, boolean useFiltering)
	{
		SpriteShader shader = ShaderManager.use(SpriteShader.class);
		shader.useFiltering.set(useFiltering);

		//	if(enableStencilBuffer)
		//	{
		//		glEnable(GL_STENCIL_TEST);
		//		glStencilOp(GL_KEEP, GL_KEEP, GL_REPLACE);
		//	}

		for (int i = 0; i < rasters.size(); i++) {
			SpriteRaster sr = rasters.get(i);

			if (sr.isSpecial)
				continue;

			//		if(enableStencilBuffer)
			//			glStencilFunc(GL_ALWAYS, i + 1, 0xFF);

			shader.selected.set(sr.selected);
			shader.highlighted.set(sr.highlighted);

			SpritePalette sp = (overridePalette == null) ? sr.defaultPal : overridePalette;
			sr.img.glBind(shader.texture);
			sp.pal.glBind(shader.palette);

			float x1 = sr.atlasX;
			float y1 = sr.atlasY;
			float x2 = sr.atlasX + sr.img.width;
			float y2 = sr.atlasY - sr.img.height;

			shader.setXYQuadCoords(x1, y2, x2, y1, 0); //TODO upside down?
			shader.renderQuad();
		}

		//	if(enableStencilBuffer)
		//		glDisable(GL_STENCIL_TEST);
	}

	public static void validate(Sprite spr)
	{
		for (int i = 0; i < spr.animations.size(); i++) {
			SpriteAnimation anim = spr.animations.get(i);
			for (int j = 0; j < anim.components.size(); j++) {
				SpriteComponent comp = anim.components.get(j);

				System.out.printf("%02X.%X : ", i, j);

				Queue<Short> sequence = new LinkedList<>(comp.rawAnim);
				Queue<Short> cmdQueue = new LinkedList<>(sequence);

				List<Integer> keyFrameCmds = new ArrayList<>(16);

				while (!cmdQueue.isEmpty()) {
					int pos = sequence.size() - cmdQueue.size();
					short s = cmdQueue.poll();

					int type = (s >> 12) & 0xF;
					int extra = (s << 20) >> 20;

					keyFrameCmds.add(type);

					System.out.printf("%04X ", s);

					switch (type) {
						case 0x0: // delay
							keyFrameCmds.clear();
							if (!cmdQueue.isEmpty())
								System.out.print("| ");
							assert (extra > 0);
							assert (extra <= 260); // longest delay = 4.333... seconds
							// assert(extra == 1 || extra % 2 == 0); false! -- delay count can be ODD! and >1
							break;
						case 0x1: // set image -- 1FFF sets to null (do not draw)
							assert (extra == -1 || (extra >= 0 && extra <= spr.rasters.size()));
							break;
						case 0x2: // goto command
							keyFrameCmds.clear();
							if (!cmdQueue.isEmpty())
								System.out.print("| ");
							assert (extra < sequence.size());
							assert (extra < pos); // this goto always jumps backwards
							break;
						case 0x3: // set pos
							assert (extra == 0 || extra == 1); //TODO absolute/relative position flag -- seems to do nothing...
							//		if(extra == 0)
							//			System.out.printf("<> %04X%n" , s);
							short dx = cmdQueue.poll();
							System.out.printf("%04X ", dx);
							short dy = cmdQueue.poll();
							System.out.printf("%04X ", dy);
							short dz = cmdQueue.poll();
							System.out.printf("%04X ", dz);
							break;
						case 0x4: // set angle
							assert (extra >= -180 && extra <= 180);
							s = cmdQueue.poll();
							System.out.printf("%04X ", s);
							assert (s >= -180 && s <= 180);
							s = cmdQueue.poll();
							System.out.printf("%04X ", s);
							assert (s >= -180 && s <= 180);
							break;
						case 0x5: // set scale -- extra == 3 is valid, but unused
							s = cmdQueue.poll();
							System.out.printf("%04X ", s);
							assert (extra == 0 || extra == 1 || extra == 2);
							break;
						case 0x6: // use palette -- FFF is valid, but unused
							assert (extra >= 0 && extra < spr.getPaletteCount());
							break;
						case 0x7: // loop
							s = cmdQueue.poll();
							System.out.printf("%04X ", s);
							keyFrameCmds.clear();
							if (!cmdQueue.isEmpty())
								System.out.print("| ");
							assert (extra < sequence.size());
							assert (extra < pos); // always jumps backwards
							assert (s >= 0 && s < 25); // can be zero, how strange
							break;
						case 0x8: // set parent
							int parentType = (extra >> 8) & 0xF;
							int index = extra & 0xFF;

							switch (parentType) {
								case 0:
									// found only for the black ash poofs included with certain animations (monty mole, bandit, etc)
									assert (s == (short) 0x8000);
									break;
								case 1:
									assert (pos == 0);
									assert (index >= 0 && index < anim.components.size());
									break;
								case 2:
									//assert(pos == comp.sequence.size() - 2);
									System.out.printf("PARENT: %X%n", extra);
									assert (index == 1 || index == 2);
									break;
								default:
									assert (false);
							}
							break;
						default:
							throw new RuntimeException(String.format("Unknown animation command: %04X", s));
					}
				}
				System.out.println();
			}
		}
		System.out.println();
	}

	public static void validateKeyframes(Sprite spr)
	{
		for (int i = 0; i < spr.animations.size(); i++) {
			SpriteAnimation anim = spr.animations.get(i);
			for (int j = 0; j < anim.components.size(); j++) {
				SpriteComponent comp = anim.components.get(j);

				System.out.printf("%02X.%X : ", i, j);

				RawAnimation sequence = comp.animator.getCommandList();
				Queue<Short> cmdQueue = new LinkedList<>(sequence);

				while (!cmdQueue.isEmpty()) {
					int pos = sequence.size() - cmdQueue.size();
					short s = cmdQueue.poll();

					int type = (s >> 12) & 0xF;
					int extra = (s << 20) >> 20;

					System.out.printf("%04X ", s);

					switch (type) {
						case 0x0: // delay
							if (!cmdQueue.isEmpty())
								System.out.print("| ");
							assert (extra > 0);
							assert (extra <= 260); // longest delay = 4.333... seconds
							break;
						case 0x1: // set image -- 1FFF sets to null (do not draw)
							assert (extra == -1 || (extra >= 0 && extra <= spr.rasters.size()));
							break;
						case 0x2: // goto command
							if (!cmdQueue.isEmpty())
								System.out.print("| ");
							assert (extra < sequence.size());
							assert (extra < pos); // this goto always jumps backwards
							break;
						case 0x3: // set pos
							assert (extra == 0 || extra == 1); //TODO absolute/relative position flag?
							//		if(extra == 0)
							//			System.out.printf("<> %04X%n" , s);
							short dx = cmdQueue.poll();
							System.out.printf("%04X ", dx);
							short dy = cmdQueue.poll();
							System.out.printf("%04X ", dy);
							short dz = cmdQueue.poll();
							System.out.printf("%04X ", dz);
							break;
						case 0x4: // set angle
							assert (extra >= -180 && extra <= 180);
							s = cmdQueue.poll();
							System.out.printf("%04X ", s);
							assert (s >= -180 && s <= 180);
							s = cmdQueue.poll();
							System.out.printf("%04X ", s);
							assert (s >= -180 && s <= 180);
							break;
						case 0x5: // set scale
							s = cmdQueue.poll();
							System.out.printf("%04X ", s);
							assert (extra == 0 || extra == 1 || extra == 2);
							break;
						case 0x6: // use palette
							assert (extra >= 0 && extra < spr.getPaletteCount());
							break;
						case 0x7: // loop
							s = cmdQueue.poll();
							System.out.printf("%04X ", s);
							if (!cmdQueue.isEmpty())
								System.out.print("| ");
							assert (extra < sequence.size());
							assert (extra < pos); // always jumps backwards
							assert (s >= 0 && s < 25); // can be zero, how strange
							break;
						case 0x8: // set parent
							int parentType = (extra >> 8) & 0xF;

							switch (parentType) {
								case 0:
									// found only for the black ash poofs included with certain animations (monty mole, bandit, etc)
									assert (s == (short) 0x8000);
									break;
								case 1:
									assert (pos == 0);
									break;
								case 2:
									//assert(pos == comp.sequence.size() - 2);
									break;
								default:
									assert (false);
							}
							break;
						default:
							throw new RuntimeException(String.format("Unknown animation command: %04X", s));
					}
				}
				System.out.println();

				comp.animator.generate(sequence);
			}
		}
		System.out.println();
	}

	public boolean hasUnusedFiles()
	{
		ArrayList<String> used = new ArrayList<>();

		for (int i = 0; i < rasters.size(); i++) {
			SpriteRaster s = rasters.get(i);
			used.add(s.filename);
		}

		for (int i = 0; i < palettes.size(); i++) {
			SpritePalette s = palettes.get(i);
			used.add(s.filename);
		}

		try {
			for (File file : IOUtils.getFilesWithExtension(source.getParentFile(), "png", false)) {
				if (!used.contains(file.getName())) {
					return true;
				}
			}
		}
		catch (IOException error) {
			// Don't care
		}

		return false;
	}

	public void deleteUnusedFiles() throws IOException
	{
		ArrayList<String> used = new ArrayList<>();

		for (int i = 0; i < rasters.size(); i++) {
			SpriteRaster s = rasters.get(i);
			used.add(s.filename);
		}

		for (int i = 0; i < palettes.size(); i++) {
			SpritePalette s = palettes.get(i);
			used.add(s.filename);
		}

		for (File file : IOUtils.getFilesWithExtension(source.getParentFile(), "png", false)) {
			if (!used.contains(file.getName())) {
				Logger.log("Deleting " + file.getPath());
				file.delete();
			}
		}
	}

	// sprite directories are organized:
	// ./spriteName/
	//   Palette_XX.png
	//   Raster_XX.png
	//   SpriteSheetID.xml

	/*
	public static void main(String[] args) throws IOException
	{
		RunContext.initialize();

		validateAll();
	//	convertAll();
	//	testBinary();
	//	testXmlCommandLists();
	//	testXml();

		RunContext.exit();
	}
	*/

	private static void validateAll() throws IOException
	{
		for (int i = 1; i <= 0xE9; i++) {
			Sprite spr = SpriteDumper.readBinaryNpc(new File(String.format("%s%02X", DUMP_SPR_NPC_RAW, i)));

			System.out.printf("Sprite %02X%n", i);
			validate(spr);
			//	validateKeyframes(spr);
		}
	}

	private static void testBinary() throws IOException
	{
		for (int i = 1; i <= 0xE9; i++) {
			String name = String.format("%02X", i);
			File f1 = new File(DUMP_SPR_NPC_RAW + name);
			File f2 = new File(DUMP_SPR_NPC_RAW + "rec" + name);

			System.out.println("Testing sprite " + name + "...");

			Sprite spr = SpriteDumper.readBinaryNpc(f1);
			SpritePatcher.writeBinaryNpc(spr, f2);

			assert (f1.length() == f2.length());

			ByteBuffer bb1 = IOUtils.getDirectBuffer(f1);
			ByteBuffer bb2 = IOUtils.getDirectBuffer(f2);
			f2.delete();

			while (bb1.hasRemaining()) {
				assert (bb1.get() == bb2.get());
			}
		}
	}

	private static void testXmlCommandLists() throws IOException
	{
		for (int i = 1; i <= 0xE9; i++) {
			String name = String.format("%02X", i);
			File raw = new File(DUMP_SPR_NPC_RAW + name);
			File xml = new File(DUMP_SPR_NPC_SRC + name + "/SpriteSheet.xml");

			System.out.println("Testing sprite " + name + "...");

			Sprite spr = Sprite.read(xml, SpriteSet.Npc);
			spr.recalculateIndices();

			for (int j = 0; j < spr.animations.size(); j++) {
				SpriteAnimation anim = spr.animations.get(j);
				System.out.println(name + " : Animation " + anim);
				for (int k = 0; k < anim.components.size(); k++) {
					SpriteComponent comp = anim.components.get(k);
					List<Short> originalCmdList = new ArrayList<>(comp.rawAnim);
					for (Short s : originalCmdList)
						System.out.printf("%04X ", s);
					System.out.println();

					comp.generate();
					List<Short> animatorCmdList = comp.animator.getCommandList();

					for (Short s : animatorCmdList)
						System.out.printf("%04X ", s);
					System.out.println();

					assert (originalCmdList.size() == animatorCmdList.size());
					for (int m = 0; m < originalCmdList.size(); m++) {
						short s1 = originalCmdList.get(m);
						short s2 = animatorCmdList.get(m);
						assert (s1 == s2) : String.format("%04X vs %04X ", s1, s2);
					}

					//	for(Short s : animatorCmdList)
					//		System.out.printf("%04X ", s);
					//	System.out.println();

				}
			}

			/*
			SpriteIO.writeBinaryNpc(spr, out);

			assert(raw.length() == out.length());

			ByteBuffer bb1 = IOUtils.getDirectBuffer(raw);
			ByteBuffer bb2 = IOUtils.getDirectBuffer(out);
			out.delete();

			assert(bb1.equals(bb2));
			 */
		}
	}

	private static void testXml() throws IOException
	{
		for (int i = 1; i <= 0xE9; i++) {
			String name = String.format("%02X", i);
			File raw = new File(DUMP_SPR_NPC_RAW + name);
			File xml = new File(DUMP_SPR_NPC_SRC + name + "/SpriteSheet.xml");
			File out = new File(DUMP_SPR_NPC_RAW + "rec" + name);

			System.out.print("Testing sprite " + name + "...");

			Sprite spr = Sprite.read(xml, SpriteSet.Npc);
			spr.recalculateIndices();
			SpritePatcher.writeBinaryNpc(spr, out);

			assert (raw.length() == out.length());

			ByteBuffer bb1 = IOUtils.getDirectBuffer(raw);
			ByteBuffer bb2 = IOUtils.getDirectBuffer(out);
			out.delete();

			assert (bb1.equals(bb2));
			System.out.println(" passed.");
		}
	}
}
