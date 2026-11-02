#!/usr/bin/env python3
"""Top-down preview PNG per hole from picnic-island.json, drawn in the SAME rotated view (centre/zoom/bearing/pixel frame)
used for the UDisc satellite screenshots, so it can be flipped against them.  Usage: picnic_preview.py [outdir]"""
import json, math, os, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import matplotlib; matplotlib.use('Agg')
import matplotlib.pyplot as plt
from matplotlib.patches import Circle, Ellipse, Polygon
from picnic_build import OUT, S_CSS, CX_CSS, CY_CSS, K, LAT0
from picnic_spec import SPEC

def to_px(view, x, z):
    cx, cz, zoom, br = view; mpp = 78271.517 * math.cos(LAT0 * K) / 2 ** zoom; b = br * K
    dx, dz = x - cx, z - cz
    right = dx * math.cos(b) - dz * math.sin(b); up = dx * math.sin(b) + dz * math.cos(b)
    return 168 + (right / mpp + CX_CSS) * S_CSS, 42 + (-up / mpp + CY_CSS) * S_CSS, mpp * 1.0 / S_CSS  # m per screenshot px

def main():
    outdir = sys.argv[1] if len(sys.argv) > 1 else os.path.join(os.path.dirname(os.path.abspath(__file__)), 'picnic_preview')
    os.makedirs(outdir, exist_ok=True)
    d = json.load(open(OUT))
    for h in d['holes']:
        n = h['number'] % 100; view = SPEC[n]['view']
        fig = plt.figure(figsize=(14, 6.5), dpi=80); ax = fig.add_axes([0, 0, 1, 1]); ax.set_xlim(168, 1568); ax.set_ylim(692, 42); ax.set_facecolor('#6b7f45'); ax.axis('off')
        P = lambda x, z: to_px(view, x, z)
        _, _, m = P(0, 0)
        for p in h.get('ponds', []):
            u, v, _ = P(p[0], p[1]); ax.add_patch(Circle((u, v), p[2] / m, color='#2f6f9f', alpha=.55, lw=0))
        # fairway band
        for tn, t in h['tees'].items():
            for bn, b in h['baskets'].items():
                pts = [t] + h.get('via', []) + [b]; px = [P(*q)[:2] for q in pts]
                ax.plot([q[0] for q in px], [q[1] for q in px], color='#d8e8a0', lw=2 * h['fairwayWidth'] / m * 0.72, alpha=.18, solid_capstyle='round', zorder=1)
        for p in h.get('bushes', []):
            u, v, _ = P(p[0], p[1]); ax.add_patch(Circle((u, v), p[2] / m, color='#a8a25a', alpha=.85, lw=0, zorder=3))
        for t in h['trees']:
            u, v, _ = P(t[0], t[1]); ax.add_patch(Circle((u, v), t[3] / m, color='#17361c', alpha=.82, lw=.5, ec='#0b1d0e', zorder=4))
        for tn, t in h['tees'].items():
            for bn, b in h['baskets'].items():
                pts = [t] + h.get('via', []) + [b]; px = [P(*q)[:2] for q in pts]
                ax.plot([q[0] for q in px], [q[1] for q in px], color='white', lw=1, alpha=.5, zorder=6)
        for tn, t in h['tees'].items():
            u, v, _ = P(*t); ax.plot(u, v, 's', color='#ff8a1c' if 'Orange' in tn else '#3b8bff', ms=9, mec='w', zorder=8); ax.text(u + 8, v - 8, tn, color='w', fontsize=9, zorder=9)
        for bn, b in h['baskets'].items():
            u, v, _ = P(*b); ax.plot(u, v, 'o', color={'A': '#ff8a1c', 'Blue': '#3b8bff'}.get(bn, 'w'), ms=9, mec='k', zorder=8); ax.text(u + 8, v + 14, bn, color='w', fontsize=9, zorder=9)
        for q in h.get('via', []):
            u, v, _ = P(*q); ax.plot(u, v, 'D', color='#ff4bd8', ms=5, zorder=9)
        ax.text(180, 70, f"{h['name']} (#{h['number']})  trees {len(h['trees'])}  bushes {len(h.get('bushes', []))}  ponds {len(h.get('ponds', []))}  fw {h['fairwayWidth']}", color='w', fontsize=12, zorder=10, bbox=dict(fc='k', alpha=.5, lw=0))
        fig.savefig(os.path.join(outdir, f"hole_{h['number']:03d}.png")); plt.close(fig)
    print('wrote', len(d['holes']), 'previews to', outdir)
main()
