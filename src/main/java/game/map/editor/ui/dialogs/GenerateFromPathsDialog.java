package game.map.editor.ui.dialogs;

import java.awt.event.WindowEvent;
import java.util.LinkedList;
import java.util.List;
import java.util.function.BiConsumer;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JSeparator;
import javax.swing.JSlider;
import javax.swing.JSpinner;
import javax.swing.SpinnerModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.WindowConstants;
import javax.swing.event.ChangeListener;

import game.globals.editor.DialogResult;
import game.map.editor.MapEditor;
import game.map.editor.geometry.FromPathsGenerator;
import game.map.editor.render.PreviewDrawMode;
import game.map.editor.render.PreviewGeneratorFromPaths;
import game.map.marker.Marker;
import game.map.marker.Marker.MarkerType;
import game.map.marker.PathPoint;
import game.map.shape.TriangleBatch;
import game.map.tree.MapObjectNode;
import net.miginfocom.swing.MigLayout;
import shared.SwingUtils;
import util.IterableListModel;

public class GenerateFromPathsDialog extends JDialog
{
	public static final String FRAME_TITLE = "Generate From Selection Options";

	private final PreviewGeneratorFromPaths preview;

	private final SliderSpinner radiusSlider;
	private final SliderSpinner taperSlider;
	private final SliderSpinner twistSlider;
	private final SliderSpinner angleSlider;
	private final SliderSpinner segmentsSlider;
	private final JCheckBox cbOverrideRadius;
	private final JCheckBox cbTwistPerUnitLength;

