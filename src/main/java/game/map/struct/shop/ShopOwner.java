package game.map.struct.shop;

import static game.shared.StructTypes.IntTableT;
import static game.shared.StructTypes.ScriptT;

import java.io.PrintWriter;
import java.nio.ByteBuffer;

import game.shared.BaseStruct;
import game.shared.decoder.BaseDataDecoder;
import game.shared.decoder.Pointer;

public class ShopOwner extends BaseStruct
{
	public static final ShopOwner instance = new ShopOwner();

	private ShopOwner()
	{}

	@Override
	public void scan(BaseDataDecoder decoder, Pointer ptr, ByteBuffer fileBuffer)
	{
		fileBuffer.getInt();
		fileBuffer.getInt(); // animation 1
		fileBuffer.getInt(); // animation 2
		int ptrScript = fileBuffer.getInt();
		fileBuffer.getInt();
		fileBuffer.getInt();
		int ptrStrings = fileBuffer.getInt();

		decoder.enqueueAsChild(ptr, ptrScript, ScriptT);
		decoder.enqueueAsChild(ptr, ptrStrings, IntTableT);
	}

	@Override
	public void print(BaseDataDecoder decoder, Pointer ptr, ByteBuffer fileBuffer, PrintWriter pw)
	{
		decoder.printHex(ptr, fileBuffer, pw, 8);
	}
}
