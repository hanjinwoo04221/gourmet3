"""
Preview renders for the Lizardman rig (Blender 5.1 / 4.x).

Renders frames of an imported `animation.lizardman.*` action with the real
textured mesh, so animation changes can be compared side by side.

Run inside Blender (via the MCP bridge or the Scripting tab):
    exec(compile(open(<this file>).read(), <this file>, 'exec'))
    render_clip("idle", [0, 24, 48, 72], outdir=".../preview_before")

Imported actions use the 5.x layered API on Blender 5.x, so action/slot
assignment is borrowed from lizardman_anim.py rather than re-implemented.
"""
import math
import os
import sys

import bpy
from mathutils import Vector

HERE = os.path.dirname(os.path.abspath(__file__)) if "__file__" in globals() else \
    r"C:\Users\hanjw\Downloads\gourmet2-main\gourmet2-main\tools\blender"
if HERE not in sys.path:
    sys.path.insert(0, HERE)

import lizardman_anim as LA  # noqa: E402  (assign_action / action_curves / PREFIX)

CAM_NAME = "PreviewCam"
LIGHT_NAME = "PreviewSun"
RES = (420, 500)


def isolate_lizardman():
    """Hide every object that is not part of the creature (the default Cube blocks the view)."""
    for obj in bpy.data.objects:
        if obj.type in {"MESH", "ARMATURE"}:
            obj.hide_render = not obj.name.startswith("Lizardman")


def ensure_light():
    """A key sun plus an ambient world, so the texture is readable."""
    scn = bpy.context.scene
    sun = bpy.data.objects.get(LIGHT_NAME)
    if sun is None:
        sun = bpy.data.objects.new(LIGHT_NAME, bpy.data.lights.new(LIGHT_NAME, "SUN"))
        scn.collection.objects.link(sun)
    sun.data.energy = 4.0
    sun.data.angle = math.radians(15)
    # key from the front-left-top (the creature faces -Y)
    sun.rotation_euler = (math.radians(55), 0.0, math.radians(-145))
    try:
        scn.view_settings.view_transform = "Standard"
    except Exception:
        pass


def world_bounds():
    """World-space bounding box of the lizardman mesh, from the evaluated depsgraph."""
    deps = bpy.context.evaluated_depsgraph_get()
    lo = Vector((1e9, 1e9, 1e9))
    hi = Vector((-1e9, -1e9, -1e9))
    for obj in bpy.data.objects:
        if obj.type != "MESH" or not obj.name.startswith("Lizardman"):
            continue
        ev = obj.evaluated_get(deps)
        for corner in ev.bound_box:
            p = ev.matrix_world @ Vector(corner)
            for i in range(3):
                lo[i] = min(lo[i], p[i])
                hi[i] = max(hi[i], p[i])
    return lo, hi


# Framing measured once from the rest pose, so a before/after pair is rendered
# from exactly the same camera. Measuring the live pose instead would move the
# camera whenever the animation changes, which silently invalidates the comparison.
_FRAMING = {}


def freeze_framing():
    """Measure the rest-pose bounds once and reuse them for every later render."""
    arm = bpy.data.objects.get(LA.ARMATURE)
    saved = None
    if arm is not None and arm.animation_data is not None:
        saved = arm.animation_data.action
        arm.animation_data.action = None
    poses = []
    if arm is not None:
        for pb in arm.pose.bones:
            poses.append((pb, tuple(pb.rotation_euler), tuple(pb.location)))
            pb.rotation_euler = (0.0, 0.0, 0.0)
            pb.location = (0.0, 0.0, 0.0)
    bpy.context.view_layer.update()
    lo, hi = world_bounds()
    _FRAMING["center"] = (lo + hi) / 2.0
    _FRAMING["span"] = hi - lo
    for pb, rot, loc in poses:
        pb.rotation_euler = rot
        pb.location = loc
    if arm is not None and arm.animation_data is not None and saved is not None:
        arm.animation_data.action = saved
    bpy.context.view_layer.update()
    return {"center": [round(v, 4) for v in _FRAMING["center"]],
            "span": [round(v, 4) for v in _FRAMING["span"]]}


