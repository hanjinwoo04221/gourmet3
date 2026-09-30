# AI animation contract, version 7

## Left/right response from the evaluated center of mass

`articulate_joint_phases(..., balance_groups=[...])` can vary the **authored
offset amplitude** of left and right limb groups separately. At each source
frame it evaluates the COM from explicit positive relative masses, projects it
between rig-specific left/right anchors, and accounts for declared support
contact. The loaded side receives less additional articulation and the free
side more, with a bounded gain. It does not add movement to a zero authored
track. The contact chain itself remains on its source pose. A
`sync_windows` interval brings both gains smoothly back to 1, preserving an
intentional two-arm reach or coordinated landing. The actual angles may still
differ because the two limb tracks remain independently authored.

```python
balance = [{
    'left': [left_shoulder, left_arm], 'right': [right_shoulder, right_arm],
    'left_anchor': left_foot, 'right_anchor': right_foot,
    'masses': {pelvis: 3, chest: 2, left_thigh: 1.5, right_thigh: 1.5},
    'gain': .35, 'contact_bias': .25,
    'sync_windows': [(coordinated_start, coordinated_end)],
    'sync_fade_frames': 2,
}]
report = ef.articulate_joint_phases(rig, axes, tracks,
    contacts=[{'bone': left_foot, 'start': start, 'end': end}],
    balance_groups=balance)
```

Left/right means the explicitly paired sides, not a name suffix assumption.
Anchors must have distinct evaluated positions. The COM is a mass estimate;
review the loaded side, contact and pose at intermediate frames. For perfectly
centered COM and no asymmetrical support, equal modulation can be appropriate.

## Phase-aware limb articulation

`articulate_joint_phases` copies the active action and adds bounded swing and
lateral spread offsets relative to its evaluated joint rotations. Specify both
local axes per joint from actual rig inspection; the API does not infer a knee
hinge or shoulder abduction sign from names. Each joint has ordered
`(frame, swing_degrees, spread_degrees)` knots spanning the action. Offsets
interpolate smoothly between preparation, impact and recovery. Include
deforming sibling helpers only after confirming their geometry. A listed
contact protects its effector and ancestor chain during its window.

```python
result = ef.articulate_joint_phases(rig,
    {'actual_shoulder': {'swing_axis': (1,0,0), 'spread_axis': (0,0,1)}},
    {'actual_shoulder': [(start,0,0), (load,-7,-10),
                         (impact,10,13), (end,0,0)]},
    contacts=[{'bone':'actual_support_foot','start':start,'end':end}],
    name='Attack_Articulated')
```

The illustrated axes/angles are only examples. Review object-space limb arcs,
floor contact and mesh deformation on each rig. This stage is suited to a
native action whose large body motion already works; it is not an automatic
fix for weak choreography or bad retargeting. Lizardman's `Elbow_*`/`Knee_*`
are sibling deform helpers beside `Hand_*`/`Leg_*`, so its test profiles move
those helpers with the distal bones rather than treating them as serial hinges.

## Reference-guided timing for existing actions

When a mocap transfer distorts an unfamiliar rig, use a native action whose
joint arcs and contacts already work on that rig. `retime_action_by_beats`
copies it and maps manually chosen source events to target frames. It changes
every channel and Bezier handle with one monotone map; it does not invent poses,
force every joint to move, or alter the original action. Review the result
before optional `refine_motion`, which can be omitted when secondary inertia
makes the animation worse.

Epic Fight examples inspected in the 1.21.1 source: `enderman/rush_kick` puts
root and both leg keys at 0, .1333, .2667, .45, .6667, .7167, .7667,
.8667, 1.0833, 1.15 and 1.6667 seconds, but its torso has only boundary
keys. `enderman/jump_kick` has dense root/leg events through .85 seconds and
a long 1.5-second ending; `biped/skill/sweeping_edge` uses distinct root,
arm and leg intermediate times. The reusable rule is to synchronize major
events across the action, leave quiet joints quiet, compress a strike/reversal
only after a readable load, and leave enough recovery to settle. These values
are observations, not fixed timings for other rigs or prompts.

```python
# Frames come from inspection of the active source action, not guessed axes.
report = ef.retime_action_by_beats(rig, [
    (source_start, 1), (load_frame, 7), (impact_frame, 12),
    (release_frame, 15), (source_end, 29),
], name="Attack_Reference_Timing")
# Inspect contacts, silhouette and subframes before deciding whether to refine.
```

For Epic Fight 1.21.1 resource paths, transform tracks, and separate combat timing,
follow [EPICFIGHT_FORMAT_RULES.md](EPICFIGHT_FORMAT_RULES.md) after motion authoring.
The format checker is `epicfight_format.py`; it does not replace visual or in-game review.

