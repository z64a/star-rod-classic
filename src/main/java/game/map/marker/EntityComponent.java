package game.map.marker;

import static game.map.MapKey.*;
import static game.world.entity.EntityInfo.*;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Collection;
import java.util.function.Consumer;

import org.w3c.dom.Element;

import game.map.editor.camera.MapEditViewport;
import game.map.editor.commands.fields.EditableField;
import game.map.editor.commands.fields.EditableField.EditableFieldFactory;
import game.map.editor.commands.fields.EditableField.StandardBoolName;
import game.map.editor.render.Renderer;
import game.map.editor.render.RenderingOptions;
import game.map.editor.render.ShadowRenderer.RenderableShadow;
import game.map.editor.render.SortedRenderable;
import game.map.editor.selection.PickRay;
import game.map.editor.selection.PickRay.Channel;
import game.map.editor.selection.PickRay.PickHit;
import game.map.editor.ui.info.marker.MarkerInfoPanel;
import game.map.mesh.Triangle;
import game.map.mesh.Vertex;
import game.world.entity.EntityInfo;
import game.world.entity.EntityInfo.EntityType;
import util.identity.IdentityArrayList;
import util.xml.XmlWrapper.XmlReader;
import util.xml.XmlWrapper.XmlTag;
import util.xml.XmlWrapper.XmlWriter;

public class EntityComponent extends BaseMarkerComponent
{
	private final Consumer<Object> notifyCallback = (o) -> {
		parentMarker.updateListeners(MarkerInfoPanel.tag_EntityTab);
	};

	public EntityComponent(Marker marker)
	{
		super(marker);
	}

	public EditableField<EntityType> type = EditableFieldFactory.create(EntityType.YellowBlock)
		.setCallback(notifyCallback).setName("Set Entity Type").build();

	public EditableField<Boolean> hasFlag = EditableFieldFactory.create(false)
		.setCallback(notifyCallback).setName(new StandardBoolName("Flag")).build();

	public EditableField<String> flagName = EditableFieldFactory.create("")
		.setCallback(notifyCallback).setName("Set Entity Flag").build();

	public EditableField<Boolean> hasSpawnFlag = EditableFieldFactory.create(false)
		.setCallback(notifyCallback).setName(new StandardBoolName("Spawn Flag")).build();

	public EditableField<String> spawnFlagName = EditableFieldFactory.create("")
		.setCallback(notifyCallback).setName("Set Spawn Flag").build();

	public EditableField<Boolean> hasCallback = EditableFieldFactory.create(false)
		.setCallback(notifyCallback).setName(new StandardBoolName("Callback")).build();

	public EditableField<Boolean> hasItem = EditableFieldFactory.create(false)
		.setCallback(notifyCallback).setName(new StandardBoolName("Item")).build();

	public EditableField<String> itemName = EditableFieldFactory.create("Mushroom")
		.setCallback(notifyCallback).setName("Set Entity Item").build();

	public EditableField<String> itemSpawnMode = EditableFieldFactory.create("Fixed")
		.setCallback(notifyCallback).setName("Set Item Spawn Mode").build();

	public EditableField<Boolean> hasAreaFlag = EditableFieldFactory.create(true)
		.setCallback(notifyCallback).setName(new StandardBoolName("Area Flag")).build();

	public EditableField<Integer> areaFlagIndex = EditableFieldFactory.create(0)
		.setCallback(notifyCallback).setName("Set Area Flag Index").build();

	//public EditableField<Boolean> autoAreaFlag = EditableFieldFactory.create(false)
	//		.setCallback(notifyCallback).setName(new StandardBoolName("Automatic Area Flag Assignment")).build();

	public EditableField<Integer> mapVarIndex = EditableFieldFactory.create(0)
		.setCallback(notifyCallback).setName("Set Map Variable Index").build();

	public EditableField<Boolean> autoMapVar = EditableFieldFactory.create(true)
		.setCallback(notifyCallback).setName(new StandardBoolName("Automatic Map Variable Assignment")).build();

	public EditableField<String> modelName = EditableFieldFactory.create("")
		.setCallback(notifyCallback).setName("Set Model").build();

	public EditableField<String> colliderName = EditableFieldFactory.create("")
		.setCallback(notifyCallback).setName("Set Collider").build();

	public EditableField<String> targetMarker = EditableFieldFactory.create("")
		.setCallback(notifyCallback).setName("Set Target Marker").build();

