package game.sound;

import static org.lwjgl.opengl.GL11.*;

import java.awt.Canvas;
import java.awt.Dimension;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.io.File;
import java.io.IOException;

import javax.swing.JCheckBoxMenuItem;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.KeyStroke;

import app.Directories;
import app.Environment;
import app.SwingUtils;
import common.BaseEditor;
import common.BaseEditorSettings;
import common.BasicCamera;
import common.KeyboardInput.KeyInputEvent;
import common.MouseInput.MouseManagerListener;
import common.MousePixelRead;
import game.map.editor.render.PresetColor;
import game.map.editor.render.TextureManager;
import net.miginfocom.swing.MigLayout;
import renderer.buffers.LineRenderQueue;
import renderer.shaders.RenderState;
import renderer.shaders.RenderState.PolygonMode;
import renderer.shaders.ShaderManager;
import renderer.shaders.scene.BasicTexturedShader;
import renderer.shaders.scene.WSPointShader;

public class AudioEditor extends BaseEditor implements MouseManagerListener
{
	public static void main(String[] args) throws IOException
	{
		Environment.initialize();
		BaseEditor editor = new AudioEditor();
		editor.launch();
		Environment.exit();
	}

	private static final BaseEditorSettings EDITOR_SETTINGS = BaseEditorSettings.create()
		.setTitle(Environment.decorateTitle("Sound Editor"))
		.setIcon(Environment.getDefaultIconImage())
		.setLog("sound_editor.log")
		.setFullscreen(true)
		.setResizeable(true)
		.hasMenuBar(true)
		.setSize(1080, 720)
		.setFramerate(60);

	private static final int MAX_SIZE = 320;

	private final BasicCamera cam;

	private boolean glTexDirty = true;
	private int glBackgroundTexID;

	private boolean bDrawBackground = false;
	private boolean bDrawGrid = true;

	private JCheckBoxMenuItem cbBackground;
	private JCheckBoxMenuItem cbGrid;

	private MousePixelRead framePick;

	private JPanel sidePanel;
	private JPanel selectedPanel;

	public AudioEditor()
	{
		super(EDITOR_SETTINGS);

		cam = new BasicCamera(
			0.0f, 0.0f, 0.5f,
			0.08f, 0.0125f, 1.0f,
			true, true);

		resetEditor();
	}

	private void resetEditor()
	{
		cbBackground.setSelected(bDrawBackground);
		cbGrid.setSelected(bDrawGrid);
		resetCam();
	}

	@Override
	protected void createGui(JPanel toolPanel, Canvas glCanvas, JMenuBar menubar, JLabel infoLabel, ActionListener openLogAction)
	{
		sidePanel = new JPanel(new MigLayout("w 320!, ins 8, wrap, hidemode 3"));
		sidePanel.add(SwingUtils.getLabel("TODO", 14), "gaptop 8");

		toolPanel.setLayout(new MigLayout("fill, ins 0, hidemode 2"));
		toolPanel.add(glCanvas, "grow, push");
		toolPanel.add(sidePanel, "growy, wrap");
		toolPanel.add(infoLabel, "h 16!, gapleft 4, gapbottom 4, span");
		addOptionsMenu(menubar, openLogAction);
		addViewMenu(menubar);
	}

	@Override
	public void glInit()
	{
		TextureManager.bindEditorTextures();
	}

	private void addOptionsMenu(JMenuBar menuBar, ActionListener openLogAction)
	{
		JMenuItem item;

		JMenu menu = new JMenu(String.format("  %-10s", "File"));
		menu.setPreferredSize(new Dimension(60, 20));
		menu.getPopupMenu().setLightWeightPopupEnabled(false);
		menuBar.add(menu);

		item = new JMenuItem("Reload");
		item.addActionListener((e) -> {
			glTexDirty = true;
		});
		menu.add(item);

		item = new JMenuItem("Save");
		item.addActionListener((e) -> {
			saveChanges();
		});
		menu.add(item);

		menu.addSeparator();

		item = new JMenuItem("Open Log");
		item.addActionListener(openLogAction);
		menu.add(item);

		menu.addSeparator();

		item = new JMenuItem("Switch Tools");
		item.addActionListener((e) -> {
			invokeLater(() -> {
				super.close(true);
			});
		});
		menu.add(item);

		item = new JMenuItem("Exit");
		item.addActionListener((e) -> {
			invokeLater(() -> {
				super.close(false);
			});
		});
		menu.add(item);
	}