Epic Fight reference clips such as `enderman/jump_kick`, `enderman/knee`, and
`biped/skill/roll_forward` share start/end keys across joints but use fewer
middle keys on some joints. `author_motion` follows this pattern: it keys all
referenced joints at the boundaries and only explicitly posed joints at middle
keyposes. Place breakdown poses at intended changes of speed/direction and
stage proximal/distal limb timing deliberately. Sparse keys are not a reason
to move an otherwise held joint. Epic Fight interpolates between exported
keys, so inspect the exported result when Blender's Bezier curves are baked.

The low-level entry point is `epicfight_ai_anim.refine_motion(obj, plan, profile)`.
For an unfamiliar rig, start with `analyze_rig`, `build_auto_refinement`, and then
`refine_motion_auto` after reviewing contact suggestions.
It enhances an **authored action**. It does not understand prose or invent choreography.
An AI translates the prompt into poses, timing, contacts and a rig profile before calling it.
No algorithm guarantees realistic animation for every prompt; report unverified anatomy,
collisions, support stability and ambiguous intent instead of silently guessing.

## Workflow for any prompt

1. Call `inspect_motion_rig(obj)`. Inspect local axes, hierarchy, constraints, scale and FPS.
   `analyze_rig(obj, overrides)` builds a conservative profile from deformation flags,
   topology, geometry, animation channels and optional name hints. Low-confidence roles
   remain visible and require review; “any rig” does not mean unknowable anatomy is guessed.
2. Identify semantic roles: driver/pelvis, spine, neck, limbs, support chains,
   deformation helpers, attachments. Do not move a bone merely because it exists.
3. Author the action: preparation, load transfer, movement, contact, follow-through,
   recovery as appropriate. Walking loops, idle, flight and attacks need different poses.
   Use existing keyframe APIs. Preserve source work; keep pose generation separate from refinement.
   `author_motion` can map object-space bone directions to an unfamiliar rig without
   assuming local Euler axes. It does not decide which direction makes a plausible attack.
   For arm-led motion, plan forward reach and lateral spread separately. A keypose's
   `lateral_targets` can move a hand sideways through a shoulder without assuming
   its local X/Y/Z axis. Give the target joint, descendant effector, object-space
   direction and desired displacement; inspect the achieved value in the report.
4. Supply actual rig names and positive relative masses. Exclude attachment/helper bones
   unless their rig-specific deformation behavior is known. The mass model uses evaluated
   segment midpoints; it is an estimate, not measured body mass.
5. Specify contact windows explicitly, or `contacts: []` for deliberate no-contact motion.
   Fix sliding/contact mistakes in the source first. Contact points are bone tails in world
   space. Protect impact/loop endpoint poses with anchors. Holds preserve sampled source poses;
   they do not invent a hit-stop or repair a source that keeps moving.
6. Call `refine_motion`. Review its report, compare the copied result visually and at subframes.
   Do not overwrite the source or claim physical realism just because validation passed.
   The default refinement samples evaluated COM throughout the clip and adds bounded
   translational and rotational inertial response. It also reports how many eligible
   authored joints actually rotate between samples. If the auto plan finds animated
   deformation helpers beside a joint chain, it includes them with bounded
   parent-motion lag for review; controls,
   attachments and planted ancestor chains remain excluded.

## Example (replace illustrative names with inspected rig names)

```python
import epicfight_ai_anim as ef

inventory = ef.inspect_motion_rig(rig)
profile = {
    "driver": "pelvis",
    "masses": {"spine": .55, "head": .10, "upper_leg_l": .175, "upper_leg_r": .175},
    "exclude": ["weapon_socket"],
    # Optional local XYZ Euler bounds in radians. Determine axes from THIS rig.
    # Never copy another skeleton's hinge signs blindly.
    "limits": {},
}
plan = {
    "name": "Motion_Refined",
    "bones": ["spine", "head", "upper_arm_l", "forearm_l", "upper_leg_l"],
    "contacts": [{"bone": "lower_leg_l", "start": 12, "end": 28}],
    "anchor_frames": [18, 40],
    "holds": [],
    "settings": {
        "strength": .35,
        "frequency_hz": 5.0,
        "damping": .95,
        "centrifugal_gain": .55,
        "translation_gain": .20,
        "helper_follow_gain": .25,
        "max_offset_degrees": 8.0,
        "sample_step": .5,
    },
}
report = ef.refine_motion(rig, plan, profile)
```

For an unfamiliar rig:

```python
analysis = ef.analyze_rig(rig)
draft = ef.build_auto_refinement(rig)  # read draft['plan'] and contact suggestions
# Accept/correct suggestions explicitly. Use [] only when the motion truly has no contact.
contacts = draft['plan']['contacts']
report = ef.refine_motion_auto(rig, contacts, anchors=[impact_frame],
    overrides={"driver": analysis["driver"]})
```

