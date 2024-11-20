package game.map.scripts;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import app.Resource;
import app.Resource.ResourceType;
import app.StarRodException;
import app.input.InvalidInputException;
import game.globals.ItemRecord;
import game.map.MapObject.MapObjectType;
import game.map.marker.Marker;
import game.map.marker.Marker.MarkerType;
import game.map.scripts.generators.Exit;
import game.map.scripts.generators.Exit.ExitType;
import game.map.scripts.generators.Generator;
import game.map.scripts.generators.Generator.GeneratorType;
import game.shared.DataUtils;
import game.shared.ProjectDatabase;
import game.shared.struct.script.ScriptVariable;
import game.world.entity.EntityInfo.EntityType;

public final class EntityGenerator
{
	private final ScriptGenerator generator;

	private final List<String> support = new ArrayList<>();
	private final List<String> body = new ArrayList<>();
	private final List<String> callbacks = new ArrayList<>();

	private static class EntityTypeComparator implements Comparator<Marker>
	{
		@Override
		public int compare(Marker a, Marker b)
		{
			EntityType typeA = a.entityComponent.type.get();
			EntityType typeB = b.entityComponent.type.get();
			return typeA.compareTo(typeB);
		}

	}

	public EntityGenerator(
		ScriptGenerator generator,
		List<Marker> entityList,
		List<String> importLines,
		List<String> callbackLines)
		throws InvalidInputException
	{
		this.generator = generator;
		entityList.sort(new EntityTypeComparator());

		addSupport(entityList);

		boolean addedSuperBlock = false;

		// calls to MakeEntity and instance data
		for (Marker entity : entityList) {
			try {
				generator.validateObject(entity.entityComponent.type.get().name,
					"Entity", MapObjectType.MARKER, entity.getName());

				switch (entity.entityComponent.type.get()) {
					case BoardedFloor:
						makeBoardedFloor(entity);
						break;

					case BombableRock1:
					case BombableRock2:
						makeBombableRock(entity);
						break;

					case Padlock:
					case PadlockRedFrame:
					case PadlockRedFace:
					case PadlockBlueFace:
						makePadlock(entity);
						break;

					case CymbalPlant:
					case PinkFlower:
					case SpinningFlower:
					case BellbellPlant:
					case TrumpetPlant:
					case Munchlesia:
					case SavePoint:
					case HealingBlock:
						makeTrivial(entity);
						break;

					case RedArrowSigns:
						makeRedArrowSign(entity);
						break;

					case Tweester:
						makeTweester(entity);
						break;

					case StarBoxLaucher:
						makeStarBoxLauncher(entity);
						break;

					case SuperBlock:
						if (addedSuperBlock)
							throw new InvalidInputException("You may only have one super block per map!");
						makeSuperBlock(entity);
						addedSuperBlock = true;
						break;

					case BrickBlock:
					case MultiCoinBrick:
						makeBrickBlock(entity);
						break;

					case MultiTriggerBlock:
					case SingleTriggerBlock:
					case PowBlock:
						makeTriggerBlock(entity);
						break;

					case YellowBlock:
					case HiddenYellowBlock:
					case RedBlock:
					case HiddenRedBlock:
						makeItemBlock(entity);
						break;

					case WoodenCrate:
						makeItemCrate(entity);
						break;

					case Item:
						makeItem(entity);
						break;

					case Chest:
						makeChest(entity);
						break;

					case GiantChest:
						makeGiantChest(entity);
						break;

					case HiddenPanel:
						makeHiddenPanel(entity);
						break;

					case Signpost:
						makeSignpost(entity);
						break;

					case SimpleSpring:
						makeSimpleSpring(entity);
						break;

					case ScriptSpring:
						makeScriptSpring(entity);
						break;

					case BlueWarpPipe:
						makeBlueWarpPipe(entity);
						break;

					case RedSwitch:
						makeRedSwitch(entity);
						break;

					case BlueSwitch:
					case HugeBlueSwitch:
					case GreenStompSwitch:
						makeLimitedSwitch(entity);
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
					case Hammer3BlockTiny:
						makeHammerBlock(entity);
						break;

					case PushBlock:
						break; // dummy
				}
			}
			catch (InvalidInputException e) {
				InvalidInputException next = new InvalidInputException(
					"Error generating entity for %s%n%s", entity.getName(), e.getMessage());
				next.setStackTrace(e.getStackTrace());
				throw next;
			}
		}

		importLines.addAll(support);
		callbackLines.addAll(callbacks);
	}

	public List<String> getLines()
	{
		return body;
	}

	// shared data for entity types
	private void addSupport(List<Marker> entityList)
	{
		boolean[] loaded = new boolean[EntityType.values().length];

		for (Marker entity : entityList) {
			EntityType type = entity.entityComponent.type.get();
			if (!loaded[type.ordinal()]) {
				if (Resource.hasResource(ResourceType.EntitySupport, type.name + ".mpat"))
					support.addAll(Resource.getText(ResourceType.EntitySupport, type.name + ".mpat"));
				loaded[type.ordinal()] = true;
			}
		}
	}

