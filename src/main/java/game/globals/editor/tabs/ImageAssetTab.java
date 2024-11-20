package game.globals.editor.tabs;

import static app.Directories.MOD_IMG_ASSETS;

import java.awt.Color;
import java.awt.Desktop;
import java.awt.Toolkit;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map.Entry;

import javax.imageio.ImageIO;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JTextField;
import javax.swing.ListCellRenderer;
import javax.swing.SwingConstants;

import org.apache.commons.io.FilenameUtils;

import com.alexandriasoftware.swing.JSplitButton;

import app.StarRodException;
import app.input.IOUtils;
import game.globals.editor.GlobalsData.GlobalsCategory;
import game.globals.editor.GlobalsEditor;
import game.globals.editor.renderers.ImageAssetListRenderer;
import game.globals.editor.renderers.PaddedCellRenderer;
import game.map.editor.ui.dialogs.ChooseDialogResult;
import game.map.editor.ui.dialogs.OpenFileChooser;
import game.texture.TileFormat;
import game.texture.images.ImageRecord;
import game.texture.images.ImageRecord.ImageReference;
import net.miginfocom.swing.MigLayout;
import shared.SwingUtils;
import util.Logger;
import util.Pair;
import util.ui.HexTextField;
import util.ui.IntTextField;

public class ImageAssetTab extends SingleListTab<ImageRecord>
{
	private JLabel nameLabel;
	private JComboBox<TileFormat> fmtBox;
	private IntTextField palCountField;

	private JTextField sourceNameField;
	private OpenFileChooser imageFileChooser;

	private JCheckBox cbFlipVertically;
	private JCheckBox cbImageIsFixed;

	private JPanel romOffsetsPanel;
	private HexTextField imgOffsetField;
	private HexTextField palOffsetField;
	private IntTextField sizeWField;
	private IntTextField sizeHField;

	private static final int PREVIEW_COUNT = 8;
	private JLabel[] previewLabels;
	private JLabel[] displayLabels;
	private JLabel notShownLabel;

	public ImageAssetTab(GlobalsEditor editor, int tabIndex)
	{
		super(editor, tabIndex, editor.data.images);
	}

	@Override
	protected String getTabName()
	{
		return "Images";
	}

	@Override
	protected String getIconPath()
	{
		return "item/key/koot_photo";
	}

	@Override
	protected GlobalsCategory getDataType()
	{
		return GlobalsCategory.IMAGE_ASSETS;
	}

	@Override
	protected void notifyDataChange(GlobalsCategory type)
	{
		if (type != GlobalsCategory.IMAGE_ASSETS)
			return;

		reacquireSelection();
		repaintList();
	}

	@Override
	protected void addListButtons(JPanel listPanel)
	{
		JButton addButton = new JButton("Add Image");
		addButton.addActionListener((e) -> {
			actionAddImage();
		});
		SwingUtils.addBorderPadding(addButton);

		JSplitButton actionsButton = new JSplitButton("Actions  ");
		JPopupMenu actionsPopup = new JPopupMenu();
		actionsButton.setPopupMenu(actionsPopup);
		actionsButton.setAlwaysPopup(true);
		SwingUtils.addBorderPadding(actionsButton);
		JMenuItem menuItem;

		menuItem = new JMenuItem("Open Asset Directory");
		menuItem.setPreferredSize(POPUP_OPTION_SIZE);
		menuItem.addActionListener(e -> {
			actionOpenAssetDir();
		});
		actionsPopup.add(menuItem);

		menuItem = new JMenuItem("Import Missing");
		menuItem.setPreferredSize(POPUP_OPTION_SIZE);
		menuItem.addActionListener(evt -> {
			actionImportMissing();
		});
		actionsPopup.add(menuItem);

		listPanel.add(addButton, "span, split 2, grow, sg but");
		listPanel.add(actionsButton, "grow, sg but");
	}

	private void actionAddImage()
	{
		ImageRecord newImage = new ImageRecord();
		newImage.identifier = "NewImage";
		newImage.fmt = TileFormat.CI_4;
		updateImages(newImage, true);

		listModel.addElement(newImage);
		updateListFilter();

		list.setSelectedValue(newImage, false);
		list.ensureIndexIsVisible(list.getSelectedIndex());
		setModified();
	}

