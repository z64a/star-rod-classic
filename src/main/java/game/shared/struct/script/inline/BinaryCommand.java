package game.shared.struct.script.inline;

import game.shared.struct.script.Script.Command;

public class BinaryCommand
{
	public final boolean isFloat;
	public final CommandType type;
	public Operand dst;
	public Operand src;

	public boolean isConst = false;
	public boolean typeConversion = false;
	public boolean reordered = false;
	public boolean invalid = false;

	public boolean noContraction = false;

	public BinaryCommand(boolean isFloat, CommandType type, int reg1, int reg2)
	{
		this.isFloat = isFloat;
		this.type = type;
		this.dst = new Operand(reg1);
		this.src = new Operand(reg2);
		isConst = (type == CommandType.SET_CONST);
	}

	public BinaryCommand(boolean isFloat, CommandType type, int reg1, String name2)
	{
		this.isFloat = isFloat;
		this.type = type;
		this.dst = new Operand(reg1);
		this.src = new Operand(name2);
		isConst = (type == CommandType.SET_CONST);
	}

	public BinaryCommand(boolean isFloat, CommandType type, String name1, int reg2)
	{
		this.isFloat = isFloat;
		this.type = type;
		this.dst = new Operand(name1);
		this.src = new Operand(reg2);
		isConst = (type == CommandType.SET_CONST);
	}

	public String toBytecodeLine()
	{
		return String.format("%08X %08X %s %s", type.cmd.opcode, type.cmd.argc, dst.toText(), src.toText());
	}

	@Override
	public String toString()
	{
		StringBuilder sb = new StringBuilder("(");
		if (isConst)
			sb.append('c');
		if (typeConversion)
			sb.append('>');
		sb.append(isFloat ? 'f' : 'i');
		return String.format("%s) %-5s %s %s", invalid ? "x" : sb.toString(), type, dst, src);
	}

	public static enum CommandType
	{
		SET (Command.SET_INT),
		SETF (Command.SET_FLT),
		AND (Command.AND),
		OR (Command.OR),
		ADD (Command.ADD_INT),
		ADDF (Command.ADD_FLT),
		SUB (Command.SUB_INT),
		SUBF (Command.SUB_FLT),
		MUL (Command.MUL_INT),
		MULF (Command.MUL_FLT),
		DIV (Command.DIV_INT),
		DIVF (Command.DIV_FLT),
		MOD (Command.MOD_INT),
		//	AND_CONST	(Command.AND_CONST),
		//	OR_CONST	(Command.OR_CONST),
		SET_CONST (Command.SET_CONST);

		public final Command cmd;

		private CommandType(Command cmd)
		{
			this.cmd = cmd;
		}
	}

	public static class Operand
	{
		public final boolean isReg;
		public final int regID;
		public final String name;

		public Operand(Operand other)
		{
			this.isReg = other.isReg;
			this.regID = other.regID;
			this.name = other.name;
		}

		public Operand(String name)
		{
			this.name = name;
			this.regID = -999;
			isReg = false;
		}

		public Operand(int id)
		{
			this.name = "ERROR";
			this.regID = id;
			isReg = true;
		}

		public String toText()
		{
			return isReg ? String.format("*Temp[%X]", regID) : name;
		}

		@Override
		public String toString()
		{
			return isReg ? String.format("[%X]", regID) : name;
		}
	}
}
