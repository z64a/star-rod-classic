package game.map.marker;

import static game.map.MapKey.*;
import static game.map.editor.render.PresetColor.*;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Collection;
import java.util.List;

import org.w3c.dom.Element;

import common.Vector3f;
import game.map.Axis;
import game.map.BoundingBox;
import game.map.Map;
import game.map.MapObject;
import game.map.MutableAngle;
import game.map.MutableAngle.AngleBackup;
import game.map.MutablePoint;
import game.map.MutablePoint.PointBackup;
import game.map.ReversibleTransform;
import game.map.editor.MapEditor;
import game.map.editor.Tickable;
import game.map.editor.camera.MapEditViewport;
import game.map.editor.camera.OrthographicCamera;
import game.map.editor.camera.ViewType;
import game.map.editor.commands.AbstractCommand;
import game.map.editor.render.PresetColor;
import game.map.editor.render.Renderer;
import game.map.editor.render.RenderingOptions;
import game.map.editor.render.SortedRenderable;
import game.map.editor.render.TextureManager;
import game.map.editor.selection.PickRay;
import game.map.editor.selection.PickRay.Channel;
import game.map.editor.selection.PickRay.PickHit;
import game.map.editor.selection.SelectablePoint;
import game.map.mesh.AbstractMesh;
import game.map.mesh.BasicMesh;
import game.map.mesh.Triangle;
import game.map.shape.TransformMatrix;
import game.map.tree.MapObjectNode;
import renderer.buffers.DeferredLineRenderer;
import renderer.buffers.LineBatch;
import renderer.shaders.RenderState;
import renderer.shaders.RenderState.PolygonMode;
import renderer.shaders.ShaderManager;
import renderer.shaders.scene.MarkerShader;
import util.identity.IdentityHashSet;
import util.xml.XmlWrapper.XmlReader;
import util.xml.XmlWrapper.XmlSerializable;
import util.xml.XmlWrapper.XmlTag;
import util.xml.XmlWrapper.XmlWriter;

public class Marker extends MapObject implements Tickable, Externalizable, XmlSerializable
{
	private static final int latestVersion = 4;
	private int instanceVersion = latestVersion;

	public static boolean movePointsWithObject = false;

	public static enum MarkerType
	{
		// @formatter:off
		Root		("Root", null, null),
		Group		("Group", null, null),
		Entry		("Entry", TEAL, PINK),
		Position	("Position", YELLOW, PINK),
		Path		("Path", YELLOW, PINK),
		NPC			("NPC", GREEN, PINK),
		Entity		("Entity", YELLOW, PINK),
		Trigger		("Bomb Position", RED, PINK),
		Grid		("Push Block Grid", RED, PINK),
		CamTarget	("Camera Target", BLUE, PINK);
		// @formatter:on

		public final String name;
		private final PresetColor c1;
		private final PresetColor c2;

		private MarkerType(String name, PresetColor c1, PresetColor c2)
		{
			this.name = name;
			this.c1 = c1;
			this.c2 = c2;
		}

		public PresetColor getColor(boolean selected)
		{
			return selected ? c2 : c1;
		}

		@Override
		public String toString()
		{
			return name;
		}
	}

	// general to all markers

	private MapObjectNode<Marker> node;

	public MutablePoint position = new MutablePoint();
	public MutableAngle yaw = new MutableAngle(0, Axis.Y, true);

	public MarkerType type;

	private String description = "";

	private transient PickHit shadowHit = null;
	public transient float heightAboveGround = Float.MAX_VALUE;

	public transient BasicMesh collisionMesh = new BasicMesh();
	public transient BoundingBox collisionAABB = new BoundingBox();

	// entry
	public transient int entryID = -1;

	// components
	public NpcComponent npcComponent = new NpcComponent(this);
	public GridComponent gridComponent = new GridComponent(this);
	public PathComponent pathComponent = new PathComponent(this);
	public EntityComponent entityComponent = new EntityComponent(this);
	public CamTargetComponent cameraComponent = new CamTargetComponent(this);
	public BombPosComponent bombPosComponent = new BombPosComponent(this);

