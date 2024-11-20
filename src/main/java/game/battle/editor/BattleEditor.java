package game.battle.editor;

import static app.Directories.DUMP_IMG_ASSETS;
import static app.Directories.MOD_IMG_TEX;
import static game.texture.TileFormat.CI_8;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.GL_CLAMP_TO_BORDER;

import java.awt.Canvas;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;

import org.apache.commons.io.FilenameUtils;

import com.alexandriasoftware.swing.JSplitButton;

import app.Directories;
import app.Environment;
import app.StarRodException;
import common.BaseEditor;
import common.BaseEditorSettings;
import common.KeyboardInput.KeyInputEvent;
import common.Vector3f;
import game.battle.ActorTypesEditor;
import game.battle.ActorTypesEditor.ActorType;
import game.battle.editor.BattleCamera.BasicTraceRay;
import game.battle.editor.ui.ActorPanel;
import game.map.Map;
import game.map.MapObject;
import game.map.editor.render.Renderer;
import game.map.editor.render.RenderingOptions;
import game.map.editor.render.ShadowRenderer;
import game.map.editor.render.ShadowRenderer.RenderableShadow;
import game.map.editor.render.SortedRenderable;
import game.map.editor.render.TextureManager;
import game.map.editor.ui.dialogs.ChooseDialogResult;
import game.map.editor.ui.dialogs.OpenFileChooser;
import game.map.shape.TransformMatrix;
import game.sprite.Sprite;
import game.sprite.SpriteLoader;
import game.sprite.SpriteLoader.SpriteSet;
import game.texture.ImageConverter;
import game.texture.Tile;
import net.miginfocom.swing.MigLayout;
import renderer.shaders.RenderState;
import renderer.shaders.RenderState.PolygonMode;
import renderer.shaders.ShaderManager;
import renderer.shaders.scene.BasicTexturedShader;
import shared.Globals;
import shared.SwingUtils;
import util.Logger;
import util.Priority;
import util.ui.ListAdapterComboboxModel;

public class BattleEditor extends BaseEditor
{
	private static final String MENU_BAR_SPACING = "    ";

	private static final int DEFAULT_SIZE_X = 1200;
	private static final int DEFAULT_SIZE_Y = 800;
	private static final int TOOL_PANEL_WIDTH = DEFAULT_SIZE_X / 2;

	private static final BaseEditorSettings EDITOR_SETTINGS = BaseEditorSettings.create()
		.setTitle(Environment.decorateTitle("Battle Editor"))
		.setIcon(Globals.getDefaultIconImage())
		.setLog("battle_editor.log")
		.setFullscreen(true)
		.setResizeable(true)
		.hasMenuBar(true)
		.setSize(DEFAULT_SIZE_X, DEFAULT_SIZE_Y)
		.setFramerate(30)
		.setGrabsMouse(false) // window doesn't grab focus
		.setWaitsForDialogs(false); // window keeps running when dialogs are open

	private static final Dimension POPUP_OPTION_SIZE = new Dimension(150, 24);

	private SpriteLoader spriteLoader;
	private OpenFileChooser texFileChooser;

	private volatile boolean ignoreChanges = false;

	private JPanel sectionSettingsPanel;
	private JComboBox<Stage> stagesComboBox;
	private JComboBox<Formation> formationsComboBox;
	private JComboBox<Actor> actorsComboBox;

	private ActorPanel actorPanel;

	private JCheckBox cbUseDefaultStageForFormation;

	private boolean useFiltering = false;

	private final BattleCamera camera;
	private BasicTraceRay trace;

	private EditorMode editorMode = EditorMode.Formations;

	private static enum EditorMode
	{
		Formations (0, "Formation"),
		Stages (1, "Stage"),
		Actors (2, "Actor"),
		Parts (3, "Parts"),
		Moves (4, "Moves"),
		ActorTypes (5, "Actor Types");

		public final int tabIndex;
		public final String tabName;

		private EditorMode(int tabIndex, String tabName)
		{
			this.tabIndex = tabIndex;
			this.tabName = tabName;
		}
	}

	private static enum PartnerActor
	{
		GOOMBARIO (-130, 0, -10, 0x09, 1, 2, "Goombario"),
		KOOPER (-130, 0, -10, 0x0A, 4, 5, "Kooper"),
		BOMBETTE (-130, 0, -10, 0x0B, 4, 6, "Bombette"),
		PARAKARRY (-130, 30, -10, 0x0C, 1, 2, "Parakarry"),
		BOW (-130, 20, -10, 0x0D, 1, 2, "Bow"),
		WATT (-130, 20, -10, 0x0E, 1, 2, "Watt"), //TODO convert these to actual actors and give watt his second part
		SUSHIE (-130, 0, -10, 0x0F, 1, 2, "Sushie"),
		LAKILESTER (-130, 10, -10, 0x10, 1, 1, "Lakilester");

		public final int posX;
		public final int posY;
		public final int posZ;
		public final int spriteID;
		public final int calmAnim;
		public final int idleAnim;
		public final String name;

		public Sprite sprite;

