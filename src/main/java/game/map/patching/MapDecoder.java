package game.map.patching;

import static app.Directories.DUMP_MAP_NPC;
import static app.Directories.DUMP_MAP_SRC;
import static game.map.shape.TexturePanner.*;
import static game.shared.StructTypes.*;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map.Entry;
import java.util.TreeMap;

import app.StarRodException;
import app.input.IOUtils;
import asm.ArgSnooper;
import game.ROM;
import game.ROM.EPointer;
import game.ROM.LibScope;
import game.map.Map;
import game.map.MapIndex;
import game.map.config.MapConfigTable.MapConfig;
import game.map.marker.GridComponent;
import game.map.marker.GridOccupant;
import game.map.marker.GridOccupant.OccupantType;
import game.map.marker.Marker;
import game.map.marker.Marker.MarkerType;
import game.map.scripts.generators.Exit;
import game.map.scripts.generators.Exit.ExitType;
import game.map.scripts.generators.Generator;
import game.map.scripts.generators.Generator.GeneratorType;
import game.map.scripts.generators.foliage.Foliage;
import game.map.scripts.generators.foliage.Foliage.FoliageDataCategory;
import game.map.scripts.generators.foliage.Foliage.FoliageType;
import game.map.scripts.generators.foliage.FoliageModel;
import game.map.shape.Model;
import game.map.shape.TexturePanner;
import game.map.templates.MapScriptTemplate;
import game.map.tree.MapObjectNode;
import game.shared.ProjectDatabase;
import game.shared.ProjectDatabase.ConstEnum;
import game.shared.SyntaxConstants;
import game.shared.decoder.BaseDataDecoder;
import game.shared.decoder.Pointer;
import game.shared.decoder.Pointer.Origin;
import game.shared.lib.LibEntry;
import game.shared.lib.LibEntry.LibParam;
import game.shared.lib.LibEntry.LibParamList;
import game.shared.lib.LibEntry.ParamCategory;
import game.shared.lib.LibEntry.ParamListType;
import game.shared.struct.script.Script;
import game.shared.struct.script.Script.Command;
import game.shared.struct.script.Script.ScriptLine;
import game.shared.struct.script.ScriptVariable;
import game.world.entity.EntityInfo.EntityType;
import reports.EffectTypeTracker;
import reports.FunctionCallTracker;
import util.Logger;

public class MapDecoder extends BaseDataDecoder
{
	private final Map map;
	private final MapIndex index;

	public static final String NPC_ID_NAMESPACE = ".NpcID:";
	private boolean createNpcIDConstants;
	private boolean printingNPCs;
	private List<Pointer> npcList;
	private TreeMap<Integer, String> npcIdMap;
	private HashSet<Integer> npcIdSet;

	private List<Marker> markerList;
	private List<Marker> pushGridList;

	// special list only kept so we can print them
	private List<Marker> entrances;

	private Pointer ptrShapeOverride = null;
	private Pointer ptrHitOverride = null;
	private Pointer ptrTexOverride = null;

	public int treeCount = 0;
	public int bushCount = 0;

	public Pointer mainScript;

	private HashMap<UniqueStruct, Pointer> namedScriptMap;

	private static enum UniqueStruct
	{
		BindTrigger ("Script_BindTriggers"),
		BindFoliage ("Script_BindFoliage"),
		MakeEntities ("Script_MakeEntities");

		private final String name;

		private UniqueStruct(String name)
		{
			this.name = name;
		}
	}

	public void assignUniqueStruct(Pointer ptr, UniqueStruct struct)
	{
		//	if(namedScriptMap)

		if (namedScriptMap.containsKey(struct))
			throw new StarRodException(
				"%s encountered more than once: %08X and %08X", struct,
				namedScriptMap.get(struct).address, ptr.address);

		ptr.forceName(struct.name);

		List<Pointer> list = new LinkedList<>();
		list.add(ptr);
		namedScriptMap.put(struct, ptr);
	}

	@Override
	public String getSourceName()
	{
		return map.name;
	}

	public void addNpc(Pointer ptr)
	{
		npcList.add(ptr);
	}

	public String getNpcName(int id)
	{
		return npcIdMap.get(id);
	}

	public String makeUniqueNpcName(String name, int id)
	{
		if (!npcIdSet.contains(id)) {
			String baseName = String.format("%s_%02d", name, id);
			npcIdMap.put(id, baseName);
			npcIdSet.add(id);
			return baseName;
		}
		else {
			npcIdMap.remove(id);
			return null;
		}
	}

	@Override
	public void printNpcID(PrintWriter pw, ConstEnum enumType, int id)
	{
		if (ScriptVariable.isScriptVariable(id))
			printScriptWord(pw, id);
		else if (enumType.has(id))
			pw.print(enumType.getConstantString(id) + " ");
		else if (!printingNPCs && npcIdMap.containsKey(id))
			pw.print(NPC_ID_NAMESPACE + npcIdMap.get(id) + " ");
		else
			printScriptWord(pw, id);
	}

	public void addMarker(Marker m)
	{
		if (m.getType() == MarkerType.Entry)
			entrances.add(m);
		markerList.add(m);
	}

	public MapDecoder(ROM rom, Map map, MapConfig cfg, File source) throws IOException
	{
		super(LibScope.World, NpcT, rom.getLibrary(LibScope.World));

		this.map = map;
		index = new MapIndex(this.map);

		npcList = new LinkedList<>();
		npcIdMap = new TreeMap<>();
		npcIdSet = new HashSet<>();

		markerList = new ArrayList<>();
		entrances = new ArrayList<>();
		pushGridList = new ArrayList<>();
		namedScriptMap = new HashMap<>();

		ByteBuffer fileBuffer = IOUtils.getDirectBuffer(source);

		int startAddress = rom.getAddress(EPointer.WORLD_MAP_DATA_START);
		int endAddress = startAddress + fileBuffer.limit();
		setAddressRange(startAddress, endAddress, rom.getAddress(EPointer.WORLD_MAP_DATA_LIMIT));
		setOffsetRange(0, fileBuffer.limit());

		findLocalPointers(fileBuffer);

		enqueueAsRoot(cfg.ptrHeader, HeaderT, Origin.DECODED);
		if (cfg.ptrInitFunction != 0)
			enqueueAsRoot(cfg.ptrInitFunction, InitFunctionT, Origin.DECODED);

		decode(fileBuffer);
		index.refreshMarkers(map);

		File scriptFile = new File(DUMP_MAP_SRC + map.name + ".mscr");
		File indexFile = new File(DUMP_MAP_SRC + map.name + ".midx");

		printScriptFile(scriptFile, fileBuffer);
		printIndexFile(indexFile);
		printNPCFiles(fileBuffer);

		addMarkers(markerList);

		MapDecorator.decorate(map);
		decorateScriptData(fileBuffer);

		cfg.unknownPointers = unknownPointers;
		cfg.missingSections = missingSections;
	}

	@Override
	protected void printPreamble(PrintWriter pw)
	{
		for (Entry<Integer, String> entry : npcIdMap.entrySet())
			pw.printf("#define %s%-20s %02X%n", NPC_ID_NAMESPACE, entry.getValue(), entry.getKey());
		pw.println();
	}

