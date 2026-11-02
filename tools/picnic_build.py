#!/usr/bin/env python3
"""Build app/src/main/assets/courses/picnic-island.json from
  * tools/picnic_udisc_raw.txt  (tee/basket/dogleg lat-lon + par + length, copied from the UDisc course-map page data)
  * tools/picnic_spec.py        (hand-read canopy/scrub/water shapes, in screenshot pixels, per hole view)

Usage: python3 tools/picnic_build.py [--out PATH]
Local frame: origin lat 27.8548 lon -82.5520, x east, z north, metres (equirectangular, R=6371008.8).
"""
import json, math, random, re, sys, os
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from picnic_spec import SPEC

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
OUT = os.path.join(ROOT, 'app/src/main/assets/courses/picnic-island.json')

R = 6371008.8; LAT0 = 27.8548; LON0 = -82.5520; K = math.pi / 180
def ll2xz(lat, lon): return (R * (lon - LON0) * K * math.cos(LAT0 * K), R * (lat - LAT0) * K)

# ---- screenshot pixel -> world (map pane centre (868,367); screenshot is 0.6613 of css px) -------------
S_CSS = 0.6613; CX_CSS, CY_CSS = 1058.5, 491.0
def px2xz(view, u, v):
    cx, cz, zoom, bearing = view
    mpp = 78271.517 * math.cos(LAT0 * K) / (2 ** zoom)
    right = ((u - 168) / S_CSS - CX_CSS) * mpp
    up = -(((v - 42) / S_CSS) - CY_CSS) * mpp
    b = bearing * K
    return (cx + right * math.cos(b) + up * math.sin(b), cz - right * math.sin(b) + up * math.cos(b))
def mpp_px(view): return 78271.517 * math.cos(LAT0 * K) / (2 ** view[2]) / S_CSS

# ---- raw UDisc data ---------------------------------------------------------------------------------
def parse_raw():
    holes = {}
    for line in open(os.path.join(HERE, 'picnic_udisc_raw.txt')):
        n, T, B, P = line.strip().split('|')
        n = int(n)
        tees = {}
        for e in T[2:].split(';'):
            lab, ll = e.split(' ', 1)
            la, lo = map(float, ll.split(','))
            tees[{'Sh': 'Short Orange', 'Lo': 'Long Blue'}[lab]] = ll2xz(la, lo)
        bas = {}
        for e in B[2:].split(';'):
            lab, ll = e.split(' ', 1)
            la, lo = map(float, ll.split(','))
            bas[lab] = ll2xz(la, lo)
        paths = []
        for e in P[2:].split(';'):
            m = re.match(r'([SL])(Blue|A|B) par(\d) (\d+)m(?: DL(.*))?$', e)
            t, b, par, dist, dl = m.groups()
            dls = []
            if dl:
                for pt in dl.split('/'):
                    la, lo = map(float, pt.split(','))
                    dls.append(ll2xz(la, lo))
            paths.append(dict(tee={'S': 'Short Orange', 'L': 'Long Blue'}[t], basket=b, par=int(par), dist=int(dist), dl=dls))
        holes[n] = dict(tees=tees, baskets=bas, paths=paths)
    return holes

# ---- geometry helpers -------------------------------------------------------------------------------
def dseg(p, a, b):
    ax, az = a; bx, bz = b; px, pz = p
    dx, dz = bx - ax, bz - az
    L2 = dx * dx + dz * dz
    t = 0 if L2 == 0 else max(0, min(1, ((px - ax) * dx + (pz - az) * dz) / L2))
    return math.hypot(px - (ax + t * dx), pz - (az + t * dz))
def dpoly(p, pl): return min(dseg(p, pl[i], pl[i + 1]) for i in range(len(pl) - 1))
def inpoly(p, poly):
    x, z = p; c = False; n = len(poly)
    for i in range(n):
        x1, z1 = poly[i]; x2, z2 = poly[(i + 1) % n]
        if (z1 > z) != (z2 > z) and x < (x2 - x1) * (z - z1) / (z2 - z1) + x1: c = not c
    return c
