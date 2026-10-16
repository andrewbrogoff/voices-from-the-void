"""Render a Java block/item model JSON to an isometric PNG with headless Blender.

Usage:
  blender -b --factory-startup --python render_model.py -- <assets_root> <ns:block/model> <out.png> [size]

<assets_root> is the folder containing <ns>/models and <ns>/textures (i.e. an `assets/` dir).
Parents in the `minecraft` namespace are read from the vanilla 26.2 client jar (local preview only).
Faces get Minecraft's fixed directional shading (up 1.0, N/S 0.8, E/W 0.6, down 0.5) and nearest filtering.
ponytail: no element rotation, no tint, no animated textures (first frame only); add if a prototype needs them.
"""

import json
import os
import sys
import tempfile
import zipfile

import bpy

VANILLA = os.path.expanduser("~/.gradle/caches/neoformruntime/artifacts/minecraft_26.2_client.jar")
SHADE = {"up": 1.0, "down": 0.5, "north": 0.8, "south": 0.8, "east": 0.6, "west": 0.6}

args = sys.argv[sys.argv.index("--") + 1 :]
root, model_id, out = args[0], args[1], args[2]
size = int(args[3]) if len(args) > 3 else 512
jar = None  # opened on first fallback, so self-contained assets need no vanilla jar
tmp = tempfile.mkdtemp()


def split(rid):
    ns, path = rid.split(":", 1) if ":" in rid else ("minecraft", rid)
    return ns, path


def read(ns, rel):
    local = os.path.join(root, ns, rel)
    if os.path.exists(local):
        return open(local, "rb").read()
    global jar
    jar = jar or zipfile.ZipFile(VANILLA)
    return jar.read(f"assets/{ns}/{rel}")


def load_model(rid):
    ns, path = split(rid)
    m = json.loads(read(ns, f"models/{path}.json"))
    textures, elements = dict(m.get("textures", {})), m.get("elements")
    if "parent" in m:
        pt, pe = load_model(m["parent"])
        textures = {**pt, **textures}
        elements = elements if elements is not None else pe
    return textures, elements


def resolve(tex, ref, depth=0):
    while ref.startswith("#") and depth < 10:
        ref, depth = tex.get(ref[1:], "minecraft:missingno"), depth + 1
    return ref


def texture_file(rid):
    ns, path = split(rid)
    dst = os.path.join(tmp, ns, f"{path}.png")  # the id's own structure: no two ids collide
    if not os.path.exists(dst):
        os.makedirs(os.path.dirname(dst), exist_ok=True)
        open(dst, "wb").write(read(ns, f"textures/{path}.png"))
    return dst


materials = {}


def material(rid, shade):
    key = (rid, shade)
    if key in materials:
        return materials[key]
    mat = bpy.data.materials.new(f"{rid}@{shade}")
    mat.use_nodes = True
    nt = mat.node_tree
    nt.nodes.clear()
    img = nt.nodes.new("ShaderNodeTexImage")
    img.image = bpy.data.images.load(texture_file(rid))
    img.interpolation = "Closest"
    h = img.image.size[1]
    w = img.image.size[0]
    if h > w:  # animated strip: show first frame
        mapping = nt.nodes.new("ShaderNodeMapping")
        coord = nt.nodes.new("ShaderNodeTexCoord")
        mapping.inputs["Scale"].default_value[1] = w / h
        mapping.inputs["Location"].default_value[1] = 1 - w / h
        nt.links.new(coord.outputs["UV"], mapping.inputs["Vector"])
        nt.links.new(mapping.outputs["Vector"], img.inputs["Vector"])
    emit = nt.nodes.new("ShaderNodeEmission")
    emit.inputs["Strength"].default_value = shade
    transp = nt.nodes.new("ShaderNodeBsdfTransparent")
    mix = nt.nodes.new("ShaderNodeMixShader")
    outn = nt.nodes.new("ShaderNodeOutputMaterial")
    nt.links.new(img.outputs["Color"], emit.inputs["Color"])
    nt.links.new(img.outputs["Alpha"], mix.inputs["Fac"])
    nt.links.new(transp.outputs[0], mix.inputs[1])
    nt.links.new(emit.outputs[0], mix.inputs[2])
    nt.links.new(mix.outputs[0], outn.inputs["Surface"])
    mat.use_backface_culling = True  # Minecraft culls back faces too
    if hasattr(mat, "surface_render_method"):
        mat.surface_render_method = "DITHERED"  # depth-correct; BLENDED mis-sorts faces
    else:
        mat.blend_method = "HASHED"
    materials[key] = mat
    return mat


