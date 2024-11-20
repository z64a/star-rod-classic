package game.map.mesh;

import static game.map.MapKey.*;

import org.w3c.dom.Element;

import common.Vector3f;
import game.map.Axis;
import game.map.BoundingBox;
import game.map.MutablePoint;
import game.map.MutablePoint.PointBackup;
import game.map.ReversibleTransform;
import game.map.editor.selection.Selectable;
import game.map.shape.Model;
import game.map.shape.TransformMatrix;
import game.map.shape.UV;
import util.identity.IdentityHashSet;
import util.xml.XmlWrapper.XmlReader;
import util.xml.XmlWrapper.XmlSerializable;
import util.xml.XmlWrapper.XmlTag;
import util.xml.XmlWrapper.XmlWriter;

public class Vertex implements XmlSerializable, Selectable
{
	private int instanceVersion = latestVersion;
	private static final int latestVersion = 0;

	public transient boolean selected = false;
	public transient boolean uvselected = false;
	public transient boolean painted = false;

	// index for vertex buffer
	public transient int index = -1;

	public transient float[] normal;

	private MutablePoint localPos;
	private MutablePoint worldPos;
	public boolean useLocal;

	public transient float cutSign; // used for triangle cutting

	/**
	 * In a triangulated mesh, vertices do NOT have a unique 'parent' triangle
	 * that they belong to. The number of triangles that can share a vertex is
	 * unbounded. By convention, triangle batches are allowed to share vertices
	 * but meshes are not. Thus, vertices only have one unique parent: a mesh.
	 */
	public AbstractMesh parentMesh;

	/**
	 * The texture coordinate for this vertex is encapsulated in the UV class.
	 */
	public UV uv;

	// store as integer to keep them in range [0-255]
	public int r = 255;
	public int g = 255;
	public int b = 255;
	public int a = 255;

	public static Vertex read(XmlReader xmr, Element vertexElement)
	{
		Vertex v = new Vertex();
		v.fromXML(xmr, vertexElement);
		return v;
	}

	@Override
	public void fromXML(XmlReader xmr, Element elem)
	{
		xmr.requiresAttribute(elem, ATTR_VERT_XYZ);

		int[] xyz = xmr.readIntArray(elem, ATTR_VERT_XYZ, 3);
		localPos = new MutablePoint(xyz[0], xyz[1], xyz[2]);
		worldPos = new MutablePoint(xyz[0], xyz[1], xyz[2]);

		if (xmr.hasAttribute(elem, ATTR_VERT_UV)) {
			int[] uv = xmr.readIntArray(elem, ATTR_VERT_UV, 2);
			this.uv = new UV(uv[0], uv[1]);
		}
		else {
			this.uv = new UV(0, 0);
		}

		if (xmr.hasAttribute(elem, ATTR_VERT_COLOR)) {
			int[] rgba = xmr.readIntArray(elem, ATTR_VERT_COLOR, 4);
			r = rgba[0] & 0xFF;
			g = rgba[1] & 0xFF;
			b = rgba[2] & 0xFF;
			a = rgba[3] & 0xFF;
		}
		else {
			r = 255;
			g = 255;
			b = 255;
			a = 255;
		}
	}

	@Override
	public void toXML(XmlWriter xmw)
	{
		XmlTag vertexTag = xmw.createTag(TAG_VERTEX, true);

		xmw.addIntArray(vertexTag, ATTR_VERT_XYZ, localPos.getX(), localPos.getY(), localPos.getZ());

		if (uv.getU() != 0 || uv.getV() != 0)
			xmw.addIntArray(vertexTag, ATTR_VERT_UV, uv.getU(), uv.getV());

		if (r != 255 || g != 255 || b != 255 || a != 255)
			xmw.addIntArray(vertexTag, ATTR_VERT_COLOR, r, g, b, a);

		xmw.printTag(vertexTag);
	}

	public Vertex()
	{
		uv = new UV(0, 0);
	}