		private PartnerActor(int posX, int posY, int posZ, int spriteID, int calmAnim, int idleAnim, String name)
		{
			this.posX = posX;
			this.posY = posY;
			this.posZ = posZ;
			this.spriteID = spriteID;
			this.calmAnim = calmAnim;
			this.idleAnim = idleAnim;
			this.name = name;
		}

		@Override
		public String toString()
		{
			return name;
		}
	}

	private static final Vector3f MARIO_POS = new Vector3f(-95, 0, 0);
	private static final Vector3f UP_NORM = new Vector3f(0, 1, 0);

	private List<ActorType> actorTypes;

	private BattleSection currentBattleSection = null;
	private Stage currentStage = null;
	private Formation currentFormation = null;
	private Actor currentActor = null;

	private Map stageMap;
	private boolean loadingStage = false;
	private BufferedImage bgImage = null;
	private int glBackgroundTexID = -1;

	private int healthBarTexID = -1;

	private Sprite playerSprite;
	private PartnerActor currentPartner = PartnerActor.GOOMBARIO;

	// fields used by the renderer, don't set these from the gui
	private volatile int spriteID;

	public static void main(String[] args) throws IOException
	{
		Environment.initialize();
		BattleEditor editor = new BattleEditor();
		editor.launch();
		Environment.exit();
	}

	public BattleEditor()
	{
		super(EDITOR_SETTINGS);

		camera = new BattleCamera();
		camera.pitch = 8;
		camera.yaw = 0;

		//useNpcFiles(DEFAULT_NPC_SPRITE);
		modified = true; //TODO actually track this

		try {
			actorTypes = ActorTypesEditor.load();
		}
		catch (IOException e) {
			actorTypes = null;
			throw new StarRodException("IOException while loading actor type data: %n%s", e.getMessage());
		}
	}

	public void loadBattleSection(BattleSection section)
	{
		assert (!SwingUtilities.isEventDispatchThread());

		currentBattleSection = section;
		if (section == null) {
			sectionSettingsPanel.setVisible(false);
			stagesComboBox.setModel(new DefaultComboBoxModel<>());
			formationsComboBox.setModel(new DefaultComboBoxModel<>());
			actorsComboBox.setModel(new DefaultComboBoxModel<>());

			setStage(null);
			setFormation(null);
			setActor(null);
		}
		else {
			sectionSettingsPanel.setVisible(true);
			stagesComboBox.setModel(new ListAdapterComboboxModel<>(section.stages));
			formationsComboBox.setModel(new ListAdapterComboboxModel<>(section.formations));
			actorsComboBox.setModel(new ListAdapterComboboxModel<>(section.actors));

			setStage(null); // will be set to formation default
			setFormation(section.formations.isEmpty() ? null : section.formations.get(0));
			setActor(section.actors.isEmpty() ? null : section.actors.get(0));
		}
	}

	public void setStage(Stage stage)
	{
		assert (!SwingUtilities.isEventDispatchThread());

		if (currentStage == stage)
			return;

		currentStage = stage;
		if (currentStage == null) {
			openMap(null);
			setBackground(null);
		}
		else {
			openMap(new File(Directories.MOD_MAP_SRC + stage.name + ".xml"));
			setBackground(stage.bgName);
		}

		if (stagesComboBox.getSelectedItem() != stage) {
			ignoreChanges = true;
			stagesComboBox.setSelectedItem(stage);
			ignoreChanges = false;
		}
	}

	public void setFormation(Formation formation)
	{
		assert (!SwingUtilities.isEventDispatchThread());

		if (currentFormation == formation)
			return;

		currentFormation = formation;

		if (formationsComboBox.getSelectedItem() != formation) {
			ignoreChanges = true;
			formationsComboBox.setSelectedItem(formation);
			ignoreChanges = false;
		}

		if (cbUseDefaultStageForFormation.isSelected())
			setStage((formation == null) ? null : formation.stage);
	}

	public void setActor(Actor actor)
	{
		assert (!SwingUtilities.isEventDispatchThread());

		if (currentActor == actor)
			return;

		currentActor = actor;
		actorPanel.setData(currentActor);

		if (actorsComboBox.getSelectedItem() != actor) {
			ignoreChanges = true;
			actorsComboBox.setSelectedItem(actor);
			ignoreChanges = false;
		}
	}

	public ActorType getActorType(int index)
	{
		if (actorTypes == null)
			return null;

		if (index < 0 || index >= actorTypes.size())
			return null;

		return actorTypes.get(index);
	}

	@Override
	public void beforeCreateGui()
	{
		spriteLoader = new SpriteLoader();
	}

