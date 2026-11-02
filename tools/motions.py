"""Key tables for the throws, run-ups and putts.  See swing.py for the conventions.

One timeline per throw type: g = 0 is the address, g = T0 (29) is the end of the run-up (= first throw frame), the throw
runs g = T0 .. T0 + 21.  Release is at throw time 0.26 s of 0.70 s (frame 7.8 of 0..21) -> g = T0 + 7.8.
"""
import sys, os
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from swing import *

T0 = 29
REL = 0.26 / 0.70 * 21          # 7.8 : release frame inside the 22-frame throw
GR = T0 + REL                   # 36.8 on the shared timeline

def foot_ball(ax, ay, yaw):
    """Ball-of-foot position for an ankle at (ax, ay) with the given yaw."""
    t = rz(yaw, (0, -1, 0)); return (ax + LF * t.x, ay + LF * t.y)

# ---------------------------------------------------------------- BACKHAND
_Lb = foot_ball(0.20, -0.50, -14)    # front foot plants wide-left, well ahead
_Rb = foot_ball(-0.22, -0.02, 50)    # back foot loaded, toe turned out
_Rf = foot_ball(-0.35, -0.20, 0)      # trailing foot after the finish
BH_T = dict(
    # pelvis yaw leads the shoulders: the hips start to open while the chest is still loading, then the chest overtakes them
    hip=[(0, 0), (6, 0), (11, 8), (17, 22), (22, 36), (26, 46), (29, 50), (30.5, 52), (32.5, 40), (34.5, 12), (GR, -32), (39, -45), (42, -48), (46, -46), (50, -44)],
    # chest: slow close through the run-up, a last small extra turn (anticipation), then the whip through to open
    sh=[(0, 0), (5, 6), (11, 22), (17, 48), (22, 72), (26, 90), (29, 96), (30.5, 98), (32.5, 80), (34.5, 38), (GR, -4), (39, -40), (42, -62), (46, -60), (50, -56)],
    lean=[(0, 12), (13, 18), (23, 20), (29, 22), (33, 20), (GR, 22), (39, 26), (46, 22), (50, 14)],
    hinge=[(0, 8), (29, 12), (GR, 8), (50, 4)],
    bend=[(0, 0), (29, 6), (33, 10), (GR, 18), (42, 12), (50, 4)],
    look=[(0, 0), (50, 0)],
    bx=[(0, 0), (8, 0.04), (13, 0), (16, -0.05), (23, -0.06), (29, -0.08), (GR, 0.08), (50, 0.12)],
    by=[(0, 0), (6, 0), (13, -0.05), (23, -0.14), (29, -0.17), (33, -0.21), (GR, -0.31), (42, -0.34), (50, -0.35)],
    bz=[(0, -0.07), (6, -0.06), (9, -0.10), (13, -0.09), (19, -0.15), (23, -0.21), (29, -0.28), (33, -0.24), (GR, -0.13), (42, -0.09), (50, -0.07)],
    # throwing arm, in the chest's frame: out-and-back reach, pull in close (elbow leads), snap out flat at hip height, sweep across and up
    raz=[(0, -12), (5, -6), (11, 20), (17, 52), (22, 84), (26, 102), (29, 108), (30.5, 110), (32.5, 92), (34.5, 58), (GR, 22), (39, -8), (42, -30), (46, -36), (50, -32)],
    rrad=[(0, 0.37), (13, 0.36), (23, 0.40), (29, 0.40), (32.5, 0.34), (34.5, 0.28), (GR, 0.40), (39, 0.40), (42, 0.35), (50, 0.30)],
    rel=[(0, -34), (23, -26), (29, -20), (33, -30), (34.5, -34), (GR, -16), (39, -4), (42, 12), (46, 24), (50, 20)],
    rpd=[(0, 1.0), (50, 1.0)], rpb=[(0, 0.3), (33, 0.8), (GR, 0.3), (50, 0.3)], rpo=[(0, 0.5), (50, 0.5)],
    rwh=[(0, 0), (33, -20), (GR, 10), (42, 0)],
    # off arm: holds the disc through the run-up, tucks across the chest at the coil, then opens like a wing to counter-rotate
    lsync=[(0, 1), (7, 1), (13, 0), (50, 0)],
    laz=[(0, 10), (10, 10), (17, 0), (23, 8), (29, 15), (33, 30), (GR, 85), (42, 95), (46, 65), (50, 38)],
    lrad=[(0, 0.30), (10, 0.30), (17, 0.26), (23, 0.25), (29, 0.25), (GR, 0.38), (42, 0.35), (50, 0.26)],
    lel=[(0, -30), (10, -30), (17, -5), (23, 5), (29, 5), (GR, 10), (42, 20), (50, 0)],
    # left (front) foot: steps through and plants wide at g23.5, then holds the whole throw
    Lbx=[(0, 0.193), (14, 0.193), (23.5, _Lb[0]), (50, _Lb[0])],
    Lby=[(0, -0.232), (14, -0.232), (18, -0.40), (22, -0.64), (23.5, _Lb[1]), (50, _Lb[1])],
    Lz=[(0, 0), (14, 0), (16, 0.05), (19, 0.14), (22, 0.04), (23.5, 0), (50, 0)],
    Lyaw=[(0, -14), (50, -14)], Lheel=[(0, 0), (14, 0), (16, 22), (19, 8), (23.5, 0), (50, 0)],
    # right (back) foot: one step forward, then loaded and turned out; heel rises and pivots through the throw, then trails
    Rbx=[(0, -0.293), (6, -0.293), (13, _Rb[0]), (34, _Rb[0]), (42, _Rb[0]), (46, _Rf[0]), (50, _Rf[0])],
    Rby=[(0, -0.142), (6, -0.142), (9, -0.22), (13, _Rb[1]), (34, _Rb[1]), (42, _Rb[1]), (46, _Rf[1]), (50, _Rf[1])],
    Rz=[(0, 0), (6, 0), (9, 0.10), (13, 0), (34, 0), (42, 0.0), (45, 0.14), (48, 0.06), (50, 0.03)],
    Ryaw=[(0, 14), (6, 14), (13, 50), (34, 50), (40, 34), (44, 26), (50, 8)],
    Rheel=[(0, 0), (6, 0), (9, 25), (13, 0), (34, 0), (39, 40), (42, 38), (46, 55), (50, 55)],
)

