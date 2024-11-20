package game.globals.editor.tabs;

import java.awt.Toolkit;

import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JTextField;
import javax.swing.ListCellRenderer;

import com.alexandriasoftware.swing.JSplitButton;

import game.globals.ItemRecord;
import game.globals.MoveRecord;
import game.globals.editor.GlobalsData.GlobalsCategory;
import game.globals.editor.GlobalsEditor;
import game.globals.editor.ListSelectorDialog;
import game.globals.editor.renderers.MessageCellRenderer;
import game.globals.editor.renderers.MoveRecordCellRenderer;
import game.globals.editor.renderers.PaddedCellRenderer;
import game.shared.ProjectDatabase;
import game.shared.ProjectDatabase.ConstEnum.EnumPair;
import game.string.PMString;
import net.miginfocom.swing.MigLayout;
import shared.SwingUtils;
import util.Logger;
import util.MathUtil;
import util.ui.FlagEditorPanel;
import util.ui.IntTextField;
import util.ui.StringField;

public class MoveTab extends SingleListTab<MoveRecord>
{
	private JComboBox<String> abilityBox;
	private DefaultComboBoxModel<String> abilityListModel;

	private JComboBox<String> battleMessageBox;
	private DefaultComboBoxModel<String> battleMessageModel;

	private JComboBox<String> moveTypeBox;
	private DefaultComboBoxModel<String> moveTypeListModel;

	private JLabel displayNameLabel;
	private StringField nameField;

	private StringField nameMsgField;
	private StringField shortDescMsgField;
	private StringField fullDescMsgField;

	private JLabel nameMsgPreview;
	private JLabel fullDescMsgPreview;
	private JLabel shortDescMsgPreview;

	private JLabel flagsLabel;

	private IntTextField fpCostField;
	private IntTextField bpCostField;

	private boolean reloading;

	private static final String[] BATTLE_INPUT_MESSAGE_IDS = {
			"1D-0AF", "1D-0B0", "1D-0B1", "1D-0B2", "1D-0B3", "1D-0B4", "1D-0AF", "1D-0AF",
			"1D-0B5", "1D-0B6", "1D-0B7", "1D-0B8", "1D-0B9", "1D-0AF", "1D-0BA", "1D-0BB",
			"1D-0BC", "1D-0AF", "1D-0BD", "1D-0BE", "1D-0BF"
	};

	public MoveTab(GlobalsEditor editor, int tabIndex)
	{
		super(editor, tabIndex, editor.data.moves);
	}

	@Override
	protected String getTabName()
	{
		return "Moves";
	}

	@Override
	protected String getIconPath()
	{
		return "item/badge/PowerJump";
	}

	@Override
	protected GlobalsCategory getDataType()
	{
		return GlobalsCategory.MOVE_TABLE;
	}

	@Override
	protected void notifyDataChange(GlobalsCategory type)
	{
		if (type != GlobalsCategory.MOVE_TABLE)
			return;

		loadExternalData();
		reacquireSelection();
		repaintList();
	}

	private void loadExternalData()
	{
		reloading = true;
		abilityListModel.removeAllElements();
		abilityListModel.addElement(MoveRecord.NONE);
		for (EnumPair e : ProjectDatabase.AbilityType.getDecoding())
			abilityListModel.addElement(e.value);

		moveTypeListModel.removeAllElements();
		for (EnumPair e : ProjectDatabase.getFromNamespace("MoveType").getDecoding())
			moveTypeListModel.addElement(e.value);

		battleMessageModel.removeAllElements();
		battleMessageModel.addElement(MoveRecord.NONE);
		for (int i = 0; i < BATTLE_INPUT_MESSAGE_IDS.length; i++) {
			PMString message = editor.messageNameMap.get(BATTLE_INPUT_MESSAGE_IDS[i]);
			if (message == null)
				battleMessageModel.addElement(BATTLE_INPUT_MESSAGE_IDS[i]);
			else
				battleMessageModel.addElement(message.toString());
		}
		reloading = false;
	}

