package game.battle.editor;

import static app.Directories.*;
import static game.battle.BattleConstants.BLANK_SECTION;
import static game.battle.editor.BattleKey.*;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;

import org.w3c.dom.Element;

import app.Directories;
import app.Environment;
import app.input.IOUtils;
import app.input.InputFileException;
import game.shared.SyntaxConstants;
import util.IterableListModel;
import util.Logger;
import util.xml.XmlWrapper.XmlReader;
import util.xml.XmlWrapper.XmlSerializable;
import util.xml.XmlWrapper.XmlTag;
import util.xml.XmlWrapper.XmlWriter;

public class BattleSection implements XmlSerializable
{
	public static void main(String[] args) throws IOException
	{
		Environment.initialize();

		/*
		//	String sectionName = "00 Area KMR Part 1";
		//	String sectionName = "01 Area KMR Part 2";
		//	String sectionName = "03 Area MAC";
		//	String sectionName = "0B Area ISK Part 1";
		//	String sectionName = "0C Area ISK Part 2";
		String sectionName = "19 Area FLO2";

		File index = new File(DUMP_FORMA_SRC + sectionName + ".bidx");
		File raw = new File(DUMP_FORMA_RAW + sectionName + ".bin");

		BattleSection battle = new BattleSection(raw, index);

		XmlWriter xmw = new XmlWriter(new File(Directories.DUMP_FORMA + "test.xml"));
		battle.toXML(xmw);
		*/

		extractData();

		Environment.exit();
	}

	public static void extractData() throws IOException
	{
		File in = new File(DUMP_BATTLE + FN_BATTLE_SECTIONS);
		for (String line : IOUtils.readFormattedTextFile(in, false)) {
			if (line.equals(BLANK_SECTION))
				continue;

			String[] tokens = line.split(":");
			if (tokens.length != 2)
				throw new InputFileException(in, "Invalid line in " + FN_BATTLE_SECTIONS + ":\r\n" + line);

			int baseAddress;
			try {
				baseAddress = (int) Long.parseLong(tokens[0].trim(), 16);
			}
			catch (NumberFormatException e) {
				throw new InputFileException(in, "Invalid address in " + FN_BATTLE_SECTIONS + ":\r\n" + line);
			}

			String sectionName = tokens[1].trim();
			System.out.println("Creating battle XML for " + sectionName);

			File index = new File(DUMP_FORMA_SRC + sectionName + ".bidx");
			File raw = new File(DUMP_FORMA_RAW + sectionName + ".bin");

			BattleSection battle = new BattleSection(raw, index, baseAddress);

			XmlWriter xmw = new XmlWriter(new File(Directories.DUMP_FORMA + sectionName + ".xml"));
			battle.toXML(xmw);
			xmw.save();
		}
	}

	public IterableListModel<Formation> formations = new IterableListModel<>();
	public IterableListModel<Stage> stages = new IterableListModel<>();
	public IterableListModel<Actor> actors = new IterableListModel<>();

	private int baseAddress;

	//TODO not all stages go in stage table, nor all formations in formation table

	private transient HashMap<Integer, IndexEntry> structMap;

	public String getPointerName(String type, int address)
	{
		if (structMap.containsKey(address)) {
			String name = structMap.get(address).name;
			if (name.charAt(0) == SyntaxConstants.POINTER_PREFIX)
				name = name.substring(1);
			return name;
		}
		return String.format("%s_%08X", type, address);
	}

	public int toOffset(int address)
	{
		return address - baseAddress;
	}

	public BattleSection(File binFile, File indexFile, int baseAddress) throws IOException
	{
		this.baseAddress = baseAddress;
		structMap = loadIndexFile(indexFile);
		ByteBuffer fileBuffer = IOUtils.getDirectBuffer(binFile);

		for (IndexEntry e : getEntries(structMap, "Actor")) {
			Actor a = new Actor(this, fileBuffer, e.offset);
			a.name = getPointerName("Actor", e.address);
			actors.addElement(a);
		}

		HashMap<Integer, Stage> stageMap = new HashMap<>();
		addStages(stageMap, fileBuffer);
		addFormations(stageMap, fileBuffer);

		updateReferences();
	}

	public BattleSection(File xmlFile)
	{
		XmlReader xmr = new XmlReader(xmlFile);
		fromXML(xmr, xmr.getRootElement());
	}

	private void addStages(HashMap<Integer, Stage> stageMap, ByteBuffer fileBuffer)
	{
		for (IndexEntry e : getEntries(structMap, "Stage")) {
			Stage stage = new Stage(this, fileBuffer, e.offset);
			stage.name = stage.shapeName.substring(0, stage.shapeName.length() - 6); //getPointerName("Stage", e.address);
			stages.addElement(stage);
			stageMap.put(e.address, stage);
		}

		List<IndexEntry> stageTables = getEntries(structMap, "StageTable");
		if (stageTables.size() > 0) {
			for (int i = 0; true; i++) {
				fileBuffer.position(stageTables.get(0).offset + i * 8);
				int ptrName = fileBuffer.getInt();
				int ptrStage = fileBuffer.getInt();
				if (ptrName == 0)
					break;

				Stage s = stageMap.get(ptrStage);
				//	s.name = readString(fileBuffer, toOffset(ptrName));
				s.includeInStageTable = true;
			}
		}
	}

