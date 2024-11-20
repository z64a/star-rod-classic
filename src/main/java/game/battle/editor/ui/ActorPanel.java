package game.battle.editor.ui;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;

import common.InfoPanel;
import game.battle.editor.Actor;
import game.battle.editor.BattleEditor;
import net.miginfocom.swing.MigLayout;
import shared.SwingUtils;
import util.ui.FlagEditorPanel;
import util.ui.HexTextField;
import util.ui.IntSpinner;

public class ActorPanel extends InfoPanel<Actor>
{
	/*
	//TODO
	public String name = "";
	public String scriptName = "";
	
	public int actorType;
	
	... parts ...
	public IterableListModel<ActorPart> parts = new IterableListModel<>();
	 */

	private JLabel flagsLabel;
	private HexTextField typeField;
	private IntSpinner levelField; // max vanilla = 100
	private IntSpinner maxHPField;
	private IntSpinner coinsField; // max vanilla = 5

	private IntSpinner sizeXField;
	private IntSpinner sizeYField;

	private IntSpinner hpBarXField;
	private IntSpinner hpBarYField;

	private IntSpinner statusCountXField;
	private IntSpinner statusCountYField;

	private IntSpinner statusIconXField;
	private IntSpinner statusIconYField;

	private IntSpinner escapeField; // vanilla: (0-100)
	private IntSpinner airliftField; // vanilla: (0-100)
	private IntSpinner hurricaneField; // vanilla: (0-100)
	private IntSpinner itemField; // vanilla: (0-100)
	private IntSpinner upAndAwayField; // vanilla: (0-100)
	private IntSpinner powerBounceField; // vanilla: (0-100)
	private IntSpinner weightField; // vanilla: (0-4)

