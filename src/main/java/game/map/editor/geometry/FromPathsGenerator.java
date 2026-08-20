package game.map.editor.geometry;

import common.Vector3f;

import game.map.editor.MapEditor;
import game.map.marker.PathPoint;
import game.map.mesh.Triangle;
import game.map.mesh.Vertex;
import game.map.shape.TransformMatrix;
import game.map.shape.TriangleBatch;
import game.map.shape.UV;
import util.IterableListModel;
import util.MathUtil;

public class FromPathsGenerator
{
	public static TriangleBatch generate(
		IterableListModel<PathPoint> path,
		boolean twistPerUnitLength, boolean rollWithSpline,
		int radius, int taper, int startAngle, int twist)
	{
		int num = path.size();
		if (num < 2)
			return null;

		Vector3f[] center = new Vector3f[num];
		Vector3f[] fwd = new Vector3f[num];
		Vector3f[] norm = new Vector3f[num];
		Vector3f[] tang = new Vector3f[num];
		TransformMatrix[] transforms = new TransformMatrix[num];

		for (int i = 0; i < num; i++)
			center[i] = path.get(i).getPosition();

		fwd[0] = Vector3f.sub(center[1], center[0]);
		for (int i = 1; i < num - 1; i++) {
			Vector3f prev = Vector3f.sub(center[i], center[i - 1]);
			Vector3f next = Vector3f.sub(center[i + 1], center[i]);
			fwd[i] = Vector3f.add(next, prev);
			if (fwd[i].length() < MathUtil.SMALL_NUMBER)
				fwd[i].set(prev);
			if (fwd[i].length() < MathUtil.SMALL_NUMBER)
				fwd[i].set(next);
		}
		fwd[num - 1] = Vector3f.sub(center[num - 1], center[num - 2]);

		for (int i = 0; i < num; i++) {
			if (fwd[i].length() < MathUtil.SMALL_NUMBER) {
				if (i > 0)
					fwd[i].set(fwd[i - 1]);
				else
					fwd[i].set(1.0f, 0.0f, 0.0f);
			}
			else
				fwd[i].normalize();
		}

		norm[0] = getHorizontalNormal(fwd[0], null);
		for (int i = 1; i < num; i++) {
			if (rollWithSpline)
				norm[i] = transportNormal(norm[i - 1], fwd[i - 1], fwd[i]);
			else
				norm[i] = getHorizontalNormal(fwd[i], norm[i - 1]);
		}

		for (int i = 0; i < num; i++) {
			tang[i] = Vector3f.cross(fwd[i], norm[i]);
			if (tang[i].length() < MathUtil.SMALL_NUMBER)
				tang[i].set(0.0f, -1.0f, 0.0f);
			else
				tang[i].normalize();

			transforms[i] = new TransformMatrix();
			transforms[i].makeRotation(fwd[i], norm[i], tang[i]);
		}

		return generateTwistedRibbon(transforms, center, center, norm, tang,
			true, twistPerUnitLength,
			radius, taper, startAngle, twist, 2);
	}

	private static Vector3f getHorizontalNormal(Vector3f fwd, Vector3f previous)
	{
		Vector3f norm = Vector3f.cross(fwd, new Vector3f(0.0f, 1.0f, 0.0f));
		if (norm.length() < MathUtil.SMALL_NUMBER) {
			if (previous != null)
				norm.set(previous);
			else
				norm.set(1.0f, 0.0f, 0.0f);
		}
		else
			norm.normalize();

		if (previous != null && Vector3f.dot(norm, previous) < 0.0f)
			norm.negate();

		return norm;
	}

