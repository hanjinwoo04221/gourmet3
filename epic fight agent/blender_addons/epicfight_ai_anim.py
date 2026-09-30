"""AI animation tools.

General workflow: inspect_motion_rig -> author poses/contact plan -> refine_motion.
MOTION_RULES and AI_ANIMATION_API.md define the supported AI contract. Legacy
primitives below edit in place and may encode Epic Fight-specific assumptions;
they must not be treated as universal biomechanics or a natural-language parser.
"""

bl_info = {
    "name": "Epic Fight AI Animation Assistant",
    "author": "Claude",
    "version": (1, 1, 0),
    "blender": (2, 93, 0),
    "location": "View3D > Sidebar > EF AI",
    "description": "AI-intent-driven motion enhancement for Epic Fight mod animations: "
                   "anticipation, overshoot, secondary motion, impact holds, easing, "
                   "amplitude scaling, plus a motion diagnostic report.",
    "category": "Animation",
}

import bpy
import math


# ---------------------------------------------------------------------------
# Core helpers
# ---------------------------------------------------------------------------

def _get_action(obj):
    if not obj or not obj.animation_data or not obj.animation_data.action:
        raise ValueError(f"{obj!r} has no active action to edit")
    return obj.animation_data.action


def _iter_fcurves(action, obj=None):
    """Yield an action's fcurves, whether it's a legacy action or a Blender
    4.4+ layered action (layers -> strips -> channelbags -> fcurves)."""
    if hasattr(action, "fcurves"):
        yield from action.fcurves
        return
    slot = obj.animation_data.action_slot if (obj and obj.animation_data) else None
    for layer in action.layers:
        for strip in layer.strips:
            if strip.type != 'KEYFRAME':
                continue
            channelbags = [strip.channelbag(slot)] if slot else list(strip.channelbags)
            for cb in channelbags:
                if cb is not None:
                    yield from cb.fcurves


def _bone_fcurves(obj, bone_name, prop=None):
    action = _get_action(obj)
    needle = obj.pose.bones[bone_name].path_from_id() + "."
    out = []
    for fc in _iter_fcurves(action, obj):
        if not fc.data_path.startswith(needle):
            continue
        if prop is not None and not fc.data_path.endswith(prop):
            continue
        out.append(fc)
    return out


def _rotation_prop(obj, bone_name):
    """Detect whether the bone's rotation is authored as euler or quaternion."""
    pbone = obj.pose.bones[bone_name]
    mode = pbone.rotation_mode
    return "rotation_quaternion" if mode == "QUATERNION" else "rotation_euler"


def _find_keyframe(fcurve, frame, epsilon=0.01):
    for kp in fcurve.keyframe_points:
        if abs(kp.co.x - frame) <= epsilon:
            return kp
    return None


def _shift_keyframe(kp, delta):
    kp.co.x += delta
    kp.handle_left.x += delta
    kp.handle_right.x += delta


def set_curve_style(fcurve, interpolation="BEZIER", easing="AUTO",
                     back=1.7, amplitude=0.15, period=0.3, only_frame=None):
    """Apply one of Blender's built-in dynamic interpolation modes to a curve.
    interpolation: BEZIER, SINE, QUAD, CUBIC, QUART, QUINT, EXPO, CIRC, BACK, BOUNCE, ELASTIC
    easing: EASE_IN, EASE_OUT, EASE_IN_OUT, AUTO
    """
    for kp in fcurve.keyframe_points:
        if only_frame is not None and abs(kp.co.x - only_frame) > 0.01:
            continue
        kp.interpolation = interpolation
        kp.easing = easing
        if interpolation == "BACK":
            kp.back = back
        if interpolation == "ELASTIC":
            kp.amplitude = amplitude
            kp.period = period


# ---------------------------------------------------------------------------
# Timing & smoothness
#
# Reference mocap (see data/*.fbx) keys EVERY frame and reads as fluid; our
# pose-to-pose keys are 4-8 frames apart, which reads as stiff/rushed no
# matter how the handles are set. Resampling onto the same curve doesn't fix
# this (it reproduces the same shape) — what actually helps is (1) genuine
# non-linear timing (slow-in/slow-out) via Blender's dynamic easing curves
# instead of plain bezier, (2) real intermediate breakdown poses so a limb
# follows an arc instead of a straight slerp, and (3) more total time per
# beat, all directly adjustable here.
# ---------------------------------------------------------------------------

def fix_quaternion_continuity(obj, bone_name, prop=None):
    """Walk a bone's quaternion keyframes in frame order and negate any
    keyframe whose (w,x,y,z) dot product with the PREVIOUS one is negative.
    q and -q represent the identical 3D rotation, but as 4D points they're
    on opposite sides of the hypersphere, so slerp between them takes the
    long way around (visible as a sudden huge spin/flip). Blender's own
    keyframe_insert can silently introduce exactly this sign flip -- it
    sometimes re-signs a new keyframe to be 'compatible' with a NEARBY
    existing one rather than the one immediately before it in time -- so
    always run this after keying a rotation that sweeps far enough to be
    ambiguous (roughly >90 degrees of total travel), before any slerp-based
    processing (apply_minimum_jerk, apply_accelerating_rotation, ...).
    Returns the frames that were flipped."""
    prop = prop or _rotation_prop(obj, bone_name)
    if prop != "rotation_quaternion":
        return []
    fcurves = _bone_fcurves(obj, bone_name, prop)
    by_idx = {fc.array_index: fc for fc in fcurves}
    if len(by_idx) < 4:
        return []
    frames = sorted({round(kp.co.x, 4) for kp in by_idx[0].keyframe_points})
    flipped = []
    prev = None
    for frame in frames:
        kps = {i: _find_keyframe(by_idx[i], frame) for i in range(4)}
        if any(v is None for v in kps.values()):
            continue
        cur = [kps[i].co.y for i in range(4)]
        if prev is not None:
            dot = sum(a * b for a, b in zip(prev, cur))
            if dot < 0:
                for i in range(4):
                    kps[i].co.y = -cur[i]
                    kps[i].handle_left.y = -kps[i].handle_left.y
                    kps[i].handle_right.y = -kps[i].handle_right.y
                cur = [-v for v in cur]
                flipped.append(frame)
        prev = cur
    for fc in by_idx.values():
        fc.update()
    return flipped


def retime_action(obj, factor, pivot_frame=None):
    """Stretch (factor > 1) or compress (factor < 1) an action's timing
    around pivot_frame (defaults to the action's first frame). This is the
    direct fix for motion that reads as too fast: e.g. factor=1.6 turns an
    18-frame punch into a ~29-frame one without touching any pose values."""
    action = _get_action(obj)
    if pivot_frame is None:
        pivot_frame = action.frame_range[0]
    touched = []
    for fc in _iter_fcurves(action, obj):
        for kp in fc.keyframe_points:
            kp.co.x = pivot_frame + (kp.co.x - pivot_frame) * factor
            kp.handle_left.x = pivot_frame + (kp.handle_left.x - pivot_frame) * factor
            kp.handle_right.x = pivot_frame + (kp.handle_right.x - pivot_frame) * factor
        fc.update()
        touched.append(fc.data_path + f"[{fc.array_index}]")
    return touched


def retime_range(obj, range_start, range_end, factor):
    """Stretch (factor > 1) or compress (factor < 1) ONLY the keyframes
    inside [range_start, range_end], across every fcurve in the action, and
    shift everything AFTER range_end by the resulting change in duration so
    it keeps its spacing relative to the now-longer/shorter window. Use this
    to make one beat of an action (e.g. the chamber-to-strike part of a
    kick) linger longer without re-timing the whole clip. Keyframes before
    range_start are untouched. Call this on the action BEFORE any dense
    resampling (minimum-jerk / inertial lag / centripetal drift), since it
    moves keyframes directly rather than re-deriving them."""
    action = _get_action(obj)
    added = (range_end - range_start) * (factor - 1)
    touched = []
    for fc in _iter_fcurves(action, obj):
        for kp in fc.keyframe_points:
            for point in (kp, ):
                x = point.co.x
                if x < range_start:
                    continue
                elif x <= range_end:
                    new_x = range_start + (x - range_start) * factor
                else:
                    new_x = x + added
                point.co.x = new_x
            # handles follow the same piecewise remap as their keyframe
            for handle in (kp.handle_left, kp.handle_right):
                x = handle.x
                if x < range_start:
                    continue
                elif x <= range_end:
                    handle.x = range_start + (x - range_start) * factor
                else:
                    handle.x = x + added
        fc.update()
        touched.append(fc.data_path + f"[{fc.array_index}]")
    return {"touched": touched, "new_range_end": range_start + (range_end - range_start) * factor,
            "shift_after": added}


def retime_action_by_beats(obj, beats, name=None):
    """Copy and retime an authored action without changing its rig-space poses.

    ``beats`` is an ordered sequence of (source_frame, target_frame) pairs,
    including the action boundaries and manually identified events such as
    load, impact and recovery. Both columns must increase strictly. The same
    monotone, piecewise-linear map is applied to every channel and handle so
    limb/root coordination and planted holds survive the timing edit. Select
    events from the *source* action; a reference clip only suggests cadence.
    """
    action = _get_action(obj)
    if len(beats) < 2:
        raise ValueError('At least two timing beats are required')
    points = []
    for pair in beats:
        if len(pair) != 2 or not all(math.isfinite(float(v)) for v in pair):
            raise ValueError('Each timing beat needs finite source and target frames')
        points.append((float(pair[0]), float(pair[1])))
    if any(b[0] <= a[0] or b[1] <= a[1] for a, b in zip(points, points[1:])):
        raise ValueError('Source and target timing beats must increase strictly')
    start, end = map(float, action.frame_range)
    if abs(points[0][0] - start) > .01 or abs(points[-1][0] - end) > .01:
        raise ValueError('Timing beats must include the source action boundaries')

    def warp(frame):
        for i in range(len(points) - 1):
            if frame <= points[i + 1][0]:
                break
        else:
            i = len(points) - 2
        source_a, target_a = points[i]
        source_b, target_b = points[i + 1]
        return target_a + (frame - source_a) * (target_b - target_a) / (source_b - source_a)

    old_slot = obj.animation_data.action_slot
    copied = action.copy()
    copied.name = name or action.name + '_Reference_Timing'
    try:
        obj.animation_data.action = copied
        if old_slot is not None:
            obj.animation_data.action_slot = copied.slots[old_slot.identifier]
        count = 0
        for fc in _iter_fcurves(copied, obj):
            for kp in fc.keyframe_points:
                for point in (kp.co, kp.handle_left, kp.handle_right):
                    point.x = warp(float(point.x))
                count += 1
            fc.update()
        for marker in copied.pose_markers:
            marker.frame = round(warp(marker.frame))
        if copied.use_frame_range:
            copied.frame_start, copied.frame_end = points[0][1], points[-1][1]
    except Exception:
        obj.animation_data.action = action
        if old_slot is not None:
            obj.animation_data.action_slot = old_slot
        bpy.data.actions.remove(copied)
        raise
    return {'source': action.name, 'action': copied.name, 'beats': points,
            'keys_retimed': count, 'frame_range': (points[0][1], points[-1][1])}


