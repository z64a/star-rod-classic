package game.shared.struct.function;

import java.io.PrintWriter;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import app.StarRodException;
import app.input.InputFileException;
import app.input.Line;
import asm.AsmUtils;
import asm.MIPS;
import asm.pseudoinstruction.PseudoInstruction;
import game.shared.SyntaxConstants;
import game.shared.decoder.BaseDataDecoder;
import game.shared.decoder.Pointer;
import game.shared.decoder.PointerHeuristic;
import game.shared.lib.LibEntry;
import game.shared.lib.Library;
import util.Logger;
import util.Priority;

public class Function
{
	public static final char LABEL_CHAR = '.';

	public static FunctionScanResults scan(BaseDataDecoder decoder, ByteBuffer fileBuffer, int functionAddress)
	{
		int startOffset = fileBuffer.position();
		assert (startOffset == decoder.toOffset(functionAddress));

		FunctionScanResults results = new FunctionScanResults();
		ArrayList<String> asmList = scanPass(decoder, fileBuffer, results, functionAddress);
		Queue<JumpTable> newTables = findJumpTables(decoder, asmList, results.jumpTableAddresses);

		// keep looking for new jump tables
		while (!newTables.isEmpty()) {
			while (!newTables.isEmpty()) {
				JumpTable table = newTables.poll();
				results.jumpTables.add(table);
				results.jumpTableAddresses.add(table.baseAddress);
				scanJumpTable(decoder, fileBuffer, results, table, functionAddress);
			}

			fileBuffer.position(startOffset);
			asmList = scanPass(decoder, fileBuffer, results, functionAddress);
			newTables = findJumpTables(decoder, asmList, results.jumpTableAddresses);
		}

		fileBuffer.position(startOffset);
		asmList = scanPass(decoder, fileBuffer, results, functionAddress);

		scanPseudoinstructions(decoder, asmList, results);
		results.intTables.removeAll(results.jumpTableAddresses);

		// return the code with PIs
		asmList = PseudoInstruction.addAll(asmList);
		results.code = new ArrayList<>(asmList.size());
		for (int i = 0; i < asmList.size(); i++)
			results.code.add(asmList.get(i).replaceAll("[\\(\\)]", "").split("[, ]+"));

		return results;
	}

	/*
	 * Expect jump tables to be accessed via the following pattern:
		-?	SLTIU     V0, V1, 32
		    ...
		-1	SLL       V0, A1, 2
		==	LUI       AT, 8025
		+1	ADDU      AT, AT, V0
		+2	LW        V0, DF98 (AT)
		+3	JR        V0
	 */
	private static Queue<JumpTable> findJumpTables(BaseDataDecoder decoder, ArrayList<String> asmList, Set<Integer> foundTables)
	{
		String[][] tokens = AsmUtils.tokenize(asmList);
		List<Integer> jumpTableLines = AsmUtils.findSequence(tokens, "LUI", "ADDU", "LW", "JR");
		Queue<JumpTable> jumpTables = new LinkedList<>();

		for (int i : jumpTableLines) {
			int tableSize = -1;
			for (int j = i - 2; j >= 0; j--) {
				if (tokens[j][0].equals("SLTIU")) {
					//	assert(tokens[j][1].equals("V0"));
					//	assert(tokens[j][2].equals(tokens[i - 1][2]));
					tableSize = Integer.parseInt(tokens[j][3], 16);
					break;
				}
			}

			if (tableSize <= 0)
				continue;

			if (!tokens[i - 1][3].equals("2"))
				continue;

			assert (tableSize > 0);
			assert (tokens[i - 1][1].equals("V0"));
			assert (tokens[i - 1][3].equals("2"));
			assert (tokens[i][1].equals("AT"));
			int upper = (int) Long.parseLong(tokens[i][2], 16);
			assert (tokens[i + 1][1].equals("AT"));
			assert (tokens[i + 1][2].equals("AT"));
			assert (tokens[i + 1][3].equals("V0"));
			assert (tokens[i + 2][1].equals("V0"));
			int lower = (int) Long.parseLong(tokens[i + 2][2], 16);
			assert (tokens[i + 2][3].equals("AT"));
			assert (tokens[i + 3][1].equals("V0"));

			int tableAddress = AsmUtils.makeAddress(upper, lower);

			JumpTable table = new JumpTable(tableAddress, tableSize);

			if (decoder.isLocalAddress(tableAddress)) {
				if (!foundTables.contains(tableAddress))
					jumpTables.add(table);
			}
			else
				Logger.log(String.format("Function uses nonlocal jump table at %08X%n", tableAddress), Priority.WARNING);
		}

		return jumpTables;
	}

