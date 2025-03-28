package game.globals.editor.tabs;

import java.awt.Toolkit;
import java.util.ArrayList;
import java.util.List;

import javax.swing.ButtonGroup;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListModel;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JRadioButton;
import javax.swing.JTextField;
import javax.swing.ListCellRenderer;
import javax.swing.SwingConstants;

import com.alexandriasoftware.swing.JSplitButton;

import app.IconResource;
import app.SwingUtils;
import game.globals.ItemRecord;
import game.globals.MoveRecord;
import game.globals.editor.BadgeReorderDialog;
import game.globals.editor.GlobalsData.GlobalsCategory;
import game.globals.editor.GlobalsEditor;
import game.globals.editor.ListSelectorDialog;
import game.globals.editor.renderers.BadgeCellRenderer;
import game.globals.editor.renderers.HudElementListRenderer;
import game.globals.editor.renderers.ImageAssetBoxRenderer;
import game.globals.editor.renderers.ImageAssetListRenderer;
import game.globals.editor.renderers.ItemEntityBoxRenderer;
import game.globals.editor.renderers.ItemEntityListRenderer;
import game.globals.editor.renderers.ItemRecordListRenderer;
import game.globals.editor.renderers.MenuIconBoxRenderer;
import game.globals.editor.renderers.MessageCellRenderer;
import game.globals.editor.renderers.PaddedCellRenderer;
import game.string.PMString;
import game.texture.images.HudElementRecord;
import game.texture.images.ImageRecord;
import game.texture.images.ItemEntityRecord;
import net.miginfocom.swing.MigLayout;
import util.Logger;
import util.MathUtil;
import util.ui.FlagEditorPanel;
import util.ui.HexTextField;
import util.ui.IntTextField;
import util.ui.StringField;

public class ItemTab extends SingleListTab<ItemRecord>
{
	private JLabel displayIconLabel;
	private JLabel displayNameLabel;
	private StringField nameField;

	private StringField nameMsgField;
	private StringField fullDescMsgField;
	private StringField shortDescMsgField;

	private JLabel nameMsgPreview;
	private JLabel fullDescMsgPreview;
	private JLabel shortDescMsgPreview;

	private JLabel typeFlagsLabel;
	private JLabel targetFlagsLabel;

	private JLabel imageAssetPreview;
	private JLabel itemEntityPreview;
	private JLabel hudElementPreview;

	private JComboBox<String> imageAssetBox;
	private JComboBox<String> itemEntityBox;
	private JComboBox<String> hudElementBox;
	private JComboBox<String> moveBox;

	private JLabel moveLabel;
	private JButton chooseMoveButton;

	private JLabel sellValueLabel;
	private IntTextField sellValueField;

	private JButton editSortValueButton;
	private JLabel sortValueLabel;
	private HexTextField sortValueField;

	private JLabel potencyALabel;
	private IntTextField potencyAField;

	private JLabel potencyBLabel;
	private IntTextField potencyBField;

	private JRadioButton rbManualGraphics;
	private JRadioButton rbAutoGraphics;
	private JPanel manualGraphicsPanel;
	private JPanel autoGraphicsPanel;

	public ItemTab(GlobalsEditor editor, int tabIndex)
	{
		super(editor, tabIndex, editor.data.items);
	}

	@Override
	protected String getTabName()
	{
		return "Items";
	}

	@Override
	protected String getIconPath()
	{
		return "item/Items";
	}

	@Override
	protected GlobalsCategory getDataType()
	{
		return GlobalsCategory.ITEM_TABLE;
	}

	@Override
	protected void notifyDataChange(GlobalsCategory type)
	{
		if (type != GlobalsCategory.ITEM_TABLE)
			return;

		repaintList();
	}

	@Override
	public void onSelectTab()
	{
		DefaultComboBoxModel<String> moveModel = new DefaultComboBoxModel<>();
		for (MoveRecord rec : editor.data.moves)
			moveModel.addElement(rec.getIdentifier());
		moveBox.setModel(moveModel);

		DefaultComboBoxModel<String> imagesModel = new DefaultComboBoxModel<>();
		for (ImageRecord rec : editor.data.images)
			imagesModel.addElement(rec.getIdentifier());
		imageAssetBox.setModel(imagesModel);

		DefaultComboBoxModel<String> itemEntitiesModel = new DefaultComboBoxModel<>();
		for (ItemEntityRecord rec : editor.data.itemEntities)
			itemEntitiesModel.addElement(rec.getIdentifier());
		itemEntityBox.setModel(itemEntitiesModel);

		DefaultComboBoxModel<String> hudElemsModel = new DefaultComboBoxModel<>();
		for (HudElementRecord rec : editor.data.itemHudElements)
			hudElemsModel.addElement(rec.getIdentifier());
		hudElementBox.setModel(hudElemsModel);

		updateInfoPanel(getSelected(), false);
	}

