package app;

import static app.Directories.MOD_BACKUPS;
import static app.Directories.MOD_BACKUPS_MAP;
import static app.Directories.MOD_BACKUPS_PROJECT;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.apache.commons.io.FileUtils;

public final class ProjectBackups
{
	private ProjectBackups()
	{}

	public static File getRootDirectory()
	{
		return MOD_BACKUPS.toFile();
	}

	public static File getMapDirectory()
	{
		return MOD_BACKUPS_MAP.toFile();
	}

	public static File prepareMapDirectory() throws IOException
	{
		return prepareDirectory(MOD_BACKUPS_MAP.toFile());
	}

	public static File prepareProjectDirectory() throws IOException
	{
		return prepareDirectory(MOD_BACKUPS_PROJECT.toFile());
	}

	private static File prepareDirectory(File directory) throws IOException
	{
		File rootDirectory = MOD_BACKUPS.toFile();
		FileUtils.forceMkdir(rootDirectory);

		File gitignoreFile = new File(rootDirectory, ".gitignore");
		if (!gitignoreFile.exists())
			FileUtils.writeStringToFile(gitignoreFile, "*\n", StandardCharsets.UTF_8);

		FileUtils.forceMkdir(directory);
		return directory;
	}
}
