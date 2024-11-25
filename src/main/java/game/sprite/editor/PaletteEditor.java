package game.sprite.editor;

import java.awt.Color;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.border.EtchedBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;

import app.SwingUtils;
import game.map.editor.ui.SwatchPanel;
import game.sprite.Sprite;
import game.sprite.SpritePalette;
import net.miginfocom.swing.MigLayout;
import util.ui.ColorSlider;
import util.ui.ColorSlider.SliderListener;
import util.ui.HexTextField;
import util.ui.ListAdapterComboboxModel;

public class PaletteEditor extends JPanel
{
	private JPanel paletteInfoPanel;
	private SwatchPanel colorPreview;
	private HexTextField colorHexValue;
	private JPanel rgbaPanel;
	private ColorSlider channelR, channelG, channelB;
	private ColorSlider channelH, channelS, channelV;
	private ColorSlider channelA;

	private static enum ColorModel
	{
		RGB, HSL
	}

	private ColorModel selectedColorModel = ColorModel.RGB;

	private PaletteSwatchPanel swatchesPanel;
	private final JComboBox<SpritePalette> paletteBox;

	private int paletteColorIndex = 1;
	private Sprite sprite = null;

	private boolean ignoreChanges = false;
	private boolean ignoreSliderUpdates = false;
	private boolean ignoreTextfieldUpdates = false;