	private void addViewMenu(JMenuBar menuBar)
	{
		KeyStroke dummyKeyStroke;

		JMenu menu = new JMenu(String.format("  %-10s", "  View"));
		menu.setPreferredSize(new Dimension(60, 20));
		menu.getPopupMenu().setLightWeightPopupEnabled(false);
		menuBar.add(menu);

		cbBackground = new JCheckBoxMenuItem("Background");
		cbBackground.addActionListener((e) -> {
			invokeLater(() -> {
				bDrawBackground = cbBackground.isSelected();
			});
		});
		dummyKeyStroke = KeyStroke.getKeyStroke(KeyEvent.VK_B, 0);
		cbBackground.setAccelerator(dummyKeyStroke);
		cbBackground.getInputMap(JMenuItem.WHEN_IN_FOCUSED_WINDOW).put(dummyKeyStroke, "none");
		menu.add(cbBackground);

		cbGrid = new JCheckBoxMenuItem("Grid");
		cbGrid.addActionListener((e) -> {
			invokeLater(() -> {
				bDrawGrid = cbGrid.isSelected();
			});
		});
		dummyKeyStroke = KeyStroke.getKeyStroke(KeyEvent.VK_G, 0);
		cbGrid.setAccelerator(dummyKeyStroke);
		cbGrid.getInputMap(JMenuItem.WHEN_IN_FOCUSED_WINDOW).put(dummyKeyStroke, "none");
		menu.add(cbGrid);
	}

	@Override
	protected void update(double deltaTime)
	{
		framePick = cam.getMousePosition(mouse.getPosX(), mouse.getPosY(), false, false);

		colorLerpAlpha = 0.5f * (float) Math.sin(omega * getTime());
		colorLerpAlpha = 0.5f + colorLerpAlpha * colorLerpAlpha; // more pleasing

		cam.handleInput(mouse, keyboard, deltaTime, glCanvasWidth(), glCanvasHeight());
	}

	@Override
	public void keyPress(KeyInputEvent key)
	{
		boolean ctrl = keyboard.isCtrlDown();
		boolean shift = keyboard.isShiftDown();
		boolean alt = keyboard.isAltDown();

		switch (key.code) {
			case KeyEvent.VK_SPACE:
				if (!shift && !ctrl && !alt)
					resetCam();
				break;
			case KeyEvent.VK_G:
				if (!shift && !ctrl && !alt) {
					bDrawGrid = !bDrawGrid;
					cbGrid.setSelected(bDrawGrid);
				}
				break;
			case KeyEvent.VK_B:
				if (!shift && !ctrl && !alt) {
					bDrawBackground = !bDrawBackground;
					cbBackground.setSelected(bDrawBackground);
				}
				break;
			default:
		}
	}

	private void resetCam()
	{
		int width = MAX_SIZE;
		int height = MAX_SIZE;

		cam.centerOn(glCanvasWidth(), glCanvasHeight(),
			width / 2, height / 2, 0,
			width, height, 0);

		cam.setMinPos(0, 0);
		cam.setMaxPos(width, height);
	}

	private static double omega = 1.2 * Math.PI;
	private float colorLerpAlpha = 0.0f;

	private float interpColor(float min, float max)
	{
		return min + colorLerpAlpha * (max - min);
	}

	@Override
	public void glDraw()
	{
		if (glTexDirty) {
			glBackgroundTexID = TextureManager.loadTexture(new File(Directories.MOD_IMG_ASSETS + "ui/pause/map.png"));
			glTexDirty = false;
		}

		cam.glSetViewport(0, 0, glCanvasWidth(), glCanvasHeight());

		glClearColor(0.1f, 0.1f, 0.1f, 1.0f);

		glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT | GL_STENCIL_BUFFER_BIT);

