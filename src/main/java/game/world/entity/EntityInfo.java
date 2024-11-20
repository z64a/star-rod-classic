package game.world.entity;

import static app.Directories.DUMP_ENTITY_SRC;
import static game.world.entity.EntityMenuGroup.*;
import static game.world.entity.EntitySet.*;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.HashMap;

import app.Environment;
import app.Resource;
import app.Resource.ResourceType;
import app.input.InvalidInputException;
import common.Vector3f;
import game.map.Axis;
import game.map.BoundingBox;
import game.map.editor.render.SortedRenderable;
import game.map.editor.selection.PickRay;
import game.map.editor.selection.PickRay.PickHit;
import game.map.marker.Marker;
import game.map.shape.TransformMatrix;
import game.shared.DataUtils;
import game.shared.ProjectDatabase;
import game.world.entity.EntityModel.RenderablePart;
import renderer.shaders.RenderState;
import renderer.shaders.ShaderManager;
import renderer.shaders.scene.LineShader;
import util.Logger;

public abstract class EntityInfo
{
	private static HashMap<String, EntityType> typeNameMap = new HashMap<>();

	// @formatter:off
	private static int fieldBitPos = 0;
	public static final int FIELD_NONE			= 0;

	public static final int FIELD_HAS_FLAG		= 1 << fieldBitPos++;
	public static final int FIELD_FLAG			= 1 << fieldBitPos++;
	public static final int FIELD_HAS_SPAWN_FLAG = 1 << fieldBitPos++;
	public static final int FIELD_SPAWN_FLAG	= 1 << fieldBitPos++;

	public static final int FIELD_HAS_CALLBACK	= 1 << fieldBitPos++;
	public static final int FIELD_HAS_ITEM		= 1 << fieldBitPos++;
	public static final int FIELD_ITEM			= 1 << fieldBitPos++;
	public static final int FIELD_ITEM_SPAWN	= 1 << fieldBitPos++;

	public static final int FIELD_HAS_AREA_FLAG	= 1 << fieldBitPos++;
	public static final int FIELD_AREA_FLAG		= 1 << fieldBitPos++;
	public static final int FIELD_MODEL			= 1 << fieldBitPos++;
	public static final int FIELD_COLLIDER		= 1 << fieldBitPos++;

	public static final int FIELD_TARGET_MARKER	= 1 << fieldBitPos++;
	public static final int FIELD_LAUNCH_H		= 1 << fieldBitPos++;
	public static final int FIELD_LAUNCH_T		= 1 << fieldBitPos++;
	public static final int FIELD_ANGLE			= 1 << fieldBitPos++;

	public static final int FIELD_PATH_MARKER	= 1 << fieldBitPos++;
	public static final int FIELD_DEST_MAP		= 1 << fieldBitPos++;
	public static final int FIELD_DEST_ENTRY	= 1 << fieldBitPos++;
	public static final int FIELD_PIPE_ENTRY	= 1 << fieldBitPos++;

	public static final int FIELD_MAP_VAR		= 1 << fieldBitPos++;

	public static final int PROPERTY_HAS_SQUARE_SHADOW	= 1 << fieldBitPos++;
	public static final int PROPERTY_HAS_ROUND_SHADOW	= 1 << fieldBitPos++;
	// @formatter:on

	public static enum EntityType
	{
		// @formatter:off
		BoardedFloor			(OVERLAY_STANDARD, OtherAreas, 0x802BCE84, FIELD_FLAG | FIELD_COLLIDER | FIELD_MODEL),
		BombableRock1			(OVERLAY_STANDARD, OtherAreas, 0x802BCF00, FIELD_FLAG | FIELD_COLLIDER),
		BombableRock2			(OVERLAY_STANDARD, OtherAreas, 0x802BCF24, FIELD_FLAG | FIELD_COLLIDER),
		Padlock					(OVERLAY_STANDARD, OtherAreas, 0x802BCD68, FIELD_FLAG | FIELD_ITEM | FIELD_MAP_VAR),
		PadlockRedFrame			(OVERLAY_STANDARD, OtherAreas, 0x802BCD8C, FIELD_FLAG | FIELD_ITEM | FIELD_MAP_VAR),
		PadlockRedFace			(OVERLAY_STANDARD, OtherAreas, 0x802BCDB0, FIELD_FLAG | FIELD_ITEM | FIELD_MAP_VAR),
		PadlockBlueFace			(OVERLAY_STANDARD, OtherAreas, 0x802BCDD4, FIELD_FLAG | FIELD_ITEM | FIELD_MAP_VAR),

