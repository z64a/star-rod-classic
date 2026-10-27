package game.map.editor.commands;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import game.map.editor.selection.SelectionManager;
import game.map.mesh.Triangle;
import game.map.mesh.Vertex;
import game.map.shape.TriangleBatch;
import game.map.shape.UV;
import util.identity.IdentityHashSet;

public class FuseVertices extends ModifyVertices
{
	private static final class FusionKey
	{
		private final TriangleBatch batch;
		private final int x;
		private final int y;
		private final int z;
		private final int u;
		private final int v;
		private final int r;
		private final int g;
		private final int b;
		private final int a;
		private final boolean useLocal;

		private FusionKey(TriangleBatch batch, Vertex vertex, boolean force)
		{
			this.batch = batch;
			x = vertex.getCurrentX();
			y = vertex.getCurrentY();
			z = vertex.getCurrentZ();
			u = force ? 0 : vertex.uv.getU();
			v = force ? 0 : vertex.uv.getV();
			r = force ? 0 : vertex.r;
			g = force ? 0 : vertex.g;
			b = force ? 0 : vertex.b;
			a = force ? 0 : vertex.a;
			useLocal = vertex.useLocal;
		}

		@Override
		public int hashCode()
		{
			final int prime = 31;
			int result = System.identityHashCode(batch);
			result = prime * result + x;
			result = prime * result + y;
			result = prime * result + z;
			result = prime * result + u;
			result = prime * result + v;
			result = prime * result + r;
			result = prime * result + g;
			result = prime * result + b;
			result = prime * result + a;
			result = prime * result + (useLocal ? 1 : 0);
			return result;
		}

		@Override
		public boolean equals(Object o)
		{
			if (!(o instanceof FusionKey other))
				return false;

			return batch == other.batch
				&& x == other.x
				&& y == other.y
				&& z == other.z
				&& u == other.u
				&& v == other.v
				&& r == other.r
				&& g == other.g
				&& b == other.b
				&& a == other.a
				&& useLocal == other.useLocal;
		}
	}

	private static final class FusionGroup
	{
		private final TriangleBatch batch;
		private final IdentityHashSet<Vertex> members = new IdentityHashSet<>();
		private Vertex vertex;

		private FusionGroup(TriangleBatch batch, Vertex vertex)
		{
			this.batch = batch;
			this.vertex = vertex;
		}

		private void fuse(boolean force)
		{
			if (!force || members.size() < 2)
				return;

			long u = 0, v = 0;
			long r = 0, g = 0, b = 0, a = 0;
			for (Vertex member : members) {
				u += member.uv.getU();
				v += member.uv.getV();
				r += member.r;
				g += member.g;
				b += member.b;
				a += member.a;
			}

			// Keep original attributes intact for undo and triangles outside the selection.
			vertex = vertex.deepCopy();
			vertex.parentMesh = batch.parentMesh;
			double count = members.size();
			vertex.uv = new UV((int) Math.round(u / count), (int) Math.round(v / count));
			vertex.r = (int) Math.round(r / count);
			vertex.g = (int) Math.round(g / count);
			vertex.b = (int) Math.round(b / count);
			vertex.a = (int) Math.round(a / count);
		}
	}

	public FuseVertices(SelectionManager selectionManager)
	{
		this(selectionManager, false);
	}

	public FuseVertices(SelectionManager selectionManager, boolean force)
	{
		super(force ? "Force Fuse Vertices" : "Fuse Vertices", selectionManager);
		List<Vertex> newVertices = new ArrayList<>(triangles.size() * 3);

		HashMap<FusionKey, FusionGroup> vertexMap = new HashMap<>();
		for (Triangle t : triangles) {
			for (Vertex v : t.vert) {
				if (isVertexSelected(v)) {
					FusionKey key = new FusionKey(t.parentBatch, v, force);
					FusionGroup group = vertexMap.computeIfAbsent(key, k -> new FusionGroup(t.parentBatch, v));
					group.members.add(v);
				}
			}
		}
		for (FusionGroup group : vertexMap.values())
			group.fuse(force);

		for (Triangle t : triangles) {
			for (Vertex v : t.vert) {
				if (!isVertexSelected(v)) {
					newVertices.add(v);
					continue;
				}

				FusionKey key = new FusionKey(t.parentBatch, v, force);
				newVertices.add(vertexMap.get(key).vertex);
			}
		}

		setNewVertices(newVertices);
	}
}
