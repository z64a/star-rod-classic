package game.map.editor.ui.info.marker;

import java.util.Collection;

import javax.swing.ButtonGroup;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;

import app.StarRodException;
import app.SwingUtils;
import game.map.editor.MapEditor;
import game.map.marker.NpcComponent;
import game.map.marker.NpcComponent.SetAnimation;
import game.map.marker.NpcComponent.SetMarkerPalette;
import game.map.marker.NpcComponent.SetMarkerPreviewAnimation;
import game.map.marker.NpcComponent.SetMarkerSprite;
import game.sprite.Sprite;
import game.sprite.SpriteAnimation;
import game.sprite.SpriteLoader;
import game.sprite.SpritePalette;
import game.sprite.SpriteLoader.SpriteMetadata;
import game.sprite.SpriteLoader.SpriteSet;
import game.sprite.editor.IndexableComboBoxRenderer;
import net.miginfocom.swing.MigLayout;
import util.ui.ListAdapterComboboxModel;

public class NpcAnimationTab extends JPanel
{
	private final MarkerInfoPanel parent;

	private RangeCheckComboBox<SpriteMetadata> spriteComboBox;
	private RangeCheckComboBox<SpritePalette> paletteBox;

	private JPanel palettePanel;
	private JScrollPane animsScrollPane;

	private RangeCheckComboBox<?>[] animComboBox = new RangeCheckComboBox[16];
	private JRadioButton[] animRadioButton = new JRadioButton[16];

	public NpcAnimationTab(MarkerInfoPanel parent)
	{
		this.parent = parent;

		SpriteLoader.initialize(); // make sure the sprite files are ready

		/*
		spriteSpinner = new HexSpinner(1, SpriteLoader.getMaximumID(SpriteSet.Npc), 1);
		spriteSpinner.addChangeListener((e) -> {
			if(ignoreChanges)
				return;

			final int maxID = SpriteLoader.getMaximumID(SpriteSet.Npc);
			if(spriteSpinner.getValue() > maxID)
			{
				spriteSpinner.setValue(maxID);
				return;
			}

			MapEditor.execute(new SetMarkerSprite(data, spriteSpinner.getValue()));
		});
		spriteSpinner.setToolTipText("Sprite ID");

		paletteSpinner = new HexSpinner(0, 0, 0);
		paletteSpinner.addChangeListener((e) -> {
			if(ignoreChanges)
				return;

			if(data.previewSprite != null &&
					(paletteSpinner.getValue() > data.previewSprite.lastValidPaletteID()))
			{
				paletteSpinner.setValue(data.previewSprite.lastValidPaletteID());
				return;
			}

			MapEditor.execute(new SetMarkerPalette(data, paletteSpinner.getValue()));
		});
		paletteSpinner.setToolTipText("Palette ID");
		*/

		spriteComboBox = new RangeCheckComboBox<>();
		spriteComboBox.setRenderer(new IndexableComboBoxRenderer());
		spriteComboBox.setMaximumRowCount(24);

		Collection<SpriteMetadata> spriteNames = SpriteLoader.getValidSprites(SpriteSet.Npc);
		if (spriteNames.isEmpty())
			throw new StarRodException("No valid NPC sprites could be found!");

		for (SpriteMetadata sp : spriteNames)
			spriteComboBox.addItem(sp);

		spriteComboBox.addActionListener((e) -> {
			if (parent.ignoreEvents())
				return;
			SpriteMetadata spr = (SpriteMetadata) spriteComboBox.getSelectedItem();
			MapEditor.execute(new SetMarkerSprite(parent.getData(), spr.id));
		});

		paletteBox = new RangeCheckComboBox<>();
		paletteBox.setRenderer(new IndexableComboBoxRenderer());

		paletteBox.addActionListener((e) -> {
			if (parent.ignoreEvents())
				return;
			MapEditor.execute(new SetMarkerPalette(parent.getData(), paletteBox.getSelectedIndex()));
		});

		String[] animNames = {
				"Idle", "Walk", "Run", "Chase",
				"04", "05", "Death", "Hit",
				"08", "09", "0A", "0B",
				"0C", "0D", "0E", "0F"
		};

		animComboBox = new RangeCheckComboBox[16];
		animRadioButton = new JRadioButton[16];

		ButtonGroup bg = new ButtonGroup();

		for (int i = 0; i < 16; i++) {
			final int index = i;
			animComboBox[i] = new RangeCheckComboBox<>();
			animComboBox[i].setRenderer(new IndexableComboBoxRenderer());
			animComboBox[i].addActionListener((e) -> {
				if (parent.ignoreEvents())
					return;
				MapEditor.execute(new SetAnimation(parent.getData(), index, animComboBox[index].getSelectedIndex()));
			});
			animRadioButton[i] = new JRadioButton();
			bg.add(animRadioButton[i]);
			animRadioButton[i].setSelected(i == 0);
			animRadioButton[i].addActionListener((e) -> {
				if (parent.ignoreEvents())
					return;
				MapEditor.execute(new SetMarkerPreviewAnimation(parent.getData(), index));
			});
		}

		// use this so things line up with
		JPanel spritePanel = new JPanel(new MigLayout("fillx, ins 0"));

		spritePanel.add(new JLabel("Sprite"), "w 15%, split 2"); // gaptop 16,
		spritePanel.add(spriteComboBox, "growx, wrap");

		palettePanel = new JPanel(new MigLayout("fillx, ins 0"));

		palettePanel.add(new JLabel("Palette"), "w 15%, split 2");
		palettePanel.add(paletteBox, "growx");

		setLayout(new MigLayout("ins n 16 n 16, wrap, fill"));
		add(spritePanel, "growx, wrap");
		add(palettePanel, "growx, wrap");
		add(new JLabel(), "h 8!, wrap");

		//	animationsPanel.add(SwingUtils.getLabel("Animations", 14), "wrap, gapbottom 8");

		JPanel animsPanel = new JPanel(new MigLayout("fillx, ins 0 0 0 5%"));

		for (int i = 0; i < 16; i++) {
			animsPanel.add(animRadioButton[i], "span, split 3, w 8%");
			animsPanel.add(new JLabel(animNames[i]), "w 16%");
			animsPanel.add(animComboBox[i], "pushx, growx, wrap");
		}

		animsScrollPane = new JScrollPane(animsPanel);
		animsScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		animsScrollPane.setBorder(null);

		add(animsScrollPane, "growx");
		add(new JLabel(), "pushy");
	}

	public void onSetData()
	{
		// reload anim names here?
	}

	@SuppressWarnings("unchecked")
	public void updateFields()
	{
		NpcComponent npc = parent.getData().npcComponent;

		Sprite previewSprite = npc.previewSprite;
		spriteComboBox.setSelectedIndex(npc.getSpriteID() - 1);

		System.out.println(previewSprite);

		if (previewSprite == null) {
			palettePanel.setVisible(false);
			animsScrollPane.setVisible(false);
			return;
		}
		else {
			palettePanel.setVisible(true);
			animsScrollPane.setVisible(true);
		}

		paletteBox.setModel(new ListAdapterComboboxModel<>(previewSprite.palettes));
		paletteBox.setSelectedIndex(npc.getPaletteID());

		// reload combobox models when sprite changes
		for (int i = 0; i < 16; i++) {
			((JComboBox<SpriteAnimation>) animComboBox[i]).setModel(new ListAdapterComboboxModel<>(previewSprite.animations));
			animComboBox[i].setSelectedIndex(npc.getAnimation(i));
		}

		animRadioButton[npc.previewAnimIndex].setSelected(true);
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