	public GenerateFromPathsDialog(JFrame parent, PreviewGeneratorFromPaths preview,
		BiConsumer<DialogResult, TriangleBatch> onCloseCallback)
	{
		super(parent);
		this.preview = preview;
		preview.drawMode = PreviewDrawMode.FILLED;
		preview.useDepth = true;

		preview.setUpdate(() -> {
			List<Marker> paths = new LinkedList<>();
			for (Marker m : MapEditor.instance().selectionManager.getSelectedObjects(Marker.class)) {
				if (m.getType() == MarkerType.Path)
					paths.add(m);
			}

			preview.paths = paths;
			updatePreview();
		});

		JComboBox<PreviewDrawMode> drawModeBox = new JComboBox<>(PreviewDrawMode.values());
		drawModeBox.setSelectedItem(preview.drawMode);
		drawModeBox.addActionListener((e) -> {
			if (preview != null)
				preview.drawMode = (PreviewDrawMode) drawModeBox.getSelectedItem();
		});

		JCheckBox cbUseDepth = new JCheckBox("Depth");
		cbUseDepth.addActionListener((e) -> {
			if (preview != null)
				preview.useDepth = cbUseDepth.isSelected();
		});
		cbUseDepth.setIconTextGap(8);

		radiusSlider = new SliderSpinner("Radius", 1, 200, 64);
		radiusSlider.addChangeListener((e) -> updatePreview());

		taperSlider = new SliderSpinner("Taper", -100, 100, 0);
		taperSlider.addChangeListener((e) -> updatePreview());

		angleSlider = new SliderSpinner("Angle", -180, 180, 0);
		angleSlider.addChangeListener((e) -> updatePreview());

		twistSlider = new SliderSpinner("Twist", -90, 90, 0);
		twistSlider.addChangeListener((e) -> updatePreview());

		segmentsSlider = new SliderSpinner("Segments", 1, 32, 1);
		segmentsSlider.addChangeListener((e) -> updatePreview());

		cbOverrideRadius = new JCheckBox(" Use radii from edge path points");
		cbOverrideRadius.addActionListener((e) -> {
			boolean enabled = !cbOverrideRadius.isSelected();
			radiusSlider.setEnabled(enabled);
			taperSlider.setEnabled(enabled);
			updatePreview();
		});

		cbTwistPerUnitLength = new JCheckBox(" Use twist per length (200 units)");
		cbTwistPerUnitLength.addActionListener((e) -> updatePreview());

		cbOverrideRadius.setSelected(true);
		radiusSlider.setEnabled(false);
		taperSlider.setEnabled(false);

		JButton selectButton = new JButton("OK");
		SwingUtils.addBorderPadding(selectButton);
		selectButton.addActionListener((e) -> {
			onCloseCallback.accept(DialogResult.ACCEPT, preview.batch);
			preview.clear();
			setVisible(false);
		});

		JButton cancelButton = new JButton("Cancel");
		SwingUtils.addBorderPadding(cancelButton);
		cancelButton.addActionListener((e) -> {
			onCloseCallback.accept(DialogResult.CANCEL, null);
			preview.clear();
			setVisible(false);
		});

		setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
		addWindowListener(new java.awt.event.WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent e)
			{
				onCloseCallback.accept(DialogResult.CANCEL, null);
				preview.clear();
				setVisible(false);
			}
		});

		setLayout(new MigLayout("ins 16, fill, hidemode 3, wrap"));

		add(new JLabel("Draw Mode"), "split 3, w 25%!");
		add(drawModeBox, "growx");
		add(cbUseDepth, "growx, wrap, gapbottom 4");
		add(new JSeparator(), "growx, wrap, gapbottom 8");

		add(segmentsSlider, "span, growx");
		add(radiusSlider, "span, growx");
		add(taperSlider, "span, growx");
		add(angleSlider, "span, growx");
		add(twistSlider, "span, growx");
		add(cbOverrideRadius, "span, growx");
		add(cbTwistPerUnitLength, "span, growx");

		add(new JLabel(), "gaptop 16, growx, sg but, split 3");
		add(selectButton, "gaptop 16, growx, sg but");
		add(cancelButton, "gaptop 16, growx, sg but");

		setResizable(false);
	}

	public void beginPreview(MapObjectNode<?> parentObj)
	{
		preview.init();
		preview.visible = true;
		preview.parentObj = parentObj;
		updatePreview();
	}

	public void updatePreview()
	{
		preview.batch = generateTriangles();
	}

	public String getTypeName()
	{
		return (segmentsSlider.getValue() > 2) ? "Pipe" : "Ribbon";
	}

	public TriangleBatch generateTriangles()
	{
		if (preview.paths.size() < 2)
			return null;

		// get last two selected paths
		IterableListModel<PathPoint> pathA = preview.paths.get(preview.paths.size() - 2).pathComponent.path.points;
		IterableListModel<PathPoint> pathB = preview.paths.get(preview.paths.size() - 1).pathComponent.path.points;

		return FromPathsGenerator.generate(pathA, pathB,
			!cbOverrideRadius.isSelected(), cbTwistPerUnitLength.isSelected(),
			radiusSlider.getValue(), taperSlider.getValue(),
			angleSlider.getValue(), twistSlider.getValue(),
			segmentsSlider.getValue());
	}

	private static final class SliderSpinner extends JComponent
	{
		private JSpinner spinner;
		private JSlider slider;
		private boolean supressEvents = false;

		public int getValue()
		{
			return slider.getValue();
		}

		public SliderSpinner(String name, int minValue, int maxValue, int initialValue)
		{
			slider = new JSlider(minValue, maxValue, initialValue);
			slider.setMajorTickSpacing(0);
			slider.setMinorTickSpacing(0);
			slider.setPaintTicks(true); // necessary for windows LAF

			spinner = new JSpinner();
			spinner.setFont(spinner.getFont().deriveFont(12f));

			SpinnerModel model = new SpinnerNumberModel(initialValue, minValue, maxValue, 1);
			spinner.setModel(model);

			spinner.addChangeListener(evt -> {
				if (supressEvents)
					return;

				supressEvents = true;
				slider.setValue((Integer) spinner.getValue());
				supressEvents = false;
			});

			slider.addChangeListener(evt -> {
				if (supressEvents)
					return;

				supressEvents = true;
				spinner.setValue(slider.getValue());
				supressEvents = false;
			});

			JLabel label = new JLabel(name);
			SwingUtils.setFontSize(label, 12);

			setLayout(new MigLayout("insets 0"));
			add(label, "w 60!");
			add(slider);
			add(spinner, "w 60!");
		}

		public void addChangeListener(ChangeListener l)
		{
			slider.addChangeListener(l);
		}

		@Override
		public void setEnabled(boolean enabled)
		{
			slider.setEnabled(enabled);
			spinner.setEnabled(enabled);
		}
	}
}
