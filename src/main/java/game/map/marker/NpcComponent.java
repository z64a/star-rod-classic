package game.map.marker;

import static game.map.MapKey.*;
import static org.lwjgl.opengl.GL11.*;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

import org.w3c.dom.Element;

import common.BaseCamera;
import common.Vector3f;

import game.map.Axis;
import game.map.MutablePoint;
import game.map.MutablePoint.PointBackup;
import game.map.editor.camera.MapEditViewport;
import game.map.editor.camera.ViewType;
import game.map.editor.commands.AbstractCommand;
import game.map.editor.commands.fields.EditableField;
import game.map.editor.commands.fields.EditableField.EditableFieldFactory;
import game.map.editor.commands.fields.EditableField.StandardBoolName;
import game.map.editor.render.PresetColor;
import game.map.editor.render.RenderMode;
import game.map.editor.render.Renderer;
import game.map.editor.render.RenderingOptions;
import game.map.editor.render.ShadowRenderer.RenderableShadow;
import game.map.editor.render.SortedRenderable;
import game.map.editor.selection.PickRay.PickHit;
import game.map.editor.selection.SelectablePoint;
import game.map.editor.selection.SelectablePoint.SetPointCoord;
import game.map.editor.ui.info.marker.MarkerInfoPanel;
import game.map.shape.TransformMatrix;
import game.map.struct.npc.Npc;
import game.sprite.Sprite;
import game.sprite.SpriteLoader;
import game.sprite.SpriteLoader.SpriteSet;
import renderer.buffers.LineRenderQueue;
import renderer.buffers.PointRenderQueue;
import renderer.shaders.RenderState;
import util.identity.IdentityHashSet;
import util.xml.XmlWrapper.XmlReader;
import util.xml.XmlWrapper.XmlTag;
import util.xml.XmlWrapper.XmlWriter;

public class NpcComponent extends BaseMarkerComponent
{
	private final Consumer<Object> notifyGeneral = (o) -> {
		parentMarker.updateListeners(MarkerInfoPanel.tag_NPCData);
	};

	private final Consumer<Object> notifyMovement = (o) -> {
		parentMarker.updateListeners(MarkerInfoPanel.tag_NPCMovementTab);
	};

	public static final int CALLBACK_INIT = 0x01;
	public static final int CALLBACK_INTERACT = 0x02;
	public static final int CALLBACK_IDLE = 0x04;
	public static final int CALLBACK_AI = 0x08;
	public static final int CALLBACK_HIT = 0x10;
	public static final int CALLBACK_DEFEAT = 0x20;
	public static final int CALLBACK_AUX = 0x40;
	public static final int CALLBACK_MASK = 0x7F;

	public static enum MoveType
	{
		Stationary,
		Wander,
		Patrol
	}

	private static final double SPRITE_TICK_RATE = 1.0 / 30.0;

	private transient SpriteLoader spriteLoader;
	private transient double spriteTime = 0.0;

	private transient boolean needsReloading = false;
	private int spriteID;
	private int paletteID;

	// Entries without an override bit use defaultAnimation.
	private int[] animations = new int[Npc.ANIM_TABLE_SIZE];
	private int defaultAnimation;
	private int animationOverrideFlags;
	public transient Sprite previewSprite = null;

	// script generation

	public EditableField<Boolean> generate = EditableFieldFactory.create(false)
		.setCallback(notifyGeneral).setName(new StandardBoolName("Generate NPC Data")).build();

	public EditableField<Integer> enemyFlags = EditableFieldFactory.create(0)
		.setCallback(notifyGeneral).setName("Set Enemy Flags").build();

	public EditableField<String> tattleMessage = EditableFieldFactory.create("")
		.setCallback(notifyGeneral).setName("Set Tattle Message").build();

	public EditableField<Integer> height = EditableFieldFactory.create(0)
		.setCallback(notifyGeneral).setName("Set NPC Height").build();

	public EditableField<Integer> radius = EditableFieldFactory.create(0)
		.setCallback(notifyGeneral).setName("Set NPC Radius").build();

	public EditableField<Integer> level = EditableFieldFactory.create(0)
		.setCallback(notifyGeneral).setName("Set NPC Level").build();

	public EditableField<Integer> actionFlags = EditableFieldFactory.create(0)
		.setCallback(notifyGeneral).setName("Set Enemy Action Flags").build();

	public EditableField<Integer> callbackFlags = EditableFieldFactory.create(0)
		.setCallback(notifyGeneral).setName("Set Generated Callbacks").build();

	// movement

	public EditableField<MoveType> moveType = EditableFieldFactory.create(MoveType.Stationary)
		.setCallback(notifyMovement).setName("Set Movement Type").build();

	public EditableField<Boolean> flying = EditableFieldFactory.create(false)
		.setCallback(notifyMovement).setName(new StandardBoolName("Flying")).build();

	public EditableField<Boolean> overrideMovementSpeed = EditableFieldFactory.create(false)
		.setCallback(notifyMovement).setName(new StandardBoolName("Speed Override")).build();

	public EditableField<Float> movementSpeedOverride = EditableFieldFactory.create(1.0f)
		.setCallback(notifyMovement).setName("Set Override Speed").build();