def tail(T, key, g0, new):
    """Keep the keys of T[key] before g0 and append `new` (list of (g, v))."""
    return [k for k in T[key] if k[0] < g0] + list(new)

# --- round 17: continuous, decaying follow-through (see docs/ANIMATION_STUDY.md).  Keys at g > 50 are never played; they only keep the
# curves moving (non-zero velocity) at the last frame so the clip does not settle into a held pose.
BH_T.update(
    hip=tail(BH_T, 'hip', 39, [(39, -46), (42, -52), (46, -49), (50, -42), (56, -30)]),
    sh=tail(BH_T, 'sh', 39, [(39, -42), (42, -62), (46, -58), (50, -46), (56, -28)]),
    lean=tail(BH_T, 'lean', 39, [(39, 26), (44, 20), (50, 11), (56, 4)]),
    bend=tail(BH_T, 'bend', 42, [(42, 10), (50, 3), (56, 0)]),
    by=tail(BH_T, 'by', 42, [(42, -0.35), (50, -0.36), (56, -0.37)]),
    bz=tail(BH_T, 'bz', 42, [(42, -0.10), (50, -0.10), (56, -0.10)]),
    # throwing arm: sweeps across and UP after the release (peak ~0.25 s later), then falls back toward the side while the elbow folds
    raz=tail(BH_T, 'raz', 39, [(39, -10), (42, -34), (45, -34), (50, -14), (56, -4)]),
    rel=tail(BH_T, 'rel', 39, [(39, 2), (42, 20), (44, 18), (47, -4), (50, -26), (56, -52)]),
    rrad=tail(BH_T, 'rrad', 39, [(39, 0.40), (43, 0.33), (50, 0.30), (56, 0.29)]),
    # off arm: opens as a counterweight at the release and is already dropping at the end
    laz=tail(BH_T, 'laz', GR, [(GR, 70), (39, 80), (42, 75), (46, 44), (50, 18), (56, 4)]),
    lrad=tail(BH_T, 'lrad', GR, [(GR, 0.36), (42, 0.33), (50, 0.29), (56, 0.28)]),
    lel=tail(BH_T, 'lel', GR, [(GR, 8), (42, 14), (47, -18), (50, -42), (56, -56)]),
    lsync=[(0, 1), (5, 1), (9, 0), (50, 0)],
    # back leg: heel peels, the leg kicks up and back (peak ~0.4 s after release), then swings through and lands as the trailing foot
    Rbx=tail(BH_T, 'Rbx', 40, [(40, _Rb[0]), (44, _Rb[0] - 0.07), (47, _Rb[0] - 0.08), (50, _Rf[0] - 0.02), (56, _Rf[0] - 0.05)]),
    Rby=tail(BH_T, 'Rby', 40, [(40, _Rb[1]), (44, _Rb[1] + 0.17), (47, _Rb[1] + 0.08), (50, _Rf[1]), (56, _Rf[1] - 0.12)]),
    Rz=tail(BH_T, 'Rz', 40, [(40, 0), (43, 0.12), (44.5, 0.20), (47, 0.12), (49, 0.04), (50, 0.0), (56, 0.0)]),
    Ryaw=tail(BH_T, 'Ryaw', 34, [(34, 50), (40, 34), (44, 20), (50, 6), (56, 0)]),
    Rheel=tail(BH_T, 'Rheel', 34, [(34, 0), (37, 12), (40, 40), (44, 70), (47, 45), (50, 12), (56, 0)]),
)
BH = build(BH_T)

