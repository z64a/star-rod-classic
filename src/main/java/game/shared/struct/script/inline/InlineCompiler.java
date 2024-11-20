package game.shared.struct.script.inline;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import app.input.DummySource;
import app.input.InputFileException;
import app.input.Line;
import game.shared.DataUtils;
import game.shared.struct.script.Script.Command;
import game.shared.struct.script.inline.BinaryCommand.CommandType;
import game.shared.struct.script.inline.BinaryCommand.Operand;
import game.shared.struct.script.inline.generated.ASTAssign;
import game.shared.struct.script.inline.generated.InlineParser;
import game.shared.struct.script.inline.generated.ParseException;
import game.shared.struct.script.inline.visitors.BaseVisitor;
import game.shared.struct.script.inline.visitors.ChainExpansionVisitor;
import game.shared.struct.script.inline.visitors.CodeGenVisitor;
import game.shared.struct.script.inline.visitors.ConstReductionVisitor;
import game.shared.struct.script.inline.visitors.PrintVisitor;
import game.shared.struct.script.inline.visitors.SimplifyMathVisitor;
import game.shared.struct.script.inline.visitors.TypeVisitor;
import game.shared.struct.script.inline.visitors.ValueVisitor;
import game.shared.struct.script.inline.visitors.VisitorException;

public class InlineCompiler
{
	public static void main(String[] args) throws IOException
	{
		//	RunContext.initialize();

		//	test("Set  *Var[5] = .GateRaiseTime * 20 + int(150.0 / 16.0) * 2 + (.Item:SuperShroom & FF)");
		//	test("SetF *Debug[2] = ~square((0 + 100.0) / ~float(20))");
		//	test("Set *A = 1 + *B + 1 + *C + 1 + *D");
		//	test("SetF *Dist2 = @square(*PosX - *StartX) + @square(*PosY - *StartY) + @square(*PosZ - *StartZ)");
		test("SetF	*Debug[3] = @square(*PosX) + @square(*PosZ)+ @square(*Debug[2])");
		test("Set  *Var[5] = .GateRaiseTime * 20 + @int(150.0 / 16.0) * (-2) + (.Item:SuperShroom & FF)");
		//	test("SetF *Debug[2] = ~square(~float(*PosX)) + ~square(*PosZ)+ ~square(*Debug[2])");
		//	test("SetF *Debug[2] = ~square(*PosX) + ~square(*PosZ)+ ~square(*Debug[2])");
		//	test("SetF *Debug[2] = ~float(1.0 + *X)");
		//	test("Set *Debug[2] = ~int(1.0 + (~float(~int(~int(~float(~int(100.0))))) + 20.0) * ~float(~float(*X/10)))");

		//	test("Set *Blah = (11F&5)**Ok[8] + *Var0 - ~int(6.6e2) - 6/*C - .Const:Blah[.other[999`]+5] | $Pointer[70`] - ~int(6.0)");
		//	test("Set *Blah = 1 + $Ptr + 100");
		//	test("Set  *Var[F] = -6-8-(6 * 10`)");
		//	test("Set  *Test = 1 - 6 * 3 + *Var8 + .MyConst[50` * ~int(5.0 + 3.3 * 1.2)]");
		//	test("-mod(5,2)");
		//	test("-5 * mod(6 * 10, 2)");

		//	RunContext.exit();
	}

	private static void test(String s)
	{
		DummySource src = new DummySource("InlineExpressionTest");
		Line line = new Line(src, 1, s);
		line.tokenize();

		convert(new DummyDatabase(), line, true);
	}

	public static boolean DEBUG_OPTIMIZE = true;

	public static boolean matchesPattern(Line line)
	{
		String opName = line.getString(0);
		return (opName.equals(Command.SET_INT.name) || opName.equals(Command.SET_FLT.name))
			&& line.numTokens() > 3
			&& line.getString(2).equals("=");
	}

	public static List<Line> convert(ConstantDatabase db, Line inputLine)
	{
		return convert(db, inputLine, false);
	}

