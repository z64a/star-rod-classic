package game.string;

import static app.Directories.*;
import static game.texture.TileFormat.CI_4;
import static org.lwjgl.opengl.GL11.*;
import static renderer.buffers.BufferedMesh.VBO_UV;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;

import org.apache.commons.io.FileUtils;

import app.Directories;
import app.Environment;
import asm.AsmUtils;
import game.map.editor.render.TextureManager;
import game.map.shape.TransformMatrix;
import game.texture.ImageConverter;
import game.texture.Palette;
import game.texture.Texture;
import game.texture.Tile;
import game.texture.TileFormat;
import patcher.RomPatcher;
import renderer.buffers.BufferedMesh;
import renderer.shaders.ShaderManager;
import renderer.shaders.scene.BasicIndexedShader;
import renderer.shaders.scene.BasicSolidShader;
import renderer.shaders.scene.BasicTexturedShader;

public class MessageBoxes
{
	private static BufferedMesh borderMesh;
	private static TransformMatrix modelMatrix;

	public static enum WindowPalette
	{
		// @formatter:off
		Standard_0	("PAL_Standard_0", 0x10CEB0, 0x10DC30, 32, 64),
		Standard_1	("PAL_Standard_1", 0x10CEB0, 0x10DC50, 32, 64),
		Standard_2	("PAL_Standard_2", 0x10CEB0, 0x10DC70, 32, 64),
		Standard_3	("PAL_Standard_3", 0x10CEB0, 0x10DC90, 32, 64),
		Standard_4	("PAL_Standard_4", 0x10CEB0, 0x10DCB0, 32, 64),
		Standard_5	("PAL_Standard_5", 0x10CEB0, 0x10DCD0, 32, 64),
		Standard_6	("PAL_Standard_6", 0x10CEB0, 0x10DCF0, 32, 64),
		Standard_7	("PAL_Standard_7", 0x10CEB0, 0x10DD10, 32, 64),
		Standard_8	("PAL_Standard_8", 0x10CEB0, 0x10DD30, 32, 64),
		Standard_9	("PAL_Standard_9", 0x10CEB0, 0x10DD50, 32, 64),
		Standard_A	("PAL_Standard_A", 0x10CEB0, 0x10DD70, 32, 64),
		Standard_B	("PAL_Standard_B", 0x10CEB0, 0x10DD90, 32, 64),
		Standard_C	("PAL_Standard_C", 0x10CEB0, 0x10DDB0, 32, 64),
		Standard_D	("PAL_Standard_D", 0x10CEB0, 0x10DDD0, 32, 64),
		Standard_E	("PAL_Standard_E", 0x10CEB0, 0x10DDF0, 32, 64),
		Standard_F	("PAL_Standard_F", 0x10CEB0, 0x10DE10, 32, 64),
		Sign		("PAL_WoodSign",   0x10DE30, 0x10E550, 16, 16),
		LampPost	("PAL_StreetSign", 0x10DE30, 0x10E570, 16, 16);
		// @formatter:on

		private WindowPalette(String filename, int imgOffset, int palOffset, int sizeW, int sizeH)
		{
			this.filename = filename;
			this.imgOffset = imgOffset;
			this.offset = palOffset;
			this.sizeW = sizeW;
			this.sizeH = sizeH;
		}

		private final String filename;
		private final int imgOffset;
		private final int offset;
		private final int sizeW;
		private final int sizeH;

		private Palette pal;

		public Color[] getColors()
		{
			return pal.getColors();
		}
	}

	public static enum WindowPart
	{
		// @formatter:off
		Speech_L	("Speech_L", 	0x10CEB0, 32, 64, WindowPalette.Standard_0),
		Speech_M	("Speech_M", 	0x10D2B0, 8,  64, WindowPalette.Standard_0),
		Speech_R	("Speech_R", 	0x10D3B0, 32, 64, WindowPalette.Standard_0),
		SpeechArrow	("SpeechArrow", 0x10D7B0, 16, 16, WindowPalette.Standard_0),

