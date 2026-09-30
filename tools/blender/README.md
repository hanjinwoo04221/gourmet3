# Lizardman animations in Blender

`lizardman_anim.py` turns the mod's Lizardman files into a Blender rig and back. Tested on Blender 5.1.

## One-time setup
1. Open Blender, go to the **Scripting** tab, **Open** `tools/blender/lizardman_anim.py`, press **Run Script**.
2. In the 3D viewport press **N** and open the **Lizardman** tab. Set **Project** to this project folder
   (it is found automatically when the .blend is inside the project).

`tools/blender/lizardman.blend` is a ready-made import (rig, textured mesh, all 20 clips). You still need step 1 in it
to get the panel.

## Workflow
| Button | What it does |
|---|---|
| **Import model + animations** | Rebuilds the rig, mesh and every clip from `geo/entity/lizardman.geo.json`, `textures/entity/lizardman.png` and `animations/entity/lizardman.animation.json`. Replaces anything unsaved in Blender. |
| **New animation** | Adds an empty `animation.lizardman.<name>` action with every bone keyed at rest. |
| **Export animations to the mod** | Writes every action named `animation.lizardman.*` into `lizardman.animation.json` (a `.bak` copy of the old file is kept the first time). |

Edit clips in the Dope Sheet / Graph Editor / Pose Mode. Only bones' **rotation** and **location** are exported.
Timeline is 24 fps, frame 0 = time 0. The action's frame range is the clip length; the action's *Cyclic* option is the
clip's `loop` flag (walk, run, idle loop). After exporting, run `./gradlew build` or reload resources in game (F3+T).

## Rules to know
* The model faces **-Y** in Blender. Its right side is -X. 1 unit = 1 block.
* Every bone's axes are aligned with the world, so rotating a bone rotates it about world axes through its pivot.
  **X**: + swings a hanging limb *backward*, - forward (a forward lean is - on Torso/Chest/Head, a bent knee is - on
  Leg_*, an open jaw is - on Jaw). **Y**: + tilts a hanging limb toward -X (right arm outward). **Z**: + turns to the
  character's left. Rotation mode is XZY on purpose (it matches GeckoLib's order); do not change it.
* Bind tilts of the tail and head are part of the rest pose; the exporter subtracts them, animate on top as usual.
* Interpolation: Linear = no easing, Bezier (default) = `easeInOutSine`, Sine/Quad/Cubic/Quart/Quint/Expo/Circ with an
  Ease mode = the matching `easeIn/Out/InOut...` name. A keyframe's interpolation is the one used getting to the *next*.
* Only actions whose name starts with `animation.lizardman.` are exported. Renaming an action renames the clip.
* New clips must also be registered in game: add the name to `MOVES` in `LizardmanEntity.java` and map it in
  `playClip` (walk, run and idle need nothing).

Command line (no UI):
```
blender -b --python tools/blender/lizardman_anim.py -- import out.blend
blender -b file.blend --python tools/blender/lizardman_anim.py -- export
```

## Realism pass

`lizardman_realism.py` layers an automatic polish over every clip. Run it *before* exporting:

```python
exec(compile(open("tools/blender/lizardman_realism.py").read(), "lizardman_realism.py", "exec"))
run()                      # rebuild from the mod's files, then improve every clip
LA.export_all(project)     # write lizardman.animation.json
```

**It must start from the committed animation.** `run()` re-imports the mod's files first, so after a run
that already exported, call `restore_original()` (it copies the `.bak` the exporter kept) before `run()` again.
Otherwise the pass is applied a second time on top of its own output. Always export in the same call as the
`run()` you are exporting, so nothing rewrites the file in between.

What it changes, and why:

| Pass | Clips | Effect |
|---|---|---|
| `pass_gait_weight` | walk, run | Pelvis rolls toward the swinging foot, with the torso and head counter-rolling to stay level. Measured from the evaluated foot heights, not derived from a joint angle, so the phase is exact. |
| `pass_tail_bob` | walk, run | Vertical tail follow-through driven by the body's own bob, lagging progressively down Tail1 -> Tail3. |
| `pass_idle_life` | idle | Breathing, a slow weight shift, a held head look, tail drift and a staggered toe flex. Every term completes a whole number of cycles so the loop still closes. |
| `pass_drag` | 17 one-shots | Head, Jaw and Hands are delayed about one frame so a strike follows through instead of the whole body arriving on the same frame. |

