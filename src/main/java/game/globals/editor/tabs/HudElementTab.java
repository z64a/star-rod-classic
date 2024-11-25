package game.globals.editor.tabs;

import static app.Directories.EXT_HUD_SCRIPT;
import static app.Directories.MOD_HUD_SCRIPTS;

import java.awt.Desktop;
import java.awt.Font;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListCellRenderer;
import javax.swing.SwingConstants;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.undo.UndoManager;

import com.alexandriasoftware.swing.JSplitButton;

import app.StarRodException;
import app.SwingUtils;
import app.input.IOUtils;
import game.globals.ItemRecord;
import game.globals.editor.GlobalsData.GlobalsCategory;
import game.globals.editor.GlobalsEditor;
import game.globals.editor.GlobalsListModel;
import game.globals.editor.ListConfirmDialog;
import game.globals.editor.renderers.HudElementListRenderer;
import game.texture.images.HudElementRecord;
import net.miginfocom.swing.MigLayout;
import util.Logger;
import util.ui.StringField;

public class HudElementTab extends MultiListTab<HudElementRecord>
{
	private JLabel nameLabel;
	private JTextField nameField;
	private JTextArea scriptTextArea;
	private UndoManager scriptUndoManager;

	@SuppressWarnings("unchecked")
	public HudElementTab(GlobalsEditor editor, int tabIndex)
	{
		super(editor, tabIndex, new String[] { "Item Icons", "Always Loaded", "During Battle Only", "Pause and File Menus Only" },
			editor.data.itemHudElements, editor.data.globalHudElements, editor.data.battleHudElements, editor.data.menuHudElements);
	}

	@Override
	protected String getTabName()
	{
		return "Hud Elements";
	}

	@Override
	protected String getIconPath()
	{
		return "ui/battle/status/static_5";
	}

	@Override
	protected GlobalsCategory getDataType()
	{
		return GlobalsCategory.HUD_SCRIPTS;
	}

	@Override
	protected void notifyDataChange(GlobalsCategory type)
	{
		if (type != GlobalsCategory.HUD_SCRIPTS)
			return;

		reacquireSelection();
		repaintList();
	}

	@Override
	protected void addListButtons(JPanel listPanel)
	{
		JButton addButton = new JButton("Add Script");
		addButton.addActionListener((e) -> {
			HudElementRecord newElem = new HudElementRecord();
			newElem.identifier = "NewHudElement";
			newElem.setBody(HudElementRecord.DEFAULT_SCRIPT);
			getCurrentListModel().addElement(newElem);

			updateListFilter();
			list.setSelectedValue(newElem, false);
			list.ensureIndexIsVisible(list.getSelectedIndex());
			setModified();
		});
		SwingUtils.addBorderPadding(addButton);

		JSplitButton actionsButton = new JSplitButton("Actions  ");
		JPopupMenu actionsPopup = new JPopupMenu();
		actionsButton.setPopupMenu(actionsPopup);
		actionsButton.setAlwaysPopup(true);
		SwingUtils.addBorderPadding(actionsButton);
		JMenuItem menuItem;

		menuItem = new JMenuItem("Open Scripts Directory");
		menuItem.setPreferredSize(POPUP_OPTION_SIZE);
		menuItem.addActionListener(e -> {
			actionOpenScriptsDir();
		});
		actionsPopup.add(menuItem);

		menuItem = new JMenuItem("Create Missing Scripts");
		menuItem.setPreferredSize(POPUP_OPTION_SIZE);
		menuItem.addActionListener(evt -> {
			actionFileCleanup(false);
		});
		actionsPopup.add(menuItem);

		menuItem = new JMenuItem("Delete Unused Scripts");
		menuItem.setPreferredSize(POPUP_OPTION_SIZE);
		menuItem.addActionListener(evt -> {
			actionFileCleanup(true);
		});
		actionsPopup.add(menuItem);

		listPanel.add(addButton, "span, split 2, grow, sg but");
		listPanel.add(actionsButton, "grow, sg but");
	}