		FrameA_1_1	("FrameA_1_1", 	0x10D830, 8,  8,  WindowPalette.Standard_0),
		FrameA_1_2	("FrameA_1_2", 	0x10D850, 8,  8,  WindowPalette.Standard_0),
		FrameA_1_3	("FrameA_1_3", 	0x10D870, 8,  8,  WindowPalette.Standard_0),
		FrameA_1_4	("FrameA_1_4", 	0x10D890, 8,  8,  WindowPalette.Standard_0),
		FrameA_1_5	("FrameA_1_5", 	0x10D8B0, 8,  8,  WindowPalette.Standard_0),
		FrameA_2_1	("FrameA_2_1", 	0x10D8D0, 8,  8,  WindowPalette.Standard_0),
		FrameA_2_5	("FrameA_2_5", 	0x10D8F0, 8,  8,  WindowPalette.Standard_0),
		FrameA_3_1	("FrameA_3_1", 	0x10D910, 8,  8,  WindowPalette.Standard_0),
		FrameA_3_5	("FrameA_3_5", 	0x10D930, 8,  8,  WindowPalette.Standard_0),
		FrameA_4_1	("FrameA_4_1", 	0x10D950, 8,  8,  WindowPalette.Standard_0),
		FrameA_4_5	("FrameA_4_5", 	0x10D970, 8,  8,  WindowPalette.Standard_0),
		FrameA_5_1	("FrameA_5_1", 	0x10D990, 8,  8,  WindowPalette.Standard_0),
		FrameA_5_2	("FrameA_5_2", 	0x10D9B0, 8,  8,  WindowPalette.Standard_0),
		FrameA_5_3	("FrameA_5_3", 	0x10D9D0, 8,  8,  WindowPalette.Standard_0),
		FrameA_5_4	("FrameA_5_4", 	0x10D9F0, 8,  8,  WindowPalette.Standard_0),
		FrameA_5_5	("FrameA_5_5", 	0x10DA10, 8,  8,  WindowPalette.Standard_0),

		FrameB_1_1	("FrameB_1_1", 	0x10DA30, 8,  8,  WindowPalette.Standard_0),
		FrameB_1_2	("FrameB_1_2", 	0x10DA50, 8,  8,  WindowPalette.Standard_0),
		FrameB_1_3	("FrameB_1_3", 	0x10DA70, 8,  8,  WindowPalette.Standard_0),
		FrameB_1_4	("FrameB_1_4", 	0x10DA90, 8,  8,  WindowPalette.Standard_0),
		FrameB_1_5	("FrameB_1_5", 	0x10DAB0, 8,  8,  WindowPalette.Standard_0),
		FrameB_2_1	("FrameB_2_1", 	0x10DAD0, 8,  8,  WindowPalette.Standard_0),
		FrameB_2_5	("FrameB_2_5", 	0x10DAF0, 8,  8,  WindowPalette.Standard_0),
		FrameB_3_1	("FrameB_3_1", 	0x10DB10, 8,  8,  WindowPalette.Standard_0),
		FrameB_3_5	("FrameB_3_5", 	0x10DB30, 8,  8,  WindowPalette.Standard_0),
		FrameB_4_1	("FrameB_4_1", 	0x10DB50, 8,  8,  WindowPalette.Standard_0),
		FrameB_4_5	("FrameB_4_5", 	0x10DB70, 8,  8,  WindowPalette.Standard_0),
		FrameB_5_1	("FrameB_5_1", 	0x10DB90, 8,  8,  WindowPalette.Standard_0),
		FrameB_5_2	("FrameB_5_2", 	0x10DBB0, 8,  8,  WindowPalette.Standard_0),
		FrameB_5_3	("FrameB_5_3", 	0x10DBD0, 8,  8,  WindowPalette.Standard_0),
		FrameB_5_4	("FrameB_5_4", 	0x10DBF0, 8,  8,  WindowPalette.Standard_0),
		FrameB_5_5	("FrameB_5_5", 	0x10DC10, 8,  8,  WindowPalette.Standard_0),

