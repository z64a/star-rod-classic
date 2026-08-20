package game.sprite.editor;

import java.awt.Font;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;

import common.CameraInput;
import common.KeyInput;

import net.miginfocom.swing.MigLayout;

public class SpriteShortcutsPanel extends JPanel
{
	private final SpriteKeyConfig keyConfig;

	public SpriteShortcutsPanel(SpriteKeyConfig keyConfig)
	{
		this.keyConfig = keyConfig;
		setLayout(new MigLayout("fill"));

		JTabbedPane tabs = new JTabbedPane();

		tabs.addTab("Commands", getCommandsTab());
		tabs.addTab("Camera", getCameraTab());
		tabs.addTab("Playback", getPlaybackTab());
		tabs.addTab("Misc", getMiscTab());

		add(tabs, "grow, w 320!");
	}

	private void addHeader(JPanel panel, String text)
	{
		boolean first = panel.getComponentCount() == 0;
		String fmt = first ? "span, wrap" : "span, wrap, gaptop 8";

		JLabel lbl = new JLabel(text);
		lbl.setFont(new Font(lbl.getFont().getFontName(), Font.BOLD, 12));

		panel.add(lbl, fmt);
	}

	private void addShortcut(JPanel panel, String name, String keys)
	{
		addShortcut(panel, name, keys, "");
	}

	private void addShortcut(JPanel panel, String name, KeyInput input)
	{
		addShortcut(panel, name, keyConfig.getBindingText(input));
	}

	private void addShortcut(JPanel panel, String desc, String keys, String tip)
	{
		String lblText = tip.isEmpty() ? desc : desc + "*";
		JLabel lbl = new JLabel(lblText);
		if (!tip.isEmpty())
			lbl.setToolTipText(tip);
		panel.add(lbl);
		panel.add(new JLabel(keys), "wrap");
	}

	private String getBindingList(KeyInput ... inputs)
	{
		StringBuilder text = new StringBuilder();
		for (KeyInput input : inputs) {
			if (text.length() > 0)
				text.append(" / ");
			text.append(keyConfig.getBindingText(input));
		}
		return text.toString();
	}

	private JPanel getCommandsTab()
	{
		JPanel tab = new JPanel(new MigLayout("fillx", "[50%][50%]"));

		addShortcut(tab, "Select", "Left Click");
		addShortcut(tab, "Reorder", "Click + Drag");
		addShortcut(tab, "Duplicate", "Ctrl + D");
		addShortcut(tab, "Copy", "Ctrl + C");
		addShortcut(tab, "Paste", "Ctrl + V");
		addShortcut(tab, "Delete", "Delete (while selected)");
		addShortcut(tab, "Skip Animation To", "Right Click", "Play the current animation until the command is reached, if possible.");

		return tab;
	}

	private JPanel getCameraTab()
	{
		JPanel tab = new JPanel(new MigLayout("fillx", "[50%][50%]"));

		addShortcut(tab, "Pan", getBindingList(CameraInput.PAN_UP, CameraInput.PAN_LEFT, CameraInput.PAN_DOWN, CameraInput.PAN_RIGHT));
		addShortcut(tab, "Zoom", "Mouse Wheel");
		addShortcut(tab, "Reset", SpriteInput.RESET_CAMERA);

		return tab;
	}

	private JPanel getPlaybackTab()
	{
		JPanel tab = new JPanel(new MigLayout("fillx", "[50%][50%]"));

		addShortcut(tab, "Play/Pause", SpriteInput.PLAY);
		addShortcut(tab, "Stop", SpriteInput.STOP);
		addShortcut(tab, "Prev Frame", SpriteInput.PREVIOUS_FRAME);
		addShortcut(tab, "Next Frame", SpriteInput.NEXT_FRAME);
		addShortcut(tab, "Restart", SpriteInput.RESET_PLAYBACK);

		return tab;
	}

	private JPanel getMiscTab()
	{
		JPanel tab = new JPanel(new MigLayout("fillx", "[50%][50%]"));

		addHeader(tab, "Selection");
		addShortcut(tab, "Delete Selected Component / Raster", SpriteInput.DELETE_SELECTED);

		addHeader(tab, "Components");
		addShortcut(tab, "Toggle Component Visibility", SpriteInput.TOGGLE_COMPONENT);
		addShortcut(tab, "Rename Component", "Triple Click on Tab");

		return tab;
	}
}
