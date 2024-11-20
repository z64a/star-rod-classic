package game.globals;

import static app.Directories.*;
import static game.globals.MoveRecordKey.TAG_MOVE;
import static game.globals.MoveRecordKey.TAG_ROOT;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

import org.w3c.dom.Element;

import app.Environment;
import app.StarRodException;
import app.input.IOUtils;
import app.input.InputFileException;
import app.input.InvalidInputException;
import game.ROM.EOffset;
import game.globals.editor.GlobalsData;
import game.shared.ProjectDatabase;
import game.shared.ProjectDatabase.ConstEnum.EnumPair;
import game.shared.encoder.GlobalPatchManager;
import patcher.IGlobalDatabase;
import patcher.RomPatcher;
import util.Logger;
import util.Priority;
import util.xml.XmlWrapper.XmlReader;
import util.xml.XmlWrapper.XmlTag;
import util.xml.XmlWrapper.XmlWriter;

public class MoveModder
{
	public static final int NUM_MOVES = 0xB9;
	// 6A460 + 14 * B9

	public static ArrayList<MoveRecord> dumpTable() throws IOException
	{
		Logger.log("Dumping move table.", Priority.MILESTONE);

		String[] moveNames = getMoveNames();

		ArrayList<MoveRecord> moves = new ArrayList<>();

		RandomAccessFile raf = Environment.getBaseRomReader();
		raf.seek(ProjectDatabase.rom.getOffset(EOffset.MOVE_TABLE));
		for (int i = 0; i < NUM_MOVES; i++) {
			MoveRecord move = MoveRecord.read(i, raf);
			move.identifier = moveNames[i];

			move.abilityName = getAbilityName(move.listIndex);
			if (move.abilityName == null)
				move.abilityName = "";

			moves.add(move);
		}
		raf.close();

		writeXML(moves, new File(DUMP_GLOBALS + FN_MOVES));
		return moves;
	}

	private static String[] getMoveNames() throws IOException
	{
		File f = new File(DATABASE + "default_move_names.txt");
		String[] moveNames = new String[NUM_MOVES];

		List<String> lines = IOUtils.readFormattedTextFile(f);
		for (int i = 0; i < lines.size(); i++) {
			String line = lines.get(i);

			if (line.isEmpty())
				continue;

			String[] tokens = IOUtils.getKeyValuePair(f, line, i);

			assert (moveNames[i] == null) : tokens[1];
			assert (i == Integer.parseInt(tokens[0], 16)) : tokens[0];
			moveNames[i] = tokens[1];
		}

		for (int i = 0; i < moveNames.length; i++)
			assert (moveNames[i] != null) : String.format("%02X", i);

		return moveNames;
	}

