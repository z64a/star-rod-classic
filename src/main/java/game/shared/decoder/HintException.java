package game.shared.decoder;

public class HintException extends RuntimeException
{
	public HintException(String msg)
	{
		super(msg);
	}

	public HintException(String format, Object ... args)
	{
		super(String.format(format, args));
	}
}
