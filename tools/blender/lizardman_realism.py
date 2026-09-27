"""
Lizardman realism pass for Blender (Blender 5.1 / 4.x).

Runs on top of `lizardman_anim.py`: it rebuilds the rig from the mod's own files
and then improves every clip, so it is safe to re-run (each run starts from the
committed animation, never from a previously modified one).

Usage, inside Blender:
    exec(compile(open(<this file>).read(), <this file>, 'exec'))
    run()                       # rebuild + improve every clip
    LA.export_all(project)      # write lizardman.animation.json

Why these passes
----------------
An axis audit of the committed animation showed pitch (forward/back swing) and
yaw (spine twist, tail sway) are well covered, but **roll - the lateral weight
shift - is almost dead**: 1.4 deg peak in `walk`, 1.8 in `run`, 1.6 in `idle`,
and exactly 0 on Chest/Head in every locomotion clip. A biped that never rolls
its pelvis reads as weightless, and a 4 s idle that never shifts weight reads as
a statue. So the passes add:

  * gait weight shift : pelvis roll dropping toward the swing leg, with the
    torso and head counter-rolling to stay level (walk, run)
  * tail bob          : vertical tail follow-through driven by the body bob,
    lagging progressively down Tail1 -> Tail3 (walk, run)
  * idle life         : breathing, a slow weight shift, a held head look, tail
    drift and toe flex, all periodic over the clip so the loop still closes
  * impact overlap    : one-shot clips get progressive drag on the distal bones
    (Head, Jaw, Hands, Tail2/3) so a strike follows through instead of the whole
    body arriving on the same frame

Axis conventions (these two are NOT the same - this is the easy bug)
-------------------------------------------------------------------
`lizardman_anim.rot_to_blender(fx, fy, fz) = (fx, fz, -fy)` in radians, so a
Blender fcurve array index means:
    index 0 = fx  -> pitch  (a hanging limb swings back / forward)
    index 1 = fz  -> roll   (tilts toward -X, i.e. the character's right)
    index 2 = -fy -> yaw    (turns counter-clockwise seen from above)
The JSON file axis order is [pitch, yaw, roll], so JSON 1/2 and Blender 2/1 are
swapped. Everything below uses the Blender indices via PITCH/ROLL/YAW.
"""
import math
import os
import shutil

import bpy

import lizardman_anim as LA

FPS = 24.0
PITCH, ROLL, YAW = 0, 1, 2

# Every rotation fcurve stores RADIANS (lizardman_anim.rot_to_blender converts
# degrees on import). Amplitudes in this module are written in degrees because
# that is how the rig is described, so they must be converted before being added
# to a curve - adding a raw degree number swings a bone ~57x too far.


def rad(degrees):
    return math.radians(degrees)


# One-shot clips (everything that is not a loop): the distal drag pass applies.
LOOPING = ("walk", "run", "idle")

# frames of lag to introduce on distal bones, by depth in the chain. One frame at
# 24 fps is 4-14% of these clips, and a full snap is fast, so the jaw is kept to a
# single frame - a longer shift starts to swallow the strike itself.
DRAG = {"Head": 1.0, "Jaw": 1.0, "Hand_L": 1.0, "Hand_R": 1.0, "Tail2": 1.0, "Tail3": 2.0}


# ------------------------------------------------------------------ curve helpers
def curves(action):
    return LA.action_curves(action)


def data_path(bone, path):
    return 'pose.bones["%s"].%s' % (bone, path)


def find_curve(action, bone, path, index):
    dp = data_path(bone, path)
    for fc in curves(action):
        if fc.data_path == dp and fc.array_index == index:
            return fc
    return None


def ensure_curve(action, bone, path, index):
    fc = find_curve(action, bone, path, index)
    if fc is None:
        fc = LA.new_fcurve(curves(action), bone, path, index)
    return fc


def key_times(action, bone, path):
    """Sorted union of every key time on one bone/path (all three axes)."""
    times = set()
    dp = data_path(bone, path)
    for fc in curves(action):
        if fc.data_path == dp:
            times.update(round(kp.co[0], 4) for kp in fc.keyframe_points)
    return sorted(times)


def rewrite(fc, samples):
    """Replace a curve's keys with (frame, value) samples, keeping easing on reused times."""
    style = {round(kp.co[0], 4): (kp.interpolation, kp.easing) for kp in fc.keyframe_points}
    for i in range(len(fc.keyframe_points) - 1, -1, -1):
        fc.keyframe_points.remove(fc.keyframe_points[i])
    for frame, value in samples:
        kp = fc.keyframe_points.insert(frame, value, options={"FAST"})
        kp.interpolation, kp.easing = style.get(round(frame, 4), ("LINEAR", "AUTO"))
    fc.update()


