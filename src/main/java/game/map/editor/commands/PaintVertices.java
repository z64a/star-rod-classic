package game.map.editor.commands;

import java.util.IdentityHashMap;

import game.map.editor.render.Color4d;
import game.map.mesh.Vertex;

public class PaintVertices extends AbstractCommand
{
	IdentityHashMap<Vertex, Color4d> oldColorMap;
	IdentityHashMap<Vertex, Color4d> newColorMap;

	public PaintVertices(IdentityHashMap<Vertex, Color4d> oldColorMap, IdentityHashMap<Vertex, Color4d> newColorMap)
	{
		super("Painting " + newColorMap.size() + " Vertices");
		this.oldColorMap = oldColorMap;
		this.newColorMap = newColorMap;
	}

	@Override
	public void exec()
	{
		super.exec();

		for (Vertex v : newColorMap.keySet()) {
			Color4d newColor = newColorMap.get(v);
			v.r = newColor.r;
			v.g = newColor.g;
			v.b = newColor.b;
			v.a = newColor.a;
		}
	}

	@Override
	public void undo()
	{
		super.undo();

		for (Vertex v : newColorMap.keySet()) {
			Color4d oldColor = oldColorMap.get(v);
			v.r = oldColor.r;
			v.g = oldColor.g;
			v.b = oldColor.b;
			v.a = oldColor.a;
		}
	}
}
