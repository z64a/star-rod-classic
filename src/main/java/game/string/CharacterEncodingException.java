package game.string;

public class CharacterEncodingException extends RuntimeException
{
	public CharacterEncodingException(String format, Object ... args)
	{
		super(String.format(format, args));
	}
}