def articulate_joint_phases(obj, joint_profiles, phase_tracks, contacts, name=None,
                            max_degrees=35.0, balance_groups=None):
    """Add bounded swing and lateral spread to a copy of a native rig action.

    ``joint_profiles`` maps bone names to rig-verified local ``swing_axis`` and
    ``spread_axis`` vectors. ``phase_tracks`` maps those bones to ordered
    ``(frame, swing_degrees, spread_degrees)`` knots; angles are offsets from
    the evaluated source pose, not absolute Euler values. Matched deforming
    helpers may have their own tracks. Explicit contacts protect a planted
    effector and its ancestor chain throughout each contact window. Optional
    ``balance_groups`` pair explicit left/right bones and anchors: evaluated
    COM proximity plus declared contact modulates their authored offsets, while
    ``sync_windows`` suppresses this difference when coordinated motion is
    intended. No mirror sign or hinge axis is inferred from bone names.
    """
    from mathutils import Quaternion, Vector
    action = _get_action(obj)
    if obj.type != 'ARMATURE' or not phase_tracks or contacts is None:
        raise ValueError('Expected an armature, joint tracks and explicit contacts')
    if not math.isfinite(max_degrees) or not 0 < max_degrees <= 60:
        raise ValueError('Invalid articulation angle bound')
    start, end = map(float, action.frame_range)
    known = {bone.name for bone in obj.pose.bones}
    tracks = {}
    axes = {}
    for bone, knots in phase_tracks.items():
        if bone not in known or bone not in joint_profiles or obj.pose.bones[bone].constraints:
            raise ValueError(f'{bone}: unknown, unprofiled or constrained bone')
        spec = joint_profiles[bone]
        if set(spec) != {'swing_axis', 'spread_axis'}:
            raise ValueError(f'{bone}: expected swing_axis and spread_axis')
        axis_pair = []
        for key in ('swing_axis', 'spread_axis'):
            vector = Vector(spec[key])
            if len(vector) != 3 or not all(math.isfinite(v) for v in vector) or vector.length < 1e-6:
                raise ValueError(f'{bone}: invalid {key}')
            axis_pair.append(vector.normalized())
        if abs(axis_pair[0].dot(axis_pair[1])) > .95:
            raise ValueError(f'{bone}: swing and spread axes are nearly parallel')
        axes[bone] = axis_pair
        if len(knots) < 2:
            raise ValueError(f'{bone}: at least two phase knots are required')
        parsed = [tuple(float(v) for v in knot) for knot in knots]
        if any(len(k) != 3 or not all(math.isfinite(v) for v in k) or
               abs(k[1]) > max_degrees or abs(k[2]) > max_degrees for k in parsed):
            raise ValueError(f'{bone}: invalid phase angles')
        if any(b[0] <= a[0] for a, b in zip(parsed, parsed[1:])) or \
                abs(parsed[0][0]-start) > .01 or abs(parsed[-1][0]-end) > .01:
            raise ValueError(f'{bone}: phase knots must span the action in order')
        tracks[bone] = parsed
    protected = []
    for contact in contacts:
        if set(contact) != {'bone', 'start', 'end'} or contact['bone'] not in known:
            raise ValueError('Invalid contact window')
        a, b = float(contact['start']), float(contact['end'])
        if not all(math.isfinite(v) for v in (a, b)) or not start <= a <= b <= end:
            raise ValueError('Contact window outside action')
        chain = {contact['bone']} | {p.name for p in obj.pose.bones[contact['bone']].parent_recursive}
        protected.append((a, b, chain))
    groups = []
    assigned = set()
    for group in balance_groups or []:
        if set(group) - {'left','right','left_anchor','right_anchor','masses',
                         'gain','contact_bias','sync_windows','sync_fade_frames'} or not \
                {'left','right','left_anchor','right_anchor','masses'} <= set(group):
            raise ValueError('Invalid balance group fields')
        left, right = set(group['left']), set(group['right'])
        if not left or not right or left & right or (left | right) - set(tracks) or \
                (left | right) & assigned:
            raise ValueError('Balance sides must be distinct, tracked bones')
        assigned |= left | right
        la, ra = group['left_anchor'], group['right_anchor']
        if la not in known or ra not in known or la == ra:
            raise ValueError('Balance anchors must be distinct rig bones')
        masses = group['masses']
        if not masses or any(n not in known or not isinstance(w,(int,float)) or
                             not math.isfinite(w) or w <= 0 for n,w in masses.items()):
            raise ValueError('Balance masses must be positive rig bone weights')
        gain = float(group.get('gain', .35))
        contact_bias = float(group.get('contact_bias', .25))
        if not all(math.isfinite(v) for v in (gain,contact_bias)) or \
                not 0 <= gain <= .5 or not 0 <= contact_bias <= .5:
            raise ValueError('Invalid balance gain or contact bias')
        fade = float(group.get('sync_fade_frames',2.0))
        if not math.isfinite(fade) or not 0 <= fade <= 10:
            raise ValueError('Invalid synchronization fade')
        windows = []
        for window in group.get('sync_windows', []):
            if len(window) != 2 or not all(isinstance(v,(int,float)) and math.isfinite(v)
                                           for v in window) or not start <= window[0] <= window[1] <= end:
                raise ValueError('Invalid synchronized window')
            windows.append(tuple(window))
        groups.append((left,right,la,ra,masses,gain,contact_bias,windows,fade))

    def angles_at(knots, frame):
        for i in range(len(knots)-1):
            if frame <= knots[i+1][0]:
                break
        else:
            i = len(knots)-2
        a, b = knots[i:i+2]
        t = max(0., min(1., (frame-a[0])/(b[0]-a[0])))
        t = t*t*(3.-2.*t)
        return (a[1]*(1.-t)+b[1]*t, a[2]*(1.-t)+b[2]*t)

    old_frame = bpy.context.scene.frame_current + bpy.context.scene.frame_subframe
    old_slot = obj.animation_data.action_slot
    frames = sorted(set([start, end] + list(range(math.ceil(start), math.floor(end)+1)) +
                        [k[0] for knots in tracks.values() for k in knots] +
                        [v for a, b, _ in protected for v in (a, b)] +
                        [v for group in groups for window in group[7] for v in window]))
    samples = {}
    weights = {}
    try:
        for frame in frames:
            bpy.context.scene.frame_set(math.floor(frame), subframe=frame-math.floor(frame))
            samples[frame] = {bone: obj.pose.bones[bone].matrix_basis.to_quaternion().normalized()
                              for bone in tracks}
            weights[frame] = {bone: 1.0 for bone in tracks}
            if groups:
                rig = obj.evaluated_get(bpy.context.evaluated_depsgraph_get())
                for left,right,la,ra,masses,gain,contact_bias,windows,fade in groups:
                    left_point = rig.matrix_world @ rig.pose.bones[la].tail
                    right_point = rig.matrix_world @ rig.pose.bones[ra].tail
                    span = right_point-left_point
                    if span.length_squared < 1e-8:
                        raise ValueError('Balance anchors coincide at a sampled frame')
                    com = evaluated_center_of_mass(obj,masses)
                    signed = (com-(left_point+right_point)*.5).dot(span) / (span.length_squared*.5)
                    signed = max(-1.,min(1.,signed))
                    left_contact = any(a <= frame <= b and la in chain for a,b,chain in protected)
                    right_contact = any(a <= frame <= b and ra in chain for a,b,chain in protected)
                    if left_contact != right_contact:
                        signed += -contact_bias if left_contact else contact_bias
                    signed = max(-1.,min(1.,signed))
                    if windows:
                        distance = min(max(a-frame,frame-b,0.) for a,b in windows)
                        t = min(1.,distance/fade) if fade else float(distance>0)
                        signed *= t*t*(3.-2.*t)
                    for bone in left: weights[frame][bone] = 1.+gain*signed
                    for bone in right: weights[frame][bone] = 1.-gain*signed
        copied = action.copy()
        copied.name = name or action.name + '_Articulated'
        obj.animation_data.action = copied
        if old_slot is not None:
            obj.animation_data.action_slot = copied.slots[old_slot.identifier]
        for frame in frames:
            for bone, knots in tracks.items():
                swing, spread = angles_at(knots, frame)
                swing = max(-max_degrees,min(max_degrees,swing*weights[frame][bone]))
                spread = max(-max_degrees,min(max_degrees,spread*weights[frame][bone]))
                if any(a <= frame <= b and bone in chain for a, b, chain in protected):
                    swing = spread = 0.
                if abs(swing) + abs(spread) < 1e-8 and frame not in (start, end):
                    continue
                base = samples[frame][bone]
                swing_axis, spread_axis = axes[bone]
                q = (base @ Quaternion(swing_axis, math.radians(swing)) @
                     Quaternion(spread_axis, math.radians(spread))).normalized()
                pb = obj.pose.bones[bone]
                if pb.rotation_mode == 'QUATERNION':
                    pb.rotation_quaternion = q
                    path = 'rotation_quaternion'
                elif pb.rotation_mode == 'AXIS_ANGLE':
                    axis, angle = q.to_axis_angle()
                    pb.rotation_axis_angle = (angle, *axis)
                    path = 'rotation_axis_angle'
                else:
                    pb.rotation_euler = q.to_euler(pb.rotation_mode, pb.rotation_euler)
                    path = 'rotation_euler'
                pb.keyframe_insert(data_path=path, frame=frame, group=bone)
        if copied.use_frame_range:
            copied.frame_start, copied.frame_end = start, end
    except Exception:
        if 'copied' in locals():
            obj.animation_data.action = action
            if old_slot is not None:
                obj.animation_data.action_slot = old_slot
            bpy.data.actions.remove(copied)
        raise
    finally:
        bpy.context.scene.frame_set(math.floor(old_frame), subframe=old_frame-math.floor(old_frame))
    return {'source': action.name, 'action': copied.name, 'bones': sorted(tracks),
            'sampled_frames': len(frames), 'contacts': len(protected),
            'balance_factors': {str(frame): {bone: round(value,4) for bone,value in values.items()
                                             if bone in assigned}
                                for frame,values in weights.items()} if groups else {}}


def set_natural_easing(obj, bone_name=None, style="SINE", easing="EASE_IN_OUT",
                        prop=None, skip_styles=("BACK", "BOUNCE", "ELASTIC", "CONSTANT")):
    """Give keyframes genuine slow-in/slow-out timing using Blender's dynamic
    curve types (SINE/QUAD/CUBIC/...), which is a real non-linear time-warp —
    unlike plain BEZIER+AUTO_CLAMPED, which mostly just avoids overshoot and
    can still read as a flat, constant-speed slide between sparse poses.
    Keyframes already deliberately styled (BACK overshoot, CONSTANT holds,
    ...) are left alone by default via `skip_styles`."""
    if bone_name is not None:
        prop = prop or _rotation_prop(obj, bone_name)
        fcurves = _bone_fcurves(obj, bone_name, prop)
    else:
        fcurves = list(_iter_fcurves(_get_action(obj), obj))
    touched = []
    for fc in fcurves:
        changed = False
        for kp in fc.keyframe_points:
            if kp.interpolation in skip_styles:
                continue
            kp.interpolation = style
            kp.easing = easing
            changed = True
        if changed:
            fc.update()
            touched.append(fc.data_path + f"[{fc.array_index}]")
    return touched


def add_breakdown(obj, bone_name, frame_a, frame_b, ratio=0.5, arc_bias=0.15, prop=None):
    """Insert a genuine in-between pose between two existing keyframes,
    instead of relying on the curve's own straight slerp. Without this, a
    joint travels from pose A to pose B along a single mathematically
    'shortest' rotation path, which reads as sterile; real limbs overshoot
    slightly past the straight path partway through and settle in, which is
    what `arc_bias` adds (a small extra push along the same rotation axis,
    scaled down again as it nears frame_b so it still lands exactly on
    keyframe B's value)."""
    import mathutils
    prop = prop or _rotation_prop(obj, bone_name)
    fcurves = _bone_fcurves(obj, bone_name, prop)
    if not fcurves:
        return []
    if prop == "rotation_quaternion":
        return _add_breakdown_quaternion(fcurves, frame_a, frame_b, ratio, arc_bias)
    return _add_breakdown_euler(fcurves, frame_a, frame_b, ratio, arc_bias)


def _add_breakdown_euler(fcurves, frame_a, frame_b, ratio, arc_bias):
    touched = []
    mid_frame = frame_a + (frame_b - frame_a) * ratio
    for fc in fcurves:
        kp_a = _find_keyframe(fc, frame_a)
        kp_b = _find_keyframe(fc, frame_b)
        if kp_a is None or kp_b is None:
            continue
        straight = kp_a.co.y + (kp_b.co.y - kp_a.co.y) * ratio
        overshoot = (kp_b.co.y - kp_a.co.y) * arc_bias * (1 - ratio)
        kp = fc.keyframe_points.insert(mid_frame, straight + overshoot, keyframe_type='KEYFRAME')
        kp.interpolation = "SINE"
        kp.easing = "EASE_IN_OUT"
        fc.update()
        touched.append(fc.data_path + f"[{fc.array_index}]")
    return touched


def _add_breakdown_quaternion(fcurves, frame_a, frame_b, ratio, arc_bias):
    import mathutils
    by_index = {fc.array_index: fc for fc in fcurves}
    if len(by_index) < 4:
        return []
    kps_a, kps_b = {}, {}
    for i in range(4):
        kps_a[i] = _find_keyframe(by_index[i], frame_a)
        kps_b[i] = _find_keyframe(by_index[i], frame_b)
        if kps_a[i] is None or kps_b[i] is None:
            return []
    q_a = mathutils.Quaternion([kps_a[i].co.y for i in range(4)]).normalized()
    q_b = mathutils.Quaternion([kps_b[i].co.y for i in range(4)]).normalized()
    straight = q_a.slerp(q_b, ratio)
    diff = q_a.rotation_difference(q_b)
    axis, angle = diff.to_axis_angle()
    mid_frame = frame_a + (frame_b - frame_a) * ratio
    if abs(angle) < 1e-6:
        breakdown = straight
    else:
        extra = mathutils.Quaternion(axis, angle * arc_bias * (1 - ratio))
        breakdown = extra @ straight
    touched = []
    for i in range(4):
        fc = by_index[i]
        kp = fc.keyframe_points.insert(mid_frame, breakdown[i], keyframe_type='KEYFRAME')
        kp.interpolation = "SINE"
        kp.easing = "EASE_IN_OUT"
        fc.update()
        touched.append(fc.data_path + f"[{fc.array_index}]")
    return touched


def smooth_motion(obj, bone_names, cyclic=False):
    """Preserve poses and timing while giving adjacent segments shared velocity.

    Monotone cubic tangents avoid component overshoot. Matching loop endpoints
    share a tangent; deliberate holds and dynamic effects retain their handles.
    Quaternion sign continuity is repaired before computing tangents.
    """
    names = list(dict.fromkeys(bone_names))
    for name in names:
        if name not in obj.pose.bones:
            raise ValueError(f"Unknown bone: {name}")
    touched = []
    protected = {"CONSTANT", "BACK", "BOUNCE", "ELASTIC"}

    def tangent(h0, h1, d0, d1):
        if d0 * d1 <= 0:
            return 0.0
        w0, w1 = 2 * h1 + h0, h1 + 2 * h0
        return (w0 + w1) / (w0 / d0 + w1 / d1)

    for name in names:
        fix_quaternion_continuity(obj, name)
        for fc in _bone_fcurves(obj, name):
            if not fc.data_path.endswith(("rotation_quaternion", "rotation_euler", "location")):
                continue
            pts = sorted(fc.keyframe_points, key=lambda k: k.co.x)
            if len(pts) < 2:
                continue
            xy = [(p.co.x, p.co.y) for p in pts]
            h = [b[0] - a[0] for a, b in zip(xy, xy[1:])]
            if min(h) <= 1e-6:
                continue
            d = [(b[1] - a[1]) / dt for a, b, dt in zip(xy, xy[1:], h)]
            slopes = [0.0] + [tangent(h[i-1], h[i], d[i-1], d[i])
                               for i in range(1, len(pts)-1)] + [0.0]
            if cyclic and abs(xy[0][1] - xy[-1][1]) < 1e-5:
                slopes[0] = slopes[-1] = tangent(h[-1], h[0], d[-1], d[0])
            styles = [p.interpolation for p in pts]
            for i, p in enumerate(pts):
                if styles[i] in protected or (i and styles[i-1] in protected):
                    continue
                x, y = xy[i]
                left = h[i-1] if i else h[0]
                right = h[i] if i < len(h) else h[-1]
                p.interpolation = 'BEZIER'
                p.handle_left_type = p.handle_right_type = 'FREE'
                p.handle_left = (x - left / 3, y - slopes[i] * left / 3)
                p.handle_right = (x + right / 3, y + slopes[i] * right / 3)
            fc.update()
            touched.append(fc.data_path + f"[{fc.array_index}]")
    return touched


def humanize_motion(obj, bone_names, slow_factor=1.0, add_breakdowns=False, breakdown_ratio=0.5, arc_bias=0.15):
    """Smooth motion through authored poses without stopping at every key.
    Timing changes and extra breakdowns are opt-in. Deliberate dynamic easing
    and holds are preserved. Run after pose authoring and intent effects.
    """
    import math
    bone_names = list(dict.fromkeys(bone_names))
    if not math.isfinite(slow_factor) or slow_factor <= 0:
        raise ValueError("Slow factor must be a finite positive number")
    for name in bone_names:
        if name not in obj.pose.bones:
            raise ValueError(f"Unknown bone: {name}")
    report = {"retime": None, "breakdowns": {}, "easing": {}}
    if slow_factor and slow_factor != 1.0:
        report["retime"] = retime_action(obj, slow_factor)

    for bone_name in bone_names:
        prop = _rotation_prop(obj, bone_name)
        fcurves = _bone_fcurves(obj, bone_name, prop)
        if not fcurves:
            continue
        frames = sorted({round(kp.co.x, 4) for kp in fcurves[0].keyframe_points})
        added = []
        if add_breakdowns:
            for f_a, f_b in zip(frames, frames[1:]):
                if f_b - f_a < 3:
                    continue  # too close together to need a breakdown
                added += add_breakdown(obj, bone_name, f_a, f_b,
                                        ratio=breakdown_ratio, arc_bias=arc_bias, prop=prop)
        report["breakdowns"][bone_name] = added
        report["easing"][bone_name] = smooth_motion(obj, [bone_name])

    return report