	public Vertex(int x, int y, int z)
	{
		uv = new UV(0, 0);
		localPos = new MutablePoint(x, y, z);
		worldPos = new MutablePoint(x, y, z);
	}

	public Vertex(float x, float y, float z)
	{
		this(Math.round(x), Math.round(y), Math.round(z));
	}

	public Vertex(Vector3f v)
	{
		this(v.x, v.y, v.z);
	}

	public Vertex deepCopy()
	{
		Vertex v = new Vertex();
		v.localPos = localPos.deepCopy();
		v.worldPos = worldPos.deepCopy();
		v.useLocal = this.useLocal;
		v.uv = uv.deepCopy();
		v.r = this.r;
		v.g = this.g;
		v.b = this.b;
		v.a = this.a;
		return v;
	}

	/**
	 * @return
	 * The current position for this vertex, taking into account the distiction between
	 * world/local models.
	 */
	public MutablePoint getPosition()
	{
		return useLocal ? localPos : worldPos;
	}

	public void setPositon(Vector3f pos)
	{
		if (useLocal)
			localPos.setPosition(pos);
		else
			worldPos.setPosition(pos);
	}

	public MutablePoint getLocalPosition()
	{
		return localPos;
	}

	/**
	 * @return
	 * The current x coordinate for this vertex, taking into account both incomplete
	 * transformations (previews) and distiction between world/local models.
	 */
	public Vector3f getCurrentPos()
	{
		if (useLocal)
			return new Vector3f(localPos.getX(), localPos.getY(), localPos.getZ());
		else
			return new Vector3f(worldPos.getX(), worldPos.getY(), worldPos.getZ());
	}

	/**
	 * @return
	 * The current x coordinate for this vertex, taking into account both incomplete
	 * transformations (previews) and distiction between world/local models.
	 */
	public int getCurrentX()
	{
		return useLocal ? localPos.getX() : worldPos.getX();
	}

	/**
	 * @return
	 * The current y coordinate for this vertex, taking into account both incomplete
	 * transformations (previews) and distiction between world/local models.
	 */
	public int getCurrentY()
	{
		return useLocal ? localPos.getY() : worldPos.getY();
	}

	/**
	 * @return
	 * The current z coordinate for this vertex, taking into account both incomplete
	 * transformations (previews) and distiction between world/local models.
	 */
	public int getCurrentZ()
	{
		return useLocal ? localPos.getZ() : worldPos.getZ();
	}

	/**
	 * Applies a transformation matrix to generate a world coordinate for this vertex.
	 * @param transformMatrix
	 */
	public void forceTransform(TransformMatrix transformMatrix)
	{
		transformMatrix.forceTransform(localPos, worldPos);
	}

	/**
	 * Rounds the local position of this vertex to the nearest grid interval.
	 * @param dg
	 */
	public void round(int dg)
	{
		if (parentMesh.parentObject instanceof Model) {
			if (useLocal)
				localPos.roundTemp(dg);
			// do not allow vertex rounding for transformed models
		}
		else
			worldPos.roundTemp(dg); // colliders and zones default to worldPos vertices
	}

	public void copyWorldToLocal()
	{
		localPos.setPosition(worldPos);
	}

	/**
	 * @return
	 * Byte array representing this vertex in F3DEX2.<BR>
	 * Format: XXXXYYYY ZZZZ0000 UUUUVVVV RRGGBBAA
	 */
	public byte[] getCompiledRepresentation()
	{
		byte[] bb = new byte[16];
		bb[0] = (byte) (localPos.getX() >> 8);
		bb[1] = (byte) localPos.getX();
		bb[2] = (byte) (localPos.getY() >> 8);
		bb[3] = (byte) localPos.getY();
		bb[4] = (byte) (localPos.getZ() >> 8);
		bb[5] = (byte) localPos.getZ();

		bb[8] = (byte) (uv.getU() >> 8);
		bb[9] = (byte) uv.getU();
		bb[10] = (byte) (uv.getV() >> 8);
		bb[11] = (byte) uv.getV();
		bb[12] = (byte) r;
		bb[13] = (byte) g;
		bb[14] = (byte) b;
		bb[15] = (byte) a;

		return bb;
	}

