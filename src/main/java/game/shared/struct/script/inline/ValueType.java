package game.shared.struct.script.inline;

public enum ValueType
{
	// @formatter:off
	INT		(true),
	FLOAT	(true),
	UNK		(false),
	DC		(false);
	// @formatter:on

	private final boolean resolved;

	private ValueType(boolean resolved)
	{
		this.resolved = resolved;
	}

	public boolean isResolved()
	{
		return resolved;
	}
}
