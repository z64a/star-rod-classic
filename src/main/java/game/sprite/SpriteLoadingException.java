package game.sprite;

import java.io.File;

import app.StarRodException;

public class SpriteLoadingException extends StarRodException
{
	public SpriteLoadingException(Throwable cause, String spriteType, int id, String name, File source)
	{
		super("Could not load %s sprite $%02X \"%s\" from:%n%s%n%s", spriteType, id, name, source.getAbsolutePath(), getCauseMessage(cause));
		setStackTrace(cause.getStackTrace());
	}

	private static String getCauseMessage(Throwable cause)
	{
		String message = cause.getMessage();
		return (message == null) ? cause.getClass().getSimpleName() : message;
	}
}
