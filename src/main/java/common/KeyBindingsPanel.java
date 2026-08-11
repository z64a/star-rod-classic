package common;

import java.awt.Component;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;

import net.miginfocom.swing.MigLayout;

public final class KeyBindingsPanel extends JPanel
{
	private final List<KeyInput> inputs = new ArrayList<>();
	private final KeyboardInputConfig keyConfig;
	private final Map<KeyInput, KeyBinding> bindings;
	private final BindingsTableModel tableModel;
	private final JTable table;

	public KeyBindingsPanel(KeyboardInputConfig keyConfig)
	{
		this.keyConfig = keyConfig;
		bindings = keyConfig.copyBindings();
		inputs.addAll(keyConfig.getUserBindableInputs());

		setLayout(new MigLayout("fill, wrap 4", "[grow][grow][grow][grow]", "[][grow][]"));

		add(new JLabel("Double-click an input or select it and choose Change."), "span, growx");

		tableModel = new BindingsTableModel();
		table = new JTable(tableModel);
		table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		table.setFillsViewportHeight(true);
		table.setDefaultRenderer(Object.class, new BindingsCellRenderer());
		table.getColumnModel().getColumn(0).setPreferredWidth(110);
		table.getColumnModel().getColumn(1).setPreferredWidth(220);
		table.getColumnModel().getColumn(2).setPreferredWidth(120);
		table.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e)
			{
				if (e.getClickCount() == 2)
					changeSelectedBinding();
			}
		});

		JScrollPane scrollPane = new JScrollPane(table);
		add(scrollPane, "span, grow, w 520!, h 420!");

		JButton changeButton = new JButton("Change");
		changeButton.addActionListener((e) -> changeSelectedBinding());
		add(changeButton, "growx");

		JButton clearButton = new JButton("Clear");
		clearButton.addActionListener((e) -> clearSelectedBinding());
		add(clearButton, "growx");

		JButton resetButton = new JButton("Reset Selected");
		resetButton.addActionListener((e) -> resetSelectedBinding());
		add(resetButton, "growx");

		JButton resetAllButton = new JButton("Reset All");
		resetAllButton.addActionListener((e) -> resetAllBindings());
		add(resetAllButton, "growx");
	}

	public void applyBindings()
	{
		keyConfig.setBindings(bindings);
	}

	private KeyInput getSelectedInput()
	{
		int row = table.getSelectedRow();
		if (row < 0)
			return null;
		return inputs.get(table.convertRowIndexToModel(row));
	}

	private void changeSelectedBinding()
	{
		KeyInput input = getSelectedInput();
		if (input == null)
			return;

		KeyCaptureField captureField = new KeyCaptureField();
		JPanel prompt = new JPanel(new MigLayout("fillx, wrap", "[grow]"));
		prompt.add(new JLabel("Press a key combination for " + input.getDisplayName() + "."));
		prompt.add(captureField, "growx");

		SwingUtilities.invokeLater(() -> captureField.requestFocusInWindow());
		int choice = JOptionPane.showConfirmDialog(this, prompt, "Change Key Binding",
			JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
		if (choice != JOptionPane.OK_OPTION || captureField.getBinding() == null)
			return;

		assignBinding(input, captureField.getBinding());
	}

	private void clearSelectedBinding()
	{
		KeyInput input = getSelectedInput();
		if (input == null)
			return;
		bindings.put(input, KeyBinding.NONE);
		tableModel.fireTableDataChanged();
	}

	private void resetSelectedBinding()
	{
		KeyInput input = getSelectedInput();
		if (input == null)
			return;
		assignBinding(input, keyConfig.getDefaultBinding(input));
	}

	private void resetAllBindings()
	{
		for (KeyInput input : inputs)
			bindings.put(input, keyConfig.getDefaultBinding(input));
		tableModel.fireTableDataChanged();
	}

	private void assignBinding(KeyInput input, KeyBinding binding)
	{
		KeyInput conflict = keyConfig.findConflict(bindings, input, binding);
		if (conflict != null) {
			int choice = JOptionPane.showConfirmDialog(this,
				binding.getDisplayText() + " is currently assigned to " + conflict.getDisplayName()
					+ ". Replace that binding?",
				"Key Binding Conflict", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
			if (choice != JOptionPane.YES_OPTION)
				return;
			bindings.put(conflict, KeyBinding.NONE);
		}

		bindings.put(input, binding);
		tableModel.fireTableDataChanged();
	}

	private class BindingsTableModel extends AbstractTableModel
	{
		private static final long serialVersionUID = 1L;

		@Override
		public int getRowCount()
		{
			return inputs.size();
		}

		@Override
		public int getColumnCount()
		{
			return 3;
		}

		@Override
		public String getColumnName(int column)
		{
			switch (column) {
				case 0:
					return "Category";
				case 1:
					return "Input";
				case 2:
					return "Shortcut";
				default:
					return "";
			}
		}

		@Override
		public Object getValueAt(int rowIndex, int columnIndex)
		{
			KeyInput input = inputs.get(rowIndex);
			switch (columnIndex) {
				case 0:
					return input.getCategory();
				case 1:
					return input.getDisplayName();
				case 2:
					return bindings.get(input).getDisplayText();
				default:
					return "";
			}
		}
	}

	private static class BindingsCellRenderer extends DefaultTableCellRenderer
	{
		private static final long serialVersionUID = 1L;

		@Override
		public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column)
		{
			super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
			setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 8));
			return this;
		}
	}

	private static class KeyCaptureField extends JTextField
	{
		private static final long serialVersionUID = 1L;
		private common.KeyBinding binding;

		private KeyCaptureField()
		{
			super("Press a key combination...");
			setEditable(false);
			setFocusTraversalKeysEnabled(false);
			addKeyListener(new KeyAdapter() {
				@Override
				public void keyPressed(KeyEvent e)
				{
					if (common.KeyBinding.isModifierKey(e.getKeyCode()))
						return;

					binding = new common.KeyBinding(e.getKeyCode(), e.getModifiersEx());
					setText(binding.getDisplayText());
					e.consume();
				}
			});
		}

		private common.KeyBinding getBinding()
		{
			return binding;
		}
	}
}
