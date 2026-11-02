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

def two_bone_ik(arm, up, lo, foot_target, knee_fwd=True):
    """Place thigh and shin so the ankle reaches foot_target; the knee bends toward the front."""
    hip = up.head.copy()
    L1 = (up.tail - up.head).length; L2 = (lo.tail - lo.head).length
    to = foot_target - hip
    dist = min(max(to.length, abs(L1 - L2) + 1e-3), L1 + L2 - 1e-3)
    to_n = to.normalized()
    a = math.acos(max(-1, min(1, (L1 * L1 + dist * dist - L2 * L2) / (2 * L1 * dist))))
    side = to_n.cross(Vector((1, 0, 0)))  # axis to swing the thigh forward
    # thigh direction: rotate to_n toward the front by angle a
    bend = Vector((0, -1, 0)) if knee_fwd else Vector((0, 1, 0))
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
    drop = 0.18 * P['crouch']
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
    rot_about(pb['Neck'], 'Z', D(total * 0.50))
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
    tL = footL + Vector((0.20 * wide, -0.30 * step - 0.20 * stride, 0.11 * math.sin(min(step, 1.0) * math.pi) + P.get('lZ', 0.0)))
    cross = P.get('cross', 0.0)
    tR = footR + Vector((-0.12 * wide + 0.30 * cross, 0.14 * wide + 0.10 * trail + 0.12 * cross + P.get('rY', 0.0), 0.20 * trail + P.get('rZ', 0.0)))
    two_bone_ik(arm, pb['UpperLeg.L'], pb['LowerLeg.L'], tL)
    two_bone_ik(arm, pb['UpperLeg.R'], pb['LowerLeg.R'], tR)
    pb['Foot.L'].matrix = Matrix.Translation(tL - footL) @ pb['Foot.L'].matrix
    pb['Foot.R'].matrix = Matrix.Translation(tR - footR) @ pb['Foot.R'].matrix
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
    return P

# key columns: t, armYaw, elbow, bodyYaw, hipYaw, step, lean, forePitch, wristYaw, offTuck, lag, cross, armPitch, crouch, headComp
def swing_keys(forehand, release):
    W = WIND[forehand]
    k0 = (0, W['armYaw'], W['elbow'], W['bodyYaw'], W['hipYaw'], 0.0, W['lean'], W['forePitch'], W['wristYaw'], W['offTuck'], 0.0, W['cross'], W['armPitch'], W['crouch'], W['hc'])
    if forehand:
        return [k0,
                (0.12, 100, 98, 44, -6, 0.65, 24, -10, -10, 0.9, 0.7, 0.0, 46, 1.4, 0.5),
                (0.24, 70, 80, 30, -32, 1.0, 24, -22, 0, 1.0, 1.0, 0.0, 42, 1.45, 0.55),
                (release, 16, 20, -8, -44, 1.0, 28, -4, 28, 0.2, 0.8, 0.0, 38, 1.4, 0.6),
                (0.52, -40, 16, -40, -46, 1.0, 28, 6, 34, -0.3, 0.2, 0.0, 36, 1.2, 0.6),
                (0.75, -60, 24, -42, -44, 1.0, 22, -6, 15, 0.0, 0.0, 0.0, 34, 0.9, 0.6),
                (1.0, -50, 30, -36, -36, 1.0, 10, -10, 5, 0.1, 0.0, 0.0, 32, 0.6, 0.5)]
    # Backhand, low flat sweep: disc taken back across the body at hip height, pulled across the front of the
    # waist (elbow bent), snapped out, then a long extended finish out to the right with a deep forward lean.
    return [k0,
            (0.12, -90, 40, -50, -20, 0.65, 22, -22, -6, 0.2, 0.6, 0.5, 58, 1.25, 0.65),
            (0.24, -45, 80, -34, 8, 1.0, 26, -45, 10, 0.0, 1.0, 0.0, 62, 1.5, 0.75),
            (release, 14, 28, 6, 34, 1.0, 30, -38, 34, -0.3, 0.9, 0.0, 60, 1.4, 0.8),
            (0.52, 70, 24, 20, 38, 1.0, 36, -20, 26, -0.5, 0.2, 0.0, 50, 1.25, 0.85),
            (0.75, 92, 30, 24, 32, 1.0, 40, -10, 8, -0.5, 0.0, 0.0, 36, 1.0, 0.85),
            (1.0, 100, 34, 28, 34, 1.0, 40, -12, 0, -0.3, 0.0, 0.0, 30, 0.85, 0.8)]

