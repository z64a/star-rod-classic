package game.map.scripts;

import static app.Directories.*;

import java.awt.Toolkit;
import java.io.File;
import java.io.IOException;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;

import org.apache.commons.io.FileUtils;

import app.Environment;
import app.StarRodClassic;
import app.input.IOUtils;
import app.input.InvalidInputException;
import game.map.Map;
import game.map.editor.MapEditor;
import game.map.editor.ui.StandardEditableComboBox;
import game.shared.ProjectDatabase;
import net.miginfocom.swing.MigLayout;
import shared.SwingUtils;
import util.Logger;
import util.ui.IntTextField;
import util.ui.IntVectorPanel;
import util.ui.StringField;

public class MainOptionsPanel extends JPanel
{
	private static final long serialVersionUID = 1L;

	private boolean ignoreChanges = false;

	private Map map;
	private ScriptData scripts;

	private JLabel mscrLabel;
	private JLabel mpatLabel;
	private JLabel mgenLabel;
	private JButton openMscrButton;
	private JButton openMpatButton;
	private JButton openMgenButton;

	private JButton generateScripts;
	private JButton refreshFiles;

	private JComboBox<String> locationsBox;

	// save the state from the last time we checked
	private boolean missingSource = false;
	private boolean missingPatch = false;
	private boolean missingGenerated = false;

	private File dumpSource;
	private File modSource;
	private File modPatch;
	private File modGenerated;

	private JCheckBox cbDarkness;

	private JCheckBox cbOverrideShape;
	private JCheckBox cbOverrideHit;
	private JCheckBox cbOverrideTex;
	private StringField overrideShapeText;
	private StringField overrideHitText;

	private JCheckBox cbCallbackBeforeEnter;
	private JCheckBox cbCallbackAfterEnter;

	private JCheckBox cbHasMusic;
	private StandardEditableComboBox songBox;

	private JCheckBox cbHasSounds;
	private StandardEditableComboBox soundsBox;

	// camera settings
	private JCheckBox cbLeadPlayer;

	private IntTextField vfovField;
	private IntVectorPanel clipDist;
	private IntVectorPanel bgColor;

	private JCheckBox cbWorldFog;
	private IntVectorPanel worldFogDist;
	private IntVectorPanel worldFogColor;

	private JCheckBox cbEntityFog;
	private IntVectorPanel entityFogDist;
	private IntVectorPanel entityFogColor;

	public MainOptionsPanel()
	{
		JScrollPane fileScrollPane = new JScrollPane(getFilesPanel());
		fileScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_NEVER);
		fileScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
		fileScrollPane.setBorder(null);

		overrideShapeText = new StringField((s) -> {
			if (ignoreChanges || scripts == null)
				return;
			MapEditor.execute(scripts.shapeOverrideName.mutator(s));
		});
		overrideHitText = new StringField((s) -> {
			if (ignoreChanges || scripts == null)
				return;
			MapEditor.execute(scripts.hitOverrideName.mutator(s));
		});

		cbOverrideShape = new JCheckBox(" Geometry");
		cbOverrideShape.addActionListener((e) -> {
			if (ignoreChanges || scripts == null)
				return;
			MapEditor.execute(scripts.overrideShape.mutator(cbOverrideShape.isSelected()));
		});

		cbOverrideHit = new JCheckBox(" Collision");
		cbOverrideHit.addActionListener((e) -> {
			if (ignoreChanges || scripts == null)
				return;
			MapEditor.execute(scripts.overrideHit.mutator(cbOverrideHit.isSelected()));
		});

		cbOverrideTex = new JCheckBox(" Match textures in editor");
		cbOverrideTex.addActionListener((e) -> {
			if (ignoreChanges || scripts == null)
				return;
			MapEditor.execute(scripts.overrideTex.mutator(cbOverrideTex.isSelected()));
		});

		cbDarkness = new JCheckBox(" Dark, requires Watt to see");
		cbDarkness.addActionListener((e) -> {
			if (ignoreChanges || scripts == null)
				return;
			MapEditor.execute(scripts.isDark.mutator(cbDarkness.isSelected()));
		});