	public EditableField<Integer> springLaunchHeight = EditableFieldFactory.create(60)
		.setCallback(notifyCallback).setName("Set Spring Launch Height").build();

	public EditableField<Integer> springLaunchArc = EditableFieldFactory.create(30)
		.setCallback(notifyCallback).setName("Set Spring Launch Arc").build();

	public EditableField<Integer> signAngle = EditableFieldFactory.create(0)
		.setCallback(notifyCallback).setName("Set Sign Angle").build();

	public EditableField<String> pathMarker = EditableFieldFactory.create("")
		.setCallback(notifyCallback).setName("Set Path Marker").build();

	public EditableField<String> gotoMap = EditableFieldFactory.create("machi")
		.setCallback(notifyCallback).setName("Set Destination Map").build();

	public EditableField<String> gotoEntry = EditableFieldFactory.create("")
		.setCallback(notifyCallback).setName("Set Destination Entry").build();

	public EditableField<Boolean> useDestMarkerID = EditableFieldFactory.create(false)
		.setCallback(notifyCallback)
		.setName((newValue) -> {
			return newValue ? "Interpret Dest Marker as ID" : "Interpret Dest Marker as Name";
		})
		.build();

	public EditableField<String> pipeEntry = EditableFieldFactory.create("")
		.setCallback(notifyCallback).setName("Set Pipe Entry").build();

	@Override
	public EntityComponent deepCopy(Marker copyParent)
	{
		EntityComponent copy = new EntityComponent(copyParent);

		copy.type.copy(type);

		copy.hasCallback.copy(hasCallback);
		copy.hasFlag.copy(hasFlag);
		copy.flagName.copy(flagName);
		copy.hasSpawnFlag.copy(hasSpawnFlag);
		copy.spawnFlagName.copy(spawnFlagName);

		copy.hasItem.copy(hasItem);
		copy.itemName.copy(itemName);
		copy.itemSpawnMode.copy(itemSpawnMode);

		copy.hasAreaFlag.copy(hasAreaFlag);
		copy.areaFlagIndex.copy(areaFlagIndex);
		copy.mapVarIndex.copy(mapVarIndex);
		copy.autoMapVar.copy(autoMapVar);
		copy.modelName.copy(modelName);
		copy.colliderName.copy(colliderName);
		copy.targetMarker.copy(targetMarker);

		copy.springLaunchHeight.copy(springLaunchHeight);
		copy.springLaunchArc.copy(springLaunchArc);
		copy.signAngle.copy(signAngle);

		copy.pathMarker.copy(pathMarker);
		copy.gotoMap.copy(gotoMap);
		copy.gotoEntry.copy(gotoEntry);

		copy.pipeEntry.copy(pipeEntry);

		return copy;
	}

	@Override
	public void toBinary(ObjectOutput out) throws IOException
	{
		out.writeObject(type.get());

		// serialize everything

		out.writeBoolean(hasCallback.get());
		out.writeBoolean(hasFlag.get());
		out.writeUTF(flagName.get());
		out.writeBoolean(hasSpawnFlag.get());
		out.writeUTF(spawnFlagName.get());

		out.writeBoolean(hasItem.get());
		out.writeUTF(itemName.get());
		out.writeUTF(itemSpawnMode.get());

		out.writeBoolean(hasAreaFlag.get());
		out.writeInt(areaFlagIndex.get());
		out.writeInt(mapVarIndex.get());
		out.writeBoolean(autoMapVar.get());
		out.writeUTF(modelName.get());
		out.writeUTF(colliderName.get());
		out.writeUTF(targetMarker.get());

		out.writeInt(springLaunchHeight.get());
		out.writeInt(springLaunchArc.get());
		out.writeInt(signAngle.get());

		out.writeUTF(pathMarker.get());
		out.writeUTF(gotoMap.get());
		out.writeUTF(gotoEntry.get());
		out.writeBoolean(useDestMarkerID.get());

		out.writeUTF(pipeEntry.get());
	}

