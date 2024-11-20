package game.shared;

import static game.shared.struct.StructType.ARRAY;
import static game.shared.struct.StructType.UNIQUE;

import game.battle.struct.Actor;
import game.battle.struct.DefenseTable;
import game.battle.struct.DmaArgTable;
import game.battle.struct.ForegroundList;
import game.battle.struct.Formation;
import game.battle.struct.FormationTable;
import game.battle.struct.IdleAnimations;
import game.battle.struct.IntVector;
import game.battle.struct.PartsTable;
import game.battle.struct.SpecialFormation;
import game.battle.struct.Stage;
import game.battle.struct.StageTable;
import game.battle.struct.StatusTable;
import game.map.struct.EntryList;
import game.map.struct.Header;
import game.map.struct.ItemList;
import game.map.struct.LavaResetList;
import game.map.struct.TriggerCoord;
import game.map.struct.npc.AISettings;
import game.map.struct.npc.AnimationList;
import game.map.struct.npc.Npc;
import game.map.struct.npc.NpcGroup;
import game.map.struct.npc.NpcGroupList;
import game.map.struct.npc.NpcSettings;
import game.map.struct.shop.BadgeShopInventory;
import game.map.struct.shop.ShopInventory;
import game.map.struct.shop.ShopItemPositions;
import game.map.struct.shop.ShopOwner;
import game.map.struct.shop.ShopPriceList;
import game.map.struct.special.NpcList;
import game.map.struct.special.SearchBushEvent;
import game.map.struct.special.ShakeTreeEvent;
import game.map.struct.special.TreeDropList;
import game.map.struct.special.TreeEffectVectors;
import game.map.struct.special.TreeModelList;
import game.map.struct.special.TweesterPath;
import game.map.struct.special.TweesterPathList;
import game.shared.struct.StructType;
import game.shared.struct.TypeMap;
import game.shared.struct.f3dex2.DisplayList;
import game.shared.struct.f3dex2.DisplayMatrix;
import game.shared.struct.f3dex2.VertexTable;
import game.shared.struct.miniscript.EntityModelScript;
import game.shared.struct.miniscript.EntityScript;
import game.shared.struct.miniscript.HudElementScript;
import game.shared.struct.miniscript.ItemEntityScript;
import game.shared.struct.miniscript.ModelAnimation;
import game.shared.struct.miniscript.SparkleScript;
import game.shared.struct.other.AnimatedModelNode;
import game.shared.struct.other.AnimatedModelTree;
import game.shared.struct.other.EntityBP;
import game.shared.struct.other.StringASCII;
import game.shared.struct.other.StringMarkup;
import game.shared.struct.other.StringSJIS;
import game.shared.struct.other.VectorList;

public abstract class StructTypes
{
	public final static TypeMap sharedTypes;
	public final static TypeMap allTypes;

	public static final StructType UnknownT;
	public static final StructType FunctionT;
	public static final StructType JumpTableT;
	public static final StructType ScriptT;
	public static final StructType SjisT;
	public static final StructType AsciiT;
	public static final StructType StringT;

	public static final StructType ByteTableT;
	public static final StructType ShortTableT;
	public static final StructType IntTableT;
	public static final StructType StringTableT;
	public static final StructType DataTableT;
	public static final StructType FloatTableT;
	public static final StructType ConstDoubleT;

	public static final StructType EntityBPT;
	public static final StructType EntityScriptT;
	public static final StructType EntityModelScriptT;

	public static final StructType HudElementScriptT;
	public static final StructType ItemEntityScriptT;
	public static final StructType SparkleScriptT;

	public static final StructType AnimatedModelTreeT;
	public static final StructType AnimatedModelNodeT;
	public static final StructType ModelAnimationT;

	public static final StructType VectorListT;

	public static final StructType DisplayListT;
	public static final StructType DisplayMatrixT;
	public static final StructType VertexTableT;

	// ---------------------------------------------------------------------

	public static final TypeMap battleTypes;

	public static final StructType UseScriptT; // Used by Items/Moves/etc

	public static final StructType DmaArgTableT;
	public static final StructType StageTableT;
	public static final StructType StageT;
	public static final StructType ForegroundListT;

	public static final StructType FormationTableT;
	public static final StructType IndexedFormationT;
	public static final StructType SpecialFormationT;
	public static final StructType Vector3dT;

	public static final StructType ActorT;
	public static final StructType StatusTableT;
	public static final StructType DefenseTableT;
	public static final StructType PartsTableT;
	public static final StructType IdleAnimationsT;

	// ---------------------------------------------------------------------

	public static final TypeMap mapTypes;

	public static final StructType HeaderT;
	public static final StructType InitFunctionT;
	public static final StructType GetTattleFunctionT;
	public static final StructType MainScriptT;
	public static final StructType EntryListT;

	public static final StructType NpcGroupListT;
	public static final StructType NpcGroupT;
	public static final StructType NpcSettingsT;
	public static final StructType AISettingsT;
	public static final StructType ExtraAnimationListT;

	public static final StructType NpcT;

