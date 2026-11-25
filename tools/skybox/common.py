import bpy, math, sys

def args():
    a = sys.argv
    return a[a.index("--") + 1:] if "--" in a else []

def reset():
    bpy.ops.wm.read_factory_settings(use_empty=True)

def gpu(scene):
    scene.render.engine = 'CYCLES'
    prefs = bpy.context.preferences.addons['cycles'].preferences
    for kind in ('OPTIX', 'CUDA'):
        try:
            prefs.compute_device_type = kind
            prefs.get_devices()
            ok = False
            for d in prefs.devices:
                d.use = d.type == kind
                ok = ok or d.use
            if ok:
                scene.cycles.device = 'GPU'
                print("device", kind)
                return
        except Exception as e:
            print("no", kind, e)
    scene.cycles.device = 'CPU'

def pano_camera(scene, w, h, height=1.7):
    cam_data = bpy.data.cameras.new("pano")
    cam_data.type = 'PANO'
    try:
        cam_data.panorama_type = 'EQUIRECTANGULAR'
    except Exception:
        cam_data.cycles.panorama_type = 'EQUIRECTANGULAR'
    cam = bpy.data.objects.new("pano", cam_data)
    scene.collection.objects.link(cam)
    # looks along +Y (Blender) = image centre
    cam.location = (0, 0, height)
    cam.rotation_euler = (math.radians(90), 0, 0)
    scene.camera = cam
    scene.render.resolution_x = w
    scene.render.resolution_y = h
    scene.render.resolution_percentage = 100
    return cam

def world_image(scene, path, strength=1.0, rot_z=0.0):
    world = bpy.data.worlds.new("w")
    scene.world = world
    world.use_nodes = True
    nt = world.node_tree
    nt.nodes.clear()
    tex = nt.nodes.new("ShaderNodeTexEnvironment")
    tex.image = bpy.data.images.load(path)
    mapping = nt.nodes.new("ShaderNodeMapping")
    coord = nt.nodes.new("ShaderNodeTexCoord")
    mapping.inputs['Rotation'].default_value[2] = rot_z
    bg = nt.nodes.new("ShaderNodeBackground")
    bg.inputs['Strength'].default_value = strength
    out = nt.nodes.new("ShaderNodeOutputWorld")
    nt.links.new(coord.outputs['Generated'], mapping.inputs['Vector'])
    nt.links.new(mapping.outputs['Vector'], tex.inputs['Vector'])
    nt.links.new(tex.outputs['Color'], bg.inputs['Color'])
    nt.links.new(bg.outputs['Background'], out.inputs['Surface'])
    return tex

def output(scene, path, samples, view='Standard', exposure=0.0):
    scene.cycles.samples = samples
    scene.cycles.use_denoising = True
    scene.view_settings.view_transform = view
    scene.view_settings.exposure = exposure
    scene.render.image_settings.file_format = 'PNG'
    scene.render.image_settings.color_depth = '8'
    scene.render.filepath = path
    bpy.ops.render.render(write_still=True)
