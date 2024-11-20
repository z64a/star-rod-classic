package game.shared.decoder;

import java.io.PrintWriter;
import java.nio.ByteBuffer;

public interface IPrint
{
	void print(BaseDataDecoder decoder, Pointer ptr, ByteBuffer fileBuffer, PrintWriter pw);
}
