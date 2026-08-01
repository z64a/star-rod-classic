package game.map.editor.ui.info.marker;

import app.SwingUtils;
import game.map.editor.MapEditor;
import game.map.marker.NpcComponent;
import game.shared.ProjectDatabase;
import game.string.PMString;
import net.miginfocom.swing.MigLayout;
import util.ui.*;

import javax.swing.*;

public class NpcSubpanel extends JPanel
{
	public static final FlagEditorPanel.Flag[] NPC_FLAGS = new FlagEditorPanel.Flag[] {
		new FlagEditorPanel.Flag(0x00000001, "Passive"),
		new FlagEditorPanel.Flag(0x00000002, "(unused)"),
		new FlagEditorPanel.Flag(0x00000004, "Do not kill"),
		new FlagEditorPanel.Flag(0x00000008, "Enable hit script"),
		new FlagEditorPanel.Flag(0x00000010, "Fled"),
		new FlagEditorPanel.Flag(0x00000020, "Disable AI movement and collision"),
		new FlagEditorPanel.Flag(0x00000040, "Projectile"),
		new FlagEditorPanel.Flag(0x00000080, "Don't update shadow Y"),
		new FlagEditorPanel.Flag(0x00000100, "Ignore world collision"),
		new FlagEditorPanel.Flag(0x00000200, "Ignore player collision"),
		new FlagEditorPanel.Flag(0x00000400, "Ignore entity collision"),
		new FlagEditorPanel.Flag(0x00000800, "Flying"),
		new FlagEditorPanel.Flag(0x00001000, "Gravity"),
		new FlagEditorPanel.Flag(0x00002000, "No shadow raycast"),
		new FlagEditorPanel.Flag(0x00004000, "Has no sprite"),
		new FlagEditorPanel.Flag(0x00008000, "Use inspect icon"),
		new FlagEditorPanel.Flag(0x00010000, "Raycast to interact"),
		new FlagEditorPanel.Flag(0x00020000, "Use player sprite"),
		new FlagEditorPanel.Flag(0x00040000, "No delay after free"),
		new FlagEditorPanel.Flag(0x00080000, "Don't suspend scripts"),
		new FlagEditorPanel.Flag(0x00100000, "Skip battle"),
		new FlagEditorPanel.Flag(0x00200000, "Active while offscreen"),
		new FlagEditorPanel.Flag(0x00400000, "Don't auto-face player"),
		new FlagEditorPanel.Flag(0x00800000, "No Drops"),
		new FlagEditorPanel.Flag(0x01000000, "Ignore touch"),
		new FlagEditorPanel.Flag(0x02000000, "Ignore Jump"),
		new FlagEditorPanel.Flag(0x04000000, "Ignore Hammer"),
		new FlagEditorPanel.Flag(0x08000000, "Can't interact"),
		new FlagEditorPanel.Flag(0x10000000, "Ignore Partner"),
		new FlagEditorPanel.Flag(0x20000000, "Ignore spin"),
		new FlagEditorPanel.Flag(0x40000000, "Begin with chasing"),
		new FlagEditorPanel.Flag(0x80000000, "Suspended"),
	};

	private final MarkerInfoPanel parent;

	private final JCheckBox genDefaultGroup;
	private final HexTextField flagsField;
	private JButton editFlagsButton;

	private final StringField tattleStringField;
	private final JButton chooseTattleStringButton;

	private final IntTextField radiusField;
	private final IntTextField heightField;
	private final IntTextField levelField;

	private final JCheckBox hasInit;
	private final JCheckBox hasInteract;
	private final JCheckBox hasIdle;
	private final JCheckBox hasAI;
	private final JCheckBox hasHit;
	private final JCheckBox hasDefeat;
	private final JCheckBox hasAux;

