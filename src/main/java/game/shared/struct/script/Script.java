package game.shared.struct.script;

import java.io.PrintWriter;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Stack;
import java.util.TreeMap;

import app.Environment;
import app.config.Options;
import app.input.InputFileException;
import app.input.InvalidInputException;
import app.input.Line;
import game.map.marker.Marker;
import game.shared.DataUtils;
import game.shared.ProjectDatabase;
import game.shared.decoder.BaseDataDecoder;
import game.shared.decoder.Pointer;
import game.shared.decoder.PointerHeuristic;
import game.shared.encoder.BaseDataEncoder;
import game.shared.encoder.ScriptParsingException;
import game.shared.lib.LibEntry;
import game.shared.lib.LibEntry.EntryType;
import game.shared.lib.LibEntry.LibParam;
import game.shared.lib.LibEntry.LibParamList;
import game.shared.lib.LibEntry.LibType;
import game.shared.lib.LibEntry.ParamCategory;
import game.shared.lib.LibEntry.ParamListType;
import game.shared.lib.Library;
import game.shared.struct.Struct;
import game.shared.struct.script.inline.ConstantDatabase;
import game.shared.struct.script.inline.InlineCompiler;

public abstract class Script
{
	// @formatter:off
	private static enum Format
	{
		Plain,		// opname, args ...
		If,			// 'if' arg0 operation arg1
		Case,		// 'case' operation arg0
		CaseRange,	// 'case' arg0 to arg1
		Set,		// opname arg0 = arg1
		Call		// 'call' funcname ( args ... )
	}
	// @formatter:on

	private static final String IF = "If";
	private static final String CASE = "Case";
	private static final String EXEC = "Exec";

	public static enum Command
	{
		// @formatter:off
		// basic flow control
		END			(0x01, 0, "End"),
		RETURN		(0x02, 0, "Return"),
		LABEL		(0x03, 1, "Label"),
		GOTO		(0x04, 1, "Goto"),
		LOOP		(0x05, 1, 0, 1, "Loop"),
		END_LOOP	(0x06, 0,-1, 0, "EndLoop"),
		BREAK_LOOP	(0x07, 0, "BreakLoop"),
		WAIT_FRAMES	(0x08, 1, "Wait"),
		WAIT_SECS	(0x09, 1, "WaitSeconds"),
		// if commands
		IF_EQ		(0x0A, 2, 0, 1, IF, Format.If, "=="),
		IF_NEQ		(0x0B, 2, 0, 1, IF, Format.If, "!="),
		IF_LT		(0x0C, 2, 0, 1, IF, Format.If, "<"),
		IF_GT		(0x0D, 2, 0, 1, IF, Format.If, ">"),
		IF_LTEQ		(0x0E, 2, 0, 1, IF, Format.If, "<="),
		IF_GTEQ		(0x0F, 2, 0, 1, IF, Format.If, ">="),
		IF_AND		(0x10, 2, 0, 1, IF, Format.If, "&"),
		IF_NAND		(0x11, 2, 0, 1, IF, Format.If, "!&"),
		ELSE		(0x12, 0,-1, 1, "Else"),
		ENDIF		(0x13, 0,-1, 0, "EndIf"),
		// switch commands
		SWITCH_VAR	(0x14, 1, 0, 2, "Switch"),
		SWITCH_CONST(0x15, 1, 0, 2, "SwitchConst"),
		CASE_EQ		(0x16, 1,-1, 1, CASE, Format.Case, "=="),
		CASE_NEQ	(0x17, 1,-1, 1, CASE, Format.Case, "!="),
		CASE_LT		(0x18, 1,-1, 1, CASE, Format.Case, "<"),
		CASE_GT		(0x19, 1,-1, 1, CASE, Format.Case, ">"),
		CASE_LTEQ	(0x1A, 1,-1, 1, CASE, Format.Case, "<="),
		CASE_GTEQ	(0x1B, 1,-1, 1, CASE, Format.Case, ">="),
		CASE_DEFAULT(0x1C, 0,-1, 1, "Default"),
		MCASE_OR	(0x1D, 1,-1, 1, "CaseOR"),
		MCASE_AND	(0x1E, 1,-1, 1, "CaseAND"),
		CASE_AND	(0x1F, 1,-1, 1, CASE, Format.Case, "&"),
		END_GROUP	(0x20, 0,-1, 1, "EndCaseGroup"),
		CASE_RANGE	(0x21, 2,-1, 1, CASE, Format.CaseRange, "to"),
		BREAK_CASE	(0x22, 0, "BreakCase"),
		END_SWITCH	(0x23, 0,-2, 0, "EndSwitch"),
		// assignment commands
		SET_INT		(0x24, 2, "Set"),		// copy int from one variable to another	(formerly SETVAR)
		SET_CONST	(0x25, 2, "SetConst"),	// set the value of a variable				(formerly SETVARI)
		SET_FLT		(0x26, 2, "SetF"),		// copy float from one variable to another	(formerly SETVARF)
		// arithmetic commands
		ADD_INT		(0x27, 2, "Add"),
		SUB_INT		(0x28, 2, "Sub"),
		MUL_INT		(0x29, 2, "Mul"),
		DIV_INT		(0x2A, 2, "Div"),
		MOD_INT		(0x2B, 2, "Mod"),
		ADD_FLT		(0x2C, 2, "AddF"),
		SUB_FLT		(0x2D, 2, "SubF"),
		MUL_FLT		(0x2E, 2, "MulF"),
		DIV_FLT		(0x2F, 2, "DivF"),
		// buffer commands
		SET_BUFFER	(0x30, 1, "UseIntBuffer"),
		GET_1_INT	(0x31, 1, "Get1Int"),
		GET_2_INT	(0x32, 2, "Get2Int"),
		GET_3_INT	(0x33, 3, "Get3Int"),
		GET_4_INT	(0x34, 4, "Get4Int"),
		GET_INT_N	(0x35, 2, "GetIntN"),
		SET_FBUFFER	(0x36, 1, "UseFloatBuffer"),
		GET_1_FLT	(0x37, 1, "Get1Float"),
		GET_2_FLT	(0x38, 2, "Get2Float"),
		GET_3_FLT	(0x39, 3, "Get3Float"),
		GET_4_FLT	(0x3A, 4, "Get4Float"),
		GET_FLT_N	(0x3B, 2, "GetFloatN"),
		SET_ARRAY	(0x3C, 1, "UseArray"),
		SET_FLAGS	(0x3D, 1, "UseFlags"),
		ALLOC_ARRAY	(0x3E, 2, "NewArray"),
		// bitwise logic commands
		AND			(0x3F, 2, "AND"),
		AND_CONST	(0x40, 2, "ConstAND"),
		OR			(0x41, 2, "OR"),
		OR_CONST	(0x42, 2, "ConstOR"),
		// function and script commands
		CALL		(0x43,-1, "Call", Format.Call, ""),
		EXEC1		(0x44, 1, EXEC),		// Run(1) -- launches an independent script ....or maybe Start/Launch?
		EXEC2		(0x45, 2, EXEC),		// Run(2) -- launches an independent script, returns the ID
		EXEC_WAIT	(0x46, 1, "ExecWait"),	// Exec -- executes a script and waits for it to finish
		TRIGGER		(0x47, 5, "Bind"),
		RM_TRIGGER	(0x48, 0, "Unbind"),
		KILL		(0x49, 1, "Kill"),
		JUMP		(0x4A, 1, "Jump"),
		PRIORITY	(0x4B, 1, "SetPriority"),
		TIMESCALE	(0x4C, 1, "SetTimescale"),
		GROUP		(0x4D, 1, "SetGroup"),
		LOCK		(0x4E, 6, "BindLock"),
		SUSPEND_ALL	(0x4F, 1, "SuspendAll"),
		RESUME_ALL	(0x50, 1, "ResumeAll"),
		SUSPEND_OTHERS	(0x51, 1, "SuspendOthers"),
		RESUME_OTHERS	(0x52, 1, "ResumeOthers"),
		SUSPEND		(0x53, 1, "Suspend"),
		RESUME		(0x54, 1, "Resume"),
		EXISTS		(0x55, 2, "DoesScriptExist"),
		THREAD1		(0x56, 0, 0, 1, "Thread"),
		END_THREAD1	(0x57, 0,-1, 0, "EndThread"),
		THREAD2		(0x58, 0, 0, 1, "ChildThread"),
		END_THREAD2	(0x59, 0,-1, 0, "EndChildThread"),
		// 802C6E14	5A	NOP
		PRINT_DEBUG	(0x5B, 1, "PrintVar");
		/*
		802C739C	5C	SetDebugMode?? Sets 160 to arg
		802C73B0	5D	NOP
		802C73B8	5E	GetID (Broken)
		 */
		// @formatter:on