An axis audit of the committed animation is what motivated this: pitch and yaw were well authored, but **roll
was nearly dead** - 1.4 deg peak in `walk`, 1.8 in `run`, 1.6 in `idle`, and exactly 0 on Chest/Head in every
locomotion clip. After the pass: 7.3 / 11.2 / 6.2 deg.

The tail chain is deliberately **not** touched on the one-shots: the committed clips already stagger
Tail1 -> Tail2 -> Tail3 by 0.72-0.96 frames, and `tail_needs_drag` verifies that before doing anything.

### Axis conventions - read this before editing the passes

`lizardman_anim.rot_to_blender(fx, fy, fz)` is `(fx, fz, -fy)`, so a Blender fcurve index means:

| fcurve index | Blender | meaning |
|---|---|---|
| 0 | X (`fx`) | pitch - a hanging limb swings back (+) / forward (-) |
| 1 | Y (`fz`) | **roll** - tilts toward -X, the character's right |
| 2 | Z (`-fy`) | yaw - turns counter-clockwise seen from above |

The JSON file stores `[pitch, yaw, roll]`, so **JSON index 1 and Blender index 2 are swapped**. Use the
`PITCH` / `ROLL` / `YAW` constants, never a bare number.

Two mistakes are easy to make here and both were hit while writing this pass:

* Rotation fcurves store **radians**. Amplitudes are written in degrees in this module and converted with
  `rad()`; adding a raw degree number swings a bone about 57x too far.
* Angle thresholds must also be in radians. `run()` calls `sanity_check()` against a post-import snapshot,
  which fails the run if any curve grew more than 1 rad beyond its authored range.

## Previewing and verifying

`lizardman_preview.py` renders frames of a clip with the real textured mesh:

```python
import lizardman_preview as PV
PV.RES = (300, 360)
PV.freeze_framing()        # measure the framing once, from the rest pose
PV.render_clip("walk", [0, 6, 13, 19], outdir="preview_compare", azimuth_deg=90.0, tag="a")
PV.contact_sheet(list_of_paths, "sheet.png", cols=4)
```

Call `freeze_framing()` before rendering a before/after pair. Without it the camera is re-fitted to the
*current pose* on every render, so the two rows would be framed differently and the comparison would be
meaningless. `azimuth_deg` 0 = straight in front of the face (best for roll), 90 = the creature's left side
(best for pitch).

| Script | Purpose |
|---|---|
| `lizardman_axis_audit.py` | Pitch/yaw/roll amplitude per clip, flagging axes that stay dead. |
| `lizardman_export_check.py` | Diffs the exported JSON against its `.bak`: clip set, lengths, loop flags, bones, channels, easing, value bounds. |

Run `lizardman_export_check.py` after any edit to the passes. It is the regression check, and it must end with
`OK: clip set, lengths, loop flags, bones, channels and easing all preserved.`

## Mixamo retargeting

`lizardman_mixamo.py` drives this rig from Mixamo FBX clips. Drop the downloads in `tools/blender/mixamo/`
(animation-only, no skin, is enough) and run:

```python
import lizardman_mixamo as MX
MX.apply(project, {"idle": "Idle.fbx", "run": "Run.fbx"})   # replaces those clips
LA.export_all(project)
```

`apply` rebuilds the rig from the mod files first, so the other clips keep whatever the realism pass gave
them. Only the clips you name are replaced. Before overwriting a good export, snapshot it —
`tools/blender/snapshots/` holds the pre-Mixamo realism version, and the exporter's `.bak` holds the pristine
pre-realism original.

### Bone mapping

`MAPPING` maps one source bone per target bone, using the **deepest** bone of each source chain, because
matching world orientations already folds the skipped bones in — `Hand_L <- mixamorig:LeftHand` carries the
whole forearm and wrist bend, which matters here since this rig has no separate forearm.

