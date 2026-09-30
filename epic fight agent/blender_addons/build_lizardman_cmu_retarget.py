"""Retarget selected CMU boxing and front-kick capture windows to Lizardman."""
import importlib.util
import json
import math
from pathlib import Path

import bpy
from mathutils import Matrix, Quaternion, Vector

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location('ef', ROOT/'blender_addons/epicfight_ai_anim.py')
ef = importlib.util.module_from_spec(spec)
spec.loader.exec_module(ef)
scene = bpy.context.scene
rig = bpy.data.objects['Lizardman']
analysis = ef.analyze_rig(rig, {'driver': 'Root'})
ef.build_auto_refinement(rig, {'driver': 'Root'})

MAP = {
    'Torso':'LowerBack', 'Chest':'Spine', 'Head':'Head',
    'Shoulder_L':'LeftShoulder', 'Arm_L':'LeftArm',
    'Elbow_L':'LeftForeArm',
    'Shoulder_R':'RightShoulder', 'Arm_R':'RightArm',
    'Elbow_R':'RightForeArm',
    'Thigh_L':'LeftUpLeg', 'Knee_L':'LeftLeg',
    'Leg_L':'LeftLeg', 'Foot_L':'LeftFoot',
    'Thigh_R':'RightUpLeg', 'Knee_R':'RightLeg',
    'Leg_R':'RightLeg', 'Foot_R':'RightFoot',
}
CLIPS = [
    {'name':'mocap.boxing_two_strikes', 'file':'14_01.bvh',
     'first':1531, 'last':1711, 'anchors':[13,22],
     'contacts':[{'bone':'Foot_R','start':7,'end':15},
                 {'bone':'Foot_L','start':18,'end':28}]},
    {'name':'mocap.front_kick_right', 'file':'135_04.bvh',
     'first':311, 'last':461, 'anchors':[14],
     'contacts':[{'bone':'Foot_L','start':7,'end':24}]},
]


def assign(obj, action):
    obj.animation_data.action = action
    if action.slots:
        obj.animation_data.action_slot = action.slots[0]


def basis(side, up):
    up = up.normalized()
    side = (side - up * side.dot(up)).normalized()
    forward = side.cross(up).normalized()
    return Matrix((side, forward, up)).transposed()


assign(rig, bpy.data.actions['animation.lizardman.guard'])
scene.frame_set(2)
target_base = {name:(rig.pose.bones[name].tail-rig.pose.bones[name].head).normalized()
               for name in MAP}
target_side = rig.pose.bones['Shoulder_R'].head-rig.pose.bones['Shoulder_L'].head
target_up = rig.pose.bones['Head'].head-rig.pose.bones['Root'].head
target_basis = basis(target_side, target_up)
guard = {bone.name:list(bone.rotation_euler.to_quaternion().normalized())
         for bone in rig.pose.bones if bone.bone.use_deform and not bone.name.startswith('Tool')}
target_endpoints={name:rig.pose.bones[name].tail.copy()
                  for name in ('Hand_L','Hand_R','Foot_R')}
target_joint_origins={name:rig.pose.bones['Shoulder_'+name[-1] if name.startswith('Hand')
                                    else 'Root'].head.copy() for name in target_endpoints}
IK_PREVIOUS={}


def solve_endpoint(bone_name,joints,desired,frame,limit):
    """Bounded CCD-like coordinate descent for this rig's nonstandard chains."""
    scene.frame_set(frame)
    target=Vector(desired)
    starting={name:rig.pose.bones[name].rotation_euler.copy() for name in joints}
    for _ in range(18):
        endpoint=rig.pose.bones[bone_name].tail.copy()
        error=target-endpoint
        if error.length<.012:break
        for name in reversed(joints):
            bone=rig.pose.bones[name]
            for axis in range(3):
                old=bone.rotation_euler[axis]
                bone.rotation_euler[axis]=old+.01
                bpy.context.view_layer.update()
                gradient=(rig.pose.bones[bone_name].tail-endpoint)/.01
                bone.rotation_euler[axis]=old
                if gradient.length_squared<1e-8:continue
                step=.7*gradient.dot(error)/(gradient.length_squared+.02)
                step=max(-.10,min(.10,step))
                bound=starting[name][axis]
                bone.rotation_euler[axis]=max(bound-limit,min(bound+limit,old+step))
                bpy.context.view_layer.update()
                endpoint=rig.pose.bones[bone_name].tail.copy()
                error=target-endpoint
    for name in joints:
        bone=rig.pose.bones[name]
        q=bone.rotation_euler.to_quaternion().normalized()
        previous=IK_PREVIOUS.get(name)
        if previous is not None:
            if previous.dot(q)<0:q.negate()
            turn=2*math.acos(min(1,previous.dot(q)))
            maximum=math.radians(18)
            if turn>maximum:
                q=previous.slerp(q,maximum/turn)
                bone.rotation_euler=q.to_euler(bone.rotation_mode,bone.rotation_euler)
                bpy.context.view_layer.update()
        IK_PREVIOUS[name]=q.copy()
        bone.keyframe_insert(data_path='rotation_euler',frame=frame,group=name)
    return (rig.pose.bones[bone_name].tail-target).length

