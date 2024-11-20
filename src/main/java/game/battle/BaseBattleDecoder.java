package game.battle;

import static game.shared.StructTypes.*;

import java.io.PrintWriter;
import java.nio.ByteBuffer;

import app.StarRodException;
import game.ROM.LibScope;
import game.shared.ProjectDatabase;
import game.shared.SyntaxConstants;
import game.shared.decoder.BaseDataDecoder;
import game.shared.decoder.DumpMetadata;
import game.shared.decoder.Pointer;
import game.shared.decoder.PointerHeuristic;
import game.shared.lib.LibEntry;
import game.shared.lib.LibEntry.LibParam;
import game.shared.lib.LibEntry.LibParamList;
import game.shared.lib.LibEntry.ParamCategory;
import game.shared.lib.LibEntry.ParamListType;
import game.shared.struct.script.Script;
import game.shared.struct.script.Script.ScriptLine;
import reports.EffectTypeTracker;
import reports.FunctionCallTracker;

public abstract class BaseBattleDecoder extends BaseDataDecoder
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

	public int getSectionID()
	{
		return -1;
	}

	public BaseBattleDecoder()
	{
		super(LibScope.Battle, ActorT, ProjectDatabase.rom.getLibrary(LibScope.Battle));
	}

	@Override
	public String printFunctionArgs(Pointer ptr, PrintWriter pw, ScriptLine line, int lineAddress)
	{
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

	@Override
	public void scanScript(Pointer ptr, ByteBuffer fileBuffer)
	{
		Script.scan(this, ptr, fileBuffer);
		int endPosition = fileBuffer.position();

		//XXX do these have to be done first?
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
					break;

				case TRIGGER:
				case LOCK:
					// ignore for battles
					break;

				default:
					break;
			}
		}

		fileBuffer.position(endPosition);
	}

	private void scanFunctionCall(Pointer scriptPtr, ByteBuffer fileBuffer, ScriptLine line)
	{
		int funcAddress = line.args[0];
		int nargs = line.args.length - 1;

		// local function calls
		if (isLocalAddress(funcAddress)) {
			enqueueAsChild(scriptPtr, funcAddress, FunctionT).isAPI = true;
			scanUnknownArguments(scriptPtr, line);
		}
		// library function calls
		else {
			FunctionCallTracker.addCall(funcAddress);

			LibEntry entry = library.get(funcAddress);
			if (entry != null && !entry.isFunction())
				throw new StarRodException("Invalid call to %08X, this address is registered as %s in library.",
					funcAddress, entry.type);

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
				int v = line.args[i + 1];

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
						else if (isLocalAddress(v)) {
							Pointer childPtr = getPointer(v);
							if (childPtr.isTypeUnknown())
								enqueueAsChild(scriptPtr, v, UnknownT);
						}
					}
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
					enqueueAsChild(scriptPtr, v, UnknownT);
			}
		}
	}

	private void printEntityArgs(Pointer ptr, PrintWriter pw, ScriptLine line, int lineAddress)
	{
		String entityName = ProjectDatabase.EntityType.getConstantString(line.args[1]);
		pw.print(entityName + " ");
		for (int i = 2; i < line.args.length; i++)
			printScriptWord(pw, ptr, line.types[i], line.args[i]);
	}

	@Override
	protected int guessType(PointerHeuristic h, ByteBuffer fileBuffer)
	{
		int matches = super.guessType(h, fileBuffer);

		if (isActor(h, fileBuffer)) {
			h.structType = ActorT;
			matches++;
		}

		if (matches != 1)
			h.structType = UnknownT;

		return matches;
	}

	private boolean isActor(PointerHeuristic h, ByteBuffer fileBuffer)
	{
		fileBuffer.position(h.start);
		int length = h.getLength();
		int enemyOffset = 0;

		// right size
		if (length == 40)
			fileBuffer.getInt(); // skip flags
		else if (length != 36)
			enemyOffset = -4;
		else
			return false;

		// check name index
		if (fileBuffer.get() != 0)
			return false;
		int nameIndex = (fileBuffer.get() & 0xFF);
		if (nameIndex > 212)
			return false;

		// check max HP
		fileBuffer.get();
		if ((fileBuffer.get() & 0xFF) > 99)
			return false;

		// check state count
		if ((fileBuffer.getShort() & 0xFFFF) > 32)
			return false;
		if ((fileBuffer.getShort() & 0xFFFF) != 0)
			return false;

		// check pointers
		if (!isLocalAddress(fileBuffer.getInt()))
			return false; // state table
		if (!isLocalAddress(fileBuffer.getInt()))
			return false; // AI script
		//	if(!isSectionAddress(fileBuffer.getInt())) return false; // status table

		// probably an enemy
		h.structOffset = enemyOffset;
		return true;
	}
}
