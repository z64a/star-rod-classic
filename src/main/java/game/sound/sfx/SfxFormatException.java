package game.sound.sfx;

/** A user-facing validation or malformed-input error in an SFX asset. */
public class SfxFormatException extends RuntimeException
{
	public SfxFormatException(String message)
	{
		super(message);
	}

	public SfxFormatException(String message, Throwable cause)
	{
		super(message, cause);
	}
}
