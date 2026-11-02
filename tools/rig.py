import bpy, math
from mathutils import Vector, Matrix, Quaternion

BLEND = "/tmp/claude-0/-home-claude/d08d10ca-5d1d-583b-b660-da3d8bb7f65d/scratchpad/a2/all/All together/Blends/Humans_Master.blend"
D = math.radians
FWD = Vector((0, -1, 0))

def setup():
    bpy.ops.wm.open_mainfile(filepath=BLEND)
    arm = bpy.data.objects['CharacterArmature']
    bpy.context.view_layer.objects.active = arm
    bpy.ops.object.mode_set(mode='POSE')
    for pb in arm.pose.bones:
        pb.rotation_mode = 'QUATERNION'
    arm.animation_data_create()
    arm.animation_data.action = None
    for pb in arm.pose.bones:   # the leg IK + pole constraints fight the hand-solved legs (knees swing inward): drive legs from two_bone_ik only
        for c in pb.constraints: c.mute = True
    return arm

def upd(): bpy.context.view_layer.update()

def reset(arm):
    for pb in arm.pose.bones:
        pb.rotation_quaternion = (1, 0, 0, 0); pb.location = (0, 0, 0); pb.scale = (1, 1, 1)
    upd()

def aim(pb, d):
    """Turn a posed bone about its head so it points along direction d (armature space)."""
    d = Vector(d).normalized()
    cur = (pb.tail - pb.head).normalized()
    q = cur.rotation_difference(d)
    m = pb.matrix
    t = m.translation.copy()
    pb.matrix = Matrix.Translation(t) @ (q.to_matrix() @ m.to_3x3()).to_4x4()
    upd()

def rot_about(pb, axis, ang, pivot=None):
    """Rotate a posed bone (and so its children) about a world axis through its head."""
    m = pb.matrix
    p = pivot if pivot is not None else m.translation.copy()
    R = Matrix.Rotation(ang, 4, axis)
    pb.matrix = Matrix.Translation(p) @ R @ Matrix.Translation(-p) @ m
    upd()

def arm_dir(yaw, pitch, side):
    """side=+1 throwing (right, -X) arm, -1 other arm. yaw 0 forward, + toward that arm's own side."""
    y = D(yaw); p = D(pitch)
    return Vector((-side * math.sin(y) * math.cos(p), -math.cos(y) * math.cos(p), -math.sin(p)))

def two_bone_ik(arm, up, lo, foot_target, knee_fwd=True, out=0.0):
    """Place thigh and shin so the ankle reaches foot_target; the knee bends toward the front."""
    hip = up.head.copy()
    L1 = (up.tail - up.head).length; L2 = (lo.tail - lo.head).length
    to = foot_target - hip
    dist = min(max(to.length, abs(L1 - L2) + 1e-3), L1 + L2 - 1e-3)
    to_n = to.normalized()
    a = math.acos(max(-1, min(1, (L1 * L1 + dist * dist - L2 * L2) / (2 * L1 * dist))))
    side = to_n.cross(Vector((1, 0, 0)))  # axis to swing the thigh forward
    # thigh direction: rotate to_n toward the front by angle a
    bend = Vector((out, -1, 0)) if knee_fwd else Vector((0, 1, 0))   # out: lateral knee push (+X = left side)
    perp = (bend - to_n * bend.dot(to_n)).normalized()
    thigh = (to_n * math.cos(a) + perp * math.sin(a)).normalized()
    aim(up, thigh)
    knee = up.tail.copy()
    aim(lo, (hip + to_n * dist) - knee)