		Sign_1_1	("Sign_1_1",	0x10DE30, 16, 16, WindowPalette.Sign, WindowPalette.LampPost),
		Sign_1_3	("Sign_1_3",	0x10DEB0, 16, 16, WindowPalette.Sign, WindowPalette.LampPost),
		Sign_3_1	("Sign_3_1",	0x10DF30, 16, 16, WindowPalette.Sign, WindowPalette.LampPost),
		Sign_3_3	("Sign_3_3",	0x10E030, 16, 16, WindowPalette.Sign, WindowPalette.LampPost),
		Sign_1_2	("Sign_1_2",	0x10E0B0, 16, 32, WindowPalette.Sign, WindowPalette.LampPost),
		Sign_2_1	("Sign_2_1",	0x10E1B0, 16, 40, WindowPalette.Sign, WindowPalette.LampPost),
		Sign_2_3	("Sign_2_3",	0x10E2F0, 16, 40, WindowPalette.Sign, WindowPalette.LampPost),
		Sign_3_2	("Sign_3_2",	0x10E430, 16, 32, WindowPalette.Sign, WindowPalette.LampPost),
		Sign_2_2	("Sign_2_2",	0x10E530, 8,  8,  WindowPalette.Sign, WindowPalette.LampPost);
		// @formatter:on

		private WindowPart(String filename, int imgOffset, int sizeW, int sizeH, WindowPalette ... palette)
		{
			this.filename = filename;
			this.sharedPalettes = palette;

			this.imgOffset = imgOffset;

			this.sizeW = sizeW;
			this.sizeH = sizeH;
		}

		private final String filename;
		private final int imgOffset;
		private final int sizeW;
		private final int sizeH;

		private final WindowPalette[] sharedPalettes;
		private Tile tile;

		public void drawBasicQuad(float x, float y)
		{
			drawBasicQuad(x, x + sizeW, y, y + sizeH);
		}

		public void drawBasicQuad(WindowPalette palette, float x, float y)
		{
			drawBasicQuad(palette, x, x + sizeW, y, y + sizeH);
		}

		public void drawBasicQuad(float x1, float x2, float y1, float y2)
		{
			drawBasicQuad(sharedPalettes[0], x1, x2, y1, y2);
		}

		public void drawBasicQuad(WindowPalette palette, float x1, float x2, float y1, float y2)
		{
			BasicIndexedShader shader = ShaderManager.use(BasicIndexedShader.class);
			tile.glBind(shader.texture);
			palette.pal.glBind(shader.palette);

			shader.setQuadTexCoords(0, 0, 1, 1);
			shader.setXYQuadCoords(x1, y2, x2, y1, 0); //TODO y reversed...?
			shader.renderQuad();

			/*

			TriangleRenderQueue.addQuad(
					TriangleRenderQueue.addVertex().setPosition(x1, y1, 0).setUV(0,0).getIndex(),
					TriangleRenderQueue.addVertex().setPosition(x2, y1, 0).setUV(1,0).getIndex(),
					TriangleRenderQueue.addVertex().setPosition(x2, y2, 0).setUV(1,1).getIndex(),
					TriangleRenderQueue.addVertex().setPosition(x1, y2, 0).setUV(0,1).getIndex());

			/*
			TriangleRenderQueue.addVertex().setPosition(x1, y2, 0).setUV(0.05f,0.05f).getIndex(),
			TriangleRenderQueue.addVertex().setPosition(x2, y2, 0).setUV(0.95f,0.05f).getIndex(),
			TriangleRenderQueue.addVertex().setPosition(x2, y1, 0).setUV(0.95f,0.95f).getIndex(),
			TriangleRenderQueue.addVertex().setPosition(x1, y1, 0).setUV(0.05f,0.95f).getIndex());
			/

			TriangleRenderQueue.render(true);
			*/
		}
	}

	public static final WindowPart[][] Sign = {
			{ WindowPart.Sign_1_1, WindowPart.Sign_1_2, WindowPart.Sign_1_3 },
			{ WindowPart.Sign_2_1, WindowPart.Sign_2_2, WindowPart.Sign_2_3 },
			{ WindowPart.Sign_3_1, WindowPart.Sign_3_2, WindowPart.Sign_3_3 }
	};