	private void addCallback(String scriptName)
	{
		callbacks.add("#new:Script " + scriptName);
		callbacks.add("{");
		callbacks.add("\tReturn");
		callbacks.add("\tEnd");
		callbacks.add("}");
		callbacks.add("");
	}

	/*
	 * requires:
	 * - none!
	 */
	private void makeTrivial(Marker m)
	{
		body.add(String.format("\tCall  MakeEntity    ( .Entity:%s ~Vec4d:%s 80000000 )",
			m.entityComponent.type.get(),
			m.getName()
		));
	}

	/*
	 * requires:
	 * - flag name
	 * - has flag
	 */
	private void makeBrickBlock(Marker m) throws InvalidInputException
	{
		boolean hasFlag = m.entityComponent.hasFlag.get();
		String typeName = m.entityComponent.type.get().name;
		String flagName = m.entityComponent.flagName.get();

		// validate fields
		if (hasFlag)
			ScriptVariable.parseScriptVariable(flagName);

		body.add(String.format("\tCall  MakeEntity    ( .Entity:%s ~Vec4d:%s 80000000 )",
			typeName, m.getName()));

		if (hasFlag)
			body.add("\tCall  AssignBlockFlag   ( " + flagName + " )");
	}

	/*
	 * requires:
	 * - flag name
	 * - has flag
	 * - has callback
	 */
	private void makeTriggerBlock(Marker m) throws InvalidInputException
	{
		boolean hasFlag = m.entityComponent.hasFlag.get();
		String typeName = m.entityComponent.type.get().name;
		String flagName = m.entityComponent.flagName.get();

		// validate fields
		if (hasFlag)
			ScriptVariable.parseScriptVariable(flagName);

		body.add(String.format("\tCall  MakeEntity    ( .Entity:%s ~Vec4d:%s 80000000 )",
			typeName, m.getName()));

		if (hasFlag)
			body.add("\tCall  AssignBlockFlag   ( " + flagName + " )");

		if (m.entityComponent.hasCallback.get()) {
			String callbackName = "$Script_EVT_" + m.getName();
			body.add("\tCall  AssignScript    ( " + callbackName + " )");
			addCallback(callbackName);
		}
	}

	/*
	 * requires:
	 * - item name
	 * - flag name
	 * - has callback
	 */
	private void makeItemBlock(Marker m) throws InvalidInputException
	{
		String typeName = m.entityComponent.type.get().name;
		String itemName = m.entityComponent.itemName.get();
		String flagName = m.entityComponent.flagName.get();

		// validate fields
		ScriptVariable.parseScriptVariable(flagName);

		if (null == ProjectDatabase.getFromNamespace("Item").getID(itemName))
			throw new InvalidInputException("Invalid item name: " + itemName);

		body.add(String.format("\tCall  MakeEntity    ( .Entity:%s ~Vec4d:%s .Item:%s 80000000 )",
			typeName, m.getName(),
			itemName));

		body.add("\tCall  AssignBlockFlag   ( " + flagName + " )");

		if (m.entityComponent.hasCallback.get()) {
			String callbackName = "$Script_EVT_" + m.getName();
			body.add("\tCall  AssignScript    ( " + callbackName + " )");
			addCallback(callbackName);
		}
	}

	/*
	 * requires:
	 * - item
	 * - has item
	 * - flag
	 * - has callback
	 */
	private void makeItemCrate(Marker m) throws InvalidInputException
	{
		String typeName = m.entityComponent.type.get().name;
		String itemName = m.entityComponent.itemName.get();
		String flagName = m.entityComponent.flagName.get();
		boolean hasItem = m.entityComponent.hasItem.get();

		if (hasItem && null == ProjectDatabase.getFromNamespace("Item").getID(itemName))
			throw new InvalidInputException("Invalid item name: " + itemName);

		if (hasItem)
			ScriptVariable.parseScriptVariable(flagName);

		if (hasItem) {
			body.add(String.format("\tCall  MakeEntity    ( .Entity:%s ~Vec4d:%s .Item:%s 80000000 )",
				typeName, m.getName(), itemName));
			body.add("\tCall  AssignCrateFlag   ( " + flagName + " )");
		}
		else {
			body.add(String.format("\tCall  MakeEntity    ( .Entity:%s ~Vec4d:%s FFFFFFFF 80000000 )",
				typeName, m.getName()));
		}

		if (m.entityComponent.hasCallback.get()) {
			String callbackName = "$Script_EVT_" + m.getName();
			body.add("\tCall  AssignScript    ( " + callbackName + " )");
			addCallback(callbackName);
		}
	}