def _minimum_jerk_s(t):
    """Flash & Hogan (1985) minimum-jerk trajectory shape: the bell-shaped
    speed profile real human point-to-point reaching movements follow
    (slow-fast-slow), as a 0..1 -> 0..1 remap of the interpolation fraction."""
    return 10 * t**3 - 15 * t**4 + 6 * t**5


def apply_minimum_jerk(obj, bone_name, frame_a, frame_b, num_points=4, prop=None):
    """Replace the space between two existing keyframes with several densely
    sampled, LINEARLY-interpolated points along the minimum-jerk curve. This
    mirrors how the reference mocap in data/*.fbx actually reads as fluid:
    every one of its frames is keyed with plain LINEAR interpolation — the
    smoothness comes from keyframe DENSITY plus a real acceleration profile,
    not from a fancy curve type on sparse keys. Quaternion bones only."""
    import mathutils
    prop = prop or _rotation_prop(obj, bone_name)
    if prop != "rotation_quaternion":
        return []
    fcurves = _bone_fcurves(obj, bone_name, prop)
    by_index = {fc.array_index: fc for fc in fcurves}
    if len(by_index) < 4:
        return []
    kp_a = {i: _find_keyframe(by_index[i], frame_a) for i in range(4)}
    kp_b = {i: _find_keyframe(by_index[i], frame_b) for i in range(4)}
    if any(v is None for v in list(kp_a.values()) + list(kp_b.values())):
        return []
    q_a = mathutils.Quaternion([kp_a[i].co.y for i in range(4)]).normalized()
    q_b = mathutils.Quaternion([kp_b[i].co.y for i in range(4)]).normalized()

    # the endpoints must also become LINEAR or the segment would still be
    # shaped by their old bezier/back handles instead of the sampled points
    for i in range(4):
        kp_a[i].interpolation = "LINEAR"

    touched = []
    for n in range(1, num_points + 1):
        t = n / (num_points + 1)
        frame = frame_a + (frame_b - frame_a) * t
        q = q_a.slerp(q_b, _minimum_jerk_s(t))
        for i in range(4):
            fc = by_index[i]
            kp = fc.keyframe_points.insert(frame, q[i], keyframe_type='KEYFRAME')
            kp.interpolation = "LINEAR"
        touched.append(round(frame, 3))
    for fc in by_index.values():
        fc.update()
    return touched


def apply_accelerating_rotation(obj, bone_name, frame_a, frame_b, num_points=8,
                                 power=2.4, reverse=False, prop=None):
    """Like apply_minimum_jerk, but for a rotation that should genuinely
    SPEED UP across the whole span (a spin building momentum into a kick)
    instead of minimum-jerk's slow-fast-slow bell. Minimum-jerk decelerates
    back to zero at EVERY segment boundary, so chaining it across several
    keyframes makes a spin look like it keeps stalling and restarting; this
    samples a single power-curve (t**power, power>1 = ease-in/accelerating)
    across the WHOLE frame_a..frame_b span instead, so velocity keeps
    building continuously. Use reverse=True for the opposite (decelerating
    into a stop, e.g. the landing side of a spin)."""
    import mathutils
    prop = prop or _rotation_prop(obj, bone_name)
    if prop != "rotation_quaternion":
        return []
    fcurves = _bone_fcurves(obj, bone_name, prop)
    by_index = {fc.array_index: fc for fc in fcurves}
    if len(by_index) < 4:
        return []
    kp_a = {i: _find_keyframe(by_index[i], frame_a) for i in range(4)}
    kp_b = {i: _find_keyframe(by_index[i], frame_b) for i in range(4)}
    if any(v is None for v in list(kp_a.values()) + list(kp_b.values())):
        return []
    q_a = mathutils.Quaternion([kp_a[i].co.y for i in range(4)]).normalized()
    q_b = mathutils.Quaternion([kp_b[i].co.y for i in range(4)]).normalized()

    for i in range(4):
        kp_a[i].interpolation = "LINEAR"

    touched = []
    for n in range(1, num_points + 1):
        t = n / (num_points + 1)
        s = (1 - (1 - t) ** power) if reverse else (t ** power)
        frame = frame_a + (frame_b - frame_a) * t
        q = q_a.slerp(q_b, s)
        for i in range(4):
            fc = by_index[i]
            kp = fc.keyframe_points.insert(frame, q[i], keyframe_type='KEYFRAME')
            kp.interpolation = "LINEAR"
        touched.append(round(frame, 3))
    for fc in by_index.values():
        fc.update()
    return touched


# ---------------------------------------------------------------------------
# Human motion rules: joint limits, weight shift / counterbalance, balance
#
# What actually reads as "doll-like" rather than human, beyond timing:
#   1. Joints bending on the wrong axis or too far (amplitude scaling can
#      push a quaternion's rotation axis off the joint's real hinge line).
#   2. No weight shift: a real body counter-rotates the torso/opposite limb
#      whenever one limb swings, to keep the center of mass over the feet.
#   3. Uniform speed instead of the accelerate-then-decelerate profile real
#      reaching/kicking/punching movements have (see apply_minimum_jerk).
# These rules are deliberately simple approximations, not a physics sim —
# they catch the most visually obvious anatomical tells cheaply.
# ---------------------------------------------------------------------------

# axis: 0/1/2 = this bone's local X/Y/Z (as authored via mathutils.Quaternion
# axis-angle in this rig's rest orientation — see the axis analysis notes).
# hinge joints (knee, elbow-equivalent) only rotate on one axis and only one
# way; ball joints get a per-axis range instead. Ranges are rough real-human
# ballpark figures (radians), meant to be adjusted per-rig, not medical fact.
#
# Canonical facing direction for this whole rig/toolset: -Y is forward.
# Every "reach/step/punch forward" pose uses a NEGATIVE local-X angle on
# Torso/Shoulder/Arm/Thigh (verified empirically: negating a bone's local X
# angle negates only the resulting world-Y position, world X/Z unchanged —
# see the axis analysis notes). The asymmetric X ranges below put the LARGER
# range on the negative (forward) side to match — keep it that way if you
# add bones; flipping forward/back later means flipping these ranges too.
#
# Legacy limits for one authored rig. A joint's local hinge axis/sign follows
# its rest basis and constraints, NOT the sign of its parent's rotation.
# Validate these bounds against the current skeleton before use. New workflows
# supply explicit profile['limits'] to refine_motion instead of assuming anatomy.
JOINT_RULES = {
    "Leg_R":  dict(type="hinge", axis=0, min=-2.35, max=2.35),
    "Leg_L":  dict(type="hinge", axis=0, min=-2.35, max=2.35),
    "Hand_R": dict(type="hinge", axis=0, min=-2.6, max=2.6),
    "Hand_L": dict(type="hinge", axis=0, min=-2.6, max=2.6),
    # axis 2 (Z, hip abduction/adduction -- lifting the leg out to the side)
    # widened for kicks like a roundhouse that need real hip abduction; a
    # forward/back-only kick won't touch this axis anyway.
    "Thigh_R": dict(type="ball", limits={0: (-2.0, 0.5), 1: (-0.3, 0.3), 2: (-1.3, 1.3)}),
    "Thigh_L": dict(type="ball", limits={0: (-2.0, 0.5), 1: (-0.3, 0.3), 2: (-1.3, 1.3)}),
    "Arm_R":   dict(type="ball", limits={0: (-2.6, 1.0), 1: (-1.2, 1.2), 2: (-1.5, 1.5)}),
    "Arm_L":   dict(type="ball", limits={0: (-2.6, 1.0), 1: (-1.2, 1.2), 2: (-1.5, 1.5)}),
    "Shoulder_R": dict(type="ball", limits={0: (-0.4, 0.4), 1: (-0.3, 0.3), 2: (-0.4, 0.4)}),
    "Shoulder_L": dict(type="ball", limits={0: (-0.4, 0.4), 1: (-0.3, 0.3), 2: (-0.4, 0.4)}),
    # Torso/Chest point UP at rest (opposite of Thigh/Arm, which point down),
    # so the same world-X rotation tilts them the OPPOSITE way: forward lean
    # for these two is POSITIVE local X (verified empirically), so the large
    # side of the range stays on the positive side, unlike the limb bones above.
    "Torso": dict(type="ball", limits={0: (-0.5, 0.6), 1: (-1.0, 1.0), 2: (-0.4, 0.4)}),
    "Chest": dict(type="ball", limits={0: (-0.4, 0.5), 1: (-0.5, 0.5), 2: (-0.3, 0.3)}),
    "Head":  dict(type="ball", limits={0: (-0.6, 0.6), 1: (-0.8, 0.8), 2: (-0.5, 0.5)}),
}


def clamp_joint_limits(obj, bone_name, prop=None):
    """Clip a bone's existing keyframes into JOINT_RULES' plausible range.
    Hinge joints are also projected onto their single bend axis, discarding
    any swing on the other two — a knee or elbow does not bend sideways,
    which is exactly the kind of drift amplitude scaling or composed
    rotations can introduce. Returns the frames that were actually clamped
    (i.e. were out of range) so callers can tell whether anything fired."""
    import math
    rule = JOINT_RULES.get(bone_name)
    if rule is None:
        return []
    prop = prop or _rotation_prop(obj, bone_name)
    fcurves = _bone_fcurves(obj, bone_name, prop)
    if not fcurves:
        return []
    if prop == "rotation_quaternion":
        return _clamp_quaternion_fcurves(fcurves, rule)
    return _clamp_euler_fcurves(fcurves, rule)


def _clamp_quaternion_fcurves(fcurves, rule):
    import mathutils, math
    by_index = {fc.array_index: fc for fc in fcurves}
    if len(by_index) < 4:
        return []
    axis_vecs = [mathutils.Vector((1, 0, 0)), mathutils.Vector((0, 1, 0)), mathutils.Vector((0, 0, 1))]
    frames = sorted({round(kp.co.x, 4) for kp in by_index[0].keyframe_points})
    changed = []
    for frame in frames:
        kps = {i: _find_keyframe(by_index[i], frame) for i in range(4)}
        if any(v is None for v in kps.values()):
            continue
        q = mathutils.Quaternion([kps[i].co.y for i in range(4)]).normalized()

        if rule["type"] == "hinge":
            n = axis_vecs[rule["axis"]]
            vec = mathutils.Vector((q.x, q.y, q.z))
            proj = vec.dot(n)
            twist_w, twist_v = q.w, n * proj
            twist_len = (twist_w**2 + twist_v.length**2) ** 0.5
            angle = 2 * math.atan2(proj, q.w) if twist_len > 1e-8 else 0.0
            clamped = max(rule["min"], min(rule["max"], angle))
            new_q = mathutils.Quaternion(n, clamped)
            fired = abs(clamped - angle) > 1e-4
        else:
            eul = q.to_euler('XYZ')
            vals = [eul.x, eul.y, eul.z]
            fired = False
            for ax, (lo, hi) in rule["limits"].items():
                c = max(lo, min(hi, vals[ax]))
                if abs(c - vals[ax]) > 1e-4:
                    fired = True
                vals[ax] = c
            new_q = mathutils.Euler(vals, 'XYZ').to_quaternion()

        if fired:
            for i in range(4):
                kps[i].co.y = new_q[i]
            changed.append(frame)
    for fc in by_index.values():
        fc.update()
    return changed


def _clamp_euler_fcurves(fcurves, rule):
    by_index = {fc.array_index: fc for fc in fcurves}
    changed = []
    if rule["type"] == "hinge":
        for idx, fc in by_index.items():
            for kp in fc.keyframe_points:
                new_v = kp.co.y if idx == rule["axis"] else 0.0
                if idx == rule["axis"]:
                    new_v = max(rule["min"], min(rule["max"], new_v))
                if abs(new_v - kp.co.y) > 1e-4:
                    changed.append(round(kp.co.x, 3))
                kp.co.y = new_v
            fc.update()
    else:
        for idx, fc in by_index.items():
            lo, hi = rule["limits"].get(idx, (-6.28, 6.28))
            for kp in fc.keyframe_points:
                new_v = max(lo, min(hi, kp.co.y))
                if abs(new_v - kp.co.y) > 1e-4:
                    changed.append(round(kp.co.x, 3))
                kp.co.y = new_v
            fc.update()
    return changed


def enforce_joint_limits(obj, bone_names):
    """Run clamp_joint_limits over several bones; returns {bone: [frames]}
    for every bone where something was actually out of range."""
    report = {}
    for bone_name in bone_names:
        fired = clamp_joint_limits(obj, bone_name)
        if fired:
            report[bone_name] = fired
    return report


def _insert_quat_keyframe(obj, bone_name, frame, quat):
    pb = obj.pose.bones[bone_name]
    pb.rotation_quaternion = quat
    pb.keyframe_insert(data_path="rotation_quaternion", frame=frame)