	private void actionImportMissing()
	{
		HashSet<String> includedImageNames = new HashSet<>();
		LinkedHashMap<String, Pair<Integer>> imagesToAdd = new LinkedHashMap<>();
		for (ImageRecord rec : listModel)
			includedImageNames.add(rec.identifier);
		try {
			for (File f : IOUtils.getFilesWithExtension(MOD_IMG_ASSETS, "png", true)) {
				String assetName = IOUtils.getRelativePath(MOD_IMG_ASSETS.toFile(), f);
				assetName = FilenameUtils.removeExtension(assetName);

				ImageReference ref = ImageRecord.parseImageName(assetName);
				if (ref == null)
					continue;

				if (!includedImageNames.contains(ref.name)) {
					if (!imagesToAdd.containsKey(ref.name)) {
						imagesToAdd.put(ref.name, new Pair<>(ref.index, ref.index));
					}
					else {
						Pair<Integer> indexRange = imagesToAdd.get(ref.name);
						if (ref.index < indexRange.first)
							indexRange.first = ref.index;
						if (ref.index > indexRange.second)
							indexRange.second = ref.index;
					}
				}
			}
		}
		catch (IOException e) {
			Logger.printStackTrace(e);
		}

		ImageRecord last = null;
		for (Entry<String, Pair<Integer>> entry : imagesToAdd.entrySet()) {
			Pair<Integer> indexRange = entry.getValue();
			if (indexRange.first != 0 || indexRange.second < 0)
				continue;

			ImageRecord newImage = new ImageRecord();
			newImage.identifier = entry.getKey();
			newImage.palCount = 1 + (indexRange.second - indexRange.first);

			// check base image file to estimate format
			try {
				BufferedImage bimg = ImageIO.read(new File(MOD_IMG_ASSETS.toString() + newImage.identifier + ".png"));
				if (bimg.getType() == BufferedImage.TYPE_BYTE_INDEXED) {
					newImage.fmt = TileFormat.CI_4;
				}
				else {
					newImage.fmt = TileFormat.IA_16;
					outer:
					for (int x = 0; x < bimg.getWidth(); x++)
						for (int y = 0; y < bimg.getHeight(); y++) {
							Color c = new Color(bimg.getRGB(x, y));
							if (c.getRed() != c.getGreen() || c.getRed() != c.getBlue() || c.getGreen() != c.getBlue()) {
								newImage.fmt = TileFormat.RGBA_16;
								break outer;
							}
						}
				}
			}
			catch (IOException e) {
				e.printStackTrace();
			}

			updateImages(newImage, true);
			listModel.addElement(newImage);
			newImage.setModified(true);
			last = newImage;

			Logger.log("Imported " + newImage.identifier + " as " + newImage.fmt);
		}

		if (!imagesToAdd.isEmpty()) {
			listModel.rebuildNameCache();
			updateListFilter();

			list.setSelectedValue(last, false);
			list.ensureIndexIsVisible(list.getSelectedIndex());
			setModified();
		}
	}

	private void actionOpenAssetDir()
	{
		if (!MOD_IMG_ASSETS.toFile().exists())
			throw new StarRodException("Can't find icon images directory: %n%s",
				MOD_IMG_ASSETS.toFile().getAbsolutePath());
		try {
			Desktop.getDesktop().open(MOD_IMG_ASSETS.toFile());
		}
		catch (IOException e1) {
			throw new StarRodException("Failed to open icon images directory: %n%s",
				MOD_IMG_ASSETS.toFile().getAbsolutePath());
		}
	}

