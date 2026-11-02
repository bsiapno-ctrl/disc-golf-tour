import sys, os; sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from motions import *
import math
CAM = Vector((-0.8, 3.3, 0)); RIGHT = Vector((-3.3, -0.8, 0)).normalized()
def clips():
    from motions import SETS, T0, windup, PUTTS, putt_at
    for sfx, S in SETS.items():
        yield 'Windup_' + sfx, [windup(S, p) for p in (0, 1)]
        yield 'Throw_' + sfx, [at(S, T0 + i) for i in range(22)]
        yield 'Runup_' + sfx, [at(S, i) for i in range(RUN_N)]
    for name, (S, nf, off) in PUTTS.items():
        yield name, [putt_at(S, i, nf) for i in range(nf)]
    yield 'Idle', [idle_pose(True, i) for i in range(0, 60, 6)]
def lat(v): return v.dot(RIGHT)
ok = True
for ch in sys.argv[sys.argv.index('--') + 1:]:
    arm = setup()
    for o in bpy.data.objects:
        if o.type == 'MESH': o.hide_viewport = True
    for name, Ps in clips():
        mg = mk = 1e9; flip = False; wf = wk = None
        for i, P in enumerate(Ps):
            (pose if name == 'Idle' else pose2)(arm, P); pb = arm.pose.bones
            # screen-lateral: R leg (character right, -X) should stay on camera-right (larger lateral)
            fr, fl = lat(pb['Foot.R'].head), lat(pb['Foot.L'].head)
            kr, kl = lat(pb['LowerLeg.R'].head), lat(pb['LowerLeg.L'].head)
            g, k = fr - fl, kr - kl
            if g < mg: mg, wf = g, i
            if k < mk: mk, wk = k, i
        bad = mg < 0.25 or mk < 0.45
        ok &= not bad
        print(f"{ch:10s} {name:10s} min foot gap {mg:.3f} (f{wf}) min knee gap {mk:.3f} (f{wk}) {'FAIL' if bad else 'ok'}", flush=True)
print('ALL OK' if ok else 'FAILURES')
