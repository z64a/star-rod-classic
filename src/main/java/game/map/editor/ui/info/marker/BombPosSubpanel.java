package game.map.editor.ui.info.marker;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;

import app.SwingUtils;
import game.map.editor.MapEditor;
import game.map.marker.BombPosComponent;
import game.map.marker.Marker.MarkerType;
import net.miginfocom.swing.MigLayout;
import util.ui.FloatTextField;

public class BombPosSubpanel extends JPanel
{
	private final MarkerInfoPanel parent;

	private FloatTextField radiusField;

	public BombPosSubpanel(MarkerInfoPanel parent)
	{
		this.parent = parent;

		radiusField = new FloatTextField((radius) -> MapEditor.execute(
			parent.getData().bombPosComponent.radius.mutator(radius)));
		radiusField.setHorizontalAlignment(JTextField.CENTER);

		setLayout(new MigLayout("fillx, ins 0", MarkerInfoPanel.FOUR_COLUMNS));

		add(SwingUtils.getLabel("Bomb Target", 14), "growx, span, wrap, gapbottom 4");

		add(new JLabel("Radius"), "sgy row");
		add(radiusField, "growx");
		add(new JPanel(), "growx");
		add(new JPanel(), "growx, wrap");

		add(new JPanel(), "h 16lp!, span, wrap");

		//		add(new JLabel("The approximate physical size of the destructible blast target."), "span, wrap");
		//		add(new JLabel("Setting it to 0 is sufficient for 'small' objects like tree trunks."), "span, wrap");
		//		add(new JLabel("Bombette's blast radius alone is 50 units."), "span, wrap");
	}

	public void updateFields()
	{
		BombPosComponent volume = parent.getData().bombPosComponent;
		MarkerType type = parent.getData().getType();

		radiusField.setValue(volume.radius.get());
	}
}
