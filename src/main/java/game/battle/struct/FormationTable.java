package game.battle.struct;

import static game.shared.StructTypes.*;

import java.io.PrintWriter;
import java.nio.ByteBuffer;

import game.battle.BaseBattleDecoder;
import game.shared.BaseStruct;
import game.shared.decoder.BaseDataDecoder;
import game.shared.decoder.Pointer;
import reports.BattleMapTracker;

public class FormationTable extends BaseStruct
{
	public static final FormationTable instance = new FormationTable();

	private FormationTable()
	{}

	@Override
	public void scan(BaseDataDecoder decoder, Pointer ptr, ByteBuffer fileBuffer)
	{
		int formationIndex = 0;

		int v;
		while ((v = fileBuffer.getInt()) != 0) {
			int ptrSJIS = v;
			decoder.enqueueAsChild(ptr, ptrSJIS, SjisT);

			int numEnemies = fileBuffer.getInt();
			int ptrFormation = fileBuffer.getInt();
			Pointer formationInfo = decoder.enqueueAsChild(ptr, ptrFormation, IndexedFormationT);
			formationInfo.listIndex = formationIndex;
			formationInfo.listLength = numEnemies;

			int ptrResourcesList = fileBuffer.getInt();
			decoder.enqueueAsChild(ptr, ptrResourcesList, StageT);

			// script found only for section 0x27
			int ptrUnknownScript = fileBuffer.getInt();
			if (ptrUnknownScript != 0)
				decoder.enqueueAsChild(ptr, ptrUnknownScript, ScriptT);

			formationIndex++;
		}

		fileBuffer.position(fileBuffer.position() + 0x10); // battle tables are always terminated with 0x14 zero bytes
	}

	@Override
	public void print(BaseDataDecoder decoder, Pointer ptr, ByteBuffer fileBuffer, PrintWriter pw)
	{
		for (int i = 0; i < ptr.getSize() / 20; i++) {
			for (int j = 0; j < 5; j++) {
				int v = fileBuffer.getInt();
				decoder.printWord(pw, v);
			}

			if (decoder instanceof BaseBattleDecoder) {
				int currentSection = ((BaseBattleDecoder) decoder).getSectionID();
				assert (currentSection >= 0);
				int battleID = (currentSection << 24) | (i << 16);
				if (BattleMapTracker.isEnabled() && !BattleMapTracker.hasBattleID(battleID))
					pw.print("% unused");
			}

			pw.println();
		}
	}
}
