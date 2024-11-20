package game.map.struct.special;

import java.io.PrintWriter;
import java.nio.ByteBuffer;

import game.map.marker.Marker;
import game.map.marker.Marker.MarkerType;
import game.map.patching.MapDecoder;
import game.shared.BaseStruct;
import game.shared.SyntaxConstants;
import game.shared.decoder.BaseDataDecoder;
import game.shared.decoder.Pointer;

public class TreeEffectVectors extends BaseStruct
{
	public static final TreeEffectVectors instance = new TreeEffectVectors();

	private TreeEffectVectors()
	{}

	@Override
	public void scan(BaseDataDecoder decoder, Pointer ptr, ByteBuffer fileBuffer)
	{
		ptr.listLength = fileBuffer.getInt();
		for (int i = 0; i < ptr.listLength; i++) {
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
			int x = fileBuffer.getInt();
			int y = fileBuffer.getInt();
			int z = fileBuffer.getInt();

			if (decoder instanceof MapDecoder) {
				String markerName = String.format("TreeFX_%s_%X", ptr.foliageName, i);
				((MapDecoder) decoder).addMarker(new Marker(markerName, MarkerType.Position, x, y, z, 0));
				pw.printf("%cVec3f:%s %% %7d %7d %7d%n", SyntaxConstants.EXPRESSION_PREFIX, markerName, x, y, z);
			}
			else {
				decoder.printScriptWord(pw, x);
				decoder.printScriptWord(pw, y);
				decoder.printScriptWord(pw, z);
			}
		}
	}
}