	public MutablePoint wanderPoint;

	public EditableField<Boolean> useWanderCircle = EditableFieldFactory.create(false)
		.setName((b) -> b ? "Use Box Wander Volume" : "Use Circle Wander Volume")
		.setCallback(notifyMovement).build();

	public EditableField<Integer> wanderSizeX = EditableFieldFactory.create(0)
		.setCallback(notifyMovement).setName("Set Wander Size X").build();

	public EditableField<Integer> wanderSizeZ = EditableFieldFactory.create(0)
		.setCallback(notifyMovement).setName("Set Wander Size Z").build();

	public EditableField<Integer> wanderRadius = EditableFieldFactory.create(0)
		.setCallback(notifyMovement).setName("Set Wander Radius").build();

	public MutablePoint detectPoint;

	public EditableField<Boolean> useDetectCircle = EditableFieldFactory.create(false)
		.setName((b) -> b ? "Use Box Detection Volume" : "Use Circle Detection Volume")
		.setCallback(notifyMovement).build();

	public EditableField<Integer> detectSizeX = EditableFieldFactory.create(0)
		.setCallback(notifyMovement).setName("Set Detection Size X").build();

	public EditableField<Integer> detectSizeZ = EditableFieldFactory.create(0)
		.setCallback(notifyMovement).setName("Set Detection Size Z").build();

	public EditableField<Integer> detectRadius = EditableFieldFactory.create(0)
		.setCallback(notifyMovement).setName("Set Detection Radius").build();

	public transient SelectablePoint detectCenter;
	public transient SelectablePoint wanderCenter;

	public PathData patrolPath;

	public NpcComponent(Marker marker)
	{
		super(marker);

		this.moveType.set(MoveType.Stationary);

		wanderPoint = new MutablePoint(
			marker.position.getX(),
			marker.position.getY(),
			marker.position.getZ());

		detectPoint = new MutablePoint(
			marker.position.getX(),
			marker.position.getY(),
			marker.position.getZ());

		wanderCenter = new SelectablePoint(wanderPoint, 2.0f);
		detectCenter = new SelectablePoint(detectPoint, 2.0f);

		patrolPath = new PathData(marker, MarkerInfoPanel.tag_NPCMovementTab, 10);
	}

	@Override
	public NpcComponent deepCopy(Marker copyParent)
	{
		NpcComponent copy = new NpcComponent(copyParent);

		copy.generate.set(generate.get());
		copy.enemyFlags.set(enemyFlags.get());
		copy.tattleMessage.set(tattleMessage.get());
		copy.height.set(height.get());
		copy.radius.set(radius.get());
		copy.level.set(level.get());
		copy.actionFlags.set(actionFlags.get());
		copy.callbackFlags.set(callbackFlags.get());

		copy.loadTerritoryData(moveType.get(), getTerritoryData());

		copy.setSpriteID(spriteID);
		copy.setPaletteID(paletteID);
		copy.defaultAnimation = defaultAnimation;
		copy.animationOverrideFlags = animationOverrideFlags;

		for (int i = 0; i < animations.length; i++)
			copy.animations[i] = animations[i];

		return copy;
	}

	@Override
	public void toXML(XmlWriter xmw)
	{
		XmlTag npcTag = xmw.createTag(TAG_NPC, true);
		xmw.addBoolean(npcTag, ATTR_NPC_GENERATE, generate.get());
		xmw.addHex(npcTag, ATTR_NPC_FLAGS, enemyFlags.get());
		xmw.addAttribute(npcTag, ATTR_NPC_TATTLE, tattleMessage.get());
		xmw.addInt(npcTag, ATTR_NPC_HEIGHT, height.get());
		xmw.addInt(npcTag, ATTR_NPC_RADIUS, radius.get());
		xmw.addInt(npcTag, ATTR_NPC_LEVEL, level.get());
		xmw.addHex(npcTag, ATTR_NPC_ACTION_FLAGS, actionFlags.get());
		xmw.addHex(npcTag, ATTR_NPC_CALLBACKS, callbackFlags.get());
		xmw.printTag(npcTag);

		XmlTag movementTag = xmw.createTag(TAG_MOVEMENT, true);
		xmw.addEnum(movementTag, ATTR_MOVEMENT_TYPE, moveType.get());
		int[] data = getTerritoryData();
		xmw.addHexArray(movementTag, ATTR_MOVEMENT_DATA, data);
		xmw.printTag(movementTag);

		XmlTag spriteTag = xmw.createTag(TAG_SPRITE, true);
		xmw.addHex(spriteTag, ATTR_SPRITE, getSpriteID());
		xmw.addHex(spriteTag, ATTR_PALETTE, paletteID);
		xmw.addHex(spriteTag, ATTR_DEFAULT_ANIMATION, defaultAnimation);
		xmw.addHex(spriteTag, ATTR_ANIMATION_OVERRIDES, animationOverrideFlags);
		xmw.addHexArray(spriteTag, ATTR_ANIMATIONS, animations);
		xmw.printTag(spriteTag);
	}

