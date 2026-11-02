#!/usr/bin/env python3
"""Reshape the bundled course JSONs: add doglegs (via points), blocking trees, bushes, ponds and OB beside the fairway.

Run from the repo root: python3 tools/shape_holes.py   (reads each course from git HEAD~ base, the pre-shaping commit BASE_REF, so it is idempotent).
Tee/basket positions, fairway widths and pars are never touched. All shaping here is invented, not surveyed.

Per-hole recipe tokens (space separated):
  L / R           dogleg left / right (optional  L:apex:lateral  e.g. L:0.45:0.18). Trees fill the inside corner automatically.
  SL / SR         S-curve, the first bend turning left / right.
  GL / GR         gate: tall trees block the middle, the open lane is on the left / right.
  BL / BR         low bushes on the left / right half; stay on the other side or loft it over.
  PL / PR         pond beside the fairway on the left / right.   OL / OR  out-of-bounds strip on the left / right.
  T               tight tunnel (dense trees close to the fairway).    -  nothing (open hole).
  =               keep the hole as the previous round shaped it (existing bends/ponds), add only what follows.
Append @u to place a feature at that fraction of the way (PL@0.4).
"""
import json, math, subprocess, sys, random, os

BASE_REF = os.environ.get("BASE_REF", "823650f")
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DIR = "app/src/main/assets/courses/"

# ---------------------------------------------------------------------------------------------------------------------
PLANS = {
    "blue-ribbon-pines-inspired": ("pine", [
        "L", "T", "SR", "BR", "-", "L OR@0.45", "= GR", "R PL@0.6", "-", "L:0.5:0.2", "SL", "GL", "T", "R", "-", "PL@0.55", "-", "R"]),
    "cypress-point-park": ("oak", [
        "L", "GR", "=", "R", "BL", "-", "= GL", "L", "-", "-", "T", "=", "-", "R", "-", "=", "L OL@0.5", "R"]),
    "idlewild-inspired": ("pine", [
        "R", "-", "L", "SR", "= BR", "R", "L OR@0.45", "T", "L", "-", "SL", "=", "R PL@0.55", "T", "-", "L", "-", "SL"]),
    "maple-hill-inspired": ("oak", [
        "-", "L", "-", "R", "BL", "SL", "PR@0.5", "L", "T", "R OL@0.4", "-", "R", "SR", "T", "BR", "L", "-", "R PL@0.6"]),
    "olympus-disc-golf-course": ("oak", [
        "-", "R", "=", "GR", "-", "-", "L", "-", "-", "OR@0.5", "=", "R", "BL", "=", "L OR@0.5", "=", "-", "R"]),
    "smugglers-notch-inspired": ("pine", [
        "-", "L", "=", "SL", "BR", "-", "SR", "GL", "=", "R", "L", "OL@0.5", "-", "SL", "-", "=", "-", "BL"]),
}

def git_base(path):
    out = subprocess.run(["git", "show", f"{BASE_REF}:{path}"], cwd=ROOT, capture_output=True, text=True)
    if out.returncode != 0:
        sys.exit(out.stderr)
    return out.stdout

class Path:
    def __init__(self, pts):
        self.p = [tuple(q) for q in pts]
        self.cum = [0.0]
        for i in range(1, len(self.p)):
            self.cum.append(self.cum[-1] + math.dist(self.p[i], self.p[i - 1]))
        self.length = self.cum[-1]
    def at(self, s):
        s = max(0.0, min(self.length, s))
        for i in range(len(self.p) - 1):
            if s <= self.cum[i + 1] or i == len(self.p) - 2:
                a, b = self.p[i], self.p[i + 1]
                seg = max(self.cum[i + 1] - self.cum[i], 1e-6)
                t = (s - self.cum[i]) / seg
                d = ((b[0] - a[0]) / seg, (b[1] - a[1]) / seg)
                return (a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t), d
    def frame(self, s, v):
        (x, z), (dx, dz) = self.at(s)
        return x - dz * v, z + dx * v            # v > 0 is to the left of travel
    def dist(self, x, z):
        best = 1e9
        for i in range(len(self.p) - 1):
            a, b = self.p[i], self.p[i + 1]
            abx, abz = b[0] - a[0], b[1] - a[1]
            l2 = abx * abx + abz * abz
            t = 0 if l2 < 1e-9 else max(0, min(1, ((x - a[0]) * abx + (z - a[1]) * abz) / l2))
            best = min(best, math.hypot(x - a[0] - abx * t, z - a[1] - abz * t))
        return best