	@Override
	protected JPanel createInfoPanel(JLabel infoLabel)
	{
		DefaultComboBoxModel<TileFormat> fmtBoxListModel = new DefaultComboBoxModel<>(TileFormat.values());

		File imgDir = MOD_IMG_ASSETS.toFile();
		imageFileChooser = new OpenFileChooser(imgDir, "Choose Image Asset", "Images", "png");

		JButton chooseSourceButton = new JButton("Choose");
		chooseSourceButton.addActionListener((e) -> {
			promptSelectImage(imgDir);
		});
		SwingUtils.addBorderPadding(chooseSourceButton);

		nameLabel = SwingUtils.getLabel("???", 18);

		fmtBox = new JComboBox<>();
		fmtBox.setModel(fmtBoxListModel);
		fmtBox.setRenderer(new PaddedCellRenderer<>());
		fmtBox.setMaximumRowCount(16);
		SwingUtils.addBorderPadding(fmtBox);

		fmtBox.addActionListener((e) -> {
			if (shouldIgnoreChanges())
				return;

			ImageRecord currentImage = getSelected();
			TileFormat newFormat = (TileFormat) fmtBox.getSelectedItem();

			if (currentImage != null && currentImage.fmt != newFormat) {
				currentImage.fmt = newFormat;
				setModified();
				repaintList();

				palCountField.setEditable(newFormat.type == TileFormat.TYPE_CI);
				palOffsetField.setEditable(newFormat.type == TileFormat.TYPE_CI);
			}
		});

		sourceNameField = new JTextField(20);
		sourceNameField.setEditable(false);
		sourceNameField.setMargin(SwingUtils.TEXTBOX_INSETS);
		SwingUtils.addBorderPadding(sourceNameField);

		palCountField = new IntTextField((v) -> {
			ImageRecord image = getSelected();
			if (shouldIgnoreChanges() || image == null)
				return;

			if (v < 1) {
				Logger.log("Palette count must always be greater than zero");
				Toolkit.getDefaultToolkit().beep();
				palCountField.setValue(image.palCount);
				return;
			}

			image.palCount = v;
			updateImages(image, true);
			setModified();
		});
		SwingUtils.addBorderPadding(palCountField);

		cbFlipVertically = new JCheckBox("Flip vertically when patching");
		cbFlipVertically.addActionListener((e) -> {
			if (shouldIgnoreChanges() || getSelected() == null)
				return;
			getSelected().flip = cbFlipVertically.isSelected();
			setModified();
		});
		cbFlipVertically.setIconTextGap(12);

		cbImageIsFixed = new JCheckBox("Has fixed ROM location");
		cbImageIsFixed.addActionListener((e) -> {
			ImageRecord currentImage = getSelected();
			if (shouldIgnoreChanges() || currentImage == null)
				return;

			currentImage.fixedPos = cbImageIsFixed.isSelected();
			romOffsetsPanel.setVisible(currentImage.fixedPos);
			setModified();
		});
		cbImageIsFixed.setIconTextGap(12);

		imgOffsetField = new HexTextField(8, (v) -> {
			if (shouldIgnoreChanges() || getSelected() != null) {
				getSelected().imgOffset = v;
				setModified();
			}
		});
		SwingUtils.addBorderPadding(imgOffsetField);

		palOffsetField = new HexTextField(8, (v) -> {
			if (shouldIgnoreChanges() || getSelected() != null) {
				getSelected().palOffset = v;
				setModified();
			}
		});
		SwingUtils.addBorderPadding(palOffsetField);

		sizeWField = new IntTextField((v) -> {
			if (shouldIgnoreChanges() || getSelected() == null)
				return;
			getSelected().sizeW = v;
			setModified();
		});
		SwingUtils.addBorderPadding(sizeWField);

		sizeHField = new IntTextField((v) -> {
			if (shouldIgnoreChanges() || getSelected() == null)
				return;
			getSelected().sizeH = v;
			setModified();
		});
		SwingUtils.addBorderPadding(sizeHField);

		previewLabels = new JLabel[PREVIEW_COUNT];
		displayLabels = new JLabel[PREVIEW_COUNT];
		for (int i = 0; i < PREVIEW_COUNT; i++) {
			previewLabels[i] = SwingUtils.getLabel("", 12);
			displayLabels[i] = SwingUtils.getLabel("", 12);
		}
		notShownLabel = SwingUtils.getLabel("", 12);

		JPanel infoPanel = new JPanel(new MigLayout("ins 0, fill, wrap 3, hidemode 3", "[90!]5[270!]5[90!][grow]"));

		infoPanel.add(nameLabel, "span, h 32!, wrap");

		infoPanel.add(SwingUtils.getLabel("Source", 12));
		infoPanel.add(sourceNameField, "growx");
		infoPanel.add(chooseSourceButton, "growx, wrap");

		infoPanel.add(SwingUtils.getLabel("Format", SwingConstants.RIGHT, 12));
		infoPanel.add(fmtBox, "grow, wrap");

		infoPanel.add(SwingUtils.getLabel("Palette Count", SwingConstants.RIGHT, 12));
		infoPanel.add(palCountField, "w 132!, wrap");

		infoPanel.add(new JLabel(), "span, h 4!, wrap");

		infoPanel.add(cbFlipVertically, "span, growx, wrap");
		infoPanel.add(cbImageIsFixed, "span, growx, wrap");

		infoPanel.add(new JLabel(), "span, h 4!, wrap");

		romOffsetsPanel = new JPanel(new MigLayout("ins 0, fill, wrap 3", "[90!]5[270!]5[90!][grow]"));

		romOffsetsPanel.add(SwingUtils.getLabel("Raster Offset", SwingConstants.RIGHT, 12));
		romOffsetsPanel.add(imgOffsetField, "grow, wrap");

		romOffsetsPanel.add(SwingUtils.getLabel("Palette Offset", SwingConstants.RIGHT, 12));
		romOffsetsPanel.add(palOffsetField, "grow, wrap");

		romOffsetsPanel.add(SwingUtils.getLabel("Default Size", SwingConstants.RIGHT, 12));
		romOffsetsPanel.add(sizeWField, "grow, split 2");
		romOffsetsPanel.add(sizeHField, "grow, wrap");

		JPanel previewPanel = new JPanel(new MigLayout("ins 0, fill, wrap 2", "16![]16![grow]"));

		for (int i = 0; i < PREVIEW_COUNT; i++) {
			previewPanel.add(previewLabels[i], "grow");
			previewPanel.add(displayLabels[i], "grow, wrap");
		}
		previewPanel.add(notShownLabel, "grow, span, wrap");

		infoPanel.add(romOffsetsPanel, "grow, span, wrap");
		infoPanel.add(previewPanel, "grow, span, wrap");

		JPanel embedPanel = new JPanel(new MigLayout("ins 8 16 8 16, fill, wrap"));
		embedPanel.add(infoPanel, "growx");
		embedPanel.add(new JLabel(), "growy, pushy");
		embedPanel.add(infoLabel, "growx");

		return embedPanel;
	}