# ---------------------------------------------------------------- FOREHAND (sidearm: open stance, smaller coil, elbow-led pocket pull, wrist whip)
_Lf = foot_ball(0.27, -0.46, -30)
_Rfb = foot_ball(-0.20, -0.02, 32)
_Rff = foot_ball(-0.26, -0.10, 0)
FH_T = dict(BH_T,
    # sidearm: small coil (chest ~55 deg closed, hips ~26), hips and chest both fire through to open, lean stays upright/back (no forward dive)
    hip=[(0, 0), (6, 0), (13, 5), (19, 12), (23, 18), (27, 24), (29, 26), (31, 24), (33, 14), (35, -6), (GR, -28), (39, -42), (42, -46), (46, -42), (50, -32), (56, -22)],
    sh=[(0, 0), (6, 2), (13, 10), (19, 24), (23, 36), (27, 48), (29, 54), (30.5, 56), (33, 42), (35, 16), (GR, -12), (39, -38), (42, -52), (46, -48), (50, -38), (56, -26)],
    lean=[(0, 12), (13, 14), (23, 12), (29, 10), (33, 8), (GR, 6), (39, 12), (46, 14), (50, 10), (56, 6)],
    hinge=[(0, 8), (29, 10), (GR, 8), (50, 4)],
    bend=[(0, 0), (29, 8), (33, 14), (GR, 16), (42, 10), (50, 3), (56, 0)],
    bx=[(0, 0), (13, 0), (29, -0.03), (GR, 0.06), (50, 0.10)],
    by=[(0, 0), (13, -0.04), (23, -0.10), (29, -0.13), (33, -0.17), (GR, -0.26), (42, -0.31), (50, -0.34), (56, -0.35)],
    bz=[(0, -0.07), (9, -0.09), (19, -0.13), (29, -0.20), (33, -0.19), (GR, -0.12), (42, -0.09), (50, -0.08), (56, -0.08)],
    # hand rides at the right hip pocket (below the belt, elbow bent and BEHIND the hand); the elbow leads the pull, the forearm and wrist
    # whip out flat at the release, then the forearm swings UP to head height (elbow folded) and falls back
    raz=[(0, -12), (6, -4), (13, 30), (19, 55), (23, 68), (27, 74), (29, 76), (31, 76), (33, 66), (35, 48), (GR, 24), (39, 0), (42, -18), (45, -22), (50, -10), (56, 0)],
    rrad=[(0, 0.37), (6, 0.36), (13, 0.31), (23, 0.28), (29, 0.27), (33, 0.26), (35, 0.27), (GR, 0.40), (39, 0.40), (42, 0.31), (50, 0.28), (56, 0.28)],
    rel=[(0, -34), (13, -40), (23, -46), (29, -50), (33, -52), (35, -44), (GR, -12), (39, 8), (42, 34), (44, 38), (47, 28), (50, 6), (56, -20)],
    rpd=[(0, 1.0), (29, 0.6), (50, 1.0)], rpb=[(0, 0.3), (29, 0.9), (35, 1.0), (GR, 0.3), (50, 0.3)], rpo=[(0, 0.5), (29, 0.3), (50, 0.5)],
    rwh=[(0, 0), (29, -10), (33, -30), (35, -25), (GR, 30), (39, 10), (42, 0)],
    lsync=[(0, 1), (5, 1), (9, 0), (50, 0)],
    laz=[(0, 10), (10, 10), (17, 0), (23, 6), (29, 12), (33, 28), (GR, 64), (39, 72), (42, 66), (46, 44), (50, 20), (56, 6)],
    lrad=[(0, 0.30), (10, 0.30), (17, 0.26), (29, 0.25), (GR, 0.36), (42, 0.33), (50, 0.29), (56, 0.28)],
    lel=[(0, -30), (10, -30), (17, -5), (29, 5), (GR, 6), (42, 12), (47, -18), (50, -40), (56, -54)],
    # open stance: front foot wide-left and turned out, back foot only pivots (heel peels, short drag-through) instead of the BH kick
    Lbx=[(0, 0.193), (14, 0.193), (23.5, _Lf[0]), (50, _Lf[0])],
    Lby=[(0, -0.232), (14, -0.232), (18, -0.38), (22, -0.60), (23.5, _Lf[1]), (50, _Lf[1])],
    Lyaw=[(0, -14), (14, -14), (23.5, -30), (50, -30)],
    Rbx=[(0, -0.293), (6, -0.293), (13, _Rfb[0]), (40, _Rfb[0]), (50, _Rff[0]), (56, _Rff[0] - 0.03)],
    Rby=[(0, -0.142), (6, -0.142), (9, -0.22), (13, _Rfb[1]), (40, _Rfb[1]), (45, _Rfb[1] + 0.08), (50, _Rff[1]), (56, _Rff[1] - 0.10)],
    Rz=[(0, 0), (6, 0), (9, 0.10), (13, 0), (36, 0), (40, 0.03), (44, 0.10), (48, 0.06), (50, 0.02), (56, 0)],
    Ryaw=[(0, 14), (6, 14), (13, 32), (34, 32), (40, 10), (46, 2), (50, 0)],
    Rheel=[(0, 0), (6, 0), (9, 25), (13, 0), (34, 0), (38, 35), (42, 55), (46, 40), (50, 15), (56, 0)],
)
FH = build(FH_T)
SETS = {'BH': BH, 'FH': FH}


