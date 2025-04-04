package game.map.tree;

import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;

import game.map.editor.MapEditor;
import game.map.editor.ui.MapObjectPanel;
import game.map.editor.ui.SwingGUI;
import game.map.hit.Zone;

public class ZoneJTree extends MapObjectJTree<Zone>
{
	private static final long serialVersionUID = 1L;

	public ZoneJTree(MapEditor editor, SwingGUI gui, MapObjectPanel panel)
	{
		super(editor, gui, panel);
		setRootVisible(false);
	}

	@Override
	protected JPopupMenu createPopupMenu(JPopupMenu popupMenu)
	{
		JMenuItem item = new JMenuItem("Move Selected Here");
		addButtonCommand(item, TreeCommand.POPUP_MOVE_SELECTION);
		popupMenu.add(item);

		item = new JMenuItem("Select Children");
		addButtonCommand(item, TreeCommand.POPUP_SELECT_CHILDREN);
		popupMenu.add(item);

		popupMenu.addSeparator();

		item = new JMenuItem("Create Subgroup");
		addButtonCommand(item, TreeCommand.POPUP_NEW_GROUP);
		popupMenu.add(item);

		item = new JMenuItem("Create Primitive");
		addButtonCommand(item, TreeCommand.POPUP_NEW_PRIMITIVE);
		popupMenu.add(item);

		popupMenu.addSeparator();

		item = new JMenuItem("Import Zones");
		addButtonCommand(item, TreeCommand.POPUP_IMPORT_HERE);
		popupMenu.add(item);

		item = new JMenuItem("Paste Zones Here");
		addButtonCommand(item, TreeCommand.POPUP_PASTE_HERE);
		popupMenu.add(item);

		return popupMenu;
	}

	@Override
	public void handleTreeCommand(TreeCommand cmd)
	{
		super.handleTreeCommand(cmd);

		switch (cmd) {
			case DND_MOVE_SELECTION:
				editor.doNextFrame(() -> {
					editor.action_MoveSelectedZones(dropDestination, dropChildIndex);
				});
				break;

			case POPUP_MOVE_SELECTION:
				editor.doNextFrame(() -> {
					editor.action_MoveSelectedZones(popupSource, -1);
				});
				break;

			default:
		}
	}
}