	private void promptSelectImage(File imgDir)
	{
		ImageRecord currentImage = getSelected();
		if (currentImage == null)
			return;

		File currentFile = new File(imgDir.getAbsolutePath() + "/" + currentImage.identifier + ".png");
		imageFileChooser.setDirectoryContaining(currentFile);

		if (imageFileChooser.prompt() != ChooseDialogResult.APPROVE)
			return;

		File file = imageFileChooser.getSelectedFile();
		if (file == null || !file.exists()) {
			Toolkit.getDefaultToolkit().beep();
			return;
		}

		Path dirPath = Paths.get(imgDir.getAbsolutePath());
		Path filePath = Paths.get(file.getAbsolutePath());
		String relativePath;
		try {
			relativePath = dirPath.relativize(filePath).toString();
			relativePath = FilenameUtils.removeExtension(relativePath);
			relativePath = FilenameUtils.separatorsToUnix(relativePath);
		}
		catch (IllegalArgumentException ex) {
			Toolkit.getDefaultToolkit().beep();
			Logger.printStackTrace(ex);
			return;
		}

		if (relativePath.startsWith("../")) {
			Logger.logError("Selected file not from image assets directory!");
			Toolkit.getDefaultToolkit().beep();
			return;
		}

		if (relativePath != currentImage.identifier) {
			currentImage.identifier = relativePath;
			listModel.rebuildNameCache();

			updateName(currentImage);
			updateImages(currentImage, true);
			repaintList();
			setModified();
		}
	}

	@Override
	protected void updateInfoPanel(ImageRecord image, boolean fromSet)
	{
		updateName(image);

		fmtBox.setSelectedItem(image.fmt);

		palCountField.setValue(image.palCount);

		cbFlipVertically.setSelected(image.flip);
		cbImageIsFixed.setSelected(image.fixedPos);

		romOffsetsPanel.setVisible(image.fixedPos);
		palCountField.setEditable(image.fmt.type == TileFormat.TYPE_CI);
		palOffsetField.setEditable(image.fmt.type == TileFormat.TYPE_CI);

		imgOffsetField.setValue(image.imgOffset);
		palOffsetField.setValue(image.palOffset);
		sizeWField.setValue(image.sizeW);
		sizeHField.setValue(image.sizeH);

		updateImages(image, true);
	}

	private void updateName(ImageRecord image)
	{
		nameLabel.setText(FilenameUtils.getBaseName(image.identifier));
		sourceNameField.setText(image.identifier);
	}

	private void updateImages(ImageRecord image, boolean reloadFiles)
	{
		try {
			image.loadPreviews(180);
		}
		catch (IOException e) {
			Logger.printStackTrace(e);
		}

		for (int i = 0; i < PREVIEW_COUNT; i++) {
			if (image.palCount > i) {
				previewLabels[i].setIcon(image.preview[i]);
				displayLabels[i].setText(image.source[i]);
				displayLabels[i].setForeground(image.preview[i] == null ? SwingUtils.getRedTextColor() : null);
			}
			else {
				previewLabels[i].setIcon(null);
				displayLabels[i].setText("");
			}
		}

		if (image.palCount > PREVIEW_COUNT) {
			int extraCount = image.palCount - PREVIEW_COUNT;
			notShownLabel.setText(String.format("( %d additional palette%s not shown )", extraCount, extraCount > 1 ? "s" : ""));
		}
		else
			notShownLabel.setText("");
	}

	@Override
	protected ListCellRenderer<ImageRecord> getCellRenderer()
	{
		return new ImageAssetListRenderer();
	}
}