	@Override
	public void fromXML(XmlReader xmr, Element markerElem)
	{
		Element npcElem = xmr.getUniqueTag(markerElem, TAG_NPC);
		if (npcElem != null) {
			if (xmr.hasAttribute(npcElem, ATTR_NPC_GENERATE))
				generate.set(xmr.readBoolean(npcElem, ATTR_NPC_GENERATE));
			if (xmr.hasAttribute(npcElem, ATTR_NPC_FLAGS))
				enemyFlags.set(xmr.readHex(npcElem, ATTR_NPC_FLAGS));
			if (xmr.hasAttribute(npcElem, ATTR_NPC_TATTLE))
				tattleMessage.set(xmr.getAttribute(npcElem, ATTR_NPC_TATTLE));
			if (xmr.hasAttribute(npcElem, ATTR_NPC_HEIGHT))
				height.set(xmr.readInt(npcElem, ATTR_NPC_HEIGHT));
			if (xmr.hasAttribute(npcElem, ATTR_NPC_RADIUS))
				radius.set(xmr.readInt(npcElem, ATTR_NPC_RADIUS));
			if (xmr.hasAttribute(npcElem, ATTR_NPC_LEVEL))
				level.set(xmr.readInt(npcElem, ATTR_NPC_LEVEL));
			if (xmr.hasAttribute(npcElem, ATTR_NPC_ACTION_FLAGS))
				actionFlags.set(xmr.readHex(npcElem, ATTR_NPC_ACTION_FLAGS));
			if (xmr.hasAttribute(npcElem, ATTR_NPC_CALLBACKS))
				callbackFlags.set(xmr.readHex(npcElem, ATTR_NPC_CALLBACKS));
		}

		Element movementElem = xmr.getUniqueRequiredTag(markerElem, TAG_MOVEMENT);

		xmr.requiresAttribute(movementElem, ATTR_MOVEMENT_TYPE);
		moveType.set(xmr.readEnum(movementElem, ATTR_MOVEMENT_TYPE, MoveType.class));

		xmr.requiresAttribute(movementElem, ATTR_MOVEMENT_DATA);
		loadTerritoryData(moveType.get(), xmr.readHexArray(movementElem, ATTR_MOVEMENT_DATA, 48));

		Element spriteElement = xmr.getUniqueTag(markerElem, TAG_SPRITE);
		if (spriteElement != null) {
			setSpriteID(xmr.readHex(spriteElement, ATTR_SPRITE));
			setPaletteID(xmr.readHex(spriteElement, ATTR_PALETTE));
			animations = xmr.readHexArray(spriteElement, ATTR_ANIMATIONS, Npc.ANIM_TABLE_SIZE);
			if (xmr.hasAttribute(spriteElement, ATTR_DEFAULT_ANIMATION))
				defaultAnimation = xmr.readHex(spriteElement, ATTR_DEFAULT_ANIMATION);
			else
				defaultAnimation = animations[0];

			if (xmr.hasAttribute(spriteElement, ATTR_ANIMATION_OVERRIDES))
				animationOverrideFlags = xmr.readHex(spriteElement, ATTR_ANIMATION_OVERRIDES) & 0xFFFF;
			else
				inferAnimationOverrides();
		}
	}

	@Override
	public void toBinary(ObjectOutput out) throws IOException
	{
		out.writeObject(moveType.get());
		out.writeBoolean(flying.get());

		out.writeObject(wanderPoint);
		out.writeBoolean(useWanderCircle.get());
		out.writeInt(wanderSizeX.get());
		out.writeInt(wanderSizeZ.get());
		out.writeInt(wanderRadius.get());

		out.writeBoolean(overrideMovementSpeed.get());
		out.writeFloat(movementSpeedOverride.get());

		out.writeObject(detectPoint);
		out.writeBoolean(useDetectCircle.get());
		out.writeInt(detectSizeX.get());
		out.writeInt(detectSizeZ.get());
		out.writeInt(detectRadius.get());

		out.writeInt(patrolPath.points.size());
		for (PathPoint wp : patrolPath.points)
			out.writeObject(wp.point);

		out.writeInt(getSpriteID());
		out.writeInt(paletteID);
		for (int i = 0; i < 16; i++)
			out.writeInt(animations[i]);
		out.writeInt(defaultAnimation);
		out.writeInt(animationOverrideFlags);

		out.writeBoolean(generate.get());
		out.writeInt(enemyFlags.get());
		out.writeUTF(tattleMessage.get() == null ? "" : tattleMessage.get());
		out.writeInt(height.get());
		out.writeInt(radius.get());
		out.writeInt(level.get());
		out.writeInt(actionFlags.get());
		out.writeInt(callbackFlags.get());
	}

	@Override
	public void fromBinary(ObjectInput in) throws IOException, ClassNotFoundException
	{
		fromBinary(in, true, true);
	}

	public void fromBinary(ObjectInput in, int instanceVersion) throws IOException, ClassNotFoundException
	{
		fromBinary(in, instanceVersion >= 5, instanceVersion >= 6);
	}

