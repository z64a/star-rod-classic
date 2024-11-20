package game.map.struct;

import java.io.PrintWriter;
import java.nio.ByteBuffer;

import game.shared.BaseStruct;
import game.shared.ProjectDatabase;
import game.shared.decoder.BaseDataDecoder;
import game.shared.decoder.Pointer;

public class ItemList extends BaseStruct
{
	public static final ItemList instance = new ItemList();

	private ItemList()
	{}

	@Override
	public void scan(BaseDataDecoder decoder, Pointer ptr, ByteBuffer fileBuffer)
	{
		ptr.listLength = 0;
		while (ptr.listLength < 10 && fileBuffer.getInt() != 0)
			ptr.listLength++;
	}

	@Override
	public void print(BaseDataDecoder decoder, Pointer ptr, ByteBuffer fileBuffer, PrintWriter pw)
	{
		for (int i = 0; i < ptr.listLength; i++) {
			int itemID = fileBuffer.getInt();
			pw.println(ProjectDatabase.getItemConstant(itemID));
		}

		if (ptr.listLength < 10)
			pw.println("00000000");
	}
}
