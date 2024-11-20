package game.world;

import static game.shared.StructTypes.*;

import java.io.PrintWriter;
import java.nio.ByteBuffer;

import app.StarRodException;
import game.ROM.LibScope;
import game.map.templates.MapScriptTemplate;
import game.shared.ProjectDatabase;
import game.shared.SyntaxConstants;
import game.shared.decoder.BaseDataDecoder;
import game.shared.decoder.DumpMetadata;
import game.shared.decoder.Pointer;
import game.shared.lib.LibEntry;
import game.shared.lib.LibEntry.LibParam;
import game.shared.lib.LibEntry.LibParamList;
import game.shared.lib.LibEntry.ParamCategory;
import game.shared.lib.LibEntry.ParamListType;
import game.shared.struct.script.Script;
import game.shared.struct.script.Script.ScriptLine;
import reports.EffectTypeTracker;
import reports.FunctionCallTracker;

public abstract class BaseWorldDecoder extends BaseDataDecoder
{
	/**
	 * instance variables
	 */

	protected String sourceName;

	protected void useDumpMetadata(DumpMetadata metadata)
	{
		sourceName = metadata.sourceName;

		startOffset = metadata.romStart;
		endOffset = metadata.romEnd;

		setAddressRange(metadata.ramStart, metadata.ramEnd, metadata.ramLimit);
		setOffsetRange(metadata.romStart, metadata.romEnd);
	}

	@Override
	public String getSourceName()
	{
		return sourceName;
	}

	public BaseWorldDecoder()
	{
		super(LibScope.World, NpcT, ProjectDatabase.rom.getLibrary(LibScope.World));
	}

	@Override
	public void scanScript(Pointer ptr, ByteBuffer fileBuffer)
	{
		Script.scan(this, ptr, fileBuffer);
		int endPosition = fileBuffer.position();

		for (ScriptLine line : ptr.script) {
			if (line.cmd == Script.Command.CALL)
				scanFunctionCall(ptr, fileBuffer, line);
		}

		for (ScriptLine line : ptr.script) {
			switch (line.cmd) {
				case SET_INT: // impossible to tell what the local pointer is from this command
					if (isLocalAddress(line.args[1])) {
						//XXX necessary? shouldnt these be automatically found during the initial scan?
						Pointer childPtr = getPointer(line.args[1]);
						ptr.addUniqueChild(childPtr);
					}
					break;

				case SET_BUFFER:
					if (isLocalAddress(line.args[0])) {
						addPointer(line.args[0]);
						enqueueAsChild(ptr, line.args[0], IntTableT);
					}
					break;

				case SET_FBUFFER:
					if (isLocalAddress(line.args[0])) {
						addPointer(line.args[0]);
						enqueueAsChild(ptr, line.args[0], FloatTableT);
					}
					break;

				case EXEC1:
				case EXEC2:
				case EXEC_WAIT:
					if (isLocalAddress(line.args[0])) {
						enqueueAsChild(ptr, line.args[0], ScriptT);
					}
					else {
						switch (line.args[0]) {
							case 0x80285960: // PREVIOUS line sets TEMP[0] to LoadExits script!
								assert (line.lineNum > 0);
								ScriptLine prevLine = ptr.script.get(line.lineNum - 1);
								assert (prevLine.cmd == Script.Command.SET_INT);
								enqueueAsChild(ptr, prevLine.args[1], ScriptT);
								break;

							default: // do nothing
						}
					}
					break;

				case TRIGGER:
				case LOCK:
					scanTrigger(ptr, fileBuffer, line);
					break;

				default:
					break;
			}
		}

		fileBuffer.position(endPosition);
	}

