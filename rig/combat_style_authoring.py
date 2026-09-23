# Shared framework for authoring this mod's Epic Fight combat-style animation clips.
#
# A combat style gets its own small script next to this one (swift_authoring.py,
# capoeira_authoring.py) that declares the style's poses and calls export_clips()/preview().
#
# Run a style script inside Blender with rig/EpicFight Animation Rig.blend open:
#   exec(open(r"...\rig\capoeira_authoring.py", encoding="utf-8").read())
# then call preview("basic1", 7) etc.
#
# Headless full export:
#   blender --background "EpicFight Animation Rig.blend" --python capoeira_authoring.py
#
# Poses are authored as per-bone Euler angles (degrees) in each bone's rest-local frame, in world
# terms (the character faces +Y, +X is its right, +Z is up):
#
#   Root / Torso / Chest / Head      (these bones point up)
#       rx  lean back (+) / forward (-)
#       ry  turn left (+) / right (-)          a full 360 ends where it started, so it is a safe spin
#       rz  tilt toward the character's left (+) / right (-)
#   Thigh_* / Leg_* / Arm_* / Hand_*  (these bones point down)
#       rx  swing forward (+) / backward (-)   on a Leg this is knee flex: negative bends the knee
#       ry  twist about the bone's own axis
#       rz  swing toward the character's left (+) / right (-)
#   Shoulder_R / Shoulder_L           (they point out and down along the clavicle)
#       ry  roll the shoulder to the front (+) / back (-) — the sign is mirrored: the left shoulder
#           rolls forward with -ry, matching the two sides' rest frames
#
# 'root_pos' moves the Root bone's head in armature space (x right, y forward, z up); the rest
# height is about 0.764.
#
# Clips interpolate between keyframe specs and bake one matrix keyframe per 24 fps frame, then
# export the Epic Fight JSON format: each bone's transform is its pose matrix relative to its
# parent's pose matrix.
import bpy
import json
import math
import os
from mathutils import Matrix, Vector, Euler

FPS = 24.0

# The Knee (foot) bones sit at the ankle in the rest pose; this is their height there.
GROUND_Z = 0.012

# Set True by a style whose poses lean and lunge a lot: then every baked frame (not just the keys)
# gets the same lift grounded() applies, so interpolation never pushes a foot through the floor.
# Off for swift so its committed clips stay byte-for-byte reproducible.
FLOOR_CLAMP = False

BONE_ORDER = ["Root", "Thigh_R", "Leg_R", "Knee_R", "Thigh_L", "Leg_L", "Knee_L",
              "Torso", "Chest", "Head", "Shoulder_R", "Arm_R", "Hand_R", "Tool_R",
              "Elbow_R", "Shoulder_L", "Arm_L", "Hand_L", "Tool_L", "Elbow_L"]

try:
    _HERE = os.path.dirname(os.path.abspath(__file__))
except NameError:  # exec'd inside Blender's interactive console
    _HERE = r"C:\Users\hanjw\Downloads\gourmet2-main\gourmet2-main\rig"
RIG_BLEND = os.path.join(_HERE, "EpicFight Animation Rig.blend")

arm = bpy.data.objects["Armature"]
REST = {b.name: b.matrix_local.copy() for b in arm.data.bones}
REST_LOCAL = {}
for _name in BONE_ORDER:
    _bone = arm.data.bones[_name]
    if _bone.parent is None:
        REST_LOCAL[_name] = _bone.matrix_local.copy()
    else:
        REST_LOCAL[_name] = _bone.parent.matrix_local.inverted() @ _bone.matrix_local

# Length of the shin bone; the Knee (foot) bone is authored at the knee joint and slides down the
# shin this far so it sits at the ankle, pointing forward.
KNEE_BASIS_T = REST_LOCAL["Knee_R"].to_3x3().inverted() @ Vector((0.0, 0.3747, 0.0))
ROOT_REST_POS = REST["Root"].to_translation()


def clip_entry(clips, name):
    """A clips entry is (n_frames, keys) or (n_frames, keys, key_step)."""
    entry = clips[name]
    return entry[0], entry[1], (entry[2] if len(entry) > 2 else 3)


def spec_to_locals(spec):
    """One keyframe spec {bone: (rx, ry, rz), 'root_pos': (x, y, z)} -> parent-relative matrices.

    root_pos is the Root bone's head in absolute armature space (x right, y forward, z up);
    it defaults to the rest position. """
    locals_ = {}
    for name in BONE_ORDER:
        basis = Matrix.Identity(4)
        rot = spec.get(name)
        if rot:
            basis = Euler(tuple(math.radians(a) for a in rot), 'XYZ').to_matrix().to_4x4()
        if name == "Root":
            desired = Vector(spec.get("root_pos", tuple(ROOT_REST_POS)))
            basis.translation = REST["Root"].to_3x3().inverted() @ (desired - ROOT_REST_POS)
        if name.startswith("Knee"):
            basis.translation = KNEE_BASIS_T.copy()
        locals_[name] = REST_LOCAL[name] @ basis
    return locals_