	private BaseMarkerComponent getCurrentComponent()
	{
		switch (type) {
			case Path:
				return pathComponent;
			case Grid:
				return gridComponent;
			case NPC:
				return npcComponent;
			case Entity:
				return entityComponent;
			case CamTarget:
				return cameraComponent;
			case Trigger:
				return bombPosComponent;

			case Root:
			case Group:
			case Position:
			case Entry:
				return null;
		}

		throw new IllegalStateException("Can't get component reference for marker type " + type);
	}

	/**
	 * For serialization purposes only!
	 */
	public Marker()
	{
		super(MapObjectType.MARKER);
	}

	// factory method for XML deserialization
	public static Marker read(XmlReader xmr, Element markerElem)
	{
		xmr.requiresAttribute(markerElem, ATTR_MARKER_TYPE);
		Marker m = new Marker();
		m.fromXML(xmr, markerElem);
		return m;
	}

	@Override
	public void fromXML(XmlReader xmr, Element markerElem)
	{
		super.fromXML(xmr, markerElem);

		xmr.requiresAttribute(markerElem, ATTR_VERSION);
		instanceVersion = xmr.readInt(markerElem, ATTR_VERSION);

		xmr.requiresAttribute(markerElem, ATTR_MARKER_TYPE);
		setType(xmr.readEnum(markerElem, ATTR_MARKER_TYPE, MarkerType.class));

		if (xmr.hasAttribute(markerElem, ATTR_MARKER_DESC))
			description = xmr.getAttribute(markerElem, ATTR_MARKER_DESC);

		if (type != MarkerType.Root && type != MarkerType.Group) {
			xmr.requiresAttribute(markerElem, ATTR_MARKER_POS);
			int[] xyz = xmr.readIntArray(markerElem, ATTR_MARKER_POS, 3);
			position = new MutablePoint(xyz[0], xyz[1], xyz[2]);

			xmr.requiresAttribute(markerElem, ATTR_MARKER_YAW);
			double angle = xmr.readDouble(markerElem, ATTR_MARKER_YAW);
			yaw = new MutableAngle(angle, Axis.Y, true);
		}

		BaseMarkerComponent comp = getCurrentComponent();
		if (comp != null)
			comp.fromXML(xmr, markerElem);

		node = new MapObjectNode<>(this);
	}

	@Override
	public void toXML(XmlWriter xmw)
	{
		XmlTag markerTag = xmw.createTag(TAG_MARKER, false);
		xmw.addInt(markerTag, ATTR_VERSION, latestVersion);
		xmw.addEnum(markerTag, ATTR_MARKER_TYPE, type);

		if (!description.isEmpty())
			xmw.addAttribute(markerTag, ATTR_MARKER_DESC, description);

		if (type != MarkerType.Root && type != MarkerType.Group) {
			xmw.addIntArray(markerTag, ATTR_MARKER_POS, position.getX(), position.getY(), position.getZ());
			xmw.addDouble(markerTag, ATTR_MARKER_YAW, yaw.getAngle());
		}

		xmw.openTag(markerTag);

		super.toXML(xmw);

		BaseMarkerComponent comp = getCurrentComponent();
		if (comp != null)
			comp.toXML(xmw);

		xmw.closeTag(markerTag);
	}

