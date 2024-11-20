package game.sprite.editor;

import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

import javax.swing.BorderFactory;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import game.map.editor.render.TextureManager;
import game.sprite.Sprite;
import game.sprite.SpritePalette;
import game.sprite.SpriteRaster;
import net.miginfocom.swing.MigLayout;
import shared.SwingUtils;
import util.ui.ListAdapterComboboxModel;

public class RasterInfoPanel extends JPanel
{
	private final JPanel image;
	private final JLabel filenameLabel;
	private final JTextField nameField;
	private final JLabel sizeLabel;
	private final JComboBox<SpritePalette> defaultPaletteBox;

	private final int size = 160;

	private final SpriteEditor editor;
	private SpriteRaster sr = null;

	private boolean ignoreChanges = false;

	public RasterInfoPanel(SpriteEditor editor)
	{
		this.editor = editor;
		image = new JPanel() {
			@Override
			public void paintComponent(Graphics g)
			{
				super.paintComponent(g);

				Graphics2D g2 = (Graphics2D) g;
				g.drawImage(TextureManager.background, 0, 0, size, size, null);

				if (sr != null) {
					sr.previewImg = new BufferedImage(
						sr.defaultPal.pal.getIndexColorModel(),
						sr.previewImg.getRaster(), false, null);

					//	sr.previewImg = ImageConverter.getIndexedBufferedImage(sr.img, sr.defaultPal.pal);

					SwingUtils.centerAndFitImage(sr.previewImg, this, g2);
				}
			}
		};

		filenameLabel = SwingUtils.getLabel("", 14);
		sizeLabel = SwingUtils.getLabel("", 12);

		defaultPaletteBox = new JComboBox<>();
		SwingUtils.setFontSize(defaultPaletteBox, 14);
		defaultPaletteBox.setMaximumRowCount(24);
		defaultPaletteBox.setRenderer(new PaletteSlicesRenderer());
		defaultPaletteBox.addActionListener((e) -> {
			if (ignoreChanges)
				return;
			SpritePalette selectedPal = (SpritePalette) defaultPaletteBox.getSelectedItem();
			if (selectedPal != null && selectedPal != sr.defaultPal) {
				sr.defaultPal = selectedPal;
				sr.loadEditorImages();
				image.repaint();
			}
		});

		nameField = new JTextField();
		nameField.setBorder(BorderFactory.createCompoundBorder(
			nameField.getBorder(),
			BorderFactory.createEmptyBorder(2, 4, 2, 4)));
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
				String name = nameField.getText().trim();
				if (ignoreChanges || sr == null || name.isEmpty())
					return;
				sr.name = name;
			}

		});

		setLayout(new MigLayout("fill"));
		add(image, String.format("w %d!, h %d!, gapright 16", size, size));

		JPanel generalInfo = new JPanel(new MigLayout("fill, ins 0, wrap"));
		generalInfo.add(filenameLabel);
		generalInfo.add(sizeLabel, "gapbottom 16");
		generalInfo.add(SwingUtils.getLabel("Raster Name:", 12), "gaptop push");
		generalInfo.add(nameField, "w 200!, gapbottom 8");
		generalInfo.add(SwingUtils.getLabel("Default Palette:", 12));
		generalInfo.add(defaultPaletteBox, "grow");
		add(generalInfo, "pushx, grow, top");
	}

	public void repaintImage()
	{
		image.repaint();
	}

	public void setSpriteEDT(Sprite sprite)
	{
		assert (SwingUtilities.isEventDispatchThread());
		defaultPaletteBox.setModel(new ListAdapterComboboxModel<>(sprite.palettes));

		SpriteRaster selected = null;
		for (int i = 0; i < sprite.rasters.size(); i++) {
			SpriteRaster sr = sprite.rasters.get(i);
			if (sr.selected)
				selected = sr;
		}
		editor.selectRasterEDT(selected);
	}

	public void setRasterEDT(SpriteRaster sr)
	{
		assert (SwingUtilities.isEventDispatchThread());

		this.sr = sr;
		image.repaint();

		ignoreChanges = true;

		if (sr != null) {
			filenameLabel.setText(sr.filename);
			sizeLabel.setText(sr.img.width + " x " + sr.img.height);
			defaultPaletteBox.setSelectedItem(sr.defaultPal);
			nameField.setText((sr.name == null) ? "" : sr.name);
		}
		else {
			filenameLabel.setText("none");
			sizeLabel.setText(" ");
			nameField.setText("");
		}

		ignoreChanges = false;
	}

	public SpriteRaster getDisplayedRaster()
	{
		return sr;
	}
}
