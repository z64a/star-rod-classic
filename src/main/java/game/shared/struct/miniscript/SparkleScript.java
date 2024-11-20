package game.shared.struct.miniscript;

import java.io.PrintWriter;
import java.nio.ByteBuffer;
import java.util.Iterator;
import java.util.List;

import app.input.InvalidInputException;
import game.shared.decoder.BaseDataDecoder;
import game.shared.decoder.Pointer;
import game.shared.encoder.BaseDataEncoder;
import game.texture.TileFormat;
import patcher.RomPatcher;

public class SparkleScript extends Miniscript
{
	public static final SparkleScript instance = new SparkleScript();

	/*
	0	Free			( )						frees current entity model
	1	Draw			( holdTime, Gfx* )		ENDS
	2	Restart			( )
	3	Jump			( pointer)
	4	SetRenderMode	( renderMode )
	5	SetFlags		( bits )
	6	ClearFlags		( bits )
	7	AppendGfx		( holdTime, Gfx[4] )	ENDS
	*/

	private SparkleScript()
	{
		// @formatter:off
		super("SparkleScript",
			new CommandBuilder(0, "End",			ENDS),
			new CommandBuilder(1, "op_01",			new DecArg(false), new HexArg()),
			new CommandBuilder(2, "Restart"),
			new CommandBuilder(3, "Jump",			new HexArg()), // new PointerArg(EntityModelCommandListT)),
			new CommandBuilder(4, "op_04",			new HexArg()),
			new CommandBuilder(7, "SetCI",			BLOCKS, new DecArg(), new SparkleCIArg())
			);
		// @formatter:on
	}

	public static class SparkleCIArg extends MiniCommandArg
	{
		public SparkleCIArg()
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
			int raster = fileBuffer.getInt();
			int palette = fileBuffer.getInt();
			int sizeX = fileBuffer.getInt();
			int sizeY = fileBuffer.getInt();

			decoder.addImage(ptr, fileBuffer, TileFormat.CI_4, raster, palette, sizeX, sizeY);
		}

		@Override
		public void print(Miniscript context, BaseDataDecoder decoder, Pointer ptr, ByteBuffer fileBuffer, PrintWriter pw)
		{
			int raster = fileBuffer.getInt();
			int palette = fileBuffer.getInt();
			int sizeX = fileBuffer.getInt();
			int sizeY = fileBuffer.getInt();

			decoder.printScriptWord(pw, raster);
			decoder.printScriptWord(pw, palette);
			pw.printf("%d` %d` ", sizeX, sizeY);
		}

		@Override
		public void parse(Miniscript context, BaseDataEncoder encoder, Iterator<String> in, List<String> out)
			throws NotEnoughArgsException, InvalidInputException
		{
			if (!in.hasNext())
				throw new NotEnoughArgsException();
			String img = in.next();
			out.add(img);

			if (!in.hasNext())
				throw new NotEnoughArgsException();
			String pal = in.next();
			out.add(pal);

			if (!in.hasNext())
				throw new NotEnoughArgsException();
			String sizeX = in.next();
			out.add(sizeX);

			if (!in.hasNext())
				throw new NotEnoughArgsException();
			String sizeY = in.next();
			out.add(sizeY);
		}
	}
}
