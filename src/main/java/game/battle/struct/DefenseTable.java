package game.battle.struct;

import java.io.PrintWriter;
import java.nio.ByteBuffer;

import game.shared.BaseStruct;
import game.shared.ProjectDatabase;
import game.shared.decoder.BaseDataDecoder;
import game.shared.decoder.Pointer;
import util.Logger;

public class DefenseTable extends BaseStruct
{
	public static final DefenseTable instance = new DefenseTable();

	private DefenseTable()
	{}

	@Override
	public void scan(BaseDataDecoder decoder, Pointer ptr, ByteBuffer fileBuffer)
	{
		while (fileBuffer.getInt() != 0)
			fileBuffer.getInt();
	}

	@Override
	public void print(BaseDataDecoder decoder, Pointer ptr, ByteBuffer fileBuffer, PrintWriter pw)
	{
		int defenseType = 0;
		while ((defenseType = fileBuffer.getInt()) != 0) {
			if (ProjectDatabase.ElementType.has(defenseType)) {
				pw.printf("%-16s", ProjectDatabase.ElementType.getConstantString(defenseType));
			}
			else {
				Logger.logWarning("WARNING: Unknown defense type found! " + defenseType);
				pw.printf("%08X ", defenseType);
			}
			pw.printf("%08X\n", fileBuffer.getInt());
		}
		pw.println(ProjectDatabase.ElementType.getConstantString(0));
	}
}
