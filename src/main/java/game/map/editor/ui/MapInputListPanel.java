package game.map.editor.ui;

import java.awt.Font;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;

import game.map.editor.MapInput;
import game.map.editor.MapKeyConfig;
import net.miginfocom.swing.MigLayout;

public class MapInputListPanel extends JPanel
{
	private final MapKeyConfig keyConfig;

	public MapInputListPanel(MapKeyConfig keyConfig)
	{
		this.keyConfig = keyConfig;
		setLayout(new MigLayout("fill"));

		JTabbedPane tabs = new JTabbedPane();

		tabs.addTab("Editor", getEditorTab());
		tabs.addTab("Play in Editor", getPlayInEditorTab());
		tabs.addTab("Selection", getSelectionTab());
		tabs.addTab("Transform", getTransformTab());
		tabs.addTab("Drawing", getDrawTab());
		tabs.addTab("Viewports", getViewportsTab());
		tabs.addTab("Grid / Snap", getGridTab());

		add(tabs, "grow, w 400!, h 500!");
	}

	private void addHeader(JPanel panel, String text)
	{
		addHeader(panel, text, null);
	}

	private void addHeader(JPanel panel, String text, String tooltip)
	{
		boolean first = panel.getComponentCount() == 0;
		String fmt = first ? "span, wrap, growx, gaptop 8" : "span, wrap, growx, gaptop 8";

		JLabel lbl = new JLabel((tooltip == null) ? text : text + "*");
		lbl.setFont(new Font(lbl.getFont().getFontName(), Font.BOLD, 12));

		if (tooltip != null)
			lbl.setToolTipText(tooltip);

		panel.add(lbl, fmt);
	}

	private void addShortcut(JPanel panel, String name, String keys)
	{
		addShortcut(panel, name, keys, "");
	}

	private void addShortcut(JPanel panel, String name, MapInput input)
	{
		addShortcut(panel, name, keyConfig.getBindingText(input), "");
	}

	private void addShortcut(JPanel panel, String name, MapInput input, String tip)
	{
		addShortcut(panel, name, keyConfig.getBindingText(input), tip);
	}

	private void addShortcut(JPanel panel, String desc, String keys, String tip)
	{
		String lblText = tip.isEmpty() ? desc : desc + "*";
		JLabel lbl = new JLabel(lblText);
		if (!tip.isEmpty())
			lbl.setToolTipText(tip);
		panel.add(lbl, "growx");
		panel.add(new JLabel(keys), "growx, wrap");
	}

	private String getBindingList(MapInput ... inputs)
	{
		StringBuilder text = new StringBuilder();
		for (MapInput input : inputs) {
			if (text.length() > 0)
				text.append(" / ");
			text.append(keyConfig.getBindingText(input));
		}
		return text.toString();
	}

	private JPanel getEditorTab()
	{
		JPanel tab = new JPanel(new MigLayout("fillx", "[50%][50%]"));

		addHeader(tab, "General");
		addShortcut(tab, "Save", MapInput.SAVE);
		addShortcut(tab, "Quit", MapInput.QUIT);
		addShortcut(tab, "Quit to Menu", MapInput.SWITCH);
		addShortcut(tab, "Undo", MapInput.UNDO);
		addShortcut(tab, "Redo", MapInput.REDO);
		addShortcut(tab, "Toggle Info Panel", MapInput.TOGGLE_INFO_PANEL);

		addHeader(tab, "Switch Transform Mode");
		addShortcut(tab, "Objects", MapInput.SELECT_OBJECTS);
		addShortcut(tab, "Triangles", MapInput.SELECT_TRIANGLES);
		addShortcut(tab, "Vertices", MapInput.SELECT_VERTICIES);
		addShortcut(tab, "Points", MapInput.SELECT_POINTS);

		addHeader(tab, "Switch Object Tab");
		addShortcut(tab, "Models", MapInput.OPEN_MODEL_TAB);
		addShortcut(tab, "Colliders", MapInput.OPEN_COLLIDER_TAB);
		addShortcut(tab, "Zones", MapInput.OPEN_ZONE_TAB);
		addShortcut(tab, "Markers", MapInput.OPEN_MARKER_TAB);

		return tab;
	}

