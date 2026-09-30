# Epic Fight 1.21.1 animation rules

These rules describe the checked-in `epicfight-1.21.1` source, not every Epic Fight version. They complement the motion-authoring workflow in [AI_ANIMATION_API.md](AI_ANIMATION_API.md). Plan and refine the motion first; validate the exported resource separately.

## Resource and clip

- Register an animation with its intended armature. A registry name such as `epicfight:enderman/jump_kick` resolves to `assets/epicfight/animmodels/animations/enderman/jump_kick.json`. Keep the resource path and registration in sync. (`StaticAnimation.java`, constructors.)
- The pose file has a top-level `animation` array. Each track is `{"name": jointName, "time": [seconds...], "transform": [pose...]}`. `time` and `transform` lengths must match. Tracks can have different key times. Use finite, nonnegative, strictly increasing timestamps; this is a safe authoring rule even though the loader only explicitly checks array length and skips negative timestamps. Clip duration follows the latest track key. (`JsonAssetLoader.loadClipForAnimation`, `getTransformSheet`.)
- Match track names exactly to the target armature. The loader skips unknown joints, so inspect the result instead of treating successful JSON parsing as proof that a bone animated. `Coord` is a special motion-coordinate track for action animations, not an ordinary armature joint. Put `Root` or `Coord` first when appropriate: the loader's root conversion depends on track order. Do not invent tracks for every bone; preserve intentional holds and attachments. (`JsonAssetLoader.java`, lines 558–610.)
- Absent `format` means `MATRIX`. In `MATRIX`, each transform is 16 numbers. The loader loads it as an `OpenMatrix4f`, transposes it, applies Blender-to-Minecraft root-axis conversion, and multiplies by the inverse joint local transform. Export evaluated transforms in the format expected by this loader; direct serialization of Blender pose-local Euler values is insufficient. Check translation, facing, handedness, and root travel in-game. (`JsonAssetLoader.java`, lines 55–59 and 708–760.)
- `"format":"ATTRIBUTES"` uses `{"loc":[x,y,z],"rot":[a,b,c,d],"sca":[x,y,z]}` per key. The loader applies its own quaternion component/sign mapping. Do not assume raw Blender quaternion order matches this format without a round-trip check. Avoid mixing matrix and attribute keys even though the loader has a format-recovery workaround. (`JsonAssetLoader.java`, lines 730–800.)

## Combat timing and effects

- Pose JSON does not define damage or hit windows. `AttackAnimation` registers transition, anticipation, pre-delay, contact and recovery seconds, collider, and collider joint separately. The attacking interval is bound from pre-delay to contact. Align authored windup, strike, hit pose, and follow-through to those values; confirm the collider follows the actual weapon/limb. (`AttackAnimation.java`, constructors and `bindPhaseState`; `gameasset/Animations.java` registrations.)
- Optional sibling `data/<clip>.json` files can hold presentation metadata such as `trail_effects` with `start_time`, `end_time`, `joint` and `item_skin_hand`. Match its joint and interval to the swing. This file does not replace the attack registration. (Example: `wither_skeleton/data/sword_attack1.json`; `AnimationSubFileReader.java`.)
- Keep contact windows and impact anchors in the authoring plan passed to `refine_motion`. The exporter JSON does not encode those constraints. Preserve planted feet and hit poses while refining, then visually inspect subframes, root travel and the in-game collider. Structural validation cannot certify balance, contact, mesh collision, or attack effectiveness.

## Before using a new clip

1. Inspect the target armature with `analyze_rig`; review uncertain roles, axes, and `Coord`/root behavior.
2. Author phases for the requested move. Review COM response, rotation inertia, contacts and joint coverage in the refinement report.
3. Export to the registered path without replacing a source action. Run `epicfight_format.py <clip.json>` for structural errors; pass armature names to `validate_clip(data, joint_names=...)` to detect ignored tracks.
4. Compare attack registration and any trail metadata against the clip duration, then preview in Epic Fight. A clean validator report is only a format check.

Reference implementation: `epicfight-1.21.1/epicfight-1.21.1/src/main/java/yesman/epicfight/api/asset/JsonAssetLoader.java`, `.../api/animation/types/StaticAnimation.java`, `.../api/animation/types/AttackAnimation.java`, and the checked-in JSON resources under `src/main/resources/assets/epicfight/animmodels/animations/`.