	private void fromBinary(ObjectInput in, boolean hasGenerationData, boolean hasAnimationOverrides) throws IOException, ClassNotFoundException
	{
		moveType.set((MoveType) in.readObject());
		flying.set(in.readBoolean());

		wanderPoint = (MutablePoint) in.readObject();
		wanderCenter = new SelectablePoint(wanderPoint, 2.0f);
		useWanderCircle.set(in.readBoolean());
		wanderSizeX.set(in.readInt());
		wanderSizeZ.set(in.readInt());
		wanderRadius.set(in.readInt());

		overrideMovementSpeed.set(in.readBoolean());
		movementSpeedOverride.set(in.readFloat());

		detectPoint = (MutablePoint) in.readObject();
		detectCenter = new SelectablePoint(detectPoint, 2.0f);
		useDetectCircle.set(in.readBoolean());
		detectSizeX.set(in.readInt());
		detectSizeZ.set(in.readInt());
		detectRadius.set(in.readInt());

		int numPatrolPoints = in.readInt();
		patrolPath.points.clear();
		for (int i = 0; i < numPatrolPoints; i++) {
			MutablePoint point = (MutablePoint) in.readObject();
			patrolPath.points.addElement(new PathPoint(patrolPath, point.getX(), point.getY(), point.getZ()));
		}

		setSpriteID(in.readInt());
		setPaletteID(in.readInt());

		for (int i = 0; i < 16; i++)
			animations[i] = in.readInt();

		if (hasAnimationOverrides) {
			defaultAnimation = in.readInt();
			animationOverrideFlags = in.readInt() & 0xFFFF;
		}
		else {
			defaultAnimation = animations[0];
			inferAnimationOverrides();
		}

		if (hasGenerationData) {
			generate.set(in.readBoolean());
			enemyFlags.set(in.readInt());
			tattleMessage.set(in.readUTF());
			height.set(in.readInt());
			radius.set(in.readInt());
			level.set(in.readInt());
			actionFlags.set(in.readInt());
			callbackFlags.set(in.readInt());
		}
	}

	public boolean hasCallback(int callback)
	{
		return (callbackFlags.get() & callback) != 0;
	}

	public boolean needsInitScript()
	{
		return callbackFlags.get() != 0;
	}

	public int[] getTerritoryData()
	{
		int[] moveData = new int[48];

		if (moveType.get() == MoveType.Wander) {
			moveData[0] = wanderPoint.getX();
			moveData[1] = wanderPoint.getY();
			moveData[2] = wanderPoint.getZ();
			moveData[3] = useWanderCircle.get() ? wanderRadius.get() : wanderSizeX.get();
			moveData[4] = useWanderCircle.get() ? 0 : wanderSizeZ.get();
			moveData[5] = overrideMovementSpeed.get() ? (int) (movementSpeedOverride.get() * 32767) : -32767;
			moveData[6] = useWanderCircle.get() ? 0 : 1;

			moveData[7] = detectPoint.getX();
			moveData[8] = detectPoint.getY();
			moveData[9] = detectPoint.getZ();
			moveData[10] = useDetectCircle.get() ? detectRadius.get() : detectSizeX.get();
			moveData[11] = useDetectCircle.get() ? 0 : detectSizeZ.get();
			moveData[12] = useDetectCircle.get() ? 0 : 1;
			moveData[13] = flying.get() ? 1 : 0;
		}
		else if (moveType.get() == MoveType.Patrol) {
			moveData[0] = patrolPath.points.size();
			for (int i = 0; i < patrolPath.points.size(); i++) {
				PathPoint wp = patrolPath.points.get(i);
				moveData[3 * i + 1] = wp.getX();
				moveData[3 * i + 2] = wp.getY();
				moveData[3 * i + 3] = wp.getZ();
			}
			moveData[31] = overrideMovementSpeed.get() ? (int) (movementSpeedOverride.get() * 32767) : -32767;

			moveData[32] = detectPoint.getX();
			moveData[33] = detectPoint.getY();
			moveData[34] = detectPoint.getZ();
			moveData[35] = useDetectCircle.get() ? detectRadius.get() : detectSizeX.get();
			moveData[36] = useDetectCircle.get() ? 0 : detectSizeZ.get();
			moveData[37] = useDetectCircle.get() ? 0 : 1;
			moveData[38] = flying.get() ? 1 : 0;
		}

		return moveData;
	}

	public void loadTerritoryData(MoveType type, int[] moveData)
	{
		moveType.set(type);

		if (type == MoveType.Wander) {
			wanderPoint = new MutablePoint(moveData[0], moveData[1], moveData[2]);
			useWanderCircle.set(moveData[6] == 0);
			if (useWanderCircle.get()) {
				wanderRadius.set(moveData[3]);
			}
			else {
				wanderSizeX.set(moveData[3]);
				wanderSizeZ.set(moveData[4]);
			}
			overrideMovementSpeed.set(moveData[5] != -32767);
			if (overrideMovementSpeed.get())
				movementSpeedOverride.set(moveData[5] / 32767.0f);

			detectPoint = new MutablePoint(moveData[7], moveData[8], moveData[9]);
			useDetectCircle.set(moveData[12] == 0);
			if (useDetectCircle.get()) {
				detectRadius.set(moveData[10]);
			}
			else {
				detectSizeX.set(moveData[10]);
				detectSizeZ.set(moveData[11]);
			}

			flying.set(moveData[13] != 0);
		}
		else if (type == MoveType.Patrol) {
			wanderPoint = new MutablePoint(moveData[32], moveData[33], moveData[34]);

			int numPatrolPoints = moveData[0];
			patrolPath.points.clear();
			for (int i = 0; i < numPatrolPoints; i++)
				patrolPath.points.addElement(new PathPoint(patrolPath, moveData[3 * i + 1], moveData[3 * i + 2], moveData[3 * i + 3]));

			overrideMovementSpeed.set(moveData[31] != -32767);
			if (overrideMovementSpeed.get())
				movementSpeedOverride.set(moveData[31] / 32767.0f);

			detectPoint = new MutablePoint(moveData[32], moveData[33], moveData[34]);
			useDetectCircle.set(moveData[37] == 0);
			if (useDetectCircle.get()) {
				detectRadius.set(moveData[35]);
			}
			else {
				detectSizeX.set(moveData[35]);
				detectSizeZ.set(moveData[36]);
			}

			flying.set(moveData[38] != 0);
		}
		else {
			wanderPoint = new MutablePoint(
				parentMarker.position.getX(),
				parentMarker.position.getY(),
				parentMarker.position.getZ());

			detectPoint = new MutablePoint(
				parentMarker.position.getX(),
				parentMarker.position.getY(),
				parentMarker.position.getZ());
		}
	}

