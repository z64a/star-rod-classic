package game.shared.decoder;

import java.nio.ByteBuffer;

public interface IScan
{
	void scan(BaseDataDecoder decoder, Pointer ptr, ByteBuffer fileBuffer);
}