	public static final WindowPart[][] FrameA = {
			{ WindowPart.FrameA_1_1, WindowPart.FrameA_1_2, WindowPart.FrameA_1_3, WindowPart.FrameA_1_4, WindowPart.FrameA_1_5 },
			{ WindowPart.FrameA_2_1, null, null, null, WindowPart.FrameA_2_5 },
			{ WindowPart.FrameA_3_1, null, null, null, WindowPart.FrameA_3_5 },
			{ WindowPart.FrameA_4_1, null, null, null, WindowPart.FrameA_4_5 },
			{ WindowPart.FrameA_5_1, WindowPart.FrameA_5_2, WindowPart.FrameA_5_3, WindowPart.FrameA_5_4, WindowPart.FrameA_5_5 }
	};

	public static final WindowPart[][] FrameB = {
			{ WindowPart.FrameB_1_1, WindowPart.FrameB_1_2, WindowPart.FrameB_1_3, WindowPart.FrameB_1_4, WindowPart.FrameB_1_5 },
			{ WindowPart.FrameB_2_1, null, null, null, WindowPart.FrameB_2_5 },
			{ WindowPart.FrameB_3_1, null, null, null, WindowPart.FrameB_3_5 },
			{ WindowPart.FrameB_4_1, null, null, null, WindowPart.FrameB_4_5 },
			{ WindowPart.FrameB_5_1, WindowPart.FrameB_5_2, WindowPart.FrameB_5_3, WindowPart.FrameB_5_4, WindowPart.FrameB_5_5 }
	};

	public static enum Graphic
	{
		// @formatter:off
		Scroll_BG	("Scroll_BG",	TileFormat.I_4, 0x10E590, 64, 64),
		RewindArrow	("RewindArrow", TileFormat.CI_4, 0x10ED90, 0x10EEB0, 24, 24),
		NextStar	("NextStar", 	TileFormat.RGBA_16, 0x10EED0, 16, 18),
		NextStarMask	("NextStarMask", 	TileFormat.I_4, 0x10F110, 16, 18),

		Letter_Peach	("Letter_Peach",	TileFormat.CI_8, 0x1164B8, 0x11A240,  150,  105),
		Letter_BG	("Letter_BG",	TileFormat.CI_4, 0x11A440, 0x11C308,  150,  105),

		Letter_00	("Letter_00",	TileFormat.CI_8, 0x11C328, 0x11DD28,  70,  95),
		Letter_01	("Letter_01",	TileFormat.CI_8, 0x11DF28, 0x11F928,  70,  95),
		Letter_02	("Letter_02",	TileFormat.CI_8, 0x11FB28, 0x121528,  70,  95),
		Letter_03	("Letter_03",	TileFormat.CI_8, 0x121728, 0x123128,  70,  95),

		Letter_04	("Letter_04",	TileFormat.CI_8, 0x123328, 0x124D28,  70,  95),
		Letter_05	("Letter_05",	TileFormat.CI_8, 0x124F28, 0x126928,  70,  95),
		Letter_06	("Letter_06",	TileFormat.CI_8, 0x126B28, 0x128528,  70,  95),
		Letter_07	("Letter_07",	TileFormat.CI_8, 0x128728, 0x12A128,  70,  95),

		Letter_08	("Letter_08",	TileFormat.CI_8, 0x12A328, 0x12BD28,  70,  95),
		Letter_09	("Letter_09",	TileFormat.CI_8, 0x12BF28, 0x12D928,  70,  95),
		Letter_0A	("Letter_0A",	TileFormat.CI_8, 0x12DB28, 0x12F528,  70,  95),
		Letter_0B	("Letter_0B",	TileFormat.CI_8, 0x12F728, 0x131128,  70,  95);
		// @formatter:on

		private Graphic(String filename, TileFormat fmt, int imgOffset, int sizeW, int sizeH)
		{
			this.filename = filename;

			this.fmt = fmt;
			this.imgOffset = imgOffset;
			this.palOffset = -1;

			this.sizeW = sizeW;
			this.sizeH = sizeH;
		}