	private static Vector3f transportNormal(Vector3f norm, Vector3f prevFwd, Vector3f nextFwd)
	{
		Vector3f axis = Vector3f.cross(prevFwd, nextFwd);
		float sinAngle = axis.length();
		float cosAngle = MathUtil.clamp(Vector3f.dot(prevFwd, nextFwd), -1.0f, 1.0f);

		Vector3f transported = new Vector3f(norm);
		if (sinAngle >= MathUtil.SMALL_NUMBER) {
			axis.scale(1.0f / sinAngle);
			Vector3f cross = Vector3f.cross(axis, norm);
			float projection = Vector3f.dot(axis, norm) * (1.0f - cosAngle);

			transported.x = norm.x * cosAngle + cross.x * sinAngle + axis.x * projection;
			transported.y = norm.y * cosAngle + cross.y * sinAngle + axis.y * projection;
			transported.z = norm.z * cosAngle + cross.z * sinAngle + axis.z * projection;
		}

		float forwardProjection = Vector3f.dot(transported, nextFwd);
		transported.x -= forwardProjection * nextFwd.x;
		transported.y -= forwardProjection * nextFwd.y;
		transported.z -= forwardProjection * nextFwd.z;
		if (transported.length() < MathUtil.SMALL_NUMBER)
			return getHorizontalNormal(nextFwd, norm);

		return transported.normalize();
	}

	public static TriangleBatch generate(
		IterableListModel<PathPoint> pathA, IterableListModel<PathPoint> pathB,
		boolean overrideRadius, boolean twistPerUnitLength,
		int radius, int taper, int startAngle, int twist, int segments)
	{

		int num = Math.min(pathA.size(), pathB.size());
		if (num < 2)
			return null;

		Vector3f[] center = new Vector3f[num];
		Vector3f[] edge = new Vector3f[num];
		Vector3f[] fwd = new Vector3f[num];
		Vector3f[] norm = new Vector3f[num];
		Vector3f[] tang = new Vector3f[num];
		TransformMatrix[] transforms = new TransformMatrix[num];

		for (int i = 0; i < num; i++) {
			center[i] = pathA.get(i).getPosition();
			edge[i] = pathB.get(i).getPosition();
			fwd[i] = new Vector3f();
			norm[i] = new Vector3f();
			tang[i] = new Vector3f();

			norm[i] = Vector3f.sub(edge[i], center[i]);
			if (norm[i].length() < MathUtil.SMALL_NUMBER)
				norm[i].set(0.0f, 1.0f, 0.0f);
			else
				norm[i].normalize();
		}

		// first segment
		fwd[0] = Vector3f.sub(center[1], center[0]);
		// middle segments
		for (int i = 1; i < num - 1; i++) {
			Vector3f prev = new Vector3f();
			Vector3f next = new Vector3f();
			next = Vector3f.sub(center[i + 1], center[i]);
			prev = Vector3f.sub(center[i], center[i - 1]);
			fwd[i] = Vector3f.add(next, prev);
			if (fwd[i].length() < MathUtil.SMALL_NUMBER)
				fwd[i].set(prev);
			if (fwd[i].length() < MathUtil.SMALL_NUMBER)
				fwd[i].set(next);
		}
		// last segments
		fwd[num - 1] = Vector3f.sub(center[num - 1], center[num - 2]);

		// normalize all fwd
		for (int i = 0; i < num; i++) {
			if (fwd[i].length() < MathUtil.SMALL_NUMBER)
				fwd[i].set(1.0f, 0.0f, 0.0f);
			else
				fwd[i].normalize();
		}

		for (int i = 0; i < num; i++) {
			if (Math.abs(Vector3f.dot(fwd[i], norm[i])) < 0.99f) {
				// make orthogonal basis
				tang[i] = Vector3f.cross(fwd[i], norm[i]);
				fwd[i] = Vector3f.cross(norm[i], tang[i]);
			}
			else {
				// use default basis
				fwd[i].set(1.0f, 0.0f, 0.0f);
				norm[i].set(0.0f, 1.0f, 0.0f);
				tang[i].set(0.0f, 0.0f, 1.0f);
			}
		}

		for (int i = 0; i < num; i++) {
			transforms[i] = new TransformMatrix();
			transforms[i].makeRotation(fwd[i], norm[i], tang[i]);
		}

		if (segments < 3) {
			if (startAngle == 0 && twist == 0)
				return generateRibbon(transforms, center, edge, norm, tang,
					overrideRadius, radius, taper, segments);
			else
				return generateTwistedRibbon(transforms, center, edge, norm, tang,
					overrideRadius, twistPerUnitLength,
					radius, taper, startAngle, twist, segments);
		}
		else
			return generatePipe(transforms, center, edge, norm, tang,
				overrideRadius, twistPerUnitLength,
				radius, taper, startAngle, twist, segments);
	}

