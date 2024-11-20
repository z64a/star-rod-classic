package game.map.tree;

import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map.Entry;
import java.util.TreeMap;

import javax.swing.SwingUtilities;
import javax.swing.tree.TreePath;

import game.map.MapObject;
import game.map.editor.commands.AbstractCommand;

@Deprecated
public class MoveNodes<T extends MapObject> extends AbstractCommand
{
	private MapObjectTreeModel<T> treeModel;
	private List<MapObjectNode<T>> nodeList;
	private TreeMap<TreePosition, MapObjectNode<T>> oldParents;

	private MapObjectNode<T> newParentNode;
	private int insertionIndex;

	private class TreePosition implements Comparable<TreePosition>
	{
		MapObjectNode<T> oldParentNode;
		int position;

		@Override
		public int compareTo(MoveNodes<T>.TreePosition other)
		{
			return position - other.position;
		}
	}

	public MoveNodes(
		MapObjectTreeModel<T> treeModel,
		List<MapObjectNode<T>> nodeList,
		MapObjectNode<T> newParent,
		int childIndex,
		boolean allowMoveWithinGroup)
	{
		super("Move Selected Objects");
		this.treeModel = treeModel;
		this.nodeList = nodeList;
		this.newParentNode = newParent;
		this.insertionIndex = childIndex;

		if (insertionIndex == -1)
			insertionIndex = newParentNode.getChildCount();

		// crucial step to prevent exceptions when undoing
		Collections.sort(nodeList);

		LinkedList<MapObjectNode<T>> groups = new LinkedList<>();
		int selectedTargetSibilings = 0;

		// find all acceptable groups
		Iterator<MapObjectNode<T>> iter = nodeList.iterator();
		while (iter.hasNext()) {
			MapObjectNode<T> node = iter.next();

			// reject moving to oneself
			if (node == newParent) {
				iter.remove();
				continue;
			}

			if (newParent == node.getParent())
				selectedTargetSibilings++;

			// reject circular hierarchies
			if (newParentNode.isNodeAncestor(node)) {
				iter.remove();
				continue;
			}

			// reject moving to current parent
			if (!allowMoveWithinGroup && newParentNode == node.getParent()) {
				iter.remove();
				continue;
			}

			// save groups
			if (node.getAllowsChildren())
				groups.add(node);
		}

		insertionIndex -= selectedTargetSibilings;
		if (insertionIndex < 0)
			insertionIndex = 0;

		// remove groups that don't allow childen? -- unnecessary?
		iter = nodeList.iterator();
		while (iter.hasNext()) {
			MapObjectNode<T> node = iter.next();

			if (!node.getAllowsChildren()) {
				if (groups.contains(node))
					iter.remove();
			}
		}

		oldParents = new TreeMap<>();

		for (int i = 0; i < nodeList.size(); i++) {
			MapObjectNode<T> node = nodeList.get(i);
			MapObjectNode<T> parent = node.getParent();

			TreePosition pos = new TreePosition();
			pos.oldParentNode = parent;
			pos.position = treeModel.getIndexOfChild(parent, node);
			oldParents.put(pos, node);
		}
	}

	@Override
	public void exec()
	{
		// must run in the swing thread
		SwingUtilities.invokeLater(() -> {
			int position = insertionIndex;

			for (int i = 0; i < nodeList.size(); i++) {
				MapObjectNode<T> node = nodeList.get(i);
				treeModel.removeNodeFromParent(node);

				if (node.parentNode != newParentNode)
					position--;
			}

			for (int i = 0; i < nodeList.size(); i++) {
				MapObjectNode<T> node = nodeList.get(i);
				treeModel.insertNodeInto(node, newParentNode, position);
				position++;

				node.parentNode = newParentNode;
			}

			for (int i = 0; i < newParentNode.getChildCount(); i++)
				newParentNode.getChildAt(i).childIndex = i;

			for (MapObjectNode<T> node : nodeList)
				treeModel.getTree().addSelectionPath(new TreePath(node.getPath()));

			treeModel.recalculateIndicies();
		});

	}

	@Override
	public void undo()
	{
		// must run in the swing thread
		SwingUtilities.invokeLater(() -> {
			for (Entry<TreePosition, MapObjectNode<T>> entry : oldParents.entrySet()) {
				TreePosition oldTreePos = entry.getKey();
				MapObjectNode<T> node = entry.getValue();

				treeModel.removeNodeFromParent(node);
				treeModel.insertNodeInto(node, oldTreePos.oldParentNode, oldTreePos.position);

				node.parentNode = oldTreePos.oldParentNode;
			}

			for (TreePosition pos : oldParents.keySet()) {
				for (int i = 0; i < pos.oldParentNode.getChildCount(); i++)
					pos.oldParentNode.getChildAt(i).childIndex = i;
			}

			for (MapObjectNode<T> node : nodeList)
				treeModel.getTree().addSelectionPath(new TreePath(node.getPath()));

			treeModel.recalculateIndicies();
		});
	}
}
