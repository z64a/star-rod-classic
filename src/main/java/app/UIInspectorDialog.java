package app;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Window;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.UIDefaults;
import javax.swing.UIManager;
import javax.swing.plaf.UIResource;

import net.miginfocom.swing.MigLayout;

public class UIInspectorDialog extends JDialog
{
	private static final int MAX_MATCHES = 40;

	private final JTextArea details;

	public UIInspectorDialog(Window owner)
	{
		super(owner, "UI Defaults Inspector", ModalityType.MODELESS);

		details = new JTextArea();
		details.setEditable(false);
		details.setTabSize(4);

		JScrollPane scrollPane = new JScrollPane(details);
		scrollPane.setPreferredSize(new Dimension(600, 480));

		JButton closeButton = new JButton("Close");
		closeButton.addActionListener(e -> dispose());

		JPanel buttonPanel = new JPanel(new MigLayout("ins 0", "push[]"));
		buttonPanel.add(closeButton);

		JPanel contentPanel = new JPanel(new MigLayout("fill, wrap, ins 12"));
		contentPanel.add(scrollPane, "grow, push");
		contentPanel.add(buttonPanel, "growx");
		setContentPane(contentPanel);

		setIconImage(Environment.getDefaultIconImage());
		setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
		pack();
		setLocationRelativeTo(owner);
	}

	public void inspect(Component component)
	{
		details.setText(describe(component));
		details.setCaretPosition(0);
		if (!isVisible())
			setVisible(true);
		else
			toFront();
	}

	private String describe(Component component)
	{
		StringBuilder sb = new StringBuilder();
		String prefix = getDefaultsPrefix(component);
		Color foreground = component.getForeground();
		Color background = component.getBackground();

		sb.append("Component\n");
		sb.append("  Class: ").append(component.getClass().getName()).append('\n');
		if (component.getName() != null)
			sb.append("  Name: ").append(component.getName()).append('\n');
		if (component instanceof JComponent)
			sb.append("  UI class: ").append(((JComponent) component).getUIClassID()).append('\n');
		sb.append("  Foreground: ").append(describeColor(foreground)).append(describeSource(component.isForegroundSet(), foreground)).append('\n');
		sb.append("  Background: ").append(describeColor(background)).append(describeSource(component.isBackgroundSet(), background)).append('\n');
		if (component instanceof JComponent)
			sb.append("  Opaque: ").append(((JComponent) component).isOpaque()).append('\n');

		sb.append("\nParent chain\n");
		Component parent = component.getParent();
		for (int i = 0; parent != null && i < 8; i++) {
			sb.append("  ").append(parent.getClass().getName());
			if (parent.getName() != null)
				sb.append(" [").append(parent.getName()).append(']');
			sb.append('\n');
			parent = parent.getParent();
		}

		UIDefaults defaults = UIManager.getLookAndFeelDefaults();
		List<String> relevant = new ArrayList<>();
		List<String> matches = new ArrayList<>();
		for (Object defaultKey : defaults.keySet()) {
			if (!(defaultKey instanceof String))
				continue;
			String key = (String) defaultKey;
			Object value = defaults.get(defaultKey);
			if (!(value instanceof Color))
				continue;
			Color color = (Color) value;
			String entry = key + " = " + describeColor(color);
			if (prefix != null && key.startsWith(prefix + "."))
				relevant.add(entry);
			if (color.equals(foreground) || color.equals(background))
				matches.add(entry);
		}

		Collections.sort(relevant);
		Collections.sort(matches);
		appendEntries(sb, "Likely " + ((prefix == null) ? "component" : prefix) + " defaults", relevant);
		appendEntries(sb, "Defaults matching the current colors", matches);
		return sb.toString();
	}

	private static String getDefaultsPrefix(Component component)
	{
		if (!(component instanceof JComponent))
			return null;
		String id = ((JComponent) component).getUIClassID();
		return id.endsWith("UI") ? id.substring(0, id.length() - 2) : id;
	}

	private static String describeColor(Color color)
	{
		if (color == null)
			return "null";
		if (color.getAlpha() == 255)
			return String.format("#%02X%02X%02X", color.getRed(), color.getGreen(), color.getBlue());
		return String.format("#%02X%02X%02X%02X", color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha());
	}

	private static String describeSource(boolean explicitlySet, Color color)
	{
		if (color instanceof UIResource)
			return " (look-and-feel UIResource)";
		return explicitlySet ? " (explicit component value)" : " (inherited component value)";
	}

	private static void appendEntries(StringBuilder sb, String title, List<String> entries)
	{
		sb.append("\n").append(title).append("\n");
		if (entries.isEmpty()) {
			sb.append("  None\n");
			return;
		}

		int count = Math.min(entries.size(), MAX_MATCHES);
		for (int i = 0; i < count; i++)
			sb.append("  ").append(entries.get(i)).append('\n');
		if (entries.size() > count)
			sb.append("  ... and ").append(entries.size() - count).append(" more\n");
	}
}
