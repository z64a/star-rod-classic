package game.map.scripts;

import java.awt.Toolkit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JOptionPane;
import javax.swing.JPanel;

import app.SwingUtils;
import game.map.Map;
import game.map.editor.MapEditor;
import game.map.editor.MapEditor.IShutdownListener;
import game.map.editor.commands.AbstractCommand;
import game.map.editor.commands.CommandBatch;
import game.map.editor.ui.SwingGUI;
import game.map.shading.ShadingLightSource;
import game.map.shading.ShadingProfile;
import game.map.shading.ShadingProfile.ShadingProfileComboBoxRenderer;
import game.map.shading.SpriteShadingData.CreateProfile;
import game.map.shading.SpriteShadingData.DeleteProfile;
import game.shared.ProjectDatabase;
import net.miginfocom.swing.MigLayout;
import util.ui.ListAdapterComboboxModel;

/**
 * Singleton JPanel for dipsplaying model lighting data.
 */
public class ShadingPanel extends JPanel implements IShutdownListener
{
	private Map map;

	private JCheckBox cbHasShading;
	private JComboBox<ShadingProfile> profileBox;
	private ShadingProfileInfoPanel profilePanel;

	private boolean ignoreChanges = false;

	private static ShadingPanel instance = null;

	public static ShadingPanel instance()
	{
		return instance;
	}

	@Override
	public void shutdown()
	{
		instance = null;
	}

	public void repaintComboBox()
	{
		profileBox.repaint();
	}

	public ShadingPanel()
	{
		if (instance != null)
			throw new IllegalStateException("There can be only one LightingPanel");
		instance = this;
		MapEditor.instance().registerOnShutdown(this);

		cbHasShading = new JCheckBox(" Using profile");
		cbHasShading.addActionListener((e) -> {
			if (ignoreChanges || map.scripts == null)
				return;

			if (cbHasShading.isSelected() && map.scripts.shadingProfile.get() == null) {
				Toolkit.getDefaultToolkit().beep();
				updateFields(map.scripts);
				return;
			}

			MapEditor.execute(new SetShadingEnabled(cbHasShading.isSelected()));
		});

		profileBox = new JComboBox<>(new ListAdapterComboboxModel<>(ProjectDatabase.SpriteShading.listModel));
		profileBox.addActionListener((e) -> {
			if (ignoreChanges || map.scripts == null)
				return;
			ShadingProfile selected = (ShadingProfile) profileBox.getSelectedItem();
			MapEditor.execute(new SetShadingProfile(selected));
		});
		SwingUtils.addBorderPadding(profileBox);
		profileBox.setMaximumRowCount(24);
		profileBox.setRenderer(new ShadingProfileComboBoxRenderer());

		profilePanel = new ShadingProfileInfoPanel(this);

		JButton createProfileButton = new JButton("Create");
		createProfileButton.addActionListener((e) -> {
			if (ignoreChanges)
				return;
			CreateProfile createCmd = new CreateProfile(ProjectDatabase.SpriteShading);
			ShadingProfile newProfile = createCmd.getProfile();
			CommandBatch createBatch = new CommandBatch("Create Shading Profile");
			createBatch.addCommand(createCmd);
			createBatch.addCommand(new SetShadingProfile(newProfile));
			MapEditor.execute(createBatch);
		});

		JButton deleteProfileButton = new JButton("Delete");
		deleteProfileButton.addActionListener((e) -> {
			if (ignoreChanges)
				return;
			ShadingProfile selected = (ShadingProfile) profileBox.getSelectedItem();
			if (selected == null)
				return;

			boolean shouldDelete = true;
			if (selected.vanilla) {
				int choice = SwingUtils.getConfirmDialog()
					.setParent(SwingGUI.instance())
					.setCounter(SwingGUI.instance().getDialogCounter())
					.setTitle("Warning")
					.setMessage("Selected profile is vanilla.", "Are you sure you want to delete it?")
					.choose();

				shouldDelete = (choice == JOptionPane.YES_OPTION);
			}

			if (shouldDelete) {
				CommandBatch deleteBatch = new CommandBatch("Delete Shading Profile");
				deleteBatch.addCommand(new SetShadingProfile(null));
				deleteBatch.addCommand(new DeleteProfile(ProjectDatabase.SpriteShading, selected));
				MapEditor.execute(deleteBatch);
			}
		});

		setLayout(new MigLayout("fill, hidemode 0, ins 0 16 16 16", "[][50%]"));

		add(SwingUtils.getLabel("Sprite Shading", 14), "gaptop 16, gapbottom 8, wrap, span");

		add(cbHasShading, "growx, growy, gapleft 8");
		add(profileBox, "growx, wrap");

		add(new JPanel(), "grow");
		add(createProfileButton, "grow, split 2");
		add(deleteProfileButton, "grow, wrap");

		add(profilePanel, "pushy, grow, span, gaptop 16");
	}

	public void setMap(Map m)
	{
		this.map = m;
		updateFields(m.scripts);
	}

	public void updateFields(ScriptData data)
	{
		ignoreChanges = true;

		cbHasShading.setSelected(data.hasSpriteShading.get());
		profileBox.setEnabled(true);
		profileBox.setSelectedItem(data.shadingProfile.get());

		ShadingProfile profile = data.shadingProfile.get();
		profilePanel.setData(profile);
		profilePanel.setVisible(profile != null);

		ignoreChanges = false;
	}