	private void addMarkers(List<Marker> markers)
	{
		List<Marker> entryMarkers = new ArrayList<>();
		List<Marker> npcMarkers = new ArrayList<>();
		List<Marker> entityMarkers = new ArrayList<>();
		List<Marker> otherMarkers = new ArrayList<>();

		for (Marker m : markers) {
			switch (m.getType()) {
				case Entry:
					entryMarkers.add(m);
					break;
				case NPC:
					npcMarkers.add(m);
					break;
				case Grid:
				case Entity:
					entityMarkers.add(m);
					break;
				default:
					otherMarkers.add(m);
					break;
			}
		}

		MapObjectNode<Marker> rootNode = map.markerTree.getRoot();

		if (entryMarkers.size() > 0) {
			Marker group = Marker.createGroup("Entrances");
			MapObjectNode<Marker> groupNode = group.getNode();
			groupNode.parentNode = rootNode;
			groupNode.childIndex = rootNode.getChildCount();
			rootNode.add(groupNode);

			for (Marker m : entryMarkers) {
				m.getNode().parentNode = groupNode;
				m.getNode().childIndex = groupNode.getChildCount();
				groupNode.add(m.getNode());
			}
		}

		if (npcMarkers.size() > 0) {
			Marker group = Marker.createGroup("NPCs");
			MapObjectNode<Marker> groupNode = group.getNode();
			groupNode.parentNode = rootNode;
			groupNode.childIndex = rootNode.getChildCount();
			rootNode.add(groupNode);

			for (Marker m : npcMarkers) {
				m.getNode().parentNode = groupNode;
				m.getNode().childIndex = groupNode.getChildCount();
				groupNode.add(m.getNode());
			}
		}

		if (entityMarkers.size() > 0) {
			Marker group = Marker.createGroup("Entities");
			MapObjectNode<Marker> groupNode = group.getNode();
			groupNode.parentNode = rootNode;
			groupNode.childIndex = rootNode.getChildCount();
			rootNode.add(groupNode);

			for (Marker m : entityMarkers) {
				m.getNode().parentNode = groupNode;
				m.getNode().childIndex = groupNode.getChildCount();
				groupNode.add(m.getNode());
			}
		}

		for (Marker m : otherMarkers) {
			m.getNode().parentNode = rootNode;
			m.getNode().childIndex = rootNode.getChildCount();
			rootNode.add(m.getNode());
		}
	}

	private void decorateScriptData(ByteBuffer fileBuffer)
	{
		for (Generator generator : map.scripts.generatorsTreeModel.getObjectsInCategory(GeneratorType.Exit)) {
			Exit exit = (Exit) generator;
			exit.markerName.set(getEntryName(exit.markerID));
			exit.colliderName.set(getColliderName(exit.colliderID));
			switch (exit.type.get()) {
				case DoubleDoor:
					exit.door2Name.set(getModelName(exit.door2ID));
				case SingleDoor:
					exit.door1Name.set(getModelName(exit.door1ID));
				default:
			}
			exit.destMap.set(localPointerMap.get(exit.ptrDestMapName).text);
			exit.destMarkerName.set("Entry" + exit.destMarkerID); // cheating!
		}

		for (Pointer ptr : localPointerMap.values()) {
			if (ptr.getType() == ShakeTreeEventT)
				createTree(fileBuffer, ptr);
			else if (ptr.getType() == SearchBushEventT)
				createBush(fileBuffer, ptr);
		}

		if (ptrShapeOverride != null) {
			String name = ptrShapeOverride.text;
			if (name.endsWith("_shape")) {
				map.scripts.overrideShape.set(true);
				map.scripts.shapeOverrideName.set(name.substring(0, name.length() - 6));
			}
		}

		if (ptrHitOverride != null) {
			String name = ptrHitOverride.text;
			if (name.endsWith("_hit")) {
				map.scripts.overrideHit.set(true);
				map.scripts.hitOverrideName.set(name.substring(0, name.length() - 4));
			}
		}

		if (map.scripts.overrideShape.get() || map.scripts.overrideHit.get())
			map.scripts.generatorsTreeModel.clearAll();

		if (ptrTexOverride != null) {
			String name = map.scripts.hitOverrideName.get();
			if (name.endsWith("_tex"))
				map.texName = name.substring(0, name.length() - 4);
		}
	}

	private void createTree(ByteBuffer fileBuffer, Pointer ptr)
	{
		Foliage tree = new Foliage(FoliageType.Tree);
		if (ptr.foliageCollider >= 0)
			tree.colliderName.set(getColliderName(ptr.foliageCollider));
		if (ptr.ptrBombPos != 0)
			tree.bombPosName.set(String.format("Bomb_%08X", ptr.ptrBombPos));

		for (Pointer child : ptr.children) {
			fileBuffer.position(toOffset(child.address));
			int v;

			if (child.getType() == TreeModelListT && "Trunk".equals(child.getDescriptor())) {
				while ((v = fileBuffer.getInt()) != 0) {
					String name = getModelName(v);
					if (name != null && !name.isEmpty())
						tree.dataTreeModel.addToCategory(FoliageDataCategory.TrunkModels, new FoliageModel(tree, name));
				}
			}
			else if (child.getType() == TreeModelListT && "Leaves".equals(child.getDescriptor())) {
				while ((v = fileBuffer.getInt()) != 0) {
					String name = getModelName(v);
					if (name != null && !name.isEmpty())
						tree.dataTreeModel.addToCategory(FoliageDataCategory.LeafModels, new FoliageModel(tree, name));
				}
			}
			else if (child.getType() == TreeEffectVectorsT) {

			}
			else if (child.getType() == TreeDropListT) {

			}
			else if (child.getType() == ScriptT) {
				tree.hasCallback.set(true);
			}
		}
	}

	private void createBush(ByteBuffer fileBuffer, Pointer ptr)
	{
		Foliage bush = new Foliage(FoliageType.Bush);
		if (ptr.foliageCollider >= 0)
			bush.colliderName.set(getColliderName(ptr.foliageCollider));

		for (Pointer child : ptr.children) {
			fileBuffer.position(toOffset(child.address));
			int v;

			if (child.getType() == TreeModelListT) {
				while ((v = fileBuffer.getInt()) != 0) {
					String name = getModelName(v);
					if (name != null && !name.isEmpty())
						bush.dataTreeModel.addToCategory(FoliageDataCategory.BushModels, new FoliageModel(bush, name));
				}
			}
			else if (child.getType() == TreeEffectVectorsT) {

			}
			else if (child.getType() == TreeDropListT) {

			}
			else if (child.getType() == ScriptT) {
				bush.hasCallback.set(true);
			}
		}
	}

