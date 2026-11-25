"""Cuts the CC-BY F-22 (andertan, Sketchfab) into the Jet Mech's parts and writes them in jet space.

in : f22n.json (Blender dump: verts, tris with corner normals, material names)
out: f22.bin (big-endian, read by MechF22.java) + preview OBJ (one object per segment)
F-22 source axes: x nose(0) -> tail(12.1), y = right wing (+) / left wing (-), z up.
Jet space: +X left wing, +Y up, +Z nose.
"""
import json, math, struct, sys

src, out_bin, out_obj = sys.argv[1], sys.argv[2], sys.argv[3]
K = 0.25            # F-22 units -> blocks (12.1 long -> ~3.0 blocks)
NOSE_Z = 1.65       # jet-space z of the nose tip
MID_Z = 0.2         # F-22 z that becomes jet y = 0

d = json.load(open(src))
V, T, MATS = d['verts'], d['tris'], d['mats']

ARMOR, ACCENT, SILVER, DARK, PANEL, CANOPY, TRIM, GLOW, NOZZLE = range(9)

# ---------------------------------------------------------------- islands (welded by position)
par = list(range(len(V)))
def find(a):
    while par[a] != a:
        par[a] = par[par[a]]
        a = par[a]
    return a
seen = {}
for i, v in enumerate(V):
    k = tuple(round(c, 4) for c in v)
    if k in seen:
        par[find(i)] = find(seen[k])
    else:
        seen[k] = i
for t in T:
    a = find(t['v'][0])
    for j in t['v'][1:]:
        par[find(j)] = a
islands = {}
for ti, t in enumerate(T):
    islands.setdefault(find(t['v'][0]), []).append(ti)

def bbox(tis):
    pts = [V[i] for ti in tis for i in T[ti]['v']]
    return [min(p[k] for p in pts) for k in range(3)], [max(p[k] for p in pts) for k in range(3)]

main = max(islands.values(), key=len)
keep = []  # (kind, tri indices)
for tis in islands.values():
    if tis is main:
        keep.append(('main', tis))
        continue
    lo, hi = bbox(tis)
    names = {MATS[T[ti]['m']] for ti in tis}
    if 'plumes' in names:
        continue                     # painted exhaust plumes: the mod draws its own flames
    if hi[0] < 3.6 and lo[0] > 1.6:
        continue                     # cockpit interior: hidden under a solid canopy
    if lo[0] > 8.0 and hi[2] > 1.5:
        keep.append(('fin', tis))
    elif lo[0] > 9.8 and hi[0] < 10.8 and (lo[1] > 0.05 or hi[1] < -0.05):
        keep.append(('nozzle', tis))
    elif lo[0] > 8.9:
        keep.append(('keel', tis))
    else:
        print('dropped island', len(tis), lo, hi, names)

# ---------------------------------------------------------------- polygons
def mat_class(name, kind):
    if name == 'glass':
        return CANOPY
    if name in ('dark',):
        return DARK
    if name == 'None':
        return NOZZLE if kind == 'nozzle' else DARK
    if name == 'Grey':
        return PANEL
    if name.startswith('Whitish'):
        return SILVER
    return ARMOR  # the painted skin

class Poly:
    __slots__ = ('p', 'n', 'm', 'kind', 'src')
    def __init__(self, p, n, m, kind):
        self.p, self.n, self.m, self.kind = p, n, m, kind

polys = []
for kind, tis in keep:
    for ti in tis:
        t = T[ti]
        polys.append(Poly([list(V[i]) for i in t['v']], [list(n) for n in t['n']], mat_class(MATS[t['m']], kind), kind))

def lerp(a, b, f):
    return [a[k] + (b[k] - a[k]) * f for k in range(3)]

def norm(v):
    l = math.sqrt(sum(c * c for c in v)) or 1.0
    return [c / l for c in v]

def clip(poly, plane):
    """Splits a polygon by plane (n, d): returns (front, back) polygons or None."""
    n, dd = plane
    dist = [sum(n[k] * p[k] for k in range(3)) - dd for p in poly.p]
    if all(x >= -1e-6 for x in dist) or all(x <= 1e-6 for x in dist):
        return None
    out = ([], []), ([], [])
    cnt = len(poly.p)
    for i in range(cnt):
        j = (i + 1) % cnt
        pi, pj, ni, nj, di, dj = poly.p[i], poly.p[j], poly.n[i], poly.n[j], dist[i], dist[j]
        for side, sgn in ((0, 1), (1, -1)):
            if sgn * di >= 0:
                out[side][0].append(pi); out[side][1].append(ni)
        if (di > 0) != (dj > 0) and abs(di - dj) > 1e-9 and di != 0 and dj != 0:
            f = di / (di - dj)
            q, m = lerp(pi, pj, f), norm(lerp(ni, nj, f))
            for side in (0, 1):
                out[side][0].append(q); out[side][1].append(m)
    res = []
    for ps, ns in out:
        if len(ps) >= 3:
            pp = Poly(ps, ns, poly.m, poly.kind)
            res.append(pp)
    return res

