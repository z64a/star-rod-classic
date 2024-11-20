package game.shared.struct.script.inline.visitors;

public class VisitorException extends RuntimeException
{
	public VisitorException(String fmt, Object ... args)
	{
		super(String.format(fmt, args));
	}

	public VisitorException(Throwable t)
	{
		super(t.getMessage());
		setStackTrace(t.getStackTrace());
	}
}
