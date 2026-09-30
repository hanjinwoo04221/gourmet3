"""Inspect saved combat actions at half frames and contact windows."""
import json
import math
from pathlib import Path

import bpy

root = Path(__file__).resolve().parents[1]
report = json.loads((root / 'sampleRig/lizardman_epicfight_timing_report.json').read_text())
rig = bpy.data.objects['Lizardman']
scene = bpy.context.scene
names = [b.name for b in rig.data.bones if b.use_deform and not b.name.startswith('Tool')]
result = {}
for move in report:
    action = bpy.data.actions[move['refined']]
    rig.animation_data.action = action
    if action.slots:
        rig.animation_data.action_slot = action.slots[0]
    end = int(action.frame_range[1])
    prior = None
    max_angle = 0.0
    worst = None
    positions = {}
    moving_counts = []
    for step in range(2, 2 * end + 1):
        frame = step / 2
        scene.frame_set(math.floor(frame), subframe=frame - math.floor(frame))
        now = {name: rig.pose.bones[name].matrix.to_quaternion().copy().normalized()
               for name in names}
        for contact in move['contacts']:
            if contact['start'] <= frame <= contact['end']:
                bone = rig.pose.bones[contact['bone']]
                positions.setdefault(contact['bone'], []).append(
                    tuple(rig.matrix_world @ bone.tail))
        if prior:
            angles = [math.degrees(2 * math.acos(min(1.0, abs(prior[name].dot(now[name])))))
                      for name in names]
            if max(angles) > max_angle:
                worst = [frame, names[angles.index(max(angles))]]
            max_angle = max(max_angle, *angles)
            moving_counts.append(sum(angle > .1 for angle in angles))
        prior = now
    drift = {name: max(math.dist(points[0], point) for point in points)
             for name, points in positions.items()}
    counts = move['authored_joint_key_counts']
    assert min(counts.values()) < max(counts.values()), 'No joint timing variation'
    assert max_angle < 85, 'Half-frame rotation spike'
    result[move['name']] = {
        'authored_key_count_min_max': [min(counts.values()), max(counts.values())],
        'max_half_frame_rotation_degrees': max_angle,
        'worst_step': worst,
        'median_moving_joints': sorted(moving_counts)[len(moving_counts) // 2],
        'contact_world_tail_drift': drift,
    }
out = root / 'sampleRig/lizardman_epicfight_timing_validation.json'
out.write_text(json.dumps(result, indent=2), encoding='utf-8')
print('LIZARDMAN_TIMING_VALIDATION', json.dumps(result))
