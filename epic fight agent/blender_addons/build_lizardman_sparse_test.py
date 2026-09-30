"""Make three Lizardman combat tests with joint-specific Epic Fight timing."""
import importlib.util
import json
import math
from pathlib import Path

import bpy
from mathutils import Quaternion

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location('ef', ROOT / 'blender_addons/epicfight_ai_anim.py')
ef = importlib.util.module_from_spec(spec)
spec.loader.exec_module(ef)
rig = bpy.data.objects['Lizardman']
scene = bpy.context.scene
analysis = ef.analyze_rig(rig, {'driver': 'Root'})
draft = ef.build_auto_refinement(rig, {'driver': 'Root'})
deform = [b.name for b in rig.data.bones if b.use_deform and not b.name.startswith('Tool')]
ARM_L = ['Shoulder_L', 'Arm_L', 'Elbow_L', 'Hand_L']
ARM_R = ['Shoulder_R', 'Arm_R', 'Elbow_R', 'Hand_R']
LEGS = ['Thigh_L', 'Knee_L', 'Leg_L', 'Foot_L',
        'Thigh_R', 'Knee_R', 'Leg_R', 'Foot_R']
AXIAL = ['Root', 'Torso', 'Chest', 'Head', 'Jaw']
TAIL = ['Tail1', 'Tail2', 'Tail3']


def assign(action):
    rig.animation_data.action = action
    if action.slots:
        rig.animation_data.action_slot = action.slots[0]


def source_pose(source, phase, frame, bones, marker, lateral=None):
    action = bpy.data.actions['animation.lizardman.' + source]
    assign(action)
    time = action.frame_range[0] + phase * (action.frame_range[1] - action.frame_range[0])
    scene.frame_set(math.floor(time), subframe=time - math.floor(time))
    rotations = {}
    for name in bones:
        bone = rig.pose.bones[name]
        q = (bone.rotation_quaternion if bone.rotation_mode == 'QUATERNION'
             else bone.rotation_euler.to_quaternion()).copy().normalized()
        # The source tail slam has a sharp distal whip. Bound its amplitude
        # before refinement while retaining the direction of each authored pose.
        if name == 'Tail2':
            q = Quaternion().slerp(q, .75)
        elif name == 'Tail3':
            q = Quaternion().slerp(q, .25)
        rotations[name] = list(q)
    result = {'frame': frame, 'marker': marker, 'local_quaternions': rotations}
    if lateral:
        side, distance = lateral
        result['lateral_targets'] = {'Shoulder_' + side: {
            'effector': 'Hand_' + side, 'direction': [1, 0, 0],
            'distance': distance, 'max_degrees': 25}}
    return result


# Rig-specific attack accents, in the inspected Lizardman local joint axes.
# These are deliberate strike poses rather than per-frame procedural noise.
ACCENTS = {
    ('test.hook_claw_bite', 8): {'Arm_L': -12, 'Elbow_L': 8},
    ('test.hook_claw_bite', 15): {'Arm_L': 28, 'Elbow_L': -16},
    ('test.tail_pivot_rake', 16): {'Torso': 13},
    ('test.tail_pivot_rake', 20): {'Arm_R': -14},
    ('test.tail_pivot_rake', 24): {'Arm_R': 30, 'Elbow_R': -14},
    ('test.low_pounce_double_rake', 19): {'Arm_L': 26, 'Elbow_L': -12},
    ('test.low_pounce_double_rake', 25): {'Arm_R': 28, 'Elbow_R': -12},
}
AIR_TRAVEL = {1: (0, 0, 0), 5: (0, 0, -.06), 8: (-.02, 0, 0),
              12: (-.08, 0, .18), 16: (-.14, 0, .30),
              17: (-.16, 0, .30), 19: (-.19, 0, .27),
              22: (-.23, 0, .23), 25: (-.27, 0, .18),
              29: (-.30, 0, .04), 35: (-.30, 0, 0)}