def add_on_curve(action, bone, path, index, times, fn):
    """value(t) += fn(t), sampled on `times`, creating the curve if the axis was unused."""
    fc = ensure_curve(action, bone, path, index)
    base = [(t, fc.evaluate(t)) for t in times]
    rewrite(fc, [(t, v + fn(t)) for t, v in base])
    return fc


def add_values(action, bone, path, index, times, values):
    """value(t) += v from a per-time list, creating the curve if the axis was unused."""
    fc = ensure_curve(action, bone, path, index)
    base = [fc.evaluate(t) for t in times]
    rewrite(fc, [(t, b + v) for t, b, v in zip(times, base, values)])
    return fc


def times_or(action, bone, path, fallback_bone="Root"):
    return key_times(action, bone, path) or key_times(action, fallback_bone, path)


# ------------------------------------------------------------------ motion reading
def swing_signal(action, times):
    """(right foot height - left foot height) normalised to [-1, 1] at `times`.

    Measured from the evaluated pose instead of derived from a joint angle, so
    the phase is exactly right regardless of how the clip was timed.
    Positive means the RIGHT foot is higher, i.e. the right leg is swinging.
    """
    arm = bpy.data.objects[LA.ARMATURE]
    LA.assign_action(arm, action)
    scn = bpy.context.scene
    raw = []
    for t in times:
        scn.frame_set(int(t), subframe=float(t) - int(t))
        ev = arm.evaluated_get(bpy.context.evaluated_depsgraph_get())
        zl = (ev.matrix_world @ ev.pose.bones["Foot_L"].head).z
        zr = (ev.matrix_world @ ev.pose.bones["Foot_R"].head).z
        raw.append(zr - zl)
    peak = max((abs(v) for v in raw), default=0.0) or 1.0
    return [v / peak for v in raw]


def vertical_signal(action, times, lag=0.0, wrap=None):
    """Body height (Root vertical translation), mean-centred and normalised.

    For a looping clip pass `wrap` = the cycle length: sampling before frame 0
    wraps to the end of the cycle, which is what keeps the added motion periodic
    instead of clamping flat and opening a seam at the loop point.
    """
    fc = find_curve(action, "Root", "location", 2)  # Blender Z = model up
    raw = []
    for t in times:
        s = t - lag
        if wrap and s < 0.0:
            s += wrap
        raw.append(fc.evaluate(s))
    mean = sum(raw) / len(raw)
    peak = max((abs(v - mean) for v in raw), default=0.0) or 1.0
    return [(v - mean) / peak for v in raw]


def dominant_extremum_frame(action, bone):
    """Frame of the largest deviation from the bone's first pose, over its busiest axis."""
    best = (0.0, -1.0)
    for index in (PITCH, ROLL, YAW):
        fc = find_curve(action, bone, "rotation_euler", index)
        if fc is None or not fc.keyframe_points:
            continue
        first = fc.evaluate(fc.keyframe_points[0].co[0])
        for kp in fc.keyframe_points:
            deviation = abs(kp.co[1] - first)
            if deviation > best[1]:
                best = (kp.co[0], deviation)
    return best[0], best[1]


# ------------------------------------------------------------------------ passes
def pass_gait_weight(action, hip, torso, chest, head):
    """Pelvis roll dropping toward the swinging foot, torso and head counter-rolling.

    +roll drops the character's LEFT side (it faces -Y, so +X is its left), so a
    swinging right leg takes a negative pelvis roll. The torso and head roll the
    other way by a smaller amount, which keeps the head level and reads as weight
    being carried rather than as the body tipping over.
    """
    for bone, amp, sign in (("Root", hip, -1.0), ("Torso", torso, 1.0),
                            ("Chest", chest, 1.0), ("Head", head, 1.0)):
        times = times_or(action, bone, "rotation_euler")
        signal = swing_signal(action, times)
        add_values(action, bone, "rotation_euler", ROLL, times,
                   [sign * rad(amp) * s for s in signal])


def pass_tail_bob(action, amps, lags=(0.0, 0.8, 1.6)):
    """Vertical tail follow-through, driven by the body's own vertical bob.

    This rig already bobs twice per gait cycle; the tail tip is the heaviest part
    of the chain, so each segment answers the body's lowest point a little later
    than the one before it. The tail rises as the body falls, hence the inversion.
    """
    wrap = float(action.frame_end)
    for bone, amp, lag in zip(("Tail1", "Tail2", "Tail3"), amps, lags):
        times = times_or(action, bone, "rotation_euler")
        signal = vertical_signal(action, times, lag=lag, wrap=wrap)
        add_values(action, bone, "rotation_euler", PITCH, times,
                   [-rad(amp) * v for v in signal])


