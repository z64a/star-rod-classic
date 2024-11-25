package game.string.editor;

import java.awt.Component;
import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Stack;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTree;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.TreeCellRenderer;
import javax.swing.tree.TreePath;

import app.StarRodClassic;
import app.SwingUtils;
import game.string.PMString;
import game.string.editor.io.FileMetadata;
import game.string.editor.io.StringResource;
import game.string.editor.io.StringResourceTree;
import game.string.editor.io.StringTreeModel;
import game.string.editor.io.StringTreeNode;
import game.string.editor.io.FileMetadata.FileType;
import net.miginfocom.swing.MigLayout;
import util.Logger;

public class ResourcePanel extends JPanel
{
	private StringEditor editor;
	private StringListPanel listPanel;

	private StringResourceTree resourceTree;
	private StringTreeModel resourceTreeModel;

	private JPopupMenu allPopup = new JPopupMenu();
	private JPopupMenu dirPopup = new JPopupMenu();
	private JPopupMenu resPopup = new JPopupMenu();
	private StringTreeNode popupNode;

	public ResourcePanel(StringEditor editor, StringListPanel listPanel)
	{
		this.editor = editor;
		this.listPanel = listPanel;
		resourceTreeModel = new StringTreeModel();

		resourceTree = new StringResourceTree(resourceTreeModel);
		resourceTree.setRowHeight(20);
		resourceTree.setCellRenderer(new ResourceTreeCellRenderer());

		resourceTree.setBorder(BorderFactory.createCompoundBorder(
			resourceTree.getBorder(),
			BorderFactory.createEmptyBorder(8, 8, 8, 8)));

		resourceTree.getSelectionModel().addTreeSelectionListener(e -> {
			StringTreeNode node = (StringTreeNode) resourceTree.getLastSelectedPathComponent();
			if (node == null)
				return;

			List<PMString> strings = new ArrayList<>();
			node.addStrings(strings);
			listPanel.setStrings(strings);
		});

		JMenuItem item;

		item = new JMenuItem("Save Changes");
		item.addActionListener((evt) -> {
			saveChangesTo(popupNode);
		});
		allPopup.add(item);

		item = new JMenuItem("Save Changes");
		item.addActionListener((evt) -> {
			saveChangesTo(popupNode);
		});
		dirPopup.add(item);
		item = new JMenuItem("Open Directory");
		item.addActionListener((evt) -> {
			try {
				Desktop.getDesktop().open(popupNode.getUserObject().getFile());
			}
			catch (IOException e) {
				Logger.printStackTrace(e);
			}
		});
		dirPopup.add(item);

		item = new JMenuItem("Save Changes");
		item.addActionListener((evt) -> {
			saveChangesTo(popupNode);
		});
		resPopup.add(item);
		item = new JMenuItem("Open Resource");
		item.addActionListener((evt) -> {
			StarRodClassic.openTextFile(popupNode.getUserObject().getFile());
		});
		resPopup.add(item);
		item = new JMenuItem("Add New String");
		item.addActionListener((evy) -> {
			StringResource res = popupNode.getUserObject().getResource();
			res.strings.add(new PMString(res));
			updateEditorInfo();

			StringTreeNode node = (StringTreeNode) resourceTree.getLastSelectedPathComponent();
			if (node == null)
				return;

			List<PMString> strings = new ArrayList<>();
			node.addStrings(strings);
			listPanel.setStrings(strings);
		});
		resPopup.add(item);

		resourceTree.addPopupListener((evt) -> {
			popupNode = (StringTreeNode) evt.getNode();
			switch (popupNode.getUserObject().getType()) {
				case Root:
					evt.show(allPopup);
					break;
				case Dir:
					evt.show(dirPopup);
					break;
				case Resource:
					evt.show(resPopup);
					break;
			}
		});

		JButton reloadButton = new JButton("Reload All Resources");
		reloadButton.addActionListener((e) -> fullReload());

		JButton saveAllButton = new JButton("Save All Changes");
		reloadButton.addActionListener((e) -> saveAllChanges());

		setLayout(new MigLayout("fill, ins 0"));
		JScrollPane scrollPane = new JScrollPane(resourceTree);
		add(scrollPane, "gaptop 12, gapbottom 8, grow, pushy, span, wrap");

		add(reloadButton, "sg but, growx, h 32!");
		add(saveAllButton, "sg but, growx, h 32!");
	}

