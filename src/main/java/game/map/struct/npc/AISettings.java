package game.map.struct.npc;

import java.io.PrintWriter;
import java.nio.ByteBuffer;

import game.shared.BaseStruct;
import game.shared.decoder.BaseDataDecoder;
import game.shared.decoder.Pointer;

public class AISettings extends BaseStruct
{
	public static final AISettings instance = new AISettings();

	private AISettings()
	{}

	@Override
	public void scan(BaseDataDecoder decoder, Pointer ptr, ByteBuffer fileBuffer)
	{}

	@Override
	public void print(BaseDataDecoder decoder, Pointer ptr, ByteBuffer fileBuffer, PrintWriter pw)
	{
		if (ptr.getSize() == 48 || ptr.getSize() == 36) {
			if (ptr.getSize() == 48) {
				pw.printf("%7s %% move speed\r\n", fileBuffer.getFloat());
				pw.printf("    %d` %% move time\r\n", fileBuffer.getInt());
				pw.printf("    %d` %% wait time\r\n", fileBuffer.getInt());
			}

			pw.printf("%7s %% alert radius\r\n", fileBuffer.getFloat());
			pw.printf("%7s\r\n", fileBuffer.getFloat());
			pw.printf("    %d`\r\n", fileBuffer.getInt());

			pw.printf("%7s %% chase speed\r\n", fileBuffer.getFloat());
			pw.printf("    %d`\r\n", fileBuffer.getInt());
			pw.printf("    %d`\r\n", fileBuffer.getInt());

			pw.printf("%7s %% chase radius\r\n", fileBuffer.getFloat());
			pw.printf("%7s\r\n", fileBuffer.getFloat());
			pw.printf("    %d`\r\n", fileBuffer.getInt());
		}
		else {
			decoder.printHex(ptr, fileBuffer, pw, 8);
		}
	}
}