	@Override
	public void fromBinary(ObjectInput in) throws IOException, ClassNotFoundException
	{
		type.set((EntityType) in.readObject());

		// deserialize everything

		hasCallback.set(in.readBoolean());
		hasFlag.set(in.readBoolean());
		flagName.set(in.readUTF());
		hasSpawnFlag.set(in.readBoolean());
		spawnFlagName.set(in.readUTF());

		hasItem.set(in.readBoolean());
		itemName.set(in.readUTF());
		itemSpawnMode.set(in.readUTF());

		hasAreaFlag.set(in.readBoolean());
		areaFlagIndex.set(in.readInt());
		mapVarIndex.set(in.readInt());
		autoMapVar.set(in.readBoolean());
		modelName.set(in.readUTF());
		colliderName.set(in.readUTF());
		targetMarker.set(in.readUTF());

		springLaunchHeight.set(in.readInt());
		springLaunchArc.set(in.readInt());
		signAngle.set(in.readInt());

		pathMarker.set(in.readUTF());
		gotoMap.set(in.readUTF());
		gotoEntry.set(in.readUTF());
		useDestMarkerID.set(in.readBoolean());

		pipeEntry.set(in.readUTF());
	}

	@Override
	public void toXML(XmlWriter xmw)
	{
		XmlTag entityTag = xmw.createTag(TAG_ENTITY, true);
		xmw.addHex(entityTag, ATTR_TYPE, EntityType.toID(type.get()));

		// only deserialize fields belonging to type
		int typeFlags = type.get().fieldFlags;

		if ((typeFlags & FIELD_HAS_FLAG) != 0)
			xmw.addBoolean(entityTag, ATTR_NTT_HAS_FLAG, hasFlag.get());

		if ((typeFlags & FIELD_FLAG) != 0)
			xmw.addAttribute(entityTag, ATTR_NTT_FLAG, flagName.get());

		if ((typeFlags & FIELD_HAS_SPAWN_FLAG) != 0)
			xmw.addBoolean(entityTag, ATTR_NTT_HAS_SPAWN_FLAG, hasSpawnFlag.get());

		if ((typeFlags & FIELD_SPAWN_FLAG) != 0)
			xmw.addAttribute(entityTag, ATTR_NTT_SPAWN_FLAG, spawnFlagName.get());

		if ((typeFlags & FIELD_HAS_CALLBACK) != 0)
			xmw.addBoolean(entityTag, ATTR_NTT_CALLBACK, hasCallback.get());

		if ((typeFlags & FIELD_HAS_ITEM) != 0)
			xmw.addBoolean(entityTag, ATTR_NTT_HAS_ITEM, hasItem.get());

		if ((typeFlags & FIELD_ITEM) != 0)
			xmw.addAttribute(entityTag, ATTR_NTT_ITEM, itemName.get());

		if ((typeFlags & FIELD_ITEM_SPAWN) != 0)
			xmw.addAttribute(entityTag, ATTR_NTT_ITEM_SPAWN, itemSpawnMode.get());

		if ((typeFlags & FIELD_HAS_AREA_FLAG) != 0)
			xmw.addBoolean(entityTag, ATTR_NTT_HAS_AREA_FLAG, hasAreaFlag.get());

		if ((typeFlags & FIELD_AREA_FLAG) != 0)
			xmw.addHex(entityTag, ATTR_NTT_AREA_FLAG, areaFlagIndex.get());

		if ((typeFlags & FIELD_MAP_VAR) != 0) {
			xmw.addBoolean(entityTag, ATTR_NTT_AUTO_MAP_VAR, autoMapVar.get());
			xmw.addHex(entityTag, ATTR_NTT_MAP_VAR, mapVarIndex.get());
		}

		if ((typeFlags & FIELD_MODEL) != 0)
			xmw.addAttribute(entityTag, ATTR_NTT_MODEL, modelName.get());

		if ((typeFlags & FIELD_COLLIDER) != 0)
			xmw.addAttribute(entityTag, ATTR_NTT_COLLIDER, colliderName.get());

		if ((typeFlags & FIELD_TARGET_MARKER) != 0)
			xmw.addAttribute(entityTag, ATTR_NTT_DEST_MARKER, targetMarker.get());

		if ((typeFlags & FIELD_LAUNCH_H) != 0)
			xmw.addHex(entityTag, ATTR_NTT_LAUNCH_H, springLaunchHeight.get());

		if ((typeFlags & FIELD_LAUNCH_T) != 0)
			xmw.addHex(entityTag, ATTR_NTT_LAUNCH_T, springLaunchArc.get());

		if ((typeFlags & FIELD_ANGLE) != 0)
			xmw.addHex(entityTag, ATTR_NTT_ANGLE, signAngle.get());

		if ((typeFlags & FIELD_PATH_MARKER) != 0)
			xmw.addAttribute(entityTag, ATTR_NTT_PATH, targetMarker.get());

		if ((typeFlags & FIELD_DEST_MAP) != 0)
			xmw.addAttribute(entityTag, ATTR_NTT_DEST_MAP, gotoMap.get());

		if ((typeFlags & FIELD_DEST_ENTRY) != 0) {
			xmw.addBoolean(entityTag, ATTR_NTT_USE_DEST_ID, useDestMarkerID.get());
			xmw.addAttribute(entityTag, ATTR_NTT_DEST_ENTRY, gotoEntry.get());
		}

		if ((typeFlags & FIELD_PIPE_ENTRY) != 0)
			xmw.addAttribute(entityTag, ATTR_NTT_PIPE_ENTRY, pipeEntry.get());

		xmw.printTag(entityTag);
	}

