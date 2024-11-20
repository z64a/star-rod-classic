package game.battle.editor;

import static game.battle.editor.BattleKey.*;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import org.w3c.dom.Element;

import game.map.editor.render.RenderingOptions;
import renderer.buffers.LineRenderQueue;
import renderer.buffers.PointRenderQueue;
import renderer.shaders.RenderState;
import util.xml.XmlWrapper.XmlReader;
import util.xml.XmlWrapper.XmlSerializable;
import util.xml.XmlWrapper.XmlTag;
import util.xml.XmlWrapper.XmlWriter;

public class Unit implements XmlSerializable
{
	private static final int[][] INDEX_POSITIONS = {
			{ 5, 0, -20 }, { 45, 0, -5 }, { 85, 0, 10 }, { 125, 0, 25 },
			{ 10, 50, -20 }, { 50, 45, -5 }, { 90, 50, 10 }, { 130, 55, 25 },
			{ 15, 85, -20 }, { 55, 80, -5 }, { 95, 85, 10 }, { 135, 90, 25 },
			{ 15, 125, -20 }, { 55, 120, -5 }, { 95, 125, 10 }, { 135, 130, 25 },
			{ 105, 0, 0 } };

	public String _actorName;
	public Actor actor;

	public transient boolean useHomeVector;
	public int homeIndex;
	public int[] homeVector = new int[3];

	public int priority;
	public int[] vars = new int[4];

	// editor-only fields

	public transient float[] homePos = new float[3];
	public transient float[] pos = new float[3];
	public transient float[] visualOffset = new float[3];

	public transient List<UnitPart> parts = new ArrayList<>();

	public Unit(BattleSection section, ByteBuffer fileBuffer, int offset)
	{
		fileBuffer.position(offset);

		int ptrActor = fileBuffer.getInt();
		int home = fileBuffer.getInt();
		priority = fileBuffer.getInt();
		vars[0] = fileBuffer.getInt();
		vars[1] = fileBuffer.getInt();
		vars[2] = fileBuffer.getInt();
		vars[3] = fileBuffer.getInt();

		_actorName = section.getPointerName("Actor", ptrActor);

		if (home < 0) {
			useHomeVector = true;
			fileBuffer.position(section.toOffset(home));
			homeVector[0] = fileBuffer.getInt();
			homeVector[1] = fileBuffer.getInt();
			homeVector[2] = fileBuffer.getInt();
		}
		else {
			useHomeVector = false;
			homeIndex = home;
		}

		setHome();
		setPosToHome();
	}

	public Unit(XmlReader xmr, Element elem)
	{
		fromXML(xmr, elem);
	}

	@Override
	public void fromXML(XmlReader xmr, Element elem)
	{
		_actorName = xmr.getAttribute(elem, ATTR_UNIT_ACTOR);
		priority = xmr.readHex(elem, ATTR_UNIT_PRIORITY);
		if (xmr.hasAttribute(elem, ATTR_UNIT_HOME_VECTOR)) {
			homeVector = xmr.readIntArray(elem, ATTR_UNIT_HOME_VECTOR, homeVector.length);
			useHomeVector = true;
		}
		else {
			homeIndex = xmr.readHex(elem, ATTR_UNIT_HOME_INDEX);
			useHomeVector = false;
		}
		vars = xmr.readHexArray(elem, ATTR_UNIT_VARS, vars.length);

		setHome();
		setPosToHome();
	}

	@Override
	public void toXML(XmlWriter xmw)
	{
		XmlTag unitTag = xmw.createTag(TAG_UNIT, true);
		if (actor != null)
			xmw.addAttribute(unitTag, ATTR_UNIT_ACTOR, actor.name);
		else
			xmw.addAttribute(unitTag, ATTR_UNIT_ACTOR, _actorName);
		xmw.addHex(unitTag, ATTR_UNIT_PRIORITY, priority);
		if (useHomeVector)
			xmw.addIntArray(unitTag, ATTR_UNIT_HOME_VECTOR, homeVector);
		else
			xmw.addHex(unitTag, ATTR_UNIT_HOME_INDEX, homeIndex);
		xmw.addHexArray(unitTag, ATTR_UNIT_VARS, vars);
		xmw.printTag(unitTag);
	}

