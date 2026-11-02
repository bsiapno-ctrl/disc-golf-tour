import sys, os; sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from rig import *
import math
CAM = Vector((-0.8, 3.3, 0)); RIGHT = Vector((-3.3, -0.8, 0)).normalized()
def clips():
    n = 22
    yield 'Windup_BH', [windup(True, p) for p in (0, 1)]
    yield 'Windup_FH', [windup(False, p) for p in (0, 1)]
    yield 'Throw_BH', [swing_pose(True, i / (n - 1), R_) for i in range(n)]
    yield 'Throw_FH', [swing_pose(False, i / (n - 1), R_) for i in range(n)]
    yield 'Runup_BH', [runup_pose(True, i) for i in range(RUN_N)]
    yield 'Runup_FH', [runup_pose(False, i) for i in range(RUN_N)]
    yield 'Idle', [idle_pose(True, i) for i in range(0, 60, 6)]
def lat(v): return v.dot(RIGHT)
ok = True
for ch in sys.argv[sys.argv.index('--') + 1:]:
    arm = setup()
    for name, Ps in clips():
        mg = mk = 1e9; flip = False; wf = None
        for i, P in enumerate(Ps):
            pose(arm, P); pb = arm.pose.bones
            # screen-lateral: R leg (character right, -X) should stay on camera-right (larger lateral)
            fr, fl = lat(pb['Foot.R'].head), lat(pb['Foot.L'].head)
            kr, kl = lat(pb['LowerLeg.R'].head), lat(pb['LowerLeg.L'].head)
            g, k = fr - fl, kr - kl
            if g < mg: mg, wf = g, i
            mk = min(mk, k)
        bad = mg < 0.25 or mk < 0.18
        ok &= not bad
        print(f"{ch:10s} {name:10s} min foot gap {mg:.3f} (f{wf}) min knee gap {mk:.3f} {'FAIL' if bad else 'ok'}", flush=True)
print('ALL OK' if ok else 'FAILURES')
