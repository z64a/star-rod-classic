package game.shared.struct.miniscript;

import static game.shared.StructTypes.IntTableT;

import java.io.PrintWriter;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import app.StarRodException;
import app.input.InvalidInputException;
import game.shared.ProjectDatabase;
import game.shared.SyntaxConstants;
import game.shared.decoder.BaseDataDecoder;
import game.shared.decoder.Pointer;
import game.shared.decoder.PointerHeuristic;
import game.shared.encoder.BaseDataEncoder;
import game.texture.TileFormat;
import game.texture.images.ImageDatabase;
import game.texture.images.ImageDatabase.DecodedImageAsset;
import game.texture.images.ImageDatabase.EncodedImageAsset;
import patcher.RomPatcher;
import util.Logger;

public class HudElementScript extends Miniscript
{
	public static final int[][] ICON_SIZE_PRESETS = { // x, y, size
			{ 0x0008, 0x0008, 0x0020 },
			{ 0x0010, 0x0010, 0x0080 },
			{ 0x0018, 0x0018, 0x0120 },
			{ 0x0020, 0x0020, 0x0200 },
			{ 0x0030, 0x0030, 0x0480 },
			{ 0x0040, 0x0040, 0x0800 },
			{ 0x0008, 0x0010, 0x0040 },
			{ 0x0010, 0x0008, 0x0040 },
			{ 0x0010, 0x0018, 0x00C0 },
			{ 0x0010, 0x0020, 0x0100 },
			{ 0x0040, 0x0020, 0x0400 },
			{ 0x0020, 0x0010, 0x0100 },
			{ 0x000C, 0x000C, 0x0048 },
			{ 0x0030, 0x0018, 0x0240 },
			{ 0x0020, 0x0008, 0x0080 },
			{ 0x0018, 0x0008, 0x0060 },
			{ 0x0040, 0x0010, 0x0200 },
			{ 0x0010, 0x0040, 0x0200 },
			{ 0x00C0, 0x0020, 0x0C00 },
			{ 0x0028, 0x0028, 0x0320 },
			{ 0x0018, 0x0010, 0x00C0 },
			{ 0x0020, 0x0028, 0x0280 },
			{ 0x0028, 0x0010, 0x0140 },
			{ 0x0028, 0x0018, 0x01E0 },
			{ 0x0020, 0x0018, 0x0180 }
	};

	public static final HudElementScript instance = new HudElementScript();

	private HudElementScript()
	{
		// @formatter:off
		super("HudElementScript",
			new CommandBuilder(0x00, "End",				ENDS),
			new CommandBuilder(0x01, "SetRGBA",			BLOCKS, new DecArg(), new ImageRGBAArg()),
			new CommandBuilder(0x02, "SetCI",			BLOCKS, new DecArg(), new ImageCIArg()),

			new CommandBuilder(0x03, "Restart",			INDENT_LESS_NOW),
			new CommandBuilder(0x04, "Loop",			INDENT_MORE_AFTER),

			new CommandBuilder(0x05, "SetTileSize",			new IconSizeArg()),
			new CommandBuilder(0x06, "SetSizesAutoScale",	new IconSizeArg(), new EnumArg("IconSize")),	// tile size, draw size

			new CommandBuilder(0x07, "SetSizesFixedScale",	new IconSizeArg(), new EnumArg("IconSize")),	// tile size, draw size

			new CommandBuilder(0x08, "SetVisible"),
			new CommandBuilder(0x09, "SetHidden"),

			new CommandBuilder(0x0A, "AddTexelOffsetX",	new DecArg()),
			new CommandBuilder(0x0B, "AddTexelOffsetY",	new DecArg()),
			new CommandBuilder(0x0C, "SetTexelOffset",	new DecArg(), new DecArg()),

			new CommandBuilder(0x0D, IconImageArg.SET_ICON_IMG_CMD_NAME, new DecArg(false), new IconImageArg()),

			//  delay
			new CommandBuilder(0x0E, "SetScale",		new Fixed16Arg()),
			new CommandBuilder(0x0F, "SetAlpha",		new DecArg()),

			new CommandBuilder(0x10, "RandomDelay",		new DecArg(), new DecArg()), // min, max

			new CommandBuilder(0x11, "Delete",			ENDS),
			new CommandBuilder(0x12, "UseIA8").setScanCallback((c)-> {
				HudElementScript script = (HudElementScript)c;
				script.intensityMode = true;
			}),

			new CommandBuilder(0x13, "SetCustomSize",	new SizeXArg(), new SizeYArg()),
			new CommandBuilder(0x14, "RandomRestart",	new DecArg(), new DecArg()), // random max, cutoff (< randmax)
			new CommandBuilder(0x15, "op_15",			new HexArg()),
			// 16 is not used

			new CommandBuilder(0x17, "RandomBranch",	VARARGS, new BranchListArg()),

			new CommandBuilder(0x18, "SetFlags",		new HexArg()),
			new CommandBuilder(0x19, "ClearFlags",		new HexArg()),
			new CommandBuilder(0x1A, "PlaySound",		new EnumArg("Sound")),
			new CommandBuilder(0x1B, "SetRotPivotOffset",	new HexArg(), new HexArg())
			);
		// @formatter:on
	}

