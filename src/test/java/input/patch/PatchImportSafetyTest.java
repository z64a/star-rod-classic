package input.patch;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.lang.reflect.Field;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import app.AppVersion;
import app.Directories;
import app.Environment;
import app.Project;
import app.input.InputFileException;
import app.input.InvalidInputException;
import app.input.Line;
import game.ROM.LibScope;
import game.map.MapIndex;
import game.shared.StructTypes;
import game.shared.encoder.BaseDataEncoder;
import patcher.DefaultGlobals;
import patcher.IGlobalDatabase;
import patcher.RomPatcher;

public class PatchImportSafetyTest
{
	private Project previousProject;
	private AppVersion previousVersion;
	private String previousModPath;
	private Path projectDirectory;

	@BeforeEach
	public void setUpProject() throws Exception
	{
		URL configURL = getClass().getResource("/input/patch/imports/project/mod.cfg");
		assertNotNull(configURL, "Missing import test project.");

		projectDirectory = Paths.get(configURL.toURI()).getParent();
		previousProject = Environment.project;
		previousModPath = Directories.getModPath();
		Field versionField = Environment.class.getDeclaredField("currentVersion");
		versionField.setAccessible(true);
		previousVersion = (AppVersion) versionField.get(null);
		if (previousVersion == null)
			versionField.set(null, AppVersion.fromString("0.0.0"));

		Directories.setProjectDirectory(projectDirectory.toString());
		Environment.project = new Project(projectDirectory.toFile());
	}

	@AfterEach
	public void restoreProject() throws Exception
	{
		Environment.project = previousProject;

		Field modPathField = Directories.class.getDeclaredField("modPath");
		modPathField.setAccessible(true);
		modPathField.set(null, previousModPath);

		Field versionField = Environment.class.getDeclaredField("currentVersion");
		versionField.setAccessible(true);
		versionField.set(null, previousVersion);
	}

	@Test
	public void rejectsRecursiveImportCycle()
	{
		ImportTestEncoder encoder = new ImportTestEncoder();
		File patchFile = projectDirectory.resolve("map/patch/cycle-root.patch").toFile();

		InputFileException exception = assertThrows(InputFileException.class, () -> encoder.read(patchFile));

		assertTrue(exception.getMessage().contains("Import cycle"), exception.getMessage());
	}

	@Test
	public void rejectsImportOutsideConfiguredDirectory()
	{
		ImportTestEncoder encoder = new ImportTestEncoder();
		File patchFile = projectDirectory.resolve("map/patch/traversal-root.patch").toFile();

		InputFileException exception = assertThrows(InputFileException.class, () -> encoder.read(patchFile));

		assertTrue(exception.getMessage().contains("outside the import directory"), exception.getMessage());
	}

	private static class ImportTestEncoder extends BaseDataEncoder
	{
		public ImportTestEncoder()
		{
			super(StructTypes.mapTypes, LibScope.World, new TestGlobalsDatabase(), Directories.MOD_MAP_IMPORT, true);
		}

		public void read(File patchFile) throws Exception
		{
			readPatchFile(patchFile);
		}

		@Override
		protected void replaceExpression(Line line, String[] args, List<String> newTokenList)
		{}
	}

	private static class TestGlobalsDatabase implements IGlobalDatabase
	{
		@Override
		public void setGlobalPointer(DefaultGlobals global, int addr)
		{}

		@Override
		public void setGlobalPointer(String name, int addr)
		{}

		@Override
		public boolean hasGlobalPointer(String name)
		{
			return false;
		}

		@Override
		public int getGlobalPointerAddress(String name)
		{
			return 0;
		}

		@Override
		public void setGlobalConstant(DefaultGlobals global, String value)
		{}

		@Override
		public void setGlobalConstant(String name, String value)
		{}

		@Override
		public boolean hasGlobalConstant(String name)
		{
			return false;
		}

		@Override
		public String getGlobalConstant(String name)
		{
			return name;
		}

		@Override
		public int resolveStringID(String s) throws InvalidInputException
		{
			return 0;
		}

		@Override
		public boolean hasStringName(String name)
		{
			return false;
		}

		@Override
		public int getStringFromName(String name)
		{
			return 0;
		}

		@Override
		public boolean hasMapIndex(String name)
		{
			return false;
		}

		@Override
		public MapIndex getMapIndex(String name)
		{
			return null;
		}

		@Override
		public int getNpcAnimID(String spriteName, String animName, String palName)
		{
			return 0;
		}

		@Override
		public int getPlayerAnimID(String spriteName, String animName, String palName)
		{
			return 0;
		}

		@Override
		public RomPatcher getRomPatcher()
		{
			return null;
		}
	}
}
