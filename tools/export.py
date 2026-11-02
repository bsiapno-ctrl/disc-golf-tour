import sys; sys.path.insert(0,'.')
from rig import *
import os
OUT = "/home/claude/wt/anim10/app/src/main/assets/characters"
os.makedirs(OUT, exist_ok=True)
chars = sys.argv[sys.argv.index("--")+1:] if "--" in sys.argv else sys.argv[1:]
FPS = 30
BH, FH = True, False   # data dict keyed True = reach out and back to the throwing side (a backhand); False = across the body (a forehand)
RELEASE_T = 0.26 / 0.70

def build_actions(arm):
    for a in list(bpy.data.actions): bpy.data.actions.remove(a)
    def clip(name, frames, off=0):
        frames = [(fr + off, P) for fr, P in frames]
        act = bpy.data.actions.new(name); arm.animation_data.action = act
        for fr, P in frames:
            pose(arm, P); key(arm, fr)
        arm.animation_data.action = None
        tr = arm.animation_data.nla_tracks.new(); tr.name = name
        st = tr.strips.new(name, int(frames[0][0]), act); st.extrapolation = 'NOTHING'; st.blend_type = 'REPLACE'
    clip("Windup_BH", [(1, windup(BH, 0)), (2, windup(BH, 1))])
    clip("Windup_FH", [(1, windup(FH, 0)), (2, windup(FH, 1))])
    clip("Idle", [(1 + i, idle_pose(True, i)) for i in range(60)])
    n = 22
    clip("Throw_BH", [(1 + i, swing_pose(BH, i / (n - 1), RELEASE_T)) for i in range(n)])
    clip("Runup_BH", [(1 + i, runup_pose(BH, i)) for i in range(RUN_N)], 200)
    clip("Runup_FH", [(1 + i, runup_pose(FH, i)) for i in range(RUN_N)], 300)
    clip("Runup", [(1 + i, runup_pose(BH, i)) for i in range(RUN_N)], 400)
    clip("Cheer", [(1 + i, cheer_pose(i)) for i in range(36)], 500)
    clip("Groan", [(1 + i, groan_pose(i)) for i in range(40)], 600)
    clip("Throw_FH", [(1 + i, swing_pose(FH, i / (n - 1), RELEASE_T)) for i in range(n)])

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
    build_actions(arm)
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

    # The exporter's very first sample of every clip is a stray (un-posed) frame; for the clips added after Throw/Windup
    # overwrite it with the true first pose so a clip never starts with a one-frame glitch.
    import json, numpy as np
    g = json.load(open(f"{OUT}/{ch}.gltf")); raw = bytearray(open(f"{OUT}/{ch}.bin", "rb").read())
    for an in g["animations"]:
        if an["name"] not in ("Runup", "Runup_BH", "Runup_FH", "Cheer", "Groan"): continue
        for c in an["channels"]:
            a = g["accessors"][an["samplers"][c["sampler"]]["output"]]; bv = g["bufferViews"][a["bufferView"]]
            n = {"SCALAR": 1, "VEC3": 3, "VEC4": 4}[a["type"]]; off = bv.get("byteOffset", 0) + a.get("byteOffset", 0)
            arr = np.frombuffer(raw, dtype="<f4", count=a["count"] * n, offset=off).reshape(a["count"], n)
            arr = arr.copy(); arr[0] = arr[1]; raw[off:off + arr.nbytes] = arr.tobytes()
    open(f"{OUT}/{ch}.bin", "wb").write(bytes(raw))