def ease(u, mode):
    if mode == "lin":
        return u
    if mode == "snap":  # slow start, explosive finish
        return u * u
    if mode == "settle":  # fast start, long follow-through
        return 1.0 - (1.0 - u) * (1.0 - u)
    return u * u * (3.0 - 2.0 * u)  # smooth


def bake_frames(keys, n_frames):
    """keys: [(frame, spec, ease_mode_of_segment_starting_here)] -> per-frame locals."""
    frames = []
    for f in range(n_frames + 1):
        k0 = keys[0]
        k1 = keys[-1]
        for i in range(len(keys) - 1):
            if keys[i][0] <= f <= keys[i + 1][0]:
                k0, k1 = keys[i], keys[i + 1]
                break
        span = max(k1[0] - k0[0], 1)
        u = ease(min(max((f - k0[0]) / span, 0.0), 1.0), k0[2] if len(k0) > 2 else "smooth")
        spec = {}
        all_bones = set(k0[1]) | set(k1[1])
        for bone in all_bones:
            a = k0[1].get(bone)
            b = k1[1].get(bone)
            if a is None and b is None:
                continue
            if a is None:
                a = b
            if b is None:
                b = a
            spec[bone] = tuple(a[j] + (b[j] - a[j]) * u for j in range(3))
        ra = k0[1].get("root_pos", tuple(ROOT_REST_POS))
        rb = k1[1].get("root_pos", tuple(ROOT_REST_POS))
        spec["root_pos"] = tuple(ra[j] + (rb[j] - ra[j]) * u for j in range(3))
        locals_ = spec_to_locals(spec)
        if FLOOR_CLAMP:
            deficit = GROUND_Z - min(ankle_zs(locals_))
            if deficit > 0.0:
                spec["root_pos"] = (spec["root_pos"][0], spec["root_pos"][1], spec["root_pos"][2] + deficit)
                locals_ = spec_to_locals(spec)
        frames.append(locals_)
    return frames


def write_clip(name, keys, n_frames, out_dir, step=3):
    """Bake the clip and write it as Epic Fight JSON. 'step' is the key spacing inside a segment:
    3 matches the hand-made clips' size, use 2 or 1 for clips with fast spins so the interpolation
    between exported keys never has to travel far."""
    frames = bake_frames(keys, n_frames)
    # Sparse keys: segment boundaries plus every 'step'th frame inside them (captures the easing
    # curve, keeps files near the size of the hand-made clips).
    boundaries = {k[0] for k in keys} | {0, n_frames}
    wanted = sorted(boundaries | {f for f in range(n_frames + 1) if f % step == 0})
    channels = []
    for bone in BONE_ORDER:
        times = [round(f / FPS, 4) for f in wanted]
        transforms = []
        for f in wanted:
            m = frames[f][bone]
            flat = []
            for row in range(4):
                for col in range(4):
                    flat.append(round(m[row][col], 6))
            transforms.append(flat)
        channels.append({"name": bone, "time": times, "transform": transforms})
    os.makedirs(out_dir, exist_ok=True)
    path = os.path.join(out_dir, name + ".json")
    with open(path, "w") as fh:
        json.dump({"animation": channels}, fh, indent=4)
    return path


def export_clips(clips, out_dir):
    out = []
    for name in clips:
        n_frames, keys, step = clip_entry(clips, name)
        out.append(write_clip(name, keys, n_frames, out_dir, step))
    return out


def apply_locals(locals_):
    """Put these parent-relative local matrices on the rig (parents first).

    matrix_basis is set directly instead of going through pb.matrix: the pb.matrix
    setter reads the parent's pose through the (stale) depsgraph between updates.
    """
    world = {}
    for bone_name in BONE_ORDER:
        pb = arm.pose.bones[bone_name]
        if pb.parent is None:
            world[bone_name] = locals_[bone_name].copy()
            pb.matrix_basis = REST_LOCAL[bone_name].inverted() @ locals_[bone_name]
        else:
            parent_world = world[pb.parent.name]
            world[bone_name] = parent_world @ locals_[bone_name]
            pb.matrix_basis = (parent_world @ REST_LOCAL[bone_name]).inverted() @ world[bone_name]
    bpy.context.view_layer.update()


def apply_clip(clips, name):
    """Put the clip's first frame pose on the rig (for previews)."""
    n_frames, keys, _step = clip_entry(clips, name)
    apply_locals(bake_frames(keys, n_frames)[0])


def apply_frame(clips, name, frame):
    n_frames, keys, _step = clip_entry(clips, name)
    frame = max(0, min(frame, n_frames))
    apply_locals(bake_frames(keys, n_frames)[frame])


def ankle_zs(locals_):
    """World heights of the two Knee (foot) bones with these locals applied."""
    apply_locals(locals_)
    return [arm.pose.bones[name].matrix.to_translation().z for name in ("Knee_R", "Knee_L")]


