package app.config;

import java.awt.Dimension;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;

import app.config.Options.ConfigOptionEditor;
import net.miginfocom.swing.MigLayout;
import shared.SwingUtils;

public class DumpOptionsPanel extends JPanel
{
	private final List<ConfigOptionEditor> optEditors;

	public void setValues(Config cfg)
	{
		for (ConfigOptionEditor editor : optEditors)
			editor.read(cfg);
	}

	public void getValues(Config cfg)
	{
		for (ConfigOptionEditor editor : optEditors)
			editor.write(cfg);
	}

	private void addCheckbox(JPanel container, Options option, String layout)
	{
		ConfigCheckBox cb = new ConfigCheckBox(option);
		optEditors.add(cb);

		SwingUtils.setFontSize(cb, 12);
		container.add(cb, layout);
	}

	public DumpOptionsPanel()
	{
		optEditors = new ArrayList<>();
		setPreferredSize(new Dimension(380, 400));

		JTabbedPane tabbedPane = new JTabbedPane();
		SwingUtils.setFontSize(tabbedPane, 14);

		addTab(tabbedPane, createAssetsTab(), "Assets");
		addTab(tabbedPane, createOptionsTab(), "Options");
		//addTab(tabbedPane, createDebugTab(), "Formatting");

		//	tabbedPane.addTab("Assets", createAssetsTab());
		//	tabbedPane.addTab("Properties", createPropertiesTab());
		//	tabbedPane.addTab("Options", createOptionsTab());
		//	tabbedPane.addTab("Debug", createDebugTab());

		setLayout(new MigLayout("fill, ins 0"));
		add(tabbedPane, "grow");
	}

	private void addTab(JTabbedPane tabbedPane, JPanel tab, String name)
	{
		JLabel tabLabel = new JLabel(name);
		tabLabel.setPreferredSize(new Dimension(60, 18));
		SwingUtils.setFontSize(tabLabel, 12);

		int index = tabbedPane.getTabCount();
		tabbedPane.addTab(null, tab);
		tabbedPane.setTabComponentAt(index, tabLabel);
	}

	private static final String TAB_INSETS = "ins 20 10 10 10";

	private JPanel createAssetsTab()
	{
		JPanel tab = new JPanel(new MigLayout("wrap, fillx, " + TAB_INSETS));

		addCheckbox(tab, Options.CleanDump, "growx, gapbottom 16");
		addCheckbox(tab, Options.DumpMessages, "growx");
		addCheckbox(tab, Options.DumpTables, "growx");
		addCheckbox(tab, Options.DumpMaps, "growx");
		addCheckbox(tab, Options.DumpWorld, "growx");
		addCheckbox(tab, Options.DumpBattles, "growx");
		addCheckbox(tab, Options.DumpMoves, "growx");
		addCheckbox(tab, Options.DumpPartners, "growx");
		addCheckbox(tab, Options.DumpTextures, "growx");
		addCheckbox(tab, Options.DumpSprites, "growx");
		addCheckbox(tab, Options.DumpAudio, "growx");
		addCheckbox(tab, Options.DumpLibrary, "growx");

		return tab;
	}

	private JPanel createOptionsTab()
	{
		JPanel tab = new JPanel(new MigLayout("wrap, fillx, " + TAB_INSETS));

		addCheckbox(tab, Options.DumpReports, "growx");
		addCheckbox(tab, Options.RecompressMaps, "growx, gapbottom 16");

		addCheckbox(tab, Options.UseTabIndents, "growx");
		addCheckbox(tab, Options.UseTabSpacing, "growx");
		addCheckbox(tab, Options.IndentPrintedData, "growx");
		addCheckbox(tab, Options.NewlineOpenBrace, "growx, gapbottom 16");

		addCheckbox(tab, Options.PrintLineOffsets, "growx");
		addCheckbox(tab, Options.PrintRequiredBy, "growx, gapbottom 16");

		addCheckbox(tab, Options.RoundFixedVars, "growx");
		addCheckbox(tab, Options.UseShorthandVars, "growx");
		addCheckbox(tab, Options.TrackScriptVarTypes, "growx");
		addCheckbox(tab, Options.GenerateNpcIDs, "growx");

		return tab;
	}
}
