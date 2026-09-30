"""Check saved martial-arts actions at subframes and across contact windows."""
import json
import math
from pathlib import Path

import bpy

root = Path(__file__).resolve().parents[1]
report = json.loads((root / 'sampleRig/lizardman_martial_arts_report.json').read_text())
rig = bpy.data.objects['Lizardman']
scene = bpy.context.scene
names = [bone.name for bone in rig.data.bones if bone.use_deform
         and not bone.name.startswith('Tool')]
checks = {}
for move in report:
    action = bpy.data.actions[move['refined']]
    rig.animation_data.action = action
    if action.slots:
        rig.animation_data.action_slot = action.slots[0]
    duration = int(action.frame_range[1])
    previous = None
    maximum = 0
    worst = None
    moving = []
    contact_positions = {}
    for step in range(2, 2 * duration + 1):
        frame = step / 2
        scene.frame_set(math.floor(frame), subframe=frame - math.floor(frame))
        current = {name: rig.pose.bones[name].matrix.to_quaternion().copy().normalized()
                   for name in names}
        if previous:
            angles = [math.degrees(2 * math.acos(min(1, abs(previous[name].dot(current[name])))))
                      for name in names]
            if max(angles) > maximum:
                worst = [frame, names[angles.index(max(angles))]]
            maximum = max(maximum, *angles)
            moving.append(sum(angle > .1 for angle in angles))
        previous = current
        for contact in move['contacts']:
            if contact['start'] <= frame <= contact['end']:
                bone = rig.pose.bones[contact['bone']]
                key = f"{contact['bone']}:{contact['start']}-{contact['end']}"
                contact_positions.setdefault(key, []).append(tuple(rig.matrix_world @ bone.tail))
    drift = {key: max(math.dist(points[0], point) for point in points)
             for key, points in contact_positions.items()}
    counts = list(move['authored_joint_key_counts'].values())
    assert min(counts) < max(counts), 'No joint-specific timing'
    assert maximum < 85, 'Subframe rotational spike'
    assert move['validation']['contact_error'] < 1e-4
    checks[move['name']] = {
        'authored_joint_keys_min_max': [min(counts), max(counts)],
        'max_half_frame_rotation_degrees': maximum,
        'worst_step': worst,
        'median_moving_joints': sorted(moving)[len(moving) // 2],
        'contact_world_tail_drift': drift,
        'largest_reach_error': max(abs(r['requested'] - r['achieved'])
                                   for r in move['lateral_results']),
    }
out = root / 'sampleRig/lizardman_martial_arts_validation.json'
out.write_text(json.dumps(checks, indent=2), encoding='utf-8')
print('MARTIAL_ARTS_VALIDATION', json.dumps(checks))
