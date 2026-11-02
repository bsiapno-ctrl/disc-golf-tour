# Animation study (rounds 16 + 17)

Sources: Jomez DiscGolf, 2026 Discraft Supreme Flight Open, MPO R1F9 (youtube YkaPk2PR9cM, 2259 s) and MPO R1B9 (youtu.be/coQSmIg0gxY, 2534 s).
Method: pause the player, seek, screenshot (frame sheets at 0.3 s steps, roughly 3 fps; sub-frame detail is not available).
Only general technique is recorded; nothing here copies one player's signature style.

## Coverage actually achieved (short of the brief)
Browser capture was flaky (zoom screenshots timed out repeatedly), so the study is smaller than the >=8 / >=3 / >=6 target:
- Backhand drives, full sequences: 3 (video 1: tee, ~126.6-129.3 s player A; ~159.6-161.7 s player B; ~98.4-100.0 s player C), plus a partial run-up/pre-throw of a 4th (~178-184 s).
- Forehands: 0 found. Forehand tables use general technique knowledge, not footage.
- Putts: 1 standstill sequence (video 2, ~542-547 s) and one follow-through (video 1, ~553-555 s). Jump putts: 0 found; jump tables keep the previous authored form.
Everything marked (K) below is prior knowledge, not seen in these frames.

## Observations (backhand, behind / side-behind camera on the tee pad)
- Run-up is upright: 3-4 steps, knees only slightly bent, disc carried in front at belly/chest height, shoulders turned ~5-20 deg by mid run-up. The crouch happens only in the last step (pelvis drops ~0.2-0.3 m).
- Reach-back (127.5 s, 160.5-160.8 s): chest closed roughly 90-100 deg from the target line, hips less (~45-55 deg), so the shoulder line leads the closed turn; throwing arm fully extended back, disc between hip and shoulder height, off arm tucked across the chest, head still looking at the target. Feet wide, back foot turned out, weight on the back leg.
- Pull-through (~0.15-0.25 s): hips open first; at release the hips are roughly 25-35 deg more open than the shoulders (hip-shoulder separation), trunk leans away from the throw (side bend ~15-20 deg), elbow comes close to the ribs then the arm extends flat at roughly chest/hip height; wrist snaps last.
- Finish (128.6-129.3 s, 161.4-161.7 s, 100.0 s): chest fully open (60+ deg past square), arm sweeps across and up then falls, back leg trails/steps through, torso leans forward onto the front leg.
## Putt (standstill, 542-547 s, side view)
Feet about shoulder width, slightly staggered, knees bent ~15-20 deg, torso pitched ~20-25 deg; disc starts at ribs/belly with the off hand, tiny back-stroke, arm pushes straight out and finishes pointing slightly up (~10 deg above horizontal) with a wrist flick; rises toward the front foot at the end. Jump putt (K): crouch, spring off both feet, release at apex with legs kicked back, soft landing.

## Keyframe tables (yaw positive = closed, degrees; timeline frames, throw clip = frames 29-50, release 36.8)
| phase | frame | shoulder | hip | side bend | notes |
|---|---|---|---|---|---|
| address | 0 | 0 | 0 | 0 | upright (pelvis -0.07 m) |
| mid run-up | 17 | 48 | 22 | 0 | arms carry disc, pelvis -0.15 |
| reach-back (= throw f0) | 29-30.5 | 96-98 | 50-52 | 6 | arm az 108-110, rad 0.40, pelvis -0.28 |
| pull start | 32.5 | 80 | 40 | 10 | elbow in, rad 0.34 |
| mid pull | 34.5 | 38 | 12 | 14 | rad 0.28 (elbow close) |
| release | 36.8 | -4 | -32 | 18 | hip leads by ~28; hand rel -16, rad 0.40 |
| follow-through | 42 | -62 | -48 | 12 | arm az -30, up 12 |
| end | 50 | -56 | -44 | 4 | trailing leg steps, lean 14 |
Measured on the rig (tools/measure_swing.py): BH chest peak closed 98.5 deg then opens by 158.8 deg to -60 deg; FH peak 63 then opens by 118 deg. At release BH hip yaw is -19.6/-33.8 (frames 7/8) vs shoulder 12/-5.
Putt_Stand: stance staggered 0.09 m, bend lean 22-28 deg, follow-through rel +8..+10 deg, wrist flick 14 deg. Putt_Jump: unchanged form (hop, legs kicked back).

