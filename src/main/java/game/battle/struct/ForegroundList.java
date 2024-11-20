package game.battle.struct;

import java.io.PrintWriter;
import java.nio.ByteBuffer;

import game.shared.BaseStruct;
import game.shared.decoder.BaseDataDecoder;
import game.shared.decoder.Pointer;

public class ForegroundList extends BaseStruct
{
	public static final ForegroundList instance = new ForegroundList();

	private ForegroundList()
	{}

	@Override
	public void scan(BaseDataDecoder decoder, Pointer ptr, ByteBuffer fileBuffer)
	{
		while (fileBuffer.getInt() != 0)
			; // keep going
	}

	@Override
	public void print(BaseDataDecoder decoder, Pointer ptr, ByteBuffer fileBuffer, PrintWriter pw)
	{
		for (int i = 0; i < ptr.getSize(); i += 4) {
			int v = fileBuffer.getInt();

			if (v != 0)
				decoder.printModelID(ptr, pw, v);
			else
				decoder.printWord(pw, v);

			pw.println();
		}
	}
}
