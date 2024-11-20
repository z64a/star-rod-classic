package game.world.partner;

import static app.Directories.DUMP_ASSIST_RAW;
import static app.Directories.DUMP_ASSIST_SRC;
import static game.shared.StructTypes.FunctionT;
import static game.shared.StructTypes.ScriptT;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;

import game.shared.decoder.DumpMetadata;
import game.shared.decoder.Pointer;
import game.shared.decoder.Pointer.Origin;
import game.world.BaseWorldDecoder;

public class PartnerWorldDecoder extends BaseWorldDecoder
{
	private PartnerConfig cfg = new PartnerConfig();

	public PartnerWorldDecoder(ByteBuffer fileBuffer, DumpMetadata metadata, PartnerTableEntry entry) throws IOException
	{
		super();

		useDumpMetadata(metadata);

		System.out.printf("%08X -> %08X :: %s%n", startAddress, endAddress, sourceName);

		findLocalPointers(fileBuffer);

		enqueueAsRoot(entry.ptrFuncInit, FunctionT, Origin.DECODED, "Function_Init");
		enqueueAsRoot(entry.ptrScriptTakeOut, ScriptT, Origin.DECODED, "Script_TakeOut");
		enqueueAsRoot(entry.ptrScriptUseAbility, ScriptT, Origin.DECODED, "Script_UseAbility");
		enqueueAsRoot(entry.ptrScriptUpdate, ScriptT, Origin.DECODED, "Script_Update");
		enqueueAsRoot(entry.ptrScriptPutAway, ScriptT, Origin.DECODED, "Script_PutAway");

		tryEnqueueAsRoot(entry.ptrFuncTestEnemyCollision, FunctionT, Origin.DECODED, "Function_TestEnemyCollision");
		tryEnqueueAsRoot(entry.ptrFuncPlayerCanPause, FunctionT, Origin.DECODED, "Function_PlayerCanPause");
		tryEnqueueAsRoot(entry.ptrFuncCanUseAbility, FunctionT, Origin.DECODED, "Function_CanUseAbility");
		tryEnqueueAsRoot(entry.ptrFuncBeforeBattle, FunctionT, Origin.DECODED, "Function_BeforeBattle");
		tryEnqueueAsRoot(entry.ptrFuncAfterBattle, FunctionT, Origin.DECODED, "Function_AfterBattle");

		tryEnqueueAsRoot(entry.ptrScriptWhileRiding, ScriptT, Origin.DECODED, "Script_WhileRiding");

		super.decode(fileBuffer);

		File rawFile = new File(DUMP_ASSIST_RAW + sourceName + ".bin");
		File scriptFile = new File(DUMP_ASSIST_SRC + sourceName + ".wscr");
		File indexFile = new File(DUMP_ASSIST_SRC + sourceName + ".widx");

		printScriptFile(scriptFile, fileBuffer);
		printIndexFile(indexFile);
		writeRawFile(rawFile, fileBuffer);

		cfg.name = sourceName;
		cfg.isFlying = entry.isFlying;
		cfg.idleAnim = entry.spriteID;

		Pointer ptr = getPointer(entry.ptrFuncInit);
		if (ptr != null)
			cfg.funcInit = ptr.getPointerName();

		cfg.scriptUpdate = resolvePointer(entry.ptrScriptUpdate);
		cfg.scriptUseAbility = resolvePointer(entry.ptrScriptUseAbility);
		cfg.scriptWhileRiding = resolvePointer(entry.ptrScriptWhileRiding);
		cfg.scriptOnTakeOut = resolvePointer(entry.ptrScriptTakeOut);
		cfg.scriptOnPutAway = resolvePointer(entry.ptrScriptPutAway);

		cfg.funcTestEnemyCollision = resolvePointer(entry.ptrFuncTestEnemyCollision);
		cfg.funcCanUseAbility = resolvePointer(entry.ptrFuncCanUseAbility);
		cfg.funcPlayerCanPause = resolvePointer(entry.ptrFuncPlayerCanPause);
		cfg.funcBeforeBattle = resolvePointer(entry.ptrFuncBeforeBattle);
		cfg.funcAfterBattle = resolvePointer(entry.ptrFuncAfterBattle);
	}

	private String resolvePointer(int addr)
	{
		Pointer ptr = getPointer(addr);
		if (ptr != null)
			return ptr.getPointerName();
		else if (addr != 0)
			return String.format("%08X", addr);
		else
			return "";
	}

	public PartnerConfig getPartnerConfig()
	{
		return cfg;
	}
}
