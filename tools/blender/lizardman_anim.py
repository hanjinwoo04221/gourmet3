"""
Lizardman animation tools for Blender (tested on Blender 5.1; written to also work on 4.x).

What it does
------------
* IMPORT  builds the Lizardman in Blender from the mod's own files: an armature from lizardman.geo.json, a textured
          mesh made of the same cubes, and every clip in lizardman.animation.json as a Blender Action.
* EXPORT  writes the Actions back into lizardman.animation.json in the format GeckoLib reads.
* NEW     makes an empty Action (all bones keyed at their rest pose) to animate from.

So the loop is: Import -> pose and key bones in Blender -> Export -> rebuild the mod (or reload resources with F3+T).

How to run
----------
Inside Blender: Scripting tab -> Open this file -> Run Script. A "Lizardman" tab appears in the 3D viewport sidebar
(press N). From a shell (no UI):
    blender -b --python tools/blender/lizardman_anim.py -- import  out.blend
    blender -b file.blend --python tools/blender/lizardman_anim.py -- export

Conventions (what a value in Blender means in game)
---------------------------------------------------
Blender axes: the model faces -Y (front view, numpad 1, shows its face), its right side is -X, up is +Z.
1 Blender unit = 1 block = 16 model pixels. Time: 24 frames per second, frame 0 = time 0.
Every bone's axes are aligned with the world (bones are tiny stubs; the cubes are the visible body), so
the rotation you type on a bone is the rotation of that bone about the world axes through its pivot:
    X rotation  + = the bone's far end swings BACK  (a hanging arm swings backward), - = forward
    Y rotation  + = tilts a hanging limb toward -X  (the right arm out, the left arm in)
    Z rotation  + = turns the bone counter-clockwise seen from above (to the character's left)
Rotation mode is 'XZY' (X applied first, then Z, then Y), which is the order GeckoLib applies its own.
The bones' bind rotations from the geo file (the tail and head tilt) are already in the rest pose you see;
an animation value is added on top of them in game, and the exporter takes them off again.
Location channels are in blocks (exported as pixels), also along the world axes: +Y is backward, +Z up.
Interpolation: Linear -> none; Sine/Cubic/Quint/etc. with an Ease mode -> GeckoLib easing (easeInOutSine ...);
Bezier (Blender's default) -> easeInOutSine. The interpolation set on a keyframe is the one that leads INTO the next
keyframe in Blender, which is exactly how the file stores it (a keyframe's easing is how it is approached).
"""
import collections
import json
import math
import os
import re
import sys

import bmesh
import bpy
from bpy.props import StringProperty
from mathutils import Vector

FPS = 24
PX = 1.0 / 16.0
ARMATURE = "Lizardman"
MESH = "Lizardman_Mesh"
PREFIX = "animation.lizardman."
ASSETS = ("src", "main", "resources", "assets", "gourmet2")
BONE_STUB = 0.12  # bone length in blocks; only for clicking on, they carry no geometry

EASINGS = {
    "Sine": "SINE", "Quad": "QUAD", "Cubic": "CUBIC", "Quart": "QUART", "Quint": "QUINT", "Expo": "EXPO", "Circ": "CIRC",
}
EASE_MODES = {"easeIn": "EASE_IN", "easeOut": "EASE_OUT", "easeInOut": "EASE_IN_OUT"}


def easing_from_name(name):
    """GeckoLib easing name -> (Blender interpolation, easing mode)."""
    if not name or name == "linear":
        return "LINEAR", "AUTO"
    for mode_name, mode in EASE_MODES.items():
        if name.startswith(mode_name):
            kind = EASINGS.get(name[len(mode_name):])
            if kind:
                return kind, mode
    return "LINEAR", "AUTO"


def easing_to_name(interpolation, easing):
    if interpolation == "BEZIER":
        return "easeInOutSine"
    if interpolation == "LINEAR" or interpolation == "CONSTANT":
        return None
    kind = {v: k for k, v in EASINGS.items()}.get(interpolation)
    mode = {"EASE_IN": "easeIn", "EASE_OUT": "easeOut", "EASE_IN_OUT": "easeInOut", "AUTO": "easeInOut"}.get(easing)
    return (mode + kind) if kind and mode else None