	//==================================================================
	// BEGIN: Selectable Interface Methods
	//==================================================================

	@Override
	public void addTo(BoundingBox aabb)
	{
		aabb.encompass(this);
	}

	@Override
	public boolean transforms()
	{
		return true;
	}

	@Override
	public boolean isTransforming()
	{
		return getPosition().isTransforming();
	}

	@Override
	public void startTransformation()
	{
		getPosition().startTransform();
	}

	@Override
	public void endTransformation()
	{
		getPosition().endTransform();
	}

	@Override
	public void recalculateAABB()
	{
		if (parentMesh == null || parentMesh.parentObject == null)
			return;

		parentMesh.parentObject.dirtyAABB = true;
	}

	@Override
	public boolean allowRotation(Axis axis)
	{
		return true;
	}

	@Override
	public void addPoints(IdentityHashSet<MutablePoint> positions)
	{
		positions.add(getPosition());
	}

	@Override
	public void setSelected(boolean val)
	{
		selected = val;
	}

	@Override
	public boolean isSelected()
	{
		return selected;
	}

	@Override
	public ReversibleTransform createTransformer(TransformMatrix m)
	{
		return new VertexTransformer(this);
	}

	//==================================================================
	// END: Selectable Interface Methods
	//==================================================================

	private static class VertexTransformer extends ReversibleTransform
	{
		private final Vertex v;
		private PointBackup localBackup;
		private PointBackup worldBackup;

		public VertexTransformer(Vertex v)
		{
			this.v = v;
			if (!v.useLocal) {
				// an alternative solution is to allow MapObject to have a globalTransformMatrix
				if (v.parentMesh.parentObject instanceof Model) {
					TransformMatrix globalTransform = ((Model) v.parentMesh.parentObject).cumulativeTransformMatrix;
					globalTransform.getInverse().applyTransform(v.worldPos, v.localPos);
				}
				worldBackup = v.worldPos.getBackup();
			}

			localBackup = v.localPos.getBackup();
		}

		@Override
		public void transform()
		{
			if (!v.useLocal)
				v.worldPos.setPosition(worldBackup.newx, worldBackup.newy, worldBackup.newz);

			v.localPos.setPosition(localBackup.newx, localBackup.newy, localBackup.newz);
		}

		@Override
		public void revert()
		{
			if (!v.useLocal)
				v.worldPos.setPosition(worldBackup.oldx, worldBackup.oldy, worldBackup.oldz);

			v.localPos.setPosition(localBackup.oldx, localBackup.oldy, localBackup.oldz);
		}
	}

	@Override
	public int hashCode()
	{
		final int prime = 31;
		int result = 1;
		result = prime * result + a;
		result = prime * result + b;
		result = prime * result + g;
		result = prime * result
			+ ((localPos == null) ? 0 : localPos.hashCode());
		result = prime * result + r;
		result = prime * result + (useLocal ? 1231 : 1237);
		result = prime * result + ((uv == null) ? 0 : uv.hashCode());
		result = prime * result
			+ ((worldPos == null) ? 0 : worldPos.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj)
	{
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		Vertex other = (Vertex) obj;
		if (a != other.a)
			return false;
		if (b != other.b)
			return false;
		if (g != other.g)
			return false;
		if (localPos == null) {
			if (other.localPos != null)
				return false;
		}
		else if (!localPos.equals(other.localPos))
			return false;
		if (parentMesh == null) {
			if (other.parentMesh != null)
				return false;
		}
		else if (!parentMesh.equals(other.parentMesh))
			return false;
		if (r != other.r)
			return false;
		if (useLocal != other.useLocal)
			return false;
		if (uv == null) {
			if (other.uv != null)
				return false;
		}
		else if (!uv.equals(other.uv))
			return false;
		if (worldPos == null) {
			if (other.worldPos != null)
				return false;
		}
		else if (!worldPos.equals(other.worldPos))
			return false;
		return true;
	}
}
