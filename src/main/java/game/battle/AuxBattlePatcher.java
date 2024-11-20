package game.battle;

import java.io.IOException;

import game.battle.items.ItemPatcher;
import game.battle.minigame.MinigamePatcher;
import game.battle.moves.MovePatcher;
import game.battle.partners.PartnerActorPatcher;
import game.battle.starpowers.StarPowerPatcher;
import patcher.Patcher;

public class AuxBattlePatcher
{
	private MovePatcher movePatcher;
	private PartnerActorPatcher partnerPatcher;
	private ItemPatcher itemPatcher;
	private StarPowerPatcher starPatcher;
	private MinigamePatcher commandPatcher;

	public AuxBattlePatcher(Patcher patcher)
	{
		movePatcher = new MovePatcher(patcher);
		partnerPatcher = new PartnerActorPatcher(patcher);
		itemPatcher = new ItemPatcher(patcher);
		starPatcher = new StarPowerPatcher(patcher);
		commandPatcher = new MinigamePatcher(patcher);
	}

	public void patchData() throws IOException
	{
		movePatcher.patchMoveData();
		partnerPatcher.patchPartnerData();
		itemPatcher.patchItemData();
		starPatcher.patchStarPowerData();
		commandPatcher.patchData();
	}

	public void generateConfigs() throws IOException
	{
		movePatcher.generateConfigs();
		partnerPatcher.generateConfigs();
		itemPatcher.generateConfigs();
		starPatcher.generateConfigs();
		commandPatcher.generateConfigs();
	}

	public void writeTables() throws IOException
	{
		movePatcher.writeMoveTable();
		partnerPatcher.writePartnerTable();
		itemPatcher.writeItemTable();
		starPatcher.writeStarPowerTable();
		commandPatcher.writeTable();
	}

	public void writeData() throws IOException
	{
		movePatcher.writeMoveData();
		partnerPatcher.writePartnerData();
		itemPatcher.writeItemData();
		starPatcher.writeStarPowerData();
		commandPatcher.writeData();
	}
}