def hausdorff_v(A, B):
    return max(max(dpoly(p, B) for p in A), max(dpoly(p, A) for p in B))

# ---- via selection ----------------------------------------------------------------------------------
def choose_variants(h, thresh=8.0):
    """Group the (tee,basket) paths into hole variants that share one 'via' list each."""
    paths = h['paths']
    def poly(p): return [h['tees'][p['tee']]] + p['dl'] + [h['baskets'][p['basket']]]
    keys = {}
    for p in paths: keys.setdefault(tuple(p['dl']), []).append(p)
    cands = [list(k) for k in keys]
    best = None
    for v in cands:
        cost = max(hausdorff_v(poly(p), [h['tees'][p['tee']]] + v + [h['baskets'][p['basket']]]) for p in paths)
        if best is None or cost < best[0]: best = (cost, v)
    if best[0] <= thresh:
        return [dict(via=best[1], paths=paths, dev=best[0])]
    # split: one variant per distinct dogleg list
    out = []
    for k, ps in keys.items():
        out.append(dict(via=list(k), paths=ps, dev=0.0))
    # merge variants whose straight/bent lines are within thresh of each other
    merged = []
    for var in out:
        for m in merged:
            c = max(hausdorff_v(poly(p), [h['tees'][p['tee']]] + m['via'] + [h['baskets'][p['basket']]]) for p in var['paths'])
            if c <= thresh:
                m['paths'] += var['paths']; m['dev'] = max(m['dev'], c); break
        else: merged.append(var)
    return merged

# ---- Poisson-disc style filling ---------------------------------------------------------------------
def fill(poly, spacing, rng, existing, accept):
    xs = [p[0] for p in poly]; zs = [p[1] for p in poly]
    pts = []
    n = int(((max(xs) - min(xs)) * (max(zs) - min(zs))) / (spacing * spacing) * 6) + 50
    for _ in range(n):
        p = (rng.uniform(min(xs), max(xs)), rng.uniform(min(zs), max(zs)))
        if not inpoly(p, poly) or not accept(p): continue
        if any(math.hypot(p[0] - q[0], p[1] - q[1]) < spacing * 0.92 for q in existing + pts): continue
        pts.append(p)
    return pts

def tree_h(r, rng): return round(2.6 * r + 2.4 + rng.uniform(0, 2.5), 1)

CORRIDOR = 32.0; TREE_CAP = 90; BUSH_CAP = 60