def cut_all(ps, planes, kinds):
    for pl in planes:
        region = pl[2] if len(pl) > 2 else None
        pl = (pl[0], pl[1])
        nxt = []
        for p in ps:
            if p.kind not in kinds or (region is not None and not region(p)):
                nxt.append(p)
                continue
            r = clip(p, pl)
            if r is None:
                nxt.append(p)
            else:
                nxt.extend(r)
        ps = nxt
    return ps

def xplane(x): return ([1, 0, 0], x)
def yplane(y): return ([0, 1, 0], y)
def zplane(z): return ([0, 0, 1], z)
def line_plane(ax, ay, bx, by):
    """Vertical plane (contains z) through two points in the x-y plane."""
    n = norm([by - ay, -(bx - ax), 0])
    return (n, n[0] * ax + n[1] * ay)
def xz_plane(ax, az, bx, bz):
    """Plane containing y through two points in the x-z plane."""
    n = norm([bz - az, 0, -(bx - ax)])
    return (n, n[0] * ax + n[2] * az)

# wing control surfaces: hinge line roughly parallel to the forward-swept trailing edge
HINGE_A, HINGE_B = (9.35, 1.30), (8.45, 4.00)
FLAP_OUT, AIL_OUT = 2.85, 4.05
WING_ROOT, STAB_ROOT, STAB_X = 1.25, 0.85, 10.02
def box(x0=-99, x1=99, s0=-1, s1=99):
    """Region test on a polygon's bounding box (x along, s = |y|)."""
    def f(p):
        xs = [q[0] for q in p.p]; ss = [abs(q[1]) for q in p.p]
        return max(xs) >= x0 and min(xs) <= x1 and max(ss) >= s0 and min(ss) <= s1
    return f
def with_region(pl, region):
    return (pl[0], pl[1], region)
main_planes = [with_region(xplane(x), box(s1=WING_ROOT + 0.05)) for x in (1.5, 3.0, 4.2, 6.5, 8.3)]
main_planes.append(with_region(xplane(STAB_X), box(s0=STAB_ROOT - 0.05)))
for sgn in (1, -1):
    main_planes += [with_region(yplane(sgn * WING_ROOT), box(x1=STAB_X + 0.05)), with_region(yplane(sgn * 0.45), box(2.9, 6.6)),
                    with_region(yplane(sgn * STAB_ROOT), box(STAB_X - 0.05)),
                    with_region(yplane(sgn * FLAP_OUT), box(8.0, STAB_X, WING_ROOT)), with_region(yplane(sgn * AIL_OUT), box(8.0, STAB_X, WING_ROOT)),
                    with_region(line_plane(HINGE_A[0], sgn * HINGE_A[1], HINGE_B[0], sgn * HINGE_B[1]), box(7.5, STAB_X + 0.1, WING_ROOT - 0.05, AIL_OUT + 0.05))]
main_planes += [with_region(yplane(0.0), box(6.4)), with_region(zplane(0.35), box(1.4, 4.3, -1, 0.5))]
polys = cut_all(polys, main_planes, {'main'})
# rudders: lower trailing part of each fin
RUD_A, RUD_B, RUD_TOP = (10.40, 0.40), (9.62, 2.00), 1.45
polys = cut_all(polys, [xz_plane(RUD_A[0], RUD_A[1], RUD_B[0], RUD_B[1]), zplane(RUD_TOP)], {'fin'})

def behind_hinge(x, s):
    # point is behind (towards the tail of) the hinge line A->B
    ax, ay = HINGE_A; bx, by = HINGE_B
    t = (s - ay) / (by - ay)
    return x > ax + (bx - ax) * t

def behind_rudder(x, z):
    ax, az = RUD_A; bx, bz = RUD_B
    t = (z - az) / (bz - az)
    return x > ax + (bx - ax) * t

def classify(p):
    c = [sum(q[k] for q in p.p) / len(p.p) for k in range(3)]
    x, y, z = c
    s = abs(y)
    side = 'l' if y < 0 else 'r'   # -y is the pilot's left
    if p.kind == 'fin':
        return ('rudder-' if behind_rudder(x, z) and z < RUD_TOP else 'fin-') + side
    if p.kind == 'nozzle':
        return 'nozzle-' + side
    if p.kind == 'keel':
        return 'keel'
    if x >= STAB_X and s >= STAB_ROOT:
        return 'stab-' + side
    if s >= WING_ROOT and x < STAB_X:
        if behind_hinge(x, s) and s < AIL_OUT:
            return ('flaperon-' if s < FLAP_OUT else 'aileron-') + side
        return 'wing-' + side
    if x < 1.5:
        return 'nose-tip'
    if x < 4.2:
        if x >= 3.0 and s >= 0.45:
            return 'upper-arm-' + side
        return 'backpack' if z >= 0.35 else 'nose-base'
    if x < 6.5:
        return 'upper-arm-' + side if s >= 0.45 else 'chest'
    if x < 8.3:
        return 'thigh-' + side
    return 'shin-' + side