def parse_tokens(spec):
    out = []
    for tok in spec.split():
        u = None
        if "@" in tok:
            tok, u = tok.split("@"); u = float(u)
        parts = tok.split(":")
        out.append((parts[0], [float(x) for x in parts[1:]], u))
    return out

def shape_hole(h, style, rnd):
    tee = list(h["tees"].values())[0]; bas = list(h["baskets"].values())[0]
    L = math.dist(tee, bas)
    W = h["fairwayWidth"]
    toks = parse_tokens(h["_spec"])
    keep = any(t[0] == "=" for t in toks)
    toks = [t for t in toks if t[0] not in ("=", "-")]
    c = ((bas[0] - tee[0]) / L, (bas[1] - tee[1]) / L)
    nl = (-c[1], c[0])                                  # left normal of the chord
    notes = []
    via = h.get("via", []) if keep else []
    h.pop("via", None)
    for kind, args, u in toks:
        if kind in ("L", "R"):
            a = args[0] if len(args) > 0 else rnd.uniform(0.42, 0.62)
            k = args[1] if len(args) > 1 else rnd.uniform(0.13, 0.2) * (1.15 if L < 110 else 1.0)
            lat = min(max(k * L, W / 2 + 6.0), 0.3 * L)
            side = 1 if kind == "R" else -1             # a left dogleg bulges to the right of the chord
            via = [[round(tee[0] + c[0] * a * L + nl[0] * side * lat, 1), round(tee[1] + c[1] * a * L + nl[1] * side * lat, 1)]]
            if kind == "L":
                notes.append("Dogleg left: the woods on the inside shut the straight line, so shape a hyzer around the bend.")
            else:
                notes.append("Dogleg right: the inside corner is wooded; work an anhyzer (or forehand hyzer) round the bend.")
        elif kind in ("SL", "SR"):
            lat = min(max(rnd.uniform(0.09, 0.115) * L, W / 2 + 6.0), 0.2 * L)
            first = -1 if kind == "SL" else 1
            via = [[round(tee[0] + c[0] * 0.33 * L + nl[0] * first * lat, 1), round(tee[1] + c[1] * 0.33 * L + nl[1] * first * lat, 1)],
                   [round(tee[0] + c[0] * 0.67 * L - nl[0] * first * lat, 1), round(tee[1] + c[1] * 0.67 * L - nl[1] * first * lat, 1)]]
            notes.append("S-curve: the lane bends " + ("left then right" if kind == "SL" else "right then left") + " between wooded corners.")
    path = Path([tee] + via + [bas])
    trees = h.get("extraTrees", [])
    bushes = h.get("bushes", [])
    ponds = h.get("ponds", [])
    obs = []
    sn = lambda kd: 1 if kd[1] == "L" else -1
    hs = lambda: (13 + rnd.random() * 3.5)
    var = 0 if style == "pine" else 1
    for kind, args, u in toks:
        if kind in ("GL", "GR"):
            lane = sn(kind)
            s = (u or 0.55) * path.length
            spans = [(-lane * 0.9, 15.5, 2.9), (-lane * 3.9, 13.5, 2.6)]
            if W >= 16:
                spans.append((-lane * 6.9, 14.5, 2.8))
            for v, hh, cc in spans:
                x, z = path.frame(s, v)
                trees.append([round(x, 1), round(z, 1), hh, cc, var])
            x, z = path.frame(s + 7, -lane * (W / 2 - 0.5))
            trees.append([round(x, 1), round(z, 1), 11.5, 2.4, 1 - var])
            notes.append("Tall trees guard the middle; the open lane is " + ("left: start left and work back right (flex or anhyzer)." if lane > 0 else "right: start right and fade back left (hyzer)."))
        elif kind in ("BL", "BR"):
            side = 1 if kind == "BL" else -1             # bushes sit on this side, the lane is on the other
            s = (u or 0.6) * path.length
            e = W / 2 + 0.5                           # a diagonal wall of shrubs from just off the centreline to the edge
            for off, f, r in ((-4.5, 0.10, 1.8), (-1.5, 0.38, 2.1), (1.5, 0.66, 2.0), (4.5, 0.94, 1.8), (-0.5, 0.80, 1.6)):
                x, z = path.frame(s + off, side * f * e)
                bushes.append([round(x, 1), round(z, 1), r])
            notes.append("Low bushes fill the " + ("left" if side > 0 else "right") + " half of the fairway: keep it " + ("right" if side > 0 else "left") + " or throw high over them.")
        elif kind in ("PL", "PR"):
            side = sn(kind)
            s = (u or 0.55) * path.length
            r = min(8.0, 0.09 * L + 3.5)
            (x0, z0), (dx, dz) = path.at(s)
            rx, rz = (r * 1.4, r * 0.85) if abs(dx) > abs(dz) else (r * 0.85, r * 1.4)
            v = W / 2 + 0.8 + r
            for _ in range(14):
                cx, cz = path.frame(s, side * v)
                pts = [(cx, cz)] + [(cx + rx * math.cos(a * 0.39), cz + rz * math.sin(a * 0.39)) for a in range(16)]
                if min(path.dist(*q) for q in pts) >= W / 2 + 0.9:
                    break
                v += 0.8
            ponds.append([round(cx, 1), round(cz, 1), round(rx, 1), round(rz, 1)])
            notes.append("Water " + ("left" if side > 0 else "right") + " of the fairway: bail out the other way.")
        elif kind in ("OL", "OR"):
            side = sn(kind)
            s0 = (u or 0.5) * path.length
            r = 7.5
            for off in (-0.13, 0.0, 0.13):
                s = s0 + off * path.length
                v = W / 2 + 2.6 + r
                for _ in range(14):
                    cx, cz = path.frame(s, side * v)
                    pts = [(cx, cz)] + [(cx + r * math.cos(a * 0.39), cz + r * math.sin(a * 0.39)) for a in range(16)]
                    if min(path.dist(*q) for q in pts) >= W / 2 + 2.2:
                        break
                    v += 0.8
                obs.append([round(cx, 1), round(cz, 1), r, r])
            notes.append("Out of bounds " + ("left" if side > 0 else "right") + " (a creek bank): stay on the fairway side.")
        elif kind == "T":
            h["treeDensity"] = 0.95; h["guardianChance"] = 0.55
            notes.append("A tight tree-lined corridor: low and straight, with little room to curve.")
    if via: h["via"] = via
    if trees: h["extraTrees"] = trees
    elif "extraTrees" in h: del h["extraTrees"]
    if bushes: h["bushes"] = bushes
    if ponds: h["ponds"] = ponds
    if obs: h["ob"] = obs
    if notes: h["obstacleNotes"] = " ".join(notes)
    h.pop("_spec", None)

def main():
    summary = {}
    for cid, (style, plan) in PLANS.items():
        path = DIR + cid + ".json"
        d = json.loads(git_base(path))
        assert len(plan) == len(d["holes"]) == 18, cid
        rnd = random.Random(cid)
        for h, spec in zip(d["holes"], plan):
            # reset anything this script adds, keeping pre-existing ponds and via for "=" holes
            for k in ("extraTrees", "bushes", "ob", "obstacleNotes", "guardianChance"):
                h.pop(k, None)
            h["_spec"] = spec
            shape_hole(h, style, rnd)
        lay = d["layouts"][0]
        extra = " Fairway bends, bushes, tree blockers, water and OB are invented shaping, not surveyed features of the real course."
        if "Fairway bends" not in lay.get("description", ""):
            lay["description"] = lay.get("description", "") + extra
        open(os.path.join(ROOT, path), "w").write(json.dumps(d, separators=(",", ":")))
        summary[cid] = sum(1 for h in d["holes"] if any(k in h for k in ("via", "bushes", "ob", "ponds", "extraTrees")) )
    print(summary)

main()
