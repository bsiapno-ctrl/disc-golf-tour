# Changelog

## [Unreleased]

### Added
- `tools/course-builder.html`: a page you open on a PC to build real courses on satellite imagery (tees, baskets, bends, water, layouts, optional elevation) and export a course file.

### Added
- Terrain: real elevation. Holes climb, drop and roll. Discs gain or lose distance on slopes and roll downhill.
- Ground rendering: grass and rough with noise, mowing stripes, a ringed putting green, banks and mud, shaped ponds.
- Sky with sun glow and clouds, haze that matches the horizon, varied trees that sit on the slope, shadows that follow the ground.
- Layouts: pick a course and layout (built in: Maple Ridge, Championship or Recreational).
- Course files (JSON) with terrain grid, several tees and baskets per hole and several layouts per course. See docs/COURSE_FORMAT.md.
- Courses folder on the phone is scanned for course files.
- Crash screen: if the game crashes, the next launch shows the error so it can be reported without a computer.

### Fixed
- Crash on launch on newer Android versions (immersive mode was applied before the window existed).

### Changed
- CI builds every branch.

## [0.1.0] - 2026-10-29
- Disc Golf Tour: character creator, RPG ratings, hold/release power meter with accuracy tap, 9-hole 3D course, gamepad/touch/keyboard.
