import sys; sys.path.insert(0,'.')
from rig import *
import os
OUT = "/home/claude/wt/anim9/app/src/main/assets/characters"
os.makedirs(OUT, exist_ok=True)
chars = sys.argv[sys.argv.index("--")+1:] if "--" in sys.argv else sys.argv[1:]
FPS = 30
BH, FH = True, False   # data dict keyed True = reach out and back to the throwing side (a backhand); False = across the body (a forehand)
RELEASE_T = 0.26 / 0.70

def build_actions(arm):
    for a in list(bpy.data.actions): bpy.data.actions.remove(a)
    def clip(name, frames):
        act = bpy.data.actions.new(name); arm.animation_data.action = act
        for fr, P in frames:
            pose(arm, P); key(arm, fr)
        arm.animation_data.action = None
        tr = arm.animation_data.nla_tracks.new(); tr.name = name
        tr.strips.new(name, int(frames[0][0]), act)
    clip("Windup_BH", [(1, windup(BH, 0)), (2, windup(BH, 1))])
    clip("Windup_FH", [(1, windup(FH, 0)), (2, windup(FH, 1))])
    clip("Idle", [(1 + i, idle_pose(True, i)) for i in range(60)])
    n = 22
    clip("Throw_BH", [(1 + i, swing_pose(BH, i / (n - 1), RELEASE_T)) for i in range(n)])
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
