package util.ui;

import javax.swing.JCheckBox;
import javax.swing.JPanel;
import javax.swing.JTextField;

import app.SwingUtils;
import net.miginfocom.swing.MigLayout;

public class FlagEditorPanel extends JPanel
{
	private final HexTextField valueField;
	private final int[] flagBits;
	private final JCheckBox[] checkBoxes;
	private int changingIndex = -1;

	public static class Flag
	{
		public final int bits;
		public final String name;
		public final String desc;

		public Flag(int bits, String name)
		{
			this(bits, name, "");
		}

		public Flag(int bits, String name, String desc)
		{
			this.bits = bits;
			this.name = name;
			this.desc = desc;
		}
	}

	public FlagEditorPanel(int digits, Flag[] flags)
	{
		this.flagBits = new int[flags.length];

		valueField = new HexTextField(digits, true, (newValue) -> {
			setCheckboxes(newValue);
			repaint();
		});
		valueField.setHorizontalAlignment(JTextField.CENTER);
		SwingUtils.addBorderPadding(valueField);

		checkBoxes = new JCheckBox[flags.length];

		setLayout(new MigLayout("fillx, ins 8 0 8 8, wrap"));
		add(valueField, "w 80!, gapbottom 8");

		for (int i = 0; i < flags.length; i++) {
			flagBits[i] = flags[i].bits;

			final int index = i;
			checkBoxes[i] = new JCheckBox(" " + flags[i].name);

			if (flags[i].desc != null && !flags[i].desc.isEmpty())
				checkBoxes[i].setToolTipText(String.format("%0" + digits + "X - %s", flags[i].bits, flags[i].desc));
			else
				checkBoxes[i].setToolTipText(String.format("%0" + digits + "X", flags[i].bits));

			checkBoxes[i].addActionListener((e) -> {
				changingIndex = index;
				setBits(flagBits[index], checkBoxes[index].isSelected());
				changingIndex = -1;
			});

			add(checkBoxes[i], "growx, sg checkbox");
		}
	}

	private void setBits(int bits, boolean set)
	{
		int flags = valueField.getValue();

		if (set)
			flags |= bits;
		else
			flags &= ~bits;

		setValue(flags);
	}

	public void setValue(int value)
	{
		valueField.setValue(value);
		setCheckboxes(value);
		repaint();
	}

	private void setCheckboxes(int value)
	{
		for (int i = 0; i < checkBoxes.length; i++) {
			if (i == changingIndex)
				continue;

			if (checkBoxes[i].isSelected() && (value & flagBits[i]) == 0)
				checkBoxes[i].setSelected(false);

			if (!checkBoxes[i].isSelected() && (value & flagBits[i]) != 0)
				checkBoxes[i].setSelected(true);
		}
	}

	public int getValue()
	{
		return valueField.getValue();
	}
}
