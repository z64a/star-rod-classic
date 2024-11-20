package game.battle.editor;

import game.battle.editor.ActorPart.IdleAnimTableEntry;
import game.map.editor.render.RenderingOptions;
import game.map.shape.TransformMatrix;
import game.sprite.Sprite;
import game.sprite.SpriteLoader;
import game.sprite.SpriteLoader.SpriteSet;
import renderer.shaders.RenderState;

public class UnitPart
{
	private final SpriteLoader spriteLoader;
	public final ActorPart actorPart;

	private int spriteID;
	private Sprite sprite;

	private int currentAnimID;
	private boolean validAnim;

	public int activeAnimID;
	public int idleAnimID;
	public boolean isIdle = true;

	public int currentStatus;
	public int desiredStatus = 1;

	public transient boolean useAbsolutePos;
	public transient float[] absolutePos = new float[3];
	public transient float[] relativePos = new float[3];
	public transient float[] visualOffset = new float[3];

	public UnitPart(ActorPart part)
	{
		spriteLoader = new SpriteLoader();
		actorPart = part;
	}

	private void reloadSprite()
	{
		if (sprite != null)
			sprite.unloadTextures();

		sprite = spriteLoader.getSprite(SpriteSet.Npc, spriteID);

		if (sprite != null) {
			sprite.prepareForEditor();
			sprite.loadTextures();
		}
	}

	public void setIdle(boolean idle)
	{
		isIdle = idle;
	}

	public void setAnimation(int anim)
	{
		activeAnimID = anim;
	}

	public void setStatus(int statusKey)
	{
		desiredStatus = statusKey;
	}

	public void tick(double deltaTime)
	{
		if (spriteID != actorPart.spriteID.get()) {
			spriteID = actorPart.spriteID.get();
			reloadSprite();
		}

		// change animation based on desired status state
		if (currentStatus != desiredStatus) {
			idleAnimID = -1;
			for (IdleAnimTableEntry e : actorPart.animationTable) {
				if (idleAnimID == -1 && e.enumValue == 1) // normal
					idleAnimID = (e.animID & 0xFF);
				if (e.enumValue == desiredStatus)
					idleAnimID = (e.animID & 0xFF);
			}
			currentStatus = desiredStatus;
		}

		int desiredAnimID = isIdle ? idleAnimID : activeAnimID;

		if (sprite == null)
			return;

		if (currentAnimID != desiredAnimID) {
			currentAnimID = desiredAnimID;
			validAnim = currentAnimID >= 0 && currentAnimID < sprite.animations.size();

			if (validAnim)
				sprite.animations.get(currentAnimID).reset();
		}
		else //XXX ? reset+step?
		{
			if (validAnim)
				sprite.animations.get(currentAnimID).step();
		}
	}

	public void render(float[] partPos, RenderingOptions opts)
	{
		//TODO set palette override based on status
		if (!validAnim)
			return;

		TransformMatrix mtx = TransformMatrix.identity();
		mtx.scale(Sprite.WORLD_SCALE);
		mtx.translate(partPos[0], partPos[1], partPos[2]);
		RenderState.setModelMatrix(mtx);
		sprite.render(null, currentAnimID, actorPart.palID.get(), opts.useFiltering, false);
	}

	public float[] getPosition()
	{
		// TODO Auto-generated method stub
		return null;
	}
}
