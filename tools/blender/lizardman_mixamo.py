"""
Retarget Mixamo FBX animations onto the Lizardman rig.

Usage inside Blender:
    import lizardman_mixamo as MX
    MX.apply(project, {"idle": "Idle.fbx", "run": "Run.fbx"})
    LA.export_all(project)          # or export to a separate path first

Why it is written this way
--------------------------
Two properties of this rig make an exact retarget possible without a solver:

1. **Every Lizardman bone's `matrix_local` is the identity matrix.** The rig is
   built from axis-aligned `+Y` stubs with roll 0, so a bone's local axes are the
   world axes and its rest orientation contributes nothing. With `Qr = 1` the
   Blender pose relation
       D_bone = D_parent @ (Qr @ Qbasis @ Qr^-1)
   collapses to
       Qbasis = D_parent^-1 @ D_bone
   so a desired world delta converts to a local rotation by one quaternion
   product. See `retarget_clip`.

2. **Mixamo is a T-pose, this rig rests with the arms down**, so transferring the
   source's *delta from its own rest* would drive the already-down arms through
   the torso. Instead the target's world orientation is set equal to the
   source's world orientation, which is what makes a source limb point where the
   source limb points regardless of rest pose. Legs and spine happen to rest
   the same way (pointing down / axis aligned) so both approaches agree there.

The rig's own bind tilts (only `Head` matters for the mapped bones) are added
back componentwise, exactly like `lizardman_anim.import_action` does, so the
exported motion is the pure source delta.

Coordinate handling: both rigs end up facing -Y in Blender world space after the
FBX import (the importer puts the Y-up conversion in the armature object
transform), so bone *world* orientations transfer with no extra rotation, and
the hip translation transfers with a single scale factor measured from the two
hip heights.
"""
import math
import os

import bpy
from mathutils import Matrix, Quaternion, Vector

import lizardman_anim as LA

HERE = os.path.dirname(os.path.abspath(__file__))
MIXAMO_DIR = os.path.join(HERE, "mixamo")
SRC_PREFIX = "mixamorig:"

# One source bone per target bone. The deepest bone of each source chain is used,
# because matching world orientations already folds the skipped bones in (e.g.
# Hand <- mixamorig:Hand carries the whole forearm+wrist bend).
MAPPING = {
    "Hips": "Root",
    "Spine": "Torso",
    "Spine2": "Chest",
    "Head": "Head",
    "RightShoulder": "Shoulder_R",
    "LeftShoulder": "Shoulder_L",
    "RightArm": "Arm_R",
    "LeftArm": "Arm_L",
    "RightHand": "Hand_R",
    "LeftHand": "Hand_L",
    "RightUpLeg": "Thigh_R",
    "LeftUpLeg": "Thigh_L",
    "RightLeg": "Leg_R",
    "LeftLeg": "Leg_L",
    "RightFoot": "Foot_R",
    "LeftFoot": "Foot_L",
    "RightToeBase": "Toe_R_mid",
    "LeftToeBase": "Toe_L_mid",
}

# Target bones that no source bone drives. The nubs follow their parent at a
# fraction; the rest is synthesised so the creature keeps its own anatomy.
FOLLOW_FRACTION = {"Elbow_R": ("Hand_R", 0.35), "Elbow_L": ("Hand_L", 0.35),
                   "Knee_R": ("Leg_R", 0.35), "Knee_L": ("Leg_L", 0.35)}

LOOPING = ("walk", "run", "idle")

# degrees of static fan applied to the side toes of each foot
TOE_SPLAY_DEG = 9.0

# assembled per clip kind: (tail gains, tail lag frames, jaw amplitude deg, jaw cycles)
SECONDARY = {
    "idle": {"tail": (0.34, 0.5, 0.66), "tail_lag": (1.0, 2.5, 4.0), "jaw": 2.4, "jaw_cycles": 2.0},
    "run": {"tail": (0.45, 0.62, 0.78), "tail_lag": (0.7, 1.6, 2.5), "jaw": 3.2, "jaw_cycles": 0.0},
    "walk": {"tail": (0.34, 0.5, 0.66), "tail_lag": (0.8, 1.8, 2.8), "jaw": 1.6, "jaw_cycles": 0.0},
}
DEFAULT_SECONDARY = {"tail": (0.30, 0.44, 0.58), "tail_lag": (1.0, 2.2, 3.4), "jaw": 2.0, "jaw_cycles": 0.0}