	@Override
	public void glInit()
	{
		RenderState.init();
		TextureManager.bindEditorTextures();
		loadTextures();
		ShadowRenderer.init();

		playerSprite = spriteLoader.getSprite(SpriteSet.Player, 1);
		//	playerSprite.enableStencilBuffer = true;
		playerSprite.prepareForEditor();
		playerSprite.loadTextures();

		for (PartnerActor partner : PartnerActor.values()) {
			partner.sprite = spriteLoader.getSprite(SpriteSet.Npc, partner.spriteID);
			partner.sprite.prepareForEditor();
			partner.sprite.loadTextures();
		}

		glEnable(GL_STENCIL_TEST);
		glClearStencil(0);
		glStencilOp(GL_KEEP, GL_KEEP, GL_REPLACE);

		BattleSection battleSection = new BattleSection(new File(Directories.DUMP_FORMA + "17 Area KZN2.xml"));
		loadBattleSection(battleSection);

		Logger.log("Loaded battles editor.");
	}

	private void loadTextures()
	{
		Tile img;
		BufferedImage bimg;

		try {
			img = Tile.load(new File(DUMP_IMG_ASSETS + "ui/battle/hp_bar.png"), CI_8);
			bimg = ImageConverter.convertToBufferedImage(img);
			healthBarTexID = glLoadImage(bimg);
		}
		catch (IOException e) {
			throw new StarRodException("Could not image: ");
		}
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

		if (currentFormation != null) {
			for (Unit unit : currentFormation.units)
				unit.tick(deltaTime);
		}

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

		List<SortedRenderable> renderables = new ArrayList<>();
		if (stageMap != null) {
			for (MapObject obj : stageMap.modelTree)
				obj.prepareVertexBuffers(opts);

			for (MapObject obj : stageMap.colliderTree)
				obj.prepareVertexBuffers(opts);

			for (MapObject obj : stageMap.zoneTree)
				obj.prepareVertexBuffers(opts);

			// first pass for models
			renderables = Renderer.getRenderables(opts, stageMap.modelTree, null, false);
		}

		renderables.add(new RenderableShadow(new Vector3f(
			MARIO_POS.x, 0, MARIO_POS.z), UP_NORM, 0, true, true, 100.0f));
		renderables.add(new RenderableShadow(new Vector3f(
			currentPartner.posX, 0, currentPartner.posZ), UP_NORM, currentPartner.posY, true, true, 100.0f));

		// following func_802559BC for shadow-logic
		for (Unit unit : getVisibleUnits()) {
			if ((unit.actor.flags.get() & 1) != 0)
				continue;

			float shadowScale = unit.actor.size.get(0) / 24.0f;
			float[] unitPos = unit.getPos();

			renderables.add(new RenderableShadow(new Vector3f(unitPos[0], 0, unitPos[2]),
				UP_NORM, unitPos[1], true, true, 100.0f * shadowScale));

			for (UnitPart part : unit.parts) {
				if ((part.actorPart.flags.get() & 4) != 0)
					continue;

				float[] partPos = Unit.getPartPos(unit, part);

				renderables.add(new RenderableShadow(new Vector3f(partPos[0], 0, partPos[2]),
					UP_NORM, partPos[1], true, true, 100.0f * shadowScale));
			}
		}

		RenderState.setModelMatrix(null);

		renderables = Renderer.sortByRenderDepth(camera, renderables);
		Renderer.drawOpaque(opts, camera, renderables);

		TransformMatrix mtx = TransformMatrix.identity();
		mtx.setScale(-1, 1, 1);
		mtx.scale(Sprite.WORLD_SCALE);
		//	mtx.rotate(Axis.Y, -renderYaw);
		mtx.translate(MARIO_POS);
		RenderState.setModelMatrix(mtx);
		playerSprite.updateAnimation(4);
		playerSprite.render(null, 4, 0, useFiltering, false);

		mtx = TransformMatrix.identity();
		mtx.setScale(-1, 1, 1);
		mtx.scale(Sprite.WORLD_SCALE);
		//	mtx.rotate(Axis.Y, -renderYaw);
		mtx.translate(currentPartner.posX, currentPartner.posY, currentPartner.posZ);
		RenderState.setModelMatrix(mtx);
		currentPartner.sprite.updateAnimation(currentPartner.idleAnim);
		currentPartner.sprite.render(null, currentPartner.idleAnim, 0, useFiltering, false);

		for (Unit unit : getVisibleUnits()) {
			if ((unit.actor.flags.get() & 1) != 0)
				continue;

			unit.render(opts);
		}

		RenderState.setModelMatrix(null);

		// second pass for models
		Renderer.drawTranslucent(opts, camera, renderables);

		//
		TransformMatrix projMatrix = TransformMatrix.identity();
		projMatrix.ortho(12.0f, 308.0f, 208.0f, 12.0f, -1.0f, 1.0f);
		RenderState.setProjectionMatrix(projMatrix);
		RenderState.setViewMatrix(null);

		float canvasScaleX = 296.0f / camera.glViewSizeX;
		float canvasScaleY = 200.0f / camera.glViewSizeY;

		RenderState.setDepthWrite(false);
		RenderState.setPolygonMode(PolygonMode.FILL);
		BasicTexturedShader shader = ShaderManager.use(BasicTexturedShader.class);
		shader.texture.bind(healthBarTexID);

		//	BasicSolidShader shader = ShaderManager.use(BasicSolidShader.class);

		for (Unit unit : getVisibleUnits()) {
			float halfSizeX = unit.actor.size.get(0) / 2;
			float halfSizeY = unit.actor.size.get(1) / 2;

			//	System.out.println("X: " + unit.getPos()[0]);
			//	System.out.println("Y: " + unit.getPos()[1]);
			//	System.out.println("Z: " + unit.getPos()[2]);

			Vector3f screenPos = camera.getScreenCoords(unit.getPos()[0], unit.getPos()[1], unit.getPos()[2]);
			System.out.println(screenPos); // matches mouse

			float hpBarPosX = (screenPos.x - camera.glViewMinX) * 296.0f / camera.glViewSizeX;
			float hpBarPosY = 200 * (1.0f - (screenPos.y - camera.glViewMinY) / camera.glViewSizeY);

			//	System.out.println(hpBarPosX + " " + hpBarPosY);

			//	System.out.println("sx: " + hpBarPosX);
			//	System.out.println("sy: " + hpBarPosY);
			/*
			if((unit.actor.flags.get() & 0x800) != 0)
			{
				hpBarPosX += halfSizeX - unit.actor.healthBarOffset.get(0);
				hpBarPosY += halfSizeY - unit.actor.healthBarOffset.get(1);
			}
			else
			{
				hpBarPosX += -halfSizeX + unit.actor.healthBarOffset.get(0);
				hpBarPosY += -halfSizeY + unit.actor.healthBarOffset.get(1);
			}
			/
			*/

			shader.setXYQuadCoords(hpBarPosX - 16, hpBarPosY + 16, hpBarPosX + 16, hpBarPosY, 0);
			shader.renderQuad();
		}

		int leftX = 28;
		int rightX = 292;
		int topY = 12;
		int bottomY = 208;

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

		int mX = mouse.getPosX();
		int mY = mouse.getPosY();
		System.out.println("MOUSE: " + mX + ", " + mY);
		System.out.println("VIEWW: " + mX * (296.0f / camera.glViewSizeX) + ", " + mY * (296.0f / camera.glViewSizeY));

		RenderState.setDepthWrite(true);
	}

