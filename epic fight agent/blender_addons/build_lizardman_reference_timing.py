"""Build native-rig attack timing comparisons without mocap retargeting."""
import bpy
import importlib.util
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
spec = importlib.util.spec_from_file_location('ef', Path(__file__).with_name('epicfight_ai_anim.py'))
ef = importlib.util.module_from_spec(spec)
spec.loader.exec_module(ef)

bpy.ops.wm.open_mainfile(filepath=str(ROOT / 'sampleRig' / 'lizardman.blend'))
rig = next(o for o in bpy.data.objects if o.type == 'ARMATURE' and 'Leg_L' in o.pose.bones)
print('RIG', rig.name)
for suffix in ('claw_right', 'tail_slam', 'bite'):
    action = bpy.data.actions['animation.lizardman.' + suffix]
    rig.animation_data_create()
    rig.animation_data.action = action
    if action.slots:
        rig.animation_data.action_slot = action.slots[0]
    print('SOURCE', suffix, tuple(action.frame_range),
          sorted(set(round(k.co.x, 3) for fc in ef._iter_fcurves(action, rig)
                     for k in fc.keyframe_points)))

reports = []
def joint_profile(names):
    return {bone: {'swing_axis': (1, 0, 0), 'spread_axis': (0, 0, 1)}
            for bone in names}

def track(frames, load, impact, release):
    return [(frames[0],0,0),(frames[1],*load),(frames[2],*impact),
            (frames[3],*release),(frames[4],0,0)]