# ------------------------------------------------------------------ scene helpers
def fbx_armature_and_action(path):
    """Import an FBX and return only the objects/actions it added."""
    before_objects = {o.name for o in bpy.data.objects}
    before_actions = {a.name for a in bpy.data.actions}
    bpy.ops.import_scene.fbx(filepath=path)
    new_objects = [o for o in bpy.data.objects if o.name not in before_objects]
    new_actions = [a for a in bpy.data.actions if a.name not in before_actions]
    arms = [o for o in new_objects if o.type == "ARMATURE"]
    if not arms or not new_actions:
        for o in new_objects:
            bpy.data.objects.remove(o, do_unlink=True)
        for a in new_actions:
            bpy.data.actions.remove(a)
        raise RuntimeError("no armature or action in %s" % os.path.basename(path))
    return arms[0], new_actions[0], new_objects, new_actions


def discard(objects, actions):
    for o in objects:
        bpy.data.objects.remove(o, do_unlink=True)
    for a in actions:
        bpy.data.actions.remove(a)


def sample_source(src_arm, src_action):
    """{source bone: [world quaternion per frame]} plus the hips' world path."""
    first, last = int(round(src_action.frame_range[0])), int(round(src_action.frame_range[1]))
    bones = [SRC_PREFIX + n for n in MAPPING]
    rot = {b: [] for b in bones}
    hip_path = []
    scn = bpy.context.scene
    world = src_arm.matrix_world
    for frame in range(first, last + 1):
        scn.frame_set(frame)
        for b in bones:
            m = world @ src_arm.pose.bones[b].matrix
            rot[b].append(m.to_quaternion().normalized())
        hip_path.append((world @ src_arm.pose.bones[SRC_PREFIX + "Hips"].matrix).translation.copy())
    return rot, hip_path, first, last


def rig_facts(src_arm, dst_arm):
    """Axis agreement, unit scale and hip height ratio between the two rigs."""
    src_up = (src_arm.matrix_world.to_3x3() @ Vector((0.0, 1.0, 0.0))).normalized()
    src_fwd = (src_arm.matrix_world.to_3x3() @ Vector((0.0, 0.0, 1.0))).normalized()
    dst_up = (dst_arm.matrix_world.to_3x3() @ Vector((0.0, 0.0, 1.0))).normalized()
    dst_fwd = (dst_arm.matrix_world.to_3x3() @ Vector((0.0, -1.0, 0.0))).normalized()
    src_hip_world = (src_arm.matrix_world
                     @ src_arm.data.bones[SRC_PREFIX + "Hips"].head_local).z
    dst_root = dst_arm.data.bones["Root"].head_local.z
    return {
        "up_alignment": round(src_up.dot(dst_up), 5),
        "forward_alignment": round(src_fwd.dot(dst_fwd), 5),
        "src_hip_world_z": round(src_hip_world, 5),
        "dst_root_z": round(dst_root, 5),
        "hip_scale": dst_root / src_hip_world,
    }


# ------------------------------------------------------------------ the retarget
def bind_euler(dst_arm, bone):
    return tuple(dst_arm.pose.bones[bone].get("gecko_bind", (0.0, 0.0, 0.0)))


def chain_order(dst_arm):
    """MAPPING items sorted parents-before-children.

    The parent accumulation below reads the parent's already-computed basis, so
    the order must be topological. Deriving it from the rig instead of relying on
    dict insertion order keeps it correct if the mapping is ever edited.
    """
    def depth(name):
        d, bone = 0, dst_arm.data.bones[name]
        while bone.parent:
            d += 1
            bone = bone.parent
        return d

    return sorted(MAPPING.items(), key=lambda kv: depth(kv[1]))