		public final int opcode;
		public final int argc;
		public final Format fmt;

		public final String name;
		public final String operator; // assumed to be unqiue for a given fmt

		public final int tabsBefore;
		public final int tabsAfter;

		public final String opString;

		// standard format, no tab change
		private Command(int opcode, int argc, String name)
		{
			this(opcode, argc, 0, 0, name, Format.Plain, "");
		}

		// special format, no tab change
		private Command(int opcode, int argc, String name, Format fmt, String operator)
		{
			this(opcode, argc, 0, 0, name, fmt, operator);
		}

		// standard format, tab change
		private Command(int opcode, int argc, int before, int after, String name)
		{
			this(opcode, argc, before, after, name, Format.Plain, "");
		}

		// special format, tab change
		private Command(int opcode, int argc, int before, int after, String name, Format fmt, String operator)
		{
			this.opcode = opcode;
			this.argc = argc;
			this.tabsBefore = before;
			this.tabsAfter = after;
			this.name = name;
			this.fmt = fmt;
			this.operator = operator;
			opString = String.format("%08X", opcode);
		}

		private static final Command[] table;

		private static final HashMap<String, Command> cmdMap;
		private static final HashMap<String, Command> ifOpMap;
		private static final HashMap<String, Command> caseOpMap;

		static {
			int maxOpcode = 0;
			for (Command cmd : Command.values()) {
				if (cmd.opcode > maxOpcode)
					maxOpcode = cmd.opcode;
			}

			table = new Command[maxOpcode + 1];
			cmdMap = new HashMap<>();
			ifOpMap = new HashMap<>();
			caseOpMap = new HashMap<>();

			for (Command cmd : Command.values()) {
				table[cmd.opcode] = cmd;
				if (cmd.fmt == Format.If)
					ifOpMap.put(cmd.operator, cmd);
				else if (cmd.fmt == Format.Case)
					caseOpMap.put(cmd.operator, cmd);
				else
					cmdMap.put(cmd.name, cmd);
			}
		}

		public static Command get(int opcode)
		{
			if (opcode <= 0 || opcode >= table.length)
				return null;

			return table[opcode];
		}

		public static Command get(String name)
		{
			return cmdMap.get(name);
		}

		public static Command getIf(String operator)
		{
			return ifOpMap.get(operator);
		}

		public static Command getCase(String operator)
		{
			return caseOpMap.get(operator);
		}
	}

	public static void scan(BaseDataDecoder decoder, Pointer ptr, ByteBuffer fileBuffer)
	{
		ptr.script = readScript(fileBuffer);
		int endPos = fileBuffer.position();

		boolean aggressive = Environment.mainConfig.getBoolean(Options.TrackScriptVarTypes);
		doStaticAnalysis(decoder, ptr.script, aggressive);

		fileBuffer.position(endPos);
	}

	public static class ScriptLine
	{
		public final Command cmd;
		public final int[] args;
		public final LibType[] types;

		public final int lineNum;
		public final int startOffset;
		private final int indentLevel;

		private int callNameLength = -1;
		private int callAlignedLength = -1;

		public Marker marker = null; // some lines generate a marker, reference is saved here

		private ScriptLine(Command cmd, int[] args, int lineNum, int startOffset, int indentLevel)
		{
			this.cmd = cmd;
			this.args = new int[args.length];
			this.types = new LibType[args.length];
			for (int i = 0; i < args.length; i++) {
				this.args[i] = args[i];
				this.types[i] = null;
			}
			this.lineNum = lineNum;
			this.startOffset = startOffset;
			this.indentLevel = indentLevel;
		}
	}

	public static ArrayList<ScriptLine> readScript(ByteBuffer fileBuffer)
	{
		int lineStart = 0;
		int indentLevel = 0;

		ArrayList<ScriptLine> lines = new ArrayList<>();
		int opcode;
		do {
			opcode = fileBuffer.getInt();
			int nargs = fileBuffer.getInt();
			int args[] = new int[nargs];

			Command cmd = Command.get(opcode);
			assert (cmd != null);
			assert (cmd.argc == -1 || nargs == cmd.argc);

			for (int i = 0; i < nargs; i++)
				args[i] = fileBuffer.getInt();

			indentLevel += cmd.tabsBefore;
			lines.add(new ScriptLine(cmd, args, lines.size(), lineStart, indentLevel));
			indentLevel += cmd.tabsAfter;

			lineStart += (8 + 4 * nargs);
		}
		while (opcode != 0x1);

		return lines;
	}

	private static Integer getVarIndex(int v)
	{
		int i = v + 30000000;
		if (i < 0 || i > 15)
			return null;
		return i;
	}

	private static void clearVar(LibType[] varTypes, int v)
	{
		Integer i = getVarIndex(v);
		if (i != null)
			varTypes[i] = null;
	}

	private static boolean checkForEnum(LibType type, int value)
	{
		return (type != null) && (type.category == ParamCategory.Enum) && (getVarIndex(value) == null);
	}

