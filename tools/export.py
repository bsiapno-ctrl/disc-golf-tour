import sys, os; sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from rig import *
import motions as M
from motions import *  # noqa

OUT = os.environ.get("OUT") or os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "app/src/main/assets/characters")
ONLY = os.environ.get("ONLY")   # comma list of clip names, for quick iterations
ONLY = ONLY.split(",") if ONLY else None
os.makedirs(OUT, exist_ok=True)
chars = sys.argv[sys.argv.index("--")+1:] if "--" in sys.argv else sys.argv[1:]
FPS = 30
BH, FH = True, False   # data dict keyed True = reach out and back to the throwing side (a backhand); False = across the body (a forehand)
RELEASE_T = 0.26 / 0.70

def build_actions(arm):
    for a in list(bpy.data.actions): bpy.data.actions.remove(a)
    def clip(name, frames, off=0, solver=None):
        if ONLY and name not in ONLY: return
        solver = solver or pose
        frames = [(fr + off, P) for fr, P in frames]
        act = bpy.data.actions.new(name); arm.animation_data.action = act
        for fr, P in frames:
            solver(arm, P); key(arm, fr)
        arm.animation_data.action = None
        tr = arm.animation_data.nla_tracks.new(); tr.name = name
        st = tr.strips.new(name, int(frames[0][0]), act); st.extrapolation = 'NOTHING'; st.blend_type = 'REPLACE'
    n = 22
    clip("Idle", [(1 + i, idle_pose(True, i)) for i in range(60)])
    base = 0
    for sfx in ("BH", "FH"):
        S = M.SETS.get(sfx)
        if S is None: continue
        clip("Windup_" + sfx, [(1, M.windup(S, 0)), (2, M.windup(S, 1))], solver=pose2)
        clip("Throw_" + sfx, [(1 + i, M.at(S, M.T0 + i)) for i in range(n)], solver=pose2)
        clip("Runup_" + sfx, [(1 + i, M.at(S, i)) for i in range(RUN_N)], 200 if sfx == "BH" else 300, solver=pose2)
        if sfx == "BH": clip("Runup", [(1 + i, M.at(S, i)) for i in range(RUN_N)], 400, solver=pose2)
    clip("Cheer", [(1 + i, cheer_pose(i)) for i in range(36)], 500)
    clip("Groan", [(1 + i, groan_pose(i)) for i in range(40)], 600)
    for name, (S, nf, off) in M.PUTTS.items():
        clip(name, [(1 + i, M.putt_at(S, i, nf)) for i in range(nf)], off, solver=pose2)

for ch in chars:
    arm = setup()
    for o in list(bpy.data.objects):
        if o.type == 'MESH' and not o.name.startswith(ch + "_"): bpy.data.objects.remove(o, do_unlink=True)
    for m in bpy.data.materials:
        m.use_nodes = True
        nt = m.node_tree
        col = tuple(m.diffuse_color)
        nt.nodes.clear()
        out = nt.nodes.new('ShaderNodeOutputMaterial'); out.is_active_output = True
        bsdf = nt.nodes.new('ShaderNodeBsdfPrincipled')
        bsdf.inputs['Base Color'].default_value = col
        bsdf.inputs['Roughness'].default_value = 1.0
        nt.links.new(bsdf.outputs['BSDF'], out.inputs['Surface'])
    for o in bpy.data.objects:
        if o.type == 'MESH': o.hide_viewport = True   # keep the depsgraph light while posing
    build_actions(arm)
    for o in bpy.data.objects:
        if o.type == 'MESH': o.hide_viewport = False
    bpy.ops.object.mode_set(mode='OBJECT')
    bpy.ops.object.select_all(action='DESELECT')
    arm.select_set(True)
    for o in bpy.data.objects:
        if o.type == 'MESH': o.hide_set(False); o.select_set(True)
    bpy.context.view_layer.objects.active = arm
    bpy.context.scene.render.fps = FPS
    bpy.ops.export_scene.gltf(filepath=f"{OUT}/{ch}.gltf", export_format='GLTF_SEPARATE', use_selection=True,
        export_image_format='NONE', export_apply=True, export_animations=True, export_animation_mode='NLA_TRACKS',
        export_force_sampling=True, export_skins=True, export_yup=True, export_materials='EXPORT', export_cameras=False, export_lights=False)
    print("exported", ch)

    # The exporter's very first sample of every clip is a stray (un-posed) frame; overwrite it with the true first pose so a clip never starts with a one-frame glitch.
    import json, numpy as np
    g = json.load(open(f"{OUT}/{ch}.gltf")); raw = bytearray(open(f"{OUT}/{ch}.bin", "rb").read())
    for an in g["animations"]:
        for c in an["channels"]:
            a = g["accessors"][an["samplers"][c["sampler"]]["output"]]; bv = g["bufferViews"][a["bufferView"]]
            if a["count"] <= 2: continue   # 2-sample clips (Windup) are real data; the rotation/translation outputs of a pose clip with a stray first sample are longer
            n = {"SCALAR": 1, "VEC3": 3, "VEC4": 4}[a["type"]]; off = bv.get("byteOffset", 0) + a.get("byteOffset", 0)
            arr = np.frombuffer(raw, dtype="<f4", count=a["count"] * n, offset=off).reshape(a["count"], n)
            arr = arr.copy(); arr[0] = arr[1]; raw[off:off + arr.nbytes] = arr.tobytes()
    open(f"{OUT}/{ch}.bin", "wb").write(bytes(raw))