def set_hinge_flexion(obj, parent_bone, child_bone, frame, flexion_deg):
    """LEGACY WORLD-ANGLE CONVENTION; not a generic local hinge solver.

    Set child_bone's own rotation so the joint's ACTUAL fold angle away
    from a straight limb equals flexion_deg -- e.g. 'bend the knee 74
    degrees from fully extended', not 'set the child's raw local value to
    74 degrees'. This distinction matters because parent and child rotate
    around the SAME world axis here, so their angles simply add: the real
    fold seen at the joint is (parent_angle + child_angle), not child_angle
    alone. Picking a child value without knowing the parent's current angle
    is exactly what produced a knee that folds too much or the wrong way in
    earlier passes. Requires parent_bone to already have a keyframe at
    `frame`; writes/overwrites child_bone's keyframe at that same frame.
    Positive flexion_deg = folds correctly (see JOINT_RULES notes on sign);
    0 = straight. Returns the parent/child angles actually used, in degrees,
    for sanity-checking."""
    import math, mathutils
    prop = _rotation_prop(obj, parent_bone)
    p_idx = {fc.array_index: fc for fc in _bone_fcurves(obj, parent_bone, prop)}
    if len(p_idx) < 4:
        raise ValueError(f"{parent_bone} has no rotation_quaternion fcurves")
    p_kp = {i: _find_keyframe(p_idx[i], frame) for i in range(4)}
    if any(v is None for v in p_kp.values()):
        raise ValueError(f"{parent_bone} has no keyframe at frame {frame}")
    p_q = mathutils.Quaternion([p_kp[i].co.y for i in range(4)]).normalized()
    # Extract ONLY the X-axis twist component (swing-twist decomposition),
    # not the combined axis-angle of the whole rotation -- the parent may
    # have abduction (Z) mixed in with flexion (X) (e.g. a roundhouse-style
    # hip), and to_axis_angle() on the combined quaternion would report an
    # angle dominated by whichever axis has the larger contribution, silently
    # producing a wrong flexion number.
    twist = mathutils.Quaternion((p_q.w, p_q.x, 0.0, 0.0))
    if twist.magnitude < 1e-8:
        p_signed = 0.0
    else:
        twist.normalize()
        p_signed = 2 * math.atan2(twist.x, twist.w)

    target_total = math.radians(flexion_deg)
    child_signed = target_total - p_signed
    new_q = mathutils.Quaternion((1, 0, 0), child_signed)
    _insert_quat_keyframe(obj, child_bone, frame, new_q)

    return {
        "parent_angle_deg": round(math.degrees(p_signed), 2),
        "child_angle_deg": round(math.degrees(child_signed), 2),
        "resulting_flexion_deg": flexion_deg,
    }


def apply_counterbalance(obj, mover_bone, balance_bone, ratio=-0.35, frames=None):
    """Weight shift / contrapposto: at every frame `mover_bone` is keyed
    (or the given `frames`), set `balance_bone` to a scaled counter-rotation
    of the mover's own rotation (relative to rest). A kicking leg swinging
    forward by angle A should be met by e.g. the torso or opposite shoulder
    rotating by ratio*A the other way — without this, one limb moves while
    the rest of the body stays frozen, which is the single biggest 'puppet'
    tell. Negative ratio (default) means "the other way"; both bones must be
    quaternion (this rig's convention)."""
    import mathutils
    mover_fcs = _bone_fcurves(obj, mover_bone, "rotation_quaternion")
    by_index = {fc.array_index: fc for fc in mover_fcs}
    if len(by_index) < 4:
        return []
    target_frames = frames if frames is not None else sorted(
        {round(kp.co.x, 4) for kp in by_index[0].keyframe_points})
    touched = []
    for frame in target_frames:
        kps = {i: _find_keyframe(by_index[i], frame) for i in range(4)}
        if any(v is None for v in kps.values()):
            continue
        q = mathutils.Quaternion([kps[i].co.y for i in range(4)]).normalized()
        axis, angle = q.to_axis_angle()
        counter = mathutils.Quaternion((1, 0, 0, 0)) if abs(angle) < 1e-6 else mathutils.Quaternion(axis, angle * ratio)
        _insert_quat_keyframe(obj, balance_bone, frame, counter)
        touched.append(frame)
    for fc in _bone_fcurves(obj, balance_bone, "rotation_quaternion"):
        fc.update()
    return touched


def check_balance(obj, frame, support_bones, com_bones=None):
    """Rough static-balance proxy (not a physics sim): approximate the
    body's center of mass as a weighted average of major bone midpoints and
    check whether its ground-plane (X/Y) position falls within the
    horizontal range of the current support foot/feet. Useful to sanity
    check a landing/single-leg pose isn't leaving the character floating
    outside its own feet."""
    import mathutils
    bpy.context.scene.frame_set(int(round(frame)))
    com_bones = com_bones or {
        "Torso": 0.35, "Chest": 0.25, "Head": 0.08,
        "Thigh_R": 0.08, "Thigh_L": 0.08,
        "Arm_R": 0.04, "Arm_L": 0.04, "Leg_R": 0.03, "Leg_L": 0.03,
    }
    total_w = sum(com_bones.values())
    com = mathutils.Vector((0.0, 0.0, 0.0))
    for bone, w in com_bones.items():
        pb = obj.pose.bones.get(bone)
        if pb is None:
            continue
        mid = ((obj.matrix_world @ pb.head) + (obj.matrix_world @ pb.tail)) * 0.5
        com += mid * (w / total_w)

    support_pts = []
    for bone in support_bones:
        pb = obj.pose.bones.get(bone)
        if pb is None:
            continue
        support_pts.append(obj.matrix_world @ pb.tail)
    if not support_pts:
        return {"error": "no support points found"}

    xs = [p.x for p in support_pts]
    ys = [p.y for p in support_pts]
    margin = 0.08  # feet have real width/length, not a single point
    in_x = (min(xs) - margin) <= com.x <= (max(xs) + margin)
    in_y = (min(ys) - margin) <= com.y <= (max(ys) + margin)
    return {
        "com_xy": (round(com.x, 3), round(com.y, 3)),
        "support_x_range": (round(min(xs) - margin, 3), round(max(xs) + margin, 3)),
        "support_y_range": (round(min(ys) - margin, 3), round(max(ys) + margin, 3)),
        "balanced": in_x and in_y,
    }


def compute_com_trajectory(obj, frame_start, frame_end, samples=60, com_bones=None):
    """The body's center of mass computed FRESH at each sampled frame from
    whatever pose is currently keyed (not a fixed/static point) -- the actual
    3D path the COM travels over the course of the action. This is what
    'decide the center of mass according to the motion' means concretely:
    it comes out of the pose data itself, frame by frame, rather than being
    a single assumed point. Returns a list of (frame, Vector) samples; feed
    this into apply_centripetal_drift below."""
    import mathutils
    com_bones = com_bones or {
        "Torso": 0.35, "Chest": 0.25, "Head": 0.08,
        "Thigh_R": 0.08, "Thigh_L": 0.08,
        "Arm_R": 0.04, "Arm_L": 0.04, "Leg_R": 0.03, "Leg_L": 0.03,
    }
    total_w = sum(com_bones.values())
    orig_frame = bpy.context.scene.frame_current
    traj = []
    for i in range(samples + 1):
        f = frame_start + (frame_end - frame_start) * i / samples
        bpy.context.scene.frame_set(int(round(f)))
        com = mathutils.Vector((0.0, 0.0, 0.0))
        for bone, w in com_bones.items():
            pb = obj.pose.bones.get(bone)
            if pb is None:
                continue
            mid = ((obj.matrix_world @ pb.head) + (obj.matrix_world @ pb.tail)) * 0.5
            com += mid * (w / total_w)
        traj.append((f, com))
    bpy.context.scene.frame_set(orig_frame)
    return traj


def apply_centripetal_drift(obj, bone_name, driver_bone, frame_start, frame_end,
                             gain=0.4, axis=(0, 0, 1), samples=40):
    """Push a limb OUTWARD from the spin axis in proportion to how fast the
    body is actually rotating at that instant (driver_bone's angular speed,
    squared -- real centrifugal drift scales with omega^2), instead of a
    hand-picked, fixed 'arms out' pose. This is the concrete form of 'let
    the centripetal/centrifugal force at each moment decide the pose that
    carries the most load': fast part of the spin -> limbs get pulled out
    hard: slow part -> they barely drift. Adds this as an EXTRA rotation on
    top of whatever is already keyed (additive, via quaternion multiply), so
    call it after the base pose + any inertial lag is already in place.
    `axis` is the bone's local axis that represents 'away from the body'
    (Z for this rig's Thigh/Arm bones, i.e. abduction). Returns the frames
    touched."""
    import mathutils, math
    driver_fcs = _bone_fcurves(obj, driver_bone, "rotation_quaternion")
    d_idx = {fc.array_index: fc for fc in driver_fcs}
    if len(d_idx) < 4:
        return []
    prop = _rotation_prop(obj, bone_name)
    if prop != "rotation_quaternion":
        return []
    fcurves = _bone_fcurves(obj, bone_name, prop)
    by_idx = {fc.array_index: fc for fc in fcurves}
    if len(by_idx) < 4:
        return []

    def driver_q(f):
        return mathutils.Quaternion([d_idx[i].evaluate(f) for i in range(4)]).normalized()

    dt = (frame_end - frame_start) / samples
    # angular speed via finite difference on the driver's quaternion
    omegas = []
    prev_q = driver_q(frame_start)
    for i in range(1, samples + 1):
        f = frame_start + i * dt
        cur_q = driver_q(f)
        dot = max(-1.0, min(1.0, abs(prev_q.dot(cur_q))))
        dtheta = 2 * math.acos(dot)
        omegas.append((f - dt / 2, dtheta / dt if dt > 0 else 0.0))
        prev_q = cur_q
    peak_omega_sq = max((w * w for _, w in omegas), default=1e-6) or 1e-6

    touched = []
    for f, w in omegas:
        base_q = mathutils.Quaternion([by_idx[i].evaluate(f) for i in range(4)]).normalized()
        strength = gain * (w * w) / peak_omega_sq  # normalized 0..gain
        if strength < 1e-4:
            continue
        drift = mathutils.Quaternion(axis, strength)
        new_q = base_q @ drift
        for i in range(4):
            kp = by_idx[i].keyframe_points.insert(f, new_q[i], keyframe_type='KEYFRAME')
            kp.interpolation = "LINEAR"
        touched.append(round(f, 3))
    for fc in by_idx.values():
        fc.update()
    return touched


def simulate_inertial_lag(obj, bone_name, stiffness=0.35, damping=0.55, sample_step=1.0, prop=None):
    """Real physical follow-through: treat the bone's EXISTING authored
    curve as a target being chased by a damped spring (a stand-in for the
    limb's own mass/inertia), instead of the bone instantly hitting every
    keyframe on schedule. This is what 'one part's motion creates inertia
    that moves the rest of the body' means mechanically -- the limb lags
    behind the target, can overshoot, and settles, all driven by the SAME
    target curve's velocity/acceleration rather than independently chosen
    timing. This is also the direct fix for joints that read as moving
    'separately': a spring chain reacting to a shared target reads as one
    connected system.
    stiffness: pull-back strength (higher = shorter lag, snaps closer to
    target). damping: energy loss (higher = less overshoot/wobble). Keep
    both under ~1.0 with sample_step=1 frame or the simple integrator can
    blow up. Replaces the curve with a dense LINEAR-keyed simulated result;
    call this AFTER poses/enhancement are final but BEFORE retiming."""
    import mathutils
    prop = prop or _rotation_prop(obj, bone_name)
    if prop != "rotation_quaternion":
        return []
    fcurves = _bone_fcurves(obj, bone_name, prop)
    by_idx = {fc.array_index: fc for fc in fcurves}
    if len(by_idx) < 4:
        return []
    frames_all = sorted({round(kp.co.x, 4) for kp in by_idx[0].keyframe_points})
    if len(frames_all) < 2:
        return []
    f_start, f_end = frames_all[0], frames_all[-1]

    n_steps = max(2, int(round((f_end - f_start) / sample_step)) + 1)
    actual_step = (f_end - f_start) / (n_steps - 1)
    targets = []
    for i in range(n_steps):
        f = f_start + i * actual_step
        q = mathutils.Quaternion([by_idx[j].evaluate(f) for j in range(4)]).normalized()
        targets.append((f, q))

    pos = list(targets[0][1])
    vel = [0.0, 0.0, 0.0, 0.0]
    results = [(targets[0][0], mathutils.Quaternion(pos).normalized())]
    for i in range(1, n_steps):
        f, target = targets[i]
        tgt = list(target)
        if sum(a * b for a, b in zip(pos, tgt)) < 0:
            tgt = [-v for v in tgt]  # same hemisphere, avoid fighting a sign flip
        new_pos, new_vel = [], []
        for k in range(4):
            accel = stiffness * (tgt[k] - pos[k]) - damping * vel[k]
            v = vel[k] + accel
            p = pos[k] + v
            new_vel.append(v)
            new_pos.append(p)
        pos, vel = new_pos, new_vel
        results.append((f, mathutils.Quaternion(pos).normalized()))

    for fc in by_idx.values():
        for i in range(len(fc.keyframe_points) - 1, -1, -1):
            fc.keyframe_points.remove(fc.keyframe_points[i])
    for f, q in results:
        for k in range(4):
            kp = by_idx[k].keyframe_points.insert(f, q[k], keyframe_type='KEYFRAME')
            kp.interpolation = "LINEAR"
    for fc in by_idx.values():
        fc.update()
    return [f for f, _ in results]


def find_hinge_violations(obj, child_bone, parent_bone, samples=60):
    """Scan the CURRENT interpolated pose (not just the authored keyframes)
    for moments where the child segment's tip ends up on the WRONG side of
    its own joint -- e.g. a knee where the foot is further forward than the
    knee while the thigh is raised, a physically backward-bending knee.
    This class of bug is invisible at the keyframes themselves: each bone's
    fcurve is interpolated independently (doubly so after retiming/minimum-
    jerk, which reshape each curve on its own timeline), so two bones that
    fold correctly at every authored pose can still drift onto the wrong
    relative side somewhere in the gap between poses. Checks world-space
    tip position, matching how this was visually verified, not local-axis
    sign math (which is unreliable near small angles).
    Returns a list of {frame, thigh_lift, fold} for violations, where `fold`
    is (foot_y - knee_y) and negative means the foot is forward of the knee
    while the parent is meaningfully raised (thigh_lift > 0.05)."""
    action = _get_action(obj)
    f_start, f_end = action.frame_range
    pb_child = obj.pose.bones.get(child_bone)
    pb_parent = obj.pose.bones.get(parent_bone)
    if pb_child is None or pb_parent is None:
        return []
    violations = []
    orig_frame = bpy.context.scene.frame_current
    for i in range(samples + 1):
        frame = f_start + (f_end - f_start) * i / samples
        bpy.context.scene.frame_set(int(round(frame)))
        hip = obj.matrix_world @ pb_parent.head
        knee = obj.matrix_world @ pb_child.head
        foot = obj.matrix_world @ pb_child.tail
        thigh_lift = hip.y - knee.y
        fold = foot.y - knee.y
        if thigh_lift > 0.05 and fold < -0.02:
            violations.append({"frame": round(frame, 3), "thigh_lift": round(thigh_lift, 3), "fold": round(fold, 3)})
    bpy.context.scene.frame_set(orig_frame)
    return violations


