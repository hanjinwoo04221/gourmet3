"""Integration tests in a disposable Blender process, not the user's open scene."""
import bpy
import math
import importlib.util
from pathlib import Path
from mathutils import Quaternion, Vector

spec=importlib.util.spec_from_file_location('ef',Path(__file__).with_name('epicfight_ai_anim.py'))
ef=importlib.util.module_from_spec(spec);spec.loader.exec_module(ef)
bpy.ops.object.armature_add()
rig=bpy.context.object
bpy.ops.object.mode_set(mode='EDIT')
root=rig.data.edit_bones[0];root.name='pivot'
for name,parent,x in [('mass','pivot',0),('support','pivot',1),('free','mass',-1)]:
    b=rig.data.edit_bones.new(name);b.head=(x,0,1);b.tail=(x,0,2);b.parent=rig.data.edit_bones[parent]
bpy.ops.object.mode_set(mode='OBJECT')
for b in rig.pose.bones:b.rotation_mode='QUATERNION'
for f,angle in [(1,0),(8,.6),(17,1.8),(30,2.4)]:
    for n in ['pivot','mass','support','free']:
        b=rig.pose.bones[n];b.rotation_quaternion=Quaternion((0,0,1) if n=='pivot' else (1,0,0),angle*(1 if n=='pivot' else .3))
        b.keyframe_insert(data_path='rotation_quaternion',frame=f)
source=rig.animation_data.action
slot=rig.animation_data.action_slot
profile={'driver':'pivot','masses':{'mass':3,'free':1},'exclude':[]}
plan={'bones':['mass','support','free'],'contacts':[{'bone':'support','start':4,'end':22}],
      'anchor_frames':[8.25,17], 'settings':{'max_step_degrees':90}}

def restore():
    rig.animation_data.action=source;rig.animation_data.action_slot=source.slots[slot.identifier]

def snapshot(action):
    return [(f.data_path,f.array_index,[(tuple(k.co),k.interpolation) for k in f.keyframe_points])
            for f in ef._iter_fcurves(action,rig)]

before=snapshot(source)
timed=ef.retime_action_by_beats(rig,[(1,1),(8,10),(17,13),(30,33)],name='Reference_Beats_Test')
assert timed['keys_retimed']>0 and rig.animation_data.action.name=='Reference_Beats_Test'
assert snapshot(source)==before
assert abs(rig.animation_data.action.frame_range[1]-33)<1e-5
assert any(abs(k.co.x-13)<1e-5 for fc in ef._iter_fcurves(rig.animation_data.action,rig)
           for k in fc.keyframe_points)
restore()
articulation=ef.articulate_joint_phases(rig,
    {'free':{'swing_axis':(1,0,0),'spread_axis':(0,1,0)}},
    {'free':[(1,0,0),(8,12,8),(17,-5,4),(30,0,0)]},[],name='Articulation_Test')
assert articulation['bones']==['free'] and snapshot(source)==before
assert len(list(ef._bone_fcurves(rig,'free')))>0
articulated_action=rig.animation_data.action
rig.animation_data.action=source;rig.animation_data.action_slot=source.slots[slot.identifier]
bpy.context.scene.frame_set(8);baseline=rig.pose.bones['free'].rotation_quaternion.copy()
rig.animation_data.action=articulated_action
rig.animation_data.action_slot=articulated_action.slots[slot.identifier]
bpy.context.scene.frame_set(8)
assert abs(baseline.dot(rig.pose.bones['free'].rotation_quaternion))<.99999
restore()
balanced=ef.articulate_joint_phases(rig,
    {'mass':{'swing_axis':(1,0,0),'spread_axis':(0,1,0)},
     'free':{'swing_axis':(1,0,0),'spread_axis':(0,1,0)}},
    {n:[(1,0,0),(8,10,6),(17,10,6),(30,0,0)] for n in ('mass','free')},
    [{'bone':'support','start':4,'end':22}],name='Balanced_Limbs_Test',
    balance_groups=[{'left':['mass'],'right':['free'],
                     'left_anchor':'support','right_anchor':'free',
                     'masses':{'mass':3,'free':1},'gain':.4,
                     'sync_windows':[(16,18)]}])
at8=balanced['balance_factors'][next(k for k in balanced['balance_factors'] if float(k)==8)]
at17=balanced['balance_factors'][next(k for k in balanced['balance_factors'] if float(k)==17)]
assert at8['mass'] != at8['free']
assert at17['mass'] == at17['free'] == 1
assert snapshot(source)==before
restore()
try:ef.articulate_joint_phases(rig,{'free':{'swing_axis':(1,0,0),'spread_axis':(1,0,0)}},
                              {'free':[(1,0,0),(30,0,0)]},[])
except ValueError:pass
else:raise AssertionError('Parallel articulation axes accepted')
assert rig.animation_data.action==source
for bad_beats in [[(1,1),(8,8)],[(1,1),(17,12),(8,20),(30,33)],[(1,1),(30,float('nan'))]]:
    count=len(bpy.data.actions)
    try:ef.retime_action_by_beats(rig,bad_beats)
    except ValueError:pass
    else:raise AssertionError('Invalid timing beats accepted')
    assert len(bpy.data.actions)==count and rig.animation_data.action==source
