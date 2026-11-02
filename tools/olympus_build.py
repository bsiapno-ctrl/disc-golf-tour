#!/usr/bin/env python3
"""Build app/src/main/assets/courses/olympus-disc-golf-course.json from tools/olympus_spec.py.

Geometry: UDisc pins (lat/lon) -> local metres (origin lat 28.5565, lon -82.3090; x east, z north).
Vegetation + ground: procedural, steered by what the Jomez DiscGolf 2026 Supreme Flight Open videos show (see docs/COURSE_SOURCES.md).
Holes 1-18 are the MPO (Gold tee -> Blue basket) lines, with a tee slid along the line when the broadcast length differs from UDisc's.
Holes 101-118 (not 106) are the Red-tee -> Red-basket lines for the 'Red to Red' layout.
Usage: python3 tools/olympus_build.py [--out PATH]
"""
import json, math, os, random, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from olympus_spec import RAW, STYLE, PAR_MPO, PAR_RED, OFFSET

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, 'app/src/main/assets/courses/olympus-disc-golf-course.json')
R = 6371008.8; K = math.pi / 180; LAT0 = 28.5565; LON0 = -82.3090

def ll(a, b):
    lat = 28.55 + a * 1e-5; lon = -82.30 + b * 1e-5
    return (R * (lon - LON0) * K * math.cos(LAT0 * K), R * (lat - LAT0) * K)

def plen(pl): return sum(math.dist(pl[i], pl[i + 1]) for i in range(len(pl) - 1))
def dseg(p, a, b):
    dx, dz = b[0] - a[0], b[1] - a[1]; L2 = dx * dx + dz * dz
    t = 0 if L2 == 0 else max(0, min(1, ((p[0] - a[0]) * dx + (p[1] - a[1]) * dz) / L2))
    return math.hypot(p[0] - a[0] - t * dx, p[1] - a[1] - t * dz), t
def along(pl, p):
    """(distance to polyline, u 0..1 along it, signed lateral metres (+ = right of travel))"""
    tot = plen(pl); best = (1e9, 0, 0); acc = 0
    for i in range(len(pl) - 1):
        a, b = pl[i], pl[i + 1]; d, t = dseg(p, a, b); L = math.dist(a, b)
        if d < best[0]:
            dx, dz = (b[0] - a[0]) / L, (b[1] - a[1]) / L
            side = (p[0] - a[0]) * dz - (p[1] - a[1]) * dx  # right of travel = (dz,-dx)
            best = (d, (acc + t * L) / tot, side)
        acc += L
    return best
def at(pl, u, v=0.0):
    tot = plen(pl); s = max(0.0, min(1.0, u)) * tot; acc = 0
    for i in range(len(pl) - 1):
        a, b = pl[i], pl[i + 1]; L = math.dist(a, b)
        if acc + L >= s or i == len(pl) - 2:
            t = (s - acc) / L; dx, dz = (b[0] - a[0]) / L, (b[1] - a[1]) / L
            return (a[0] + (b[0] - a[0]) * t + dz * v, a[1] + (b[1] - a[1]) * t - dx * v)
        acc += L
def dpoly(p, pl): return min(dseg(p, pl[i], pl[i + 1])[0] for i in range(len(pl) - 1))

# ---- lines --------------------------------------------------------------------------------------------
def build_line(n, key):
    r = RAW[n][key]
    return [ll(*r[0])] + [ll(*q) for q in r[1]] + [ll(*r[2])]

def mpo_line(n):
    pl = build_line(n, 'mpo'); vid = RAW[n]['vid']
    if vid:
        diff = vid - plen(pl)
        if abs(diff) > 0.03 * vid:  # slide the tee along the first leg (extend back / shorten forward)
            a, b = pl[0], pl[1]; L = math.dist(a, b); ux, uz = (b[0] - a[0]) / L, (b[1] - a[1]) / L
            pl[0] = (a[0] - ux * diff, a[1] - uz * diff)
    return pl

# ---- trees --------------------------------------------------------------------------------------------
def tree_of(kind, rng):
    if kind == 'oak': r = rng.uniform(3.6, 5.2); return [0, 0, round(rng.uniform(14, 19), 1), round(r, 1)]
    if kind == 'pine': return [0, 0, round(rng.uniform(20, 26), 1), round(rng.uniform(2.0, 2.7), 1)]
    return [0, 0, round(rng.uniform(7, 11), 1), round(rng.uniform(2.2, 3.0), 1)]

