package game.map.struct;

import java.io.PrintWriter;
import java.nio.ByteBuffer;

import game.map.marker.Marker;
import game.map.marker.Marker.MarkerType;
import game.map.patching.MapDecoder;
import game.shared.BaseStruct;
import game.shared.SyntaxConstants;
import game.shared.decoder.BaseDataDecoder;
import game.shared.decoder.Pointer;

public class LavaResetList extends BaseStruct
{
	public static final LavaResetList instance = new LavaResetList();

	private LavaResetList()
	{}

	@Override
	public void scan(BaseDataDecoder decoder, Pointer ptr, ByteBuffer fileBuffer)
	{
		int colliderID;
		ptr.listLength = 0;
		do {
			colliderID = fileBuffer.getInt();
			float x = fileBuffer.getFloat();
			float y = fileBuffer.getFloat();
			float z = fileBuffer.getFloat();

			if (colliderID >= 0 && decoder instanceof MapDecoder mapDecoder) {
				String markerName = String.format("LavaReset_%08X_%s", ptr.address, mapDecoder.getColliderName(colliderID));
				mapDecoder.addMarker(new Marker(markerName, MarkerType.Position, x, y, z, 0));
			}
			ptr.listLength++;
		}

		while (colliderID != -1);
	}

	@Override
	public void print(BaseDataDecoder decoder, Pointer ptr, ByteBuffer fileBuffer, PrintWriter pw)
	{
		for (int i = 0; i < ptr.listLength; i++) {
			int colliderID = fileBuffer.getInt();
			int x = fileBuffer.getInt();
			int y = fileBuffer.getInt();
			int z = fileBuffer.getInt();

			if (colliderID >= 0 && decoder instanceof MapDecoder mapDecoder) {
				decoder.printColliderID(ptr, pw, colliderID);
				pw.printf("%cMarker:LavaReset_%08X_%s ", SyntaxConstants.EXPRESSION_PREFIX, ptr.address, mapDecoder.getColliderName(colliderID));
				pw.println("% " + Float.intBitsToFloat(x) + " " + Float.intBitsToFloat(y) + " " + Float.intBitsToFloat(z));
			}
			else {
				decoder.printWord(pw, colliderID);
				decoder.printWord(pw, x);
				decoder.printWord(pw, y);
				decoder.printWord(pw, z);
				pw.println();
			}
		}

		//	if(ptr.listLength < 10)
		//		pw.println("00000000"); //XXX???
	}
}
