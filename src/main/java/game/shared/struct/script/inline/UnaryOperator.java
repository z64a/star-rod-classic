package game.shared.struct.script.inline;

public enum UnaryOperator
{
	// @formatter:off
	INT ("int"),
	FLT ("float"),
	SQR ("square"),
	NEG ("-");
	// @formatter:on

	private final String opString;

	private UnaryOperator(String opString)
	{
		this.opString = opString;
	}

	@Override
	public String toString()
	{
		return opString;
	}

	public static UnaryOperator getOperator(String s)
	{
		for (UnaryOperator op : UnaryOperator.values())
			if (op.opString.equalsIgnoreCase(s))
				return op;

		throw new IllegalStateException("Unknown unary operator: " + s);
	}
}
