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
_Rf = foot_ball(-0.32, -0.10, 0)      # trailing foot after the finish
BH_T = dict(
    # pelvis yaw leads the shoulders: the hips start to open while the chest is still loading, then the chest overtakes them
    hip=[(0, 0), (6, 0), (13, 10), (19, 20), (23, 28), (27, 38), (29, 41), (31, 36), (33, 20), (35, -5), (GR, -30), (39, -48), (42, -52), (46, -48), (50, -44)],
    # chest: slow close through the run-up, a last small extra turn (anticipation), then the whip through to open
    sh=[(0, 0), (6, 3), (13, 18), (19, 40), (23, 62), (27, 80), (29, 86), (30.5, 90), (33, 62), (35, 25), (GR, -12), (39, -42), (42, -58), (46, -52), (50, -46)],
    lean=[(0, 12), (13, 18), (23, 22), (29, 24), (33, 22), (GR, 18), (39, 20), (50, 10)],
    hinge=[(0, 8), (29, 12), (GR, 8), (50, 4)],
    bend=[(0, 0), (29, 4), (GR, 14), (42, 8), (50, 3)],
    look=[(0, 0), (50, 0)],
    bx=[(0, 0), (8, 0.04), (13, 0), (16, -0.05), (23, -0.06), (29, -0.08), (GR, 0.08), (50, 0.12)],
    by=[(0, 0), (6, 0), (13, -0.05), (23, -0.14), (29, -0.17), (33, -0.21), (GR, -0.31), (42, -0.34), (50, -0.35)],
    bz=[(0, -0.18), (13, -0.20), (23, -0.26), (29, -0.28), (33, -0.24), (GR, -0.14), (42, -0.10), (50, -0.08)],
    # throwing arm, in the chest's frame: out-and-back reach, pull in close (elbow leads), snap out flat at hip height, sweep across and up
    raz=[(0, -12), (6, -6), (13, 18), (19, 50), (23, 80), (27, 98), (29, 104), (30.5, 106), (33, 85), (35, 55), (GR, 25), (39, -5), (42, -25), (46, -30), (50, -28)],
    rrad=[(0, 0.37), (13, 0.36), (23, 0.40), (29, 0.41), (33, 0.30), (35, 0.27), (GR, 0.40), (39, 0.40), (42, 0.34), (50, 0.30)],
    rel=[(0, -34), (23, -28), (29, -22), (33, -35), (35, -38), (GR, -20), (39, -12), (42, 5), (50, 20)],
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
BH = build(BH_T)

# ---------------------------------------------------------------- FOREHAND (sidearm: open stance, smaller coil, elbow-led pocket pull, wrist whip)
_Lf = foot_ball(0.27, -0.46, -30)
_Rfb = foot_ball(-0.20, -0.02, 32)
_Rff = foot_ball(-0.26, -0.10, 0)
FH_T = dict(BH_T,
    hip=[(0, 0), (6, 0), (13, 6), (19, 14), (23, 20), (27, 27), (29, 30), (31, 27), (33, 16), (35, -4), (GR, -26), (39, -44), (42, -50), (46, -46), (50, -42)],
    sh=[(0, 0), (6, 2), (13, 12), (19, 28), (23, 42), (27, 54), (29, 60), (30.5, 63), (33, 44), (35, 14), (GR, -14), (39, -42), (42, -56), (46, -50), (50, -45)],
    bend=[(0, 0), (29, 6), (GR, 16), (42, 8), (50, 3)],
    # hand rides at the right hip, elbow bent and behind it; forearm uncoils at the end of the pull
    raz=[(0, -12), (6, -6), (13, 12), (19, 34), (23, 52), (27, 62), (29, 66), (30.5, 68), (33, 58), (35, 42), (GR, 20), (39, -8), (42, -30), (46, -34), (50, -30)],
    rrad=[(0, 0.37), (13, 0.34), (23, 0.30), (29, 0.28), (33, 0.26), (35, 0.26), (GR, 0.40), (39, 0.40), (42, 0.33), (50, 0.30)],
    rel=[(0, -34), (23, -36), (29, -40), (33, -42), (35, -36), (GR, -14), (39, -4), (42, 12), (50, 24)],
    rpd=[(0, 1.0), (29, 0.6), (50, 1.0)], rpb=[(0, 0.3), (29, 0.9), (35, 1.0), (GR, 0.3), (50, 0.3)], rpo=[(0, 0.5), (29, 0.3), (50, 0.5)],
    rwh=[(0, 0), (29, -10), (33, -35), (GR, 25), (42, 0)],
    laz=[(0, 10), (10, 10), (17, 0), (23, 8), (29, 10), (33, 25), (GR, 70), (42, 80), (50, 38)],
    Lbx=[(0, 0.193), (14, 0.193), (23.5, _Lf[0]), (50, _Lf[0])],
    Lby=[(0, -0.232), (14, -0.232), (18, -0.38), (22, -0.60), (23.5, _Lf[1]), (50, _Lf[1])],
    Lyaw=[(0, -14), (14, -14), (23.5, -30), (50, -30)],
    Rbx=[(0, -0.293), (6, -0.293), (13, _Rfb[0]), (34, _Rfb[0]), (42, _Rfb[0]), (46, _Rff[0]), (50, _Rff[0])],
    Rby=[(0, -0.142), (6, -0.142), (9, -0.22), (13, _Rfb[1]), (34, _Rfb[1]), (42, _Rfb[1]), (46, _Rff[1]), (50, _Rff[1])],
    Ryaw=[(0, 14), (6, 14), (13, 32), (34, 32), (40, 4), (50, 0)],
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
_narrowL = foot_ball(0.16, -0.02, -10); _narrowR = foot_ball(-0.16, 0.0, 10)
STAND_T = dict(
    # planted feet, soft knees, trunk quiet; a small pendulum of the arm with a short push at release
    Lbx=[(0, _narrowL[0])], Lby=[(0, _narrowL[1])], Lyaw=[(0, -10)], Rbx=[(0, _narrowR[0])], Rby=[(0, _narrowR[1])], Ryaw=[(0, 10)],
    hip=[(0, 0), (9, 10), (_RS, 0), (14, -8), (19, -6)],
    sh=[(0, 0), (9, 22), (_RS - 0.8, 17), (_RS, 2), (13, -10), (19, -10)],
    lean=[(0, 24), (9, 26), (_RS, 28), (19, 24)], hinge=[(0, 14), (19, 12)], bend=[(0, 0), (9, 4), (_RS, 2), (19, 0)],
    bz=[(0, -0.14), (9, -0.17), (_RS, -0.13), (19, -0.12)], by=[(0, 0), (9, 0.03), (_RS, -0.05), (19, -0.06)], bx=[(0, 0.0), (9, -0.02), (_RS, 0.02), (19, 0.02)],
    look=[(0, 0), (19, 0)],
    # backstroke grows with the charge (frames 0-9), then a short push and follow-through along the target line
    raz=[(0, 4), (9, 42), (_RS - 0.8, 38), (_RS, 6), (13, -2), (19, -2)],
    rrad=[(0, 0.32), (9, 0.24), (_RS - 0.8, 0.25), (_RS, 0.40), (13, 0.41), (19, 0.40)],
    rel=[(0, -30), (9, -36), (_RS, -10), (13, -2), (19, -6)],
    rpb=[(0, 0.3), (9, 0.6), (_RS, 0.2)], rwh=[(0, 0), (9, -10), (_RS, 8), (14, 0)],
    lsync=[(0, 1), (8, 1), (10, 0), (19, 0)], laz=[(0, 10), (8, 10), (12, 35), (19, 40)], lrad=[(0, 0.28), (19, 0.28)], lel=[(0, -30), (12, -42), (19, -45)],
)
STAND = build(STAND_T)

JUMP_T = dict(
    # crouch deepens with the charge, stays loaded through the timing marker, springs at 50%, release in the air with legs
    # together and kicked back, soft landing absorbed through the knees
    Lbx=[(0, _narrowL[0]), (12, _narrowL[0]), (14, 0.16), (16, 0.16), (18.5, 0.17), (25, _narrowL[0])],
    Lby=[(0, _narrowL[1]), (12, _narrowL[1]), (14, -0.05), (16, 0.05), (18.5, _narrowL[1]), (25, _narrowL[1])],
    Lz=[(0, 0), (12, 0), (13, 0.0), (14.2, 0.12), (16, 0.22), (17.4, 0.10), (18.5, 0), (25, 0)],
    Lyaw=[(0, -10), (14, -4), (18.5, -10)], Lheel=[(0, 0), (12, 0), (13.5, 35), (16, 25), (17.8, 8), (18.5, 0)],
    Rbx=[(0, _narrowR[0]), (12, _narrowR[0]), (14, -0.16), (16, -0.16), (18.5, -0.17), (25, _narrowR[0])],
    Rby=[(0, _narrowR[1]), (12, _narrowR[1]), (14, -0.04), (16, 0.08), (18.5, _narrowR[1]), (25, _narrowR[1])],
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
    'Putt_Stand': (STAND, PUTT_STAND_N, 700), 'Putt_Stand_BH': (STAND, PUTT_STAND_N, 800), 'Putt_Stand_FH': (build(_fh_putt(STAND_T)), PUTT_STAND_N, 900),
    'Putt_Jump': (JUMP, PUTT_JUMP_N, 1000), 'Putt_Jump_BH': (JUMP, PUTT_JUMP_N, 1100), 'Putt_Jump_FH': (build(_fh_putt(JUMP_T)), PUTT_JUMP_N, 1200),
}

def putt_at(S, i, n):
    return sample(S, i)
