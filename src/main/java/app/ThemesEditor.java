package app;

import java.awt.Component;
import java.awt.Dialog.ModalityType;
import java.awt.Dimension;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import javax.swing.AbstractAction;
import javax.swing.ButtonGroup;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.JTree;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;

import app.Themes.Theme;
import app.config.Options;
import game.shared.ProjectDatabase;
import game.shared.ProjectDatabase.ConstEnum.EnumPair;
import net.miginfocom.swing.MigLayout;

public class ThemesEditor
{
	public static final int WINDOW_SIZE_X = 640;
	public static final int WINDOW_SIZE_Y = 600;

	private StarRodFrame frame;
	public boolean exitToMainMenu;

	public Theme initialTheme;

	private JLabel lblR;
	private JLabel lblG;
	private JLabel lblB;
	private static ThemeDialog themeDialog;

	public static void main(String[] args) throws InterruptedException
	{
		Environment.initialize();

		CountDownLatch guiClosedSignal = new CountDownLatch(1);
		new ThemesEditor(guiClosedSignal);
		guiClosedSignal.await();

		Environment.exit();
	}

	public static void showThemeDialog(Component parent)
	{
		if (themeDialog != null && themeDialog.isDisplayable())
			themeDialog.cancel();

		themeDialog = new ThemeDialog(parent);
		themeDialog.setVisible(true);
	}

	public static void addThemeMenuItem(JMenuBar menuBar, Component parent)
	{
		if (menuBar == null)
			return;

		JMenu editorMenu = null;
		for (int i = 0; i < menuBar.getMenuCount(); i++) {
			JMenu menu = menuBar.getMenu(i);
			if (menu != null && "Editor".equals(menu.getText().trim())) {
				editorMenu = menu;
				break;
			}
		}

		if (editorMenu == null) {
			editorMenu = new JMenu("Editor");
			editorMenu.getPopupMenu().setLightWeightPopupEnabled(false);
			menuBar.add(editorMenu);
		}

		addThemeMenuItem(editorMenu, parent);
	}

	public static void addThemeMenuItem(JMenu editorMenu, Component parent)
	{
		boolean hasChangeTheme = false;
		boolean hasDesignTheme = false;
		for (int i = 0; i < editorMenu.getItemCount(); i++) {
			JMenuItem item = editorMenu.getItem(i);
			if (item != null && "Change Theme".equals(item.getText()))
				hasChangeTheme = true;
			if (item != null && "Design Theme".equals(item.getText()))
				hasDesignTheme = true;
		}

		if (hasChangeTheme && hasDesignTheme)
			return;
		if (!hasChangeTheme && !hasDesignTheme && editorMenu.getItemCount() > 0)
			editorMenu.addSeparator();

		if (!hasChangeTheme) {
			JMenuItem item = new JMenuItem("Change Theme");
			item.addActionListener(e -> showThemeDialog(parent));
			editorMenu.add(item);
		}

		if (!hasDesignTheme) {
			JMenuItem item = new JMenuItem("Design Theme");
			item.addActionListener(e -> ThemeDesignerDialog.showDesigner(parent));
			editorMenu.add(item);
		}
	}

	private static class ThemeDialog extends JDialog
	{
		private final Theme initialTheme;
		private final JList<Theme> themeList;
		private boolean keepTheme;

		private ThemeDialog(Component parent)
		{
			super((parent instanceof Window) ? (Window) parent : SwingUtilities.getWindowAncestor(parent), "Change Theme", ModalityType.MODELESS);

			initialTheme = Themes.getCurrentTheme();
			DefaultListModel<Theme> themes = new DefaultListModel<>();
			for (Theme theme : Themes.getThemes())
				themes.addElement(theme);

			themeList = new JList<>(themes);
			themeList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
			themeList.setSelectedValue(initialTheme, true);
			themeList.addListSelectionListener(e -> {
				if (!e.getValueIsAdjusting())
					Themes.setTheme(themeList.getSelectedValue());
			});

			JScrollPane scrollPane = new JScrollPane(themeList);
			scrollPane.setPreferredSize(new Dimension(320, 420));

			JButton cancelButton = new JButton("Cancel");
			cancelButton.addActionListener(e -> cancel());

			JButton okButton = new JButton("OK");
			okButton.addActionListener(e -> accept());

			JPanel buttonPanel = new JPanel(new MigLayout("ins 0", "push[][]"));
			buttonPanel.add(okButton);
			buttonPanel.add(cancelButton);

			JPanel contentPanel = new JPanel(new MigLayout("fill, wrap, ins 12"));
			contentPanel.add(scrollPane, "grow, push");
			contentPanel.add(buttonPanel, "growx");
			setContentPane(contentPanel);

			setIconImage(Environment.getDefaultIconImage());
			setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
			addWindowListener(new WindowAdapter() {
				@Override
				public void windowClosing(WindowEvent e)
				{
					cancel();
				}

				@Override
				public void windowClosed(WindowEvent e)
				{
					if (!keepTheme)
						Themes.setTheme(initialTheme);
					if (themeDialog == ThemeDialog.this)
						themeDialog = null;
				}
			});

			getRootPane().setDefaultButton(okButton);
			getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "cancel");
			getRootPane().getActionMap().put("cancel", new AbstractAction() {
				@Override
				public void actionPerformed(ActionEvent e)
				{
					cancel();
				}
			});