		private Graphic(String filename, TileFormat fmt, int imgOffset, int palOffset, int sizeW, int sizeH)
		{
			this.filename = filename;

			this.fmt = fmt;
			this.imgOffset = imgOffset;
			this.palOffset = palOffset;

			this.sizeW = sizeW;
			this.sizeH = sizeH;
		}

		public final String filename;
		private final int imgOffset;
		private final int palOffset;
		private final int sizeW;
		private final int sizeH;
		private final TileFormat fmt;

		private Tile tile;
		private int glTexID;

		public void drawBasicQuad(float x, float y)
		{
			drawBasicQuad(x, x + sizeW, y, y + sizeH);
		}

		public void drawBasicQuad(float x1, float x2, float y1, float y2)
		{
			BasicTexturedShader shader = ShaderManager.use(BasicTexturedShader.class);
			shader.texture.bind(glTexID);
			shader.setXYQuadCoords(x1, y1, x2, y2, 0);
			shader.setQuadTexCoords(0, 0, 1, 1);
			shader.renderQuad();
		}
	}

	public static final Graphic[] Letters = {
			Graphic.Letter_00,
			Graphic.Letter_01,
			Graphic.Letter_02,
			Graphic.Letter_03,
			Graphic.Letter_04,
			Graphic.Letter_05,
			Graphic.Letter_06,
			Graphic.Letter_07,
			Graphic.Letter_08,
			Graphic.Letter_09,
			Graphic.Letter_0A,
			Graphic.Letter_0B
	};

	public static void dump() throws IOException
	{
		try (RandomAccessFile raf = Environment.getBaseRomReader()) {
			for (WindowPart g : WindowPart.values()) {
				Tile img = new Tile(CI_4, g.sizeH, g.sizeW);
				img.readImage(raf, g.imgOffset, false);
				img.readPalette(raf, g.sharedPalettes[0].offset);

				FileUtils.touch(new File(DUMP_TXTBOX_IMG + g.filename + ".png"));
				img.savePNG(DUMP_TXTBOX_IMG + g.filename);
			}

			for (WindowPalette pal : WindowPalette.values()) {
				Tile img = new Tile(TileFormat.CI_4, pal.sizeH, pal.sizeW);
				img.readImage(raf, pal.imgOffset, false);
				img.readPalette(raf, pal.offset);

				FileUtils.touch(new File(DUMP_TXTBOX_PAL + pal.filename + ".png"));
				img.savePNG(DUMP_TXTBOX_PAL + pal.filename);
			}

			for (Graphic l : Graphic.values()) {
				Tile img = new Tile(l.fmt, l.sizeH, l.sizeW);
				img.readImage(raf, l.imgOffset, false);
				if (l.palOffset >= 0)
					img.readPalette(raf, l.palOffset);

				FileUtils.touch(new File(DUMP_TXTBOX_IMG + l.filename + ".png"));
				img.savePNG(DUMP_TXTBOX_IMG + l.filename);
			}
		}
	}

	public static void patch(RomPatcher rp) throws IOException
	{
		loadImages();

		rp.seek("ChoiceBoxColorFix", 0xC448C);
		Color c = WindowPalette.Standard_0.pal.getColors()[4];
		AsmUtils.assembleAndWrite(true, "ChoiceBoxColorFix", rp, String.format("LI  V0, %02X%02X%02X00",
			c.getRed() & 0xFF, c.getGreen() & 0xFF, c.getBlue() & 0xFF));

		for (WindowPalette p : WindowPalette.values()) {
			rp.seek("MessageBoxPalette", p.offset);
			p.pal.write(rp);
		}

		for (WindowPart g : WindowPart.values()) {
			rp.seek("MessageBoxRaster", g.imgOffset);
			g.tile.writeRaster(rp, false);
		}

		for (Graphic l : Graphic.values()) {
			rp.seek("MessageGraphicRaster", l.imgOffset);
			l.tile.writeRaster(rp, false);

			if (l.palOffset >= 0) {
				rp.seek("MessageGraphicPalette", l.palOffset);
				l.tile.palette.write(rp);
			}
		}
	}

	private static Directories getImageDirectory()
	{
		if (Environment.project.isDecomp)
			return DUMP_TXTBOX_IMG;
		else
			return MOD_TXTBOX_IMG;
	}

