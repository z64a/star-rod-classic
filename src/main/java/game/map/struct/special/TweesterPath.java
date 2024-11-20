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

public class TweesterPath extends BaseStruct
{
	public static final TweesterPath instance = new TweesterPath();

	/*
	This data structure controls the motion of the Tweester NPC.
	It appears to be organized as a list of int32[3] vectors, but the game actually
	reads only the lower halfword of each coordinate.

	End of list is identified by 0x80000001.

	Values are read by func_802BB76C, which returns true if the Tweester comes within
	10 units in X and Z of the next vector in the list.
	 */
	private TweesterPath()
	{}

	@Override
	public void scan(BaseDataDecoder decoder, Pointer ptr, ByteBuffer fileBuffer)
	{
		while (fileBuffer.getInt() != 0x80000001)
			;
	}

	@Override
	public void print(BaseDataDecoder decoder, Pointer ptr, ByteBuffer fileBuffer, PrintWriter pw)
	{
		int count = 0;
		int x, y, z;

		while ((x = fileBuffer.getInt()) != 0x80000001) {
			y = fileBuffer.getInt();
			z = fileBuffer.getInt();
			count++;

			if (decoder instanceof MapDecoder) {
				String markerName = ptr.getPointerName().substring(1) + "_" + count;

				Marker m = new Marker(markerName, MarkerType.Position, x, y, z, 0);
				m.setDescription(ptr.getPointerName());
				((MapDecoder) decoder).addMarker(m);

				pw.printf("%cVec3d:%s ", SyntaxConstants.EXPRESSION_PREFIX, markerName);
			}
			else {
				decoder.printScriptWord(pw, x);
				decoder.printScriptWord(pw, y);
				decoder.printScriptWord(pw, z);
			}
		}

		pw.printf("%08X\r\n", x);
	}
}
