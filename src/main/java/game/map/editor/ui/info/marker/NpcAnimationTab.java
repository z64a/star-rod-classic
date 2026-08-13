package game.map.editor.ui.info.marker;

import java.util.Collection;

import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;

import app.StarRodException;
import app.SwingUtils;
import game.map.editor.MapEditor;
import game.map.marker.NpcComponent;
import game.map.marker.NpcComponent.SetAnimation;
import game.map.marker.NpcComponent.SetAnimationOverride;
import game.map.marker.NpcComponent.SetDefaultAnimation;
import game.map.marker.NpcComponent.SetMarkerPalette;
import game.map.marker.NpcComponent.SetMarkerSprite;
import game.sprite.Sprite;
import game.sprite.SpriteAnimation;
import game.sprite.SpriteLoader;
import game.sprite.SpriteLoader.SpriteMetadata;
import game.sprite.SpriteLoader.SpriteSet;
import game.sprite.SpritePalette;
import game.sprite.editor.IndexableComboBoxRenderer;
import net.miginfocom.swing.MigLayout;
import util.ui.ListAdapterComboboxModel;

public class NpcAnimationTab extends JPanel
{
	private static final String[] ANIMATION_NAMES = {
			"Idle", "Walk", "Run", "Chase",
			"Alert", "Unused", "Death", "Hit",
			"08", "09", "0A", "0B",
			"0C", "0D", "0E", "0F"
	};

	private final MarkerInfoPanel parent;

	private final RangeCheckComboBox<SpriteMetadata> spriteComboBox;
	private final RangeCheckComboBox<SpritePalette> paletteBox;
	private final RangeCheckComboBox<SpriteAnimation> defaultAnimationBox;

	private final JPanel spriteDetailsPanel;
	private final JPanel animationsPanel;
	private final JCheckBox hideAiAnimationsCheckbox;

	private final RangeCheckComboBox<SpriteAnimation>[] animationComboBoxes;
	private final JCheckBox[] animationOverrideCheckboxes;
	private final JPanel[] animationRows;

	@SuppressWarnings("unchecked")
	public NpcAnimationTab(MarkerInfoPanel parent)
	{
		this.parent = parent;

		SpriteLoader.initialize();

		spriteComboBox = new RangeCheckComboBox<>();
		spriteComboBox.setRenderer(new IndexableComboBoxRenderer());
		spriteComboBox.setMaximumRowCount(24);

		Collection<SpriteMetadata> spriteNames = SpriteLoader.getValidSprites(SpriteSet.Npc);
		if (spriteNames.isEmpty())
			throw new StarRodException("No valid NPC sprites could be found!");

		for (SpriteMetadata sprite : spriteNames)
			spriteComboBox.addItem(sprite);

		spriteComboBox.addActionListener((e) -> {
			if (parent.ignoreEvents() || parent.getData() == null)
				return;
			SpriteMetadata sprite = (SpriteMetadata) spriteComboBox.getSelectedItem();
			if (sprite != null)
				MapEditor.execute(new SetMarkerSprite(parent.getData(), sprite.id));
		});

		paletteBox = new RangeCheckComboBox<>();
		paletteBox.setRenderer(new IndexableComboBoxRenderer());
		paletteBox.addActionListener((e) -> {
			if (parent.ignoreEvents() || parent.getData() == null)
				return;
			MapEditor.execute(new SetMarkerPalette(parent.getData(), paletteBox.getSelectedIndex()));
		});

		defaultAnimationBox = new RangeCheckComboBox<>();
		defaultAnimationBox.setRenderer(new IndexableComboBoxRenderer());
		defaultAnimationBox.addActionListener((e) -> {
			if (parent.ignoreEvents() || parent.getData() == null || defaultAnimationBox.getSelectedIndex() < 0)
				return;
			MapEditor.execute(new SetDefaultAnimation(parent.getData(), defaultAnimationBox.getSelectedIndex()));
		});

		animationComboBoxes = new RangeCheckComboBox[16];
		animationOverrideCheckboxes = new JCheckBox[16];
		animationRows = new JPanel[16];

		animationsPanel = new JPanel(new MigLayout("fillx, ins 0, hidemode 3"));
		for (int i = 0; i < 16; i++) {
			final int index = i;
			animationComboBoxes[i] = new RangeCheckComboBox<>();
			animationComboBoxes[i].setRenderer(new IndexableComboBoxRenderer());
			animationComboBoxes[i].addActionListener((e) -> {
				if (parent.ignoreEvents() || parent.getData() == null || animationComboBoxes[index].getSelectedIndex() < 0)
					return;
				MapEditor.execute(new SetAnimation(parent.getData(), index, animationComboBoxes[index].getSelectedIndex()));
			});

			animationOverrideCheckboxes[i] = new JCheckBox();
			animationOverrideCheckboxes[i].setToolTipText("Override the default animation for " + ANIMATION_NAMES[i]);
			animationOverrideCheckboxes[i].addActionListener((e) -> {
				if (parent.ignoreEvents() || parent.getData() == null)
					return;
				MapEditor.execute(new SetAnimationOverride(parent.getData(), index, animationOverrideCheckboxes[index].isSelected()));
			});

			animationRows[i] = new JPanel(new MigLayout("fillx, ins 0"));
			animationRows[i].add(animationOverrideCheckboxes[i], "w 8%!");
			animationRows[i].add(new JLabel(ANIMATION_NAMES[i]), "w 18%!");
			animationRows[i].add(animationComboBoxes[i], "growx, pushx");
			animationsPanel.add(animationRows[i], "growx, wrap");
		}

		hideAiAnimationsCheckbox = new JCheckBox(" Hide AI-specific animations (08-0F)", true);
		hideAiAnimationsCheckbox.addActionListener((e) -> updateAnimationVisibility());

		JPanel spritePanel = new JPanel(new MigLayout("fillx, ins 0"));
		spritePanel.add(new JLabel("Sprite"), "w 28%!");
		spritePanel.add(spriteComboBox, "growx, pushx");

		spriteDetailsPanel = new JPanel(new MigLayout("fillx, ins 0"));
		spriteDetailsPanel.add(new JLabel("Palette"), "w 28%!");
		spriteDetailsPanel.add(paletteBox, "growx, pushx, wrap");
		spriteDetailsPanel.add(new JLabel("Default Anim"), "w 28%!");
		spriteDetailsPanel.add(defaultAnimationBox, "growx, pushx");

		setLayout(new MigLayout("fillx, ins n 16 n 16, wrap"));
		add(spritePanel, "growx");
		add(spriteDetailsPanel, "growx");
		add(hideAiAnimationsCheckbox, "gaptop 8");
		add(animationsPanel, "growx");

		updateAnimationVisibility();
	}