	/*
	 * requires:
	 * - item
	 * - item spawn mode
	 * - flag
	 */
	private void makeItem(Marker m) throws InvalidInputException
	{
		String itemName = m.entityComponent.itemName.get();
		String flagName = m.entityComponent.flagName.get();
		String itemSpawnName = m.entityComponent.itemSpawnMode.get();

		// validate fields
		ScriptVariable.parseScriptVariable(flagName);

		if (null == ProjectDatabase.getFromNamespace("Item").getID(itemName))
			throw new InvalidInputException("Invalid item name: " + itemName);

		if (null == ProjectDatabase.getFromNamespace("ItemSpawnMode").getID(itemSpawnName))
			throw new InvalidInputException("Invalid item spawn type: " + itemSpawnName);

		body.add(String.format("\tCall  MakeItemEntity    ( .Item:%s ~Vec3d:%s .ItemSpawnMode:%s %s )",
			itemName, m.getName(),
			itemSpawnName,
			flagName
		));
	}

	/*
	 * requires:
	 * - has callback
	 */
	private void makeRedSwitch(Marker m)
	{
		String typeName = m.entityComponent.type.get().name;

		body.add(String.format("\tCall  MakeEntity    ( .Entity:%s ~Vec4d:%s 80000000 )",
			typeName, m.getName()));

		if (m.entityComponent.hasCallback.get()) {
			String callbackName = "$Script_EVT_" + m.getName();
			body.add("\tCall  AssignScript    ( " + callbackName + " )");
			addCallback(callbackName);
		}
	}

	/*
	 * requires:
	 * - area flag index
	 * - has callback
	 */
	private void makeLimitedSwitch(Marker m) throws InvalidInputException
	{
		String typeName = m.entityComponent.type.get().name;

		int areaFlagIndex = m.entityComponent.areaFlagIndex.get();
		if (areaFlagIndex < 0 || areaFlagIndex >= ScriptVariable.AreaFlag.getMaxIndex())
			throw new InvalidInputException("(%s) %X is not a valid area flag index!", m.getName(), areaFlagIndex);

		String areaFlagName = ".AreaFlag_Entity_" + m.getName();
		generator.defineLines.add(String.format("#define %s %X", areaFlagName, areaFlagIndex));

		String indexName = "*AreaFlag[" + areaFlagName + "]";

		boolean hasFlag = m.entityComponent.hasSpawnFlag.get();
		String flagName = m.entityComponent.spawnFlagName.get();

		// validate fields
		if (hasFlag)
			ScriptVariable.parseScriptVariable(flagName);

		String indent = "\t";
		if (hasFlag) {
			body.add("\tIf  " + flagName + "  ==  .False");
			indent = "\t\t";
		}

		body.add(String.format("%sCall  MakeEntity    ( .Entity:%s ~Vec4d:%s 80000000 )",
			indent, typeName, m.getName()));
		body.add(indent + "Call  AssignAreaFlag      ( " + indexName + " )");

		if (m.entityComponent.hasCallback.get()) {
			String callbackName = "$Script_EVT_" + m.getName();
			body.add(indent + "Call  AssignScript    ( " + callbackName + " )");
			addCallback(callbackName);
		}

		if (hasFlag)
			body.add("\tEndIf");
	}

	/*
	 * requires:
	 * - sign angle
	 */
	private void makeRedArrowSign(Marker m)
	{
		body.add(String.format("\tCall  MakeEntity    ( .Entity:%s ~Vec4d:%s %d` 80000000 )",
			m.entityComponent.type.get(), m.getName(),
			m.entityComponent.signAngle.get()
		));
	}

	/*
	 * requires:
	 * - flag
	 * - collider (optional)
	 * - model (optional)
	 */
	private void makeBoardedFloor(Marker m) throws InvalidInputException
	{
		String typeName = m.entityComponent.type.get().name;
		String flagName = m.entityComponent.flagName.get();
		String colliderName = m.entityComponent.colliderName.get();
		String modelName = m.entityComponent.colliderName.get();

		String supportName = "$Script_BreakFloor_" + m.getName();

		boolean hasCollider = !colliderName.isEmpty();
		boolean hasModel = !modelName.isEmpty();

		// validate fields
		ScriptVariable.parseScriptVariable(flagName);
		if (hasCollider)
			generator.validateObject(typeName, "Wall", MapObjectType.COLLIDER, colliderName);
		if (hasModel)
			generator.validateObject(typeName, "Wall", MapObjectType.MODEL, modelName);

		body.add("\tIf  " + flagName + "  ==  .False");
		body.add(String.format("\t\tCall  MakeEntity    ( .Entity:%s ~Vec4d:%s 80000000 )",
			typeName, m.getName()));
		body.add("\t\tCall  AssignScript  ( " + supportName + " )");
		if (hasCollider || hasModel)
			body.add("\tElse");
		if (hasCollider)
			support.add("\t\tCall  ModifyColliderFlags   ( 00000000 ~Collider:" + colliderName + " 7FFFFE00 )");
		if (hasModel)
			support.add("\t\tCall  EnableModel   ( ~Model:" + modelName + " .False )");
		body.add("\tEndIf");

		support.add("#new:Script " + supportName);
		support.add("{");
		support.add("\tSet  " + flagName + "  .True");
		if (hasCollider)
			support.add("\tCall  ModifyColliderFlags   ( 00000000 ~Collider:" + colliderName + " 7FFFFE00 )");
		if (hasModel)
			support.add("\tCall  EnableModel   ( ~Model:" + modelName + " .False )");
		support.add("\tReturn");
		support.add("\tEnd");
		support.add("}");
		support.add("");
	}

