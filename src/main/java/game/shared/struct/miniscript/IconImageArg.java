package game.shared.struct.miniscript;

import java.io.PrintWriter;
import java.nio.ByteBuffer;
import java.util.Iterator;
import java.util.List;

import app.StarRodException;
import app.input.InvalidInputException;
import game.ROM.EOffset;
import game.shared.ProjectDatabase;
import game.shared.StructTypes;
import game.shared.SyntaxConstants;
import game.shared.decoder.BaseDataDecoder;
import game.shared.decoder.Pointer;
import game.shared.encoder.BaseDataEncoder;
import game.shared.struct.miniscript.Miniscript.MiniCommandArg;
import game.texture.TileFormat;
import game.texture.images.ImageDatabase.DecodedImageAsset;
import game.texture.images.ImageDatabase.EncodedImageAsset;
import patcher.RomPatcher;
import util.Logger;

public class IconImageArg extends MiniCommandArg
{
	public static final String SET_ICON_IMG_CMD_NAME = "SetIcon";
	public static final String IMAGE_ICON_EXPR = SyntaxConstants.EXPRESSION_PREFIX + "ImageIcon";

	public IconImageArg()
	{}

	@Override
	public void consume(RomPatcher rp)
	{
		rp.readInt();
		rp.readInt();
		rp.readInt();
		rp.readInt();
	}

	@Override
	public void scan(Miniscript context, BaseDataDecoder decoder, Pointer ptr, ByteBuffer fileBuffer)
	{
		int iconBase = ProjectDatabase.rom.getOffset(EOffset.ICON_BASE);

		int A = fileBuffer.getInt() + iconBase;
		int B = fileBuffer.getInt() + iconBase;
		int C = fileBuffer.getInt();
		int D = fileBuffer.getInt();
		assert (C == 0);
		assert (D == 0);

		Integer ptrImg = ProjectDatabase.rom.getAddress(A);
		Integer ptrPal = ProjectDatabase.rom.getAddress(B);

		if (ptrImg != null)
			decoder.tryEnqueueAsChild(ptr, ptrImg, StructTypes.DataTableT);
		if (ptrPal != null)
			decoder.tryEnqueueAsChild(ptr, ptrPal, StructTypes.DataTableT);
	}

	@Override
	public void print(Miniscript context, BaseDataDecoder decoder, Pointer ptr, ByteBuffer fileBuffer, PrintWriter pw)
	{
		int iconBase = ProjectDatabase.rom.getOffset(EOffset.ICON_BASE);

		int A = fileBuffer.getInt();
		int B = fileBuffer.getInt();
		int C = fileBuffer.getInt();
		int D = fileBuffer.getInt();
		int img = iconBase + A;
		int pal = iconBase + B;

		DecodedImageAsset ref = ProjectDatabase.images.getImage(img, pal);
		if (ref == null) {
			Logger.logfError("No image entry found for offsets: %06X %06X", img, pal);
			pw.print(String.format("%08X %08X ", A, B));
		}
		else {
			pw.print(IMAGE_ICON_EXPR + ":" + ref.name + " ");
			context.appendLineComment(String.format("aka: %08X %08X", img, pal));
		}

		if (C != 0 || D != 0)
			pw.print(String.format("%08X %08X ", C, D));
	}

	@Override
	public void parse(Miniscript context, BaseDataEncoder encoder, Iterator<String> in, List<String> out) throws NotEnoughArgsException, InvalidInputException
	{
		if (!in.hasNext())
			throw new NotEnoughArgsException();
		String img = in.next();

		if (img.startsWith(IMAGE_ICON_EXPR + ":")) {
			int iconBase = ProjectDatabase.rom.getOffset(EOffset.ICON_BASE);

			String filename = img.substring(img.indexOf(":") + 1);
			EncodedImageAsset ref = ProjectDatabase.images.getImage(filename);
			if (ref == null)
				throw new StarRodException("No image found with name: %s", filename);
			if (ref.fmt != TileFormat.CI_4)
				throw new StarRodException("%s must be in CI-4 format to use with %s!", filename, SET_ICON_IMG_CMD_NAME);
			out.add(String.format("%08X", ref.outImgOffset - iconBase));
			out.add(String.format("%08X", ref.outPalOffset - iconBase));
		}
		else {
			out.add(img);
			if (!in.hasNext())
				throw new NotEnoughArgsException();
			out.add(in.next()); // pal
		}

		if (in.hasNext()) {
			out.add(in.next()); // C
			if (!in.hasNext())
				throw new NotEnoughArgsException();
			out.add(in.next()); // D
		}
		else {
			out.add("0");
			out.add("0");
		}
	}
}
