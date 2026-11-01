# Overnight improvement loop - state and playbook

Goal: make Disc Golf Tour look and play like **Hot Shots Golf (Everybody's Golf), but disc golf**:
clean cel-shaded characters with thin smooth outlines, lush bright saturated world with detailed round trees,
glossy chunky UI, a physically believable disc flight, a pro-style backhand/forehand animation.

## Loop (one iteration)
1. Read this file and `git log --oneline -15` on branch `round4` (or the newest `roundN`).
2. Install the newest build in BlueStacks (see "Test rig"), play hole 1-2, take screenshots, write a critique
   against the checklist below.
3. Spawn sub-agents in git worktrees (`git worktree add -b agent/<name> /home/claude/wt/<name> <integration branch>`)
   for the worst 3-5 items; each commits to its own branch.
4. Merge the branches into the integration branch, `kotlinc` check pure-Kotlin (`core/*.kt`), push to `roundN`
   (CI builds + runs unit tests + publishes the APK to the release `latest-roundN`).
5. Update the status table below, commit it, repeat. Stop only if CI is red twice in a row (then fix first).

## Test rig
- CI: GitHub Actions on every push; APK at
  `https://github.com/bsiapno-ctrl/disc-golf-tour/releases/download/latest-<branch>/DiscGolfTour-debug.apk`
- Builds are signed with the committed `app/debug.keystore`, so updates install in place.
- Desktop preview (3D scene only): `tools/preview` (node + playwright + swiftshader).
- BlueStacks on the user's PC: copy the APK to `C:\Users\brian\Documents\`, open File Explorer, double-click it
  (BlueStacks installs it), launch from the BlueStacks home screen. Screenshots are slow (~0.5 s) so the swing
  cannot be captured in-game; judge animation from Blender frame sheets (`scratchpad/agents/anim*/strip.py`).
- Gradle is NOT runnable in the sandbox; only CI compiles Android code. Read Android edits twice.

## Checklist (score each 0-5 every iteration)
- Outline: thin, smooth, no fuzz (needs MSAA); face readable; disc clearly held (not intersecting hand).
- Trees: detailed round foliage with leaf texture, not smooth blobs; species variety; LOD.
- Ground/sky/water: Hot Shots palette, soft stripes, clouds, mountains, sparkling water, props.
- HUD/menus: glossy chunky panels, nothing covers the golfer, text never clipped, portrait shows full golfer.
- Controls: 8-way aim stick (heading + aim distance/pitch), separate ANGLE/TILT release controls.
- Throw feel: backhand = left-handed-baseball path (reach back to the left, chest turned, pull across, snap, finish right);
  forehand flat sidearm; disc flight S-curves per disc class.
- Putting: circle 1 ~85%, circle 2 ~45% for a mid-skill golfer, clear PUTT / CHAINS labels.

## Status
| Date | Round | Notes |
|------|-------|-------|
| 2026-10-31 | 4 | New HUD, aim stick, puffy trees, menus restyled, flight tuned, fixed debug key |
| 2026-10-31 | 5 | (in progress) 8-way stick, MSAA + outline + disc grip, textured trees/world, Hot Shots HUD |
