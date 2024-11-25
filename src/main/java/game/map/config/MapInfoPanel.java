package game.map.config;

import static app.Directories.*;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

import javax.imageio.ImageIO;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSeparator;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import org.apache.commons.io.FileUtils;

import app.Directories;
import app.StarRodClassic;
import app.SwingUtils;
import app.SwingUtils.TextColor;
import app.input.IOUtils;
import game.map.Map;
import game.map.compiler.CollisionCompiler;
import game.map.compiler.GeometryCompiler;
import game.map.config.MapConfigTable.MapConfig;
import game.map.editor.MapEditor;
import net.miginfocom.swing.MigLayout;
import util.Logger;
import util.ui.HexTextField;
import util.ui.ImagePanel;
import util.ui.LimitedLengthDocument;

public class MapInfoPanel extends JPanel
{
	// map properties
	private JTextField nameField;
	private JTextField nicknameField;
	private JTextArea descArea;
	//	private JTextField bgField;

	private JLabel mapIDLabel;

	private JLabel flagsLabel;
	private HexTextField flagsField;

	private ImagePanel thumbnailPanel;
	private JPanel scriptPanel;
	private JCheckBox cbScript;
	private JLabel mscrLabel;
	private JLabel mpatLabel;
	private JButton openMscrButton;
	private JButton openMpatButton;

	private static enum SourceAction
	{
		NONE, OPEN, CREATE, RECOPY
	}

	private boolean missingSource = false;
	private boolean missingPatch = false;
	private File modSource;
	private File dumpSource;
	private File modPatch;

	private SourceAction mapAction = SourceAction.NONE;
	private File dumpMap;
	private File modMap;
	private File saveMap;
	private File validMap;

	private JPanel geomPanel;
	private JCheckBox cbShape;
	private JCheckBox cbHit;
	private JLabel mapLabel;
	private JLabel shapeLabel;
	private JLabel hitLabel;
	private JButton openMapButton;
	private JButton buildShapeButton;
	private JButton buildHitButton;

	private MapConfig currentMap;

	private MapEditor mapEditor = null;
	private Thread editorThread = null;
	private Thread buildThread = null;

