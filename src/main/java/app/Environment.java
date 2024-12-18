package app;

import java.awt.Image;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.security.CodeSource;
import java.util.Properties;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.SystemUtils;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import app.Resource.ResourceType;
import app.AppVersion.VersionLevel;
import app.config.Config;
import app.config.Options;
import app.config.Options.Scope;
import app.input.IOUtils;
import game.ROM.RomVersion;
import game.map.editor.ui.dialogs.ChooseDialogResult;
import game.map.editor.ui.dialogs.DirChooser;
import game.map.editor.ui.dialogs.OpenFileChooser;
import game.shared.ProjectDatabase;
import util.Logger;
import util.Priority;

public abstract class Environment
{
	private static DirChooser projectChooser;
	private static OpenFileChooser romChooser;
	private static final String FN_MAIN_CONFIG = "cfg/main.cfg";
	private static final String FN_DUMP_CONFIG = "dump.cfg";

	public static ImageIcon ICON_DEFAULT = loadIconResource(ResourceType.Icon, "icon.png");
	public static ImageIcon ICON_ERROR = null;

	private static enum OSFamily
	{
		Windows,
		Mac,
		Linux,
		Unknown
	}

	private static OSFamily osFamily = OSFamily.Unknown;

	public static Config mainConfig = null;
	public static Project project;

	public static Config dumpConfig = null;
	public static AppVersion dumpVersion = null;
	public static boolean hasCurrentDump = false;

	private static boolean fromJar = false;
	private static boolean commandLine = false;

	private static File codeSource;
	private static File baseRom;

	private static boolean initialized = false;

	private static AppVersion currentVersion;
	private static String gitBuildBranch;
	private static String gitBuildCommit;
	private static String gitBuildTag;

	public static AppVersion getVersion()
	{
		return currentVersion;
	}

	public static String getVersionString()
	{
		return currentVersion.toString();
	}