# ----------------------------------------------------------------------------------------- paths
def find_project():
    """The mod's project folder: the scene setting, else found by walking up from the .blend or this script."""
    scene = bpy.context.scene
    given = getattr(scene, "gecko_project", "")
    starts = [given, bpy.data.filepath, os.environ.get("GOURMET_PROJECT", "")]
    try:
        starts.append(os.path.abspath(__file__))
    except NameError:
        pass
    for start in starts:
        if not start:
            continue
        path = os.path.abspath(bpy.path.abspath(start))
        for _ in range(8):
            if os.path.isdir(os.path.join(path, *ASSETS)):
                return path
            parent = os.path.dirname(path)
            if parent == path:
                break
            path = parent
    return ""


def asset(project, *parts):
    return os.path.join(project, *ASSETS, *parts)


# -------------------------------------------------------------------------- coordinate conversion
def geo_point(v):
    """A model-space point in pixels (x right-negative, y up, z back) -> Blender coordinates in blocks."""
    return Vector((v[0] * PX, v[2] * PX, v[1] * PX))


def rot_to_blender(fx, fy, fz):
    """A rotation as the file stores it (degrees) -> Blender euler in radians, order XZY."""
    return (math.radians(fx), math.radians(fz), math.radians(-fy))


def rot_from_blender(bx, by, bz):
    return (math.degrees(bx), math.degrees(-bz), math.degrees(by))


def loc_to_blender(fx, fy, fz):
    return (fx * PX, fz * PX, fy * PX)


def loc_from_blender(bx, by, bz):
    return (bx / PX, bz / PX, by / PX)


# ------------------------------------------------------------------------------------- action access
def action_curves(action, arm_obj=None, create=False):
    """The list-like of F-curves of an action, in either the 5.x (layered) or the older API."""
    if hasattr(action, "fcurves"):
        return action.fcurves
    if not action.slots:
        if not create:
            return []
        action.slots.new(id_type="OBJECT", name=arm_obj.name if arm_obj else ARMATURE)
    slot = action.slots[0]
    if not action.layers:
        if not create:
            return []
        action.layers.new("Layer")
    layer = action.layers[0]
    if not layer.strips:
        if not create:
            return []
        layer.strips.new(type="KEYFRAME")
    strip = layer.strips[0]
    bag = strip.channelbag(slot, ensure=create)
    return bag.fcurves if bag else []


def new_fcurve(curves, bone, path_name, index):
    data_path = 'pose.bones["%s"].%s' % (bone, path_name)
    try:
        return curves.new(data_path, index=index, action_group=bone)
    except TypeError:
        return curves.new(data_path, index=index)


def assign_action(arm_obj, action):
    if not arm_obj.animation_data:
        arm_obj.animation_data_create()
    arm_obj.animation_data.action = action
    if hasattr(arm_obj.animation_data, "action_slot") and hasattr(action, "slots") and action.slots:
        try:
            arm_obj.animation_data.action_slot = action.slots[0]
        except Exception:
            pass


# ---------------------------------------------------------------------------------------- the rig
def load_geometry(project):
    with open(asset(project, "geo", "entity", "lizardman.geo.json"), encoding="utf-8") as f:
        return json.load(f)["minecraft:geometry"][0]


def bind_euler(bone):
    r = bone.get("rotation")
    return rot_to_blender(*r) if r else (0.0, 0.0, 0.0)


def build_armature(geo):
    arm_data = bpy.data.armatures.new(ARMATURE)
    arm_data.display_type = "STICK"
    arm_obj = bpy.data.objects.new(ARMATURE, arm_data)
    arm_obj.show_in_front = True
    bpy.context.scene.collection.objects.link(arm_obj)
    bpy.context.view_layer.objects.active = arm_obj
    arm_obj.select_set(True)
    bpy.ops.object.mode_set(mode="EDIT")
    edit = {}
    for bone in geo["bones"]:
        eb = arm_data.edit_bones.new(bone["name"])
        head = geo_point(bone["pivot"])
        eb.head = head
        eb.tail = head + Vector((0.0, BONE_STUB, 0.0))
        eb.roll = 0.0
        edit[bone["name"]] = eb
    for bone in geo["bones"]:
        if bone.get("parent"):
            edit[bone["name"]].parent = edit[bone["parent"]]
    bpy.ops.object.mode_set(mode="POSE")
    for bone in geo["bones"]:
        pb = arm_obj.pose.bones[bone["name"]]
        pb.rotation_mode = "XZY"
        pb.rotation_euler = bind_euler(bone)
        pb["gecko_bind"] = list(bind_euler(bone))
    bpy.ops.object.mode_set(mode="OBJECT")
    return arm_obj