def ankle_heights(spec):
    """World height of each Knee (foot) bone in this pose. They hang off the thigh, so a bent knee
    does not move them: they are the ankle you would get with the shin straight, which is what you
    want to plant on the floor."""
    apply_locals(spec_to_locals(spec))
    return {name: arm.pose.bones[name].matrix.to_translation().z for name in ("Knee_R", "Knee_L")}


def grounded(spec, target=GROUND_Z, feet=("Knee_R", "Knee_L")):
    """Return a copy of spec with root_pos moved vertically so the lowest of 'feet' is at 'target'.

    There is no IK on this rig: authoring a knee bend or a lunge otherwise leaves the foot hanging
    above the floor (or buried in it), because root_pos is the only thing that moves the hips.
    Poses that are meant to be off the ground (a jump apex, the middle of a cartwheel) must not be
    passed through here."""
    heights = ankle_heights(spec)
    lift = target - min(heights[f] for f in feet)
    out = dict(spec)
    root_pos = list(out.get("root_pos", tuple(ROOT_REST_POS)))
    root_pos[2] += lift
    out["root_pos"] = tuple(root_pos)
    return out


def joint_positions(names=("Root", "Torso", "Head", "Hand_R", "Hand_L", "Knee_R", "Knee_L")):
    """Current pose's bone head/tail world positions — handy for sanity-checking a pose."""
    out = {}
    for name in names:
        pb = arm.pose.bones[name]
        out[name] = {"head": [round(v, 3) for v in pb.matrix.to_translation()],
                     "tail": [round(v, 3) for v in (pb.matrix @ Vector((0.0, pb.length, 0.0)))]}
    return out


def build_pose_preview():
    scn = bpy.context.scene
    name = "PosePreview"
    old = bpy.data.objects.get(name)
    if old:
        bpy.data.objects.remove(old, do_unlink=True)
    mesh = bpy.data.meshes.new(name)
    obj = bpy.data.objects.new(name, mesh)
    scn.collection.objects.link(obj)
    verts, faces = [], []
    for pb in arm.pose.bones:
        head = pb.matrix.to_translation()
        tail = pb.matrix @ Vector((0.0, pb.length, 0.0))
        d = tail - head
        length = d.length
        if length < 1e-5:
            length, d = 0.05, Vector((0, 0, 1))
        d.normalize()
        up = Vector((0, 0, 1))
        if abs(d.dot(up)) > 0.99:
            up = Vector((0, 1, 0))
        x = d.cross(up).normalized() * 0.035
        z = d.cross(x).normalized() * 0.035
        mid = (head + tail) / 2
        base = len(verts)
        for sx, sy, sz in [(-1, -1, -1), (1, -1, -1), (1, 1, -1), (-1, 1, -1),
                           (-1, -1, 1), (1, -1, 1), (1, 1, 1), (-1, 1, 1)]:
            verts.append(mid + x * sx + d * (sy * length / 2) + z * sz)
        for quad in [(0, 1, 2, 3), (7, 6, 5, 4), (0, 4, 5, 1), (1, 5, 6, 2), (2, 6, 7, 3), (3, 7, 4, 0)]:
            faces.append(tuple(base + i for i in quad))
    mesh.from_pydata(verts, [], faces)
    return obj


def setup_scene():
    scn = bpy.context.scene
    cam = bpy.data.objects.get("SnapCam")
    if cam is None:
        cam_data = bpy.data.cameras.new("SnapCam")
        cam = bpy.data.objects.new("SnapCam", cam_data)
        scn.collection.objects.link(cam)
        cam_data.lens = 50
        light_data = bpy.data.lights.new("SnapLight", 'SUN')
        light_data.energy = 3.0
        light = bpy.data.objects.new("SnapLight", light_data)
        light.rotation_euler = (math.radians(50), 0, math.radians(30))
        scn.collection.objects.link(light)
    cam.location = (1.15, -2.4, 1.35)
    target = Vector((0.0, 0.05, 0.9))
    cam.rotation_euler = (target - cam.location).to_track_quat('-Z', 'Y').to_euler()
    scn.camera = cam
    scn.render.engine = 'BLENDER_WORKBENCH'
    scn.display.shading.light = 'STUDIO'
    scn.display.shading.color_type = 'OBJECT'
    scn.render.resolution_x = 560
    scn.render.resolution_y = 640
    scn.world = scn.world or bpy.data.worlds.new("World")


def preview(clips, clip, frame=0, tag=None, preview_dir=None):
    """Render one frame of a clip to <preview_dir>/preview_<tag>.png and return the path."""
    preview_dir = preview_dir or _HERE
    setup_scene()
    apply_frame(clips, clip, frame)
    obj = build_pose_preview()
    obj.color = (0.25, 0.75, 1.0, 1.0)
    tag = tag or "%s_f%d" % (clip, frame)
    bpy.context.scene.render.filepath = os.path.join(preview_dir, "preview_%s.png" % tag)
    bpy.ops.render.render(write_still=True)
    return bpy.context.scene.render.filepath
