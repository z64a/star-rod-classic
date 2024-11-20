package game.battle.struct;

import static game.shared.StructTypes.AsciiT;
import static game.shared.StructTypes.StageT;

import java.io.PrintWriter;
import java.nio.ByteBuffer;

import game.shared.BaseStruct;
import game.shared.decoder.BaseDataDecoder;
import game.shared.decoder.Pointer;

public class StageTable extends BaseStruct
{
	public static final StageTable instance = new StageTable();

	private StageTable()
	{}

	@Override
	public void scan(BaseDataDecoder decoder, Pointer ptr, ByteBuffer fileBuffer)
	{
		int v;
		while ((v = fileBuffer.getInt()) != 0) {
			decoder.enqueueAsChild(ptr, v, AsciiT);
			decoder.enqueueAsChild(ptr, fileBuffer.getInt(), StageT);
		}
		fileBuffer.getInt();
	}

	@Override
	public void print(BaseDataDecoder decoder, Pointer ptr, ByteBuffer fileBuffer, PrintWriter pw)
	{
		decoder.printHex(ptr, fileBuffer, pw, 2);
	}
}