def build_mesh(geo, arm_obj, project):
    bm = bmesh.new()
    uv = bm.loops.layers.uv.new("UVMap")
    width = float(geo["description"].get("texture_width", 128))
    height = float(geo["description"].get("texture_height", 128))
    groups = collections.defaultdict(list)  # bone -> vertex indices (in creation order)
    counter = 0

    def face(bone, corners, outward, uv_of):
        nonlocal counter
        pts = [geo_point(c) for c in corners]
        normal = (pts[1] - pts[0]).cross(pts[2] - pts[0])
        out = geo_point(outward)
        order = [0, 1, 2, 3] if normal.dot(out) > 0 else [3, 2, 1, 0]
        verts = []
        for i in order:
            verts.append(bm.verts.new(pts[i]))
            groups[bone].append(counter)
            counter += 1
        f = bm.faces.new(verts)
        for loop, i in zip(f.loops, order):
            u, v = uv_of(corners[i])
            loop[uv].uv = (u / width, 1.0 - v / height)

    for bone in geo["bones"]:
        for cube in bone.get("cubes", []):
            (ox, oy, oz), (sx, sy, sz) = cube["origin"], cube["size"]
            u0, v0 = cube["uv"]
            x0, y0, z0, x1, y1, z1 = ox, oy, oz, ox + sx, oy + sy, oz + sz
            w, h, d = sx, sy, sz

            def horizontal(u_origin, u_span, v_origin, v_span, u_of, v_of):
                return lambda p: (u_origin + u_span * u_of(p), v_origin + v_span * v_of(p))

            # Bedrock box layout, left to right: east, north, west, south; top and bottom above.
            north = horizontal(u0 + d, w, v0 + d, h, lambda p: (x1 - p[0]) / max(sx, 1e-6), lambda p: (y1 - p[1]) / max(sy, 1e-6))
            south = horizontal(u0 + 2 * d + w, w, v0 + d, h, lambda p: (p[0] - x0) / max(sx, 1e-6), lambda p: (y1 - p[1]) / max(sy, 1e-6))
            east = horizontal(u0, d, v0 + d, h, lambda p: (z1 - p[2]) / max(sz, 1e-6), lambda p: (y1 - p[1]) / max(sy, 1e-6))
            west = horizontal(u0 + d + w, d, v0 + d, h, lambda p: (p[2] - z0) / max(sz, 1e-6), lambda p: (y1 - p[1]) / max(sy, 1e-6))
            up = horizontal(u0 + d, w, v0, d, lambda p: (x1 - p[0]) / max(sx, 1e-6), lambda p: (z1 - p[2]) / max(sz, 1e-6))
            down = horizontal(u0 + d + w, w, v0, d, lambda p: (x1 - p[0]) / max(sx, 1e-6), lambda p: (p[2] - z0) / max(sz, 1e-6))
            name = bone["name"]
            face(name, [(x0, y0, z0), (x1, y0, z0), (x1, y1, z0), (x0, y1, z0)], (0, 0, -1), north)
            face(name, [(x0, y0, z1), (x1, y0, z1), (x1, y1, z1), (x0, y1, z1)], (0, 0, 1), south)
            face(name, [(x1, y0, z0), (x1, y0, z1), (x1, y1, z1), (x1, y1, z0)], (1, 0, 0), east)
            face(name, [(x0, y0, z0), (x0, y0, z1), (x0, y1, z1), (x0, y1, z0)], (-1, 0, 0), west)
            face(name, [(x0, y1, z0), (x1, y1, z0), (x1, y1, z1), (x0, y1, z1)], (0, 1, 0), up)
            face(name, [(x0, y0, z0), (x1, y0, z0), (x1, y0, z1), (x0, y0, z1)], (0, -1, 0), down)

    mesh = bpy.data.meshes.new(MESH)
    bm.to_mesh(mesh)
    bm.free()
    obj = bpy.data.objects.new(MESH, mesh)
    bpy.context.scene.collection.objects.link(obj)
    for bone, indices in groups.items():
        obj.vertex_groups.new(name=bone).add(indices, 1.0, "REPLACE")
    modifier = obj.modifiers.new("Armature", "ARMATURE")
    modifier.object = arm_obj
    obj.parent = arm_obj

    material = bpy.data.materials.new("Lizardman")
    material.use_nodes = True
    texture_path = asset(project, "textures", "entity", "lizardman.png")
    if os.path.exists(texture_path):
        image = bpy.data.images.load(texture_path, check_existing=True)
        node = material.node_tree.nodes.new("ShaderNodeTexImage")
        node.image = image
        node.interpolation = "Closest"
        bsdf = material.node_tree.nodes.get("Principled BSDF")
        if bsdf:
            material.node_tree.links.new(node.outputs["Color"], bsdf.inputs["Base Color"])
            if "Roughness" in bsdf.inputs:
                bsdf.inputs["Roughness"].default_value = 0.9
    mesh.materials.append(material)
    return obj