	@Override
	public void scanScript(Pointer ptr, ByteBuffer fileBuffer)
	{
		int startPosition = fileBuffer.position();
		Script.scan(this, ptr, fileBuffer);
		int endPosition = fileBuffer.position();

		//	ScriptRegistry.add(map.name, ptr.getPointerName(), ptr.script);

		if (MapScriptTemplate.EXIT_WALKOFF.matches(this, fileBuffer, startPosition)) {
			Exit exit = new Exit(ExitType.Walk, ptr.script);
			assert (ptr.bindLines != null);
			assert (ptr.bindLines.size() == 1);
			exit.colliderID = ptr.bindLines.get(0).args[2];
			map.scripts.generatorsTreeModel.addToCategory(GeneratorType.Exit, exit);
			ptr.setDescriptor("ExitWalk");
		}
		else if (MapScriptTemplate.EXIT_SINGLE_DOOR.matches(this, fileBuffer, startPosition)) {
			map.scripts.generatorsTreeModel.addToCategory(GeneratorType.Exit, new Exit(ExitType.SingleDoor, ptr.script));
			ptr.setDescriptor("ExitSingleDoor");
		}
		else if (MapScriptTemplate.EXIT_DOUBLE_DOOR.matches(this, fileBuffer, startPosition)) {
			map.scripts.generatorsTreeModel.addToCategory(GeneratorType.Exit, new Exit(ExitType.DoubleDoor, ptr.script));
			ptr.setDescriptor("ExitDoubleDoor");
		}
		// mark these unused scripts as ShakeTree/SearchBush ones
		else if (MapScriptTemplate.SHAKE_TREE.matches(this, fileBuffer, startPosition))
			ptr.setDescriptor(MapScriptTemplate.SHAKE_TREE.getName());
		else if (MapScriptTemplate.SEARCH_BUSH.matches(this, fileBuffer, startPosition))
			ptr.setDescriptor(MapScriptTemplate.SEARCH_BUSH.getName());
		else if (MapScriptTemplate.SHOW_GOT_ITEM.matches(this, fileBuffer, startPosition)) {
			//TODO
			/*
			fileBuffer.position(startPosition + 0x14);
			int flags = fileBuffer.getInt();
			switch(flags)
			{
			case 0:
				ptr.forceName("ShowGotItem");
				break;
			case 0x10:
				ptr.forceName("ShowGotCoin");
				break;
			//	case 0x20:
			//		ptr.setDescriptor("ShowGotStarPieces");
			//		break;
			default:
				assert(false) : flags;
			}
			*/
		}

		Marker currentMarker = null;

		int makeEntityCalls = 0;
		for (ScriptLine line : ptr.script) {
			if (line.cmd == Script.Command.CALL) {
				scanFunctionCall(ptr, fileBuffer, line);

				switch (line.args[0]) {
					case 0x80111D38: // MakeEntity
						currentMarker = scanEntityArgs(ptr, fileBuffer, line);
						line.marker = currentMarker;
						markerList.add(currentMarker);
						makeEntityCalls++;
						break;

					case 0x80111FF8: // AssignAreaFlag
						if (currentMarker != null) {
							currentMarker.entityComponent.areaFlagIndex.set(line.args[1]);
							currentMarker.entityComponent.hasFlag.set(true);
						}
						break;

					case 0x801120B8: // AssignFlag
					case 0x8011206C: // AssignBlockFlag
					case 0x80112114: // AssignPanelFlag
					case 0x80112170: // AssignCrateFlag
						if (currentMarker != null) {
							currentMarker.entityComponent.flagName.set(ScriptVariable.getScriptVariable(line.args[1]));
							currentMarker.entityComponent.hasFlag.set(true);
						}
						break;

					case 0x80111FB0: // AssignScript
						if (currentMarker != null) {
							EntityType assignedToType = currentMarker.entityComponent.type.get();
							Pointer assignedScriptPtr = getPointer(line.args[1]);
							assignedScriptPtr.assignedToMarker = currentMarker;
							assignedScriptPtr.assignedToType = assignedToType;

							switch (assignedToType) {
								case Chest:
									fileBuffer.position(toOffset(line.args[1] + 0x8));
									if (fileBuffer.getInt() == 0xFE363C8A) {
										String itemName = ProjectDatabase.getItemName(fileBuffer.getInt());
										if (itemName != null) {
											currentMarker.entityComponent.itemName.set(itemName);
											currentMarker.entityComponent.hasItem.set(true);
										}
									}
									break;

								case BoardedFloor: {
									fileBuffer.position(toOffset(line.args[1]));
									ArrayList<ScriptLine> child = Script.readScript(fileBuffer);
									for (ScriptLine childLine : child) {
										if (childLine.cmd == Command.SET_INT &&
											ScriptVariable.getType(childLine.args[0]) == ScriptVariable.GameFlag)
											currentMarker.entityComponent.flagName.set(ScriptVariable.getScriptVariable(childLine.args[0]));
										else if (childLine.cmd == Command.CALL && childLine.args[0] == 0x802C9DCC) // ModifyColliderFlags
											currentMarker.entityComponent.colliderName.set(getColliderName(childLine.args[2]));
										else if (childLine.cmd == Command.CALL && childLine.args[0] == 0x802C9288) // EnableModel
											currentMarker.entityComponent.modelName.set(getModelName(childLine.args[1]));
									}
								}
									break;

								case Hammer1Block:
								case Hammer1BlockWide:
								case Hammer1BlockThick:
								case Hammer1BlockTiny:
								case Hammer2Block:
								case Hammer2BlockWide:
								case Hammer2BlockThick:
								case Hammer2BlockTiny:
								case Hammer3Block:
								case Hammer3BlockWide:
								case Hammer3BlockThick:
								case Hammer3BlockTiny: {
									fileBuffer.position(toOffset(line.args[1]));
									ArrayList<ScriptLine> child = Script.readScript(fileBuffer);
									for (ScriptLine childLine : child) {
										if (childLine.cmd == Command.SET_INT &&
											ScriptVariable.getType(childLine.args[0]) == ScriptVariable.GameFlag)
											currentMarker.entityComponent.flagName.set(ScriptVariable.getScriptVariable(childLine.args[0]));
										else if (childLine.cmd == Command.CALL && childLine.args[0] == 0x802C9DCC) // ModifyColliderFlags
											currentMarker.entityComponent.colliderName.set(getColliderName(childLine.args[2]));
									}
								}
									break;

								default:
							}
							currentMarker.entityComponent.hasCallback.set(true);
						}
						break;

					case 0x802D18E8: // PlayerJump
						int x = line.args[1];
						int y = line.args[2];
						int z = line.args[3];

						boolean varX = ((x & 0xFFFFFFF0) == 0xFE363C80);
						boolean varY = ((y & 0xFFFFFFF0) == 0xFE363C80);
						boolean varZ = ((z & 0xFFFFFFF0) == 0xFE363C80);

						if (!varX && !varY && !varZ) {
							String markerName = String.format("JumpDest_%X", ptr.address + line.startOffset);
							line.marker = new Marker(markerName, MarkerType.Position, x, y, z, 0);
							markerList.add(line.marker);

							if (ptr.assignedToType == EntityType.ScriptSpring)
								ptr.assignedToMarker.entityComponent.targetMarker.set(markerName);
						}
						break;

					case 0x802D9700: // SetSpriteShading
						map.scripts.hasSpriteShading.set(true);
						map.scripts.shadingProfile.set(ProjectDatabase.SpriteShading.getShadingProfile(line.args[1]));
						break;

					case 0x802CA828: // SetCamPerspective
						map.scripts.camVfov.set(line.args[3]);
						map.scripts.camNearClip.set(line.args[4]);
						map.scripts.camFarClip.set(line.args[5]);
						break;

					case 0x802CAD98: // SetCamBGColor -- unique per map
						map.scripts.bgColorR.set(line.args[2]);
						map.scripts.bgColorG.set(line.args[3]);
						map.scripts.bgColorB.set(line.args[4]);
						break;

					case 0x802CB680: // SetCamLeadPlayer
						map.scripts.cameraLeadsPlayer.set(line.args[2] == 1);
						break;

					// generally not great, but these kinda work
					case 0x802D60E8: // ClearAmbientSounds
						map.scripts.hasAmbientSFX.set(false);
						break;
					case 0x802D611C: // PlayAmbientSounds
						map.scripts.hasAmbientSFX.set(true);
						map.scripts.ambientSFX.set(ProjectDatabase.getFromNamespace("AmbientSounds").getName(line.args[1]));
						break;
					case 0x802D5D4C: // SetMusicTrack
						map.scripts.hasMusic.set(true);
						map.scripts.songName.set(ProjectDatabase.getFromNamespace("Song").getName(line.args[2]));
						break;
				}
			}

			if (line.cmd.tabsAfter < 0)
				currentMarker = null; // end of scope
		}

		if (mainScript != null && mainScript.children.contains(ptr)) {
			if (makeEntityCalls > 0)
				assignUniqueStruct(ptr, UniqueStruct.MakeEntities);
		}

		for (ScriptLine line : ptr.script) {
			switch (line.cmd) {
				case SET_INT: // impossible to tell what the local pointer is from this command
					if (isLocalAddress(line.args[1])) {
						//XXX necessary? shouldnt these be automatically found during the initial scan?
						Pointer childPtr = getPointer(line.args[1]);
						ptr.addUniqueChild(childPtr);
					}
					if (line.cmd == Script.Command.SET_INT && line.args[0] == 0xF5DE0329)
						map.scripts.locationName.set(ProjectDatabase.LocationType.getName(line.args[1]));
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
					scanExec(ptr, fileBuffer, startPosition, line);
					break;

				case TRIGGER:
				case LOCK:
					scanTrigger(ptr, fileBuffer, line);
					break;

				default:
					break;
			}
		}

		if (ptr.assignedToType != null) {
			switch (ptr.assignedToType) {
				case Chest:
					if (ptr.script.get(0).args[0] == 0xFE363C8A)
						ptr.script.get(0).types[1] = LibEntry.resolveType(LibScope.Common, "#itemID");
					break;
				default:
			}
		}

		fileBuffer.position(endPosition);
	}