def zone_factor(st, u, side):
    f = 1.0
    for (u0, u1, s, fac) in st.get('zones', []):
        if u0 <= u <= u1 and s in ('B', side): f = max(f, fac) if fac > 1 else min(f, fac)
    return f

def make_trees(n, line, st, seed, pins, named_abs, cap=82):
    rng = random.Random(seed)
    fw = st['fw']; trees = []
    # named trees (absolute positions from the MPO line)
    for p in named_abs:
        if dpoly(p[:2], line) <= 30 and dpoly(p[:2], line) > fw / 2 * 0.0 + p[3] * 0.4 + 1.5: trees.append(list(p))
    def ok(p, r, sp):
        d, u, v = along(line, p)
        if d < fw / 2 * 0.55 + 0.35 * r + 1.8: return False
        if any(math.dist(p, q) < r + 3.5 for q in pins): return False
        if any(math.dist(p, (t[0], t[1])) < sp * 0.9 for t in trees): return False
        return True
    xs = [q[0] for q in line]; zs = [q[1] for q in line]
    for side, key in (('R', 'R'), ('L', 'L')):
        sp, depth = st[key]
        for _ in range(60000):
            p = (rng.uniform(min(xs) - 40, max(xs) + 40), rng.uniform(min(zs) - 40, max(zs) + 40))
            d, u, v = along(line, p)
            if (v >= 0) != (side == 'R'): continue
            f = zone_factor(st, u, side)
            if f >= 90:
                inner = fw / 2 + 8
            else: inner = fw / 2 * 0.55
            if d < inner or d > inner + depth: continue
            sp_eff = sp * (f if f > 1 else f)
            if f > 1 and rng.random() > 1.0 / (f * f) : continue
            kind = 'oak' if rng.random() < st['oak'] else 'pine'
            t = tree_of(kind, rng)
            if ok(p, t[3], sp_eff * 0.9) and (f < 90 or d > inner):
                t[0], t[1] = round(p[0], 1), round(p[1], 1); trees.append(t)
    trees.sort(key=lambda t: dpoly((t[0], t[1]), line))
    return trees[:cap]

# ---- terrain ------------------------------------------------------------------------------------------
def make_terrain(profiles, bumps, bounds):
    cell = 5.0; x0, z0, x1, z1 = bounds
    nx = int((x1 - x0) / cell) + 1; nz = int((z1 - z0) / cell) + 1; hs = []
    for iz in range(nz):
        z = z0 + iz * cell
        for ix in range(nx):
            x = x0 + ix * cell; sw = 0.03; sh = 0.03 * 10.5; best = None
            for line, elev, sig in profiles:
                d, u, v = along(line, (x, z))
                w = math.exp(-(d / sig) ** 2) + 1e-9
                h = interp(elev, u)
                sw += w; sh += w * h
            h = sh / sw
            for (bx, bz, dh, br) in bumps:
                h += dh * math.exp(-((x - bx) ** 2 + (z - bz) ** 2) / (br * br))
            h += 0.25 * math.sin(x * 0.045 + 1.3) * math.cos(z * 0.05) + 0.15 * math.sin(z * 0.11 + x * 0.07)
            hs.append(round(h, 1))
    return dict(x0=x0, z0=z0, cell=cell, nx=nx, nz=nz, heights=hs)

def interp(elev, u):
    if u <= elev[0][0]: return elev[0][1]
    for (a, ha), (b, hb) in zip(elev, elev[1:]):
        if u <= b: return ha + (hb - ha) * (u - a) / (b - a) if b > a else hb
    return elev[-1][1]

