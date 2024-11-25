package game.fold;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.GL_CLAMP_TO_BORDER;

import java.awt.Canvas;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;

import javax.swing.JLabel;
import javax.swing.JMenuBar;
import javax.swing.JPanel;

import app.Environment;
import common.BaseEditor;
import common.BaseEditorSettings;
import common.KeyboardInput.KeyInputEvent;
import game.fold.FoldAnimations.FoldAnim;
import game.fold.FoldAnimations.FoldTriangle;
import game.fold.FoldAnimations.FoldVertex;
import game.fold.FoldCamera.BasicTraceRay;
import game.map.editor.render.RenderingOptions;
import game.map.editor.render.ShadowRenderer;
import game.map.editor.render.TextureManager;
import game.map.shape.TransformMatrix;
import game.texture.Tile;
import net.miginfocom.swing.MigLayout;
import renderer.buffers.LineRenderQueue;
import renderer.buffers.PointRenderQueue;
import renderer.buffers.TriangleRenderQueue;
import renderer.shaders.RenderState;
import renderer.shaders.RenderState.PolygonMode;
import renderer.shaders.ShaderManager;
import renderer.shaders.scene.BasicSolidShader;
import renderer.shaders.scene.BasicTexturedShader;
import renderer.shaders.scene.LineShader;
import renderer.text.DrawableString;
import renderer.text.TextRenderer;
import renderer.text.TextStyle;
import util.Logger;

public class FoldEditor extends BaseEditor
{
	private static final String MENU_BAR_SPACING = "    ";

	private static final int DEFAULT_SIZE_X = 1200;
	private static final int DEFAULT_SIZE_Y = 800;
	private static final int TOOL_PANEL_WIDTH = DEFAULT_SIZE_X / 2;

	private static final BaseEditorSettings EDITOR_SETTINGS = BaseEditorSettings.create()
		.setTitle(Environment.decorateTitle("Fold Viewer"))
		.setIcon(Environment.getDefaultIconImage())
		.setLog("battle_editor.log")
		.setFullscreen(true)
		.setResizeable(true)
		.hasMenuBar(true)
		.setSize(DEFAULT_SIZE_X, DEFAULT_SIZE_Y)
		.setFramerate(30)
		.setGrabsMouse(false) // window doesn't grab focus
		.setWaitsForDialogs(false); // window keeps running when dialogs are open

	private volatile boolean ignoreChanges = false;

	private boolean useFiltering = false;

	private final FoldCamera camera;
	private BasicTraceRay trace;

	private List<FoldAnim> foldAnims;

	private BufferedImage bgImage = null;
	private int glBackgroundTexID = -1;

	private int currentAnimID = 6;

	private static final TextStyle STYLE_SIZE_16 = new TextStyle(TextRenderer.FONT_ROBOTO)
		.setCentered(false, false)
		.setThickness(0.4f, 0.2f).setColor(1.0f, 1.0f, 0.0f)
		.enableOutline(true).setOutlineThickness(0.6f, 0.3f).setOutlineColor(0.0f, 0.0f, 0.0f)
		.enableBackground(true).setBackgroundPadding(2.0f, 2.0f).setBackgroundAlpha(0.5f);

	private DrawableString uiText;

	public static void main(String[] args) throws IOException
	{
		Environment.initialize();
		FoldEditor editor = new FoldEditor();
		editor.launch();
		Environment.exit();
	}

	public FoldEditor()
	{
		super(EDITOR_SETTINGS);

		camera = new FoldCamera(
			0.0f, 1000.0f, 0.3f,
			8.0f, 0.125f, 32.0f);
		camera.pitch = 8;
		camera.yaw = 0;
	}

	@Override
	public void beforeCreateGui()
	{
		try {
			foldAnims = FoldAnimations.load();
		}
		catch (IOException e) {
			e.printStackTrace();
		}
	}

	@Override
	public void glInit()
	{
		RenderState.init();
		TextureManager.bindEditorTextures();
		loadTextures();
		ShadowRenderer.init();

		glEnable(GL_STENCIL_TEST);
		glClearStencil(0);
		glStencilOp(GL_KEEP, GL_KEEP, GL_REPLACE);

		uiText = new DrawableString(STYLE_SIZE_16);
		uiText.setVisible(true);
		uiText.setText("TEST");

		Logger.log("Loaded folds viewer.");
	}

	private void loadTextures()
	{
		Tile img;
		BufferedImage bimg;

	}

