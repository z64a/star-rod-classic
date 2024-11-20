package app;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.util.Properties;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

import org.apache.commons.io.FileUtils;

import app.config.Config;
import app.config.Options;
import app.config.Options.Scope;
import app.input.IOUtils;
import game.ROM.Version;
import game.map.editor.ui.dialogs.ChooseDialogResult;
import game.map.editor.ui.dialogs.DirChooser;
import game.map.editor.ui.dialogs.OpenFileChooser;
import game.shared.ProjectDatabase;
import shared.Globals;
import shared.RomValidator;
import shared.SwingUtils;
import util.Logger;
import util.Priority;

public abstract class Environment
{
	private static DirChooser projectChooser;
	private static OpenFileChooser romChooser;
	private static final String MAIN_CONFIG_FILENAME = "cfg/main.cfg";
	private static final String DUMP_CONFIG_FILENAME = "dump.cfg";

	public static Config mainConfig = null;
	public static Project project;

	public static Config dumpConfig = null;
	public static String dumpVersion = null;
	public static boolean hasCurrentDump = false;

	private static boolean fromJar = false;
	private static boolean commandLine = false;

	private static File codeSource;
	private static File baseRom;

	private static boolean initialized = false;

	private static String versionString;
	private static String gitBuildBranch;
	private static String gitBuildCommit;
	private static String gitBuildTag;

	public static OS os;

	public static String getVersionString()
	{
		return versionString;
	}

	public static String decorateTitle(String title)
	{
		StringBuilder sb = new StringBuilder();

		sb.append(title);

		sb.append(" (v").append(versionString).append(")");

		if (fromJar && (gitBuildTag == null || !gitBuildTag.startsWith("v")) && gitBuildCommit != null)
			sb.append(" (").append(gitBuildCommit.substring(0, 8)).append(")");

		return sb.toString();
	}

	public static void initialize()
	{
		initialize(false);
	}

	public static void initialize(boolean isCommandLine)
	{
		if (initialized)
			return;

		os = new OS();
		commandLine = isCommandLine;

		// running from a jar, we need to set the natives directory at runtime
		try {
			String sourceName = StarRodClassic.class.getProtectionDomain().getCodeSource().getLocation().toURI().toString();

			Matcher matcher = Pattern.compile("%[0-9A-Fa-f]{2}").matcher(sourceName);
			StringBuffer sb = new StringBuffer(sourceName.length());
			while (matcher.find()) {
				String encoded = matcher.group(0).substring(1);
				matcher.appendReplacement(sb, "" + (char) Integer.parseInt(encoded, 16));
			}
			matcher.appendTail(sb);
			sourceName = sb.toString();

			if (sourceName.startsWith("file:/"))
				sourceName = sourceName.substring(5);

			codeSource = new File(sourceName);
			fromJar = (sourceName.endsWith(".jar"));
			Logger.log("Executing from " + codeSource.getAbsolutePath());
		}
		catch (URISyntaxException e) {
			Logger.logError("Could not determine path to StarRod code source!");
			StarRodClassic.handleEarlyCrash(e);
		}

		if (fromJar) {
			ClassLoader cl = Environment.class.getClassLoader();
			try {
				Manifest manifest = new Manifest(cl.getResourceAsStream("META-INF/MANIFEST.MF"));
				Attributes attr = manifest.getMainAttributes();
				versionString = attr.getValue("App-Version");
				gitBuildBranch = attr.getValue("Build-Branch");
				gitBuildCommit = attr.getValue("Build-Commit");
				gitBuildTag = attr.getValue("Build-Tag");

				Logger.logf("Detected version %s (%s-%s)", versionString, gitBuildBranch, gitBuildCommit.subSequence(0, 8));
			}
			catch (IOException e) {
				Logger.logError("Could not read MANIFEST.MF");
				Logger.printStackTrace(e);
			}
		}
		else {
			try {
				Properties prop = new Properties();
				prop.load(new FileInputStream(new File("./app.properties")));
				versionString = prop.getProperty("version");
				Logger.logf("Detected version %s (IDE)", versionString);
			}
			catch (IOException e) {
				Logger.logError("Could not read version properties file: " + e.getMessage());
			}
		}

		projectChooser = new DirChooser(codeSource.getParentFile(), "Select Project Directory");
		romChooser = new OpenFileChooser(codeSource.getParentFile(), "Select Base ROM", "N64 Roms", "n64", "z64", "v64");

		try {
			checkForDependencies();
			readStarRodConfig();

			if (!isCommandLine)
				Themes.setThemeByKey(Environment.mainConfig.getString(Options.Theme));

			Logger.setDefaultOuputPriority(mainConfig.getBoolean(Options.LogDetails) ? Priority.DETAIL : Priority.STANDARD);
			ProjectDatabase.initialize(false);
			Globals.reloadIcons();
		}
		catch (Throwable t) {
			StarRodClassic.handleEarlyCrash(t);
		}

		initialized = true;
	}

