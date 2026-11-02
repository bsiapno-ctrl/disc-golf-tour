"""Throw / run-up / putt animation, rebuilt around real trunk rotation.

Everything here is driven by *channels*: lists of (frame, value) keys on one shared timeline, evaluated with a monotone
cubic (no overshoot, no wobble between keys) and then solved on the armature with two-bone IK for the arms and legs and
real twist on the Abdomen / Torso / Chest bones.  Run-up and throw are two windows onto the same timeline, so the last
run-up frame IS the first throw frame.

Conventions (armature space, as in rig.py): the character faces -Y, its right (throwing) side is -X, up is +Z.
Yaw angles are 'closed' positive: +90 turns the chest to face the character's right (away from the target, for a
right-hander), negative turns it open, toward the left.
"""
import sys, os, math, bisect
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from rig import *

LF = 0.177            # ankle -> ball of foot
ERR = {'max': 0.0}    # worst unreachable IK target (m), for the checker


# ---------------------------------------------------------------- curves
def pchip(keys):
    """Monotone cubic through (x, y) keys; flat (eased) at both ends, no overshoot between keys."""
    xs = [k[0] for k in keys]; ys = [k[1] for k in keys]; n = len(xs)
    h = [xs[i + 1] - xs[i] for i in range(n - 1)]; d = [(ys[i + 1] - ys[i]) / h[i] for i in range(n - 1)]
    m = [0.0] * n
    for i in range(1, n - 1):
        if d[i - 1] * d[i] > 0:
            w1 = 2 * h[i] + h[i - 1]; w2 = h[i] + 2 * h[i - 1]
            m[i] = (w1 + w2) / (w1 / d[i - 1] + w2 / d[i])
    def f(x):
        if x <= xs[0]: return ys[0]
        if x >= xs[-1]: return ys[-1]
        i = bisect.bisect_right(xs, x) - 1; t = (x - xs[i]) / h[i]
        t2 = t * t; t3 = t2 * t
        return (2 * t3 - 3 * t2 + 1) * ys[i] + (t3 - 2 * t2 + t) * h[i] * m[i] + (-2 * t3 + 3 * t2) * ys[i + 1] + (t3 - t2) * h[i] * m[i + 1]
    return f

def build(table):
    return {k: pchip(v) for k, v in table.items()}

def sample(fns, g):
    return {k: f(g) for k, f in fns.items()}


# ---------------------------------------------------------------- solver
_FR = {}
def _prep(arm):
    if _FR: return
    reset(arm)
    for s in 'LR':
        ft = arm.pose.bones['Foot.' + s]
        h = ft.head.copy(); t = ft.tail.copy()
        _FR[s] = dict(mat=ft.matrix.copy(), head=h, tail=t, psi=math.degrees(math.atan2(-(t.x - h.x), -(t.y - h.y))))

def rz(deg, v=(1, 0, 0)):
    return Matrix.Rotation(-D(deg), 3, 'Z') @ Vector(v)

def ik2(b1, b2, target, pole):
    """Two-bone IK: b1 upper, b2 lower; the joint between them bends toward `pole`."""
    S = b1.head.copy()
    L1 = (b1.tail - b1.head).length; L2 = (b2.tail - b2.head).length
    to = target - S; d = to.length
    dd = min(max(d, abs(L1 - L2) + 1e-3), L1 + L2 - 1e-3)
    ERR['max'] = max(ERR['max'], d - dd)
    u = to / max(d, 1e-6)
    a = (dd * dd + L1 * L1 - L2 * L2) / (2 * dd)
    h = math.sqrt(max(L1 * L1 - a * a, 0.0))
    v = pole - u * pole.dot(u)
    if v.length < 1e-4: v = Vector((0, -1, 0)) - u * u.y * -1
    v.normalize()
    E = S + u * a + v * h
    aim(b1, E - S)
    aim(b2, S + u * dd - E)
    return E

DEFAULT = dict(bx=0.0, by=0.0, bz=-0.18, hip=0.0, hinge=8.0, sh=0.0, lean=12.0, bend=0.0, look=0.0, hp=0.0,
               raz=0.0, rrad=0.37, rel=-34.0, rpd=1.0, rpb=0.3, rpo=0.5, rwh=0.0,
               laz=10.0, lrad=0.30, lel=-30.0, lpd=1.0, lpb=0.3, lpo=0.5, lsync=1.0,
               Lbx=0.193, Lby=-0.232, Lz=0.0, Lyaw=-14.0, Lheel=0.0,
               Rbx=-0.293, Rby=-0.142, Rz=0.0, Ryaw=14.0, Rheel=0.0)