	private static TriangleBatch generateRibbon(TransformMatrix[] transforms,
		Vector3f[] center, Vector3f[] edge, Vector3f[] norm, Vector3f[] tang,
		boolean overrideRadius, int radius, int taper, int segments)
	{
		float uvScale = MapEditor.instance().getDefaultUVScale();
		Vertex[][] grid = new Vertex[center.length][2];

		Vector3f delta = new Vector3f();
		float totalLenA = 0;
		float lenA = 0;

		for (int i = 1; i < center.length; i++) {
			delta = Vector3f.sub(center[i], center[i - 1]);
			totalLenA += delta.length();
		}

		for (int i = 0; i < center.length; i++) {
			// update length along path
			if (i > 0) {
				delta = Vector3f.sub(center[i], center[i - 1]);
				lenA += delta.length();
			}

			if (overrideRadius) {
				delta.set(norm[i]);

				float rawScale;
				if (taper < 0)
					rawScale = Math.abs(taper) * (totalLenA - lenA) / totalLenA;
				else
					rawScale = Math.abs(taper) * lenA / totalLenA;
				float taperScale = MathUtil.clamp(1.0f - rawScale / 100.0f, 0.0f, 1.0f);

				delta.x = delta.x * radius * taperScale;
				delta.y = delta.y * radius * taperScale;
				delta.z = delta.z * radius * taperScale;
			}
			else
				delta = Vector3f.sub(edge[i], center[i]);

			if (delta.length() < MathUtil.SMALL_NUMBER)
				delta.set(0.0f, 1.0f, 0.0f);

			if (segments == 2)
				grid[i][0] = new Vertex(center[i].x - delta.x, center[i].y - delta.y, center[i].z - delta.z);
			else
				grid[i][0] = new Vertex(center[i].x, center[i].y, center[i].z);

			grid[i][1] = new Vertex(center[i].x + delta.x, center[i].y + delta.y, center[i].z + delta.z);

			grid[i][0].uv = new UV(Math.round(uvScale * lenA), 0);
			grid[i][1].uv = new UV(Math.round(uvScale * lenA), 2048);
		}

		TriangleBatch batch = new TriangleBatch(null);

		for (int i = 0; i < center.length - 1; i++) {
			Triangle t1 = new Triangle(grid[i][1], grid[i + 1][0], grid[i][0]);
			Triangle t2 = new Triangle(grid[i + 1][0], grid[i][1], grid[i + 1][1]);
			batch.triangles.add(t1);
			batch.triangles.add(t2);
		}

		return batch;
	}

	private static TriangleBatch generateTwistedRibbon(TransformMatrix[] transforms,
		Vector3f[] center, Vector3f[] edge, Vector3f[] norm, Vector3f[] tang,
		boolean overrideRadius, boolean twistPerUnitLength,
		int radius, int taper, int startAngle, int twist, int segments)
	{
		float uvScale = MapEditor.instance().getDefaultUVScale();
		Vertex[][] grid = new Vertex[center.length][2];

		Vector3f delta = new Vector3f();
		float totalLenA = 0;
		float lenA = 0;

		for (int i = 1; i < center.length; i++) {
			delta = Vector3f.sub(center[i], center[i - 1]);
			totalLenA += delta.length();
		}

		for (int i = 0; i < center.length; i++) {
			// update length along path
			if (i > 0) {
				delta = Vector3f.sub(center[i], center[i - 1]);
				lenA += delta.length();
			}

			float currentRadius;
			double angle;

			if (twistPerUnitLength)
				angle = Math.toRadians(twist * (lenA / 200.0f) + startAngle);
			else
				angle = Math.toRadians(twist * i + startAngle);

			if (overrideRadius) {
				float rawScale;
				if (taper < 0)
					rawScale = Math.abs(taper) * (totalLenA - lenA) / totalLenA;
				else
					rawScale = Math.abs(taper) * lenA / totalLenA;
				float taperScale = MathUtil.clamp(1.0f - rawScale / 100.0f, 0.0f, 1.0f);
				currentRadius = radius * taperScale;
			}
			else {
				delta = Vector3f.sub(edge[i], center[i]);
				currentRadius = delta.length();
			}

			delta.set(0.0f, currentRadius * (float) Math.cos(angle), -currentRadius * (float) Math.sin(angle));
			delta = transforms[i].applyTransform(delta);

			if (segments == 2)
				grid[i][0] = new Vertex(center[i].x - delta.x, center[i].y - delta.y, center[i].z - delta.z);
			else
				grid[i][0] = new Vertex(center[i].x, center[i].y, center[i].z);

			grid[i][1] = new Vertex(center[i].x + delta.x, center[i].y + delta.y, center[i].z + delta.z);

			grid[i][0].uv = new UV(Math.round(uvScale * lenA), 0);
			grid[i][1].uv = new UV(Math.round(uvScale * lenA), 2048);
		}

		TriangleBatch batch = new TriangleBatch(null);

		for (int i = 0; i < center.length - 1; i++) {
			Triangle t1 = new Triangle(grid[i][1], grid[i + 1][0], grid[i][0]);
			Triangle t2 = new Triangle(grid[i + 1][0], grid[i][1], grid[i + 1][1]);
			batch.triangles.add(t1);
			batch.triangles.add(t2);
		}

		return batch;
	}

