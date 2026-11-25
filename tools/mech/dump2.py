import bpy, json, sys
out = sys.argv[sys.argv.index('--')+1]
o = bpy.data.objects['f22']
dg = bpy.context.evaluated_depsgraph_get()
eo = o.evaluated_get(dg)
me = eo.to_mesh()
me.calc_loop_triangles()
mw = o.matrix_world; nm = mw.to_3x3().inverted().transposed()
cn = me.corner_normals
V = [list(mw @ v.co) for v in me.vertices]
tris = []
for t in me.loop_triangles:
    ns = []
    for li in t.loops:
        n = (nm @ cn[li].vector).normalized(); ns.append([round(c,5) for c in n])
    tris.append({'v': list(t.vertices), 'n': ns, 'm': t.material_index})
mats = [m.name if m else 'None' for m in me.materials]
json.dump({'verts': V, 'tris': tris, 'mats': mats}, open(out, 'w'))
print("DONE", len(V), len(tris))