	@Override
	public boolean hasSelectablePoints()
	{
		return true;
	}

	@Override
	public void addSelectablePoints(List<SelectablePoint> points)
	{
		points.add(detectCenter);

		if (moveType.get() == MoveType.Wander)
			points.add(wanderCenter);
		else if (moveType.get() == MoveType.Patrol) {
			for (PathPoint wp : patrolPath.points)
				points.add(wp);
		}
	}

	@Override
	public void addToBackup(IdentityHashSet<PointBackup> backupList)
	{
		backupList.add(detectPoint.getBackup());
		backupList.add(wanderPoint.getBackup());

		for (PathPoint wp : patrolPath.points)
			backupList.add(wp.point.getBackup());
	}

	@Override
	public void addPoints(IdentityHashSet<MutablePoint> positions)
	{
		positions.add(detectPoint);
		positions.add(wanderPoint);

		for (PathPoint wp : patrolPath.points)
			positions.add(wp.point);
	}

	@Override
	public void startTransformation()
	{
		detectPoint.startTransform();
		wanderPoint.startTransform();

		for (PathPoint wp : patrolPath.points)
			wp.point.startTransform();
	}

	@Override
	public void endTransformation()
	{
		detectPoint.endTransform();
		wanderPoint.endTransform();

		for (PathPoint wp : patrolPath.points)
			wp.point.endTransform();
	}

	@Override
	public void initialize()
	{
		spriteLoader = new SpriteLoader();

		if (getSpriteID() > 0)
			needsReloading = true;
	}

	@Override
	public void tick(double deltaTime)
	{
		if (previewSprite != null) {
			spriteTime += deltaTime;
			if (spriteTime >= SPRITE_TICK_RATE) {
				int animID = defaultAnimation;
				if (animID >= 0 && animID < previewSprite.animations.size())
					previewSprite.updateAnimation(animID);
				spriteTime -= SPRITE_TICK_RATE;
			}
		}
	}

	@Override
	public void addRenderables(RenderingOptions opts, Collection<SortedRenderable> renderables, PickHit shadowHit)
	{
		if (needsReloading) {
			reloadSprite();
			needsReloading = false;
		}

		if (previewSprite != null)
			renderables.add(new RenderableSprite(this));

		if (shadowHit != null && shadowHit.dist < Float.MAX_VALUE)
			renderables.add(new RenderableShadow(shadowHit.point, shadowHit.norm, shadowHit.dist, false, true, 100.0f));
	}

	@Override
	public void render(RenderingOptions opts, MapEditViewport view, Renderer renderer)
	{
		if ((view.type != ViewType.PERSPECTIVE) || (getSpriteID() <= 0) || (previewSprite == null))
			parentMarker.renderCube(opts, view, renderer);
		parentMarker.renderDirectionIndicator(opts, view, renderer);

		if (!parentMarker.selected)
			return;

		boolean editPointsMode = true;
		boolean drawHiddenPaths = (view.type == ViewType.PERSPECTIVE);

		RenderState.setColor(PresetColor.WHITE);
		RenderState.setLineWidth(2.0f);

		if (useDetectCircle.get())
			drawCircularVolume(
				detectPoint.getX(), detectPoint.getY(),
				detectPoint.getZ(), detectRadius.get(), 50);
		else
			drawRectangularVolume(
				detectPoint.getX(), detectPoint.getY(),
				detectPoint.getZ(), detectSizeX.get(), detectSizeZ.get(), 50);

		if (moveType.get() == MoveType.Wander) {
			if (useWanderCircle.get())
				drawCircularVolume(
					wanderPoint.getX(), wanderPoint.getY(),
					wanderPoint.getZ(), wanderRadius.get(), 50);
			else
				drawRectangularVolume(
					wanderPoint.getX(), wanderPoint.getY(),
					wanderPoint.getZ(), wanderSizeX.get(), wanderSizeZ.get(), 50);
		}
		else if (moveType.get() == MoveType.Patrol) {
			drawPatrolPath(editPointsMode, drawHiddenPaths);
		}

		if (editPointsMode) {
			RenderState.setColor(PresetColor.YELLOW);
			RenderState.setPointSize(editPointsMode ? 12.0f : 8.0f);
			RenderState.setLineWidth(3.0f);
			RenderState.setDepthFunc(GL_ALWAYS);

			SelectablePoint point = detectCenter;
			PointRenderQueue.addPoint().setPosition(point.getX(), point.getY(), point.getZ());

			if (moveType.get() == MoveType.Wander) {
				point = wanderCenter;
				PointRenderQueue.addPoint().setPosition(point.getX(), point.getY(), point.getZ());
			}

			PointRenderQueue.render(true);
		}

		RenderState.initDepthFunc();
	}