def build_vegetation(n, h, variants):
    spec = SPEC[n]; view = spec['view']; rng = random.Random(7000 + n * 31)
    lines = []
    for var in variants:
        for p in var['paths']: lines.append([h['tees'][p['tee']]] + var['via'] + [h['baskets'][p['basket']]])
    pins = list(h['tees'].values()) + list(h['baskets'].values())
    clear = spec.get('clear', 2.0)
    opens = [[px2xz(view, *q) for q in poly] for poly in spec.get('open', [])]
    def dline(p): return min(dpoly(p, L) for L in lines)
    def ok_tree(r):
        def f(p):
            if dline(p) > CORRIDOR or dline(p) < clear + 0.3 * r: return False
            if any(math.hypot(p[0] - q[0], p[1] - q[1]) < 3.0 + r for q in pins): return False
            if any(inpoly(p, o) for o in opens): return False
            return True
        return f
    trees = []; centres = []
    for poly_px, sp, rmin, rmax in spec.get('forest', []):
        poly = [px2xz(view, *q) for q in poly_px]
        rr = (rmin + rmax) / 2
        for p in fill(poly, sp, rng, centres, ok_tree(rr)):
            r = round(rng.uniform(rmin, rmax), 1)
            if ok_tree(r)(p):
                centres.append(p); trees.append([round(p[0], 1), round(p[1], 1), tree_h(r, rng), r])
    for u, v, rpx in spec.get('trees', []):
        p = px2xz(view, u, v); r = round(max(1.5, rpx * mpp_px(view)), 1)
        if dline(p) <= CORRIDOR + 8 and not any(math.hypot(p[0] - q[0], p[1] - q[1]) < 3.0 + r for q in pins):
            trees.append([round(p[0], 1), round(p[1], 1), tree_h(r, rng), r])
    trees.sort(key=lambda t: dline((t[0], t[1])))
    dropped = max(0, len(trees) - TREE_CAP); trees = trees[:TREE_CAP]
    bushes = []; bc = []
    for poly_px, sp in spec.get('scrub', []):
        poly = [px2xz(view, *q) for q in poly_px]
        for p in fill(poly, sp, rng, bc, lambda p: dline(p) <= CORRIDOR and dline(p) >= 1.5 and not any(math.hypot(p[0]-q[0], p[1]-q[1]) < 2.5 for q in pins)):
            bc.append(p); bushes.append([round(p[0], 1), round(p[1], 1), round(rng.uniform(0.9, 1.5), 1)])
    bushes.sort(key=lambda t: dline((t[0], t[1]))); bushes = bushes[:BUSH_CAP]
    # shore -> chain of circular ponds (UDisc/Mapbox waterline read in px)
    ponds = []
    for pl_px, inland_px in spec.get('shore', []):
        pl = [px2xz(view, *q) for q in pl_px]; inl = px2xz(view, *inland_px)
        total = sum(math.dist(pl[i], pl[i + 1]) for i in range(len(pl) - 1)); step = 22.0; s = 0.0
        while s <= total:
            acc = 0.0
            for i in range(len(pl) - 1):
                L = math.dist(pl[i], pl[i + 1])
                if acc + L >= s:
                    t = (s - acc) / L; p = (pl[i][0] + t * (pl[i + 1][0] - pl[i][0]), pl[i][1] + t * (pl[i + 1][1] - pl[i][1]))
                    dx, dz = pl[i + 1][0] - pl[i][0], pl[i + 1][1] - pl[i][1]; nl = math.hypot(dx, dz)
                    nx, nz = -dz / nl, dx / nl
                    if (p[0] + nx - inl[0]) ** 2 + (p[1] + nz - inl[1]) ** 2 < (p[0] - inl[0]) ** 2 + (p[1] - inl[1]) ** 2: nx, nz = -nx, -nz
                    rad = 30.0; c = (p[0] + nx * rad, p[1] + nz * rad)
                    # keep pins dry: push outward until every pin is >= 3 m outside
                    for _ in range(40):
                        if min(math.dist(c, q) for q in pins) - rad >= 3.0: break
                        c = (c[0] + nx * 1.0, c[1] + nz * 1.0)
                    ponds.append([round(c[0], 1), round(c[1], 1), rad, rad]); break
                acc += L
            s += step
    return trees, bushes, ponds, dropped

# ---- terrain -----------------------------------------------------------------------------------------
# west (bay) waterline read from a z16.9 overview (x,z metres); land east of it
SHORE = [(-215, -450), (-215, -300), (-208, -270), (-178, -211), (-140, -160), (-105, -125), (-80, -82), (-84, -31),
         (-62, 38), (-33, 98), (-15, 166), (32, 243), (60, 300), (75, 450)]
def shore_x(z):
    for i in range(len(SHORE) - 1):
        (x1, z1), (x2, z2) = SHORE[i], SHORE[i + 1]
        if z1 <= z <= z2: return x1 + (x2 - x1) * (z - z1) / (z2 - z1)
    return SHORE[0][0] if z < SHORE[0][1] else SHORE[-1][0]
def sstep(a, b, x):
    t = max(0.0, min(1.0, (x - a) / (b - a))); return t * t * (3 - 2 * t)
def make_terrain():
    x0, z0, cell, nx, nz = -300.0, -450.0, 5.0, 140, 180
    hs = []
    for iz in range(nz):
        z = z0 + iz * cell
        for ix in range(nx):
            x = x0 + ix * cell
            land = sstep(-6, 10, x - shore_x(z))
            hs.append(round(-0.3 + 1.8 * land, 2))
    return dict(x0=x0, z0=z0, cell=cell, nx=nx, nz=nz, heights=hs)