# The Lizardman's bones are all `+Y` stubs, so a bone's own axis says nothing
# about which way the body part it carries actually extends: the legs hang -Z,
# the spine runs +Z, the shoulders reach +X. Lining up the two rigs' bone frames
# therefore aims the limbs in the wrong direction entirely. What has to match is
# the LIMB direction, so it is stated explicitly per bone, taken from the geo
# file's pivots (model +y -> Blender +Z, model +z -> Blender +Y, model +x -> +X).
#
# Measuring it from the mesh's vertex-group centroid instead was tried and is
# wrong for four bones, because the weight of their geometry sits behind the
# joint rather than along the part: Foot carries the heel (centroid points
# backward, ~90 deg off), Leg reaches back, and the Elbow/Knee nubs sit forward.
LIMB_AXIS = {
    "Root": (0.0, 0.0, 1.0),
    "Torso": (0.0, 0.0, 1.0),
    "Chest": (0.0, 0.0, 1.0),
    "Head": (0.0, 0.0, 1.0),
    "Jaw": (0.0, -0.998, -0.07),
    "Tail1": (0.0, 0.984, -0.176),
    "Tail2": (0.0, 0.997, -0.074),
    "Tail3": (0.0, 0.995, -0.100),
    "Shoulder_L": (0.952, 0.0, -0.305), "Shoulder_R": (-0.952, 0.0, -0.305),
    "Arm_L": (0.0, 0.0, -1.0), "Arm_R": (0.0, 0.0, -1.0),
    "Elbow_L": (0.0, 0.0, -1.0), "Elbow_R": (0.0, 0.0, -1.0),
    "Hand_L": (0.0, 0.0, -1.0), "Hand_R": (0.0, 0.0, -1.0),
    "Tool_L": (0.0, 0.0, -1.0), "Tool_R": (0.0, 0.0, -1.0),
    "Thigh_L": (0.0, 0.0, -1.0), "Thigh_R": (0.0, 0.0, -1.0),
    "Knee_L": (0.0, 0.0, -1.0), "Knee_R": (0.0, 0.0, -1.0),
    "Leg_L": (0.106, 0.0, -0.994), "Leg_R": (-0.106, 0.0, -0.994),
    "Foot_L": (0.0, -0.912, -0.410), "Foot_R": (0.0, -0.912, -0.410),
    "Toe_L_mid": (0.0, -0.999, -0.03), "Toe_R_mid": (0.0, -0.999, -0.03),
    "Toe_L_in": (0.0, -0.999, -0.03), "Toe_R_in": (0.0, -0.999, -0.03),
    "Toe_L_out": (0.0, -0.999, -0.03), "Toe_R_out": (0.0, -0.999, -0.03),
}
LIMB_AXIS_MIN = 0.02      # blocks; below this a centroid is not a direction


def limb_axes(dst_arm):
    """{target bone: unit limb direction in armature space}.

    The explicit table wins; geometry is only a fallback so the module keeps
    working if the rig ever gains a bone.
    """
    mesh = bpy.data.objects.get(LA.MESH)
    group_name = {g.index: g.name for g in mesh.vertex_groups} if mesh else {}
    total, weighted = {}, {}
    for vert in (mesh.data.vertices if mesh else []):
        for membership in vert.groups:
            name = group_name.get(membership.group)
            if name is None or membership.weight <= 0.0:
                continue
            weighted[name] = weighted.get(name, Vector((0.0, 0.0, 0.0))) + vert.co * membership.weight
            total[name] = total.get(name, 0.0) + membership.weight

    axes = {}
    for bone in dst_arm.data.bones:
        if bone.name in LIMB_AXIS:
            axes[bone.name] = Vector(LIMB_AXIS[bone.name]).normalized()
            continue
        weight = total.get(bone.name, 0.0)
        direction = None
        if weight > 0.0:
            offset = (weighted[bone.name] / weight) - bone.head_local
            if offset.length >= LIMB_AXIS_MIN:
                direction = offset.normalized()
        if direction is None and bone.children:
            offset = bone.children[0].head_local - bone.head_local
            direction = offset.normalized() if offset.length > 1e-6 else None
        axes[bone.name] = direction or Vector((0.0, 1.0, 0.0))
    return axes