	public void setActor(Actor actor)
	{
		this.actor = actor;
		for (ActorPart part : actor.parts)
			parts.add(new UnitPart(part));
	}

	public void setHome()
	{
		homePos[0] = useHomeVector ? homeVector[0] : INDEX_POSITIONS[homeIndex][0];
		homePos[1] = useHomeVector ? homeVector[1] : INDEX_POSITIONS[homeIndex][1];
		homePos[2] = useHomeVector ? homeVector[2] : INDEX_POSITIONS[homeIndex][2];
	}

	public void setPosToHome()
	{
		pos[0] = homePos[0];
		pos[1] = homePos[1];
		pos[2] = homePos[2];
	}

	public void setStatus(int statusKey)
	{
		for (UnitPart part : parts)
			part.setStatus(statusKey);
	}

	public void tick(double deltaTime)
	{
		for (UnitPart part : parts)
			part.tick(deltaTime);
	}

	public void render(RenderingOptions opts)
	{
		float[] actorPos = new float[3];
		actorPos[0] = pos[0] + visualOffset[0];
		actorPos[1] = pos[1] + (((actor.flags.get() & 0x800) == 0) ? visualOffset[1] : -visualOffset[1]);
		actorPos[2] = pos[2] + visualOffset[2];

		for (UnitPart part : parts) {
			float[] partPos = getPartPos(this, part);
			part.render(partPos, opts);

			/*
			RenderState.setModelMatrix(null);
			PointRenderQueue.addPoint(2.0f).setPosition(0,0,0);
			PointRenderQueue.addPoint(2.0f).setPosition(10,0,0);
			PointRenderQueue.addPoint(2.0f).setPosition(0,10,0);
			PointRenderQueue.addPoint(2.0f).setPosition(0,0,10);
			PointRenderQueue.addPoint(2.0f).setPosition(-10,0,0);
			PointRenderQueue.addPoint(2.0f).setPosition(0,-10,0);
			PointRenderQueue.addPoint(2.0f).setPosition(0,0,-10);
			System.out.println("!");
			//					partPos[0] + part.actorPart.targetOffset.get(0),
			//					partPos[1] + part.actorPart.targetOffset.get(1),
			//					partPos[2]);
			* */

		}

		RenderState.setModelMatrix(null);
		int sizeX = actor.size.get(0);
		int sizeY = actor.size.get(1);
		LineRenderQueue.addLineLoop(
			LineRenderQueue.addVertex().setPosition(actorPos[0] - sizeX / 2, actorPos[1], actorPos[2]).getIndex(),
			LineRenderQueue.addVertex().setPosition(actorPos[0] + sizeX / 2, actorPos[1], actorPos[2]).getIndex(),
			LineRenderQueue.addVertex().setPosition(actorPos[0] + sizeX / 2, actorPos[1] + sizeY, actorPos[2]).getIndex(),
			LineRenderQueue.addVertex().setPosition(actorPos[0] - sizeX / 2, actorPos[1] + sizeY, actorPos[2]).getIndex());
		LineRenderQueue.render(true);
		PointRenderQueue.render(true);
	}

	public float[] getPos()
	{
		float[] actorPos = new float[3];

		actorPos[0] = pos[0] + visualOffset[0];
		actorPos[1] = pos[1] + (((actor.flags.get() & 0x800) == 0) ? visualOffset[1] : -visualOffset[1]);
		actorPos[2] = pos[2] + visualOffset[2];

		return actorPos;
	}

	public static float[] getPartPos(Unit unit, UnitPart part)
	{
		float[] actorPos = unit.getPos();
		float[] partPos = new float[3];

		if ((part.actorPart.flags.get() & 0x100000) == 0) {
			partPos[0] = actorPos[0] + part.visualOffset[0];
			partPos[1] = actorPos[1] + (((unit.actor.flags.get() & 0x800) == 0) ? part.visualOffset[1] : -part.visualOffset[1]);
			partPos[2] = actorPos[2] + part.visualOffset[2];
		}
		else {
			partPos[0] = part.absolutePos[0] + part.visualOffset[0];
			partPos[1] = part.absolutePos[1] + part.visualOffset[1];
			partPos[2] = part.absolutePos[2] + part.visualOffset[2];
		}

		return partPos;
	}
}