	private void addFormations(HashMap<Integer, Stage> stageMap, ByteBuffer fileBuffer)
	{
		HashMap<Integer, Formation> formationMap = new HashMap<>();

		for (IndexEntry e : getEntries(structMap, "Formation")) {
			Formation f = new Formation(this, fileBuffer, e.offset, e.length);
			f.name = getPointerName("Formation", e.address);
			formations.addElement(f);
			formationMap.put(e.address, f);
		}

		for (IndexEntry e : getEntries(structMap, "SpecialFormation")) {
			Formation f = new Formation(this, fileBuffer, e.offset, e.length);
			f.name = getPointerName("SpecialFormation", e.address);
			formations.addElement(f);
			formationMap.put(e.address, f);
		}

		List<IndexEntry> formationTables = getEntries(structMap, "FormationTable");
		if (formationTables.size() > 0) {
			IndexEntry e = formationTables.get(0);

			for (int i = 0; true; i++) {
				int formationOffset = e.offset + 0x14 * i;
				fileBuffer.position(formationOffset);
				if (fileBuffer.getInt() == 0)
					break; // SJIS name

				fileBuffer.getInt(); // num units
				int ptrFormation = fileBuffer.getInt();
				int ptrStage = fileBuffer.getInt();
				int ptrDemoScript = fileBuffer.getInt();

				Formation f = formationMap.get(ptrFormation);
				f.includeInFormationTable = true;

				if (ptrStage != 0)
					f._stageName = stageMap.get(ptrStage).name;

				if (ptrDemoScript != 0)
					f.scriptName = getPointerName("Script", ptrDemoScript);
			}
		}
	}

	@Override
	public void fromXML(XmlReader xmr, Element rootElem)
	{
		baseAddress = xmr.readHex(rootElem, ATTR_BASE_ADDRESS);

		for (Element actorElem : xmr.getTags(rootElem, TAG_ACTOR))
			actors.addElement(new Actor(xmr, actorElem));

		for (Element stageElem : xmr.getTags(rootElem, TAG_STAGE))
			stages.addElement(new Stage(xmr, stageElem));

		for (Element formationElem : xmr.getTags(rootElem, TAG_FORMATION))
			formations.addElement(new Formation(xmr, formationElem));

		updateReferences();
	}

	@Override
	public void toXML(XmlWriter xmw)
	{
		XmlTag rootTag = xmw.createTag(TAG_BATTLE_SECTION, false);
		xmw.addHex(rootTag, ATTR_BASE_ADDRESS, baseAddress);
		xmw.openTag(rootTag);

		for (Actor a : actors)
			a.toXML(xmw);

		for (Formation f : formations)
			f.toXML(xmw);

		for (Stage s : stages)
			s.toXML(xmw);

		xmw.closeTag(rootTag);
		xmw.close();
	}

	private void updateReferences()
	{
		HashMap<String, Actor> actorMap = new HashMap<>();
		for (Actor a : actors)
			actorMap.put(a.name, a);

		HashMap<String, Stage> stageMap = new HashMap<>();
		for (Stage s : stages)
			stageMap.put(s.name, s);

		for (int i = 0; i < formations.size(); i++) {
			Formation f = formations.get(i);
			for (int j = 0; j < f.units.size(); j++) {
				Unit u = f.units.get(j);
				if (!u._actorName.isEmpty()) {
					Actor actor = actorMap.get(u._actorName);
					if (actor != null)
						u.setActor(actor);
					else
						Logger.logfWarning("Could not find actor %s for unit %02X of formation %02X.", u._actorName, j, i);
				}
				else
					Logger.logfWarning("Missing actor name for unit %02X of formation %02X.", j, i);
			}

			if (!f._stageName.isEmpty()) {
				Stage stage = stageMap.get(f._stageName);
				if (stage != null)
					f.stage = stage;
				else
					Logger.logfWarning("Could not find stage %s for formation %02X.", f._stageName, i);
			}
		}
	}

	private static class IndexEntry
	{
		public final String name;
		public final String type;
		public final int offset;
		public final int address;
		public final int length;

		public IndexEntry(String name, String type, int offset, int address, int length)
		{
			this.name = name;
			this.type = type;
			this.offset = offset;
			this.address = address;
			this.length = length;
		}
	}

	private final static List<IndexEntry> getEntries(HashMap<Integer, IndexEntry> structMap, String typeName)
	{
		List<IndexEntry> list = new LinkedList<>();
		for (IndexEntry e : structMap.values()) {
			if (e.type.equalsIgnoreCase(typeName))
				list.add(e);
		}
		return list;
	}

	private final static HashMap<Integer, IndexEntry> loadIndexFile(File f) throws IOException
	{
		LinkedHashMap<Integer, IndexEntry> structMap = new LinkedHashMap<>();

		try (BufferedReader in = new BufferedReader(new FileReader(f))) {
			String line;
			while ((line = in.readLine()) != null) {
				line = line.replaceAll("\\s+", "");
				if (line.isEmpty())
					continue;

				String[] tokens = line.split(SyntaxConstants.INDEX_FILE_SEPARATOR);

				if (line.startsWith("Padding"))
					continue;

				if (line.startsWith("Missing"))
					continue;

				String name = tokens[0];
				String type = tokens[1];
				int offset = (int) Long.parseLong(tokens[2], 16);
				int address = (int) Long.parseLong(tokens[3], 16);
				int size = (int) Long.parseLong(tokens[4], 16);

				IndexEntry entry = new IndexEntry(name, type, offset, address, size);
				structMap.put(address, entry);
			}
		}
		catch (Exception e) {
			throw new InputFileException(f, e.getMessage());
		}

		return structMap;
	}

	public static final String readString(ByteBuffer fileBuffer, int offset)
	{
		fileBuffer.position(offset);
		byte[] buf = new byte[100];
		int pos = 0;
		byte b;
		while (fileBuffer.hasRemaining() && (b = fileBuffer.get()) != 0)
			buf[pos++] = b;

		if (pos > 0)
			return new String(buf, 0, pos);
		else
			return "";
	}
}
