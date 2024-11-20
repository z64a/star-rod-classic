package game.requests;

import static game.shared.StructTypes.NpcGroupT;
import static game.shared.StructTypes.ScriptT;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.ByteBuffer;

import app.Environment;
import game.ROM.LibScope;
import game.shared.ProjectDatabase;
import game.shared.decoder.BaseDataDecoder;
import game.shared.decoder.Pointer;
import game.shared.struct.script.Script;
import game.shared.struct.script.Script.ScriptLine;

public class IsolatedMapScriptDumper extends BaseDataDecoder
{
	public static void main(String[] args) throws IOException
	{
		Environment.initialize();
		ByteBuffer romBuffer = Environment.getBaseRomBuffer();
		PrintWriter pw = new PrintWriter(System.out);

		IsolatedMapScriptDumper dumper = new IsolatedMapScriptDumper(romBuffer, pw);
		pw.println();

		//	dumper.printScript(romBuffer, 0x78CF28);
		//	dumper.printScript(romBuffer, 0x78E628);
		dumper.printScript(romBuffer, 0x7E6C2C, "AnimateDoorExit");
		dumper.printScript(romBuffer, 0x7E6C54, "AnimateDoorEntry");

		pw.close();
		Environment.exit();
	}

	private final PrintWriter pw;

	public IsolatedMapScriptDumper(ByteBuffer romBuffer, PrintWriter pw)
	{
		super(LibScope.World, NpcGroupT, ProjectDatabase.rom.getLibrary(LibScope.World));
		this.pw = pw;
	}

	public void printScript(ByteBuffer romBuffer, int offset) throws IOException
	{
		printScript(romBuffer, offset, "");
	}

	public void printScript(ByteBuffer romBuffer, int offset, String name) throws IOException
	{
		pw.printf("Script: %s (%08X)\r\n", name, offset);
		pw.println();

		Pointer ptr = new Pointer(-1);
		ptr.setType(ScriptT);

		romBuffer.position(offset);
		Script.scan(this, ptr, romBuffer); // ignore return value
		int length = romBuffer.position() - offset;

		romBuffer.position(offset);
		Script.print(this, ptr, 0, length, romBuffer, pw);
		pw.println();
	}

	@Override
	public String getSourceName()
	{
		return Environment.getBaseRomName();
	}

	@Override
	public String printFunctionArgs(Pointer ptr, PrintWriter pw, ScriptLine line, int lineAddress)
	{
		for (int i = 1; i < line.args.length; i++)
			printScriptWord(pw, ptr, line.types[i], line.args[i]);

		return ""; // no comments
	}
	/*
	@Override
	protected void updateStructHierarchy(Pointer parent, Pointer child)
	{
		// only print, don't do any scanning
		throw new RuntimeException();
	}

	@Override
	protected void scanPointer(ByteBuffer fileBuffer, Pointer ptr)
	{
		// only print, don't do any scanning
		throw new RuntimeException();
	}

	@Override
	protected Pointer createNewPointer(int address)
	{
		// only print, don't do any scanning
		throw new RuntimeException();
	}

	@Override
	protected Struct getType(String typename)
	{
		return MapStruct.nameMap.get(typename);
	}
	*/

	@Override
	public void scanScript(Pointer ptr, ByteBuffer fileBuffer)
	{
		// only print, don't do any scanning
		throw new RuntimeException();
	}
}