22 of the 32 bones are driven: `Hips`→`Root`, `Spine`→`Torso`, `Spine2`→`Chest`, `Head`→`Head`, the
shoulders/arms/hands, `UpLeg`/`Leg`/`Foot`/`ToeBase` per side. The rest are synthesised, because Mixamo has
nothing like them: `Tail1..3` follow the pelvis's rotation delayed and amplified, `Jaw` gets a breath or a
step-synced pant, `Toe_*_in/out` copy the middle toe plus a static splay, and the `Elbow`/`Knee` nubs follow
their parent at 35%.

### Why the retarget looks the way it does

Three properties of this rig and of Mixamo had to be handled, and each one produced a visible bug first:

1. **Every Lizardman bone's `matrix_local` is the identity.** The rig is axis-aligned `+Y` stubs with roll 0,
   so `D_bone = D_parent @ (Qr @ Qbasis @ Qr^-1)` collapses to `Qbasis = Q_parent_pose^-1 @ D_bone`. Feed it
   the parent's *basis* instead of its *pose* and every joint lands about 90 deg out.

2. **The target's bone axis is not its limb direction.** The stubs all point `+Y` while the parts extend
   elsewhere: legs `-Z`, spine `+Z`, shoulders `+X`, feet forward-down. Transferring bone *frames* therefore
   folds the legs sideways. `LIMB_AXIS` states the real direction for all 32 bones, taken from the geo
   pivots. It is explicit rather than measured from the mesh, because four bones carry their weight behind the
   joint — `Foot` holds the heel and its centroid points backwards, ~90 deg off; `Leg` reaches back; the
   `Elbow`/`Knee` nubs sit forward.

3. **Matching the limb direction alone leaves the twist free**, which on a blocky model turns the silhouette.
   `alignment()` does a swing-twist decomposition: keep the swing that puts the limb on the source's limb
   direction, then carry over the twist component about that limb. Result: `Q(target) = Q(source) @ C` with
   `C` the constant rest-convention offset.

Mixamo is a T-pose and this rig rests with the arms down; because the transfer is `delta @ alignment` it does
not matter — the target ends up pointing wherever the source points, which is the point.

Then two more things that only show up in the game:

* **Euler branch flips.** `to_euler` picks any equivalent branch, and a flip between neighbouring frames makes
  GeckoLib spin the bone across one frame. Conversion passes the previous frame as `euler_compat`, then shifts
  each axis by whole turns to keep the values near zero. `Run`'s `Thigh_L` went from a 351 deg adjacent jump to
  0 deg this way.
* **Ground contact.** The leg-to-hip ratio differs by ~2.6%, so the feet end up ~0.03-0.07 blocks off the
  floor. `ground_contact` applies one constant vertical offset, which keeps the source's bob and contact
  timing; per-frame clamping would flatten a run's flight phase.

The tail chain is deliberately *not* dragged on the one-shots — `tail_needs_drag` first checks whether the
committed clips already stagger Tail1→Tail2→Tail3, and they do, by 0.72-0.96 frames.

### Verifying a retarget

`MX.verify(dst_arm, src_arm, src_action, clip)` returns two independent per-bone measurements, both in degrees:

| key | meaning | expected |
|---|---|---|
| `limb` | where the source's limb points vs where the target's limb points, each rig using its own limb axis | `Head` 8.0 (its own bind tilt), everything else ~0 |
| `twist_drift` | how much the frame difference `Q(src)^-1 @ Q(target)` moves over the clip | 0 for every bone |

`twist_drift` is the check that catches a direction-only retarget; note the product order, since the reverse
is not constant even when the retarget is perfect. Fold the quaternion double cover with `abs(w)` when
comparing, or a perfect match reports a phantom 180 deg.

`lizardman_mixamo_check.py` then validates the exported JSON: clip set, loop flags, finite values, that only
the named clips changed, and that no retargeted curve has a whole-turn jump between adjacent keys. It must end
with `PROBLEMS: none`.


