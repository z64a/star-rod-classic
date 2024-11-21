package app;

import static app.Directories.*;
import static app.config.Options.*;

import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.GraphicsEnvironment;
import java.awt.Image;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import javax.imageio.ImageIO;
import javax.swing.AbstractButton;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JProgressBar;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.WindowConstants;

import org.apache.commons.io.FileUtils;

import app.config.BuildOptionsPanel;
import app.config.Config;
import app.config.DumpOptionsPanel;
import app.config.Options;
import app.input.IOUtils;
import app.input.InvalidInputException;
import app.update.BackupCreator;
import app.update.MinorUpdator;
import asm.AsmUtils;
import common.BaseEditor;
import game.ROM;
import game.ROM.LibScope;
import game.RomLoader;
import game.RomLoader.ByteOrder;
import game.battle.ActorTypesEditor;
import game.battle.AuxBattleDumper;
import game.battle.BattleDumper;
import game.battle.struct.Actor;
import game.effects.EffectEditor;
import game.globals.ItemModder;
import game.globals.MoveModder;
import game.globals.MoveRecord;
import game.globals.editor.GlobalsEditor;
import game.globals.library.LibraryScriptDumper;
import game.map.Map;
import game.map.MapIndex;
import game.map.compiler.BuildException;
import game.map.compiler.CollisionCompiler;
import game.map.compiler.GeometryCompiler;
import game.map.config.LevelEditor;
import game.map.editor.MapEditor;
import game.map.editor.ui.dialogs.ChooseDialogResult;
import game.map.editor.ui.dialogs.DirChooser;
import game.map.patching.MapDumper;
import game.map.scripts.DecompScriptGenerator;
import game.map.scripts.ScriptGenerator;
import game.map.shading.SpriteShadingEditor;
import game.requests.SpecialRequestDumper;
import game.shared.ProjectDatabase;
import game.shared.struct.script.ScriptVariable;
import game.sound.AudioEditor;
import game.sprite.SpriteDumper;
import game.sprite.editor.SpriteEditor;
import game.string.MessageBoxes;
import game.string.StringDumper;
import game.string.editor.StringEditor;
import game.string.font.FontManager;
import game.texture.CompressedImageDumper;
import game.texture.CompressedImagePatcher;
import game.texture.editor.ImageEditor;
import game.texture.images.ImageScriptModder;
import game.world.action.ActionEditor;
import game.world.entity.EntityDecompiler;
import game.world.entity.EntityEditor;
import game.world.partner.PartnerWorldDumper;
import game.worldmap.WorldMapEditor;
import game.worldmap.WorldMapModder;
import net.miginfocom.swing.MigLayout;
import patcher.Patcher;
import reports.BattleMapTracker;
import reports.EffectTypeTracker;
import reports.FunctionCallTracker;
import shared.Globals;
import shared.SwingUtils;
import util.LogFile;
import util.Logger;
import util.Logger.Listener;
import util.Priority;

public class StarRodClassic extends JFrame
{
	public CountDownLatch doneSignal;

	public static void main(String[] args)
	{
		Environment.initialize(args.length > 0 || GraphicsEnvironment.isHeadless());

		if (Environment.isCommandLine()) {
			runCommandLine(args);
			Environment.exit();
		}

		try {
			checkVersion();

			boolean showMenu = true;
			CountDownLatch editorClosedSignal;

			while (showMenu) {
				if (!Environment.hasCurrentDump) {
					StarRodClassic app = new StarRodClassic();
					app.doneSignal.await();
					showMenu = Environment.hasCurrentDump;
				}
				else {
					GreetingDialog hello = new GreetingDialog();
					GreetingChoice chosenTool = hello.getChoice();
					hello.dispose();

					switch (chosenTool) {
						case EXIT:
							Logger.log("Star Rod successfully shut down.");
							Environment.exit();
							break;

						case MOD_MANAGER:
							StarRodClassic dev = new StarRodClassic();
							dev.doneSignal.await();
							showMenu = dev.exitToMainMenu;
							break;

						case GLOBALS_EDITOR:
							editorClosedSignal = new CountDownLatch(1);
							GlobalsEditor globalsEditor = new GlobalsEditor(editorClosedSignal);
							editorClosedSignal.await();
							showMenu = globalsEditor.exitToMainMenu;
							break;

						case LEVEL_EDITOR:
							editorClosedSignal = new CountDownLatch(1);
							LevelEditor levelEditor = new LevelEditor(editorClosedSignal);
							editorClosedSignal.await();
							showMenu = levelEditor.exitToMainMenu;
							break;

						case MAP_EDITOR:
							MapEditor mapEditor = new MapEditor(true);
							showMenu = mapEditor.launch();
							break;

						case IMAGE_EDITOR:
							BaseEditor imgEditor = new ImageEditor();
							showMenu = imgEditor.launch();
							break;

						case SPRITE_EDITOR:
							BaseEditor spriteEditor = new SpriteEditor();
							showMenu = spriteEditor.launch();
							break;

						case STRING_EDITOR:
							StringEditor stringEditor = new StringEditor();
							showMenu = stringEditor.launch();
							break;

						case WORLD_MAP_EDITOR:
							BaseEditor worldEditor = new WorldMapEditor();
							showMenu = worldEditor.launch();
							break;

						case THEMES:
							editorClosedSignal = new CountDownLatch(1);
							ThemesEditor themesEditor = new ThemesEditor(editorClosedSignal);
							editorClosedSignal.await();
							showMenu = themesEditor.exitToMainMenu;
							break;

						//	default:
						//		throw new IllegalStateException("Tool does not exist for " + chosenTool);
					}
				}
			}
		}
		catch (Throwable e) {
			displayStackTrace(e);
		}
	}

	private final DumpOptionsPanel dumpOptionsPanel = new DumpOptionsPanel();
	private final BuildOptionsPanel buildOptionsPanel = new BuildOptionsPanel();

	private final JTextArea consoleTextArea;
	private final Listener consoleListener;

	private final Listener progressListener;
	private final JPanel progressPanel;
	private final JProgressBar progressBar;
	//	private final JLabel taskLabel;
	private final JLabel progressLabel;

	private boolean exitToMainMenu = false;
	private boolean taskRunning = false;

	private List<JButton> buttons = new ArrayList<>();

	private JButton createModButton;
	private JButton compileModButton;
	private JButton packageModButton;
	private JButton compileOptionsButton;

	private void updateButtonAvailablity()
	{
		boolean modButtonsEnabled = !Environment.project.isDecomp;
		createModButton.setEnabled(modButtonsEnabled);
		compileModButton.setEnabled(modButtonsEnabled);
		packageModButton.setEnabled(modButtonsEnabled);
		compileOptionsButton.setEnabled(modButtonsEnabled);
	}

