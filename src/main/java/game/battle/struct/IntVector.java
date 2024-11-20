package game.battle.struct;

import java.io.PrintWriter;
import java.nio.ByteBuffer;

import game.shared.BaseStruct;
import game.shared.decoder.BaseDataDecoder;
import game.shared.decoder.Pointer;

public class IntVector extends BaseStruct
{
	public static final IntVector instance = new IntVector();

	private IntVector()
	{}

	@Override
	public void scan(BaseDataDecoder decoder, Pointer ptr, ByteBuffer fileBuffer)
	{
		fileBuffer.getInt();
		fileBuffer.getInt();
		fileBuffer.getInt();
	}

	@Override
	public void print(BaseDataDecoder decoder, Pointer ptr, ByteBuffer fileBuffer, PrintWriter pw)
	{
		pw.print(fileBuffer.getInt() + "` ");
		pw.print(fileBuffer.getInt() + "` ");
		pw.println(fileBuffer.getInt() + "`");
	}
}