	private Iterable<Unit> getVisibleUnits()
	{
		if (currentFormation == null)
			return new ArrayList<>();
		else
			return currentFormation.units;

		// TODO different modes
	}

	public RenderingOptions getRenderingOptions()
	{
		RenderingOptions opts = new RenderingOptions();
		opts.useFiltering = useFiltering;
		opts.useGeometryFlags = true;
		return opts;
	}

	public void openMap(File f)
	{
		if (stageMap != null)
			TextureManager.clear();

		if (f == null) {
			stageMap = null;
			return;
		}

		stageMap = f.exists() ? Map.loadMap(f) : null;

		if (stageMap == null) {
			Logger.log("Could not open " + f.getName(), Priority.WARNING);
			return;
		}

		loadingStage = true;
		stageMap.texName = stageMap.getExpectedTexFilename();
		loadMapResources();
		stageMap.initializeAllObjects();
		loadingStage = false;
	}

	private void loadMapResources()
	{
		boolean loadedTextures = TextureManager.load(stageMap.texName);

		while (!loadedTextures) {
			int choice = SwingUtils.getConfirmDialog()
				.setParent(getFrame())
				.setTitle("Missing Texture Archive")
				.setMessage("Could not open texture archive \"" + stageMap.texName + "\", select a different one?")
				.setOptionsType(JOptionPane.YES_NO_OPTION)
				.choose();

			if (choice == JOptionPane.YES_OPTION) {
				if (texFileChooser.prompt() == ChooseDialogResult.APPROVE && texFileChooser.getSelectedFile() != null) {
					File f = texFileChooser.getSelectedFile();
					String texName = FilenameUtils.getBaseName(f.getName());
					loadedTextures = TextureManager.load(texName);
				}
			}
			else
				break;
		}

		TextureManager.assignModelTextures(stageMap);
	}

