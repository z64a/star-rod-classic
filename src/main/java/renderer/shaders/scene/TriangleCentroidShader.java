package renderer.shaders.scene;

import renderer.shaders.BaseShader;

public final class TriangleCentroidShader extends BaseShader
{
	public TriangleCentroidShader()
	{
		super("TriangleCentroidShader", VS_POINT, FS_CENTROID);
		initializeCache();
	}
}
