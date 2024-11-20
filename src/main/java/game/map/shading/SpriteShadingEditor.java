package game.map.shading;

import static app.Directories.*;
import static game.map.shading.ShadingKey.*;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.w3c.dom.Element;

import app.Environment;
import app.Resource;
import app.Resource.ResourceType;
import app.StarRodException;
import game.ROM.EOffset;
import game.shared.ProjectDatabase;
import patcher.RomPatcher;
import util.Logger;
import util.Priority;
import util.xml.XmlWrapper.XmlReader;
import util.xml.XmlWrapper.XmlTag;
import util.xml.XmlWrapper.XmlWriter;

public class SpriteShadingEditor
{
	public static void main(String[] args) throws IOException
	{
		Environment.initialize();
		dumpShading();
		Environment.exit();
	}

	/*
	 * 00000000 00000068 14
	 * 00000078 000000B8 1
	 * 0000008E 000000BC 10
	 * 0000053E 000000FC 1
	 *
	 * 000005B4 00000100 1
	 * 0000061A 00000104 C
	 * 00000802 00000134 2
	 * 0000086E 0000013C B
	 *
	 * 000008B0 00000168 5
	 * 000008EE 0000017C 1
	 * 00000904 00000180 11
	 * 00000C5A 000001C4 1
	 *
	 * 00000C70 000001C8 1
	 * 4 bytes padding
	 */

	public static final int NUM_GROUPS = 13;
	public static final int[] NUM_PROFILES_PER_GROUP = { 20, 1, 16, 1, 1, 12, 2, 11, 5, 1, 17, 1, 1 };

	public static void dumpShading() throws IOException
	{
		int shadingTableOffset = ProjectDatabase.rom.getOffset(EOffset.SHADING_TABLE);
		int shadingDataStart = ProjectDatabase.rom.getOffset(EOffset.SHADING_DATA_START);

		// get default names for profiles
		HashMap<Integer, String> nameMap = new HashMap<>();

		Pattern KVPattern = Pattern.compile("([0-9A-Fa-f]+) (\\S+)");
		Matcher KVMatcher = KVPattern.matcher("");
		for (String line : Resource.getTextInput(ResourceType.Basic, FN_SPRITE_PROFILE_NAMES, false)) {
			KVMatcher.reset(line);
			if (KVMatcher.matches()) {
				int key = (int) Long.parseLong(KVMatcher.group(1), 16);
				String name = KVMatcher.group(2);

				if (nameMap.containsKey(key))
					throw new IllegalStateException("Duplicate shading profile name for key: " + line);

				nameMap.put(key, name);
			}
		}

		// read binary data from baseROM
		SpriteShadingData data = new SpriteShadingData();

		try (RandomAccessFile raf = Environment.getBaseRomReader()) {
			int[] groupDataOffset = new int[NUM_GROUPS];
			int[] profileListOffsets = new int[NUM_GROUPS];
			ByteBuffer bb = Environment.getBaseRomBuffer();

			bb.position(shadingTableOffset);
			for (int i = 0; i < NUM_GROUPS; i++) {
				groupDataOffset[i] = bb.getInt();
				profileListOffsets[i] = bb.getInt();
			}

			ArrayList<ArrayList<ShadingProfile>> groups = new ArrayList<>();

			for (int i = 0; i < NUM_GROUPS; i++) {
				ArrayList<ShadingProfile> profileList = new ArrayList<>();
				groups.add(profileList);

				for (int j = 0; j < NUM_PROFILES_PER_GROUP[i]; j++) {
					bb.position(shadingTableOffset + profileListOffsets[i] + 4 * j);
					int profileDataOffset = bb.getInt();
					bb.position(shadingDataStart + groupDataOffset[i] + profileDataOffset);

					ShadingProfile profile = new ShadingProfile(bb, i, j);
					profile.vanilla = true;
					profileList.add(profile);

					profile.name.set(nameMap.get(profile.key));
					if (profile.name == null)
						profile.name.set(String.format("%08X", profile.key));
				}
			}

			data.createModel(groups);
		}

		saveShadingProfiles(new File(DUMP_SPRITE + FN_SPRITE_SHADING), data);
	}