	private PMString chooseMessage(String title)
	{
		ListSelectorDialog<PMString> chooser = new ListSelectorDialog<>(editor.messageListModel, new MessageCellRenderer(48));

		SwingUtils.showModalDialog(chooser, title);
		if (!chooser.isResultAccepted())
			return null;

		PMString selected = chooser.getValue();
		return selected;
	}

	private void updateMessageField(StringField field, JLabel lbl, String identifier)
	{
		field.setText(identifier);

		PMString msg = editor.getMessage(identifier);
		if (msg == null) {
			field.setForeground(SwingUtils.getRedTextColor());
			lbl.setForeground(SwingUtils.getRedTextColor());
			lbl.setText("Unknown identifier");
		}
		else {
			field.setForeground(null);
			lbl.setForeground(null);

			String s = msg.toString();
			if (s.length() > 60) {
				s = s.substring(0, 60);
				int lastSpace = s.lastIndexOf(" ");
				if (lastSpace > 48)
					s = s.substring(0, lastSpace);
				else
					s = s.substring(0, 56);
				s += " ...";
			}
			lbl.setText(s);
		}
	}

	private String makeUniqueName(MoveRecord move, String name)
	{
		String out = name;
		int suffix = 1;

		boolean done = false;
		outer:
		while (!done) {
			for (MoveRecord rec : listModel) {
				if (rec != move && rec.identifier.equalsIgnoreCase(out)) {
					out = name + "_" + suffix++;
					continue outer;
				}
			}
			done = true;
		}

		return out;
	}

	private void makeUniqueName(MoveRecord move)
	{
		move.setName(makeUniqueName(move, move.identifier));
	}

	private MoveRecord makeNewMove()
	{
		MoveRecord newMove = new MoveRecord(listModel.size());
		newMove.abilityName = "";
		newMove.inputPopupIndex = -1;
		return newMove;
	}

