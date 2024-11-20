package game.string.editor.io;

import java.awt.Component;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.Consumer;

import javax.swing.JPopupMenu;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;

public class StringResourceTree extends JTree
{
	public StringResourceTree(TreeModel model)
	{
		throw new IllegalStateException("StringResourceTree may only use StringTreeModel!");
	}

	public StringResourceTree(StringTreeModel model)
	{
		super(model);
		setRootVisible(true);
		setShowsRootHandles(true);
		getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);

		addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e)
			{
				if (!isEnabled())
					return;
				TreePath path = getClosestPathForLocation(e.getX(), e.getY());
				Rectangle pathBounds = getUI().getPathBounds(StringResourceTree.this, path);

				if (pathBounds != null) {
					if (pathBounds.y > e.getY() || pathBounds.y + pathBounds.height < e.getY())
						clearSelection(); // clicked outside nearest line -> clicked nowhere
				}
			}
		});
	}

	@Override
	public StringTreeModel getModel()
	{
		return (StringTreeModel) super.getModel();
	}

	public void addPopupListener(Consumer<PopupEvent> popupListener)
	{
		addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e)
			{
				if (!isEnabled())
					return;

				TreePath path = getPathForLocation(e.getX(), e.getY());
				Rectangle pathBounds = getUI().getPathBounds(StringResourceTree.this, path);
				boolean inBounds = (pathBounds != null) && pathBounds.contains(e.getX(), e.getY());

				if (SwingUtilities.isRightMouseButton(e)) {
					if (inBounds) {
						DefaultMutableTreeNode source = (DefaultMutableTreeNode) path.getLastPathComponent();
						popupListener.accept(new PopupEvent(source, e.getComponent(), e.getX(), e.getY()));
					}
				}
			}
		});
	}

	public static class PopupEvent
	{
		private final DefaultMutableTreeNode source;
		private final Component comp;
		private final int x;
		private final int y;

		private PopupEvent(DefaultMutableTreeNode node, Component comp, int x, int y)
		{
			this.source = node;
			this.comp = comp;
			this.x = x;
			this.y = y;
		}

		public DefaultMutableTreeNode getNode()
		{
			return source;
		}

		public void show(JPopupMenu menu)
		{
			menu.show(comp, x, y);
		}
	}
}
