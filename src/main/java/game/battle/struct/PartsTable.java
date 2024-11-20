package game.battle.struct;

import static game.shared.StructTypes.DefenseTableT;
import static game.shared.StructTypes.IdleAnimationsT;

import java.io.PrintWriter;
import java.nio.ByteBuffer;

import game.shared.BaseStruct;
import game.shared.decoder.BaseDataDecoder;
import game.shared.decoder.Pointer;

public class PartsTable extends BaseStruct
{
	public static final PartsTable instance = new PartsTable();

	private PartsTable()
	{}

	@Override
	public void scan(BaseDataDecoder decoder, Pointer ptr, ByteBuffer fileBuffer)
	{
		int start = fileBuffer.position();
		for (int i = 0; i < ptr.listLength; i++) {
			fileBuffer.position(start + i * 0x24);

			int flags1 = fileBuffer.getInt();
			fileBuffer.get(); // stateID

			int a = fileBuffer.get(); // 05 Area NOK, $PartsTable_80227AB4
			int b = fileBuffer.get(); // 03 Area MAC, $PartsTable_80225A80
			int c = fileBuffer.get(); // 07 Area TRD Part 2, $PartsTable_8021A74

			int targetOffsetX = fileBuffer.get();
			int targetOffsetY = fileBuffer.get();
			int opacity = fileBuffer.getShort();
			assert (opacity < 256);

			int ptrIdleAnimations = fileBuffer.getInt();
			decoder.tryEnqueueAsChild(ptr, ptrIdleAnimations, IdleAnimationsT);

			int ptrDefenseTable = fileBuffer.getInt(); // sometimes zero, see 0C:Tutankoopa
			decoder.tryEnqueueAsChild(ptr, ptrDefenseTable, DefenseTableT);

			int flags2 = fileBuffer.getInt();
			int flags3 = fileBuffer.getInt();

			fileBuffer.getInt();
			fileBuffer.getInt();
		}
	}

	@Override
	public void print(BaseDataDecoder decoder, Pointer ptr, ByteBuffer fileBuffer, PrintWriter pw)
	{
		decoder.printHex(ptr, fileBuffer, pw, 9);
	}
}
