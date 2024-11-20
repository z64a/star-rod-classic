package game.globals.editor;

import static app.Directories.*;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.swing.DefaultComboBoxModel;
import javax.swing.ImageIcon;

import app.StarRodException;
import app.input.InputFileException;
import game.globals.ItemModder;
import game.globals.ItemRecord;
import game.globals.MoveModder;
import game.globals.MoveRecord;
import game.shared.ProjectDatabase;
import game.shared.ProjectDatabase.ConstEnum.EnumPair;
import game.texture.images.HudElementRecord;
import game.texture.images.ImageDatabase;
import game.texture.images.ImageRecord;
import game.texture.images.ImageRecord.ImageReference;
import game.texture.images.ImageScriptModder;
import game.texture.images.ItemEntityRecord;
import shared.SwingUtils;
import util.Logger;

public class GlobalsData
{
	// enum order determines load order! prerequisite data must come first
	public static enum GlobalsCategory
	{
		// @formatter:off
		IMAGE_ASSETS	(),
		ITEM_SCRIPTS	(IMAGE_ASSETS),
		HUD_SCRIPTS		(IMAGE_ASSETS),
		MOVE_TABLE		(),
		ITEM_TABLE		(MOVE_TABLE, IMAGE_ASSETS, ITEM_SCRIPTS, HUD_SCRIPTS);
		// @formatter:on

		public final GlobalsCategory[] dependencies;

		private GlobalsCategory(GlobalsCategory ... dependencies)
		{
			this.dependencies = dependencies;
		}
	}

	public final GlobalsListModel<ImageRecord> images;
	public final GlobalsListModel<ItemRecord> items;
	public final GlobalsListModel<MoveRecord> moves;

	public final GlobalsListModel<ItemEntityRecord> itemEntities;

	public final GlobalsListModel<HudElementRecord> itemHudElements;
	public final GlobalsListModel<HudElementRecord> globalHudElements;
	public final GlobalsListModel<HudElementRecord> battleHudElements;
	public final GlobalsListModel<HudElementRecord> menuHudElements;

	private static final String NO_ABILITY = "None";
	private final DefaultComboBoxModel<String> abilities = new DefaultComboBoxModel<>();

	public GlobalsData()
	{
		moves = new GlobalsListModel<>();
		items = new GlobalsListModel<>();
		images = new GlobalsListModel<>();

		itemEntities = new GlobalsListModel<>();

		itemHudElements = new GlobalsListModel<>();
		globalHudElements = new GlobalsListModel<>();
		battleHudElements = new GlobalsListModel<>();
		menuHudElements = new GlobalsListModel<>();
	}

	public void loadDataStrict(boolean fromProject)
	{
		loadImageAssets(fromProject);
		loadItemScripts(fromProject);
		loadHudScripts(fromProject);
		loadMoves(fromProject);
		loadItems(fromProject);
	}

	public void loadDataFlexible(boolean fromProject)
	{
		try {
			loadImageAssets(fromProject);
		}
		catch (Exception e) {
			SwingUtils.getErrorDialog()
				.setTitle("Failed to load image assets!")
				.setMessage(e.getMessage())
				.show();
		}

		try {
			loadItemScripts(fromProject);
		}
		catch (Exception e) {
			SwingUtils.getErrorDialog()
				.setTitle("Failed to load image scripts!")
				.setMessage(e.getMessage())
				.show();
		}

		try {
			loadHudScripts(fromProject);
		}
		catch (Exception e) {
			SwingUtils.getErrorDialog()
				.setTitle("Failed to load HUD elements!")
				.setMessage(e.getMessage())
				.show();
		}

		try {
			loadMoves(fromProject);
		}
		catch (Exception e) {
			SwingUtils.getErrorDialog()
				.setTitle("Failed to load moves!")
				.setMessage(e.getMessage())
				.show();
		}

		try {
			loadItems(fromProject);
		}
		catch (Exception e) {
			SwingUtils.getErrorDialog()
				.setTitle("Failed to load items!")
				.setMessage(e.getMessage())
				.show();
		}
	}

	public void loadAssets()
	{
		for (ImageRecord img : images) {
			try {
				img.loadPreviews();
			}
			catch (IOException e) {
				Logger.printStackTrace(e);
			}
		}

		for (ItemEntityRecord itemEntity : itemEntities) {
			try {
				itemEntity.load();
			}
			catch (IOException e) {
				Logger.printStackTrace(e);
			}
		}

		loadHudElementPreviews(itemHudElements);
		loadHudElementPreviews(globalHudElements);
		loadHudElementPreviews(battleHudElements);
		loadHudElementPreviews(menuHudElements);
	}

