package game.shared.struct.other;

import java.io.PrintWriter;
import java.nio.ByteBuffer;
import java.util.ArrayList;

import common.Vector3f;
import game.map.marker.Marker;
import game.map.marker.Marker.MarkerType;
import game.map.patching.MapDecoder;
import game.shared.BaseStruct;
import game.shared.SyntaxConstants;
import game.shared.decoder.BaseDataDecoder;
import game.shared.decoder.Pointer;

public class VectorList extends BaseStruct
{
	public static final VectorList instance = new VectorList();

	private VectorList()
	{}

	@Override
	public void scan(BaseDataDecoder decoder, Pointer ptr, ByteBuffer fileBuffer)
	{
		if (decoder instanceof MapDecoder) {
			String markerName = String.format("Path_%08X", decoder.toAddress(fileBuffer.position()));
			ArrayList<Vector3f> vectors = new ArrayList<>(ptr.listLength);
			for (int i = 0; i < ptr.listLength; i++) {
				float x = fileBuffer.getFloat();
				float y = fileBuffer.getFloat();
				float z = fileBuffer.getFloat();
				vectors.add(new Vector3f(x, y, z));
			}

			Vector3f last = vectors.get(ptr.listLength - 1);
			Marker pathMarker = new Marker(markerName, MarkerType.Path, last.x, last.y, last.z, 0);
			pathMarker.pathComponent.setPath(vectors);
			((MapDecoder) decoder).addMarker(pathMarker);
		}
		else {
			for (int i = 0; i < ptr.listLength; i++) {
				fileBuffer.getFloat();
				fileBuffer.getFloat();
				fileBuffer.getFloat();
			}
		}
	}

	@Override
	public void print(BaseDataDecoder decoder, Pointer ptr, ByteBuffer fileBuffer, PrintWriter pw)
	{
		String markerName = String.format("Path_%08X", decoder.toAddress(fileBuffer.position()));
		pw.printf("%% %cPath3f:%s%n", SyntaxConstants.EXPRESSION_PREFIX, markerName);

		for (int i = 0; i < ptr.listLength; i++) {
			float x = fileBuffer.getFloat();
			float y = fileBuffer.getFloat();
			float z = fileBuffer.getFloat();
			pw.printf("%f %f %f%n", x, y, z);
		}
	}
}
