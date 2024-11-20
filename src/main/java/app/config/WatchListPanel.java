package app.config;

import java.awt.Toolkit;

import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;

import app.StarRodException;
import app.config.WatchListEntry.Category;
import app.config.WatchListEntry.MemoryType;
import net.miginfocom.swing.MigLayout;
import shared.SwingUtils;
import util.ui.HexTextField;
import util.ui.StandardInputField;

public class WatchListPanel extends JPanel
{
	private WatchListEntry entry;

	public WatchListPanel(String entryText)
	{
		try {
			entry = new WatchListEntry(entryText);
		}
		catch (StarRodException e) {
			entry = new WatchListEntry();
			Toolkit.getDefaultToolkit().beep();
		}

		JLabel lblSize = SwingUtils.getLabel("Size", 12);
		JLabel lblAddr = SwingUtils.getLabel("Address", 12);

		HexTextField addrField = new HexTextField((v) -> {
			entry.addr = v;
		});
		SwingUtils.addBorderPadding(addrField);

		JTextField nameField = new StandardInputField((text) -> {
			entry.name = text;
		});

		JComboBox<MemoryType> memoryBox = new JComboBox<>(MemoryType.values());
		memoryBox.addActionListener((e) -> {
			entry.memType = (MemoryType) memoryBox.getSelectedItem();
		});
		SwingUtils.addBorderPadding(memoryBox);

		JComboBox<Category> categoryBox = new JComboBox<>(Category.values());
		categoryBox.addActionListener((e) -> {
			entry.category = (Category) categoryBox.getSelectedItem();
			switch (entry.category) {
				case Memory:
					lblSize.setVisible(true);
					memoryBox.setVisible(true);
					lblAddr.setVisible(true);
					addrField.setVisible(true);
					break;
				case Variable:
				case Clear:
					lblSize.setVisible(false);
					memoryBox.setVisible(false);
					lblAddr.setVisible(false);
					addrField.setVisible(false);
					break;
			}
			revalidate();
		});
		SwingUtils.addBorderPadding(categoryBox);

		nameField.setText(entry.name);
		categoryBox.setSelectedItem(entry.category);
		memoryBox.setSelectedItem(entry.memType);
		addrField.setValue(entry.addr);

		setLayout(new MigLayout("fill, hidemode 0"));

		add(SwingUtils.getLabel("Name", 12));
		add(nameField, "growx, pushx, wrap");

		add(SwingUtils.getLabel("Category", 12));
		add(categoryBox, "growx, pushx, wrap");

		add(lblSize);
		add(memoryBox, "growx, pushx, wrap");

		add(lblAddr);
		add(addrField, "growx, pushx, wrap");

		add(new JPanel(), "growy, pushy");
	}

	public String getText()
	{
		return entry.getText();
	}
}