			pack();
			setLocationRelativeTo(parent);
		}

		private void cancel()
		{
			Themes.setTheme(initialTheme);
			keepTheme = true;
			dispose();
		}

		private void accept()
		{
			if (Themes.getCurrentTheme() != null) {
				Environment.mainConfig.setString(Options.Theme, Themes.getCurrentTheme().key);
				Environment.mainConfig.saveConfigFile();
			}

			keepTheme = true;
			dispose();
		}
	}

	public ThemesEditor(CountDownLatch guiClosedSignal)
	{
		initialTheme = Themes.getCurrentTheme();

		frame = new StarRodFrame();

		frame.setTitle(Environment.decorateTitle("Choose Theme"));

		frame.setBounds(0, 0, WINDOW_SIZE_X, WINDOW_SIZE_Y);
		frame.setLocationRelativeTo(null);

		frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
		frame.addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent e)
			{
				exitToMainMenu = true;
				Theme currentTheme = Themes.getCurrentTheme();
				if (initialTheme != currentTheme) {
					int choice = SwingUtils.getConfirmDialog()
						.setTitle("Save Changes")
						.setMessage("Theme has been changed.", "Do you want to save changes?")
						.choose();

					switch (choice) {
						case JOptionPane.YES_OPTION:
							if (Themes.getCurrentTheme() != null) {
								Environment.mainConfig.setString(Options.Theme, Themes.getCurrentTheme().key);
								Environment.mainConfig.saveConfigFile();
							}
							break;
						case JOptionPane.NO_OPTION:
							Themes.setTheme(initialTheme);
							break;
						case JOptionPane.CANCEL_OPTION:
						case JOptionPane.CLOSED_OPTION:
							return;
					}
				}

				guiClosedSignal.countDown();
				frame.dispose();
			}
		});

		frame.setLayout(new MigLayout("fill"));
		frame.add(getThemesPanel(), "w 240!, growy");
		frame.add(getPreviewPanel(), "span, grow, push");

		frame.setVisible(true);
	}

	private JPanel getThemesPanel()
	{
		DefaultListModel<Theme> themes = new DefaultListModel<>();

		for (Theme theme : Themes.getThemes())
			themes.addElement(theme);

		JList<Theme> themeList = new JList<>(themes);
		themeList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

		themeList.setSelectedValue(Themes.getCurrentTheme(), true);

		themeList.addListSelectionListener(e -> SwingUtilities.invokeLater(() -> {
			Themes.setTheme(themeList.getSelectedValue());
			lblR.setForeground(SwingUtils.getRedTextColor());
			lblG.setForeground(SwingUtils.getGreenTextColor());
			lblB.setForeground(SwingUtils.getBlueTextColor());
		}));

		JPanel panel = new JPanel(new MigLayout("fill, wrap, ins 16"));
		panel.add(SwingUtils.getLabel("Choose a Theme:", 14));
		panel.add(new JScrollPane(themeList), "grow, pushy");
		return panel;
	}

	private JPanel getPreviewPanel()
	{
		JPanel panel = new JPanel(new MigLayout("fill, wrap, ins 16"));

		JTextField exampleField = new JTextField("input");
		SwingUtils.addBorderPadding(exampleField);

		panel.add(new JLabel("Input Field:"), "sg lbl, w 80!, split 3");
		panel.add(exampleField, "w 160!");
		panel.add(new JLabel(), "growx, pushx");

		JComboBox<String> comboBox = new JComboBox<>();
		comboBox.addItem("First");
		comboBox.addItem("Second");
		comboBox.addItem("Third");

		panel.add(new JLabel("Combo Box:"), "sg lbl, split 2");
		panel.add(comboBox, "w 160!");

		panel.add(new JLabel(""), "sg lbl, split 2");
		panel.add(new JCheckBox("Check Box"), "gaptop 8");

		JCheckBox checkedBox = new JCheckBox("Another Check Box");
		checkedBox.setSelected(true);

		panel.add(new JLabel(""), "sg lbl, split 2");
		panel.add(checkedBox);

		JPanel tabPanel = new JPanel(new MigLayout("fill, wrap"));
		JTabbedPane tabs = new JTabbedPane();
		tabs.addTab("Tab  ", tabPanel);
		panel.add(tabs, "span, grow, push");

		JRadioButton but1 = new JRadioButton("Option A");
		JRadioButton but2 = new JRadioButton("Option B");
		JRadioButton but3 = new JRadioButton("Option C");
		ButtonGroup grp = new ButtonGroup();
		grp.add(but1);
		grp.add(but2);
		grp.add(but3);
		but3.setSelected(true);

		tabPanel.add(but1, "growx, sg radio, split 3");
		tabPanel.add(but2, "growx, sg radio");
		tabPanel.add(but3, "growx, sg radio");

		JSlider slider = new JSlider(0, 100, 25);
		slider.setMajorTickSpacing(0);
		slider.setMinorTickSpacing(0);
		slider.setPaintTicks(true);

		tabPanel.add(slider, "grow");

		lblR = SwingUtils.getLabel("<html><b>Red Text</b></html>", SwingConstants.CENTER, 12);
		lblG = SwingUtils.getLabel("<html><b>Green Text</b></html>", SwingConstants.CENTER, 12);
		lblB = SwingUtils.getLabel("<html><b>Blue Text</b></html>", SwingConstants.CENTER, 12);
		lblR.setForeground(SwingUtils.getRedTextColor());
		lblG.setForeground(SwingUtils.getGreenTextColor());
		lblB.setForeground(SwingUtils.getBlueTextColor());
		tabPanel.add(lblR, "growx, sg font, split 3");
		tabPanel.add(lblG, "growx, sg font");
		tabPanel.add(lblB, "growx, sg font");

		JButton disabledButton = new JButton("Button 3");
		disabledButton.setEnabled(false);
		tabPanel.add(new JButton("Button 1"), "growx, split 3, gaptop 8");
		tabPanel.add(new JButton("Button 2"), "growx");
		tabPanel.add(disabledButton, "growx");

		DefaultListModel<String> listModel = new DefaultListModel<>();
		List<EnumPair> locations = ProjectDatabase.LocationType.getDecoding();
		for (EnumPair pair : locations)
			listModel.addElement(pair.value);
		JList<String> list = new JList<>(listModel);
		SwingUtils.addBorderPadding(list);

		DefaultMutableTreeNode root = new DefaultMutableTreeNode("Root");
		DefaultTreeModel treeModel = new DefaultTreeModel(root);

		DefaultMutableTreeNode elementsNode = new DefaultMutableTreeNode("Elements");
		root.add(elementsNode);
		List<EnumPair> elements = ProjectDatabase.ElementType.getDecoding();
		for (EnumPair pair : elements)
			elementsNode.add(new DefaultMutableTreeNode(pair.value));

		DefaultMutableTreeNode abilitiesNode = new DefaultMutableTreeNode("Abilities");
		root.add(abilitiesNode);
		List<EnumPair> abilities = ProjectDatabase.AbilityType.getDecoding();
		for (EnumPair pair : abilities)
			abilitiesNode.add(new DefaultMutableTreeNode(pair.value));

		DefaultMutableTreeNode triggersNode = new DefaultMutableTreeNode("Triggers");
		root.add(triggersNode);
		List<EnumPair> triggers = ProjectDatabase.TriggerType.getDecoding();
		for (EnumPair pair : triggers)
			triggersNode.add(new DefaultMutableTreeNode(pair.value));

		JTree tree = new JTree(treeModel);
		tree.setRootVisible(false);
		tree.expandRow(1);
		SwingUtils.addBorderPadding(tree);

		tabPanel.add(SwingUtils.getLabel("Sample List", 14), "gaptop 8, split 2, w 50%");
		tabPanel.add(SwingUtils.getLabel("Sample Tree", 14), "w 50%");
		tabPanel.add(new JScrollPane(list), "split 2, w 50%, growy");
		tabPanel.add(new JScrollPane(tree), "w 50%, growy");

		tabPanel.add(new JLabel(), "span, growy, pushy");
		return panel;
	}
}