	public static List<Line> convert(ConstantDatabase db, Line inputLine, boolean printOutput)
	{
		if (!matchesPattern(inputLine))
			throw new IllegalStateException("Tried to read an inline script expression with invalid format!");

		if (!DataUtils.isScriptVarFmt(inputLine.getString(1)))
			throw new InlineParsingException(inputLine, "Destination is not a valid variable!");

		// merge inputs
		StringBuilder sb = new StringBuilder();
		for (int i = 3; i < inputLine.numTokens(); i++)
			sb.append(inputLine.tokens[i].str);

		String expr = sb.toString();
		String inOpcode = inputLine.getString(0);
		String outVariable = inputLine.getString(1);
		boolean assignAsFloat = inOpcode.equalsIgnoreCase(Command.SET_FLT.name);

		if (printOutput) {
			System.out.println("INPUT:");
			System.out.println(inputLine.str);
			System.out.println();
		}

		InlineParser parser = new InlineParser(new StringReader(expr));
		try {
			ASTAssign assign = new ASTAssign(assignAsFloat, outVariable, parser.Line());
			BaseVisitor visitor;

			if (printOutput) {
				System.out.println("PARSED:");
				visitor = new PrintVisitor();
				visitor.visit(assign, "");
				System.out.println();
			}

			visitor = new ChainExpansionVisitor();
			visitor.visit(assign, null);

			visitor = new TypeVisitor();
			visitor.visit(assign, db);

			// two passes for additional reduction after tree reorderings
			for (int i = 0; i < 2; i++) {
				visitor = new ValueVisitor();
				visitor.visit(assign, db);

				visitor = new ConstReductionVisitor();
				visitor.visit(assign, null);

				visitor = new SimplifyMathVisitor();
				visitor.visit(assign, null);
			}

			if (printOutput) {
				System.out.println("DECORATED:");
				visitor = new PrintVisitor();
				visitor.visit(assign, "");
				System.out.println();
			}

			CodeGenVisitor codegen = new CodeGenVisitor();
			codegen.visit(assign, null);

			BinaryCommand[] code = codegen.getCommands();
			optimize(code);
			reindex(code);

			if (printOutput)
				System.out.println("GENERATED:");
			List<Line> newLines = new ArrayList<>(code.length);
			for (BinaryCommand cmd : code) {
				if (cmd.invalid)
					continue;

				if (printOutput)
					System.out.println(cmd.toString());

				Line newLine = inputLine.createLine(cmd.toBytecodeLine());
				newLine.tokenize();
				newLines.add(newLine);
			}
			if (printOutput)
				System.out.println();

			return newLines;

		}
		catch (ParseException e) {
			throw new InputFileException(inputLine, e);
		}
		catch (VisitorException e) {
			throw new InputFileException(inputLine, e);
		}
	}

	private static void optimize(BinaryCommand[] code)
	{
		if (DEBUG_OPTIMIZE) {
			System.out.println("UNOPTIMIZED:");
			int k = 0;
			for (BinaryCommand cmd : code)
				if (!cmd.invalid)
					System.out.println(k++ + " " + cmd);
			System.out.println();
		}

		int lastIndex = code.length - 1;

		// stage 0: reorder SET commands to occur immediately before their first reference
		for (int i = 0; i < lastIndex; i++) {
			BinaryCommand cmd = code[i];
			if (cmd.invalid)
				continue;

			if (!cmd.reordered && cmd.dst.isReg && (cmd.type == CommandType.SET ||
				cmd.type == CommandType.SETF ||
				cmd.type == CommandType.SET_CONST)) {
				if (tryReordering(code, i))
					i--;
			}
		}

		/*
		(forward) contract command seqeunces of the form:
			Set [n] *Var
			...
			Op	[m] [n]

		to the simplified form:
			...
			Op  [m] *Var
		 */
		for (int i = 0; i < lastIndex; i++) {
			BinaryCommand cmd = code[i];
			if (cmd.invalid || cmd.noContraction)
				continue;

			// looking for DEST=register, SRC=var (but NOT CONST! which would be assigned via SET_CONST)
			if (!cmd.typeConversion && cmd.dst.isReg && !cmd.src.isReg && (cmd.type == CommandType.SET ||
				cmd.type == CommandType.SETF)) {
				int hits = contractForward(code, i);
				if (hits > 0)
					i--; // contracted, keep looking for others to contract
			}
		}

		if (DEBUG_OPTIMIZE) {
			System.out.println("POST-FORWARD:");
			int k = 0;
			for (BinaryCommand cmd : code)
				if (!cmd.invalid)
					System.out.println(k++ + " " + cmd);
			System.out.println();
		}

		/*
		(backward) contract command seqeunces of the form:
			Op [n] X
			...
			Set	*Var [n]

		to the simplified form:
			Op  *Var X
			...

		this really only applies to the return value, as that is the only
		script variable being set by a valid inline expression
		 */
		for (int i = lastIndex; i > 0; i--) {
			BinaryCommand cmd = code[i];
			if (cmd.invalid)
				continue;

			// looking for DEST=var, SRC=register
			if (!cmd.typeConversion && !cmd.dst.isReg && cmd.src.isReg && (cmd.type == CommandType.SET ||
				cmd.type == CommandType.SETF)) {
				int hits = contractBackward(code, i);
				if (hits > 0)
					i++; // contracted, keep looking for others to contract
			}
		}

		if (DEBUG_OPTIMIZE) {
			System.out.println("POST-BACKWARD:");
			int k = 0;
			for (BinaryCommand cmd : code)
				if (!cmd.invalid)
					System.out.println(k++ + " " + cmd);
			System.out.println();
		}
	}

