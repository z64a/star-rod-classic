package app;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.swing.UIManager;

import com.formdev.flatlaf.IntelliJTheme;

import app.input.IOUtils;
import util.Logger;
import util.Priority;

public abstract class Themes
{
	private static final List<Theme> THEMES = new ArrayList<>();
	private static Theme SYSTEM_THEME;
	private static Theme currentTheme = null;

	public static class Theme
	{
		public final String key;
		public final String name;
		public final String className;
		public final boolean custom;

		public Theme(String name, String className)
		{
			this(name, className, false);
		}

		public Theme(String name, String className, boolean custom)
		{
			this.name = name;
			this.key = name.replaceAll("\\s+", "");
			this.className = className;
			this.custom = custom;
		}
	}

	public static String getCurrentThemeKey()
	{
		return currentTheme.key;
	}

	public static String getCurrentThemeName()
	{
		return currentTheme.name;
	}

	public static List<String> getThemeNames()
	{
		List<String> list = new ArrayList<>(THEMES.size());
		for (Theme theme : THEMES)
			list.add(theme.name);
		return list;
	}

	private static void setTheme(Theme theme)
	{
		if (theme == null)
			theme = SYSTEM_THEME;
		else if (theme == currentTheme)
			return;

		if (theme.custom) {
			try {
				IntelliJTheme.install(new BufferedInputStream(new FileInputStream(new File(theme.className))));
				currentTheme = theme;
				return;
			}
			catch (FileNotFoundException e) {
				Logger.logError("Could not find file for theme: " + theme.name);
			}
			// if error, reset to system
			theme = SYSTEM_THEME;
		}

		try {
			UIManager.setLookAndFeel(theme.className);
			currentTheme = theme;
		}
		catch (Exception e) {
			// many types of exceptions are possible here
			Logger.log("Could not set UI to " + theme.key, Priority.ERROR);
		}
	}

	public static void setThemeByKey(String themeKey)
	{
		if (themeKey == null || themeKey.isEmpty())
			themeKey = SYSTEM_THEME.key;

		if (currentTheme != null && themeKey.equalsIgnoreCase(currentTheme.key))
			return;

		Theme newTheme = null;
		for (Theme theme : THEMES) {
			if (theme.key.equalsIgnoreCase(themeKey)) {
				newTheme = theme;
				break;
			}
		}

		setTheme(newTheme);
	}

	public static void setThemeByName(String themeName)
	{
		if (themeName == null || themeName.isEmpty())
			themeName = SYSTEM_THEME.name;

		if (currentTheme != null && themeName.equalsIgnoreCase(currentTheme.name))
			return;

		Theme newTheme = null;
		for (Theme theme : THEMES) {
			if (theme.name.equalsIgnoreCase(themeName)) {
				newTheme = theme;
				break;
			}
		}

		setTheme(newTheme);
	}