	private JPanel getPlayInEditorTab()
	{
		JPanel shortcuts = new JPanel(new MigLayout("fillx, ins 0", "[50%][50%]"));

		addHeader(shortcuts, "Controls");
		addShortcut(shortcuts, "Begin/End", MapInput.PLAY_IN_EDITOR_TOGGLE);

		addShortcut(shortcuts, "Move", getBindingList(MapInput.MOVE_FORWARD, MapInput.MOVE_LEFT,
			MapInput.MOVE_BACKWARD, MapInput.MOVE_RIGHT));
		addShortcut(shortcuts, "Jump", MapInput.PLAY_IN_EDITOR_JUMP);
		addShortcut(shortcuts, "Run", "Hold Shift");
		addShortcut(shortcuts, "Hover", "Hold " + keyConfig.getBindingText(MapInput.PLAY_IN_EDITOR_HOVER));

		JPanel info = new JPanel(new MigLayout("fillx, wrap, ins 0"));
		addHeader(info, "How to Use");

		// screws up the layout for reasons completely unknown!
		info.add(new JLabel("<html><p>Press P when using the 3D viewport to spawn "
			+ "at the location of the 3D cursor. Gravity, jump, and collision "
			+ "physics match in-game.</p><br><p>Current camera zones will switch "
			+ "automatically as you move about. You can set hidden colliders and "
			+ "zones to be ignored (they will still exist in-game).</p><br>"
			+ "<p>All normal editor actions are available in this mode.</p></html>"),
			"growx, span, wrap");

		JPanel tab = new JPanel(new MigLayout("fillx, wrap"));
		tab.add(shortcuts, "growx");
		tab.add(info, "growx");
		return tab;
	}

	private JPanel getSelectionTab()
	{
		JPanel tab = new JPanel(new MigLayout("fillx", "[50%][50%]"));

		addHeader(tab, "Change Selection");
		addShortcut(tab, "Select", "Left Click");
		addShortcut(tab, "Select Add/Remove", "Ctrl + Left Click");
		addShortcut(tab, "Select All", MapInput.SELECT_ALL);
		addShortcut(tab, "Find Object", MapInput.FIND_OBJECT);
		addShortcut(tab, "Copy Objects", MapInput.COPY_OBJECTS, "You can even copy/paste objects between maps.");
		addShortcut(tab, "Paste Objects", MapInput.PASTE_OBJECTS);
		addShortcut(tab, "Delete Selected", MapInput.DELETE_SELECTED);

		addHeader(tab, "Modify Properties of Selection");
		addShortcut(tab, "Hide Selected", MapInput.HIDE_SELECTED);
		addShortcut(tab, "Open UV Editor", MapInput.TOGGLE_UV_EDIT, "Must have models or model triangles selected.");
		addShortcut(tab, "Toggle Double Sided", MapInput.TOGGLE_TWO_SIDED,
			"<html>Toggles triangle type for selected zones and colliders.<br>"
				+ "Useful for making one-way walls.</html>");

		return tab;
	}

	private JPanel getTransformTab()
	{
		JPanel tab = new JPanel(new MigLayout("fillx", "[50%][50%]"));

		addHeader(tab, "Transforming Selected Objects");
		addShortcut(tab, "Translate", "Left Mouse Drag", "Hold shift to translate a selecton without having to click on it.");
		addShortcut(tab, "Rotate", "Right Mouse Drag");
		addShortcut(tab, "Scale", "Space + Left Mouse Drag");
		addShortcut(tab, "Uniform Scale", "Space + Right Mouse Drag");
		addShortcut(tab, "Clone", "Any above + Alt");
		addShortcut(tab, "Nudge", getBindingList(MapInput.NUDGE_UP, MapInput.NUDGE_DOWN,
			MapInput.NUDGE_LEFT, MapInput.NUDGE_RIGHT, MapInput.NUDGE_OUT, MapInput.NUDGE_IN));
		addShortcut(tab, "Translate", "Hold Ctrl + Any Nudge Key");

		addHeader(tab, "Special Transformations");
		addShortcut(tab, "Flip Along X", MapInput.FLIP_SELECTED_X);
		addShortcut(tab, "Flip Along Y", MapInput.FLIP_SELECTED_Y);
		addShortcut(tab, "Filp Along Z", MapInput.FLIP_SELECTED_Z);
		addShortcut(tab, "Flip Normals", MapInput.FLIP_NORMALS);
		addShortcut(tab, "Open Transform Menu", MapInput.OPEN_TRANSFORM_DIALOG);

		addShortcut(tab, "Nudge to Grid", MapInput.ROUND_VERTICIES, "Moves any selected vertices to the nearest grid point.");

		return tab;
	}