	public static final StructType BadgeShopInventoryT;
	public static final StructType ShopOwnerT;
	public static final StructType ShopItemPositionsT;
	public static final StructType ShopInventoryT;
	public static final StructType ShopPriceListT;

	public static final StructType ItemListT;
	public static final StructType TriggerCoordT;

	public static final StructType TweesterPathListT;
	public static final StructType TweesterPathT;

	public static final StructType SearchBushEventT;
	public static final StructType ShakeTreeEventT;
	public static final StructType TreeModelListT;
	public static final StructType TreeDropListT;
	public static final StructType TreeEffectVectorsT;

	public static final StructType NpcListT;
	public static final StructType LavaResetListT;

	static {
		sharedTypes = new TypeMap();

		// @formatter:off
		UnknownT	= new StructType(sharedTypes, "Unknown");
		FunctionT	= new StructType(sharedTypes, "Function");
		JumpTableT	= new StructType(sharedTypes, "JumpTable");
		ScriptT		= new StructType(sharedTypes, "Script");
		SjisT		= new StructType(sharedTypes, "SJIS");
		AsciiT		= new StructType(sharedTypes, "ASCII");
		StringT		= new StructType(sharedTypes, "String");
		sharedTypes.put("Message", StringT);

		ByteTableT	= new StructType(sharedTypes, "ByteTable");
		ShortTableT	= new StructType(sharedTypes, "ShortTable");
		IntTableT	= new StructType(sharedTypes, "IntTable");
		FloatTableT	= new StructType(sharedTypes, "FloatTable");
		StringTableT = new StructType(sharedTypes, "StringTable");
		DataTableT	= new StructType(sharedTypes, "DataTable");
		sharedTypes.put("Data", DataTableT);

		ConstDoubleT= new StructType(sharedTypes, 8, "ConstDouble");

		VectorListT = new StructType(sharedTypes, "VectorList");

		DisplayListT = new StructType(sharedTypes, "DisplayList");
		DisplayMatrixT = new StructType(sharedTypes, 0x40, "RSPMatrix");
		VertexTableT = new StructType(sharedTypes, "VertexTable");

		EntityBPT = new StructType(sharedTypes, 0x24, "EntityBP");
		EntityScriptT = new StructType(sharedTypes, EntityScript.instance.scriptName);
		EntityModelScriptT = new StructType(sharedTypes, EntityModelScript.instance.scriptName);

		HudElementScriptT = new StructType(sharedTypes, HudElementScript.instance.scriptName);
		ItemEntityScriptT = new StructType(sharedTypes, ItemEntityScript.instance.scriptName);
		SparkleScriptT = new StructType(sharedTypes, SparkleScript.instance.scriptName);

		AnimatedModelTreeT = new StructType(sharedTypes, "AnimatedModelTree");
		AnimatedModelNodeT = new StructType(sharedTypes, 0x2C, "AnimatedModelNode");
		ModelAnimationT = new StructType(sharedTypes, "ModelAnimation");
		// @formatter:on

		SjisT.bind(StringSJIS.instance);
		AsciiT.bind(StringASCII.instance);
		StringT.bind(StringMarkup.instance);
		VectorListT.bind(VectorList.instance);
		VertexTableT.bind(VertexTable.instance);

		DisplayListT.bind(DisplayList.instance);
		DisplayMatrixT.bind(DisplayMatrix.instance);

		EntityBPT.bind(EntityBP.instance);
		EntityScriptT.bind(EntityScript.instance);
		EntityModelScriptT.bind(EntityModelScript.instance);

		HudElementScriptT.bind(HudElementScript.instance);
		ItemEntityScriptT.bind(ItemEntityScript.instance);
		SparkleScriptT.bind(SparkleScript.instance);

		AnimatedModelTreeT.bind(AnimatedModelTree.instance);
		AnimatedModelNodeT.bind(AnimatedModelNode.instance);
		ModelAnimationT.bind(ModelAnimation.instance);

		// ---------------------------------------------------------------------

		battleTypes = new TypeMap();
		battleTypes.add(sharedTypes);

		// @formatter:off
		UseScriptT			= new StructType(battleTypes, "Script_Use", ScriptT);

		DmaArgTableT		= new StructType(battleTypes, "DmaArgTable", StructType.UNIQUE);
		StageTableT			= new StructType(battleTypes, "StageTable", StructType.UNIQUE);
		StageT				= new StructType(battleTypes, 0x28, "Stage");
		ForegroundListT 	= new StructType(battleTypes, "ForegroundModelList");

		FormationTableT		= new StructType(battleTypes, "FormationTable", StructType.UNIQUE);
		IndexedFormationT	= new StructType(battleTypes, "Formation");
		SpecialFormationT	= new StructType(battleTypes, "SpecialFormation");
		Vector3dT			= new StructType(battleTypes, "Vector3D");

		ActorT 				= new StructType(battleTypes, 0x28, "Actor");
		StatusTableT		= new StructType(battleTypes, "StatusTable");
		DefenseTableT		= new StructType(battleTypes, "DefenseTable");
		PartsTableT			= new StructType(battleTypes, "PartsTable");
		IdleAnimationsT		= new StructType(battleTypes, "IdleAnimations");
		// @formatter:on

		DmaArgTableT.bind(DmaArgTable.instance);
		StageTableT.bind(StageTable.instance);
		StageT.bind(Stage.instance);
		ForegroundListT.bind(ForegroundList.instance);

		FormationTableT.bind(FormationTable.instance);
		IndexedFormationT.bind(Formation.instance);
		SpecialFormationT.bind(SpecialFormation.instance);
		Vector3dT.bind(IntVector.instance);

		ActorT.bind(Actor.instance);
		StatusTableT.bind(StatusTable.instance);
		DefenseTableT.bind(DefenseTable.instance);
		PartsTableT.bind(PartsTable.instance);
		IdleAnimationsT.bind(IdleAnimations.instance);

		// ---------------------------------------------------------------------

		mapTypes = new TypeMap();
		mapTypes.add(sharedTypes);

		// @formatter:off
		HeaderT 			= new StructType(mapTypes, 0x40, "Header", UNIQUE);
		InitFunctionT 		= new StructType(mapTypes, "Function_Init", FunctionT, UNIQUE);
		GetTattleFunctionT	= new StructType(mapTypes, "Function_GetTattle", FunctionT, UNIQUE);
		MainScriptT			= new StructType(mapTypes, "Script_Main", ScriptT, UNIQUE);
		EntryListT 			= new StructType(mapTypes, "EntryList", UNIQUE);

		NpcT	 			= new StructType(mapTypes, 0x1F0, "Npc", ARRAY);
		NpcGroupListT 		= new StructType(mapTypes, "NpcGroupList");
		NpcGroupT 			= new StructType(mapTypes, "NpcGroup");
		NpcSettingsT 		= new StructType(mapTypes, 0x2C, "NpcSettings");
		AISettingsT 		= new StructType(mapTypes, "AISettings");
		ExtraAnimationListT = new StructType(mapTypes, "ExtraAnimationList");

		BadgeShopInventoryT = new StructType(mapTypes, "BadgeShopInventory");
		ShopOwnerT			= new StructType(mapTypes, "ShopOwnerNPC");
		ShopItemPositionsT 	= new StructType(mapTypes, "ShopItemPositions");
		ShopInventoryT 		= new StructType(mapTypes, "ShopInventory");
		ShopPriceListT 		= new StructType(mapTypes, "ShopPriceList");

		ItemListT			= new StructType(mapTypes, "ItemList");
		TriggerCoordT		= new StructType(mapTypes, "TriggerCoord");

		TweesterPathListT 	= new StructType(mapTypes, "TweesterPathList");
		TweesterPathT 		= new StructType(mapTypes, "TweesterPath");

		SearchBushEventT	= new StructType(mapTypes, "SearchBushEvent");
		ShakeTreeEventT		= new StructType(mapTypes, "ShakeTreeEvent");
		TreeModelListT		= new StructType(mapTypes, "TreeModelList");
		TreeDropListT		= new StructType(mapTypes, "TreeDropList");
		TreeEffectVectorsT	= new StructType(mapTypes, "TreeEffectVectors");
		NpcListT			= new StructType(mapTypes, "NpcList");

		LavaResetListT		= new StructType(mapTypes, "LavaResetList");
		// @formatter:on

		HeaderT.bind(Header.instance);
		EntryListT.bind(EntryList.instance);

		NpcT.bind(Npc.instance);

		NpcGroupListT.bind(NpcGroupList.instance);
		NpcGroupT.bind(NpcGroup.instance);
		NpcSettingsT.bind(NpcSettings.instance);
		AISettingsT.bind(AISettings.instance);
		ExtraAnimationListT.bind(AnimationList.instance);

		BadgeShopInventoryT.bind(BadgeShopInventory.instance);
		ShopOwnerT.bind(ShopOwner.instance);
		ShopItemPositionsT.bind(ShopItemPositions.instance);
		ShopInventoryT.bind(ShopInventory.instance);
		ShopPriceListT.bind(ShopPriceList.instance);

		ItemListT.bind(ItemList.instance);
		TriggerCoordT.bind(TriggerCoord.instance);

		TweesterPathListT.bind(TweesterPathList.instance);
		TweesterPathT.bind(TweesterPath.instance);

		SearchBushEventT.bind(SearchBushEvent.instance);
		ShakeTreeEventT.bind(ShakeTreeEvent.instance);
		TreeModelListT.bind(TreeModelList.instance);
		TreeDropListT.bind(TreeDropList.instance);
		TreeEffectVectorsT.bind(TreeEffectVectors.instance);
		NpcListT.bind(NpcList.instance);
		LavaResetListT.bind(LavaResetList.instance);

		// ---------------------------------------------------------------------

		allTypes = new TypeMap();
		allTypes.add(sharedTypes);
		allTypes.add(battleTypes);
		allTypes.add(mapTypes);
	}
}
