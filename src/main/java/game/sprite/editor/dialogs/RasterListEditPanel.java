package game.sprite.editor.dialogs;

import javax.swing.DefaultListModel;
import javax.swing.JButton;

import game.sprite.Sprite;
import game.sprite.SpriteRaster;

public class RasterListEditPanel extends ListEditPanel<SpriteRaster>
{
	public RasterListEditPanel(Sprite sprite, DefaultListModel<SpriteRaster> listModel)
	{
		super(listModel);

		JButton addButton = new JButton("Add New Raster");
		addButton.addActionListener((e) -> {
			//	SpriteRaster sr = new SpriteRaster();
			//	sr.name = "New Raster";
			//	listModel.addElement(sr);
		});

		JButton dupeButton = new JButton("Duplicate Selected");
		dupeButton.addActionListener((e) -> {
			SpriteRaster original = list.getSelectedValue();
			if (original == null)
				return;

			SpriteRaster sr = new SpriteRaster(original);
			sr.name = original + " (copy)";
			listModel.addElement(sr);
		});

		//add(addButton, "sg but, growx, split 2");
		add(dupeButton, "sg but, w 50%, center");
	}

	@Override
	public void onDelete(SpriteRaster raster)
	{
		raster.deleted = true;
	}

	@Override
	public void rename(int index, String newName)
	{
		SpriteRaster sr = listModel.get(index);
		sr.name = newName;
	}
}