def source_rest(src_arm, dst_arm):
    """Per source bone: rest orientation and rest limb direction, target armature space."""
    to_dst = dst_arm.matrix_world.inverted().to_3x3()
    rot = {}
    direction = {}
    for src_bone in MAPPING:
        bone = src_arm.data.bones[SRC_PREFIX + src_bone]
        rot[src_bone] = (to_dst @ (src_arm.matrix_world.to_3x3()
                                   @ bone.matrix_local.to_3x3())).to_quaternion().normalized()
        world_dir = (src_arm.matrix_world.to_3x3() @ (bone.tail_local - bone.head_local))
        direction[src_bone] = (to_dst @ world_dir).normalized()
    return rot, direction


def alignment(axes, rest_direction, rest_rotation):
    """{target bone: quaternion mapping its rest part frame onto the source's rest bone frame}.

    Matching the limb axis alone leaves the rotation about that axis free, and on
    a blocky model a free twist is not cosmetic - it turns the part's silhouette.
    The fix is a swing-twist decomposition:

    * `swing` is the minimal rotation that puts the limb on the source's limb
      direction (this is what a direction-only retarget already did), then
    * the remaining rotation `swing^-1 @ source_rest` is a twist about the limb,
      and its signed twist angle is carried over.

    The result matches the source's rest frame as closely as the limb constraint
    allows, so the transferred twist is the source's own.
    """
    out = {}
    for src_bone, target in MAPPING.items():
        axis = axes[target]
        swing = axis.rotation_difference(rest_direction[src_bone])
        residual = swing.inverted() @ rest_rotation[src_bone]
        # mathutils.Quaternion exposes x/y/z/w individually, not an .xyz vector
        twist_axis_component = Vector((residual.x, residual.y, residual.z)).dot(axis)
        twist = 2.0 * math.atan2(twist_axis_component, residual.w)
        out[target] = (swing @ Quaternion(axis, twist)).normalized()
    return out


def verify(dst_arm, src_arm, src_action, clip, sample_every=1):
    """Two independent checks per mapped bone, both in degrees.

    `limb`: where the source's limb points against where the target's limb
    points, using each rig's own limb axis (the source's bone axis, the target's
    declared LIMB_AXIS). The two bone *frames* deliberately differ, so comparing
    frames here would report a fake error.

    `twist_drift`: how much the frame difference between the two rigs moves over
    the clip. A correct retarget makes that difference a constant - equal to the
    rest-convention offset - so any drift means the rotation about the limb is
    not being carried over, which a direction-only check cannot see.
    """
    action = bpy.data.actions[LA.PREFIX + clip]
    LA.assign_action(dst_arm, action)
    axes = limb_axes(dst_arm)
    scn = bpy.context.scene
    first, last = int(round(src_action.frame_range[0])), int(round(src_action.frame_range[1]))
    limb, drift, reference = {}, {}, {}
    for frame in range(first, last + 1, sample_every):
        scn.frame_set(frame)
        src_dir, src_q = {}, {}
        for b in MAPPING:
            matrix = src_arm.matrix_world @ src_arm.pose.bones[SRC_PREFIX + b].matrix
            src_dir[b] = (matrix.to_3x3() @ Vector((0.0, 1.0, 0.0))).normalized()
            src_q[b] = matrix.to_quaternion().normalized()
        # target key index for this source frame is (frame - first), NOT the loop
        # counter: using the counter silently compares mismatched poses, which a
        # slow clip hides and a fast one exposes.
        scn.frame_set(frame - first)
        ev = dst_arm.evaluated_get(bpy.context.evaluated_depsgraph_get())
        for src_bone, target in MAPPING.items():
            key = "%s<-%s" % (target, src_bone)
            matrix = ev.matrix_world @ ev.pose.bones[target].matrix
            posed = (matrix.to_3x3() @ axes[target]).normalized()
            limb[key] = max(limb.get(key, 0.0), math.degrees(posed.angle(src_dir[src_bone])))

            # The retarget aims for Q(target) = Q(source) @ C with C the constant
            # rest-convention offset, i.e. the target's frame rotates exactly with
            # the source's, twist included. So the invariant to test is
            # Q(source)^-1 @ Q(target) - note the order; the reverse product is
            # not constant even for a perfect retarget.
            residual = (src_q[src_bone].inverted() @ matrix.to_quaternion().normalized()).normalized()
            if key not in reference:
                reference[key] = residual
            else:
                # q and -q are the same rotation, so fold with abs(w): comparing
                # with rotation_difference reports a phantom 180 deg flip.
                delta = (reference[key].inverted() @ residual).normalized()
                drift[key] = max(drift.get(key, 0.0),
                                 math.degrees(2.0 * math.acos(max(-1.0, min(1.0, abs(delta.w))))))
    rank = lambda d: {k: round(v, 3) for k, v in sorted(d.items(), key=lambda kv: -kv[1])}
    return {"limb": rank(limb), "twist_drift": rank(drift)}


