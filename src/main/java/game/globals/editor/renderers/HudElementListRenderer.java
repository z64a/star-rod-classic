package game.globals.editor.renderers;

import java.awt.Component;

import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.ListCellRenderer;
import javax.swing.SwingConstants;

import app.IconResource;
import game.globals.editor.GlobalsData;
import game.texture.images.HudElementRecord;
import net.miginfocom.swing.MigLayout;
import shared.SwingUtils;

public class HudElementListRenderer extends JPanel implements ListCellRenderer<HudElementRecord>
{
	private final GlobalsData globals;
	private final JLabel iconLabel;
	private final JLabel nameLabel;

	public HudElementListRenderer(GlobalsData globals)
	{
		this.globals = globals;
		iconLabel = new JLabel(IconResource.CROSS_16, SwingConstants.CENTER);
		nameLabel = new JLabel();

		setLayout(new MigLayout("ins 0, fill"));
		add(iconLabel, "w 32!, h 16!");
		add(nameLabel, "growx, pushx, gapright push");

		setOpaque(true);
		setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
	}

	@Override
	public Component getListCellRendererComponent(
		JList<? extends HudElementRecord> list,
		HudElementRecord obj,
		int index,
		boolean isSelected,
		boolean cellHasFocus)
	{
		if (isSelected) {
			setBackground(list.getSelectionBackground());
			setForeground(list.getSelectionForeground());
		}
		else {
			setBackground(list.getBackground());
			setForeground(list.getForeground());
		}

		if (obj != null) {
			ImageIcon preview = globals.getSmallPreviewImage(obj.previewImageName);
			iconLabel.setIcon((preview != null) ? preview : IconResource.CROSS_16);
			nameLabel.setText(obj.identifier + (obj.getModified() ? " *" : ""));

			nameLabel.setForeground(obj.getModified() && !isSelected ? SwingUtils.getBlueTextColor() : null);
		}
		else {
			iconLabel.setIcon(IconResource.CROSS_16);
			nameLabel.setText("Missing!");

			nameLabel.setForeground(SwingUtils.getRedTextColor());
		}

		return this;
	}
}