	public MapInfoPanel(LevelEditor editor)
	{
		mapIDLabel = new JLabel();

		nameField = new JTextField();
		nameField.setColumns(8);
		nameField.setDocument(new LimitedLengthDocument(8));
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
				if (currentMap == null)
					return;
				currentMap.name = nameField.getText();
				updateSourceInfo();
				editor.validateNames();
				editor.repaintTrees();
				editor.modified();
			}
		});
		SwingUtils.addBorderPadding(nameField);

		nicknameField = new JTextField();
		nicknameField.getDocument().addDocumentListener(new DocumentListener() {
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
				if (currentMap == null)
					return;
				currentMap.nickname = nicknameField.getText();
				editor.repaintTrees();
				editor.modified();
			}
		});
		SwingUtils.addBorderPadding(nicknameField);

		/*
		bgField = new JTextField();
		bgField.setColumns(8);
		bgField.setDocument(new LimitedLengthDocument(8));
		bgField.getDocument().addDocumentListener(new DocumentListener()
		{
			@Override public void changedUpdate(DocumentEvent e)	{ updateName(); };
			@Override public void insertUpdate(DocumentEvent e)		{ updateName(); };
			@Override public void removeUpdate(DocumentEvent e)		{ updateName(); };

			private void updateName()
			{
				if(currentMap == null) return;
				currentMap.bgName = bgField.getText();
				if(!currentMap.bgName.isEmpty())
				{
					if(editor.hasResource(currentMap.bgName)) {
						bgField.setForeground(null);
						bgField.setToolTipText(null);
					} else {
						bgField.setForeground(SwingUtils.getRedTextColor());
						bgField.setToolTipText("Resource cannot be found.");
					}
				}
				editor.modified();
			}
		});

		bgField.addActionListener((e) -> {
			if(currentMap == null) return;
			currentMap.bgName = bgField.getText();
			editor.modified();
		});
		SwingUtils.addBorderPadding(bgField);
		*/

		descArea = new JTextArea();
		descArea.setRows(5);
		descArea.setLineWrap(true);
		descArea.getDocument().addDocumentListener(new DocumentListener() {
			@Override
			public void insertUpdate(DocumentEvent e)
			{
				if (currentMap == null)
					return;
				currentMap.desc = descArea.getText();
				editor.modified();
			}

			@Override
			public void removeUpdate(DocumentEvent e)
			{
				if (currentMap == null)
					return;
				currentMap.desc = descArea.getText();
				editor.modified();
			}

			@Override
			public void changedUpdate(DocumentEvent e)
			{}
		});
		SwingUtils.addBorderPadding(descArea);

		flagsField = new HexTextField((v) -> {
			if (currentMap == null)
				return;
			currentMap.flags = v;
			editor.modified();
		});
		flagsField.setColumns(8);
		SwingUtils.addBorderPadding(flagsField);

		thumbnailPanel = new ImagePanel();

		createScriptPanel(editor);
		createGeomPanel(editor);

		setLayout(new MigLayout("fill, wrap 2, hidemode 3", "[]8[grow]", "grow"));

		add(new JLabel("Nickname"));
		add(nicknameField, "w 240!");

		add(new JLabel("Engine Name"));
		add(nameField, "sg fields");
		//	add(nameField, "sg fields, split 2");
		//	add(mapIDLabel, "gapleft 16");

		//	add(new JLabel("Background"));
		//	add(bgField, "sg fields");

		flagsLabel = new JLabel("Flags");
		add(flagsLabel);
		add(flagsField, "sg fields");

		add(geomPanel, "span, growx");
		add(scriptPanel, "span, growx, gapbottom 16");
		add(thumbnailPanel, "span, center, growx, h 180!");
	}

	private void createScriptPanel(LevelEditor editor)
	{
		cbScript = new JCheckBox(" Script Data");
		cbScript.setToolTipText("Does this map have script data?");
		cbScript.addChangeListener((e) -> {
			if (currentMap == null)
				return;
			currentMap.hasData = cbScript.isSelected();
			openMscrButton.setEnabled(currentMap.hasData);
			openMpatButton.setEnabled(currentMap.hasData);
			editor.modified();
		});

		mscrLabel = new JLabel();
		mpatLabel = new JLabel();
		openMscrButton = new JButton("Open");
		openMscrButton.addActionListener((evt) -> {
			if (dumpSource == null || modSource == null) {
				updateSourceInfo();
				return;
			}

			if (missingSource) {
				if (dumpSource.exists() && !modSource.exists()) {
					try {
						FileUtils.copyFile(dumpSource, modSource);
					}
					catch (IOException e) {
						MapEditor.instance().displayStackTrace(e);
					}
				}
				updateSourceInfo();
			}
			else {
				if (modSource.exists())
					StarRodClassic.openTextFile(modSource);
				else
					updateSourceInfo();
			}
		});

		openMpatButton = new JButton("Open");
		openMpatButton.addActionListener((evt) -> {
			if (missingPatch) {
				if (modPatch == null) {
					updateSourceInfo();
					return;
				}

				if (!modPatch.exists()) {
					try {
						File templatePatch = new File(Directories.DATABASE_EDITOR + "template_map.mpat");
						if (templatePatch.exists())
							FileUtils.copyFile(templatePatch, modPatch);
						else
							FileUtils.touch(modPatch);
					}
					catch (IOException e) {
						MapEditor.instance().displayStackTrace(e);
					}
				}
				updateSourceInfo();
			}
			else {
				if (modPatch.exists())
					StarRodClassic.openTextFile(modPatch);
				else
					updateSourceInfo();
			}
		});

		scriptPanel = new JPanel();
		scriptPanel.setLayout(new MigLayout("fill, ins 0, wrap 2, hidemode 3"));
		scriptPanel.add(cbScript, "span, wrap");

		scriptPanel.add(openMscrButton, "w 80!, gapright 8");
		scriptPanel.add(mscrLabel, "pushx, growx, wrap");

		scriptPanel.add(openMpatButton, "w 80!, gapright 8");
		scriptPanel.add(mpatLabel, "pushx, growx, wrap");
	}

	private void createGeomPanel(LevelEditor editor)
	{
		cbShape = new JCheckBox(" Shape Data");
		cbShape.setToolTipText("Does this map have model data?");
		cbShape.addChangeListener((e) -> {
			if (currentMap == null)
				return;
			currentMap.hasShape = cbShape.isSelected();
			buildShapeButton.setEnabled(currentMap.hasShape);
			editor.modified();
		});

		cbHit = new JCheckBox(" Hit Data");
		cbHit.setToolTipText("Does this map have collision data?");
		cbHit.addChangeListener((e) -> {
			if (currentMap == null)
				return;
			currentMap.hasHit = cbHit.isSelected();
			buildHitButton.setEnabled(currentMap.hasHit);
			editor.modified();
		});

		mapLabel = new JLabel();
		shapeLabel = new JLabel();
		hitLabel = new JLabel();
		openMapButton = new JButton("Open");
		openMapButton.addActionListener((evt) -> {
			switch (mapAction) {
				case CREATE:
					generateMapSource((JFrame) SwingUtilities.getWindowAncestor(this), saveMap);
					updateSourceInfo();
					break;

				case OPEN:
					if (validMap != null && validMap.exists())
						openMap(validMap);
					break;

				case RECOPY:
					if (dumpMap.exists() && !modMap.exists()) {
						try {
							FileUtils.copyFile(dumpMap, modMap);
						}
						catch (IOException e) {
							MapEditor.instance().displayStackTrace(e);
						}
					}
					updateSourceInfo();
					break;

				default:
					break;
			}
		});

		buildShapeButton = new JButton("Build");
		buildShapeButton.addActionListener((evt) -> {
			buildShapeFile();
		});

		buildHitButton = new JButton("Build");
		buildHitButton.addActionListener((evt) -> {
			buildHitFile();
		});

		geomPanel = new JPanel();
		geomPanel.setLayout(new MigLayout("fill, ins 0"));

		geomPanel.add(new JSeparator(), "grow, span, wrap, gaptop 6, gapbottom 4");

		//	geomPanel.add(new JLabel("Map File"), "span, wrap");
		geomPanel.add(openMapButton, "w 80!, gapright 8");
		geomPanel.add(mapLabel, "pushx, growx, wrap");

		geomPanel.add(new JSeparator(), "grow, span, wrap, gaptop 6");

		geomPanel.add(cbShape, "span, wrap");
		geomPanel.add(buildShapeButton, "w 80!, gapright 8");
		geomPanel.add(shapeLabel, "pushx, growx, wrap");

		geomPanel.add(new JSeparator(), "grow, span, wrap, gaptop 6");

		geomPanel.add(cbHit, "span, wrap");
		geomPanel.add(buildHitButton, "w 80!, gapright 8");
		geomPanel.add(hitLabel, "pushx, growx, wrap");

		geomPanel.add(new JSeparator(), "grow, span, wrap, gaptop 6");
	}

	public void setMap(MapConfig map)
	{
		currentMap = map;

		mapIDLabel.setText(map.hasData ? String.format("ID:  %02X-%02X", map.areaID, map.mapID) : "");

		nameField.setText(map.name);
		nicknameField.setText(map.nickname);
		descArea.setText(map.desc);

		//	bgField.setText(map.bgName);

		flagsField.setValue(map.flags);

		cbScript.setSelected(map.hasData);
		openMscrButton.setEnabled(currentMap.hasData);
		openMpatButton.setEnabled(currentMap.hasData);

		cbShape.setSelected(map.hasShape);
		cbHit.setSelected(map.hasHit);
		buildShapeButton.setEnabled(map.hasShape);
		buildHitButton.setEnabled(map.hasHit);

		flagsLabel.setVisible(!map.isStage);
		flagsField.setVisible(!map.isStage);
		scriptPanel.setVisible(!map.isStage);

		updateSourceInfo();

		if (map.thumbnail != null) {
			thumbnailPanel.setImage(map.thumbnail);
		}
		else {
			File thumbnail = new File(MOD_MAP_THUMBNAIL + map.name + ".jpg");
			if (thumbnail.exists()) {
				try {
					map.thumbnail = resizeImage(ImageIO.read(thumbnail), 240);
					thumbnailPanel.setImage(map.thumbnail);
				}
				catch (IOException e) {
					Logger.printStackTrace(e);
				}
			}
			else
				thumbnailPanel.setImage(null);
		}
	}

	private static BufferedImage resizeImage(BufferedImage src, int targetSize)
	{
		if (targetSize <= 0) {
			return src; //this can't be resized
		}
		int targetWidth = targetSize;
		int targetHeight = targetSize;
		float ratio = ((float) src.getHeight() / (float) src.getWidth());
		if (ratio <= 1) { //square or landscape-oriented image
			targetHeight = (int) Math.ceil(targetWidth * ratio);
		}
		else { //portrait image
			targetWidth = Math.round(targetHeight / ratio);
		}
		BufferedImage bi = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
		Graphics2D g2d = bi.createGraphics();
		g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		g2d.drawImage(src, 0, 0, targetWidth, targetHeight, null);
		g2d.dispose();
		return bi;
	}

	private void updateSourceInfo()
	{
		if (!currentMap.isStage) {
			String sourceName = currentMap.name + ".mscr";
			String patchName = currentMap.name + ".mpat";

			dumpSource = new File(DUMP_MAP_SRC + sourceName);
			File[] matches = IOUtils.getFileWithin(MOD_MAP_PATCH, patchName, true);
			modPatch = (matches.length > 0) ? matches[0] : new File(MOD_MAP_PATCH + patchName);

			if (dumpSource.exists()) {
				// existing map
				modSource = new File(MOD_MAP_SRC + sourceName);
				mscrLabel.setVisible(true);
				openMscrButton.setVisible(true);

				if (modSource.exists()) {
					mscrLabel.setText("/src/" + sourceName);
					openMscrButton.setText("Open");
					missingSource = false;
				}
				else {
					mscrLabel.setText("<html><i>Source file is missing!</i></html>");
					openMscrButton.setText("Recopy");
					missingSource = true;
				}
			}
			else {
				// custom map
				mscrLabel.setVisible(false);
				openMscrButton.setVisible(false);
			}

			if (modPatch.exists()) {
				if (matches.length > 1) {
					mpatLabel.setForeground(SwingUtils.getRedTextColor());
					mpatLabel.setText("<html><i>Found multiple patch files!</i></html>");
				}
				else {
					mpatLabel.setForeground(null);
					mpatLabel.setText(MOD_MAP.getRelativeName(modPatch));
				}
				openMpatButton.setText("Open");
				missingPatch = false;
			}
			else {
				mpatLabel.setText("<html><i>Patch file not found!</i></html>");
				openMpatButton.setText("Create");
				missingPatch = true;
			}
		}

		String mapName = currentMap.name + Map.EXTENSION;

		dumpMap = new File(DUMP_MAP_SRC + mapName);
		modMap = new File(MOD_MAP_SRC + mapName);

		File[] matches = IOUtils.getFileWithin(MOD_MAP_SAVE, mapName, true);
		validMap = (matches.length > 0) ? matches[0] : modMap;

		saveMap = (matches.length > 0) ? matches[0] : new File(MOD_MAP_SAVE + mapName);
		validMap = (matches.length > 0) ? matches[0] : modMap;
		validMap = saveMap.exists() ? saveMap : modMap;

		String mapFileName = MOD_MAP.getRelativeName(validMap);

		String shapeName = currentMap.name + "_shape";
		String hitName = currentMap.name + "_hit";
		File oldShape = new File(DUMP_MAP_YAY0 + shapeName);
		File newShape = new File(MOD_MAP_BUILD + shapeName);
		File oldHit = new File(DUMP_MAP_YAY0 + hitName);
		File newHit = new File(MOD_MAP_BUILD + hitName);

		mapLabel.setForeground(null); // just reset this, dont wory about the logic

		if (dumpMap.exists()) // vanilla map
		{
			if (!validMap.exists()) {
				// default map with missing source
				mapLabel.setText("<html><i>Map file is missing!</i></html>");
				openMapButton.setText("Recopy");
				mapAction = SourceAction.RECOPY;
			}
			else {
				// default map with existing source
				if (matches.length > 1) {
					mapLabel.setForeground(SwingUtils.getRedTextColor());
					mapLabel.setText("<html><i>Found multiple map files!</i></html>");
				}
				else
					mapLabel.setText(mapFileName);
				openMapButton.setText("Open");
				mapAction = SourceAction.OPEN;
			}
		}
		else // custom map
		{
			if (!validMap.exists()) {
				// custom map with missing source
				mapLabel.setText("<html><i>Map file is missing!</i></html>");
				openMapButton.setText("Create");
				mapAction = SourceAction.CREATE;
			}
			else {
				// custom map with existing source
				if (matches.length > 1) {
					mapLabel.setForeground(SwingUtils.getRedTextColor());
					mapLabel.setText("<html><i>Found multiple map files!</i></html>");
				}
				else
					mapLabel.setText(mapFileName);
				openMapButton.setText("Open");
				mapAction = SourceAction.OPEN;
			}
		}

		if (newShape.exists()) {
			buildShapeButton.setText("Rebuild");
			SwingUtils.setTextAndColor(shapeLabel, TextColor.NORMAL, shapeName);
		}
		else {
			buildShapeButton.setText("Build");
			TextColor warningColor = currentMap.hasShape ? TextColor.RED : TextColor.NORMAL;
			if (!oldShape.exists())
				SwingUtils.setTextAndColor(shapeLabel, warningColor, "<html><i>Shape file is missing!</i></html>");
			else
				SwingUtils.setTextAndColor(shapeLabel, TextColor.NORMAL, "<html><i>Using shape file from dump.</i></html>");
		}

		if (newHit.exists()) {
			buildHitButton.setText("Rebuild");
			SwingUtils.setTextAndColor(hitLabel, TextColor.NORMAL, hitName);
		}
		else {
			buildHitButton.setText("Build");
			TextColor warningColor = currentMap.hasHit ? TextColor.RED : TextColor.NORMAL;
			if (!oldHit.exists())
				SwingUtils.setTextAndColor(hitLabel, warningColor, "<html><i>Hit file is missing!</i></html>");
			else
				SwingUtils.setTextAndColor(hitLabel, TextColor.NORMAL, "<html><i>Using hit file from dump.</i></html>");
		}
	}

	private static void generateMapSource(JFrame frame, File dest)
	{
		int choice = SwingUtils.getOptionDialog()
			.setTitle("Create Map")
			.setMessage("How should the map be generated?")
			.setMessageType(JOptionPane.PLAIN_MESSAGE)
			.setOptionsType(JOptionPane.YES_NO_OPTION)
			.setOptions("Use Template", "Copy Another Map")
			.choose();

		if (choice == JOptionPane.CLOSED_OPTION)
			return;

		String name = "";
		if (choice == 1) {
			name = SwingUtils.getInputDialog()
				.setTitle("Copy Map Source")
				.setMessage("Map to copy:")
				.setMessageType(JOptionPane.QUESTION_MESSAGE)
				.prompt();
		}

		File xmlSource = getSourceFile(frame, name);

		if (dest.exists()) {
			choice = SwingUtils.getConfirmDialog()
				.setTitle("Overwrite " + dest.getName() + "?")
				.setMessage(dest.getName() + " already exists.", "Do you want to overwrite it?")
				.setOptionsType(JOptionPane.YES_NO_OPTION)
				.setMessageType(JOptionPane.QUESTION_MESSAGE)
				.choose();

			if (choice != JOptionPane.YES_OPTION)
				return;
		}

		try {
			FileUtils.copyFile(xmlSource, dest);
		}
		catch (IOException e) {
			Logger.logError(e.getMessage());
			SwingUtils.getErrorDialog()
				.setTitle("Map Creation Failed")
				.setMessage("IOException while copying map file.", e.getMessage())
				.show();
		}
	}

	private static File getSourceFile(JFrame frame, String copyName)
	{
		if (!copyName.isEmpty()) {
			File[] matches = IOUtils.getFileWithin(MOD_MAP_SAVE, copyName + Map.EXTENSION, true);
			if (matches.length >= 1)
				return matches[0];

			File source = new File(Directories.MOD_MAP_SRC + copyName + Map.EXTENSION);

			if (source.exists())
				return source;

			SwingUtils.getWarningDialog()
				.setTitle("Map Creation Failed")
				.setMessage("Could not find selected map file.", "Using template map instead.")
				.show();
		}

		return new File(Directories.DATABASE_EDITOR + "template_map" + Map.EXTENSION);
	}

	private void openMap(File mapFile)
	{
		if (editorThread != null && editorThread.isAlive()) {
			if (mapEditor == null || mapEditor.isLoading())
				return;

			int choice = SwingUtils.getOptionDialog()
				.setTitle("Editor Already Open")
				.setMessage("Map editor is currently open with " + mapEditor.map.name + ".",
					"Close and reopen with " + currentMap.name + "?")
				.setMessageType(JOptionPane.WARNING_MESSAGE)
				.setOptionsType(JOptionPane.YES_NO_CANCEL_OPTION)
				.setOptions("Continue", "Cancel")
				.choose();

			if (choice == 0)
				mapEditor.changeMap(Map.loadMap(mapFile));

			return;
		}

		editorThread = new Thread() {
			@Override
			public void run()
			{
				try {
					mapEditor = new MapEditor(true);
					mapEditor.launch(Map.loadMap(mapFile));
				}
				catch (IOException e) {
					MapEditor.instance().displayStackTrace(e);
				}
			}
		};
		editorThread.start();
	}

	private void buildShapeFile()
	{
		if (validMap == null || !validMap.exists())
			return;

		if (buildThread != null && buildThread.isAlive())
			return;

		if (editorThread != null && editorThread.isAlive() && mapEditor.map.name.equals(currentMap.name) && mapEditor.map.modified) {
			SwingUtils.getWarningDialog()
				.setParent(SwingUtilities.getWindowAncestor(this))
				.setTitle("Map Changes Detected")
				.setMessage("Map " + currentMap.name + " has been modified in the editor.",
					"Save your changes before building assets.")
				.show();
			return;
		}

		buildThread = new Thread() {
			@Override
			public void run()
			{
				try {
					new GeometryCompiler(Map.loadMap(validMap));
					SwingUtilities.invokeLater(() -> {
						updateSourceInfo();
					});
				}
				catch (IOException e) {
					MapEditor.instance().displayStackTrace(e);
				}
			}
		};
		buildThread.start();
	}

	private void buildHitFile()
	{
		if (validMap == null || !validMap.exists())
			return;

		if (buildThread != null && buildThread.isAlive())
			return;

		if (editorThread != null && editorThread.isAlive() && mapEditor.map.name.equals(currentMap.name) && mapEditor.map.modified) {
			SwingUtils.getWarningDialog()
				.setParent(SwingUtilities.getWindowAncestor(this))
				.setTitle("Map Changes Detected")
				.setMessage("Map " + currentMap.name + " has been modified in the editor.",
					"Save your changes before building assets.")
				.show();
			return;
		}

		buildThread = new Thread() {
			@Override
			public void run()
			{
				try {
					new CollisionCompiler(Map.loadMap(validMap));
					SwingUtilities.invokeLater(() -> {
						updateSourceInfo();
					});
				}
				catch (IOException e) {
					MapEditor.instance().displayStackTrace(e);
				}
			}
		};
		buildThread.start();
	}
}
