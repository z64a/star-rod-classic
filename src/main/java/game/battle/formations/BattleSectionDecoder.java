package game.battle.formations;

import static app.Directories.*;
import static game.battle.BattleConstants.BATTLE_RAM_LIMIT;
import static game.battle.BattleConstants.SECTION_NAMES;
import static game.shared.StructTypes.*;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map.Entry;

import app.input.IOUtils;
import game.battle.BaseBattleDecoder;
import game.map.MapIndex;
import game.shared.SyntaxConstants;
import game.shared.decoder.Pointer;
import game.shared.decoder.Pointer.Origin;
import util.Logger;

public class BattleSectionDecoder extends BaseBattleDecoder
{
	private final int sectionID;
	private final List<Pointer> enemyList;
	private final HashMap<String, MapIndex> stageIndexLookup;

	@Override
	public int getSectionID()
	{
		return sectionID;
	}

	public BattleSectionDecoder(ByteBuffer fileBuffer, int section, HashMap<String, MapIndex> stageIndexLookup) throws IOException
	{
		super();

		this.stageIndexLookup = stageIndexLookup;
		sectionID = section;
		sourceName = SECTION_NAMES[section];
		readTableEntry(fileBuffer, section);

		enemyList = new LinkedList<>();
		super.decode(fileBuffer);

		File scriptFile = new File(DUMP_FORMA_SRC + sourceName + ".bscr");
		File indexFile = new File(DUMP_FORMA_SRC + sourceName + ".bidx");
		File rawFile = new File(DUMP_FORMA_RAW + sourceName + ".bin");

		printScriptFile(scriptFile, fileBuffer);
		printIndexFile(indexFile);
		writeRawFile(rawFile, fileBuffer);

		printEnemyFiles(fileBuffer);
	}

	private void readTableEntry(ByteBuffer fileBuffer, int section)
	{
		fileBuffer.position(0x70E30 + section * 0x20);

		fileBuffer.getInt(); // SJIS name
		int startOffset = fileBuffer.getInt();
		int endOffset = fileBuffer.getInt();
		int startAddress = fileBuffer.getInt();
		int ptrBattleTable = fileBuffer.getInt();
		int ptrMapTable = fileBuffer.getInt();
		fileBuffer.getInt(); // always zero
		int ptrDmaTable = fileBuffer.getInt();

		int endAddress = (endOffset - startOffset) + startAddress;
		setAddressRange(startAddress, endAddress, BATTLE_RAM_LIMIT);
		setOffsetRange(startOffset, endOffset);

		// scan for local pointers in the battle section
		findLocalPointers(fileBuffer);

		tryEnqueueAsRoot(ptrBattleTable, FormationTableT, Origin.DECODED);
		tryEnqueueAsRoot(ptrMapTable, StageTableT, Origin.DECODED);
		tryEnqueueAsRoot(ptrDmaTable, DmaArgTableT, Origin.DECODED);
	}

	private void printEnemyFiles(ByteBuffer fileBuffer) throws IOException
	{
		ArrayList<Integer> pointerList = getSortedLocalPointerList();

		for (int i : pointerList) {
			Pointer ptr = localPointerMap.get(i);
			ptr.setImportAffix(String.format("%02X", sectionID));
		}

		for (Entry<Integer, Pointer> e : localPointerMap.entrySet()) {
			Pointer ptr = e.getValue();
			if (ptr.getType() == ActorT)
				enemyList.add(ptr);
		}

		for (Pointer enemy : enemyList) {
			String enemyName = enemy.getImportName();
			File f = new File(DUMP_FORMA_ENEMY + enemyName + ".bpat");

			PrintWriter pw = IOUtils.getBufferedPrintWriter(f);

			pw.printf("%% Automatically dumped from section %02X%n", sectionID);
			pw.println();

			pw.println("#new:" + ActorT.toString() + " $" + enemyName + " {");
			printPointer(enemy, fileBuffer, enemy.address, pw);
			pw.println("}");
			pw.println();

			for (int address : pointerList) {
				Pointer pointer = localPointerMap.get(address);
				if (pointer.ancestors.contains(enemy)) {
					pw.println("#new:" + pointer.getType().toString() + " " + pointer.getPointerName() + " {");
					printPointer(pointer, fileBuffer, address, pw);
					pw.println("}");
					pw.println();
				}
			}
			pw.close();
		}
	}

	@Override
	public void printModelID(Pointer ptr, PrintWriter pw, int id)
	{
		while (true) {
			if (id < 0 || id > 256) // don't try to lookup IDs for script variables
				break;

			if (ptr.mapName == null || ptr.mapName.isEmpty())
				break;

			String stageName = ptr.mapName;
			MapIndex index = stageIndexLookup.get(stageName);
			if (index == null) {
				Logger.logfWarning("Can't find stage %s", id, stageName);
				break;
			}

			String name = index.getModelName(id);
			if (name == null) {
				Logger.logfWarning("Can't find model %X for stage %s", id, stageName);
				break;
			}

			pw.printf("%cModel:%s:%s ", SyntaxConstants.EXPRESSION_PREFIX, stageName, name);
			return;
		}

		super.printModelID(ptr, pw, id);
		return;
	}

	@Override
	public void printColliderID(Pointer ptr, PrintWriter pw, int id)
	{
		while (true) {
			if (id < 0 || id > 256) // don't try to lookup IDs for script variables
				break;

			if (ptr.mapName == null || ptr.mapName.isEmpty())
				break;

			String stageName = ptr.mapName;
			MapIndex index = stageIndexLookup.get(stageName);
			if (index == null) {
				Logger.logfWarning("Can't find stage %s", id, stageName);
				break;
			}

			String name = index.getColliderName(id);
			if (name == null) {
				Logger.logfWarning("Can't find collider %X for stage %s", id, stageName);
				break;
			}

			pw.printf("%cCollider:%s:%s ", SyntaxConstants.EXPRESSION_PREFIX, stageName, name);
			return;
		}

		super.printColliderID(ptr, pw, id);
		return;
	}

	@Override
	public void printZoneID(Pointer ptr, PrintWriter pw, int id)
	{
		while (true) {
			if (id < 0 || id > 256) // don't try to lookup IDs for script variables
				break;

			if (ptr.mapName == null || ptr.mapName.isEmpty())
				break;

			String stageName = ptr.mapName;
			MapIndex index = stageIndexLookup.get(stageName);
			if (index == null) {
				Logger.logfWarning("Can't find stage %s", id, stageName);
				break;
			}

			String name = index.getZoneName(id);
			if (name == null) {
				Logger.logfWarning("Can't find zone %X for stage %s", id, stageName);
				break;
			}

			pw.printf("%cZone:%s:%s} ", SyntaxConstants.EXPRESSION_PREFIX, stageName, name);
			return;
		}

		super.printZoneID(ptr, pw, id);
		return;
	}
}