	public static void patchTable(RomPatcher rp, IGlobalDatabase db, GlobalPatchManager gpm, GlobalsData globals, boolean addingPartner10) throws IOException
	{
		int moveTableBase = ProjectDatabase.rom.getOffset(EOffset.MOVE_TABLE);

		int numMoves = globals.moves.size();
		if (numMoves > 255)
			throw new StarRodException("Too many moves: %d (only 255 supported)", numMoves);

		int numPartners = addingPartner10 ? 10 : 9;
		int minMoveCount = NUM_MOVES + (addingPartner10 ? 6 : 0);

		if (numMoves < NUM_MOVES)
			throw new StarRodException("Not enough moves: %d (need %d to support %d partners)", numMoves, minMoveCount, numPartners);

		HashMap<String, Integer> abilityMoveMap = new HashMap<>();

		if (ProjectDatabase.AbilityType.getNumDefined() < 0x10)
			throw new StarRodException("Not enough abilities are defined!");

		String afxAbilityName = ProjectDatabase.AbilityType.getName(0xF);
		List<Integer> afxMoveList = new LinkedList<>();

		ByteBuffer bb = ByteBuffer.allocate(0x14 * 256);
		for (int i = 0; i < 256; i++) {
			bb.putInt(-1);
			bb.putInt(-1);
			bb.putInt(-1);
			bb.putInt(-1);
			bb.putInt(-1);
		}
		bb.rewind();
		for (MoveRecord move : globals.moves) {
			if (move.abilityName != null && !move.abilityName.isEmpty()) {
				if (move.abilityName.equals(afxAbilityName))
					afxMoveList.add(move.listIndex);
				else if (!abilityMoveMap.containsKey(move.abilityName))
					abilityMoveMap.put(move.abilityName, move.listIndex);
				else
					throw new StarRodException("Move %02X is invalid. %nAbility %s is already assigned to move %X.",
						move.listIndex, move.abilityName, abilityMoveMap.get(move.abilityName));
			}

			try {
				move.put(bb, db);
			}
			catch (InvalidInputException e) {
				throw new StarRodException(e);
			}
		}
		rp.seek("Move Property Table", moveTableBase);
		rp.write(bb.array());

		StringBuilder basicCasesBuilder = new StringBuilder();
		StringBuilder jumpTableBuilder = new StringBuilder();

		for (EnumPair pair : ProjectDatabase.AbilityType.getDecoding()) {
			String caseLabel = String.format(".Case_%02X", pair.key);
			boolean makeAutoCase = true;
			boolean hasTarget = true;

			Integer moveID = abilityMoveMap.get(pair.value);
			if (moveID == null) {
				// no move is bound
				makeAutoCase = false;
				hasTarget = false;
			}

			// do not generate cases for attackFX or mysteryScroll
			if (pair.key == 0xF || pair.key == 0x6) {
				makeAutoCase = false;
				hasTarget = true;
			}

			if (makeAutoCase) {
				basicCasesBuilder.append("\t" + caseLabel + "\n");
				basicCasesBuilder.append("\tB     .CheckBadgeID\n");
				basicCasesBuilder.append("\tLI    V0, " + String.format("%X", moveID) + "\n");
			}

			if (hasTarget)
				jumpTableBuilder.append("\t$AutoIsAbilityActive[" + caseLabel + "]\n");
			else
				jumpTableBuilder.append("\t0\n");
		}

		StringBuilder attackFXCasesBuilder = new StringBuilder();

		int afxCounter = 1;
		for (int i = 0; i < afxMoveList.size(); i++) {
			int moveID = afxMoveList.get(i);
			MoveRecord moveRec = globals.moves.get(moveID);

			String nextTarget;
			if (i + 1 == afxMoveList.size())
				nextTarget = ".CheckLoopEnd";
			else
				nextTarget = ".Check_" + (afxCounter + 1);

			attackFXCasesBuilder.append("\t.Check_" + afxCounter + "\n");
			attackFXCasesBuilder.append("\tLI    V0, " + moveRec.listIndex + "`\n");
			attackFXCasesBuilder.append("\tBNE   *CurID, V0, " + nextTarget + "\n");
			attackFXCasesBuilder.append("\tNOP\n");
			attackFXCasesBuilder.append("\tB     .StoreFXMove\n");
			attackFXCasesBuilder.append("\tLI    V0, " + afxCounter + "`\n");

			afxCounter++;
		}

		gpm.readInternalPatch("IsAbilityActive.patch",
			"LargestAbilityID=" + ProjectDatabase.AbilityType.getMaxDefinedValue() + "`",
			"AttackFXArraySize=" + 4 * afxMoveList.size() + "`",
			"BasicCases=" + basicCasesBuilder.toString(),
			"AttackFXCases=" + attackFXCasesBuilder.toString(),
			"JumpTable=" + jumpTableBuilder.toString());
	}

	public static List<MoveRecord> loadMoves(boolean fromProject) throws IOException
	{
		return readXML(new File((fromProject ? MOD_GLOBALS : DUMP_GLOBALS) + FN_MOVES));
	}

	public static void saveMoves(List<MoveRecord> moves) throws IOException
	{
		writeXML(moves, new File(MOD_GLOBALS + FN_MOVES));
	}

	private static void writeXML(List<MoveRecord> moves, File itemTableFile) throws IOException
	{
		try (XmlWriter xmw = new XmlWriter(itemTableFile)) {
			XmlTag rootTag = xmw.createTag(TAG_ROOT, false);
			xmw.openTag(rootTag);

			int i = 0;
			for (MoveRecord item : moves)
				item.writeXML(xmw, i++);

			xmw.closeTag(rootTag);
			xmw.save();
		}
	}

	private static List<MoveRecord> readXML(File moveTableFile) throws IOException
	{
		List<MoveRecord> moves = new ArrayList<>();
		XmlReader xmr = new XmlReader(moveTableFile);
		List<Element> moveElements = xmr.getTags(xmr.getRootElement(), TAG_MOVE);
		for (int i = 0; i < moveElements.size(); i++)
			moves.add(MoveRecord.readXML(xmr, moveElements.get(i), i));
		return moves;
	}

	@Deprecated
	public static List<MoveRecord> readCSV(File moveTableFile) throws IOException
	{
		List<MoveRecord> moves = new LinkedList<>();

		BufferedReader in = new BufferedReader(new FileReader(moveTableFile));
		String line = in.readLine(); // skip title line

		try {
			int i = 0;
			while ((line = in.readLine()) != null) {
				if (line.isEmpty())
					continue;

				moves.add(MoveRecord.load(i, line));
				i++;
			}
			in.close();
		}
		catch (StarRodException e) {
			throw new InputFileException(moveTableFile, e.getMessage());
		}

		return moves;
	}

	private static final HashMap<Integer, Integer> defaultAbilityMap = new HashMap<>();