	private void drawPatrolPath(boolean editPointsMode, boolean drawHiddenPaths)
	{
		RenderState.setColor(PresetColor.YELLOW);
		RenderState.setPointSize(editPointsMode ? 12.0f : 8.0f);
		RenderState.setLineWidth(3.0f);

		for (int i = 0; i < patrolPath.points.size(); i++) {
			PathPoint wp = patrolPath.points.get(i);
			PointRenderQueue.addPoint().setPosition(wp.getX(), wp.getY(), wp.getZ());
		}

		RenderState.setDepthFunc(GL_ALWAYS);
		PointRenderQueue.render(true);

		for (int i = 0; i < (patrolPath.points.size() - 1); i++) {
			PathPoint wp1 = patrolPath.points.get(i);
			PathPoint wp2 = patrolPath.points.get(i + 1);
			LineRenderQueue.addLine(
				LineRenderQueue.addVertex().setPosition(wp1.getX(), wp1.getY(), wp1.getZ()).getIndex(),
				LineRenderQueue.addVertex().setPosition(wp2.getX(), wp2.getY(), wp2.getZ()).getIndex());
		}

		if (drawHiddenPaths)
			RenderState.setDepthFunc(GL_LEQUAL);
		LineRenderQueue.render(true);

		if (drawHiddenPaths) {

			for (int i = 0; i < (patrolPath.points.size() - 1); i++) {
				PathPoint wp1 = patrolPath.points.get(i);
				PathPoint wp2 = patrolPath.points.get(i + 1);
				Renderer.queueStipple(
					wp1.getX(), wp1.getY(), wp1.getZ(),
					wp2.getX(), wp2.getY(), wp2.getZ(),
					10.0f);
			}

			RenderState.setDepthFunc(GL_GREATER);
			RenderState.setDepthWrite(false);
			LineRenderQueue.render(true);
			RenderState.setDepthWrite(true);
		}

		RenderState.initDepthFunc();
	}

	private void drawCircularVolume(int cx, int cy, int cz, int radius, int h)
	{
		if (radius == 0)
			return;

		int N = 2 * Math.round(1.0f + (float) (radius / Math.sqrt(radius)));
		int[][] indices = new int[2][N + 1];

		for (int i = 0; i < N; i++) {
			float x = radius * (float) Math.cos(2 * i * Math.PI / N);
			float z = radius * (float) Math.sin(2 * i * Math.PI / N);
			indices[0][i] = LineRenderQueue.addVertex().setPosition(cx + x, cy, cz + z).getIndex();
			indices[1][i] = LineRenderQueue.addVertex().setPosition(cx + x, cy + h, cz + z).getIndex();
			LineRenderQueue.addLine(indices[0][i], indices[1][i]);
		}
		indices[0][N] = indices[0][0];
		indices[1][N] = indices[1][0];

		LineRenderQueue.addLine(indices[0]);
		LineRenderQueue.addLine(indices[1]);

		LineRenderQueue.render(true);
	}

	private void drawRectangularVolume(int cx, int cy, int cz, int sizeX, int sizeZ, int h)
	{
		if (sizeX == 0 && sizeZ == 0)
			return;

		int mmm = LineRenderQueue.addVertex().setPosition(cx - sizeX, cy + 0, cz - sizeZ).getIndex();
		int mMm = LineRenderQueue.addVertex().setPosition(cx - sizeX, cy + h, cz - sizeZ).getIndex();
		int mmM = LineRenderQueue.addVertex().setPosition(cx - sizeX, cy + 0, cz + sizeZ).getIndex();
		int mMM = LineRenderQueue.addVertex().setPosition(cx - sizeX, cy + h, cz + sizeZ).getIndex();
		int Mmm = LineRenderQueue.addVertex().setPosition(cx + sizeX, cy + 0, cz - sizeZ).getIndex();
		int MMm = LineRenderQueue.addVertex().setPosition(cx + sizeX, cy + h, cz - sizeZ).getIndex();
		int MmM = LineRenderQueue.addVertex().setPosition(cx + sizeX, cy + 0, cz + sizeZ).getIndex();
		int MMM = LineRenderQueue.addVertex().setPosition(cx + sizeX, cy + h, cz + sizeZ).getIndex();

		LineRenderQueue.addLine(mmm, Mmm, MMm, mMm, mmm);
		LineRenderQueue.addLine(mmM, MmM, MMM, mMM, mmM);
		LineRenderQueue.addLine(mmm, mmM);
		LineRenderQueue.addLine(Mmm, MmM);
		LineRenderQueue.addLine(mMm, mMM);
		LineRenderQueue.addLine(MMm, MMM);

		LineRenderQueue.render(true);
	}

