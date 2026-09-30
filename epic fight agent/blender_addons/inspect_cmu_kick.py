"""Locate front-kick peaks in CMU subject 135 trial 04."""
import json
from pathlib import Path
import bpy

root = Path(__file__).resolve().parents[1]
bpy.ops.import_anim.bvh(filepath=str(root/'sampleRig/mocap_reference/135_04.bvh'),
                        rotate_mode='QUATERNION')
actor = bpy.context.object
scene = bpy.context.scene
samples = []
for frame in range(1, 1318, 5):
    scene.frame_set(frame)
    bones = actor.pose.bones
    samples.append((frame, bones['LeftFoot'].head.z, bones['RightFoot'].head.z,
                    bones['Hips'].head.z))
events = []
for index in range(1, len(samples)-1):
    frame, left, right, hips = samples[index]
    for side, value, column in [('L',left,1),('R',right,2)]:
        if value > samples[index-1][column] and value >= samples[index+1][column]:
            events.append({'frame':frame,'seconds':round((frame-1)/120,2),
                           'side':side,'foot_z':round(value,3),
                           'relative_hip_z':round(value-hips,3)})
events.sort(key=lambda item:item['foot_z'],reverse=True)
(root/'sampleRig/mocap_reference/kick_peaks.json').write_text(json.dumps(events[:40],indent=2))
print('MOCAP_KICK_PEAKS',json.dumps(events[:12]))