	private static void doStaticAnalysis(BaseDataDecoder decoder, ArrayList<ScriptLine> lines, boolean aggressive)
	{
		Library library = decoder.getLibrary();
		Stack<LibType> switchEnums = new Stack<>();
		LibType[] varTypes = new LibType[16];

		Integer dest, src;
		for (int lineIndex = 0; lineIndex < lines.size(); lineIndex++) {
			ScriptLine line = lines.get(lineIndex);

			switch (line.cmd) {
				case SET_INT:
					dest = getVarIndex(line.args[0]);
					src = getVarIndex(line.args[1]);

					if (dest == null)
						break;

					if (src == null) {
						varTypes[dest] = null;
						break;
					}

					varTypes[dest] = varTypes[src];
					break;

				case GET_1_INT:
				case GET_1_FLT:
				case GET_INT_N:
				case GET_FLT_N:
					clearVar(varTypes, line.args[0]);
					break;
				case GET_2_INT:
				case GET_2_FLT:
					clearVar(varTypes, line.args[0]);
					clearVar(varTypes, line.args[1]);
					break;
				case GET_3_INT:
				case GET_3_FLT:
					clearVar(varTypes, line.args[0]);
					clearVar(varTypes, line.args[1]);
					clearVar(varTypes, line.args[2]);
					break;
				case GET_4_INT:
				case GET_4_FLT:
					clearVar(varTypes, line.args[0]);
					clearVar(varTypes, line.args[1]);
					clearVar(varTypes, line.args[2]);
					clearVar(varTypes, line.args[3]);
					break;

				case ALLOC_ARRAY:
				case EXISTS:
					clearVar(varTypes, line.args[1]);
					break;

				case TRIGGER:
					clearVar(varTypes, line.args[4]); // set var to trigger*
				case LOCK:
					line.types[1] = LibEntry.resolveType(decoder.getScope(), "#trigger");
					// this can be a collider ID or coord pointer
					if (line.args[1] != 0x10 && line.args[2] >= 0)
						line.types[2] = LibEntry.resolveType(decoder.getScope(), "colliderID");
					else
						line.types[2] = null; // coord pointer
					break;

				case SET_CONST:
				case SET_FLT:
				case ADD_INT:
				case SUB_INT:
				case MUL_INT:
				case DIV_INT:
				case MOD_INT:
				case ADD_FLT:
				case SUB_FLT:
				case MUL_FLT:
				case DIV_FLT:
				case AND:
				case AND_CONST:
				case OR:
				case OR_CONST:
					clearVar(varTypes, line.args[0]);
					break;

				case IF_EQ:
				case IF_NEQ:
				case IF_LTEQ:
				case IF_GTEQ:
				case IF_LT:
				case IF_GT:
				case IF_AND:
				case IF_NAND:
					Integer left = getVarIndex(line.args[0]);
					Integer right = getVarIndex(line.args[1]);

					if (line.args[0] == 0xF5DE0180 && right == null) // story progress
					{
						line.types[1] = LibEntry.resolveType(decoder.getScope(), "#story");
					}
					else if (left == null && right != null) {
						// copy type: L <-- R
						line.types[0] = varTypes[right];
						line.types[1] = varTypes[right];
						continue;
					}
					else if (left != null && right == null) {
						// copy type: L --> R
						line.types[0] = varTypes[left];
						line.types[1] = varTypes[left];
						continue;
					}
					break;

				case SWITCH_VAR:
					src = getVarIndex(line.args[0]);
					if (src != null)
						switchEnums.push(varTypes[src]);
					else if (line.args[0] == 0xF5DE0180) // story progress
						switchEnums.push(LibEntry.resolveType(decoder.getScope(), "#story"));
					else
						switchEnums.push(null);
					break;

				case SWITCH_CONST:
					switchEnums.push(null);
					break;

				case END_SWITCH:
					switchEnums.pop();
					break;

				case CASE_EQ:
				case CASE_NEQ:
				case CASE_LT:
				case CASE_GT:
				case CASE_LTEQ:
				case CASE_GTEQ:
				case CASE_AND:
				case MCASE_OR:
				case MCASE_AND:
					line.types[0] = switchEnums.peek();
					continue;

				case CASE_RANGE:
					line.types[0] = switchEnums.peek();
					line.types[1] = switchEnums.peek();
					continue;

				case CALL:
					if (!aggressive) {
						Arrays.fill(varTypes, null);
					}

					boolean clearVars = false;
					examine_call:
					while (true) {
						unknown_call:
						while (true) {
							LibEntry callEntry = library.get(line.args[0]);
							if (callEntry == null) {
								line.callNameLength = 8;
								break unknown_call;
							}

							line.callNameLength = callEntry.name.length();
							if (callEntry.type != EntryType.api)
								break unknown_call;

							LibParamList params = callEntry.getMatchingParams(line.args.length - 1);
							if (params == null || params.listType != ParamListType.Normal)
								break unknown_call;

							// known library function with valid parameter list
							int j = 1;
							for (LibParam param : params) {
								if (param.isOutParam) {
									dest = getVarIndex(line.args[j]);
									if (dest != null) {
										varTypes[dest] = param.outTypeInfo;
									}
								}
								line.types[j] = param.typeInfo;
								j++;
							}

							if (aggressive) {
								for (LibParam param : callEntry.getReturns()) {
									if (param.storage == null) {
										clearVars = true;
										break examine_call;
									}

									if (param.storage.baseName.equalsIgnoreCase("Var"))
										varTypes[param.storage.offset] = param.typeInfo;
								}
							}
							break examine_call;
						}

						if (aggressive) {
							for (int j = 1; j < line.args.length; j++) {
								// assume all variables passed to unknown function are outVars
								dest = getVarIndex(line.args[j]);
								if (dest != null)
									varTypes[dest] = null;
							}
						}
						else {
							clearVars = true;
						}
						break examine_call;
					}

					if (clearVars) {
						Arrays.fill(varTypes, null);
					}
					break;

				case EXEC1:
				case EXEC2:
				case EXEC_WAIT:
					examine_call:
					while (true) {
						LibEntry callEntry = library.get(line.args[0]);
						if (callEntry == null || callEntry.type != EntryType.script)
							break examine_call;

						LibParamList params = callEntry.getDefaultParams();
						if (params == null || params.listType != ParamListType.Normal)
							break examine_call;

						int linePos = lineIndex;
						while (true) {
							if (--linePos < 0)
								break examine_call;

							ScriptLine argLine = lines.get(linePos);
							if (argLine.cmd != Command.SET_INT)
								break examine_call;

							Integer varIndex = getVarIndex(argLine.args[0]);
							if (varIndex == null)
								break examine_call;

							for (LibParam param : params) {
								if (param.storage == null)
									continue;

								if (!param.storage.baseName.equalsIgnoreCase("Var"))
									continue;

								if (param.storage.offset != varIndex)
									continue;

								argLine.types[1] = param.typeInfo;
							}
						}
					}
					Arrays.fill(varTypes, null);
					break;

				default:
					break;
			}

			// save var types for line
			for (int i = 0; i < line.args.length; i++) {
				Integer index = getVarIndex(line.args[i]);
				if (index != null)
					line.types[i] = varTypes[index];
			}
		}
	}

	private static void doAlignment(BaseDataDecoder decoder, ArrayList<ScriptLine> lines)
	{
		int indent = -1;
		ArrayList<ScriptLine> callLines = new ArrayList<>(lines.size());
		for (int lineIndex = 0; lineIndex < lines.size(); lineIndex++) {
			ScriptLine line = lines.get(lineIndex);

			if (line.indentLevel != indent) {
				if (!callLines.isEmpty())
					alignLines(callLines);
				callLines.clear();
				indent = line.indentLevel;
			}

			if (line.cmd == Command.CALL && decoder.isLocalAddress(line.args[0]))
				line.callNameLength = decoder.getVariableName(line.args[0]).length();

			if (line.callNameLength > 0) {
				int spaces = 0;
				/*
				// ensures equivalent of 2 trailing spaces + tab-alignment
				switch(line.callNameLength % 4) {
				case 0: spaces = 2; break;
				case 1: spaces = 5; break;
				case 2: spaces = 4; break;
				case 3: spaces = 3; break;
				}
				 */
				// ensures equivalent of 1 trailing space + tab-alignment
				switch (line.callNameLength % 4) {
					// @formatter:off
					case 0: spaces = 2; break;
					case 1: spaces = 1; break;
					case 2: spaces = 4; break;
					case 3: spaces = 3; break;
					// @formatter:on
				}

				line.callAlignedLength = (line.callNameLength + spaces);

				if (line.args.length > 1) // dont try to align empty param lists
					callLines.add(line);
			}
		}

		if (!callLines.isEmpty())
			alignLines(callLines);
	}

	private static final void alignLines(List<ScriptLine> lines)
	{
		Collections.sort(lines, (a, b) -> a.callAlignedLength - b.callAlignedLength);

		while (!lines.isEmpty()) {
			int median;
			if (lines.size() == 1)
				median = lines.get(0).callAlignedLength;
			else if (lines.size() % 2 == 1)
				median = lines.get((lines.size() / 2) + 1).callAlignedLength;
			else { // odd and > 1
				int mid = lines.size() / 2;
				int first = lines.get(mid - 1).callAlignedLength;
				int second = lines.get(mid).callAlignedLength;
				median = Math.max(first, second);
			}

			Iterator<ScriptLine> iter = lines.iterator();
			while (iter.hasNext()) {
				ScriptLine line = iter.next();
				if (line.callAlignedLength <= median) {
					if (median - line.callAlignedLength < 8)
						line.callAlignedLength = median;
					iter.remove();
				}
			}
		}
	}

	private static final boolean isFlag(int value)
	{
		ScriptVariable varType = ScriptVariable.getType(value);
		switch (varType) {
			case AreaFlag:
			case MapFlag:
			case GameFlag:
			case ModFlag:
			case FlagArray:
			case Flag:
				return true;
			default:
				return false;
		}
	}

	private static final String OPCODE_FMT = "%-4s  ";
	private static final String SPACE_TAB = "    ";

	public static void print(BaseDataDecoder decoder, Pointer ptr, ByteBuffer fileBuffer, PrintWriter pw)
	{
		print(decoder, ptr, ptr.address, ptr.getSize(), fileBuffer, pw);
	}

