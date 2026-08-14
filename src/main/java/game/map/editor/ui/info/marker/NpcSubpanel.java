package game.map.editor.ui.info.marker;

import static game.map.marker.NpcComponent.*;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;

import app.SwingUtils;
import game.globals.editor.ListSelectorDialog;
import game.globals.editor.renderers.MessageCellRenderer;
import game.map.editor.MapEditor;
import game.map.marker.NpcComponent;
import game.shared.ProjectDatabase;
import game.string.PMString;
import net.miginfocom.swing.MigLayout;
import util.ui.FlagEditorPanel;
import util.ui.FlagEditorPanel.Flag;
import util.ui.HexTextField;
import util.ui.IntTextField;
import util.ui.StringField;

public class NpcSubpanel extends JPanel
{
	private static final Flag[] ENEMY_FLAGS = new Flag[] {
			new Flag(0x00000001, "Passive", "Collision does not trigger a battle."),
			new Flag(0x00000002, "Unused"),
			new Flag(0x00000004, "Do not kill", "Keep the NPC after it is defeated in battle."),
			new Flag(0x00000008, "Enable hit script"),
			new Flag(0x00000010, "Fled"),
			new Flag(0x00000020, "Disable AI", "Disable movement AI and collision."),
			new Flag(0x00000040, "Projectile"),
			new Flag(0x00000080, "Do not update shadow Y"),
			new Flag(0x00000100, "Ignore world collision"),
			new Flag(0x00000200, "Ignore player collision"),
			new Flag(0x00000400, "Ignore entity collision"),
			new Flag(0x00000800, "Flying"),
			new Flag(0x00001000, "Gravity"),
			new Flag(0x00002000, "No shadow raycast"),
			new Flag(0x00004000, "Has no sprite"),
			new Flag(0x00008000, "Use inspect icon"),
			new Flag(0x00010000, "Raycast to interact"),
			new Flag(0x00020000, "Use player sprite"),
			new Flag(0x00040000, "No delay after flee"),
			new Flag(0x00080000, "Do not suspend scripts"),
			new Flag(0x00100000, "Skip battle"),
			new Flag(0x00200000, "Active while offscreen"),
			new Flag(0x00400000, "Do not auto-face player"),
			new Flag(0x00800000, "No drops"),
			new Flag(0x01000000, "Ignore touch"),
			new Flag(0x02000000, "Ignore jump"),
			new Flag(0x04000000, "Ignore hammer"),
			new Flag(0x08000000, "Cannot interact"),
			new Flag(0x10000000, "Ignore partner"),
			new Flag(0x20000000, "Ignore spin"),
			new Flag(0x40000000, "Begin chasing"),
			new Flag(0x80000000, "Suspended"),
	};

	private static final Flag[] ACTION_FLAGS = new Flag[] {
			new Flag(0x01, "Jump when player seen"),
			new Flag(0x02, "No first strike"),
			new Flag(0x04, "Chase requires path"),
			new Flag(0x08, "No spin reaction"),
			new Flag(0x10, "Look around while idle"),
			new Flag(0x20, "Mute while offscreen"),
	};

	private final MarkerInfoPanel parent;

	private final JCheckBox generateCheckbox;
	private final HexTextField enemyFlagsField;
	private final JLabel tattleMessageLabel;
	private final StringField tattleMessageField;
	private final IntTextField heightField;
	private final IntTextField radiusField;
	private final IntTextField levelField;
	private final HexTextField actionFlagsField;

	private final JCheckBox initCallback;
	private final JCheckBox interactCallback;
	private final JCheckBox idleCallback;
	private final JCheckBox aiCallback;
	private final JCheckBox hitCallback;
	private final JCheckBox defeatCallback;
	private final JCheckBox auxCallback;