	@Override
	public void fromXML(XmlReader xmr, Element markerElem)
	{
		if (xmr.getUniqueTag(markerElem, TAG_ENTITY) == null) //XXX temp check
			return;

		Element entityElem = xmr.getUniqueRequiredTag(markerElem, TAG_ENTITY);

		xmr.requiresAttribute(entityElem, ATTR_TYPE);
		type.set(EntityType.fromID(xmr.readHex(entityElem, ATTR_TYPE)));

		// only serialize fields belonging to type
		int typeFlags = type.get().fieldFlags;

		if ((typeFlags & FIELD_HAS_FLAG) != 0) {
			if (xmr.hasAttribute(entityElem, ATTR_NTT_HAS_FLAG))
				hasFlag.set(xmr.readBoolean(entityElem, ATTR_NTT_HAS_FLAG));
		}

		if ((typeFlags & FIELD_FLAG) != 0) {
			if (xmr.hasAttribute(entityElem, ATTR_NTT_FLAG))
				flagName.set(xmr.getAttribute(entityElem, ATTR_NTT_FLAG));
		}

		if ((typeFlags & FIELD_HAS_SPAWN_FLAG) != 0) {
			if (xmr.hasAttribute(entityElem, ATTR_NTT_HAS_SPAWN_FLAG))
				hasSpawnFlag.set(xmr.readBoolean(entityElem, ATTR_NTT_HAS_SPAWN_FLAG));
		}

		if ((typeFlags & FIELD_SPAWN_FLAG) != 0) {
			if (xmr.hasAttribute(entityElem, ATTR_NTT_SPAWN_FLAG))
				spawnFlagName.set(xmr.getAttribute(entityElem, ATTR_NTT_SPAWN_FLAG));
		}

		if ((typeFlags & FIELD_HAS_CALLBACK) != 0) {
			if (xmr.hasAttribute(entityElem, ATTR_NTT_CALLBACK))
				hasCallback.set(xmr.readBoolean(entityElem, ATTR_NTT_CALLBACK));
		}

		if ((typeFlags & FIELD_HAS_ITEM) != 0) {
			if (xmr.hasAttribute(entityElem, ATTR_NTT_HAS_ITEM))
				hasItem.set(xmr.readBoolean(entityElem, ATTR_NTT_HAS_ITEM));
		}

		if ((typeFlags & FIELD_ITEM) != 0) {
			if (xmr.hasAttribute(entityElem, ATTR_NTT_ITEM))
				itemName.set(xmr.getAttribute(entityElem, ATTR_NTT_ITEM));
		}

		if ((typeFlags & FIELD_ITEM_SPAWN) != 0) {
			if (xmr.hasAttribute(entityElem, ATTR_NTT_ITEM_SPAWN))
				itemSpawnMode.set(xmr.getAttribute(entityElem, ATTR_NTT_ITEM_SPAWN));
		}

		if ((typeFlags & FIELD_HAS_AREA_FLAG) != 0) {
			if (xmr.hasAttribute(entityElem, ATTR_NTT_HAS_AREA_FLAG))
				hasAreaFlag.set(xmr.readBoolean(entityElem, ATTR_NTT_HAS_AREA_FLAG));
		}

		if ((typeFlags & FIELD_AREA_FLAG) != 0) {
			if (xmr.hasAttribute(entityElem, ATTR_NTT_AREA_FLAG))
				areaFlagIndex.set(xmr.readHex(entityElem, ATTR_NTT_AREA_FLAG));
		}

		if ((typeFlags & FIELD_MAP_VAR) != 0) {
			if (xmr.hasAttribute(entityElem, ATTR_NTT_AUTO_MAP_VAR))
				autoMapVar.set(xmr.readBoolean(entityElem, ATTR_NTT_AUTO_MAP_VAR));

			if (xmr.hasAttribute(entityElem, ATTR_NTT_MAP_VAR))
				mapVarIndex.set(xmr.readHex(entityElem, ATTR_NTT_MAP_VAR));
		}

		if ((typeFlags & FIELD_MODEL) != 0) {
			if (xmr.hasAttribute(entityElem, ATTR_NTT_MODEL))
				modelName.set(xmr.getAttribute(entityElem, ATTR_NTT_MODEL));
		}

		if ((typeFlags & FIELD_COLLIDER) != 0) {
			if (xmr.hasAttribute(entityElem, ATTR_NTT_COLLIDER))
				colliderName.set(xmr.getAttribute(entityElem, ATTR_NTT_COLLIDER));
		}

		if ((typeFlags & FIELD_TARGET_MARKER) != 0) {
			if (xmr.hasAttribute(entityElem, ATTR_NTT_DEST_MARKER))
				targetMarker.set(xmr.getAttribute(entityElem, ATTR_NTT_DEST_MARKER));
		}

		if ((typeFlags & FIELD_LAUNCH_H) != 0) {
			if (xmr.hasAttribute(entityElem, ATTR_NTT_LAUNCH_H))
				springLaunchHeight.set(xmr.readHex(entityElem, ATTR_NTT_LAUNCH_H));
		}

		if ((typeFlags & FIELD_LAUNCH_T) != 0) {
			if (xmr.hasAttribute(entityElem, ATTR_NTT_LAUNCH_T))
				springLaunchArc.set(xmr.readHex(entityElem, ATTR_NTT_LAUNCH_T));
		}

		if ((typeFlags & FIELD_ANGLE) != 0) {
			if (xmr.hasAttribute(entityElem, ATTR_NTT_ANGLE))
				signAngle.set(xmr.readHex(entityElem, ATTR_NTT_ANGLE));
		}

		if ((typeFlags & FIELD_PATH_MARKER) != 0) {
			if (xmr.hasAttribute(entityElem, ATTR_NTT_PATH))
				pathMarker.set(xmr.getAttribute(entityElem, ATTR_NTT_PATH));
		}

		if ((typeFlags & FIELD_DEST_MAP) != 0) {
			if (xmr.hasAttribute(entityElem, ATTR_DEST_MAP))
				gotoMap.set(xmr.getAttribute(entityElem, ATTR_DEST_MAP));
		}

		if ((typeFlags & FIELD_DEST_ENTRY) != 0) {
			if (xmr.hasAttribute(entityElem, ATTR_NTT_USE_DEST_ID))
				useDestMarkerID.set(xmr.readBoolean(entityElem, ATTR_NTT_USE_DEST_ID));

			if (xmr.hasAttribute(entityElem, ATTR_NTT_DEST_ENTRY))
				gotoEntry.set(xmr.getAttribute(entityElem, ATTR_NTT_DEST_ENTRY));
		}

		if ((typeFlags & FIELD_PIPE_ENTRY) != 0) {
			if (xmr.hasAttribute(entityElem, ATTR_NTT_PIPE_ENTRY))
				pipeEntry.set(xmr.getAttribute(entityElem, ATTR_NTT_PIPE_ENTRY));
		}
	}

