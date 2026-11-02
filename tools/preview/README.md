# Desktop preview

Renders the game's real shaders, glTF models and skinned golfer in headless Chromium (needs `npm i playwright`, and `kotlinc` for the data export).

1. `python3 extract.py` pulls the GLSL out of the Kotlin sources into `shaders.json`.
2. Compile `core/*.kt` + `Export.kt` and run `ExportKt <hole>` to write `hole<N>.json` (terrain, trees, decor).
3. `node pv.js <hole> tee 1280 600 out.png Casual` (env `CAM=back,right,height,ahead,lookHeight`, `FOV`, `CLIP=Windup_BH,0` clip and 0..1 time for the pose).

Shade map: the preview mirrors TerrainRender's baked tree-shadow texture (trunk size assumed). Trees: `ExportTrees.kt` + `gfx/PuffyTreeGen.kt` (pure Kotlin) write `trees.json` (`ExportTreesKt > trees.json`), which `pv.js` reads from the working directory, so the preview draws the exact tree meshes the game builds. Env `ASSETS` / `GFX_SRC` override the asset and Kotlin source paths.

gfx8: `Export.kt` now writes the real `Scenery.decorFor` items (rocks, shrubs, signs, stakes); `pv.js` ports the basket, flag, tee pad props and shrubs.