	public NpcSubpanel(MarkerInfoPanel parent)
	{
		this.parent = parent;
		setLayout(new MigLayout("fillx, hidemode 3, ins n 16 n 16, wrap"));

		generateCheckbox = new JCheckBox(" Generate in default NPC group");
		generateCheckbox.addActionListener((e) -> {
			if (parent.ignoreEvents() || parent.getData() == null)
				return;
			MapEditor.execute(parent.getData().npcComponent.generate.mutator(generateCheckbox.isSelected()));
		});

		enemyFlagsField = new HexTextField(8, (value) -> {
			if (parent.ignoreEvents() || parent.getData() == null)
				return;
			MapEditor.execute(parent.getData().npcComponent.enemyFlags.mutator(value));
		});
		enemyFlagsField.setHorizontalAlignment(JTextField.CENTER);

		JButton editEnemyFlagsButton = new JButton("Edit");
		editEnemyFlagsButton.addActionListener((e) -> editEnemyFlags());

		tattleMessageLabel = new JLabel("Tattle Message");
		tattleMessageField = new StringField(JTextField.LEADING, (value) -> {
			if (parent.ignoreEvents() || parent.getData() == null)
				return;

			NpcComponent npc = parent.getData().npcComponent;
			if (!value.equals(npc.tattleMessage.get()))
				MapEditor.execute(npc.tattleMessage.mutator(value));
		});

		JButton chooseTattleButton = new JButton("Choose");
		chooseTattleButton.addActionListener((e) -> chooseTattleMessage());

		heightField = new IntTextField((value) -> {
			if (parent.ignoreEvents() || parent.getData() == null)
				return;
			MapEditor.execute(parent.getData().npcComponent.height.mutator(value));
		});
		radiusField = new IntTextField((value) -> {
			if (parent.ignoreEvents() || parent.getData() == null)
				return;
			MapEditor.execute(parent.getData().npcComponent.radius.mutator(value));
		});
		levelField = new IntTextField((value) -> {
			if (parent.ignoreEvents() || parent.getData() == null)
				return;
			MapEditor.execute(parent.getData().npcComponent.level.mutator(value));
		});

		actionFlagsField = new HexTextField(4, (value) -> {
			if (parent.ignoreEvents() || parent.getData() == null)
				return;
			MapEditor.execute(parent.getData().npcComponent.actionFlags.mutator(value));
		});
		actionFlagsField.setHorizontalAlignment(JTextField.CENTER);

		JButton editActionFlagsButton = new JButton("Edit");
		editActionFlagsButton.addActionListener((e) -> editActionFlags());

		initCallback = makeCallbackCheckbox("Init", CALLBACK_INIT);
		interactCallback = makeCallbackCheckbox("Interact", CALLBACK_INTERACT);
		idleCallback = makeCallbackCheckbox("Idle", CALLBACK_IDLE);
		aiCallback = makeCallbackCheckbox("AI", CALLBACK_AI);
		hitCallback = makeCallbackCheckbox("Hit", CALLBACK_HIT);
		defeatCallback = makeCallbackCheckbox("Defeat", CALLBACK_DEFEAT);
		auxCallback = makeCallbackCheckbox("Aux", CALLBACK_AUX);

		add(generateCheckbox);

		add(new JLabel("Enemy Flags"), "split 3, w 28%!");
		add(enemyFlagsField, "w 40%!");
		add(editEnemyFlagsButton);

		add(tattleMessageLabel, "split 3, w 28%!");
		add(tattleMessageField, "w 40%!");
		add(chooseTattleButton);

		add(SwingUtils.getLabel("NPC Settings", 12), "gaptop 8");
		add(new JLabel("Height"), "split 2, w 28%!");
		add(heightField, "w 40%!");
		add(new JLabel("Radius"), "split 2, w 28%!");
		add(radiusField, "w 40%!");
		add(new JLabel("Level"), "split 2, w 28%!");
		add(levelField, "w 40%!");
		add(new JLabel("Action Flags"), "split 3, w 28%!");
		add(actionFlagsField, "w 40%!");
		add(editActionFlagsButton);

		add(SwingUtils.getLabel("Generate Script Callbacks", 12), "gaptop 8");
		JPanel callbackPanel = new JPanel(new MigLayout("fillx, ins 0", "[grow][grow]"));
		callbackPanel.add(initCallback, "growx");
		callbackPanel.add(interactCallback, "growx, wrap");
		callbackPanel.add(idleCallback, "growx");
		callbackPanel.add(aiCallback, "growx, wrap");
		callbackPanel.add(hitCallback, "growx");
		callbackPanel.add(defeatCallback, "growx, wrap");
		callbackPanel.add(auxCallback, "growx");
		add(callbackPanel, "growx");
	}