	/*
	 * requires:
	 * - nothing!
	 */
	private void makeSignpost(Marker m)
	{
		String typeName = m.entityComponent.type.get().name;
		String callbackName = "ReadSign_" + m.getName();

		body.add(String.format("\tCall  MakeEntity    ( .Entity:%s ~Vec4d:%s 80000000 )",
			typeName, m.getName()));
		body.add("\tCall  AssignScript    ( $Script_" + callbackName + " )");

		callbacks.add("#new:Script $Script_" + callbackName);
		callbacks.add("{");
		callbacks.add("\tCall  DisablePlayerInput        ( .True )");
		callbacks.add("\tCall  ShowMessageAtScreenPos    ( $String_" + callbackName + " 000000A0 00000028 )");
		callbacks.add("\tCall  DisablePlayerInput        ( .False )");
		callbacks.add("\tReturn");
		callbacks.add("\tEnd");
		callbacks.add("}");
		callbacks.add("");

		callbacks.add("#new:String $String_" + callbackName);
		callbacks.add("{");
		callbacks.add("\t[DelayOff][STYLE:SIGN]");
		callbacks.add("\t[CenterX:FF]");
		callbacks.add("\t[Down:03]Write message here.");
		callbacks.add("\t[DelayOn][WAIT][END]");
		callbacks.add("}");
		callbacks.add("");
	}

	/*
	 * requires:
	 * - dest map name
	 * - dest entry ID
	 * - path marker
	 */
	private void makeTweester(Marker m) throws InvalidInputException
	{
		String typeName = m.entityComponent.type.get().name;
		String pathMarkerName = m.entityComponent.pathMarker.get();

		String destMap = m.entityComponent.targetMarker.get();
		String destMarkerName = m.entityComponent.gotoEntry.get();
		int destMarkerID = 0;
		if (m.entityComponent.useDestMarkerID.get()) {
			try {
				destMarkerID = DataUtils.parseIntString(destMarkerName);
			}
			catch (InvalidInputException e) {
				throw new StarRodException("Could not generate exit using invalid dest marker ID: " + destMarkerName);
			}
		}

		// validate fields
		generator.validateObject(typeName, "Path", MapObjectType.MARKER, pathMarkerName);

		String pathList = "$TweesterPathList_" + m.getName();
		String path = "$TweesterPath_" + m.getName();
		String callbackName = "$Script_TweesterLaunch_" + m.getName();

		body.add(String.format("\tCall  MakeEntity    ( .Entity:%s ~Vec4d:%s %s 80000000 )",
			typeName, m.getName(), pathList));
		body.add("\tCall  AssignScript    ( " + callbackName + " )");

		support.add("#new:TweesterPathList " + pathList);
		support.add("{");
		support.add("\t" + path + " FFFFFFFF");
		support.add("}");
		support.add("");

		support.add("#new:TweesterPath " + path);
		support.add("{");
		support.add("\t~Path3d:" + pathMarkerName + " 80000001");
		support.add("}");
		support.add("");

		callbacks.add("#new:Script " + callbackName);
		callbacks.add("{");
		callbacks.add("\tCall  DisablePlayerInput    ( .True )");
		callbacks.add("\tCall  DisablePlayerPhysics  ( .True )");

		if (m.entityComponent.useDestMarkerID.get())
			callbacks.add(String.format("\tCall  GotoMap   ( \"%s\" %08X )", destMap, destMarkerID));
		else
			callbacks.add(String.format("\tCall  GotoMap   ( \"%s\" ~Entry:%s:%s )", destMap, destMap, destMarkerName));

		callbacks.add("\tWait  100`");
		callbacks.add("\tReturn");
		callbacks.add("\tEnd");
		callbacks.add("}");
		callbacks.add("");
	}

	/*
	 * requires:
	 * - dest marker
	 */
	private void makeStarBoxLauncher(Marker m) throws InvalidInputException
	{
		String typeName = m.entityComponent.type.get().name;
		String destMarkerName = m.entityComponent.targetMarker.get();
		String callbackName = "StarBoxLaunch_" + m.getName();

		// validate fields
		generator.validateObject(typeName, "Dest", MapObjectType.MARKER, destMarkerName);

		body.add(String.format("\tCall  MakeEntity    ( .Entity:%s ~Vec4d:%s 80000000 )",
			typeName, m.getName()));
		body.add("\tCall  AssignScript    ( $Script_" + callbackName + " )");

		callbacks.add("#new:Script $Script_" + callbackName);
		callbacks.add("{");
		callbacks.add("\tSet  *Var[7] ~PosXd:" + destMarkerName);
		callbacks.add("\tSet  *Var[8] ~PosYd:" + destMarkerName);
		callbacks.add("\tSet  *Var[9] ~PosZd:" + destMarkerName);
		callbacks.add("\tExec  $Script_StarBoxLaunch");
		callbacks.add("\tReturn");
		callbacks.add("\tEnd");
		callbacks.add("}");
		callbacks.add("");
	}

