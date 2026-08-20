package game.sprite.editor;

import java.awt.Dimension;
import java.awt.Frame;
import java.awt.Point;
import java.awt.Toolkit;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingConstants;

import app.SwingUtils;
import game.sprite.SpriteRaster;
import net.miginfocom.swing.MigLayout;

public class RasterSelectDialog extends JDialog
{
	// this dialog remembers its previous location when reopened
	private static Point prevLocation = null;

	private SpriteRaster selected = null;

	public RasterSelectDialog(Frame owner, DefaultListModel<SpriteRaster> rasters)
	{
		super(owner, "Choose Raster", true);
		setDefaultCloseOperation(DISPOSE_ON_CLOSE);

		int numRasters = 0;
		for (int i = 0; i < rasters.size(); i++) {
			if (!rasters.get(i).isSpecial)
				numRasters++;
		}

		int columns = 5;
		if (numRasters > 25)
			columns = (int) Math.sqrt(numRasters);
		if (columns > 9)
			columns = 9;

		JPanel gridPanel = new JPanel(new MigLayout("wrap " + columns + ", gap 10"));

		for (int i = 0; i < rasters.size(); i++) {
			SpriteRaster raster = rasters.get(i);
			if (raster.isSpecial)
				continue;

			JButton button = new JButton(raster.icon);
			button.setText(raster.toString());
			button.setVerticalTextPosition(SwingConstants.BOTTOM);
			button.setHorizontalTextPosition(SwingConstants.CENTER);
			button.setContentAreaFilled(false);
			button.setBorder(BorderFactory.createEmptyBorder());
			button.addActionListener((e) -> {
				selected = raster;
				dispose();
			});

			button.addMouseListener(new MouseAdapter() {
				@Override
				public void mouseEntered(MouseEvent e)
				{
					button.setForeground(SwingUtils.getBlueTextColor());
				}

				@Override
				public void mouseExited(MouseEvent e)
				{
					button.setForeground(null);
				}
			});

			gridPanel.add(button);
		}

		JScrollPane scrollPane = new JScrollPane(gridPanel);
		scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
		scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		scrollPane.getVerticalScrollBar().setUnitIncrement(20);

		Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
		int maxHeight = (int) (screenSize.height * 0.70);

		setLayout(new MigLayout("fill, insets 10, wrap"));
		add(scrollPane, "grow, push, hmax " + maxHeight);
		pack();
	}

	public SpriteRaster getSelected()
	{
		return selected;
	}

	@Override
	public void setVisible(boolean visible)
	{
		if (visible) {
			if (prevLocation == null)
				setLocationRelativeTo(getOwner());
			else
				setLocation(prevLocation);
		}
		super.setVisible(visible);
	}

	@Override
	public void dispose()
	{
		if (isShowing())
			prevLocation = getLocation();
		super.dispose();
	}
}