	public static void exit()
	{
		exit(0);
	}

	public static void exit(int status)
	{
		System.exit(status);
	}

	public static boolean isCommandLine()
	{
		return commandLine;
	}

	public static File getCodeSource()
	{
		return codeSource;
	}

	public static File getWorkingDirectory()
	{
		if (fromJar)
			return codeSource.getParentFile();
		else
			return new File(".");
	}

	private static final void checkForDependencies() throws IOException
	{
		File db = Directories.DATABASE.toFile();

		if (!db.exists() || !db.isDirectory()) {
			SwingUtils.getErrorDialog()
				.setTitle("Missing Directory")
				.setMessage("Could not find required directory: " + db.getName(),
					"It should be in the same directory as the jar.")
				.show();

			System.exit(0);
		}
	}

	private static final void readStarRodConfig() throws IOException
	{
		File configFile = new File(codeSource.getParent(), MAIN_CONFIG_FILENAME);

		// we may need to create a new config file here
		if (!configFile.exists()) {
			int choice = SwingUtils.getConfirmDialog()
				.setTitle("Missing Config")
				.setMessage("Could not find Star Rod config!", "Create a new one?")
				.setMessageType(JOptionPane.QUESTION_MESSAGE)
				.setOptionsType(JOptionPane.YES_NO_OPTION)
				.choose();

			if (choice != JOptionPane.OK_OPTION)
				System.exit(0);

			boolean success = makeNewConfig(configFile);

			if (!success) {
				SwingUtils.getErrorDialog()
					.setTitle("Create Config Failed")
					.setMessage("Failed to create new config.",
						"Please try again.")
					.show();

				System.exit(0);
			}

			mainConfig.saveConfigFile();
			return;
		}

		// if config exists, read it
		mainConfig = new Config(configFile, Scope.Main);
		mainConfig.readConfig();

		String romFilename = mainConfig.getString(Options.RomPath);
		String modDirectoryName = mainConfig.getString(Options.ModPath);

		boolean missingROM = true;
		boolean missingMod = true;

		// get mod directory

		if (modDirectoryName != null) {
			File modDirectory;
			if (modDirectoryName.startsWith("."))
				modDirectory = new File(codeSource.getParent(), modDirectoryName);
			else
				modDirectory = new File(modDirectoryName);

			if (modDirectory.exists() && modDirectory.isDirectory()) {
				loadProject(modDirectory);
				missingMod = false;
			}
		}

		if (missingMod) {
			SwingUtils.getErrorDialog()
				.setTitle("Missing Mod Directory")
				.setMessage("Could not find mod directory!", "Please select a new one.")
				.show();

			File selectedDirectory = promptSelectProjectDirectory();

			if (selectedDirectory == null)
				System.exit(0);

			loadProject(selectedDirectory);
		}

		// get ROM
		if (romFilename != null) {
			if (romFilename.startsWith("."))
				baseRom = new File(codeSource.getParent(), romFilename);
			else
				baseRom = new File(romFilename);

			if (baseRom.exists()) {
				// do not validate when loading from the config
				setBaseRom(baseRom, Version.US); //TODO
				missingROM = false;
			}
		}

		if (missingROM) {
			SwingUtils.getErrorDialog()
				.setTitle("Missing ROM")
				.setMessage("Could not find base ROM!", "Please select a new one.")
				.show();

			File selectedRom = promptSelectBaseRom();
			if (selectedRom == null)
				System.exit(0);

			setBaseRom(selectedRom, Version.US); //TODO
		}
	}

	private static boolean makeNewConfig(File configFile) throws IOException
	{
		FileUtils.touch(configFile);
		mainConfig = new Config(configFile, Scope.Main);

		SwingUtils.getMessageDialog()
			.setTitle("Select Mod Directory")
			.setMessage("Select a directory to use for your mod.")
			.setMessageType(JOptionPane.PLAIN_MESSAGE)
			.show();

		File selectedDirectory = promptSelectProjectDirectory();
		if (selectedDirectory == null)
			return false;

		loadProject(selectedDirectory);

		SwingUtils.getMessageDialog()
			.setTitle("Select ROM")
			.setMessage("Select a clean Paper Mario ROM.")
			.setMessageType(JOptionPane.PLAIN_MESSAGE)
			.show();

		File selectedRom = promptSelectBaseRom();
		if (selectedRom == null)
			return false;

		setBaseRom(selectedRom, Version.US); //TODO

		// set default values for other options
		for (Options opt : Options.values()) {
			if (opt == Options.ModPath || opt == Options.RomPath)
				continue;

			switch (opt.scope) {
				case Main:
					opt.setToDefault(mainConfig);
					break;
				default:
			}
		}

		return true;
	}