# ---- main ---------------------------------------------------------------------------------------------
def main():
    out = OUT
    if '--out' in sys.argv: out = sys.argv[sys.argv.index('--out') + 1]
    holes = []; mpo_layout = []; red_layout = []; profiles = []; bumps = []; report = []
    for n in range(1, 19):
        st = STYLE[n]; raw = RAW[n]
        line = mpo_line(n); st_elev = st['elev']
        profiles.append((line, [(u, h + OFFSET[n]) for u, h in st_elev], max(34.0, st['fw'] * 1.5)))
        for (u, v, dh, br) in st.get('bumps', []):
            p = at(line, u, v); bumps.append((p[0], p[1], dh, br))
        named = []
        rng = random.Random(900 + n)
        for (u, v, kind, h, r) in st.get('trees', []):
            p = at(line, u, v); named.append([round(p[0], 1), round(p[1], 1), h, r])
        bushes_abs = []
        for (u, v, r) in st.get('bushes', []):
            p = at(line, u, v); bushes_abs.append([round(p[0], 1), round(p[1], 1), r])
        def pack(num, nm, tees, baskets, via, ln, seedshift):
            pins = list(tees.values()) + list(baskets.values())
            trees = make_trees(n, ln, st, 4000 + n * 17 + seedshift, pins, named, 82)
            if via:  # close the inside corner: two oaks on the straight tee-basket chord, wherever the bent line leaves it
                for f in (.4, .6):
                    c = (ln[0][0] + (ln[-1][0] - ln[0][0]) * f, ln[0][1] + (ln[-1][1] - ln[0][1]) * f)
                    if dpoly(c, ln) > st['fw'] / 2 + 7 and not any(math.dist(c, (t[0], t[1])) < 5 for t in trees):
                        trees.append([round(c[0], 1), round(c[1], 1), 18.0, 4.4])
            bs = [b for b in bushes_abs if dpoly(b[:2], ln) <= 22 and min(math.dist(b[:2], q) for q in pins) > b[2] + 2.0]
            h = dict(number=num, name=nm, fairwayWidth=st['fw'],
                     tees={k: [round(v[0], 1), round(v[1], 1)] for k, v in tees.items()},
                     baskets={k: [round(v[0], 1), round(v[1], 1)] for k, v in baskets.items()})
            if via: h['via'] = [[round(v[0], 1), round(v[1], 1)] for v in via]
            h['trees'] = trees
            if bs: h['bushes'] = bs
            h['obstacleNotes'] = st['note']
            return h
        holes.append(pack(n, f'Hole {n}', {'MPO': line[0], 'Red': ll(*raw['red'][0]) if raw['red'] else line[0]},
                          {'Blue': line[-1], 'Red': ll(*raw['red'][2]) if raw['red'] else line[-1]}, line[1:-1], line, 0))
        mpo_layout.append(dict(hole=n, tee='MPO', basket='Blue', par=PAR_MPO[n]))
        if raw['red']:
            rl = build_line(n, 'red')
            holes.append(pack(100 + n, f'Hole {n} (Red line)', {'Red': rl[0]}, {'Red': rl[-1]}, rl[1:-1], rl, 5))
            red_layout.append(dict(hole=100 + n, tee='Red', basket='Red', par=PAR_RED[n]))
        else:
            red_layout.append(dict(hole=n, tee='Red', basket='Red', par=PAR_RED[n]))
        report.append((n, plen(line), raw['vid'], len(holes[-1]['trees'])))
    pts = [q for h in holes for k in ('tees', 'baskets') for q in h[k].values()]
    bounds = (math.floor(min(p[0] for p in pts) / 5) * 5 - 70, math.floor(min(p[1] for p in pts) / 5) * 5 - 70,
              math.ceil(max(p[0] for p in pts) / 5) * 5 + 70, math.ceil(max(p[1] for p in pts) / 5) * 5 + 70)
    terrain = make_terrain(profiles, bumps, bounds)
    # 'Red' basket/tee in MPO holes are only present as extra pins; the MPO layout uses MPO/Blue
    course = dict(id='olympus-disc-golf-course', name='Olympus Disc Golf Course', location='Brooksville, FL', terrain=terrain, holes=holes,
                  layouts=[dict(name='MPO Supreme Flight Open', description="Gold tees to Blue baskets, 18 holes, par 66 (par and tournament lengths from the Jomez DiscGolf broadcast of the 2026 Supreme Flight Open; holes 14, 16 and 18 from UDisc).", holes=mpo_layout),
                           dict(name='Red to Red', description="UDisc 'Red to Red' (par 61): Red tees to Red baskets.", holes=red_layout)])
    json.dump(course, open(out, 'w'), separators=(',', ':'))
    print('wrote', out, os.path.getsize(out), 'bytes; terrain', terrain['nx'], 'x', terrain['nz'], 'height range', min(terrain['heights']), max(terrain['heights']))
    for n, L, vid, nt in report: print(f'hole {n:2d} line {L:6.1f} m  broadcast {vid}  trees {nt}')

if __name__ == '__main__': main()
