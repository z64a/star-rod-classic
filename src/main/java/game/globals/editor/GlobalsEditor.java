package game.globals.editor;

import static app.Directories.MOD_STRINGS_PATCH;
import static app.Directories.MOD_STRINGS_SRC;

import java.awt.Dimension;
import java.awt.Image;
import java.awt.Toolkit;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.concurrent.CountDownLatch;

import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;

import app.Directories;
import app.Environment;
import app.LoadingBar;
import app.StarRodException;
import app.SwingUtils;
import app.config.Options;
import app.input.IOUtils;
import game.globals.editor.GlobalsData.GlobalsCategory;
import game.globals.editor.tabs.HudElementTab;
import game.globals.editor.tabs.ImageAssetTab;
import game.globals.editor.tabs.ItemEntityTab;
import game.globals.editor.tabs.ItemTab;
import game.globals.editor.tabs.MoveTab;
import game.string.PMString;
import game.string.editor.io.StringResource;
import net.miginfocom.swing.MigLayout;
import util.IterableListModel;
import util.Logger;

public class GlobalsEditor
{
	private static final String MENU_BAR_SPACING = "    ";
	public static final int WINDOW_SIZE_X = 800;
	public static final int WINDOW_SIZE_Y = 800;

	private JFrame frame;
	public boolean exitToMainMenu;

	private JTabbedPane tabbedPane;
	private ArrayList<GlobalEditorTab> tabList;
	private int selectedTabIndex;

	public final GlobalsData data;
	public final IterableListModel<PMString> messageListModel;
	public final HashMap<String, PMString> messageNameMap;
	public final HashMap<Integer, PMString> messageIDMap;

	public static abstract class GlobalEditorTab extends JPanel
	{
		public final GlobalsEditor editor;
		public final int tabIndex;
		private JLabel tabLabel;
		private boolean modified;

		protected GlobalEditorTab(GlobalsEditor editor, int tabIndex)
		{
			this.editor = editor;
			this.tabIndex = tabIndex;
		}

		protected void setModified()
		{
			modified = true;
			tabLabel.setText(getTabName() + " *");
		}

		protected void clearModified()
		{
			modified = false;
			tabLabel.setText(getTabName());
		}

		protected abstract String getTabName();

		protected abstract String getIconPath();

		protected abstract GlobalsCategory getDataType();

		protected abstract void notifyDataChange(GlobalsCategory type);

		protected void onDeselectTab()
		{}

		protected void onSelectTab()
		{}
	}

	public static void main(String[] args) throws InterruptedException
	{
		Environment.initialize();

		CountDownLatch guiClosedSignal = new CountDownLatch(1);
		new GlobalsEditor(guiClosedSignal);
		guiClosedSignal.await();

		Environment.exit();
	}

	public GlobalsEditor(CountDownLatch guiClosedSignal)
	{
		LoadingBar.show("Please Wait");

		tabList = new ArrayList<>();

		messageListModel = new IterableListModel<>();
		messageNameMap = new HashMap<>();
		messageIDMap = new HashMap<>();
		loadStrings();

		data = new GlobalsData();
		data.loadDataFlexible(true);
		data.loadAssets();

		for (GlobalsCategory type : GlobalsCategory.values()) {
			for (GlobalEditorTab tab : tabList)
				tab.notifyDataChange(type);
		}

		createGUI(guiClosedSignal);

		LoadingBar.dismiss();
		frame.setVisible(true);
	}

	private boolean currentTabHasChanges()
	{
		GlobalEditorTab tab = (GlobalEditorTab) tabbedPane.getSelectedComponent();
		return tab.modified;
	}

	private void reloadCurrentTab()
	{
		GlobalEditorTab current = (GlobalEditorTab) tabbedPane.getSelectedComponent();
		GlobalsCategory category = current.getDataType();
		data.loadDataStrict(true);
		data.loadAssets();

		for (GlobalEditorTab tab : tabList)
			tab.notifyDataChange(category);

		current.clearModified();
	}

	private boolean tabsHaveChanges()
	{
		boolean modified = false;
		for (GlobalEditorTab tab : tabList)
			modified = modified || tab.modified;
		return modified;
	}

