package game.map.struct.special;

import java.nio.ByteBuffer;

import game.shared.BaseStruct;
import game.shared.decoder.BaseDataDecoder;
import game.shared.decoder.Pointer;

public class NpcList extends BaseStruct
{
	public static final NpcList instance = new NpcList();

	private NpcList()
	{}

	@Override
	public void scan(BaseDataDecoder decoder, Pointer ptr, ByteBuffer fileBuffer)
	{
		while (fileBuffer.getInt() != -1)
			;
	}
}
