package game.sprite;

import static game.sprite.SpriteKey.*;
import static org.lwjgl.opengl.GL11.GL_ALWAYS;
import static org.lwjgl.opengl.GL11.glStencilFunc;

import java.awt.Container;
import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;

import org.w3c.dom.Element;

import common.Vector3f;
import game.map.Axis;
import game.map.BoundingBox;
import game.map.editor.render.PresetColor;
import game.map.shading.ShadingProfile;
import game.map.shape.TransformMatrix;
import game.sprite.SpriteLoader.Indexable;
import game.sprite.editor.SpriteEditor;
import game.sprite.editor.animators.CommandAnimator;
import game.sprite.editor.animators.ComponentAnimator;
import game.sprite.editor.animators.KeyframeAnimator;
import renderer.buffers.LineRenderQueue;
import renderer.shaders.RenderState;
import renderer.shaders.RenderState.PolygonMode;
import renderer.shaders.ShaderManager;
import renderer.shaders.scene.SpriteShader;
import util.xml.XmlWrapper.XmlReader;
import util.xml.XmlWrapper.XmlSerializable;
import util.xml.XmlWrapper.XmlTag;
import util.xml.XmlWrapper.XmlWriter;

public class SpriteComponent implements XmlSerializable, Indexable<SpriteComponent>
{
	public int posx, posy, posz;

	public final SpriteAnimation parentAnimation;

	public boolean usesKeyframes = false;
	private CommandAnimator cmdAnimator;
	private KeyframeAnimator keyframeAnimator;

	protected ComponentAnimator animator;

	public RawAnimation rawAnim = new RawAnimation();

	// editor fields
	public transient String name = "";
	public transient int listIndex;

	public transient boolean highlighted;
	public transient boolean selected;
	public transient boolean hidden;

	// animation state
	public SpriteRaster sr = null;
	public SpritePalette sp = null;
	public SpriteComponent parent = null;
	public int parentType;
	public int keyframeCount; // how many 'keyframes' have we gone through
	public int frameCount;
	public boolean complete;

	public int delayCount;
	public int repeatCount;

	public int flag = 1;
	public int dx, dy, dz;
	public int rx, ry, rz;
	public int scaleX, scaleY, scaleZ;

	// 4f so we can operate on them with lwjgl matrix library
	public transient Vector3f[] corners = new Vector3f[4];

	public SpriteComponent(SpriteAnimation parentAnimation)
	{
		this.parentAnimation = parentAnimation;
		cmdAnimator = new CommandAnimator(this);
		keyframeAnimator = new KeyframeAnimator(this);
		animator = usesKeyframes ? keyframeAnimator : cmdAnimator;
		reset();
	}

	// deep copy contructor
	public SpriteComponent(SpriteAnimation anim, SpriteComponent original)
	{
		this.parentAnimation = anim;
		this.usesKeyframes = original.usesKeyframes;
		this.rawAnim = new RawAnimation(original.rawAnim);
		this.cmdAnimator = new CommandAnimator(this);
		this.keyframeAnimator = new KeyframeAnimator(this);
		animator = usesKeyframes ? keyframeAnimator : cmdAnimator;
		reset();

		this.posx = original.posx;
		this.posy = original.posy;
		this.posz = original.posz;

		this.rawAnim = rawAnim.deepCopy();

		this.generate();

		this.name = original.name;

		this.sr = original.sr;
		this.sp = original.sp;
		this.parent = original.parent;
		this.parentType = original.parentType;

		this.flag = original.flag;
		this.dx = original.dx;
		this.dy = original.dy;
		this.dz = original.dz;

		this.rx = original.rx;
		this.ry = original.ry;
		this.rz = original.rz;

		this.scaleX = original.scaleX;
		this.scaleY = original.scaleY;
		this.scaleZ = original.scaleZ;
	}

	public void reset()
	{
		animator.reset();
		corners[0] = new Vector3f(0, 0, 0);
		corners[1] = new Vector3f(0, 0, 0);
		corners[2] = new Vector3f(0, 0, 0);
		corners[3] = new Vector3f(0, 0, 0);
		frameCount = 0;
	}

	public void step()
	{
		animator.step();
		frameCount += 2;
	}

	public int getX()
	{
		return posx + dx;
	}

	public int getY()
	{
		return posy + dy;
	}

	public int getZ()
	{
		return posz + dz;
	}

	public void addCorners(BoundingBox aabb)
	{
		for (Vector3f vec : corners)
			aabb.encompass((int) vec.x, (int) vec.y, (int) vec.z);
	}