# Source actions supply actual Lizardman articulation. Each intermediate row
# changes only the named anatomical group; endpoints share the full-body pose.
MOVES = [
    {'name': 'test.hook_claw_bite', 'end': 31, 'impacts': [15, 23],
     'contacts': [{'bone': 'Foot_R', 'start': 13, 'end': 16}],
     'poses': [
         ('guard', .15, 1, deform, 'Guard', None),
         ('dodge', .43, 5, AXIAL + LEGS, 'Slip and load', None),
         ('claw_left', .25, 8, ARM_L + ['Chest'], 'Left hook chamber', ('L', .10)),
         ('tail_slam', .25, 10, TAIL + ['Torso'], 'Counterturn', None),
         ('claw_left', .66, 15, ARM_L + ['Chest', 'Torso'], 'Hook impact', ('L', -.12)),
         ('bite', .20, 19, AXIAL + ['Jaw'], 'Neck windup', None),
         ('bite', .67, 23, AXIAL + ['Jaw'], 'Bite impact', None),
         ('bite', .88, 26, ['Head', 'Jaw'] + TAIL, 'Release', None),
         ('idle', 0, 31, deform, 'Recover', None)]},
    {'name': 'test.tail_pivot_rake', 'end': 33, 'impacts': [16, 24],
     'contacts': [{'bone': 'Foot_L', 'start': 14, 'end': 17}],
     'poses': [
         ('guard', .12, 1, deform, 'Guard', None),
         ('dodge', .55, 5, AXIAL + LEGS, 'Outside step', None),
         ('tail_slam', .18, 8, TAIL + ['Torso'], 'Tail coil', None),
         ('tail_slam', .47, 12, TAIL + ['Torso', 'Chest'], 'Pivot', None),
         ('tail_slam', .72, 16, TAIL + ['Torso', 'Chest'], 'Tail strike', None),
         ('claw_right', .22, 20, ARM_R + ['Chest'], 'Rake chamber', ('R', -.10)),
         ('claw_right', .68, 24, ARM_R + ['Chest'], 'Rake impact', ('R', .12)),
         ('guard', .55, 28, AXIAL + ARM_R, 'Counterbalance', None),
         ('idle', 0, 33, deform, 'Recover', None)]},
    {'name': 'test.low_pounce_double_rake', 'end': 35, 'impacts': [19, 25],
     'contacts': [],  # airborne attack: no asserted planted support
     'poses': [
         ('idle', 0, 1, deform, 'Stalk', None),
         ('leap_charge', .45, 5, AXIAL + LEGS, 'Compress', None),
         ('leap_charge', .86, 8, AXIAL + LEGS, 'Push off', None),
         ('leap', .30, 12, AXIAL + LEGS + TAIL, 'Launch', None),
         ('leap', .60, 16, AXIAL + LEGS, 'Airborne', None),
         ('flurry_left', .23, 17, ARM_L + ['Chest'], 'First rake windup', ('L', .10)),
         ('flurry_left', .70, 19, ARM_L + ['Chest'], 'First rake', ('L', -.12)),
         ('flurry_right', .24, 22, ARM_R + ['Chest'], 'Second rake windup', ('R', -.10)),
         ('flurry_right', .72, 25, ARM_R + ['Chest'], 'Second rake', ('R', .12)),
         ('leap', .90, 29, AXIAL + LEGS + TAIL, 'Land', None),
         ('idle', 0, 35, deform, 'Recover', None)]},
]

reports = []
for move in MOVES:
    poses = [source_pose(*row) for row in move['poses']]
    for pose in poses:
        frame = pose['frame']
        for bone, angle in ACCENTS.get((move['name'], frame), {}).items():
            base = Quaternion(pose['local_quaternions'][bone])
            pose['local_quaternions'][bone] = list(
                (base @ Quaternion((1, 0, 0), math.radians(angle))).normalized())
        if move['name'] == 'test.low_pounce_double_rake' and frame in AIR_TRAVEL:
            pose['translations'] = {'Root': AIR_TRAVEL[frame]}
    authored = ef.author_motion(rig, poses, analysis, move['name'] + '.authored')
    authored_joint_key_counts = {}
    for bone in deform:
        authored_joint_key_counts[bone] = len({round(k.co.x, 3)
                                                  for fc in ef._bone_fcurves(rig, bone)
                                                  for k in fc.keyframe_points})
    refined = ef.refine_motion_auto(
        rig, move['contacts'], overrides={'driver': 'Root'},
        anchors=move['impacts'], name=move['name'],
        settings={'strength': .25, 'frequency_hz': 7.0, 'damping': .95,
                  'centrifugal_gain': .30, 'translation_gain': .18,
                  'max_offset_degrees': 5.0, 'sample_step': .5,
                  'max_step_degrees': 85.0})
    reports.append({'name': move['name'], 'authored': authored['action'],
                    'refined': refined['action'], 'contacts': move['contacts'],
                    'impacts': move['impacts'],
                    'lateral_results': authored['lateral_results'],
                    'authored_joint_key_counts': authored_joint_key_counts,
                    'validation': refined['validation'],
                    'joint_coverage': refined.get('joint_coverage'),
                    'warnings': refined['warnings']})

assign(bpy.data.actions[MOVES[0]['name']])
scene.frame_start, scene.frame_end = 1, MOVES[0]['end']
scene.frame_set(MOVES[0]['impacts'][0])
output = ROOT / 'sampleRig/Lizardman_EpicFight_Timing_Test.blend'
bpy.ops.wm.save_as_mainfile(filepath=str(output))
(ROOT / 'sampleRig/lizardman_epicfight_timing_report.json').write_text(
    json.dumps(reports, indent=2, default=str), encoding='utf-8')
print('LIZARDMAN_EPICFIGHT_TIMING_OK', output)