	public static void print(BaseDataDecoder decoder, Pointer ptr, int startAddress, int length, ByteBuffer fileBuffer, PrintWriter pw)
	{
		doAlignment(decoder, ptr.script);

		for (ScriptLine line : ptr.script) {
			if (decoder.printLineOffsets)
				pw.printf("%5X:  ", line.startOffset);
			else
				pw.print(decoder.useTabIndents ? "\t" : SPACE_TAB);

			for (int i = 0; i < line.indentLevel; i++)
				pw.print(decoder.useTabIndents ? "\t" : SPACE_TAB);

			switch (line.cmd) {
				case LOOP:
					if (line.args.length == 0 || line.args[0] == 0) {
						pw.printf(line.cmd.name); // suppress printing loop count for infinite loops
					}
					else {
						pw.printf(OPCODE_FMT, line.cmd.name);
						decoder.printScriptWord(pw, ptr, line.types[0], line.args[0]);
					}
					break;

				case SET_INT:
					pw.printf(OPCODE_FMT, line.cmd.name);
					decoder.printScriptWord(pw, ptr, line.types[0], line.args[0]);
					pw.print(" ");

					switch (line.args[0]) {
						case 0xF5DE0180: // story progress
							pw.print(ProjectDatabase.StoryType.getConstantString(line.args[1]) + " ");
							break;
						case 0xF5DE0329: // world map location
							pw.print(ProjectDatabase.LocationType.getConstantString(line.args[1]) + " ");
							break;
						default:
							if (isFlag(line.args[0]) && line.types[1] == null)
								decoder.printBoolean(pw, line.args[1]);
							else
								decoder.printScriptWord(pw, ptr, line.types[1], line.args[1]);
							break;
					}
					if (checkForEnum(line.types[1], line.args[1]))
						pw.printf("%% %X", line.args[1]);
					break;

				case SET_CONST:
				case SET_FLT:
				case ADD_INT:
				case SUB_INT:
				case MUL_INT:
				case DIV_INT:
				case MOD_INT:
				case ADD_FLT:
				case SUB_FLT:
				case MUL_FLT:
				case DIV_FLT:
					pw.printf(OPCODE_FMT, line.cmd.name);
					decoder.printScriptWord(pw, ptr, line.types[0], line.args[0]);
					pw.print(" ");
					decoder.printScriptWord(pw, ptr, line.types[1], line.args[1]);
					break;

				case IF_EQ:
				case IF_NEQ:
				case IF_LTEQ:
				case IF_GTEQ:
				case IF_LT:
				case IF_GT:
				case IF_AND:
				case IF_NAND:
					pw.printf("%-2s  ", line.cmd.name);
					decoder.printScriptWord(pw, ptr, line.types[0], line.args[0]);
					pw.printf(" %s  ", line.cmd.operator);
					if (isFlag(line.args[0]) && line.types[1] == null)
						decoder.printBoolean(pw, line.args[1]);
					else
						decoder.printScriptWord(pw, ptr, line.types[1], line.args[1]);
					if (checkForEnum(line.types[1], line.args[1]))
						pw.printf("%% %X", line.args[1]);
					break;

				case SWITCH_VAR:
					pw.printf(OPCODE_FMT, line.cmd.name);
					decoder.printScriptWord(pw, ptr, line.types[0], line.args[0]);
					break;

				case SWITCH_CONST:
					pw.printf(OPCODE_FMT, line.cmd.name);
					decoder.printScriptWord(pw, ptr, line.types[0], line.args[0]);
					break;

				case END_SWITCH:
					pw.print(line.cmd.name);
					break;

				case CASE_EQ:
				case CASE_NEQ:
				case CASE_LT:
				case CASE_GT:
				case CASE_LTEQ:
				case CASE_GTEQ:
				case CASE_AND:
					pw.printf("%-4s  %s  ", line.cmd.name, line.cmd.operator);
					decoder.printScriptWord(pw, ptr, line.types[0], line.args[0]);
					if (checkForEnum(line.types[0], line.args[0]))
						pw.printf("%% %X", line.args[0]);
					break;

				case MCASE_OR:
				case MCASE_AND:
					pw.printf("%-4s  ==  ", line.cmd.name);
					decoder.printScriptWord(pw, ptr, line.types[0], line.args[0]);
					if (checkForEnum(line.types[0], line.args[0]))
						pw.printf("%% %X", line.args[0]);
					break;

				case CASE_RANGE:
					pw.print(line.cmd.name + "  ");
					decoder.printScriptWord(pw, ptr, line.types[0], line.args[0]);
					pw.printf(" %s  ", line.cmd.operator);
					decoder.printScriptWord(pw, ptr, line.types[1], line.args[1]);
					if (checkForEnum(line.types[0], line.args[0]) || checkForEnum(line.types[1], line.args[1]))
						pw.printf("%% %X %s %X", line.args[0], line.cmd.operator, line.args[1]);
					break;

				case CALL:
					pw.printf(OPCODE_FMT, line.cmd.name);
					decoder.printFunctionCall(ptr, pw, line, startAddress + line.startOffset, line.callAlignedLength);
					break;

				case EXEC1:
				case EXEC2:
				case EXEC_WAIT:
					pw.printf(OPCODE_FMT, line.cmd.name);
					decoder.printScriptExec(ptr, pw, line, startAddress + line.startOffset);
					break;

				case WAIT_SECS:
				case WAIT_FRAMES:
					pw.printf(OPCODE_FMT, line.cmd.name);
					int time = line.args[0];
					if (0 < time && time < 5000)
						pw.print(time + "`");
					else
						decoder.printScriptWord(pw, ptr, line.types[0], line.args[0]);
					break;

				case GOTO:
				case LABEL:
					pw.printf(OPCODE_FMT, line.cmd.name);
					pw.printf("%X", line.args[0]);
					break;

				default:
					pw.printf((line.args.length == 0 ? "%s" : OPCODE_FMT), line.cmd.name);
					for (int i = 0; i < line.args.length; i++)
						decoder.printScriptWord(pw, ptr, line.types[i], line.args[i]);
			}
			pw.println();
		}
	}

	private static void checkLength(Line line, int argc)
	{
		if (line.numTokens() - 1 != argc)
			throw new ScriptParsingException(line, "Invalid script command: %n%s", line.trimmedInput());
	}

	// cache common lengths
	private static String[] LENGTH_STRINGS = {
			"00000000", "00000001", "00000002", "00000003", "00000004", "00000005", "00000006"
	};

	private static void addOpcodeLength(List<String> list, String opString, int len)
	{
		String lenString;
		if (len < LENGTH_STRINGS.length)
			lenString = LENGTH_STRINGS[len];
		else
			lenString = String.format("%08X", len);

		list.add(opString);
		list.add(lenString);
	}

	private static void addArgs(List<String> list, String ... tokens)
	{
		Collections.addAll(list, tokens);
	}

