package game.map.struct.shop;

import java.io.PrintWriter;
import java.nio.ByteBuffer;

import game.shared.BaseStruct;
import game.shared.ProjectDatabase;
import game.shared.decoder.BaseDataDecoder;
import game.shared.decoder.Pointer;

public class BadgeShopInventory extends BaseStruct
{
	public static final BadgeShopInventory instance = new BadgeShopInventory();

	private BadgeShopInventory()
	{}

	@Override
	public void scan(BaseDataDecoder decoder, Pointer ptr, ByteBuffer fileBuffer)
	{}

	@Override
	public void print(BaseDataDecoder decoder, Pointer ptr, ByteBuffer fileBuffer, PrintWriter pw)
	{
		int pos = 0;

		while (pos < ptr.getSize()) {
			int id = fileBuffer.getInt();
			int cost = fileBuffer.getInt();
			int stringID = fileBuffer.getInt();
			pos += 12;
			String itemName = ProjectDatabase.getItemConstant(id);
			pw.printf("%-20s %3d`    %08X ", itemName, cost, stringID);
			pw.println(decoder.getStringComment(stringID));
		}
	}
}