	/*
	 * requires:
	 * - model name
	 * - flag
	 */
	private void makeHiddenPanel(Marker m) throws InvalidInputException
	{
		String typeName = m.entityComponent.type.get().name;
		String modelName = m.entityComponent.modelName.get();
		String flagName = m.entityComponent.flagName.get();

		// validate fields
		ScriptVariable.parseScriptVariable(flagName);
		generator.validateObject(typeName, "Dest", MapObjectType.MODEL, modelName);

		body.add(String.format("\tCall  MakeEntity    ( .Entity:%s ~Vec4d:%s ~Model:%s 80000000 )",
			typeName, m.getName(),
			modelName
		));

		body.add("\tCall  AssignPanelFlag   ( " + flagName + " )");
	}

	/*
	 * requires:
	 * - launch height
	 */
	private void makeSimpleSpring(Marker m) throws InvalidInputException
	{
		body.add(String.format("\tCall  MakeEntity    ( .Entity:%s ~Vec4d:%s %d` 80000000 )",
			m.entityComponent.type.get().name, m.getName(),
			m.entityComponent.springLaunchHeight.get()
		));
	}

	/*
	 * requires:
	 * - launch time
	 * - dest marker
	 */
	private void makeScriptSpring(Marker m) throws InvalidInputException
	{
		String typeName = m.entityComponent.type.get().name;
		String destMarkerName = m.entityComponent.targetMarker.get();
		String supportName = "$Script_ScriptedSpring_" + m.getName();

		// validate fields
		generator.validateObject(typeName, "Dest", MapObjectType.MARKER, destMarkerName);

		body.add(String.format("\tCall  MakeEntity    ( .Entity:%s ~Vec4d:%s 80000000 )",
			typeName, m.getName()));
		body.add("\tCall  AssignScript    ( " + supportName + " )");

		support.add("#new:Script " + supportName);
		support.add("{");
		support.add("\tCall  DisablePlayerInput    ( .True )");
		support.add("\tCall  DisablePlayerPhysics  ( .True )");
		support.add("\tCall  SetPlayerActionState  ( .ActionState:Launch )");
		support.add("\tWait  1`");
		support.add("\tExec  $Script_ScriptedSpring_FollowCam *Var[A]");
		support.add("\tCall  SetPlayerJumpscale    ( *Fixed[0.7] )");
		support.add(String.format("\tCall  PlayerJump            ( ~Vec3d:%s %d` )",
			m.entityComponent.targetMarker.get(),
			m.entityComponent.springLaunchArc.get()
		));
		support.add("\tKill  *Var[A]");
		support.add("\tCall  SetPlayerActionState  ( .ActionState:Idle )");
		support.add("\tCall  DisablePlayerPhysics  ( .False )");
		support.add("\tCall  DisablePlayerInput    ( .False )");
		support.add("\tReturn");
		support.add("\tEnd");
		support.add("}");
		support.add("");
	}

	/*
	 * requires:
	 * - flag
	 * - collider (optional)
	 */
	private void makeBombableRock(Marker m) throws InvalidInputException
	{
		String typeName = m.entityComponent.type.get().name;
		String colliderName = m.entityComponent.colliderName.get();
		String flagName = m.entityComponent.flagName.get();

		boolean usesCollider = !colliderName.isEmpty();

		// validate fields
		ScriptVariable.parseScriptVariable(flagName);
		if (usesCollider)
			generator.validateObject(typeName, "Wall", MapObjectType.COLLIDER, colliderName);

		String supportName = "$Script_RockDestroyed_" + m.getName();

		body.add("\tIf  " + flagName + "  ==  .False");
		body.add(String.format("\t\tCall  MakeEntity    ( .Entity:%s ~Vec4d:%s 80000000 )",
			typeName, m.getName()));
		body.add("\t\tCall  AssignScript  ( " + supportName + " )");
		if (usesCollider) {
			body.add("\tElse");
			body.add("\t\tCall  ModifyColliderFlags   ( 00000000 ~Collider:" + colliderName + " 7FFFFE00 )");
		}
		body.add("\tEndIf");

		support.add("#new:Script " + supportName);
		support.add("{");
		support.add("\tSet   " + flagName + "  .True");
		if (usesCollider)
			support.add("\tCall  ModifyColliderFlags   ( 00000000 ~Collider:" + colliderName + " 7FFFFE00 )");
		support.add("\tReturn");
		support.add("\tEnd");
		support.add("}");
		support.add("");
	}

