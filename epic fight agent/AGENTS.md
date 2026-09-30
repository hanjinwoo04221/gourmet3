# Animation authoring in this project

The reusable implementation is `blender_addons/epicfight_ai_anim.py`.
Read `blender_addons/AI_ANIMATION_API.md` before creating or refining animation.
For Epic Fight 1.21.1 JSON delivery, also follow
`blender_addons/EPICFIGHT_FORMAT_RULES.md` and run its format validator.

- Treat each user prompt as a motion-planning task. Inspect the active rig/action,
  choose meaningful phases, author poses, then call `refine_motion` with explicit
  contacts, anchors, selected bones and a rig profile. The API does not generate
  choreography from text. Do not claim all prompts are automatically realistic.
- For unfamiliar rigs call `analyze_rig` and `build_auto_refinement`. Review low-confidence
  roles and detected contacts; pass explicit overrides for creatures or unconventional axes.
- Do not infer physical hinge signs from a parent's rotation or blindly apply
  `JOINT_RULES`. Profile axes and bounds must match the actual rig.
- Preserve planted chains and impact poses before adding secondary motion.
  An empty contact list is an explicit no-contact decision, not a default guess.
- For any prompt with active arms, plan shoulder and hand travel in both the
  forward and lateral directions. Use `author_motion` `lateral_targets` for
  side reach when the rig supports it; the solver probes local axes. Specify
  the effector, object-space direction and displacement in each relevant pose.
  Do not force lateral motion into poses whose intent does not call for it.
- Use evaluated frame-specific COM, FPS-aware units, bounded acceleration and
  quaternion continuity. Do not add arbitrary movement to every bone. Attachments
  remain attachments; deformation helpers follow joint geometry if needed.
- Use the shared refinement defaults for COM translation and rotational inertia on
  authored motion. Review `joint_coverage` and include animated deformation helpers
  when their topology is verified. A planted chain or meaningful hold may remain still.
- Preserve original actions. Use the report and visual/subframe review; distinguish
  tested contact/rotation properties from unverified collisions or physical balance.
- `build_capoeira.py` is a choreography example with top-level scene mutations.
  Never import or execute it to handle unrelated prompts. `com_inertia.py` and legacy
  in-place presets are examples, not the general AI entry point.
- Run `blender --background --factory-startup --python blender_addons/test_motion_api.py`
  after changing the general API. Never run the test file in the user's active scene.