	public void setBackground(String bgName)
	{
		if (bgImage != null)
			glDeleteTextures(glBackgroundTexID);

		if (bgName == null || bgName.isEmpty()) {
			Logger.log("Cleared background.");
			bgImage = null;
			return;
		}

		try {
			bgImage = ImageIO.read(new File(Directories.MOD_IMG_BG + bgName + ".png"));
			glBackgroundTexID = TextureManager.bindBufferedImage(bgImage);
			Logger.log("Loaded background " + bgName);
		}
		catch (IOException e) {
			Logger.log("Could not load background image!", Priority.WARNING);
			bgImage = null;
		}
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
		camera.handleInput(mouse.getFrameDW(), deltaTime, glCanvasWidth(), glCanvasHeight());
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
		File texDir = Environment.project.isDecomp ? Environment.project.getDirectory() : MOD_IMG_TEX.toFile();
		texFileChooser = new OpenFileChooser(texDir, "Select Texture Archive", "Texture Archives", "txa");

		JLabel spriteSpinnerLabel = new JLabel("Sprite ID");
		SwingUtils.setFontSize(spriteSpinnerLabel, 14);

		//TODO other panels
		actorPanel = new ActorPanel(this);

		JTabbedPane editorModeTabs = new JTabbedPane();
		editorModeTabs.addChangeListener((e) -> {
			JTabbedPane sourceTabbedPane = (JTabbedPane) e.getSource();
			int index = sourceTabbedPane.getSelectedIndex();
			editorMode = EditorMode.values()[index];
		});
		createTab(editorModeTabs, EditorMode.Formations, new JPanel());
		createTab(editorModeTabs, EditorMode.Stages, new JPanel());
		createTab(editorModeTabs, EditorMode.Actors, actorPanel);
		createTab(editorModeTabs, EditorMode.Parts, new JPanel());
		createTab(editorModeTabs, EditorMode.Moves, new JPanel());
		//	createTab(editorModeTabs, EditorMode.ActorTypes, new JPanel());

		JComboBox<PartnerActor> partnerComboBox = new JComboBox<>(PartnerActor.values());
		partnerComboBox.addActionListener((e) -> {
			currentPartner = (PartnerActor) partnerComboBox.getSelectedItem();
		});
		SwingUtils.setFontSize(partnerComboBox, 12);
		SwingUtils.addBorderPadding(partnerComboBox);

		sectionSettingsPanel = createSettingsPanel();

		JPanel leftSide = new JPanel(new MigLayout("fill, ins 0"));
		JPanel rightSide = new JPanel(new MigLayout("fill, ins 0"));

		rightSide.add(editorModeTabs, "grow, push, span, wrap");

		JPanel previewSettingsPanel = new JPanel(new MigLayout("fill, ins 16"));

		previewSettingsPanel.add(SwingUtils.getLabel("Partner", 14), "w 80lp!");
		previewSettingsPanel.add(partnerComboBox, "w 200lp!");
		previewSettingsPanel.add(new JPanel(), "grow, pushx, wrap");

		previewSettingsPanel.add(sectionSettingsPanel, "grow, span, wrap, gaptop 16lp");
		previewSettingsPanel.add(new JPanel(), "grow, pushy, span");

		leftSide.add(glCanvas, "grow, pushy, wrap");
		leftSide.add(previewSettingsPanel, "grow, h 25%, wrap");

		toolPanel.add(leftSide, "grow, push");
		toolPanel.add(rightSide, "gapleft 8, grow, wrap, w " + TOOL_PANEL_WIDTH + "!");

		toolPanel.add(infoLabel, "h 16!, growx, span");

		/*
		addOptionsMenu(menuBar, openLogAction);
		addEditorMenu(menuBar);
		addSpriteMenu(menuBar);
		addRenderingMenu(menuBar);
		 */

		/*
		KeyboardFocusManager manager = KeyboardFocusManager.getCurrentKeyboardFocusManager();
		manager.addKeyEventDispatcher(new KeyEventDispatcher()
		{
			@Override
			public boolean dispatchKeyEvent(KeyEvent e)
			{
				return handleKey(e.isControlDown(), e.isAltDown(), e.isShiftDown(), e.getKeyCode());
			}
		});
		 */
	}

	private JPanel createSettingsPanel()
	{
		cbUseDefaultStageForFormation = new JCheckBox("Use default stage for formation");
		cbUseDefaultStageForFormation.setSelected(true);

		cbUseDefaultStageForFormation.addActionListener((e) -> {
			if (ignoreChanges)
				return;
			invokeLater(() -> {
				if (cbUseDefaultStageForFormation.isSelected()) {
					if (currentFormation == null) {
						if (currentStage != null)
							setStage(null);
					}
					else if (currentStage != currentFormation.stage)
						setStage(currentFormation.stage);
				}
			});
		});

		stagesComboBox = new JComboBox<>();
		stagesComboBox.addActionListener((e) -> {
			if (ignoreChanges)
				return;
			invokeLater(() -> setStage((Stage) stagesComboBox.getSelectedItem()));
		});
		SwingUtils.setFontSize(stagesComboBox, 12);
		SwingUtils.addBorderPadding(stagesComboBox);

		formationsComboBox = new JComboBox<>();
		formationsComboBox.addActionListener((e) -> {
			if (ignoreChanges)
				return;
			invokeLater(() -> setFormation((Formation) formationsComboBox.getSelectedItem()));
		});
		SwingUtils.setFontSize(formationsComboBox, 12);
		SwingUtils.addBorderPadding(formationsComboBox);

		actorsComboBox = new JComboBox<>();
		actorsComboBox.addActionListener((e) -> {
			if (ignoreChanges)
				return;
			invokeLater(() -> setActor((Actor) actorsComboBox.getSelectedItem()));
		});
		SwingUtils.setFontSize(actorsComboBox, 12);
		SwingUtils.addBorderPadding(actorsComboBox);

		JSplitButton stageButton = new JSplitButton("Edit   ");
		JPopupMenu stageMenu = new JPopupMenu();
		buildPopupStageMenu(stageMenu);
		stageButton.setPopupMenu(stageMenu);
		stageButton.setAlwaysPopup(true);
		SwingUtils.addBorderPadding(stageButton);

		JSplitButton formationButton = new JSplitButton("Edit   ");
		JPopupMenu formationMenu = new JPopupMenu();
		buildPopupStageMenu(formationMenu); //TODO
		formationButton.setPopupMenu(formationMenu);
		formationButton.setAlwaysPopup(true);
		SwingUtils.addBorderPadding(formationButton);

		JSplitButton actorButton = new JSplitButton("Edit   ");
		JPopupMenu actorMenu = new JPopupMenu();
		buildPopupStageMenu(actorMenu); //TODO
		actorButton.setPopupMenu(actorMenu);
		actorButton.setAlwaysPopup(true);
		SwingUtils.addBorderPadding(actorButton);

		JPanel panel = new JPanel(new MigLayout("fill, ins 0"));

		panel.add(SwingUtils.getLabel("Formation", 14), "w 80lp!");
		panel.add(formationsComboBox, "w 200lp!");
		panel.add(formationButton, "w 80lp!");
		panel.add(new JPanel(), "growx, pushx, wrap");

		panel.add(SwingUtils.getLabel("Actor", 14), "w 80lp!");
		panel.add(actorsComboBox, "w 200lp!");
		panel.add(actorButton, "w 80lp!");
		panel.add(new JPanel(), "growx, pushx, wrap");

		panel.add(SwingUtils.getLabel("Stage", 14), "w 80lp!");
		panel.add(stagesComboBox, "w 200lp!");
		panel.add(stageButton, "w 80lp!");
		panel.add(new JPanel(), "growx, pushx, wrap");

		panel.add(new JPanel(), "w 80lp!");
		panel.add(cbUseDefaultStageForFormation, "growx, span, wrap");

		return panel;
	}