def at(S, g):
    """Parameters of timeline set S at timeline frame g."""
    return sample(S, g)

def windup(S, p):
    """Power-charge pose: the upper body coils with power while the feet stay at the address stance."""
    a = sample(S, 0); c = sample(S, T0)
    P = dict(a)
    for k in ('hip', 'sh', 'lean', 'bend', 'raz', 'rrad', 'rel', 'rwh', 'laz', 'lrad', 'lel', 'lsync', 'rpb'):
        P[k] = a[k] + (c[k] - a[k]) * smooth(p) * (0.85 if k in ('hip', 'sh') else 1.0)
    P['bz'] = a['bz'] - 0.06 * smooth(p); P['by'] = a['by'] + 0.04 * smooth(p)
    return P


# ---------------------------------------------------------------- PUTTING
# Each putt is ONE clip: the first half is driven by the power charge (and, for the jump putt, the jump-timing marker),
# the second half by swing time.  Release fractions (of frames 0..n-1):  Putt_Stand 0.55  (frame 10.45 of 20),
# Putt_Jump 0.58 (frame 14.5 of 26; power charge = 0-25%, jump timing marker = 25-50%, take-off at 50%).
PUTT_STAND_N, PUTT_STAND_REL = 20, 0.55
PUTT_JUMP_N, PUTT_JUMP_REL = 26, 0.58
_RS = PUTT_STAND_REL * (PUTT_STAND_N - 1)
_RJ = PUTT_JUMP_REL * (PUTT_JUMP_N - 1)
_narrowL = foot_ball(0.17, -0.07, -10); _narrowR = foot_ball(-0.17, 0.02, 10)
STAND_T = dict(
    # planted feet, soft knees, trunk quiet; a small pendulum of the arm with a short push at release
    Lbx=[(0, _narrowL[0])], Lby=[(0, _narrowL[1])], Lyaw=[(0, -10)], Rbx=[(0, _narrowR[0])], Rby=[(0, _narrowR[1])], Ryaw=[(0, 10)],
    hip=[(0, 0), (9, 10), (_RS, 0), (14, -8), (19, -6)],
    sh=[(0, 0), (9, 22), (_RS - 0.8, 17), (_RS, 2), (13, -10), (19, -10)],
    lean=[(0, 22), (9, 26), (_RS, 28), (19, 18)], hinge=[(0, 14), (19, 8)], bend=[(0, 0), (9, 4), (_RS, 2), (19, 0)],
    bz=[(0, -0.12), (9, -0.17), (_RS, -0.14), (19, -0.07)], by=[(0, 0), (9, 0.03), (_RS, -0.05), (19, -0.06)], bx=[(0, 0.0), (9, -0.02), (_RS, 0.02), (19, 0.02)],
    look=[(0, 0), (19, 0)],
    # backstroke grows with the charge (frames 0-9), then a short push and follow-through along the target line
    raz=[(0, 4), (9, 42), (_RS - 0.8, 38), (_RS, 6), (13, -2), (19, -2)],
    rrad=[(0, 0.32), (9, 0.24), (_RS - 0.8, 0.25), (_RS, 0.40), (13, 0.41), (19, 0.40)],
    rel=[(0, -30), (9, -36), (_RS, -10), (13, 8), (19, 10)],
    rpb=[(0, 0.3), (9, 0.6), (_RS, 0.2)], rwh=[(0, 0), (9, -10), (_RS, 14), (14, 4)],
    lsync=[(0, 1), (8, 1), (10, 0), (19, 0)], laz=[(0, 10), (8, 10), (12, 35), (19, 40)], lrad=[(0, 0.28), (19, 0.28)], lel=[(0, -30), (12, -42), (19, -45)],
)
STAND_T.update(
    # round 17 (Heimburg 255.8-257.1 s, Robinson 1327-1329 s): short back-stroke, flat push to chest height, then a recoil toward the belly while
    # the trunk rises and the back heel peels (weight onto the front foot) -- nothing holds at the end
    raz=[(0, 4), (9, 36), (_RS - 0.8, 34), (_RS, 4), (13, -2), (16, -4), (19, -8), (23, -10)],
    rrad=[(0, 0.32), (9, 0.24), (_RS - 0.8, 0.25), (_RS, 0.40), (13, 0.42), (15, 0.40), (19, 0.30), (23, 0.26)],
    rel=[(0, -30), (9, -38), (_RS, -8), (12.5, 10), (15, 6), (19, -20), (23, -34)],
    lean=[(0, 22), (9, 26), (_RS, 28), (14, 22), (19, 15), (23, 10)],
    Rheel=[(0, 0), (_RS, 0), (13, 16), (19, 30), (23, 34)],
    laz=[(0, 10), (8, 10), (12, 30), (19, 22), (23, 18)], lel=[(0, -30), (12, -48), (19, -52), (23, -54)],
)
# backhand-style putt: hand starts at chest/shoulder height (Robinson style), elbow folds on the back-stroke, then pushes out and up
BHPUSH_T = dict(STAND_T,
    rel=[(0, -4), (9, -10), (_RS, 4), (12.5, 16), (15, 12), (19, -12), (23, -26)],
    rrad=[(0, 0.30), (9, 0.24), (_RS - 0.8, 0.25), (_RS, 0.40), (13, 0.42), (19, 0.30), (23, 0.26)],
    raz=[(0, 14), (9, 30), (_RS - 0.8, 28), (_RS, 4), (13, 0), (19, -4), (23, -6)],
    rwh=[(0, 0), (9, -14), (_RS, 16), (14, 6)],
)
BHPUSH = build(BHPUSH_T)
STAND = build(STAND_T)