	private void scanTrigger(Pointer ptr, ByteBuffer fileBuffer, ScriptLine line)
	{
		int scriptAddr = line.args[0];
		Pointer scriptPtr = tryEnqueueAsChild(ptr, scriptAddr, ScriptT);

		if (line.args[3] == 0x00100000)
			tryEnqueueAsChild(ptr, line.args[2], TriggerCoordT);

		if (line.args.length == 8)
			tryEnqueueAsChild(ptr, line.args[3], ItemListT);

		if (MapScriptTemplate.SHAKE_TREE.matches(this, fileBuffer, toOffset(scriptAddr))) {
			scriptPtr.setDescriptor(MapScriptTemplate.SHAKE_TREE.getName());

			assert (line.lineNum > 0);
			ScriptLine prevLine = ptr.script.get(line.lineNum - 1);

			if (prevLine.cmd == Script.Command.SET_INT) {
				assert (line.args[1] == 0x00001000 || line.args[1] == 0x00100000);
				tryEnqueueAsChild(ptr, prevLine.args[1], ShakeTreeEventT);
			}
		}
		else if (MapScriptTemplate.SEARCH_BUSH.matches(this, fileBuffer, toOffset(scriptAddr))) {
			scriptPtr.setDescriptor(MapScriptTemplate.SEARCH_BUSH.getName());

			assert (line.lineNum > 0);
			ScriptLine prevLine = ptr.script.get(line.lineNum - 1);

			if (prevLine.cmd == Script.Command.SET_INT) {
				assert (line.args[1] == 0x00000100);
				tryEnqueueAsChild(ptr, prevLine.args[1], SearchBushEventT);
			}
		}
	}

	private void scanFunctionCall(Pointer scriptPtr, ByteBuffer fileBuffer, ScriptLine line)
	{
		int funcAddress = line.args[0];
		int nargs = line.args.length - 1;

		// local function calls
		if (isLocalAddress(funcAddress)) {
			enqueueAsChild(scriptPtr, funcAddress, FunctionT).isAPI = true;

			// try to find AISettings scripts
			if (scriptPtr.getDescriptor().equals("NpcAI") && nargs > 0) {
				if (isLocalAddress(line.args[1]) && getPointer(line.args[1]).isTypeUnknown())
					enqueueAsChild(scriptPtr, line.args[1], AISettingsT);
			}

			scanUnknownArguments(scriptPtr, line);
		}
		// library function calls
		else {
			FunctionCallTracker.addCall(funcAddress);

			LibEntry entry = library.get(funcAddress);
			if (entry != null && !entry.isFunction())
				throw new StarRodException("Invalid call to %08X, this address is registered as %s in library.",
					funcAddress, entry.type);

			// functions not in the library that have important pointers
			switch (funcAddress) {
				case 0x80111D38: // MakeEntity
					scanEntityArgs(scriptPtr, line);
					return;

				case 0x802C94A0:
					tryEnqueueAsChild(scriptPtr, line.args[2], FunctionT);
					tryEnqueueAsChild(scriptPtr, line.args[3], FunctionT);
					assert (entry == null) : String.format("%08X has been identified as %s, remove it from switch!", funcAddress, entry.name);
					break;

				case 0x802C9428:
					enqueueAsChild(scriptPtr, line.args[2], DisplayListT);
					assert (entry == null) : String.format("%08X has been identified as %s, remove it from switch!", funcAddress, entry.name);
					break;
			}

			// function not found in library
			if (entry == null) {
				scanUnknownArguments(scriptPtr, line);
				return;
			}

			LibParamList params = entry.getMatchingParams(nargs);

			if (params == null) {
				throw new RuntimeException(String.format("Call to %08X in %s does not match library signature: argc = %d",
					funcAddress, getSourceName(), nargs));
			}

			// function is named, but list is either empty, unknown, varargs
			if (params.listType != ParamListType.Normal) {
				scanUnknownArguments(scriptPtr, line);
				return;
			}

			// scan pointers defined in the library, make sure there aren't any missing from the library
			int i = 0;
			for (LibParam param : params) {
				int v = line.args[1 + i];

				if (param.typeInfo.category == ParamCategory.StaticStruct) {
					Pointer ptr = tryEnqueueAsChild(scriptPtr, v, param.typeInfo.staticType);
					if (ptr != null) {
						String ptrDesc = param.suffix;
						if (ptrDesc != null)
							ptr.setDescriptor(ptrDesc);

						int arrayLen = param.arrayLen;
						if (arrayLen != 0) {
							if (arrayLen < 0)
								ptr.listLength = line.args[-arrayLen + 1];
							else
								ptr.listLength = arrayLen;

							if (ptr.listLength > 128 || ptr.listLength < 1)
								throw new RuntimeException(String.format(
									"Invalid list length for function: %s (%d)", entry.name, ptr.listLength));
						}
					}
				}
				else if (isLocalAddress(v)) {
					Pointer childPtr = getPointer(v);
					if (childPtr.isTypeUnknown())
						enqueueAsChild(scriptPtr, v, UnknownT);
				}

				i++;
			}
		}
	}