	public void updateFields()
	{
		NpcComponent npc = parent.getData().npcComponent;

		spriteComboBox.setSelectedIndex(npc.getSpriteID() - 1);
		Sprite previewSprite = npc.previewSprite;
		if (previewSprite == null) {
			spriteDetailsPanel.setVisible(false);
			hideAiAnimationsCheckbox.setVisible(false);
			animationsPanel.setVisible(false);
			return;
		}

		spriteDetailsPanel.setVisible(true);
		hideAiAnimationsCheckbox.setVisible(true);
		animationsPanel.setVisible(true);

		paletteBox.setModel(new ListAdapterComboboxModel<>(previewSprite.palettes));
		paletteBox.setSelectedIndex(npc.getPaletteID());

		defaultAnimationBox.setModel(new ListAdapterComboboxModel<>(previewSprite.animations));
		defaultAnimationBox.setSelectedIndex(npc.getDefaultAnimation());

		for (int i = 0; i < 16; i++) {
			animationComboBoxes[i].setModel(new ListAdapterComboboxModel<>(previewSprite.animations));
			boolean overridden = npc.isAnimationOverridden(i);
			animationOverrideCheckboxes[i].setSelected(overridden);
			animationComboBoxes[i].setSelectedIndex(npc.getAnimation(i));
			animationComboBoxes[i].setEnabled(overridden);
		}

		updateAnimationVisibility();
	}

	private void updateAnimationVisibility()
	{
		boolean hideAiAnimations = hideAiAnimationsCheckbox.isSelected();
		for (int i = 8; i < animationRows.length; i++)
			animationRows[i].setVisible(!hideAiAnimations);
		animationsPanel.revalidate();
		animationsPanel.repaint();
	}

	private static class RangeCheckComboBox<T> extends JComboBox<T>
	{
		@Override
		public void setSelectedIndex(int index)
		{
			int maxValue = getModel().getSize() - 1;
			if (index > maxValue) {
				setForeground(SwingUtils.getRedTextColor());
				super.setSelectedIndex(maxValue);
			}
			else {
				setForeground(SwingUtils.getTextColor());
				super.setSelectedIndex(index);
			}
		}
	}
}