	@Override
	protected void addListButtons(JPanel listPanel)
	{
		JButton addButton = new JButton("Add Move");
		addButton.addActionListener((e) -> {
			if (listModel.getSize() > 0xFF) {
				Logger.log("Can't add any more moves!");
				Toolkit.getDefaultToolkit().beep();
				return;
			}

			MoveRecord newMove = makeNewMove();
			newMove.setName("NewMove");
			makeUniqueName(newMove);
			listModel.addElement(newMove);
			onModelDataChange();

			updateListFilter();
			list.setSelectedValue(newMove, false);
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

		menuItem = new JMenuItem("Copy Data");
		menuItem.setPreferredSize(POPUP_OPTION_SIZE);
		menuItem.addActionListener(e -> {
			clipboard = getSelected();
		});
		actionsPopup.add(menuItem);

		menuItem = new JMenuItem("Paste Data");
		menuItem.setPreferredSize(POPUP_OPTION_SIZE);
		menuItem.addActionListener(e -> {
			MoveRecord move = getSelected();
			if (move == null || clipboard == null) {
				Toolkit.getDefaultToolkit().beep();
				return;
			}
			move.copyFrom(clipboard);
			makeUniqueName(move);
			onModelDataChange();
			setModified();

			updateInfoPanel(move);
			repaintList();
		});
		actionsPopup.add(menuItem);

		menuItem = new JMenuItem("Clear Data");
		menuItem.setPreferredSize(POPUP_OPTION_SIZE);
		menuItem.addActionListener(e -> {
			MoveRecord move = getSelected();
			if (move == null) {
				Toolkit.getDefaultToolkit().beep();
				return;
			}
			MoveRecord newMove = makeNewMove();
			newMove.identifier = move.identifier;
			move.copyFrom(newMove);
			onModelDataChange();
			setModified();

			updateInfoPanel(move);
			repaintList();
		});
		actionsPopup.add(menuItem);

		listPanel.add(addButton, "span, split 2, grow, sg but");
		listPanel.add(actionsButton, "grow, sg but");
	}

	@Override
	protected JPanel createInfoPanel(JLabel infoLabel)
	{
		abilityListModel = new DefaultComboBoxModel<>();
		moveTypeListModel = new DefaultComboBoxModel<>();
		battleMessageModel = new DefaultComboBoxModel<>();
		loadExternalData();

		displayNameLabel = SwingUtils.getLabel("???", 18);
		displayNameLabel.setIconTextGap(12);

		nameMsgPreview = new JLabel();
		fullDescMsgPreview = new JLabel();
		shortDescMsgPreview = new JLabel();

		nameField = new StringField(JTextField.LEADING, (s) -> {
			if (hasSelected() && !s.isBlank()) {
				MoveRecord move = getSelected();
				String newValue = makeUniqueName(move, s);
				if (!newValue.equals(move.identifier)) {
					String oldValue = move.identifier;
					move.setName(newValue);
					updateInfoPanel(move);
					setModified();
					repaintList();

					listModel.rebuildNameCache();

					for (ItemRecord item : editor.data.items) {
						if (item.moveName != null && item.moveName.equals(oldValue))
							item.moveName = newValue;
					}
				}
			}
		});
		SwingUtils.addTextFieldFilter(nameField, "\\W+");

		nameMsgField = new StringField(JTextField.LEADING, (s) -> {
			MoveRecord move = getSelected();
			if (move != null && !s.equals(move.msgName)) {
				move.msgName = s;
				updateInfoPanel(move);
				setModified();
			}
		});
		SwingUtils.addTextFieldFilter(nameMsgField, "\\s+");

		shortDescMsgField = new StringField(JTextField.LEADING, (s) -> {
			MoveRecord move = getSelected();
			if (move != null && !s.equals(move.msgShortDesc)) {
				move.msgShortDesc = s;
				updateInfoPanel(move);
				setModified();
			}
		});
		SwingUtils.addTextFieldFilter(shortDescMsgField, "\\s+");

		fullDescMsgField = new StringField(JTextField.LEADING, (s) -> {
			MoveRecord move = getSelected();
			if (move != null && !s.equals(move.msgFullDesc)) {
				move.msgFullDesc = s;
				updateInfoPanel(move);
				setModified();
			}
		});
		SwingUtils.addTextFieldFilter(fullDescMsgField, "\\s+");

		JButton chooseNameButton = new JButton("Choose");
		chooseNameButton.addActionListener((e) -> {
			if (hasSelected()) {
				MoveRecord move = getSelected();
				PMString msg = chooseMessage("Choose Name");
				if (msg != null) {
					String newValue = msg.getIdentifier();
					if (!newValue.equals(move.msgName)) {
						move.msgName = msg.getIdentifier();
						updateInfoPanel(move);
						setModified();
					}
				}
			}
		});
		SwingUtils.addBorderPadding(chooseNameButton);

		JButton chooseFullDescButton = new JButton("Choose");
		chooseFullDescButton.addActionListener((e) -> {
			if (hasSelected()) {
				MoveRecord move = getSelected();
				PMString msg = chooseMessage("Choose Full Description");
				if (msg != null) {
					String newValue = msg.getIdentifier();
					if (!newValue.equals(move.msgFullDesc)) {
						move.msgFullDesc = newValue;
						updateInfoPanel(move);
						setModified();
					}
				}
			}
		});
		SwingUtils.addBorderPadding(chooseFullDescButton);

		JButton chooseShortDescButton = new JButton("Choose");
		chooseShortDescButton.addActionListener((e) -> {
			if (hasSelected()) {
				MoveRecord move = getSelected();
				PMString msg = chooseMessage("Choose Short Description");
				if (msg != null) {
					String newValue = msg.getIdentifier();
					if (!newValue.equals(move.msgShortDesc)) {
						move.msgShortDesc = msg.getIdentifier();
						updateInfoPanel(move);
						setModified();
					}
				}
			}
		});
		SwingUtils.addBorderPadding(chooseShortDescButton);

		flagsLabel = SwingUtils.getLabel("00000000", 12);
		JButton flagsButton = new JButton("Edit");
		flagsButton.addActionListener((e) -> {
			if (hasSelected()) {
				MoveRecord move = getSelected();
				FlagEditorPanel flagPanel = new FlagEditorPanel(8, MoveRecord.FLAGS);
				flagPanel.setValue(move.flags);

				int choice = SwingUtils.getConfirmDialog()
					.setTitle("Set Flags")
					.setMessage(flagPanel)
					.setOptionsType(JOptionPane.OK_CANCEL_OPTION)
					.choose();

				if (choice == JOptionPane.YES_OPTION) {
					int newValue = flagPanel.getValue();
					if (newValue != move.flags) {
						move.flags = newValue;
						setModified();

						flagsLabel.setText(String.format("%04X", move.flags));
						flagsLabel.repaint();
					}
				}
			}
		});
		SwingUtils.addBorderPadding(flagsButton);

		fpCostField = new IntTextField((v) -> {
			if (hasSelected()) {
				MoveRecord move = getSelected();
				byte newValue = (byte) (int) v;
				if (newValue != move.fpCost) {
					move.fpCost = newValue;
					setModified();
				}
			}
		});
		SwingUtils.addBorderPadding(fpCostField);

		bpCostField = new IntTextField((v) -> {
			if (hasSelected()) {
				MoveRecord move = getSelected();
				byte newValue = (byte) (int) v;
				if (newValue != move.bpCost) {
					move.bpCost = newValue;
					setModified();
				}
			}
		});
		SwingUtils.addBorderPadding(bpCostField);

		abilityBox = new JComboBox<>();
		abilityBox.setModel(abilityListModel);
		abilityBox.setRenderer(new PaddedCellRenderer<>());
		abilityBox.setMaximumRowCount(16);
		abilityBox.addActionListener((e) -> {
			if (!hasSelected() || shouldIgnoreChanges() || reloading)
				return;
			MoveRecord move = getSelected();
			String newValue = (String) abilityBox.getSelectedItem();
			if (newValue == null)
				newValue = MoveRecord.NONE;
			if (!newValue.equals(move.abilityName)) {
				move.abilityName = newValue.equals(MoveRecord.NONE) ? "" : newValue;
				setModified();
			}
		});
		SwingUtils.addBorderPadding(abilityBox);

		battleMessageBox = new JComboBox<>();
		battleMessageBox.setModel(battleMessageModel);
		battleMessageBox.setRenderer(new PaddedCellRenderer<>(128));
		battleMessageBox.setMaximumRowCount(16);
		battleMessageBox.addActionListener((e) -> {
			if (!hasSelected() || shouldIgnoreChanges())
				return;
			MoveRecord move = getSelected();
			byte newValue = (byte) (battleMessageBox.getSelectedIndex() - 1);
			if (move.inputPopupIndex != newValue) {
				move.inputPopupIndex = newValue;
				setModified();
			}
		});
		SwingUtils.addBorderPadding(battleMessageBox);

		moveTypeBox = new JComboBox<>();
		moveTypeBox.setModel(moveTypeListModel);
		moveTypeBox.setRenderer(new PaddedCellRenderer<>());
		moveTypeBox.setMaximumRowCount(16);
		moveTypeBox.addActionListener((e) -> {
			if (!hasSelected() || shouldIgnoreChanges())
				return;
			MoveRecord move = getSelected();
			byte newValue = (byte) moveTypeBox.getSelectedIndex();
			if (move.category != newValue) {
				move.category = newValue;
				setModified();
			}
		});
		SwingUtils.addBorderPadding(moveTypeBox);

		JPanel infoPanel = new JPanel(new MigLayout("ins 0, fill, hidemode 3", "[90!]5[270!]5[90!][grow]"));
		infoPanel.add(displayNameLabel, "span, h 32!");

		infoPanel.add(SwingUtils.getLabel("Name", 12));
		infoPanel.add(nameField, "grow, wrap");

		infoPanel.add(new JLabel(""));
		infoPanel.add(nameMsgField, "grow");
		infoPanel.add(chooseNameButton, "grow, wrap");
		infoPanel.add(nameMsgPreview, "skip 1, span, grow, wrap");

		infoPanel.add(SwingUtils.getLabelWithTooltip("Short Desc", "Message used in shops"));
		infoPanel.add(shortDescMsgField, "grow");
		infoPanel.add(chooseShortDescButton, "grow, wrap");
		infoPanel.add(shortDescMsgPreview, "skip 1, span, grow, wrap");

		infoPanel.add(SwingUtils.getLabelWithTooltip("Full Desc", "Message used in most menus"));
		infoPanel.add(fullDescMsgField, "grow");
		infoPanel.add(chooseFullDescButton, "grow, wrap");
		infoPanel.add(fullDescMsgPreview, "skip 1, span, grow, wrap");

		infoPanel.add(new JLabel(), "span, h 4!, wrap");

		infoPanel.add(SwingUtils.getLabel("Target Flags", 12));
		infoPanel.add(flagsLabel, "grow");
		infoPanel.add(flagsButton, "grow, wrap");

		infoPanel.add(new JLabel(), "span, h 4!, wrap");

		infoPanel.add(SwingUtils.getLabelWithTooltip("Input Popup", "Sets the message explaining the action command controls."));
		infoPanel.add(battleMessageBox, "span 2, grow, wrap");

		infoPanel.add(SwingUtils.getLabelWithTooltip("Move Type", "Mostly determines where this move will appear in the battle menu."));
		infoPanel.add(moveTypeBox, "grow, wrap");

		infoPanel.add(SwingUtils.getLabel("Ability", 12));
		infoPanel.add(abilityBox, "grow, wrap");

		infoPanel.add(SwingUtils.getLabel("FP Cost", 12));
		infoPanel.add(fpCostField, "w 132!, wrap");

		infoPanel.add(SwingUtils.getLabel("BP Cost", 12));
		infoPanel.add(bpCostField, "w 132!, wrap");

		JPanel embedPanel = new JPanel(new MigLayout("ins 8 16 8 16, fill, wrap"));
		embedPanel.add(infoPanel, "growx");
		embedPanel.add(new JLabel(), "growy, pushy");
		embedPanel.add(infoLabel, "growx");

		return embedPanel;
	}

	@Override
	protected void updateInfoPanel(MoveRecord move, boolean fromSet)
	{
		displayNameLabel.setText(move.displayName);
		nameField.setText(move.identifier);

		if (move.abilityName == null || move.abilityName.isEmpty())
			abilityBox.setSelectedItem(MoveRecord.NONE);
		else
			abilityBox.setSelectedItem(move.abilityName);

		updateMessageField(nameMsgField, nameMsgPreview, move.msgName);
		updateMessageField(shortDescMsgField, shortDescMsgPreview, move.msgShortDesc);
		updateMessageField(fullDescMsgField, fullDescMsgPreview, move.msgFullDesc);

		flagsLabel.setText(String.format("%04X", move.flags));

		battleMessageBox.setSelectedIndex(1 + move.inputPopupIndex);
		moveTypeBox.setSelectedIndex(move.category);

		fpCostField.setValue(MathUtil.clamp(move.fpCost, 0, 255));
		bpCostField.setValue(MathUtil.clamp(move.bpCost, 0, 255));
	}

	@Override
	protected ListCellRenderer<MoveRecord> getCellRenderer()
	{
		return new MoveRecordCellRenderer();
	}
}