def corners(face, a, b):
    x1, y1, z1 = a
    x2, y2, z2 = b
    # top-left, top-right, bottom-right, bottom-left as seen from outside (vanilla UV orientation)
    return {
        "north": [(x2, y2, z1), (x1, y2, z1), (x1, y1, z1), (x2, y1, z1)],
        "south": [(x1, y2, z2), (x2, y2, z2), (x2, y1, z2), (x1, y1, z2)],
        "east": [(x2, y2, z2), (x2, y2, z1), (x2, y1, z1), (x2, y1, z2)],
        "west": [(x1, y2, z1), (x1, y2, z2), (x1, y1, z2), (x1, y1, z1)],
        "up": [(x1, y2, z1), (x2, y2, z1), (x2, y2, z2), (x1, y2, z2)],
        "down": [(x1, y1, z2), (x2, y1, z2), (x2, y1, z1), (x1, y1, z1)],
    }[face]


def default_uv(face, a, b):
    x1, y1, z1 = a
    x2, y2, z2 = b
    return {
        "north": [16 - x2, 16 - y2, 16 - x1, 16 - y1],
        "south": [x1, 16 - y2, x2, 16 - y1],
        "east": [16 - z2, 16 - y2, 16 - z1, 16 - y1],
        "west": [z1, 16 - y2, z2, 16 - y1],
        "up": [x1, z1, x2, z2],
        "down": [x1, 16 - z2, x2, 16 - z1],
    }[face]


bpy.ops.wm.read_factory_settings(use_empty=True)
textures, elements = load_model(model_id)
if not elements:
    sys.exit(f"{model_id} has no elements (item/generated models are previewed by sheet.py instead)")

verts, faces, uvs, mats = [], [], [], []
for el in elements:
    a, b = el["from"], el["to"]
    for name, f in el["faces"].items():
        u1, v1, u2, v2 = f.get("uv", default_uv(name, a, b))
        uv = [(u1, v1), (u2, v1), (u2, v2), (u1, v2)]
        rot = (f.get("rotation", 0) // 90) % 4
        uv = uv[rot:] + uv[:rot]
        base = len(verts)
        for x, y, z in corners(name, a, b):
            verts.append(((x - 8) / 16, -(z - 8) / 16, y / 16))  # MC (x,y,z) -> Blender (x,-z,y)
        faces.append([base, base + 3, base + 2, base + 1])  # TL,BL,BR,TR = CCW from outside
        uvs.append(uv)
        mats.append(material(resolve(textures, f["texture"]), SHADE[name]))

mesh = bpy.data.meshes.new("model")
mesh.from_pydata(verts, [], faces)
layer = mesh.uv_layers.new()
for poly, quad in zip(mesh.polygons, uvs):
    base = min(poly.vertices)
    for li, vi in zip(poly.loop_indices, poly.vertices):
        u, v = quad[vi - base]  # corner k of the face carries uv k
        layer.data[li].uv = (u / 16, 1 - v / 16)
unique = []
for m in mats:
    if m not in unique:
        unique.append(m)
        mesh.materials.append(m)
for poly, m in zip(mesh.polygons, mats):
    poly.material_index = unique.index(m)
obj = bpy.data.objects.new("model", mesh)
bpy.context.scene.collection.objects.link(obj)

cam_data = bpy.data.cameras.new("cam")
cam_data.type = "ORTHO"
cam_data.ortho_scale = 1.75
cam = bpy.data.objects.new("cam", cam_data)
bpy.context.scene.collection.objects.link(cam)
cam.location = (2.2, -2.2, 2.25)  # looking from the south-east, above: inventory-like iso
cam.rotation_euler = (1.0472, 0, 0.7854)
bpy.context.scene.camera = cam

scene = bpy.context.scene
for engine in ("BLENDER_EEVEE", "BLENDER_EEVEE_NEXT"):
    try:
        scene.render.engine = engine
        break
    except TypeError:
        continue
scene.render.film_transparent = True
scene.render.resolution_x = scene.render.resolution_y = size
scene.view_settings.view_transform = "Standard"
scene.render.filepath = os.path.abspath(out)
bpy.ops.render.render(write_still=True)
print("rendered", out)