	private void saveAllChanges()
	{
		for (GlobalEditorTab tab : tabList) {
			if (tab.modified)
				tab.clearModified();
		}
		data.saveAllData();
	}

	private void createGUI(CountDownLatch guiClosedSignal)
	{
		frame = new JFrame();

		frame.setTitle(Environment.decorateTitle("Globals Editor"));
		frame.setIconImage(Environment.getDefaultIconImage());

		frame.setBounds(0, 0, WINDOW_SIZE_X, WINDOW_SIZE_Y);
		frame.setLocationRelativeTo(null);

		frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
		frame.addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent e)
			{
				int choice = !tabsHaveChanges() ? JOptionPane.NO_OPTION
					: SwingUtils.getWarningDialog()
						.setTitle("Warning")
						.setMessage("Unsaved changes will be lost!", "Would you like to save now?")
						.setOptionsType(JOptionPane.YES_NO_CANCEL_OPTION)
						.choose();

				switch (choice) {
					case JOptionPane.YES_OPTION:
						saveAllChanges();
						break;
					case JOptionPane.NO_OPTION:
						break;
					case JOptionPane.CANCEL_OPTION:
						return;
				}

				guiClosedSignal.countDown();
				frame.dispose();
			}
		});

		tabbedPane = new JTabbedPane();

		createTab(new ItemTab(this, tabbedPane.getTabCount()));
		createTab(new MoveTab(this, tabbedPane.getTabCount()));
		createTab(new ImageAssetTab(this, tabbedPane.getTabCount()));
		createTab(new ItemEntityTab(this, tabbedPane.getTabCount()));
		createTab(new HudElementTab(this, tabbedPane.getTabCount()));

		tabbedPane.addChangeListener((e) -> {
			if (selectedTabIndex >= 0)
				tabList.get(selectedTabIndex).onDeselectTab();

			selectedTabIndex = tabbedPane.getSelectedIndex();
			tabList.get(selectedTabIndex).onSelectTab();
		});

		// initial selection
		tabList.get(0).onSelectTab();

		SwingUtils.setFontSize(tabbedPane, 14);

		frame.setLayout(new MigLayout("fill"));
		frame.add(tabbedPane, "grow");

		JMenuBar menuBar = new JMenuBar();
		frame.setJMenuBar(menuBar);

		addActionsMenu(menuBar);
	}

	private void addActionsMenu(JMenuBar menuBar)
	{
		JMenuItem item;

		JMenu menu = new JMenu(MENU_BAR_SPACING + "Actions" + MENU_BAR_SPACING);
		menu.getPopupMenu().setLightWeightPopupEnabled(false);
		menuBar.add(menu);

		item = new JMenuItem("Reload Data");
		item.addActionListener((e) -> {
			int choice = !currentTabHasChanges() ? JOptionPane.OK_OPTION
				: SwingUtils.getWarningDialog()
					.setTitle("Warning")
					.setMessage("Unsaved changes will be lost!", "Would you like to save now?")
					.setOptionsType(JOptionPane.YES_NO_OPTION)
					.choose();

			if (choice == JOptionPane.OK_OPTION)
				reloadCurrentTab();
		});
		menu.add(item);

		menu.addSeparator();

		item = new JMenuItem("Save Changes");
		item.addActionListener((e) -> {
			int choice = !tabsHaveChanges() ? JOptionPane.OK_OPTION
				: SwingUtils.getWarningDialog()
					.setTitle("Warning")
					.setMessage("Are you sure you want to overwrite existing data?")
					.setOptionsType(JOptionPane.YES_NO_OPTION)
					.choose();

			if (choice == JOptionPane.OK_OPTION)
				saveAllChanges();
		});
		menu.add(item);

		menu.addSeparator();

		item = new JMenuItem("Switch Tools");
		item.addActionListener((e) -> {
			exitToMainMenu = true;
			WindowEvent closingEvent = new WindowEvent(frame, WindowEvent.WINDOW_CLOSING);
			Toolkit.getDefaultToolkit().getSystemEventQueue().postEvent(closingEvent);
		});
		menu.add(item);

		item = new JMenuItem("Exit");
		item.addActionListener((e) -> {
			exitToMainMenu = Environment.mainConfig.getBoolean(Options.ExitToMenu);
			WindowEvent closingEvent = new WindowEvent(frame, WindowEvent.WINDOW_CLOSING);
			Toolkit.getDefaultToolkit().getSystemEventQueue().postEvent(closingEvent);
		});
		menu.add(item);
	}

	private void createTab(GlobalEditorTab tab)
	{
		tabList.add(tab);

		tab.tabLabel = new JLabel(tab.getTabName());
		tab.tabLabel.setHorizontalTextPosition(JLabel.TRAILING);
		tab.tabLabel.setIcon(getIcon(tab.getIconPath()));
		tab.tabLabel.setIconTextGap(8);

		if (tab.getTabName().length() > 10)
			tab.tabLabel.setPreferredSize(new Dimension(110, 24));
		else
			tab.tabLabel.setPreferredSize(new Dimension(80, 24));

		tabbedPane.addTab(null, tab);
		tabbedPane.setTabComponentAt(tab.tabIndex, tab.tabLabel);
	}

	private static final ImageIcon getIcon(String iconName)
	{
		if (Environment.dumpVersion == null)
			return null;

		if (!(new File(Directories.getDumpPath())).exists()) {
			Logger.log("Dump directory could not be found.");
			return null;
		}

		File f = new File(Directories.DUMP_IMG_ASSETS + iconName + ".png");
		ImageIcon imageIcon;

		try {
			imageIcon = new ImageIcon(ImageIO.read(f));
		}
		catch (IOException e) {
			System.err.println("Exception while reading icon: " + iconName);
			return null;
		}

		int height = 24;
		float aspectRatio = imageIcon.getIconWidth() / imageIcon.getIconHeight();

		if (imageIcon.getIconHeight() <= height)
			return imageIcon;

		Image image = imageIcon.getImage().getScaledInstance(Math.round(aspectRatio * height), height, java.awt.Image.SCALE_SMOOTH);
		return new ImageIcon(image);
	}

	private void loadStrings()
	{
		messageNameMap.clear();
		messageIDMap.clear();

		try {
			if (Environment.project.isDecomp) {
				for (File assetDir : Environment.project.decompConfig.assetDirectories) {
					File msgDir = new File(assetDir, "msg");
					if (!msgDir.exists())
						continue;

					loadMessages(IOUtils.getFilesWithExtension(assetDir, new String[] { "msg" }, true));
				}
			}
			else {
				loadMessages(IOUtils.getFilesWithExtension(MOD_STRINGS_SRC, new String[] { "str", "msg" }, true));
				loadMessages(IOUtils.getFilesWithExtension(MOD_STRINGS_PATCH, new String[] { "str", "msg" }, true));
			}
		}
		catch (IOException e) {
			throw new StarRodException("Exception while loading strings! %n%s", e.getMessage());
		}

		Logger.logf("Loaded %d strings", messageListModel.getSize());
	}

	private void loadMessages(Collection<File> msgFiles)
	{
		for (File f : msgFiles) {
			StringResource res = new StringResource(f);
			for (PMString str : res.strings) {
				if (str.hasName()) {
					messageNameMap.put(str.name, str);
					messageListModel.addElement(str);
				}
				else if (str.indexed && !str.autoAssign) {
					messageNameMap.put(str.getIDName(), str);
					messageIDMap.put(str.getID(), str);
					messageListModel.addElement(str);
				}
			}
		}
	}

	public PMString getMessage(String msgName)
	{
		if (msgName == null)
			return null;

		if (messageNameMap.containsKey(msgName))
			return messageNameMap.get(msgName);

		if (msgName.matches("[0-9A-Fa-f]{1,4}-[0-9A-Fa-f]{1,4}")) {
			String[] parts = msgName.split("-");
			int group = Integer.parseInt(parts[0], 16);
			int index = Integer.parseInt(parts[1], 16);
			int fullID = (group << 16) | (index & 0xFFFF);
			return messageIDMap.get(fullID);
		}

		if (msgName.matches("[0-9A-Fa-f]{1,8}"))
			return messageIDMap.get((int) Long.parseLong(msgName, 16));

		return null;
	}
}
