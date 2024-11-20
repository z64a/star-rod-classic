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

public class EntryList extends BaseStruct
{
	public static final EntryList instance = new EntryList();

	private EntryList()
	{}

	@Override
	public void scan(BaseDataDecoder decoder, Pointer ptr, ByteBuffer fileBuffer)
	{
		if (ptr.getSize() == 0)
			ptr.setSize(ptr.listLength * 0x10);

		int n = 0;
		for (int i = 0; i < ptr.getSize(); i += 0x10) {
			float x = fileBuffer.getFloat();
			float y = fileBuffer.getFloat();
			float z = fileBuffer.getFloat();
			float a = fileBuffer.getFloat();

			if (decoder instanceof MapDecoder) {
				String markerName = String.format("Entry%X", n);
				((MapDecoder) decoder).addMarker(new Marker(markerName, MarkerType.Entry, x, y, z, a));
			}
			n++;
		}
	}

	@Override
	public void print(BaseDataDecoder decoder, Pointer ptr, ByteBuffer fileBuffer, PrintWriter pw)
	{
		// thank you, kpa_08 and kpa_09!
		int n = 0;
		for (int i = 0; i < ptr.getSize(); i += 0x10) {
			float x = fileBuffer.getFloat();
			float y = fileBuffer.getFloat();
			float z = fileBuffer.getFloat();
			float a = fileBuffer.getFloat();

			String markerName = String.format("Entry%X", n);
			pw.printf("%cVec4f:%s %% %6.1f %6.1f %6.1f %6.1f%n",
				SyntaxConstants.EXPRESSION_PREFIX, markerName, x, y, z, a);
			n++;
		}
	}
}
