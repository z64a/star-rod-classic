package game.texture.images;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.ByteBuffer;

import app.Environment;
import game.ROM.LibScope;
import game.shared.ProjectDatabase;
import game.shared.decoder.BaseDataDecoder;
import game.shared.decoder.Pointer;
import game.shared.struct.StructType;

public class ImageScriptDecoder extends BaseDataDecoder
{
	public ImageScriptDecoder(LibScope scope)
	{
		super(scope, null, ProjectDatabase.rom.getLibrary(scope));
	}

	public void printStruct(StructType type, ByteBuffer romBuffer, int offset, PrintWriter pw) throws IOException
	{
		Pointer ptr = new Pointer(-1);
		ptr.setType(type);

		romBuffer.position(offset);
		type.print(this, ptr, romBuffer, pw);
	}

	@Override
	public String getSourceName()
	{
		return Environment.getBaseRomName();
	}

	@Override
	public void scanScript(Pointer ptr, ByteBuffer fileBuffer)
	{
		throw new RuntimeException();
	}
}
