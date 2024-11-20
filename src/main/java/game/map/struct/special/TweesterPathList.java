package game.map.struct.special;

import static game.shared.StructTypes.TweesterPathT;

import java.nio.ByteBuffer;

import game.shared.BaseStruct;
import game.shared.decoder.BaseDataDecoder;
import game.shared.decoder.Pointer;

public class TweesterPathList extends BaseStruct
{
	public static final TweesterPathList instance = new TweesterPathList();

	private TweesterPathList()
	{}

	@Override
	public void scan(BaseDataDecoder decoder, Pointer ptr, ByteBuffer fileBuffer)
	{
		int currentPointer;
		while ((currentPointer = fileBuffer.getInt()) != -1) {
			decoder.enqueueAsChild(ptr, currentPointer, TweesterPathT);
		}
	}
}