	@Override
	protected void addListButtons(JPanel listPanel)
	{
		JButton addButton = new JButton("Add Item");
		addButton.addActionListener((e) -> {
			if (listModel.getSize() > 920) {
				Logger.log("Can't add any more items!");
				Toolkit.getDefaultToolkit().beep();
				return;
			}
			ItemRecord newItem = new ItemRecord(listModel.size());
			newItem.setName("NewItem");
			makeUniqueName(newItem);
			listModel.addElement(newItem);
			onModelDataChange();

			updateListFilter();
			list.setSelectedValue(newItem, false);
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
			ItemRecord item = getSelected();
			if (item == null || clipboard == null) {
				Toolkit.getDefaultToolkit().beep();
				return;
			}
			item.copyFrom(clipboard);
			makeUniqueName(item);
			onModelDataChange();
			setModified();

			updateInfoPanel(item);
			repaintList();
		});
		actionsPopup.add(menuItem);

		menuItem = new JMenuItem("Clear Data");
		menuItem.setPreferredSize(POPUP_OPTION_SIZE);
		menuItem.addActionListener(e -> {
			ItemRecord item = getSelected();
			if (item == null) {
				Toolkit.getDefaultToolkit().beep();
				return;
			}
			ItemRecord newItem = new ItemRecord(listModel.size());
			newItem.identifier = item.identifier;
			item.copyFrom(newItem);
			onModelDataChange();
			setModified();

			updateInfoPanel(item);
			repaintList();
		});
		actionsPopup.add(menuItem);

		listPanel.add(addButton, "span, split 2, grow, sg but");
		listPanel.add(actionsButton, "grow, sg but");
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

	private String makeUniqueName(ItemRecord item, String name)
	{
		String out = name;
		int suffix = 1;

		boolean done = false;
		outer:
		while (!done) {
			for (ItemRecord rec : listModel) {
				if (rec != item && rec.identifier.equalsIgnoreCase(out)) {
					out = name + "_" + suffix++;
					continue outer;
				}
			}
			done = true;
		}

		return out;
	}

	private void makeUniqueName(ItemRecord item)
	{
		item.setName(makeUniqueName(item, item.identifier));
	}

	@Override
	protected JPanel createInfoPanel(JLabel infoLabel)
	{
		displayIconLabel = new JLabel(IconResource.CROSS_24, SwingConstants.CENTER);
		displayNameLabel = SwingUtils.getLabel("???", 18);

		nameField = new StringField(JTextField.LEADING, (s) -> {
			if (hasSelected() && !s.isBlank()) {
				ItemRecord item = getSelected();
				s = makeUniqueName(item, s);
				if (!s.equals(item.identifier)) {
					item.setName(s);
					updateInfoPanel(item);
					setModified();
					repaintList();

					listModel.rebuildNameCache();
				}
			}
		});
		SwingUtils.addTextFieldFilter(nameField, "\\W+");

		nameMsgPreview = new JLabel();
		fullDescMsgPreview = new JLabel();
		shortDescMsgPreview = new JLabel();

		nameMsgField = new StringField(JTextField.LEADING, (s) -> {
			ItemRecord item = getSelected();
			if (item != null && !s.equals(item.msgName)) {
				item.msgName = s;
				updateInfoPanel(item);
				setModified();
			}
		});
		SwingUtils.addTextFieldFilter(nameMsgField, "\\s+");

		fullDescMsgField = new StringField(JTextField.LEADING, (s) -> {
			ItemRecord item = getSelected();
			if (item != null && !s.equals(item.msgFullDesc)) {
				item.msgFullDesc = s;
				updateInfoPanel(item);
				setModified();
			}
		});
		SwingUtils.addTextFieldFilter(fullDescMsgField, "\\s+");

		shortDescMsgField = new StringField(JTextField.LEADING, (s) -> {
			ItemRecord item = getSelected();
			if (item != null && !s.equals(item.msgShortDesc)) {
				item.msgShortDesc = s;
				updateInfoPanel(item);
				setModified();
			}
		});
		SwingUtils.addTextFieldFilter(shortDescMsgField, "\\s+");

		JButton chooseNameButton = new JButton("Choose");
		chooseNameButton.addActionListener((e) -> {
			if (hasSelected()) {
				ItemRecord item = getSelected();
				PMString msg = chooseMessage("Choose Name");
				if (msg != null) {
					String s = msg.getIdentifier();
					if (!s.equals(item.msgName)) {
						item.msgName = s;
						updateInfoPanel(item);
						setModified();
					}
				}
			}
		});

		JButton chooseFullDescButton = new JButton("Choose");
		chooseFullDescButton.addActionListener((e) -> {
			if (hasSelected()) {
				ItemRecord item = getSelected();
				PMString msg = chooseMessage("Choose Full Description");
				if (msg != null) {
					String s = msg.getIdentifier();
					if (!s.equals(item.msgFullDesc)) {
						item.msgFullDesc = s;
						updateInfoPanel(item);
						setModified();
					}
				}
			}
		});

		JButton chooseShortDescButton = new JButton("Choose");
		chooseShortDescButton.addActionListener((e) -> {
			if (hasSelected()) {
				ItemRecord item = getSelected();
				PMString msg = chooseMessage("Choose Short Description");
				if (msg != null) {
					String s = msg.getIdentifier();
					if (!s.equals(item.msgShortDesc)) {
						item.msgShortDesc = s;
						updateInfoPanel(item);
						setModified();
					}
				}
			}
		});

		typeFlagsLabel = SwingUtils.getLabel("0000", 12);
		JButton typeFlagsButton = new JButton("Edit");
		typeFlagsButton.addActionListener((e) -> {
			if (hasSelected()) {
				ItemRecord item = getSelected();
				FlagEditorPanel flagPanel = new FlagEditorPanel(4, ItemRecord.TYPE_FLAGS);
				flagPanel.setValue(item.typeFlags);

				int choice = SwingUtils.getConfirmDialog()
					.setTitle("Set Type Flags")
					.setMessage(flagPanel)
					.setOptionsType(JOptionPane.OK_CANCEL_OPTION)
					.choose();

				if (choice == JOptionPane.YES_OPTION) {
					short newValue = (short) flagPanel.getValue();
					if (item.typeFlags != newValue) {
						item.typeFlags = newValue;
						updateInfoPanel(item);
						setModified();

						typeFlagsLabel.setText(String.format("%04X", item.typeFlags));
						typeFlagsLabel.repaint();
					}
				}
			}
		});
		SwingUtils.addBorderPadding(typeFlagsButton);

		targetFlagsLabel = SwingUtils.getLabel("00000000", 12);
		JButton targetFlagsButton = new JButton("Edit");
		targetFlagsButton.addActionListener((e) -> {
			if (hasSelected()) {
				ItemRecord item = getSelected();
				FlagEditorPanel flagPanel = new FlagEditorPanel(8, ItemRecord.TARGET_FLAGS);
				flagPanel.setValue(item.targetFlags);

				int choice = SwingUtils.getConfirmDialog()
					.setTitle("Set Target Flags")
					.setMessage(flagPanel)
					.setOptionsType(JOptionPane.OK_CANCEL_OPTION)
					.choose();

				if (choice == JOptionPane.YES_OPTION) {
					int newValue = flagPanel.getValue();
					if (item.targetFlags != newValue) {
						item.targetFlags = newValue;
						setModified();

						targetFlagsLabel.setText(String.format("%04X", item.targetFlags));
						targetFlagsLabel.repaint();
					}
				}
			}
		});
		SwingUtils.addBorderPadding(targetFlagsButton);

		itemEntityPreview = new JLabel("", SwingConstants.CENTER);
		hudElementPreview = new JLabel("", SwingConstants.CENTER);
		imageAssetPreview = new JLabel("", SwingConstants.CENTER);

		imageAssetBox = new JComboBox<>();
		imageAssetBox.setRenderer(new ImageAssetBoxRenderer(editor.data));
		imageAssetBox.setMaximumRowCount(32);
		imageAssetBox.addActionListener((e) -> {
			if (!shouldIgnoreChanges() && hasSelected()) {
				ItemRecord item = getSelected();
				String newValue = (String) imageAssetBox.getSelectedItem();
				if (item.imageAssetName == null || !item.imageAssetName.equals(newValue)) {
					item.imageAssetName = (newValue == null) ? "" : newValue;
					updateInfoPanel(item);
					setModified();
				}
			}
		});
		imageAssetBox.setEditable(true);

		itemEntityBox = new JComboBox<>();
		itemEntityBox.setRenderer(new ItemEntityBoxRenderer(editor.data));
		itemEntityBox.setMaximumRowCount(32);
		itemEntityBox.addActionListener((e) -> {
			if (!shouldIgnoreChanges() && hasSelected()) {
				ItemRecord item = getSelected();
				String newValue = (String) itemEntityBox.getSelectedItem();
				if (item.itemEntityName == null || !item.itemEntityName.equals(newValue)) {
					item.itemEntityName = (newValue == null) ? "" : newValue;
					updateInfoPanel(item);
					setModified();
				}
			}
		});
		itemEntityBox.setEditable(true);

		hudElementBox = new JComboBox<>();
		hudElementBox.setRenderer(new MenuIconBoxRenderer(editor.data));
		hudElementBox.setMaximumRowCount(32);
		hudElementBox.addActionListener((e) -> {
			if (!shouldIgnoreChanges() && hasSelected()) {
				ItemRecord item = getSelected();
				String newValue = (String) hudElementBox.getSelectedItem();
				if (item.hudElemName == null || !item.hudElemName.equals(newValue)) {
					item.hudElemName = (newValue == null) ? "" : newValue;
					updateInfoPanel(item);
					setModified();
				}
			}
		});
		hudElementBox.setEditable(true);

		moveBox = new JComboBox<>();
		moveBox.setRenderer(new PaddedCellRenderer<>(24));
		moveBox.setMaximumRowCount(16);
		moveBox.addActionListener((e) -> {
			if (!shouldIgnoreChanges() && hasSelected()) {
				ItemRecord item = getSelected();
				String newValue = (String) moveBox.getSelectedItem();
				if (item.moveName == null || !item.moveName.equals(newValue)) {
					item.moveName = (newValue == null) ? "" : newValue;
					updateInfoPanel(item);
					setModified();
				}
			}
		});
		moveBox.setEditable(true);

		JButton chooseItemEntityButton = new JButton("Choose");
		chooseItemEntityButton.addActionListener((e) -> {
			if (hasSelected()) {
				ItemRecord item = getSelected();
				ListSelectorDialog<ItemEntityRecord> chooser = new ListSelectorDialog<>(
					editor.data.itemEntities, new ItemEntityListRenderer(editor.data));
				chooser.setValue(editor.data.itemEntities.getElement(item.itemEntityName));
				SwingUtils.showModalDialog(chooser, "Choose Item Entity");
				if (chooser.isResultAccepted())
					itemEntityBox.setSelectedItem(chooser.getValue() == null ? "" : chooser.getValue().getIdentifier());
			}
		});
		SwingUtils.addBorderPadding(chooseItemEntityButton);

		JButton chooseHudElemButton = new JButton("Choose");
		chooseHudElemButton.addActionListener((e) -> {
			if (hasSelected()) {
				ItemRecord item = getSelected();
				ListSelectorDialog<HudElementRecord> chooser = new ListSelectorDialog<>(
					editor.data.itemHudElements, new HudElementListRenderer(editor.data));
				chooser.setValue(editor.data.itemHudElements.getElement(item.hudElemName));
				SwingUtils.showModalDialog(chooser, "Choose HUD Element");
				if (chooser.isResultAccepted())
					hudElementBox.setSelectedItem(chooser.getValue() == null ? "" : chooser.getValue().getIdentifier());
			}
		});
		SwingUtils.addBorderPadding(chooseHudElemButton);

		JButton chooseImageAssetButton = new JButton("Choose");
		chooseImageAssetButton.addActionListener((e) -> {
			if (hasSelected()) {
				ItemRecord item = getSelected();
				ListSelectorDialog<ImageRecord> chooser = new ListSelectorDialog<>(
					editor.data.images, new ImageAssetListRenderer());
				chooser.setValue(editor.data.images.getElement(item.imageAssetName));
				SwingUtils.showModalDialog(chooser, "Choose Image");
				if (chooser.isResultAccepted())
					imageAssetBox.setSelectedItem(chooser.getValue() == null ? "" : chooser.getValue().getIdentifier());
			}
		});
		SwingUtils.addBorderPadding(chooseImageAssetButton);

		chooseMoveButton = new JButton("Choose");
		chooseMoveButton.addActionListener((e) -> {
			if (hasSelected()) {
				ItemRecord item = getSelected();
				ListSelectorDialog<MoveRecord> chooser = new ListSelectorDialog<>(editor.data.moves);
				chooser.setValue(editor.data.moves.getElement(item.moveName));
				SwingUtils.showModalDialog(chooser, "Choose Associated Move");
				if (chooser.isResultAccepted())
					moveBox.setSelectedItem(chooser.getValue() == null ? "" : chooser.getValue().getIdentifier());
			}
		});
		SwingUtils.addBorderPadding(chooseMoveButton);

		sortValueField = new HexTextField(4, (v) -> {
			if (hasSelected()) {
				ItemRecord item = getSelected();
				short newValue = (short) (int) v;
				if (newValue != item.sortValue) {
					item.sortValue = newValue;
					setModified();
				}
			}
		});
		SwingUtils.addBorderPadding(sortValueField);

		editSortValueButton = new JButton("Reorder");
		editSortValueButton.addActionListener((e) -> {
			// get all badges
			List<ItemRecord> badges = new ArrayList<>();
			for (ItemRecord rec : listModel) {
				if ((rec.typeFlags & 0x40) != 0) // isBadge
					badges.add(rec);
			}
			// sort by menu sort value
			badges.sort((ItemRecord a, ItemRecord b) -> Integer.compare(a.sortValue, b.sortValue));
			DefaultListModel<ItemRecord> sortedBadgeModel = new DefaultListModel<>();
			sortedBadgeModel.addAll(badges);

			BadgeReorderDialog chooser = new BadgeReorderDialog(sortedBadgeModel, new BadgeCellRenderer(editor.data));

			SwingUtils.showModalDialog(chooser, "Change Badge Menu Order");
			if (chooser.isResultAccepted()) {
				short currentValue = 0;
				short prevOldValue = 0;
				for (int i = 0; i < sortedBadgeModel.size(); i++) {
					ItemRecord item = sortedBadgeModel.get(i);
					if (i > 0 && item.sortValue != prevOldValue)
						currentValue++;
					prevOldValue = item.sortValue;
					if (item.sortValue != currentValue) {
						item.sortValue = currentValue;
						item.setModified(true);
						super.setModified();
					}
				}
				updateInfoPanel(getSelected());
			}
		});
		SwingUtils.addBorderPadding(editSortValueButton);

		potencyAField = new IntTextField((v) -> {
			if (hasSelected()) {
				ItemRecord item = getSelected();
				byte newValue = (byte) (int) v;
				if (newValue != item.potencyA) {
					item.potencyA = newValue;
					setModified();
				}
			}
		});
		SwingUtils.addBorderPadding(potencyAField);

		potencyBField = new IntTextField((v) -> {
			if (hasSelected()) {
				ItemRecord item = getSelected();
				byte newValue = (byte) (int) v;
				if (newValue != item.potencyB) {
					item.potencyB = newValue;
					setModified();
				}
			}
		});
		SwingUtils.addBorderPadding(potencyBField);

		sellValueField = new IntTextField((v) -> {
			if (hasSelected()) {
				ItemRecord item = getSelected();
				short newValue = (short) (int) v;
				if (newValue != item.sellValue) {
					item.sellValue = newValue;
					setModified();
				}
			}
		});
		SwingUtils.addBorderPadding(sellValueField);

		ButtonGroup bg = new ButtonGroup();
		rbManualGraphics = new JRadioButton("Select item entity and HUD element scripts");
		rbAutoGraphics = new JRadioButton("Create them automatically from an image");
		bg.add(rbManualGraphics);
		bg.add(rbAutoGraphics);

		rbManualGraphics.addActionListener((e) -> {
			if (hasSelected()) {
				ItemRecord item = getSelected();
				if (item.autoGraphics) {
					item.autoGraphics = false;
					updateInfoPanel(item);
					setModified();
				}
			}
		});

		rbAutoGraphics.addActionListener((e) -> {
			if (hasSelected()) {
				ItemRecord item = getSelected();
				if (!item.autoGraphics) {
					item.autoGraphics = true;
					updateInfoPanel(item);
					setModified();
				}
			}
		});

		JPanel infoPanel = new JPanel(new MigLayout("ins 0, fill, hidemode 3", "[90!]5[270!]5[90!][grow]"));
		infoPanel.add(displayIconLabel, "span, split 2, w 32!, h 32!");
		infoPanel.add(displayNameLabel, "gapleft 8, growx, center");

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

		infoPanel.add(new JLabel(), "span, wrap, h 4!");

		infoPanel.add(SwingUtils.getLabel("Type Flags", 12));
		infoPanel.add(typeFlagsLabel, "grow");
		infoPanel.add(typeFlagsButton, "grow, wrap");

		infoPanel.add(SwingUtils.getLabel("Target Flags", 12));
		infoPanel.add(targetFlagsLabel, "grow");
		infoPanel.add(targetFlagsButton, "grow, wrap");

		infoPanel.add(new JLabel(), "span, h 4!, wrap");

		infoPanel.add(SwingUtils.getLabel("Graphics", 12));

		infoPanel.add(rbManualGraphics, "span, grow, wrap");
		infoPanel.add(rbAutoGraphics, "skip 1, span, grow, wrap");

		infoPanel.add(new JLabel(), "span, h 4!, wrap");

		manualGraphicsPanel = new JPanel(new MigLayout("ins 0, fillx, wrap 3", "[90!]5[270!]5[90!][grow]"));
		autoGraphicsPanel = new JPanel(new MigLayout("ins 0, fillx, wrap 3", "[90!]5[270!]5[90!][grow]"));

		manualGraphicsPanel.add(SwingUtils.getLabelWithTooltip("Item Entity", SwingConstants.LEADING,
			"Item entity script used for displaying this item in the world"), "split 2, growx");
		manualGraphicsPanel.add(itemEntityPreview, "h 16!, w 16!");
		manualGraphicsPanel.add(itemEntityBox, "grow");
		manualGraphicsPanel.add(chooseItemEntityButton, "growx");

		manualGraphicsPanel.add(SwingUtils.getLabelWithTooltip("Menu Icon", SwingConstants.LEADING,
			"HUD element script used for displaying this item in menus"), "split 2, growx");
		manualGraphicsPanel.add(hudElementPreview, "h 16!, w 16!");
		manualGraphicsPanel.add(hudElementBox, "grow");
		manualGraphicsPanel.add(chooseHudElemButton, "growx");

		autoGraphicsPanel.add(SwingUtils.getLabelWithTooltip("Image Asset", SwingConstants.LEADING,
			"Image asset used for automatic graphics"), "split 2, growx");
		autoGraphicsPanel.add(imageAssetPreview, "h 16!, w 16!");
		autoGraphicsPanel.add(imageAssetBox, "grow");
		autoGraphicsPanel.add(chooseImageAssetButton, "growx");

		infoPanel.add(manualGraphicsPanel, "span, growx, wrap");
		infoPanel.add(autoGraphicsPanel, "span, growx, wrap");

		infoPanel.add(new JLabel(), "span, h 4!, wrap");

		moveLabel = SwingUtils.getLabelWithTooltip("Move", "Associated move for badge");
		infoPanel.add(moveLabel);
		infoPanel.add(moveBox, "grow");
		infoPanel.add(chooseMoveButton, "grow, wrap");

		sortValueLabel = SwingUtils.getLabelWithTooltip("Menu Order", "Used to sort badges in the pause menu. Higher is further down.");
		infoPanel.add(sortValueLabel);
		infoPanel.add(sortValueField, "w 132!");
		infoPanel.add(editSortValueButton, "grow, wrap");

		potencyALabel = SwingUtils.getLabel("Potency A", 12);
		infoPanel.add(potencyALabel);
		infoPanel.add(potencyAField, "w 132!, wrap");

		potencyBLabel = SwingUtils.getLabel("Potency B", 12);
		infoPanel.add(potencyBLabel);
		infoPanel.add(potencyBField, "w 132!, wrap");

		sellValueLabel = SwingUtils.getLabelWithTooltip("Sell Value", "Default coin value in shops and Refund.");
		infoPanel.add(sellValueLabel);
		infoPanel.add(sellValueField, "w 132!, wrap");

		JPanel embedPanel = new JPanel(new MigLayout("ins 8 16 8 16, fill, wrap"));
		embedPanel.add(infoPanel, "growx");
		embedPanel.add(new JLabel(), "growy, pushy");
		embedPanel.add(infoLabel, "growx");

		return embedPanel;
	}

	@Override
	protected void updateInfoPanel(ItemRecord item, boolean fromSet)
	{
		ImageIcon preview;
		String assetPreviewName = item.imageAssetName;

		ItemEntityRecord entityRec = editor.data.itemEntities.getElement(item.itemEntityName);
		String entityPreviewName = (entityRec == null) ? null : entityRec.previewImageName;

		HudElementRecord hudElemRec = editor.data.itemHudElements.getElement(item.hudElemName);
		String hudElemPreviewName = (hudElemRec == null) ? null : hudElemRec.previewImageName;

		String previewName = (item.autoGraphics) ? assetPreviewName : entityPreviewName;
		preview = editor.data.getPreviewImage(previewName);
		displayIconLabel.setIcon((preview != null) ? preview : IconResource.CROSS_24);

		preview = editor.data.getSmallPreviewImage(assetPreviewName);
		imageAssetPreview.setIcon((preview != null) ? preview : IconResource.CROSS_16);
		imageAssetBox.setForeground((preview != null) ? null : SwingUtils.getRedTextColor());

		preview = editor.data.getSmallPreviewImage(entityPreviewName);
		itemEntityPreview.setIcon((preview != null) ? preview : IconResource.CROSS_16);
		itemEntityBox.setForeground((preview != null) ? null : SwingUtils.getRedTextColor());

		preview = editor.data.getSmallPreviewImage(hudElemPreviewName);
		hudElementPreview.setIcon((preview != null) ? preview : IconResource.CROSS_16);
		hudElementBox.setForeground((preview != null) ? null : SwingUtils.getRedTextColor());

		MoveRecord move = editor.data.moves.getElement(item.moveName);
		moveBox.setForeground((move != null) ? null : SwingUtils.getRedTextColor());

		displayNameLabel.setText(item.displayName);
		nameField.setText(item.identifier);

		updateMessageField(nameMsgField, nameMsgPreview, item.msgName);
		updateMessageField(shortDescMsgField, shortDescMsgPreview, item.msgShortDesc);
		updateMessageField(fullDescMsgField, fullDescMsgPreview, item.msgFullDesc);

		typeFlagsLabel.setText(String.format("%04X", item.typeFlags));
		targetFlagsLabel.setText(String.format("%04X", item.targetFlags));

		autoGraphicsPanel.setVisible(item.autoGraphics);
		manualGraphicsPanel.setVisible(!item.autoGraphics);

		if (item.autoGraphics)
			rbAutoGraphics.setSelected(true);
		else
			rbManualGraphics.setSelected(true);

		moveBox.setSelectedItem(item.moveName);
		hudElementBox.setSelectedItem(item.hudElemName);
		itemEntityBox.setSelectedItem(item.itemEntityName);
		imageAssetBox.setSelectedItem(item.imageAssetName);

		sortValueField.setValue(MathUtil.clamp(item.sortValue, 0, 0xFFFF));
		potencyAField.setValue(MathUtil.clamp(item.potencyA, -128, 127));
		potencyBField.setValue(MathUtil.clamp(item.potencyB, -128, 127));
		sellValueField.setValue(MathUtil.clamp(item.sellValue, -1, 999));

		boolean isWeapon = (item.typeFlags & 0x2) != 0;
		boolean isKey = (item.typeFlags & 0x8) != 0;
		boolean isBadge = (item.typeFlags & 0x40) != 0;
		boolean isFood = (item.typeFlags & 0x80) != 0;

		sortValueLabel.setVisible(isBadge);
		sortValueField.setVisible(isBadge);
		editSortValueButton.setVisible(isBadge);

		moveLabel.setVisible(isBadge);
		moveBox.setVisible(isBadge);
		chooseMoveButton.setVisible(isBadge);

		potencyALabel.setVisible(isFood || isWeapon);
		potencyAField.setVisible(isFood || isWeapon);
		potencyALabel.setText(isFood ? "HP Gain" : "Power");

		potencyBLabel.setVisible(isFood);
		potencyBField.setVisible(isFood);
		potencyBLabel.setText("FP Gain");

		sellValueLabel.setVisible(!isKey);
		sellValueField.setVisible(!isKey);
	}

	@Override
	protected ListCellRenderer<ItemRecord> getCellRenderer()
	{
		return new ItemRecordListRenderer(editor.data);
	}
}