## Rebuild summary
tools/motions.py tables for BH rewritten (trunk rotation, side bend, arm radius path, upright run-up pelvis, finish), putt stance/follow-through reworked; clip names, frame counts (22/30/20/26) and release fractions (0.26/0.70, 0.55, 0.58) unchanged. tools/strip.py renders contact sheets (docs/anim_strips). All 6 characters re-exported; check_legs ALL OK on all.

## Still off
- Forehand has no footage behind it and shares the BH arm/lean tables (strips look nearly identical); needs its own study.
- Only 3 backhand throws were studied; no jump putt footage.
- measure_swing reports a 2 cm IK miss in run-up frames 6-8 (left arm), above its 2 cm gate.
- Pelvis does not travel in the clips; run-up stepping is feet-only. The finish still has the knees bent more than a pro's.
- Strips use Blender workbench renders, not the game renderer (the node/kotlinc preview was unavailable).


---
# Round 17 additions

Method (r17): a canvas contact-sheet built in the page (seek -> `drawImage(video)` -> labelled tile, 12-24 tiles per screenshot, step 0.06-0.2 s, one light zoom screenshot per sheet), which avoided the screenshot timeouts. Players differ (Robinson, McBeth, Barela, Heimburg, two shirts/builds each); only technique is recorded.

## Samples (video 1 = R1F9 YkaPk2PR9cM, video 2 = R1B9 coQSmIg0gxY, video 3 = Y2ti_v1fWoc CG reference)
Backhand drives (13): v1 Robinson H1 97.3-101.9 (side-rear, run-up + release 98.9 + finish); Robinson H2 372.4-375.6 (release 374.9); McBeth H2 389.7-390.8 (release 390.1); Barela H2 407.9-410.2 (release ~409.6); Heimburg H2 425.9-428.2 (release ~427.2; arm held HIGH until 428.2); McBeth H3 638.2-639.3 (release 638.7); Robinson H3 656.6-657.7 (release 656.85); Barela H3 675.5-676.6 (release 675.8); Heimburg H3 696.7-697.8 (release ~696.5, arm drops by 697.8); Barela H4 879.4-880.5 (finish only); Heimburg H4 896.6-897.7 (finish only); v2 Barela H11 308.6-309.9 (run-up/reach-back, two-hand carry at chest); v2 Robinson H11 356.6-357.9 (run-up).
Forehand / sidearm (2 real, short of 6): v1 Barela 212.4-213.06 (approach from behind trees); v1 Heimburg 222.7-223.36 (approach, hand at hip pocket). Several other candidate sidearm shots were located but turned out to be pick-ups or pre-shot routines.
Putts (3, short of 8): v1 Heimburg 254-257.1 (belly pitch, release 256.55); v1 Robinson-style shoulder-high push v2 1327.4-1329.4 (release ~1328.9); v1 262-267 (marks/pick-ups, no putt). The earlier r16 sequence (542-547 s v2) is also used. **Jump putts: none found** (searched ends of holes 1, 5, 18) -> jump tables are built from technique knowledge (K), flagged.
CG reference (Y2ti_v1fWoc, 3.4 s, 1280x720, stepped 0.1 s): 0.0-0.2 walk, 0.3-0.5 X-step (legs cross), 0.6-0.7 deepest reach-back, 0.8-1.0 plant wide with arm across the chest (pull), 1.1-1.3 release (arm flat, hips already open), 1.4-1.5 lunge/rotation, 1.6-3.4 **frozen pose** (running stride, arms like wings) -- this hold is exactly what the r16 follow-through copied, so only its phase timing (reach-back -> plant ~0.1-0.2 s -> release ~0.4 s later, slower than real players) is used, never its end hold.

