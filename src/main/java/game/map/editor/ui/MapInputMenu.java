package game.map.editor.ui;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;

import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComponent;
import javax.swing.JMenuItem;
import javax.swing.KeyStroke;

import common.KeyboardInputConfig;
import game.map.editor.MapEditor;
import game.map.editor.MapKeyConfig;
import game.map.editor.MapInput;

/**
 * Swing presentation for Map Editor key inputs. Keyboard execution remains owned by KeyboardInput; accelerators here are display-only.
 */
public final class MapInputMenu implements KeyboardInputConfig.Listener
{
	private final MapEditor editor;
	private final MapKeyConfig keyConfig;
	private final EnumMap<MapInput, List<JMenuItem>> menuItems = new EnumMap<>(MapInput.class);
	private final EnumMap<MapInput, JCheckBoxMenuItem> checkboxes = new EnumMap<>(MapInput.class);

	public MapInputMenu(MapEditor editor, MapKeyConfig keyConfig)
	{
		this.editor = editor;
		this.keyConfig = keyConfig;
		keyConfig.addListener(this);
	}

	public void bindMenuItem(MapInput input, JMenuItem item)
	{
		List<JMenuItem> items = menuItems.get(input);
		if (items == null) {
			items = new ArrayList<>();
			menuItems.put(input, items);
		}
		items.add(item);
		updateMenuItem(input, item);

		item.addActionListener((e) -> editor.enqueueShortcut(input));
	}

	public void bindMenuCheckbox(MapInput input, JCheckBoxMenuItem checkbox)
	{
		checkboxes.put(input, checkbox);
		bindMenuItem(input, checkbox);
	}

	public void setCheckbox(MapInput input, boolean selected)
	{
		JCheckBoxMenuItem checkbox = checkboxes.get(input);
		assert (checkbox != null);
		checkbox.setSelected(selected);
	}

	public JCheckBoxMenuItem getCheckbox(MapInput input)
	{
		return checkboxes.get(input);
	}

	@Override
	public void keyBindingsChanged()
	{
		for (MapInput input : menuItems.keySet()) {
			for (JMenuItem item : menuItems.get(input))
				updateMenuItem(input, item);
		}
	}

	private void updateMenuItem(MapInput input, JMenuItem item)
	{
		KeyStroke oldStroke = item.getAccelerator();
		if (oldStroke != null)
			item.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).remove(oldStroke);

		KeyStroke stroke = keyConfig.getBinding(input).toKeyStroke();
		item.setAccelerator(stroke);
		if (stroke != null)
			item.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(stroke, "none");
	}
}