	/*
	 * requires:
	 * - flag
	 * - collider (optional)
	 */
	private void makeHammerBlock(Marker m) throws InvalidInputException
	{
		String typeName = m.entityComponent.type.get().name;
		String colliderName = m.entityComponent.colliderName.get();
		String flagName = m.entityComponent.flagName.get();

		boolean usesCollider = !colliderName.isEmpty();

		// validate fields
		ScriptVariable.parseScriptVariable(flagName);
		if (usesCollider)
			generator.validateObject(typeName, "Wall", MapObjectType.COLLIDER, colliderName);

		String supportName = "$Script_BreakBlock_" + m.getName();

		body.add("\tIf  " + flagName + "  ==  .False");
		body.add(String.format("\t\tCall  MakeEntity    ( .Entity:%s ~Vec4d:%s 80000000 )",
			typeName, m.getName()));
		body.add("\t\tCall  AssignScript  ( " + supportName + " )");
		if (usesCollider) {
			body.add("\tElse");
			body.add("\t\tCall  ModifyColliderFlags   ( 00000000 ~Collider:" + colliderName + " 7FFFFE00 )");
		}
		body.add("\tEndIf");

		support.add("#new:Script " + supportName);
		support.add("{");
		support.add("\tSet   " + flagName + "  .True");
		if (usesCollider)
			support.add("\tCall  ModifyColliderFlags   ( 00000000 ~Collider:" + colliderName + " 7FFFFE00 )");
		support.add("\tReturn");
		support.add("\tEnd");
		support.add("}");
		support.add("");
	}

	/*
	 * requires:
	 * - item
	 * - flag
	 */
	private void makeChest(Marker m) throws InvalidInputException
	{
		String typeName = m.entityComponent.type.get().name;
		String itemName = m.entityComponent.itemName.get();
		String flagName = m.entityComponent.flagName.get();

		// validate fields
		ScriptVariable.parseScriptVariable(flagName);

		Integer itemID = ProjectDatabase.getFromNamespace("Item").getID(itemName);
		if (null == itemID)
			throw new InvalidInputException("Invalid item name: " + itemName);

		ItemRecord rec = ProjectDatabase.globalsData.items.get(itemID);
		if (rec == null)
			throw new InvalidInputException("Item ID not found in table: %X for %s", itemID, itemName);

		int itemClass;
		if ((rec.typeFlags & 8) != 0)
			itemClass = 1; // key item
		else if ((rec.typeFlags & 0x40) != 0)
			itemClass = 2; // badge item
		else
			itemClass = 0;

		String supportName = "$Script_OpenChest_" + m.getName();

		body.add(String.format("\tCall  MakeEntity    ( .Entity:%s ~Vec4d:%s 80000000 )",
			typeName, m.getName()));
		body.add("\tCall  AssignFlag    ( " + flagName + " )");
		body.add("\tCall  AssignScript  ( " + supportName + " )");

		support.add("#new:Script " + supportName);
		support.add("{");
		support.add("\tSet   *Var[A]  .Item:" + itemName);
		support.add("\tSet   *Var[B]  " + itemClass);
		support.add("\tSet   " + flagName + "  .True");
		support.add("\tExecWait  $Script_Chest_GiveItem");
		support.add("\tReturn");
		support.add("\tEnd");
		support.add("}");
		support.add("");
	}

	/*
	 * requires:
	 * - item
	 * - flag
	 */
	private void makeGiantChest(Marker m) throws InvalidInputException
	{
		String typeName = m.entityComponent.type.get().name;
		String itemName = m.entityComponent.itemName.get();
		String flagName = m.entityComponent.flagName.get();

		// validate fields
		ScriptVariable.parseScriptVariable(flagName);

		Integer itemID = ProjectDatabase.getFromNamespace("Item").getID(itemName);
		if (null == itemID)
			throw new InvalidInputException("Invalid item name: " + itemName);

		ItemRecord rec = ProjectDatabase.globalsData.items.get(itemID);
		if (rec == null)
			throw new InvalidInputException("Item ID not found in table: %X for %s", itemID, itemName);

		String monitorName = "$Script_MonitorGiantChest_" + m.getName();
		String stringName = "$String_OpenGiantChest_" + m.getName();

		body.add(String.format("\tCall  MakeEntity    ( .Entity:%s ~Vec4d:%s .Item:%s 80000000 )",
			typeName, m.getName(), itemName));
		body.add("\tCall  AssignFlag    ( " + flagName + " )");
		body.add("\tExec  " + monitorName);

		callbacks.add("#new:Script " + monitorName);
		callbacks.add("{");
		callbacks.add("\tIf  " + flagName + "  ==  .False");
		callbacks.add("\t\tLoop");
		callbacks.add("\t\t\tIf  " + flagName + "  ==  .True");
		callbacks.add("\t\t\t\tBreakLoop");
		callbacks.add("\t\t\tEndIf");
		callbacks.add("\t\t\tWait  1`");
		callbacks.add("\t\tEndLoop");
		callbacks.add("\t\tWait  60`");
		callbacks.add("\t\t% note: ShowMessageAtScreenPos overwrites Var[0], this is a bug in vanilla");
		callbacks.add("\t\tSet   *Var[4] *Var[0]");
		callbacks.add("\t\tExec  $Script_GiantChest_Music");
		callbacks.add("\t\tCall  ShowMessageAtScreenPos    ( " + stringName + " 000000A0 00000028 )");
		callbacks.add("\t\tCall  $Function_GiantChest_Support ( )");
		callbacks.add("\t\t% add your extra code here");
		callbacks.add("\tEndIf");
		callbacks.add("\tReturn");
		callbacks.add("\tEnd");
		callbacks.add("}");
		callbacks.add("");

		callbacks.add("#new:String " + stringName);
		callbacks.add("{");
		callbacks.add("\t[STYLE:NARRATE]");
		callbacks.add("\tYou got an item![BR]");
		callbacks.add("\t[WAIT][NEXT]");
		callbacks.add("\tAdditional information!");
		callbacks.add("\t[WAIT][END]");
		callbacks.add("}");
		callbacks.add("");
	}