	private Marker scanEntityArgs(Pointer ptr, ByteBuffer fileBuffer, ScriptLine line)
	{
		String entityName = ProjectDatabase.EntityType.getConstantString(line.args[1]);
		String markerName = String.format("Entity%X", ptr.address + line.startOffset);

		int x = line.args[2];
		int y = line.args[3];
		int z = line.args[4];
		int a = line.args[5];
		Marker entityMarker = new Marker(markerName, MarkerType.Entity, x, y, z, a);
		entityMarker.setDescription(entityName.substring(1));
		entityMarker.entityComponent.type.set(EntityType.fromID(line.args[1]));

		switch (line.args[1]) {
			case 0x802EAF80: // BlueWarpPipe
				entityMarker.entityComponent.pipeEntry.set(getEntryName(line.args[6]));
				entityMarker.entityComponent.flagName.set(ScriptVariable.getScriptVariable(line.args[8] - 130000000));
				break;

			case 0x802EA564: // YellowBlock
			case 0x802EA588: // HiddenYellowBlock
			case 0x802EA5AC: // RedBlock
			case 0x802EA5D0: // HiddenRedBlock
			case 0x802EAED4: // WoodenCrate
			case 0x802EAE0C: { // GiantChest
				String itemName = ProjectDatabase.getItemName(line.args[6]);
				if (itemName != null) {
					entityMarker.entityComponent.itemName.set(itemName);
					entityMarker.entityComponent.hasItem.set(true);
				}
			}
				break;

			case 0x802EAB04: // HiddenPanel
				entityMarker.entityComponent.modelName.set(getModelName(line.args[6]));
				break;

			/*
			case 0x802E9A18: break; // SavePoint
			case 0x802E9BB0: break; // RedSwitch
			case 0x802E9BD4: break; // BlueSwitch
			case 0x802E9BF8: break; // HugeBlueSwitch
			case 0x802E9C1C: break; // GreenStompSwitch
			case 0x802EA07C: break; // MultiTriggerBlock
			case 0x802EA0C4: break; // BrickBlock
			case 0x802EA0E8: break; // MultiCoinBrick
			case 0x802EA10C: break; // Hammer1Block
			case 0x802EA130: break; // Hammer1BlockWide
			case 0x802EA154: break; // Hammer1BlockThick
			case 0x802EA178: break; // Hammer1BlockTiny
			case 0x802EA19C: break; // Hammer2Block
			case 0x802EA1C0: break; // Hammer2BlockWide
			case 0x802EA1E4: break; // Hammer2BlockThick
			case 0x802EA208: break; // Hammer2BlockTiny
			case 0x802EA22C: break; // Hammer3Block
			case 0x802EA250: break; // Hammer3BlockWide
			case 0x802EA274: break; // Hammer3BlockThick
			case 0x802EA298: break; // Hammer3BlockTiny
			case 0x802EA2E0: break; // PowBlock
			
			case 0x802EA5F4: break; // SingleTriggerBlock
			case 0x802EA7E0: break; // HealingBlock
			case 0x802EA910: break; // SuperBlock
			case 0x802EAA30: break; // ScriptSpring
			case 0x802EAE30: break; // Chest
			case 0x802EAFDC: break; // Signpost
			case 0x802BCD68: break; // Padlock
			case 0x802BCD8C: break; // PadlockRedFrame
			case 0x802BCDB0: break; // PadlockRedFace
			case 0x802BCDD4: break; // PadlockBlueFace
			case 0x802BCE84: break; // BoardedFloor
			case 0x802BCF00: break; // BombableRock1
			case 0x802BCF24: break; // BombableRock2
			
			case 0x802BCA74: break; // Tweester
			case 0x802BCB44: break; // StarBoxLaucher
			 */

			case 0x802EAA54: // SimpleSpring
				entityMarker.entityComponent.springLaunchHeight.set(line.args[6]);
				break;

			case 0x802BCD9C: // RedArrowSigns
				entityMarker.entityComponent.signAngle.set(line.args[6]);
				break;

			case 0x802BC788: // CymbalPlant
			case 0x802BC7AC: // PinkFlower
			case 0x802BC7F4: // SpinningFlower
			case 0x802BCBD8: // BellbellPlant
			case 0x802BCBFC: // TrumpetPlant
			case 0x802BCC20: // SpongeyFlower
			default:
		}

		return entityMarker;
	}

	private void scanExec(Pointer ptr, ByteBuffer fileBuffer, int startPosition, ScriptLine line)
	{
		int ptrScript = line.args[0];
		if (isLocalAddress(line.args[0])) {
			Integer[] execArgs = new Integer[16];

			for (int i = line.lineNum - 1; i >= 0; i--) {
				ScriptLine argLine = ptr.script.get(i);
				if (argLine.cmd == Script.Command.SET_INT) {
					if ((argLine.args[0] & 0xFFFFFFF0) == 0xFE363C80) {
						execArgs[argLine.args[0] & 0xF] = argLine.args[1];
						continue;
					}
				}
				i = -1;
			}

			if (MapScriptTemplate.TEX_PANNER.matches(this, fileBuffer, toOffset(ptrScript))) {
				assert (line.startOffset >= 0xD8);
				fileBuffer.position(startPosition + line.startOffset - 0xD0);

				int v;
				Integer[] panArgs = new Integer[13];
				for (int i = 0; i < 13; i++) {
					v = fileBuffer.getInt();
					assert (v == 0x24);
					v = fileBuffer.getInt();
					assert (v == 2);
					v = fileBuffer.getInt();
					assert (v == 0xFE363C80 + i);
					panArgs[i] = fileBuffer.getInt();
				}

				map.scripts.texPanners.get(panArgs[0]).load(panArgs);
				Pointer child = enqueueAsChild(ptr, ptrScript, ScriptT);
				child.scriptExecArgs = execArgs;
				child.setDescriptor("UpdateTexturePan");
			}
			else {
				// normal script
				Pointer child = enqueueAsChild(ptr, ptrScript, ScriptT);
				child.scriptExecArgs = execArgs;
			}
		}
		else {
			switch (ptrScript) {
				case 0x80285960: // EnterWalk -- previous line sets *Var[0] to LoadExits script
					ptr.setDescriptor("EnterWalk");
					assert (line.lineNum > 0);
					ScriptLine prevLine = ptr.script.get(line.lineNum - 1);
					assert (prevLine.cmd == Script.Command.SET_INT);
					enqueueAsChild(ptr, prevLine.args[1], ScriptT);
					break;

				case 0x80285DD4: // EnterSingleDoor
					ptr.setDescriptor("EnterSingleDoor");
					break;

				case 0x80285E74: // EnterDoubleDoor
					ptr.setDescriptor("EnterDoubleDoor");
					break;

				case 0x80285CF4: // ExitWalk
					ptr.setDescriptor("ExitWalk");
					break;

				case 0x80285DAC: // ExitSingleDoor
					ptr.setDescriptor("ExitSingleDoor");
					break;

				case 0x80285E4C: // ExitDoubleDoor
					ptr.setDescriptor("ExitDoubleDoor");
					break;

				default: // do nothing
			}
		}
	}

