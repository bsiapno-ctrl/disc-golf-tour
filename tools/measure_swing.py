"""Numeric check of the throw / run-up / putt clips: shoulder-line and hip-line yaw per frame, IK reach error, foot planting.
usage: python3 measure_swing.py [CSV_OUT]    (blender bpy via python3)"""
import sys, os; sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from motions import *
arm = setup()
for o in bpy.data.objects:
    if o.type == 'MESH': o.hide_viewport = True
pb = arm.pose.bones
rows = []
def clip(name, Ps):
    ys = []
    for i, P in enumerate(Ps):
        ERR['max'] = 0.0
        pose2(arm, P)
        ys.append((i, shoulder_yaw(arm), hip_yaw(arm), ERR['max'], pb['Wrist.R'].head.copy(), pb['Foot.L'].head.copy(), pb['Foot.R'].head.copy()))
    return ys
ok = True
for sfx in ('BH', 'FH'):
    S = SETS.get(sfx)
    if not S: continue
    run = clip('Runup_' + sfx, [at(S, i) for i in range(RUN_N)])
    thr = clip('Throw_' + sfx, [at(S, T0 + i) for i in range(22)])
    allf = run[:-1] + [(len(run) - 1 + i, *r[1:]) for i, r in enumerate(thr)]
    sy = [r[1] for r in allf]
    pk = max(range(len(sy)), key=lambda i: sy[i]); mn = min(range(pk, len(sy)), key=lambda i: sy[i])
    print(f"{sfx}: shoulder yaw peak closed {sy[pk]:.1f} deg at timeline frame {pk}; most open after it {sy[mn]:.1f} deg at {mn}; opens by {sy[pk]-sy[mn]:.1f} deg")
    rf = round(REL)
    print(f"   at release (throw frame {REL:.1f}): shoulder yaw {thr[7][1]:.1f}/{thr[8][1]:.1f}, hip yaw {thr[7][2]:.1f}/{thr[8][2]:.1f}; max IK miss {max(r[3] for r in allf):.4f} m")
    print("   frame  shoulder  hip   (run-up 0-29, throw 0-21 = timeline 29-50)")
    for i, r in enumerate(allf):
        print(f"   {i:3d}   {r[1]:7.1f} {r[2]:7.1f}  ikmiss {r[3]:.3f}")
    ok &= max(r[3] for r in allf) < 0.02
print('IK OK' if ok else 'IK REACH PROBLEM')