def pose(arm, P):
    """P: dict armYaw elbow armPitch bodyYaw hipYaw crouch step lean (degrees, same meaning as in the game)."""
    reset(arm)
    pb = arm.pose.bones
    # remember rest ankle positions
    footL = pb['Foot.L'].head.copy(); footR = pb['Foot.R'].head.copy()
    # crouch: lower the body, knees bend
    drop = 0.22 * min(P["crouch"], 1.25)
    pb['Body'].location = (0, 0, 0)
    sh = P.get('shift', 0.0)
    drive = P.get('drive', 0.2 * sh)
    pb['Body'].matrix = Matrix.Translation((P.get('sway', 0.0), 0.04 - drive, -drop + P.get('bob', 0.0))) @ pb['Body'].matrix
    upd()
    # lean and turn of the trunk
    total = P['bodyYaw']; hip = P['hipYaw']
    rot_about(pb['Hips'], 'Z', -D(hip))
    rest = total - hip
    lag = P.get('lag', 0.0)   # shoulders trail the hips: abdomen/torso lead, chest hangs back
    for name, f in (('Abdomen', 0.25 + 0.15 * lag), ('Torso', 0.40 + 0.05 * lag), ('Chest', 0.35 - 0.20 * lag)):
        rot_about(pb[name], 'Z', -D(rest * f))
        rot_about(pb[name], 'X', D(P['lean'] * (0.3 if name != 'Chest' else 0.4)))
    # head keeps looking at the target
    rot_about(pb['Neck'], 'Z', D(total * 0.50 + P.get('hy', 0.0)))
    rot_about(pb['Head'], 'Z', D(total * 0.42))
    rot_about(pb['Head'], 'X', D(-4 - P.get('hc', 0.5) * P['lean']) if 'hc' in P else D(-8 - 0.5 * P['lean']))
    # throwing arm (right = .R) and the balancing arm
    fp = P.get('forePitch', 0.0); wy = P.get('wristYaw', 0.0); tuck = P.get('offTuck', 0.0)
    sg = max(-1.0, min(1.0, P['armYaw'] / 30.0))   # arm across the body bends the other way, toward the chest
    aim(pb['UpperArm.R'], arm_dir(P['armYaw'], P['armPitch'], 1))
    aim(pb['LowerArm.R'], arm_dir(P['armYaw'] - sg * P['elbow'], P['armPitch'] + fp, 1))
    aim(pb['Wrist.R'], arm_dir(P['armYaw'] - sg * P['elbow'] + wy, P['armPitch'] + fp - 5 + 0.5 * abs(wy), 1))
    off = 52 - 0.22 * P['armYaw']
    offp = min(P['armPitch'], 50)
    uy = mix(off, 24, tuck); up_ = mix(offp - 6, 58, tuck)
    ly = mix(off - 14, uy - 80, tuck); lp = mix(offp + 6, up_ - 62, tuck)
    aim(pb['UpperArm.L'], arm_dir(uy, up_, -1))
    aim(pb['LowerArm.L'], arm_dir(ly, lp, -1))
    aim(pb['Wrist.L'], arm_dir(ly - 6 * tuck, lp + 4, -1))
    # legs: planted feet, lead foot steps toward the target
    step = P['step']; wide = P.get('wide', 0.0); stride = P.get('stride', 0.0); trail = P.get('trail', 0.0)
    # athletic stance: feet shoulder-width or wider (hip width 0.24 -> ~0.46 apart at address, ~0.7 in the charge)
    sp = 0.09 + 0.09 * wide + 0.06 * min(step, 1.0)
    cross = P.get('cross', 0.0)
    tL = footL + Vector((sp, -0.18 * step - 0.10 * stride + P.get('lY', 0.0), 0.11 * math.sin(min(step, 1.0) * math.pi) + P.get('lZ', 0.0)))
    tR = footR + Vector((-sp * (1.0 - 0.45 * trail) + 0.0 * cross, 0.14 * wide + 0.10 * trail + 0.05 * cross + P.get('rY', 0.0), 0.20 * trail + P.get('rZ', 0.0)))
    ko = 0.45   # knees pushed outward over the toes
    two_bone_ik(arm, pb['UpperLeg.L'], pb['LowerLeg.L'], tL, out=ko)
    two_bone_ik(arm, pb['UpperLeg.R'], pb['LowerLeg.R'], tR, out=-ko)
    pb['Foot.L'].matrix = Matrix.Translation(tL - footL) @ pb['Foot.L'].matrix
    pb['Foot.R'].matrix = Matrix.Translation(tR - footR) @ pb['Foot.R'].matrix
    for nm, sgn in (('Foot.L', 1), ('Foot.R', -1)):   # toes turned out ~18 degrees
        rot_about(pb[nm], 'Z', sgn * D(18), pivot=pb[nm].head.copy())
    upd()

def key(arm, frame):
    for pb in arm.pose.bones:
        pb.keyframe_insert('rotation_quaternion', frame=frame)
        pb.keyframe_insert('location', frame=frame)

# ---------------------------------------------------------------- the motions (same numbers as the game's ThrowAnimation)
def smooth(x): x = max(0, min(1, x)); return x * x * (3 - 2 * x)
def mix(a, b, t): return a + (b - a) * t

ADDR = dict(armYaw=8, elbow=70, bodyYaw=0, hipYaw=0, lean=22, armPitch=62, crouch=0.95, wide=0.7, forePitch=-50,
            offTuck=1.0, wristYaw=0, cross=0.0, hc=0.3)