	private void actionFileCleanup(boolean deleteMode)
	{
		if (!MOD_HUD_SCRIPTS.toFile().exists())
			throw new StarRodException("Can't find HUD element scripts directory: %n%s",
				MOD_HUD_SCRIPTS.toFile().getAbsolutePath());
		try {
			Collection<File> scriptFiles = IOUtils.getFilesWithExtension(MOD_HUD_SCRIPTS, EXT_HUD_SCRIPT, true);
			ArrayList<HudElementRecord> missingScripts = new ArrayList<>();

			for (HudElementRecord rec : editor.data.itemHudElements) {
				File f = new File(MOD_HUD_SCRIPTS + rec.identifier + EXT_HUD_SCRIPT);
				if (f.exists())
					scriptFiles.remove(f);
				else
					missingScripts.add(rec);
			}

			for (HudElementRecord rec : editor.data.globalHudElements) {
				File f = new File(MOD_HUD_SCRIPTS + rec.identifier + EXT_HUD_SCRIPT);
				if (f.exists())
					scriptFiles.remove(f);
				else
					missingScripts.add(rec);
			}

			for (HudElementRecord rec : editor.data.battleHudElements) {
				File f = new File(MOD_HUD_SCRIPTS + rec.identifier + EXT_HUD_SCRIPT);
				if (f.exists())
					scriptFiles.remove(f);
				else
					missingScripts.add(rec);
			}

			for (HudElementRecord rec : editor.data.menuHudElements) {
				File f = new File(MOD_HUD_SCRIPTS + rec.identifier + EXT_HUD_SCRIPT);
				if (f.exists())
					scriptFiles.remove(f);
				else
					missingScripts.add(rec);
			}

			if (deleteMode) {
				ListConfirmDialog<File> dialog = new ListConfirmDialog<>("The following files will be deleted:", scriptFiles);
				SwingUtils.showModalDialog(dialog, "Deleting Unused Scripts");
				if (dialog.isResultAccepted()) {
					for (File f : scriptFiles) {
						Logger.log("Deleting HUD element script: " + f.getName());
						IOUtils.disposeOrDelete(f);
					}
				}
			}
			else {
				ListConfirmDialog<HudElementRecord> dialog = new ListConfirmDialog<>("The following scripts will be created:", missingScripts);
				SwingUtils.showModalDialog(dialog, "Creating Missing Scripts");
				if (dialog.isResultAccepted()) {
					for (HudElementRecord rec : missingScripts) {
						Logger.log("Saving HUD element script: " + rec.identifier + EXT_HUD_SCRIPT);
						rec.buildAutoScript(rec.identifier, 32, 32);
						rec.save();
					}
				}
			}

		}
		catch (IOException e) {
			throw new StarRodException("IOException while cleaning up HUD element scripts: %n%s", e.getMessage());
		}
	}

	private void actionOpenScriptsDir()
	{
		if (!MOD_HUD_SCRIPTS.toFile().exists())
			throw new StarRodException("Can't find HUD element scripts directory: %n%s",
				MOD_HUD_SCRIPTS.toFile().getAbsolutePath());
		try {
			Desktop.getDesktop().open(MOD_HUD_SCRIPTS.toFile());
		}
		catch (IOException e) {
			throw new StarRodException("IOException while opening HUD element scripts directory: %n%s", e.getMessage());
		}
	}

	private String makeUniqueName(HudElementRecord script, String name)
	{
		String out = name;
		int suffix = 1;

		boolean done = false;
		outer:
		while (!done) {
			for (HudElementRecord rec : getCurrentListModel()) {
				if (rec != script && rec.identifier.equalsIgnoreCase(out)) {
					out = name + "_" + suffix++;
					continue outer;
				}
			}
			done = true;
		}

		return out;
	}