	@Override
	public void toXML(XmlWriter xmw)
	{
		XmlTag compTag = xmw.createTag(TAG_COMPONENT, false);

		if (!name.isEmpty())
			xmw.addAttribute(compTag, ATTR_NAME, name);

		if (usesKeyframes)
			xmw.addBoolean(compTag, ATTR_KEYFRAMES, true);

		xmw.addIntArray(compTag, ATTR_OFFSET, posx, posy, posz);
		xmw.openTag(compTag);

		for (Short s : rawAnim) {
			XmlTag commandTag = xmw.createTag(TAG_COMMAND, true);
			xmw.addHex(commandTag, ATTR_VAL, s & 0xFFFF);
			xmw.printTag(commandTag);
		}

		List<Entry<Integer, String>> labelentries = new ArrayList<>(rawAnim.getAllLabels());
		for (Entry<Integer, String> e : labelentries) {
			XmlTag labelTag = xmw.createTag(TAG_LABEL, true);
			xmw.addHex(labelTag, ATTR_POS, e.getKey());
			xmw.addAttribute(labelTag, ATTR_POS, e.getValue());
		}

		xmw.closeTag(compTag);
	}

	@Override
	public void fromXML(XmlReader xmr, Element componentElem)
	{
		if (xmr.hasAttribute(componentElem, ATTR_NAME))
			name = xmr.getAttribute(componentElem, ATTR_NAME);

		if (xmr.hasAttribute(componentElem, ATTR_KEYFRAMES))
			usesKeyframes = xmr.readBoolean(componentElem, ATTR_KEYFRAMES);

		if (xmr.hasAttribute(componentElem, ATTR_OFFSET)) {
			int[] xyz = xmr.readIntArray(componentElem, ATTR_OFFSET, 3);
			posx = xyz[0];
			posy = xyz[1];
			posz = xyz[2];
		}
		else {
			xmr.requiresAttribute(componentElem, ATTR_X);
			xmr.requiresAttribute(componentElem, ATTR_Y);
			xmr.requiresAttribute(componentElem, ATTR_Z);
			posx = xmr.readInt(componentElem, ATTR_X);
			posy = xmr.readInt(componentElem, ATTR_Y);
			posz = xmr.readInt(componentElem, ATTR_Z);
		}

		List<Element> commandElems = xmr.getTags(componentElem, TAG_COMMAND);
		rawAnim = new RawAnimation();
		for (Element commandElem : commandElems) {
			xmr.requiresAttribute(commandElem, ATTR_VAL);
			rawAnim.add((short) xmr.readHex(commandElem, ATTR_VAL));
		}

		List<Element> labelElems = xmr.getTags(componentElem, TAG_LABEL);
		for (Element labelElem : labelElems) {
			xmr.requiresAttribute(labelElem, ATTR_NAME);
			xmr.requiresAttribute(labelElem, ATTR_POS);
			rawAnim.setLabel(xmr.readHex(labelElem, ATTR_POS), xmr.getAttribute(labelElem, ATTR_NAME));
		}
	}

	public void saveChanges()
	{
		rawAnim = animator.getCommandList();
	}

	public void convertToKeyframes()
	{
		if (usesKeyframes)
			return;

		RawAnimation rawAnim = cmdAnimator.getCommandList();
		keyframeAnimator.generate(rawAnim);
		animator = keyframeAnimator;
		usesKeyframes = true;
	}

	public void convertToCommands()
	{
		if (!usesKeyframes)
			return;

		RawAnimation rawAnim = keyframeAnimator.getCommandList();
		cmdAnimator.generate(rawAnim);
		animator = cmdAnimator;
		usesKeyframes = false;
	}

	public void validateGenerators()
	{
		List<Short> originalCommandList = new ArrayList<>(rawAnim);
		cmdAnimator.generate(rawAnim);
		List<Short> cmdCommands = cmdAnimator.getCommandList();

		// commands should ALWAYS be faithful to the original sequence
		assert (cmdCommands.size() == originalCommandList.size());
		for (int i = 0; i < cmdCommands.size(); i++) {
			short original = originalCommandList.get(i);
			short generated = cmdCommands.get(i);
			System.out.printf("%04X %04X%n", original, generated);
			assert (original == generated);
		}

		// keyframes will generally NOT equal the original sequence
		/*
		keyframeAnimator.generate(cmdList, labelNames);
		List<Short> kfCommands = keyframeAnimator.getCommandList();
		assert(kfCommands.size() == originalCommandList.size());
		for(int i = 0; i < kfCommands.size(); i++)
		{
			short original = originalCommandList.get(i);
			short generated = kfCommands.get(i);
			System.out.printf("%04X %04X%n", original, generated);
			assert(original == generated);
		}
		*/
	}