	private boolean intensityMode = false;
	private int imgSizeX = -1;
	private int imgSizeY = -1;

	@Override
	public void scan(BaseDataDecoder decoder, Pointer ptr, ByteBuffer fileBuffer)
	{
		intensityMode = false;
		imgSizeX = -1;
		imgSizeY = -1;
		super.scan(decoder, ptr, fileBuffer);
	}

	public static class Fixed16Arg extends MiniCommandArg
	{
		// arg is divided by 65536 and cast to float (e.g. 8000 --> 0.5)
		public Fixed16Arg()
		{}

		@Override
		public void consume(RomPatcher rp)
		{
			rp.readInt();
		}

		@Override
		public void scan(Miniscript context, BaseDataDecoder decoder, Pointer ptr, ByteBuffer fileBuffer)
		{
			fileBuffer.getInt();
		}

		@Override
		public void print(Miniscript context, BaseDataDecoder decoder, Pointer ptr, ByteBuffer fileBuffer, PrintWriter pw)
		{
			float decoded = (fileBuffer.getInt() / 65536.0f);
			pw.print(decoded + " ");
		}

		@Override
		public void parse(Miniscript context, BaseDataEncoder encoder, Iterator<String> in, List<String> out) throws NotEnoughArgsException
		{
			if (!in.hasNext())
				throw new NotEnoughArgsException();

			float decoded = Float.parseFloat(in.next());
			out.add(String.format("%08X", Math.round(decoded * 65536.0f)));
		}
	}

	public static class IconSizeArg extends EnumArg
	{
		public IconSizeArg()
		{
			super("IconSize");
		}

		@Override
		public void scan(Miniscript context, BaseDataDecoder decoder, Pointer ptr, ByteBuffer fileBuffer)
		{
			int preset = fileBuffer.getInt();

			HudElementScript script = (HudElementScript) context;
			script.imgSizeX = ICON_SIZE_PRESETS[preset][0];
			script.imgSizeY = ICON_SIZE_PRESETS[preset][1];
		}
	}

	public static class SizeXArg extends DecArg
	{
		@Override
		public void scan(Miniscript context, BaseDataDecoder decoder, Pointer ptr, ByteBuffer fileBuffer)
		{
			HudElementScript script = (HudElementScript) context;
			script.imgSizeX = fileBuffer.getInt();
		}
	}

	public static class SizeYArg extends DecArg
	{
		@Override
		public void scan(Miniscript context, BaseDataDecoder decoder, Pointer ptr, ByteBuffer fileBuffer)
		{
			HudElementScript script = (HudElementScript) context;
			script.imgSizeY = fileBuffer.getInt();
		}
	}

	public static class ImageCIArg extends MiniCommandArg
	{
		public static final String IMAGE_CI_EXPR = SyntaxConstants.EXPRESSION_PREFIX + "ImageCI";

		public ImageCIArg()
		{}

		@Override
		public void consume(RomPatcher rp)
		{
			rp.readInt();
			rp.readInt();
		}

		@Override
		public void scan(Miniscript context, BaseDataDecoder decoder, Pointer ptr, ByteBuffer fileBuffer)
		{
			int raster = fileBuffer.getInt();
			int palette = fileBuffer.getInt();

			HudElementScript script = (HudElementScript) context;

			if (script.imgSizeX > 0 && script.imgSizeY > 0)
				decoder.addImage(ptr, fileBuffer, TileFormat.CI_4, raster, palette, script.imgSizeX, script.imgSizeY);
		}