		CymbalPlant				(OVERLAY_JUNGLE_RUGGED, JanIwaOnly, 0x802BC788, FIELD_NONE),
		PinkFlower				(OVERLAY_JUNGLE_RUGGED, JanIwaOnly, 0x802BC7AC, FIELD_NONE),
		SpinningFlower			(OVERLAY_JUNGLE_RUGGED, JanIwaOnly, 0x802BC7F4, FIELD_NONE),
		BellbellPlant			(OVERLAY_JUNGLE_RUGGED, JanIwaOnly, 0x802BCBD8, FIELD_NONE),
		TrumpetPlant			(OVERLAY_JUNGLE_RUGGED, JanIwaOnly, 0x802BCBFC, FIELD_NONE),
		Munchlesia				(OVERLAY_JUNGLE_RUGGED, JanIwaOnly, 0x802BCC20, FIELD_NONE),
		RedArrowSigns			(OVERLAY_JUNGLE_RUGGED, JanIwaOnly, 0x802BCD9C, FIELD_ANGLE),

		Tweester				(OVERLAY_TOYBOX_DESERT, SbkOmoOnly, 0x802BCA74, FIELD_DEST_MAP | FIELD_DEST_ENTRY | FIELD_PATH_MARKER),
		StarBoxLaucher			(OVERLAY_TOYBOX_DESERT, SbkOmoOnly, 0x802BCB44, FIELD_TARGET_MARKER),

		SavePoint				(COMMON, Block, 0x802E9A18, FIELD_NONE | PROPERTY_HAS_SQUARE_SHADOW),
		HealingBlock			(COMMON, Block, 0x802EA7E0, FIELD_NONE | PROPERTY_HAS_SQUARE_SHADOW),
		SuperBlock				(COMMON, Block, 0x802EA910, FIELD_FLAG | FIELD_MAP_VAR | PROPERTY_HAS_SQUARE_SHADOW),
		BrickBlock				(COMMON, Block, 0x802EA0C4, FIELD_HAS_FLAG | FIELD_FLAG | PROPERTY_HAS_SQUARE_SHADOW),
		MultiCoinBrick			(COMMON, Block, 0x802EA0E8, FIELD_HAS_FLAG | FIELD_FLAG | PROPERTY_HAS_SQUARE_SHADOW),
		YellowBlock				(COMMON, Block, 0x802EA564, FIELD_FLAG | FIELD_ITEM | FIELD_HAS_CALLBACK | PROPERTY_HAS_SQUARE_SHADOW),
		HiddenYellowBlock		(COMMON, Block, 0x802EA588, FIELD_FLAG | FIELD_ITEM | FIELD_HAS_CALLBACK | PROPERTY_HAS_SQUARE_SHADOW),
		RedBlock				(COMMON, Block, 0x802EA5AC, FIELD_FLAG | FIELD_ITEM | FIELD_HAS_CALLBACK | PROPERTY_HAS_SQUARE_SHADOW),
		HiddenRedBlock			(COMMON, Block, 0x802EA5D0, FIELD_FLAG | FIELD_ITEM | FIELD_HAS_CALLBACK | PROPERTY_HAS_SQUARE_SHADOW),