	public PaletteEditor(SpriteEditor editor)
	{
		JTextField nameField = new JTextField();
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
				SpritePalette selectedPal = (SpritePalette) paletteBox.getSelectedItem();

				if (ignoreChanges || selectedPal == null || name.isEmpty())
					return;

				selectedPal.name = name;
				for (ListDataListener listener : sprite.palettes.getListDataListeners()) {
					listener.contentsChanged(new ListDataEvent(sprite.palettes,
						ListDataEvent.CONTENTS_CHANGED,
						selectedPal.getIndex(), selectedPal.getIndex()));
				}
			}
		});

		paletteBox = new JComboBox<>();
		SwingUtils.setFontSize(paletteBox, 14);
		paletteBox.setMaximumRowCount(24);
		paletteBox.setRenderer(new PaletteSwatchesRenderer(16));
		paletteBox.addActionListener((e) -> {
			if (ignoreChanges)
				return;
			SpritePalette selectedPal = (SpritePalette) paletteBox.getSelectedItem();
			swatchesPanel.setPalette(selectedPal);
			if (selectedPal != null)
				nameField.setText(selectedPal.name);
			paletteInfoPanel.setVisible(selectedPal != null);
		});

		JRadioButton rgbButton = new JRadioButton(ColorModel.RGB.toString());
		rgbButton.setSelected(true);
		rgbButton.addActionListener((e) -> {
			if (rgbButton.isSelected())
				setColorModel(ColorModel.RGB);
		});

		JRadioButton hslButton = new JRadioButton(ColorModel.HSL.toString());
		hslButton.setSelected(false);
		hslButton.addActionListener((e) -> {
			if (hslButton.isSelected())
				setColorModel(ColorModel.HSL);
		});

		ButtonGroup group = new ButtonGroup();
		group.add(rgbButton);
		group.add(hslButton);

		// update the color preview when the sliders are adjusted
		SliderListener colorPreviewListener = (preview, value) -> {
			if (ignoreSliderUpdates)
				return;

			int[] rgb = new int[3];

			switch (selectedColorModel) {
				case RGB:
					rgb[0] = channelR.getValue();
					rgb[1] = channelG.getValue();
					rgb[2] = channelB.getValue();
					break;
				case HSL:
					int[] hsl = new int[3];
					hsl[0] = channelH.getValue();
					hsl[1] = channelS.getValue();
					hsl[2] = channelV.getValue();
					rgb = HSLtoRGB(hsl);
					break;
				default:
					throw new RuntimeException("Unknown color model.");
			}

			int a = channelA.getValue();
			Color c = new Color(rgb[0], rgb[1], rgb[2], a);

			int packedRGB = (c.getRGB() << 8) & 0xFFFFFF00;
			colorHexValue.setValue(packedRGB | a);

			swatchesPanel.swatches[paletteColorIndex].setForeground(c);
			colorPreview.setForeground(c);

			SpritePalette selectedPal = (SpritePalette) paletteBox.getSelectedItem();
			if (selectedPal != null) {
				selectedPal.pal.setColor(paletteColorIndex, rgb[0], rgb[1], rgb[2], a);
				for (ListDataListener listener : sprite.palettes.getListDataListeners()) {
					listener.contentsChanged(new ListDataEvent(sprite.palettes,
						ListDataEvent.CONTENTS_CHANGED,
						selectedPal.getIndex(), selectedPal.getIndex()));
				}
				selectedPal.dirty = true;
				selectedPal.modified = true;
				editor.repaintRasterPreview();
			}
		};

		colorHexValue = new HexTextField(8, true, (e) -> {
			if (ignoreTextfieldUpdates)
				return;

			int rgba = colorHexValue.getValue();
			int r = (rgba >>> 24) & 0xFF;
			int g = (rgba >>> 16) & 0xFF;
			int b = (rgba >>> 8) & 0xFF;
			int a = (rgba >>> 0) & 0xFF;

			Color c = new Color(r, g, b, a);

			ignoreTextfieldUpdates = true;
			setSelectedColor(c);
			ignoreTextfieldUpdates = false;

			// all needed below here?

			swatchesPanel.swatches[paletteColorIndex].setForeground(c);
			colorPreview.setForeground(c);

			SpritePalette selectedPal = (SpritePalette) paletteBox.getSelectedItem();
			if (selectedPal != null) {
				selectedPal.pal.setColor(paletteColorIndex, r, g, b, a);
				for (ListDataListener listener : sprite.palettes.getListDataListeners()) {
					listener.contentsChanged(new ListDataEvent(sprite.palettes,
						ListDataEvent.CONTENTS_CHANGED,
						selectedPal.getIndex(), selectedPal.getIndex()));
				}
				selectedPal.dirty = true;
				selectedPal.modified = true;
				editor.repaintRasterPreview();
			}
		});

		colorHexValue.setBorder(BorderFactory.createCompoundBorder(
			colorHexValue.getBorder(),
			BorderFactory.createEmptyBorder(2, 4, 2, 4)));
		colorHexValue.setHorizontalAlignment(JTextField.CENTER);
		SwingUtils.setFontSize(colorHexValue, 12.0f);

		channelR = new ColorSlider("R", "w 30!", 0, 255, 255, 32, colorPreviewListener);
		channelG = new ColorSlider("G", "w 30!", 0, 255, 255, 32, colorPreviewListener);
		channelB = new ColorSlider("B", "w 30!", 0, 255, 255, 32, colorPreviewListener);

		channelH = new ColorSlider("H", "w 30!", 0, hmax, hmax, 600, colorPreviewListener);
		channelS = new ColorSlider("S", "w 30!", 0, smax, smax, 100, colorPreviewListener);
		channelV = new ColorSlider("L", "w 30!", 0, lmax, lmax, 100, colorPreviewListener);

		channelH.setVisible(false);
		channelS.setVisible(false);
		channelV.setVisible(false);

		channelA = new ColorSlider("A", "w 30!", 0, 255, 255, 32, colorPreviewListener);

		rgbaPanel = new JPanel(new MigLayout("fill, wrap 3, hidemode 3"));
		rgbaPanel.setBorder(BorderFactory.createEtchedBorder(EtchedBorder.LOWERED));

		rgbaPanel.add(SwingUtils.getLabel("Color Model:", 12), "span, split 3, gapright 10");
		rgbaPanel.add(rgbButton, "w 50!, sg radio");
		rgbaPanel.add(hslButton, "w 50!, sg radio");

		rgbaPanel.add(channelR, "wrap");
		rgbaPanel.add(channelG, "wrap");
		rgbaPanel.add(channelB, "wrap");

		rgbaPanel.add(channelH, "wrap");
		rgbaPanel.add(channelS, "wrap");
		rgbaPanel.add(channelV, "wrap");

		rgbaPanel.add(channelA, "wrap");

		colorPreview = new SwatchPanel(1.32f, 1.33f);
		swatchesPanel = new PaletteSwatchPanel(this);

		JPanel p2 = new JPanel(new MigLayout("ins 0, fill, wrap"));
		p2.add(colorPreview, "h 32!, w 66%, split 2");
		//p2.add(SwingUtils.getLabel("#", 12));
		p2.add(colorHexValue, "h 32!, growx, pushx");
		p2.add(rgbaPanel, "gaptop 8");

		paletteInfoPanel = new JPanel(new MigLayout("ins 0, fill, wrap"));

		paletteInfoPanel.add(SwingUtils.getLabel("Palette Name:", 12), "gaptop 8");
		paletteInfoPanel.add(nameField, "w 200!, gapleft 8");

		paletteInfoPanel.add(SwingUtils.getLabel("Edit Colors:", 12), "gaptop 12");

		paletteInfoPanel.add(swatchesPanel, "split 3, gapleft 16, gaptop 8, gapbottom push");
		paletteInfoPanel.add(p2, "gapleft 24, gaptop 8");

		setLayout(new MigLayout("fillx, insets 8, wrap, hidemode 3"));
		add(paletteBox, "gapbottom 8, grow");
		add(paletteInfoPanel);
	}

	public void setSpriteEDT(Sprite sprite)
	{
		assert (SwingUtilities.isEventDispatchThread());
		this.sprite = sprite;

		paletteBox.setModel(new ListAdapterComboboxModel<>(sprite.palettes));

		if (!sprite.palettes.isEmpty())
			paletteBox.setSelectedIndex(0);
	}

	public SpritePalette getOverridePalette()
	{
		return (SpritePalette) paletteBox.getSelectedItem();
	}

	private void setSelectedColor(Color c)
	{
		ignoreSliderUpdates = true;

		switch (selectedColorModel) {
			case RGB:
				channelR.setValue(c.getRed());
				channelG.setValue(c.getGreen());
				channelB.setValue(c.getBlue());
				break;
			case HSL:
				int[] hsl = RGBtoHSL(new int[] { c.getRed(), c.getGreen(), c.getBlue() });
				channelH.setValue(hsl[0]);
				channelS.setValue(hsl[1]);
				channelV.setValue(hsl[2]);
				break;
			default:
				throw new RuntimeException("Unknown color model.");
		}

		channelA.setValue(c.getAlpha());
		ignoreSliderUpdates = false;

		if (!ignoreTextfieldUpdates) {
			int argb = c.getRGB();
			int rgb = (argb << 8) & 0xFFFFFF00;
			int a = (argb >>> 24) & 0xFF;
			colorHexValue.setValue(rgb | a);
		}

		colorPreview.setForeground(c);
	}

	private void setColorModel(ColorModel mdl)
	{
		if (mdl == selectedColorModel)
			return;

		switch (mdl) {
			case RGB:
				channelR.setVisible(true);
				channelG.setVisible(true);
				channelB.setVisible(true);
				channelH.setVisible(false);
				channelS.setVisible(false);
				channelV.setVisible(false);
				int[] rgb = HSLtoRGB(new int[] { channelH.getValue(), channelS.getValue(), channelV.getValue() });
				channelR.setValue(rgb[0]);
				channelG.setValue(rgb[1]);
				channelB.setValue(rgb[2]);
				break;
			case HSL:
				channelR.setVisible(false);
				channelG.setVisible(false);
				channelB.setVisible(false);
				channelH.setVisible(true);
				channelS.setVisible(true);
				channelV.setVisible(true);
				int[] hsl = RGBtoHSL(new int[] { channelR.getValue(), channelG.getValue(), channelB.getValue() });
				channelH.setValue(hsl[0]);
				channelS.setValue(hsl[1]);
				channelV.setValue(hsl[2]);
				break;
			default:
				throw new RuntimeException("Unknown color model.");
		}

		selectedColorModel = mdl;
		rgbaPanel.revalidate();
	}

	// sliders are integer-valued, so HSL colors are represented with the following
	// integer ranges, which have been chosen to make RGB -> HSL -> RGB invariant.
	// H: 0 - 3600 (0.0 - 360.0)
	// S: 0 - 1000 (0.000 - 1.000)
	// L: 0 - 255
	private static final int hmax = 3600;
	private static final int smax = 1000;
	private static final int lmax = 1000;

	// RGB <-> HSL conversion adapted from Mohsen on stackoverflow
	// https://stackoverflow.com/questions/2353211/hsl-to-rgb-color-conversion

	private static int[] RGBtoHSL(int[] rgb)
	{
		double r = rgb[0] / 255.0;
		double g = rgb[1] / 255.0;
		double b = rgb[2] / 255.0;
		double h, s, l;

		double max = Math.max(Math.max(r, g), b);
		double min = Math.min(Math.min(r, g), b);
		double diff = max - min;

		l = (max + min) / 2;

		if (Math.abs(diff) < 1e-4)
			return new int[] { 0, 0, (int) Math.round(l * lmax) };

		if (l > 0.5)
			s = diff / (2 - max - min);
		else
			s = diff / (max + min);

		if (max == r)
			h = (g - b) / diff + (g < b ? 6.0 : 0.0);
		else if (max == g)
			h = (b - r) / diff + 2.0;
		else
			h = (r - g) / diff + 4.0;
		h = h / 6.0;

		return new int[] {
				(int) Math.round(h * hmax),
				(int) Math.round(s * smax),
				(int) Math.round(l * lmax)
		};
	}

	private static int[] HSLtoRGB(int[] hsl)
	{
		double h = (double) hsl[0] / hmax;
		double s = (double) hsl[1] / smax;
		double l = (double) hsl[2] / lmax;

		if (Math.abs(s) < 1e-4) {
			int v = (int) Math.round(l * 255);
			return new int[] { v, v, v };
		}

		double q = l < 0.5 ? l * (1.0 + s) : l + s - l * s;
		double p = 2.0 * l - q;

		double r = hue2rgb(p, q, h + 1.0 / 3.0);
		double g = hue2rgb(p, q, h);
		double b = hue2rgb(p, q, h - 1.0 / 3.0);

		return new int[] {
				(int) Math.round(r * 255),
				(int) Math.round(g * 255),
				(int) Math.round(b * 255)
		};
	}

	private static double hue2rgb(double p, double q, double t)
	{
		if (t < 0.0)
			t += 1.0;
		if (t > 1.0)
			t -= 1.0;
		if (t < 1.0 / 6.0)
			return p + (q - p) * 6.0 * t;
		if (t < 1.0 / 2.0)
			return q;
		if (t < 2.0 / 3.0)
			return p + (q - p) * (2.0 / 3.0 - t) * 6.0;
		return p;
	}

	private static class PaletteSwatchPanel extends JPanel
	{
		private final PaletteEditor palEditor;
		private SwatchPanel[] swatches;

		public PaletteSwatchPanel(PaletteEditor palEditor)
		{
			this.palEditor = palEditor;
			setLayout(new MigLayout("fill, ins 0"));

			swatches = new SwatchPanel[16];
			for (int i = 0; i < swatches.length; i++) {
				swatches[i] = new SwatchPanel(1.25f, 1.25f);
				add(swatches[i], "h 40!, w 40!, gapbottom 4, gapright 4" + (((i + 1) % 4 == 0) ? ", wrap" : ""));

				final int index = i;
				swatches[i].addMouseListener(new MouseAdapter() {
					@Override
					public void mouseClicked(MouseEvent e)
					{
						int prevIndex = palEditor.paletteColorIndex;
						palEditor.paletteColorIndex = index;
						palEditor.setSelectedColor(swatches[index].getForeground());

						if (prevIndex >= 0)
							swatches[prevIndex].setBorder(null);
						swatches[index].setBorder(BorderFactory.createDashedBorder(null));
					}
				});

			}
		}

		public void setPalette(SpritePalette value)
		{
			if (value == null) {
				setVisible(false);
				for (int i = 0; i < swatches.length; i++)
					swatches[i].setForeground(Color.gray);
			}
			else {
				setVisible(true);
				Color[] colors = value.pal.getColors();
				for (int i = 0; i < swatches.length; i++)
					swatches[i].setForeground(colors[i]);

				palEditor.setSelectedColor(colors[palEditor.paletteColorIndex]);
			}
		}
	}
}