bpy.context.scene.frame_set(5,subframe=.25)
report=ef.refine_motion(rig,plan,profile)
assert report['modified_bones']==['mass','free']
assert report['validation']['contact_error']<1e-5
assert bpy.context.scene.frame_current==5 and abs(bpy.context.scene.frame_subframe-.25)<1e-5
refined=rig.animation_data.action
def pose_at(action,frame):
    rig.animation_data.action=action
    rig.animation_data.action_slot=action.slots[slot.identifier]
    bpy.context.scene.frame_set(math.floor(frame),subframe=frame-math.floor(frame))
    return rig.pose.bones['free'].rotation_quaternion.normalized()
for frame in [1,8.25,17,30]:
    a=pose_at(source,frame);b=pose_at(refined,frame)
    assert abs(a.dot(b))>1-1e-6,('anchor changed',frame)
assert abs(pose_at(source,12).dot(pose_at(refined,12)))<1-1e-7,'No refinement occurred'
restore();assert snapshot(source)==before
# COM weights are relative, missing names must not silently bias the estimate.
a=ef.evaluated_center_of_mass(rig,profile['masses'])
b=ef.evaluated_center_of_mass(rig,{n:w*10 for n,w in profile['masses'].items()})
assert (a-b).length<1e-6
for bad in [dict(plan,contacts=[{'bone':'absent','start':1,'end':2}]),
            dict(plan,settings={'strength':float('nan')}),dict(plan,anchor_frames=[99])]:
    count=len(bpy.data.actions)
    try:ef.refine_motion(rig,bad,profile)
    except ValueError:pass
    else:raise AssertionError('Invalid input accepted')
    assert rig.animation_data.action==source and len(bpy.data.actions)==count
# Failure AFTER copy creation must roll back and remove the copy.
count=len(bpy.data.actions)
try:ef.refine_motion(rig,dict(plan,settings={'max_step_degrees':.00001}),profile)
except ValueError:pass
else:raise AssertionError('Validation failure not raised')
assert rig.animation_data.action==source and len(bpy.data.actions)==count
# Bounds fail before committing rather than creating a clipped pose.
try:ef.refine_motion(rig,plan,dict(profile,limits={'free':[[-.01,.01]]*3}))
except ValueError:pass
else:raise AssertionError('Joint limit failure not raised')
assert rig.animation_data.action==source
# Euler channels and non-integer boundaries.
rig.pose.bones['free'].rotation_mode='XYZ'
for f,x in [(1,0),(8.25,.3),(17,.7),(30,.1)]:
    rig.pose.bones['free'].rotation_euler=(x,0,0)
    rig.pose.bones['free'].keyframe_insert(data_path='rotation_euler',frame=f)
report=ef.refine_motion(rig,plan,profile)
assert report['validation']['contact_error']<1e-5
restore()
assert ef.inspect_motion_rig(rig)['action']==source.name
# Translational and centrifugal terms must affect the solved pose, beyond spring lag.
quiet=dict(plan,settings={'strength':.6,'frequency_hz':4.,'centrifugal_gain':0.,
                          'translation_gain':0.,'max_step_degrees':90.})
rotation_only=dict(plan,settings={'strength':.6,'frequency_hz':4.,'centrifugal_gain':.8,
                                  'translation_gain':0.,'max_step_degrees':90.})
translation_only=dict(plan,settings={'strength':.6,'frequency_hz':4.,'centrifugal_gain':0.,
                                     'translation_gain':.35,'max_step_degrees':90.})
ef.refine_motion(rig,quiet,profile);quiet_action=rig.animation_data.action
quiet_pose=pose_at(quiet_action,12)
restore()
ef.refine_motion(rig,rotation_only,profile);rotation_action=rig.animation_data.action
rotation_pose=pose_at(rotation_action,12)
restore()
ef.refine_motion(rig,translation_only,profile);translation_action=rig.animation_data.action
translation_pose=pose_at(translation_action,12)
assert abs(quiet_pose.dot(rotation_pose))<1-1e-7,'Centrifugal term had no effect'
assert abs(quiet_pose.dot(translation_pose))<1-1e-7,'COM acceleration term had no effect'
restore()
# Geometry-only analysis and direction authoring work with meaningless names.
analysis=ef.analyze_rig(rig,{'driver':'pivot','include':['pivot','mass','support','free']})
authored=ef.author_motion(rig,[
    {'frame':1.5,'directions':{'free':[0,1,0]},'marker':'ready'},
    {'frame':9.25,'directions':{'free':[1,0,0]},'marker':'strike'}],analysis,'OpaqueRig_Motion')