	private void scanUnknownArguments(Pointer scriptPtr, ScriptLine line)
	{
		// unknown pointers should still be considered children of this script
		for (int i = 1; i < line.args.length; i++) {
			int v = line.args[i];
			if (isLocalAddress(v)) {
				Pointer childPtr = getPointer(v);
				if (childPtr.isTypeUnknown())
					enqueueAsChild(scriptPtr, v, UnknownT); //XXX what is this for?
			}
		}
	}

	private void scanEntityArgs(Pointer ptr, ScriptLine line)
	{
		// @formatter:off
		switch(line.args[1])
		{
			case 0x802BC788: break; // CymbalPlant
			case 0x802BC7AC: break; // PinkFlower
			case 0x802BC7F4: break; // SpinningFlower
			case 0x802BCA74:
				enqueueAsChild(ptr, line.args[6], TweesterPathListT);
				break; // Tweester
			case 0x802BCB44: break; // StarBoxLaucher
			case 0x802BCBD8: break; // BellbellPlant
			case 0x802BCBFC: break; // TrumpetPlant
			case 0x802BCC20: break; // SpongeyFlower
			case 0x802BCD68: break; // DoorController
			case 0x802BCD9C: break; // RedArrowSigns
			case 0x802BCE84: break; // Boarded-UpFloor
			case 0x802BCF00: break; // BombableRock
			case 0x802BCF24: break; // BombableRock(2)
			case 0x802E9A18: break; // SavePoint
			case 0x802E9BB0: break; // RedSwitch
			case 0x802E9BD4: break; // BlueSwitch
			case 0x802E9BF8: break; // HugeBlueSwitch
			case 0x802E9C1C: break; // GreenStompSwitch
			case 0x802EA07C: break; // CounterBlock
			case 0x802EA0C4: break; // BrickBlock
			case 0x802EA0E8: break; // Multi-CoinBrick
			case 0x802EA10C: break; // Hammer1Block
			case 0x802EA130: break; // Hammer1Block(2)
			case 0x802EA154: break; // Hammer1Block(3)
			case 0x802EA178: break; // 802EA178
			case 0x802EA19C: break; // Hammer2Block
			case 0x802EA1C0: break; // Hammer2Block(2)
			case 0x802EA1E4: break; // Hammer2Block(3)
			case 0x802EA208: break; // 802EA208
			case 0x802EA22C: break; // Hammer3Block
			case 0x802EA274: break; // Hammer3Block(2)
			case 0x802EA298: break; // 802EA298
			case 0x802EA2E0: break; // 802EA2E0
			case 0x802EA564: break; // YellowBlock
			case 0x802EA588: break; // HiddenYellowBlock
			case 0x802EA5AC: break; // RedBlock
			case 0x802EA5D0: break; // HiddenRedBlock
			case 0x802EA5F4: break; // 802EA5F4
			case 0x802EA7E0: break; // HealingBlock
			case 0x802EA910: break; // SuperBlock
			case 0x802EAA30: break; // Spring
			case 0x802EAA54: break; // Spring(2)
			case 0x802EAB04: break; // HiddenPanel
			case 0x802EAE0C: break; // GiantChest
			case 0x802EAE30: break; // Chest
			case 0x802EAF80: 		// BlueWarpPipe;
				enqueueAsChild(ptr, line.args[7], ScriptT);
				break;
			case 0x802EAED4: break; // WoodenCrate
			case 0x802EAFDC: break; // Signpost
			default:
				throw new RuntimeException(String.format("Encountered unknown entity type %08X", line.args[1]));
		}
		// @formatter:on
	}

	@Override
	public String printFunctionArgs(Pointer ptr, PrintWriter pw, ScriptLine line, int lineAddress)
	{
		// overrides
		switch (line.args[0]) {
			case 0x80111D38: // MakeEntity
				printEntityArgs(ptr, pw, line, lineAddress);
				return "";

			case 0x802D829C: // PlayEffect
				int effectTypeA = (line.args[1] << 16) | (line.args[2] & 0xFFFF);
				int effectTypeB = (line.args[1] << 16) | 0xFFFF; // single argument
				EffectTypeTracker.addEffect(effectTypeA, getSourceName());
				if (ProjectDatabase.EffectType.contains(effectTypeA)) {
					String effectName = ProjectDatabase.EffectType.get(effectTypeA);
					pw.printf("%cFX:%s ", SyntaxConstants.EXPRESSION_PREFIX, effectName);
					for (int i = 3; i < line.args.length; i++)
						printScriptWord(pw, ptr, line.types[i], line.args[i]);
				}
				else if (ProjectDatabase.EffectType.contains(effectTypeB)) {
					String effectName = ProjectDatabase.EffectType.get(effectTypeB);
					pw.printf("%cFX:%s ", SyntaxConstants.EXPRESSION_PREFIX, effectName);
					for (int i = 2; i < line.args.length; i++)
						printScriptWord(pw, ptr, line.types[i], line.args[i]);
				}
				else {
					for (int i = 1; i < line.args.length; i++)
						printScriptWord(pw, ptr, line.types[i], line.args[i]);
				}
				return "";
		}

		return super.printFunctionArgs(ptr, pw, line, lineAddress);
	}