	// reads jump target pointers from a jump table
	private static void scanJumpTable(
		BaseDataDecoder decoder,
		ByteBuffer fileBuffer,
		FunctionScanResults findings,
		JumpTable table,
		int functionAddress)
	{
		fileBuffer.position(decoder.toOffset(table.baseAddress));

		for (int i = 0; i < table.numEntries; i++) {
			int v = fileBuffer.getInt();
			JumpTarget target = new JumpTarget(functionAddress, v, true);
			target.jumpTableAddress = table.baseAddress;
			target.jumpTableIndicies.add(i);

			// only keep the most recent
			if (findings.jumpTableTargets.contains(target))
				findings.jumpTableTargets.remove(target);

			findings.jumpTableTargets.add(target);
		}
	}

	private static void scanPseudoinstructions(
		BaseDataDecoder decoder,
		ArrayList<String> asmList,
		FunctionScanResults findings)
	{
		TreeSet<PseudoInstruction> piSet = PseudoInstruction.scanAll(asmList);

		for (PseudoInstruction pi : piSet) {
			int addr = pi.getAddress();
			if (decoder.isLocalAddress(addr)) {
				switch (pi.getType()) {
					case LI:
					case LA:
					case LAW:
					case LAF:
					case SAW:
					case SAF:
						findings.unknownChildPointers.add(addr);
						break;
					case LAD:
						findings.constDoubles.add(addr);
						break;
					case LAB:
					case LABU:
					case LAH:
					case LAHU:
						break;
					case LTB:
					case LTBU:
						findings.byteTables.add(addr);
						break;
					case LTH:
					case LTHU:
						findings.shortTables.add(addr);
						break;
					case LTW:
					case STW:
						findings.intTables.add(addr);
						break;
					case STF:
					case LTF:
						findings.floatTables.add(addr);
						break;
					default:
						findings.unknownChildPointers.add(addr);
				}
			}
		}

		for (String s : asmList) {
			if (s.startsWith("JAL") && !s.startsWith("JALR")) {
				String[] tokens = s.split(",?\\s+");
				int addr = (int) Long.parseLong(tokens[1], 16);
				if (decoder.isLocalAddress(addr))
					findings.localFunctionCalls.add(addr);
				else
					findings.libraryFunctionCalls.add(addr);
			}
		}
	}

