package game.string;

public class TagEncodingException extends RuntimeException
{
	public TagEncodingException(String format, Object ... args)
	{
		super(String.format(format, args));
	}
}