	private static int glLoadImage(BufferedImage bimg)
	{
		ByteBuffer buffer = TextureManager.createByteBuffer(bimg);

		int glID = glGenTextures();
		glBindTexture(GL_TEXTURE_2D, glID);

		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_BORDER);
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_BORDER);

		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);

		glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, bimg.getWidth(),
			bimg.getHeight(), 0, GL_RGBA, GL_UNSIGNED_BYTE, buffer);

		return glID;
	}

	public void setModified()
	{
		// TODO track this
	}

	@Override
	protected void update(double deltaTime)
	{
		trace = camera.getTraceRay(mouse.getPosX(), mouse.getPosY());
		handleInput(deltaTime);
	}

	@Override
	public void glDraw()
	{
		RenderingOptions opts = getRenderingOptions();

		camera.glSetViewport(0, 0, glCanvasWidth(), glCanvasHeight());

		glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
		glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

		drawBackground();

		camera.setPerspView(25.0f);
		camera.glLoadTransform();
		RenderState.setModelMatrix(null);

		RenderState.setLineWidth(1.0f);

		LineRenderQueue.addLine(
			LineRenderQueue.addVertex().setPosition(Short.MIN_VALUE, 0, 0).setColor(1.0f, 0.0f, 0.0f).getIndex(),
			LineRenderQueue.addVertex().setPosition(Short.MAX_VALUE, 0, 0).setColor(1.0f, 0.0f, 0.0f).getIndex());

		LineRenderQueue.addLine(
			LineRenderQueue.addVertex().setPosition(0, Short.MIN_VALUE, 0).setColor(0.0f, 1.0f, 0.0f).getIndex(),
			LineRenderQueue.addVertex().setPosition(0, Short.MAX_VALUE, 0).setColor(0.0f, 1.0f, 0.0f).getIndex());

		LineRenderQueue.addLine(
			LineRenderQueue.addVertex().setPosition(0, 0, Short.MIN_VALUE).setColor(0.0f, 0.0f, 1.0f).getIndex(),
			LineRenderQueue.addVertex().setPosition(0, 0, Short.MAX_VALUE).setColor(0.0f, 0.0f, 1.0f).getIndex());

		LineRenderQueue.render(true);

		//	camera.yaw++;

		FoldAnim anim = foldAnims.get(currentAnimID);

		RenderState.setPointSize(5.0f);

		int i = (int) (super.getFrameCount() % anim.keyFrames);

		RenderState.setColor(1.0f, (float) i / anim.keyFrames, 1.0f);

		for (int j = 0; j < anim.vtxCount; j++) {
			FoldVertex vtx = anim.frames[i][j];
			PointRenderQueue.addPoint().setPosition(
				vtx.x, vtx.y, vtx.z);
		}

		PointRenderQueue.render(true);

		RenderState.setPolygonMode(PolygonMode.LINE);
		//		RenderState.setColor(preview.color.x, preview.color.y, preview.color.z);

		for (FoldTriangle tri : anim.triangles) {
			FoldVertex vi = anim.frames[i][tri.i];
			FoldVertex vj = anim.frames[i][tri.j];
			FoldVertex vk = anim.frames[i][tri.k];
			TriangleRenderQueue.addTriangle(
				TriangleRenderQueue.addVertex().setPosition(vi.x, vi.y, vi.z).getIndex(),
				TriangleRenderQueue.addVertex().setPosition(vj.x, vj.y, vj.z).getIndex(),
				TriangleRenderQueue.addVertex().setPosition(vk.x, vk.y, vk.z).getIndex());
		}

		LineShader shader = ShaderManager.use(LineShader.class);
		TriangleRenderQueue.render(shader, true);

		TransformMatrix projMatrix = TransformMatrix.identity();
		projMatrix.ortho(12.0f, 308.0f, 208.0f, 12.0f, -1.0f, 1.0f);
		RenderState.setProjectionMatrix(projMatrix);
		RenderState.setViewMatrix(null);

		float canvasScaleX = 296.0f / camera.glViewSizeX;
		float canvasScaleY = 200.0f / camera.glViewSizeY;

		RenderState.setDepthWrite(false);
		RenderState.setPolygonMode(PolygonMode.FILL);
		BasicSolidShader shaderd = ShaderManager.use(BasicSolidShader.class);

		int leftX = 28;
		int rightX = 292;
		int topY = 12;
		int bottomY = 208;

		/*
		// left
		shader.setXYQuadCoords(leftX - 16, 120, leftX + 16, 120 - 16, 0);
		shader.renderQuad();
		
		// right
		shader.setXYQuadCoords(rightX - 16, 120, rightX + 16, 120 - 16, 0);
		shader.renderQuad();
		
		// top
		shader.setXYQuadCoords(160 - 16, topY + 16, 160 + 16, topY, 0);
		shader.renderQuad();
		
		// bottom
		shader.setXYQuadCoords(160 - 16, bottomY, 160 + 16, bottomY - 16, 0);
		shader.renderQuad();
		
		*/

		uiText.draw(16, 0, 0, (float) super.getDeltaTime());

		int mX = mouse.getPosX();
		int mY = mouse.getPosY();
		//	System.out.println("MOUSE: " + mX + ", " + mY);
		//	System.out.println("VIEWW: " + mX * (296.0f / camera.glViewSizeX) + ", " + mY * (296.0f / camera.glViewSizeY));

		RenderState.setDepthWrite(true);
	}

	public RenderingOptions getRenderingOptions()
	{
		RenderingOptions opts = new RenderingOptions();
		opts.useFiltering = useFiltering;
		opts.useGeometryFlags = true;
		return opts;
	}

	// parallax mode takes into account yaw and fov
	public void drawBackground()
	{
		if (bgImage == null)
			return;

		TransformMatrix projMatrix = TransformMatrix.identity();
		projMatrix.ortho(0.0f, 1.0f, 1.0f, 0.0f, -1.0f, 1.0f);
		RenderState.setProjectionMatrix(projMatrix);
		RenderState.setViewMatrix(null);

		float left = camera.getYaw() / 360;
		float right = (camera.getYaw() + camera.getHFov() + 360) / 360;

		BasicTexturedShader shader = ShaderManager.use(BasicTexturedShader.class);
		shader.texture.bind(glBackgroundTexID);
		shader.setXYQuadCoords(0, 0, 1, 1, 0);
		shader.setQuadTexCoords(left, 0, right, 1);

		RenderState.setDepthWrite(false);
		shader.renderQuad();
		RenderState.setDepthWrite(true);
	}

	private void handleInput(double deltaTime)
	{
		camera.handleInput(keyboard, mouse, deltaTime, glCanvasWidth(), glCanvasHeight());
		if (keyboard.isKeyDown(KeyEvent.VK_SPACE))
			camera.resetPosition();
	}

	@Override
	public void keyPress(KeyInputEvent key)
	{
		boolean ctrl = keyboard.isCtrlDown();
		boolean shift = keyboard.isShiftDown();

		if (key.code == KeyEvent.VK_CONTROL || key.code == KeyEvent.VK_SHIFT)
			return;

		if (ctrl && shift)
			return;

		if (key.code == KeyEvent.VK_UP) {
			currentAnimID--;
			if (currentAnimID < 0)
				currentAnimID = 0;
			System.out.println(currentAnimID);
		}

		if (key.code == KeyEvent.VK_DOWN) {
			currentAnimID++;
			if (currentAnimID >= foldAnims.size())
				currentAnimID = foldAnims.size() - 1;
			System.out.println(currentAnimID);
		}

		/*
		SwingUtilities.invokeLater(() -> {
			handleKey(Keyboard.isKeyDown(Keyboard.KEY_LCONTROL) || Keyboard.isKeyDown(Keyboard.KEY_RCONTROL),
					Keyboard.isKeyDown(Keyboard.KEY_LMENU) || Keyboard.isKeyDown(Keyboard.KEY_RMENU),
					Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT),
					KeyMapper.toAWT(key));
		});
		 */
	}

	@Override
	protected void createGui(JPanel toolPanel, Canvas glCanvas, JMenuBar menuBar, JLabel infoLabel, ActionListener openLogAction)
	{
		JPanel leftSide = new JPanel(new MigLayout("fill, ins 0"));
		JPanel rightSide = new JPanel(new MigLayout("fill, ins 0"));

		toolPanel.add(glCanvas, "grow, push, wrap");

		//	toolPanel.add(leftSide, "grow, push");
		//	toolPanel.add(rightSide, "gapleft 8, grow, wrap, w " + TOOL_PANEL_WIDTH + "!");

		//		toolPanel.add(infoLabel, "h 16!, growx, span");
	}

	@Override
	protected void saveChanges()
	{

	}

	@Override
	public void clickLMB()
	{}
}
