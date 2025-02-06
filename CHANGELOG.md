# Changelog

All notable changes to this project will be documented in this file.

## Next Release

### Added
- (Sprite Editor) Copy/paste/duplicate for commands/keyframes

## [0.5.8] - 2025-01-18

### Fixed
- (Map Editor) Axis lines not showing up in ortho views
- (Map Editor) Fixed crash when fusing vertices
- (Sprite Editor) Copy animation works properly

## [0.5.7] - 2024-11-25

### Fixed
- Action commands would crash in any project that didn't mod them

## [0.5.6] - 2024-11-25

### Added
- New file menu option for saving shading profiles in Map Editor
- Check for new version on startup, can be disabled with option `CheckForUpdates`

### Changed
- Increased the length limit for map and background names in the Level Editor
- Restored icons to groups and objects in MapObject tree view
- Moved Windows launcher functionality into batch file
- Added new themes, renamed and removed some old ones
- Support for user-supplied custom themes has been improved

### Fixed
- Entity subpanel UI much less likely to stretch beyond the window
- Fixed orientation for lightbulb icons in ortho viewports
- Renamed Bombomb in enum files to Bobomb

### Removed
- System UI theme

## [0.5.5] - 2024-11-20

### Changed
- Project updater no longer runs for patch version changes

### Fixed
- Map editor crashes when opening a new project
- World partner data is corrupted when a partner patch adds new data

## [0.5.4] - 2024-11-20

### Added
- Constants may use constant expressions, e.g.: `#define .MyConst ~Model:MyModel`
- SetNofify sprite command decoupled from SetParent
- Paths now show game-accurate interpolated trajectories
- Push grid markers now use a 'live editing' mode
- Additional controls added for camera markers

### Changed
- Redesigned MapObject info panels to remove clutter
- Upgraded dependencies for lwjgl, MigLayout, and UI themes
- Texture panning properies and now edited from a non-modal dialog

### Fixed
- Sprite shading profiles are no longer reset when opening the Map Editor
- Resolved inconsistent depth-ordering (flicker) for decal render modes

### Deprecated
- Support for decomp projects using this version of Star Rod will be removed in the next release
- Legacy ant build system and StarRodUser app will be removed in the next release