		Item					(NONE, Misc, -1, FIELD_FLAG | FIELD_ITEM | FIELD_ITEM_SPAWN | PROPERTY_HAS_ROUND_SHADOW), // logical entity, not actual one
		Chest					(COMMON, Misc, 0x802EAE30, FIELD_FLAG | FIELD_ITEM),
		GiantChest				(COMMON, Misc, 0x802EAE0C, FIELD_FLAG | FIELD_ITEM),
		WoodenCrate				(COMMON, Misc, 0x802EAED4, FIELD_FLAG | FIELD_ITEM | FIELD_HAS_CALLBACK | FIELD_HAS_ITEM),
		HiddenPanel				(COMMON, Misc, 0x802EAB04, FIELD_FLAG | FIELD_MODEL),
		Signpost				(COMMON, Misc, 0x802EAFDC, FIELD_NONE),
		SimpleSpring			(COMMON, Misc, 0x802EAA54, FIELD_LAUNCH_H),
		ScriptSpring			(COMMON, Misc, 0x802EAA30, FIELD_TARGET_MARKER |  FIELD_LAUNCH_T),
		BlueWarpPipe			(COMMON, Misc, 0x802EAF80, FIELD_FLAG | FIELD_PIPE_ENTRY | FIELD_DEST_MAP | FIELD_DEST_ENTRY | FIELD_HAS_AREA_FLAG | FIELD_AREA_FLAG),
		PushBlock				(COMMON, Hidden, 0x802EA2BC, FIELD_NONE), // logical entity which cannot normally be created

		RedSwitch				(COMMON, Mechanism, 0x802E9BB0, FIELD_HAS_CALLBACK),
		BlueSwitch				(COMMON, Mechanism, 0x802E9BD4, FIELD_HAS_CALLBACK | FIELD_AREA_FLAG | FIELD_HAS_SPAWN_FLAG | FIELD_SPAWN_FLAG),
		HugeBlueSwitch			(COMMON, Mechanism, 0x802E9BF8, FIELD_HAS_CALLBACK | FIELD_AREA_FLAG | FIELD_HAS_SPAWN_FLAG | FIELD_SPAWN_FLAG),
		GreenStompSwitch		(COMMON, Mechanism, 0x802E9C1C, FIELD_HAS_CALLBACK | FIELD_AREA_FLAG | FIELD_HAS_SPAWN_FLAG | FIELD_SPAWN_FLAG),
		SingleTriggerBlock		(COMMON, Mechanism, 0x802EA5F4, FIELD_HAS_CALLBACK | FIELD_FLAG),
		MultiTriggerBlock		(COMMON, Mechanism, 0x802EA07C, FIELD_HAS_CALLBACK),
		PowBlock				(COMMON, Mechanism, 0x802EA2E0, FIELD_HAS_CALLBACK | PROPERTY_HAS_SQUARE_SHADOW),

		Hammer1Block			(COMMON, HammerBlock, 0x802EA10C, FIELD_FLAG | FIELD_COLLIDER),
		Hammer1BlockWide		(COMMON, HammerBlock, 0x802EA130, FIELD_FLAG | FIELD_COLLIDER),
		Hammer1BlockThick		(COMMON, HammerBlock, 0x802EA154, FIELD_FLAG | FIELD_COLLIDER),
		Hammer1BlockTiny		(COMMON, HammerBlock, 0x802EA178, FIELD_FLAG | FIELD_COLLIDER),
		Hammer2Block			(COMMON, HammerBlock, 0x802EA19C, FIELD_FLAG | FIELD_COLLIDER),
		Hammer2BlockWide		(COMMON, HammerBlock, 0x802EA1C0, FIELD_FLAG | FIELD_COLLIDER),
		Hammer2BlockThick		(COMMON, HammerBlock, 0x802EA1E4, FIELD_FLAG | FIELD_COLLIDER),
		Hammer2BlockTiny		(COMMON, HammerBlock, 0x802EA208, FIELD_FLAG | FIELD_COLLIDER),
		Hammer3Block			(COMMON, HammerBlock, 0x802EA22C, FIELD_FLAG | FIELD_COLLIDER),
		Hammer3BlockWide		(COMMON, HammerBlock, 0x802EA250, FIELD_FLAG | FIELD_COLLIDER),
		Hammer3BlockThick		(COMMON, HammerBlock, 0x802EA274, FIELD_FLAG | FIELD_COLLIDER),
		Hammer3BlockTiny		(COMMON, HammerBlock, 0x802EA298, FIELD_FLAG | FIELD_COLLIDER);
		// @formatter:on

		public final EntitySet set;
		public final EntityMenuGroup menuGroup;
		public final int addr;
		public final String name;
		public final int fieldFlags;