	private void renderSprite(RenderingOptions opts, BaseCamera camera, boolean selected)
	{
		float renderYaw = (float) parentMarker.yaw.getAngle();
		Vector3f deltaPos = Vector3f.sub(camera.pos, parentMarker.position.getVector());
		renderYaw = -(float) Math.toDegrees(Math.atan2(deltaPos.x, deltaPos.z));

		TransformMatrix mtx = TransformMatrix.identity();
		RenderState.setColor(PresetColor.WHITE);

		int x = parentMarker.position.getX();
		int y = parentMarker.position.getY();
		int z = parentMarker.position.getZ();
		y -= Sprite.WORLD_SCALE;

		if (opts.spriteShading != null)
			opts.spriteShading.setSpriteRenderingPos(camera, x, y, z, -renderYaw);

		mtx.scale(Sprite.WORLD_SCALE);

		if (!opts.isStage)
			mtx.rotate(Axis.Y, -renderYaw);

		mtx.translate(x, y, z);
		RenderState.setModelMatrix(mtx);

		int animID = Math.min(defaultAnimation, previewSprite.animations.size() - 1);
		int palID = Math.min(paletteID, previewSprite.palettes.size() - 1);
		if (animID >= 0 && palID >= 0) // watch out for sprites with no animations
			previewSprite.render(opts.spriteShading, animID, palID, opts.useFiltering, selected);

		RenderState.setModelMatrix(null);

		Vector3f size = Vector3f.sub(previewSprite.aabb.getMax(), previewSprite.aabb.getMin());
		float w = 0.75f * 0.5f * Math.max(size.x, size.z);
		float h = 0.75f * 0.75f * size.y;

		parentMarker.AABB.clear();
		parentMarker.AABB.encompass(new Vector3f(x - w / 2, y, z - w / 2));
		parentMarker.AABB.encompass(new Vector3f(x + w / 2, y + h, z + w / 2));

		if (opts.showBoundingBoxes)
			parentMarker.AABB.render();
	}

	public static class RenderableSprite implements SortedRenderable
	{
		private final NpcComponent comp;
		private int depth;

		public RenderableSprite(NpcComponent comp)
		{
			this.comp = comp;
		}

		@Override
		public RenderMode getRenderMode()
		{
			return RenderMode.ALPHA_TEST_AA_ZB_2SIDE;
		}

		@Override
		public Vector3f getCenterPoint()
		{
			return comp.parentMarker.position.getVector();
		}

		@Override
		public void render(RenderingOptions opts, BaseCamera camera)
		{
			comp.renderSprite(opts, camera, comp.parentMarker.isSelected());
		}

		@Override
		public void setDepth(int normalizedDepth)
		{
			depth = normalizedDepth;
		}

		@Override
		public int getDepth()
		{
			return depth;
		}
	}

	private void reloadSprite()
	{
		if (previewSprite != null)
			previewSprite.unloadTextures();

		previewSprite = spriteLoader.getSprite(SpriteSet.Npc, getSpriteID());

		if (previewSprite != null) {
			previewSprite.prepareForEditor();
			previewSprite.loadTextures();
		}

		parentMarker.updateListeners(MarkerInfoPanel.tag_SetSprite);
	}

	public int getAnimation(int index)
	{
		return isAnimationOverridden(index) ? animations[index] : defaultAnimation;
	}

	public void setAnimation(int index, int animation)
	{
		animations[index] = animation;
		parentMarker.updateListeners(MarkerInfoPanel.tag_NPCAnimTab);
	}

	public int getDefaultAnimation()
	{
		return defaultAnimation;
	}

	public void setDefaultAnimation(int value)
	{
		defaultAnimation = value;
		if (previewSprite != null && value >= 0 && value < previewSprite.animations.size())
			previewSprite.resetAnimation(value);
		parentMarker.updateListeners(MarkerInfoPanel.tag_NPCAnimTab);
	}

	public int getDefaultAnimationID()
	{
		return ((getSpriteID() & 0xFF) << 16) | ((getPaletteID() & 0xFF) << 8) | (defaultAnimation & 0xFF);
	}

	public boolean isAnimationOverridden(int index)
	{
		return (animationOverrideFlags & (1 << index)) != 0;
	}

	public void setAnimationOverride(int index, boolean override)
	{
		if (override)
			animationOverrideFlags |= (1 << index);
		else
			animationOverrideFlags &= ~(1 << index);
		parentMarker.updateListeners(MarkerInfoPanel.tag_NPCAnimTab);
	}

	public void inferAnimationOverrides()
	{
		animationOverrideFlags = 0;
		for (int i = 0; i < animations.length; i++) {
			if (animations[i] != defaultAnimation)
				animationOverrideFlags |= (1 << i);
		}
	}

	public int getPaletteID()
	{
		return paletteID;
	}

	public void setPaletteID(int value)
	{
		paletteID = value;
		parentMarker.updateListeners(MarkerInfoPanel.tag_NPCAnimTab);
	}

	public int getSpriteID()
	{
		return spriteID;
	}

