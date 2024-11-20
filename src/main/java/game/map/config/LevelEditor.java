package game.map.config;

import static app.Directories.FN_MAP_TABLE;
import static app.Directories.MOD_MAP;

import java.awt.Container;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.util.Stack;
import java.util.concurrent.CountDownLatch;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.DropMode;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTree;
import javax.swing.KeyStroke;
import javax.swing.TransferHandler;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;

import app.Directories;
import app.Environment;
import app.LoadingScreen;
import app.StarRodClassic;
import app.config.Options;
import game.map.config.MapConfigTable.AreaConfig;
import game.map.config.MapConfigTable.MapConfig;
import game.map.config.MapConfigTable.Resource;
import net.miginfocom.swing.MigLayout;
import shared.Globals;
import shared.SwingUtils;
import util.Logger;

public class LevelEditor
{
	private static final String MENU_BAR_SPACING = "    ";
	public static final int WINDOW_SIZE_X = 800;
	public static final int WINDOW_SIZE_Y = 720;

	private JFrame frame;
	public boolean exitToMainMenu;
	private boolean modified;

	private MapConfigTable table;

	// info panel
	private enum DisplayMode
	{
		NONE, AREA, MAP, RESOURCE
	}

	private DisplayMode displayMode;
	private Container infoContainer;
	private AreaInfoPanel areaInfoPanel;
	private MapInfoPanel mapInfoPanel;
	private ResourceInfoPanel resourceInfoPanel;

	// tabs
	private enum TabMode
	{
		MAPS, STAGES, RESOURCES
	}

	private TabMode currentTab;
	private JTabbedPane tabs;
	private boolean ignoreTabChanges = false;

	// trees
	private JTree mapTree;
	private JTree stageTree;
	private JTree resourceTree;

	// current selection
	private DefaultMutableTreeNode currentNode;

	public static void main(String[] args) throws InterruptedException
	{
		Environment.initialize();

		CountDownLatch guiClosedSignal = new CountDownLatch(1);
		new LevelEditor(guiClosedSignal);
		guiClosedSignal.await();

		Environment.exit();
	}

	public LevelEditor(CountDownLatch guiClosedSignal)
	{
		LoadingScreen loadingScreen = new LoadingScreen();

		table = MapConfigTable.readXML(new File(MOD_MAP + FN_MAP_TABLE));
		table.createTreeModels();
		table.validateNames();
		modified = false;

		/*
		table.mapsModel.addTreeModelListener(new TreeModelListener()
		{
			@Override public void treeNodesChanged(TreeModelEvent e)     { treeChanged(); }
			@Override public void treeNodesInserted(TreeModelEvent e)    { treeChanged(); }
			@Override public void treeNodesRemoved(TreeModelEvent e)     { treeChanged(); }
			@Override public void treeStructureChanged(TreeModelEvent e) { treeChanged(); }
		
			private void treeChanged()
			{
				//TODO
				table.recalculateMapIDs();
				setMap(currentMap);
			}
		});
		*/

		createGUI(guiClosedSignal);
		//	mapTree.setSelectionInterval(0, 1);
		setSelectedNode(null);
		currentTab = TabMode.MAPS;

		loadingScreen.dispose();
		frame.setVisible(true);
	}

	private void reload()
	{
		File xmlFile = new File(MOD_MAP + FN_MAP_TABLE);
		table = MapConfigTable.readXML(xmlFile);
		table.createTreeModels();
		table.validateNames();
		modified = false;

		mapTree.setModel(table.mapsModel);
		stageTree.setModel(table.stagesModel);
		resourceTree.setModel(table.resourcesModel);
		setSelectedNode(null);
		currentTab = TabMode.MAPS;
		Logger.logf("Reloaded tables from %s", xmlFile.getName());
	}

	private void save()
	{
		File xmlFile = new File(MOD_MAP + FN_MAP_TABLE);
		table.writeXML(xmlFile);
		Logger.logf("Saved changes to %s", xmlFile.getName());
		modified = false;
	}

