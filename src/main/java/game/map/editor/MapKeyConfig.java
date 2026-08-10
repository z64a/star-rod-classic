package game.map.editor;

import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

import common.CameraInput;
import common.KeyboardInputConfig;

/**
 * Map Editor key bindings and input policy.
 */
public final class MapKeyConfig extends KeyboardInputConfig
{
	public MapKeyConfig()
	{
		addDefault(MapInput.UNDO, KeyEvent.VK_Z, InputEvent.CTRL_DOWN_MASK);
		addDefault(MapInput.REDO, KeyEvent.VK_Y, InputEvent.CTRL_DOWN_MASK);
		addDefault(MapInput.SAVE, KeyEvent.VK_S, InputEvent.CTRL_DOWN_MASK);
		addDefault(MapInput.SWITCH, KeyEvent.VK_ESCAPE, InputEvent.SHIFT_DOWN_MASK);
		addDefault(MapInput.QUIT, KeyEvent.VK_ESCAPE);

		addDefault(MapInput.SELECT_OBJECTS, KeyEvent.VK_1);
		addDefault(MapInput.SELECT_TRIANGLES, KeyEvent.VK_2);
		addDefault(MapInput.SELECT_VERTICIES, KeyEvent.VK_3);
		addDefault(MapInput.SELECT_POINTS, KeyEvent.VK_4);
		addDefault(MapInput.OPEN_MODEL_TAB, KeyEvent.VK_1, InputEvent.SHIFT_DOWN_MASK);
		addDefault(MapInput.OPEN_COLLIDER_TAB, KeyEvent.VK_2, InputEvent.SHIFT_DOWN_MASK);
		addDefault(MapInput.OPEN_ZONE_TAB, KeyEvent.VK_3, InputEvent.SHIFT_DOWN_MASK);
		addDefault(MapInput.OPEN_MARKER_TAB, KeyEvent.VK_4, InputEvent.SHIFT_DOWN_MASK);
		addDefault(MapInput.TOGGLE_UV_EDIT, KeyEvent.VK_U);
		addDefault(MapInput.TOGGLE_INFO_PANEL, KeyEvent.VK_I);

		addAlias(MapInput.MOVE_FORWARD, CameraInput.PAN_UP);
		addAlias(MapInput.MOVE_BACKWARD, CameraInput.PAN_DOWN);
		addAlias(MapInput.MOVE_LEFT, CameraInput.PAN_LEFT);
		addAlias(MapInput.MOVE_RIGHT, CameraInput.PAN_RIGHT);

		addDefault(MapInput.MOVE_FORWARD, KeyEvent.VK_W);
		addDefault(MapInput.MOVE_BACKWARD, KeyEvent.VK_S);
		addDefault(MapInput.MOVE_LEFT, KeyEvent.VK_A);
		addDefault(MapInput.MOVE_RIGHT, KeyEvent.VK_D);
		addDefault(MapInput.CENTER_VIEW, KeyEvent.VK_C);

		addDefault(MapInput.SELECT_ALL, KeyEvent.VK_A, InputEvent.CTRL_DOWN_MASK);
		addDefault(MapInput.FIND_OBJECT, KeyEvent.VK_F, InputEvent.CTRL_DOWN_MASK);
		addDefault(MapInput.COPY_OBJECTS, KeyEvent.VK_C, InputEvent.CTRL_DOWN_MASK);
		addDefault(MapInput.PASTE_OBJECTS, KeyEvent.VK_V, InputEvent.CTRL_DOWN_MASK);
		addDefault(MapInput.DELETE_SELECTED, KeyEvent.VK_DELETE);
		addDefault(MapInput.HIDE_SELECTED, KeyEvent.VK_H);

		addDefault(MapInput.OPEN_TRANSFORM_DIALOG, KeyEvent.VK_T, InputEvent.CTRL_DOWN_MASK);
		addDefault(MapInput.ROUND_VERTICIES, KeyEvent.VK_N, InputEvent.CTRL_DOWN_MASK);
		addDefault(MapInput.FLIP_SELECTED_X, KeyEvent.VK_X, InputEvent.SHIFT_DOWN_MASK);
		addDefault(MapInput.FLIP_SELECTED_Y, KeyEvent.VK_Y, InputEvent.SHIFT_DOWN_MASK);
		addDefault(MapInput.FLIP_SELECTED_Z, KeyEvent.VK_Z, InputEvent.SHIFT_DOWN_MASK);
		addDefault(MapInput.FLIP_NORMALS, KeyEvent.VK_N, InputEvent.SHIFT_DOWN_MASK);
		addDefault(MapInput.TOGGLE_TWO_SIDED, KeyEvent.VK_O);
		addDefault(MapInput.NUDGE_UP, KeyEvent.VK_UP);
		addDefault(MapInput.NUDGE_DOWN, KeyEvent.VK_DOWN);
		addDefault(MapInput.NUDGE_LEFT, KeyEvent.VK_LEFT);
		addDefault(MapInput.NUDGE_RIGHT, KeyEvent.VK_RIGHT);
		addDefault(MapInput.NUDGE_OUT, KeyEvent.VK_PAGE_UP);
		addDefault(MapInput.NUDGE_IN, KeyEvent.VK_PAGE_DOWN);

		addDefault(MapInput.DRAW_CONVEX, KeyEvent.VK_COMMA);
		addDefault(MapInput.DRAW_CONCAVE, KeyEvent.VK_PERIOD);
		addDefault(MapInput.DRAW_WALLS, KeyEvent.VK_SLASH);
		addDefault(MapInput.CUT_GEOMETRY, KeyEvent.VK_BACK_QUOTE);

		addDefault(MapInput.PLAY_IN_EDITOR_TOGGLE, KeyEvent.VK_P);
		addDefault(MapInput.PLAY_IN_EDITOR_JUMP, KeyEvent.VK_J);
		addDefault(MapInput.PLAY_IN_EDITOR_SPIN, KeyEvent.VK_L);
		addDefault(MapInput.PLAY_IN_EDITOR_HOVER, KeyEvent.VK_K);
		addDefault(MapInput.PLACE_CURSOR_AT_MOUSE, KeyEvent.VK_UNDEFINED);

		addDefault(MapInput.TOGGLE_GRID, KeyEvent.VK_G);
		addDefault(MapInput.TOGGLE_GRID_TYPE, KeyEvent.VK_G, InputEvent.SHIFT_DOWN_MASK);
		addDefault(MapInput.INCREASE_GRID_POWER, KeyEvent.VK_EQUALS);
		addDefault(MapInput.DECREASE_GRID_POWER, KeyEvent.VK_MINUS);
		addDefault(MapInput.VERTEX_SNAP, KeyEvent.VK_6);
		addDefault(MapInput.VERTEX_SNAP_LIMIT, KeyEvent.VK_6, InputEvent.SHIFT_DOWN_MASK);
		addDefault(MapInput.SNAP_TRANSLATION, KeyEvent.VK_7);
		addDefault(MapInput.SNAP_ROTATION, KeyEvent.VK_8);
		addDefault(MapInput.SNAP_SCALE, KeyEvent.VK_9);
		addDefault(MapInput.SNAP_SCALE_GRID, KeyEvent.VK_0);

		addDefault(MapInput.SHOW_MODELS, KeyEvent.VK_F1);
		addDefault(MapInput.SHOW_COLLIDERS, KeyEvent.VK_F2);
		addDefault(MapInput.SHOW_ZONES, KeyEvent.VK_F3);
		addDefault(MapInput.SHOW_MARKERS, KeyEvent.VK_F4);
		addDefault(MapInput.SHOW_ONLY_MODELS, KeyEvent.VK_F1, InputEvent.SHIFT_DOWN_MASK);
		addDefault(MapInput.SHOW_ONLY_COLLIDERS, KeyEvent.VK_F2, InputEvent.SHIFT_DOWN_MASK);
		addDefault(MapInput.SHOW_ONLY_ZONES, KeyEvent.VK_F3, InputEvent.SHIFT_DOWN_MASK);
		addDefault(MapInput.SHOW_ONLY_MARKERS, KeyEvent.VK_F4, InputEvent.SHIFT_DOWN_MASK);
		addDefault(MapInput.SHOW_NORMALS, KeyEvent.VK_N);
		addDefault(MapInput.SHOW_GIZMO, KeyEvent.VK_Y);
		addDefault(MapInput.SHOW_AABB, KeyEvent.VK_B);
		addDefault(MapInput.TOGGLE_QUADVIEW, KeyEvent.VK_F);
		addDefault(MapInput.TOGGLE_WIREFRAME, KeyEvent.VK_T);
		addDefault(MapInput.TOGGLE_EDGES, KeyEvent.VK_E);
		addDefault(MapInput.USE_GEOMETRY_FLAGS, KeyEvent.VK_M);

		makeGlobal(MapInput.UNDO);
		makeGlobal(MapInput.REDO);
		finishDefaults();
	}

}
