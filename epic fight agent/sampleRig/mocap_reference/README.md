# Motion reference library for Lizardman tests

The three BVH files here are selected samples from the [CMU Graphics Lab Motion Capture Database](https://mocap.cs.cmu.edu/) in [Bruce Hahne's BVH conversion](https://github.com/una-dinosauria/cmu-mocap). CMU describes its dataset as free for all uses; the converter's accompanying [READMEFIRST.txt](READMEFIRST.txt) says no extra restrictions were added and records the corrected 120 fps timing. Retain that notice with redistributed BVH files.

| Local file | Capture | Frames | Source |
| --- | --- | ---: | --- |
| `14_01.bvh` | CMU subject 14, boxing, used for `mocap.boxing_two_strikes` | 5,595 | [BVH](https://github.com/una-dinosauria/cmu-mocap/blob/master/data/014/14_01.bvh) |
| `14_02.bvh` | CMU subject 14, alternate boxing take, available for future tests | 5,424 | [BVH](https://github.com/una-dinosauria/cmu-mocap/blob/master/data/014/14_02.bvh) |
| `135_04.bvh` | CMU subject 135, front kick, used for `mocap.front_kick_right` | 1,317 | [BVH](https://github.com/una-dinosauria/cmu-mocap/blob/master/data/135/135_04.bvh) |

The reproducible selection and retargeting workflow is in `blender_addons/inspect_cmu_boxing.py`, `inspect_cmu_kick.py`, and `build_lizardman_cmu_retarget.py`. The script maps measured bone directions and hip travel onto the inspected Lizardman topology, then uses bounded positional IK to move the hands and kicking foot toward capture-derived targets. It locks declared support-foot windows before `refine_motion_auto`. `Knee_*` and `Elbow_*` are deformation helpers beside the main `Leg_*` and `Arm_*` chains. No source BVH action is overwritten.

The saved `Lizardman_CMU_Mocap_Test.blend` is an **experimental retarget**, not a clean transfer of human anatomy. The lizardman's short bone chains and mesh weights limit how faithfully a human punch or high kick reads. Some IK targets remain unreachable (see `ik_residual_max` in the report). The numeric checks cover effector displacement, sampled motion, and foot drift, not mesh collision or biomechanical realism. Open `sampleRig/lizardman_cmu_playback/index.html` for a 24 fps frame player and inspect the action in Blender before using it in game. In this test the boxing hand moves 0.396 Blender units at the selected beat, and the kicking foot moves 0.640; the earlier direction-only transfer moved the boxing hand about 0.002.

Other libraries worth considering for a future capture set:

- [Adobe Mixamo](https://helpx.adobe.com/creative-cloud/faq/mixamo-faq.html) has a humanoid animation library and permits use in games, but requires an Adobe ID for downloads; its auto-rigger is for humanoids and may not accept a tailed lizardman directly.
- [Rokoko Motion Library](https://support.rokoko.com/hc/en-us/articles/4410021327121-Getting-Started-Rokoko-Studio-Motion-Library) has free and paid captured clips and exports motion-only FBX through its application. It was not downloaded or used in this test.
