import re, json
base='../../app/src/main/java/com/discgolf/game/gfx/'
consts={}
for m in re.finditer(r'const val (\w+) = ([\d.]+)f', open(base+'GlUtil.kt').read()):
    if m.group(1) in ('FOG_START','FOG_END','HORIZON_R','HORIZON_G','HORIZON_B'): consts[m.group(1)]=m.group(2) if '.' in m.group(2) else m.group(2)+'.0'
out={}
for f in ['Sky.kt','TerrainRender.kt','GameRenderer.kt','Models.kt','Skinned.kt']:
    s=open(base+f).read()
    for m in re.finditer(r'(?:private )?const val (\w+) = """(.*?)"""', s, re.S):
        name,body=m.group(1),m.group(2)
        for k,v in consts.items(): body=body.replace('$'+k,v)
        assert '$' not in body, (f,name)
        out[f+':'+name]=body
json.dump(out,open('shaders.json','w'))
print(list(out))