	private static boolean tryReordering(BinaryCommand[] code, int start)
	{
		BinaryCommand origin = code[start];
		int pivotRegID = origin.dst.regID;
		boolean reordered = false;

		for (int i = start + 1; i < code.length; i++) {
			BinaryCommand cmd = code[i];

			//	System.out.println(start + " ? " + i + " : " + cmd);

			if (cmd.invalid) {
				BinaryCommand temp = code[i - 1];
				code[i - 1] = code[i];
				code[i] = temp;
				origin.reordered = true;
				reordered = true;
				continue;
			}

			boolean matchSRC = cmd.src.isReg && cmd.src.regID == pivotRegID;
			boolean matchDST = cmd.dst.isReg && cmd.dst.regID == pivotRegID;

			if (matchSRC || matchDST)
				return reordered;

			// filter down until register is used
			BinaryCommand temp = code[i - 1];
			code[i - 1] = code[i];
			code[i] = temp;
			origin.reordered = true;
			reordered = true;
		}

		return reordered;
	}

	/*
	if(!cmd.typeConversion && !cmd.dst.isReg && cmd.src.isReg && (
				cmd.type == CommandType.SET ||
				cmd.type == CommandType.SETF))
	 */
	private static int contractBackward(BinaryCommand[] code, int start)
	{
		BinaryCommand origin = code[start];
		int replacements = 0;

		assert (!origin.dst.isReg);
		assert (origin.src.isReg);
		assert (!origin.typeConversion);
		assert (origin.type == CommandType.SET || origin.type == CommandType.SETF);

		for (int i = start - 1; i >= 0; i--) {
			BinaryCommand cmd = code[i];
			if (cmd.invalid)
				continue;

			if (!cmd.src.isReg && cmd.src.name.equals(origin.dst.name))
				break;

			if (cmd.dst.isReg && (cmd.dst.regID == origin.src.regID)) {
				cmd.dst = new Operand(origin.dst);
				replacements++;

				// continue contraction chain through casts, ie: op [X] [X]
				if (cmd.src.isReg && (cmd.src.regID == origin.src.regID))
					cmd.src = new Operand(origin.dst);
			}
		}

		if (replacements > 0 && (origin.type == CommandType.SET || origin.type == CommandType.SETF))
			origin.invalid = true;

		return replacements;
	}

	/*
	if(!cmd.typeConversion && cmd.dst.isReg && !cmd.src.isReg && (
					cmd.type == CommandType.SET ||
					cmd.type == CommandType.SETF))
	 */
	private static int contractForward(BinaryCommand[] code, int start)
	{
		BinaryCommand origin = code[start];
		int replacements = 0;

		assert (origin.dst.isReg);
		assert (!origin.src.isReg);
		assert (!origin.typeConversion);
		assert (origin.type == CommandType.SET || origin.type == CommandType.SETF);

		for (int i = start + 1; i < code.length; i++) {
			BinaryCommand cmd = code[i];
			if (cmd.invalid)
				continue;

			boolean matchSRC = cmd.src.isReg && (cmd.src.regID == origin.dst.regID);
			boolean matchDST = cmd.dst.isReg && (cmd.dst.regID == origin.dst.regID);

			// continue contraction chain through casts, ie: op [X] [X]
			if (matchDST && !matchSRC)
				break;

			// skip situations with SRC=DEST like with SQR
			if (matchSRC && matchDST)
				break;

			if (matchSRC) {
				cmd.src = new Operand(origin.src);
				replacements++;

				//TODO original purpose?
				//	if(matchDST)
				//		cmd.dst = new Operand(origin.src);
			}
		}

		if (replacements > 0)
			origin.invalid = true;

		return replacements;
	}

	private static void reindex(BinaryCommand[] code)
	{
		Map<Integer, Integer> usedRegisters = new HashMap<>();

		for (int i = 0; i < code.length; i++) {
			BinaryCommand cmd = code[i];
			if (cmd.invalid)
				continue;

			if (cmd.dst.isReg) {
				Integer newIndex = usedRegisters.get(cmd.dst.regID);
				if (newIndex == null) {
					newIndex = usedRegisters.size();
					usedRegisters.put(cmd.dst.regID, newIndex);
				}
				cmd.dst = new Operand(newIndex);
			}

			if (cmd.src.isReg) {
				Integer newIndex = usedRegisters.get(cmd.src.regID);
				if (newIndex == null) {
					newIndex = usedRegisters.size();
					usedRegisters.put(cmd.src.regID, newIndex);
				}
				cmd.src = new Operand(newIndex);
			}
		}
	}

	private static class DummyDatabase implements ConstantDatabase
	{
		@Override
		public boolean hasConstant(String s)
		{
			return true;
		}

		@Override
		public Integer getConstantValue(String s)
		{
			return 7;
		}
	}
}
