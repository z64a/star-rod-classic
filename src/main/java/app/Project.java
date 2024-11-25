package app;

import static app.Directories.MOD_OUT;

import java.io.File;
import java.io.IOException;

import javax.swing.JOptionPane;

import org.apache.commons.io.FileUtils;

import app.config.Config;
import app.config.DecompConfig;
import app.config.Options;
import app.config.Options.Scope;
import patcher.RomPatcher;
import util.Logger;

public final class Project
{
	private final File directory;
	public Config config = null;

	public final boolean isDecomp;
	public DecompConfig decompConfig = null;

	public Project(File modDirectory)
	{
		directory = modDirectory;
		File decompCfg = new File(directory, "/ver/us/splat.yaml");
		File decompSpriteCfg = new File(directory, "/tools/splat_ext/npc_sprite_names.yaml");
		isDecomp = decompCfg.exists() && decompSpriteCfg.exists();

		if (isDecomp)
			Logger.log("Running in decomp mode.");

		if (isDecomp) {
			try {
				decompConfig = new DecompConfig(directory, decompCfg, decompSpriteCfg);
			}
			catch (IOException e) {
				SwingUtils.getErrorDialog()
					.setTitle("Config Read Exception")
					.setMessage("IOException while attempting to read " + decompCfg.getPath(), e.getMessage())
					.show();
				System.exit(-1);
			}
		}
		else {
			String configName = modDirectory.getAbsolutePath() + "/mod.cfg";
			try {
				readModConfig(new File(configName));
			}
			catch (IOException e) {
				SwingUtils.getErrorDialog()
					.setTitle("Config Read Exception")
					.setMessage("IOException while attempting to read " + configName, e.getMessage())
					.show();
				System.exit(-1);
			}
		}
	}

	private void readModConfig(File configFile) throws IOException
	{
		if (!configFile.exists()) {
			int choice = SwingUtils.getConfirmDialog()
				.setTitle("Missing Config")
				.setMessage("Could not find mod config!", "Create a new one?")
				.setOptionsType(JOptionPane.YES_NO_OPTION)
				.setMessageType(JOptionPane.QUESTION_MESSAGE)
				.choose();

			if (choice != JOptionPane.OK_OPTION)
				System.exit(0);

			boolean success = makeNewConfig(configFile);
			FileUtils.touch(configFile);

			if (!success) {
				SwingUtils.getErrorDialog()
					.setTitle("Create Config Failed")
					.setMessage("Failed to create new config.")
					.show();
				System.exit(0);
			}

			config.saveConfigFile();
			return;
		}

		// if config exists, read it
		config = new Config(configFile, Scope.Dump, Scope.Patch);
		config.readConfig();
	}

	private boolean makeNewConfig(File configFile) throws IOException
	{
		config = new Config(configFile, Scope.Dump, Scope.Patch);
		for (Options opt : Options.values()) {
			switch (opt.scope) {
				case Patch:
				case Dump:
					opt.setToDefault(config);
					break;
				default:
			}
		}

		return true;
	}

	public File getDirectory()
	{
		return directory;
	}

	public void prepareNewRom() throws IOException
	{
		File targetRom = new File(MOD_OUT + Environment.getBaseRomName());
		Environment.copyBaseRom(targetRom);
	}

	public RomPatcher getTargetRomPatcher(int bufferSize) throws IOException
	{
		File targetRom = new File(MOD_OUT + Environment.getBaseRomName());
		return new RomPatcher(bufferSize, targetRom);
	}

	public File getTargetRom()
	{
		return new File(MOD_OUT + Environment.getBaseRomName());
	}
}