def camo(x, y, z):
    """F-22 two-tone: soft darker blotches over the lighter base grey."""
    n = math.sin(x * 0.85 + math.sin(y * 1.2) * 1.6) * math.cos(y * 1.05 + x * 0.33) + 0.45 * math.sin(z * 2.4 + x * 0.5 + y * 0.7)
    return n > 0.42

segs = {}
for p in polys:
    name = classify(p)
    m = p.m
    if m == ARMOR:
        c = [sum(q[k] for q in p.p) / len(p.p) for k in range(3)]
        if camo(*c):
            m = ACCENT
    # fan-triangulate
    for i in range(1, len(p.p) - 1):
        segs.setdefault(name, []).append((m, [p.p[0], p.p[i], p.p[i + 1]], [p.n[0], p.n[i], p.n[i + 1]]))

def to_jet(v):
    return [-v[1] * K, (v[2] - MID_Z) * K, NOSE_Z - v[0] * K]
def to_jet_n(n):
    return [-n[1], n[2], -n[0]]

names = sorted(segs)
with open(out_bin, 'wb') as f:
    f.write(b'F22M')
    f.write(struct.pack('>i', len(names)))
    for nme in names:
        tris = segs[nme]
        b = nme.encode()
        f.write(struct.pack('>H', len(b))); f.write(b)
        f.write(struct.pack('>i', len(tris)))
        for m, ps, ns in tris:
            f.write(struct.pack('>b', m))
            for q, n in zip(ps, ns):
                f.write(struct.pack('>6f', *to_jet(q), *to_jet_n(n)))
with open(out_obj, 'w') as f:
    base = 1
    for nme in names:
        f.write(f'o {nme}\nusemtl {nme}\n')
        for m, ps, ns in segs[nme]:
            for q in ps:
                f.write('v %.5f %.5f %.5f\n' % tuple(to_jet(q)))
        for i in range(len(segs[nme])):
            f.write(f'f {base + i * 3} {base + i * 3 + 1} {base + i * 3 + 2}\n')
        base += len(segs[nme]) * 3
total = sum(len(v) for v in segs.values())
print('segments', len(names), 'tris', total)
for nme in names:
    print(' ', nme, len(segs[nme]))

# ---------------------------------------------------------------- hinges and pivots (jet space), appended to f22.bin
def on_plane(pt, plane, eps=2e-4):
    n, dd = plane
    return abs(sum(n[k] * pt[k] for k in range(3)) - dd) < eps

def hinge_of(name, plane):
    pts = [q for m, ps, ns in segs[name] for q in ps if on_plane(q, plane)]
    if len(pts) < 2:
        raise SystemExit('no hinge points for ' + name)
    a, b = max(((p, q) for p in pts for q in pts), key=lambda pq: sum((pq[0][k] - pq[1][k]) ** 2 for k in range(3)))
    return to_jet(a), to_jet(b)

extra = {}
for side, sgn in (('l', -1), ('r', 1)):
    hp = line_plane(HINGE_A[0], sgn * HINGE_A[1], HINGE_B[0], sgn * HINGE_B[1])
    extra['flaperon-' + side] = hinge_of('flaperon-' + side, hp)
    extra['aileron-' + side] = hinge_of('aileron-' + side, hp)
    extra['rudder-' + side] = hinge_of('rudder-' + side, xz_plane(RUD_A[0], RUD_A[1], RUD_B[0], RUD_B[1]))
    # nozzle: hinge at its front, exit at the back of the glowing inside
    nz = [to_jet(q) for m, ps, ns in segs['nozzle-' + side] for q in ps]
    inner = [to_jet(q) for m, ps, ns in segs['nozzle-' + side] for q in ps if m == NOZZLE] or nz
    cx = sum(p[0] for p in nz) / len(nz); cy = sum(p[1] for p in nz) / len(nz)
    extra['nozzle-' + side] = ([cx, cy, max(p[2] for p in nz)],
                               [sum(p[0] for p in inner) / len(inner), sum(p[1] for p in inner) / len(inner), min(p[2] for p in inner)])
    extra['nozzle-size-' + side] = ([(max(p[0] for p in inner) - min(p[0] for p in inner)) / 2, (max(p[1] for p in inner) - min(p[1] for p in inner)) / 2, 0], [0, 0, 0])
    # all-moving stabiliser: spindle near the root, mid chord
    root = [q for m, ps, ns in segs['stab-' + side] for q in ps if abs(q[1]) < STAB_ROOT + 0.25]
    px = sum(q[0] for q in root) / len(root); pz = sum(q[2] for q in root) / len(root)
    piv = to_jet([px, sgn * STAB_ROOT, pz])
    extra['stab-' + side] = (piv, piv)
with open(out_bin, 'ab') as f:
    f.write(struct.pack('>i', len(extra)))
    for nme, (a, b) in sorted(extra.items()):
        bb = nme.encode()
        f.write(struct.pack('>H', len(bb))); f.write(bb)
        f.write(struct.pack('>6f', *a, *b))
for k, v in sorted(extra.items()):
    print(' ', k, [round(c, 3) for c in v[0]], [round(c, 3) for c in v[1]])