def retarget_clip(dst_arm, src_arm, src_action, clip, fps=LA.FPS):
    """Build `animation.lizardman.<clip>` from a Mixamo action. Returns a report."""
    facts = rig_facts(src_arm, dst_arm)
    if facts["up_alignment"] < 0.99 or facts["forward_alignment"] < 0.99:
        raise RuntimeError("rig axes disagree (%s); refusing to retarget blind" % facts)

    rot, hip_path, first, last = sample_source(src_arm, src_action)
    key_count = last - first + 1                     # sample per source frame
    dst_world_inv = dst_arm.matrix_world.inverted().to_3x3()
    hip_rest = (src_arm.matrix_world
                @ src_arm.data.bones[SRC_PREFIX + "Hips"].head_local)
    scale = facts["hip_scale"]

    # which target bone each source bone feeds, plus the target's own parent chain
    name = LA.PREFIX + clip
    old = bpy.data.actions.get(name)
    if old:
        bpy.data.actions.remove(old)
    action = bpy.data.actions.new(name)
    action.use_fake_user = True
    curves = LA.action_curves(action, dst_arm, create=True)

    per_frame = []                                   # [{target bone: local quaternion}]
    root_loc = []                                    # target Root location per frame
    chain = chain_order(dst_arm)
    rest_rot, rest_dir = source_rest(src_arm, dst_arm)
    align = alignment(limb_axes(dst_arm), rest_dir, rest_rot)
    to_dst = dst_world_inv.to_quaternion()

    for i in range(key_count):
        basis = {}
        pose_of = {}                                 # desired armature-space orientation

        for src_bone, target in chain:
            q_pose = (to_dst @ rot[SRC_PREFIX + src_bone][i]).normalized()
            q_rest = (to_dst @ rest_rot[src_bone]).normalized()
            # the world-space rotation the source applied to this bone ...
            delta = (q_pose @ q_rest.inverted()).normalized()
            # ... re-based onto a rest orientation whose limb axis matches, so the
            # target's limb points where the source's limb points
            q_target = (delta @ align[target]).normalized()
            parent = dst_arm.data.bones[target].parent
            # Q(pose) = Q(parent_pose) @ Q(basis); the rest rotations are the
            # identity, so the parent factor is its desired POSE orientation -
            # dividing by its basis instead is wrong and costs ~90 deg per joint.
            q_parent = pose_of.get(parent.name, Quaternion()) if parent else Quaternion()
            basis[target] = q_parent.inverted() @ q_target
            pose_of[target] = q_target
        per_frame.append(basis)

        offset = (hip_path[i] - hip_rest) * scale
        root_loc.append(dst_world_inv @ offset)

    # Convert to euler with the previous frame as a compatibility hint. Without it
    # `to_euler` picks whichever branch it likes, and a flip between neighbouring
    # frames (seen as a ~351 deg jump on Thigh_L in Run) makes GeckoLib spin the
    # bone across a single frame.
    eulers = {}
    for _src, target in chain:
        previous = None
        series = []
        for i in range(key_count):
            euler = (per_frame[i][target].to_euler("XZY", previous) if previous is not None
                     else per_frame[i][target].to_euler("XZY"))
            series.append(euler)
            previous = euler
        # Unwrapping can walk the values outside +-180. Subtracting a whole turn
        # from every key is the same rotation and keeps the continuity just built,
        # so bring each axis back near zero.
        for axis in range(3):
            shift = math.floor((series[0][axis] + 180.0) / 360.0) * 360.0
            if shift:
                for euler in series:
                    euler[axis] -= shift
        eulers[target] = series

    # write rotation curves: rotation_euler = bind + local delta (rig convention)
    for src_bone, target in chain:
        bind = bind_euler(dst_arm, target)
        for axis in range(3):
            fc = LA.new_fcurve(curves, target, "rotation_euler", axis)
            for i in range(key_count):
                fc.keyframe_points.insert(i, bind[axis] + eulers[target][i][axis], options={"FAST"})
            fc.update()

    # write the root translation (location fcurves are in blocks, no bind involved)
    for axis in range(3):
        fc = LA.new_fcurve(curves, "Root", "location", axis)
        for i in range(key_count):
            fc.keyframe_points.insert(i, root_loc[i][axis], options={"FAST"})
        fc.update()

    action.use_frame_range = True
    action.frame_start = 0
    action.frame_end = key_count
    action.use_cyclic = clip in LOOPING
    action["gecko_loop"] = clip in LOOPING
    return {"source_frames": [first, last], "target_keys": key_count + 1, "facts": facts,
            "seconds": round(key_count / fps, 4)}