		RenderState.setDepthWrite(false);
		RenderState.setPolygonMode(PolygonMode.FILL);

		cam.setOrthoView();
		cam.glLoadTransform();
		RenderState.setModelMatrix(null);

		if (bDrawBackground) {
			BasicTexturedShader shader = ShaderManager.use(BasicTexturedShader.class);
			shader.enableFiltering.set(true);
			shader.multiplyBaseColor.set(true);
			shader.baseColor.set(1.0f, 1.0f, 1.0f, 1.0f);
			shader.saturation.set(1.0f);
			drawImage(shader, glBackgroundTexID, 0, 0, MAX_SIZE, MAX_SIZE);
			RenderState.setModelMatrix(null);
		}

		if (bDrawGrid)
			drawGrid(1.0f);

		//	drawAxes(2.0f);
	}

	private void drawCenteredImage(BasicTexturedShader shader, int texID, float x, float y, float sizeX, float sizeY)
	{
		drawImage(shader, texID, x - sizeX / 2, y - sizeY / 2, x + sizeX / 2, y + sizeY / 2);
	}

	private void drawImage(BasicTexturedShader shader, int texID, float x1, float y1, float x2, float y2)
	{
		shader.texture.bind(texID);
		shader.setXYQuadCoords(x1, y1, x2, y2, 0);
		shader.renderQuad();
	}

	private void drawCenteredImage(WSPointShader shader, int texID, float x, float y, float sizeX, float sizeY)
	{
		drawImage(shader, texID, x - sizeX / 2, y - sizeY / 2, x + sizeX / 2, y + sizeY / 2);
	}

	private void drawImage(WSPointShader shader, int texID, float x1, float y1, float x2, float y2)
	{
		shader.setXYQuadCoords(x1, y1, x2, y2, 0);
		shader.renderQuad();
	}

	protected static void drawAxes(float lineWidth)
	{
		RenderState.setLineWidth(lineWidth);
		float zpos = -10.0f;

		LineRenderQueue.addLine(
			LineRenderQueue.addVertex().setPosition(0, 0, zpos).setColor(PresetColor.RED).getIndex(),
			LineRenderQueue.addVertex().setPosition(Short.MAX_VALUE, 0, zpos).setColor(PresetColor.RED).getIndex());

		LineRenderQueue.addLine(
			LineRenderQueue.addVertex().setPosition(0, 0, zpos).setColor(PresetColor.GREEN).getIndex(),
			LineRenderQueue.addVertex().setPosition(0, Short.MAX_VALUE, zpos).setColor(PresetColor.GREEN).getIndex());

		LineRenderQueue.render(true);
	}

	private void drawGrid(float lineWidth)
	{
		RenderState.setColor(0.8f, 0.8f, 0.8f, 0.25f);
		RenderState.setLineWidth(lineWidth);
		float zpos = -10.0f;

		int max = MAX_SIZE;
		int step = 32;

		for (int i = 0; i <= max; i += step) {
			LineRenderQueue.addLine(
				LineRenderQueue.addVertex().setPosition(i, 0, zpos).getIndex(),
				LineRenderQueue.addVertex().setPosition(i, max, zpos).getIndex());

		}

		for (int i = 0; i <= max; i += step) {
			LineRenderQueue.addLine(
				LineRenderQueue.addVertex().setPosition(0, i, zpos).getIndex(),
				LineRenderQueue.addVertex().setPosition(max, i, zpos).getIndex());
		}

		RenderState.setDepthWrite(false);
		LineRenderQueue.render(true);
		RenderState.setDepthWrite(true);
	}

	@Override
	protected void saveChanges()
	{
		//TODO
	}

	@Override
	public void moveMouse(int dx, int dy)
	{
		//TODO
	}

	@Override
	public void clickLMB()
	{
		//TODO
	}

	@Override
	public void clickRMB()
	{
		//TODO
	}

	@Override
	public void startHoldingLMB()
	{
		//TODO
	}

	@Override
	public void stopHoldingLMB()
	{
		//TODO
	}

	@Override
	public void startHoldingRMB()
	{
		//TODO
	}

	@Override
	public void stopHoldingRMB()
	{
		//TODO
	}
}
