"""Human-inspired combat tests on the inspected Lizardman rig."""
import importlib.util
import json
import math
from pathlib import Path

import bpy
from mathutils import Quaternion, Vector

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location('ef', ROOT / 'blender_addons/epicfight_ai_anim.py')
ef = importlib.util.module_from_spec(spec)
spec.loader.exec_module(ef)
rig = bpy.data.objects['Lizardman']
scene = bpy.context.scene
analysis = ef.analyze_rig(rig, {'driver': 'Root'})
ef.build_auto_refinement(rig, {'driver': 'Root'})
BODY = [b.name for b in rig.data.bones if b.use_deform and not b.name.startswith('Tool')]
LEFT = ['Shoulder_L', 'Arm_L', 'Elbow_L', 'Hand_L']
RIGHT = ['Shoulder_R', 'Arm_R', 'Elbow_R', 'Hand_R']
CORE = ['Root', 'Torso', 'Chest', 'Head']
LEGS = ['Thigh_L', 'Knee_L', 'Leg_L', 'Foot_L',
        'Thigh_R', 'Knee_R', 'Leg_R', 'Foot_R']
TAIL = ['Tail1', 'Tail2', 'Tail3']


def assign(action):
    rig.animation_data.action = action
    if action.slots:
        rig.animation_data.action_slot = action.slots[0]


def sample(source, phase, names):
    action = bpy.data.actions['animation.lizardman.' + source]
    assign(action)
    time = action.frame_range[0] + phase * (action.frame_range[1] - action.frame_range[0])
    scene.frame_set(math.floor(time), subframe=time - math.floor(time))
    result = {}
    for name in names:
        bone = rig.pose.bones[name]
        q = (bone.rotation_quaternion if bone.rotation_mode == 'QUATERNION'
             else bone.rotation_euler.to_quaternion()).copy().normalized()
        result[name] = q
    return result


def pose(frame, marker, names, source='guard', phase=.15, offsets=None,
         root=None, reach=None):
    rotations = sample(source, phase, names)
    for name, axis, degrees in offsets or []:
        if name not in rotations:
            raise ValueError(f'{marker}: offset for unposed {name}')
        rotations[name] = (rotations[name] @ Quaternion(Vector(axis), math.radians(degrees))).normalized()
    item = {'frame': frame, 'marker': marker,
            'local_quaternions': {name: list(q) for name, q in rotations.items()}}
    if root is not None:
        item['translations'] = {'Root': root}
    if reach:
        item['lateral_targets'] = {
            'Shoulder_' + side: {'effector': 'Hand_' + side,
                                 'direction': direction, 'distance': distance,
                                 'max_degrees': 35}
            for side, direction, distance in reach}
    return item