	private JCheckBox makeCallbackCheckbox(String name, int callback)
	{
		JCheckBox checkbox = new JCheckBox(" " + name);
		checkbox.addActionListener((e) -> {
			if (parent.ignoreEvents() || parent.getData() == null)
				return;

			NpcComponent npc = parent.getData().npcComponent;
			int flags = npc.callbackFlags.get();
			if (checkbox.isSelected())
				flags |= callback;
			else
				flags &= ~callback;
			MapEditor.execute(npc.callbackFlags.mutator(flags));
		});
		return checkbox;
	}

	private void editEnemyFlags()
	{
		if (parent.getData() == null)
			return;

		NpcComponent npc = parent.getData().npcComponent;
		FlagEditorPanel flagPanel = new FlagEditorPanel(8, 2, ENEMY_FLAGS);
		flagPanel.setValue(npc.enemyFlags.get());

		int choice = SwingUtils.getConfirmDialog()
			.setTitle("Set Enemy Flags")
			.setMessage(flagPanel)
			.setOptionsType(JOptionPane.OK_CANCEL_OPTION)
			.choose();
		if (choice == JOptionPane.OK_OPTION && flagPanel.getValue() != npc.enemyFlags.get())
			MapEditor.execute(npc.enemyFlags.mutator(flagPanel.getValue()));
	}

	private void editActionFlags()
	{
		if (parent.getData() == null)
			return;

		NpcComponent npc = parent.getData().npcComponent;
		FlagEditorPanel flagPanel = new FlagEditorPanel(4, 2, ACTION_FLAGS);
		flagPanel.setValue(npc.actionFlags.get());

		int choice = SwingUtils.getConfirmDialog()
			.setTitle("Set Enemy Action Flags")
			.setMessage(flagPanel)
			.setOptionsType(JOptionPane.OK_CANCEL_OPTION)
			.choose();
		if (choice == JOptionPane.OK_OPTION && flagPanel.getValue() != npc.actionFlags.get())
			MapEditor.execute(npc.actionFlags.mutator(flagPanel.getValue()));
	}

	private void chooseTattleMessage()
	{
		if (parent.getData() == null || ProjectDatabase.messages == null)
			return;

		ListSelectorDialog<PMString> chooser = new ListSelectorDialog<>(ProjectDatabase.messages.getMessages(), new MessageCellRenderer(48));
		SwingUtils.showModalDialog(chooser, "Choose NPC Tattle Message");
		if (!chooser.isResultAccepted())
			return;

		PMString message = chooser.getValue();
		if (message != null)
			MapEditor.execute(parent.getData().npcComponent.tattleMessage.mutator(message.getIdentifier()));
	}

	public void updateFields()
	{
		NpcComponent npc = parent.getData().npcComponent;
		generateCheckbox.setSelected(npc.generate.get());
		enemyFlagsField.setValue(npc.enemyFlags.get());
		tattleMessageField.setText(npc.tattleMessage.get());
		heightField.setValue(npc.height.get());
		radiusField.setValue(npc.radius.get());
		levelField.setValue(npc.level.get());
		actionFlagsField.setValue(npc.actionFlags.get());

		String tattle = npc.tattleMessage.get();
		boolean validTattle = tattle == null || tattle.isEmpty() || (ProjectDatabase.messages != null && ProjectDatabase.messages.getMessage(tattle) != null);
		tattleMessageField.setForeground(validTattle ? null : SwingUtils.getRedTextColor());
		tattleMessageLabel.setForeground(validTattle ? null : SwingUtils.getRedTextColor());

		initCallback.setSelected(npc.hasCallback(CALLBACK_INIT));
		interactCallback.setSelected(npc.hasCallback(CALLBACK_INTERACT));
		idleCallback.setSelected(npc.hasCallback(CALLBACK_IDLE));
		aiCallback.setSelected(npc.hasCallback(CALLBACK_AI));
		hitCallback.setSelected(npc.hasCallback(CALLBACK_HIT));
		defeatCallback.setSelected(npc.hasCallback(CALLBACK_DEFEAT));
		auxCallback.setSelected(npc.hasCallback(CALLBACK_AUX));
	}
}