WIND = {True: dict(armYaw=112, elbow=80, bodyYaw=44, hipYaw=24, lean=14, armPitch=48, crouch=1.0, wide=1.0, forePitch=-10,
                   offTuck=0.6, wristYaw=-10, cross=0.0, hc=0.5),
        False: dict(armYaw=-85, elbow=45, bodyYaw=-46, hipYaw=-26, lean=18, armPitch=55, crouch=1.1, wide=1.0, forePitch=-25,
                    offTuck=0.3, wristYaw=-10, cross=1.0, hc=0.6)}

def windup(forehand, power):
    p = smooth(power)
    W = WIND[forehand]
    P = {k: mix(ADDR[k], W[k], p) for k in ADDR}
    P['step'] = 0.0
    P['drive'] = -0.05 * p   # weight loads onto the back foot
    return P

def idle_pose(bh, f, n=60):
    """Gentle breathing about the address pose: chest rises, tiny knee give, arm hangs."""
    P = windup(False, 0.0)
    s = math.sin(2 * math.pi * f / n)
    P['lean'] += 1.6 * s; P['crouch'] += 0.04 * s; P['armPitch'] += 1.0 * s
    P['sway'] = 0.012 * math.sin(2 * math.pi * f / n + 1.0); P['bob'] = 0.008 * s; P['bodyYaw'] += 1.5 * math.sin(2 * math.pi * f / n + 2.0); P['hipYaw'] += 0.8 * math.sin(2 * math.pi * f / n + 2.0)
    return P


# ---------------------------------------------------------------- run-up, cheer, groan
def kf(keys, f, lo=None):
    """Catmull-Rom through (frame, value) keys."""
    i = 0
    while i < len(keys) - 2 and f > keys[i + 1][0]: i += 1
    a, b = keys[i], keys[i + 1]; p0 = keys[max(i - 1, 0)]; p3 = keys[min(i + 2, len(keys) - 1)]
    u = max(0.0, min(1.0, (f - a[0]) / (b[0] - a[0])))
    y0, y1, y2, y3 = p0[1], a[1], b[1], p3[1]
    v = 0.5 * ((2 * y1) + (-y0 + y2) * u + (2 * y0 - 5 * y1 + 4 * y2 - y3) * u * u + (-y0 + 3 * y1 - 3 * y2 + y3) * u ** 3)
    return max(lo, v) if lo is not None else v

RUN_N = 30   # run-up clips are 30 frames; their motion lives in motions.py / swing.py

def cheer_pose(f, n=36):
    """Fist pump: rise out of the stance, throwing fist punched up twice, small hop, settle back to address."""
    P = windup(False, 0.0); t = f / (n - 1)
    up = smooth(t / 0.2) * (1 - smooth((t - 0.8) / 0.2))
    pump = 0.5 - 0.5 * math.cos(2 * math.pi * 2 * min(1, max(0, (t - 0.2) / 0.6)))  # two pumps
    P['crouch'] = mix(0.95, 0.15, up) + 0.25 * pump * up
    P['lean'] = mix(22, -4, up); P['armYaw'] = mix(8, 22, up); P['armPitch'] = mix(62, -30, up)
    P['elbow'] = mix(70, 15, up); P['forePitch'] = mix(-50, 25 - 80 * pump, up)
    P['offTuck'] = mix(1.0, 0.9, up); P['hc'] = mix(0.3, -0.2, up); P['wide'] = mix(0.7, 0.5, up)
    P['bodyYaw'] = 8 * up * math.sin(2 * math.pi * 2 * t); P['hipYaw'] = 0.5 * P['bodyYaw']
    P['bob'] = 0.06 * pump * up
    P['rZ'] = 0.10 * pump * up; P['lZ'] = 0.10 * pump * up
    return P

def groan_pose(f, n=40):
    """Frustrated: shoulders slump, throwing arm drops, head shaken side to side."""
    P = windup(False, 0.0); t = f / (n - 1)
    d = smooth(t / 0.2) * (1 - smooth((t - 0.85) / 0.15))
    P['crouch'] = mix(0.95, 0.35, d); P['lean'] = mix(22, 36, d); P['armPitch'] = mix(62, 85, d)
    P['elbow'] = mix(70, 5, d); P['forePitch'] = mix(-50, 0, d); P['offTuck'] = mix(1.0, -0.4, d); P['hc'] = mix(0.3, -0.3, d)
    P['hy'] = 50 * d * math.sin(2 * math.pi * 3 * min(1, max(0, (t - 0.15) / 0.7)))
    P['bodyYaw'] = 0; P['sway'] = 0.0
    return P
