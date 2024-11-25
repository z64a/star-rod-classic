package game.map.editor.ui.info.marker;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.ScrollPaneConstants;
import javax.swing.SpinnerNumberModel;

import app.SwingUtils;
import game.map.MutablePoint;
import game.map.editor.MapEditor;
import game.map.editor.ui.PathList;
import game.map.marker.Marker.MarkerType;
import game.map.marker.NpcComponent;
import game.map.marker.NpcComponent.MoveType;
import game.map.marker.NpcComponent.SetDetectPos;
import game.map.marker.NpcComponent.SetWanderPos;
import game.map.marker.PathData.AddPathPoint;
import net.miginfocom.swing.MigLayout;
import util.ui.FloatTextField;
import util.ui.IntVectorPanel;

public class NpcMovementTab extends JPanel
{
	private static enum VolumeType
	{
		Circle, Box
	}

	private final MarkerInfoPanel parent;

	private JPanel wanderPanel;
	private JPanel patrolPanel;
	private PathList patrolPathList;

	private JComboBox<MoveType> moveTypeBox;
	private JCheckBox moveFlyingCheckbox;

	private JCheckBox overrideCheckbox;
	private FloatTextField overrideField;

	private IntVectorPanel detectCenterPanel;
	private JComboBox<VolumeType> detectTypeBox;
	private JSpinner detectSpinner1;
	private JSpinner detectSpinner2;

	private IntVectorPanel wanderCenterPanel;
	private JComboBox<VolumeType> wanderTypeBox;
	private JSpinner wanderSpinner1;
	private JSpinner wanderSpinner2;

	public NpcMovementTab(MarkerInfoPanel parent)
	{
		this.parent = parent;

		moveTypeBox = new JComboBox<>(MoveType.values());
		moveTypeBox.addActionListener(e -> {
			if (parent.ignoreEvents())
				return;

			MoveType type = (MoveType) moveTypeBox.getSelectedItem();
			MapEditor.execute(parent.getData().npcComponent.moveType.mutator(type));
		});
		SwingUtils.addBorderPadding(moveTypeBox);

		moveFlyingCheckbox = new JCheckBox(" Is Flying?");
		moveFlyingCheckbox.addActionListener((e) -> MapEditor.execute(
			parent.getData().npcComponent.flying.mutator(moveFlyingCheckbox.isSelected())));

		overrideCheckbox = new JCheckBox(" Override Movement Speed?");
		overrideCheckbox.addActionListener((e) -> MapEditor.execute(
			parent.getData().npcComponent.overrideMovementSpeed.mutator(overrideCheckbox.isSelected())));

		overrideField = new FloatTextField((speed) -> MapEditor.execute(
			parent.getData().npcComponent.movementSpeedOverride.mutator(speed)));

		detectCenterPanel = new IntVectorPanel(3, (i, v) -> MapEditor.execute(new SetDetectPos(parent.getData(), i, v)));

		detectTypeBox = new JComboBox<>(VolumeType.values());
		detectTypeBox.addActionListener(e -> {
			if (parent.ignoreEvents())
				return;

			boolean useCircle = (VolumeType) detectTypeBox.getSelectedItem() == VolumeType.Circle;
			MapEditor.execute(parent.getData().npcComponent.useDetectCircle.mutator(useCircle));
		});

		detectSpinner1 = new JSpinner(new SpinnerNumberModel(256, 0, 4096, 1));
		detectSpinner1.addChangeListener((e) -> {
			int v = (int) detectSpinner1.getValue();
			if (parent.getData().npcComponent.useDetectCircle.get())
				MapEditor.execute(parent.getData().npcComponent.detectRadius.mutator(v));
			else
				MapEditor.execute(parent.getData().npcComponent.detectSizeX.mutator(v));
		});

		detectSpinner2 = new JSpinner(new SpinnerNumberModel(256, 0, 4096, 1));
		detectSpinner2.addChangeListener((e) -> {
			int v = (int) detectSpinner2.getValue();
			if (!parent.getData().npcComponent.useDetectCircle.get())
				MapEditor.execute(parent.getData().npcComponent.detectSizeZ.mutator(v));
		});

		wanderCenterPanel = new IntVectorPanel(3, (i, v) -> MapEditor.execute(new SetWanderPos(parent.getData(), i, v)));

		wanderTypeBox = new JComboBox<>(VolumeType.values());
		wanderTypeBox.addActionListener(e -> {
			if (parent.ignoreEvents())
				return;

			boolean useCircle = (VolumeType) wanderTypeBox.getSelectedItem() == VolumeType.Circle;
			MapEditor.execute(parent.getData().npcComponent.useWanderCircle.mutator(useCircle));
		});

		wanderSpinner1 = new JSpinner(new SpinnerNumberModel(256, 0, 4096, 1));
		wanderSpinner1.addChangeListener((e) -> {
			int v = (int) wanderSpinner1.getValue();
			if (parent.getData().npcComponent.useWanderCircle.get())
				MapEditor.execute(parent.getData().npcComponent.wanderRadius.mutator(v));
			else
				MapEditor.execute(parent.getData().npcComponent.wanderSizeX.mutator(v));
		});

		wanderSpinner2 = new JSpinner(new SpinnerNumberModel(256, 0, 4096, 1));
		wanderSpinner2.addChangeListener((e) -> {
			int v = (int) wanderSpinner2.getValue();
			if (!parent.getData().npcComponent.useWanderCircle.get())
				MapEditor.execute(parent.getData().npcComponent.wanderSizeZ.mutator(v));
		});

		JButton addPointButton = new JButton("Add Point");
		addPointButton.addActionListener((e) -> {
			MapEditor.execute(new AddPathPoint(parent.getData().npcComponent.patrolPath));
		});

		wanderPanel = new JPanel(new MigLayout("fill, ins 0"));

		wanderPanel.add(new JLabel("Wandering Volume"), "growx, wrap");
		wanderPanel.add(new JLabel("Center"), "w 17%!, span, split 4");
		wanderPanel.add(wanderCenterPanel, "span, growx, wrap");

		wanderPanel.add(new JLabel("Shape"), "w 17%!, span, split 4");
		wanderPanel.add(wanderTypeBox, "growx, sg shape");
		wanderPanel.add(wanderSpinner1, "growx, sg shape");
		wanderPanel.add(wanderSpinner2, "growx, sg shape, wrap");

		patrolPathList = new PathList();

		JScrollPane scrollPane = new JScrollPane(patrolPathList);
		scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		scrollPane.setBorder(null);

		patrolPanel = new JPanel(new MigLayout("fill, ins 0"));
		patrolPanel.add(new JLabel("Patrol Path"), "growx, wrap");
		patrolPanel.add(scrollPane, "growx, growy, pushy, wrap");
		patrolPanel.add(addPointButton, "w 50%, center, wrap");

		JPanel contents = new JPanel(new MigLayout("fillx, ins 0, hidemode 3"));

		contents.add(new JLabel("Type"), "span, split 3, w 15%!");
		contents.add(moveTypeBox, "growx");
		contents.add(moveFlyingCheckbox, "w 25%!, gapleft 3%, wrap");

		contents.add(new JLabel(), "span, split 3, w 15%!");
		contents.add(overrideCheckbox, "growx");
		contents.add(overrideField, "w 25%!, gapleft 3%, wrap");

		contents.add(new JLabel(), "h 0!, wrap");

		contents.add(new JLabel("Detection Volume"), "growx, wrap");
		contents.add(new JLabel("Center"), "w 15%!, span, split 2");
		contents.add(detectCenterPanel, "span, growx, wrap");

		contents.add(new JLabel("Shape"), "w 15%!, span, split 4");
		contents.add(detectTypeBox, "growx, sg shape");
		contents.add(detectSpinner1, "growx, sg shape");
		contents.add(detectSpinner2, "growx, sg shape, wrap");

		contents.add(new JLabel(), "h 0!, wrap");

		contents.add(wanderPanel, "growx, span, wrap");

		contents.add(patrolPanel, "growx, span, wrap");

		setLayout(new MigLayout("fill, ins n 16 n 16"));
		add(contents, "growx, gapbottom push");
	}