def close_loop(action):
    """Force the last key to repeat the first, so the cycle wraps with no seam."""
    for fc in LA.action_curves(action):
        points = fc.keyframe_points
        if len(points) < 2:
            continue
        first = points[0].co[0]
        last = points[len(points) - 1].co[0]
        if abs(last - first) < 1e-6:
            continue
        value = fc.evaluate(first)
        points[len(points) - 1].co[1] = value


def ground_contact(dst_arm, action, feet=("Toe_L_mid", "Toe_R_mid", "Toe_L_in", "Toe_R_in",
                                          "Toe_L_out", "Toe_R_out")):
    """Shift the whole clip vertically so the lowest foot sits where it rests.

    The two rigs' leg-to-hip ratios differ by ~2.6%, so a straight scale leaves
    the feet a little below the ground. A single constant offset keeps the
    source's bob and contact timing intact, unlike per-frame clamping which would
    flatten a run's flight phase.
    """
    LA.assign_action(dst_arm, action)
    scn = bpy.context.scene
    root_fc = [fc for fc in LA.action_curves(action)
               if fc.data_path == 'pose.bones["Root"].location']
    if not root_fc:
        return None
    rest_low = min(dst_arm.data.bones[b].head_local.z for b in feet if b in dst_arm.data.bones)
    low = None
    for frame in range(int(action.frame_start), int(action.frame_end) + 1):
        scn.frame_set(frame)
        ev = dst_arm.evaluated_get(bpy.context.evaluated_depsgraph_get())
        here = min((ev.matrix_world @ ev.pose.bones[b].head).z
                   for b in feet if b in ev.pose.bones)
        low = here if low is None else min(low, here)
    offset = rest_low - low
    for fc in root_fc:
        if fc.array_index != 2:
            continue
        for kp in fc.keyframe_points:
            kp.co[1] += offset
        fc.update()
    return {"offset_blocks": round(offset, 5), "clip_min_foot_z": round(low, 5),
            "rest_min_foot_z": round(rest_low, 5)}