	public NpcSubpanel(MarkerInfoPanel parent)
	{
		this.parent = parent;

		setLayout(new MigLayout("fillx, hidemode 3, ins 0, wrap"));

		genDefaultGroup = new JCheckBox(" Add to default NPC Group");
		genDefaultGroup.addActionListener((e) -> MapEditor.execute(
				parent.getData().npcComponent.genDefaultGroup.mutator(genDefaultGroup.isSelected())));

		// NPC flags
		flagsField = new HexTextField(8, (v) -> {
			if (parent.ignoreEvents() || parent.getData() == null)
				return;
			MapEditor.execute(parent.getData().npcComponent.flags.mutator(v));
		});
		flagsField.setHorizontalAlignment(SwingConstants.LEFT);

		editFlagsButton = new JButton("Edit");
		editFlagsButton.addActionListener(e -> {
			NpcComponent npcComponent = parent.getData().npcComponent;

			FlagEditorPanel flagPanel = new FlagEditorPanel(8, 2, NPC_FLAGS);
			flagPanel.setValue(npcComponent.flags.get());

			int choice = SwingUtils.getConfirmDialog()
					.setTitle("Set NPC Flags")
					.setMessage(flagPanel)
					.setOptionsType(JOptionPane.OK_CANCEL_OPTION)
					.choose();

			if (choice == JOptionPane.YES_OPTION) {
				int newValue = flagPanel.getValue();

				if (npcComponent.flags.get() != newValue) {
					MapEditor.execute(npcComponent.flags.mutator(newValue));
				}
			}
		});

		// tattle string
		tattleStringField = new StringField(JTextField.LEADING, (s) -> {
			NpcComponent npcComponent = parent.getData().npcComponent;

			if (!s.equals(npcComponent.tattleString.get())) {
				MapEditor.execute(npcComponent.tattleString.mutator(s));
			}
		});
		chooseTattleStringButton = new JButton("Choose");
		chooseTattleStringButton.addActionListener((e) -> {
			NpcComponent npcComponent = parent.getData().npcComponent;

			PMString msg = ProjectDatabase.gameStrings.chooseMessage("Choose Short Description");
			if (msg != null) {
				String s = msg.getIdentifier();
				if (!s.equals(npcComponent.tattleString.get())) {
					MapEditor.execute(npcComponent.tattleString.mutator(s));
				}
			}
		});

		// NPC settings
		radiusField = new IntTextField((v) -> {
			if (parent.ignoreEvents() || parent.getData() == null)
				return;
			MapEditor.execute(parent.getData().npcComponent.radius.mutator(v));
		});
		heightField = new IntTextField((v) -> {
			if (parent.ignoreEvents() || parent.getData() == null)
				return;
			MapEditor.execute(parent.getData().npcComponent.height.mutator(v));
		});
		levelField = new IntTextField((v) -> {
			if (parent.ignoreEvents() || parent.getData() == null)
				return;
			MapEditor.execute(parent.getData().npcComponent.level.mutator(v));
		});

		// callbacks
		hasInit = new JCheckBox(" Always has Init callback");
		hasInit.addActionListener((e) -> MapEditor.execute(
				parent.getData().npcComponent.hasInit.mutator(hasInit.isSelected())));

		hasInteract = new JCheckBox(" Has Interact callback");
		hasInteract.addActionListener((e) -> MapEditor.execute(
				parent.getData().npcComponent.hasInteract.mutator(hasInteract.isSelected())));
		hasIdle = new JCheckBox(" Has Idle callback");
		hasIdle.addActionListener((e) -> MapEditor.execute(
				parent.getData().npcComponent.hasIdle.mutator(hasIdle.isSelected())));
		hasAI = new JCheckBox(" Has AI callback");
		hasAI.addActionListener((e) -> MapEditor.execute(
				parent.getData().npcComponent.hasAI.mutator(hasAI.isSelected())));
		hasHit = new JCheckBox(" Has Hit callback");
		hasHit.addActionListener((e) -> MapEditor.execute(
				parent.getData().npcComponent.hasHit.mutator(hasHit.isSelected())));
		hasDefeat = new JCheckBox(" Has Defeat callback");
		hasDefeat.addActionListener((e) -> MapEditor.execute(
				parent.getData().npcComponent.hasDefeat.mutator(hasDefeat.isSelected())));
		hasAux = new JCheckBox(" Has Aux callback");
		hasAux.addActionListener((e) -> MapEditor.execute(
				parent.getData().npcComponent.hasAux.mutator(hasAux.isSelected())));

		add(new JLabel("NPC Flags"), "split 3, w 28%!");
		add(flagsField, "w 40%!");
		add(editFlagsButton);

		add(new JLabel("Tattle String"), "split 3, w 28%!");
		add(tattleStringField, "w 40%!");
		add(chooseTattleStringButton);

		add(genDefaultGroup);

		add(new JLabel("NPC Settings"), "gaptop 8");
		add(new JLabel("Height"), "split 2, w 28%!");
		add(heightField, "w 40%!");
		add(new JLabel("Radius"), "split 2, w 28%!");
		add(radiusField, "w 40%!");
		add(new JLabel("Level"), "split 2, w 28%!");
		add(levelField, "w 40%!");

		add(hasInit, "gaptop 12");
		add(hasInteract);
		add(hasIdle);
		add(hasAI);
		add(hasHit);
		add(hasDefeat);
		add(hasAux);
	}

	public void onUpdateFields()
	{
		NpcComponent data = parent.getData().npcComponent;

		genDefaultGroup.setSelected(data.genDefaultGroup.get());
		flagsField.setValue(data.flags.get());

		tattleStringField.setText(data.tattleString.get());

		radiusField.setValue(data.radius.get());
		heightField.setValue(data.height.get());
		levelField.setValue(data.level.get());

		hasInit.setSelected(data.hasInit.get());
		hasInteract.setSelected(data.hasInteract.get());
		hasIdle.setSelected(data.hasIdle.get());
		hasAI.setSelected(data.hasAI.get());
		hasHit.setSelected(data.hasHit.get());
		hasDefeat.setSelected(data.hasDefeat.get());
		hasAux.setSelected(data.hasAux.get());
	}

}