	@Override
	protected JPanel createInfoPanel(JLabel infoLabel)
	{
		nameLabel = SwingUtils.getLabel("???", 18);

		nameField = new StringField(JTextField.LEADING, (s) -> {
			HudElementRecord script = getSelected();
			if (script != null && !s.isBlank()) {
				String newValue = makeUniqueName(script, s);
				if (!script.identifier.equals(newValue)) {
					String oldValue = script.identifier;
					script.setIdentifier(newValue);
					updateInfoPanel(script);
					setModified();
					repaintList();

					GlobalsListModel<HudElementRecord> model = getCurrentListModel();
					model.rebuildNameCache();

					if (model == editor.data.itemHudElements) {
						for (ItemRecord item : editor.data.items) {
							if (item.hudElemName != null && item.hudElemName.equals(oldValue))
								item.hudElemName = newValue;
						}
					}
				}
			}
		});
		SwingUtils.addTextFieldFilter(nameField, "\\W+");

		scriptTextArea = new JTextArea();
		scriptTextArea.setTabSize(4);
		scriptTextArea.getDocument().addDocumentListener(new DocumentListener() {
			@Override
			public void insertUpdate(DocumentEvent e)
			{
				HudElementRecord script = getSelected();
				if (shouldIgnoreChanges() || script == null)
					return;
				String newValue = scriptTextArea.getText();
				if (!script.getBody().equals(newValue)) {
					script.setBody(newValue);
					setModified();
				}
			}

			@Override
			public void removeUpdate(DocumentEvent e)
			{
				HudElementRecord script = getSelected();
				if (shouldIgnoreChanges() || script == null)
					return;
				String newValue = scriptTextArea.getText();
				if (!script.getBody().equals(newValue)) {
					script.setBody(newValue);
					setModified();
				}
			}

			@Override
			public void changedUpdate(DocumentEvent e)
			{}
		});
		JScrollPane scrollPane = new JScrollPane(scriptTextArea);
		SwingUtils.addBorderPadding(scriptTextArea);
		scriptTextArea.setFont(new Font("monospaced", Font.PLAIN, 12));
		scriptUndoManager = SwingUtils.addUndoRedo(scriptTextArea);

		JButton saveButton = new JButton("Save File");
		saveButton.addActionListener((evt) -> {
			HudElementRecord script = getSelected();
			if (script == null || !script.getModified())
				return;

			try {
				Logger.log("Saved HUD element script to " + script.save());
			}
			catch (IOException e) {
				Logger.printStackTrace(e);
			}
		});
		SwingUtils.addBorderPadding(saveButton);

		JButton reloadButton = new JButton("Reload File");
		reloadButton.addActionListener((evt) -> {
			HudElementRecord script = getSelected();
			if (script == null)
				return;

			try {
				Logger.log("Loaded HUD element script from " + script.load());
				scriptTextArea.setText(script.getBody());
				setModified();
			}
			catch (IOException e) {
				Logger.printStackTrace(e);
			}
		});
		SwingUtils.addBorderPadding(reloadButton);

		JButton newButton = new JButton("Reset Script");
		newButton.addActionListener((evt) -> {
			HudElementRecord script = getSelected();
			if (script == null)
				return;

			script.setBody(HudElementRecord.DEFAULT_SCRIPT);
			scriptTextArea.setText(script.getBody());
			setModified();
		});
		SwingUtils.addBorderPadding(newButton);

		JPanel infoPanel = new JPanel(new MigLayout("ins 0, fill", "[60!]5[395!][grow]"));

		infoPanel.add(nameLabel, "span, h 32!, wrap");

		infoPanel.add(SwingUtils.getLabel("Name", SwingConstants.RIGHT, 12));
		infoPanel.add(nameField, "growx, wrap");

		infoPanel.add(new JLabel(), "span, h 4!, wrap");

		infoPanel.add(saveButton, "span 2, split 3, sg button, grow");
		infoPanel.add(reloadButton, "sg button, grow");
		infoPanel.add(newButton, "sg button, grow, wrap");

		JPanel embedPanel = new JPanel(new MigLayout("ins 8 16 8 16, fill, wrap"));
		embedPanel.add(infoPanel, "growx");
		embedPanel.add(scrollPane, "grow, push");
		embedPanel.add(infoLabel, "growx");

		return embedPanel;
	}

	@Override
	protected void updateInfoPanel(HudElementRecord rec, boolean fromSet)
	{
		nameLabel.setText(rec.identifier);
		nameField.setText(rec.identifier);
		scriptTextArea.setText(rec.getBody());

		if (fromSet)
			scriptUndoManager.discardAllEdits();
	}

	@Override
	protected ListCellRenderer<HudElementRecord> getCellRenderer()
	{
		return new HudElementListRenderer(editor.data);
	}
}
