package game.shared.struct.script.inline;

public enum BinaryOperator
{
	// @formatter:off
	ADD	("+"),
	SUB ("-"),
	MUL ("*"),
	DIV ("/"),
	AND ("&"),
	OR  ("|"),
	MOD ("%");
	// @formatter:on

	private final String opString;

	private BinaryOperator(String opString)
	{
		this.opString = opString;
	}

	@Override
	public String toString()
	{
		return opString;
	}

	public static BinaryOperator getOperator(String s)
	{
		for (BinaryOperator op : BinaryOperator.values())
			if (op.opString.equalsIgnoreCase(s))
				return op;

		throw new IllegalStateException("Unknown binary operator: " + s);
	}
}
