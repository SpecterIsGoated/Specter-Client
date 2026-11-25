import bpy, bmesh, math, os, sys, random
from mathutils import Vector
sys.path.insert(0, os.path.dirname(__file__))
from common import *
src, out, w, samples = args()[0], args()[1], int(args()[2]), int(args()[3])
rot = float(args()[4]) if len(args()) > 4 else 0.0
reset()
scene = bpy.context.scene
gpu(scene)
pano_camera(scene, w, w // 2, 1.7)
world_image(scene, os.path.join(src, "golden_bay_4k.hdr"), 0.55, math.radians(rot))
rnd = random.Random(7)

def material_blossom():
    m = bpy.data.materials.new("blossom")
    m.use_nodes = True
    nt = m.node_tree
    b = nt.nodes['Principled BSDF']
    noise = nt.nodes.new("ShaderNodeTexNoise")
    noise.inputs['Scale'].default_value = 1.6
    ramp = nt.nodes.new("ShaderNodeValToRGB")
    ramp.color_ramp.elements[0].color = (1.0, 0.80, 0.88, 1)
    ramp.color_ramp.elements[1].color = (0.93, 0.42, 0.62, 1)
    nt.links.new(noise.outputs['Fac'], ramp.inputs['Fac'])
    nt.links.new(ramp.outputs['Color'], b.inputs['Base Color'])
    nt.links.new(ramp.outputs['Color'], b.inputs['Emission Color'])
    b.inputs['Emission Strength'].default_value = 0.10
    b.inputs['Roughness'].default_value = 0.7
    b.inputs['Subsurface Weight'].default_value = 0.25
    return m

def material_bark():
    m = bpy.data.materials.new("bark")
    m.use_nodes = True
    b = m.node_tree.nodes['Principled BSDF']
    b.inputs['Base Color'].default_value = (0.025, 0.018, 0.02, 1)
    b.inputs['Roughness'].default_value = 0.9
    return m

def material_lantern():
    m = bpy.data.materials.new("lantern")
    m.use_nodes = True
    b = m.node_tree.nodes['Principled BSDF']
    b.inputs['Base Color'].default_value = (1.0, 0.35, 0.12, 1)
    b.inputs['Emission Color'].default_value = (1.0, 0.18, 0.04, 1)
    b.inputs['Emission Strength'].default_value = 2.6
    return m

BLOSSOM, BARK, LANTERN = material_blossom(), material_bark(), material_lantern()
bpy.ops.mesh.primitive_ico_sphere_add(subdivisions=2, radius=1.0, location=(0, 0, -500))
BLOB = bpy.context.object
bpy.ops.object.shade_smooth()
BLOB.data.materials.append(BLOSSOM)


def branch_curve(points, radii, name):
    cd = bpy.data.curves.new(name, 'CURVE')
    cd.dimensions = '3D'
    cd.bevel_depth = 1.0
    cd.bevel_resolution = 2
    sp = cd.splines.new('POLY')
    sp.points.add(len(points) - 1)
    for i, (p, r) in enumerate(zip(points, radii)):
        sp.points[i].co = (p.x, p.y, p.z, 1)
        sp.points[i].radius = r
    ob = bpy.data.objects.new(name, cd)
    ob.data.materials.append(BARK)
    scene.collection.objects.link(ob)

def grow(start, direction, length, radius, depth, tips):
    pts, rads = [start.copy()], [radius]
    p, d = start.copy(), direction.normalized()
    n = 5
    for i in range(n):
        d = (d + Vector((rnd.uniform(-.25, .25), rnd.uniform(-.25, .25), rnd.uniform(-.05, .2)))).normalized()
        p = p + d * (length / n)
        pts.append(p.copy())
        rads.append(radius * (1 - 0.6 * (i + 1) / n))
    branch_curve(pts, rads, "branch")
    if depth == 0:
        tips.append((p, length))
        return
    kids = rnd.randint(2, 3)
    for k in range(kids):
        a = 2 * math.pi * k / kids + rnd.uniform(-.5, .5)
        nd = (d * 0.55 + Vector((math.cos(a), math.sin(a), 0.35)) * 0.75).normalized()
        start_at = pts[rnd.randint(3, n)]
        grow(start_at, nd, length * rnd.uniform(0.62, 0.78), radius * 0.55, depth - 1, tips)

def tree(x, y, scale):
    tips = []
    base = Vector((x, y, 0))
    grow(base, Vector((rnd.uniform(-.15, .15), rnd.uniform(-.15, .15), 1)), 3.4 * scale, 0.32 * scale, 3, tips)
    me = bpy.data.meshes.new('crownpts')
    pts = []
    for tip, length in tips:
        for _ in range(int(190 * scale)):
            off = Vector((rnd.gauss(0, 1), rnd.gauss(0, 1), rnd.gauss(0.25, 0.7)))
            if off.length > 1.7:
                off = off.normalized() * rnd.uniform(1.0, 1.7)
            pts.append(tip + off * (0.8 * scale))
    me.from_pydata(pts, [], [])
    ob = bpy.data.objects.new('crown', me)
    scene.collection.objects.link(ob)
    ps = ob.modifiers.new('blossoms', 'PARTICLE_SYSTEM').particle_system.settings
    ps.count = len(pts)
    ps.emit_from = 'VERT'
    ps.use_emit_random = False
    ps.frame_start = ps.frame_end = 1
    ps.lifetime = 10000
    ps.physics_type = 'NO'
    ps.render_type = 'OBJECT'
    ps.instance_object = BLOB
    ps.particle_size = 0.17 * scale
    ps.size_random = 0.55
    ps.use_rotation_instance = True
    ps.use_rotations = True
    ps.rotation_mode = 'NONE'
    ps.rotation_factor_random = 1.0
    return tips

def lantern(p):
    bpy.ops.mesh.primitive_uv_sphere_add(segments=24, ring_count=12, radius=0.28, location=p)
    o = bpy.context.object
    o.scale = (1, 1, 1.25)
    o.data.materials.append(LANTERN)
    l = bpy.data.objects.new("lamp", bpy.data.lights.new("lamp", 'POINT'))
    l.data.energy = 260
    l.data.color = (1.0, 0.55, 0.3)
    l.data.shadow_soft_size = 0.4
    l.location = p
    scene.collection.objects.link(l)

# A ring of trees framing the view, with a wide gap ahead (image centre) where the city skyline sits.
for i in range(16):
    az = math.radians(-180 + i * 22.5 + rnd.uniform(-6, 6))
    if abs(math.degrees(az)) < 50:
        continue
    dist = rnd.uniform(11, 22)
    x, y = math.sin(az) * dist, math.cos(az) * dist
    s = rnd.uniform(1.15, 1.6)
    tree(x, y, s)
    lantern(Vector((x * 0.82, y * 0.82, 2.6)))

# Some fallen petals glowing faintly on the ground around you
bm = bmesh.new()
for _ in range(2200):
    a, d = rnd.uniform(0, 2 * math.pi), rnd.uniform(3, 24)
    m = bmesh.ops.create_circle(bm, cap_ends=True, segments=5, radius=rnd.uniform(0.04, 0.08))
    bmesh.ops.translate(bm, verts=m['verts'], vec=Vector((math.cos(a) * d, math.sin(a) * d, 0.01)))
me = bpy.data.meshes.new("petals")
bm.to_mesh(me)
ob = bpy.data.objects.new("petals", me)
ob.data.materials.append(BLOSSOM)
scene.collection.objects.link(ob)

# a soft pink fill from above so the canopies read against the night sky
fill = bpy.data.objects.new("fill", bpy.data.lights.new("fill", 'SUN'))
fill.data.energy = 0.18
fill.data.color = (1.0, 0.7, 0.85)
fill.rotation_euler = (math.radians(25), 0, math.radians(30))
scene.collection.objects.link(fill)
output(scene, out, samples, 'AgX', 0.3)
