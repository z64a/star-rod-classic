package game.globals.editor.tabs;

import static app.Directories.EXT_ITEM_SCRIPT;
import static app.Directories.MOD_ITEM_SCRIPTS;

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
import game.globals.editor.ListConfirmDialog;
import game.globals.editor.renderers.ItemEntityListRenderer;
import game.texture.images.ItemEntityRecord;
import net.miginfocom.swing.MigLayout;
import util.Logger;
import util.ui.StringField;

public class ItemEntityTab extends SingleListTab<ItemEntityRecord>
{
	private JLabel nameLabel;
	private JTextField nameField;
	private JTextArea scriptTextArea;
	private UndoManager scriptUndoManager;

	public ItemEntityTab(GlobalsEditor editor, int tabIndex)
	{
		super(editor, tabIndex, editor.data.itemEntities);
	}

	@Override
	protected String getTabName()
	{
		return "Item Entities";
	}

	@Override
	protected String getIconPath()
	{
		return "item/anim/star_piece_0";
	}

	@Override
	protected GlobalsCategory getDataType()
	{
		return GlobalsCategory.ITEM_SCRIPTS;
	}

	@Override
	protected void notifyDataChange(GlobalsCategory type)
	{
		if (type != GlobalsCategory.ITEM_SCRIPTS)
			return;

		reacquireSelection();
		repaintList();
	}

	@Override
	protected void addListButtons(JPanel listPanel)
	{
		JButton addButton = new JButton("Add Script");
		addButton.addActionListener((e) -> {
			ItemEntityRecord newElem = new ItemEntityRecord();
			newElem.identifier = "NewItemEntity";
			newElem.setBody(ItemEntityRecord.DEFAULT_SCRIPT);
			listModel.addElement(newElem);

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
		if (!MOD_ITEM_SCRIPTS.toFile().exists())
			throw new StarRodException("Can't find item entity scripts directory: %n%s",
				MOD_ITEM_SCRIPTS.toFile().getAbsolutePath());
		try {
			Collection<File> scriptFiles = IOUtils.getFilesWithExtension(MOD_ITEM_SCRIPTS, EXT_ITEM_SCRIPT, true);
			ArrayList<ItemEntityRecord> missingScripts = new ArrayList<>();

			for (ItemEntityRecord rec : editor.data.itemEntities) {
				File f = new File(MOD_ITEM_SCRIPTS + rec.identifier + EXT_ITEM_SCRIPT);
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
						Logger.log("Deleting item entity script: " + f.getName());
						IOUtils.disposeOrDelete(f);
					}
				}
			}
			else {
				ListConfirmDialog<ItemEntityRecord> dialog = new ListConfirmDialog<>("The following scripts will be created:", missingScripts);
				SwingUtils.showModalDialog(dialog, "Creating Missing Scripts");
				if (dialog.isResultAccepted()) {
					for (ItemEntityRecord rec : missingScripts) {
						Logger.log("Saving item entity script: " + rec.identifier + EXT_ITEM_SCRIPT);
						rec.buildAutoScript(rec.identifier);
						rec.save();
					}
				}
			}

		}
		catch (IOException e) {
			throw new StarRodException("IOException while cleaning up item entity scripts: %n%s", e.getMessage());
		}
	}

	private void actionOpenScriptsDir()
	{
		if (!MOD_ITEM_SCRIPTS.toFile().exists())
			throw new StarRodException("Can't find item entity scripts directory: %n%s",
				MOD_ITEM_SCRIPTS.toFile().getAbsolutePath());
		try {
			Desktop.getDesktop().open(MOD_ITEM_SCRIPTS.toFile());
		}
		catch (IOException e) {
			throw new StarRodException("IOException while opening item entity scripts directory: %n%s", e.getMessage());
		}
	}

	private String makeUniqueName(ItemEntityRecord script, String name)
	{
		String out = name;
		int suffix = 1;

		boolean done = false;
		outer:
		while (!done) {
			for (ItemEntityRecord rec : listModel) {
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
			ItemEntityRecord script = getSelected();
			if (script != null && !s.isBlank()) {
				String newValue = makeUniqueName(script, s);
				if (!script.identifier.equals(newValue)) {
					String oldValue = script.identifier;
					script.identifier = newValue;
					updateInfoPanel(script);
					setModified();
					repaintList();

					listModel.rebuildNameCache();

					for (ItemRecord item : editor.data.items) {
						if (item.itemEntityName != null && item.itemEntityName.equals(oldValue))
							item.itemEntityName = newValue;
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
				ItemEntityRecord script = getSelected();
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
				ItemEntityRecord script = getSelected();
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
			ItemEntityRecord script = getSelected();
			if (script == null || !script.getModified())
				return;

			try {
				Logger.log("Saved item entity script to " + script.save());
			}
			catch (IOException e) {
				Logger.printStackTrace(e);
			}
		});
		SwingUtils.addBorderPadding(saveButton);

		JButton reloadButton = new JButton("Reload File");
		reloadButton.addActionListener((evt) -> {
			ItemEntityRecord script = getSelected();
			if (script == null)
				return;

			try {
				Logger.log("Loaded item entity script from " + script.load());
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
			ItemEntityRecord script = getSelected();
			if (script == null)
				return;

			script.setBody(ItemEntityRecord.DEFAULT_SCRIPT);
			scriptTextArea.setText(script.getBody());
			setModified();
		});
		SwingUtils.addBorderPadding(newButton);

		JPanel infoPanel = new JPanel(new MigLayout("ins 0, fill", "[60!]5[395!][grow]"));

		infoPanel.add(nameLabel, "span, h 32!, wrap");

		infoPanel.add(SwingUtils.getLabel("Name", 12));
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
	protected void updateInfoPanel(ItemEntityRecord rec, boolean fromSet)
	{
		nameLabel.setText(rec.identifier);
		nameField.setText(rec.identifier);
		scriptTextArea.setText(rec.getBody());

		if (fromSet)
			scriptUndoManager.discardAllEdits();
	}

	@Override
	protected ListCellRenderer<ItemEntityRecord> getCellRenderer()
	{
		return new ItemEntityListRenderer(editor.data);
	}
}
