package game.map.editor;

import common.KeyInput;

/**
 * Actions available for Map Editor key bindings.
 */
public enum MapInput implements KeyInput
{
	// @formatter:off
	UNDO,
	REDO,

	SELECT_ALL,
	FIND_OBJECT,
	COPY_OBJECTS,
	PASTE_OBJECTS,
	OPEN_TRANSFORM_DIALOG,
	TOGGLE_TWO_SIDED,
	TOGGLE_UV_EDIT,

	PLAY_IN_EDITOR_TOGGLE,
	PLAY_IN_EDITOR_JUMP,
	PLAY_IN_EDITOR_SPIN,
	PLAY_IN_EDITOR_HOVER,
	PIE_IGNORE_HIDDEN_COL,
	PIE_IGNORE_HIDDEN_ZONE,
	PIE_SHOW_ACTIVE_CAMERA,
	PIE_ENABLE_MAP_EXITS,

	MOVE_FORWARD,
	MOVE_BACKWARD,
	MOVE_LEFT,
	MOVE_RIGHT,
	PLACE_CURSOR_AT_MOUSE,

	TOGGLE_INFO_PANEL,

	SELECT_OBJECTS,
	SELECT_TRIANGLES,
	SELECT_VERTICIES,
	SELECT_POINTS,
	PAINT_RAINBOW,

	OPEN_MODEL_TAB,
	OPEN_COLLIDER_TAB,
	OPEN_ZONE_TAB,
	OPEN_MARKER_TAB,

	TOGGLE_GRID,
	TOGGLE_GRID_TYPE,
	INCREASE_GRID_POWER,
	DECREASE_GRID_POWER,

	VERTEX_SNAP,
	VERTEX_SNAP_LIMIT,
	SNAP_TRANSLATION,
	SNAP_ROTATION,
	SNAP_SCALE,
	SNAP_SCALE_GRID,
	ROUND_VERTICIES,

	MOVE_MARKER_POINTS,

	DUPLICATE_SELECTED,
	DELETE_SELECTED,
	HIDE_SELECTED,
	FLIP_SELECTED_X,
	FLIP_SELECTED_Y,
	FLIP_SELECTED_Z,
	FLIP_NORMALS,
	NORMALS_TO_CAMERA,

	DRAW_CONVEX,
	DRAW_CONCAVE,
	DRAW_WALLS,
	CUT_GEOMETRY,

	SHOW_MODELS,
	SHOW_COLLIDERS,
	SHOW_ZONES,
	SHOW_MARKERS,
	SHOW_ONLY_MODELS,
	SHOW_ONLY_COLLIDERS,
	SHOW_ONLY_ZONES,
	SHOW_ONLY_MARKERS,
	SHOW_NORMALS,
	SHOW_GIZMO,
	SHOW_ENTITY_COLLISION,
	USE_COLLIDER_COLORS,

	SHOW_AABB,
	SHOW_AXES,
	USE_GAME_ASPECT_RATIO,
	USE_MAP_CAM_PROPERTIES,
	USE_MAP_BG_COLOR,
	USE_GEOMETRY_FLAGS,
	USE_FILTERING,
	USE_TEXTURE_LOD,
	RESET_LAYOUT,
	RESET_OPTIONS,

	NUDGE_UP,
	NUDGE_DOWN,
	NUDGE_LEFT,
	NUDGE_RIGHT,
	NUDGE_OUT,
	NUDGE_IN,

	TOGGLE_WIREFRAME,
	TOGGLE_EDGES,
	TOGGLE_QUADVIEW,
	CENTER_VIEW,

	SAVE,
	SWITCH,
	QUIT,

	DEBUG_TOGGLE_LIGHT_SETS;
	// @formatter:on

