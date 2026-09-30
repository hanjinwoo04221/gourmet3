"""Find short boxing events in the downloaded CMU BVH without changing the source scene."""
import json
import math
from pathlib import Path

import bpy
from mathutils import Vector

root = Path(__file__).resolve().parents[1]
bpy.ops.import_anim.bvh(filepath=str(root / 'sampleRig/mocap_reference/14_01.bvh'),
                        rotate_mode='QUATERNION')
actor = bpy.context.object
action = actor.animation_data.action
scene = bpy.context.scene
samples = []
for frame in range(1, int(action.frame_range[1]) + 1, 5):
    scene.frame_set(frame)
    bones = actor.pose.bones
    hips = bones['Hips'].head.copy()
    chest = bones['Spine1'].head.copy()
    left = bones['LeftArm'].head.copy()
    right = bones['RightArm'].head.copy()
    up = (chest - hips).normalized()
    side = (right - left).normalized()
    front = side.cross(up).normalized()
    width = (right - left).length
    reaches = {}
    for hand, shoulder in [('L', 'LeftArm'), ('R', 'RightArm')]:
        end = bones['LeftHand' if hand == 'L' else 'RightHand'].head
        origin = bones[shoulder].head
        reaches[hand] = (end - origin).dot(front) / width
    samples.append((frame, reaches['L'], reaches['R']))

peaks = []
for index in range(2, len(samples) - 2):
    frame, left, right = samples[index]
    for hand, value, column in [('L', left, 1), ('R', right, 2)]:
        if value > samples[index - 1][column] and value >= samples[index + 1][column]:
            delta = value - samples[index - 2][column]
            if delta > .04:
                peaks.append({'frame': frame, 'seconds': round((frame - 1) / 120, 2),
                              'hand': hand, 'reach': round(value, 3),
                              'extension_0.08s': round(delta, 3)})
peaks.sort(key=lambda x: x['extension_0.08s'], reverse=True)
out = root / 'sampleRig/mocap_reference/boxing_peaks.json'
out.write_text(json.dumps(peaks[:60], indent=2), encoding='utf-8')
print('MOCAP_BOXING_PEAKS', json.dumps(peaks[:20]))