		@Override
		public void print(Miniscript context, BaseDataDecoder decoder, Pointer ptr, ByteBuffer fileBuffer, PrintWriter pw)
		{
			int imgPtr = fileBuffer.getInt();
			int palPtr = fileBuffer.getInt();

			int imgOffset = ProjectDatabase.rom.getOffset(decoder.getScope(), imgPtr);
			int palOffset = ProjectDatabase.rom.getOffset(decoder.getScope(), palPtr);

			DecodedImageAsset ref = ProjectDatabase.images.getImage(imgOffset, palOffset);
			if (ref == null) {
				Logger.logfError("No image entry found for: %08X %08X (%s)", imgPtr, palPtr, decoder.getScope());
				pw.print(String.format("%08X %08X ", imgPtr, palPtr));
				context.appendLineComment("ERROR: No image entry found for offsets!");
			}
			else {
				pw.print(IMAGE_CI_EXPR + ":" + ref.name + " ");
				context.appendLineComment(String.format("aka: %08X %08X", imgPtr, palPtr));
			}
		}

		@Override
		public void parse(Miniscript context, BaseDataEncoder encoder, Iterator<String> in, List<String> out)
			throws NotEnoughArgsException, InvalidInputException
		{
			if (!in.hasNext())
				throw new NotEnoughArgsException();
			String img = in.next();

			if (img.startsWith(IMAGE_CI_EXPR + ":")) {
				String filename = img.substring(img.indexOf(":") + 1);
				EncodedImageAsset ref = ProjectDatabase.images.getImage(filename);
				if (ref == null)
					throw new StarRodException("No image found with name: %s", filename);
				if (ref.fmt != TileFormat.CI_4)
					throw new StarRodException("%s must be in CI-4 format to use with SetCI!", filename);

				if (ref.outImgAddress == ImageDatabase.NO_DATA)
					throw new StarRodException("Could not determine address for image during SetCI: %s", filename);
				out.add(String.format("%08X", ref.outImgAddress));

				if (ref.outPalAddress == ImageDatabase.NO_DATA)
					throw new StarRodException("Could not determine address for palette during SetCI: %s", filename);
				out.add(String.format("%08X", ref.outPalAddress));
			}
			else {
				out.add(img);
				if (!in.hasNext())
					throw new NotEnoughArgsException();
				String pal = in.next();
				out.add(pal);
			}
		}
	}

	public static class ImageRGBAArg extends HexArg
	{
		public static final String IMAGE_RGBA_EXPR = SyntaxConstants.EXPRESSION_PREFIX + "ImageRGBA";

		@Override
		public void scan(Miniscript context, BaseDataDecoder decoder, Pointer ptr, ByteBuffer fileBuffer)
		{
			int raster = fileBuffer.getInt();

			HudElementScript script = (HudElementScript) context;

			if (script.imgSizeX > 0 && script.imgSizeY > 0)
				decoder.addImage(ptr, fileBuffer,
					script.intensityMode ? TileFormat.IA_8 : TileFormat.RGBA_32,
					raster, 0, script.imgSizeX, script.imgSizeY);

			if (raster != 0)
				decoder.tryEnqueueAsChild(ptr, raster, IntTableT);
		}

		@Override
		public void print(Miniscript context, BaseDataDecoder decoder, Pointer ptr, ByteBuffer fileBuffer, PrintWriter pw)
		{
			int imgPtr = fileBuffer.getInt();
			int imgOffset = ProjectDatabase.rom.getOffset(decoder.getScope(), imgPtr);

			DecodedImageAsset rec = ProjectDatabase.images.getImage(imgOffset);
			if (rec == null) {
				Logger.logfError("No image entry found for: %08X", imgPtr);
				pw.print(String.format("%08X ", imgPtr));
				context.appendLineComment("ERROR: No image entry found for offset!");
			}
			else {
				pw.print(IMAGE_RGBA_EXPR + ":" + rec.name + " ");
				context.appendLineComment(String.format("aka: %08X", imgPtr));
			}
		}

		@Override
		public void parse(Miniscript context, BaseDataEncoder encoder, Iterator<String> in, List<String> out)
			throws NotEnoughArgsException, InvalidInputException
		{
			if (!in.hasNext())
				throw new NotEnoughArgsException();
			String img = in.next();

			if (img.startsWith(IMAGE_RGBA_EXPR + ":")) {
				String filename = img.substring(img.indexOf(":") + 1);
				EncodedImageAsset ref = ProjectDatabase.images.getImage(filename);
				if (ref == null)
					throw new StarRodException("No image found with name: %s", filename);
				if (ref.fmt == TileFormat.CI_4)
					throw new StarRodException("%s must not be in CI-4 format when used with SetRGBA!", filename);
				if (ref.outImgAddress == ImageDatabase.NO_DATA)
					throw new StarRodException("Could not determine address for image during SetRGBA: %s", filename);
				out.add(String.format("%08X", ref.outImgAddress));
			}
			else {
				out.add(img);
			}
		}
	}

