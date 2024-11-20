package game.string.editor;

import java.awt.Component;
import java.awt.Point;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import game.string.PMString;
import net.miginfocom.swing.MigLayout;
import shared.SwingUtils;
import util.ui.FilteredListModel;
import util.ui.HexTextField;
import util.ui.StandardInputField;

public class StringListPanel extends JPanel
{
	private JTextField filterField;
	private JList<PMString> list;

	private DefaultListModel<PMString> listModel;
	private FilteredListModel<PMString> filteredListModel;

	private boolean ignoreChanges = false;

	public StringListPanel(StringEditor editor)
	{
		super(new MigLayout("fill, ins 0, wrap"));

		list = new JList<>() {
			// clicking blank space returns -1 instead of the last list item
			@Override
			public int locationToIndex(Point location)
			{
				int index = super.locationToIndex(location);
				if (index != -1 && !getCellBounds(index, index).contains(location)) {
					return -1;
				}
				else {
					return index;
				}
			}
		};

		list.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e)
			{
				int index = list.locationToIndex(e.getPoint());
				if (index == -1)
					list.clearSelection();
			}
		});

		list.addListSelectionListener((e) -> {
			if (ignoreChanges || e.getValueIsAdjusting())
				return;

			PMString selected = list.getSelectedValue();
			editor.invokeLater(() -> {
				editor.setString(selected);
			});
		});

		list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		list.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
		list.setCellRenderer(new StringCellRenderer());

		listModel = new DefaultListModel<>();
		filteredListModel = new FilteredListModel<>(listModel);
		list.setModel(filteredListModel);

		filterField = new JTextField(20);
		filterField.setMargin(SwingUtils.TEXTBOX_INSETS);
		filterField.getDocument().addDocumentListener(new DocumentListener() {
			@Override
			public void changedUpdate(DocumentEvent e)
			{
				updateListFilter();
			}

			@Override
			public void insertUpdate(DocumentEvent e)
			{
				updateListFilter();
			}

			@Override
			public void removeUpdate(DocumentEvent e)
			{
				updateListFilter();
			}
		});
		SwingUtils.addBorderPadding(filterField);

		stringInfoPanel = getStringInfoPanel();

		JScrollPane listScrollPane = new JScrollPane(list);
		listScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		listScrollPane.setWheelScrollingEnabled(true);

		add(SwingUtils.getLabel("Filter:", 12), "w 40!, split 2");
		add(filterField, "growx, gaptop 12");
		add(listScrollPane, "grow, push, gaptop 8, gapbottom 8");
		add(stringInfoPanel, "growx");

		setString(null);
	}

	public void setStrings(List<PMString> strings)
	{
		ignoreChanges = true;
		filteredListModel.setIgnoreChanges(true);

		listModel.clear();
		for (PMString str : strings)
			listModel.addElement(str);

		ignoreChanges = false;
		filteredListModel.setIgnoreChanges(false);

		updateListFilter();
	}

	private void updateListFilter()
	{
		filteredListModel.setFilter(element -> {
			PMString str = (PMString) element;
			String needle;

			if (str.hasName())
				needle = str.name.replaceAll("_", " ");
			else
				needle = str.getIDName();

			needle += " " + str.toString();

			return (needle.toUpperCase().contains(filterField.getText().toUpperCase()));
		});
	}

	private JPanel stringInfoPanel;
	private JLabel stringSourceLabel;
	private JTextField stringNameField;
	private HexTextField stringSectionField;
	private HexTextField stringIndexField;
	private JCheckBox cbAutoAssign;

	private JPanel getStringInfoPanel()
	{
		JPanel stringPanel = new JPanel(new MigLayout("fill, ins 0"));

		stringSourceLabel = SwingUtils.getLabel("Source", 14);
		SwingUtils.addBorderPadding(stringSourceLabel);

		stringNameField = new StandardInputField((text) -> {
			if (ignoreChanges)
				return;
			PMString selected = list.getSelectedValue();
			if (!text.isEmpty() && selected != null) {
				selected.name = text;
				selected.setModified();
				list.repaint();
			}
		});

		stringSectionField = new HexTextField(2, (value) -> {
			PMString selected = list.getSelectedValue();
			if (value != null && selected != null) {
				selected.section = value;
				selected.setModified();
				list.repaint();
			}
		});
		SwingUtils.addBorderPadding(stringSectionField);

		stringIndexField = new HexTextField(4, (value) -> {
			PMString selected = list.getSelectedValue();
			if (value != null && selected != null) {
				selected.index = (short) (int) value;
				selected.setModified();
				list.repaint();
			}
		});
		SwingUtils.addBorderPadding(stringIndexField);

		cbAutoAssign = new JCheckBox("Auto-assign");
		cbAutoAssign.addActionListener((e) -> {
			PMString selected = list.getSelectedValue();
			if (selected != null && !ignoreChanges) {
				selected.autoAssign = cbAutoAssign.isSelected();
				selected.setModified();
				stringIndexField.setEnabled(!selected.autoAssign);
				list.repaint();
			}
		});

		stringPanel.add(SwingUtils.getLabel("Source", 14), "w 15%!");
		stringPanel.add(stringSourceLabel, "growx, pushx, wrap");

		stringPanel.add(SwingUtils.getLabel("Name", 14), "w 15%!");
		stringPanel.add(stringNameField, "growx, pushx, wrap");
		stringPanel.add(SwingUtils.getLabel("ID", 14), "w 15%");
		stringPanel.add(stringSectionField, "split 3, growx 1");
		stringPanel.add(stringIndexField, "growx 1");
		stringPanel.add(cbAutoAssign, "growx 2");

		return stringPanel;
	}

	public void setString(PMString string)
	{
		ignoreChanges = true;
		if (string != null) {
			stringSourceLabel.setText(string.source.file.getName());
			stringNameField.setText(string.name);

			SwingUtils.enableComponents(stringInfoPanel, true);

			if (string.indexed) {
				stringSectionField.setValue(string.section);
				stringIndexField.setValue(string.index);
				cbAutoAssign.setSelected(string.autoAssign);

				stringSectionField.setEnabled(true);
				stringIndexField.setEnabled(!string.autoAssign);
				cbAutoAssign.setEnabled(true);
			}
			else {
				// embedded
				stringSectionField.setText("");
				stringIndexField.setText("");
				cbAutoAssign.setSelected(false);

				stringSectionField.setEnabled(false);
				stringIndexField.setEnabled(false);
				cbAutoAssign.setEnabled(false);
			}
		}
		else {
			stringSourceLabel.setText("");
			stringNameField.setText("");
			stringSectionField.setText("");
			stringIndexField.setText("");
			cbAutoAssign.setEnabled(false);
			SwingUtils.enableComponents(stringInfoPanel, false);
		}
		ignoreChanges = false;
	}

	private static class StringCellRenderer extends JPanel implements ListCellRenderer<PMString>
	{
		private JLabel contentLabel;
		private JLabel idLabel;

		public StringCellRenderer()
		{
			idLabel = new JLabel();
			contentLabel = new JLabel();

			contentLabel.setForeground(SwingUtils.getGreyTextColor());

			setLayout(new MigLayout("ins 0, fillx"));
			add(idLabel, "gapleft 8, w 15%");
			add(contentLabel, "growx, pushx, gapright push");

			setOpaque(true);
		}

		@Override
		public Component getListCellRendererComponent(
			JList<? extends PMString> list,
			PMString str,
			int index,
			boolean isSelected,
			boolean cellHasFocus)
		{
			if (isSelected) {
				setBackground(list.getSelectionBackground());
				setForeground(list.getSelectionForeground());
			}
			else {
				setBackground(list.getBackground());
				setForeground(list.getForeground());
			}

			setBorder(BorderFactory.createEmptyBorder(4, 0, 4, 0));
			if (str != null) {
				boolean error = str.hasError();

				idLabel.setForeground(error ? SwingUtils.getRedTextColor() : null);
				contentLabel.setForeground(error ? SwingUtils.getRedTextColor() : null);

				setToolTipText(error ? str.getErrorMessage() : null);

				if (str.indexed)
					idLabel.setText(str.getIDName());
				else
					idLabel.setText("<html><i>embed</i></html>");

				String preview;
				if (str.hasName())
					preview = str.name;
				else
					preview = str.toString().trim();

				if (preview.length() > 40) {
					int lastSpace = preview.lastIndexOf(" ", 32);
					if (lastSpace < 0)
						preview = preview.substring(0, 40) + " (...)";
					else
						preview = preview.substring(0, lastSpace) + " (...)";
				}

				if (str.isModified())
					contentLabel.setText("* " + preview);
				else
					contentLabel.setText(preview);
			}
			else {
				idLabel.setText("null");
				contentLabel.setText("error!");
			}

			return this;
		}
	}
}