	static {
		if (!Environment.isCommandLine()) {
			SYSTEM_THEME = new Theme("System", UIManager.getSystemLookAndFeelClassName());
			THEMES.add(SYSTEM_THEME);
			// @formatter:off
			THEMES.add(new Theme("Flat Light",				"com.formdev.flatlaf.FlatLightLaf"));
			THEMES.add(new Theme("Flat Dark",				"com.formdev.flatlaf.FlatDarkLaf"));
			THEMES.add(new Theme("Arc",						"com.formdev.flatlaf.intellijthemes.FlatArcIJTheme"));
			THEMES.add(new Theme("Arc Orange",				"com.formdev.flatlaf.intellijthemes.FlatArcOrangeIJTheme"));
			THEMES.add(new Theme("Arc Dark",				"com.formdev.flatlaf.intellijthemes.materialthemeuilite.FlatArcDarkIJTheme"));
			THEMES.add(new Theme("Arc Dark Contrast",		"com.formdev.flatlaf.intellijthemes.materialthemeuilite.FlatArcDarkContrastIJTheme"));
			THEMES.add(new Theme("Atom One Dark",			"com.formdev.flatlaf.intellijthemes.materialthemeuilite.FlatAtomOneDarkIJTheme"));
			THEMES.add(new Theme("Atom One Dark Contrast",	"com.formdev.flatlaf.intellijthemes.materialthemeuilite.FlatAtomOneDarkContrastIJTheme"));
			THEMES.add(new Theme("Atom One Light",			"com.formdev.flatlaf.intellijthemes.materialthemeuilite.FlatAtomOneLightIJTheme"));
			THEMES.add(new Theme("Atom One Light Contrast",	"com.formdev.flatlaf.intellijthemes.materialthemeuilite.FlatAtomOneLightContrastIJTheme"));
			THEMES.add(new Theme("Cyan light",				"com.formdev.flatlaf.intellijthemes.FlatCyanLightIJTheme"));
			THEMES.add(new Theme("Dark Flat",				"com.formdev.flatlaf.intellijthemes.FlatDarkFlatIJTheme"));
			THEMES.add(new Theme("Dark purple",				"com.formdev.flatlaf.intellijthemes.FlatDarkPurpleIJTheme"));
			THEMES.add(new Theme("Dracula",					"com.formdev.flatlaf.intellijthemes.FlatDraculaIJTheme"));
		//	THEMES.add(new Theme("Dracula2",				"com.formdev.flatlaf.intellijthemes.materialthemeuilite.FlatDraculaIJTheme"));
		//	THEMES.add(new Theme("Dracula Contrast",		"com.formdev.flatlaf.intellijthemes.materialthemeuilite.FlatDraculaContrastIJTheme"));
			THEMES.add(new Theme("GitHub",					"com.formdev.flatlaf.intellijthemes.materialthemeuilite.FlatGitHubIJTheme"));
		//	THEMES.add(new Theme("GitHub Contrast",			"com.formdev.flatlaf.intellijthemes.materialthemeuilite.FlatGitHubContrastIJTheme"));
			THEMES.add(new Theme("Gradianto Dark Fuchsia",	"com.formdev.flatlaf.intellijthemes.FlatGradiantoDarkFuchsiaIJTheme"));
			THEMES.add(new Theme("Gradianto Deep Ocean",	"com.formdev.flatlaf.intellijthemes.FlatGradiantoDeepOceanIJTheme"));
			THEMES.add(new Theme("Gradianto Midnight Blue",	"com.formdev.flatlaf.intellijthemes.FlatGradiantoMidnightBlueIJTheme"));
			THEMES.add(new Theme("Gray",					"com.formdev.flatlaf.intellijthemes.FlatGrayIJTheme"));
			THEMES.add(new Theme("Gruvbox Dark Hard",		"com.formdev.flatlaf.intellijthemes.FlatGruvboxDarkHardIJTheme"));
			THEMES.add(new Theme("Gruvbox Dark Medium",		"com.formdev.flatlaf.intellijthemes.FlatGruvboxDarkMediumIJTheme"));
			THEMES.add(new Theme("Gruvbox Dark Soft",		"com.formdev.flatlaf.intellijthemes.FlatGruvboxDarkSoftIJTheme"));
			THEMES.add(new Theme("Hiberbee Dark",			"com.formdev.flatlaf.intellijthemes.FlatHiberbeeDarkIJTheme"));
			THEMES.add(new Theme("High contrast",			"com.formdev.flatlaf.intellijthemes.FlatHighContrastIJTheme"));
			THEMES.add(new Theme("Light Flat",				"com.formdev.flatlaf.intellijthemes.FlatLightFlatIJTheme"));
			THEMES.add(new Theme("Light Owl",				"com.formdev.flatlaf.intellijthemes.materialthemeuilite.FlatLightOwlIJTheme"));
		//	THEMES.add(new Theme("Light Owl Contrast",		"com.formdev.flatlaf.intellijthemes.materialthemeuilite.FlatLightOwlContrastIJTheme"));
			THEMES.add(new Theme("Material Design Dark",	"com.formdev.flatlaf.intellijthemes.FlatMaterialDesignDarkIJTheme"));
			THEMES.add(new Theme("Monocai",					"com.formdev.flatlaf.intellijthemes.FlatMonocaiIJTheme"));
			THEMES.add(new Theme("Nord",					"com.formdev.flatlaf.intellijthemes.FlatNordIJTheme"));
			THEMES.add(new Theme("Night Owl",				"com.formdev.flatlaf.intellijthemes.materialthemeuilite.FlatNightOwlIJTheme"));
			THEMES.add(new Theme("Night Owl Contrast",		"com.formdev.flatlaf.intellijthemes.materialthemeuilite.FlatNightOwlContrastIJTheme"));
			THEMES.add(new Theme("One Dark",				"com.formdev.flatlaf.intellijthemes.FlatOneDarkIJTheme"));
			THEMES.add(new Theme("Solarized Dark",			"com.formdev.flatlaf.intellijthemes.FlatSolarizedDarkIJTheme"));
			THEMES.add(new Theme("Solarized Light",			"com.formdev.flatlaf.intellijthemes.FlatSolarizedLightIJTheme"));
			THEMES.add(new Theme("Spacegray",				"com.formdev.flatlaf.intellijthemes.FlatSpacegrayIJTheme"));
			THEMES.add(new Theme("Vuesion",					"com.formdev.flatlaf.intellijthemes.FlatVuesionIJTheme"));
			/*
			THEMES.add(new Theme("Material Darker","com.formdev.flatlaf.intellijthemes.materialthemeuilite.FlatMaterialDarkerIJTheme"));
			THEMES.add(new Theme("Material Darker Contrast","com.formdev.flatlaf.intellijthemes.materialthemeuilite.FlatMaterialDarkerContrastIJTheme"));
			THEMES.add(new Theme("Material Deep Ocean","com.formdev.flatlaf.intellijthemes.materialthemeuilite.FlatMaterialDeepOceanIJTheme"));
			THEMES.add(new Theme("Material Deep Ocean Contrast","com.formdev.flatlaf.intellijthemes.materialthemeuilite.FlatMaterialDeepOceanContrastIJTheme"));
			THEMES.add(new Theme("Material Lighter","com.formdev.flatlaf.intellijthemes.materialthemeuilite.FlatMaterialLighterIJTheme"));
			THEMES.add(new Theme("Material Lighter Contrast","com.formdev.flatlaf.intellijthemes.materialthemeuilite.FlatMaterialLighterContrastIJTheme"));
			THEMES.add(new Theme("Material Oceanic","com.formdev.flatlaf.intellijthemes.materialthemeuilite.FlatMaterialOceanicIJTheme"));
			THEMES.add(new Theme("Material Oceanic Contrast","com.formdev.flatlaf.intellijthemes.materialthemeuilite.FlatMaterialOceanicContrastIJTheme"));
			THEMES.add(new Theme("Material Palenight","com.formdev.flatlaf.intellijthemes.materialthemeuilite.FlatMaterialPalenightIJTheme"));
			THEMES.add(new Theme("Material Palenight Contrast","com.formdev.flatlaf.intellijthemes.materialthemeuilite.FlatMaterialPalenightContrastIJTheme"));
			THEMES.add(new Theme("Monokai Pro","com.formdev.flatlaf.intellijthemes.materialthemeuilite.FlatMonokaiProIJTheme"));
			THEMES.add(new Theme("Monokai Pro Contrast","com.formdev.flatlaf.intellijthemes.materialthemeuilite.FlatMonokaiProContrastIJTheme"));
			THEMES.add(new Theme("Solarized Dark","com.formdev.flatlaf.intellijthemes.materialthemeuilite.FlatSolarizedDarkIJTheme"));
			THEMES.add(new Theme("Solarized Dark Contrast","com.formdev.flatlaf.intellijthemes.materialthemeuilite.FlatSolarizedDarkContrastIJTheme"));
			THEMES.add(new Theme("Solarized Light","com.formdev.flatlaf.intellijthemes.materialthemeuilite.FlatSolarizedLightIJTheme"));
			THEMES.add(new Theme("Solarized Light Contrast","com.formdev.flatlaf.intellijthemes.materialthemeuilite.FlatSolarizedLightContrastIJTheme"));
			*/
			// @formatter:on

			try {
				for (File f : IOUtils.getFilesWithExtension(Directories.DATABASE_THEMES, "theme.json", true)) {
					String name = f.getName().substring(0, f.getName().length() - 11);
					THEMES.add(new Theme(name, f.getAbsolutePath(), true));
				}
			}
			catch (IOException e) {
				Logger.logError("IOException while loading custom themes: " + e.getMessage());
			}

			Logger.log("Loaded " + THEMES.size() + " themes.");
		}
	}
}