		locationsBox = new JComboBox<>(ProjectDatabase.LocationType.getValues());
		locationsBox.addActionListener((e) -> {
			if (ignoreChanges || scripts == null)
				return;
			MapEditor.execute(scripts.locationName.mutator((String) locationsBox.getSelectedItem()));
		});
		locationsBox.setMaximumRowCount(20);
		SwingUtils.addBorderPadding(locationsBox);

		cbCallbackBeforeEnter = new JCheckBox(" Add callback before EnterMap");
		cbCallbackBeforeEnter.addActionListener((e) -> {
			if (ignoreChanges || scripts == null)
				return;
			MapEditor.execute(scripts.addCallbackBeforeEnterMap.mutator(cbCallbackBeforeEnter.isSelected()));
		});

		cbCallbackAfterEnter = new JCheckBox(" Add callback after EnterMap");
		cbCallbackAfterEnter.addActionListener((e) -> {
			if (ignoreChanges || scripts == null)
				return;
			MapEditor.execute(scripts.addCallbackAfterEnterMap.mutator(cbCallbackAfterEnter.isSelected()));
		});

		cbHasMusic = new JCheckBox(" Has music");
		cbHasMusic.addActionListener((e) -> {
			if (ignoreChanges || scripts == null)
				return;
			MapEditor.execute(scripts.hasMusic.mutator(cbHasMusic.isSelected()));
		});

		songBox = new StandardEditableComboBox((s) -> {
			if (ignoreChanges || scripts == null)
				return;
			MapEditor.execute(scripts.songName.mutator(s));
		}, ProjectDatabase.getFromNamespace("Song").getValues());

		cbHasSounds = new JCheckBox(" Has sounds");
		cbHasSounds.addActionListener((e) -> {
			if (ignoreChanges || scripts == null)
				return;
			MapEditor.execute(scripts.hasAmbientSFX.mutator(cbHasSounds.isSelected()));
		});

		soundsBox = new StandardEditableComboBox((s) -> {
			if (ignoreChanges || scripts == null)
				return;
			MapEditor.execute(scripts.ambientSFX.mutator(s));
		}, ProjectDatabase.getFromNamespace("AmbientSounds").getValues());

		cbLeadPlayer = new JCheckBox(" Camera leads player motion");
		cbLeadPlayer.addActionListener((e) -> {
			if (ignoreChanges)
				return;
			MapEditor.execute(scripts.cameraLeadsPlayer.mutator(cbLeadPlayer.isSelected()));
		});

		vfovField = new IntTextField((v) -> {
			if (ignoreChanges)
				return;
			MapEditor.execute(scripts.camVfov.mutator(v));
		});
		vfovField.setHorizontalAlignment(JTextField.CENTER);

		clipDist = new IntVectorPanel(2, (index, value) -> {
			if (ignoreChanges)
				return;
			if (index == 0)
				MapEditor.execute(scripts.camNearClip.mutator(value));
			else if (index == 1)
				MapEditor.execute(scripts.camFarClip.mutator(value));
		});

		bgColor = new IntVectorPanel(3, (index, value) -> {
			if (ignoreChanges)
				return;
			if (index == 0)
				MapEditor.execute(scripts.bgColorR.mutator(value));
			else if (index == 1)
				MapEditor.execute(scripts.bgColorG.mutator(value));
			else if (index == 2)
				MapEditor.execute(scripts.bgColorB.mutator(value));
		});

		cbWorldFog = new JCheckBox(" Enable World Fog");
		cbWorldFog.addActionListener((e) -> {
			if (ignoreChanges)
				return;
			MapEditor.execute(scripts.worldFogSettings.enabled.mutator(cbWorldFog.isSelected()));
		});
		worldFogDist = new IntVectorPanel(2, (index, value) -> {
			if (ignoreChanges)
				return;
			if (index == 0)
				MapEditor.execute(scripts.worldFogSettings.start.mutator(value));
			else if (index == 1)
				MapEditor.execute(scripts.worldFogSettings.end.mutator(value));

		});
		worldFogColor = new IntVectorPanel(3, (index, value) -> {
			if (ignoreChanges)
				return;
			if (index == 0)
				MapEditor.execute(scripts.worldFogSettings.R.mutator(value));
			else if (index == 1)
				MapEditor.execute(scripts.worldFogSettings.G.mutator(value));
			else if (index == 2)
				MapEditor.execute(scripts.worldFogSettings.B.mutator(value));

		});