	@Override
	public String getDisplayName()
	{
		switch (this) {
			case OPEN_TRANSFORM_DIALOG:
				return "Open Transform Menu";
			case TOGGLE_TWO_SIDED:
				return "Toggle Double Sided";
			case PLAY_IN_EDITOR_TOGGLE:
				return "Start / Stop Play in Editor";
			case PLAY_IN_EDITOR_JUMP:
				return "Play in Editor Jump";
			case PLAY_IN_EDITOR_SPIN:
				return "Play in Editor Spin";
			case PLAY_IN_EDITOR_HOVER:
				return "Play in Editor Hover";
			case PLACE_CURSOR_AT_MOUSE:
				return "Place 3D Cursor at Mouse";
			case SELECT_VERTICIES:
				return "Select Vertices";
			case PAINT_RAINBOW:
				return "Rainbow Painting";
			case OPEN_MODEL_TAB:
				return "Open Models Tab";
			case OPEN_COLLIDER_TAB:
				return "Open Colliders Tab";
			case OPEN_ZONE_TAB:
				return "Open Zones Tab";
			case OPEN_MARKER_TAB:
				return "Open Markers Tab";
			case INCREASE_GRID_POWER:
				return "Increase Grid Spacing";
			case DECREASE_GRID_POWER:
				return "Decrease Grid Spacing";
			case VERTEX_SNAP_LIMIT:
				return "Vertex Snap to All";
			case SNAP_SCALE_GRID:
				return "Toggle Scale Snap Mode";
			case ROUND_VERTICIES:
				return "Nudge to Grid";
			case SHOW_ONLY_MODELS:
				return "Show Only Models";
			case SHOW_ONLY_COLLIDERS:
				return "Show Only Colliders";
			case SHOW_ONLY_ZONES:
				return "Show Only Zones";
			case SHOW_ONLY_MARKERS:
				return "Show Only Markers";
			case USE_GEOMETRY_FLAGS:
				return "Toggle Geometry Flags";
			case NUDGE_OUT:
				return "Nudge Out";
			case NUDGE_IN:
				return "Nudge In";
			case TOGGLE_QUADVIEW:
				return "Toggle Four Views";
			case SWITCH:
				return "Quit to Menu";
			case DRAW_CONVEX:
				return "Draw Convex Polygon";
			case DRAW_CONCAVE:
				return "Draw Concave Polygon";
			case CUT_GEOMETRY:
				return "Cut Geometry";
			default:
				return makeDisplayName(name());
		}
	}

	@Override
	public String getCategory()
	{
		switch (this) {
			case UNDO:
			case REDO:
			case SAVE:
			case SWITCH:
			case QUIT:
				return "General";

			case SELECT_OBJECTS:
			case SELECT_TRIANGLES:
			case SELECT_VERTICIES:
			case SELECT_POINTS:
			case OPEN_MODEL_TAB:
			case OPEN_COLLIDER_TAB:
			case OPEN_ZONE_TAB:
			case OPEN_MARKER_TAB:
			case TOGGLE_UV_EDIT:
			case TOGGLE_INFO_PANEL:
				return "Editing Modes";

			case MOVE_FORWARD:
			case MOVE_BACKWARD:
			case MOVE_LEFT:
			case MOVE_RIGHT:
			case CENTER_VIEW:
				return "Navigation";

			case SELECT_ALL:
			case FIND_OBJECT:
			case COPY_OBJECTS:
			case PASTE_OBJECTS:
			case DELETE_SELECTED:
			case HIDE_SELECTED:
				return "Selection";

			case OPEN_TRANSFORM_DIALOG:
			case ROUND_VERTICIES:
			case FLIP_SELECTED_X:
			case FLIP_SELECTED_Y:
			case FLIP_SELECTED_Z:
			case FLIP_NORMALS:
			case TOGGLE_TWO_SIDED:
			case NUDGE_UP:
			case NUDGE_DOWN:
			case NUDGE_LEFT:
			case NUDGE_RIGHT:
			case NUDGE_OUT:
			case NUDGE_IN:
				return "Transform";

			case DRAW_CONVEX:
			case DRAW_CONCAVE:
			case DRAW_WALLS:
			case CUT_GEOMETRY:
				return "Drawing";

			case PLAY_IN_EDITOR_TOGGLE:
			case PLAY_IN_EDITOR_JUMP:
			case PLAY_IN_EDITOR_SPIN:
			case PLAY_IN_EDITOR_HOVER:
			case PLACE_CURSOR_AT_MOUSE:
				return "Play in Editor";

			case TOGGLE_GRID:
			case TOGGLE_GRID_TYPE:
			case INCREASE_GRID_POWER:
			case DECREASE_GRID_POWER:
			case VERTEX_SNAP:
			case VERTEX_SNAP_LIMIT:
			case SNAP_TRANSLATION:
			case SNAP_ROTATION:
			case SNAP_SCALE:
			case SNAP_SCALE_GRID:
				return "Grid and Snap";

			case SHOW_MODELS:
			case SHOW_COLLIDERS:
			case SHOW_ZONES:
			case SHOW_MARKERS:
			case SHOW_ONLY_MODELS:
			case SHOW_ONLY_COLLIDERS:
			case SHOW_ONLY_ZONES:
			case SHOW_ONLY_MARKERS:
			case SHOW_NORMALS:
			case SHOW_GIZMO:
			case SHOW_AABB:
			case TOGGLE_QUADVIEW:
			case TOGGLE_WIREFRAME:
			case TOGGLE_EDGES:
			case USE_GEOMETRY_FLAGS:
				return "Visibility";

			case PAINT_RAINBOW:
				return "Vertex Painting";

			default:
				return "Other";
		}
	}

	private static String makeDisplayName(String name)
	{
		StringBuilder result = new StringBuilder();
		String[] words = name.toLowerCase().split("_");
		for (String word : words) {
			if (result.length() > 0)
				result.append(' ');
			result.append(Character.toUpperCase(word.charAt(0)));
			result.append(word.substring(1));
		}
		return result.toString();
	}
}