	public static void encode(ConstantDatabase db, Library library, List<Line> lines)
	{
		ListIterator<Line> iter = lines.listIterator();
		HashMap<String, String> labelNameMap = new HashMap<>();
		int nextLabelIndex = 0;

		List<String> newLineStrings = new ArrayList<>(8);

		HashMap<String, String> varNameMap = new HashMap<>();
		int nextDynamicVarIndex = 0;

		while (iter.hasNext()) {
			newLineStrings.clear();
			Line line = iter.next();

			String opcode = line.getString(0);

			if (opcode.equalsIgnoreCase("declare")) {
				// declare *X, *Y,*Z

				line.str = line.str.replaceAll(",", " ");
				line.tokenize("\\s+");

				if (line.numTokens() < 2)
					throw new InputFileException(line, "Invalid variable declaration: %n%s", line.trimmedInput());

				for (int i = 1; i < line.numTokens(); i++) {
					String name = line.tokens[i].str;

					try {
						ScriptVariable.parseScriptVariable(name);
						throw new InputFileException(line, "Variable name already in use: %n%s",
							name, line.trimmedInput());
					}
					catch (InvalidInputException e) {
						// OK name -- not used by any other script variable
					}

					if (varNameMap.containsKey(name))
						throw new InputFileException(line, "Variable name already declared: %n%s",
							name, line.trimmedInput());

					if (!ScriptVariable.isValidName(name))
						throw new InputFileException(line, "Variable name has invalid format: %s%n"
							+ "Must begin with * and use only alphanumeric characters and underscores. %n%s",
							name, line.trimmedInput());

					// everything is ok, add the definition
					varNameMap.put(name, ScriptVariable.getString(ScriptVariable.Dynamic, nextDynamicVarIndex++));
				}

				iter.remove();
				continue;
			}

			// replace variable names
			List<String> replacedVarNames = new ArrayList<>(line.numTokens());
			for (int i = 0; i < line.numTokens(); i++) {
				String s = line.getString(i);
				if (varNameMap.containsKey(s))
					replacedVarNames.add(varNameMap.get(s));
				else
					replacedVarNames.add(s);
			}
			line.replace(replacedVarNames);

			if (opcode.equals(IF)) {
				checkLength(line, 3);

				String operator = line.getString(2);
				Command cmd = Command.getIf(operator);
				if (cmd == null)
					throw new ScriptParsingException(line, "Unknown comparison operator: " + operator);

				addOpcodeLength(newLineStrings, cmd.opString, 2);
				addArgs(newLineStrings, line.getString(1), line.getString(3));
			}
			else if (opcode.equals(CASE)) {
				if (line.numTokens() == 4) {
					// Case  FFFFFFCB  to  00000006
					Command cmd = Command.CASE_RANGE;
					String operator = line.getString(2);

					if (!operator.equals(cmd.operator))
						throw new ScriptParsingException(line, "Unknown case operator: " + operator);

					addOpcodeLength(newLineStrings, cmd.opString, 2);
					addArgs(newLineStrings, line.getString(1), line.getString(3));
				}
				else if (line.numTokens() == 3) {
					// Case  <  FFFFFFCB
					String operator = line.getString(1);
					String value = line.getString(2);

					Command cmd = Command.getCase(operator);
					if (cmd == null)
						throw new ScriptParsingException(line, "Unknown case operator: " + operator);

					addOpcodeLength(newLineStrings, cmd.opString, 1);
					addArgs(newLineStrings, value);
				}
				else
					throw new ScriptParsingException(line, "Invalid case command: %n%s", line.trimmedInput());
			}
			else if (opcode.equals(Command.MCASE_OR.name)) {
				// CaseOR  ==  00000003
				Command cmd = Command.MCASE_OR;
				checkLength(line, 2);

				String operator = line.getString(1);
				if (!operator.equals("=="))
					throw new ScriptParsingException(line, "Unknown CaseOR operator: " + operator);

				addOpcodeLength(newLineStrings, cmd.opString, 1);
				addArgs(newLineStrings, line.getString(2));
			}
			else if (opcode.equals(Command.MCASE_AND.name)) {
				Command cmd = Command.MCASE_AND;
				checkLength(line, 2);

				String operator = line.getString(1);
				if (!operator.equals("=="))
					throw new ScriptParsingException(line, "Unknown CaseAND operator: " + operator);

				addOpcodeLength(newLineStrings, cmd.opString, 1);
				addArgs(newLineStrings, line.getString(2));
			}
			else if (opcode.equals(Command.CALL.name)) {
				Command cmd = Command.CALL;
				if (line.numTokens() < 2)
					throw new ScriptParsingException(line, "Incomplete Call command.");

				addOpcodeLength(newLineStrings, cmd.opString, line.numTokens() - 1);
				String functionName = line.getString(1);

				// replace function name
				LibEntry e = library.get(functionName);
				if (e != null) {
					if (e.type != EntryType.api)
						throw new ScriptParsingException(line, "Call to %s(...) is registered as %s in library.", functionName, e.type);

					newLineStrings.add(String.format("%08X", e.address));

					int nargs = line.numTokens() - 2;
					if (e.getMatchingParams(nargs) == null)
						throw new ScriptParsingException(line, "Call to %s(...) has incorrect number of arguments: %d", functionName, nargs);
				}
				else
					newLineStrings.add(functionName);

				// copy args
				for (int i = 2; i < line.numTokens(); i++)
					newLineStrings.add(line.getString(i));
			}
			else if (opcode.equals(Command.LOOP.name)) {
				Command cmd = Command.LOOP;
				if (line.numTokens() == 2) {
					addOpcodeLength(newLineStrings, cmd.opString, 1);
					addArgs(newLineStrings, line.getString(1));
				}
				else if (line.numTokens() == 1) // support for friendly infinite loops
				{
					addOpcodeLength(newLineStrings, cmd.opString, 1);
					addArgs(newLineStrings, "00000000");
				}
				else
					checkLength(line, -1);
			}
			else if (opcode.equals(Command.LABEL.name)) {
				Command cmd = Command.LABEL;
				checkLength(line, cmd.argc);
				String labelName = line.getString(1);

				if (!DataUtils.isInteger(labelName)) {
					if (!labelNameMap.containsKey(labelName))
						labelNameMap.put(labelName, String.format("%X", nextLabelIndex++));
					if (labelNameMap.size() > 15)
						throw new ScriptParsingException(line,
							"Ran out of room for label: %s %nScripts cannot have more than 16 labels.", labelName);

					addOpcodeLength(newLineStrings, cmd.opString, 1);
					addArgs(newLineStrings, labelNameMap.get(labelName));
				}
				else {
					addOpcodeLength(newLineStrings, cmd.opString, 1);
					addArgs(newLineStrings, labelName);
				}
			}
			else if (opcode.equals(Command.GOTO.name)) {
				Command cmd = Command.GOTO;
				checkLength(line, cmd.argc);
				String labelName = line.getString(1);

				if (!DataUtils.isInteger(labelName)) {
					if (!labelNameMap.containsKey(labelName))
						labelNameMap.put(labelName, String.format("%X", nextLabelIndex++));
					if (labelNameMap.size() > 15)
						throw new ScriptParsingException(line,
							"Ran out of room for label: %s %nScripts cannot have more than 16 labels.", labelName);

					addOpcodeLength(newLineStrings, cmd.opString, 1);
					addArgs(newLineStrings, labelNameMap.get(labelName));
				}
				else {
					addOpcodeLength(newLineStrings, cmd.opString, 1);
					addArgs(newLineStrings, labelName);
				}
			}
			else if (opcode.equals(EXEC)) {
				if (line.numTokens() < 2)
					throw new ScriptParsingException(line, "Incomplete Exec command.");

				String scriptName = line.getString(1);

				LibEntry e = library.get(scriptName);
				if (e != null) {
					if (e.type != EntryType.script)
						throw new ScriptParsingException(line, "Exec for %s is registered as %s in library.", scriptName, e.type);

					scriptName = String.format("%08X", e.address);
				}

				if (line.numTokens() == 3) {
					addOpcodeLength(newLineStrings, Command.EXEC2.opString, 2);
					addArgs(newLineStrings, scriptName, line.getString(2));
				}
				else if (line.numTokens() == 2) {
					addOpcodeLength(newLineStrings, Command.EXEC1.opString, 1);
					addArgs(newLineStrings, scriptName);
				}
				else
					checkLength(line, -1);
			}
			else if (opcode.equals(Command.EXEC_WAIT.name)) {
				Command cmd = Command.EXEC_WAIT;
				checkLength(line, cmd.argc);

				String scriptName = line.getString(1);

				LibEntry e = library.get(scriptName);
				if (e != null) {
					if (e.type != EntryType.script)
						throw new ScriptParsingException(line, "Exec for %s is registered as %s in library.", scriptName, e.type);

					scriptName = String.format("%08X", e.address);
				}

				addOpcodeLength(newLineStrings, cmd.opString, 1);
				addArgs(newLineStrings, scriptName);
			}
			else if (opcode.equals(Command.JUMP.name)) {
				Command cmd = Command.JUMP;
				checkLength(line, cmd.argc);

				String scriptName = line.getString(1);

				LibEntry e = library.get(scriptName);
				if (e != null) {
					if (e.type != EntryType.script)
						throw new ScriptParsingException(line, "Exec for %s is registered as %s in library.", scriptName, e.type);

					scriptName = String.format("%08X", e.address);
				}

				addOpcodeLength(newLineStrings, Command.JUMP.opString, 1);
				addArgs(newLineStrings, scriptName);
			}
			else if (InlineCompiler.matchesPattern(line)) {
				List<Line> newLines = InlineCompiler.convert(db, line);
				iter.remove(); //TODO added

				for (Line newline : newLines) {
					replacedVarNames = new ArrayList<>(newline.numTokens());
					for (int i = 0; i < newline.numTokens(); i++) {
						String s = newline.getString(i);
						if (varNameMap.containsKey(s))
							replacedVarNames.add(varNameMap.get(s));
						else
							replacedVarNames.add(s);
					}
					newline.replace(replacedVarNames);
					newline.tokenize();

					iter.add(newline);
				}
				continue;
			}
			else {
				Command cmd = Command.get(opcode);

				if (cmd == null)
					throw new InputFileException(line, "Unknown script command: %s", opcode);

				checkLength(line, cmd.argc);

				String cmdName = (cmd == null) ? opcode : cmd.opString;
				addOpcodeLength(newLineStrings, cmdName, line.numTokens() - 1);

				for (int i = 1; i < line.numTokens(); i++)
					newLineStrings.add(line.getString(i));
			}

			String[] newStringsArray = new String[newLineStrings.size()];
			newLineStrings.toArray(newStringsArray);

			line.replace(newStringsArray);
			//TODO	iter.add(line); no longer needed?
		}
	}