	private JPanel getDrawTab()
	{
		JPanel shortcuts = new JPanel(new MigLayout("fillx, ins 0", "[50%][50%]"));
		addHeader(shortcuts, "Polygon Drawing and Cut");
		addShortcut(shortcuts, "Draw Convex", "Hold " + keyConfig.getBindingText(MapInput.DRAW_CONVEX));
		addShortcut(shortcuts, "Draw Concave", "Hold " + keyConfig.getBindingText(MapInput.DRAW_CONCAVE));
		addShortcut(shortcuts, "Draw Walls", "Hold " + keyConfig.getBindingText(MapInput.DRAW_WALLS));
		addShortcut(shortcuts, "Cut Meshes", "Hold " + keyConfig.getBindingText(MapInput.CUT_GEOMETRY),
			"Two points to define cutting plane and a third to choose the 'positive' side.");

		JPanel info = new JPanel(new MigLayout("fillx, wrap, ins 0"));
		addHeader(info, "How to Use");

		// screws up the layout for reasons completely unknown!
		info.add(new JLabel("<html>Hold down one of these keys and click in an ortho view to define vertices. "
			+ "The vertices will automatically connect and UVs will be created.</html>"), "growx, span, wrap");

		JPanel tab = new JPanel(new MigLayout("fillx, wrap"));
		tab.add(shortcuts, "growx");
		tab.add(info, "growx");
		return tab;
	}

	private JPanel getViewportsTab()
	{
		JPanel tab = new JPanel(new MigLayout("fillx", "[50%][50%]"));

		addHeader(tab, "Moving the Camera");
		String movementBindings = getBindingList(MapInput.MOVE_FORWARD, MapInput.MOVE_LEFT,
			MapInput.MOVE_BACKWARD, MapInput.MOVE_RIGHT);
		addShortcut(tab, "Pan (2D View)", movementBindings);
		addShortcut(tab, "Zoom (2D View)", "Mousewheel");
		addShortcut(tab, "Move (3D View)", "Hold Shift + " + movementBindings);
		addShortcut(tab, "Center on Selection", MapInput.CENTER_VIEW);

		addHeader(tab, "Rendering Options");
		addShortcut(tab, "Toggle 4-View", MapInput.TOGGLE_QUADVIEW);
		addShortcut(tab, "Toggle Wireframe", MapInput.TOGGLE_WIREFRAME);
		addShortcut(tab, "Toggle Edge Highlights", MapInput.TOGGLE_EDGES);
		addShortcut(tab, "Toggle Normals", MapInput.SHOW_NORMALS);
		addShortcut(tab, "Toggle Geometry Flags", MapInput.USE_GEOMETRY_FLAGS);
		addShortcut(tab, "Toggle Transform Gizmo", MapInput.SHOW_GIZMO);

		addHeader(tab, "Object Visibility");
		addShortcut(tab, "Toggle Models", MapInput.SHOW_MODELS);
		addShortcut(tab, "Toggle Colliders", MapInput.SHOW_COLLIDERS);
		addShortcut(tab, "Toggle Zones", MapInput.SHOW_ZONES);
		addShortcut(tab, "Toggle Markers", MapInput.SHOW_MARKERS);
		addShortcut(tab, "Show Only Models", MapInput.SHOW_ONLY_MODELS);
		addShortcut(tab, "Show Only Colliders", MapInput.SHOW_ONLY_COLLIDERS);
		addShortcut(tab, "Show Only Zones", MapInput.SHOW_ONLY_ZONES);
		addShortcut(tab, "Show Only Markers", MapInput.SHOW_ONLY_MARKERS);

		return tab;
	}

	private JPanel getGridTab()
	{
		JPanel tab = new JPanel(new MigLayout("fillx", "[50%][50%]"));

		addHeader(tab, "Grid Options");
		addShortcut(tab, "Toggle Grid", MapInput.TOGGLE_GRID);
		addShortcut(tab, "Switch Grid Type", MapInput.TOGGLE_GRID_TYPE,
			"<html>Switches between \"powers of 2\" (binary) and \"powers of 10\" (decimal) grids.<br>"
				+ "Most in-game geometry follows a decimal grid.</html>");
		addShortcut(tab, "Increase Grid Spacing", MapInput.INCREASE_GRID_POWER);
		addShortcut(tab, "Decrease Grid Spacing", MapInput.DECREASE_GRID_POWER);

		addHeader(tab, "Snap Options");
		addShortcut(tab, "Snap Translation", MapInput.SNAP_TRANSLATION);
		addShortcut(tab, "Snap Rotation", MapInput.SNAP_ROTATION);
		addShortcut(tab, "Snap Scale", MapInput.SNAP_SCALE);
		addShortcut(tab, "Toggle Scale Snap Mode", MapInput.SNAP_SCALE_GRID,
			"<html>Switches the rescale snap method between:<br>"
				+ "a. increments of 10%<br>"
				+ "b. nearest multiple of grid spacing</html>");

		addShortcut(tab, "Toggle Vertex Snap", MapInput.VERTEX_SNAP,
			"Vertices will snap to others during ortho view translations.");
		addShortcut(tab, "Toggle Vertex Snap to All", MapInput.VERTEX_SNAP_LIMIT,
			"Snap between only like objects (models, colliders, zones) or any object.");

		return tab;
	}
}