	public static class BranchListArg extends MiniCommandArg
	{
		public BranchListArg()
		{}

		@Override
		public void consume(RomPatcher rp)
		{
			int count = rp.readInt();
			while (count-- > 0)
				rp.readInt();
		}

		@Override
		public void scan(Miniscript context, BaseDataDecoder decoder, Pointer ptr, ByteBuffer fileBuffer)
		{
			int count = fileBuffer.getInt();
			while (count-- > 0)
				fileBuffer.getInt();
		}

		@Override
		public void print(Miniscript context, BaseDataDecoder decoder, Pointer ptr, ByteBuffer fileBuffer, PrintWriter pw)
		{
			int count = fileBuffer.getInt();
			while (count-- > 0)
				decoder.printScriptWord(pw, fileBuffer.getInt());
		}

		@Override
		public void parse(Miniscript context, BaseDataEncoder encoder, Iterator<String> in, List<String> out) throws NotEnoughArgsException
		{
			List<String> temp = new ArrayList<>();
			while (in.hasNext())
				temp.add(in.next());

			out.add(String.format("%08X", temp.size()));
			out.addAll(temp);
		}
	}

	public static boolean isHudElement(PointerHeuristic h, ByteBuffer fileBuffer)
	{
		fileBuffer.position(h.start);
		if (fileBuffer.remaining() < 4)
			return false;

		int next = fileBuffer.getInt();
		if (next != 8 && next != 0x12)
			return false;

		boolean done = false;
		while (!done) {
			int size;
			switch (next) {
				case 0:
				case 0x11:
					// only resolve scripts which are sufficiently long
					return (fileBuffer.position() - h.start) > 0x18;
				case 1:
					if (fileBuffer.remaining() < 4 * 2 || fileBuffer.getInt() > 1024)
						return false;
					fileBuffer.getInt(); // RGBA img ptr
					break;
				case 2:
					if (fileBuffer.remaining() < 4 * 3 || fileBuffer.getInt() > 1024)
						return false;
					fileBuffer.getInt(); // CI img ptr
					fileBuffer.getInt(); // CI pal ptr
					break;
				case 0xD:
					if (fileBuffer.remaining() < 4 * 5 || fileBuffer.getInt() > 1024)
						return false;
					fileBuffer.getInt(); // CI img ptr
					fileBuffer.getInt(); // CI pal ptr
					fileBuffer.getInt();
					fileBuffer.getInt();
					break;
				case 5:
					if (fileBuffer.remaining() < 4)
						return false;
					size = fileBuffer.getInt();
					if (size < 0 || size > 0x18)
						return false;
					break;
				case 6:
				case 7:
					if (fileBuffer.remaining() < 4 * 2)
						return false;
					size = fileBuffer.getInt();
					if (size < 0 || size > 0x18)
						return false;
					size = fileBuffer.getInt();
					if (size < 0 || size > 0x18)
						return false;
					break;
				case 0x13:
					if (fileBuffer.remaining() < 4 * 2)
						return false;
					size = fileBuffer.getInt();
					if (size <= 0 || size > 1024)
						return false;
					size = fileBuffer.getInt();
					if (size <= 0 || size > 1024)
						return false;
					break;
				case 3:
				case 4:
				case 8:
				case 9:
				case 0x12:
					break;
				case 0xA:
				case 0xB:
				case 0xE:
				case 0xF:
				case 0x15:
				case 0x18:
				case 0x19:
				case 0x1A:
					if (fileBuffer.remaining() < 4)
						return false;
					fileBuffer.getInt();
					break;
				case 0xC:
				case 0x10: // count check arg0 > arg1
				case 0x14: // count check arg0 > arg1
				case 0x1B:
					if (fileBuffer.remaining() < 4 * 2)
						return false;
					fileBuffer.getInt();
					fileBuffer.getInt();
					break;
				case 0x17:
					if (fileBuffer.remaining() < 4)
						return false;
					int count = fileBuffer.getInt();
					if (fileBuffer.remaining() < 4 * count)
						return false;
					for (int i = 0; i < count; i++)
						fileBuffer.getInt();
				default:
					return false;
			}

			if ((fileBuffer.position() - h.start) > 0x400)
				return false; // too long, reject it

			if (fileBuffer.remaining() < 4)
				return false;
			next = fileBuffer.getInt();
		}
		return false;
	}
}