_jL = foot_ball(0.16, -0.02, -10); _jR = foot_ball(-0.16, 0.0, 10)
JUMP_T = dict(
    # crouch deepens with the charge, stays loaded through the timing marker, springs at 50%, release in the air with legs
    # together and kicked back, soft landing absorbed through the knees
    Lbx=[(0, _jL[0]), (12, _jL[0]), (14, 0.16), (16, 0.16), (18.5, 0.17), (25, _jL[0])],
    Lby=[(0, _jL[1]), (12, _jL[1]), (14, -0.05), (16, 0.05), (18.5, _jL[1]), (25, _jL[1])],
    Lz=[(0, 0), (12, 0), (13, 0.0), (14.2, 0.12), (16, 0.22), (17.4, 0.10), (18.5, 0), (25, 0)],
    Lyaw=[(0, -10), (14, -4), (18.5, -10)], Lheel=[(0, 0), (12, 0), (13.5, 35), (16, 25), (17.8, 8), (18.5, 0)],
    Rbx=[(0, _jR[0]), (12, _jR[0]), (14, -0.16), (16, -0.16), (18.5, -0.17), (25, _jR[0])],
    Rby=[(0, _jR[1]), (12, _jR[1]), (14, -0.04), (16, 0.08), (18.5, _jR[1]), (25, _jR[1])],
    Rz=[(0, 0), (12, 0), (13, 0.0), (14.2, 0.10), (16, 0.24), (17.4, 0.10), (18.5, 0), (25, 0)],
    Ryaw=[(0, 10), (14, 4), (18.5, 10)], Rheel=[(0, 0), (12, 0), (13.5, 35), (16, 28), (17.8, 8), (18.5, 0)],
    hip=[(0, 0), (6, 8), (12, 10), (_RJ, 0), (18, -4), (25, -2)],
    sh=[(0, 0), (6, 16), (12, 18), (14, 10), (_RJ, 0), (17, -6), (25, -6)],
    lean=[(0, 22), (6, 30), (12, 34), (_RJ, 22), (18.5, 30), (25, 22)], hinge=[(0, 12), (6, 22), (12, 26), (14.5, 10), (18.5, 22), (25, 12)],
    bend=[(0, 0), (12, 4), (_RJ, 3), (25, 0)],
    bz=[(0, -0.14), (6, -0.40), (12, -0.43), (13, -0.30), (14.2, -0.05), (16, 0.10), (17.4, -0.05), (18.5, -0.36), (20.5, -0.30), (25, -0.14)],
    by=[(0, 0), (12, 0.03), (16, -0.12), (18.5, -0.10), (25, -0.06)], bx=[(0, 0.0), (25, 0.0)],
    look=[(0, 0), (25, 0)],
    raz=[(0, 4), (6, 30), (12, 32), (13.6, 24), (_RJ, 4), (17, 0), (25, 0)],
    rrad=[(0, 0.32), (6, 0.26), (12, 0.25), (13.6, 0.28), (_RJ, 0.40), (17, 0.40), (25, 0.36)],
    rel=[(0, -30), (6, -32), (12, -34), (_RJ, -4), (17, 2), (25, -14)],
    rpb=[(0, 0.3), (12, 0.6), (_RJ, 0.2)], rwh=[(0, 0), (12, -12), (_RJ, 10), (18, 0)],
    lsync=[(0, 1), (11, 1), (13, 0), (25, 0)], laz=[(0, 10), (11, 10), (14, 55), (17, 70), (25, 35)], lrad=[(0, 0.28), (14, 0.34), (25, 0.28)], lel=[(0, -30), (14, -10), (17, 10), (25, -40)],
)
JUMP_T.update(
    # round 17 (K: no jump-putt footage was found in the two videos; built from the landing/follow-through studied on the throws):
    # deep crouch, spring at 12.5, release at the apex with the lower legs kicked BACK (heels up, knees bent), body pitched forward,
    # staggered soft landing, arms recover, trunk rises again
    Lby=[(0, _jL[1]), (12, _jL[1]), (14, -0.02), (15.5, 0.26), (17, 0.20), (18.5, _jL[1]), (25, _jL[1])],
    Rby=[(0, _jR[1]), (12, _jR[1]), (14, -0.02), (15.5, 0.30), (17, 0.20), (18.5, _jR[1]), (25, _jR[1])],
    Lz=[(0, 0), (12, 0), (13, 0.0), (14.2, 0.12), (15.5, 0.24), (17, 0.12), (18.2, 0), (25, 0)],
    Rz=[(0, 0), (12, 0), (13, 0.0), (14.2, 0.10), (15.5, 0.26), (17, 0.10), (18.5, 0), (25, 0)],
    Lheel=[(0, 0), (12, 0), (13.5, 35), (15.5, 80), (17.5, 30), (18.5, 0)],
    Rheel=[(0, 0), (12, 0), (13.5, 35), (15.5, 85), (17.5, 30), (18.5, 0)],
    lean=[(0, 22), (6, 30), (12, 34), (_RJ, 36), (17, 34), (18.5, 32), (22, 24), (28, 18)],
    rel=[(0, -30), (6, -32), (12, -34), (_RJ, 6), (17, 10), (21, -8), (25, -26), (28, -32)],
    raz=[(0, 4), (6, 30), (12, 32), (13.6, 24), (_RJ, 4), (17, 0), (21, -4), (25, -6)],
    bz=[(0, -0.14), (6, -0.40), (12, -0.43), (13, -0.30), (14.2, -0.05), (15.5, 0.10), (17, -0.02), (18.5, -0.34), (20.5, -0.32), (25, -0.18), (28, -0.14)],
    laz=[(0, 10), (11, 10), (14, 55), (16, 60), (20, 40), (25, 20)], lel=[(0, -30), (14, -10), (16, 4), (20, -20), (25, -44)],
)
JUMP = build(JUMP_T)

def _fh_putt(T):
    """Forehand (sidearm) putt: hand starts at the hip, elbow behind, the forearm flicks through along the target line."""
    F = dict(T)
    F['raz'] = [(k, v + (28 if v > 10 else 18)) for k, v in T['raz']]
    F['rel'] = [(k, v - 8) for k, v in T['rel']]
    F['rpb'] = [(k, v + 0.5) for k, v in T['rpb']]
    F['rwh'] = [(k, v * 2.0) for k, v in T['rwh']]
    return F

PUTTS = {
    'Putt_Stand': (STAND, PUTT_STAND_N, 700), 'Putt_Stand_BH': (BHPUSH, PUTT_STAND_N, 800), 'Putt_Stand_FH': (build(_fh_putt(STAND_T)), PUTT_STAND_N, 900),
    'Putt_Jump': (JUMP, PUTT_JUMP_N, 1000), 'Putt_Jump_BH': (JUMP, PUTT_JUMP_N, 1100), 'Putt_Jump_FH': (build(_fh_putt(JUMP_T)), PUTT_JUMP_N, 1200),
}

def putt_at(S, i, n):
    return sample(S, i)