	@Override
	public void tick(double deltaTime)
	{
		buildCollisionMesh();
	}

	@Override
	public boolean hasCollision()
	{
		return (type.get() != EntityType.Item);
	}

	private void buildCollisionMesh()
	{
		EntityType entity = type.get();

		if (entity == null || entity.typeData == null)
			return;

		IdentityArrayList<Triangle> triangles = parentMarker.collisionMesh.batch.triangles;
		triangles.clear();
		parentMarker.collisionAABB.clear();

		int[] size = entity.typeData.collisionSize;
		int halfX = size[0] / 2;
		int halfZ = size[2] / 2;

		Vertex[][] box = new Vertex[2][4];
		box[0][0] = new Vertex(-halfX, 0, -halfZ);
		box[0][1] = new Vertex(halfX, 0, -halfZ);
		box[0][2] = new Vertex(halfX, 0, halfZ);
		box[0][3] = new Vertex(-halfX, 0, halfZ);

		box[1][0] = new Vertex(-halfX, size[1], -halfZ);
		box[1][1] = new Vertex(halfX, size[1], -halfZ);
		box[1][2] = new Vertex(halfX, size[1], halfZ);
		box[1][3] = new Vertex(-halfX, size[1], halfZ);

		triangles.add(new Triangle(box[0][0], box[0][1], box[0][2]));
		triangles.add(new Triangle(box[0][2], box[0][3], box[0][0]));

		triangles.add(new Triangle(box[1][1], box[1][0], box[1][2]));
		triangles.add(new Triangle(box[1][3], box[1][2], box[1][0]));

		triangles.add(new Triangle(box[0][1], box[0][0], box[1][1]));
		triangles.add(new Triangle(box[0][2], box[0][1], box[1][2]));
		triangles.add(new Triangle(box[0][3], box[0][2], box[1][3]));
		triangles.add(new Triangle(box[0][0], box[0][3], box[1][0]));
		triangles.add(new Triangle(box[0][0], box[1][0], box[1][1]));
		triangles.add(new Triangle(box[0][1], box[1][1], box[1][2]));
		triangles.add(new Triangle(box[0][2], box[1][2], box[1][3]));
		triangles.add(new Triangle(box[0][3], box[1][3], box[1][0]));

		float yawAngle = -(float) parentMarker.yaw.getAngle();
		int posX = parentMarker.position.getX();
		int posY = parentMarker.position.getY();
		int posZ = parentMarker.position.getZ();

		for (int i = 0; i < 2; i++)
			for (int j = 0; j < 4; j++)
				box[i][j].setPositon(Marker.transformLocalToWorld(box[i][j].getCurrentPos(), yawAngle, posX, posY, posZ));

		parentMarker.collisionAABB.encompass(box[0][0]);
		parentMarker.collisionAABB.encompass(box[1][2]);
	}