	private class SetShadingProfile extends AbstractCommand
	{
		private final ShadingProfile oldShading;
		private final ShadingProfile newShading;
		private final boolean oldEnabled;
		private final boolean newEnabled;
		private final List<ShadingLightSource> oldSelection;
		private final ShadingLightSource oldSelectedSource;
		private final ShadingLightSource newSelectedSource;

		public SetShadingProfile(ShadingProfile profile)
		{
			super("Change Shading Profile");

			this.oldShading = map.scripts.shadingProfile.get();
			this.newShading = profile;
			this.oldEnabled = map.scripts.hasSpriteShading.get();
			this.newEnabled = oldEnabled && newShading != null;
			this.oldSelection = getSelectedSources(oldShading);
			this.oldSelectedSource = oldShading == null ? null : oldShading.selectedSource;
			this.newSelectedSource = newShading == null ? null : newShading.selectedSource;
		}

		@Override
		public boolean shouldExec()
		{
			return newShading != oldShading || newEnabled != oldEnabled;
		}

		@Override
		public void exec()
		{
			super.exec();
			if (oldEnabled) {
				removeSourcesFromEditor(oldShading);
				oldShading.setSelectedSource(oldSelectedSource);
			}

			setShadingState(newShading, newEnabled);
			if (newEnabled)
				addSourcesToEditor(newShading, getActivationSelection(null, newSelectedSource));
		}

		@Override
		public void undo()
		{
			super.undo();
			if (newEnabled) {
				removeSourcesFromEditor(newShading);
				newShading.setSelectedSource(newSelectedSource);
			}

			setShadingState(oldShading, oldEnabled);
			if (oldEnabled)
				addSourcesToEditor(oldShading, oldSelection);
			else if (oldShading != null)
				oldShading.setSelectedSource(oldSelectedSource);
		}
	}

	private class SetShadingEnabled extends AbstractCommand
	{
		private final ShadingProfile profile;
		private final boolean oldValue;
		private final boolean newValue;
		private final List<ShadingLightSource> oldSelection;
		private final ShadingLightSource oldSelectedSource;

		public SetShadingEnabled(boolean enabled)
		{
			super(enabled ? "Enable Sprite Shading" : "Disable Sprite Shading");
			profile = map.scripts.shadingProfile.get();
			oldValue = map.scripts.hasSpriteShading.get();
			newValue = enabled && profile != null;
			oldSelection = getSelectedSources(profile);
			oldSelectedSource = profile == null ? null : profile.selectedSource;
		}

		@Override
		public boolean shouldExec()
		{
			return oldValue != newValue;
		}

		@Override
		public void exec()
		{
			super.exec();
			if (oldValue) {
				removeSourcesFromEditor(profile);
				profile.setSelectedSource(oldSelectedSource);
			}

			setShadingState(profile, newValue);
			if (newValue)
				addSourcesToEditor(profile, getActivationSelection(oldSelection, oldSelectedSource));
		}

		@Override
		public void undo()
		{
			super.undo();
			if (newValue) {
				removeSourcesFromEditor(profile);
				profile.setSelectedSource(oldSelectedSource);
			}

			setShadingState(profile, oldValue);
			if (oldValue)
				addSourcesToEditor(profile, oldSelection);
			else if (profile != null)
				profile.setSelectedSource(oldSelectedSource);
		}
	}

	private void setShadingState(ShadingProfile profile, boolean enabled)
	{
		boolean profileChanged = map.scripts.shadingProfile.get() != profile;
		boolean enabledChanged = map.scripts.hasSpriteShading.get() != enabled;
		map.scripts.shadingProfile.set(profile, false);
		map.scripts.hasSpriteShading.set(enabled, false);
		if (profileChanged)
			map.scripts.shadingProfile.fireCallbacks();
		else if (enabledChanged)
			map.scripts.hasSpriteShading.fireCallbacks();
		updateFields(map.scripts);
	}

	private List<ShadingLightSource> getActivationSelection(List<ShadingLightSource> selectedSources, ShadingLightSource selectedSource)
	{
		if (selectedSources != null && !selectedSources.isEmpty())
			return selectedSources;
		if (selectedSource != null)
			return Collections.singletonList(selectedSource);
		return null;
	}

	private List<ShadingLightSource> getSelectedSources(ShadingProfile profile)
	{
		List<ShadingLightSource> selectedSources = new ArrayList<>();
		if (profile != null) {
			for (ShadingLightSource source : profile.sources) {
				if (source.selected)
					selectedSources.add(source);
			}
		}
		return selectedSources;
	}

	private void removeSourcesFromEditor(ShadingProfile profile)
	{
		if (profile == null)
			return;

		for (ShadingLightSource source : profile.sources)
			source.removeFromEditor();
	}

	private void addSourcesToEditor(ShadingProfile profile, List<ShadingLightSource> selectedSources)
	{
		if (profile == null)
			return;

		profile.setSelectedSource(null);
		for (ShadingLightSource source : profile.sources) {
			if (source.selected)
				MapEditor.instance().selectionManager.deselectObject(source);
			source.addToEditor();
		}

		if (selectedSources != null) {
			for (ShadingLightSource source : selectedSources)
				MapEditor.instance().selectionManager.selectObject(source);
		}
	}
}
