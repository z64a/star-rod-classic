package app.config;

import java.awt.Dimension;
import java.awt.Toolkit;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;

import app.StarRodException;
import app.config.Options.ConfigOptionEditor;
import net.miginfocom.swing.MigLayout;
import shared.SwingUtils;

public class BuildOptionsPanel extends JPanel
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

	private void addTextField(JPanel container, Options option, String layout)
	{
		ConfigTextField tf = new ConfigTextField(option);
		optEditors.add(tf);

		tf.setHorizontalAlignment(JTextField.LEFT);
		SwingUtils.addBorderPadding(tf);
		SwingUtils.setFontSize(tf, 12);
		container.add(tf, layout);
	}

	private void addHexField(JPanel container, Options option, String layout)
	{
		ConfigHexField tf = new ConfigHexField(option);
		optEditors.add(tf);

		tf.setHorizontalAlignment(JTextField.LEFT);
		SwingUtils.addBorderPadding(tf);
		SwingUtils.setFontSize(tf, 12);
		container.add(tf, layout);
	}

	public BuildOptionsPanel()
	{
		optEditors = new ArrayList<>();
		setPreferredSize(new Dimension(380, 500));

		JTabbedPane tabbedPane = new JTabbedPane();
		SwingUtils.setFontSize(tabbedPane, 14);

		addTab(tabbedPane, createAssetsTab(), "Assets");
		addTab(tabbedPane, createPropertiesTab(), "Properties");
		addTab(tabbedPane, createOptionsTab(), "Options");
		addTab(tabbedPane, createDebugTab(), "Debug");

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

		addCheckbox(tab, Options.BuildTextures, "growx");
		addCheckbox(tab, Options.BuildBackgrounds, "growx");
		addCheckbox(tab, Options.BuildSpriteSheets, "growx");
		addCheckbox(tab, Options.PatchFonts, "growx");
		addCheckbox(tab, Options.BuildAudio, "growx, gapbottom 8");

		addCheckbox(tab, Options.ClearMapCache, "growx");
		addCheckbox(tab, Options.ClearSpriteCache, "growx");
		addCheckbox(tab, Options.ClearTextureCache, "growx");
		addCheckbox(tab, Options.CaptureThumbnails, "growx");

		return tab;
	}

	private JPanel createPropertiesTab()
	{
		JPanel tab = new JPanel(new MigLayout("fillx, " + TAB_INSETS, "[]16[]"));

		tab.add(SwingUtils.getLabel("Mod Name", 12));
		addTextField(tab, Options.ModVersionString, "growx, pushx, wrap");

		tab.add(SwingUtils.getLabel("Initial Map", 12));
		addTextField(tab, Options.InitialMap, "growx, pushx, wrap");

		tab.add(SwingUtils.getLabel("Initial Entry", 12));
		addTextField(tab, Options.InitialEntry, "growx, pushx, wrap");

		tab.add(new JPanel(), "span, wrap");

		tab.add(SwingUtils.getLabel("World Heap Size", 12));
		addHexField(tab, Options.HeapSizeWorld, "growx, pushx, wrap");

		tab.add(SwingUtils.getLabel("Battle Heap Size", 12));
		addHexField(tab, Options.HeapSizeBattle, "growx, pushx, wrap");

		tab.add(SwingUtils.getLabel("Collision Heap Size", 12));
		addHexField(tab, Options.HeapSizeCollision, "growx, pushx, wrap");

		tab.add(SwingUtils.getLabel("Sprite Heap Size", 12));
		addHexField(tab, Options.HeapSizeSprite, "growx, pushx, wrap");

		tab.add(SwingUtils.getLabel("Audio Heap Size", 12));
		addHexField(tab, Options.HeapSizeAudio, "growx, pushx, wrap");

		return tab;
	}

	private JPanel createOptionsTab()
	{
		JPanel tab = new JPanel(new MigLayout("wrap, fillx, " + TAB_INSETS));

		addCheckbox(tab, Options.Allow10Partners, "growx");
		addCheckbox(tab, Options.IncreaseHeapSizes, "growx");
		addCheckbox(tab, Options.CompressModPackage, "growx");
		addCheckbox(tab, Options.CompressBattleData, "growx");
		addCheckbox(tab, Options.PackScriptOpcodes, "growx");

		addCheckbox(tab, Options.ClearJapaneseStrings, "growx");
		addCheckbox(tab, Options.SkipIntroLogos, "growx");
		addCheckbox(tab, Options.DisableDemoReel, "growx");
		addCheckbox(tab, Options.DisableIntroStory, "growx");

		addCheckbox(tab, Options.AllowWriteConflicts, "growx");
		addCheckbox(tab, Options.AllowDuplicateSpriteNames, "growx");

		return tab;
	}

	private JPanel createDebugTab()
	{
		JPanel tab = new JPanel(new MigLayout("fillx, " + TAB_INSETS, "[]16[]"));

		addCheckbox(tab, Options.EnableDebugCode, "growx, span, wrap, gapbottom 4");
		addCheckbox(tab, Options.EnableVarLogging, "growx, span, wrap, gapbottom 4");

		addCheckbox(tab, Options.QuickLaunch, "growx, wrap, gapbottom 8");

		tab.add(SwingUtils.getLabel("Debug Battle", 12));
		addTextField(tab, Options.DebugBattleID, "growx, pushx, wrap, gapbottom 8");

		tab.add(SwingUtils.getLabel("Watch List 0", 12));
		addWatchListField(tab, Options.DebugWatch0);
		tab.add(SwingUtils.getLabel("Watch List 1", 12));
		addWatchListField(tab, Options.DebugWatch1);
		tab.add(SwingUtils.getLabel("Watch List 2", 12));
		addWatchListField(tab, Options.DebugWatch2);
		tab.add(SwingUtils.getLabel("Watch List 3", 12));
		addWatchListField(tab, Options.DebugWatch3);
		tab.add(SwingUtils.getLabel("Watch List 4", 12));
		addWatchListField(tab, Options.DebugWatch4);
		tab.add(SwingUtils.getLabel("Watch List 5", 12));
		addWatchListField(tab, Options.DebugWatch5);
		tab.add(SwingUtils.getLabel("Watch List 6", 12));
		addWatchListField(tab, Options.DebugWatch6);
		tab.add(SwingUtils.getLabel("Watch List 7", 12));
		addWatchListField(tab, Options.DebugWatch7);

		return tab;
	}

	private void addWatchListField(JPanel tab, Options option)
	{
		ConfigTextField tf = new ConfigTextField(option);
		optEditors.add(tf);
		tf.setEditable(false);

		JButton editButton = new JButton("Edit");

		tf.setHorizontalAlignment(JTextField.LEFT);
		SwingUtils.addBorderPadding(tf);
		SwingUtils.setFontSize(tf, 12);
		tab.add(tf, "split 2, growx, pushx");
		tab.add(editButton, "growy, sgx but, wrap");

		editButton.addActionListener((cmd) -> {
			WatchListPanel panel = new WatchListPanel(tf.getText());

			int choice = SwingUtils.getConfirmDialog()
				.setTitle("Watch List Entry")
				.setMessage(panel)
				.setOptionsType(JOptionPane.OK_CANCEL_OPTION)
				.setMessageType(JOptionPane.QUESTION_MESSAGE)
				.choose();

			if (choice == JOptionPane.YES_OPTION) {
				try {
					tf.setText(panel.getText());
					repaint();
				}
				catch (StarRodException e) {
					Toolkit.getDefaultToolkit().beep();
					SwingUtils.getErrorDialog()
						.setParent(tab)
						.setTitle("Invalid Entry")
						.setMessage(e.getMessage())
						.show();
				}
			}
		});
	}
}