	public static Config createNewDumpConfig() throws IOException
	{
		File dumpConfigFile = new File(Directories.getDumpPath() + "/" + Environment.DUMP_CONFIG_FILENAME);
		Environment.dumpConfig = new Config(dumpConfigFile, Scope.Dump);
		return Environment.dumpConfig;
	}

	public static File promptSelectProjectDirectory()
	{
		if (projectChooser.prompt() == ChooseDialogResult.APPROVE) {
			File modChoice = projectChooser.getSelectedFile();
			if (project == null || !modChoice.equals(project.getDirectory())) {
				loadProject(modChoice);
				mainConfig.setString(Options.ModPath, modChoice.getAbsolutePath());
				mainConfig.saveConfigFile();
			}

			return modChoice;
		}
		return null;
	}

	private static void loadProject(File projectDir)
	{
		project = new Project(projectDir);
		SwingUtilities.invokeLater(() -> {
			projectChooser.setCurrentDirectory(projectDir);
		});
		Directories.setProjectDirectory(projectDir.getAbsolutePath());
	}

	private static void loadDump()
	{
		File dumpConfigFile = new File(Directories.getDumpPath() + "/" + DUMP_CONFIG_FILENAME);
		dumpConfig = new Config(dumpConfigFile, Scope.Dump);

		if (dumpConfigFile.exists()) {
			dumpVersion = dumpConfig.getString(Options.DumpVersion);

			if (StarRodClassic.compareVersionStrings(dumpVersion, Environment.getVersionString()) != 0) {
				SwingUtils.getErrorDialog()
					.setTitle("Old Dump Found")
					.setMessage("Found outdated ROM dump from version " + dumpVersion + ".",
						"Redump the ROM before making a mod.")
					.show();
			}
			else {
				hasCurrentDump = true;
			}
		}
		else if (!isCommandLine()) {
			SwingUtils.getErrorDialog()
				.setTitle("No Dump Found")
				.setMessage("Could not find ROM dump.", "Dump the ROM before making a mod.")
				.show();
		}
	}

	public static File promptSelectBaseRom()
	{
		if (romChooser.prompt() == ChooseDialogResult.APPROVE) {
			File romChoice = romChooser.getSelectedFile();
			if (baseRom == null || !romChoice.equals(baseRom)) {
				try {
					File validatedRom = RomValidator.validateROM(romChoice);

					if (validatedRom != null) {
						romChooser.setDirectoryContaining(validatedRom.getParentFile());
						setBaseRom(validatedRom, Version.US); //TODO
						mainConfig.setString(Options.RomPath, validatedRom.getAbsolutePath());
						mainConfig.saveConfigFile();

						return validatedRom;
					}
				}
				catch (IOException e) {
					SwingUtils.getErrorDialog()
						.setTitle("ROM Validation Failure")
						.setMessage("IOException during ROM validation.")
						.show();
				}
			}
		}
		return null;
	}

	public static void setBaseRom(File rom, Version version)
	{
		baseRom = rom;
		SwingUtilities.invokeLater(() -> {
			romChooser.setDirectoryContaining(rom.getParentFile());
		});

		String dumpPath;
		if (fromJar && Environment.project.isDecomp)
			dumpPath = getWorkingDirectory() + "/dump";
		else
			dumpPath = rom.getParentFile().getPath() + "/dump";

		if (version == Version.US)
			dumpPath += "/";
		else
			dumpPath += "_" + version.name().toLowerCase() + "/";

		Directories.setDumpDirectory(dumpPath);
		if (new File(dumpPath).exists())
			loadDump();
	}

	public static String getBaseRomName()
	{
		return baseRom.getName();
	}

	public static String getBaseRomPath()
	{
		return baseRom.getAbsolutePath();
	}

	// only allow read-only references to the base ROM
	public static RandomAccessFile getBaseRomReader() throws FileNotFoundException
	{
		return new RandomAccessFile(baseRom, "r");
	}

	// only allow read-only references to the base ROM
	public static ByteBuffer getBaseRomBuffer() throws IOException
	{
		return IOUtils.getDirectBuffer(baseRom);
	}

	// only allow read-only references to the base ROM
	public static byte[] getBaseRomBytes() throws IOException
	{
		return FileUtils.readFileToByteArray(baseRom);
	}

	// only allow read-only references to the base ROM
	public static File copyBaseRom(File copy) throws IOException
	{
		FileUtils.copyFile(baseRom, copy);
		return copy;
	}
}