## What real players do after the release (the point of r17)
| time after release | seen (BH) |
|---|---|
| 0-0.1 s | arm flat at hip/chest height pointing at the target, chest square->open, front foot planted |
| 0.1-0.3 s | arm sweeps across and UP (Heimburg H2: hand above shoulder), chest keeps opening to ~-60 deg, back leg kicks up/back (McBeth, Robinson H3: shin horizontal, torso pitched forward 30-45 deg, off arm out as counterweight) |
| 0.3-0.5 s | kick peaks, then leg swings through / lowers; arm starts dropping (Barela H4 880.0-880.5: shoulder height -> forward-down), trunk rises |
| 0.5-0.9 s | arms hang near the sides (Heimburg H3 697.8, H4 897.2-897.7), feet shoulder width, weight settled, upright |
The clip covers only the first ~0.47 s after release, so the last 8 frames must be *mid-recovery* (arm descending, leg landing, torso rising) and keep non-zero velocity at the last frame.

## Keyframe tables r17 (timeline g; release 36.8; clip = g29-50, i.e. clip frame = g-29)
BH finish: shoulder -4 (rel) -> -42 (g39) -> -62 (g42) -> -46 (g50); hips -32 -> -52 (g42) -> -42; trunk lean 22 -> 26 (g39) -> 11 (g50); right arm az 22 -> -10 -> -34 (g42), elevation -16 -> +20 (g42-44) -> -26 (g50), radius 0.40 -> 0.30; off arm az 70-80 until g42, then 18 at g50, elbow elevation 8 -> -42; back leg: heel 12 (g37) -> 70 (g44) -> 12 (g50), lift 0.20 m at g44.5 and lands at g50 as trailing foot. Keys placed past g50 (never played) keep the velocity non-zero at the last frame.
FH (own table, sidearm): chest coil 54 deg closed (BH 96), hips 26 (BH 50); lean 6-12 (upright/back), side bend 16 at release; hand rides in the hip pocket (elevation -50, radius 0.27, azimuth 76) with the elbow behind, pull leads with the elbow (radius stays 0.26 until g35), forearm whips to radius 0.40 at release (g36.8) with 30 deg wrist flick, then the forearm swings UP to head height (elevation +38 at g44) and falls (+6 at g50); front foot wide and turned out 30 deg; back foot pivots (heel 55 deg) with a 0.10 m drag instead of the BH kick.
Putt_Stand (belly pitch, Heimburg): stance shoulder-wide, stagger 0.09 m, knees bent, lean 22-28; 9-frame charge, back-stroke to the hip, push to chest height at release (frame 10.45) 12.5: elevation +10, then recoil to -20 by frame 19, trunk rises 28 -> 15, back heel peels to 30 deg.
Putt_Stand_BH (shoulder-high push, Robinson): hand starts at chest/shoulder height (elevation -4), elbow folds on the back-stroke, push out/up (+16), recoil -12. Putt_*_FH keep the hip-pocket flick.
Putt_Jump (K): crouch -0.43 m through the charge/timing marker, spring at frame 12.5-14, release at the apex (frame 14.5) with lower legs kicked back (foot 0.26-0.30 m behind, heels 80-85 deg, 0.24-0.26 m lift), torso pitched forward 36, soft landing at frame 18.5 (pelvis -0.34), arms recover, trunk rises again to frame 25.

## Measured (tools/measure_swing.py, check_legs.py)
BH: shoulder yaw peak closed 98.5 deg (g30) -> most open -60.7 (g42), opens by 159 deg; release frames 7/8: shoulder 12.4/-5.3, hip -19.4/-33.8 (hips lead by ~28 deg). FH: peak 56.2 -> -51.2 (opens by 107 deg, BH 159); release 0.8/-14.1, hip -18.9/-29.7. Max IK miss now 3.1 mm (r16: 21.5 mm run-up miss from the off-hand hand-off; fixed by ending the support earlier, lsync 1 -> 0 over frames 5-9). check_legs ALL OK on all 6 characters (feet never cross, knee gap >= 0.45, min 0.454).

## Still off
- Footage target not reached: 13 BH (>=12 ok), only 2 real forehands (6 asked), 3 putts (8 asked), 0 jump putts. Jump putt is knowledge-based.
- Real post-release recovery takes ~0.9 s but the clip ends 0.47 s after release; the game must blend the end pose into Idle (arm already dropping).
- Left/off-arm and head tracking are approximations; strips are Blender workbench renders, not the game renderer. Release-frame disc position in the FH has not been checked in the game.