# ------------------------------------------------------------------- synthesised parts
def synthesize(dst_arm, action, clip):
    """Fill the bones Mixamo has no equivalent for: tail, jaw, side toes."""
    cfg = SECONDARY.get(clip, DEFAULT_SECONDARY)
    last = int(action.frame_end)
    curves = LA.action_curves(action)
    root = {}
    for axis in range(3):
        fc = next((f for f in curves
                   if f.data_path == 'pose.bones["Root"].rotation_euler' and f.array_index == axis), None)
        if fc is None:
            return None
        root[axis] = fc

    def root_at(axis, frame):
        return root[axis].evaluate(max(0.0, min(float(last), frame)))

    # the tail answers the pelvis: same axes, progressively delayed and amplified
    tail_gain, tail_lag = cfg["tail"], cfg["tail_lag"]
    for bone, gain, lag in zip(("Tail1", "Tail2", "Tail3"), tail_gain, tail_lag):
        bind = bind_euler(dst_arm, bone)
        for axis in range(3):
            fc = LA.new_fcurve(curves, bone, "rotation_euler", axis)
            for frame in range(last + 1):
                value = bind[axis] + gain * root_at(axis, frame - lag)
                fc.keyframe_points.insert(frame, value, options={"FAST"})
            fc.update()

    # jaw: a breath for idle, a step-synced pant for locomotion, gentle on others
    jaw_bind = bind_euler(dst_arm, "Jaw")
    fc = LA.new_fcurve(curves, "Jaw", "rotation_euler", 0)
    cycles = cfg["jaw_cycles"] or last / 12.0
    for frame in range(last + 1):
        phase = 2.0 * math.pi * cycles * (frame / max(last, 1))
        value = jaw_bind[0] + math.radians(cfg["jaw"]) * max(0.0, math.sin(phase))
        fc.keyframe_points.insert(frame, value, options={"FAST"})
    fc.update()

    # side toes: follow the middle toe, fanned out by a constant splay
    splay = math.radians(TOE_SPLAY_DEG)
    for side, sign in (("L", 1.0), ("R", -1.0)):
        mid = 'pose.bones["Toe_%s_mid"].rotation_euler' % side
        for toe, extra in (("in", sign * splay), ("out", -sign * splay)):
            for axis in range(3):
                src = next((f for f in curves if f.data_path == mid and f.array_index == axis), None)
                fc = LA.new_fcurve(curves, "Toe_%s_%s" % (side, toe), "rotation_euler", axis)
                for frame in range(last + 1):
                    value = src.evaluate(frame) if src else 0.0
                    if axis == 2:
                        value += extra
                    fc.keyframe_points.insert(frame, value, options={"FAST"})
                fc.update()

    # the elbow/knee nubs ride along with their parent
    for bone, (parent, fraction) in FOLLOW_FRACTION.items():
        bind = bind_euler(dst_arm, bone)
        pbind = bind_euler(dst_arm, parent)
        for axis in range(3):
            src = next((f for f in curves
                        if f.data_path == 'pose.bones["%s"].rotation_euler' % parent
                        and f.array_index == axis), None)
            if src is None:
                continue
            fc = LA.new_fcurve(curves, bone, "rotation_euler", axis)
            for frame in range(last + 1):
                value = bind[axis] + fraction * (src.evaluate(frame) - pbind[axis])
                fc.keyframe_points.insert(frame, value, options={"FAST"})
            fc.update()
    return {"tail_gain": tail_gain, "jaw_deg": cfg["jaw"]}


# ------------------------------------------------------------------------ entry point
def apply(project, clips, export_to=None):
    """Rebuild the rig from the mod files, then replace `clips` with Mixamo motion."""
    LA.import_all(project)
    dst_arm = bpy.data.objects[LA.ARMATURE]
    report = {}
    for clip, filename in clips.items():
        path = os.path.join(MIXAMO_DIR, filename)
        src_arm, src_action, objects, actions = fbx_armature_and_action(path)
        try:
            info = retarget_clip(dst_arm, src_arm, src_action, clip)
            action = bpy.data.actions[LA.PREFIX + clip]
            info["secondary"] = synthesize(dst_arm, action, clip)
            if clip in LOOPING:
                info["ground"] = ground_contact(dst_arm, action)
                close_loop(action)
            info["source"] = filename
            report[clip] = info
        finally:
            discard(objects, actions)
    if export_to is not None:
        report["exported"] = LA.export_all(project, export_to)
    return report