	private static ArrayList<String> scanPass(
		BaseDataDecoder decoder,
		ByteBuffer fileBuffer,
		FunctionScanResults findings,
		int functionAddress)
	{
		ArrayList<String> asmList = new ArrayList<>();
		TreeSet<JumpTarget> currentTargets = new TreeSet<>(findings.branchTargets);
		int currentAddress = functionAddress;

		int nextTargetAddress = currentTargets.isEmpty() ? 0 : currentTargets.pollFirst().targetAddr;
		boolean isReturnDelaySlot = false;
		int v = 0;

		fileBuffer.position(decoder.toOffset(currentAddress));

		do {
			isReturnDelaySlot = (functionAddress == 0x80025C00 && v == 0x01400008) || // special hack for entry_point
				(functionAddress == 0x80064650 && v == 0x1000FFF8) || // special hack for bcopy
				(functionAddress != 0x80064650 && v == 0x03E00008) || // JR RA
				(functionAddress != 0x80064650 && v == 0x03400008); // JR K0
			v = fileBuffer.getInt();

			if (currentAddress == nextTargetAddress || (nextTargetAddress == 0 && !currentTargets.isEmpty()))
				nextTargetAddress = currentTargets.isEmpty() ? 0 : currentTargets.pollFirst().targetAddr;

			int jumpAddr = MIPS.getJumpAddress(currentAddress, v);
			if (jumpAddr != -1) {
				JumpTarget jumpTarget = new JumpTarget(functionAddress, jumpAddr);
				if (!findings.branchTargets.contains(jumpTarget)) {
					findings.branchTargets.add(jumpTarget);
					currentTargets.add(jumpTarget);
				}
			}

			// convert jumps to "BEQ R0, R0" to make all code relative
			if ((v >>> 26) == 2) {
				int seg = (functionAddress & 0xF0000000);
				int dest = seg + ((v & 0x03FFFFFF) << 2);
				int jumpDistance = dest - currentAddress;

				if (jumpDistance != 0)
					v = 0x10000000 | ((jumpDistance - 4) >> 2);
				else
					v = 0x1000FFFF; // BEQ R0, R0 -4
			}

			String s = String.format("%08X", v);
			asmList.add(MIPS.disassemble(s));

			currentAddress += 4;
		}
		while (Integer.compareUnsigned(currentAddress, nextTargetAddress) <= 0 || !isReturnDelaySlot);

		return asmList;
	}