	public static String decorateTitle(String title)
	{
		StringBuilder sb = new StringBuilder();

		sb.append(title);

		sb.append(" (").append(currentVersion).append(")");

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

		if (SystemUtils.IS_OS_WINDOWS)
			osFamily = OSFamily.Windows;
		else if (SystemUtils.IS_OS_LINUX)
			osFamily = OSFamily.Linux;
		else if (SystemUtils.IS_OS_MAC)
			osFamily = OSFamily.Mac;
		else
			osFamily = OSFamily.Unknown;

		commandLine = isCommandLine;

		// running from a jar, we need to set the natives directory at runtime
		try {
			CodeSource src = StarRodClassic.class.getProtectionDomain().getCodeSource();
			String sourceName = src.getLocation().toURI().toString();

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
				currentVersion = AppVersion.fromString(attr.getValue("App-Version"));
				gitBuildBranch = attr.getValue("Build-Branch");
				gitBuildCommit = attr.getValue("Build-Commit");
				gitBuildTag = attr.getValue("Build-Tag");

				Logger.logf("Detected version %s (%s-%s)", currentVersion, gitBuildBranch, gitBuildCommit.subSequence(0, 8));
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
				currentVersion = AppVersion.fromString(prop.getProperty("version"));
				Logger.logf("Detected version %s (IDE)", currentVersion);
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

			if (fromJar && mainConfig.getBoolean(Options.CheckForUpdates))
				checkForUpdate();

			Logger.setDefaultOuputPriority(mainConfig.getBoolean(Options.LogDetails) ? Priority.DETAIL : Priority.STANDARD);
			ProjectDatabase.initialize(false);
			Environment.reloadIcons();
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

	public static void checkForUpdate()
	{
		try {
			URL url = new URI("https://api.github.com/repos/z64a/star-rod-classic/releases/latest").toURL();
			HttpURLConnection connection = (HttpURLConnection) url.openConnection();
			connection.setRequestMethod("GET");
			connection.setConnectTimeout(1000);
			connection.setReadTimeout(1000);

			int responseCode = connection.getResponseCode();
			if (responseCode == HttpURLConnection.HTTP_OK) {
				InputStreamReader inputStreamReader = new InputStreamReader(connection.getInputStream());
				JsonObject jsonObject = JsonParser.parseReader(inputStreamReader).getAsJsonObject();
				String latestTag = jsonObject.get("tag_name").getAsString();

				AppVersion latestVersion = AppVersion.fromString(latestTag);

				if (latestVersion.isNewerThan(currentVersion)) {
					Logger.log("Detected newer remote version: " + latestVersion);

					SwingUtils.getWarningDialog()
						.setTitle("Update Available")
						.setMessage("A newer version is available!", "Please visit the GitHub repo to download it.")
						.show();
				}
			}
			else {
				Logger.logError("Update check failed (response code: " + responseCode + ")");
			}
		}
		catch (Exception e) {
			Logger.logError("IOException while checking for updates: " + e.getMessage());
			Logger.printStackTrace(e);
		}
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
		File configFile = new File(codeSource.getParent(), FN_MAIN_CONFIG);

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
				setBaseRom(baseRom, RomVersion.US); //TODO
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

			setBaseRom(selectedRom, RomVersion.US); //TODO
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

		setBaseRom(selectedRom, RomVersion.US); //TODO

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
		File dumpConfigFile = new File(Directories.getDumpPath() + "/" + Environment.FN_DUMP_CONFIG);
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
		File dumpConfigFile = new File(Directories.getDumpPath() + "/" + FN_DUMP_CONFIG);
		dumpConfig = new Config(dumpConfigFile, Scope.Dump);

		if (dumpConfigFile.exists()) {
			String dumpVersionString = dumpConfig.getString(Options.DumpVersion);
			dumpVersion = AppVersion.fromString(dumpVersionString);

			if (dumpVersion == null) {
				SwingUtils.getErrorDialog()
					.setTitle("Unknown Dump Version")
					.setMessage("Could not parse dump version: " + dumpVersionString)
					.show();
				return;
			}

			if (dumpVersion.isOlderThan(currentVersion, VersionLevel.MINOR)) {
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
						setBaseRom(validatedRom, RomVersion.US); //TODO
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

	public static void setBaseRom(File rom, RomVersion version)
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

		if (version == RomVersion.US)
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

	public static boolean isWindows()
	{
		return osFamily == OSFamily.Windows;
	}

	public static boolean isMacOS()
	{
		return osFamily == OSFamily.Mac;
	}

	public static boolean isLinux()
	{
		return osFamily == OSFamily.Linux;
	}

	public static void reloadIcons()
	{
		ICON_DEFAULT = loadIconFile(Directories.DUMP_IMG_ASSETS + "item/battle/ShootingStar.png");
		if (ICON_DEFAULT == null)
			ICON_DEFAULT = loadIconResource(ResourceType.Icon, "icon.png");
		ICON_ERROR = loadIconFile(Directories.DUMP_SPR_NPC_SRC + "3B/Raster_1A.png");
	}

	public static final Image getDefaultIconImage()
	{
		return (ICON_DEFAULT == null) ? null : ICON_DEFAULT.getImage();
	}

	public static final Image getErrorIconImage()
	{
		return (ICON_DEFAULT == null) ? null : ICON_DEFAULT.getImage();
	}

	private static ImageIcon loadIconFile(String fileName)
	{
		File imageFile = new File(fileName);
		if (!imageFile.exists()) {
			System.err.println("Unable to find image " + fileName);
			return null;
		}

		try {
			return new ImageIcon(ImageIO.read(imageFile));
		}
		catch (IOException e) {
			System.err.println("Exception while loading image " + fileName);
			return null;
		}
	}

	private static ImageIcon loadIconResource(ResourceType type, String resourceName)
	{
		try {
			return new ImageIcon(ImageIO.read(Resource.getStream(type, resourceName)));
		}
		catch (IOException e) {
			System.err.println("Exception while loading image " + resourceName);
			return null;
		}
	}
}