# ---- main --------------------------------------------------------------------------------------------
TEE_PAIRS = [('Short Orange', 'A', 'Orange Pads / Orange Baskets', 'Short orange pads, orange (A) baskets: the UDisc layout of the same name (par 57, 4,075 ft). Hole 3, 7-11, 15 have no A path on UDisc, so the B pin is used there.'),
             ('Long Blue', 'A', 'Blue Pads / Orange Baskets', 'Long blue pads to the orange (A) baskets (par 57, 5,495 ft, from UDisc path lengths; B pin where no A path exists).'),
             ('Short Orange', 'Blue', 'Orange Pads / Blue Baskets', 'Short orange pads to the blue baskets (par 61, 5,594 ft).'),
             ('Long Blue', 'Blue', 'Blue Pads / Blue Baskets', 'Full length: long blue pads to blue baskets (par 61, 7,024 ft).')]

def main():
    out_path = OUT
    if '--out' in sys.argv: out_path = sys.argv[sys.argv.index('--out') + 1]
    raw = parse_raw()
    holes_json = []; layout_map = {}   # (hole n, tee, basketkey) -> (hole number in file, basket name, par)
    report = []
    for n in sorted(raw):
        h = raw[n]; variants = choose_variants(h)
        # order variants so the one that holds the Short Orange / A (or B) path is the base hole number
        def pri(v): return 0 if any(p['tee'] == 'Short Orange' and p['basket'] in ('A', 'B') for p in v['paths']) else 1
        variants.sort(key=pri)
        trees_all = None
        for vi, var in enumerate(variants):
            num = n if vi == 0 else 100 * vi + n
            trees, bushes, ponds, dropped = build_vegetation(n, h, [var])
            spec = SPEC[n]
            tees = {k: [round(v[0], 1), round(v[1], 1)] for k, v in h['tees'].items()}
            used_b = {p['basket'] for p in var['paths']}
            bas = {k: [round(v[0], 1), round(v[1], 1)] for k, v in h['baskets'].items() if k in used_b}
            hole = dict(number=num, name=f"Hole {n}" + ("" if vi == 0 else f" (alt line {vi})"), fairwayWidth=spec['fw'], tees=tees, baskets=bas)
            if var['via']: hole['via'] = [[round(v[0], 1), round(v[1], 1)] for v in var['via']]
            hole['trees'] = trees
            if bushes: hole['bushes'] = bushes
            if ponds: hole['ponds'] = ponds
            holes_json.append(hole)
            for p in var['paths']: layout_map[(n, p['tee'], p['basket'])] = (num, p['par'], p['dist'])
            report.append((n, num, len(trees), len(bushes), len(ponds), dropped, round(var['dev'], 1), len(var['via'])))
    layouts = []
    for tee, bk, name, desc in TEE_PAIRS:
        hl = []; par = 0; ft = 0
        for n in sorted(raw):
            key = (n, tee, bk)
            b = bk
            if key not in layout_map:
                b = 'B'; key = (n, tee, 'B')
            num, p, d = layout_map[key]
            hl.append(dict(hole=num, tee=tee, basket=b, par=p)); par += p; ft += d * 3.28084
        layouts.append(dict(name=name, description=f"{desc} UDisc path lengths sum to {round(ft):,} ft, par {par}.", holes=hl))
    course = dict(id='picnic-island', name='Picnic Island Disc Golf', location='Tampa, FL', terrain=make_terrain(), holes=holes_json, layouts=layouts)
    with open(out_path, 'w') as f: json.dump(course, f, separators=(',', ':'))
    print('wrote', out_path, os.path.getsize(out_path), 'bytes')
    print('hole variant trees bushes ponds dropped dev via')
    for r in report: print(*r)
if __name__ == '__main__': main()
