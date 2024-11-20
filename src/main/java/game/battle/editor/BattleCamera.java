package game.battle.editor;

import static org.lwjgl.opengl.GL11.*;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;

import org.lwjgl.BufferUtils;

import common.BaseCamera;
import common.MousePixelRead;
import common.Vector3f;
import renderer.GLUtils;
import renderer.shaders.RenderState;

public class BattleCamera extends BaseCamera
{
	public final Vector3f targetPos;
	public float boomLength;
	public float boomPitch;

	/*
	public final float defaultPosY;
	public final float panSpeedScale;

	public final float defaultZoom;
	public final float minZoom;
	public final float maxZoom;

	private float zoom;
	*/
	private float maxW;
	private float maxH;

	private float vfov = 60.0f;
	private float hfov = 120.0f;

	public BattleCamera()
	{
		targetPos = new Vector3f();
		resetPosition();
	}

	public void resetPosition()
	{
		targetPos.set(0.0f, 60.0f, 0.0f);
		boomLength = 500.0f;
		boomPitch = 8.0f;
		pitch = 0.0f;
		yaw = 0.0f;
	}

	@Override
	public void updateTransfrom()
	{
		pos.set(targetPos);
		pos.z += boomLength * Math.cos(Math.toRadians(boomPitch));
		pos.y += boomLength * Math.sin(Math.toRadians(boomPitch));
	}

	public void handleInput(int dw, double deltaTime, float canvasW, float canvasH)
	{
		float zdh = 0;
		float zdv = 0;

		// zooming input
		float sdw = Math.signum(dw);
		float zh = 0;
		float zv = 0;

		/*
		// zooming in
		if(sdw > 0)
		{
			zoom /= 1.10;
			if(zoom < minZoom) {
				zoom = minZoom;
			} else {
				zh += zdh * zoom * 100;
				zv -= zdv * zoom * 100;
			}
		}

		// zooming out
		if(sdw < 0)
		{
			zoom *= 1.10;
			if(zoom > maxZoom)
				zoom = maxZoom;
		}
		*/

		/*
		// panning
		double panSpeed = 600.0 * panSpeedScale * zoom;

		int pv = 0;
		int ph = 0;
		if(Keyboard.isKeyDown(Keyboard.KEY_W)) pv -= 1;
		if(Keyboard.isKeyDown(Keyboard.KEY_S)) pv += 1;
		if(Keyboard.isKeyDown(Keyboard.KEY_A)) ph -= 1;
		if(Keyboard.isKeyDown(Keyboard.KEY_D)) ph += 1;

		float dv = zv;
		float dh = zh;

		double panMag = Math.sqrt(pv*pv + ph*ph);
		if(!MathUtil.nearlyZero(panMag))
		{
			dv = zv + (float)(deltaTime * panSpeed * (pv / panMag));
			dh = zh + (float)(deltaTime * panSpeed * (ph / panMag));
		}

		targetPos.x += dh;
		targetPos.y -= dv;
		targetPos.z  = 400.0f * zoom;

		if(targetPos.x > maxW) targetPos.x = maxW;
		if(targetPos.x < -maxW) targetPos.x = -maxW;
		if(targetPos.y > maxH) targetPos.y = maxH;
		if(targetPos.y < -maxH) targetPos.y = -maxH;
		*/
	}

	@Override
	public void glSetViewport(int minX, int minY, int sizeX, int sizeY)
	{
		float goalRatio = 320.0f / 240.0f;
		float viewRatio = (float) sizeX / sizeY;

		// sizeX = sizeXX + padX
		// sizeX = sizeXX + padX
		// such that sizeXX and sizeYY are the largest rectangle with aspect ratio = goalRatio
		int sizeXX = 0;
		int sizeYY = 0;
		int padX = 0;
		int padY = 0;

		if (viewRatio > goalRatio) // too wide
		{
			sizeYY = sizeY;
			sizeXX = Math.round(goalRatio * sizeY);
			padX = (sizeX - sizeXX) / 2;
			assert (false);
			assert (padX >= 0);
		}
		else if (viewRatio < goalRatio) // too tall
		{
			sizeXX = sizeX;
			sizeYY = Math.round(sizeX / goalRatio);
			padY = (sizeY - sizeYY) / 2;
			assert (false);
			assert (padY >= 0);
		}
		else // exactly right
		{
			sizeYY = sizeY;
			sizeXX = sizeX;
		}

		int sx = padX + (int) Math.round((12.0 / 320.0) * sizeXX);
		int ssx = (int) Math.round((296.0 / 320.0) * sizeXX);

		int sy = padY + (int) Math.round((20.0 / 240.0) * sizeYY);
		int ssy = (int) Math.round((200.0 / 240.0) * sizeYY);

		RenderState.setViewport(sx, sy, ssx, ssy);
		glViewMinX = sx;
		glViewMinY = sy;
		glViewSizeX = ssx;
		glViewSizeY = ssy;
		glAspectRatio = (float) ssx / ssy;
	}

	public void setPerspView(float fov)
	{
		projMatrix.perspective(fov, glAspectRatio, 1, 0x2000);
		glLoadProjection();

		vfov = fov;
		hfov = (float) (2.0f * Math.atan(Math.tan(vfov / 2) / glAspectRatio));
	}