	private static Directories getPaletteDirectory()
	{
		if (Environment.project.isDecomp)
			return DUMP_TXTBOX_PAL;
		else
			return MOD_TXTBOX_PAL;
	}

	public static void loadImages() throws IOException
	{
		for (WindowPalette p : WindowPalette.values())
			p.pal = Tile.load(new File(getPaletteDirectory() + p.filename + ".png"), CI_4).palette;

		for (WindowPart g : WindowPart.values())
			g.tile = Tile.load(new File(getImageDirectory() + g.filename + ".png"), CI_4);

		for (Graphic l : Graphic.values())
			l.tile = Tile.load(new File(getImageDirectory() + l.filename + ".png"), l.fmt);
	}

	private static int glLoadImage(BufferedImage bimg)
	{
		ByteBuffer buffer = TextureManager.createByteBuffer(bimg);

		int glID = glGenTextures();
		glBindTexture(GL_TEXTURE_2D, glID);

		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_REPEAT);
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_REPEAT);

		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);

		glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, bimg.getWidth(),
			bimg.getHeight(), 0, GL_RGBA, GL_UNSIGNED_BYTE, buffer);

		return glID;
	}

	public static void glLoad() throws IOException
	{
		for (WindowPalette p : WindowPalette.values())
			p.pal.glLoad();

		for (WindowPart g : WindowPart.values())
			g.tile.glLoad(Texture.WRAP_CLAMP, Texture.WRAP_CLAMP, false);

		for (Graphic l : Graphic.values()) {
			BufferedImage bimg = ImageConverter.convertToBufferedImage(l.tile);
			l.glTexID = glLoadImage(bimg);
		}

		borderMesh = new BufferedMesh(128, 16, VBO_UV);
		modelMatrix = TransformMatrix.identity();
	}

	public static void glDelete()
	{
		for (WindowPalette p : WindowPalette.values())
			p.pal.glDelete();

		for (WindowPart g : WindowPart.values())
			g.tile.glDelete();

		for (Graphic l : Graphic.values())
			glDeleteTextures(l.glTexID);

		borderMesh.glDelete();
	}

	private static float SCROLL_OFFX = 0.0f;
	private static float SCROLL_OFFY = 0.0f;
	private static final float SCROLL_SCALE = 64.0f;

	public static void drawBorder(WindowPart[][] frame, int x1, int x2, int y1, int y2, Color fillColor, long counter)
	{
		int W = 8;
		int H = 8;

		int winX = x2 - x1;
		int winY = y2 - y1;

		if ((winX < 2 * W) || (winY < 2 * H))
			return;

		boolean skipX = (winX < 4 * W);
		boolean skipY = (winY < 4 * H);

		int pos = 0;
		int[] divX = new int[6];
		for (int i = 0; i < divX.length; i++) {
			divX[i] = pos;

			switch (i) {
				case 1:
				case 3:
					if (skipX)
						continue;
				case 0:
				case 4:
					pos += W;
					break;
				case 2:
					if (skipX)
						pos += winX - 2 * W;
					else
						pos += winX - 4 * W;
			}
		}

		pos = 0;
		int[] divY = new int[6];
		for (int i = 0; i < divY.length; i++) {
			divY[i] = pos;

			switch (i) {
				case 1:
				case 3:
					if (skipY)
						continue;
				case 0:
				case 4:
					pos += H;
					break;
				case 2:
					if (skipY)
						pos += winY - 2 * H;
					else
						pos += winY - 4 * H;
			}
		}

		if (fillColor != null) {
			BasicSolidShader shader = ShaderManager.use(BasicSolidShader.class);
			shader.baseColor.set(fillColor.getRed() / 255.0f, fillColor.getGreen() / 255.0f, fillColor.getBlue() / 255.0f, 1.0f);
			shader.setXYQuadCoords(x1 + divX[1], y1 + divY[1], x1 + divX[4], y1 + divY[4], 0);
			shader.renderQuad();
		}
		else {
			int xa = x1 + divX[1];
			int xb = x1 + divX[4];
			int ya = y1 + divY[1];
			int yb = y1 + divY[4];

			SCROLL_OFFX = (counter % 1024) * 12.0f / 1024.0f;
			SCROLL_OFFY = (counter % 1024) * 12.0f / 1024.0f;

			BasicTexturedShader shader = ShaderManager.use(BasicTexturedShader.class);

			shader.texture.bind(Graphic.Scroll_BG.glTexID);

			shader.multiplyBaseColor.set(true);
			shader.baseColor.set(1.0f, 1.0f, 1.0f, 0.75f);

			shader.quadTexScale.set(1 / SCROLL_SCALE, -1 / SCROLL_SCALE);
			shader.quadTexShift.set(SCROLL_OFFX, SCROLL_OFFY);

			//	RenderState.setModelMatrix(modelMatrix);

			/*
			 *          x1    xa                                   xb    x2
			 *       y1       +------------------------------------+
			 *                | (xa,y1)                    (xb,y1) |
			 *       (x1,ya)  |                                    |  (x2,ya)
			 *       ya +-----+------------------------------------+-----+
			 *          |     | (xa,ya)                    (xb,ya) |     |
			 *          |     |                                    |     |
			 *          |     |                                    |     |
			 *          |     |                                    |     |
			 *          |     | (xa,yb)                    (xb,yb) |     |
			 *       yb +-----+------------------------------------+-----+
			 *       (x1,yb)  |                                    |  (x2,yb)
			 *                | (xa,y2)                    (xb,y2) |
			 *       y2       +------------------------------------+
			 */

			borderMesh.clear();

			int vaa = borderMesh.addVertex().setPosition(xa, ya, 0).setUV(xa, ya).getIndex();
			int vba = borderMesh.addVertex().setPosition(xb, ya, 0).setUV(xb, ya).getIndex();
			int vbb = borderMesh.addVertex().setPosition(xb, yb, 0).setUV(xb, yb).getIndex();
			int vab = borderMesh.addVertex().setPosition(xa, yb, 0).setUV(xa, yb).getIndex();

			int v1a = borderMesh.addVertex().setPosition(x1, ya, 0).setUV(x1, ya).getIndex();
			int v1b = borderMesh.addVertex().setPosition(x1, yb, 0).setUV(x1, yb).getIndex();
			int v2a = borderMesh.addVertex().setPosition(x2, ya, 0).setUV(x2, ya).getIndex();
			int v2b = borderMesh.addVertex().setPosition(x2, yb, 0).setUV(x2, yb).getIndex();

			int va1 = borderMesh.addVertex().setPosition(xa, y1, 0).setUV(xa, y1).getIndex();
			int vb1 = borderMesh.addVertex().setPosition(xb, y1, 0).setUV(xb, y1).getIndex();
			int va2 = borderMesh.addVertex().setPosition(xa, y2, 0).setUV(xa, y2).getIndex();
			int vb2 = borderMesh.addVertex().setPosition(xb, y2, 0).setUV(xb, y2).getIndex();

			borderMesh.addQuad(vaa, vab, vbb, vba);
			borderMesh.addQuad(va1, vaa, vba, vb1);
			borderMesh.addQuad(vab, va2, vb2, vbb);
			borderMesh.addQuad(v1a, v1b, vab, vaa);
			borderMesh.addQuad(vba, vbb, v2b, v2a);

			borderMesh.addTriangle(v1a, vaa, va1);
			borderMesh.addTriangle(v1b, va2, vab);
			borderMesh.addTriangle(vbb, vb2, v2b);
			borderMesh.addTriangle(vb1, vba, v2a);

			borderMesh.loadBuffers();
			borderMesh.renderWithTransform(modelMatrix);

			glDrawArrays(GL_TRIANGLES, 0, (6 * 5) + (3 * 4));
		}

		for (int i = 0; i < 5; i++) {
			for (int j = 0; j < 5; j++) {
				WindowPart part = frame[j][i];
				if (part == null)
					continue;

				part.drawBasicQuad(x1 + divX[i], x1 + divX[i + 1], y1 + divY[j], y1 + divY[j + 1]);
			}
		}
	}
}
