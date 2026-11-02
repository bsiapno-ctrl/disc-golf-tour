#!/usr/bin/env python3
"""Validate picnic-island.json: structure, UDisc pins/lengths/pars, trees near the line, counts per hole.  Exit 1 on failure."""
import json, math, os, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from picnic_build import OUT, parse_raw, dpoly
d = json.load(open(OUT)); raw = parse_raw(); fails = []; warn = []
def fail(m): fails.append(m)
T = d['terrain']; assert len(T['heights']) == T['nx'] * T['nz'], 'terrain size'
holes = {h['number']: h for h in d['holes']}
print(f"{'hole':>5} {'trees':>5} {'bush':>4} {'pond':>4} {'maxd':>5} {'meand':>5} {'minpin':>6}  lines")
for num, h in sorted(holes.items()):
    n = num % 100; r = raw[n]
    for k, v in h['tees'].items():
        if math.dist(v, r['tees'][k]) > 0.15: fail(f'hole {num} tee {k} off UDisc pin')
    for k, v in h['baskets'].items():
        if math.dist(v, r['baskets'][k]) > 0.15: fail(f'hole {num} basket {k} off UDisc pin')
    if len(h['trees']) < 8: warn.append(f'hole {num}: only {len(h["trees"])} trees (open hole?)')
    if len(h['trees']) > 120: fail(f'hole {num}: too many trees')
    lines = [[h['tees'][t]] + h.get('via', []) + [h['baskets'][b]] for t in h['tees'] for b in h['baskets']]
    ds = [min(dpoly((t[0], t[1]), L) for L in lines) for t in h['trees']] or [0]
    pins = list(h['tees'].values()) + list(h['baskets'].values())
    mp = min([math.dist((t[0], t[1]), p) - t[3] for t in h['trees'] for p in pins] or [99])
    if max(ds) > 45: fail(f'hole {num}: tree {max(ds):.0f} m from every line')
    if mp < 1.5: fail(f'hole {num}: tree canopy within {mp:.1f} m of a tee/basket')
    for t in h['trees']:
        if not (3 <= t[2] <= 30 and 1 <= t[3] <= 8): fail(f'hole {num}: odd tree {t}')
    for p in h.get('ponds', []):
        for q in pins:
            if math.hypot((q[0]-p[0])/p[2], (q[1]-p[1])/p[3]) < 1.0: fail(f'hole {num}: pond covers a pin')
    print(f"{num:>5} {len(h['trees']):>5} {len(h.get('bushes', [])):>4} {len(h.get('ponds', [])):>4} {max(ds):>5.1f} {sum(ds)/len(ds):>5.1f} {mp:>6.1f}  {len(lines)}")
# layouts: lengths and pars against UDisc
for L in d['layouts']:
    tot = 0; par = 0
    for e in L['holes']:
        h = holes[e['hole']]; n = e['hole'] % 100
        if e['tee'] not in h['tees'] or e['basket'] not in h['baskets']: fail(f"{L['name']}: hole {e['hole']} missing {e['tee']}/{e['basket']}")
        p = [q for q in raw[n]['paths'] if q['tee'] == e['tee'] and q['basket'] == e['basket']]
        if not p: fail(f"{L['name']}: no UDisc path for {e}"); continue
        if p[0]['par'] != e['par']: fail(f"{L['name']}: hole {n} par {e['par']} != UDisc {p[0]['par']}")
        straight = math.dist(h['tees'][e['tee']], h['baskets'][e['basket']])
        poly = [h['tees'][e['tee']]] + h.get('via', []) + [h['baskets'][e['basket']]]
        ln = sum(math.dist(poly[i], poly[i+1]) for i in range(len(poly)-1))
        if abs(ln - p[0]['dist']) > 14 and abs(straight - p[0]['dist']) > 14: warn.append(f"{L['name']} hole {n}: line {ln:.0f} m vs UDisc {p[0]['dist']} m")
        tot += p[0]['dist']; par += e['par']
    print(f"layout {L['name']}: holes {len(L['holes'])} par {par} UDisc length {tot*3.28084:.0f} ft")
for w in warn: print('WARN', w)
for f in fails: print('FAIL', f)
print('OK' if not fails else f'{len(fails)} failures'); sys.exit(1 if fails else 0)
