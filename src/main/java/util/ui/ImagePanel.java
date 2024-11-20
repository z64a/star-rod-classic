package util.ui;

import java.awt.Graphics;
import java.awt.image.BufferedImage;

import javax.swing.JPanel;

public class ImagePanel extends JPanel
{
	private BufferedImage image = null;

	public ImagePanel()
	{}

	public void setImage(BufferedImage image)
	{
		this.image = image;
		repaint();
	}

	@Override
	protected void paintComponent(Graphics g)
	{
		super.paintComponent(g);
		if (image != null)
			g.drawImage(image, 0, 0, this);
	}
}
