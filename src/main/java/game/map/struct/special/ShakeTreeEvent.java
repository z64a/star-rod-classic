package game.map.struct.special;

import static game.shared.StructTypes.*;

import java.io.PrintWriter;
import java.nio.ByteBuffer;

import game.map.patching.MapDecoder;
import game.shared.BaseStruct;
import game.shared.SyntaxConstants;
import game.shared.decoder.BaseDataDecoder;
import game.shared.decoder.Pointer;
import game.shared.struct.StructType;

public class ShakeTreeEvent extends BaseStruct
{
	public static final ShakeTreeEvent instance = new ShakeTreeEvent();

	private ShakeTreeEvent()
	{}

	@Override
	public void scan(BaseDataDecoder decoder, Pointer ptr, ByteBuffer fileBuffer)
	{
		if (!ptr.nameWasOverriden() && decoder instanceof MapDecoder mapDecoder) {
			mapDecoder.treeCount++;
			ptr.foliageName = "Tree" + mapDecoder.treeCount;
			ptr.forceName(ptr.getType().toString() + "_" + ptr.foliageName);
		}
		else {
			String overrideName = ptr.getPointerName();
			String structNamePrefix = SyntaxConstants.POINTER_PREFIX + ptr.getType().toString() + "_";
			if (overrideName.startsWith(structNamePrefix))
				ptr.foliageName = overrideName.substring(structNamePrefix.length());
			else
				ptr.foliageName = overrideName;
		}

		enqueueAndName(decoder, ptr, fileBuffer.getInt(), TreeModelListT, "Leaves");
		enqueueAndName(decoder, ptr, fileBuffer.getInt(), TreeModelListT, "Trunk");
		enqueueAndName(decoder, ptr, fileBuffer.getInt(), TreeDropListT, "");
		enqueueAndName(decoder, ptr, fileBuffer.getInt(), TreeEffectVectorsT, "");
		enqueueAndName(decoder, ptr, fileBuffer.getInt(), ScriptT, "Callback");
	}

	private void enqueueAndName(BaseDataDecoder decoder, Pointer ptr, int addr, StructType type, String suffix)
	{
		Pointer child = decoder.tryEnqueueAsChild(ptr, addr, type);
		if (child != null) {
			child.foliageName = ptr.foliageName;
			if (suffix == null || suffix.isEmpty())
				child.forceName(String.format("%s_%s", type.toString(), ptr.foliageName));
			else
				child.forceName(String.format("%s_%s_%s", type.toString(), ptr.foliageName, suffix));
		}
	}

	@Override
	public void print(BaseDataDecoder decoder, Pointer ptr, ByteBuffer fileBuffer, PrintWriter pw)
	{
		decoder.printHex(ptr, fileBuffer, pw, 1);
	}
}