def pass_idle_life(action):
    """Breathing, weight shift, a held head look, tail drift and toe flex.

    Every term completes a whole number of cycles across the clip so the loop
    still closes seamlessly.
    """
    period = float(action.frame_end)          # 96 frames = 4 s
    breath = period / 2.0                     # two breaths per loop
    tau = 2.0 * math.pi

    def breathe(t, amp, phase=0.0):
        return rad(amp) * math.sin(tau * (t / breath) + phase)

    def shift(t, amp, phase=0.0):
        return rad(amp) * math.sin(tau * (t / period) + phase)

    # --- breathing: chest lift, a slow head nod, the jaw riding along
    for bone, index, amp, phase in (
        ("Chest", PITCH, 2.4, -math.pi / 2),
        ("Torso", PITCH, 0.9, -math.pi / 2),
        ("Head", PITCH, -1.3, -math.pi / 2 + 0.5),
        ("Jaw", PITCH, 1.9, -math.pi / 2),
        ("Arm_L", PITCH, 0.9, -math.pi / 2),
        ("Arm_R", PITCH, 0.9, -math.pi / 2),
    ):
        times = times_or(action, bone, "rotation_euler")
        add_on_curve(action, bone, "rotation_euler", index, times,
                     lambda t, a=amp, p=phase: breathe(t, a, p))

    # a small vertical rise on the inhale, so the breath is visible in silhouette
    # (location fcurves are in blocks, not radians, so these are not converted)
    times = times_or(action, "Root", "location")
    add_on_curve(action, "Root", "location", 2, times,
                 lambda t: 0.020 * math.sin(tau * (t / breath) - math.pi / 2))

    # --- weight shift: the pelvis rolls and slides, the torso and head stay level
    for bone, index, amp in (
        ("Root", ROLL, 2.3),
        ("Torso", ROLL, -1.3),
        ("Chest", ROLL, -0.9),
        ("Head", ROLL, -1.4),
    ):
        times = times_or(action, bone, "rotation_euler")
        add_on_curve(action, bone, "rotation_euler", index, times,
                     lambda t, a=amp: shift(t, a))
    times = times_or(action, "Root", "location")
    add_on_curve(action, "Root", "location", 0, times,
                 lambda t: 0.090 * math.sin(tau * (t / period)))

    # --- head look: a slow scan that holds, with the neck leading and the head settling
    times = times_or(action, "Head", "rotation_euler")
    add_on_curve(action, "Head", "rotation_euler", YAW, times,
                 lambda t: rad(6.4) * math.sin(tau * (t / period) + 0.55))
    add_on_curve(action, "Chest", "rotation_euler", YAW, times,
                 lambda t: rad(1.6) * math.sin(tau * (t / period) + 0.55 - 0.35))

    # --- tail: vertical drift layered on the existing side sway, lagging outward
    for bone, amp, lag in (("Tail1", 1.7, 0.0), ("Tail2", 2.5, 0.45), ("Tail3", 3.3, 0.9)):
        times = times_or(action, bone, "rotation_euler")
        add_on_curve(action, bone, "rotation_euler", PITCH, times,
                     lambda t, a=amp, l=lag: shift(t, a, -l))

    # --- toes: a slow grip flex, staggered outward so the feet are not rigid
    for bone, phase in (("Toe_L_in", 0.0), ("Toe_L_mid", 0.25), ("Toe_L_out", 0.5),
                        ("Toe_R_in", 0.0), ("Toe_R_mid", 0.25), ("Toe_R_out", 0.5)):
        times = times_or(action, bone, "rotation_euler", fallback_bone="Foot_L")
        add_on_curve(action, bone, "rotation_euler", PITCH, times,
                     lambda t, p=phase: shift(t, 1.1, p))


def pass_drag(action, lag_by_bone=DRAG):
    """Delay distal bones so the body leads and the extremities follow through."""
    bones = {b.name for b in bpy.data.objects[LA.ARMATURE].pose.bones}
    used = []
    for bone, lag in lag_by_bone.items():
        if bone not in bones:
            continue
        moved = False
        for index in (PITCH, ROLL, YAW):
            fc = find_curve(action, bone, "rotation_euler", index)
            if fc is None or len(fc.keyframe_points) < 2:
                continue
            times = [kp.co[0] for kp in fc.keyframe_points]
            first_time = times[0]
            rewrite(fc, [(t, fc.evaluate(max(first_time, t - lag))) for t in times])
            moved = True
        if moved:
            used.append(bone)
    return used