def setup_camera(azimuth_deg=35.0, elevation_deg=12.0, margin=1.15):
    """A 3/4 front camera framing the whole creature (it faces -Y in Blender).

    azimuth 0 = straight in front of the face, 90 = the creature's left side.
    Uses the frozen rest-pose framing when `freeze_framing` has been called.
    """
    scn = bpy.context.scene
    cam = bpy.data.objects.get(CAM_NAME)
    if cam is None:
        cam = bpy.data.objects.new(CAM_NAME, bpy.data.cameras.new(CAM_NAME))
        scn.collection.objects.link(cam)
    cam.data.lens = 50

    if _FRAMING:
        center, span = _FRAMING["center"], _FRAMING["span"]
    else:
        lo, hi = world_bounds()
        center, span = (lo + hi) / 2.0, hi - lo
    # Fit the largest on-screen extent (height, or width seen at this azimuth).
    width_seen = abs(span.x * math.cos(math.radians(azimuth_deg))) + abs(span.y * math.sin(math.radians(azimuth_deg)))
    extent = max(span.z, width_seen, 0.3) * margin
    half_fov = math.atan(18.0 / cam.data.lens)  # 36 mm sensor
    dist = extent / (2.0 * math.tan(half_fov))

    az = math.radians(azimuth_deg)
    el = math.radians(elevation_deg)
    direction = Vector((math.sin(az) * math.cos(el), -math.cos(az) * math.cos(el), math.sin(el)))
    cam.location = center + direction * dist
    cam.rotation_euler = (center - cam.location).to_track_quat("-Z", "Y").to_euler()

    scn.camera = cam
    scn.render.engine = "BLENDER_EEVEE"
    scn.render.resolution_x, scn.render.resolution_y = RES
    scn.render.resolution_percentage = 100
    scn.render.film_transparent = False
    if scn.world is None:
        scn.world = bpy.data.worlds.new("World")
    scn.world.use_nodes = True
    bg = scn.world.node_tree.nodes.get("Background")
    if bg:
        bg.inputs[0].default_value = (0.16, 0.17, 0.20, 1.0)
        bg.inputs[1].default_value = 1.0
    isolate_lizardman()
    ensure_light()


def render_clip(clip, frames, outdir=None, azimuth_deg=35.0, tag=None, samples=16):
    """Render `frames` of a clip to <outdir>/<clip>_f###.png; returns the file paths."""
    outdir = outdir or os.path.join(HERE, "preview")
    os.makedirs(outdir, exist_ok=True)
    arm = bpy.data.objects[LA.ARMATURE]
    action = bpy.data.actions[LA.PREFIX + clip]
    LA.assign_action(arm, action)
    bpy.context.scene.frame_start = int(action.frame_start)
    bpy.context.scene.frame_end = int(action.frame_end)
    try:
        bpy.context.scene.eevee.taa_render_samples = samples
    except Exception:
        pass
    setup_camera(azimuth_deg=azimuth_deg)

    paths = []
    for frame in frames:
        bpy.context.scene.frame_set(int(frame))
        name = "%s_%s_f%03d.png" % (tag, clip, int(frame)) if tag else "%s_f%03d.png" % (clip, int(frame))
        path = os.path.join(outdir, name)
        bpy.context.scene.render.filepath = path
        bpy.ops.render.render(write_still=True)
        paths.append(path)
    return paths


def contact_sheet(paths, out_path, cols=4):
    """Tile rendered PNGs into one image using Blender's compositor-free image API."""
    imgs = [bpy.data.images.load(p) for p in paths]
    w, h = imgs[0].size
    rows = (len(imgs) + cols - 1) // cols
    sheet = bpy.data.images.new("sheet", width=w * cols, height=h * rows, alpha=False)
    buf = [0.0] * (w * cols * h * rows * 4)
    for index, img in enumerate(imgs):
        px = list(img.pixels)
        cx = (index % cols) * w
        cy = (rows - 1 - index // cols) * h
        for y in range(h):
            src = y * w * 4
            dst = ((cy + y) * (w * cols) + cx) * 4
            buf[dst:dst + w * 4] = px[src:src + w * 4]
    sheet.pixels = buf
    sheet.filepath_raw = out_path
    sheet.file_format = "PNG"
    sheet.save()
    return out_path