	@Override
	public PickHit trySelectionPick(PickRay ray)
	{
		assert (ray.channel == Channel.SELECTION);

		if (type.get() == null || !type.get().hasModel())
			return super.trySelectionPick(ray);

		return type.get().tryPick(ray, signAngle.get() - 90,
			(float) parentMarker.yaw.getAngle(),
			parentMarker.position.getX(),
			parentMarker.position.getY(),
			parentMarker.position.getZ());
	}

	@Override
	public void addRenderables(RenderingOptions opts, Collection<SortedRenderable> renderables, PickHit shadowHit)
	{
		EntityType entityType = type.get();
		if (entityType != null) {
			entityType.addRenderables(renderables, parentMarker.selected,
				parentMarker.position.getX(), parentMarker.position.getY(), parentMarker.position.getZ(),
				(float) parentMarker.yaw.getAngle(), signAngle.get());

			if ((entityType.fieldFlags & EntityInfo.PROPERTY_HAS_SQUARE_SHADOW) != 0) {
				if (entityType.typeData != null && shadowHit != null && shadowHit.dist < Float.MAX_VALUE)
					renderables.add(new RenderableShadow(
						shadowHit.point, shadowHit.norm, shadowHit.dist,
						false, false, 5.0f * entityType.typeData.collisionSize[0])); // = 100.0 * (size / 20.0)
			}
			else if ((entityType.fieldFlags & EntityInfo.PROPERTY_HAS_ROUND_SHADOW) != 0) {
				if (entityType.typeData != null && shadowHit != null && shadowHit.dist < Float.MAX_VALUE)
					renderables.add(new RenderableShadow(
						shadowHit.point, shadowHit.norm, shadowHit.dist,
						false, true, 5.0f * entityType.typeData.collisionSize[0])); // = 100.0 * (size / 20.0)
			}
		}
	}

	@Override
	public void render(RenderingOptions opts, MapEditViewport view, Renderer renderer)
	{
		if (!type.get().hasModel())
			parentMarker.renderCube(opts, view, renderer);
		parentMarker.renderDirectionIndicator(opts, view, renderer);

		if (opts.showEntityCollision) {
			type.get().renderCollision(parentMarker.selected, (float) parentMarker.yaw.getAngle(),
				parentMarker.position.getX(), parentMarker.position.getY(), parentMarker.position.getZ());
		}
	}
}
