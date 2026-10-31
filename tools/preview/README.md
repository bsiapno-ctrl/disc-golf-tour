# Desktop preview

Renders the game's real shaders, glTF models and skinned golfer in headless Chromium (needs `npm i playwright`, and `kotlinc` for the data export).

1. `python3 extract.py` pulls the GLSL out of the Kotlin sources into `shaders.json`.
2. Compile `core/*.kt` + `Export.kt` and run `ExportKt <hole>` to write `hole<N>.json` (terrain, trees, decor).
3. `node pv.js <hole> tee 1280 600 out.png Casual` (env `CAM=back,right,height,ahead,lookHeight`, `FOV`).

Trees: `ExportTrees.kt` + `gfx/PuffyTreeGen.kt` (pure Kotlin) write `trees.json` (`ExportTreesKt > trees.json`), which `pv.js` reads from the working directory, so the preview draws the exact tree meshes the game builds. Env `ASSETS` / `GFX_SRC` override the asset and Kotlin source paths.
