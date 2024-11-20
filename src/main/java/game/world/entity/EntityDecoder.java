package game.world.entity;

import static app.Directories.DUMP_ENTITY_SRC;
import static game.shared.StructTypes.DisplayListT;
import static game.shared.StructTypes.DisplayMatrixT;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;

import game.shared.decoder.Pointer.Origin;
import game.world.BaseWorldDecoder;
import game.world.entity.EntityDecompiler.EntityDataRoot;

public class EntityDecoder extends BaseWorldDecoder
{
	public EntityDecoder(String entityName, ByteBuffer fileBuffer, List<EntityDataRoot> roots) throws IOException
	{
		super();

		sourceName = entityName;
		annotateIndexInfo = true;
		dumpEmbeddedImages = false;

		setOffsetRange(0, fileBuffer.capacity());

		int endAddress = 0x0A000000 + (endOffset - startOffset);
		setAddressRange(0x0A000000, endAddress, endAddress);

		for (EntityDataRoot root : roots) {
			enqueueAsRoot(0x0A000000 + root.displayListOffset, DisplayListT, Origin.DECODED);
			if (root.matrixOffset != -1)
				enqueueAsRoot(0x0A000000 + root.matrixOffset, DisplayMatrixT, Origin.DECODED);
		}

		findLocalPointers(fileBuffer);

		super.decode(fileBuffer);

		File scriptFile = new File(DUMP_ENTITY_SRC + "/" + entityName + "/" + sourceName + ".wscr");
		//	File indexFile  = new File(DUMP_ENTITY_SRC + sourceName + ".widx");

		printScriptFile(scriptFile, fileBuffer);
		//	printIndexFile(indexFile);
		//	writeRawFile(rawFile, fileBuffer);
	}

	@Override
	public boolean shouldFunctionsRemoveJumps()
	{
		return true;
	}
}