		cbEntityFog = new JCheckBox(" Enable Entity Fog");
		cbEntityFog.addActionListener((e) -> {
			if (ignoreChanges)
				return;

			MapEditor.execute(scripts.entityFogSettings.enabled.mutator(cbEntityFog.isSelected()));
		});
		entityFogDist = new IntVectorPanel(2, (index, value) -> {
			if (ignoreChanges)
				return;
			if (index == 0)
				MapEditor.execute(scripts.entityFogSettings.start.mutator(value));
			else if (index == 1)
				MapEditor.execute(scripts.entityFogSettings.end.mutator(value));

		});
		entityFogColor = new IntVectorPanel(3, (index, value) -> {
			if (ignoreChanges)
				return;
			if (index == 0)
				MapEditor.execute(scripts.entityFogSettings.R.mutator(value));
			else if (index == 1)
				MapEditor.execute(scripts.entityFogSettings.G.mutator(value));
			else if (index == 2)
				MapEditor.execute(scripts.entityFogSettings.B.mutator(value));

		});

		setLayout(new MigLayout("fill, wrap, ins 0 16 16 16"));
		add(SwingUtils.getLabel("Script Files", 14), "gaptop 16");
		add(fileScrollPane, "grow");

		add(generateScripts, "w 40%, gapleft 8, gapright 8, split 2");
		add(refreshFiles, "w 40%, gapleft 8");

		add(SwingUtils.getLabel("Asset Overrides", 14), "gaptop 16");
		add(cbOverrideTex, "gapleft 8, sgy row, growx");
		add(cbOverrideShape, "gapleft 8, sg lbl, gapright 24, split 2");
		add(overrideShapeText, "sgy row, growx");
		add(cbOverrideHit, "gapleft 8, sg lbl, gapright 24, split 2");
		add(overrideHitText, "sgy row, growx");

		add(SwingUtils.getLabel("Main Options", 14), "gaptop 16");

		add(cbHasMusic, "gapleft 8, sg lbl, gapright 24, split 2");
		add(songBox, "sgy row, growx");

		add(cbHasSounds, "gapleft 8, sg lbl, gapright 24, split 2");
		add(soundsBox, "sgy row, growx");

		add(SwingUtils.getLabel("Location", 12),
			"gapleft 8, sg lbl, gapright 24, split 2");
		add(locationsBox, "growx");

		add(cbCallbackBeforeEnter, "gapleft 8, sgy row, growx");
		add(cbCallbackAfterEnter, "gapleft 8, sgy row, growx");

		add(SwingUtils.getLabel("Camera Settings", 14), "gaptop 16, gapbottom 2");
		add(SwingUtils.getLabel("Vertical FOV", 12), "gapleft 8, sg lbl, gapright 24, split 3");
		add(vfovField, "growx, pushx");

		// required to get spacing on input fields correct :(
		JLabel dummyLabel = new JLabel("deg");
		add(dummyLabel, "growx, pushx");
		dummyLabel.setVisible(false);

		add(SwingUtils.getLabel("Clip Planes", 12), "gapleft 8, sg lbl, gapright 24, split 2");
		add(clipDist, "growx");
		add(SwingUtils.getLabel("Background Color", 12), "gapleft 8, sg lbl, gapright 24, split 2");
		add(bgColor, "growx");
		add(cbLeadPlayer, "sgy row, gapleft 8, growx");
		add(cbDarkness, "sgy row, gapleft 8, growx");

		SwingUtils.setFontSize(cbWorldFog, 14);
		add(cbWorldFog, "gaptop 8");

		add(SwingUtils.getLabel("Distance", 12), "gapleft 8, sg lbl, gapright 24, split 2");
		add(worldFogDist, "growx");
		add(SwingUtils.getLabel("Color", 12), "gapleft 8, sg lbl, gapright 24, split 2");
		add(worldFogColor, "growx");

