# Animation study (round 16)

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