	//XXX
	private void printEntityArgs(Pointer ptr, PrintWriter pw, ScriptLine line, int lineAddress)
	{
		String entityName = ProjectDatabase.EntityType.getConstantString(line.args[1]);
		String markerName = String.format("Entity%X", lineAddress);
		pw.print(entityName + " ");

		int x = line.args[2];
		int y = line.args[3];
		int z = line.args[4];
		int a = line.args[5];

		pw.print("{Vec4d:" + markerName + "} ");

		switch (line.args[1]) {
			case 0x802EA564: // YellowBlock
			case 0x802EA588: // HiddenYellowBlock
			case 0x802EA5AC: // RedBlock
			case 0x802EA5D0: // HiddenRedBlock
			case 0x802EAED4: // WoodenCrate
				pw.print(ProjectDatabase.getItemConstant(line.args[6]) + " ");
				for (int i = 7; i < line.args.length; i++)
					printScriptWord(pw, ptr, line.types[i], line.args[i]);
				break;
			case 0x802EAB04: // HiddenPanel
				printModelID(ptr, pw, line.args[6]);
				for (int i = 7; i < line.args.length; i++)
					printScriptWord(pw, ptr, line.types[i], line.args[i]);
				break;
			/*
			case 0x802BC788: break; // CymbalPlant
			case 0x802BC7AC: break; // PinkFlower
			case 0x802BC7F4: break; // SpinningFlower
			case 0x802BCA74: break; // Tweester
			case 0x802BCB44: break; // StarBoxLaucher
			case 0x802BCBD8: break; // BellbellPlant
			case 0x802BCBFC: break; // TrumpetPlant
			case 0x802BCC20: break; // SpongeyFlower
			case 0x802BCD68: break; // DoorController
			case 0x802BCD9C: break; // RedArrowSigns
			case 0x802BCE84: break; // Boarded-UpFloor
			case 0x802BCF00: break; // BombableRock
			case 0x802BCF24: break; // BombableRock(2)
			case 0x802E9A18: break; // SavePoint
			case 0x802E9BB0: break; // RedSwitch
			case 0x802E9BD4: break; // BlueSwitch
			case 0x802E9BF8: break; // HugeBlueSwitch
			case 0x802E9C1C: break; // GreenStompSwitch
			case 0x802EA07C: break; // CounterBlock
			case 0x802EA0C4: break; // BrickBlock
			case 0x802EA0E8: break; // Multi-CoinBrick
			case 0x802EA10C: break; // Hammer1Block
			case 0x802EA130: break; // Hammer1Block(2)
			case 0x802EA154: break; // Hammer1Block(3)
			case 0x802EA178: break; // 802EA178
			case 0x802EA19C: break; // Hammer2Block
			case 0x802EA1C0: break; // Hammer2Block(2)
			case 0x802EA1E4: break; // Hammer2Block(3)
			case 0x802EA208: break; // 802EA208
			case 0x802EA22C: break; // Hammer3Block
			case 0x802EA274: break; // Hammer3Block(2)
			case 0x802EA298: break; // 802EA298
			case 0x802EA2E0: break; // 802EA2E0
			case 0x802EA5F4: break; // 802EA5F4
			case 0x802EA7E0: break; // HealingBlock
			case 0x802EA910: break; // SuperBlock
			case 0x802EAA30: break; // Spring
			case 0x802EAA54: break; // Spring(2)
			case 0x802EAB04: break; // HiddenPanel
			case 0x802EAE0C: break; // GiantChest
			case 0x802EAE30: break; // Chest
			case 0x802EAF80: break;
			case 0x802EAFDC: break; // Signpost
			*/

			default:
				for (int i = 6; i < line.args.length; i++)
					printScriptWord(pw, ptr, line.types[i], line.args[i]);
		}
	}
}
