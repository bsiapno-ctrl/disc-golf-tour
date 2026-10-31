# Desktop preview

Renders the game's real shaders, glTF models and skinned golfer in headless Chromium (needs `npm i playwright`, and `kotlinc` for the data export).

1. `python3 extract.py` pulls the GLSL out of the Kotlin sources into `shaders.json`.
2. Compile `core/*.kt` + `Export.kt` and run `ExportKt <hole>` to write `hole<N>.json` (terrain, trees, decor).
3. `node pv.js <hole> tee 1280 600 out.png Casual` (env `CAM=back,right,height,ahead,lookHeight`, `FOV`).