def sync_zero_crossings(obj, child_bone, parent_bone):
    """The robust fix for hinge desync: find every frame where parent_bone's
    rotation crosses zero (changes direction) and pin child_bone to identity
    (straight) at that EXACT frame. Two independently-interpolated curves
    that both need to cross zero together (parent swinging past neutral,
    child un-folding through straight) will otherwise cross at slightly
    different frames — since retiming/minimum-jerk reshape each curve's
    timing independently — leaving a window where both have the SAME sign
    (a backward-bending joint). Pinning both to 0 at the same instant removes
    that window structurally instead of patching individual bad frames
    (which doesn't converge, since patching one frame changes the curve
    shape feeding the neighboring samples). Call this LAST, after all other
    keyframing/enhancement/retiming on both bones. Returns the frames fixed."""
    import mathutils
    p_idx = {fc.array_index: fc for fc in _bone_fcurves(obj, parent_bone, "rotation_quaternion")}
    c_idx = {fc.array_index: fc for fc in _bone_fcurves(obj, child_bone, "rotation_quaternion")}
    if len(p_idx) < 4 or len(c_idx) < 4:
        return []

    def parent_angle(frame):
        q = mathutils.Quaternion([p_idx[j].evaluate(frame) for j in range(4)]).normalized()
        axis, angle = q.to_axis_angle()
        return angle * (1 if axis.x >= 0 else -1)

    frames_sorted = sorted({round(kp.co.x, 4) for kp in p_idx[0].keyframe_points})
    crossings = []
    for fa, fb in zip(frames_sorted, frames_sorted[1:]):
        a, b = parent_angle(fa), parent_angle(fb)
        if a == 0 or b == 0 or (a > 0) == (b > 0):
            continue
        lo, hi, a_sign = fa, fb, a > 0
        for _ in range(30):
            mid = (lo + hi) / 2
            if (parent_angle(mid) > 0) == a_sign:
                lo = mid
            else:
                hi = mid
        crossings.append((lo + hi) / 2)

    fixed = []
    ident = mathutils.Quaternion((1, 0, 0, 0))
    for frame in crossings:
        for j in range(4):
            kp = c_idx[j].keyframe_points.insert(frame, ident[j], keyframe_type='KEYFRAME')
            kp.interpolation = "LINEAR"
        fixed.append(round(frame, 3))
    for fc in c_idx.values():
        fc.update()
    return fixed


# ---------------------------------------------------------------------------
# Animation principles as callable primitives
# ---------------------------------------------------------------------------

def add_anticipation(obj, bone_name, key_frame, rest_frame=None,
                      lead_frames=6, intensity=0.2, prop=None):
    """Insert a small counter-motion keyframe before `key_frame` so the pose
    winds up in the opposite direction before committing to the action.
    Requires an existing keyframe on `key_frame` and a reference `rest_frame`
    (defaults to the first keyframe frame found on the curve)."""
    prop = prop or _rotation_prop(obj, bone_name)
    fcurves = _bone_fcurves(obj, bone_name, prop)
    if not fcurves:
        raise ValueError(f"No {prop} fcurves for bone '{bone_name}'")
    if prop == "rotation_quaternion":
        return _add_anticipation_quaternion(fcurves, key_frame, rest_frame, lead_frames, intensity)
    return _add_anticipation_euler(fcurves, key_frame, rest_frame, lead_frames, intensity)


def _add_anticipation_euler(fcurves, key_frame, rest_frame, lead_frames, intensity):
    touched = []
    for fc in fcurves:
        target_kp = _find_keyframe(fc, key_frame)
        if target_kp is None:
            continue
        ref_frame = rest_frame
        if ref_frame is None:
            frames = [kp.co.x for kp in fc.keyframe_points if kp.co.x < key_frame]
            ref_frame = min(frames) if frames else key_frame - lead_frames
        rest_kp = _find_keyframe(fc, ref_frame)
        rest_value = rest_kp.co.y if rest_kp else fc.evaluate(ref_frame)
        target_value = target_kp.co.y
        delta = target_value - rest_value
        anticip_frame = key_frame - lead_frames
        anticip_value = rest_value - delta * intensity
        kp = fc.keyframe_points.insert(anticip_frame, anticip_value, keyframe_type='KEYFRAME')
        kp.interpolation = "BEZIER"
        kp.easing = "EASE_IN_OUT"
        touched.append(fc.data_path + f"[{fc.array_index}]")
    return touched


def _add_anticipation_quaternion(fcurves, key_frame, rest_frame, lead_frames, intensity):
    """Same idea as the euler version, but computed as a proper rotation
    (rest -> target rotation difference, inverted and scaled) so the inserted
    keyframe stays a valid unit quaternion instead of a warped one."""
    import mathutils
    by_index = {fc.array_index: fc for fc in fcurves}
    if len(by_index) < 4:
        return []
    target_kps = {}
    for i in range(4):
        kp = _find_keyframe(by_index[i], key_frame)
        if kp is None:
            return []
        target_kps[i] = kp

    ref_frame = rest_frame
    if ref_frame is None:
        frames = [kp.co.x for kp in by_index[0].keyframe_points if kp.co.x < key_frame]
        ref_frame = min(frames) if frames else key_frame - lead_frames

    rest_values = []
    for i in range(4):
        rest_kp = _find_keyframe(by_index[i], ref_frame)
        rest_values.append(rest_kp.co.y if rest_kp else by_index[i].evaluate(ref_frame))
    rest = mathutils.Quaternion(rest_values).normalized()
    target = mathutils.Quaternion([target_kps[i].co.y for i in range(4)]).normalized()

    diff = rest.rotation_difference(target)
    axis, angle = diff.to_axis_angle()
    anticip = rest if abs(angle) < 1e-6 else (mathutils.Quaternion(axis, -angle * intensity) @ rest)

    anticip_frame = key_frame - lead_frames
    touched = []
    for i in range(4):
        fc = by_index[i]
        kp = fc.keyframe_points.insert(anticip_frame, anticip[i], keyframe_type='KEYFRAME')
        kp.interpolation = "BEZIER"
        kp.easing = "EASE_IN_OUT"
        fc.update()
        touched.append(fc.data_path + f"[{fc.array_index}]")
    return touched


def add_overshoot(obj, bone_name, key_frame, prop=None, back=1.8, easing="EASE_OUT"):
    """Make the keyframe landing on `key_frame` overshoot past its target and
    settle back, using Blender's native BACK interpolation (applied to the
    keyframe that leads INTO key_frame)."""
    prop = prop or _rotation_prop(obj, bone_name)
    fcurves = _bone_fcurves(obj, bone_name, prop)
    touched = []
    for fc in fcurves:
        kps = sorted(fc.keyframe_points, key=lambda k: k.co.x)
        for i, kp in enumerate(kps):
            if abs(kp.co.x - key_frame) <= 0.01 and i > 0:
                prev = kps[i - 1]
                prev.interpolation = "BACK"
                prev.easing = easing
                prev.back = back
                touched.append(fc.data_path + f"[{fc.array_index}]")
    return touched


def add_secondary_motion(obj, bone_chain, from_frame, delay_step=2, prop=None):
    """Stagger keyframes down a parent->child bone chain so children lag
    behind, faking whip-like follow-through. bone_chain[0] keeps its timing."""
    touched = []
    for i, bone_name in enumerate(bone_chain):
        if i == 0:
            continue
        delay = i * delay_step
        this_prop = prop or _rotation_prop(obj, bone_name)
        for fc in _bone_fcurves(obj, bone_name, this_prop):
            pts = sorted(fc.keyframe_points, key=lambda k: k.co.x, reverse=True)
            for kp in pts:
                if kp.co.x >= from_frame - 0.01:
                    _shift_keyframe(kp, delay)
            fc.update()
            touched.append(fc.data_path + f"[{fc.array_index}]")
    return touched


def add_impact_hold(obj, frame, hold_frames=3):
    """Freeze the whole rig's pose at `frame` for `hold_frames` (hit-stop),
    pushing every later keyframe back to make room."""
    action = _get_action(obj)
    touched = []
    for fc in _iter_fcurves(action, obj):
        pts = sorted(fc.keyframe_points, key=lambda k: k.co.x, reverse=True)
        for kp in pts:
            if kp.co.x > frame + 0.01:
                _shift_keyframe(kp, hold_frames)
        held = _find_keyframe(fc, frame)
        if held is not None:
            new_kp = fc.keyframe_points.insert(
                frame + hold_frames, held.co.y, keyframe_type='KEYFRAME')
            new_kp.interpolation = "CONSTANT"
            touched.append(fc.data_path + f"[{fc.array_index}]")
        fc.update()
    return touched


def scale_amplitude(obj, bone_name, factor, frame_start=None, frame_end=None, prop=None):
    """Exaggerate (factor > 1) or dampen (factor < 1) a bone's rotation swing
    relative to its rest pose (identity), within an optional frame window.
    Quaternion bones are scaled by rotation ANGLE (not raw component values)
    so the result stays a valid, non-warping unit quaternion."""
    prop = prop or _rotation_prop(obj, bone_name)
    fcurves = _bone_fcurves(obj, bone_name, prop)
    if not fcurves:
        return []
    if prop == "rotation_quaternion":
        return _scale_quaternion_fcurves(fcurves, factor, frame_start, frame_end)
    return _scale_euler_fcurves(fcurves, factor, frame_start, frame_end)


def _scale_euler_fcurves(fcurves, factor, frame_start, frame_end):
    """Euler axes scale independently around 0 (the rest value)."""
    touched = []
    for fc in fcurves:
        for kp in fc.keyframe_points:
            if frame_start is not None and kp.co.x < frame_start:
                continue
            if frame_end is not None and kp.co.x > frame_end:
                continue
            kp.co.y *= factor
            kp.handle_left.y *= factor
            kp.handle_right.y *= factor
        fc.update()
        touched.append(fc.data_path + f"[{fc.array_index}]")
    return touched


def _scale_quaternion_fcurves(fcurves, factor, frame_start, frame_end):
    """Scale the rotation ANGLE of the (w,x,y,z) keyed together at each frame,
    keeping the axis fixed and the quaternion unit-length."""
    import mathutils
    by_index = {fc.array_index: fc for fc in fcurves}
    if len(by_index) < 4:
        return []
    frames = sorted({round(kp.co.x, 4) for kp in by_index[0].keyframe_points})
    for frame in frames:
        if frame_start is not None and frame < frame_start:
            continue
        if frame_end is not None and frame > frame_end:
            continue
        kps = {}
        for i in range(4):
            kp = _find_keyframe(by_index[i], frame)
            if kp is None:
                break
            kps[i] = kp
        if len(kps) < 4:
            continue
        quat = mathutils.Quaternion([kps[i].co.y for i in range(4)]).normalized()
        axis, angle = quat.to_axis_angle()
        if abs(angle) < 1e-6:
            continue
        scaled = mathutils.Quaternion(axis, angle * factor)
        for i in range(4):
            kps[i].co.y = scaled[i]
    touched = []
    for fc in by_index.values():
        fc.update()
        touched.append(fc.data_path + f"[{fc.array_index}]")
    return touched


# ---------------------------------------------------------------------------
# Intent presets: the "AI-facing" entry point
# ---------------------------------------------------------------------------

INTENT_PRESETS = {
    "HEAVY_STRIKE": dict(anticipation=0.28, lead_frames=7, overshoot_back=2.4,
                          impact_hold=4, amplitude=1.3, slow_factor=1.6),
    "SWIFT_SLASH": dict(anticipation=0.12, lead_frames=3, overshoot_back=1.4,
                         impact_hold=1, amplitude=1.1, slow_factor=1.15),
    "REALISTIC_WEIGHT": dict(anticipation=0.18, lead_frames=5, overshoot_back=1.8,
                              impact_hold=2, amplitude=1.0, slow_factor=1.4),
    "SUBTLE": dict(anticipation=0.08, lead_frames=3, overshoot_back=1.1,
                   impact_hold=0, amplitude=1.0, slow_factor=1.2),
}


def apply_intent(obj, bone_chain, key_frame, preset="HEAVY_STRIKE", overrides=None, humanize=True):
    """Legacy in-place artistic preset. For general/contact-safe work use
    refine_motion with a rig profile and explicit plan instead.

    Single call an AI can use to push an existing pose-to-pose animation
    toward a bolder, more physical read. `bone_chain` should be ordered
    parent -> child (e.g. shoulder, elbow, wrist) with `key_frame` being the
    frame where the main pose (e.g. the strike contact) is already keyed.
    When `humanize` is True (default), finishes with humanize_motion: slows
    the clip's overall timing and adds real breakdown poses + natural easing
    so sparse pose-to-pose keys don't read as stiff/rushed next to reference
    mocap. Returns a report dict of what was changed."""
    params = dict(INTENT_PRESETS.get(preset, INTENT_PRESETS["REALISTIC_WEIGHT"]))
    if overrides:
        params.update(overrides)

    report = {"preset": preset, "params": params, "changes": {}}
    root = bone_chain[0]
    tip = bone_chain[-1]

    # anticipation initiates proximally (the torso/root winds up first)
    report["changes"]["anticipation"] = add_anticipation(
        obj, root, key_frame,
        lead_frames=params["lead_frames"], intensity=params["anticipation"])

    # overshoot matters most at both ends: the root winding into the strike
    # and the striking limb's tip carrying past its target
    overshoot_changes = list(add_overshoot(obj, root, key_frame, back=params["overshoot_back"]))
    if tip != root:
        overshoot_changes += add_overshoot(obj, tip, key_frame, back=params["overshoot_back"])
    report["changes"]["overshoot"] = overshoot_changes

    if len(bone_chain) > 1:
        report["changes"]["secondary_motion"] = add_secondary_motion(
            obj, bone_chain, key_frame, delay_step=2)

    if params["impact_hold"] > 0:
        report["changes"]["impact_hold"] = add_impact_hold(
            obj, key_frame, hold_frames=params["impact_hold"])

    # amplitude exaggerates the whole chain, not just the root, so the swing
    # actually reads as bigger rather than just the torso twitching more
    amplitude_changes = []
    for bone_name in bone_chain:
        amplitude_changes += scale_amplitude(obj, bone_name, params["amplitude"])
    report["changes"]["amplitude"] = amplitude_changes

    if humanize:
        report["humanize"] = humanize_motion(obj, bone_chain, slow_factor=params["slow_factor"])

    return report


# ---------------------------------------------------------------------------
# Diagnostics — verify the result actually reads as intended
# ---------------------------------------------------------------------------

