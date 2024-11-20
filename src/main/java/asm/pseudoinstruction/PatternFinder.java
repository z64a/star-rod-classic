package asm.pseudoinstruction;

import static asm.pseudoinstruction.PatternMatch.PIPattern.*;

import java.util.LinkedList;
import java.util.List;

import asm.MIPS;
import asm.MIPS.Instruction;

public class PatternFinder
{
	// used for the state machine to remember chains of instructions
	// LUI -> various pseudoinstrutions
	// LUI -> ADDU -> table pseudoinstrutions
	// DADDU+R0 -> clear/copy
	private enum PrimaryState
	{
		OTHER, LUI, ADDU
	}

	// use a simple state machine to find instruction sequences that match
	// those of pseudoinstructions. the last instruction is allowed to be
	// in the delay slot of a jump or branch instruction
	public static List<PatternMatch> search(String[][] tokens)
	{
		List<PatternMatch> matchList = new LinkedList<>();
		PrimaryState state = PrimaryState.OTHER;
		boolean delaySlot = false;

		for (int i = 0; i < tokens.length; i++) {
			String[] line = tokens[i];

			// string switch would compile to O(logN) lookupswitch
			// this enum switch should compile to O(N) tableswitch instead
			// - lol (years later)
			Instruction ins = MIPS.InstructionMap.get(line[0]);

			// MOV.S, NOP, etc
			if (ins == null) {
				state = PrimaryState.OTHER;
				delaySlot = false;
				continue;
			}

			switch (ins) {
				case LUI:
					state = delaySlot ? PrimaryState.OTHER : PrimaryState.LUI;
					delaySlot = false;
					break;

				case ADDU:
					if (delaySlot)
						state = PrimaryState.OTHER;
					else
						state = (state == PrimaryState.LUI) ? PrimaryState.ADDU : PrimaryState.OTHER;
					delaySlot = false;
					break;

				case ORI:
					if (state == PrimaryState.LUI) {
						if (i < tokens.length - 1) {
							String[] nextLine = tokens[i + 1];
							Instruction nextIns = MIPS.InstructionMap.get(nextLine[0]);
							if (nextIns == Instruction.MTC1) {
								matchList.add(new PatternMatch(LIWF, ins, i + 1, delaySlot));
								state = PrimaryState.OTHER;
								delaySlot = false;
								break;
							}
						}

						// match LIWF match
						matchList.add(new PatternMatch(LIW, ins, i, delaySlot));
						//	break;
					}
					state = PrimaryState.OTHER;
					delaySlot = false;
					break;

				case ADDIU:
					if (state == PrimaryState.OTHER) {
						// ADDIU XX, R0, IMM
						if (line.length == 4 && line[2].equals("R0"))
							matchList.add(new PatternMatch(LIH, ins, i, false)); // length = 1, never reserve delay slot
					}
					else if (state == PrimaryState.LUI) {
						matchList.add(new PatternMatch(LIW, ins, i, delaySlot)); // LA, specifically
					}
					state = PrimaryState.OTHER;
					delaySlot = false;
					break;

				case MTC1:
					if (state == PrimaryState.LUI)
						matchList.add(new PatternMatch(LIHF, ins, i, delaySlot));
					state = PrimaryState.OTHER;
					delaySlot = false;
					break;

				// these are the wierd ones, won't be implementing them unless necessary
				case LL:
				case LLD:
				case LDL:
				case LDR:
				case LWL:
				case LWR:
				case LWU:
				case SC:
				case SCD:
				case SDL:
				case SDR:
				case SWL:
				case SWR:
					// memory instructions after LUI or LUI-ADDU can be valid pseudoinstructions
				case LB:
				case LBU:
				case LH:
				case LHU:
				case LW:
				case LWC1:
				case LDC1:
				case SB:
				case SH: // unused?
				case SW:
					//case SD: // unused?
				case SWC1:
					if (state == PrimaryState.LUI)
						matchList.add(new PatternMatch(LSA, ins, i, delaySlot));
					else if (state == PrimaryState.ADDU)
						matchList.add(new PatternMatch(LST, ins, i, delaySlot));
					state = PrimaryState.OTHER;
					delaySlot = false;
					break;

				// instructions with subsequent delay slot leave state alone
				case BEQ:
				case BEQL:
				case BNE:
				case BNEL:
				case JALR:
				case BLEZ:
				case BGTZ:
				case BLEZL:
				case BGTZL:
				case BLTZ:
				case BGEZ:
				case BLTZL:
				case BGEZL:
				case BLTZAL:
				case BGEZAL:
				case BLTZALL:
				case BGEZALL:
				case JR:
				case J:
				case JAL:
				case BC1F:
				case BC1T:
				case BC1FL:
				case BC1TL:
					assert (!delaySlot);
					delaySlot = true;
					break;

				case DADDU:
					if (line.length == 4 && line[3].equals("R0")) {
						if (line[2].equals("R0"))
							matchList.add(new PatternMatch(CLR, ins, i, false)); // length = 1, never reserve delay slot
						else
							matchList.add(new PatternMatch(CPY, ins, i, false)); // length = 1, never reserve delay slot
					}
					state = PrimaryState.OTHER;
					delaySlot = false;
					break;

				// any other instruction resets the state
				default:
					state = PrimaryState.OTHER;
					delaySlot = false;
			}
		}

		return matchList;
	}
}