# ---------------------------------------------------------------------------------- import clips
def parse_vector(frame):
    """A keyframe value as the file writes it: [x,y,z] or {"vector":[...], "easing":...} (or a bare number)."""
    easing = None
    if isinstance(frame, dict):
        easing = frame.get("easing")
        frame = frame.get("vector", frame.get("post", [0, 0, 0]))
    if isinstance(frame, (int, float)):
        frame = [frame, frame, frame]
    return [float(x) for x in frame], easing


def import_action(arm_obj, name, clip, geo_bones):
    old = bpy.data.actions.get(name)
    if old:
        bpy.data.actions.remove(old)
    action = bpy.data.actions.new(name)
    action.use_fake_user = True
    curves = action_curves(action, arm_obj, create=True)
    length = float(clip.get("animation_length", 1.0))
    for bone_name, channels in clip.get("bones", {}).items():
        if bone_name not in arm_obj.pose.bones:
            continue
        bind = bind_euler(geo_bones.get(bone_name, {}))
        for chan, path_name in (("rotation", "rotation_euler"), ("position", "location")):
            frames = channels.get(chan)
            if not frames:
                continue
            keys = sorted(((float(t), parse_vector(v)) for t, v in frames.items()), key=lambda k: k[0])
            fcs = [new_fcurve(curves, bone_name, path_name, i) for i in range(3)]
            points = [[], [], []]
            for time, (vec, easing) in keys:
                if chan == "rotation":
                    values = rot_to_blender(*vec)
                    values = tuple(values[i] + bind[i] for i in range(3))
                else:
                    values = loc_to_blender(*vec)
                for i in range(3):
                    points[i].append((time * FPS, values[i], easing))
            for i, fc in enumerate(fcs):
                for frame, value, _ in points[i]:
                    fc.keyframe_points.insert(frame, value, options={"FAST"})
                fc.update()
                # the easing stored on a keyframe describes the segment leading INTO it, Blender keeps it on the
                # keyframe the segment starts from (indexed, not held: the point list moves as it grows)
                for k in range(len(points[i]) - 1):
                    kp = fc.keyframe_points[k]
                    kp.interpolation, kp.easing = easing_from_name(points[i][k + 1][2])
    action.use_frame_range = True
    action.frame_start = 0
    action.frame_end = max(1.0, round(length * FPS, 3))
    action.use_cyclic = bool(clip.get("loop", False))
    action["gecko_loop"] = bool(clip.get("loop", False))
    return action