	private void loadHudElementPreviews(GlobalsListModel<HudElementRecord> hudElements)
	{
		for (HudElementRecord hudElem : hudElements) {
			try {
				hudElem.load();
			}
			catch (IOException e) {
				Logger.printStackTrace(e);
			}
		}
	}

	public void saveAllData()
	{
		for (GlobalsCategory type : GlobalsCategory.values())
			saveData(type);
	}

	private void saveData(GlobalsCategory type)
	{
		switch (type) {
			case ITEM_TABLE:
				saveItems();
				break;
			case MOVE_TABLE:
				saveMoves();
				break;
			case IMAGE_ASSETS:
				saveImageAssets();
				break;
			case ITEM_SCRIPTS:
				saveItemScripts();
				break;
			case HUD_SCRIPTS:
				saveHudScripts();
				break;
		}
	}

	private void loadItems(boolean fromProject)
	{
		List<ItemRecord> itemList;
		try {
			itemList = ItemModder.loadItems(fromProject);
		}
		catch (IOException e) {
			throw new StarRodException("IOException while loading item table! %n%s", e.getMessage());
		}

		items.clear();
		int index = 0;
		for (ItemRecord item : itemList) {
			items.addElement(item);
			item.setIndex(index++);
		}

		Logger.log("Loaded " + items.size() + " items");
	}

	private void saveItems()
	{
		List<ItemRecord> itemList = new ArrayList<>(items.getSize());
		for (int i = 0; i < items.size(); i++)
			itemList.add(items.get(i));

		try {
			ItemModder.saveItems(itemList);
		}
		catch (IOException e) {
			throw new StarRodException("IOException while saving item table! %n%s", e.getMessage());
		}

		Logger.log("Saved item table");
	}

	private void loadMoves(boolean fromProject)
	{
		List<MoveRecord> moveList;
		try {
			moveList = MoveModder.loadMoves(fromProject);
		}
		catch (IOException e) {
			throw new StarRodException("IOException while loading move table! %n%s", e.getMessage());
		}

		moves.clear();
		int index = 0;
		for (MoveRecord move : moveList) {
			moves.addElement(move);
			move.setIndex(index++);
		}

		abilities.removeAllElements();
		abilities.addElement(NO_ABILITY);
		for (EnumPair e : ProjectDatabase.AbilityType.getDecoding())
			abilities.addElement(e.value);

		Logger.log("Loaded " + moves.size() + " moves");
	}

	private void saveMoves()
	{
		List<MoveRecord> moveList = new ArrayList<>(moves.getSize());
		for (int i = 0; i < moves.size(); i++)
			moveList.add(moves.get(i));

		try {
			MoveModder.saveMoves(moveList);
		}
		catch (IOException e) {
			throw new StarRodException("IOException while saving move table! %n%s", e.getMessage());
		}

		Logger.log("Saved move table");
	}

	private void loadImageAssets(boolean fromProject)
	{
		try {
			File f = new File((fromProject ? MOD_IMG : DUMP_IMG) + FN_IMAGE_ASSETS);
			List<ImageRecord> imageList = ImageDatabase.readXML(f);
			images.clear();
			for (ImageRecord image : imageList)
				images.addElement(image);
		}
		catch (InputFileException | IOException e) {
			throw new StarRodException("Exception while loading image assets! %n%s", e.getMessage());
		}

		Logger.logf("Loaded %d image assets", images.getSize());
	}

	public void saveImageAssets() //TEMP public for 0.5 conversion
	{
		List<ImageRecord> imageList = new ArrayList<>(images.getSize());
		for (int i = 0; i < images.size(); i++)
			imageList.add(images.get(i));

		try {
			ImageDatabase.writeXML(imageList, new File(MOD_IMG + FN_IMAGE_ASSETS));
		}
		catch (IOException e) {
			throw new StarRodException("Exception while saving image assets! %n%s", e.getMessage());
		}
		Logger.logf("Saved %d image assets", images.getSize());
	}

	private void loadItemScripts(boolean fromProject)
	{
		try {
			ImageScriptModder.readItemXML(this, fromProject);
			for (ItemEntityRecord item : itemEntities)
				item.scanScriptForPreviewImage();
		}
		catch (InputFileException e) {
			throw new StarRodException("Exception while loading item entity scripts! %n%s", e.getMessage());
		}
		Logger.logf("Loaded %d item entity scripts", itemEntities.getSize());
	}

