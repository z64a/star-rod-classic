package game.map.editor.ui.info.marker;

import javax.swing.JPanel;

public class VolumeSubpanel extends JPanel
{
	//TODO
	/*
	private final MarkerInfoPanel parent;
	
	private JLabel heightLabel;
	private JLabel radiusLabel;
	private FloatTextField radiusField;
	private FloatTextField heightField;
	
	public VolumeSubpanel(MarkerInfoPanel parent)
	{
		this.parent = parent;
	
		radiusField = new FloatTextField((radius) -> MapEditor.execute(
			parent.getData().volumeComponent.radius.mutator(radius)));
		radiusField.setHorizontalAlignment(SwingConstants.CENTER);
	
		heightField = new FloatTextField((radius) -> MapEditor.execute(
			parent.getData().volumeComponent.height.mutator(radius)));
		heightField.setHorizontalAlignment(SwingConstants.CENTER);
	
		setLayout(new MigLayout("fillx, ins 0", MarkerInfoPanel.FOUR_COLUMNS));
	
		radiusLabel = new JLabel("Radius");
		add(radiusLabel);
		add(radiusField, "growx, wrap");
	
		heightLabel = new JLabel("Height");
		add(heightLabel);
		add(heightField, "growx, wrap");
	}
	
	public void updateFields()
	{
		VolumeComponent volume = parent.getData().volumeComponent;
		MarkerType type = parent.getData().getType();
	
		if (type == MarkerType.Sphere) {
			heightField.setVisible(false);
			heightLabel.setVisible(false);
			radiusField.setVisible(true);
			radiusLabel.setVisible(true);
			radiusField.setValue(volume.radius.get());
		}
		else if (type == MarkerType.Cylinder) {
			heightField.setVisible(true);
			heightLabel.setVisible(true);
			radiusField.setVisible(true);
			radiusLabel.setVisible(true);
			radiusField.setValue(volume.radius.get());
			heightField.setValue(volume.height.get());
		}
		else {
			heightField.setVisible(false);
			heightLabel.setVisible(false);
			radiusField.setVisible(false);
			radiusLabel.setVisible(false);
		}
	}
	*/
}