	public static void packOpcodeLength(List<Line> lines, TreeMap<Integer, Integer> offsetMorphMap)
	{
		int oldPos = 0;
		int newPos = 0;

		for (Line line : lines) {
			offsetMorphMap.put(oldPos, newPos); // line start
			offsetMorphMap.put(oldPos + 8, newPos + 4); // args start

			int originalLength = line.numTokens();

			int cmdOpcode = line.getHex(0);
			int cmdLength = line.getHex(1);

			String[] newLine = new String[originalLength - 1];

			newLine[0] = String.format("%02X0000%02X", cmdLength + 1, cmdOpcode);

			for (int i = 2; i < originalLength; i++)
				newLine[i - 1] = line.getString(i);

			line.replace(newLine);

			oldPos += 4 * originalLength;
			newPos += 4 * newLine.length;
		}
	}

	/*
	public static int[] getLine(ByteBuffer fileBuffer, int offset)
	{
		fileBuffer.position(offset);
		int opcode = fileBuffer.getInt();
		int nargs = fileBuffer.getInt();

		int[] line = new int[nargs + 2];
		line[0] = opcode;
		line[1] = nargs;

		for(int i = 2; i < line.length; i++)
			line[i] = fileBuffer.getInt();

		return line;
	}
	 */

	public static boolean isScript(PointerHeuristic h, ByteBuffer fileBuffer)
	{
		int length = h.getLength();
		int lastop = -1;

		if (length < 12)
			return false;

		for (int pos = h.start; pos < h.end - 12; pos += 4) {
			fileBuffer.position(pos);
			int op = fileBuffer.getInt();

			while (op > 0 && op < 0x5F) {
				Command cmd = Command.get(op);
				if (cmd == null)
					return false;

				// check arg count
				int argc = fileBuffer.getInt();

				if (cmd.argc != -1) {
					if (cmd.argc != argc)
						return false;
				}
				else {
					if (argc < 0 || argc > 16)
						return false;
				}

				// check for script end
				if (op == 1 && lastop == 2) {
					h.structOffset = pos - h.start;
					return true;
				}

				fileBuffer.position(fileBuffer.position() + 4 * argc);

				if (fileBuffer.position() > h.end)
					return false;

				lastop = op;
				op = fileBuffer.getInt();
			}
		}

		return false;
	}

	public static void checkSyntax(BaseDataEncoder encoder, ByteBuffer bb, Struct str, boolean usingPackedOpcodes) throws InvalidInputException
	{
		boolean hasEnd = false;
		Stack<Integer> stackIf = new Stack<>();
		int numSwitch = 0;
		int numLoop = 0;
		int numThreadA = 0;
		int numThreadB = 0;

		int pos = str.finalFileOffset;
		int end = pos + str.finalSize;

		readscript:
		while (pos < end) {
			bb.position(pos);

			int opcode = bb.getInt();
			int nargs;

			if (opcode == 0) {
				pos += 4;
				continue;
			}

			if (usingPackedOpcodes && (opcode & 0xFF000000) != 0) {
				nargs = (opcode >>> 24) - 1;
				opcode &= 0xFF;
				pos += 4 * (nargs + 1);
			}
			else {
				nargs = bb.getInt();
				pos += 4 * (nargs + 2);
			}

			Command cmd = Command.get(opcode);

			if (cmd == Command.END)
				hasEnd = true;

			switch (cmd) {
				case END:
					hasEnd = true;
					break readscript;

				case IF_EQ:
				case IF_NEQ:
				case IF_LT:
				case IF_GT:
				case IF_LTEQ:
				case IF_GTEQ:
				case IF_AND:
				case IF_NAND:
					stackIf.push(1);
					break;
				case ELSE:
					if (stackIf.isEmpty() || stackIf.peek() == 2)
						throw new InvalidInputException("Else command with no matching If in script " + str.name);
					stackIf.push(2);
					break;
				case ENDIF:
					if (stackIf.isEmpty())
						throw new InvalidInputException("EndIf command with no matching If in script " + str.name);
					if (stackIf.peek() == 2)
						stackIf.pop();
					if (stackIf.isEmpty())
						throw new InvalidInputException("EndIf command with no matching If in script " + str.name);
					stackIf.pop();
					break;

				case SWITCH_VAR:
				case SWITCH_CONST:
					numSwitch++;
					break;

				case END_SWITCH:
					numSwitch--;
					if (numSwitch < 0)
						throw new InvalidInputException("EndSwitch command with no matching Switch in script " + str.name);
					break;

				case LOOP:
					numLoop++;
					break;

				case END_LOOP:
					numLoop--;
					if (numLoop < 0)
						throw new InvalidInputException("Loop command with no matching Loop in script " + str.name);
					break;

				case THREAD1:
					numThreadA++;
					break;

				case END_THREAD1:
					numThreadA--;
					if (numThreadA < 0)
						throw new InvalidInputException(
							Command.END_THREAD1.name + " command with no matching " + Command.THREAD1.name + " in script " + str.name);
					break;

				case THREAD2:
					numThreadB++;
					break;

				case END_THREAD2:
					numThreadB--;
					if (numThreadB < 0)
						throw new InvalidInputException(
							Command.END_THREAD2.name + " command with no matching " + Command.THREAD2.name + " in script " + str.name);
					break;

				default:
			}
		}

		if (!hasEnd)
			throw new InvalidInputException("Missing End command for script " + str.name);

		if (!stackIf.isEmpty())
			throw new InvalidInputException("If command is missing an EndIf in script " + str.name);

		if (numSwitch > 0)
			throw new InvalidInputException("Switch command is missing an EndSwitch in script " + str.name);

		if (numLoop > 0)
			throw new InvalidInputException("Loop command is missing an EndLoop in script " + str.name);

		if (numThreadA > 0)
			throw new InvalidInputException(Command.THREAD1.name + " command is missing an " + Command.END_THREAD1.name + " in script " + str.name);

		if (numThreadB > 0)
			throw new InvalidInputException(Command.THREAD2.name + " command is missing an " + Command.END_THREAD2.name + " in script " + str.name);
	}