for suffix, name, source_events, target in [
    ('claw_right', 'Lizardman_Claw_Reference_Timing', (0, 2.16, 3.6, 6.48, 12),
     (1, 8, 12, 17, 29)),
    ('tail_slam', 'Lizardman_Tail_Reference_Timing', (0, 3.36, 5.04, 9.2, 15.6),
     (1, 8, 12, 20, 37)),
]:
    action = bpy.data.actions['animation.lizardman.' + suffix]
    rig.animation_data.action = action
    if action.slots:
        rig.animation_data.action_slot = action.slots[0]
    # Events chosen after native-key inspection and rendered pose review.
    beats = list(zip(source_events, target))
    result = ef.retime_action_by_beats(rig, beats, name=name)
    rig.animation_data.action.use_fake_user = True
    reports.append(result)
    for label, frame in [('load', target[1]), ('impact', target[2]), ('release', target[3])]:
        marker = rig.animation_data.action.pose_markers.new(label)
        marker.frame = frame

    if suffix == 'claw_right':
        moves = {
            'Shoulder_R': ((-8,-8),(13,14),(4,5)),
            'Arm_R': ((-7,-10),(10,13),(2,3)),
            'Hand_R': ((10,2),(-12,-3),(4,0)),
            'Elbow_R': ((10,2),(-12,-3),(4,0)),
            'Shoulder_L': ((5,7),(-5,-8),(-2,-3)),
            'Arm_L': ((3,4),(-4,-6),(-2,-2)),
            'Thigh_R': ((6,5),(-8,-6),(-2,-2)),
            'Leg_R': ((-8,0),(9,2),(3,0)),
            'Knee_R': ((-8,0),(9,2),(3,0)),
            'Thigh_L': ((-3,-3),(4,3),(1,1)),
            'Leg_L': ((4,0),(-4,0),(-1,0)),
            'Knee_L': ((4,0),(-4,0),(-1,0)),
        }
    else:
        moves = {
            'Shoulder_R': ((-6,-7),(9,10),(3,3)),
            'Arm_R': ((-5,-6),(8,9),(2,2)),
            'Hand_R': ((7,0),(-8,-2),(3,0)),
            'Elbow_R': ((7,0),(-8,-2),(3,0)),
            'Shoulder_L': ((5,7),(-8,-10),(-2,-3)),
            'Arm_L': ((3,5),(-7,-9),(-2,-2)),
            'Hand_L': ((-5,0),(8,2),(2,0)),
            'Elbow_L': ((-5,0),(8,2),(2,0)),
            'Thigh_R': ((7,6),(-9,-7),(-3,-2)),
            'Leg_R': ((-9,0),(10,2),(3,0)),
            'Knee_R': ((-9,0),(10,2),(3,0)),
            'Thigh_L': ((-3,-3),(4,3),(1,1)),
            'Leg_L': ((4,0),(-4,0),(-1,0)),
            'Knee_L': ((4,0),(-4,0),(-1,0)),
        }
    phase_tracks = {bone: track(target,*values) for bone,values in moves.items()}
    masses = {'Root':3,'Chest':2,'Thigh_L':1.5,'Thigh_R':1.5,
              'Arm_L':.6,'Arm_R':.6}
    balance_groups = [
        {'left':['Shoulder_L','Arm_L'],'right':['Shoulder_R','Arm_R'],
         'left_anchor':'Foot_L','right_anchor':'Foot_R','masses':masses,
         'gain':.4,'contact_bias':.3,
         'sync_windows':[(target[3],target[3]+1)]},
        {'left':['Thigh_L','Leg_L','Knee_L'],
         'right':['Thigh_R','Leg_R','Knee_R'],
         'left_anchor':'Foot_L','right_anchor':'Foot_R','masses':masses,
         'gain':.4,'contact_bias':.3},
    ]
    articulated = ef.articulate_joint_phases(
        rig,joint_profile(moves),phase_tracks,
        [{'bone':'Foot_L','start':target[0],'end':target[-1]}],
        name=name.replace('Reference_Timing','Balanced_Articulated'),
        balance_groups=balance_groups)
    rig.animation_data.action.use_fake_user = True
    reports.append(articulated)
    changed = rig.animation_data.action
    timed = bpy.data.actions[name]
    max_foot_error = 0.0
    angle_changes = {}
    for frame in range(target[0], target[-1]+1):
        rig.animation_data.action = timed
        if timed.slots: rig.animation_data.action_slot = timed.slots[0]
        bpy.context.scene.frame_set(frame)
        baseline_foot = (rig.matrix_world @ rig.pose.bones['Foot_L'].tail).copy()
        if frame == target[2]:
            baseline_rot = {bone: rig.pose.bones[bone].matrix_basis.to_quaternion().copy()
                            for bone in ('Shoulder_R','Hand_R','Thigh_R','Leg_R')}
        rig.animation_data.action = changed
        if changed.slots: rig.animation_data.action_slot = changed.slots[0]
        bpy.context.scene.frame_set(frame)
        max_foot_error = max(max_foot_error,
            ((rig.matrix_world @ rig.pose.bones['Foot_L'].tail)-baseline_foot).length)
        if frame == target[2]:
            for bone, prior in baseline_rot.items():
                current = rig.pose.bones[bone].matrix_basis.to_quaternion()
                angle_changes[bone] = round(prior.rotation_difference(current).angle * 57.29578, 2)
    reports.append({'action':changed.name,'support_foot_max_delta':max_foot_error,
                    'impact_joint_delta_degrees':angle_changes})

rig.animation_data.action = bpy.data.actions['Lizardman_Claw_Balanced_Articulated']
if rig.animation_data.action.slots:
    rig.animation_data.action_slot = rig.animation_data.action.slots[0]
bpy.context.scene.frame_start = 1
bpy.context.scene.frame_end = 37
bpy.context.scene.frame_set(1)
output = ROOT / 'sampleRig' / 'Lizardman_Reference_Timing_Test.blend'
bpy.ops.wm.save_as_mainfile(filepath=str(output))
(ROOT / 'sampleRig' / 'Lizardman_Reference_Timing_Report.json').write_text(
    json.dumps(reports, indent=2, ensure_ascii=False), encoding='utf-8')
print('SAVED', output)