	private boolean scanTrigger(Pointer ptr, ByteBuffer fileBuffer, ScriptLine line)
	{
		boolean matchedFoliage = false;
		int ptrScript = line.args[0];

		Pointer scriptPtr = tryEnqueueAsChild(ptr, ptrScript, ScriptT);
		if (scriptPtr.bindLines == null)
			scriptPtr.bindLines = new ArrayList<>(3);
		scriptPtr.bindLines.add(line);

		if (line.args[1] == 0x00100000)
			tryEnqueueAsChild(ptr, line.args[2], TriggerCoordT);

		if (line.args.length == 6)
			tryEnqueueAsChild(ptr, line.args[3], ItemListT);

		// check script being bound
		if (MapScriptTemplate.SHAKE_TREE.matches(this, fileBuffer, toOffset(ptrScript))) {
			scriptPtr.setDescriptor(MapScriptTemplate.SHAKE_TREE.getName());
			matchedFoliage = true;

			assert (line.lineNum > 0);
			ScriptLine prevLine = ptr.script.get(line.lineNum - 1);
			Pointer evt = null;

			if (prevLine.cmd == Script.Command.SET_INT && prevLine.args[0] == 0xFE363C80) {
				assert (line.args[1] == 0x00001000 || line.args[1] == 0x00100000);
				evt = tryEnqueueAsChild(ptr, prevLine.args[1], ShakeTreeEventT);
			}
			else if (line.lineNum > 1) {
				prevLine = ptr.script.get(line.lineNum - 2);
				if (prevLine.cmd == Script.Command.SET_INT && prevLine.args[0] == 0xFE363C80)
					evt = getPointer(prevLine.args[1]);
			}

			if (evt != null) { // see: sbk_30, Script_802447DC
				if (line.args[1] == 0x00001000) // wall hammer
					evt.foliageCollider = line.args[2];
				else if (line.args[1] == 0x00100000) // point bomb
					evt.ptrBombPos = line.args[2];
			}
		}
		else if (MapScriptTemplate.SEARCH_BUSH.matches(this, fileBuffer, toOffset(ptrScript))) {
			scriptPtr.setDescriptor(MapScriptTemplate.SEARCH_BUSH.getName());
			matchedFoliage = true;

			assert (line.lineNum > 0);
			ScriptLine prevLine = ptr.script.get(line.lineNum - 1);

			if (prevLine.cmd == Script.Command.SET_INT) {
				assert (line.args[1] == 0x00000100);
				Pointer evt = tryEnqueueAsChild(ptr, prevLine.args[1], SearchBushEventT);
				evt.foliageCollider = line.args[2];
			}
		}
		return matchedFoliage;
	}