	private static void createTab(JTabbedPane tabs, EditorMode mode, Container contents)
	{
		JLabel lbl = SwingUtils.getLabel(mode.tabName, SwingConstants.CENTER, 12);
		lbl.setPreferredSize(new Dimension(90, 20));

		JScrollPane scrollPane = new JScrollPane(contents);
		scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		scrollPane.setBorder(null);
		tabs.addTab(null, scrollPane);
		tabs.setTabComponentAt(tabs.getTabCount() - 1, lbl);
	}

	private void buildPopupStageMenu(JPopupMenu menu)
	{
		JMenuItem item;

		item = new JMenuItem("Create New");
		item.setPreferredSize(POPUP_OPTION_SIZE);
		item.addActionListener(e -> {
			//TODO
		});
		menu.add(item);

		item = new JMenuItem("Duplicate");
		item.setPreferredSize(POPUP_OPTION_SIZE);
		item.addActionListener(e -> {
			//TODO
		});
		menu.add(item);

		item = new JMenuItem("Delete");
		item.setPreferredSize(POPUP_OPTION_SIZE);
		item.addActionListener(e -> {
			//TODO
		});
		menu.add(item);
	}

	/*
	private boolean handleKey(boolean ctrl, boolean alt, boolean shift, int key)
	{
		// no multiple modifers
		int count = 0;
		if(ctrl)  count++;
		if(alt)   count++;
		if(shift) count++;
		if(count > 1)
			return false;

		// switch key --> java.awk.KeyEvent.*, e.g. KeyEvent.VK_DOWN

		return false;
	}
	 */