	public void setSpriteID(int value)
	{
		spriteID = value;
		parentMarker.updateListeners(MarkerInfoPanel.tag_SetSprite);
	}

	public static final class SetWanderPos extends SetPointCoord
	{
		private final Marker m;

		public SetWanderPos(Marker m, int axis, int val)
		{
			super("Set Wander Center Position", m.npcComponent.wanderCenter, axis, val);
			this.m = m;
		}

		@Override
		public void exec()
		{
			super.exec();
			m.updateListeners(MarkerInfoPanel.tag_NPCMovementTab);
		}

		@Override
		public void undo()
		{
			super.undo();
			m.updateListeners(MarkerInfoPanel.tag_NPCMovementTab);
		}
	}

	public static final class SetDetectPos extends SetPointCoord
	{
		private final Marker m;

		public SetDetectPos(Marker m, int axis, int val)
		{
			super("Set Detect Center Position", m.npcComponent.detectCenter, axis, val);
			this.m = m;
		}

		@Override
		public void exec()
		{
			super.exec();
			m.updateListeners(MarkerInfoPanel.tag_NPCMovementTab);
		}

		@Override
		public void undo()
		{
			super.undo();
			m.updateListeners(MarkerInfoPanel.tag_NPCMovementTab);
		}
	}

	public static final class SetMarkerSprite extends AbstractCommand
	{
		private final NpcComponent comp;
		private final int oldSpriteID;
		private final int newSpriteID;

		public SetMarkerSprite(Marker m, int value)
		{
			super("Set Sprite");
			this.comp = m.npcComponent;
			oldSpriteID = comp.getSpriteID();
			newSpriteID = value;
		}

		@Override
		public boolean shouldExec()
		{
			return oldSpriteID != newSpriteID;
		}

		@Override
		public void exec()
		{
			super.exec();
			comp.setSpriteID(newSpriteID);
			comp.needsReloading = true;
		}

		@Override
		public void undo()
		{
			super.undo();
			comp.setSpriteID(oldSpriteID);
			comp.needsReloading = true;
		}
	}

	public static final class SetMarkerPalette extends AbstractCommand
	{
		private final NpcComponent comp;
		private final int oldValue;
		private final int newValue;

		public SetMarkerPalette(Marker m, int value)
		{
			super("Set Palette");
			this.comp = m.npcComponent;
			oldValue = comp.paletteID;
			newValue = value;
		}

		@Override
		public boolean shouldExec()
		{
			return oldValue != newValue;
		}

		@Override
		public void exec()
		{
			super.exec();
			comp.setPaletteID(newValue);
		}

		@Override
		public void undo()
		{
			super.undo();
			comp.setPaletteID(oldValue);
		}
	}

	public static final class SetDefaultAnimation extends AbstractCommand
	{
		private final NpcComponent comp;
		private final int oldValue;
		private final int newValue;

		public SetDefaultAnimation(Marker m, int value)
		{
			super("Set Default Animation");
			this.comp = m.npcComponent;
			oldValue = comp.defaultAnimation;
			newValue = value;
		}

		@Override
		public boolean shouldExec()
		{
			return oldValue != newValue;
		}

		@Override
		public void exec()
		{
			super.exec();
			comp.setDefaultAnimation(newValue);
		}

		@Override
		public void undo()
		{
			super.undo();
			comp.setDefaultAnimation(oldValue);
		}
	}

	public static final class SetAnimationOverride extends AbstractCommand
	{
		private final NpcComponent comp;
		private final int index;
		private final boolean oldValue;
		private final boolean newValue;
		private final int oldAnimation;

		public SetAnimationOverride(Marker m, int index, boolean value)
		{
			super(value ? "Enable Animation Override" : "Disable Animation Override");
			this.comp = m.npcComponent;
			this.index = index;
			oldValue = comp.isAnimationOverridden(index);
			newValue = value;
			oldAnimation = comp.animations[index];
		}

		@Override
		public boolean shouldExec()
		{
			return oldValue != newValue;
		}

		@Override
		public void exec()
		{
			super.exec();
			if (newValue && !oldValue)
				comp.animations[index] = comp.defaultAnimation;
			comp.setAnimationOverride(index, newValue);
		}

		@Override
		public void undo()
		{
			super.undo();
			comp.animations[index] = oldAnimation;
			comp.setAnimationOverride(index, oldValue);
		}
	}

	public static final class SetAnimation extends AbstractCommand
	{
		private final NpcComponent comp;
		private final int index;
		private final int oldValue;
		private final int newValue;

		public SetAnimation(Marker m, int index, int value)
		{
			super("Set Animation");
			this.comp = m.npcComponent;
			oldValue = comp.animations[index];
			this.index = index;
			newValue = value;
		}

		@Override
		public boolean shouldExec()
		{
			return oldValue != newValue;
		}

		@Override
		public void exec()
		{
			super.exec();
			comp.animations[index] = newValue;
			comp.parentMarker.updateListeners(MarkerInfoPanel.tag_NPCAnimTab);
		}

		@Override
		public void undo()
		{
			super.undo();
			comp.animations[index] = oldValue;
			comp.parentMarker.updateListeners(MarkerInfoPanel.tag_NPCAnimTab);
		}
	}
}
