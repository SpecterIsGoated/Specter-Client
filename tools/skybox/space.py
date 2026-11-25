import bpy, bmesh, math, os, sys
sys.path.insert(0, os.path.dirname(__file__))
from common import *
src, out, w, samples = args()[0], args()[1], int(args()[2]), int(args()[3])
reset()
scene = bpy.context.scene
gpu(scene)
pano_camera(scene, w, w // 2, 0)
world_image(scene, os.path.join(src, "8k_stars_milky_way.jpg"), 1.0)
scene.world.node_tree.nodes['Background'].inputs['Strength'].default_value = 4.0

def place(az_deg, el_deg, dist):
    # az 0 = image centre (+Y), positive = to the right (clockwise seen from above)
    az, el = math.radians(az_deg), math.radians(el_deg)
    return (math.sin(az) * math.cos(el) * dist, math.cos(az) * math.cos(el) * dist, math.sin(el) * dist)

def mat_image(name, path):
    m = bpy.data.materials.new(name)
    m.use_nodes = True
    nt = m.node_tree
    bsdf = nt.nodes['Principled BSDF']
    tex = nt.nodes.new("ShaderNodeTexImage")
    tex.image = bpy.data.images.load(path)
    nt.links.new(tex.outputs['Color'], bsdf.inputs['Base Color'])
    bsdf.inputs['Roughness'].default_value = 0.9
    return m

# Saturn
R = 72.0
D = 420.0
pos = place(-35, 24, D)
bpy.ops.mesh.primitive_uv_sphere_add(segments=128, ring_count=64, radius=R, location=pos)
sat = bpy.context.object
bpy.ops.object.shade_smooth()
sat.scale = (1, 1, 0.902)  # Saturn is visibly flattened
sat.data.materials.append(mat_image("saturn", os.path.join(src, "8k_saturn.jpg")))
from mathutils import Vector, Quaternion
_p = Vector(pos).normalized()
_up = (Vector((0, 0, 1)) - _p * _p.z).normalized()
_n = (_up * math.cos(math.radians(26)) - _p * math.sin(math.radians(26))).normalized()
_n = Quaternion(_p, math.radians(-18)) @ _n
tilt = _n.to_track_quat('Z', 'Y').to_euler()
sat.rotation_euler = tilt

# Rings: an annulus whose U runs from the inner to the outer edge of the ring strip texture
mesh = bpy.data.meshes.new("rings")
bm = bmesh.new()
uv = bm.loops.layers.uv.new()
rin, rout, seg = R * 1.11, R * 2.33, 256
inner = [bm.verts.new((math.cos(2*math.pi*i/seg)*rin, math.sin(2*math.pi*i/seg)*rin, 0)) for i in range(seg)]
outer = [bm.verts.new((math.cos(2*math.pi*i/seg)*rout, math.sin(2*math.pi*i/seg)*rout, 0)) for i in range(seg)]
for i in range(seg):
    j = (i + 1) % seg
    f = bm.faces.new((inner[i], outer[i], outer[j], inner[j]))
    for loop in f.loops:
        loop[uv].uv = (0.0 if loop.vert in inner else 1.0, 0.5)
bm.to_mesh(mesh)
rings = bpy.data.objects.new("rings", mesh)
scene.collection.objects.link(rings)
rings.location = pos
rings.rotation_euler = tilt
rm = bpy.data.materials.new("ringmat")
rm.use_nodes = True
nt = rm.node_tree
bsdf = nt.nodes['Principled BSDF']
tex = nt.nodes.new("ShaderNodeTexImage")
tex.image = bpy.data.images.load(os.path.join(src, "8k_saturn_ring_alpha.png"))
tex.extension = 'EXTEND'
nt.links.new(tex.outputs['Color'], bsdf.inputs['Base Color'])
nt.links.new(tex.outputs['Alpha'], bsdf.inputs['Alpha'])
bsdf.inputs['Roughness'].default_value = 1.0
rings.data.materials.append(rm)

# A small grey moon (Titan-ish) and a far one
for az, el, r, d in ((-6, 16, 7.5, 470), (52, 38, 4.0, 520)):
    bpy.ops.mesh.primitive_uv_sphere_add(segments=64, ring_count=32, radius=r, location=place(az, el, d))
    bpy.ops.object.shade_smooth()
    bpy.context.object.data.materials.append(mat_image("moon", os.path.join(src, "2k_moon.jpg")))

# The Sun, off to the side: lights Saturn as a fat crescent-to-half phase and casts the ring shadow
sun = bpy.data.objects.new("sun", bpy.data.lights.new("sun", 'SUN'))
scene.collection.objects.link(sun)
sun.data.energy = 4.2
sun.data.angle = math.radians(0.6)
from mathutils import Vector
p = Vector(pos).normalized()
right = Vector((0, 0, 1)).cross(p).normalized() * -1
toward_sun = (-p * 0.55 + right * 0.8 + _n * 0.55).normalized()
sun.rotation_euler = (-toward_sun).to_track_quat('-Z', 'Y').to_euler()
output(scene, out, samples)
