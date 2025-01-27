package game.globals.editor.renderers;

import java.awt.Component;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.ListCellRenderer;
import javax.swing.SwingConstants;

import app.IconResource;
import app.SwingUtils;
import game.globals.editor.GlobalsData;
import game.texture.images.ImageRecord;
import net.miginfocom.swing.MigLayout;

public class ImageAssetBoxRenderer extends JPanel implements ListCellRenderer<String>
{
	private final GlobalsData globals;
	private final JLabel iconLabel;
	private final JLabel nameLabel;

	public ImageAssetBoxRenderer(GlobalsData globals)
	{
		this.globals = globals;
		iconLabel = new JLabel(IconResource.CROSS_16, SwingConstants.CENTER);
		nameLabel = new JLabel();

		setLayout(new MigLayout("ins 0, fill"));
		add(iconLabel, "w 16!, h 16!");
		add(nameLabel, "growx, pushx, gapright push");

		setOpaque(true);
		setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
	}

	@Override
	public Component getListCellRendererComponent(
		JList<? extends String> list,
		String text,
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

		ImageRecord img = globals.images.getElement(text);

		if (img != null) {
			iconLabel.setIcon(img.smallPreview[0]);
			nameLabel.setText(text);
			nameLabel.setForeground((img.preview[0] == null) ? SwingUtils.getRedTextColor() : null);
		}
		else {
			iconLabel.setIcon(IconResource.CROSS_16);
			nameLabel.setText(text);
			nameLabel.setForeground(SwingUtils.getRedTextColor());
		}

		return this;
	}
}