def tail_needs_drag(action, per_segment=0.5):
    """True when the tail chain does not already trail segment by segment.

    The committed clips already stagger Tail1 -> Tail2 -> Tail3 by ~0.7 frames,
    so this normally reports False and the tail is deliberately left untouched.
    Note the amplitude floor is in radians: 1.5 deg of tail motion is too little
    to judge the phase from, and guessing there would only add noise.
    """
    t1, a1 = dominant_extremum_frame(action, "Tail1")
    t2, a2 = dominant_extremum_frame(action, "Tail2")
    t3, a3 = dominant_extremum_frame(action, "Tail3")
    if min(a1, a2, a3) < rad(1.5):
        return False
    return (t2 - t1) < per_segment or (t3 - t2) < per_segment


def snapshot():
    """{(action, data_path, axis): largest |radians|} for every rotation curve."""
    out = {}
    for action in bpy.data.actions:
        if not action.name.startswith(LA.PREFIX):
            continue
        for fc in LA.action_curves(action):
            if not fc.data_path.endswith("rotation_euler"):
                continue
            vals = [abs(kp.co[1]) for kp in fc.keyframe_points]
            out[(action.name, fc.data_path, fc.array_index)] = max(vals) if vals else 0.0
    return out


def sanity_check(baseline, tolerance=1.0):
    """Flag curves that grew far beyond their authored range.

    This rig's committed motion peaks around 3.7 rad, so an absolute limit cannot
    separate a dramatic swing from a unit bug - but a unit bug overshoots by ~57x.
    Comparing against the post-import measurement with a 1 rad (57 deg) headroom
    catches it while leaving every intended pose alone.
    """
    offenders = []
    for (name, path, axis), was in baseline.items():
        action = bpy.data.actions.get(name)
        if action is None:
            continue
        fc = find_curve(action, path.split('"')[1], "rotation_euler", axis)
        if fc is None:
            continue
        now = max((abs(kp.co[1]) for kp in fc.keyframe_points), default=0.0)
        if now > was + tolerance:
            offenders.append({
                "clip": name[len(LA.PREFIX):],
                "bone": path.split('"')[1],
                "axis": axis,
                "was_deg": round(math.degrees(was), 1),
                "now_deg": round(math.degrees(now), 1),
            })
    return offenders


# ---------------------------------------------------------------------- the pass
def clip_path():
    return LA.asset(LA.find_project(), "animations", "entity", "lizardman.animation.json")


def restore_original():
    """Put the committed animation back from the .bak the exporter kept.

    `run()` starts by re-importing the mod's files, so it must be given the
    committed animation. Once you have exported, call this first - otherwise the
    pass would be applied a second time on top of its own output.
    """
    path = clip_path()
    if not os.path.exists(path + ".bak"):
        raise RuntimeError("no %s.bak to restore from" % os.path.basename(path))
    shutil.copy2(path + ".bak", path)
    return path


def run(verbose=True):
    """Rebuild every clip from the mod's files, then apply the realism passes.

    Expects the committed animation on disk; call `restore_original()` first if a
    previous run has already exported.
    """
    project = LA.find_project()
    if not project:
        raise RuntimeError("project folder not found - set scene.gecko_project")
    count = LA.import_all(project)
    baseline = snapshot()
    applied = []

    walk = bpy.data.actions[LA.PREFIX + "walk"]
    pass_gait_weight(walk, hip=3.4, torso=-1.7, chest=-2.4, head=-1.5)
    pass_tail_bob(walk, amps=(2.1, 3.1, 4.1))
    applied.append(("walk", "weight shift + tail bob"))

    run_a = bpy.data.actions[LA.PREFIX + "run"]
    pass_gait_weight(run_a, hip=5.2, torso=-2.6, chest=-3.6, head=-2.3)
    pass_tail_bob(run_a, amps=(3.2, 4.6, 6.0), lags=(0.0, 0.5, 1.0))
    applied.append(("run", "weight shift + tail bob"))

    idle = bpy.data.actions[LA.PREFIX + "idle"]
    pass_idle_life(idle)
    applied.append(("idle", "breathing + weight shift + head look + tail + toes"))

    dragged = []
    for action in bpy.data.actions:
        if not action.name.startswith(LA.PREFIX):
            continue
        clip = action.name[len(LA.PREFIX):]
        if clip in LOOPING:
            continue
        lag = dict(DRAG)
        if not tail_needs_drag(action):
            lag.pop("Tail2", None)
            lag.pop("Tail3", None)
        pass_drag(action, lag)
        dragged.append(clip)
    applied.append(("one-shots", "%d clips get drag: %s" % (len(dragged), ", ".join(sorted(dragged)))))

    if verbose:
        for name, what in applied:
            print("realism: %-10s %s" % (name, what))
    offenders = sanity_check(baseline)
    if offenders:
        raise RuntimeError("realism: %d rotation curves grew past their authored range, first: %s"
                           % (len(offenders), offenders[0]))
    return count, dragged