	public void onSetData()
	{

	}

	public void onUpdateFields(String tag)
	{
		assert (parent.getData().getType() == MarkerType.NPC);

		NpcComponent npc = parent.getData().npcComponent;

		moveTypeBox.setSelectedItem(npc.moveType.get());
		moveFlyingCheckbox.setSelected(npc.flying.get());

		overrideField.setValue(npc.movementSpeedOverride.get());

		boolean override = npc.overrideMovementSpeed.get();
		overrideCheckbox.setSelected(override);
		overrideField.setEnabled(override);

		if (npc.useDetectCircle.get()) {
			detectTypeBox.setSelectedItem(VolumeType.Circle);
			detectSpinner2.setEnabled(false);

			detectSpinner1.setValue(npc.detectRadius.get());
			detectSpinner2.setValue(0);
		}
		else {
			detectTypeBox.setSelectedItem(VolumeType.Box);
			detectSpinner2.setEnabled(true);

			detectSpinner1.setValue(npc.detectSizeX.get());
			detectSpinner2.setValue(npc.detectSizeZ.get());
		}

		wanderPanel.setVisible(npc.moveType.get() == MoveType.Wander);
		if (npc.moveType.get() == MoveType.Wander) {
			if (npc.useWanderCircle.get()) {
				wanderTypeBox.setSelectedItem(VolumeType.Circle);
				wanderSpinner2.setEnabled(false);

				wanderSpinner1.setValue(npc.wanderRadius.get());
				wanderSpinner2.setValue(0);
			}
			else {
				wanderTypeBox.setSelectedItem(VolumeType.Box);
				wanderSpinner2.setEnabled(true);

				wanderSpinner1.setValue(npc.wanderSizeX.get());
				wanderSpinner2.setValue(npc.wanderSizeZ.get());
			}
		}

		patrolPanel.setVisible(npc.moveType.get() == MoveType.Patrol);
		if (npc.moveType.get() == MoveType.Patrol) {
			patrolPathList.setModel(npc.patrolPath.points);
			npc.patrolPath.markDegenerates();
		}
	}

	public void updateMovementCoords()
	{
		NpcComponent npc = parent.getData().npcComponent;

		MutablePoint point = npc.detectPoint;
		detectCenterPanel.setValues(point.getX(), point.getY(), point.getZ());

		switch (npc.moveType.get()) {
			case Stationary:
				break;

			case Wander:
				point = npc.wanderPoint;
				wanderCenterPanel.setValues(point.getX(), point.getY(), point.getZ());
				break;

			case Patrol:
				npc.patrolPath.markDegenerates();
				patrolPathList.repaint();
				break;
		}
	}
}
