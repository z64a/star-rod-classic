package game.battle.struct;

import static game.shared.StructTypes.ActorT;
import static game.shared.StructTypes.Vector3dT;

import java.io.PrintWriter;
import java.nio.ByteBuffer;

import game.shared.BaseStruct;
import game.shared.decoder.BaseDataDecoder;
import game.shared.decoder.Pointer;

public class SpecialFormation extends BaseStruct
{
	public static final SpecialFormation instance = new SpecialFormation();

	private SpecialFormation()
	{}

	@Override
	public void scan(BaseDataDecoder decoder, Pointer ptr, ByteBuffer fileBuffer)
	{
		for (int i = 0; i < ptr.listLength; i++) {
			int ptrEnemy = fileBuffer.getInt();
			decoder.tryEnqueueAsChild(ptr, ptrEnemy, ActorT);

			int ptrPosition = fileBuffer.getInt();
			decoder.tryEnqueueAsChild(ptr, ptrPosition, Vector3dT);

			fileBuffer.position(fileBuffer.position() + 0x14);
		}
	}

	@Override
	public void print(BaseDataDecoder decoder, Pointer ptr, ByteBuffer fileBuffer, PrintWriter pw)
	{
		decoder.printHex(ptr, fileBuffer, pw, 7);
	}
}