	public float getHFov()
	{
		return hfov;
	}

	/**
	 * Returns a pick ray based on current mouse position.
	 * @return
	 */
	public BasicTraceRay getTraceRay(int mouseX, int mouseY)
	{
		MousePixelRead mousePixel = getMousePosition(mouseX, mouseY, false);
		Vector3f pickPoint = new Vector3f(mousePixel.worldPos);
		Vector3f direction = new Vector3f();

		direction.x = pickPoint.x - pos.x;
		direction.y = pickPoint.y - pos.y;
		direction.z = pickPoint.z - pos.z;

		direction.normalize();

		return new BasicTraceRay(new Vector3f(pos), direction, mousePixel);
	}

	// Finds the 3D coordinate of the mouse position in this camera using gluUnProject.
	private MousePixelRead getMousePosition(int mouseX, int mouseY, boolean useDepth)
	{
		IntBuffer viewport = BufferUtils.createIntBuffer(16);
		viewport.put(glViewMinX);
		viewport.put(glViewMinY);
		viewport.put(glViewSizeX);
		viewport.put(glViewSizeY);
		viewport.rewind();

		float winX = mouseX;
		float winY = mouseY;
		FloatBuffer winZ = BufferUtils.createFloatBuffer(1);

		if (useDepth) {
			// this is the expensive part, reading z from the depth buffer
			glReadPixels(mouseX, mouseY, 1, 1, GL_DEPTH_COMPONENT, GL_FLOAT, winZ);
		}
		else {
			winZ.put(0);
			winZ.rewind();
		}

		IntBuffer stencilValue = BufferUtils.createIntBuffer(1);
		glReadPixels(mouseX, mouseY, 1, 1, GL_STENCIL_INDEX, GL_UNSIGNED_INT, stencilValue);

		FloatBuffer position = BufferUtils.createFloatBuffer(3);
		GLUtils.gluUnProject(winX, winY, winZ.get(), viewMatrix.toFloatBuffer(), projMatrix.toFloatBuffer(), viewport, position);

		return new MousePixelRead(position.get(), position.get(), position.get(), stencilValue.get());
	}

	public static class BasicTraceRay
	{
		public final Vector3f origin;
		public final Vector3f direction;
		public final MousePixelRead pixelData;

		@Override
		public String toString()
		{
			return origin.toString() + " --> " + direction.toString();
		}

		public BasicTraceRay(Vector3f start, Vector3f direction, MousePixelRead pixelData)
		{
			this.origin = start;
			this.direction = direction;
			this.pixelData = pixelData;
		}

		public static boolean intersects(BasicTraceRay ray, Vector3f min, Vector3f max)
		{
			return !getIntersection(ray, min, max).missed();
		}

		public static BasicTraceHit getIntersection(BasicTraceRay ray, Vector3f min, Vector3f max)
		{
			Vector3f invDir = new Vector3f(1f / ray.direction.x, 1f / ray.direction.y, 1f / ray.direction.z);
			boolean signDirX = invDir.x < 0;
			boolean signDirY = invDir.y < 0;
			boolean signDirZ = invDir.z < 0;

			Vector3f bbox = signDirX ? max : min;
			float tmin = (bbox.x - ray.origin.x) * invDir.x;
			bbox = signDirX ? min : max;
			float tmax = (bbox.x - ray.origin.x) * invDir.x;
			bbox = signDirY ? max : min;
			float tymin = (bbox.y - ray.origin.y) * invDir.y;
			bbox = signDirY ? min : max;
			float tymax = (bbox.y - ray.origin.y) * invDir.y;

			if ((tmin > tymax) || (tymin > tmax))
				return new BasicTraceHit(ray, Float.MAX_VALUE);
			if (tymin > tmin)
				tmin = tymin;
			if (tymax < tmax)
				tmax = tymax;

			bbox = signDirZ ? max : min;
			float tzmin = (bbox.z - ray.origin.z) * invDir.z;
			bbox = signDirZ ? min : max;
			float tzmax = (bbox.z - ray.origin.z) * invDir.z;

			if ((tmin > tzmax) || (tzmin > tmax))
				return new BasicTraceHit(ray, Float.MAX_VALUE);
			if (tzmin > tmin)
				tmin = tzmin;
			if (tzmax < tmax)
				tmax = tzmax;

			return new BasicTraceHit(ray, tmin);
		}
	}

	public static class BasicTraceHit
	{
		private final float dist;
		public final Vector3f point;

		public BasicTraceHit(BasicTraceRay ray)
		{
			this(ray, Float.MAX_VALUE);
		}

		public BasicTraceHit(BasicTraceRay ray, float dist)
		{
			this.dist = dist;

			if (dist < Float.MAX_VALUE) {
				float hx = ray.origin.x + dist * ray.direction.x;
				float hy = ray.origin.y + dist * ray.direction.y;
				float hz = ray.origin.z + dist * ray.direction.z;
				point = new Vector3f(hx, hy, hz);
			}
			else
				point = null;
		}

		public boolean missed()
		{
			return dist == Float.MAX_VALUE;
		}
	}
}