	public static void patchShading(RomPatcher rp)
	{
		Logger.log("Writing sprite shading profiles.", Priority.MILESTONE);

		int shadingTableOffset = ProjectDatabase.rom.getOffset(EOffset.SHADING_TABLE);
		int shadingDataStart = ProjectDatabase.rom.getOffset(EOffset.SHADING_DATA_START);
		int shadingDataEnd = ProjectDatabase.rom.getOffset(EOffset.SHADING_DATA_END);

		SpriteShadingData shadingData = loadModData();
		if (shadingData == null)
			throw new StarRodException("Could not load sprite shading data.");

		ArrayList<ArrayList<ShadingProfile>> groups = shadingData.getGroupList();

		int tableSize = groups.size() * 8;
		for (ArrayList<ShadingProfile> group : groups)
			tableSize += group.size() * 4;

		ByteBuffer table = ByteBuffer.allocateDirect(tableSize);
		table.position(groups.size() * 8);

		rp.seek("Sprite Shading Data", shadingTableOffset + tableSize);

		int[] groupDataOffset = new int[groups.size()];
		int[] profileListOffsets = new int[groups.size()];

		for (int i = 0; i < groups.size(); i++) {
			ArrayList<ShadingProfile> group = groups.get(i);
			ByteBuffer[] pbufs = new ByteBuffer[group.size()];
			int totalGroupSize = 0;

			profileListOffsets[i] = table.position();
			for (int j = 0; j < group.size(); j++) {
				table.putInt(totalGroupSize);
				ShadingProfile profile = group.get(j);
				pbufs[j] = profile.getData();
				totalGroupSize += pbufs[j].capacity();
			}

			// if out of room, begin appending to end of ROM
			if (shadingDataEnd - rp.getCurrentOffset() < totalGroupSize)
				rp.seek("Sprite Shading Data", rp.nextAlignedOffset());

			groupDataOffset[i] = rp.getCurrentOffset() - shadingDataStart;
			for (ByteBuffer pb : pbufs)
				rp.write(pb);
		}

		table.position(0);
		for (int i = 0; i < groups.size(); i++) {
			table.putInt(groupDataOffset[i]);
			table.putInt(profileListOffsets[i]);
		}
		table.rewind();

		rp.seek("Sprite Shading Table", shadingTableOffset);
		rp.write(table);
	}

	public static void saveShadingProfiles(SpriteShadingData data) throws IOException
	{
		saveShadingProfiles(new File(MOD_SPRITE + FN_SPRITE_SHADING), data);
	}

	private static void saveShadingProfiles(File xmlFile, SpriteShadingData data) throws IOException
	{
		data.validateNames();
		data.assignCustomKeys();

		try (XmlWriter xmw = new XmlWriter(xmlFile)) {
			XmlTag rootTag = xmw.createTag(TAG_SPRITE_SHADING, false);
			xmw.openTag(rootTag);

			ArrayList<ArrayList<ShadingProfile>> groups = data.getGroupList();

			for (ArrayList<ShadingProfile> profileList : groups) {
				XmlTag groupTag = xmw.createTag(TAG_GROUP, false);
				xmw.openTag(groupTag);

				for (ShadingProfile profile : profileList)
					profile.toXML(xmw);

				xmw.closeTag(groupTag);
			}

			xmw.closeTag(rootTag);
			xmw.save();
			data.modified = false;
		}
	}

	public static SpriteShadingData loadModData()
	{
		return loadData(new File(MOD_SPRITE + FN_SPRITE_SHADING));
	}

	public static SpriteShadingData loadDumpData()
	{
		return loadData(new File(DUMP_SPRITE + FN_SPRITE_SHADING));
	}

	private static SpriteShadingData loadData(File xmlFile)
	{
		SpriteShadingData profileData;

		try {
			profileData = SpriteShadingEditor.loadShadingProfiles(xmlFile);
			profileData.validateNames();
			profileData.assignCustomKeys();
			Logger.logf("Loaded %d shading profiles.", profileData.listModel.getSize());
		}
		catch (IOException e) {
			Logger.logError(e.getMessage().replaceAll("\\r?\\n", " "));
			profileData = null;
		}

		return profileData;
	}

	private static SpriteShadingData loadShadingProfiles(File xmlFile) throws IOException
	{
		if (!xmlFile.exists())
			throw new IOException(String.format("Could not locate sprite shading data at: %n%s", xmlFile));

		SpriteShadingData data = new SpriteShadingData();
		XmlReader xmr = new XmlReader(xmlFile);

		ArrayList<ArrayList<ShadingProfile>> groups = new ArrayList<>();

		int groupID = 0;
		for (Element groupElem : xmr.getTags(xmr.getRootElement(), TAG_GROUP)) {
			ArrayList<ShadingProfile> profileList = new ArrayList<>();
			groups.add(profileList);

			int profileID = 0;
			for (Element profileElem : xmr.getTags(groupElem, TAG_PROFILE)) {
				ShadingProfile profile = ShadingProfile.read(xmr, profileElem, groupID, profileID);
				profileList.add(profile);
				profileID++;
			}
			groupID++;
		}

		data.createModel(groups);

		return data;
	}
}