assert authored['action']=='OpaqueRig_Motion' and authored['frames']==[1.5,9.25]
pb=rig.pose.bones['free'];bpy.context.scene.frame_set(9,subframe=.25)
assert (pb.tail-pb.head).normalized().dot(Vector((1,0,0)))>.999
restore()
# Reference clips share endpoints but leave unchanging joints without every
# intermediate breakdown. Only explicitly posed joints receive middle keys.
sparse=ef.author_motion(rig,[
    {'frame':1,'local_quaternions':{'mass':[1,0,0,0],'free':[1,0,0,0]}},
    {'frame':5,'local_quaternions':{'free':list(Quaternion((0,0,1),.5))}},
    {'frame':9,'local_quaternions':{'mass':[1,0,0,0],
                                   'free':list(Quaternion((0,0,1),1.0))}}],
    analysis,'Sparse_Joint_Timing')
key_times=lambda bone: sorted({round(k.co.x,4) for fc in ef._bone_fcurves(rig,bone)
                               for k in fc.keyframe_points})
assert key_times('mass')==[1,9] and key_times('free')==[1,5,9]
restore()
# Inactive slots must not be modified in Blender 4.4+ actions.
other=source.slots.new('OBJECT','unrelated')
strip=source.layers[0].strips[0]
bag=strip.channelbag(other,ensure=True)
fc=bag.fcurves.new('location',index=0)
fc.keyframe_points.insert(1,123)
report=ef.refine_motion(rig,plan,profile)
copy=rig.animation_data.action
other_bag=copy.layers[0].strips[0].channelbag(copy.slots[other.identifier])
assert other_bag.fcurves[0].keyframe_points[0].co.y==123
restore()
# Explicit no-contact and full-protection branches both work.
report=ef.refine_motion(rig,dict(plan,contacts=[]),profile)
assert 'support' in report['modified_bones']
restore()
report=ef.refine_motion(rig,dict(plan,bones=['support']),profile)
assert report['modified_bones']==[] and report['warnings']
restore()
# A lateral reach must solve the rig's actual local axis, not a named Y axis.
bpy.ops.object.armature_add()
reach_rig=bpy.context.object
bpy.ops.object.mode_set(mode='EDIT')
joint=reach_rig.data.edit_bones[0];joint.name='alpha';joint.head=(0,0,1);joint.tail=(0,0,1.3)
tip=reach_rig.data.edit_bones.new('omega');tip.head=(0,0,1.3);tip.tail=(0,0,1.7);tip.parent=joint
bpy.ops.object.mode_set(mode='OBJECT')
reach_rig.pose.bones['alpha'].rotation_mode='XZY'
neutral=list(Quaternion())
authored=ef.author_motion(reach_rig,[
    {'frame':1,'local_quaternions':{'alpha':neutral}},
    {'frame':9,'local_quaternions':{'alpha':neutral},
     'lateral_targets':{'alpha':{'effector':'omega','direction':[1,0,0],
                                 'distance':.2,'max_degrees':35}}}],name='Opaque_Lateral')
assert abs(authored['lateral_results'][0]['achieved']-.2)<.01
bpy.context.scene.frame_set(1);first=reach_rig.pose.bones['omega'].tail.copy()
bpy.context.scene.frame_set(9);last=reach_rig.pose.bones['omega'].tail.copy()
assert abs((last-first).x-.2)<.01
try:
    ef.author_motion(reach_rig,[{'frame':1,'lateral_targets':{'alpha':{
        'effector':'alpha','direction':[1,0,0],'distance':.1}}}],name='Invalid_Lateral')
except ValueError:pass
else:raise AssertionError('Invalid lateral chain accepted')
# An animated leaf beside a longer sibling is included as a deforming joint helper.
bpy.ops.object.armature_add()
helper_rig=bpy.context.object
bpy.ops.object.mode_set(mode='EDIT')
base=helper_rig.data.edit_bones[0];base.name='base';base.head=(0,0,0);base.tail=(0,0,.2)
parent=helper_rig.data.edit_bones.new('parent');parent.head=(0,0,.2);parent.tail=(0,0,.5);parent.parent=base
chain=helper_rig.data.edit_bones.new('chain');chain.head=(0,0,.5);chain.tail=(0,0,.8);chain.parent=parent
tip=helper_rig.data.edit_bones.new('tip');tip.head=(0,0,.8);tip.tail=(0,0,1.);tip.parent=chain
helper=helper_rig.data.edit_bones.new('helper');helper.head=chain.head;helper.tail=(.15,0,.65);helper.parent=parent
bpy.ops.object.mode_set(mode='OBJECT')
for bone in helper_rig.pose.bones:bone.rotation_mode='QUATERNION'
for frame,angle in [(1,0),(8,.7),(16,-.4),(24,0)]:
    for bone in helper_rig.pose.bones:
        bone.rotation_quaternion=Quaternion((0,0,1),angle if bone.name=='parent' else 0)
        bone.keyframe_insert(data_path='rotation_quaternion',frame=frame)
draft=ef.build_auto_refinement(helper_rig,{'driver':'base'},contacts=[])
assert 'helper' in draft['plan']['joint_helpers']
helper_report=ef.refine_motion_auto(helper_rig,[],overrides={'driver':'base'})
assert 'helper' in helper_report['modified_bones']
assert helper_report['joint_coverage']['active_samples_by_bone']['helper']>0
print('PASS: anchors, COM and centrifugal terms, helper participation, arbitrary rig names, contacts, rollback, and calibrated lateral reach')
