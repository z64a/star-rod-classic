package game.requests;

import static game.shared.StructTypes.ActorT;
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

public class IsolatedBattleScriptDumper extends BaseDataDecoder
{
	public static void main(String[] args) throws IOException
	{
		Environment.initialize();
		ByteBuffer romBuffer = Environment.getBaseRomBuffer();
		PrintWriter pw = new PrintWriter(System.out);

		IsolatedBattleScriptDumper dumper = new IsolatedBattleScriptDumper(romBuffer, pw);
		pw.println();

		dumper.printScript(romBuffer, 0x1B3BB4, "Script_Player_HandleEvent");

		pw.close();
		Environment.exit();
	}

	private final PrintWriter pw;

	public IsolatedBattleScriptDumper(ByteBuffer romBuffer, PrintWriter pw)
	{
		super(LibScope.Battle, ActorT, ProjectDatabase.rom.getLibrary(LibScope.Battle));
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
