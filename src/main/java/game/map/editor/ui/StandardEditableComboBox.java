package game.map.editor.ui;

import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

import javax.swing.DefaultComboBoxModel;
import javax.swing.JComboBox;

import shared.SwingUtils;

public class StandardEditableComboBox extends JComboBox<String>
{
	public StandardEditableComboBox(Consumer<String> editCallback, String[] entries)
	{
		this(editCallback, Arrays.asList(entries));
	}

	public StandardEditableComboBox(Consumer<String> editCallback, List<String> entries)
	{
		super(new DefaultComboBoxModel<>());

		addActionListener((e) -> {
			editCallback.accept((String) getSelectedItem());
		});
		SwingUtils.addBorderPadding(this);

		setMaximumRowCount(16);
		setEditable(true);

		updateModel(entries);
	}

	public void updateModel(String[] entries)
	{
		updateModel(Arrays.asList(entries));
	}

	public void updateModel(List<String> entries)
	{
		DefaultComboBoxModel<String> model = (DefaultComboBoxModel<String>) getModel();

		String current = (String) getSelectedItem();

		model.removeAllElements();

		for (String s : entries)
			model.addElement(s);

		setSelectedItem(current);
	}

	public void setSelectedItem(String s)
	{
		super.setSelectedItem(s);

		boolean found = false;
		if (s != null) {
			for (int i = 0; i < getItemCount(); i++) {
				if (getItemAt(i).equals(s)) {
					found = true;
					break;
				}
			}
		}
		setForeground(found ? null : SwingUtils.getRedTextColor());
	}
}
