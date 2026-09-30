"""Validate the generated Lizardman pack and export pose data for a contact sheet."""
import bpy, json, math
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
rig = bpy.data.objects['Lizardman']
scene = bpy.context.scene
specs = {
    'feral.pounce_bite': [1, 9, 24, 33, 38],
    'feral.razor_claw_chain': [1, 11, 19, 27, 36],
    'feral.tail_reversal': [1, 10, 18, 28, 36],
}

def assign(action):
    rig.animation_data.action = action
    if action.slots:
        rig.animation_data.action_slot = action.slots[0]

def quat(pb):
    if pb.rotation_mode == 'QUATERNION':
        return pb.rotation_quaternion.normalized()
    if pb.rotation_mode == 'AXIS_ANGLE':
        from mathutils import Quaternion
        a, x, y, z = pb.rotation_axis_angle
        return Quaternion((x, y, z), a).normalized()
    return pb.rotation_euler.to_quaternion().normalized()

report = {'actions': {}, 'poses': {}}
for action_name, pose_frames in specs.items():
    action = bpy.data.actions[action_name]
    assign(action)
    start, end = (int(round(v)) for v in action.frame_range)
    previous = {}
    max_step = 0.0
    nonfinite = []
    min_contact_z = float('inf')
    for frame in range(start, end + 1):
        scene.frame_set(frame)
        for pb in rig.pose.bones:
            q = quat(pb)
            if not all(math.isfinite(v) for v in q):
                nonfinite.append([frame, pb.name])
            if pb.name in previous:
                dot = min(1.0, abs(previous[pb.name].dot(q)))
                max_step = max(max_step, math.degrees(2.0 * math.acos(dot)))
            previous[pb.name] = q.copy()
        for name in ('Foot_L', 'Foot_R', 'Toe_L_mid', 'Toe_R_mid'):
            p = rig.matrix_world @ rig.pose.bones[name].head
            min_contact_z = min(min_contact_z, p.z)
    report['actions'][action_name] = {
        'frame_range': [start, end],
        'max_integer_frame_rotation_degrees': max_step,
        'nonfinite_transforms': nonfinite,
        'minimum_foot_or_toe_head_z': min_contact_z,
    }
    report['poses'][action_name] = []
    for frame in pose_frames:
        scene.frame_set(frame)
        joints = {}
        edges = []
        for pb in rig.pose.bones:
            h = rig.matrix_world @ pb.head
            t = rig.matrix_world @ pb.tail
            joints[pb.name] = {'head': list(h), 'tail': list(t)}
            if pb.parent:
                edges.append([pb.parent.name, pb.name])
        report['poses'][action_name].append({'frame': frame, 'joints': joints, 'edges': edges})

path = ROOT / 'sampleRig/lizardman_feral_pack_v2_validation.json'
path.write_text(json.dumps(report, indent=2), encoding='utf-8')
print('PASS FERAL VALIDATION', json.dumps(report['actions']))