	private void createGUI(CountDownLatch guiClosedSignal)
	{
		frame = new JFrame();

		frame.setTitle(Environment.decorateTitle("Level Editor"));
		frame.setIconImage(Globals.getDefaultIconImage());

		frame.setBounds(0, 0, WINDOW_SIZE_X, WINDOW_SIZE_Y);
		frame.setLocationRelativeTo(null);

		frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
		frame.addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent e)
			{
				int choice = !modified ? JOptionPane.NO_OPTION
					: SwingUtils.getWarningDialog()
						.setTitle("Warning")
						.setMessage("Unsaved changes will be lost!", "Would you like to save now?")
						.setOptionsType(JOptionPane.YES_NO_CANCEL_OPTION)
						.choose();

				switch (choice) {
					case JOptionPane.YES_OPTION:
						save();
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

		areaInfoPanel = new AreaInfoPanel(this);
		mapInfoPanel = new MapInfoPanel(this);
		resourceInfoPanel = new ResourceInfoPanel(this);

		JPanel treePanel = createTreePanel();
		infoContainer = new Container();
		infoContainer.setLayout(new MigLayout("fill"));
		infoContainer.add(mapInfoPanel);

		frame.setLayout(new MigLayout("fill"));
		frame.add(treePanel, "w 40%, growy");
		frame.add(infoContainer, "w 60%, growy, gaptop 26");

		JMenuBar menuBar = new JMenuBar();
		frame.setJMenuBar(menuBar);
		addOptionsMenu(menuBar);
		addDirectoriesMenu(menuBar);
	}

	private void addOptionsMenu(JMenuBar menuBar)
	{
		JMenuItem item;

		JMenu menu = new JMenu(MENU_BAR_SPACING + "Options" + MENU_BAR_SPACING);
		menu.getPopupMenu().setLightWeightPopupEnabled(false);
		menuBar.add(menu);

		item = new JMenuItem("Reload All");
		item.addActionListener((e) -> {
			int choice = !modified ? JOptionPane.OK_OPTION
				: SwingUtils.getWarningDialog()
					.setTitle("Warning")
					.setMessage("Any changes will be lost.", "Are you sure you want to reload?")
					.setOptionsType(JOptionPane.YES_NO_OPTION)
					.choose();

			if (choice == JOptionPane.OK_OPTION)
				reload();
		});
		menu.add(item);

		item = new JMenuItem("Save Changes");
		item.addActionListener((e) -> {
			int choice = !modified ? JOptionPane.OK_OPTION
				: SwingUtils.getWarningDialog()
					.setTitle("Warning")
					.setMessage("Are you sure you want to overwrite " + FN_MAP_TABLE + "?")
					.setOptionsType(JOptionPane.YES_NO_OPTION)
					.choose();

			if (choice == JOptionPane.OK_OPTION)
				save();
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

	private void addDirectoriesMenu(JMenuBar menuBar)
	{
		JMenuItem item;

		JMenu menu = new JMenu(MENU_BAR_SPACING + "Open Folder" + MENU_BAR_SPACING);
		menu.getPopupMenu().setLightWeightPopupEnabled(false);
		menuBar.add(menu);

		item = new JMenuItem("Dumped Maps");
		item.addActionListener((e) -> {
			openFolder(Directories.DUMP_MAP_SRC);
		});
		menu.add(item);

		menu.addSeparator();

		item = new JMenuItem("Map Patches");
		item.addActionListener((e) -> {
			openFolder(Directories.MOD_MAP_PATCH);
		});
		menu.add(item);

		item = new JMenuItem("Map Geometry");
		item.addActionListener((e) -> {
			openFolder(Directories.MOD_MAP_SAVE);
		});
		menu.add(item);
	}

	private void openFolder(Directories dir)
	{
		try {
			Desktop.getDesktop().open(dir.toFile());
		}
		catch (IOException e) {
			StarRodClassic.displayStackTrace(e);
		}
	}

	private static MapConfig promptCreateMap(JFrame frame, AreaConfig area, boolean isStage)
	{
		CreateMapPanel panel = new CreateMapPanel(area.name + "_new");

		int choice = SwingUtils.getConfirmDialog()
			.setParent(frame)
			.setTitle("Create Map")
			.setMessage(panel)
			.setOptionsType(JOptionPane.OK_CANCEL_OPTION)
			.setMessageType(JOptionPane.PLAIN_MESSAGE)
			.choose();

		if (choice != JOptionPane.OK_OPTION)
			return null;

		return panel.createConfig(isStage);
	}

	private void setSelectedNode(DefaultMutableTreeNode node)
	{
		currentNode = node;
		updateInfoPanel((node == null) ? null : node.getUserObject());
	}

	private void deleteSelectedNode()
	{
		int choice = JOptionPane.NO_OPTION;
		Object obj = currentNode.getUserObject();

		if (obj instanceof AreaConfig area) {
			choice = SwingUtils.getWarningDialog()
				.setTitle("Warning")
				.setMessage(String.format("%s has %d maps and %d stages.", area.name, area.maps.size(), area.stages.size()),
					"Are you sure you want to delete them?")
				.setOptionsType(JOptionPane.YES_NO_OPTION)
				.choose();

			if (choice == JOptionPane.OK_OPTION) {
				table.removeArea(area);
				modified = true;
			}
		}
		else if (obj instanceof MapConfig map) {
			choice = SwingUtils.getWarningDialog()
				.setTitle("Warning")
				.setMessage("Are you sure you want to delete " + map.name + "?")
				.setOptionsType(JOptionPane.YES_NO_OPTION)
				.choose();

			if (choice == JOptionPane.OK_OPTION) {
				table.removeMap(map);
				modified = true;
			}
		}
		else if (obj instanceof Resource resource) {
			choice = SwingUtils.getWarningDialog()
				.setTitle("Warning")
				.setMessage("Are you sure you want to delete " + resource.name + "?")
				.setOptionsType(JOptionPane.YES_NO_OPTION)
				.choose();

			if (choice == JOptionPane.OK_OPTION) {
				table.removeResource(resource);
				modified = true;
			}
		}

		updateInfoPanel(null);
		currentNode = null;
	}

	private void updateInfoPanel(Object obj)
	{
		infoContainer.removeAll();
		displayMode = DisplayMode.NONE;

		if (obj instanceof AreaConfig) {
			infoContainer.add(areaInfoPanel, "growx, top");
			setArea((AreaConfig) obj);
		}
		else if (obj instanceof MapConfig) {
			infoContainer.add(mapInfoPanel, "growx, top");
			setMap((MapConfig) obj);
		}
		else if (obj instanceof Resource) {
			infoContainer.add(resourceInfoPanel, "growx, top");
			setResource((Resource) obj);
		}

		infoContainer.repaint();
		infoContainer.revalidate();
	}

	private void setArea(AreaConfig area)
	{
		displayMode = DisplayMode.AREA;
		areaInfoPanel.setArea(area);
	}

	private void setMap(MapConfig map)
	{
		displayMode = DisplayMode.MAP;
		mapInfoPanel.setMap(map);
	}

	private void setResource(Resource res)
	{
		displayMode = DisplayMode.RESOURCE;
		resourceInfoPanel.setResource(res);
	}

	private static void createTab(JTabbedPane tabs, String text, Container contents)
	{
		JLabel tabLabel = new JLabel(text);
		tabLabel.setHorizontalTextPosition(JLabel.TRAILING);
		tabLabel.setIconTextGap(8);
		tabLabel.setPreferredSize(new Dimension(65, 20));

		int index = tabs.getTabCount();
		tabs.addTab(null, contents);
		tabs.setTabComponentAt(index, tabLabel);
	}

	private JPanel createTreePanel()
	{
		mapTree = createTreeFromModel(table.mapsModel);
		JScrollPane mapScrollPane = new JScrollPane(mapTree);
		mapScrollPane.setBorder(BorderFactory.createEmptyBorder());

		stageTree = createTreeFromModel(table.stagesModel);
		JScrollPane stageScrollPane = new JScrollPane(stageTree);
		stageScrollPane.setBorder(BorderFactory.createEmptyBorder());

		resourceTree = createTreeFromModel(table.resourcesModel);
		JScrollPane resourceScrollPane = new JScrollPane(resourceTree);
		resourceScrollPane.setBorder(BorderFactory.createEmptyBorder());

		tabs = new JTabbedPane();
		createTab(tabs, "Maps", mapScrollPane);
		createTab(tabs, "Stages", stageScrollPane);
		createTab(tabs, "Resources", resourceScrollPane);

		tabs.addChangeListener((e) -> {
			switch (tabs.getSelectedIndex()) {
				case 0:
					currentTab = TabMode.MAPS;
					break;
				case 1:
					currentTab = TabMode.STAGES;
					break;
				case 2:
					currentTab = TabMode.RESOURCES;
					break;
			}

			if (ignoreTabChanges)
				return;

			JTree tree = null;
			switch (currentTab) {
				case MAPS:
					tree = mapTree;
					break;
				case STAGES:
					tree = stageTree;
					break;
				case RESOURCES:
					tree = resourceTree;
					break;
			}

			DefaultMutableTreeNode selected = (DefaultMutableTreeNode) tree.getLastSelectedPathComponent();
			if (selected != null)
				setSelectedNode(selected);
		});

		JButton addAreaButton = new JButton("Add New Area");
		addAreaButton.addActionListener((e) -> {
			modified = true;
			AreaConfig newArea = new AreaConfig("new");

			// add new map
			table.addArea(newArea);

			// navigate to new area, switching tabs if necessary
			JTree tree;
			TreePath path;
			switch (currentTab) {
				case MAPS:
					tree = mapTree;
					path = new TreePath(newArea.mapsTreeNode.getPath());
					break;
				case STAGES:
					tree = stageTree;
					path = new TreePath(newArea.stagesTreeNode.getPath());
					break;
				case RESOURCES:
					// force tab change
					ignoreTabChanges = true;
					tabs.setSelectedIndex(0);
					ignoreTabChanges = false;
					tree = mapTree;
					path = new TreePath(newArea.mapsTreeNode.getPath());
					break;
				default:
					throw new IllegalStateException("Invalid tab: " + currentTab);
			}
			tree.setSelectionPath(path);
			tree.scrollPathToVisible(path);
			tree.repaint();
		});

		JButton addResourceButton = new JButton("Add Resource");
		addResourceButton.addActionListener((e) -> {
			modified = true;
			Resource res = new Resource("new", false);

			// force tab change
			ignoreTabChanges = true;
			tabs.setSelectedIndex(2);
			ignoreTabChanges = false;

			// add new resource and scroll to it
			table.addResource(res);
			TreePath resourcePath = new TreePath(res.treeNode.getPath());
			resourceTree.scrollPathToVisible(resourcePath);
			resourceTree.setSelectionPath(resourcePath);
		});

		/*
		JRadioButton engineOption = new JRadioButton("Engine Names");
		JRadioButton friendOption = new JRadioButton("Friendly Names");
		ButtonGroup bg = new ButtonGroup();
		bg.add(engineOption);
		bg.add(friendOption);
		engineOption.setSelected(true);
		
		ActionListener nameModeListener = new ActionListener()
		{
			@Override
			public void actionPerformed(ActionEvent e)
			{
				JRadioButton source = (JRadioButton)e.getSource();
				showNicknames = (source == friendOption);
				reloadTree(mapTree);
				reloadTree(stageTree);
			}
		};
		engineOption.addActionListener(nameModeListener);
		friendOption.addActionListener(nameModeListener);
		 */

		JPanel panel = new JPanel();
		panel.setLayout(new MigLayout("fill, ins 0", "[grow]8[grow]"));

		panel.add(tabs, "push, grow, wrap");

		panel.add(addAreaButton, "split 2, w 50%");
		panel.add(addResourceButton, "w 50%, wrap, gaptop 4");

		return panel;
	}

	private JTree createTreeFromModel(DefaultTreeModel model)
	{
		JTree tree = new JTree(model);
		tree.setRootVisible(false);
		tree.setShowsRootHandles(true);
		tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
		tree.getSelectionModel().addTreeSelectionListener(e -> {
			DefaultMutableTreeNode node = (DefaultMutableTreeNode) tree.getLastSelectedPathComponent();

			// happens when a group is closed with something selected inside
			if (node == null)
				return;

			setSelectedNode(node);
		});

		tree.setRowHeight(20);

		tree.setBorder(BorderFactory.createCompoundBorder(
			tree.getBorder(),
			BorderFactory.createEmptyBorder(8, 8, 8, 8)));

		tree.setCellRenderer(new MapTableTreeCellRenderer(this));

		tree.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), "DeleteCommands");
		tree.getActionMap().put("DeleteCommands", new AbstractAction() {
			@Override
			public void actionPerformed(ActionEvent e)
			{
				if (currentNode != null) {
					deleteSelectedNode();
				}
			}
		});

		tree.setDragEnabled(true);
		tree.setDropMode(DropMode.ON_OR_INSERT);
		tree.setTransferHandler(new TreeTransferHandler());

		return tree;
	}

	// modified from https://coderanch.com/t/346509/java/JTree-drag-drop-tree-Java
	private class TreeTransferHandler extends TransferHandler
	{
		private DataFlavor nodesFlavor;
		private DataFlavor[] flavors = new DataFlavor[1];

		private DefaultMutableTreeNode dropDestination;
		private int dropChildIndex;

		public TreeTransferHandler()
		{
			try {
				String mimeType = DataFlavor.javaJVMLocalObjectMimeType +
					";class=\"" + javax.swing.tree.DefaultMutableTreeNode[].class.getName() + "\"";
				nodesFlavor = new DataFlavor(mimeType);
				flavors[0] = nodesFlavor;
			}
			catch (ClassNotFoundException e) {
				System.out.println("ClassNotFound: " + e.getMessage());
			}
		}

		@Override
		public boolean canImport(TransferSupport support)
		{
			if (!support.isDrop())
				return false;

			support.setShowDropLocation(true);

			if (!support.isDataFlavorSupported(nodesFlavor))
				return false;

			JTree.DropLocation dl = (JTree.DropLocation) support.getDropLocation();
			DefaultMutableTreeNode target = (DefaultMutableTreeNode) dl.getPath().getLastPathComponent();

			if (target == currentNode)
				return false;

			dropDestination = target;
			dropChildIndex = dl.getChildIndex();

			if (target.getLevel() == currentNode.getLevel()) {
				dropDestination = (DefaultMutableTreeNode) target.getParent();
				dropChildIndex = dropDestination.getIndex(target);
			}

			if (target.getLevel() != (currentNode.getLevel() - 1))
				return false;

			return true;
		}

		@Override
		protected Transferable createTransferable(JComponent c)
		{
			JTree tree = (JTree) c;
			TreePath[] paths = tree.getSelectionPaths();
			if (paths != null) {
				String[] names = { "star", "rod" };
				return new DummyTransferable(names);
			}
			return null;
		}

		@Override
		public int getSourceActions(JComponent c)
		{
			return MOVE;
		}

		@Override
		public boolean importData(TransferHandler.TransferSupport support)
		{
			if (!canImport(support))
				return false;

			JTree tree = (JTree) support.getComponent();
			DefaultTreeModel model = (DefaultTreeModel) tree.getModel();
			DefaultMutableTreeNode currentParent = (DefaultMutableTreeNode) currentNode.getParent();

			if (currentParent == dropDestination && currentParent.getIndex(currentNode) < dropChildIndex)
				dropChildIndex--;

			model.removeNodeFromParent(currentNode);
			model.insertNodeInto(currentNode, dropDestination, dropChildIndex);

			return true;
		}

		@Override
		public String toString()
		{
			return getClass().getName();
		}

		// we don't actually transfer anything
		private class DummyTransferable implements Transferable
		{
			public String[] objectNames;

			public DummyTransferable(String[] names)
			{
				objectNames = names;
			}

			@Override
			public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException
			{
				if (!isDataFlavorSupported(flavor))
					throw new UnsupportedFlavorException(flavor);

				return objectNames;
			}

			@Override
			public DataFlavor[] getTransferDataFlavors()
			{
				return flavors;
			}

			@Override
			public boolean isDataFlavorSupported(DataFlavor flavor)
			{
				return nodesFlavor.equals(flavor);
			}
		}
	}

	public void modified()
	{
		modified = true;
	}

	public void validateNames()
	{
		table.validateNames();
	}

	public boolean hasResource(String name)
	{
		Stack<DefaultMutableTreeNode> stack = new Stack<>();
		stack.push((DefaultMutableTreeNode) table.resourcesModel.getRoot());
		while (!stack.isEmpty()) {
			DefaultMutableTreeNode node = stack.pop();

			if (node.isLeaf()) {
				Resource res = (Resource) node.getUserObject();
				if (res.name.equals(name))
					return true;
			}
			else {
				for (int i = 0; i < node.getChildCount(); i++)
					stack.push((DefaultMutableTreeNode) node.getChildAt(i));
			}
		}
		return false;
	}

	public void repaintTrees()
	{
		((DefaultTreeModel) mapTree.getModel()).nodeChanged(currentNode);
		((DefaultTreeModel) stageTree.getModel()).nodeChanged(currentNode);
		mapTree.repaint();
		stageTree.repaint();
	}

	public void repaintResourceTree()
	{
		((DefaultTreeModel) resourceTree.getModel()).nodeChanged(currentNode);
		resourceTree.repaint();
	}

	public void addNewMap(AreaConfig area)
	{
		modified = true;

		MapConfig newMap = promptCreateMap(frame, area, false);

		if (newMap != null) {
			// force tab change
			ignoreTabChanges = true;
			tabs.setSelectedIndex(0);
			ignoreTabChanges = false;

			// add new map and scroll to it
			table.addMap(area, newMap);
			TreePath areaPath = new TreePath(area.mapsTreeNode.getPath());
			TreePath mapPath = new TreePath(newMap.treeNode.getPath());
			mapTree.setSelectionPath(areaPath);
			mapTree.expandPath(areaPath);
			mapTree.scrollPathToVisible(mapPath);
		}
	}

	public void addNewStage(AreaConfig area)
	{
		modified = true;

		MapConfig newStage = promptCreateMap(frame, area, true);

		// force tab change
		ignoreTabChanges = true;
		tabs.setSelectedIndex(1);
		ignoreTabChanges = false;

		// add new stage and scroll to it
		table.addStage(area, newStage);
		TreePath areaPath = new TreePath(area.stagesTreeNode.getPath());
		TreePath stagePath = new TreePath(newStage.treeNode.getPath());
		stageTree.setSelectionPath(areaPath);
		stageTree.expandPath(areaPath);
		stageTree.scrollPathToVisible(stagePath);
	}
}
