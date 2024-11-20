package game.shared.struct.other;

import static game.shared.StructTypes.*;

import java.io.PrintWriter;
import java.nio.ByteBuffer;

import game.shared.BaseStruct;
import game.shared.decoder.BaseDataDecoder;
import game.shared.decoder.Pointer;

public class EntityBP extends BaseStruct
{
	public static final EntityBP instance = new EntityBP();

	private EntityBP()
	{}

	@Override
	public void scan(BaseDataDecoder decoder, Pointer ptr, ByteBuffer fileBuffer)
	{
		fileBuffer.getShort(); // flags
		fileBuffer.getShort(); // buffer size
		int entityModelCommands = fileBuffer.getInt(); // render cmd -- 'virtual model'
		fileBuffer.getInt(); // model animation nodes
		int fpInit = fileBuffer.getInt();
		int entityCommandList = fileBuffer.getInt();
		int fpHandleCollision = fileBuffer.getInt();
		fileBuffer.getInt(); // dmaStart
		fileBuffer.getInt(); // dmaEnd
		fileBuffer.get(); // entityType
		fileBuffer.get(); // sizeX
		fileBuffer.get(); // sizeY
		fileBuffer.get(); // sizeZ

		if (fpInit != 0)
			decoder.enqueueAsChild(ptr, fpInit, FunctionT);

		if (fpHandleCollision != 0)
			decoder.enqueueAsChild(ptr, fpHandleCollision, FunctionT);

		if (entityCommandList != 0) {
			String name = ptr.getImportName();
			name = name.substring(name.lastIndexOf("_") + 1);
			Pointer child = decoder.enqueueAsChild(ptr, entityCommandList, EntityScriptT);
			child.setImportAffix(name);
		}

		if (decoder.isLocalAddress(entityModelCommands)) {
			String name = ptr.getImportName();
			name = name.substring(name.lastIndexOf("_") + 1);
			Pointer child = decoder.enqueueAsChild(ptr, entityModelCommands, EntityModelScriptT);
			child.setImportAffix(name);
		}
	}

	@Override
	public void print(BaseDataDecoder decoder, Pointer ptr, ByteBuffer fileBuffer, PrintWriter pw)
	{
		pw.printf("%04X %% flags%n", fileBuffer.getShort());
		pw.printf("%04X %% userData buffer size%n", fileBuffer.getShort());
		decoder.printScriptWord(pw, fileBuffer.getInt());
		pw.println();
		decoder.printScriptWord(pw, fileBuffer.getInt());
		pw.println();
		decoder.printScriptWord(pw, fileBuffer.getInt());
		pw.println();
		decoder.printScriptWord(pw, fileBuffer.getInt());
		pw.println();
		decoder.printScriptWord(pw, fileBuffer.getInt());
		pw.println();
		pw.printf("%08X %08X %% dma args%n", fileBuffer.getInt(), fileBuffer.getInt());
		pw.printf("%02Xb %% entity type%n", fileBuffer.get());
		pw.printf("%2d`b  %2d`b  %2d`b  %% AABB size%n", fileBuffer.get(), fileBuffer.get(), fileBuffer.get());
	}
}