	public void saveAllChanges()
	{
		saveChangesTo(resourceTreeModel.getRoot());
	}

	public void saveChangesTo(StringTreeNode startNode)
	{
		Stack<StringTreeNode> nodes = new Stack<>();
		nodes.push(startNode);
		while (!nodes.isEmpty()) {
			StringTreeNode node = nodes.pop();
			FileMetadata nodeData = node.getUserObject();

			if (nodeData.getType() == FileType.Resource && nodeData.modified) {
				StringResource res = nodeData.getResource();
				if (!editor.resourcesToSave.contains(res))
					editor.resourcesToSave.add(res);
			}

			for (int i = 0; i < node.getChildCount(); i++)
				nodes.push(node.getChildAt(i));
		}
	}

	public void fullReload()
	{
		resourceTreeModel.forceFullReload();
		updateEditorInfo();

		StringTreeNode root = resourceTreeModel.getRoot();
		resourceTree.expandPath(new TreePath(root));
		resourceTree.setSelectionPath(new TreePath(root));

		editor.invokeLater(() -> {
			editor.setString(null);
		});
	}

	public void refresh()
	{
		TreeState treeState = new TreeState(resourceTree);

		resourceTreeModel.refresh();
		updateEditorInfo();

		treeState.restoreState();
	}

	private static final class TreeState
	{
		private StringResourceTree tree;
		private FileMetadata selectedData = null;
		private HashSet<File> extendedFiles;

		private TreeState(StringResourceTree tree)
		{
			this.tree = tree;

			TreePath selectedPath = tree.getSelectionPath();
			if (selectedPath != null) {
				StringTreeNode selectedNode = (StringTreeNode) selectedPath.getLastPathComponent();
				selectedData = selectedNode.getUserObject();
			}

			extendedFiles = new HashSet<>();
			Stack<StringTreeNode> nodes = new Stack<>();
			nodes.push(tree.getModel().getRoot());
			while (!nodes.isEmpty()) {
				StringTreeNode node = nodes.pop();

				if (tree.isExpanded(new TreePath(node.getPath())))
					extendedFiles.add(node.getUserObject().getFile());

				for (int i = 0; i < node.getChildCount(); i++)
					nodes.push(node.getChildAt(i));
			}
		}

		private final void restoreState()
		{
			HashMap<FileMetadata, StringTreeNode> nodeLookup = tree.getModel().getNodeLookup();

			Stack<StringTreeNode> nodes = new Stack<>();
			nodes.push(tree.getModel().getRoot());
			while (!nodes.isEmpty()) {
				StringTreeNode node = nodes.pop();
				FileMetadata nodeData = node.getUserObject();

				if (!node.isLeaf() && extendedFiles.contains(nodeData.getFile())) {
					TreePath nodePath = new TreePath(node.getPath());
					tree.expandPath(nodePath);
				}

				for (int i = 0; i < node.getChildCount(); i++)
					nodes.push(node.getChildAt(i));
			}

			if (selectedData == null || !nodeLookup.containsKey(selectedData))
				tree.setSelectionPath(new TreePath(tree.getModel().getRoot()));
			else
				tree.setSelectionPath(new TreePath(nodeLookup.get(selectedData).getPath()));
		}
	}

	public void updateEditorInfo()
	{
		resourceTreeModel.getRoot().updateEditorInfo();
		repaint();
		listPanel.repaint();
	}

	public static class ResourceTreeCellRenderer extends DefaultTreeCellRenderer implements TreeCellRenderer
	{
		@Override
		public Component getTreeCellRendererComponent(JTree tree, Object obj,
			boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus)
		{
			super.getTreeCellRendererComponent(tree, obj, selected, expanded, leaf, row, hasFocus);

			StringTreeNode node = (StringTreeNode) obj;
			FileMetadata nodeData = node.getUserObject();

			switch (nodeData.getType()) {
				case Root:
				case Dir:
					if (expanded)
						setIcon(openIcon);
					else
						setIcon(closedIcon);
					break;
				case Resource:
					setIcon(leafIcon);
					break;
			}

			if (nodeData.modified)
				setText("<html><i>* " + nodeData.toString() + "</i></html>");
			else
				nodeData.toString();

			setForeground(nodeData.error ? SwingUtils.getRedTextColor() : null);

			return this;
		}
	}
}