		private EntityModel model = null;
		public EntityTypeData typeData = null;

		private EntityType(EntitySet set, EntityMenuGroup menuGroup, int addr, int flags)
		{
			this.set = set;
			this.menuGroup = menuGroup;
			this.addr = addr;
			this.fieldFlags = flags;

			this.name = name();
			typeNameMap.put(name, this);
		}

		public boolean hasModel()
		{
			return (model != null);
		}

		public void renderCollision(boolean selected, float yaw, int x, int y, int z)
		{
			if (typeData == null)
				return;

			RenderState.pushModelMatrix();
			TransformMatrix mtx = TransformMatrix.identity();
			mtx.rotate(Axis.Y, yaw);
			mtx.translate(x, y, z);

			ShaderManager.use(LineShader.class);

			if (selected)
				typeData.collisionBox.renderNow(mtx, 0.0f, 1.0f, 0.0f, 2.0f);
			else
				typeData.collisionBox.renderNow(mtx, 0.0f, 1.0f, 1.0f, 2.0f);

			RenderState.popModelMatrix();
		}

		public PickHit tryPick(PickRay ray, float modifier, float yaw, float x, float y, float z)
		{
			// inverse transform pick ray to local space
			Vector3f origin = Marker.transformWorldToLocal(ray.origin, -yaw, -x, -y, -z);
			Vector3f direction = Marker.transformWorldToLocal(ray.direction, -yaw, 0, 0, 0);
			PickRay newRay = new PickRay(ray.channel, origin, direction);

			PickHit hit = new PickHit(newRay);

			if (model == null)
				return hit;

			hit = model.tryPick(newRay, this, modifier);

			if (!hit.missed() && hit.norm != null)
				hit.norm = Marker.transformWorldToLocal(hit.norm, yaw, 0, 0, 0);

			return hit;
		}

		static {
			for (String line : Resource.getTextInput(ResourceType.EntityModelRoots, "EntityTypeData.txt", false)) {
				try {
					EntityTypeData data = new EntityTypeData(line);
					typeNameMap.get(data.name).typeData = data;
				}
				catch (InvalidInputException e) {
					Logger.printStackTrace(e);
				}
			}
		}

		public static int toID(EntityType entity)
		{
			if (entity.equals(EntityType.Item))
				return -1;
			else
				return ProjectDatabase.EntityType.getID(entity.name);
		}

		public static EntityType fromID(int addr)
		{
			if (addr == -1)
				return EntityType.Item;
			else
				return typeNameMap.get(ProjectDatabase.EntityType.getName(addr));
		}

		public void addRenderables(Collection<SortedRenderable> renderables,
			boolean selected, float x, float y, float z, float yaw, float modifier)
		{
			if (!hasModel())
				return;

			for (int i = 0; i < model.parts.size(); i++)
				renderables.add(new RenderablePart(model, i, selected, x, y, z, yaw, modifier));
		}
	}

	public static void loadModels()
	{
		for (EntityType type : EntityType.values()) {
			if (type.model != null)
				type.model.freeTextures();

			if (type.set == EntitySet.NONE || type == EntityType.Munchlesia)
				continue; // these types dont have models

			try {
				type.model = new EntityModel(type);
				type.model.readFromObj(DUMP_ENTITY_SRC + type.name + "/", true);
				type.model.loadTextures();
			}
			catch (Exception e) {
				type.model = null;
				Logger.printStackTrace(e);
			}
		}
	}

	public static class EntityTypeData
	{
		public final String name;
		public final int address;
		public final int offset;

		public final int[] pointers = new int[5];

		public final int[][] dmaArgs = new int[2][2];

		public final int flags;
		public final int bufferSize;
		public final int typeID;
		public final int[] collisionSize = new int[3];
		public final BoundingBox collisionBox;