	public static void print(
		BaseDataDecoder decoder,
		Pointer ptr,
		TreeMap<Integer, JumpTarget> jumpTargetMap,
		TreeMap<Integer, JumpTarget> jumpTableTargetMap,
		Library library,
		ByteBuffer fileBuffer,
		PrintWriter pw)
	{
		int functionAddress = ptr.address;
		ArrayList<String> asmList = new ArrayList<>();

		for (int i = 0; i < (ptr.getSize() / 4); i++) {
			int v = fileBuffer.getInt();

			// convert jumps to "BEQ R0, R0" to make all code relative
			if (((v >>> 26) == 2) && decoder.shouldFunctionsRemoveJumps()) {
				int seg = (functionAddress & 0xF0000000);
				int dest = seg + ((v & 0x03FFFFFF) << 2);
				int jumpDistance = dest - (functionAddress + 4 * i);

				if (jumpDistance != 0)
					v = 0x10000000 | (((jumpDistance - 4) >> 2) & 0xFFFF); // dont forget to mask, jump dist can be negative
				else
					v = 0x1000FFFF; // BEQ R0, R0 -4
			}

			String s = String.format("%08X", v);
			asmList.add(MIPS.disassemble(s));
		}

		TreeSet<JumpTarget> jumpTargetSet = new TreeSet<>(jumpTargetMap.values());
		TreeSet<JumpTarget> jumpTableTargetSet = new TreeSet<>(jumpTableTargetMap.values());
		JumpTarget nextJumpTarget, nextJumpTableTarget;
		int nextTargetAddress, nextJumpTargetAddress;

		// disregard jump targets before the function
		while (true) {
			nextJumpTarget = jumpTargetSet.pollFirst();
			nextTargetAddress = (nextJumpTarget == null) ? 0 : nextJumpTarget.targetAddr;

			if (nextJumpTarget == null)
				break;

			if (nextTargetAddress >= functionAddress)
				break;
		}

		// disregard jump table targets before the function
		while (true) {
			nextJumpTableTarget = jumpTableTargetSet.pollFirst();
			nextJumpTargetAddress = (nextJumpTableTarget == null) ? 0 : nextJumpTableTarget.targetAddr;

			if (nextJumpTableTarget == null)
				break;

			if (nextJumpTargetAddress >= functionAddress)
				break;
		}

		int insOffset = 0;
		List<String> newInstructions = PseudoInstruction.addAll(asmList, decoder);

		for (int i = 0; i < newInstructions.size(); i++) {
			// check for jump table labels
			if (functionAddress + insOffset == nextJumpTargetAddress) {
				String tableName = decoder.getVariableName(nextJumpTableTarget.jumpTableAddress);
				StringBuilder sb = (nextJumpTableTarget.jumpTableIndicies.size() > 1) ? new StringBuilder("entries") : new StringBuilder("entry");
				for (int index : nextJumpTableTarget.jumpTableIndicies)
					sb.append(String.format(" %d`", index));
				pw.println("% LBL: from " + tableName + " , " + sb.toString());

				nextJumpTableTarget = jumpTableTargetSet.pollFirst();
				nextJumpTargetAddress = (nextJumpTableTarget == null) ? 0 : nextJumpTableTarget.targetAddr;
			}
			// check for local branch/jump target labels
			if (functionAddress + insOffset == nextTargetAddress) {
				if (decoder.printLineOffsets)
					pw.printf("        %co%X%n", LABEL_CHAR, insOffset);
				else
					pw.printf("\t%co%X%n", LABEL_CHAR, insOffset);

				nextJumpTarget = jumpTargetSet.pollFirst();
				nextTargetAddress = (nextJumpTarget == null) ? 0 : nextJumpTarget.targetAddr;
			}

			String ins = newInstructions.get(i);
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
			else if (tokens[0].equals("JAL")) {
				int address = (int) Long.parseLong(ins.substring(4).trim(), 16);
				LibEntry e = library.get(address);
				if (e != null) {
					if (!e.isFunction())
						throw new StarRodException("%s jump to %08X is not registered as %s in library!",
							ptr.getPointerName(), address, e.type);
					ins = AsmUtils.getFormattedLine("JAL", "%cFunc:%s", SyntaxConstants.EXPRESSION_PREFIX, e.name);
				}
				else
					ins = AsmUtils.getFormattedLine("JAL", "%s", decoder.getVariableName(address));
			}

			if (decoder.printLineOffsets)
				pw.printf("%5X:  %s\n", insOffset, ins);
			else
				pw.printf("\t%s\n", ins);

			// how many bytes does this instruction add?
			if (PseudoInstruction.isValidOpcode(tokens[0])) {
				if ((i + 2) < newInstructions.size()) {
					String k = newInstructions.get(i + 2);
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

	public static int getPatchLength(List<Line> lines)
	{
		int length = 0;

		for (Line line : lines) {
			String opname = line.getString(0);

			if (opname.charAt(0) == LABEL_CHAR)
				continue;

			if (PseudoInstruction.isValidOpcode(opname)) {
				length += 4 * PseudoInstruction.getLengthFromLine(line);
				continue;
			}

			if (opname.equals(PseudoInstruction.RESERVED))
				continue;

			// assume opnames with a period are COP0 instructions like C.LT.S
			if (opname.indexOf('.') >= 0 || MIPS.supportedInstructions.contains(opname))
				length += 4;
			else
				throw new InputFileException(line, "Unknown instruction: " + line.trimmedInput());
		}

		return length;
	}

	public static boolean isFunction(PointerHeuristic h, ByteBuffer fileBuffer)
	{
		int length = h.getLength();

		if (h.getLength() + h.endPadding < 8)
			return false;

		fileBuffer.position(h.start);
		int startA = fileBuffer.getInt();
		int startB = fileBuffer.getInt();

		fileBuffer.position((h.start + length) - 8);
		int endA = fileBuffer.getInt();
		int endB = fileBuffer.getInt();

		// look for push stack: ADDIU SP, SP, signbit
		if ((startA & 0xFFFF8000) == 0x27BD8000)// || jumpRegister == 0x03E00008)
		{
			//	assert(jumpRegister == 0x03E00008);
			h.structOffset = 0;
			return true;
		}

		// stub function
		if (startA == 0x03E00008 && startB == 0x00000000) {
			h.structOffset = 0;
			return true;
		}

		return false;
	}
}