	public void saveItemScripts() //TEMP public for 0.5 conversion
	{
		try {
			for (ItemEntityRecord rec : itemEntities) {
				if (rec.getModified())
					rec.save();
			}
			ImageScriptModder.writeItemXML(this);
		}
		catch (IOException e) {
			throw new StarRodException("Exception while saving item entity scripts! %n%s", e.getMessage());
		}
		Logger.logf("Saved %d item entity scripts", itemEntities.getSize());
	}

	private void loadHudScripts(boolean fromProject)
	{
		try {
			ImageScriptModder.readHudXML(this, fromProject);
		}
		catch (InputFileException e) {
			throw new StarRodException("Exception while loading HUD element scripts! %n%s", e.getMessage());
		}
		int count = itemHudElements.size() + globalHudElements.size() + battleHudElements.size() + menuHudElements.size();
		Logger.logf("Loaded %d HUD element scripts", count);
	}

	public void saveHudScripts() //TEMP public for 0.5 conversion
	{
		try {
			for (HudElementRecord rec : itemHudElements) {
				if (rec.getModified())
					rec.save();
			}
			for (HudElementRecord rec : globalHudElements) {
				if (rec.getModified())
					rec.save();
			}
			for (HudElementRecord rec : battleHudElements) {
				if (rec.getModified())
					rec.save();
			}
			for (HudElementRecord rec : menuHudElements) {
				if (rec.getModified())
					rec.save();
			}
			ImageScriptModder.writeHudXML(this);
		}
		catch (IOException e) {
			throw new StarRodException("Exception while loading HUD element scripts! %n%s", e.getMessage());
		}
		int count = itemHudElements.size() + globalHudElements.size() + battleHudElements.size() + menuHudElements.size();
		Logger.logf("Saved %d HUD element scripts", count);
	}

	public ImageIcon getPreviewImage(String name)
	{
		ImageReference ref = ImageRecord.parseImageName(name);
		if (ref == null)
			return null;

		ImageRecord img = images.getElement(ref.name);
		if (img == null)
			return null;

		if (img != null && img.preview != null && img.preview.length > ref.index)
			return img.preview[ref.index];
		else
			return null;
	}

	public ImageIcon getSmallPreviewImage(String name)
	{
		ImageReference ref = ImageRecord.parseImageName(name);
		if (ref == null)
			return null;

		ImageRecord img = images.getElement(ref.name);
		if (img == null)
			return null;

		if (img != null && img.smallPreview != null && img.smallPreview.length > ref.index)
			return img.smallPreview[ref.index];
		else
			return null;
	}

	public void generateAutoGraphics()
	{
		for (ItemRecord item : items) {
			if (item.autoGraphics) {
				if (item.imageAssetName == null || item.imageAssetName.isBlank())
					throw new StarRodException("Item %s is missing image for auto-graphics.", item.getIdentifier());

				if (!item.imageAssetName.matches("\\S+"))
					throw new StarRodException("Item %s has invalid image name for auto-graphics: %s", item.getIdentifier(), item.imageAssetName);

				ImageRecord image = images.getElement(item.imageAssetName);
				if (image == null)
					throw new StarRodException("Item %s has unknown image for auto-graphics: %s", item.getIdentifier(), item.imageAssetName);

				HudElementRecord hudElem = new HudElementRecord();
				hudElem.identifier = "~auto:" + item.getIdentifier();
				hudElem.buildAutoScript(image.source[0], image.tile[0].width, image.tile[0].height);
				itemHudElements.addElement(hudElem);
				item.hudElemName = hudElem.identifier;

				if (image.palCount > 1 && image.tile[1] != null) {
					hudElem = new HudElementRecord();
					hudElem.identifier = "~auto:" + item.getIdentifier() + "_disabled";
					hudElem.buildAutoScript(image.source[1], image.tile[1].width, image.tile[1].width);
					itemHudElements.addElement(hudElem);
				}

				ItemEntityRecord itemNtt = new ItemEntityRecord();
				itemNtt.identifier = "~auto:" + item.getIdentifier();
				itemNtt.buildAutoScript(item.imageAssetName);
				itemEntities.addElement(itemNtt);
				item.itemEntityName = itemNtt.identifier;
			}
		}
	}

	public HudElementRecord getHudElement(String name)
	{
		for (HudElementRecord elem : globalHudElements)
			if (elem.getIdentifier().equals(name))
				return elem;

		for (HudElementRecord elem : battleHudElements)
			if (elem.getIdentifier().equals(name))
				return elem;

		for (HudElementRecord elem : menuHudElements)
			if (elem.getIdentifier().equals(name))
				return elem;

		for (HudElementRecord elem : itemHudElements)
			if (elem.getIdentifier().equals(name))
				return elem;

		return null;
	}
}
