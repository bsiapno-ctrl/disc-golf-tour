#!/usr/bin/env python3
"""Top-down contact sheets of olympus-disc-golf-course.json (terrain shading, canopies, bushes, lines, pins).
Usage: python3 tools/olympus_preview.py [outdir]   -> sheet_mpo.png (holes 1-9), sheet_mpo2.png (10-18), course.png"""
import json, math, os, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import numpy as np
import matplotlib; matplotlib.use('Agg')
import matplotlib.pyplot as plt
from matplotlib.patches import Circle
from olympus_build import OUT

d = json.load(open(OUT)); T = d['terrain']
H = np.array(T['heights']).reshape(T['nz'], T['nx'])
ext = [T['x0'], T['x0'] + (T['nx'] - 1) * T['cell'], T['z0'], T['z0'] + (T['nz'] - 1) * T['cell']]
holes = {h['number']: h for h in d['holes']}

def draw(ax, h, pad=38, tee='MPO', bas='Blue'):
    t = h['tees'].get(tee) or list(h['tees'].values())[0]; b = h['baskets'].get(bas) or list(h['baskets'].values())[0]
    pts = [t, b] + h.get('via', [])
    x0 = min(p[0] for p in pts) - pad; x1 = max(p[0] for p in pts) + pad; z0 = min(p[1] for p in pts) - pad; z1 = max(p[1] for p in pts) + pad
    c = ((x0 + x1) / 2, (z0 + z1) / 2); s = max(x1 - x0, z1 - z0) / 2
    ax.imshow(H, origin='lower', extent=ext, cmap='YlGn_r', alpha=.8, vmin=H.min(), vmax=H.max())
    ax.contour(np.linspace(ext[0], ext[1], T['nx']), np.linspace(ext[2], ext[3], T['nz']), H, levels=np.arange(8, 32, 1), colors='k', linewidths=.3, alpha=.45)
    for tr in h['trees']:
        col = '#2e6b2e' if tr[3] > 3.3 else '#4d8a3a'
        ax.add_patch(Circle((tr[0], tr[1]), tr[3], color=col, alpha=.7, lw=0))
    for bu in h.get('bushes', []): ax.add_patch(Circle((bu[0], bu[1]), bu[2], color='#c8a04a', alpha=.9, lw=0))
    line = [t] + h.get('via', []) + [b]
    ax.plot([p[0] for p in line], [p[1] for p in line], 'w-', lw=1.2)
    ax.plot(*t, 's', color='red', ms=5); ax.plot(*b, 'o', color='yellow', mec='k', ms=5)
    ax.set_xlim(c[0] - s, c[0] + s); ax.set_ylim(c[1] - s, c[1] + s); ax.set_aspect('equal'); ax.set_xticks([]); ax.set_yticks([])

def sheet(nums, name, outdir):
    fig, axs = plt.subplots(3, 3, figsize=(15, 15), dpi=70)
    for ax, n in zip(axs.flat, nums):
        h = holes[n]; draw(ax, h)
        L = math.dist(h['tees']['MPO'], h['baskets']['Blue'])
        ax.set_title(f"Hole {n}  {L:.0f} m straight  trees {len(h['trees'])}  ({h['obstacleNotes'][:48]}...)", fontsize=8)
    fig.tight_layout(); fig.savefig(os.path.join(outdir, name)); plt.close(fig)

def main():
    outdir = sys.argv[1] if len(sys.argv) > 1 else os.path.join(os.path.dirname(os.path.abspath(__file__)), 'olympus_preview')
    os.makedirs(outdir, exist_ok=True)
    sheet(range(1, 10), 'sheet_mpo.png', outdir); sheet(range(10, 19), 'sheet_mpo2.png', outdir)
    fig, ax = plt.subplots(figsize=(11, 8.5), dpi=80)
    ax.imshow(H, origin='lower', extent=ext, cmap='terrain', vmin=H.min() - 8)
    for n in range(1, 19):
        h = holes[n]; line = [h['tees']['MPO']] + h.get('via', []) + [h['baskets']['Blue']]
        ax.plot([p[0] for p in line], [p[1] for p in line], 'w-', lw=1); ax.text(line[0][0], line[0][1], str(n), color='yellow', fontsize=9, weight='bold')
    ax.set_title(f"Olympus: terrain {H.min():.0f}-{H.max():.0f} m (estimated), MPO lines"); fig.savefig(os.path.join(outdir, 'course.png')); plt.close(fig)

if __name__ == '__main__': main()