def import_all(project):
    geo = load_geometry(project)
    with open(asset(project, "animations", "entity", "lizardman.animation.json"), encoding="utf-8") as f:
        clips = json.load(f)["animations"]
    for name in (MESH, ARMATURE):
        old = bpy.data.objects.get(name)
        if old:
            bpy.data.objects.remove(old, do_unlink=True)
    bpy.context.scene.render.fps = FPS
    bpy.context.scene.render.fps_base = 1.0
    bpy.context.scene.frame_start = 0
    arm_obj = build_armature(geo)
    build_mesh(geo, arm_obj, project)
    geo_bones = {b["name"]: b for b in geo["bones"]}
    for name, clip in clips.items():
        import_action(arm_obj, name, clip, geo_bones)
    idle = bpy.data.actions.get(PREFIX + "idle")
    if idle:
        assign_action(arm_obj, idle)
        bpy.context.scene.frame_end = int(idle.frame_end)
    bpy.context.scene["gecko_project"] = project
    return len(clips)


# ---------------------------------------------------------------------------------------- export
BONE_PATH = re.compile(r'pose\.bones\["(.+)"\]\.(rotation_euler|location)$')


def fmt(t):
    return repr(round(t, 4))


def export_action(action, geo_bones):
    per_bone = collections.defaultdict(dict)
    for fc in action_curves(action):
        m = BONE_PATH.match(fc.data_path)
        if m:
            per_bone[m.group(1)].setdefault(m.group(2), {})[fc.array_index] = fc
    bones = collections.OrderedDict()
    for bone_name, chans in per_bone.items():
        out = collections.OrderedDict()
        for path_name, chan_name in (("rotation_euler", "rotation"), ("location", "position")):
            fcs = chans.get(path_name)
            if not fcs:
                continue
            exact = {round(kp.co[0], 3): kp.co[0] for fc in fcs.values() for kp in fc.keyframe_points}
            frames = sorted(exact)
            if not frames:
                continue
            bind = bind_euler(geo_bones.get(bone_name, {}))
            table = collections.OrderedDict()
            previous = None
            for frame in frames:
                raw = []
                for i in range(3):
                    fc = fcs.get(i)
                    raw.append(fc.evaluate(exact[frame]) if fc else (bind[i] if path_name == "rotation_euler" else 0.0))
                if path_name == "rotation_euler":
                    raw = [raw[i] - bind[i] for i in range(3)]
                    vec = rot_from_blender(*raw)
                else:
                    vec = loc_from_blender(*raw)
                vec = [round(c, 3) + 0.0 for c in vec]
                easing = None
                if previous is not None:
                    for fc in fcs.values():
                        prior = [kp for kp in fc.keyframe_points if abs(kp.co[0] - previous) < 1e-3]
                        if prior:
                            easing = easing_to_name(prior[0].interpolation, prior[0].easing)
                            break
                table[fmt(exact[frame] / FPS)] = (
                    collections.OrderedDict([("vector", vec), ("easing", easing)]) if easing else vec)
                previous = frame
            out[chan_name] = table
        if out:
            bones[bone_name] = out
    length = action.frame_end / FPS if action.use_frame_range else max(
        (kp.co[0] for fc in action_curves(action) for kp in fc.keyframe_points), default=FPS) / FPS
    return collections.OrderedDict([
        ("loop", bool(action.get("gecko_loop", action.use_cyclic))),
        ("animation_length", round(length, 4)),
        ("bones", bones)])


def export_all(project, out=None):
    geo = load_geometry(project)
    geo_bones = {b["name"]: b for b in geo["bones"]}
    path = asset(project, "animations", "entity", "lizardman.animation.json")
    with open(path, encoding="utf-8") as f:
        data = json.load(f, object_pairs_hook=collections.OrderedDict)
    if out is None and os.path.exists(path) and not os.path.exists(path + ".bak"):
        with open(path + ".bak", "w", encoding="utf-8") as f:
            json.dump(data, f, indent=1)
    clips = collections.OrderedDict()
    for action in sorted(bpy.data.actions, key=lambda a: a.name):
        if action.name.startswith(PREFIX):
            clips[action.name] = export_action(action, geo_bones)
    data["animations"] = clips
    path = out or path
    with open(path, "w", encoding="utf-8") as f:
        json.dump(data, f, indent=1)
    return path, len(clips)