	private static TriangleBatch generatePipe(TransformMatrix[] transforms,
		Vector3f[] center, Vector3f[] edge, Vector3f[] norm, Vector3f[] tang,
		boolean overrideRadius, boolean twistPerUnitLength,
		int radius, int taper, int startAngle, int twist, int segments)
	{
		float uvScale = MapEditor.instance().getDefaultUVScale();
		Vertex[][] grid = new Vertex[center.length][segments + 1];

		Vector3f delta = new Vector3f();
		float totalLenA = 0;
		float lenA = 0;

		for (int i = 1; i < center.length; i++) {
			delta = Vector3f.sub(center[i], center[i - 1]);
			totalLenA += delta.length();
		}

		for (int i = 0; i < center.length; i++) {
			// update length along path
			if (i > 0) {
				delta = Vector3f.sub(center[i], center[i - 1]);
				lenA += delta.length();
			}

			for (int j = 0; j <= segments; j++) {
				float currentRadius;
				double angle;

				if (twistPerUnitLength)
					angle = Math.toRadians(twist * (lenA / 100.0f) + startAngle + 360 * ((float) j / segments));
				else
					angle = Math.toRadians(twist * i + startAngle + 360 * ((float) j / segments));

				if (overrideRadius) {
					float rawScale;
					if (taper < 0)
						rawScale = Math.abs(taper) * (totalLenA - lenA) / totalLenA;
					else
						rawScale = Math.abs(taper) * lenA / totalLenA;
					float taperScale = MathUtil.clamp(1.0f - rawScale / 100.0f, 0.0f, 1.0f);
					currentRadius = radius * taperScale;
				}
				else {
					delta = Vector3f.sub(edge[i], center[i]);
					currentRadius = delta.length();
				}

				delta.set(0.0f, currentRadius * (float) Math.sin(angle), currentRadius * (float) Math.cos(angle));
				delta = transforms[i].applyTransform(delta);

				grid[i][j] = new Vertex(center[i].x - delta.x, center[i].y - delta.y, center[i].z - delta.z);
			}

			// evenly-spaced UVs around each pipe section
			for (int j = 0; j <= segments; j++)
				grid[i][j].uv = new UV(Math.round(uvScale * lenA), 2048 * ((float) j / segments));
		}

		TriangleBatch batch = new TriangleBatch(null);

		for (int i = 0; i < center.length - 1; i++) {
			for (int j = 0; j < segments; j++) {
				Triangle t1 = new Triangle(grid[i][j + 1], grid[i + 1][j], grid[i][j]);
				Triangle t2 = new Triangle(grid[i + 1][j], grid[i][j + 1], grid[i + 1][j + 1]);
				batch.triangles.add(t1);
				batch.triangles.add(t2);
			}
		}

		return batch;
	}
}