	public ActorPanel(BattleEditor editor)
	{
		super(editor, false);

		levelField = new IntSpinner(0, 255, (v) -> {
			if (getData() != null)
				getData().level.set(v);
		});
		SwingUtils.addBorderPadding(levelField);

		maxHPField = new IntSpinner((v) -> {
			if (getData() != null)
				getData().maxHP.set(v);
		});
		SwingUtils.addBorderPadding(maxHPField);

		coinsField = new IntSpinner(0, 255, (v) -> {
			if (getData() != null)
				getData().coins.set(v);
		});
		SwingUtils.addBorderPadding(coinsField);

		flagsLabel = SwingUtils.getLabel("00000000", 12);
		JButton flagsButton = new JButton("Edit");
		flagsButton.addActionListener((e) -> {
			if (getData() != null) {
				FlagEditorPanel flagPanel = new FlagEditorPanel(8, Actor.FLAGS);
				flagPanel.setValue(getData().flags.get());

				int choice = SwingUtils.getConfirmDialog()
					.setTitle("Set Actor Flags")
					.setMessage(flagPanel)
					.setOptionsType(JOptionPane.OK_CANCEL_OPTION)
					.choose();

				if (choice == JOptionPane.YES_OPTION) {
					getData().flags.set(flagPanel.getValue());
					flagsLabel.setText(String.format("%08X", getData().flags.get()));
				}
			}
		});
		SwingUtils.addBorderPadding(flagsButton);

		typeField = new HexTextField(2, (newValue) -> {
			if (!ignoreEvents() && getData() != null)
				editor.execute(getData().actorType.mutator(newValue));
		});

		escapeField = new IntSpinner(0, 100, (v) -> {
			if (getData() != null)
				getData().escape.set(v);
		});
		SwingUtils.addBorderPadding(escapeField);

		airliftField = new IntSpinner(0, 100, (v) -> {
			if (getData() != null)
				getData().airlift.set(v);
		});
		SwingUtils.addBorderPadding(airliftField);

		hurricaneField = new IntSpinner(0, 100, (v) -> {
			if (getData() != null)
				getData().hurricane.set(v);
		});
		SwingUtils.addBorderPadding(hurricaneField);

		itemField = new IntSpinner(0, 100, (v) -> {
			if (getData() != null)
				getData().item.set(v);
		});
		SwingUtils.addBorderPadding(itemField);

		upAndAwayField = new IntSpinner(0, 100, (v) -> {
			if (getData() != null)
				getData().upAndAway.set(v);
		});
		SwingUtils.addBorderPadding(upAndAwayField);

		powerBounceField = new IntSpinner(0, 100, (v) -> {
			if (getData() != null)
				getData().powerBounce.set(v);
		});
		SwingUtils.addBorderPadding(powerBounceField);

		weightField = new IntSpinner(0, 4, (v) -> {
			if (getData() != null)
				getData().weight.set(v);
		});
		SwingUtils.addBorderPadding(weightField);

		sizeXField = new IntSpinner(0, 255, (v) -> {
			if (getData() != null)
				getData().size.set(0, v);
		});
		SwingUtils.addBorderPadding(sizeXField);

		sizeYField = new IntSpinner(0, 255, (v) -> {
			if (getData() != null)
				getData().size.set(1, v);
		});
		SwingUtils.addBorderPadding(sizeYField);

		hpBarXField = new IntSpinner((v) -> {
			if (getData() != null)
				getData().healthBarOffset.set(0, v);
		});
		SwingUtils.addBorderPadding(hpBarXField);

		hpBarYField = new IntSpinner((v) -> {
			if (getData() != null)
				getData().healthBarOffset.set(1, v);
		});
		SwingUtils.addBorderPadding(hpBarYField);

		statusCountXField = new IntSpinner((v) -> {
			if (getData() != null)
				getData().statusCounterOffset.set(0, v);
		});
		SwingUtils.addBorderPadding(statusCountXField);

		statusCountYField = new IntSpinner(0, 255, (v) -> {
			if (getData() != null)
				getData().statusCounterOffset.set(1, v);
		});
		SwingUtils.addBorderPadding(statusCountYField);

		statusIconXField = new IntSpinner(0, 255, (v) -> {
			if (getData() != null)
				getData().statusIconOffset.set(0, v);
		});
		SwingUtils.addBorderPadding(statusIconXField);

		statusIconYField = new IntSpinner(0, 255, (v) -> {
			if (getData() != null)
				getData().statusIconOffset.set(1, v);
		});
		SwingUtils.addBorderPadding(statusIconYField);

		setLayout(new MigLayout("fill, wrap 3, hidemode 3", "[90!]5[90!]5[90!]5[90!][grow]"));

		//	JLabel nameLabel = SwingUtils.getLabel("Actor Stats", 18);

		//	add(nameLabel, "span, h 32!, wrap");
		add(SwingUtils.getLabel("Actor Stats", 18), "span, h 32!, wrap");

		add(SwingUtils.getLabel("Level", 12));
		add(levelField, "growx, wrap");

		add(SwingUtils.getLabel("Max HP", 12));
		add(maxHPField, "growx, wrap");

		add(SwingUtils.getLabel("Size", 12));
		add(sizeXField, "growx");
		add(sizeYField, "growx, wrap");

		add(SwingUtils.getLabel("Flags", 12));
		add(flagsLabel, "growx");
		add(flagsButton, "growx, wrap");

		add(SwingUtils.getLabel("Coin Reward", 12));
		add(coinsField, "growx, wrap");

		add(SwingUtils.getLabel("Move Effectiveness", 18), "span, h 32!, wrap");

		add(SwingUtils.getLabel("Escape", 12));
		add(escapeField, "growx, wrap");
		add(SwingUtils.getLabel("Airlift", 12));
		add(airliftField, "growx, wrap");
		add(SwingUtils.getLabelWithTooltip("Hurricane *", "Also used for Boo's spook"));
		add(hurricaneField, "growx, wrap");
		add(SwingUtils.getLabel("Items", 12));
		add(itemField, "growx, wrap");
		add(SwingUtils.getLabel("Up & Away", 12));
		add(upAndAwayField, "growx, wrap");
		add(SwingUtils.getLabel("Power Bounce", 12));
		add(powerBounceField, "growx, wrap");

		add(SwingUtils.getLabelWithTooltip("Spin Smash *", "Weight: hammer level (0-4) required to launch enemy"));
		add(weightField, "growx, wrap");

		add(SwingUtils.getLabel("Hud Element Offsets", 18), "span, h 32!, wrap");

		add(SwingUtils.getLabel("Health Bar", 12));
		add(hpBarXField, "growx");
		add(hpBarYField, "growx, wrap");

		add(SwingUtils.getLabel("Status Counter", 12));
		add(statusCountXField, "growx");
		add(statusCountYField, "growx, wrap");

		add(SwingUtils.getLabel("Status Icon", 12));
		add(statusIconXField, "growx");
		add(statusIconYField, "growx, wrap");

		add(new JPanel(), "span, grow, pushy");
	}

	@Override
	public void updateFields(Actor actor, String tag)
	{
		this.setVisible(actor != null);

		if (actor != null && getData() == actor) {
			levelField.setValue(actor.level.get());
			maxHPField.setValue(actor.maxHP.get());
			coinsField.setValue(actor.coins.get());
			flagsLabel.setText(String.format("%08X", actor.flags.get()));

			escapeField.setValue(actor.escape.get());
			airliftField.setValue(actor.airlift.get());
			hurricaneField.setValue(actor.hurricane.get());
			itemField.setValue(actor.item.get());
			powerBounceField.setValue(actor.powerBounce.get());
			upAndAwayField.setValue(actor.upAndAway.get());
			weightField.setValue(actor.weight.get());

			sizeXField.setValue(actor.size.get(0));
			sizeYField.setValue(actor.size.get(1));

			hpBarXField.setValue(actor.healthBarOffset.get(0));
			hpBarYField.setValue(actor.healthBarOffset.get(1));

			statusCountXField.setValue(actor.statusCounterOffset.get(0));
			statusCountYField.setValue(actor.statusCounterOffset.get(1));

			statusIconXField.setValue(actor.statusIconOffset.get(0));
			statusIconYField.setValue(actor.statusIconOffset.get(1));
		}
	}
}