	public void generate()
	{
		animator.generate(rawAnim);
	}

	public void bind(SpriteEditor editor, Container commandListContainer, Container commandEditContainer)
	{
		animator.bind(editor, commandListContainer, commandEditContainer);
	}

	@Override
	public String toString()
	{
		return name.isEmpty() ? String.format("Comp %02X", listIndex) : name;
	}

	@Override
	public SpriteComponent getObject()
	{
		return this;
	}

	@Override
	public int getIndex()
	{
		return listIndex;
	}

	/*
	 * When composing transformations, the order is:
	 * (1) Translate
	 * (2) Rotate along Y
	 * (3) Rotate along Z
	 * (4) Rotate along X
	 * (5) Scale
	 * The order of animation commands does not matter.
	 */
	public void render(ShadingProfile spriteShading, SpritePalette paletteOverride,
		boolean enableStencilBuffer, boolean enableSelectedHighlight,
		boolean useSelectShading, boolean drawBounds, boolean useFiltering)
	{
		if (sr == null || sr.deleted || hidden)
			return;

		if (enableStencilBuffer)
			glStencilFunc(GL_ALWAYS, getIndex() + 1, 0xFF);

		SpritePalette renderPalette = sp;
		if (renderPalette == null) {
			if (paletteOverride != null)
				renderPalette = paletteOverride;
			else
				renderPalette = sr.defaultPal;
		}

		int x = (parent != null) ? parent.getX() + getX() : getX();
		int y = (parent != null) ? parent.getY() + getY() : getY();
		int z = (parent != null) ? parent.getZ() + getZ() : getZ();

		float depth = z / 100.0f;

		float w = sr.img.width / 2;
		float h = sr.img.height;
		corners[0].set(-w, 0, 0);
		corners[1].set(w, 0, 0);
		corners[2].set(w, h, 0);
		corners[3].set(-w, h, 0);

		TransformMatrix mtx = TransformMatrix.identity();

		// this is the rotation order (verified)
		if (rx != 0 || ry != 0 || rz != 0) {
			mtx.rotate(Axis.Y, ry);
			mtx.rotate(Axis.Z, rz);
			mtx.rotate(Axis.X, rx);
		}

		float renderScaleX = (scaleX == 0.0f) ? 0.0f : scaleX / 100.0f;
		float renderScaleY = (scaleY == 0.0f) ? 0.0f : scaleY / 100.0f;
		float renderScaleZ = (scaleZ == 0.0f) ? 0.0f : scaleZ / 100.0f;

		// scale after rotating (verified)
		if (scaleX != 100 || scaleY != 100 || scaleZ != 100)
			mtx.scale(renderScaleX, renderScaleY, renderScaleZ);

		mtx.translate(x, y, depth);

		for (int i = 0; i < 4; i++)
			corners[i] = mtx.applyTransform(corners[i]);

		SpriteShader shader = ShaderManager.use(SpriteShader.class);

		boolean useShading = (spriteShading != null);
		shader.useShading.set(useShading);
		if (useShading) {
			spriteShading.calculateShaderParams(mtx);
			spriteShading.setShaderParams(shader);
		}

		sr.img.glBind(shader.texture);
		renderPalette.pal.glBind(shader.palette);

		shader.useFiltering.set(useFiltering);
		shader.selectShading.set(useSelectShading);
		shader.selected.set(enableSelectedHighlight && selected);
		shader.highlighted.set(highlighted);

		float x1 = -w;
		float y1 = h;
		float x2 = w;
		float y2 = 0;

		TransformMatrix currentMtx = RenderState.pushModelMatrix();

		RenderState.setPolygonMode(PolygonMode.FILL);
		shader.setXYQuadCoords(x1, y2, x2, y1, 0); //TODO reversed?
		shader.renderQuad(TransformMatrix.multiply(currentMtx, mtx));

		RenderState.popModelMatrix();

		if (drawBounds) {
			RenderState.setColor(PresetColor.TEAL);
			RenderState.setLineWidth(2.0f);

			int v1 = LineRenderQueue.addVertex().setPosition(corners[0].x, corners[0].y, corners[0].z).getIndex();
			int v2 = LineRenderQueue.addVertex().setPosition(corners[1].x, corners[1].y, corners[1].z).getIndex();
			int v3 = LineRenderQueue.addVertex().setPosition(corners[2].x, corners[2].y, corners[2].z).getIndex();
			int v4 = LineRenderQueue.addVertex().setPosition(corners[3].x, corners[3].y, corners[3].z).getIndex();
			LineRenderQueue.addLine(v1, v2, v3, v4, v1);

			LineRenderQueue.render(true);
		}
	}
}