	/*(
	private void addEditorMenu(JMenuBar menuBar)
	{
		JMenuItem item;

		JMenu menu = new JMenu(MENU_BAR_SPACING + "Editor" + MENU_BAR_SPACING);
		menu.getPopupMenu().setLightWeightPopupEnabled(false);
		menuBar.add(menu);

		item = new JMenuItem("View Shortcuts");
		item.addActionListener((e)-> {
			showControls();
		});
		menu.add(item);
	}

	private void addOptionsMenu(JMenuBar menuBar, ActionListener openLogAction)
	{
		JMenuItem item;

		JMenu menu = new JMenu(MENU_BAR_SPACING + "Options" + MENU_BAR_SPACING);
		menu.getPopupMenu().setLightWeightPopupEnabled(false);
		menuBar.add(menu);

		item = new JMenuItem("Load Player Sprites");
		item.addActionListener((e)-> {
			invokeLater(() -> {
				usePlayerFiles(DEFAULT_PLAYER_SPRITE);
			});
		});
		menu.add(item);

		item = new JMenuItem("Load NPC Sprites");
		item.addActionListener((e)-> {
			invokeLater(() -> {
				useNpcFiles(DEFAULT_NPC_SPRITE);
			});
		});
		menu.add(item);

		menu.addSeparator();

		item = new JMenuItem("Open Log");
		item.addActionListener(openLogAction);
		menu.add(item);

		if(!RunContext.mainConfig.getBoolean(Options.ExitToMenu)) {
			item = new JMenuItem("Switch Tools");
			item.addActionListener((e)-> {
				invokeLater(() -> {
					super.close(true);
				});
			});
			menu.add(item);
		}

		item = new JMenuItem("Exit");
		item.addActionListener((e)-> {
			invokeLater(() -> {
				super.close(false);
			});
		});
		menu.add(item);
	}

	private void addSpriteMenu(JMenuBar menuBar)
	{
		JMenuItem item;

		JMenu menu = new JMenu(MENU_BAR_SPACING + "Sprite" + MENU_BAR_SPACING);
		menu.getPopupMenu().setLightWeightPopupEnabled(false);
		menuBar.add(menu);

		item = new JMenuItem("Save Changes");
		item.addActionListener((e)-> {
			invokeLater(() -> {
				saveSprite();
			});
		});
		menu.add(item);

		item = new JMenuItem("Reload Current Sprite");
		item.addActionListener((e)-> {
			invokeLater(() -> {
				setSprite(spriteID, true);
			});
		});
		menu.add(item);

		menu.addSeparator();

		item = new JMenuItem("Convert to Keyframes");
		item.addActionListener((e)-> {
			invokeLater(() -> {
				if(sprite != null)
				{
					sprite.convertToKeyframes();
					setComponent(currentComp.getIndex(), false);
				}
			});
		});
		menu.add(item);

		item = new JMenuItem("Convert to Commands");
		item.addActionListener((e)-> {
			invokeLater(() -> {
				if(sprite != null)
				{
					sprite.convertToCommands();
					setComponent(currentComp.getIndex(), false);
				}
			});
		});
		menu.add(item);
	}

	private void addRenderingMenu(JMenuBar menuBar)
	{
		JMenu menu = new JMenu(MENU_BAR_SPACING + "Rendering" + MENU_BAR_SPACING);
		menu.getPopupMenu().setLightWeightPopupEnabled(false);
		menuBar.add(menu);

		final JMenuItem itemBackground = new JCheckBoxMenuItem("Show Background");
		itemBackground.setSelected(showBackground);
		itemBackground.addActionListener((e)-> {
			showBackground = itemBackground.isSelected();
		});
		menu.add(itemBackground);

		final JMenuItem itemFilter = new JCheckBoxMenuItem("Use Filtering");
		itemFilter.setSelected(useFiltering);
		itemFilter.addActionListener((e)-> {
			useFiltering = itemFilter.isSelected();
		});
		menu.add(itemFilter);

		menu.addSeparator();

		final JMenuItem itemGuide = new JCheckBoxMenuItem("Show Scale Reference");
		itemGuide.setSelected(showGuide);
		itemGuide.addActionListener((e)-> {
			showGuide = itemGuide.isSelected();
		});
		menu.add(itemGuide);

		final JMenuItem itemFlip = new JCheckBoxMenuItem("Flip Horizontally");
		itemFlip.setSelected(flipHorizontal);
		itemFlip.addActionListener((e)-> {
			flipHorizontal = itemFlip.isSelected();
		});
		menu.add(itemFlip);

		menu.addSeparator();

		final JMenuItem itemHighlightComp = new JCheckBoxMenuItem("Highlight Selected Component");
		itemHighlightComp.setSelected(true); //XXX -- really odd! this is called *before* instance variable initialization!
		itemHighlightComp.addActionListener((e)-> {
			highlightComponent = itemHighlightComp.isSelected();
		});
		menu.add(itemHighlightComp);

		final JMenuItem itemHighlightCmd = new JCheckBoxMenuItem("Highlight Current Command");
		itemHighlightCmd.setSelected(true); //XXX -- really odd! this is called *before* instance variable initialization!
		itemHighlightCmd.addActionListener((e)-> {
			highlightCommand = itemHighlightCmd.isSelected();
		});
		menu.add(itemHighlightCmd);
	}

	private JPanel getAnimationsTab()
	{
		JLabel defaultPaletteLabel = new JLabel("Palette");
		SwingUtils.setFontSize(defaultPaletteLabel, 12);

		/*
		paletteComboBox = new JComboBox<>();
		SwingUtils.setFontSize(paletteComboBox, 14);
		paletteComboBox.setMaximumRowCount(24);
		paletteComboBox.setRenderer(new IndexableComboBoxRenderer());
		paletteComboBox.addActionListener((e) -> {
			if(cbOverridePalette.isSelected())
				animOverridePalette = (SpritePalette)paletteComboBox.getSelectedItem();
		});

		animationComboBox = new JComboBox<>();
		SwingUtils.setFontSize(animationComboBox, 14);
		animationComboBox.setMaximumRowCount(24);
		animationComboBox.setRenderer(new IndexableComboBoxRenderer());

		compxSpinner = new JSpinner();
		SwingUtils.setFontSize(compxSpinner, 12);
		compxSpinner.setModel(new SpinnerNumberModel(0, -128, 128, 1));
		((DefaultEditor)compxSpinner.getEditor()).getTextField().setHorizontalAlignment(JTextField.CENTER);
		compxSpinner.addChangeListener((e) -> {
			if(currentComp != null)
				currentComp.posx = (int)compxSpinner.getValue();
		});

		compySpinner = new JSpinner();
		SwingUtils.setFontSize(compySpinner, 12);
		compySpinner.setModel(new SpinnerNumberModel(0, -128, 128, 1));
		((DefaultEditor)compySpinner.getEditor()).getTextField().setHorizontalAlignment(JTextField.CENTER);
		compySpinner.addChangeListener((e) -> {
			if(currentComp != null)
				currentComp.posy = (int)compySpinner.getValue();
		});

		compzSpinner = new JSpinner();
		SwingUtils.setFontSize(compzSpinner, 12);
		compzSpinner.setModel(new SpinnerNumberModel(0, -32, 32, 1));
		((DefaultEditor)compzSpinner.getEditor()).getTextField().setHorizontalAlignment(JTextField.CENTER);
		compzSpinner.addChangeListener((e) -> {
			if(currentComp != null)
				currentComp.posz = (int)compzSpinner.getValue();
		});

		cbShowOnlySelectedComponent = new JCheckBox(" Draw current only");
		cbShowOnlySelectedComponent.setSelected(false);
		cbShowOnlySelectedComponent.setToolTipText("Only render the currently selected component in the preview window.");

		cbOverridePalette = new JCheckBox(" Override default pal");
		cbOverridePalette.setSelected(false);
		cbOverridePalette.addActionListener((e) -> {
			if(cbOverridePalette.isSelected())
				animOverridePalette = (SpritePalette)paletteComboBox.getSelectedItem();
			else
				animOverridePalette = null;
		});

		JButton editAnimationsButton = new JButton("Edit Animation List");
		editAnimationsButton.addActionListener((e)-> {
			if(sprite == null)
				return;
			showAnimationsEditorWindow();
		});

		JButton addComponentButton = new JButton("Edit Components");
		addComponentButton.addActionListener((e)-> {
			if(sprite == null || currentAnim == null)
				return;
			showComponentsEditorWindow();
		});

		JPanel relativePosPanel = new JPanel(new MigLayout("fill, ins 0, wrap"));
		relativePosPanel.add(SwingUtils.getLabel("Relative Position:", SwingConstants.RIGHT, 12));
		relativePosPanel.add(compxSpinner, "w 72!, sg spin, split 3, gaptop 4");
		relativePosPanel.add(compySpinner, "w 72!, sg spin");
		relativePosPanel.add(compzSpinner, "w 72!, sg spin");

		playbackStatusLabel = SwingUtils.getLabel("", SwingConstants.RIGHT, 12);
		playbackFrameLabel = SwingUtils.getLabel("", SwingConstants.RIGHT, 12);

		JPanel playbackPanel = new JPanel(new MigLayout("fill, ins 0"));
		playbackPanel.add(SwingUtils.getLabel("Playback Controls:", SwingConstants.RIGHT, 12));
		playbackPanel.add(playbackStatusLabel, "growx");
		playbackPanel.add(playbackFrameLabel, "growx, w 40::, wrap");
		playbackPanel.add(getControlPanel(), "pushx, growx, span");

		commandListPanel = new JPanel(new MigLayout("ins 0, fill"));
		commandEditPanel = new JPanel(new MigLayout("ins 0, fill"));

		componentPanel = new JPanel(new MigLayout("fill, ins 16", "[]32[grow]")); //XXX 35%
		componentPanel.add(relativePosPanel, "grow");
		componentPanel.add(playbackPanel, "grow, wrap");
		componentPanel.add(commandListPanel, "gaptop 16, grow, pushy");
		componentPanel.add(commandEditPanel, "gaptop 16, grow, push");

		componentTabPool = new ArrayList<>();

		componentTabs = new JTabbedPane();
		componentTabs.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);

		componentTabs.addChangeListener((e) ->
		{
			if(ignoreComponentTabChanges)
				return;

			final int id = componentTabs.getSelectedIndex();
			if(id >= 0)
			{
				invokeLater(() -> {
					setComponent(id, true);
				});
			}
		});

		componentTabs.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent evt)
			{
				if(evt.getClickCount() == 3)
				{
					int tabIndex = componentTabs.indexAtLocation(evt.getX(), evt.getY());
					if(tabIndex < 0)
						return;

					incrementDialogsOpen();
					String input = JOptionPane.showInputDialog(
							componentTabs,
							"Enter a new name", "Rename Component",
							JOptionPane.PLAIN_MESSAGE);
					decrementDialogsOpen();

					if(input == null)
						return;

					String newName = input.trim();

					if(newName.isEmpty())
						return;

					SpriteComponent comp = currentAnim.components.get(tabIndex);
					comp.name = newName;
					componentTabs.setTitleAt(tabIndex, String.format("%-8s", newName));
				}
			}
		});


		JPanel animationsTab = new JPanel(new MigLayout("fill, wrap, ins 16"));

		return animationsTab;
	}
	 */

	@Override
	protected void saveChanges()
	{

	}

	@Override
	public void clickLMB()
	{
		/*
		if(editorMode == EditorMode.Animation)
		{
			if(sprite == null || currentAnim == null)
				return;

			int selected = (trace.pixelData.stencilValue - 1);
			currentAnim.setComponentSelected(selected);
			if(selected >= 0)
				setComponent(selected, false);
		}
		 */
	}
}