	private void scanFunctionCall(Pointer ptr, ByteBuffer fileBuffer, ScriptLine line)
	{
		int funcAddress = line.args[0];
		int nargs = line.args.length - 1;

		// local function calls
		if (isLocalAddress(funcAddress)) {
			enqueueAsChild(ptr, funcAddress, FunctionT).isAPI = true;

			// try to find AISettings scripts
			if (ptr.getDescriptor().equals("NpcAI") && nargs > 0) {
				if (isLocalAddress(line.args[1]) && getPointer(line.args[1]).isTypeUnknown())
					enqueueAsChild(ptr, line.args[1], AISettingsT);
			}

			scanUnknownArguments(ptr, line);
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
					if (line.args[1] == 0x802BCA74) // Tweester
						enqueueAsChild(ptr, line.args[6], TweesterPathListT);
					else if (line.args[1] == 0x802EAF80) // BlueWarpPipe;
						enqueueAsChild(ptr, line.args[7], ScriptT);
					return;

				case 0x802C9208: { // EnableTexPanning
					int modelID = line.args[1];
					if (((modelID & 0xFFFFFFF0) == 0xFE363C80) && ptr.scriptExecArgs != null && (ptr.scriptExecArgs[modelID & 0xF] != null))
						modelID = ptr.scriptExecArgs[modelID & 0xF];
					Model mdl = getModelByID(modelID);
					if (mdl != null && mdl.pannerID.get() < 0) // use default, but don't override SetTexPanner
						mdl.pannerID.copy(mdl.defaultPannerID);
				}
					break;

				case 0x802C9000: // SetTexPanner
					int panID = line.args[2];
					if (0 <= panID && panID < 16) {
						int modelID = line.args[1];
						if (((modelID & 0xFFFFFFF0) == 0xFE363C80) && ptr.scriptExecArgs != null && (ptr.scriptExecArgs[modelID & 0xF] != null))
							modelID = ptr.scriptExecArgs[modelID & 0xF];

						Model mdl = getModelByID(modelID);
						if (mdl != null)
							mdl.pannerID.set(panID);
					}
					break;

				case 0x802C9364: // SetTexPanOffset : tex pan group, mode "0 = main | 1 = aux", offset U, offset V
					//	boolean success = lookForTexPanGoto(ptr, line);
					//	if(!success)
					//		success = lookForTexPanLoop(ptr, line);
					lookForTexPanSmart(ptr, line);
					break;
			}

			// function not found in library
			if (entry == null) {
				scanUnknownArguments(ptr, line);
				return;
			}

			LibParamList params = entry.getMatchingParams(nargs);

			if (params == null) {
				throw new StarRodException("Call to %08X in %s does not match library signature: argc = %d",
					funcAddress, getSourceName(), nargs);
			}

			// function is named, but list is either empty, unknown, varargs
			if (params.listType != ParamListType.Normal) {
				scanUnknownArguments(ptr, line);
				return;
			}

			// scan pointers defined in the library, make sure there aren't any missing from the library
			int i = 0;
			for (LibParam param : params) {
				int v = line.args[1 + i];

				if (param.typeInfo.category == ParamCategory.StaticStruct) {
					Pointer child = tryEnqueueAsChild(ptr, v, param.typeInfo.staticType);
					if (child != null) {
						String ptrDesc = param.suffix;
						if (ptrDesc != null)
							child.setDescriptor(ptrDesc);

						int arrayLen = param.arrayLen;
						if (arrayLen != 0) {
							if (arrayLen < 0)
								child.listLength = line.args[-arrayLen + 1];
							else
								child.listLength = arrayLen;

							if (child.listLength > 128 || child.listLength < 1)
								throw new RuntimeException(String.format(
									"Invalid list length for function: %s (%d)", entry.name, child.listLength));
						}
					}
				}
				else if (isLocalAddress(v)) {
					Pointer child = getPointer(v);
					if (child.isTypeUnknown())
						enqueueAsChild(ptr, v, UnknownT);
				}

				i++;
			}
		}
	}

	@Override
	protected ArrayList<String[]> scanFunction(Pointer ptr, ByteBuffer fileBuffer)
	{
		ArrayList<String[]> code = super.scanFunction(ptr, fileBuffer);

		for (int i = 0; i < code.size(); i++) {
			String[] line = code.get(i);
			if (line[0].equals("JAL")) {
				int jalAddress = (int) Long.parseLong(line[1], 16);

				ArgSnooper args;
				switch (jalAddress) {
					case 0x8011BB50: // enable_world_fog
						map.scripts.worldFogSettings.enabled.set(true);
						break;
					case 0x8011BB74: // set_world_fog_dist
						args = new ArgSnooper(code, i);
						if (args.val[0] != null)
							map.scripts.worldFogSettings.start.set((int) Long.parseLong(args.val[0], 16));
						if (args.val[1] != null)
							map.scripts.worldFogSettings.end.set((int) Long.parseLong(args.val[1], 16));
						break;
					case 0x8011BB88: // set_world_fog_color
						args = new ArgSnooper(code, i);
						if (args.val[0] != null)
							map.scripts.worldFogSettings.R.set((int) Long.parseLong(args.val[0], 16));
						if (args.val[1] != null)
							map.scripts.worldFogSettings.G.set((int) Long.parseLong(args.val[1], 16));
						if (args.val[2] != null)
							map.scripts.worldFogSettings.B.set((int) Long.parseLong(args.val[2], 16));
						//if(args.val[3] != null)
						//	map.scripts.worldFogSettings.A = (int)Long.parseLong(args.val[3], 16);
						break;

					case 0x80122FEC: // enable_entity_fog
						map.scripts.worldFogSettings.enabled.set(true);
						break;
					case 0x80123010: // set_entity_fog_dist
						args = new ArgSnooper(code, i);
						if (args.val[0] != null)
							map.scripts.worldFogSettings.start.set((int) Long.parseLong(args.val[0], 16));
						if (args.val[1] != null)
							map.scripts.worldFogSettings.end.set((int) Long.parseLong(args.val[1], 16));
						break;
					case 0x80123028: // set_entity_fog_color
						args = new ArgSnooper(code, i);
						if (args.val[0] != null)
							map.scripts.worldFogSettings.R.set((int) Long.parseLong(args.val[0], 16));
						if (args.val[1] != null)
							map.scripts.worldFogSettings.G.set((int) Long.parseLong(args.val[1], 16));
						if (args.val[2] != null)
							map.scripts.worldFogSettings.B.set((int) Long.parseLong(args.val[2], 16));
						//if(args.val[3] != null)
						//	map.scripts.worldFogSettings.A = (int)Long.parseLong(args.val[3], 16);
						break;

					case 0x800654F0: // sprintf
						args = new ArgSnooper(code, i);
						if (args.val[0] == null)
							continue;
						switch ((int) Long.parseLong(args.val[0], 16)) {
							case 0x800D9230: // shape override
								ptrShapeOverride = tryEnqueueAsChild(ptr, (int) Long.parseLong(args.val[1], 16), AsciiT);
								break;
							case 0x800D91E0: // hit override
								ptrHitOverride = tryEnqueueAsChild(ptr, (int) Long.parseLong(args.val[1], 16), AsciiT);
								break;
							case 0x800B0CF0: // tex override
								ptrTexOverride = tryEnqueueAsChild(ptr, (int) Long.parseLong(args.val[1], 16), AsciiT);
								break;
						}
				}
			}
		}

		return code;
	}

	private static enum TexPanState
	{
		Setup, Init, Begin, Before, Reverse, Forward, After, End, INVALID
	}

	private boolean lookForTexPanSmart(Pointer ptr, ScriptLine callLine)
	{
		// error check the SetTexPanOffset call
		assert (callLine.args[2] == 0 || callLine.args[2] == 1);
		boolean aux = (callLine.args[2] == 1);

		int panID = callLine.args[1];
		int uvar = callLine.args[3];
		int vvar = callLine.args[4];

		boolean loop = false;
		int labelID = 0;
		int[] init = new int[2];
		int[] step = new int[2];
		int delay = 0;

		// fixed offset (max found = 0x18000) or FE363C80 (var) or FD050F80 (mapVar)
		assert (uvar == 0 || Math.abs(uvar) <= 0x20000
			|| ((uvar & 0xFFFFFFF0) == 0xFE363C80)
			|| ((uvar & 0xFFFFFFF0) == 0xFD050F80)) : String.format("%08X", uvar);
		assert (vvar == 0 || Math.abs(vvar) <= 0x20000
			|| ((vvar & 0xFFFFFFF0) == 0xFE363C80)
			|| ((vvar & 0xFFFFFFF0) == 0xFD050F80)) : String.format("%08X", vvar);

		//	boolean animateU = ((uvar & 0xFFFFFFF0) == 0xFE363C80);
		//	boolean animateV = ((vvar & 0xFFFFFFF0) == 0xFE363C80);
		//	assert(animateU || animateV);

		// done checking SetTexPanOffset

		TexPanState state = TexPanState.Forward;

		forward:
		for (int i = callLine.lineNum + 1; i < ptr.script.size(); i++) {
			ScriptLine line = ptr.script.get(i);

			switch (state) {
				case Forward:
					if (line.cmd == Script.Command.CALL && line.args[0] == 0x802C9364) // SetTexPanOffset
						continue;

					state = TexPanState.After;
					i--; // change state and re-evaluate line in next pass
					continue;

				case After:
					if ((line.cmd == Script.Command.ADD_INT) && (Math.abs(line.args[1]) <= 0x20000)) {
						if (line.args[0] == uvar)
							step[0] = line.args[1];
						else if (line.args[0] == vvar)
							step[1] = line.args[1];
						continue;
					}

					if ((line.cmd == Script.Command.SUB_INT) && (Math.abs(line.args[1]) <= 0x20000)) {
						if (line.args[0] == uvar)
							step[0] = -line.args[1];
						else if (line.args[0] == vvar)
							step[1] = -line.args[1];
						continue;
					}

					if (line.cmd == Script.Command.WAIT_FRAMES) {
						delay = line.args[0];
						assert (delay < 600) : String.format("%08X", delay);
						continue;
					}

					if (line.cmd == Script.Command.GOTO) {
						labelID = line.args[0];
						state = TexPanState.End;
						break forward;
					}

					if (line.cmd == Script.Command.END_LOOP) {
						loop = true;
						state = TexPanState.End;
						break forward;
					}

					Logger.logf("[%s] Couldn't work out panner script for %s", state, ptr.getPointerName());
					return false;

				default:
					throw new IllegalStateException();
			}
		}

		// we now know how it ends, start working backward
		state = TexPanState.Reverse;
		// System.out.printf("[1] %d : %d (%d %d) (%d %d)%n", panID, aux ? 1 : 0, init[0], step[0], init[1], step[1]);

		backward:
		for (int i = callLine.lineNum - 1; i >= 0; i--) {
			ScriptLine line = ptr.script.get(i);

			switch (state) {
				case Reverse:
					if (line.cmd == Script.Command.CALL && line.args[0] == 0x802C9364) // SetTexPanOffset
						continue;

					state = TexPanState.Before;
					i++; // change state and re-evaluate line in next pass
					continue;

				case Before:
					if ((line.cmd == Script.Command.ADD_INT) && (Math.abs(line.args[1]) <= 0x20000)) {
						if (line.args[0] == uvar)
							step[0] = line.args[1];
						else if (line.args[0] == vvar)
							step[1] = line.args[1];
						continue;
					}

					if ((line.cmd == Script.Command.SUB_INT) && (Math.abs(line.args[1]) <= 0x20000)) {
						if (line.args[0] == uvar)
							step[0] = -line.args[1];
						else if (line.args[0] == vvar)
							step[1] = -line.args[1];
						continue;
					}

					if ((!loop && line.cmd == Script.Command.LABEL && line.args[0] == labelID) || (loop && line.cmd == Script.Command.LOOP)) {
						state = TexPanState.Begin;
						continue;
					}

					Logger.logf("[%s] Couldn't work out panner script for %s", state, ptr.getPointerName());
					return false;

				case Begin:
					if (line.cmd == Script.Command.SET_INT && Math.abs(line.args[1]) <= 0x20000) {
						state = TexPanState.Init;
						i++; // change state and re-evaluate line in next pass
						continue;
					}

					Logger.logf("[%s] Couldn't work out panner script for %s", state, ptr.getPointerName());
					return false;

				case Init:
					if (line.cmd == Script.Command.SET_INT && Math.abs(line.args[1]) <= 0x20000) {
						if (line.args[0] == uvar)
							init[0] = line.args[1];
						else if (line.args[0] == vvar)
							init[1] = line.args[1];
						continue;
					}

					if (line.cmd == Script.Command.CALL && (line.args[0] == 0x802C9000 || line.args[0] == 0x802C9208)) // SetTexPanner or EnableTexPanning
					{
						state = TexPanState.Setup;
						break backward;
					}

					Logger.logf("[%s] Couldn't work out panner script for %s", state, ptr.getPointerName());
					return false;

				default:
					throw new IllegalStateException();
			}
		}

		if (state == TexPanState.INVALID) {
			Logger.log("Couldn't work out panner script for " + ptr.getPointerName());
			return false;
		}

		TexturePanner panner = map.scripts.texPanners.get(panID);
		if (aux) {
			panner.params.init[AUX_U] = init[0];
			panner.params.init[AUX_V] = init[1];
			panner.params.rate[AUX_U] = step[0];
			panner.params.rate[AUX_V] = step[1];

			if (step[0] != 0)
				panner.params.freq[AUX_U] = delay;

			if (step[1] != 0)
				panner.params.freq[AUX_V] = delay;
		}
		else {
			panner.params.init[MAIN_U] = init[0];
			panner.params.init[MAIN_V] = init[1];
			panner.params.rate[MAIN_U] = step[0];
			panner.params.rate[MAIN_V] = step[1];

			if (step[0] != 0)
				panner.params.freq[MAIN_U] = delay;

			if (step[1] != 0)
				panner.params.freq[MAIN_V] = delay;
		}

		Logger.log("Found panner script in " + ptr.getPointerName());
		//	System.out.printf("[2] %d : %d (%d %d) (%d %d)%n", panID, aux ? 1 : 0, init[0], step[0], init[1], step[1]);
		return true;
	}

	private Model getModelByID(int id)
	{
		for (Model mdl : map.modelTree) {
			if (id == mdl.getNode().getTreeIndex())
				return mdl;
		}
		return null;
	}

	private void scanUnknownArguments(Pointer ptr, ScriptLine line)
	{
		// unknown pointers should still be considered children of this script
		for (int i = 1; i < line.args.length; i++) {
			int v = line.args[i];
			if (isLocalAddress(v)) {
				Pointer childPtr = getPointer(v);
				if (childPtr.isTypeUnknown())
					enqueueAsChild(ptr, v, UnknownT); //XXX what is this for?
			}
		}
	}

	private void printNPCFiles(ByteBuffer fileBuffer) throws IOException
	{
		ArrayList<Integer> pointerList = getSortedLocalPointerList();

		String suffix = map.name;
		for (int i : pointerList) {
			Pointer ptr = localPointerMap.get(i);
			ptr.setImportAffix(suffix);
		}

		printingNPCs = true;
		for (Pointer npc : npcList) {
			String npcGroupName = npc.getImportName();
			File f = new File(DUMP_MAP_NPC + npcGroupName + ".mpat");

			PrintWriter pw = IOUtils.getBufferedPrintWriter(f);

			pw.println("% automatically dumped from map " + map.name);
			pw.println();

			pw.println("#new:" + NpcT.toString() + " $" + npcGroupName + " {");
			printPointer(npc, fileBuffer, npc.address, pw);
			pw.println("}");
			pw.println();

			for (int address : pointerList) {
				Pointer pointer = localPointerMap.get(address);
				if (pointer.ancestors.contains(npc)) {
					pw.println("#new:" + pointer.getType().toString() + " " + pointer.getPointerName() + " {");
					printPointer(pointer, fileBuffer, address, pw);
					pw.println("}");
					pw.println();
				}
			}
			pw.close();
		}
		printingNPCs = false;
	}

	@Override
	public String printFunctionArgs(Pointer ptr, PrintWriter pw, ScriptLine line, int lineAddress)
	{
		// overrides
		switch (line.args[0]) {
			case 0x80111D38: // MakeEntity
				printEntityArgs(ptr, pw, line, lineAddress);
				return "";

			case 0x802D6CC0: // MakeItemEntity
				printItemArgs(ptr, pw, line, lineAddress);
				return "";

			case 0x802D9700: // SetSpriteShading
				String profileName = ProjectDatabase.SpriteShading.getShadingName(line.args[1]);
				if (profileName == null)
					printScriptWord(pw, ptr, line.types[1], line.args[1]);
				else
					pw.printf("%c%s:%s ", SyntaxConstants.CONSTANT_PREFIX, ProjectDatabase.SHADING_NAMESPACE, profileName);
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

			case 0x802832E0: // CreateBlockSystem
			{
				int x = line.args[4];
				int y = line.args[5];
				int z = line.args[6];

				String markerName = String.format("Grid_%X", lineAddress);
				Marker m = new Marker(markerName, MarkerType.Grid, x, y, z, 0);
				markerList.add(m);
				line.marker = m;

				GridComponent grid = m.gridComponent;
				pushGridList.add(m);

				grid.gridIndex.set(line.args[1]);
				grid.gridSizeX.set(line.args[2]);
				grid.gridSizeZ.set(line.args[3]);
				grid.gridSpacing.set(25);

				printScriptWord(pw, ptr, line.types[1], line.args[1]);
				pw.printf("%cPushGrid:%s ", SyntaxConstants.EXPRESSION_PREFIX, markerName);
				printScriptWord(pw, ptr, line.types[7], line.args[7]);
				return "";
			}

			case 0x8028347C: // SetPushBlock
			{
				Marker m = getLastPushGridForID(line.args[1]);
				if (m != null &&
					(line.args[2] >= 0 && line.args[2] < m.gridComponent.gridSizeX.get()) &&
					(line.args[3] >= 0 && line.args[3] < m.gridComponent.gridSizeZ.get())) {
					GridComponent grid = m.gridComponent;
					if (line.args[4] == 1)
						grid.gridOccupants.add(new GridOccupant(grid.gridOccupants, line.args[2], line.args[3], OccupantType.Block));
					else if (line.args[4] == 2)
						grid.gridOccupants.add(new GridOccupant(grid.gridOccupants, line.args[2], line.args[3], OccupantType.Obstruction));
				}
			}
				break;

			case 0x802CA400: // GotoMap
				if (ptr.assignedToType == EntityType.BlueWarpPipe) {
					Pointer ptrMapName = getPointer(line.args[1]);
					ptr.assignedToMarker.entityComponent.gotoMap.set(ptrMapName.text);
					ptr.assignedToMarker.entityComponent.gotoEntry.set(Integer.toString(line.args[2], 16));
				}
				break;

			case 0x802D18E8: // PlayerJump
				if (line.marker != null) {
					pw.printf("%cVec3d:%s ", SyntaxConstants.EXPRESSION_PREFIX, line.marker.getName());
					printScriptWord(pw, ptr, line.types[4], line.args[4]);
					return "";
				}
				break;
		}

		return super.printFunctionArgs(ptr, pw, line, lineAddress);
	}

	private void printEntityArgs(Pointer ptr, PrintWriter pw, ScriptLine line, int lineAddress)
	{
		String entityName = ProjectDatabase.EntityType.getConstantString(line.args[1]);
		pw.print(entityName + " ");

		pw.printf("%cVec4d:%s ", SyntaxConstants.EXPRESSION_PREFIX, line.marker.getName());

		switch (line.args[1]) {
			case 0x802EAF80: // BlueWarpPipe
				printEntryID(ptr, pw, line.args[6]);
				printScriptWord(pw, ptr, line.types[7], line.args[7]);
				pw.print("~Index:" + ScriptVariable.getScriptVariable(line.args[8] - 130000000) + " ");
				printScriptWord(pw, ptr, line.types[9], line.args[9]);
				break;

			case 0x802EA564: // YellowBlock
			case 0x802EA588: // HiddenYellowBlock
			case 0x802EA5AC: // RedBlock
			case 0x802EA5D0: // HiddenRedBlock
			case 0x802EAED4: // WoodenCrate
			case 0x802EAE0C: // GiantChest
				String itemName = ProjectDatabase.getItemName(line.args[6]);
				if (itemName != null) {
					pw.print(ProjectDatabase.getItemConstant(line.args[6]) + " ");
				}
				else {
					printScriptWord(pw, ptr, line.types[6], line.args[6]);
				}
				for (int i = 7; i < line.args.length; i++)
					printScriptWord(pw, ptr, line.types[i], line.args[i]);
				break;

			case 0x802EAB04: // HiddenPanel
				printModelID(ptr, pw, line.args[6]);
				for (int i = 7; i < line.args.length; i++)
					printScriptWord(pw, ptr, line.types[i], line.args[i]);
				break;

			/*
			case 0x802E9A18: break; // SavePoint
			case 0x802E9BB0: break; // RedSwitch
			case 0x802E9BD4: break; // BlueSwitch
			case 0x802E9BF8: break; // HugeBlueSwitch
			case 0x802E9C1C: break; // GreenStompSwitch
			case 0x802EA07C: break; // MultiTriggerBlock
			case 0x802EA0C4: break; // BrickBlock
			case 0x802EA0E8: break; // MultiCoinBrick
			case 0x802EA10C: break; // Hammer1Block
			case 0x802EA130: break; // Hammer1BlockWide
			case 0x802EA154: break; // Hammer1BlockThick
			case 0x802EA178: break; // Hammer1BlockTiny
			case 0x802EA19C: break; // Hammer2Block
			case 0x802EA1C0: break; // Hammer2BlockWide
			case 0x802EA1E4: break; // Hammer2BlockThick
			case 0x802EA208: break; // Hammer2BlockTiny
			case 0x802EA22C: break; // Hammer3Block
			case 0x802EA250: break; // Hammer3BlockWide
			case 0x802EA274: break; // Hammer3BlockThick
			case 0x802EA298: break; // Hammer3BlockTiny
			case 0x802EA2E0: break; // PowBlock
			
			case 0x802EA5F4: break; // SingleTriggerBlock
			case 0x802EA7E0: break; // HealingBlock
			case 0x802EA910: break; // SuperBlock
			case 0x802EAA30: break; // ScriptSpring
			case 0x802EAE30: break; // Chest
			case 0x802EAFDC: break; // Signpost
			case 0x802BCD68: break; // Padlock
			case 0x802BCD8C: break; // PadlockRedFrame
			case 0x802BCDB0: break; // PadlockRedFace
			case 0x802BCDD4: break; // PadlockBlueFace
			case 0x802BCE84: break; // BoardedFloor
			case 0x802BCF00: break; // BombableRock1
			case 0x802BCF24: break; // BombableRock2
			
			case 0x802BCA74: break; // Tweester
			case 0x802BCB44: break; // StarBoxLaucher
			 */

			case 0x802EAA54: // SimpleSpring
				pw.printf("%d` ", line.args[6]);
				printScriptWord(pw, ptr, line.types[7], line.args[7]);
				//		for(int i = 7; i < line.args.length; i++)
				//			printScriptWord(pw, ptr, line.types[i], line.args[i]);
				break;

			case 0x802BCD9C: // RedArrowSigns
				pw.printf("%d` ", line.args[6]);
				printScriptWord(pw, ptr, line.types[7], line.args[7]);
				//		for(int i = 7; i < line.args.length; i++)
				//			printScriptWord(pw, ptr, line.types[i], line.args[i]);
				break;

			case 0x802BC788: // CymbalPlant
			case 0x802BC7AC: // PinkFlower
			case 0x802BC7F4: // SpinningFlower
			case 0x802BCBD8: // BellbellPlant
			case 0x802BCBFC: // TrumpetPlant
			case 0x802BCC20: // SpongeyFlower
			default:
				for (int i = 6; i < line.args.length; i++)
					printScriptWord(pw, ptr, line.types[i], line.args[i]);
		}
	}

	private void printItemArgs(Pointer ptr, PrintWriter pw, ScriptLine line, int lineAddress)
	{
		String itemName = null;
		boolean knownItemName = false;

		if (ProjectDatabase.hasItem(line.args[1])) {
			pw.print(ProjectDatabase.getItemConstant(line.args[1]) + " ");
			itemName = ProjectDatabase.getItemName(line.args[1]);
			knownItemName = true;
		}
		else {
			printScriptWord(pw, ptr, line.types[1], line.args[1]);
			if ((line.args[1] & 0xFFFFFFF0) == 0xFE363C80)
				itemName = "VariableItem";
			else
				itemName = "UnknownItem";
		}

		int x = line.args[2];
		int y = line.args[3];
		int z = line.args[4];

		boolean varX = ((x & 0xFFFFFFF0) == 0xFE363C80);
		boolean varY = ((y & 0xFFFFFFF0) == 0xFE363C80);
		boolean varZ = ((z & 0xFFFFFFF0) == 0xFE363C80);

		if (varX || varY || varZ) {
			Logger.logDetail(map.name + " has item with variable position! Cannot create marker.");
			for (int i = 2; i < line.args.length; i++)
				printScriptWord(pw, ptr, line.types[i], line.args[i]);
		}
		else {
			String markerName = String.format("Item%X", lineAddress);
			Marker m = new Marker(markerName, MarkerType.Entity, x, y, z, 0);
			m.entityComponent.type.set(EntityType.Item);
			if (knownItemName)
				m.entityComponent.itemName.set(itemName);
			String spawnMode = ProjectDatabase.getFromNamespace("ItemSpawnMode").getName(line.args[5]);
			if (spawnMode != null)
				m.entityComponent.itemSpawnMode.set(spawnMode);
			m.entityComponent.flagName.set(ScriptVariable.getScriptVariable(line.args[6]));
			markerList.add(m);

			pw.printf("%cVec3d:%s ", SyntaxConstants.EXPRESSION_PREFIX, markerName);
			for (int i = 5; i < line.args.length; i++)
				printScriptWord(pw, ptr, line.types[i], line.args[i]);
		}
	}

	private Marker getLastPushGridForID(int gridIndex)
	{
		for (int i = pushGridList.size() - 1; i >= 0; i--) {
			Marker m = pushGridList.get(i);
			if (m.gridComponent.gridIndex.get() == gridIndex)
				return m;
		}
		return null;
	}

	public String getModelName(int id)
	{
		return index.getModelName(id);
	}

	public String getColliderName(int id)
	{
		return index.getColliderName(id);
	}

	public String getZoneName(int id)
	{
		return index.getZoneName(id);
	}

	public String getEntryName(int id)
	{
		if (id < 0 || id >= entrances.size())
			return null;
		return entrances.get(id).getName();
	}

	@Override
	public void printModelID(Pointer ptr, PrintWriter pw, int id)
	{
		String name = getModelName(id);
		if (name != null)
			pw.printf("%cModel:%s ", SyntaxConstants.EXPRESSION_PREFIX, name);
		else
			printScriptWord(pw, id);
	}

	@Override
	public void printColliderID(Pointer ptr, PrintWriter pw, int id)
	{
		String name = getColliderName(id);
		if (name != null)
			pw.printf("%cCollider:%s ", SyntaxConstants.EXPRESSION_PREFIX, name);
		else
			printScriptWord(pw, id);
	}

	@Override
	public void printZoneID(Pointer ptr, PrintWriter pw, int id)
	{
		String name = getZoneName(id);
		if (name != null)
			pw.printf("%cZone:%s ", SyntaxConstants.EXPRESSION_PREFIX, name);
		else
			printScriptWord(pw, id);
	}

	@Override
	public void printEntryID(Pointer ptr, PrintWriter pw, int id)
	{
		String name = getEntryName(id);
		if (name != null)
			pw.printf("%cEntry:%s ", SyntaxConstants.EXPRESSION_PREFIX, name);
		else
			printScriptWord(pw, id);
	}
}