	private static final int[][] TEST_SCRIPT = {
			/*
			{ 0x00000043, 0x00000003, 0x8026F0EC, 0xFFFFFF81, 0x00000000 },
			{ 0x00000043, 0x00000003, 0x80278B4C, 0xFFFFFF81, 0x00000000 },
			{ 0x00000043, 0x00000003, 0x8027C548, 0xFFFFFF81, 0x00000000 },
			{ 0x00000043, 0x00000002, 0x8024E61C, 0x0000003F },
			{ 0x00000043, 0x00000002, 0x8024EB24, 0xFFFFFF81 },
			{ 0x00000043, 0x00000004, 0x8024ECF8, 0xFFFFFFFF, 0x00000001, 0x00000000 },
			{ 0x00000043, 0x00000004, 0x8026B1B0, 0xFFFFFF81, 0x00000001, 0x00260003 },
			{ 0x00000043, 0x00000002, 0x8026A3A8, 0xFFFFFF81 },
			{ 0x00000043, 0x00000005, 0x8026A748, 0xFFFFFF81, 0x00000032, 0x00000000, 0x00000000 },
			{ 0x00000043, 0x00000003, 0x8026B654, 0xFFFFFF81, 0xF24A9280 },
			{ 0x00000043, 0x00000004, 0x80279E64, 0xFFFFFF81, 0x00000000, 0x00000000 },
			{ 0x00000043, 0x00000004, 0x8026B1B0, 0xFFFFFF81, 0x00000001, 0x00260001 },
			{ 0x00000043, 0x00000005, 0x8026BA04, 0xFFFFFF81, 0x00000000, 0xFFFFFFFF, 0x00000000 },
			{ 0x00000008, 0x00000001, 0x00000001 },
			{ 0x00000043, 0x00000005, 0x8026BA04, 0xFFFFFF81, 0x00000000, 0xFFFFFFFE, 0x00000000 },
			{ 0x00000008, 0x00000001, 0x00000005 },
			{ 0x00000043, 0x00000005, 0x8026BA04, 0xFFFFFF81, 0x00000000, 0x00000000, 0x00000000 },
			{ 0x00000043, 0x00000004, 0x8026B1B0, 0xFFFFFF81, 0x00000001, 0x00260004 },
			 */
			{ 0x00000043, 0x00000007, 0x8027CFB8, 0xFFFFFF81, 0xFE363C80, 0x00000000, 0x00000000, 0x00000001, 0x00000010 },
			{ 0x00000014, 0x00000001, 0xFE363C80 },
			{ 0x0000001D, 0x00000001, 0x00000006 },
			{ 0x0000001D, 0x00000001, 0x00000005 },
			{ 0x00000024, 0x00000002, 0xFE363C8A, 0xFE363C80 },
			{ 0x00000043, 0x00000002, 0x8026A3A8, 0xFFFFFF81 },
			{ 0x00000043, 0x00000005, 0x8026A820, 0xFFFFFF81, 0xFE363C80, 0xFE363C81, 0xFE363C82 },
			{ 0x00000028, 0x00000002, 0xFE363C80, 0x0000000A },
			{ 0x00000024, 0x00000002, 0xFE363C81, 0x0000000A },
			{ 0x00000027, 0x00000002, 0xFE363C82, 0x00000003 },
			{ 0x00000043, 0x00000005, 0x8026A510, 0xFFFFFF81, 0xFE363C80, 0xFE363C81, 0xFE363C82 },
			{ 0x00000043, 0x00000003, 0x8026B55C, 0xFFFFFF81, 0xF24A7F4D },
			{ 0x00000056, 0x00000000 },
			{ 0x00000043, 0x00000005, 0x8026AAA8, 0xFFFFFF81, 0xFE363C81, 0xFE363C82, 0xFE363C80 },
			{ 0x00000024, 0x00000002, 0xFE363C80, 0x00000000 },
			{ 0x00000005, 0x00000001, 0x00000010 },

			{ 0x00000043, 0x00000005, 0x8026AAA8, 0xFFFFFF81, 0xFE363C84, 0xFE363C85, 0xFE363C86 },
			{ 0x00000043, 0x00000006, 0x8021818C, 0xFE363C81, 0xFE363C82, 0xFE363C84, 0xFE363C85, 0xFE363C80 },
			{ 0x00000043, 0x00000005, 0x8026C3AC, 0xFFFFFF81, 0x00000000, 0x00000000, 0xFE363C80 },

			{ 0x00000024, 0x00000002, 0xFE363C81, 0xFE363C84 },
			{ 0x00000024, 0x00000002, 0xFE363C82, 0xFE363C85 },
			{ 0x00000024, 0x00000002, 0xFE363C83, 0xFE363C86 },
			{ 0x00000008, 0x00000001, 0x00000001 },
			{ 0x00000006, 0x00000000 },
			/*
			{ 0x00000057, 0x00000000 },
			{ 0x00000056, 0x00000000 },
			{ 0x00000008, 0x00000001, 0x00000006 },
			{ 0x00000043, 0x00000004, 0x8026B1B0, 0xFFFFFF81, 0x00000001, 0x00260004 },
			{ 0x00000057, 0x00000000 },
			{ 0x00000043, 0x00000006, 0x80278D08, 0xFFFFFF81, 0x00000010, 0x00000000, 0x00000001, 0x00000000 },
			{ 0x00000043, 0x00000004, 0x8026B1B0, 0xFFFFFF81, 0x00000001, 0x00260008 },
			{ 0x00000043, 0x00000005, 0x8026C904, 0xFFFFFF81, 0xF24A7EE7, 0xF24A7DB4, 0xF24A7E80 },
			{ 0x00000043, 0x00000005, 0x8026BA04, 0xFFFFFF81, 0x00000000, 0x00000005, 0x00000000 },
			{ 0x00000008, 0x00000001, 0x00000001 },
			{ 0x00000043, 0x00000005, 0x8026C904, 0xFFFFFF81, 0xF24A7FB4, 0xF24A7C80, 0xF24A7E80 },
			{ 0x00000043, 0x00000005, 0x8026BA04, 0xFFFFFF81, 0x00000000, 0xFFFFFFFE, 0x00000000 },
			{ 0x00000008, 0x00000001, 0x00000001 },
			{ 0x00000043, 0x00000005, 0x8026C904, 0xFFFFFF81, 0xF24A7E80, 0xF24A7E80, 0xF24A7E80 },
			{ 0x00000043, 0x00000005, 0x8026BA04, 0xFFFFFF81, 0x00000000, 0x00000007, 0x00000000 },
			{ 0x00000043, 0x00000004, 0x8026B1B0, 0xFFFFFF81, 0x00000001, 0x00260005 },
			{ 0x00000008, 0x00000001, 0x00000005 },
			 */
			{ 0x0000000A, 0x00000002, 0xFE363C8A, 0x00000005 },
			/*
			{ 0x00000043, 0x00000007, 0x8027CFB8, 0xFFFFFF81, 0xFE363C80, 0x80000000, 0x00000000, 0x00000000, 0x00000000 },
			{ 0x00000013, 0x00000000 },
			{ 0x00000008, 0x00000001, 0x00000005 },
			{ 0x00000043, 0x00000005, 0x8026BA04, 0xFFFFFF81, 0x00000000, 0x00000000, 0x00000000 },
			{ 0x00000043, 0x00000004, 0x8026B1B0, 0xFFFFFF81, 0x00000001, 0x00260004 },
			{ 0x00000043, 0x00000002, 0x8026A3A8, 0xFFFFFF81 },
			{ 0x00000043, 0x00000005, 0x8026A820, 0xFFFFFF81, 0xFE363C80, 0xFE363C81, 0xFE363C82 },
			{ 0x00000027, 0x00000002, 0xFE363C80, 0x00000014 },
			{ 0x00000024, 0x00000002, 0xFE363C81, 0x00000000 },
			{ 0x00000043, 0x00000005, 0x8026A510, 0xFFFFFF81, 0xFE363C80, 0xFE363C81, 0xFE363C82 },
			{ 0x00000043, 0x00000003, 0x8026B55C, 0xFFFFFF81, 0xF24A8280 },
			{ 0x00000056, 0x00000000 },
			{ 0x00000008, 0x00000001, 0x00000004 },
			{ 0x00000024, 0x00000002, 0xFE363C80, 0x000000B4 },
			{ 0x00000005, 0x00000001, 0x00000004 },
			{ 0x00000028, 0x00000002, 0xFE363C80, 0x0000002D },
			{ 0x00000043, 0x00000005, 0x8026C3AC, 0xFFFFFF81, 0x00000000, 0x00000000, 0xFE363C80 },
			{ 0x00000008, 0x00000001, 0x00000001 },
			{ 0x00000006, 0x00000000 },
			{ 0x00000043, 0x00000004, 0x8026B1B0, 0xFFFFFF81, 0x00000001, 0x00260004 },
			{ 0x00000057, 0x00000000 },
			{ 0x00000043, 0x00000006, 0x80278D08, 0xFFFFFF81, 0x0000000F, 0x00000000, 0x00000001, 0x00000000 },
			{ 0x00000043, 0x00000004, 0x8026B1B0, 0xFFFFFF81, 0x00000001, 0x00260007 },
			{ 0x00000008, 0x00000001, 0x00000005 },
			{ 0x00000043, 0x00000002, 0x8024E61C, 0x00000002 },
			{ 0x00000043, 0x00000001, 0x8027D7F0 },
			{ 0x00000043, 0x00000003, 0x8026B358, 0xFFFFFF81, 0x000000B4 },
			{ 0x00000043, 0x00000005, 0x8026EE88, 0xFFFFFF81, 0x00000001, 0x00000000, 0x00000002 },
			{ 0x00000043, 0x00000004, 0x8026B2D0, 0xFFFFFF81, 0x00000001, 0xF24A8280 },
			{ 0x00000043, 0x00000002, 0x80269EC4, 0xFFFFFF81 },
			{ 0x00000043, 0x00000003, 0x8026B654, 0xFFFFFF81, 0xF24A9A80 },
			{ 0x00000043, 0x00000004, 0x80279E64, 0xFFFFFF81, 0x00000000, 0x00000000 },
			{ 0x00000043, 0x00000004, 0x8026B2D0, 0xFFFFFF81, 0x00000001, 0xF24A7E80 },
			{ 0x00000043, 0x00000003, 0x8026B358, 0xFFFFFF81, 0x00000000 },
			{ 0x00000008, 0x00000001, 0x00000005 },
			{ 0x00000043, 0x00000004, 0x8026B1B0, 0xFFFFFF81, 0x00000001, 0x00260001 },
			{ 0x00000043, 0x00000003, 0x8026B55C, 0xFFFFFF81, 0xF24A80E7 },
			{ 0x00000043, 0x00000006, 0x80278D08, 0xFFFFFF81, 0x00000005, 0x00000000, 0x00000001, 0x00000000 },
			{ 0x00000043, 0x00000004, 0x8026EF4C, 0xFFFFFF81, 0x00000001, 0x00000000 },
			{ 0x00000043, 0x00000003, 0x80278B4C, 0xFFFFFF81, 0x00000001 },
			{ 0x00000043, 0x00000003, 0x8026F0EC, 0xFFFFFF81, 0x00000001 },
			{ 0x00000002, 0x00000000 },
			{ 0x00000020, 0x00000000 },
			{ 0x0000001C, 0x00000000 },
			{ 0x00000043, 0x00000002, 0x8026A3A8, 0xFFFFFF81 },
			{ 0x00000043, 0x00000003, 0x8026B55C, 0xFFFFFF81, 0xF24A7F4D },
			{ 0x00000056, 0x00000000 },
			{ 0x00000043, 0x00000005, 0x8026AAA8, 0xFFFFFF81, 0xFE363C81, 0xFE363C82, 0xFE363C80 },
			{ 0x00000024, 0x00000002, 0xFE363C80, 0x00000000 },
			{ 0x00000005, 0x00000001, 0x00000010 },
			{ 0x00000043, 0x00000005, 0x8026AAA8, 0xFFFFFF81, 0xFE363C84, 0xFE363C85, 0xFE363C86 },
			{ 0x00000043, 0x00000006, 0x8021818C, 0xFE363C81, 0xFE363C82, 0xFE363C84, 0xFE363C85, 0xFE363C80 },
			{ 0x00000043, 0x00000005, 0x8026C3AC, 0xFFFFFF81, 0x00000000, 0x00000000, 0xFE363C80 },
			{ 0x00000024, 0x00000002, 0xFE363C81, 0xFE363C84 },
			{ 0x00000024, 0x00000002, 0xFE363C82, 0xFE363C85 },
			{ 0x00000024, 0x00000002, 0xFE363C83, 0xFE363C86 },
			{ 0x00000008, 0x00000001, 0x00000001 },
			{ 0x00000006, 0x00000000 },
			{ 0x00000057, 0x00000000 },
			{ 0x00000056, 0x00000000 },
			{ 0x00000008, 0x00000001, 0x00000006 },
			{ 0x00000043, 0x00000004, 0x8026B1B0, 0xFFFFFF81, 0x00000001, 0x00260004 },
			{ 0x00000057, 0x00000000 },
			{ 0x00000043, 0x00000006, 0x80278D08, 0xFFFFFF81, 0x00000010, 0x00000000, 0x00000001, 0x00000000 },
			{ 0x00000043, 0x00000004, 0x8026B1B0, 0xFFFFFF81, 0x00000001, 0x0026000B },
			{ 0x00000043, 0x00000005, 0x8026C904, 0xFFFFFF81, 0xF24A7EE7, 0xF24A7DB4, 0xF24A7E80 },
			{ 0x00000008, 0x00000001, 0x00000001 },
			{ 0x00000043, 0x00000005, 0x8026C904, 0xFFFFFF81, 0xF24A7FB4, 0xF24A7C80, 0xF24A7E80 },
			{ 0x00000008, 0x00000001, 0x00000001 },
			{ 0x00000023, 0x00000000 },
			{ 0x00000043, 0x00000008, 0x8027CCB4, 0xFFFFFF81, 0xFE363C80, 0x00000000, 0x00000000, 0x00000000, 0x00000001, 0x00000020 },
			{ 0x00000014, 0x00000001, 0xFE363C80 },
			{ 0x0000001D, 0x00000001, 0x00000000 },
			{ 0x0000001D, 0x00000001, 0x00000002 },
			{ 0x00000043, 0x00000002, 0x8024E61C, 0x00000002 },
			{ 0x00000043, 0x00000005, 0x8026C904, 0xFFFFFF81, 0xF24A7EE7, 0xF24A7DB4, 0xF24A7E80 },
			{ 0x00000008, 0x00000001, 0x00000001 },
			{ 0x00000043, 0x00000005, 0x8026C904, 0xFFFFFF81, 0xF24A7E80, 0xF24A7E80, 0xF24A7E80 },
			{ 0x00000008, 0x00000001, 0x00000001 },
			{ 0x00000043, 0x00000005, 0x8026C3AC, 0xFFFFFF81, 0x00000000, 0x00000000, 0x00000000 },
			{ 0x00000043, 0x00000005, 0x8026BA04, 0xFFFFFF81, 0x00000000, 0x00000000, 0x00000000 },
			{ 0x00000043, 0x00000004, 0x8026B1B0, 0xFFFFFF81, 0x00000001, 0x00260001 },
			{ 0x00000043, 0x00000005, 0x8026A820, 0xFFFFFF81, 0xFE363C80, 0xFE363C81, 0xFE363C82 },
			{ 0x00000027, 0x00000002, 0xFE363C80, 0x00000028 },
			{ 0x00000024, 0x00000002, 0xFE363C81, 0x00000000 },
			{ 0x00000043, 0x00000003, 0x8026B55C, 0xFFFFFF81, 0xF24A81B4 },
			{ 0x00000043, 0x00000005, 0x8026A510, 0xFFFFFF81, 0xFE363C80, 0xFE363C81, 0xFE363C82 },
			{ 0x00000043, 0x00000006, 0x80278D08, 0xFFFFFF81, 0x0000000A, 0x00000000, 0x00000001, 0x00000000 },
			{ 0x00000027, 0x00000002, 0xFE363C80, 0x0000001E },
			{ 0x00000043, 0x00000005, 0x8026A510, 0xFFFFFF81, 0xFE363C80, 0xFE363C81, 0xFE363C82 },
			{ 0x00000043, 0x00000006, 0x80278D08, 0xFFFFFF81, 0x00000008, 0x00000000, 0x00000001, 0x00000000 },
			{ 0x00000027, 0x00000002, 0xFE363C80, 0x00000014 },
			{ 0x00000043, 0x00000005, 0x8026A510, 0xFFFFFF81, 0xFE363C80, 0xFE363C81, 0xFE363C82 },
			{ 0x00000043, 0x00000006, 0x80278D08, 0xFFFFFF81, 0x00000006, 0x00000000, 0x00000001, 0x00000000 },
			{ 0x00000043, 0x00000004, 0x8026B1B0, 0xFFFFFF81, 0x00000001, 0x00260001 },
			{ 0x00000008, 0x00000001, 0x00000003 },
			{ 0x00000043, 0x00000001, 0x8027D7F0 },
			{ 0x00000043, 0x00000004, 0x8026B2D0, 0xFFFFFF81, 0x00000001, 0xF24A8280 },
			{ 0x00000043, 0x00000004, 0x8026B1B0, 0xFFFFFF81, 0x00000001, 0x00260003 },
			{ 0x00000043, 0x00000002, 0x80269EC4, 0xFFFFFF81 },
			{ 0x00000043, 0x00000003, 0x8026B654, 0xFFFFFF81, 0xF24A9A80 },
			{ 0x00000043, 0x00000004, 0x80279E64, 0xFFFFFF81, 0x00000000, 0x00000000 },
			{ 0x00000043, 0x00000004, 0x8026B2D0, 0xFFFFFF81, 0x00000001, 0xF24A7E80 },
			{ 0x00000020, 0x00000000 },
			{ 0x00000023, 0x00000000 },
			{ 0x00000043, 0x00000003, 0x80278B4C, 0xFFFFFF81, 0x00000001 },
			{ 0x00000043, 0x00000003, 0x8026F0EC, 0xFFFFFF81, 0x00000001 },
			{ 0x00000002, 0x00000000 },
			{ 0x00000001, 0x00000000 }
			 */
	};

	public static void main(String[] args)
	{
		Environment.initialize();

		ArrayList<ScriptLine> script = new ArrayList<>(TEST_SCRIPT.length);

		for (int[] ii : TEST_SCRIPT) {
			Command cmd = Command.get(ii[0]);
			int arg[] = new int[ii[1]];
			for (int i = 0; i < arg.length; i++)
				arg[i] = ii[i + 2];

			script.add(new ScriptLine(cmd, arg, script.size(), 0, 0));
		}

		//XXX	doTypeAnalysis(DataConstants.battleLibrary, script, true);

		for (ScriptLine line : script) {
			System.out.printf("%08X %08X ", line.cmd.opcode, line.args.length);
			for (int arg : line.args)
				System.out.printf("%08X ", arg);
			System.out.println();

			for (LibType type : line.types)
				System.out.printf(type.toString() + " ");
			System.out.println();
		}

		Environment.exit();
	}
}