	private void makePadlock(Marker m) throws InvalidInputException
	{
		String itemName = m.entityComponent.itemName.get();
		String flagName = m.entityComponent.flagName.get();

		// validate fields
		ScriptVariable.parseScriptVariable(flagName);

		Integer itemID = ProjectDatabase.getFromNamespace("Item").getID(itemName);
		if (null == itemID)
			throw new InvalidInputException("Invalid item name: " + itemName);

		ItemRecord rec = ProjectDatabase.globalsData.items.get(itemID);
		if (rec == null)
			throw new InvalidInputException("Item ID not found in table: %X for %s", itemID, itemName);

		support.add("#new:Function $Function_Lock_Support");
		support.add("{");
		support.add("\tADDIU     SP, SP, FFE8");
		support.add("\tSW        RA, 10 (SP)");
		support.add("\tJAL       ~Func:get_entity_by_index");
		support.add("\tLW        A0, 84 (A0)");
		support.add("\tCOPY      V1, V0");
		support.add("\tLW        A0, 0 (V1)");
		support.add("\tLUI       A1, 10");
		support.add("\tOR        A0, A0, A1");
		support.add("\tSW        A0, 0 (V1)");
		support.add("\tLW        RA, 10 (SP)");
		support.add("\tADDIU     V0, R0, 2");
		support.add("\tJR        RA");
		support.add("\tADDIU     SP, SP, 18");
		support.add("}");
		support.add("");

		support.add("#new:ItemList $ItemList_" + m.getName());
		support.add("{");
		support.add("\t.Item:" + itemName);
		support.add("\t00000000");
		support.add("}");
		support.add("");

		List<Generator> exitList = generator.map.scripts.generatorsTreeModel.getObjectsInCategory(GeneratorType.Exit);
		Exit lockedExit = null;
		for (Generator g : exitList) {
			Exit e = (Exit) g;
			if (e.type.get() != ExitType.SingleDoor && e.type.get() != ExitType.DoubleDoor)
				continue;

			String lockName = e.lockName.get();
			if (lockName != null && !lockName.isEmpty()) {
				if (lockName.equals(m.getName())) {
					if (lockedExit != null)
						throw new InvalidInputException("Lock " + lockName + " is used by multiple Exits!");
					lockedExit = e;
				}
			}
		}

		support.add("#new:Script $Script_CheckLock_" + m.getName());
		support.add("{");
		support.add("\tCall  802D6420 ( )");
		support.add("\tIf  *Var[0]  ==  00000000");
		support.add("\t\tCall  ShowMessageAtScreenPos    ( 001D00D8 000000A0 00000028 ) % It's locked! You can't open it.");
		support.add("\t\tCall  802D6954 ( )");
		support.add("\t\tReturn");
		support.add("\tEndIf");
		support.add("\tIf  *Var[0]  ==  FFFFFFFF");
		support.add("\t\tCall  802D6954 ( )");
		support.add("\t\tReturn");
		support.add("\tEndIf");
		support.add("\tCall  PlaySoundAt       ( 00000269 00000000 ~Vec3d:" + m.getName() + " )");
		support.add(String.format("\tSet   *Var[0]  *MapVar[.MapVar_Entity_%s]", m.getName()));
		support.add("\tCall  $Function_Lock_Support ( )");
		support.add("\tWait  5`");
		support.add("\tCall  RemoveKeyItemAt   ( *Var[1] )");
		support.add("\tSet   " + flagName + "  .True");
		support.add("\tCall  802D6954 ( )");
		support.add("\tUnbind");
		if (lockedExit != null)
			support.add(String.format("\tBind  $Script_%s .Trigger:WallPressA ~Collider:%s 00000001 00000000",
				lockedExit.getName(), lockedExit.colliderName.get()));
		support.add("\tReturn");
		support.add("\tEnd");
		support.add("\t}");
		support.add("");
	}

