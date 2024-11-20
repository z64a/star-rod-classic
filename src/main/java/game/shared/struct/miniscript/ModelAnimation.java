package game.shared.struct.miniscript;

import java.io.PrintWriter;
import java.nio.ByteBuffer;
import java.util.Iterator;
import java.util.List;

import game.shared.decoder.BaseDataDecoder;
import game.shared.decoder.Pointer;
import game.shared.encoder.BaseDataEncoder;
import patcher.RomPatcher;

public class ModelAnimation extends Miniscript
{
	public static final ModelAnimation instance = new ModelAnimation();

	private ModelAnimation()
	{
		// @formatter:off
		super("ModelAnimation", U16_OPCODES,
			new CommandBuilder(0x00, "End",			ENDS),
			new CommandBuilder(0x01, "Wait",		BLOCKS, new HexArg(16)),

			new CommandBuilder(0x03, "RestoreReadPos"),

			new CommandBuilder(0x05, "SetRotation",	new HexArg(16), new Fixed180Arg(), new Fixed180Arg(), new Fixed180Arg()),
			new CommandBuilder(0x06, "AddRotation",	new HexArg(16), new Fixed180Arg(), new Fixed180Arg(), new Fixed180Arg()),
			new CommandBuilder(0x08, "SetPosition",	new HexArg(16), new DecArg(true, 16), new DecArg(true, 16), new DecArg(true, 16)),

			new CommandBuilder(0xA, "SaveReadPos"),

			new CommandBuilder(0x0E, "SetAnimatorFlags",	new HexArg()),
			new CommandBuilder(0x0F, "SetFlags",	new HexArg(16), new HexArg()),
			new CommandBuilder(0x10, "ClearFlags",	new HexArg(16), new HexArg()),
			new CommandBuilder(0x11, "SetScale",	new HexArg(16), new Fixed180Arg(), new Fixed180Arg(), new Fixed180Arg()),
			new CommandBuilder(0x12, "SetRenderMode",	new EnumArg("RenderMode")),
			new CommandBuilder(0x13, "DisableMirroring")
			);
		// @formatter:on
	}

	public static class Fixed180Arg extends MiniCommandArg
	{
		private static final double SCALE = 180.0 / 32766.0;

		public Fixed180Arg()
		{}

		@Override
		public void consume(RomPatcher rp)
		{
			rp.readShort();
		}

		@Override
		public void scan(Miniscript context, BaseDataDecoder decoder, Pointer ptr, ByteBuffer fileBuffer)
		{
			fileBuffer.getShort();
		}

		@Override
		public void print(Miniscript context, BaseDataDecoder decoder, Pointer ptr, ByteBuffer fileBuffer, PrintWriter pw)
		{
			float factor = 200.0f;
			float decoded;

			int value = fileBuffer.getShort();
			if (value >= 0)
				decoded = (float) (Math.ceil((value * SCALE) * factor) / factor);
			else
				decoded = (float) (Math.floor((value * SCALE) * factor) / factor);

			pw.print(decoded + " ");
		}

		@Override
		public void parse(Miniscript context, BaseDataEncoder encoder, Iterator<String> in, List<String> out) throws NotEnoughArgsException
		{
			if (!in.hasNext())
				throw new NotEnoughArgsException();

			out.add(String.format("%04Xs", (int) (Float.parseFloat(in.next()) / SCALE)));
		}
	}

	public static void main(String[] args)
	{
		double SCALE = 180.0 / 32768.0;

		float factor = 250.0f;
		int limit = 0x8000;

		int failed = 0;
		int count = 0;

		for (int i = -limit; i <= limit; i++) {
			float encoded;

			if (i >= 0)
				encoded = (float) (Math.ceil((i * SCALE) * factor) / factor);
			else
				encoded = (float) (Math.floor((i * SCALE) * factor) / factor);

			int decoded = (int) (encoded / SCALE);

			System.out.printf("%04X : %-12f : %04X %n", i, encoded, decoded);

			count++;
			if (i != decoded)
				failed++;
		}

		float fail = (float) failed / count;
		System.out.printf("ERROR RATE: %f%% (%f)%n", fail * 100.0f, Math.log10(fail));
	}
}