		SwingUtils.setFontSize(cbEntityFog, 14);
		add(cbEntityFog, "gaptop 8");

		add(SwingUtils.getLabel("Distance", 12), "gapleft 8, sg lbl, gapright 24, split 2");
		add(entityFogDist, "growx");
		add(SwingUtils.getLabel("Color", 12), "gapleft 8, sg lbl, gapright 24, split 2");
		add(entityFogColor, "growx");

		add(new JLabel(), "grow, pushy");
	}

	private JPanel getFilesPanel()
	{
		mscrLabel = new JLabel();
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

		mpatLabel = new JLabel();
		openMpatButton = new JButton("Open Patch File");
		openMpatButton.addActionListener((evt) -> {
			if (modPatch == null) {
				updateSourceInfo();
				return;
			}

			if (missingPatch) {
				try {
					FileUtils.touch(modPatch);
				}
				catch (IOException e) {
					MapEditor.instance().displayStackTrace(e);
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

		mgenLabel = new JLabel();
		openMgenButton = new JButton("Open Generated");
		openMgenButton.addActionListener((evt) -> {
			if (modGenerated == null) {
				updateSourceInfo();
				return;
			}

			if (missingGenerated) {
				try {
					FileUtils.touch(modGenerated);
				}
				catch (IOException e) {
					MapEditor.instance().displayStackTrace(e);
				}
				updateSourceInfo();
			}
			else {
				if (modGenerated.exists())
					StarRodClassic.openTextFile(modGenerated);
				else
					updateSourceInfo();
			}
		});

		generateScripts = new JButton("Generate Scripts");
		generateScripts.addActionListener((e) -> {
			try {
				Logger.log("Generating script for " + map.name + "...");
				if (Environment.project.isDecomp)
					new DecompScriptGenerator(map);
				else
					new ScriptGenerator(map);
				Logger.log("Successfully generated script for " + map.name);
			}
			catch (InvalidInputException iie) {
				Logger.logError(iie.getMessage());
				Toolkit.getDefaultToolkit().beep();
				MapEditor.instance().displayStackTrace(iie);
			}
			catch (IOException ioe) {
				Logger.logError("Script compile failed! IOException. Check log for more information.");
				Toolkit.getDefaultToolkit().beep();
				MapEditor.instance().displayStackTrace(ioe);
			}
			updateSourceInfo();
		});

		refreshFiles = new JButton("Refresh Files");
		refreshFiles.addActionListener((e) -> {
			updateSourceInfo();
		});

		JPanel filePanel = new JPanel(new MigLayout("fill, wrap 2, hidemode 3, ins 8"));

		filePanel.add(openMscrButton, "w 35%, gapright 8");
		filePanel.add(mscrLabel, "pushx, growx");

		filePanel.add(openMpatButton, "w 35%, gapright 8");
		filePanel.add(mpatLabel, "pushx, growx");

		filePanel.add(openMgenButton, "w 35%, gapright 8");
		filePanel.add(mgenLabel, "pushx, growx");

		return filePanel;
	}

	private void updateSourceInfo()
	{
		String mapName = MapEditor.instance().map.name;

		String sourceName = mapName + ".mscr";
		String patchName = mapName + ".mpat";

		dumpSource = new File(DUMP_MAP_SRC + sourceName);
		if (dumpSource.exists()) {
			// existing map
			modSource = new File(MOD_MAP_SRC + sourceName);
			mscrLabel.setVisible(true);
			openMscrButton.setVisible(true);

			if (modSource.exists()) {
				mscrLabel.setText("/src/" + sourceName);
				openMscrButton.setText("Open Original");
				missingSource = false;
			}
			else {
				mscrLabel.setText("<html><i>Source file is missing!</i></html>");
				openMscrButton.setText("Recopy Original");
				missingSource = true;
			}
		}
		else {
			// custom map
			mscrLabel.setVisible(false);
			openMscrButton.setVisible(false);
		}

		File[] matches = IOUtils.getFileWithin(MOD_MAP_PATCH, patchName, true);
		modPatch = (matches.length > 0) ? matches[0] : new File(MOD_MAP_PATCH + patchName);
		if (modPatch.exists()) {
			if (matches.length > 1) {
				mpatLabel.setForeground(SwingUtils.getRedTextColor());
				mpatLabel.setText("<html><i>Found multiple patch files!</i></html>");
			}
			else {
				mpatLabel.setForeground(null);
				mpatLabel.setText(MOD_MAP.getRelativeName(modPatch));
			}
			openMpatButton.setText("Open Patch File");
			missingPatch = false;
		}
		else {
			mpatLabel.setText("<html><i>Patch file not found!</i></html>");
			openMpatButton.setText("Create Patch File");
			missingPatch = true;
		}

		matches = IOUtils.getFileWithin(MOD_MAP_GEN, patchName, true);
		modGenerated = (matches.length > 0) ? matches[0] : new File(MOD_MAP_GEN + patchName);
		if (modGenerated.exists()) {
			if (matches.length > 1) {
				mgenLabel.setForeground(SwingUtils.getRedTextColor());
				mgenLabel.setText("<html><i>Found multiple generated files!</i></html>");
			}
			else {
				mgenLabel.setForeground(null);
				mgenLabel.setText(MOD_MAP.getRelativeName(modGenerated));
			}
			//	openMgenButton.setText("Open");
			openMgenButton.setEnabled(true);
			missingGenerated = false;
		}
		else {
			mgenLabel.setText("<html><i>Generated scripts not found!</i></html>");
			//	openMgenButton.setText("Create");
			openMgenButton.setEnabled(false);
			missingGenerated = true;
		}
	}

	public void setMap(Map m)
	{
		this.map = m;
		this.scripts = map.scripts;
		updateFields(map.scripts);
	}

	public void updateFields(ScriptData data)
	{
		updateSourceInfo();

		ignoreChanges = true;

		overrideShapeText.setText(data.shapeOverrideName.get());
		overrideHitText.setText(data.hitOverrideName.get());

		cbOverrideShape.setSelected(data.overrideShape.get());
		overrideShapeText.setEnabled(data.overrideShape.get());

		cbOverrideHit.setSelected(data.overrideHit.get());
		overrideHitText.setEnabled(data.overrideHit.get());

		cbOverrideTex.setSelected(data.overrideTex.get());

		locationsBox.setSelectedItem(data.locationName.get());
		cbDarkness.setSelected(data.isDark.get());

		cbCallbackBeforeEnter.setSelected(data.addCallbackBeforeEnterMap.get());
		cbCallbackAfterEnter.setSelected(data.addCallbackAfterEnterMap.get());

		cbHasMusic.setSelected(data.hasMusic.get());
		songBox.setSelectedItem(data.songName.get());
		songBox.setEnabled(data.hasMusic.get());

		cbHasSounds.setSelected(data.hasAmbientSFX.get());
		soundsBox.setSelectedItem(data.ambientSFX.get());
		soundsBox.setEnabled(data.hasAmbientSFX.get());

		cbLeadPlayer.setSelected(data.cameraLeadsPlayer.get());

		vfovField.setValue(data.camVfov.get());
		clipDist.setValues(data.camNearClip.get(), data.camFarClip.get());
		bgColor.setValues(data.bgColorR.get(), data.bgColorG.get(), data.bgColorB.get());

		cbWorldFog.setSelected(data.worldFogSettings.enabled.get());
		worldFogDist.setEnabled(data.worldFogSettings.enabled.get());
		worldFogColor.setEnabled(data.worldFogSettings.enabled.get());
		worldFogDist.setValues(data.worldFogSettings.start.get(), data.worldFogSettings.end.get());
		worldFogColor.setValues(data.worldFogSettings.R.get(), data.worldFogSettings.G.get(), data.worldFogSettings.B.get());

		cbEntityFog.setSelected(data.entityFogSettings.enabled.get());
		entityFogDist.setEnabled(data.entityFogSettings.enabled.get());
		entityFogColor.setEnabled(data.entityFogSettings.enabled.get());
		entityFogDist.setValues(data.entityFogSettings.start.get(), data.entityFogSettings.end.get());
		entityFogColor.setValues(data.entityFogSettings.R.get(), data.entityFogSettings.G.get(), data.entityFogSettings.B.get());

		ignoreChanges = false;
	}
}
