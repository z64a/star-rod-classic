package game.requests;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.List;

import app.Directories;
import app.Environment;
import app.input.IOUtils;
import asm.MIPS;
import asm.pseudoinstruction.PseudoInstruction;

public class SpecialRequestDumper
{
	public static void main(String[] args) throws IOException
	{
		Environment.initialize();
		dumpRequestedFunctions();
		dumpRequestedScripts();
		System.exit(0);
	}

	public static void dumpRequestedScripts() throws IOException
	{
		File f = new File(Directories.DATABASE + "request_script_dumps.txt");
		if (!f.exists())
			return;

		List<String> lines = IOUtils.readFormattedTextFile(f, false);

		ByteBuffer romBuffer = Environment.getBaseRomBuffer();

		for (String line : lines) {
			String[] tokens = line.split("\\s*:\\s*");

			if (tokens.length != 3)
				throw new RuntimeException(String.format("Invalid line in %s: %s", f.getName(), line));

			String name = tokens[2].equals("?") ? tokens[0] : tokens[2];
			PrintWriter pw = new PrintWriter(new File(Directories.DUMP_REQUESTS + "script_" + name + ".txt"));

			int offset = (int) Long.parseLong(tokens[0], 16);
			if (tokens[1].equals("battle")) {
				IsolatedBattleScriptDumper dumper = new IsolatedBattleScriptDumper(romBuffer, pw);
				dumper.printScript(romBuffer, offset, name);

			}
			else if (tokens[1].equals("map")) {
				IsolatedMapScriptDumper dumper = new IsolatedMapScriptDumper(romBuffer, pw);
				dumper.printScript(romBuffer, offset, name);
			}
			else {
				pw.close();
				throw new RuntimeException(String.format("Invalid line in %s: %s", f.getName(), line));
			}

			pw.close();
		}

		PrintWriter pw = new PrintWriter(System.out);
		new IsolatedBattleScriptDumper(romBuffer, pw);
	}

	public static void dumpRequestedFunctions() throws IOException
	{
		File f = new File(Directories.DATABASE + "request_func_dumps.txt");
		if (!f.exists())
			return;

		List<String> lines = IOUtils.readFormattedTextFile(f, false);

		for (String line : lines) {
			String[] tokens = line.split("\\s*:\\s*");

			if (tokens.length != 3)
				throw new RuntimeException(String.format("Invalid line in %s: %s", f.getName(), line));

			String name = !tokens[2].isEmpty() ? tokens[2] : (!tokens[1].isEmpty() ? tokens[1] : "offset_" + tokens[0]);
			PrintWriter pw = new PrintWriter(new File(Directories.DUMP_REQUESTS + "func_" + name + ".txt"));

			int offset = (int) Long.parseLong(tokens[0], 16);
			if (tokens[1].isEmpty()) {
				printFunction(pw, offset);
			}
			else {
				int addr = (int) Long.parseLong(tokens[1], 16);
				printFunction(pw, offset, addr);
			}

			pw.close();
		}
	}

	private static void printFunction(PrintWriter pw, int offset) throws IOException
	{
		ByteBuffer fileBuffer = Environment.getBaseRomBuffer();
		fileBuffer.position(offset);

		List<String> insList = new LinkedList<>();

		// read until JR RA
		int v;
		do {
			v = fileBuffer.getInt();
			insList.add(String.format("%08X", v));
		}
		while (v != 0x03E00008);

		// read delay slot
		v = fileBuffer.getInt();
		insList.add(String.format("%08X", v));

		List<String> asmList = MIPS.disassemble(insList);
		asmList = PseudoInstruction.addAll(asmList);

		pw.printf("Function at %08X\r\n", offset);
		pw.println();

		for (String s : asmList)
			pw.println(s);
	}

	private static void printFunction(PrintWriter pw, int offset, int address) throws IOException
	{
		ByteBuffer fileBuffer = Environment.getBaseRomBuffer();
		fileBuffer.position(offset);

		List<String> insList = new LinkedList<>();

		// read until JR RA
		int v;
		do {
			v = fileBuffer.getInt();
			insList.add(String.format("%08X", v));
		}
		while (v != 0x03E00008);

		// read delay slot
		v = fileBuffer.getInt();
		insList.add(String.format("%08X", v));

		List<String> asmList = MIPS.disassemble(insList);
		asmList = PseudoInstruction.addAll(asmList);

		int insOffset = 0;

		pw.printf("Function at %08X (%08X)\r\n", address, offset);
		pw.println();

		for (int i = 0; i < asmList.size(); i++) {
			String ins = asmList.get(i);
			String[] tokens = ins.split("\\s+");

			// replace branch targets with label names
			if (tokens[0].startsWith("B")) {
				switch (tokens[0]) {
					case "BLTZ":
					case "BLTZL":
					case "BLTZAL":
					case "BLTZALL":
					case "BGEZ":
					case "BGEZL":
					case "BGEZAL":
					case "BGEZALL":
					case "BEQ":
					case "BEQL":
					case "BNE":
					case "BNEL":
					case "BLEZ":
					case "BLEZL":
					case "BGTZ":
					case "BGTZL":
					case "BC1F":
					case "BC1FL":
					case "BC1T":
					case "BC1TL":
						int lastSpaceIndex = ins.contains(" ") ? ins.lastIndexOf(' ') + 1 : ins.lastIndexOf('\t') + 1;
						int breakOffset = (int) Long.parseLong(ins.substring(lastSpaceIndex), 16);
						ins = String.format("%s.o%X", ins.substring(0, lastSpaceIndex), insOffset + breakOffset + 4);
				}
			}

			pw.printf("%5X:  %s\r\n", insOffset, ins);

			// how many bytes does this instruction add?
			if (PseudoInstruction.isValidOpcode(tokens[0])) {
				if ((i + 2) < asmList.size()) {
					String k = asmList.get(i + 2);
					if (k == PseudoInstruction.RESERVED) {
						insOffset -= 4;
					}
				}

				insOffset += 4 * PseudoInstruction.getLengthFromLine(tokens);
			}
			else {
				insOffset += 4;
			}
		}
	}
}