def diagnose_motion(obj, bone_names, samples=24):
    """Sample each bone's rotation curve across the action's frame range and
    report value range / whether it barely moves (the 'cardboard' tell)."""
    action = _get_action(obj)
    f_start, f_end = action.frame_range
    report = {}
    for bone_name in bone_names:
        prop = _rotation_prop(obj, bone_name)
        fcurves = _bone_fcurves(obj, bone_name, prop)
        axes = {}
        for fc in fcurves:
            vals = []
            for i in range(samples + 1):
                t = f_start + (f_end - f_start) * i / samples
                vals.append(fc.evaluate(t))
            rng = max(vals) - min(vals)
            axes[f"axis_{fc.array_index}"] = {
                "range": round(rng, 4),
                "min": round(min(vals), 4),
                "max": round(max(vals), 4),
                "near_static": rng < 0.02,
            }
        report[bone_name] = axes
    return report


# ---------------------------------------------------------------------------
# UI: Panel + Operators so a human can drive the same primitives
# ---------------------------------------------------------------------------

class EFAI_OT_apply_intent(bpy.types.Operator):
    bl_idname = "efai.apply_intent"
    bl_label = "Apply Intent Preset"
    bl_description = "Push the selected bone chain's animation toward the chosen preset feel"
    bl_options = {"REGISTER", "UNDO"}

    def execute(self, context):
        obj = context.object
        scene = context.scene
        chain = [b.strip() for b in scene.efai_bone_chain.split(",") if b.strip()]
        if not chain:
            self.report({"ERROR"}, "Enter at least one bone name (comma-separated)")
            return {"CANCELLED"}
        try:
            report = apply_intent(
                obj, chain, scene.efai_key_frame, preset=scene.efai_preset)
        except Exception as e:
            self.report({"ERROR"}, str(e))
            return {"CANCELLED"}
        self.report({"INFO"}, f"Applied {scene.efai_preset}: {list(report['changes'].keys())}")
        return {"FINISHED"}


class EFAI_OT_diagnose(bpy.types.Operator):
    bl_idname = "efai.diagnose"
    bl_label = "Diagnose Motion"
    bl_description = "Report per-bone rotation range to catch near-static ('cardboard') limbs"

    def execute(self, context):
        obj = context.object
        scene = context.scene
        chain = [b.strip() for b in scene.efai_bone_chain.split(",") if b.strip()]
        try:
            report = diagnose_motion(obj, chain)
        except Exception as e:
            self.report({"ERROR"}, str(e))
            return {"CANCELLED"}
        for bone, axes in report.items():
            for axis, info in axes.items():
                flag = " <-- LOW MOTION" if info["near_static"] else ""
                print(f"[EF AI] {bone}.{axis}: range={info['range']}{flag}")
        self.report({"INFO"}, "Diagnostic printed to console")
        return {"FINISHED"}


class EFAI_OT_humanize(bpy.types.Operator):
    bl_idname = "efai.humanize"
    bl_label = "Humanize (Continuous Motion)"
    bl_description = "Smooth velocity between poses; preserve timing at Slow Factor 1.0"
    bl_options = {"REGISTER", "UNDO"}

    def execute(self, context):
        obj = context.object
        scene = context.scene
        chain = [b.strip() for b in scene.efai_bone_chain.split(",") if b.strip()]
        if not chain:
            self.report({"ERROR"}, "Enter at least one bone name (comma-separated)")
            return {"CANCELLED"}
        try:
            humanize_motion(obj, chain, slow_factor=scene.efai_slow_factor)
        except Exception as e:
            self.report({"ERROR"}, str(e))
            return {"CANCELLED"}
        self.report({"INFO"}, f"Humanized {len(chain)} bone(s), slow_factor={scene.efai_slow_factor}")
        return {"FINISHED"}


class EFAI_PT_panel(bpy.types.Panel):
    bl_idname = "EFAI_PT_panel"
    bl_label = "EF AI Animation Assistant"
    bl_space_type = "VIEW_3D"
    bl_region_type = "UI"
    bl_category = "EF AI"

    def draw(self, context):
        layout = self.layout
        scene = context.scene
        layout.prop(scene, "efai_bone_chain")
        layout.prop(scene, "efai_key_frame")
        layout.prop(scene, "efai_preset")
        layout.operator("efai.apply_intent")
        layout.separator()
        layout.prop(scene, "efai_slow_factor")
        layout.operator("efai.humanize")
        layout.separator()
        layout.operator("efai.diagnose")


_CLASSES = (EFAI_OT_apply_intent, EFAI_OT_humanize, EFAI_OT_diagnose, EFAI_PT_panel)


def register():
    for cls in _CLASSES:
        bpy.utils.register_class(cls)
    bpy.types.Scene.efai_bone_chain = bpy.props.StringProperty(
        name="Bone Chain", description="Comma-separated, parent -> child (e.g. shoulder,elbow,wrist)",
        default="")
    bpy.types.Scene.efai_key_frame = bpy.props.IntProperty(
        name="Key Frame", description="Frame where the main pose is already keyed", default=12)
    bpy.types.Scene.efai_preset = bpy.props.EnumProperty(
        name="Preset",
        items=[(k, k.replace("_", " ").title(), "") for k in INTENT_PRESETS],
        default="HEAVY_STRIKE")
    bpy.types.Scene.efai_slow_factor = bpy.props.FloatProperty(
        name="Slow Factor", description="1.0 = unchanged, 1.5 = 50% slower",
        default=1.0, min=0.5, max=4.0)


def unregister():
    del bpy.types.Scene.efai_slow_factor
    del bpy.types.Scene.efai_preset
    del bpy.types.Scene.efai_key_frame
    del bpy.types.Scene.efai_bone_chain
    for cls in reversed(_CLASSES):
        bpy.utils.unregister_class(cls)


# ---------------------------------------------------------------------------
# General AI contract (v3): intent -> authored poses -> refine -> validate.
# Keep choreography OUT of this layer. No prompt keywords imply contact, mass,
# support, joint axes, or a universal obligation to move every bone.
# ---------------------------------------------------------------------------

MOTION_RULES = {
    "version": 7,
    "requires": ["authored action", "rig profile", "explicit contacts (or [])"],
    "priority": ["contacts", "authored anchors/holds", "joint limits", "inertia"],
    "units": "frames for events; seconds for simulation; radians for joint bounds",
    "mass_model": "positive relative masses at evaluated bone midpoints",
    "contact_policy": "preserve source curves on the entire ancestor chain",
    "upper_limb_policy": "plan forward and lateral hand travel for active arms; use authored lateral_targets with calibrated local axes",
    "dynamic_policy": "sample evaluated COM each frame; use bounded translational and rotational inertia on authored, unprotected deformation joints",
    "helper_policy": "animated deforming joint helpers follow parent angular change with bounded lag when topology confirms a leaf beside a longer chain",
    "key_timing_policy": "share start/end keys; add intermediate keys only to explicitly posed joints, allowing rig-specific stagger and holds",
    "reference_timing_policy": "for existing rig-valid motion, identify source load/impact/recovery beats and retime all channels together; preserve poses, contacts and original action",
    "limb_articulation_policy": "offset swing and lateral spread only on explicitly selected limb joints with verified local axes, phase knots and contact-protected support chains",
    "bilateral_policy": "sample source COM per frame against explicit left/right anchors; damp the loaded side and increase the free side within bounds, except smoothly synchronized windows",
    "limitations": ["not a text-to-motion model", "not a collision/rigid-body solver",
                    "contacts preserve authored positions, not repair foot sliding",
                    "limits require rig-specific local axes; no inferred anatomy"],
}


def inspect_motion_rig(obj):
    """Read-only inventory for an AI to construct a rig profile and motion plan."""
    if not obj or obj.type != 'ARMATURE':
        raise ValueError('Expected an armature')
    action = obj.animation_data.action if obj.animation_data else None
    return {"rules": MOTION_RULES, "object": obj.name,
            "action": action.name if action else None,
            "frame_range": list(action.frame_range) if action else None,
            "fps": bpy.context.scene.render.fps / bpy.context.scene.render.fps_base,
            "bones": [{"name": b.name, "parent": b.parent.name if b.parent else None,
                       "rotation_mode": b.rotation_mode, "length": b.bone.length,
                       "constraints": [c.type for c in b.constraints],
                       "animated": bool(_bone_fcurves(obj, b.name)) if action else False}
                      for b in obj.pose.bones]}


_ROLE_WORDS = {
    "root": ("root", "hips", "hip", "pelvis", "cog", "master"),
    "spine": ("spine", "torso", "chest", "neck"),
    "head": ("head", "jaw", "skull"),
    "arm": ("shoulder", "clavicle", "upperarm", "arm", "forearm", "elbow"),
    "hand": ("hand", "wrist", "palm", "finger", "thumb"),
    "leg": ("thigh", "upleg", "upperleg", "leg", "shin", "calf", "knee"),
    "foot": ("foot", "ankle", "toe", "ball"),
    "tail": ("tail",), "wing": ("wing",), "accessory": ("hair", "cloth", "cape", "ear"),
}
_CONTROL_WORDS = ("ik", "fk", "ctrl", "control", "pole", "target", "socket", "weapon", "tool", "mch", "mechanism")


def _name_tokens(name):
    import re
    value = re.sub(r"([a-z])([A-Z])", r"\1_\2", name).lower()
    return tuple(v for v in re.split(r"[^a-z0-9]+", value) if v)