def pose2(arm, P):
    P = dict(DEFAULT, **P)
    _prep(arm)
    reset(arm); pb = arm.pose.bones
    # ---- pelvis: pitch (hinge) and yaw about the hip centre, then place it
    hc = Vector((0.0, -0.04, 0.936))
    rot_about(pb['Body'], 'X', D(P['hinge']), pivot=hc)
    rot_about(pb['Body'], 'Z', -D(P['hip']), pivot=hc)
    pb['Body'].matrix = Matrix.Translation((P['bx'], P['by'], P['bz'])) @ pb['Body'].matrix; upd()
    # ---- trunk: twist shared down the spine, forward lean about the trunk's own lateral axis, side bend about its forward axis
    cum = P['hip']; tw = P['sh'] - P['hip']
    for name, w in (('Abdomen', 0.30), ('Torso', 0.40), ('Chest', 0.30)):
        rot_about(pb[name], 'Z', -D(tw * w)); cum += tw * w
        rot_about(pb[name], rz(cum, (1, 0, 0)), D(P['lean'] * w))
        rot_about(pb[name], -rz(cum, (0, 1, 0)), D(P['bend'] * w))
    # ---- head tracks the target: counter-rotate against the chest (neck + head share it), tip the chin up against the lean
    comp = max(-75.0, min(75.0, P['sh'] - P['look']))
    rot_about(pb['Neck'], 'Z', D(comp * 0.5)); rot_about(pb['Head'], 'Z', D(comp * 0.5))
    rot_about(pb['Head'], rz(P['sh'] - comp, (1, 0, 0)), D(-(P['lean'] + P['hinge']) * 0.7 + P['hp']))
    # ---- arms (IK to a hand target given in the chest's own frame)
    f = rz(P['sh'], (0, -1, 0)); r = rz(P['sh'], (-1, 0, 0)); u = Vector((0, 0, 1))
    def hand(side, az, rad, el, pd, pbk, po):
        sg = 1.0 if side == 'R' else -1.0
        S = pb['UpperArm.' + side].head.copy()
        a = D(az); e = D(el)
        tgt = S + (f * math.cos(a) + r * sg * math.sin(a)) * (rad * math.cos(e)) + u * (rad * math.sin(e))
        return tgt, (-u * pd) + (-f * pbk) + (r * sg * po)
    tgt, pole = hand('R', P['raz'], P['rrad'], P['rel'], P['rpd'], P['rpb'], P['rpo'])
    ik2(pb['UpperArm.R'], pb['LowerArm.R'], tgt, pole)
    wr = pb['Wrist.R'].head.copy()
    tgt, pole = hand('L', P['laz'], P['lrad'], P['lel'], P['lpd'], P['lpb'], P['lpo'])
    sup = wr + (-r) * 0.05 + f * 0.0 + u * 0.02
    tgt = tgt + (sup - tgt) * P['lsync']
    ik2(pb['UpperArm.L'], pb['LowerArm.L'], tgt, pole)
    for s in 'RL':   # hands continue the forearm line (with a small horizontal flick on the throwing hand)
        d = (pb['Wrist.' + s].head - pb['LowerArm.' + s].head).normalized()
        if s == 'R' and P['rwh']: d = Matrix.Rotation(-D(P['rwh']), 3, 'Z') @ d
        aim(pb['Wrist.' + s], d)
    # ---- legs: ball-of-foot planting with yaw and heel lift, knees follow the toes
    for s, sg in (('L', 1.0), ('R', -1.0)):
        fr = _FR[s]; ft = pb['Foot.' + s]
        psi = P[s + 'yaw']; phi = P[s + 'heel']
        B = Vector((P[s + 'bx'], P[s + 'by'], fr['tail'].z + P[s + 'z']))
        R = Matrix.Rotation(D(phi), 4, rz(psi, (1, 0, 0))) @ Matrix.Rotation(-D(psi - fr['psi']), 4, 'Z')
        T = Matrix.Translation(B) @ R @ Matrix.Translation(-fr['tail'])
        ft.matrix = T @ fr['mat']
        ankle = T @ fr['head']
        pole = rz(psi - sg * 34.0, (0, -1, 0))
        ik2(pb['UpperLeg.' + s], pb['LowerLeg.' + s], ankle, pole)
    upd()


def shoulder_yaw(arm):
    """Closed-positive yaw of the shoulder line (right shoulder joint minus left), degrees, 0 = square to the target."""
    pb = arm.pose.bones
    v = pb['UpperArm.R'].head - pb['UpperArm.L'].head
    return math.degrees(math.atan2(v.y, -v.x))

def hip_yaw(arm):
    pb = arm.pose.bones
    v = pb['UpperLeg.R'].head - pb['UpperLeg.L'].head
    return math.degrees(math.atan2(v.y, -v.x))
