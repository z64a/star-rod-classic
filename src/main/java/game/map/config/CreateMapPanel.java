package game.map.config;

import javax.swing.BorderFactory;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;

import game.map.config.MapConfigTable.MapConfig;
import net.miginfocom.swing.MigLayout;
import shared.SwingUtils;
import util.ui.HexTextField;
import util.ui.LimitedLengthDocument;

public class CreateMapPanel extends JPanel
{
	private MapConfig map;
	private String defaultName;

	private JTextField mapNameField;
	private JTextField copyNameField;
	private JTextField bgField;
	private JTextField mapNicknameField;
	private JTextArea mapDescArea;
	private HexTextField flagsField;

	private JCheckBox cbScript;
	private JCheckBox cbShape;
	private JCheckBox cbHit;

	public CreateMapPanel(String defaultName)
	{
		this.defaultName = defaultName;

		mapNameField = new JTextField();
		mapNameField.setColumns(8);
		mapNameField.setMargin(SwingUtils.TEXTBOX_INSETS);
		mapNameField.setBorder(BorderFactory.createCompoundBorder(
			mapNameField.getBorder(),
			BorderFactory.createEmptyBorder(2, 4, 2, 4)));
		mapNameField.setDocument(new LimitedLengthDocument(8));

		mapNameField.setText(defaultName);

		bgField = new JTextField();
		bgField.setColumns(8);
		bgField.setMargin(SwingUtils.TEXTBOX_INSETS);
		bgField.setBorder(BorderFactory.createCompoundBorder(
			bgField.getBorder(),
			BorderFactory.createEmptyBorder(2, 4, 2, 4)));
		bgField.setDocument(new LimitedLengthDocument(8));

		mapNicknameField = new JTextField();
		mapNicknameField.setBorder(BorderFactory.createCompoundBorder(
			mapNicknameField.getBorder(),
			BorderFactory.createEmptyBorder(2, 4, 2, 4)));

		mapDescArea = new JTextArea();
		mapDescArea.setRows(5);
		mapDescArea.setLineWrap(true);
		mapDescArea.setBorder(BorderFactory.createCompoundBorder(
			mapDescArea.getBorder(),
			BorderFactory.createEmptyBorder(2, 4, 2, 4)));

		copyNameField = new JTextField();
		copyNameField.setColumns(8);
		copyNameField.setMargin(SwingUtils.TEXTBOX_INSETS);
		copyNameField.setBorder(BorderFactory.createCompoundBorder(
			copyNameField.getBorder(),
			BorderFactory.createEmptyBorder(2, 4, 2, 4)));
		copyNameField.setDocument(new LimitedLengthDocument(7));
		copyNameField.setToolTipText("Copy geometry and scripts from this map. Leave blank to use template map.");

		JScrollPane descScrollPane = new JScrollPane(mapDescArea);

		flagsField = new HexTextField((v) -> {});
		flagsField.setColumns(8);
		flagsField.setBorder(BorderFactory.createCompoundBorder(
			flagsField.getBorder(),
			BorderFactory.createEmptyBorder(2, 4, 2, 4)));

		cbScript = new JCheckBox(" Script Data");
		cbScript.setToolTipText("Does this map have script data?");

		cbShape = new JCheckBox(" Shape Data");
		cbShape.setToolTipText("Does this map have model data?");

		cbHit = new JCheckBox(" Hit Data");
		cbHit.setToolTipText("Does this map have collision data?");

		setLayout(new MigLayout("fill, wrap 2", "[]8[grow]"));

		add(new JLabel("Friendly Name:"));
		add(mapNicknameField, "growx");

		add(new JLabel("Engine Name:"));
		add(mapNameField);

		add(new JLabel("Background:"));
		add(bgField);

		add(new JLabel("Flags:"));
		add(flagsField, "gapbottom 8");

		add(new JLabel());
		add(cbScript);

		add(new JLabel());
		add(cbShape);

		add(new JLabel());
		add(cbHit);

		add(new JLabel("Description:"), "wrap, gaptop 4, gapbottom 4");
		add(descScrollPane, "span, grow");
	}

	public MapConfig createConfig(boolean isStage)
	{
		String name = mapNameField.getText();

		map = new MapConfig(name.isEmpty() ? defaultName : name, isStage);
		map.nickname = mapNicknameField.getText();
		map.desc = mapDescArea.getText();
		map.flags = flagsField.getValue();
		map.hasData = cbScript.isSelected();
		map.hasShape = cbShape.isSelected();
		map.hasHit = cbHit.isSelected();

		return map;
	}
}