reports = []
for clip in CLIPS:
    IK_PREVIOUS.clear()
    bpy.ops.import_anim.bvh(filepath=str(ROOT/'sampleRig/mocap_reference'/clip['file']),
                            rotate_mode='QUATERNION')
    actor = bpy.context.object
    actor_action = actor.animation_data.action
    assign(actor, actor_action)
    scene.frame_set(clip['first'])
    bones = actor.pose.bones
    source_side = bones['RightArm'].head-bones['LeftArm'].head
    source_up = bones['Head'].head-bones['Hips'].head
    source_basis = basis(source_side, source_up)
    alignment = target_basis @ source_basis.transposed()
    src_initial = {}
    target_correction = {}
    for target_name, source_name in MAP.items():
        source_bone = bones[source_name]
        direction = source_bone.tail-source_bone.head
        if direction.length < 1e-5:
            continue
        initial = (alignment @ direction).normalized()
        src_initial[target_name] = initial
        target_correction[target_name] = initial.rotation_difference(target_base[target_name])
    source_hip = bones['Hips'].head.copy()
    source_endpoints={name:bones[source].head.copy() for name,source in
                      (('Hand_L','LeftHand'),('Hand_R','RightHand'),
                       ('Foot_R','RightFoot'))}
    source_reference={name:(bones['LeftArm' if name=='Hand_L' else
                                  'RightArm' if name=='Hand_R' else 'Hips'].head).copy()
                      for name in source_endpoints}
    source_width = (bones['RightArm'].head-bones['LeftArm'].head).length
    target_width = target_side.length
    scale = min(.09, target_width/source_width)
    keyposes = []
    endpoint_targets={}
    previous_direction = target_base.copy()
    for target_frame, source_frame in enumerate(range(clip['first'],clip['last']+1,5), 1):
        scene.frame_set(source_frame)
        directions = {}
        for target_name, source_name in MAP.items():
            if target_name not in target_correction:
                continue
            source_bone = bones[source_name]
            delta = source_bone.tail-source_bone.head
            if delta.length < 1e-5:
                continue
            direction = target_correction[target_name] @ ((alignment @ delta).normalized())
            baseline = target_base[target_name]
            allowed = math.radians(70 if target_name.startswith(('Elbow','Hand')) else 100)
            turn = baseline.rotation_difference(direction)
            if turn.angle > allowed:
                direction = Quaternion().slerp(turn, allowed/turn.angle) @ baseline
            step = previous_direction[target_name].rotation_difference(direction)
            max_turn = math.radians(12)
            if step.angle > max_turn:
                direction = (Quaternion().slerp(step,max_turn/step.angle)
                             @ previous_direction[target_name])
            previous_direction[target_name] = direction.copy()
            directions[target_name] = list(direction)
        hip_delta = alignment @ (bones['Hips'].head-source_hip) * scale
        # Preserve locomotion but bound capture drift for this compact rig.
        hip_delta.x = max(-.22,min(.22,hip_delta.x))
        hip_delta.y = max(-.22,min(.22,hip_delta.y))
        hip_delta.z = max(-.14,min(.20,hip_delta.z))
        endpoint_targets[target_frame]={}
        for name,source_name in (('Hand_L','LeftHand'),('Hand_R','RightHand'),
                                 ('Foot_R','RightFoot')):
            reference=bones['LeftArm' if name=='Hand_L' else
                            'RightArm' if name=='Hand_R' else 'Hips'].head
            relative=(bones[source_name].head-reference)
            original_relative=source_endpoints[name]-source_reference[name]
            target_length=(target_endpoints[name]-target_joint_origins[name]).length
            source_length=original_relative.length
            limb_scale=min(.095,max(.025,target_length/max(source_length,1e-6)))
            displacement=alignment @ (relative-original_relative)*limb_scale
            max_displacement=.48 if name.startswith('Hand') else .85
            if displacement.length>max_displacement:
                displacement.normalize();displacement*=max_displacement
            endpoint_targets[target_frame][name]=list(target_endpoints[name]+displacement+hip_delta)
        keypose={'frame':target_frame, 'directions':directions,
                 'translations':{'Root':list(hip_delta)}}
        if target_frame in (1, (clip['last']-clip['first'])//5+1):
            keypose['local_quaternions'] = guard
        if target_frame in clip['anchors']:
            keypose['marker'] = 'captured impact'
        keyposes.append(keypose)
    authored = ef.author_motion(rig,keyposes,analysis,clip['name']+'.authored')
    assign(rig,bpy.data.actions[authored['action']])
    ik_residual={name:[] for name in ('Hand_L','Hand_R','Foot_R')}
    for frame in range(1,len(keyposes)+1):
        for name,joints,limit in (
            ('Hand_L',['Shoulder_L','Arm_L'],.85),
            ('Hand_R',['Shoulder_R','Arm_R'],.85),
            ('Foot_R',['Thigh_R','Leg_R','Foot_R'],1.1)):
            if clip['name']=='mocap.boxing_two_strikes' and name=='Foot_R':continue
            if clip['name']=='mocap.front_kick_right' and name.startswith('Hand'):continue
            ik_residual[name].append(solve_endpoint(
                name,joints,endpoint_targets[frame][name],frame,limit))
    worst=(0,None,None)
    previous=None
    for half in range(2,2*len(keyposes)+1):
        frame=half*.5
        scene.frame_set(math.floor(frame),subframe=frame-math.floor(frame))
        current={name:rig.pose.bones[name].matrix.to_quaternion().normalized().copy()
                 for name in MAP}
        if previous:
            for name in MAP:
                angle=math.degrees(2*math.acos(min(1,abs(previous[name].dot(current[name])))))
                if angle>worst[0]:worst=(angle,frame,name)
        previous=current
    print('RETARGET_STEP',clip['name'],worst)
    # The capture is dense; before physics refinement keep each declared
    # support foot at its measured central world position by adjusting Root.
    contact_before={}
    for contact in clip['contacts']:
        center=(contact['start']+contact['end'])//2
        scene.frame_set(center)
        foot=rig.pose.bones[contact['bone']]
        fixed=(rig.matrix_world @ foot.tail).copy()
        contact_before[contact['bone']]=[]
        for frame in range(contact['start'],contact['end']+1):
            scene.frame_set(frame)
            foot=rig.pose.bones[contact['bone']]
            current=rig.matrix_world @ foot.tail
            contact_before[contact['bone']].append((current-fixed).length)
            correction=rig.matrix_world.inverted().to_3x3() @ (fixed-current)
            root_bone=rig.pose.bones['Root']
            root_bone.location += correction
            root_bone.keyframe_insert(data_path='location',frame=frame,group='Root')
    refined=ef.refine_motion_auto(
        rig,clip['contacts'],anchors=clip['anchors'],
        overrides={'driver':'Root'},name=clip['name'],settings={
            'strength':.18,'frequency_hz':7.0,'damping':.95,
            'centrifugal_gain':.20,'translation_gain':.10,
            'max_offset_degrees':3.5,'sample_step':.5,
            'max_step_degrees':85.0})
    reports.append({'action':refined['action'],'source':clip['file'],
                    'source_frames':[clip['first'],clip['last']],
                    'mapping':MAP,'mapped_bones':sorted(src_initial),
                    'scale':scale,'anchors':clip['anchors'],
                    'contacts':clip['contacts'],
                    'ik_residual_max':{name:max(values) if values else None
                                       for name,values in ik_residual.items()},
                    'prelock_contact_deviation':contact_before,
                    'validation':refined['validation'],
                    'joint_coverage':refined['joint_coverage'],
                    'warnings':refined['warnings']})
    bpy.data.objects.remove(actor,do_unlink=True)

assign(rig,bpy.data.actions[CLIPS[0]['name']])
scene.frame_start,scene.frame_end=1,37
scene.frame_set(13)
output=ROOT/'sampleRig/Lizardman_CMU_Mocap_Test.blend'
bpy.ops.wm.save_as_mainfile(filepath=str(output))
(ROOT/'sampleRig/lizardman_cmu_mocap_report.json').write_text(
    json.dumps(reports,indent=2),encoding='utf-8')
print('LIZARDMAN_CMU_MOCAP_OK',output)
