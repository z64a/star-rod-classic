# Changelog

All notable changes to this project will be documented in this file.

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