def analyze_rig(obj, overrides=None):
    """Infer a conservative, name-optional refinement profile for any armature.

    Geometry/topology determines the usable body set and driver. Names improve
    semantic roles but never make a low-confidence anatomy claim authoritative.
    `overrides` may provide driver, include, exclude, masses, roles and up_axis.
    """
    import math
    from mathutils import Vector
    if not obj or obj.type != 'ARMATURE':
        raise ValueError('Expected an armature')
    overrides = overrides or {}
    allowed = {'driver', 'include', 'exclude', 'masses', 'roles', 'up_axis'}
    if set(overrides) - allowed:
        raise ValueError('Unknown rig override keys')
    bones = list(obj.pose.bones)
    if not bones:
        raise ValueError('Armature has no bones')
    roots = [b for b in bones if b.parent is None]
    descendants = {}
    def count_descendants(b):
        if b.name not in descendants:
            descendants[b.name] = sum(1 + count_descendants(c) for c in b.children)
        return descendants[b.name]
    for b in bones: count_descendants(b)
    action = obj.animation_data.action if obj.animation_data else None
    animated = {b.name for b in bones if action and _bone_fcurves(obj, b.name)}
    deform = {b.name for b in bones if b.bone.use_deform}
    named_controls = {b.name for b in bones if any(w in _name_tokens(b.name) for w in _CONTROL_WORDS)}
    # A leaf sharing a joint with a longer sibling is commonly a bend/helper marker.
    helpers = set()
    for b in bones:
        if b.children or not b.parent: continue
        siblings = [s for s in b.parent.children if s != b]
        if any((b.bone.head_local-s.bone.head_local).length < max(b.bone.length,s.bone.length)*.1
               and s.children for s in siblings):
            helpers.add(b.name)
    include = set(overrides.get('include', deform))
    unknown = include - {b.name for b in bones}
    if unknown: raise ValueError(f'Unknown included bones: {sorted(unknown)}')
    excluded = named_controls | helpers | ({b.name for b in bones} - deform)
    excluded |= set(overrides.get('exclude', []))
    excluded -= set(overrides.get('include', []))
    usable = [b.name for b in bones if b.name in include and b.name not in excluded]
    if not usable: raise ValueError('No usable deformation bones after exclusions')
    driver = overrides.get('driver')
    if driver is None:
        root_named = [b for b in bones if any(w in _name_tokens(b.name) for w in _ROLE_WORDS['root'])]
        pool = root_named or roots or bones
        driver = max(pool, key=lambda b:(b.name in animated, count_descendants(b))).name
    if driver not in obj.pose.bones: raise ValueError('Driver override names a missing bone')
    roles = {role: [] for role in _ROLE_WORDS}
    confidence = {}
    for b in bones:
        tokens = _name_tokens(b.name)
        matches = [role for role, words in _ROLE_WORDS.items() if any(w in tokens or w in ''.join(tokens) for w in words)]
        if matches:
            role = matches[0]; roles[role].append(b.name); confidence[b.name] = .9
        elif b.name == driver:
            roles['root'].append(b.name); confidence[b.name] = .65
        else:
            confidence[b.name] = .25
    for role, names in overrides.get('roles', {}).items():
        if role not in roles or any(n not in obj.pose.bones for n in names):
            raise ValueError('Role overrides must use known roles and existing bones')
        for values in roles.values():
            values[:] = [n for n in values if n not in names]
        roles[role].extend(names)
        for n in names: confidence[n] = 1.
    lengths = {}
    for b in bones:
        # Some game rigs use display bones of uniform length. Child joint spacing
        # recovers a more useful segment scale without assuming an anatomical name.
        spans = [(c.bone.head_local-b.bone.head_local).length for c in b.children]
        lengths[b.name] = max([b.bone.length] + spans + [1e-6])
    median = sorted(lengths[n] for n in usable)[len(usable)//2]
    masses = {}
    for n in usable:
        b = obj.pose.bones[n]
        branch = 1 + min(4, descendants[n])**.35
        terminal = .55 if not b.children else 1.
        masses[n] = max(.05, min(8., (lengths[n]/median)**1.5 * branch * terminal))
    for n,w in overrides.get('masses', {}).items():
        if n not in obj.pose.bones or not isinstance(w,(int,float)) or not math.isfinite(w) or w<=0:
            raise ValueError('Mass overrides require existing bones and positive finite values')
        masses[n] = float(w)
    total=sum(masses.values());masses={n:w/total for n,w in masses.items()}
    up = Vector(overrides.get('up_axis',(0,0,1)))
    if up.length < 1e-8 or not all(math.isfinite(v) for v in up):
        raise ValueError('up_axis must be a finite nonzero vector')
    up.normalize()
    ambiguous = [n for n in usable if confidence[n] < .5]
    warnings=[]
    if ambiguous: warnings.append(f'{len(ambiguous)} bones have topology-only roles; review before choreography')
    if len(roots)!=1: warnings.append(f'Rig has {len(roots)} root bones; driver selection needs review')
    if action is None: warnings.append('No active action; profile is suitable for authoring but not refinement yet')
    return {'driver':driver,'masses':masses,'exclude':sorted(excluded),'limits':{},
            'roles':roles,'confidence':confidence,'usable_bones':usable,
            'animated_bones':sorted(animated),'helper_bones':sorted(helpers),
            'up_axis':list(up),'warnings':warnings,
            'object':obj.name,'action':action.name if action else None}


def detect_contact_windows(obj, rig_analysis, candidates=None, velocity_threshold=.03,
                           height_threshold=.04, min_duration_frames=2):
    """Suggest stationary low endpoint windows; returns suggestions, never assumptions.

    Height/velocity thresholds are fractions of rig extent and evaluated in world
    space along the configured up axis. Callers must review semantic suitability.
    """
    import math
    from mathutils import Vector
    action = _get_action(obj)
    start,end = action.frame_range
    fps=bpy.context.scene.render.fps/bpy.context.scene.render.fps_base
    up=Vector(rig_analysis['up_axis']).normalized()
    scale=max((obj.matrix_world @ b.bone.head_local - obj.matrix_world @ b.bone.tail_local).length
              for b in obj.pose.bones)
    scale=max(scale,1e-6)
    if candidates is None:
        semantic=sum((rig_analysis['roles'].get(r,[]) for r in ('foot','hand')),[])
        leaves=[n for n in rig_analysis['usable_bones'] if not obj.pose.bones[n].children]
        candidates=semantic or leaves
    if any(n not in obj.pose.bones for n in candidates): raise ValueError('Unknown contact candidate')
    old=bpy.context.scene.frame_current+bpy.context.scene.frame_subframe
    frames=sorted(set([float(start),float(end)]+[float(f) for f in range(math.ceil(start),math.floor(end)+1)]))
    samples={n:[] for n in candidates}
    try:
        for frame in frames:
            bpy.context.scene.frame_set(math.floor(frame),subframe=frame-math.floor(frame))
            rig=obj.evaluated_get(bpy.context.evaluated_depsgraph_get())
            for n in candidates:samples[n].append(rig.matrix_world @ rig.pose.bones[n].tail)
    finally:
        bpy.context.scene.frame_set(math.floor(old),subframe=old-math.floor(old))
    floor=min(p.dot(up) for values in samples.values() for p in values)
    output=[]
    for n,points in samples.items():
        active=[]
        for i,(f,p) in enumerate(zip(frames,points)):
            speed=0 if i==0 else (p-points[i-1]).length/((f-frames[i-1])/fps)
            active.append(p.dot(up)-floor <= height_threshold*scale and speed <= velocity_threshold*scale*fps)
        begin=None
        for i,state in enumerate(active+[False]):
            if state and begin is None:begin=i
            if not state and begin is not None:
                if frames[i-1]-frames[begin] >= min_duration_frames:
                    output.append({'bone':n,'start':frames[begin],'end':frames[i-1],
                                   'confidence':.75 if n in sum((rig_analysis['roles'].get(r,[]) for r in ('foot','hand')),[]) else .45})
                begin=None
    return output


def build_auto_refinement(obj, overrides=None, anchors=None, contacts=None, settings=None, name=None):
    """Create explicit profile/plan data for review; does not modify animation."""
    analysis=analyze_rig(obj,overrides)
    if not obj.animation_data or not obj.animation_data.action:
        raise ValueError('Author or assign an action before building a refinement plan')
    deform_helpers=[n for n in analysis['helper_bones'] if n in analysis['animated_bones']
                    and obj.pose.bones[n].bone.use_deform and not obj.pose.bones[n].constraints
                    and not any(w in _name_tokens(n) for w in _CONTROL_WORDS)]
    selected=[n for n in analysis['animated_bones'] if n in analysis['usable_bones'] and n!=analysis['driver']]
    selected.extend(n for n in deform_helpers if n not in selected and n!=analysis['driver'])
    contact_suggestions=detect_contact_windows(obj,analysis) if contacts is None else contacts
    profile={k:analysis[k] for k in ('driver','masses','exclude','limits')}
    profile['exclude']=[n for n in profile['exclude'] if n not in deform_helpers]
    plan={'name':name or obj.animation_data.action.name+'_Refined','bones':selected,
          'joint_helpers':deform_helpers,
          'contacts':contact_suggestions,'anchor_frames':list(anchors or []),'holds':[],
          'settings':dict(settings or {})}
    return {'analysis':analysis,'profile':profile,'plan':plan,
            'requires_contact_review':contacts is None,
            'warnings':analysis['warnings']
                + ([f'Review animated deforming joint helpers: {deform_helpers}'] if deform_helpers else [])
                + (['Detected contacts are suggestions; review before refinement'] if contacts is None else [])}


def refine_motion_auto(obj, contacts, overrides=None, anchors=None, settings=None, name=None):
    """Convenience wrapper after explicit contact review (`contacts` may be [])."""
    if contacts is None:
        raise ValueError('Review detected contacts, then pass the accepted list or []')
    prepared=build_auto_refinement(obj,overrides,anchors,contacts,settings,name)
    report=refine_motion(obj,prepared['plan'],prepared['profile'])
    report['rig_analysis']={k:prepared['analysis'][k] for k in
                            ('driver','roles','confidence','usable_bones','helper_bones','warnings')}
    report['warnings'].extend(w for w in prepared['warnings'] if w not in report['warnings'])
    return report


def author_motion(obj, keyposes, rig_analysis=None, name='Authored_Motion'):
    """Author rig-independent poses from object-space bone directions.

    Each keypose: {frame, directions:{bone:[x,y,z]}, optional
    local_quaternions:{bone:[w,x,y,z]}, translations:{bone:[x,y,z]}, marker,
    lateral_targets:{joint:{effector:bone,direction:[x,y,z],distance:units,
                            max_degrees:optional}}}.
    Directions avoid assumptions about a bone's local forward axis. Choreography
    and contacts still come from the AI/user. Lateral targets probe all local
    axes and solve a bounded effector displacement in object space; they do not
    assume that a named shoulder's Y axis points sideways.
    All referenced joints get start/end keys. At intermediate poses, only
    explicitly named joints get keys, so unmentioned joints interpolate across
    the interval. Add explicit breakdowns where the motion needs a timing or
    direction change; never infer extra movement merely from joint existence.
    """
    import math
    from mathutils import Vector, Quaternion, Matrix
    if not obj or obj.type != 'ARMATURE' or not keyposes:
        raise ValueError('Expected an armature and at least one keypose')
    rig_analysis = rig_analysis or analyze_rig(obj)
    known={b.name for b in obj.pose.bones}
    frames=[]; referenced=set()
    allowed={'frame','directions','local_quaternions','translations','lateral_targets','marker'}
    for pose in keyposes:
        if set(pose)-allowed or 'frame' not in pose or not math.isfinite(pose['frame']):
            raise ValueError('Invalid keypose fields/frame')
        frames.append(float(pose['frame']))
        for field in ('directions','local_quaternions','translations'):
            mapping=pose.get(field,{})
            if set(mapping)-known: raise ValueError(f'{field} contains unknown bones')
            referenced |= set(mapping)
        targets=pose.get('lateral_targets',{})
        if set(targets)-known: raise ValueError('lateral_targets contains unknown joints')
        for joint,target in targets.items():
            if set(target)-{'effector','direction','distance','max_degrees'} or not {'effector','direction','distance'}<=set(target):
                raise ValueError(f'{joint}: invalid lateral target fields')
            effector=target['effector']
            if effector not in known or joint not in [p.name for p in obj.pose.bones[effector].parent_recursive]:
                raise ValueError(f'{joint}: effector must descend from target joint')
            direction=target['direction']
            if len(direction)!=3 or any(not isinstance(v,(int,float)) or not math.isfinite(v) for v in direction) or Vector(direction).length<1e-8:
                raise ValueError(f'{joint}: lateral direction must be finite and nonzero')
            distance=target['distance'];maximum=target.get('max_degrees',30.)
            if any(not isinstance(v,(int,float)) or not math.isfinite(v) for v in (distance,maximum)) or not 0<maximum<=45:
                raise ValueError(f'{joint}: invalid lateral distance or angle bound')
            referenced.add(joint)
        for n,v in pose.get('directions',{}).items():
            vec=Vector(v)
            if len(v)!=3 or vec.length<1e-8 or not all(math.isfinite(x) for x in vec):
                raise ValueError(f'{n}: direction must be a finite nonzero vector')
        for n,v in pose.get('local_quaternions',{}).items():
            if len(v)!=4 or not all(math.isfinite(x) for x in v) or Quaternion(v).magnitude<1e-8:
                raise ValueError(f'{n}: invalid local quaternion')
        for n,v in pose.get('translations',{}).items():
            if len(v)!=3 or not all(math.isfinite(x) for x in v):
                raise ValueError(f'{n}: invalid translation')
    if len(set(frames))!=len(frames): raise ValueError('Keypose frames must be unique')
    if not referenced: raise ValueError('Keyposes do not reference any bones')
    for n in referenced:
        if obj.pose.bones[n].constraints:
            raise ValueError(f'{n}: bake constraints before direction authoring')
    original = obj.animation_data.action if obj.animation_data else None
    original_slot = getattr(obj.animation_data,'action_slot',None) if obj.animation_data else None
    action=None
    try:
        if not obj.animation_data: obj.animation_data_create()
        action=bpy.data.actions.new(name);obj.animation_data.action=action
        ordered=sorted(referenced,key=lambda n:len(obj.pose.bones[n].parent_recursive))
        previous={};lateral_results=[]
        sorted_poses=sorted(keyposes,key=lambda p:p['frame'])
        for pose_index,pose in enumerate(sorted_poses):
            frame=float(pose['frame'])
            bpy.context.scene.frame_set(math.floor(frame),subframe=frame-math.floor(frame))
            # Poses are independent: reset authored bones, then solve parent-first.
            for n in ordered: obj.pose.bones[n].matrix_basis=Matrix.Identity(4)
            bpy.context.view_layer.update()
            for n in ordered:
                pb=obj.pose.bones[n]
                if n in pose.get('local_quaternions',{}):
                    authored_q=Quaternion(pose['local_quaternions'][n]).normalized()
                    if pb.rotation_mode=='QUATERNION':pb.rotation_quaternion=authored_q
                    elif pb.rotation_mode=='AXIS_ANGLE':
                        axis,angle=authored_q.to_axis_angle();pb.rotation_axis_angle=(angle,*axis)
                    else:pb.rotation_euler=authored_q.to_euler(pb.rotation_mode,pb.rotation_euler)
                if n in pose.get('translations',{}): pb.location=Vector(pose['translations'][n])
                if n in pose.get('directions',{}):
                    current=pb.tail-pb.head;target=Vector(pose['directions'][n]).normalized()
                    if current.length<1e-8: raise ValueError(f'{n}: zero evaluated bone length')
                    object_q=current.normalized().rotation_difference(target) @ pb.matrix.to_quaternion()
                    pb.matrix=Matrix.Translation(pb.head) @ object_q.to_matrix().to_4x4()
                bpy.context.view_layer.update()
            for n,target in pose.get('lateral_targets',{}).items():
                pb=obj.pose.bones[n];effector=obj.pose.bones[target['effector']]
                direction=Vector(target['direction']).normalized()
                distance=float(target['distance'])
                limit=math.radians(target.get('max_degrees',30.))
                if pb.rotation_mode=='QUATERNION':base_q=pb.rotation_quaternion.copy()
                elif pb.rotation_mode=='AXIS_ANGLE':
                    turn,x,y,z=pb.rotation_axis_angle;base_q=Quaternion((x,y,z),turn)
                else:base_q=pb.rotation_euler.to_quaternion()
                base_q.normalize()
                origin=effector.tail.copy()
                def displacement(axis,angle):
                    q=(base_q @ Quaternion(axis,angle)).normalized()
                    if pb.rotation_mode=='QUATERNION':pb.rotation_quaternion=q
                    elif pb.rotation_mode=='AXIS_ANGLE':
                        ax,turn=q.to_axis_angle();pb.rotation_axis_angle=(turn,*ax)
                    else:pb.rotation_euler=q.to_euler(pb.rotation_mode,pb.rotation_euler)
                    bpy.context.view_layer.update()
                    return (effector.tail-origin).dot(direction)
                best=None
                for axis in (Vector((1,0,0)),Vector((0,1,0)),Vector((0,0,1))):
                    for sign in (-1.,1.):
                        reach=displacement(axis,sign*limit)
                        if abs(reach)<1e-6 or reach*distance<=0:continue
                        lo,hi=0.,limit
                        for _ in range(12):
                            mid=(lo+hi)*.5
                            if abs(displacement(axis,sign*mid))<abs(distance):lo=mid
                            else:hi=mid
                        angle=sign*(lo+hi)*.5 if abs(reach)>=abs(distance) else sign*limit
                        actual=displacement(axis,angle)
                        candidate=(abs(actual-distance),abs(angle),axis.copy(),angle,actual)
                        if best is None or candidate[:2]<best[:2]:best=candidate
                if best is None:
                    displacement(Vector((1,0,0)),0.)
                    if abs(distance)>1e-7:raise ValueError(f'{n}: lateral target is unreachable')
                    actual=0.;chosen=None
                else:
                    _,_,axis,angle,actual=best
                    displacement(axis,angle)
                    chosen=list(axis)
                lateral_results.append({'frame':frame,'joint':n,'effector':target['effector'],
                                        'requested':distance,'achieved':actual,'local_axis':chosen})
            explicit=set().union(*(set(pose.get(field,{})) for field in
                                   ('directions','local_quaternions','translations','lateral_targets')))
            keyed=set(ordered) if pose_index in (0,len(sorted_poses)-1) else explicit
            for n in ordered:
                if n not in keyed:continue
                pb=obj.pose.bones[n];prop=_rotation_prop(obj,n)
                if prop=='rotation_quaternion':
                    q=pb.rotation_quaternion.normalized()
                    if n in previous and previous[n].dot(q)<0:q.negate();pb.rotation_quaternion=q
                    previous[n]=q.copy()
                pb.keyframe_insert(data_path=prop,frame=frame,group=n)
                if n in pose.get('translations',{}):pb.keyframe_insert(data_path='location',frame=frame,group=n)
            if pose.get('marker'): action.pose_markers.new(str(pose['marker'])).frame=int(round(frame))
        for fc in _iter_fcurves(action,obj):
            for kp in fc.keyframe_points:
                kp.interpolation='BEZIER';kp.handle_left_type=kp.handle_right_type='AUTO_CLAMPED'
        action.use_fake_user=True
        low=[n for n in referenced if rig_analysis.get('confidence',{}).get(n,0)<.5]
        return {'action':action.name,'frames':sorted(frames),'bones':ordered,
                'lateral_results':lateral_results,
                'warnings':([f'Review semantic meaning of low-confidence bones: {low}'] if low else [])}
    except Exception:
        if original is not None:
            obj.animation_data.action=original
            if original_slot is not None:obj.animation_data.action_slot=original.slots[original_slot.identifier]
        if action is not None:bpy.data.actions.remove(action)
        raise


def evaluated_center_of_mass(obj, masses):
    """Use the current evaluated frame. Missing/invalid masses are errors."""
    import math
    from mathutils import Vector
    if not masses or any(n not in obj.pose.bones or not math.isfinite(w) or w <= 0
                         for n, w in masses.items()):
        raise ValueError('Masses must name existing bones with positive finite weights')
    rig = obj.evaluated_get(bpy.context.evaluated_depsgraph_get())
    return sum((rig.matrix_world @ ((rig.pose.bones[n].head + rig.pose.bones[n].tail)*.5)*w
                for n, w in masses.items()), Vector()) / sum(masses.values())


def refine_motion(obj, plan, profile):
    """General, transactional animation refinement. See AI_ANIMATION_API.md.

    A plan specifies bones, contacts, anchor_frames, holds and settings. Profile
    specifies masses, motion driver, exclusions and optional local XYZ limits.
    Contact ancestors retain their original curves, including subframe motion.
    Errors restore the original action and slot; successful output is a copy.
    """
    import math
    from mathutils import Vector, Quaternion
    allowed = {'bones', 'joint_helpers', 'contacts', 'anchor_frames', 'holds', 'settings', 'name'}
    if set(plan) - allowed or set(profile) - {'masses', 'driver', 'exclude', 'limits'}:
        raise ValueError('Unknown plan/profile keys')
    source = _get_action(obj)
    inspect_motion_rig(obj)
    if 'contacts' not in plan:
        raise ValueError('Specify contacts explicitly; use [] for no contacts')
    settings = dict(strength=.40, frequency_hz=5., damping=.95,
                    centrifugal_gain=.55, translation_gain=.20,
                    helper_follow_gain=.25,
                    max_offset_degrees=8., sample_step=.5,
                    contact_tolerance=1e-4, max_step_degrees=45.)
    if set(plan.get('settings', {})) - set(settings):
        raise ValueError('Unknown settings')
    settings.update(plan.get('settings', {}))
    if any(not isinstance(v, (int, float)) or not math.isfinite(v) for v in settings.values()):
        raise ValueError('Settings must be finite numbers')
    if not (0 <= settings['strength'] <= 1 and 0 <= settings['centrifugal_gain'] <= 2
            and 0 <= settings['translation_gain'] <= 2
            and 0 <= settings['helper_follow_gain'] <= 1
            and .5 <= settings['frequency_hz'] <= 20 and .5 <= settings['damping'] <= 2
            and 0 < settings['max_offset_degrees'] <= 30 and .1 <= settings['sample_step'] <= 1
            and settings['contact_tolerance'] > 0 and 0 < settings['max_step_degrees'] <= 180):
        raise ValueError('Settings outside supported bounds')
    masses = profile['masses']
    evaluated_center_of_mass(obj, masses)
    driver = profile['driver']
    names = list(dict.fromkeys(plan.get('bones', [])))
    excluded = set(profile.get('exclude', [])) | {driver}
    if not names or any(n not in obj.pose.bones for n in names + list(excluded)):
        raise ValueError('Select existing bones and an existing driver')
    start, end = map(float, source.frame_range)
    if end <= start:
        raise ValueError('Action must have a nonzero duration')
    contacts = plan['contacts']
    holds = plan.get('holds', [])
    anchors = sorted(set([start, end] + list(plan.get('anchor_frames', []))))
    if any(not math.isfinite(f) or not start <= f <= end for f in anchors):
        raise ValueError('Anchors must lie inside the action')
    for interval in holds:
        if len(interval) != 2 or not start <= interval[0] < interval[1] <= end:
            raise ValueError('Invalid hold interval')
    anchors = sorted(set(anchors + [f for interval in holds for f in interval]))
    protected = set(excluded)
    for c in contacts:
        if set(c) != {'bone', 'start', 'end'} or c['bone'] not in obj.pose.bones or not start <= c['start'] <= c['end'] <= end:
            raise ValueError('Invalid contact: expected bone, start, end')
        b = obj.pose.bones[c['bone']]
        while b:
            protected.add(b.name)
            b = b.parent
    candidates = [n for n in names if n not in protected]
    joint_helpers=set(plan.get('joint_helpers',[]))
    if joint_helpers-set(candidates) or any(not obj.pose.bones[n].parent
             or obj.pose.bones[n].children for n in joint_helpers):
        raise ValueError('Joint helpers must be selected leaf bones with parents')
    limits = profile.get('limits', {})
    for n, bounds in limits.items():
        if n not in obj.pose.bones or len(bounds) != 3 or any(len(v) != 2 or not all(math.isfinite(x) for x in v) or v[0] > v[1] for v in bounds):
            raise ValueError('Limits require three finite [min,max] local XYZ ranges')
    for n in candidates:
        pb = obj.pose.bones[n]
        if pb.constraints or pb.rotation_mode == 'AXIS_ANGLE':
            raise ValueError(f'{n}: bake constraints / convert axis-angle before refinement')
        prop = _rotation_prop(obj, n)
        if not _bone_fcurves(obj, n, prop):
            raise ValueError(f'{n}: author rotation poses first')
    if obj.animation_data.drivers or any(not t.mute for t in obj.animation_data.nla_tracks):
        raise ValueError('Bake drivers/NLA into an isolated action first')
    action_layers = list(getattr(source, 'layers', []))
    if len(action_layers) > 1:
        raise ValueError('Flatten layered blending before refinement')
    if any(fc.modifiers for fc in _iter_fcurves(source, obj)):
        raise ValueError('Bake F-curve modifiers before refinement')
    step = settings['sample_step']
    count = math.ceil((end-start)/step)
    if count > 20000:
        raise ValueError('Action too long; split into clips (maximum 20000 samples)')
    frames = sorted(set([start+i*(end-start)/count for i in range(count+1)] + anchors
                        + [f for h in holds for f in h]
                        + [c[k] for c in contacts for k in ('start','end')]))
    scene = bpy.context.scene
    old_frame = scene.frame_current + scene.frame_subframe
    old_slot = getattr(obj.animation_data, 'action_slot', None)
    fps = scene.render.fps/scene.render.fps_base
    output = None

    def frame_set(f):
        scene.frame_set(math.floor(f), subframe=f-math.floor(f))

    def log_rotation(q):
        q = q.normalized()
        if q.w < 0: q.negate()
        axis, angle = q.to_axis_angle()
        return axis*angle

    def assign(action, slot):
        obj.animation_data.action = action
        if slot is not None:
            obj.animation_data.action_slot = action.slots[slot.identifier]

    def contact_positions():
        rig = obj.evaluated_get(bpy.context.evaluated_depsgraph_get())
        return {c['bone']: rig.matrix_world @ rig.pose.bones[c['bone']].tail for c in contacts}

    try:
        samples = []
        for f in frames:
            frame_set(f)
            rig = obj.evaluated_get(bpy.context.evaluated_depsgraph_get())
            com = evaluated_center_of_mass(obj, masses)
            poses = {}
            for n in candidates:
                pb = obj.pose.bones[n]
                q = pb.rotation_quaternion.copy() if pb.rotation_mode == 'QUATERNION' else pb.rotation_euler.to_quaternion()
                if not all(math.isfinite(v) for v in q) or q.magnitude < 1e-8:
                    raise ValueError(f'{n}: invalid source rotation at {f}')
                if samples and samples[-1]['poses'][n]['q'].dot(q) < 0: q.negate()
                evaluated = rig.pose.bones[n]
                head = rig.matrix_world @ evaluated.head
                mid = rig.matrix_world @ ((evaluated.head+evaluated.tail)*.5)
                poses[n] = dict(q=q.normalized(), radius=mid-com, lever=mid-head,
                                world_q=(rig.matrix_world @ evaluated.matrix).to_quaternion(),
                                parent_world=((rig.matrix_world @ rig.pose.bones[pb.parent.name].matrix).to_quaternion()
                                              if n in joint_helpers else None))
            samples.append(dict(frame=f, com=com, poses=poses, contacts=contact_positions(),
                                driver=(rig.matrix_world @ rig.pose.bones[driver].matrix).to_quaternion()))
        angular = [Vector()]
        com_velocity = [Vector()]
        for a, b in zip(samples, samples[1:]):
            dt=(b['frame']-a['frame'])/fps
            angular.append(log_rotation(b['driver'] @ a['driver'].conjugated()) / dt)
            com_velocity.append((b['com']-a['com'])/dt)
        if len(angular)>1:
            angular[0] = angular[1].copy()
            com_velocity[0] = com_velocity[1].copy()
        com_acceleration = [Vector()]
        for i in range(1,len(samples)):
            dt=(samples[i]['frame']-samples[i-1]['frame'])/fps
            com_acceleration.append((com_velocity[i]-com_velocity[i-1])/dt)
        scale = max(sum(p['radius'].length for s in samples for p in s['poses'].values()) /
                    max(1, len(samples)*len(candidates)), 1e-6)
        results = {n: [] for n in candidates}
        warnings = []
        for n in candidates:
            q = samples[0]['poses'][n]['q'].copy()
            velocity = Vector()
            previous_euler = None
            for i, s in enumerate(samples):
                p = s['poses'][n]
                target = p['q']
                if i:
                    dt = (s['frame']-samples[i-1]['frame'])/fps
                    omega = angular[i]
                    alpha = (angular[i]-angular[i-1])/dt
                    radial = -omega.cross(omega.cross(p['radius']))-alpha.cross(p['radius'])
                    inertial = (radial*settings['centrifugal_gain']
                                - com_acceleration[i]*settings['translation_gain'])
                    torque = p['world_q'].inverted() @ (p['lever'].cross(inertial)/(scale*scale))
                    if torque.length > 100: torque = torque.normalized()*100
                    gain = max(.65, min(1.8, (scale*scale*1.25)/(p['radius'].length_squared+scale*scale*.25)))
                    frequency = 2*math.pi*settings['frequency_hz']*math.sqrt(gain)
                    substeps = max(4, math.ceil(dt*frequency/.15))
                    h = dt/substeps
                    for j in range(substeps):
                        goal = samples[i-1]['poses'][n]['q'].slerp(target,(j+1)/substeps)
                        acceleration = log_rotation(q.conjugated() @ goal)*frequency**2
                        acceleration += torque-velocity*(2*settings['damping']*frequency)
                        velocity += acceleration*h
                        turn = velocity.length*h
                        if turn > 1e-10: q = (q @ Quaternion(velocity.normalized(),turn)).normalized()
                distance = min(abs(s['frame']-a) for a in anchors)/fps
                fade = min(1.,distance/.08)
                fade = fade*fade*(3-2*fade)
                if any(a <= s['frame'] <= b for a,b in holds):
                    fade = 0.; q = target.copy(); velocity = Vector()
                delta = log_rotation(target.conjugated() @ q).length
                blend = settings['strength']*fade*min(1.,math.radians(settings['max_offset_degrees'])/max(delta,1e-9))
                value = target.slerp(q,blend).normalized()
                if n in joint_helpers and i and fade:
                    parent_turn=log_rotation(p['parent_world'] @
                                             samples[i-1]['poses'][n]['parent_world'].conjugated())
                    local_turn=p['world_q'].inverted() @ parent_turn
                    amount=min(local_turn.length*settings['helper_follow_gain']*fade,
                               math.radians(min(3.,settings['max_offset_degrees'])))
                    if amount>1e-10:
                        value=(value @ Quaternion(local_turn.normalized(),-amount)).normalized()
                if n in limits:
                    e = value.to_euler('XYZ', previous_euler) if previous_euler else value.to_euler('XYZ')
                    if any(not lo-1e-5 <= x <= hi+1e-5 for x,(lo,hi) in zip(e,limits[n])):
                        raise ValueError(f'{n}: joint limit violation at {s["frame"]}; revise poses or lower strength')
                    previous_euler = e
                results[n].append(value)
        output = source.copy()
        output.name = plan.get('name') or source.name+'_Refined'
        assign(output,old_slot)
        for n in candidates:
            pb = obj.pose.bones[n]
            prop = _rotation_prop(obj,n)
            for fc in _bone_fcurves(obj,n,prop): fc.keyframe_points.clear()
            previous_euler = None
            for f,q in zip(frames,results[n]):
                if pb.rotation_mode == 'QUATERNION': pb.rotation_quaternion=q
                else:
                    e=q.to_euler(pb.rotation_mode,previous_euler) if previous_euler else q.to_euler(pb.rotation_mode)
                    pb.rotation_euler=e; previous_euler=e
                pb.keyframe_insert(data_path=prop,frame=f,group=n)
            for fc in _bone_fcurves(obj,n,prop):
                for kp in fc.keyframe_points: kp.interpolation='LINEAR'
        max_contact_error=0.; max_step=0.
        active_counts=[];active_by_bone={n:0 for n in candidates}
        final_com=[]
        for i,s in enumerate(samples):
            frame_set(s['frame'])
            for c in contacts:
                if c['start'] <= s['frame'] <= c['end']:
                    error=(contact_positions()[c['bone']]-s['contacts'][c['bone']]).length
                    max_contact_error=max(max_contact_error,error)
            final_com.append([s['frame'],list(evaluated_center_of_mass(obj,masses))])
            if i:
                for n in candidates:
                    delta=math.degrees(log_rotation(results[n][i-1].conjugated() @ results[n][i]).length)
                    max_step=max(max_step,delta)
                    if delta>.1:active_by_bone[n]+=1
                active_counts.append(sum(math.degrees(log_rotation(
                    results[n][i-1].conjugated() @ results[n][i]).length)>.1 for n in candidates))
        if max_contact_error > settings['contact_tolerance'] or max_step > settings['max_step_degrees']:
            raise ValueError(f'Validation failed: contact error={max_contact_error}, sample rotation={max_step}')
        if not candidates: warnings.append('All selected bones protected; output is an unchanged copy')
        if not limits: warnings.append('No anatomical limits supplied; joint feasibility is not certified')
        source.use_fake_user=True
        output.use_fake_user=True
        return dict(action=output.name,source=source.name,modified_bones=candidates,
                    protected_bones=sorted(protected),warnings=warnings,settings=settings,
                    validation=dict(contact_error=max_contact_error,max_sample_rotation_degrees=max_step),
                    joint_coverage=dict(selected=len(candidates),
                        joint_helpers=sorted(joint_helpers),
                        mean_active_per_sample=sum(active_counts)/len(active_counts) if active_counts else 0.,
                        least_active_per_sample=min(active_counts) if active_counts else 0,
                        active_samples_by_bone=active_by_bone),
                    com_trajectory=final_com,rules_version=MOTION_RULES['version'])
    except Exception:
        assign(source,old_slot)
        if output is not None: bpy.data.actions.remove(output)
        raise
    finally:
        frame_set(old_frame)


if __name__ == "__main__":
    register()