		public EntityTypeData(EntityType type, ByteBuffer fileBuffer)
		{
			this.name = type.name;
			this.address = type.addr;
			this.offset = type.set.toOffset(type.addr);

			fileBuffer.position(offset);
			flags = fileBuffer.getShort() & 0xFFFF;
			bufferSize = fileBuffer.getShort() & 0xFFFF;

			pointers[0] = fileBuffer.getInt();
			pointers[1] = fileBuffer.getInt();
			pointers[2] = fileBuffer.getInt();

			pointers[3] = fileBuffer.getInt();
			pointers[4] = fileBuffer.getInt();

			dmaArgs[0][0] = fileBuffer.getInt(); // dma start
			dmaArgs[0][1] = fileBuffer.getInt(); // dma end

			typeID = fileBuffer.get() & 0xFF;
			collisionSize[0] = fileBuffer.get() & 0xFF;
			collisionSize[1] = fileBuffer.get() & 0xFF;
			collisionSize[2] = fileBuffer.get() & 0xFF;

			if ((dmaArgs[0][0] & 0x80000000) != 0) {
				fileBuffer.position(type.set.toOffset(dmaArgs[0][0]));
				dmaArgs[0][0] = fileBuffer.getInt();
				dmaArgs[0][1] = fileBuffer.getInt();
				dmaArgs[1][0] = fileBuffer.getInt();
				dmaArgs[1][1] = fileBuffer.getInt();
			}

			collisionBox = new BoundingBox();
			collisionBox.encompass(-collisionSize[0] / 2, 0, -collisionSize[2] / 2);
			collisionBox.encompass(collisionSize[0] / 2, collisionSize[1], collisionSize[2] / 2);
		}

		@Override
		public String toString()
		{
			StringBuilder sb = new StringBuilder(String.format("%08X:%08X", dmaArgs[0][0], dmaArgs[0][1]));
			for (int i = 1; i < dmaArgs.length; i++)
				sb.append(String.format(";%08X:%08X", dmaArgs[i][0], dmaArgs[i][1]));

			return String.format("%-20s %8X %8X %4X %4X %8X %4X %8X %8X %8X %s %2X %2X %2X %2X",
				name, address, offset,
				flags, bufferSize,
				pointers[0], pointers[1], pointers[2], pointers[3], pointers[4],
				sb.toString(), typeID,
				collisionSize[0], collisionSize[1], collisionSize[2]);
		}

		public EntityTypeData(String line) throws InvalidInputException
		{
			String[] tokens = line.split("\\s+");
			name = tokens[0];
			address = DataUtils.parseIntString(tokens[1]);
			offset = DataUtils.parseIntString(tokens[2]);
			flags = DataUtils.parseIntString(tokens[3]);
			bufferSize = DataUtils.parseIntString(tokens[4]);
			pointers[0] = DataUtils.parseIntString(tokens[5]);
			pointers[1] = DataUtils.parseIntString(tokens[6]);
			pointers[2] = DataUtils.parseIntString(tokens[7]);
			pointers[3] = DataUtils.parseIntString(tokens[8]);
			pointers[4] = DataUtils.parseIntString(tokens[9]);
			typeID = DataUtils.parseIntString(tokens[11]);
			collisionSize[0] = DataUtils.parseIntString(tokens[12]);
			collisionSize[1] = DataUtils.parseIntString(tokens[13]);
			collisionSize[2] = DataUtils.parseIntString(tokens[14]);

			String[] argPairs = tokens[10].split(";");
			for (int i = 0; i < argPairs.length; i++) {
				String[] args = argPairs[i].split(":");
				dmaArgs[i][0] = DataUtils.parseIntString(args[0]);
				dmaArgs[i][1] = DataUtils.parseIntString(args[1]);
			}

			collisionBox = new BoundingBox();
			collisionBox.encompass(-collisionSize[0] / 2, 0, -collisionSize[2] / 2);
			collisionBox.encompass(collisionSize[0] / 2, collisionSize[1], collisionSize[2] / 2);
		}
	}

	public static void main(String[] args) throws IOException
	{
		Environment.initialize();
		printEntityTypeData(Environment.getBaseRomBuffer());
		Environment.exit();
	}

	private static void printEntityTypeData(ByteBuffer fileBuffer) throws IOException
	{
		for (EntityType type : EntityType.values()) {
			if (type == EntityType.Item)
				continue;
			System.out.println(new EntityTypeData(type, fileBuffer));
		}
	}
}
