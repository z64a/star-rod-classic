package game.map.struct.npc;

import java.nio.ByteBuffer;

import game.shared.BaseStruct;
import game.shared.decoder.BaseDataDecoder;
import game.shared.decoder.Pointer;

public class AnimationList extends BaseStruct
{
	public static final AnimationList instance = new AnimationList();

	private AnimationList()
	{}

	@Override
	public void scan(BaseDataDecoder decoder, Pointer ptr, ByteBuffer fileBuffer)
	{
		while (fileBuffer.getInt() != 0xFFFFFFFF)
			;
	}
}
