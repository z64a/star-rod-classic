package game.sprite.editor.dialogs;

import javax.swing.DefaultListModel;
import javax.swing.JButton;

import game.sprite.Sprite;
import game.sprite.SpritePalette;
import game.texture.Palette;

public class PaletteListEditPanel extends ListEditPanel<SpritePalette>
{
	public PaletteListEditPanel(Sprite sprite, DefaultListModel<SpritePalette> listModel)
	{
		super(listModel);

		JButton addButton = new JButton("Add New Palette");
		addButton.addActionListener((e) -> {
			Palette pal = Palette.createDefaultForSprite();
			SpritePalette sp = new SpritePalette(pal);
			sp.name = "New Palette";
			listModel.addElement(sp);
		});

		JButton dupeButton = new JButton("Duplicate Selected");
		dupeButton.addActionListener((e) -> {
			SpritePalette original = list.getSelectedValue();
			if (original == null)
				return;

			SpritePalette sp = new SpritePalette(original);
			sp.name = original + " (copy)";
			listModel.addElement(sp);
		});

		add(addButton, "sg but, growx, split 2");
		add(dupeButton, "sg but, growx");
	}

	@Override
	public void onDelete(SpritePalette palette)
	{
		palette.deleted = true;
	}

	@Override
	public void rename(int index, String newName)
	{
		SpritePalette sp = listModel.get(index);
		sp.name = newName;
	}
}
