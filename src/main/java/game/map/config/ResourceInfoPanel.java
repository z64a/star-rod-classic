package game.map.config;

import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import game.map.config.MapConfigTable.Resource;
import net.miginfocom.swing.MigLayout;
import shared.SwingUtils;
import util.ui.LimitedLengthDocument;

public class ResourceInfoPanel extends JPanel
{
	// resource properties
	private JTextField nameField;
	private JTextField nicknameField;
	private JCheckBox cbCompressed;

	private Resource currentResource;

	public ResourceInfoPanel(LevelEditor editor)
	{
		nameField = new JTextField(5);
		nameField.setColumns(8);
		nameField.setDocument(new LimitedLengthDocument(15));
		nameField.getDocument().addDocumentListener(new DocumentListener() {
			@Override
			public void changedUpdate(DocumentEvent e)
			{
				updateName();
			}

			@Override
			public void insertUpdate(DocumentEvent e)
			{
				updateName();
			}

			@Override
			public void removeUpdate(DocumentEvent e)
			{
				updateName();
			}

			private void updateName()
			{
				if (currentResource == null)
					return;
				currentResource.name = nameField.getText();
				editor.validateNames();
				editor.repaintResourceTree();
				editor.modified();
			}
		});
		SwingUtils.addBorderPadding(nameField);

		nicknameField = new JTextField();
		nicknameField.getDocument().addDocumentListener(new DocumentListener() {
			@Override
			public void changedUpdate(DocumentEvent e)
			{
				updateName();
			}

			@Override
			public void insertUpdate(DocumentEvent e)
			{
				updateName();
			}

			@Override
			public void removeUpdate(DocumentEvent e)
			{
				updateName();
			}

			private void updateName()
			{
				if (currentResource == null)
					return;
				currentResource.nickname = nicknameField.getText();
				editor.repaintResourceTree();
				editor.modified();
			}
		});
		SwingUtils.addBorderPadding(nicknameField);

		cbCompressed = new JCheckBox(" Compressed");
		cbCompressed.setToolTipText("Is this resource Yay0 compressed?");
		cbCompressed.addChangeListener((e) -> {
			if (currentResource == null)
				return;
			editor.modified();
			currentResource.compressed = cbCompressed.isSelected();
		});

		setLayout(new MigLayout("fill, wrap 2", "[]8[grow]"));

		add(new JLabel("Nickname"));
		add(nicknameField, "w 240!");

		add(new JLabel("Engine Name"));
		add(nameField, "sg fields");

		add(new JLabel());
		add(cbCompressed);
	}

	public void setResource(Resource res)
	{
		currentResource = res;

		nameField.setText(res.name);
		nicknameField.setText(res.nickname);
		cbCompressed.setSelected(res.compressed);
	}
}
