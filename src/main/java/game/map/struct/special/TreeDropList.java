package game.map.struct.special;

import java.io.PrintWriter;
import java.nio.ByteBuffer;

import game.map.marker.Marker;
import game.map.marker.Marker.MarkerType;
import game.map.patching.MapDecoder;
import game.shared.BaseStruct;
import game.shared.ProjectDatabase;
import game.shared.SyntaxConstants;
import game.shared.decoder.BaseDataDecoder;
import game.shared.decoder.Pointer;

public class TreeDropList extends BaseStruct
{
	public static final TreeDropList instance = new TreeDropList();

	private TreeDropList()
	{}

	@Override
	public void scan(BaseDataDecoder decoder, Pointer ptr, ByteBuffer fileBuffer)
	{
		ptr.listLength = fileBuffer.getInt();
		for (int i = 0; i < ptr.listLength; i++) {
			fileBuffer.getInt();
			fileBuffer.getInt();
			fileBuffer.getInt();
			fileBuffer.getInt();
			fileBuffer.getInt();
			fileBuffer.getInt();
			fileBuffer.getInt();
		}
	}

	@Override
	public void print(BaseDataDecoder decoder, Pointer ptr, ByteBuffer fileBuffer, PrintWriter pw)
	{
		decoder.printWord(pw, fileBuffer.getInt());
		pw.println();

		for (int i = 0; i < ptr.listLength; i++) {
			int itemID = fileBuffer.getInt();
			int x = fileBuffer.getInt();
			int y = fileBuffer.getInt();
			int z = fileBuffer.getInt();
			int type = fileBuffer.getInt();
			int flag = fileBuffer.getInt();
			int unknown = fileBuffer.getInt();

			assert (ProjectDatabase.hasItem(itemID));
			String itemName = ProjectDatabase.getItemConstant(itemID);

			assert (ProjectDatabase.getFromNamespace("ItemSpawnMode").has(type));
			String typeName = ProjectDatabase.getFromNamespace("ItemSpawnMode").getConstantString(type);

			pw.print(itemName + " ");
			if (decoder instanceof MapDecoder mapDecoder) {
				String markerName = String.format("%s_Drop%d", ptr.foliageName, i + 1);
				Marker m = new Marker(markerName, MarkerType.Position, x, y, z, 0);
				m.setDescription(itemName);
				mapDecoder.addMarker(m);

				pw.printf("%cVec3d:%s ", SyntaxConstants.EXPRESSION_PREFIX, markerName);
			}
			else {
				decoder.printScriptWord(pw, x);
				decoder.printScriptWord(pw, y);
				decoder.printScriptWord(pw, z);
			}
			pw.print(typeName + " ");
			decoder.printScriptWord(pw, flag);
			decoder.printScriptWord(pw, unknown);
			pw.println();
		}
	}
}
