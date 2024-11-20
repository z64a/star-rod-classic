package game.battle.struct;

import java.io.PrintWriter;
import java.nio.ByteBuffer;

import game.shared.BaseStruct;
import game.shared.ProjectDatabase;
import game.shared.decoder.BaseDataDecoder;
import game.shared.decoder.Pointer;
import util.Logger;

public class StatusTable extends BaseStruct
{
	public static final StatusTable instance = new StatusTable();

	private StatusTable()
	{}

	@Override
	public void scan(BaseDataDecoder decoder, Pointer ptr, ByteBuffer fileBuffer)
	{
		while ((fileBuffer.getInt()) != 0) {
			fileBuffer.getInt();
		}
	}

	@Override
	public void print(BaseDataDecoder decoder, Pointer ptr, ByteBuffer fileBuffer, PrintWriter pw)
	{
		int key = 0;
		while ((key = fileBuffer.getInt()) != 0) {
			if (ProjectDatabase.StatusType.has(key)) {
				String keyName = ProjectDatabase.StatusType.getConstantString(key);
				if (keyName.length() > 17)
					pw.printf("%-23s ", keyName);
				else
					pw.printf("%-17s ", keyName);
			}
			else {
				Logger.logWarning("WARNING: Unknown status type found! " + key);
				pw.printf("%08X ", key);
			}
			pw.printf("%3d`\n", fileBuffer.getInt());
		}
		pw.println(ProjectDatabase.StatusType.getConstantString(0)); // .End
	}
}
