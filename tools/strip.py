"""Frame strips of a clip from the motion tables, for judging by eye.
usage: python3 strip.py OUTDIR CLIP [CHAR] [VIEW=game|top|side]
CLIP: Throw_BH, Throw_FH, Runup_BH, Runup_FH, Putt_Stand, Putt_Jump (+_FH).  Writes OUTDIR/<CLIP>_<VIEW>.png (a contact sheet)."""
import sys, os; sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from motions import *
from PIL import Image, ImageDraw
out, clip = sys.argv[1], sys.argv[2]
ch = sys.argv[3] if len(sys.argv) > 3 else 'Casual'
views = sys.argv[4].split(',') if len(sys.argv) > 4 else ['game', 'top']
os.makedirs(out, exist_ok=True)

def frames_of(name):
    if name.startswith('Throw_'): S = SETS[name[6:]]; return [at(S, T0 + i) for i in range(22)], 7.8
    if name.startswith('Runup_'): S = SETS[name[6:]]; return [at(S, i) for i in range(RUN_N)], None
    S, n, _ = PUTTS[name]; return [putt_at(S, i, n) for i in range(n)], PUTT_STAND_REL * (n - 1) if 'Stand' in name else PUTT_JUMP_REL * (n - 1)

arm = setup()
for o in bpy.data.objects:
    if o.type == 'MESH': o.hide_viewport = o.hide_render = not o.name.startswith(ch + '_')
sc = bpy.context.scene
sc.render.engine = 'BLENDER_WORKBENCH'; sc.render.resolution_x = 360; sc.render.resolution_y = 420
sc.display.shading.light = 'STUDIO'; sc.display.shading.color_type = 'MATERIAL'
sc.world.color = (0.55, 0.62, 0.5)
sc.display.shading.background_type = 'WORLD'
# ground disc marker + disc at hand
bpy.ops.mesh.primitive_cylinder_add(radius=0.105, depth=0.02); disc = bpy.context.object
camd = bpy.data.cameras.new('c'); cam = bpy.data.objects.new('c', camd); sc.collection.objects.link(cam); sc.camera = cam
def look(cam, loc, tgt):
    cam.location = loc; d = Vector(tgt) - Vector(loc)
    cam.rotation_euler = d.to_track_quat('-Z', 'Y').to_euler()
VIEWS = {'game': ((-1.4, 2.7, 1.45), (0, -0.4, 0.95), 'PERSP'), 'top': ((0, -0.3, 6.0), (0, -0.3, 0), 'ORTHO'), 'side': ((-4.5, -0.2, 1.2), (0, -0.2, 0.95), 'PERSP')}
Ps, rel = frames_of(clip)
idx = list(range(len(Ps))) if len(Ps) <= 12 else [round(i * (len(Ps) - 1) / 11) for i in range(12)]
if rel is not None and clip.startswith('Throw'): idx = [0, 3, 6, 8, 10, 12, 14, 16, 17, 18, 19, 20, 21][:12] if False else [0, 3, 6, 8, 10, 12, 14, 16, 18, 19, 20, 21]
for view in views:
    loc, tgt, kind = VIEWS[view]; look(cam, loc, tgt)
    camd.type = kind
    if kind == 'ORTHO': camd.ortho_scale = 3.0
    else: camd.lens = 40
    tiles = []
    for i in idx:
        pose2(arm, Ps[i]); bpy.context.view_layer.update()
        disc.matrix_world = Matrix.Translation(arm.pose.bones['Wrist.R'].tail + Vector((0, 0, 0))) ;
        path = f'/tmp/_f.png'; sc.render.filepath = path; bpy.ops.render.render(write_still=True)
        im = Image.open(path).convert('RGB'); d = ImageDraw.Draw(im)
        d.text((6, 6), f'{clip} f{i}' + (' REL' if rel is not None and abs(i - rel) < 1.0 else ''), fill=(255, 255, 255))
        tiles.append(im)
    cols = 6; rows = (len(tiles) + cols - 1) // cols
    W, H = tiles[0].size
    sheet = Image.new('RGB', (W * cols, H * rows))
    for k, t in enumerate(tiles): sheet.paste(t, ((k % cols) * W, (k // cols) * H))
    sheet.save(f'{out}/{clip}_{view}.png'); print('wrote', f'{out}/{clip}_{view}.png')