	static {
		defaultAbilityMap.put(0x4C, 0x00); // Dodge Master
		defaultAbilityMap.put(0x01, 0x01); // null
		defaultAbilityMap.put(0x40, 0x02); // Spike Shield
		defaultAbilityMap.put(0x4D, 0x03); // First Attack
		defaultAbilityMap.put(0x52, 0x04); // HP Plus
		defaultAbilityMap.put(0x35, 0x05); // Double Dip
		defaultAbilityMap.put(0x53, 0x06); // Mystery Scroll
		defaultAbilityMap.put(0x41, 0x07); // Fire Shield
		defaultAbilityMap.put(0x42, 0x08); // Pretty Lucky
		defaultAbilityMap.put(0x5A, 0x09); // HP Drain
		defaultAbilityMap.put(0x3C, 0x0A); // All or Nothing
		defaultAbilityMap.put(0x4E, 0x0B); // Slow Go
		defaultAbilityMap.put(0x5B, 0x0C); // FP Plus
		defaultAbilityMap.put(0x3D, 0x0D); // Ice Power
		defaultAbilityMap.put(0x43, 0x0E); // Feeling Fine
		defaultAbilityMap.put(0x54, 0x0F); // Attack FX A
		defaultAbilityMap.put(0x55, 0x0F); // Attack FX A
		defaultAbilityMap.put(0x56, 0x0F); // Attack FX A
		defaultAbilityMap.put(0x57, 0x0F); // Attack FX A
		defaultAbilityMap.put(0x58, 0x0F); // Attack FX A
		defaultAbilityMap.put(0x59, 0x0F); // Attack FX A
		defaultAbilityMap.put(0x5C, 0x10); // Money Money
		defaultAbilityMap.put(0x5D, 0x11); // Chill Out
		defaultAbilityMap.put(0x5E, 0x12); // Happy Heart
		defaultAbilityMap.put(0x44, 0x13); // Zap Tap
		defaultAbilityMap.put(0x5F, 0x14); // Mega Rush
		defaultAbilityMap.put(0x60, 0x15); // Berserker
		defaultAbilityMap.put(0x4F, 0x16); // Right On!
		defaultAbilityMap.put(0x61, 0x17); // Runaway Pay
		defaultAbilityMap.put(0x62, 0x18); // Flower Saver
		defaultAbilityMap.put(0x63, 0x19); // Pay-Off
		defaultAbilityMap.put(0x38, 0x1A); // Quick Change
		defaultAbilityMap.put(0x45, 0x1B); // Defend Plus
		defaultAbilityMap.put(0x3B, 0x1C); // Power Plus
		defaultAbilityMap.put(0x6E, 0x1D); // Refund
		defaultAbilityMap.put(0x64, 0x1E); // Power Rush
		defaultAbilityMap.put(0x65, 0x1F); // Crazy Heart
		defaultAbilityMap.put(0x46, 0x20); // Last Stand
		defaultAbilityMap.put(0x47, 0x21); // Close Call
		defaultAbilityMap.put(0x3E, 0x22); // P-Up & D-Down
		defaultAbilityMap.put(0x48, 0x23); // Lucky Day
		defaultAbilityMap.put(0x66, 0x24); // Mega HP Drain
		defaultAbilityMap.put(0x49, 0x25); // P-Down & D-Up
		defaultAbilityMap.put(0x67, 0x26); // Flower Fanatic
		defaultAbilityMap.put(0x6D, 0x27); // Speedy Spin
		defaultAbilityMap.put(0x6A, 0x28); // Spin Attack
		defaultAbilityMap.put(0x6C, 0x29); // I Spy
		defaultAbilityMap.put(0x50, 0x2A); // Bump Attack
		defaultAbilityMap.put(0x68, 0x2B); // Heart Finder
		defaultAbilityMap.put(0x69, 0x2C); // Flower Finder
		defaultAbilityMap.put(0x6B, 0x2D); // Dizzy Attack
		defaultAbilityMap.put(0x6F, 0x2E); // ~Final Old Man Goomba
		defaultAbilityMap.put(0x70, 0x2F); // ~Final Bomb-omb
		defaultAbilityMap.put(0x71, 0x30); // Deep Focus
		defaultAbilityMap.put(0x72, 0x31); // Super Focus
		defaultAbilityMap.put(0x73, 0x32); // Kaiden
		defaultAbilityMap.put(0x33, 0x33); // Damage Dodge
		defaultAbilityMap.put(0x74, 0x34); // Happy Flower
		defaultAbilityMap.put(0x75, 0x35); // Group Focus
		defaultAbilityMap.put(0x76, 0x36); // Peekaboo
		defaultAbilityMap.put(0x4A, 0x37); // Healthy Healthy
	}

	public static int getAbilityID(int moveID)
	{
		Integer abilityID = defaultAbilityMap.get(moveID);

		if (abilityID == null)
			return -1;

		return abilityID;
	}

	public static String getAbilityName(int moveID)
	{
		Integer abilityID = defaultAbilityMap.get(moveID);

		if (abilityID == null)
			return null;

		return ProjectDatabase.AbilityType.getName(abilityID);
	}
}
