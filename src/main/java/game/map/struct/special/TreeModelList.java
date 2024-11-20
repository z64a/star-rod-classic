package game.map.struct.special;

import java.io.PrintWriter;
import java.nio.ByteBuffer;

import game.shared.BaseStruct;
import game.shared.decoder.BaseDataDecoder;
import game.shared.decoder.Pointer;

public class TreeModelList extends BaseStruct
{
	public static final TreeModelList instance = new TreeModelList();

	private TreeModelList()
	{}

	@Override
	public void scan(BaseDataDecoder decoder, Pointer ptr, ByteBuffer fileBuffer)
	{
		ptr.listLength = fileBuffer.getInt();
		for (int i = 0; i < ptr.listLength; i++)
			fileBuffer.getInt();
	}

	@Override
	public void print(BaseDataDecoder decoder, Pointer ptr, ByteBuffer fileBuffer, PrintWriter pw)
	{
		decoder.printWord(pw, fileBuffer.getInt());

		// thank you, sam_02
		for (int i = 4; i < ptr.getSize(); i += 4)
			decoder.printModelID(ptr, pw, fileBuffer.getInt());

		pw.println();
	}
}
