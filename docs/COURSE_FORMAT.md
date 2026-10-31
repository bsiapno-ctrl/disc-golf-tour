# Course file format

A course is one JSON file. Put it in the phone's `Android/data/com.discgolf.game/files/courses/` folder (the in-game
**Course** menu shows the exact path under "Add your own courses…"), or in `app/src/main/assets/courses/` to bundle it
with the app. Files that cannot be read are listed in that same dialog with the reason.

All positions are **metres on a flat local map**: `x` grows east, `z` grows north. Heights are metres above any zero.
One file holds the land once and any number of **layouts** (different tees, baskets and par) that play on it.

```json
{
  "id": "maple-park",                // optional; made from the name if missing. Must be unique.
  "name": "Maple Park",
  "location": "Springfield",         // optional

  "terrain": {                       // optional; flat ground if missing
    "x0": -300, "z0": -300,          // position of the first height sample
    "cell": 2,                       // metres between samples
    "nx": 300, "nz": 300,            // samples in x and z
    "heights": [ ... ]               // nx * nz numbers, row by row: index = iz * nx + ix
  },

  "holes": [
    {
      "number": 1,                   // how layouts refer to this hole
      "name": "Opening Drive",
      "fairwayWidth": 14,            // metres of short grass either side of the line (default 14)
      "tees":    { "Blue": [0, 0], "Red": [0, 40] },
      "baskets": { "A": [0, 120], "B": [18, 128] },
      "via":     [[10, 70]],         // optional bends in the line from tee to basket
      "trees":   [[8, 40, 11, 2.4]], // optional [x, z, height, canopy radius]; if missing, trees are scattered along the hole
      "ponds":   [[-12, 60, 9, 14]], // optional [centre x, centre z, radius x, radius z]
      "treeDensity": 0.5,            // optional, for scattered trees
      "guardianChance": 0.2
    }
  ],

  "layouts": [                       // optional; without it every hole is played from its first tee to its first basket
    { "name": "Championship", "description": "Long tees, 18 holes",
      "holes": [ { "hole": 1, "tee": "Blue", "basket": "A", "par": 4 }, ... ] },
    { "name": "Recreational",
      "holes": [ { "hole": 1, "tee": "Red", "basket": "A" }, ... ] }
  ]
}
```

Notes

* `par` is optional; if missing it comes from the hole length (under 100 m is 3, under 165 m is 4, otherwise 5).
* A layout can use any subset of the holes in any order, and the same hole can appear with a different tee or basket
  in another layout.
* The terrain file is only sampled where it is needed, so a large grid is fine. Outside the grid the nearest edge height
  is used.
* Heights matter: discs gain or lose distance on slopes and roll downhill, so a realistic height grid makes a real course
  play like the real thing.
* Water: a pond's surface is the terrain height at its centre. Make sure the land around the pond is higher than that.