	@SuppressWarnings("unchecked")
	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException
	{
		super.readExternal(in);
		instanceVersion = in.readInt();

		setType((MarkerType) in.readObject());
		node = (MapObjectNode<Marker>) in.readObject();

		description = in.readUTF();

		position = (MutablePoint) in.readObject();
		yaw = new MutableAngle(in.readDouble(), Axis.Y, true);

		entryID = in.readInt();

		bombPosComponent = new BombPosComponent(this);
		bombPosComponent.fromBinary(in);

		npcComponent = new NpcComponent(this);
		npcComponent.fromBinary(in);

		gridComponent = new GridComponent(this);
		gridComponent.fromBinary(in);

		pathComponent = new PathComponent(this);
		pathComponent.fromBinary(in);

		entityComponent = new EntityComponent(this);
		entityComponent.fromBinary(in);

		cameraComponent = new CamTargetComponent(this);
		cameraComponent.fromBinary(in);
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException
	{
		super.writeExternal(out);
		out.writeInt(latestVersion);

		out.writeObject(type);
		out.writeObject(node);

		out.writeUTF(description);

		out.writeObject(position);
		out.writeDouble(yaw.getAngle());

		out.writeInt(entryID);

		bombPosComponent.toBinary(out);

		npcComponent.toBinary(out);

		gridComponent.toBinary(out);
		pathComponent.toBinary(out);

		entityComponent.toBinary(out);
		cameraComponent.toBinary(out);
	}

	protected void updateListeners(String tag)
	{
		notifyListeners(tag);
	}

	public Marker(String name, MarkerType type, float x, float y, float z, double angle)
	{
		super(MapObjectType.MARKER);
		this.instanceVersion = latestVersion;
		setType(type);
		setName(name);

		node = new MapObjectNode<>(this);

		AABB = new BoundingBox();

		position = new MutablePoint(x, y, z);
		yaw = new MutableAngle(angle, Axis.Y, true);
	}

	public static Marker createDefaultRoot()
	{
		Marker m = new Marker();
		m.setType(MarkerType.Root);
		m.setName("Root");
		m.node = new MapObjectNode<>(m);
		return m;
	}

	public static Marker createGroup(String name)
	{
		Marker m = new Marker();
		m.setType(MarkerType.Group);
		m.setName(name);
		m.node = new MapObjectNode<>(m);
		return m;
	}

	@Override
	public void initialize()
	{
		BaseMarkerComponent comp = getCurrentComponent();
		if (comp != null)
			comp.initialize();

		recalculateAABB();
	}

	@Override
	public Marker deepCopy()
	{
		Marker m = new Marker(getName(), type, position.getX(), position.getY(), position.getZ(), yaw.getAngle());
		m.AABB = AABB.deepCopy();
		m.dirtyAABB = dirtyAABB;

		m.bombPosComponent = bombPosComponent.deepCopy(m);

		m.npcComponent = npcComponent.deepCopy(m);

		m.pathComponent = pathComponent.deepCopy(m);
		m.gridComponent = gridComponent.deepCopy(m);

		m.entityComponent = entityComponent.deepCopy(m);
		m.cameraComponent = cameraComponent.deepCopy(m);

		return m;
	}

	public void putCoord(List<String> line, int axis, boolean useFloat)
	{
		if (useFloat) {
			if (axis == 0)
				line.add(String.format("%08X", Float.floatToIntBits(position.getX())));
			else if (axis == 1)
				line.add(String.format("%08X", Float.floatToIntBits(position.getY())));
			else
				line.add(String.format("%08X", Float.floatToIntBits(position.getZ())));
		}
		else {
			if (axis == 0)
				line.add(String.format("%08X", position.getX()));
			else if (axis == 1)
				line.add(String.format("%08X", position.getY()));
			else
				line.add(String.format("%08X", position.getZ()));
		}
	}

	public void putVectorXZ(List<String> line, boolean useFloat)
	{
		if (useFloat) {
			line.add(String.format("%08X", Float.floatToIntBits(position.getX())));
			line.add(String.format("%08X", Float.floatToIntBits(position.getZ())));
		}
		else {
			line.add(String.format("%08X", position.getX()));
			line.add(String.format("%08X", position.getZ()));
		}
	}

	public void putVector(List<String> line, boolean useFloat)
	{
		if (useFloat) {
			line.add(String.format("%08X", Float.floatToIntBits(position.getX())));
			line.add(String.format("%08X", Float.floatToIntBits(position.getY())));
			line.add(String.format("%08X", Float.floatToIntBits(position.getZ())));
		}
		else {
			line.add(String.format("%08X", position.getX()));
			line.add(String.format("%08X", position.getY()));
			line.add(String.format("%08X", position.getZ()));
		}
	}

	public void putPathXZ(List<String> line, boolean useFloat, int i)
	{
		assert (pathComponent != null);

		SelectablePoint p = pathComponent.path.points.get(i);

		if (useFloat) {
			line.add(String.format("%08X", Float.floatToIntBits(p.getX())));
			line.add(String.format("%08X", Float.floatToIntBits(p.getZ())));
		}
		else {
			line.add(String.format("%08X", p.getX()));
			line.add(String.format("%08X", p.getZ()));
		}
	}

	public void putPath(List<String> line, boolean useFloat, int i)
	{
		assert (pathComponent != null);

		SelectablePoint p = pathComponent.path.points.get(i);

		if (useFloat) {
			line.add(String.format("%08X", Float.floatToIntBits(p.getX())));
			line.add(String.format("%08X", Float.floatToIntBits(p.getY())));
			line.add(String.format("%08X", Float.floatToIntBits(p.getZ())));
		}
		else {
			line.add(String.format("%08X", p.getX()));
			line.add(String.format("%08X", p.getY()));
			line.add(String.format("%08X", p.getZ()));
		}
	}

	public void putPath(List<String> line, boolean useFloat)
	{
		assert (pathComponent != null);

		for (SelectablePoint p : pathComponent.path.points) {
			if (useFloat) {
				line.add(String.format("%08X", Float.floatToIntBits(p.getX())));
				line.add(String.format("%08X", Float.floatToIntBits(p.getY())));
				line.add(String.format("%08X", Float.floatToIntBits(p.getZ())));
			}
			else {
				line.add(String.format("%08X", p.getX()));
				line.add(String.format("%08X", p.getY()));
				line.add(String.format("%08X", p.getZ()));
			}
		}
	}

	public void putGrid(List<String> line)
	{
		assert (gridComponent != null);

		line.add(String.format("%08X", gridComponent.gridSizeX.get()));
		line.add(String.format("%08X", gridComponent.gridSizeZ.get()));
		line.add(String.format("%08X", position.getX()));
		line.add(String.format("%08X", position.getY()));
		line.add(String.format("%08X", position.getZ()));
	}

	public void putAngle(List<String> line, boolean useFloat)
	{
		if (useFloat)
			line.add(String.format("%08X", Float.floatToIntBits((float) yaw.getAngle())));
		else
			line.add(String.format("%08X", (int) yaw.getAngle()));
	}

	@Override
	public boolean hasSelectablePoints()
	{
		if (super.hasSelectablePoints())
			return true;

		BaseMarkerComponent comp = getCurrentComponent();
		if (comp != null)
			return comp.hasSelectablePoints();

		return false;
	}

	@Override
	public void addSelectablePoints(List<SelectablePoint> points)
	{
		super.addSelectablePoints(points);

		BaseMarkerComponent comp = getCurrentComponent();
		if (comp != null)
			comp.addSelectablePoints(points);
	}

	@Override
	public MapObjectType getObjectType()
	{
		return MapObjectType.MARKER;
	}

	@Override
	public void addTo(BoundingBox aabb)
	{
		if (position != null)
			aabb.encompass(position.getX(), position.getY(), position.getZ());
	}

	@Override
	public boolean isTransforming()
	{
		return position.isTransforming();
	}

	@Override
	public void startTransformation()
	{
		position.startTransform();
		yaw.startTransform();

		if (movePointsWithObject) {
			BaseMarkerComponent comp = getCurrentComponent();
			if (comp != null)
				comp.startTransformation();
		}
	}

	@Override
	public void endTransformation()
	{
		position.endTransform();
		yaw.endTransform();

		if (movePointsWithObject) {
			BaseMarkerComponent comp = getCurrentComponent();
			if (comp != null)
				comp.endTransformation();
		}

		recalculateAABB();
	}

	private int getSize()
	{
		if (MapEditor.instance() == null)
			return 10;

		return MapEditor.instance().objectGrid.binary ? 8 : 10;
	}

	@Override
	public void recalculateAABB()
	{
		int size = getSize();

		AABB.clear();

		if (type == MarkerType.NPC) {
			AABB.encompass(position.getX() - size, position.getY(), position.getZ() - size);
			AABB.encompass(position.getX() + size, position.getY() + 2 * size, position.getZ() + size);
		}
		/*
		else if(type.get() == MarkerType.Entity)
		{
			BoundingBox bb = entityData.type.get().typeData.collisionBox;
			AABB.encompass(transformLocalToWorld(bb.getMin(), (float)yaw.getAngle(), position.getX(), position.getY(), position.getZ()));
			AABB.encompass(transformLocalToWorld(bb.getMax(), (float)yaw.getAngle(), position.getX(), position.getY(), position.getZ()));
		}
		 */
		else if ((type != MarkerType.Root) && (type != MarkerType.Group)) {
			AABB.encompass(position.getX() - size, position.getY() - size, position.getZ() - size);
			AABB.encompass(position.getX() + size, position.getY() + size, position.getZ() + size);
		}
	}

	@Override
	public boolean allowRotation(Axis axis)
	{
		return axis == Axis.Y;
	}

	@Override
	public ReversibleTransform createTransformer(TransformMatrix m)
	{
		final IdentityHashSet<PointBackup> backupList = new IdentityHashSet<>();
		backupList.add(position.getBackup());

		if (movePointsWithObject) {
			BaseMarkerComponent comp = getCurrentComponent();
			if (comp != null)
				comp.addToBackup(backupList);
		}

		final AngleBackup backupYaw = yaw.getBackup();

		return new ReversibleTransform() {
			@Override
			public void transform()
			{
				for (PointBackup b : backupList)
					b.pos.setPosition(b.newx, b.newy, b.newz);
				yaw.setAngle(backupYaw.newAngle);

				recalculateAABB();
			}

			@Override
			public void revert()
			{
				for (PointBackup b : backupList)
					b.pos.setPosition(b.oldx, b.oldy, b.oldz);
				yaw.setAngle(backupYaw.oldAngle);

				recalculateAABB();
			}
		};
	}

	@Override
	public void addPoints(IdentityHashSet<MutablePoint> positions)
	{
		positions.add(position);

		if (movePointsWithObject) {
			BaseMarkerComponent comp = getCurrentComponent();
			if (comp != null)
				comp.addPoints(positions);
		}
	}

	@Override
	public void addAngles(IdentityHashSet<MutableAngle> angles)
	{
		angles.add(yaw);
	}

	@Override
	public boolean shouldTryPick(PickRay ray)
	{
		return true;
	}

	@Override
	public PickHit tryPick(PickRay ray)
	{
		PickHit hit = new PickHit(ray);

		BaseMarkerComponent comp = getCurrentComponent();
		if (comp != null) {
			if (ray.channel == Channel.SELECTION)
				hit = comp.trySelectionPick(ray);
			else if (comp.hasCollision())
				hit = tryCollisionPick(ray);
		}
		else
			hit = PickRay.getIntersection(ray, AABB);

		if (!hit.missed())
			hit.obj = this;
		return hit;
	}

	private PickHit tryCollisionPick(PickRay ray)
	{
		assert (ray.channel == Channel.COLLISION);

		PickHit nearestHit = new PickHit(ray, Float.MAX_VALUE);

		for (Triangle t : collisionMesh) {
			PickHit hit = PickRay.getIntersection(ray, t);
			if (hit.dist < nearestHit.dist)
				nearestHit = hit;
		}

		if (!nearestHit.missed())
			nearestHit.obj = this;
		return nearestHit;
	}

	@Override
	public boolean shouldTryTrianglePick(PickRay ray)
	{
		return false;
	}

	@Override
	public String toString()
	{
		return getName();
	}

	@Override
	public boolean hasMesh()
	{
		return false;
	}

	@Override
	public boolean shouldIterate()
	{
		return (type != MarkerType.Root) && (type != MarkerType.Group);
	}

	@Override
	public boolean shouldDraw()
	{
		return !hidden && (type != MarkerType.Root) && (type != MarkerType.Group);
	}

	@Override
	public AbstractMesh getMesh()
	{
		return null;
	}

	@Override
	public void updateMeshHierarchy()
	{}

	@Override
	public void tick(double deltaTime)
	{
		int posX = position.getX();
		int posY = position.getY();
		int posZ = position.getZ();

		Vector3f shadowOrigin = new Vector3f(posX, posY, posZ);
		PickRay shadowRay = new PickRay(Channel.COLLISION, shadowOrigin, PickRay.DOWN, false);

		MapEditor editor = MapEditor.instance();
		shadowHit = Map.pickObjectFromSet(shadowRay, editor.getCollisionMap().colliderTree, false);
		heightAboveGround = (shadowHit != null) ? shadowHit.dist : Float.MAX_VALUE;

		BaseMarkerComponent comp = getCurrentComponent();
		if (comp != null)
			comp.tick(deltaTime);
	}

	public boolean hasCollision()
	{
		BaseMarkerComponent comp = getCurrentComponent();
		return (comp != null) ? comp.hasCollision() : false;
	}

	public void addRenderables(RenderingOptions opts, Collection<SortedRenderable> renderables)
	{
		if (type == MarkerType.Root || type == MarkerType.Group)
			return;

		BaseMarkerComponent comp = getCurrentComponent();
		if (comp != null)
			comp.addRenderables(opts, renderables, shadowHit);
	}

	public void render(RenderingOptions opts, MapEditViewport view, Renderer renderer)
	{
		BaseMarkerComponent comp = getCurrentComponent();
		if (comp == null) {
			renderCube(opts, view, renderer);
			renderDirectionIndicator(opts, view, renderer);
		}
		else
			comp.render(opts, view, renderer);
	}

	public void renderCube(RenderingOptions opts, MapEditViewport view, Renderer renderer)
	{
		if (opts.thumbnailMode)
			return;

		PresetColor color = type.getColor(selected);
		float cubeAlpha = 1.0f;

		if (view.type != ViewType.PERSPECTIVE) {
			float zoomLevel = ((OrthographicCamera) view.camera).getZoomLevel();
			cubeAlpha = (zoomLevel > 0.5f) ? 1.0f : 2.0f * zoomLevel;
		}

		MarkerShader shader = ShaderManager.use(MarkerShader.class);
		shader.color.set(color.r, color.g, color.b, cubeAlpha);
		shader.texture.bind(TextureManager.glMarkerTexID);
		shader.time.set(opts.time + uniqueID * 100.0f);

		TransformMatrix mtx = TransformMatrix.identity();
		mtx.scale(getSize());
		mtx.translate(position.getX(), position.getY(), position.getZ());

		RenderState.setPolygonMode(PolygonMode.FILL);

		if (cubeAlpha < 1.0f)
			RenderState.setDepthWrite(false);
		renderer.renderTexturedCube(mtx);
		if (cubeAlpha < 1.0f)
			RenderState.setDepthWrite(true);

		RenderState.setModelMatrix(null);
	}

	public void renderDirectionIndicator(RenderingOptions opts, MapEditViewport view, Renderer renderer)
	{
		if (opts.thumbnailMode)
			return;

		RenderState.setColor(PresetColor.WHITE);
		RenderState.setLineWidth(5.0f);

		//XXX switch signs here and reverse the angle EVERYWHERE
		double dx = (32 * Math.sin(Math.toRadians(yaw.getAngle())));
		double dz = (32 * -Math.cos(Math.toRadians(yaw.getAngle())));

		float forwardX = (float) (position.getX() + dx);
		float forwardZ = (float) (position.getZ() + dz);

		LineBatch batch = DeferredLineRenderer.addLineBatch(true);
		int i = batch.addVertex().setPosition(position.getX(), position.getY(), position.getZ()).getIndex();
		int j = batch.addVertex().setPosition(forwardX, position.getY(), forwardZ).getIndex();
		batch.add(i, j);
	}

	// global --> local
	// order matters: translate then rotate
	public static Vector3f transformWorldToLocal(Vector3f vec, float yaw, float x, float y, float z)
	{
		double yawRad = Math.toRadians(yaw);
		float sinYaw = (float) Math.sin(yawRad);
		float cosYaw = (float) Math.cos(yawRad);

		float dx = vec.x + x;
		float dy = vec.y + y;
		float dz = vec.z + z;

		return new Vector3f(
			cosYaw * dx + sinYaw * dz,
			dy,
			-sinYaw * dx + cosYaw * dz);
	}

	// order matters: translate then rotate
	public static Vector3f transformLocalToWorld(Vector3f vec, float yaw, float x, float y, float z)
	{
		double yawRad = Math.toRadians(-yaw);
		float sinYaw = (float) Math.sin(yawRad);
		float cosYaw = (float) Math.cos(yawRad);

		return new Vector3f(
			x + cosYaw * vec.x + sinYaw * vec.z,
			y + vec.y,
			z + -sinYaw * vec.x + cosYaw * vec.z);
	}

	@Override
	public boolean allowsPopup()
	{
		return (type == MarkerType.Group);
	}

	@Override
	public boolean allowsChildren()
	{
		return (type == MarkerType.Root) || (type == MarkerType.Group);
	}

	public void setDescription(String desc)
	{
		if (desc == null)
			throw new IllegalArgumentException("Invalid description given for Marker.");

		this.description = desc;
	}

	public String getDescription()
	{
		return description;
	}

	@Override
	public MapObjectNode<Marker> getNode()
	{
		return node;
	}

	public MarkerType getType()
	{
		return type;
	}

	public void setType(MarkerType value)
	{
		type = value;
		//	yaw.clockwise = (value != MarkerType.Entity);
		notifyListeners();
	}

	public static final class SetType extends AbstractCommand
	{
		private Marker m;
		private final MarkerType oldValue;
		private final MarkerType newValue;

		public SetType(Marker m, MarkerType value)
		{
			super("Set Marker Type");
			this.m = m;
			oldValue = m.type;
			newValue = value;
		}

		@Override
		public boolean shouldExec()
		{
			return newValue != oldValue;
		}

		@Override
		public void exec()
		{
			super.exec();
			m.setType(newValue);
		}

		@Override
		public void undo()
		{
			super.undo();
			m.setType(oldValue);
		}
	}

	public static final class SetX extends AbstractCommand
	{
		private Marker m;
		private final int oldValue;
		private final int newValue;

		public SetX(Marker m, int x)
		{
			super("Set X Position");
			this.m = m;
			oldValue = m.position.getX();
			newValue = x;
		}

		@Override
		public boolean shouldExec()
		{
			return newValue != oldValue;
		}

		@Override
		public void exec()
		{
			super.exec();
			m.position.setX(newValue);
			m.notifyListeners();
		}

		@Override
		public void undo()
		{
			super.undo();
			m.position.setX(oldValue);
			m.notifyListeners();
		}
	}

	public static final class SetY extends AbstractCommand
	{
		private Marker m;
		private final int oldValue;
		private final int newValue;

		public SetY(Marker m, int y)
		{
			super("Set Y Position");
			this.m = m;
			oldValue = m.position.getY();
			newValue = y;
		}

		@Override
		public boolean shouldExec()
		{
			return newValue != oldValue;
		}

		@Override
		public void exec()
		{
			super.exec();
			m.position.setY(newValue);
			m.notifyListeners();
		}

		@Override
		public void undo()
		{
			super.undo();
			m.position.setY(oldValue);
			m.notifyListeners();
		}
	}

	public static final class SetZ extends AbstractCommand
	{
		private Marker m;
		private final int oldValue;
		private final int newValue;

		public SetZ(Marker m, int z)
		{
			super("Set Z Position");
			this.m = m;
			oldValue = m.position.getZ();
			newValue = z;
		}

		@Override
		public boolean shouldExec()
		{
			return newValue != oldValue;
		}

		@Override
		public void exec()
		{
			super.exec();
			m.position.setZ(newValue);
			m.notifyListeners();
		}

		@Override
		public void undo()
		{
			super.undo();
			m.position.setZ(oldValue);
			m.notifyListeners();
		}
	}

	public static final class SetAngle extends AbstractCommand
	{
		private Marker m;
		private final double oldValue;
		private final double newValue;

		public SetAngle(Marker m, float angle)
		{
			super("Set Angle");
			this.m = m;
			oldValue = m.yaw.getAngle();
			newValue = angle;
		}

		@Override
		public boolean shouldExec()
		{
			return newValue != oldValue;
		}

		@Override
		public void exec()
		{
			super.exec();
			m.yaw.setAngle(newValue);
			m.notifyListeners();
		}

		@Override
		public void undo()
		{
			super.undo();
			m.yaw.setAngle(oldValue);
			m.notifyListeners();
		}
	}
}
