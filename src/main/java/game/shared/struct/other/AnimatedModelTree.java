package game.shared.struct.other;

import static game.shared.StructTypes.AnimatedModelNodeT;

import java.nio.ByteBuffer;

import game.shared.BaseStruct;
import game.shared.decoder.BaseDataDecoder;
import game.shared.decoder.Pointer;

public class AnimatedModelTree extends BaseStruct
{
	public static final AnimatedModelTree instance = new AnimatedModelTree();

	private AnimatedModelTree()
	{}

	@Override
	public void scan(BaseDataDecoder decoder, Pointer ptr, ByteBuffer fileBuffer)
	{
		int ptrNode;
		while ((ptrNode = fileBuffer.getInt()) != 0) {
			decoder.enqueueAsChild(ptr, ptrNode, AnimatedModelNodeT);
		}
	}
}
