package game.shared.struct.f3dex2;

import java.io.PrintWriter;
import java.nio.ByteBuffer;

import game.shared.BaseStruct;
import game.shared.decoder.BaseDataDecoder;
import game.shared.decoder.Pointer;

public class VertexTable extends BaseStruct
{
	public static final VertexTable instance = new VertexTable();

	private VertexTable()
	{}

	@Override
	public void scan(BaseDataDecoder decoder, Pointer ptr, ByteBuffer fileBuffer)
	{
		if (ptr.getSize() != 0) {
			fileBuffer.position(fileBuffer.position() + ptr.getSize());
			return;
		}
	}

	@Override
	public void print(BaseDataDecoder decoder, Pointer ptr, ByteBuffer fileBuffer, PrintWriter pw)
	{
		String tab = decoder.getTabString();

		int i = 0;
		for (int pos = 0; pos < ptr.getSize(); pos += 0x10) {
			// column headers
			if (i % 32 == 0) {
				if (i != 0)
					pw.println();
				pw.println(tab + "%    X        Y        Z                 U        V         R   G   B   A     pos");
			}

			short x = fileBuffer.getShort();
			short y = fileBuffer.getShort();
			short z = fileBuffer.getShort();
			short unk = fileBuffer.getShort(); // flags = zero
			short u = fileBuffer.getShort();
			short v = fileBuffer.getShort();
			int r = fileBuffer.get() & 0xFF;
			int g = fileBuffer.get() & 0xFF;
			int b = fileBuffer.get() & 0xFF;
			int a = fileBuffer.get() & 0xFF;

			pw.printf("%s%6d`s %6d`s %6d`s    %04Xs  %6d`s %6d`s      %02Xb %02Xb %02Xb %02Xb   %% %2X%n", tab, x, y, z, unk, u, v, r, g, b, a, i++);
		}
	}
}