	private StarRodClassic()
	{
		doneSignal = new CountDownLatch(1);
		LoadingScreen loadingScreen = null;
		if (!Environment.isCommandLine())
			loadingScreen = new LoadingScreen();

		setTitle(Environment.decorateTitle("Mod Manager"));
		setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
		setIconImage(Globals.getDefaultIconImage());

		setMinimumSize(new Dimension(480, 32));
		setLocationRelativeTo(null);

		JTextField modFolderField = new JTextField();
		modFolderField.setMinimumSize(new Dimension(64, 24));
		modFolderField.setText(Environment.project.getDirectory().getAbsolutePath());

		JButton chooseFolderButton = new JButton("Change");
		chooseFolderButton.addActionListener(e -> {
			File choice = Environment.promptSelectProjectDirectory();
			if (choice != null) {
				modFolderField.setText(choice.getAbsolutePath());
				try {
					checkVersion();
					updateButtonAvailablity();
				}
				catch (Throwable t) {
					displayStackTrace(t);
				}
			}
		});
		buttons.add(chooseFolderButton);

		JTextField romFileField = new JTextField();
		romFileField.setEditable(false);
		romFileField.setMinimumSize(new Dimension(64, 24));
		romFileField.setText(Environment.getBaseRomPath());

		JButton chooseROMButton = new JButton("Change");
		chooseROMButton.addActionListener(e -> {
			// must do this outside the EDT or the "Please Wait" dialog will be bugged
			new Thread() {
				@Override
				public void run()
				{
					for (JButton button : buttons)
						button.setEnabled(false);

					File choice = Environment.promptSelectBaseRom();
					if (choice != null)
						romFileField.setText(choice.getAbsolutePath());

					for (JButton button : buttons)
						button.setEnabled(true);
				}
			}.start();
		});
		buttons.add(chooseROMButton);

		JButton dumpROMButton = new JButton("Dump ROM Assets");
		dumpROMButton.addActionListener(e -> {
			int choice = JOptionPane.OK_OPTION;

			if (Environment.dumpVersion != null)
				choice = SwingUtils.getConfirmDialog()
					.setTitle("Overwrite Warning")
					.setMessage("Existing dump files will be overwritten.", "Do you wish to continue?")
					.setOptionsType(JOptionPane.YES_NO_OPTION)
					.setMessageType(JOptionPane.WARNING_MESSAGE)
					.choose();

			if (choice == JOptionPane.OK_OPTION) {
				startTask(new TaskWorker(() -> {
					if (dumpAssets()) {
						SwingUtilities.invokeLater(() -> {
							// frame will not properly resize unless we do this here
							revalidate();
							pack();

							Globals.reloadIcons();
							setIconImage(Globals.getDefaultIconImage());
							Toolkit.getDefaultToolkit().beep();

							SwingUtils.getMessageDialog()
								.setTitle("Asset Dump Complete")
								.setMessage("All assets have been dumped.")
								.setMessageType(JOptionPane.INFORMATION_MESSAGE)
								.show();
						});
					}
				}));
			}
		});
		buttons.add(dumpROMButton);

		JButton dumpOptionsButton = new JButton("Options");
		dumpOptionsButton.addActionListener(e -> {
			Config cfg = Environment.mainConfig;
			dumpOptionsPanel.setValues(cfg);

			int choice = SwingUtils.getConfirmDialog()
				.setTitle("Dump Options")
				.setMessage(dumpOptionsPanel)
				.setOptionsType(JOptionPane.OK_CANCEL_OPTION)
				.setMessageType(JOptionPane.PLAIN_MESSAGE)
				.choose();

			if (choice == JOptionPane.YES_OPTION) {
				dumpOptionsPanel.getValues(cfg);
				cfg.saveConfigFile();
			}
		});
		buttons.add(dumpOptionsButton);

		createModButton = new JButton("Copy Assets to Mod");
		createModButton.addActionListener(e -> {
			if (!Environment.hasCurrentDump) {
				SwingUtils.getWarningDialog()
					.setTitle("Current Dump Required")
					.setMessage("You must dump assets before copying them to your mod.")
					.show();
				return;
			}

			int choice = SwingUtils.getConfirmDialog()
				.setTitle("Copy Assets to Mod")
				.setMessage("Any existing mod files will be overwritten.", "Do you wish to continue?")
				.setOptionsType(JOptionPane.YES_NO_OPTION)
				.setMessageType(JOptionPane.WARNING_MESSAGE)
				.choose();

			if (choice == JOptionPane.OK_OPTION) {
				startTask(new TaskWorker(() -> {
					if (copyAssets()) {
						SwingUtilities.invokeLater(() -> {
							// frame will not properly resize unless we do this here
							revalidate();
							pack();

							Toolkit.getDefaultToolkit().beep();

							SwingUtils.getMessageDialog()
								.setTitle("Asset Copy Complete")
								.setMessage("Ready to begin modding.")
								.setMessageType(JOptionPane.INFORMATION_MESSAGE)
								.show();
						});
					}
				}));
			}
		});
		buttons.add(createModButton);

		compileModButton = new JButton("Compile Mod");
		compileModButton.addActionListener(e -> {
			float requiredMemory = 1024 * 1e6f;
			long maxMemory = Runtime.getRuntime().maxMemory();

			if (maxMemory < requiredMemory) {
				if (!Environment.isCommandLine()) {
					int choice = SwingUtils.getOptionDialog()
						.setTitle("Not Enough Memory")
						.setMessage(
							String.format("Ensure Java has at least %d MB for compiling your mod!", (int) (requiredMemory / 1e6)),
							String.format("Available memory: %.2f MB", maxMemory / 1e6))
						.setMessageType(JOptionPane.WARNING_MESSAGE)
						.setOptionsType(JOptionPane.YES_NO_CANCEL_OPTION)
						.setIcon(Globals.ICON_ERROR)
						.setOptions("Continue", "Abort")
						.choose();

					if (choice != 0)
						return;
				}
				else {
					Logger.logfWarning("Ensure Java has at least 1 GB for compiling your mod! "
						+ "Available memory: %.2f MB", maxMemory / 1e6);
				}
			}

			startTask(new TaskWorker(() -> {
				if (compileMod()) {
					SwingUtilities.invokeLater(() -> {
						// frame will not properly resize unless we do this here
						revalidate();
						pack();

						Toolkit.getDefaultToolkit().beep();

						SwingUtils.getMessageDialog()
							.setTitle("Mod Compiled")
							.setMessage("Finished compiling mod.")
							.setMessageType(JOptionPane.INFORMATION_MESSAGE)
							.show();
					});
				}
			}));
		});
		buttons.add(compileModButton);

		compileOptionsButton = new JButton("Options");
		compileOptionsButton.addActionListener(e -> {
			Config cfg = Environment.project.config;
			buildOptionsPanel.setValues(cfg);

			try {
				ProjectDatabase.reload(true);
			}
			catch (IOException ex) {
				throw new StarRodException(ex);
			}

			int choice = SwingUtils.getConfirmDialog()
				.setTitle("Build Options")
				.setMessage(buildOptionsPanel)
				.setOptionsType(JOptionPane.OK_CANCEL_OPTION)
				.setMessageType(JOptionPane.PLAIN_MESSAGE)
				.choose();

			if (choice == JOptionPane.YES_OPTION) {
				buildOptionsPanel.getValues(cfg);
				cfg.saveConfigFile();
			}
		});
		buttons.add(compileOptionsButton);

		packageModButton = new JButton("Package Mod");
		packageModButton.addActionListener(e -> {
			startTask(new TaskWorker(() -> {
				packageMod();
			}));
		});
		buttons.add(packageModButton);

		consoleTextArea = new JTextArea();
		consoleTextArea.setRows(20);
		consoleTextArea.setEditable(false);

		JScrollPane consoleScrollPane = new JScrollPane(consoleTextArea);
		consoleScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
		consoleScrollPane.setVisible(false);

		consoleListener = (msg) -> {
			consoleTextArea.append(msg.text + System.lineSeparator());
			JScrollBar vertical = consoleScrollPane.getVerticalScrollBar();
			vertical.setValue(vertical.getMaximum());
		};

		JPopupMenu popupMenu = new JPopupMenu();

		JMenuItem toggleLog = new JMenuItem("Show Log");
		popupMenu.add(toggleLog);
		((JComponent) getContentPane()).setComponentPopupMenu(popupMenu);

		toggleLog.addActionListener(e -> {
			if (consoleScrollPane.isVisible()) {
				toggleLog.setText("Show Log");
				consoleScrollPane.setVisible(false);
			}
			else {
				toggleLog.setText("Hide Log");
				consoleScrollPane.setVisible(true);
			}
			revalidate();
			pack();
		});

		JMenuItem switchTools = new JMenuItem("Back to Main Menu");
		popupMenu.add(switchTools);
		((JComponent) getContentPane()).setComponentPopupMenu(popupMenu);

		switchTools.addActionListener(e -> {
			exitToMainMenu = true;
			WindowEvent closingEvent = new WindowEvent(this, WindowEvent.WINDOW_CLOSING);
			Toolkit.getDefaultToolkit().getSystemEventQueue().postEvent(closingEvent);
		});

		JMenuItem copyText = new JMenuItem("Copy Text");
		JPopupMenu copyTextMenu = new JPopupMenu();
		copyTextMenu.add(copyText);
		consoleScrollPane.setComponentPopupMenu(copyTextMenu);
		copyText.addActionListener(e -> {
			StringSelection stringSelection = new StringSelection(consoleTextArea.getText());
			Clipboard cb = Toolkit.getDefaultToolkit().getSystemClipboard();
			cb.setContents(stringSelection, null);
		});

		//	taskLabel = new JLabel("Current Task");
		progressLabel = new JLabel("The march of progress.");
		progressBar = new JProgressBar();
		progressBar.setIndeterminate(true);
		progressPanel = new JPanel();
		progressPanel.setLayout(new MigLayout("fillx"));
		//	progressPanel.add(taskLabel,"w 25%");
		progressPanel.add(progressLabel, "wrap");
		progressPanel.add(progressBar, "grow, wrap 8");
		progressPanel.setVisible(false);

		progressListener = (msg) -> {
			SwingUtilities.invokeLater(() -> progressLabel.setText(msg.text));
		};

		setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
		addWindowListener(new java.awt.event.WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent e)
			{
				int choice = JOptionPane.OK_OPTION;

				if (taskRunning)
					choice = SwingUtils.getConfirmDialog()
						.setTitle("Task Still Running")
						.setMessage("A task is still running.", "Are you sure you want to exit?")
						.setOptionsType(JOptionPane.YES_NO_OPTION)
						.setMessageType(JOptionPane.WARNING_MESSAGE)
						.choose();

				if (choice == JOptionPane.OK_OPTION) {
					doneSignal.countDown();
					dispose();
				}
			}
		});

		updateButtonAvailablity();

		setLayout(new MigLayout("fillx, ins 16 16 n 16, hidemode 3"));

		add(new JLabel("Mod Folder:"));
		add(modFolderField, "pushx, growx");
		add(chooseFolderButton, "wrap");
		add(new JLabel("Target ROM:"));
		add(romFileField, "pushx, growx");
		add(chooseROMButton, "wrap 16");

		add(dumpROMButton, "w 36%, span 3, split 3, center");
		add(dumpOptionsButton, "w 8%");
		add(createModButton, "w 36%, wrap 8");

		add(compileModButton, "w 36%, span 3, split 3, center");
		add(compileOptionsButton, "w 8%");
		add(packageModButton, "w 36%, wrap 8");

		add(progressPanel, "grow, span, wrap");
		add(consoleScrollPane, "grow, span, wrap 8");

		if (loadingScreen != null)
			loadingScreen.dispose();
		pack();
		setResizable(false);
		setVisible(!Environment.isCommandLine());

		Logger.addListener(consoleListener);
	}

	private void startTask(SwingWorker<?, ?> worker)
	{
		taskRunning = true;

		for (JButton button : buttons)
			button.setEnabled(false);

		Logger.setProgressListener(progressListener);
		consoleTextArea.setText("");
		progressLabel.setText("");
		progressPanel.setVisible(true);
		revalidate();
		pack();

		worker.execute();
	}

	private void endTask()
	{
		for (JButton button : buttons)
			button.setEnabled(true);

		Logger.removeProgressListener();
		progressPanel.setVisible(false);
		progressLabel.setText("");
		revalidate();
		pack();

		taskRunning = false;
	}

	private class TaskWorker extends SwingWorker<Boolean, String>
	{
		private final Runnable runnable;

		private TaskWorker(Runnable runnable)
		{
			this.runnable = runnable;
		}

		@Override
		protected Boolean doInBackground()
		{
			runnable.run();
			return true;
		}

		@Override
		protected void done()
		{
			endTask();
		}
	}

	private static boolean dumpAssets()
	{
		return dumpAssets(false);
	}

	public static boolean dumpAssets(boolean forceClean)
	{
		LogFile dumpLog = null;

		try {
			// refresh
			ProjectDatabase.reload(false);

			Environment.mainConfig.readConfig();
			AsmUtils.tabWidth = Environment.mainConfig.getBoolean(Options.UseTabSpacing) ? 4 : 0;

			boolean clean = Environment.mainConfig.getBoolean(CleanDump);
			boolean fullDump = (Environment.dumpVersion == null) || clean || forceClean;

			if (Directories.getDumpPath() == null)
				throw new IOException("Dump directory is not set.");

			File dumpDirectory = new File(Directories.getDumpPath());
			if (fullDump && dumpDirectory.exists())
				FileUtils.cleanDirectory(dumpDirectory);
			Directories.createDumpDirectories();

			dumpLog = new LogFile(new File(dumpDirectory.getAbsolutePath() + "/dump.log"), false);
			Logger.log("Starting ROM dump: " + new java.util.Date().toString(), Priority.IMPORTANT);

			Config cfg = Environment.mainConfig;

			if (cfg.getBoolean(DumpReports))
				BattleMapTracker.enable();

			ScriptVariable.useSettings(cfg);

			// refresh
			SpriteShadingEditor.dumpShading();
			ProjectDatabase.reload(false);

			if (fullDump || cfg.getBoolean(DumpMessages)) {
				Logger.log("Dumping strings...", Priority.MILESTONE);
				StringDumper.dumpAllStrings();
				Logger.log("Dumping font...", Priority.MILESTONE);
				FontManager.dump();
				Logger.log("Dumping message boxes...", Priority.MILESTONE);
				MessageBoxes.dump();
			}

			if (fullDump || cfg.getBoolean(DumpTables)) {
				Logger.log("Dumping globals...", Priority.MILESTONE);

				ArrayList<MoveRecord> moves = MoveModder.dumpTable();
				ItemModder.dumpTable(moves);

				ProjectDatabase.images.dumpAll();
				ImageScriptModder.dumpAll();
			}

			ProjectDatabase.loadGlobals(false);

			FileUtils.copyFile(
				new File(DATABASE + FN_GAME_BYTES),
				new File(DUMP_GLOBALS + FN_GAME_BYTES));

			FileUtils.copyFile(
				new File(DATABASE + FN_GAME_FLAGS),
				new File(DUMP_GLOBALS + FN_GAME_FLAGS));

			if (fullDump || cfg.getBoolean(DumpMaps)) {
				Logger.log("Dumping maps...", Priority.MILESTONE);
				MapDumper.dumpMaps(fullDump, cfg.getBoolean(RecompressMaps));
			}

			// start dumping battle scripts
			ByteBuffer fileBuffer = Environment.getBaseRomBuffer();
			FunctionCallTracker.clear();

			ActorTypesEditor.dump();

			if (fullDump || cfg.getBoolean(DumpBattles)) {
				Logger.log("Dumping battles...", Priority.MILESTONE);
				BattleDumper.dumpBattles(fileBuffer);
			}

			if (fullDump || cfg.getBoolean(DumpMoves)) {
				Logger.log("Dumping moves...", Priority.MILESTONE);
				AuxBattleDumper.dumpMoves(fileBuffer);
				AuxBattleDumper.dumpPartnerMoves(fileBuffer);
				AuxBattleDumper.dumpStarPowers(fileBuffer);
				AuxBattleDumper.dumpItemScripts(fileBuffer);
				AuxBattleDumper.dumpActionCommands(fileBuffer); //these dont dump well atm
			}

			if (fullDump || cfg.getBoolean(DumpPartners)) {
				Logger.log("Dumping partner data...", Priority.MILESTONE);
				PartnerWorldDumper.dumpPartners(fileBuffer);
			}

			if (fullDump || cfg.getBoolean(DumpWorld)) {
				Logger.log("Dumping world data...", Priority.MILESTONE);
				ActionEditor.dumpWorldActions(fileBuffer);
				EntityEditor.dumpWorldEntities(fileBuffer);
				EntityDecompiler.decompileAll();
			}

			if (cfg.getBoolean(DumpReports)) {
				PrintWriter pw = IOUtils.getBufferedPrintWriter(Directories.DUMP_REPORTS + "enemy_names.txt");
				for (int i = 0; i < 0xD4; i++) {
					fileBuffer.position(0x1AF9E4 + 4 * i);
					int nameStringID = fileBuffer.getInt();

					fileBuffer.position(0x1B1478 + 4 * i);
					int tattleStringID = fileBuffer.getInt();

					String actorName = ProjectDatabase.getActorName(i);
					String origin = (Actor.nameIDs[i] == null) ? "unused" : Actor.nameIDs[i];

					pw.printf("%02X  %08X %08X  %% %-16s (%s)%n", i, nameStringID, tattleStringID, actorName, origin);
				}
				pw.close();

				FileUtils.forceMkdir(DUMP_REQUESTS.toFile());
				SpecialRequestDumper.dumpRequestedScripts();
				SpecialRequestDumper.dumpRequestedFunctions();

				FunctionCallTracker.printCalls(
					ProjectDatabase.rom.getLibrary(LibScope.Battle),
					new PrintWriter(DUMP_REPORTS + "battle_func_list.txt"));

				BattleMapTracker.printBattles();
				BattleMapTracker.printMaps();

				EffectTypeTracker.printEffects(
					new PrintWriter(DUMP_REPORTS + "used_effects.txt"));
			}
			// done with battle scripts

			if (fullDump || cfg.getBoolean(DumpTextures)) {
				Logger.log("Dumping textures...", Priority.MILESTONE);
				CompressedImageDumper.dumpTextures();
			}

			if (fullDump || cfg.getBoolean(DumpSprites)) {
				SpriteDumper.dumpSprites();
			}

			if (fullDump || cfg.getBoolean(DumpAudio)) {
				Logger.log("Dumping audio...", Priority.MILESTONE);
				AudioEditor.dumpAudio();
			}

			WorldMapModder.dump();

			if (fullDump || cfg.getBoolean(DumpLibrary)) {
				Logger.log("Dumping libraries...", Priority.MILESTONE);
				LibraryScriptDumper.dumpAll();
				EffectEditor.dumpEffects(fileBuffer);
			}

			if (cfg.getBoolean(CleanDump)) {
				cfg.setBoolean(CleanDump, false);
				cfg.saveConfigFile();
			}

			Environment.createNewDumpConfig();
			Environment.dumpConfig.setString(Options.DumpVersion, Environment.getVersionString());
			Environment.dumpConfig.saveConfigFile();
			Environment.dumpVersion = Environment.getVersionString();
			Environment.hasCurrentDump = true;

			Logger.log("Finished ROM dump: " + new java.util.Date().toString(), Priority.IMPORTANT);
		}
		catch (Throwable e) {
			displayStackTrace(e);
			return false;
		}
		finally {
			if (dumpLog != null)
				dumpLog.close();
			ScriptVariable.clearSettings();
		}

		return true;
	}

	public static boolean copyAssets() //TEMP 0.5 update
	{
		try {
			File dumpDirectory = new File(Directories.getDumpPath());
			if (!dumpDirectory.exists()) {
				SwingUtils.getWarningDialog()
					.setTitle("Missing Dump Directory")
					.setMessage("Could not find dump directory.", "You must dump assets before copying them to your mod.")
					.show();
				return false;
			}

			if (Directories.getModPath() == null)
				throw new IOException("Mod directory is not set.");

			Directories.createModDirectories();

			Logger.log("Copying strings...", Priority.MILESTONE);
			FileUtils.cleanDirectory(MOD_STRINGS_SRC.toFile());
			FileUtils.copyDirectory(DUMP_STRINGS_SRC.toFile(), MOD_STRINGS_SRC.toFile());
			Directories.copyAllMissing(DUMP_STRINGS_FONT, MOD_STRINGS_FONT);
			Directories.copyAllMissing(DUMP_TXTBOX_IMG, MOD_TXTBOX_IMG);
			Directories.copyIfMissing(DEFAULTS, MOD_STRINGS, FN_STRING_CONSTANTS);

			Logger.log("Copying map data...", Priority.MILESTONE);
			Directories.copyIfMissing(DUMP_MAP, MOD_MAP, FN_MAP_TABLE);
			FileUtils.cleanDirectory(MOD_MAP_SRC.toFile());
			FileUtils.copyDirectory(DUMP_MAP_SRC.toFile(), MOD_MAP_SRC.toFile());
			Directories.copyAllMissing(DUMP_MAP_THUMBNAIL, MOD_MAP_THUMBNAIL);

			Logger.log("Copying world data...", Priority.MILESTONE);
			FileUtils.cleanDirectory(MOD_ASSIST_SRC.toFile());
			FileUtils.copyDirectory(DUMP_ASSIST_SRC.toFile(), MOD_ASSIST_SRC.toFile());

			Logger.log("Copying battle data...", Priority.MILESTONE);
			Directories.copyIfMissing(DUMP_BATTLE, MOD_FORMA, FN_BATTLE_SECTIONS);
			Directories.copyIfMissing(DUMP_BATTLE, MOD_BATTLE, FN_BATTLE_ACTORS);
			FileUtils.cleanDirectory(MOD_FORMA_SRC.toFile());
			FileUtils.copyDirectory(DUMP_FORMA_SRC.toFile(), MOD_FORMA_SRC.toFile());
			FileUtils.cleanDirectory(MOD_FORMA_ENEMY.toFile());
			FileUtils.copyDirectory(DUMP_FORMA_ENEMY.toFile(), MOD_FORMA_ENEMY.toFile());

			Directories.copyIfMissing(DUMP_MOVE, MOD_MOVE, FN_BATTLE_MOVES);
			FileUtils.cleanDirectory(MOD_MOVE_SRC.toFile());
			FileUtils.copyDirectory(DUMP_MOVE_SRC.toFile(), MOD_MOVE_SRC.toFile());

			FileUtils.cleanDirectory(MOD_ALLY_SRC.toFile());
			FileUtils.copyDirectory(DUMP_ALLY_SRC.toFile(), MOD_ALLY_SRC.toFile());

			FileUtils.cleanDirectory(MOD_MINIGAME_SRC.toFile());
			FileUtils.copyDirectory(DUMP_MINIGAME_SRC.toFile(), MOD_MINIGAME_SRC.toFile());

			Directories.copyIfMissing(DUMP_ITEM, MOD_ITEM, FN_BATTLE_ITEMS);
			FileUtils.cleanDirectory(MOD_ITEM_SRC.toFile());
			FileUtils.copyDirectory(DUMP_ITEM_SRC.toFile(), MOD_ITEM_SRC.toFile());

			FileUtils.cleanDirectory(MOD_STARS_SRC.toFile());
			FileUtils.copyDirectory(DUMP_STARS_SRC.toFile(), MOD_STARS_SRC.toFile());

			Logger.log("Copying texture data...", Priority.MILESTONE);
			Directories.copyIfEmpty(DUMP_IMG_TEX, MOD_IMG_TEX);
			Directories.copyIfEmpty(DUMP_IMG_BG, MOD_IMG_BG);
			Directories.copyIfEmpty(DUMP_IMG_ASSETS, MOD_IMG_ASSETS);
			Directories.copyIfEmpty(DUMP_IMG_COMP, MOD_IMG_COMP);
			Directories.copyIfEmpty(DUMP_HUD_SCRIPTS, MOD_HUD_SCRIPTS);
			Directories.copyIfEmpty(DUMP_ITEM_SCRIPTS, MOD_ITEM_SCRIPTS);
			Directories.copyIfMissing(DUMP_IMG, MOD_IMG, FN_IMAGE_ASSETS);
			Directories.copyIfMissing(DUMP_IMG, MOD_IMG, FN_ITEM_SCRIPTS);
			Directories.copyIfMissing(DUMP_IMG, MOD_IMG, FN_HUD_SCRIPTS);

			Logger.log("Copying sprite data...", Priority.MILESTONE);
			Directories.copyIfMissing(DUMP_SPRITE, MOD_SPRITE, FN_SPRITE_TABLE);
			Directories.copyIfMissing(DUMP_SPRITE, MOD_SPRITE, FN_SPRITE_SHADING);
			Directories.copyIfEmpty(DUMP_SPR_NPC_SRC, MOD_SPR_NPC_SRC);
			Directories.copyIfEmpty(DUMP_SPR_PLR_SRC, MOD_SPR_PLR_SRC);

			Logger.log("Copying audio data...", Priority.MILESTONE);
			Directories.copyAllMissing(DUMP_AUDIO, MOD_AUDIO);

			Logger.log("Copying global data...", Priority.MILESTONE);
			Directories.copyAllMissing(DATABASE_TYPES, MOD_ENUMS, "enum");

			Directories.copyAllMissing(DUMP_GLOBALS, MOD_GLOBALS);
			FileUtils.touch(new File(MOD_GLOBALS + FN_MOD_BYTES));
			FileUtils.touch(new File(MOD_GLOBALS + FN_MOD_FLAGS));

			Logger.log("Creating mod directories...", Priority.MILESTONE);
			//Directories.copyIfEmpty(DEFAULTS_GLOBAL, MOD_PATCH, true);
			Directories.copyIfEmpty(DEFAULTS_MAP, MOD_MAP_PATCH, true);
			Directories.copyIfEmpty(DEFAULTS_FORM, MOD_FORMA_PATCH, true);
			Directories.copyIfEmpty(DEFAULTS_MOVE, MOD_MOVE_PATCH, true);
			Directories.copyIfEmpty(DEFAULTS_ALLY, MOD_ALLY_PATCH, true);
			Directories.copyIfEmpty(DEFAULTS_ITEM, MOD_ITEM_PATCH, true);
			Directories.copyIfEmpty(DEFAULTS_STARS, MOD_STARS_PATCH, true);
		}
		catch (Throwable e) {
			displayStackTrace(e);
			return false;
		}
		return true;
	}

	/*
	private void makeBackup() throws IOException
	{
		Backup b = new Backup();

		b.addDirectory(Directories.MOD_PATCH.toFile());
		b.addDirectory(Directories.MOD_GLOBALS.toFile());
		b.addDirectory(Directories.MOD_STRINGS.toFile());
		b.addDirectory(Directories.MOD_MAP_CFG.toFile());
		b.addDirectory(Directories.MOD_MAP_PATCH.toFile());
		b.addDirectory(Directories.MOD_MAP_BUILD.toFile());
		// map areas.cfg and AssetTable.txt
		b.addDirectory(Directories.MOD_BATTLE_PATCH.toFile());

		File backupFile = new File(Directories.MOD_OUT + "backup");
		b.writeAllFiles(backupFile);
	}
	 */

	private static boolean compileMod()
	{
		LogFile compileLog = null;

		try {
			if (Directories.getModPath() == null)
				throw new IOException("Mod directory is not set.");

			compileLog = new LogFile(new File(LOGS + "compile.log"), false);

			// refresh
			Environment.project.prepareNewRom();
			Environment.project.config.readConfig();
			Config cfg = Environment.mainConfig;

			ProjectDatabase.reload(true);
			ScriptVariable.useSettings(cfg);

			new Patcher();
		}
		catch (Throwable e) {
			displayStackTrace(e);
			return false;
		}
		finally {
			if (compileLog != null)
				compileLog.close();
			ScriptVariable.clearSettings();
		}
		return true;
	}

	private boolean packageMod()
	{
		try {
			Environment.project.config.readConfig(); // refresh
			File patchedRom = Environment.project.getTargetRom();

			if (!patchedRom.exists()) {
				SwingUtils.getWarningDialog()
					.setTitle("Missing Patched ROM")
					.setMessage("Could not find patched ROM.", "You must compile your mod before it can be packaged.")
					.show();
				return false;
			}

			Patcher.packageMod(patchedRom);

			if (!Environment.isCommandLine()) {
				SwingUtilities.invokeLater(() -> {
					// frame will not properly resize unless we do this here
					revalidate();
					pack();

					Toolkit.getDefaultToolkit().beep();

					SwingUtils.getMessageDialog()
						.setTitle("Mod Package Ready")
						.setMessage("Mod package is ready.")
						.setMessageType(JOptionPane.INFORMATION_MESSAGE)
						.show();
				});
			}
		}
		catch (Throwable e) {
			displayStackTrace(e);
			return false;
		}
		return true;
	}

	public static void handleEarlyCrash(Throwable e)
	{
		if (!Environment.isCommandLine()) {
			Toolkit.getDefaultToolkit().beep();
			StackTraceDialog.display(e, null);
		}
		System.exit(-1);
	}

	public static void displayStackTrace(Throwable e)
	{
		displayStackTrace(e, null);
	}

	public static void displayStackTrace(Throwable e, File log)
	{
		Logger.printStackTrace(e);

		if (!Environment.isCommandLine()) {
			SwingUtilities.invokeLater(() -> {
				Toolkit.getDefaultToolkit().beep();
				StackTraceDialog.display(e, log);
			});
		}
	}

	public static void openTextFile(File file)
	{
		if (file == null)
			return;

		try {
			Desktop.getDesktop().open(file);
		}
		catch (IOException openDefaultIOE) {
			try {
				if (Environment.os.isWindows()) {
					Runtime rs = Runtime.getRuntime();
					rs.exec("notepad " + file.getCanonicalPath());
				}
				else {
					openDefaultIOE.printStackTrace();
				}
			}
			catch (IOException nativeIOE) {
				nativeIOE.printStackTrace();
			}
		}
	}

	private static void runCommandLine(String[] args)
	{
		for (int i = 0; i < args.length; i++) {
			switch (args[i].toUpperCase()) {
				case "-VERSION":
					System.out.println("VERSION=" + Environment.getVersionString());
					break;
				case "-DUMPASSETS":
					if (!Environment.hasCurrentDump)
						dumpAssets();
					else
						Logger.log("No need to re-dump");
					break;
				case "-COPYASSETS":
					copyAssets();
					break;
				case "-COMPILEMOD":
					compileMod();
					break;

				case "-COMPILESHAPE":
				case "-COMPILEHIT":
				case "-GENERATESCRIPT":
				case "-COMPILEMAP":
					if (args.length > i + 1) {
						String mapName = args[i + 1];
						File mapFile;
						if (mapName.endsWith(".xml")) {
							mapFile = new File(Environment.project.getDirectory(), mapName);
						}
						else {
							mapFile = AssetManager.getMap(mapName);
						}

						if (mapFile == null) {
							Logger.logfError("Cannot find map '%s'!", mapName);
							break;
						}

						Map map = Map.loadMap(mapFile);
						try {
							if (args[i].equals("-CompileMap")) {
								new GeometryCompiler(map);
								new CollisionCompiler(map);
								if (Environment.project.isDecomp)
									new DecompScriptGenerator(map);
								else
									new ScriptGenerator(map);
							}
							else if (args[i].equals("-CompileShape"))
								new GeometryCompiler(map);
							else if (args[i].equals("-CompileHit"))
								new CollisionCompiler(map);
							else if (args[i].equals("-GenerateScript"))
								new ScriptGenerator(map);
							else
								throw new IllegalStateException();
						}
						catch (BuildException be) {
							be.printStackTrace();
						}
						catch (IOException e) {
							e.printStackTrace();
						}
						catch (InvalidInputException e) {
							e.printStackTrace();
						}

						i++;
					}
					else
						Logger.logfError("%s expects a mapName argument!", args[i]);
					break;

				case "-COMPILEMAPS":
					try {
						for (File mapFile : AssetManager.getMapsToBuild()) {
							try {
								Map map = Map.loadMap(mapFile);

								new GeometryCompiler(map);
								new CollisionCompiler(map);
								if (Environment.project.isDecomp)
									new DecompScriptGenerator(map);
								else
									new ScriptGenerator(map);
							}
							catch (BuildException be) {
								be.printStackTrace();
							}
							catch (IOException e) {
								e.printStackTrace();
							}
							catch (InvalidInputException e) {
								e.printStackTrace();
							}
						}
					}
					catch (IOException e) {
						e.printStackTrace();
					}
					break;

				case "-COMPILETEXTURES":
					try {
						CompressedImagePatcher imgPatcher = new CompressedImagePatcher();
						imgPatcher.buildTextureArchives();
					}
					catch (IOException e) {
						e.printStackTrace();
					}
					break;

				case "-COMPILEBACKGROUNDS":
					try {
						CompressedImagePatcher imgPatcher = new CompressedImagePatcher();
						imgPatcher.buildBackgrounds();
					}
					catch (IOException e) {
						e.printStackTrace();
					}
					break;

				case "-DUMPMAPS":
					if (args.length > i + 1) {
						File romFile = new File(args[i + 1]);
						if (!romFile.exists()) {
							Logger.logfError("Cannot find ROM file: %s", romFile.getAbsolutePath());
							break;
						}

						try {
							ByteBuffer romBuffer = IOUtils.getDirectBuffer(romFile);
							ByteOrder romOrder = RomLoader.checkByteOrder(romBuffer);
							if (romOrder == null) {
								Logger.logfError(RomLoader.getError());
								break;
							}
							if (romOrder != ByteOrder.BIG) {
								Logger.logfError("ROM has incorrect byte order, please convert it to big endian.");
								break;
							}
							ROM rom = RomLoader.tryLoadingROM(romBuffer, Directories.DATABASE.toFile());
							if (rom == null) {
								Logger.logfError(RomLoader.getError());
								break;
							}

							Environment.setBaseRom(romFile, rom.version);
							MapDumper.dumpMaps(rom, new RandomAccessFile(romFile, "r"), false, false);
						}
						catch (IOException e) {
							Logger.logfError("Exception while dumping maps: %s", e.getMessage());
							break;
						}

						i++;
					}
					else
						Logger.logfError("%s expects a ROM file!", args[i]);
					break;

				default:
					Logger.logfError("Unrecognized command line arg: ", args[i]);
			}
		}
	}

	private static enum GreetingChoice
	{
		EXIT,
		MOD_MANAGER,
		GLOBALS_EDITOR,
		LEVEL_EDITOR,
		MAP_EDITOR,
		IMAGE_EDITOR,
		SPRITE_EDITOR,
		STRING_EDITOR,
		WORLD_MAP_EDITOR,
		THEMES
	}

	private static class GreetingDialog extends JDialog
	{
		private GreetingChoice selected = null;

		public GreetingDialog()
		{
			super((JDialog) null, true);

			addWindowListener(new WindowAdapter() {
				@Override
				public void windowClosing(WindowEvent e)
				{
					if (selected == null)
						selected = GreetingChoice.EXIT;
				}
			});

			JButton modManagerButton = new JButton("Mod Manager");
			trySetIcon(modManagerButton, "item/battle/PowerStar");
			SwingUtils.setFontSize(modManagerButton, 12);
			modManagerButton.addActionListener((e) -> {
				selected = GreetingChoice.MOD_MANAGER;
				dispatchEvent(new WindowEvent(this, WindowEvent.WINDOW_CLOSING));
			});

			JButton globalsEditorButton = new JButton("Globals Editor");
			trySetIcon(globalsEditorButton, "item/Items");
			SwingUtils.setFontSize(globalsEditorButton, 12);
			globalsEditorButton.addActionListener((e) -> {
				selected = GreetingChoice.GLOBALS_EDITOR;
				dispatchEvent(new WindowEvent(this, WindowEvent.WINDOW_CLOSING));
			});

			JButton levelEditorButton = new JButton("Level Editor");
			trySetIcon(levelEditorButton, "item/key/dictionary");
			SwingUtils.setFontSize(levelEditorButton, 12);
			levelEditorButton.addActionListener((e) -> {
				selected = GreetingChoice.LEVEL_EDITOR;
				dispatchEvent(new WindowEvent(this, WindowEvent.WINDOW_CLOSING));
			});

			JButton mapEditorButton = new JButton("Map Editor");
			trySetIcon(mapEditorButton, "item/Hammer2");
			SwingUtils.setFontSize(mapEditorButton, 12);
			mapEditorButton.addActionListener((e) -> {
				selected = GreetingChoice.MAP_EDITOR;
				dispatchEvent(new WindowEvent(this, WindowEvent.WINDOW_CLOSING));
			});

			JButton imageEditorButton = new JButton("Image Editor");
			trySetIcon(imageEditorButton, "item/food/TastyTonic");
			SwingUtils.setFontSize(imageEditorButton, 12);
			imageEditorButton.addActionListener((e) -> {
				selected = GreetingChoice.IMAGE_EDITOR;
				dispatchEvent(new WindowEvent(this, WindowEvent.WINDOW_CLOSING));
			});

			JButton spriteEditorButton = new JButton("Sprite Editor");
			trySetIcon(spriteEditorButton, "item/peach/BakingStrawberry");
			SwingUtils.setFontSize(spriteEditorButton, 12);
			spriteEditorButton.addActionListener((e) -> {
				selected = GreetingChoice.SPRITE_EDITOR;
				dispatchEvent(new WindowEvent(this, WindowEvent.WINDOW_CLOSING));
			});

			JButton stringEditorButton = new JButton("String Editor");
			trySetIcon(stringEditorButton, "item/key/EmptyBook");
			SwingUtils.setFontSize(stringEditorButton, 12);
			stringEditorButton.addActionListener((e) -> {
				selected = GreetingChoice.STRING_EDITOR;
				dispatchEvent(new WindowEvent(this, WindowEvent.WINDOW_CLOSING));
			});

			JButton worldMapEditorButton = new JButton("World Map Editor");
			trySetIcon(worldMapEditorButton, "ui/pause/unused_compass");
			SwingUtils.setFontSize(worldMapEditorButton, 12);
			worldMapEditorButton.addActionListener((e) -> {
				selected = GreetingChoice.WORLD_MAP_EDITOR;
				dispatchEvent(new WindowEvent(this, WindowEvent.WINDOW_CLOSING));
			});

			JButton themesEditorButton = new JButton("Themes");
			trySetIcon(themesEditorButton, "item/badge/PUpDDown");
			SwingUtils.setFontSize(themesEditorButton, 12);
			themesEditorButton.addActionListener((e) -> {
				selected = GreetingChoice.THEMES;
				dispatchEvent(new WindowEvent(this, WindowEvent.WINDOW_CLOSING));
			});

			setTitle(Environment.decorateTitle("Star Rod"));
			setIconImage(Globals.getDefaultIconImage());

			//	setMinimumSize(new Dimension(320,64)); // 2x2
			setMinimumSize(new Dimension(220, 220));
			setLocationRelativeTo(null);

			String fmtButton = "sg buttons, grow, push";

			setLayout(new MigLayout("fill, wrap"));
			add(modManagerButton, fmtButton);
			if (!Environment.project.isDecomp)
				add(globalsEditorButton, fmtButton);
			add(stringEditorButton, fmtButton);
			if (!Environment.project.isDecomp)
				add(levelEditorButton, fmtButton);
			add(mapEditorButton, fmtButton);
			add(spriteEditorButton, fmtButton);
			add(imageEditorButton, fmtButton);
			if (!Environment.project.isDecomp)
				add(worldMapEditorButton, fmtButton);
			add(themesEditorButton, fmtButton);

			pack();
			setResizable(false);
			setVisible(true);
		}

		public GreetingChoice getChoice()
		{
			return selected;
		}
	}

	private static final void trySetIcon(AbstractButton button, String iconName)
	{
		if (Environment.dumpVersion == null)
			return;

		if (!(new File(Directories.getDumpPath())).exists()) {
			Logger.log("Dump directory could not be found.");
			return;
		}

		File f = new File(Directories.DUMP_IMG_ASSETS + iconName + ".png");
		ImageIcon imageIcon;

		try {
			imageIcon = new ImageIcon(ImageIO.read(f));
		}
		catch (IOException e) {
			System.err.println("Exception while reading icon: " + iconName);
			return;
		}

		int size = 24;

		Image image = imageIcon.getImage().getScaledInstance(size, size, java.awt.Image.SCALE_SMOOTH); // scale it the smooth way
		imageIcon = new ImageIcon(image);

		button.setIcon(imageIcon);
		button.setIconTextGap(24);
		button.setHorizontalAlignment(SwingConstants.LEFT);
		button.setVerticalTextPosition(SwingConstants.CENTER);
		button.setHorizontalTextPosition(SwingConstants.RIGHT);
	}

	/**
	 * @return positive = a later than b, negative = b later than a, 0 = equal
	 */
	public static int compareVersionStrings(String a, String b)
	{
		int[] avals, bvals;

		avals = tokenizeVersionString(a);
		bvals = tokenizeVersionString(b);

		for (int i = 0; i < avals.length; i++) {
			if (avals[i] > bvals[i])
				return 1;
			else if (avals[i] < bvals[i])
				return -1;
		}

		return 0;
	}

	private static int[] tokenizeVersionString(String ver)
	{
		if (ver == null || !ver.contains("."))
			throw new IllegalArgumentException("Invalid version string: " + ver);

		String[] tokens = ver.split("\\.");
		int[] values = new int[3];

		for (int i = 0; i < 3; i++) {
			try {
				values[i] = Integer.parseInt(tokens[i]);
			}
			catch (NumberFormatException e) {
				throw new IllegalArgumentException("Invalid version string: " + ver);
			}
		}

		return values;
	}

	private static void checkVersion() throws IOException, InterruptedException
	{
		Config modConfig = Environment.project.config;

		if (modConfig == null) {
			// Assume everything is fine.
			return;
		}

		String modVersion = modConfig.getString(Options.CompileVersion);
		if (modVersion.equals(Environment.getVersionString()))
			return;

		// clear caches
		FileUtils.deleteDirectory(Directories.MOD_IMG_CACHE.toFile());
		FileUtils.deleteDirectory(Directories.MOD_SPR_NPC_CACHE.toFile());
		FileUtils.deleteDirectory(Directories.MOD_SPR_PLR_CACHE.toFile());
		if (MapIndex.getFile().exists())
			FileUtils.forceDelete(MapIndex.getFile());

		int[] curVer = tokenizeVersionString(Environment.getVersionString());
		int[] modVer = tokenizeVersionString(modVersion);
		if ((curVer[0] != modVer[0]) || (curVer[1] != modVer[1])) { // && (curVer[2] != modVer[2])) {
			update(modVersion, modConfig, (f) -> {
				new MinorUpdator(f);
			});
		}
		else if (modVer[0] == 0 && modVer[1] == 4) {
			SwingUtils.getWarningDialog()
				.setTitle("Out of Date Mod")
				.setMessage("Detected mod version " + modVersion + ".",
					"Please update to version 0.5 using an older 0.5.x release",
					"before updating to " + Environment.getVersionString() + ".")
				.show();
			Environment.exit();
		}
		else if (modVer[0] == 0 && modVer[1] < 2) {
			SwingUtils.getWarningDialog()
				.setTitle("Out of Date Mod")
				.setMessage("Detected mod version " + modVersion + ".", "This version is no longer supported.")
				.show();
			Environment.exit();
		}
	}

	private static void update(String modVersion, Config modConfig, UpdateFunction updateFunc) throws IOException, InterruptedException
	{
		int choice = SwingUtils.getConfirmDialog()
			.setTitle("Out of Date Mod")
			.setMessage("Detected mod version " + modVersion + ".",
				"Migrate mod to " + Environment.getVersionString() + "?")
			.setOptionsType(JOptionPane.YES_NO_OPTION)
			.setMessageType(JOptionPane.WARNING_MESSAGE)
			.choose();

		if (choice != JOptionPane.OK_OPTION)
			Environment.exit();

		choice = SwingUtils.getConfirmDialog()
			.setTitle("Make Backup")
			.setMessage("Create a backup of your mod directory?")
			.setOptionsType(JOptionPane.YES_NO_OPTION)
			.setMessageType(JOptionPane.WARNING_MESSAGE)
			.choose();

		if (choice == JOptionPane.OK_OPTION) {
			try {
				new BackupCreator(Environment.project);
			}
			catch (IOException e) {
				SwingUtils.getErrorDialog()
					.setTitle("Backup Failed")
					.setMessage("Failed to create backup!", e.getMessage())
					.show();

				Logger.printStackTrace(e);
				Environment.exit();
			}

			SwingUtils.getMessageDialog()
				.setTitle("Backup Successful")
				.setMessage("Backup complete.")
				.setMessageType(JOptionPane.INFORMATION_MESSAGE)
				.show();
		}

		SwingUtils.getMessageDialog()
			.setTitle("Find Old Database")
			.setMessage("Select the old Star Rod database folder.")
			.setMessageType(JOptionPane.INFORMATION_MESSAGE)
			.show();

		DirChooser dbChooser = new DirChooser(Environment.getWorkingDirectory(), "Select Old Database Directory");
		if (dbChooser.prompt() == ChooseDialogResult.APPROVE) {
			File dbChoice = dbChooser.getSelectedFile();
			if (dbChoice == null) {
				SwingUtils.getErrorDialog()
					.setTitle("Update Failed")
					.setMessage("Can't update without reading the old database.")
					.show();

				Environment.exit();
			}

			updateFunc.update(dbChoice);
			modConfig.setString(Options.CompileVersion, Environment.getVersionString());
			modConfig.saveConfigFile();

			SwingUtils.getMessageDialog()
				.setTitle("Update Complete")
				.setMessage("Automatic update done.", "Don't forget to copy assets to your mod.")
				.setMessageType(JOptionPane.INFORMATION_MESSAGE)
				.show();
		}
		else {
			SwingUtils.getErrorDialog()
				.setTitle("Update Failed")
				.setMessage("Can't update without reading the old database.")
				.show();

			Environment.exit();
		}
	}

	private static abstract interface UpdateFunction
	{
		public void update(File dbFile) throws IOException, InterruptedException;
	}
}
