package game.map.config;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSeparator;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import game.map.config.MapConfigTable.AreaConfig;
import net.miginfocom.swing.MigLayout;
import shared.SwingUtils;
import util.ui.LimitedLengthDocument;

public class AreaInfoPanel extends JPanel
{
	// area properties
	private JTextField nameField;
	private JTextField nicknameField;

	private AreaConfig currentArea;

	public AreaInfoPanel(LevelEditor editor)
	{
		nameField = new JTextField(5);
		nameField.setColumns(8);
		nameField.setDocument(new LimitedLengthDocument(3));
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
				if (currentArea == null)
					return;
				currentArea.name = nameField.getText();
				editor.validateNames();
				editor.repaintTrees();
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
				if (currentArea == null)
					return;
				currentArea.nickname = nicknameField.getText();
				editor.repaintTrees();
				editor.modified();
			}
		});
		SwingUtils.addBorderPadding(nicknameField);

		JButton createMapButton = new JButton("Add New Map");
		createMapButton.addActionListener((e) -> {
			if (currentArea == null)
				return;
			editor.addNewMap(currentArea);
		});

		JButton createStageButton = new JButton("Add New Stage");
		createStageButton.addActionListener((e) -> {
			if (currentArea == null)
				return;
			editor.addNewStage(currentArea);
		});

		setLayout(new MigLayout("fill, wrap 2", "[]8[grow]", "grow"));

		add(new JLabel("Nickname"));
		add(nicknameField, "w 240!");

		add(new JLabel("Engine Name"));
		add(nameField, "sg fields");

		add(new JSeparator(), "grow, span, wrap, gaptop 6, gapbottom 4");

		add(createMapButton, "span, split 2, grow");
		add(createStageButton, "grow");
	}

	public void setArea(AreaConfig area)
	{
		currentArea = area;

		nameField.setText(area.name);
		nicknameField.setText(area.nickname);
	}
}