Z = (0, 0, 1)
X = (1, 0, 0)
MOVES = [
    {'name': 'martial.jab_cross_hook', 'end': 34, 'impacts': [9, 18, 27],
     'contacts': [{'bone': 'Foot_R', 'start': 7, 'end': 10},
                  {'bone': 'Foot_L', 'start': 16, 'end': 19},
                  {'bone': 'Foot_R', 'start': 25, 'end': 28}],
     'poses': [
         pose(1, 'Boxing guard', BODY, root=(0, 0, 0)),
         pose(4, 'Weight onto rear foot', CORE + LEGS + TAIL,
              offsets=[('Torso', Z, -8), ('Chest', Z, -5)], root=(.025, .02, -.025)),
         pose(6, 'Jab shoulder leads', LEFT + ['Chest'],
              offsets=[('Arm_L', X, -12), ('Elbow_L', X, 12)],
              reach=[('L', [0, -1, 0], .08)]),
         pose(9, 'Jab impact', LEFT + ['Chest', 'Torso'],
              offsets=[('Torso', Z, -7), ('Arm_L', X, -10)],
              reach=[('L', [0, -1, 0], .08)]),
         pose(11, 'Jab recoils', LEFT + ['Chest'],
              offsets=[('Arm_L', X, 10)], reach=[('L', [0, -1, 0], .035)]),
         pose(13, 'Hip and chest load cross', CORE + RIGHT + TAIL,
              offsets=[('Root', Z, -8), ('Torso', Z, -14), ('Chest', Z, -9),
                       ('Arm_R', X, 14)], root=(-.02, 0, -.015)),
         pose(16, 'Cross shoulder starts', RIGHT + ['Torso', 'Chest'],
              offsets=[('Torso', Z, 6), ('Chest', Z, 7), ('Arm_R', X, -12)],
              reach=[('R', [0, -1, 0], .08)]),
         pose(18, 'Cross impact', RIGHT + CORE,
              offsets=[('Root', Z, 8), ('Torso', Z, 17), ('Chest', Z, 8),
                       ('Arm_R', X, -10)],
              root=(-.04, -.07, 0), reach=[('R', [0, -1, 0], .08)]),
         pose(21, 'Cross recoils', RIGHT + CORE,
              offsets=[('Torso', Z, 5), ('Arm_R', X, 8)], root=(-.04, -.07, 0)),
         pose(24, 'Hook chamber', LEFT + ['Torso', 'Chest'],
              offsets=[('Torso', Z, 13), ('Arm_L', X, 12), ('Elbow_L', X, 20)],
              reach=[('L', [1, 0, 0], -.11)]),
         pose(27, 'Hook impact', LEFT + ['Torso', 'Chest'],
              offsets=[('Torso', Z, -15), ('Chest', Z, -12),
                       ('Arm_L', X, -8), ('Elbow_L', X, 12)],
              reach=[('L', [1, 0, 0], .10)]),
         pose(30, 'Hook follow through', LEFT + CORE + TAIL,
              offsets=[('Torso', Z, -6), ('Arm_L', X, -6)]),
         pose(34, 'Return to guard', BODY, root=(0, 0, 0))]},
    {'name': 'martial.slip_elbow_knee', 'end': 32, 'impacts': [14, 24],
     'contacts': [{'bone': 'Foot_L', 'start': 11, 'end': 15},
                  {'bone': 'Foot_R', 'start': 22, 'end': 26}],
     'poses': [
         pose(1, 'Guard', BODY, root=(0, 0, 0)),
         pose(5, 'Slip outside', CORE + LEGS + TAIL, source='dodge', phase=.50,
              offsets=[('Torso', Z, -12), ('Chest', Z, -8)], root=(-.10, .02, -.04)),
         pose(8, 'Step in', CORE + LEGS, source='dodge', phase=.80,
              offsets=[('Torso', Z, -8)], root=(-.10, -.08, -.03)),
         pose(11, 'Elbow chamber', RIGHT + ['Torso', 'Chest'],
              offsets=[('Torso', Z, -12), ('Arm_R', X, 20), ('Elbow_R', X, 30)],
              reach=[('R', [0, -1, 0], .05)]),
         pose(14, 'Elbow impact', RIGHT + CORE,
              offsets=[('Torso', Z, 16), ('Chest', Z, 10),
                       ('Arm_R', X, -17), ('Elbow_R', X, 37)],
              root=(-.10, -.10, -.01), reach=[('R', [0, -1, 0], .08)]),
         pose(17, 'Elbow retract', RIGHT + CORE, root=(-.10, -.10, -.01)),
         pose(20, 'Knee load and counterguard', CORE + LEFT + LEGS,
              source='rise_left', phase=.28,
              offsets=[('Torso', Z, -7), ('Thigh_L', X, 12)],
              root=(-.10, -.10, -.035)),
         pose(24, 'Knee impact', CORE + LEFT + LEGS,
              source='rise_left', phase=.70,
              offsets=[('Torso', Z, 7), ('Thigh_L', X, 26), ('Knee_L', X, -20)],
              root=(-.10, -.12, .015)),
         pose(27, 'Knee retract', CORE + LEGS, source='rise_left', phase=.89,
              root=(-.10, -.12, .01)),
         pose(32, 'Replant and guard', BODY, root=(0, 0, 0))]},
]

reports = []
for move in MOVES:
    for keypose in move['poses']:
        if move['name'] == 'martial.slip_elbow_knee':
            frame = keypose['frame']
            support = []
            if frame == 14:
                support = ['Root', 'Thigh_L', 'Knee_L', 'Leg_L', 'Foot_L']
            elif frame in (20, 24, 27):
                support = ['Root', 'Thigh_R', 'Knee_R', 'Leg_R', 'Foot_R']
            if support:
                keypose['local_quaternions'].update(
                    {name: list(q) for name, q in sample('guard', .15, support).items()})
    authored = ef.author_motion(rig, move['poses'], analysis,
                                move['name'] + '.authored')
    authored_action = bpy.data.actions[authored['action']]
    assign(authored_action)
    key_counts = {name: len({round(k.co.x, 3) for fc in ef._bone_fcurves(rig, name)
                             for k in fc.keyframe_points}) for name in BODY}
    refined = ef.refine_motion_auto(
        rig, move['contacts'], anchors=move['impacts'],
        overrides={'driver': 'Root'}, name=move['name'],
        settings={'strength': .26, 'frequency_hz': 7.0, 'damping': .92,
                  'centrifugal_gain': .32, 'translation_gain': .16,
                  'max_offset_degrees': 5.0, 'sample_step': .5,
                  'max_step_degrees': 85.0})
    reports.append({'name': move['name'], 'authored': authored['action'],
                    'refined': refined['action'], 'impacts': move['impacts'],
                    'contacts': move['contacts'], 'lateral_results': authored['lateral_results'],
                    'authored_joint_key_counts': key_counts,
                    'validation': refined['validation'],
                    'joint_coverage': refined['joint_coverage'],
                    'warnings': refined['warnings']})

assign(bpy.data.actions[MOVES[0]['name']])
scene.frame_start, scene.frame_end = 1, MOVES[0]['end']
scene.frame_set(MOVES[0]['impacts'][1])
output = ROOT / 'sampleRig/Lizardman_Martial_Arts_Test.blend'
bpy.ops.wm.save_as_mainfile(filepath=str(output))
(ROOT / 'sampleRig/lizardman_martial_arts_report.json').write_text(
    json.dumps(reports, indent=2), encoding='utf-8')
print('LIZARDMAN_MARTIAL_ARTS_OK', output)