Portable pose authoring example:

```python
draft = ef.analyze_rig(rig, overrides={"roles": {"arm": [actual_upper_arm]}})
ef.author_motion(rig, [
  {"frame": 1, "directions": {actual_upper_arm: [0, 0, -1]}, "marker": "ready"},
  {"frame": 9, "directions": {actual_upper_arm: [0, -1, 0]}, "marker": "strike"},
], draft, name="Portable_Strike_Authored")

# Optional on any rig with a shoulder-to-hand chain. This requests +0.1 object
# units along the object's X axis from the pose authored above. The solver
# probes the joint's local axes, clamps to 30 degrees by default, and reports
# the achieved displacement; the AI still chooses where the hand should travel.
ef.author_motion(rig, [
  {"frame": 1, "local_quaternions": {actual_shoulder: [1,0,0,0]}},
  {"frame": 9, "local_quaternions": {actual_shoulder: [1,0,0,0]},
   "lateral_targets": {actual_shoulder: {
       "effector": actual_hand, "direction": [1,0,0], "distance": 0.1,
       "max_degrees": 30}}},
], draft, name="Portable_Lateral_Reach")
```

`analyze_rig` works without conventional names by selecting deformation bones and a
topological driver. Names only improve semantic role confidence. For creatures, wings,
tails, extra limbs and unusual axes, supply role/mass/driver/up-axis overrides. Automatic
contact detection uses evaluated endpoint height and velocity and returns suggestions only.

All event frames must lie within the active action. Missing names, unknown options,
nonfinite numbers and invalid intervals are errors. A profile's `driver` is required
even for motion without rotation. `bones` explicitly defines the modification scope.
The root is not assumed to be named Root and the action length is never hardcoded.

## Guarantees and limitations

- Original action, its slot and frame are restored on failure; the failed copy is removed.
  On success the result becomes active, the original remains available and scene frame is restored.
- Each contact's entire ancestor chain retains its source curves for the **whole clip**.
  This deliberately sacrifices some secondary motion to avoid generic IK corrupting contact.
  It does not pin a sliding source foot or establish a new floor contact.
- Quaternion and Euler targets use normalized quaternion error integration, compatible signs,
  FPS-aware seconds, substeps, damping, bounded torque and capped deviations. Euler curves
  are converted back in their authored order. Axis-angle must be converted first.
- Rotation driver velocity/acceleration comes from evaluated world rotations. The outward
  `-omega × (omega × radius)` and tangential `-alpha × radius` terms produce bounded rotational
  spring response. Distance changes spring stiffness; it is not a law that proximity alone
  must increase the instantaneous speed of every body part.
- COM translation acceleration contributes a bounded opposing inertial response. Both
  translation and centrifugal gains can be set to zero for a deliberately static or
  tightly constrained action. The `joint_coverage` report counts actual sampled local
  rotations; it does not imply that every bone should move on every frame.
- `joint_helpers` in an explicit plan identifies verified deforming leaf bones that
  sit beside a longer joint chain. Their limited follow-through uses the parent's
  measured angular change, not a guessed hinge sign. Automatic plans populate this
  list only for animated, unconstrained helpers and report it for inspection.
- Original endpoints and supplied anchors are preserved as rotation poses. Corrections fade
  near anchors/holds. Timing is unchanged. Sampling may change between-sample interpolation;
  explicit loop velocity continuity and collision avoidance are not certified.
- Limits are rig-specific local XYZ ranges. Violations reject the result rather than silently
  clipping a pose and breaking contact. Missing limits produce a warning.
- Active NLA, drivers, selected-bone constraints, F-curve modifiers and multi-layer blending
  require baking first. Existing multi-slot actions are edited only through the active slot.
- `validation` covers sampled contact deviations and rotation steps, not physical balance,
  anatomy, mesh collisions or every subframe. `com_trajectory` reflects the output poses.
- Standard mass estimation uses strictly positive existing segments and normalizes their sum.
  Uniform rescaling preserves the dimensionless torque model; contact tolerance is world units.

Defaults favor modest correction. Increase `centrifugal_gain`/`strength` only after reviewing
contact and angular motion; do not repeat refinement recursively on its own output.

## Existing tools

`apply_intent`, `humanize_motion`, `JOINT_RULES`, `set_hinge_flexion`, `com_inertia.py` and
`build_capoeira.py` remain compatibility/choreography examples. They are not the general
contract: some use Epic Fight names, pose assumptions or fixed beats. New AI workflows should
use this API and explicit rig profiles instead of copying the capoeira script.

Run regression tests with Blender in background mode:
`blender --background --factory-startup --python blender_addons/test_motion_api.py`
