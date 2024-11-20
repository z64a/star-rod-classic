package game.map.templates;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;

import app.Directories;
import app.Environment;
import app.input.IOUtils;
import game.shared.struct.script.Script;
import game.shared.struct.script.Script.ScriptLine;

public class TemplateCreater
{
	public static void main(String[] args) throws IOException
	{
		Environment.initialize();

		//	printMe("mac_02", 0x5020);
		//	printMe("kkj_01", 0x1138); // ExitSingleDoor
		printMe("kmr_02", 0x5604); // ExitSingleDoor
		//	printMe("kkj_01", 0x1084); // ExitDoubleDoor

		//		compare(MapTemplateData.SEARCH_BUSH, MapTemplateData.NEW_SCRIPT);

		Environment.exit();
	}

	private static void printMe(String mapName, int offset) throws IOException
	{
		File f = new File(Directories.DUMP_MAP_RAW + mapName + ".bin");
		ByteBuffer bb = IOUtils.getDirectBuffer(f);

		bb.position(offset);
		ArrayList<ScriptLine> lines = Script.readScript(bb);

		System.out.println("protected static final Integer[] NEW_SCRIPT = {");
		for (int i = 0; i < lines.size(); i++) {
			ScriptLine line = lines.get(i);

			System.out.printf("0x%08X, 0x%08X", line.cmd.opcode, line.args.length);
			if (line.cmd.argc != 0) {
				for (int j = 0; j < line.args.length; j++) {
					int v = line.args[j];
					if ((v & 0xFFFF0000) == 0x80240000)
						System.out.printf(", null", v);
					else
						System.out.printf(", 0x%08X", v);
				}
			}

			if ((i + 1) < lines.size())
				System.out.print(", ");
			System.out.println();
		}
		System.out.println("};");
	}

	public static void compare(Integer[] a, Integer[] b)
	{
		if (a.length != b.length) {
			System.out.printf("Size differs! %08X vs %08X%n", a.length * 4, b.length * 4);
			return;
		}

		for (int i = 0; i < a.length; i++) {
			if (a[i] == null && b[i] == null)
				continue;

			if ((a[i] == null && b[i] != null) || (a[i] != null && b[i] == null) || (int) a[i] != (int) b[i])
				System.out.printf("Bytes differ at [%X]: %08X vs %08X%n", i * 4, a[i], b[i]);
		}

		System.out.println("Done.");
	}
}
