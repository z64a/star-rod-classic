package game.map.marker;

import static game.map.MapKey.ATTR_MARKER_RADIUS;
import static game.map.MapKey.TAG_BOMB_POS;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.function.Consumer;

import org.w3c.dom.Element;

import game.map.editor.camera.MapEditViewport;
import game.map.editor.commands.fields.EditableField;
import game.map.editor.commands.fields.EditableField.EditableFieldFactory;
import game.map.editor.render.Renderer;
import game.map.editor.render.RenderingOptions;
import game.map.editor.ui.info.marker.MarkerInfoPanel;
import game.map.shape.TransformMatrix;
import renderer.shaders.RenderState;
import renderer.shaders.ShaderManager;
import renderer.shaders.scene.LineShader;
import util.xml.XmlWrapper.XmlReader;
import util.xml.XmlWrapper.XmlTag;
import util.xml.XmlWrapper.XmlWriter;

public class BombPosComponent extends BaseMarkerComponent
{
	private final Consumer<Object> notifyCallback = (o) -> {
		parentMarker.updateListeners(MarkerInfoPanel.tag_GeneralTab);
	};

	public EditableField<Float> radius = EditableFieldFactory.create(0.0f)
		.setCallback(notifyCallback).setName("Set Bomb Radius").build();

	public BombPosComponent(Marker parent)
	{
		super(parent);
	}

	@Override
	public BombPosComponent deepCopy(Marker copyParent)
	{
		BombPosComponent copy = new BombPosComponent(copyParent);
		copy.radius.copy(radius);
		return copy;
	}

	@Override
	public void toXML(XmlWriter xmw)
	{
		XmlTag compTag = xmw.createTag(TAG_BOMB_POS, true);

		xmw.addFloat(compTag, ATTR_MARKER_RADIUS, radius.get());

		xmw.printTag(compTag);
	}

	@Override
	public void fromXML(XmlReader xmr, Element markerElem)
	{
		if (xmr.hasAttribute(markerElem, ATTR_MARKER_RADIUS)) {
			radius.set(xmr.readFloat(markerElem, ATTR_MARKER_RADIUS));
			return; //XXX temporary support for old XML format
		}

		Element triggerElem = xmr.getUniqueRequiredTag(markerElem, TAG_BOMB_POS);
		if (xmr.hasAttribute(triggerElem, ATTR_MARKER_RADIUS))
			radius.set(xmr.readFloat(triggerElem, ATTR_MARKER_RADIUS));
	}

	@Override
	public void toBinary(ObjectOutput out) throws IOException
	{
		out.writeFloat(radius.get());
	}

	@Override
	public void fromBinary(ObjectInput in) throws IOException, ClassNotFoundException
	{
		radius.set(in.readFloat());
	}

	@Override
	public void render(RenderingOptions opts, MapEditViewport view, Renderer renderer)
	{
		super.render(opts, view, renderer);

		if (!parentMarker.selected)
			return;

		float R = radius.get();
		if (R < -50.0f)
			return;

		if (R >= 0.0f) {
			LineShader shader = ShaderManager.use(LineShader.class);
			shader.useVertexColor.set(false);
			shader.color.set(1.0f, 0.0f, 0.0f, 1.0f);

			TransformMatrix mtx = TransformMatrix.identity();
			mtx.scale(50.0f + R);
			mtx.translate(parentMarker.position.getX(), parentMarker.position.getY(), parentMarker.position.getZ());

			RenderState.setLineWidth(2.0f);
			Renderer.instance().renderLineSphere36(mtx);
		}

		if (R > 0.0f) {
			LineShader shader = ShaderManager.use(LineShader.class);
			shader.useVertexColor.set(false);
			shader.color.set(1.0f, 1.0f, 1.0f, 1.0f);

			TransformMatrix mtx = TransformMatrix.identity();
			mtx.scale(R);
			mtx.translate(parentMarker.position.getX(), parentMarker.position.getY(), parentMarker.position.getZ());

			RenderState.setLineWidth(2.0f);
			Renderer.instance().renderLineSphere36(mtx);
		}
	}
}