	/*
	 * requires
	 * - flag
	 * - map variable index
	 */
	private void makeSuperBlock(Marker m) throws InvalidInputException
	{
		String typeName = m.entityComponent.type.get().name;
		String flagName = m.entityComponent.flagName.get();

		String scriptName = "$Script_SuperBlock";

		// validate fields
		ScriptVariable.parseScriptVariable(flagName);

		int mapVar = m.entityComponent.autoMapVar.get() ? generator.getNextMapVarIndex() : m.entityComponent.mapVarIndex.get();
		if (mapVar < 0 || mapVar >= ScriptVariable.MapVar.getMaxIndex())
			throw new InvalidInputException("(MapVarIndex for %s) %X is not a valid map var index!", m.getName(), mapVar);

		String varName = ".MapVar_Entity_" + m.getName();
		generator.defineLines.add(String.format("#define %s %X", varName, mapVar));

		body.add(String.format("\tCall  MakeEntity    ( .Entity:%s ~Vec4d:%s 80000000 )",
			typeName, m.getName()));
		body.add(String.format("\tSet   *MapVar[%X]  *Var[0]", mapVar));
		body.add("\tCall  AssignBlockFlag   ( " + flagName + " )");
		body.add("\tCall  AssignScript      ( " + scriptName + " )");

		List<String> lines = ScriptGenerator.readScriptTemplate("SuperBlock.mpat");
		for (int i = 0; i < lines.size(); i++) {
			String original = lines.get(i);
			String fixed = original.replaceAll("##FLAG", flagName);
			fixed = fixed.replaceAll("##MAPVARINDEX", varName);
			lines.set(i, fixed);
		}

		support.addAll(lines);
	}

	private void makeBlueWarpPipe(Marker m) throws InvalidInputException
	{
		String typeName = m.entityComponent.type.get().name;
		String flagName = m.entityComponent.flagName.get();
		String entryMarkerName = m.entityComponent.pipeEntry.get();

		String destMap = m.entityComponent.gotoMap.get();
		String destMarkerName = m.entityComponent.gotoEntry.get();
		int destMarkerID = 0;
		if (m.entityComponent.useDestMarkerID.get()) {
			try {
				destMarkerID = DataUtils.parseIntString(destMarkerName);
			}
			catch (InvalidInputException e) {
				throw new StarRodException("Could not generate exit using invalid dest marker ID: " + destMarkerName);
			}
		}

		// validate fields
		ScriptVariable.parseScriptVariable(flagName);
		generator.validateObject(typeName, "Entry Marker", MarkerType.Entry, entryMarkerName);

		String gotoScriptName = "$Script_GotoMap_" + m.getName();
		String raiseScriptName = "$Script_RaisePipe_" + m.getName();

		if (m.entityComponent.hasAreaFlag.get()) {
			int areaFlagIndex = m.entityComponent.areaFlagIndex.get();
			if (areaFlagIndex < 0 || areaFlagIndex >= ScriptVariable.AreaFlag.getMaxIndex())
				throw new InvalidInputException("(%s) %X is not a valid area flag index!", m.getName(), areaFlagIndex);

			String areaFlagName = ".AreaFlag_Entity_" + m.getName();
			generator.defineLines.add(String.format("#define %s %X", areaFlagName, areaFlagIndex));

			body.add("\tIf  " + flagName + "  ==  .False");
			body.add(String.format("\t\t%% raise this pipe by setting *AreaFlag[%s] = .True", areaFlagName));
			body.add(String.format("\t\tBind  %s .Trigger:AreaFlagSet *AreaFlag[%s] 00000001 00000000",
				raiseScriptName, areaFlagName));
			body.add("\tEndIf");
		}

		body.add(String.format("\tCall  MakeEntity    ( .Entity:%s ~Vec4d:%s ~Entry:%s %s ~Index:%s 80000000 )",
			typeName, m.getName(), entryMarkerName, gotoScriptName, flagName));

		support.add("#new:Script " + gotoScriptName);
		support.add("{");

		if (m.entityComponent.useDestMarkerID.get())
			support.add(String.format("\tCall  GotoMap   ( \"%s\" %08X )", destMap, destMarkerID));
		else
			support.add(String.format("\tCall  GotoMap   ( \"%s\" ~Entry:%s:%s )", destMap, destMap, destMarkerName));

		support.add("\tWait  100`");
		support.add("\tReturn");
		support.add("\tEnd");
		support.add("}");
		support.add("");

		support.add("#new:Script " + raiseScriptName);
		support.add("{");
		support.add("\tWait  10`");
		support.add("\tCall  PlaySound  ( 0000208E )");
		support.add("\tSet   " + flagName + " .True");
		support.add("\tUnbind");
		support.add("\tReturn");
		support.add("\tEnd");
		support.add("}");
		support.add("");
	}
}