R_ = 0.26 / 0.70
# t, drive(m fwd), rYextra(neg=fwd), rZ lift, lZ lift, sway(m), bob(m)
EXTRA = {True: [(0, 0, 0, 0, 0, 0, 0), (0.12, 0.04, -0.12, 0.10, 0, -0.03, 0.0), (0.24, 0.16, -0.34, 0.06, 0.0, -0.04, -0.02),
                (R_, 0.26, -0.10, 0.0, 0, -0.02, 0.0), (0.52, 0.30, 0.10, 0.12, 0, 0.0, 0.03), (0.75, 0.26, 0.20, 0.27, 0, 0.01, 0.04), (1.0, 0.22, 0.18, 0.30, 0, 0.01, 0.03)],
         False: [(0, 0, 0, 0, 0, 0, 0), (0.12, 0.04, -0.10, 0.08, 0, 0.03, 0.0), (0.24, 0.16, -0.30, 0.05, 0.0, 0.04, -0.02),
                (R_, 0.26, -0.10, 0.0, 0, 0.02, 0.0), (0.52, 0.30, 0.10, 0.12, 0, 0.0, 0.03), (0.75, 0.26, 0.18, 0.24, 0, 0.0, 0.04), (1.0, 0.22, 0.16, 0.26, 0, 0.0, 0.03)]}

def swing_pose(forehand, t, release):
    keys = swing_keys(forehand, release)
    i = 0
    while i < len(keys) - 2 and t > keys[i + 1][0]: i += 1
    a, b = keys[i], keys[i + 1]
    p0 = keys[max(i - 1, 0)]; p3 = keys[min(i + 2, len(keys) - 1)]
    u = max(0.0, min(1.0, (t - a[0]) / (b[0] - a[0])))
    def cr(k):   # Catmull-Rom: smooth velocity through every key, no stop-start
        y0, y1, y2, y3 = p0[k], a[k], b[k], p3[k]
        return 0.5 * ((2 * y1) + (-y0 + y2) * u + (2 * y0 - 5 * y1 + 4 * y2 - y3) * u * u + (-y0 + 3 * y1 - 3 * y2 + y3) * u ** 3)
    v = [cr(k) for k in range(1, 15)]
    v[4] = max(0.0, min(1.0, v[4]))
    E = EXTRA[forehand]
    ex = {}
    j = 0
    while j < len(E) - 2 and t > E[j + 1][0]: j += 1
    ea, eb = E[j], E[j + 1]; e0 = E[max(j - 1, 0)]; e3 = E[min(j + 2, len(E) - 1)]
    w = max(0.0, min(1.0, (t - ea[0]) / (eb[0] - ea[0])))
    for n, k in (('drive', 1), ('rY', 2), ('rZ', 3), ('lZ', 4), ('sway', 5), ('bob', 6)):
        y0, y1, y2, y3 = e0[k], ea[k], eb[k], e3[k]
        ex[n] = 0.5 * ((2 * y1) + (-y0 + y2) * w + (2 * y0 - 5 * y1 + 4 * y2 - y3) * w * w + (-y0 + 3 * y1 - 3 * y2 + y3) * w ** 3)
    r = dict(armYaw=v[0], elbow=max(4.0, v[1]), bodyYaw=v[2], hipYaw=v[3], step=v[4], lean=v[5],
                forePitch=v[6], wristYaw=v[7], offTuck=max(-0.5, min(1.0, v[8])), lag=max(0.0, min(1.0, v[9])), cross=max(0.0, min(1.0, v[10])),
                armPitch=v[11], crouch=v[12], hc=v[13],
                shift=smooth((t - 0.04) / 0.28), wide=mix(1.0, 1.3, smooth(t / 0.3)), stride=smooth(t / 0.35), trail=smooth((t - 0.55) / 0.35))
    r.update(ex)
    return r

def idle_pose(bh, f, n=60):
    """Gentle breathing about the address pose: chest rises, tiny knee give, arm hangs."""
    P = windup(False, 0.0)
    s = math.sin(2 * math.pi * f / n)
    P['lean'] += 1.6 * s; P['crouch'] += 0.04 * s; P['armPitch'] += 1.0 * s
    P['sway'] = 0.012 * math.sin(2 * math.pi * f / n + 1.0); P['bob'] = 0.008 * s; P['bodyYaw'] += 1.5 * math.sin(2 * math.pi * f / n + 2.0); P['hipYaw'] += 0.8 * math.sin(2 * math.pi * f / n + 2.0)
    return P
