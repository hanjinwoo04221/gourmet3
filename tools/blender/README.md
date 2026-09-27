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

