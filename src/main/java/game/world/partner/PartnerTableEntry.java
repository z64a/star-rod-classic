package game.world.partner;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashMap;

import app.input.InvalidInputException;
import game.shared.DataUtils;
import game.shared.struct.Struct;
import patcher.RomPatcher;

public class PartnerTableEntry
{
	public File binary;
	public int romStart;
	public int romEnd;

	public final boolean isFlying;
	public final int spriteID;

	public final int ptrFuncInit;

	public final int ptrFuncTestEnemyCollision;
	public final int ptrFuncCanUseAbility;
	public final int ptrFuncPlayerCanPause;
	public final int ptrFuncBeforeBattle;
	public final int ptrFuncAfterBattle;

	public final int ptrScriptTakeOut;
	public final int ptrScriptUseAbility;
	public final int ptrScriptUpdate;
	public final int ptrScriptPutAway;
	public final int ptrScriptWhileRiding;

	protected PartnerTableEntry(ByteBuffer fileBuffer)
	{
		romStart = fileBuffer.getInt();
		romEnd = fileBuffer.getInt();
		fileBuffer.getInt(); // 802BD100
		int flying = fileBuffer.getInt();
		assert (flying == 0 || flying == 1);
		isFlying = (flying != 0);

		ptrFuncInit = fileBuffer.getInt();
		ptrScriptTakeOut = fileBuffer.getInt();
		ptrScriptUseAbility = fileBuffer.getInt();
		ptrScriptUpdate = fileBuffer.getInt();

		ptrScriptPutAway = fileBuffer.getInt();
		spriteID = fileBuffer.getInt();
		ptrFuncTestEnemyCollision = fileBuffer.getInt();
		ptrFuncCanUseAbility = fileBuffer.getInt();

		ptrFuncPlayerCanPause = fileBuffer.getInt();
		ptrFuncBeforeBattle = fileBuffer.getInt();
		ptrFuncAfterBattle = fileBuffer.getInt();
		ptrScriptWhileRiding = fileBuffer.getInt();
	}

	private static int getAddr(HashMap<String, Struct> structMap, String name) throws InvalidInputException
	{
		if (name.isEmpty())
			return 0;

		Struct str = structMap.get(name);
		if (str != null)
			return str.finalAddress; //TODO use original address here?

		try {
			return DataUtils.parseIntString(name);
		}
		catch (InvalidInputException e) {
			throw new InvalidInputException("Invalid name for pointer or address: " + name);
		}
	}

	public PartnerTableEntry(File binary, PartnerConfig cfg, HashMap<String, Struct> structMap) throws InvalidInputException
	{
		this.binary = binary;
		spriteID = cfg.idleAnim;
		isFlying = cfg.isFlying;

		ptrFuncInit = getAddr(structMap, cfg.funcInit);
		ptrFuncTestEnemyCollision = getAddr(structMap, cfg.funcTestEnemyCollision);
		ptrFuncCanUseAbility = getAddr(structMap, cfg.funcCanUseAbility);
		ptrFuncPlayerCanPause = getAddr(structMap, cfg.funcPlayerCanPause);
		ptrFuncBeforeBattle = getAddr(structMap, cfg.funcBeforeBattle);
		ptrFuncAfterBattle = getAddr(structMap, cfg.funcAfterBattle);

		ptrScriptTakeOut = getAddr(structMap, cfg.scriptOnTakeOut);
		ptrScriptUseAbility = getAddr(structMap, cfg.scriptUseAbility);
		ptrScriptUpdate = getAddr(structMap, cfg.scriptUpdate);
		ptrScriptPutAway = getAddr(structMap, cfg.scriptOnPutAway);
		ptrScriptWhileRiding = getAddr(structMap, cfg.scriptWhileRiding);
	}

	public void write(RomPatcher rp) throws IOException
	{
		rp.writeInt(romStart);
		rp.writeInt(romEnd);
		rp.writeInt(0x802BD100);
		rp.writeInt(isFlying ? 1 : 0);

		rp.writeInt(ptrFuncInit);
		rp.writeInt(ptrScriptTakeOut);
		rp.writeInt(ptrScriptUseAbility);
		rp.writeInt(ptrScriptUpdate);

		rp.writeInt(ptrScriptPutAway);
		rp.writeInt(spriteID);
		rp.writeInt(ptrFuncTestEnemyCollision);
		rp.writeInt(ptrFuncCanUseAbility);

		rp.writeInt(ptrFuncPlayerCanPause);
		rp.writeInt(ptrFuncBeforeBattle);
		rp.writeInt(ptrFuncAfterBattle);
		rp.writeInt(ptrScriptWhileRiding);
	}
}