def new_action(arm_obj, name):
    action = bpy.data.actions.new(PREFIX + name)
    action.use_fake_user = True
    curves = action_curves(action, arm_obj, create=True)
    for pb in arm_obj.pose.bones:
        for path_name, values in (("rotation_euler", tuple(pb.get("gecko_bind", (0.0, 0.0, 0.0)))), ("location", (0.0, 0.0, 0.0))):
            for i in range(3):
                fc = new_fcurve(curves, pb.name, path_name, i)
                fc.keyframe_points.insert(0, values[i], options={"FAST"})
    action.use_frame_range = True
    action.frame_start = 0
    action.frame_end = FPS
    return action


# ------------------------------------------------------------------------------------------ UI
class GECKO_OT_import(bpy.types.Operator):
    bl_idname = "gecko.import_lizardman"
    bl_label = "Import model + animations"
    bl_description = "Rebuild the rig, mesh and every animation from the mod's files (replaces unsaved Blender edits)"

    def execute(self, context):
        project = find_project()
        if not project:
            self.report({"ERROR"}, "Set the project folder first")
            return {"CANCELLED"}
        count = import_all(project)
        self.report({"INFO"}, "Imported %d animations" % count)
        return {"FINISHED"}


class GECKO_OT_export(bpy.types.Operator):
    bl_idname = "gecko.export_animations"
    bl_label = "Export animations to the mod"
    bl_description = "Write every animation.lizardman.* action to lizardman.animation.json (a .bak copy is kept once)"

    def execute(self, context):
        project = find_project()
        if not project:
            self.report({"ERROR"}, "Set the project folder first")
            return {"CANCELLED"}
        path, count = export_all(project)
        self.report({"INFO"}, "Exported %d animations to %s" % (count, path))
        return {"FINISHED"}


class GECKO_OT_new(bpy.types.Operator):
    bl_idname = "gecko.new_animation"
    bl_label = "New animation"
    bl_description = "Create an empty animation.lizardman.<name> action with every bone keyed at rest"
    name: StringProperty(name="Name", default="my_move")

    def invoke(self, context, event):
        return context.window_manager.invoke_props_dialog(self)

    def execute(self, context):
        arm = bpy.data.objects.get(ARMATURE)
        if not arm:
            self.report({"ERROR"}, "Import the model first")
            return {"CANCELLED"}
        assign_action(arm, new_action(arm, self.name))
        return {"FINISHED"}


class GECKO_PT_panel(bpy.types.Panel):
    bl_label = "Lizardman"
    bl_idname = "GECKO_PT_lizardman"
    bl_space_type = "VIEW_3D"
    bl_region_type = "UI"
    bl_category = "Lizardman"

    def draw(self, context):
        col = self.layout.column(align=True)
        col.prop(context.scene, "gecko_project", text="Project")
        col.operator("gecko.import_lizardman", icon="IMPORT")
        col.operator("gecko.new_animation", icon="ADD")
        col.operator("gecko.export_animations", icon="EXPORT")


CLASSES = (GECKO_OT_import, GECKO_OT_export, GECKO_OT_new, GECKO_PT_panel)


def register():
    for cls in CLASSES:
        try:
            bpy.utils.unregister_class(cls)
        except Exception:
            pass
        bpy.utils.register_class(cls)
    bpy.types.Scene.gecko_project = StringProperty(name="Project folder", subtype="DIR_PATH", default="")


def main_cli(argv):
    project = find_project() or os.environ.get("GOURMET_PROJECT", "")
    if not project:
        print("lizardman_anim: project folder not found (set GOURMET_PROJECT)")
        return
    if argv and argv[0] == "import":
        print("imported", import_all(project))
        if len(argv) > 1:
            bpy.ops.wm.save_as_mainfile(filepath=os.path.abspath(argv[1]))
    elif argv and argv[0] == "export":
        print("exported", export_all(project, argv[1] if len(argv) > 1 else None))
    elif argv and argv[0] == "roundtrip":
        import_all(project)
        print("roundtrip", export_all(project, argv[1]))


if __name__ == "__main__":
    register()
    if "--" in sys.argv:
        main_cli(sys.argv[sys.argv.index("--") + 1:])
