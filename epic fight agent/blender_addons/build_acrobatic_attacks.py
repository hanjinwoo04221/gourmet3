"""Abstract-prompt test: author three acrobatic attacks, then refine via the v1 API.

Run inside Blender. Choreography is deliberately separate from refinement rules.
"""
import bpy
import math
import json
import importlib.util
from pathlib import Path
from mathutils import Quaternion, Euler, Vector, Matrix

BASE = Path(bpy.data.filepath).parent
spec = importlib.util.spec_from_file_location('efai', BASE/'blender_addons/epicfight_ai_anim.py')
ef = importlib.util.module_from_spec(spec); spec.loader.exec_module(ef)
rig = bpy.data.objects['Armature']
scene = bpy.context.scene

BODY = ['Root','Torso','Chest','Head','Shoulder_R','Arm_R','Hand_R',
        'Shoulder_L','Arm_L','Hand_L','Thigh_R','Leg_R','Thigh_L','Leg_L']
PROFILE = {
    'driver':'Root',
    'masses':{'Torso':.28,'Chest':.25,'Head':.08,'Thigh_R':.10,'Thigh_L':.10,
              'Leg_R':.04,'Leg_L':.04,'Arm_R':.035,'Arm_L':.035,
              'Hand_R':.02,'Hand_L':.02},
    'exclude':['Root','Tool_R','Tool_L','Knee_R','Knee_L','Elbow_R','Elbow_L'],
    'limits':{},
}

def q(x=0,y=0,z=0):
    return Euler(tuple(math.radians(v) for v in (x,y,z)),'XYZ').to_quaternion()

def reset_pose():
    for b in rig.pose.bones:
        b.matrix_basis=Matrix.Identity(4)

def make_action(name, beats, markers):
    action=bpy.data.actions.new(name+'_Authored');action.use_fake_user=True
    rig.animation_data.action=action
    previous={}
    for beat in beats:
        frame=beat['f'];scene.frame_set(frame);reset_pose()
        root=rig.pose.bones['Root']
        root.location=Vector(beat.get('loc',(0,0,0)))
        root.rotation_quaternion=q(*beat.get('root',(0,0,0)))
        for bone,angles in beat.get('pose',{}).items():
            rig.pose.bones[bone].rotation_quaternion=q(*angles)
        for bone in BODY:
            pb=rig.pose.bones[bone];quat=pb.rotation_quaternion.normalized()
            if bone in previous and previous[bone].dot(quat)<0:quat.negate();pb.rotation_quaternion=quat
            previous[bone]=quat.copy()
            pb.keyframe_insert(data_path='rotation_quaternion',frame=frame,group=bone)
            if bone=='Root':pb.keyframe_insert(data_path='location',frame=frame,group=bone)
    for fc in ef._iter_fcurves(action,rig):
        for kp in fc.keyframe_points:kp.interpolation='BEZIER';kp.handle_left_type=kp.handle_right_type='AUTO_CLAMPED'
    for label,frame in markers:
        action.pose_markers.new(label).frame=frame
    return action

def bake_clearance(action, flight=None):
    """Numerically map root translation channels to world Z, then bake clearance."""
    rig.animation_data.action=action
    if action.slots:rig.animation_data.action_slot=action.slots[0]
    start,end=action.frame_range
    frames=[start+i*.5 for i in range(round((end-start)*2)+1)]
    root=rig.pose.bones['Root']
    body=[rig.pose.bones[n] for n in BODY]
    for frame in frames:
        scene.frame_set(math.floor(frame),subframe=frame-math.floor(frame))
        phase=0.
        if flight and flight[0]<frame<flight[1]:
            phase=math.sin(math.pi*(frame-flight[0])/(flight[1]-flight[0]))
        desired=.015+.16*phase
        for _ in range(2):
            bpy.context.view_layer.update()
            lowest=min(min(b.head.z,b.tail.z) for b in body)
            error=desired-lowest
            if abs(error)<1e-5:break
            original=root.location.copy();eps=1e-3;derivatives=[]
            for axis in range(3):
                root.location=original.copy();root.location[axis]+=eps
                bpy.context.view_layer.update()
                shifted=min(min(b.head.z,b.tail.z) for b in body)
                derivatives.append((shifted-lowest)/eps)
            axis=max(range(3),key=lambda i:abs(derivatives[i]))
            root.location=original
            if abs(derivatives[axis])<1e-5:raise ValueError('Cannot map root translation to world Z')
            root.location[axis]+=error/derivatives[axis]
            bpy.context.view_layer.update()
        root.keyframe_insert(data_path='location',frame=frame,group='Root')
    for fc in ef._bone_fcurves(rig,'Root','location'):
        for kp in fc.keyframe_points:kp.interpolation='LINEAR'

def lift_free_endpoints(action, contacts, floor=.012):
    """Resolve small floor penetrations on non-contact distal bones only."""
    rig.animation_data.action=action
    if action.slots:rig.animation_data.action_slot=action.slots[0]
    start,end=action.frame_range
    contact_at=lambda name,f:any(c['bone']==name and c['start']<=f<=c['end'] for c in contacts)
    previous={}
    for i in range(round((end-start)*2)+1):
        frame=start+i*.5;scene.frame_set(math.floor(frame),subframe=frame-math.floor(frame))
        for name in ('Leg_R','Leg_L','Hand_R','Hand_L'):
            if contact_at(name,frame):continue
            pb=rig.pose.bones[name]
            if pb.tail.z>=floor:continue
            head=pb.head.copy();direction=(pb.tail-head).normalized()
            z=max(-1.,min(1.,(floor-head.z)/pb.bone.length))
            horizontal=Vector((direction.x,direction.y,0))
            if horizontal.length<1e-6:horizontal=Vector((1,0,0))
            target=horizontal.normalized()*math.sqrt(max(0.,1-z*z));target.z=z
            world_q=direction.rotation_difference(target) @ pb.matrix.to_quaternion()
            pb.matrix=Matrix.Translation(head) @ world_q.to_matrix().to_4x4()
            quat=pb.rotation_quaternion.normalized()
            if name in previous and previous[name].dot(quat)<0:quat.negate();pb.rotation_quaternion=quat
            previous[name]=quat.copy()
            pb.keyframe_insert(data_path='rotation_quaternion',frame=frame,group=name)
    for name in ('Leg_R','Leg_L','Hand_R','Hand_L'):
        for fc in ef._bone_fcurves(rig,name,'rotation_quaternion'):
            for kp in fc.keyframe_points:kp.interpolation='LINEAR'

ATTACKS = [
dict(name='Acro_Tornado_Heel_Kick', impact=52, end=92,
 flight=(26,76),
 contacts=[{'bone':'Leg_L','start':1,'end':25}],
 beats=[
  dict(f=1, pose={'Torso':qv if False else (5,0,-8),'Thigh_R':(-18,0,8),'Leg_R':(35,0,0),'Arm_R':(-25,0,-35),'Arm_L':(-10,0,30)}),
  dict(f=16,loc=(0,-.05,-.05),root=(0,-35,0),pose={'Torso':(18,0,18),'Chest':(5,0,18),'Thigh_R':(-52,0,20),'Leg_R':(82,0,0),'Arm_R':(-55,0,-55),'Arm_L':(-35,0,45)}),
  dict(f=28,loc=(0,-.16,.28),root=(0,-105,0),pose={'Torso':(-8,0,25),'Chest':(0,0,20),'Thigh_R':(-35,0,45),'Leg_R':(55,0,0),'Thigh_L':(18,0,-30),'Leg_L':(28,0,0),'Arm_R':(-80,0,-70),'Arm_L':(-65,0,65)}),
  dict(f=43,loc=(0,-.34,.48),root=(0,-205,0),pose={'Torso':(-18,0,20),'Chest':(0,0,25),'Thigh_R':(-15,0,82),'Leg_R':(18,0,0),'Thigh_L':(30,0,-30),'Leg_L':(38,0,0),'Arm_R':(-95,0,-70),'Arm_L':(-85,0,70)}),
  dict(f=52,loc=(0,-.48,.38),root=(0,-275,0),pose={'Torso':(-12,0,-18),'Chest':(0,0,-18),'Head':(0,0,18),'Thigh_R':(-5,0,112),'Leg_R':(5,0,0),'Thigh_L':(25,0,-42),'Leg_L':(45,0,0),'Arm_R':(-75,0,-92),'Arm_L':(-70,0,82)}),
  dict(f=64,loc=(0,-.60,.16),root=(0,-335,0),pose={'Torso':(12,0,-18),'Thigh_R':(18,0,38),'Leg_R':(52,0,0),'Thigh_L':(-18,0,-10),'Leg_L':(30,0,0),'Arm_R':(-35,0,-45),'Arm_L':(-30,0,40)}),
  dict(f=76,loc=(0,-.68,0),root=(0,-360,0),pose={'Torso':(18,0,5),'Thigh_R':(-28,0,8),'Leg_R':(55,0,0),'Thigh_L':(10,0,-5),'Leg_L':(18,0,0),'Arm_R':(-18,0,-25),'Arm_L':(-10,0,20)}),
  dict(f=92,loc=(0,-.66,0),root=(0,-360,0),pose={'Torso':(4,0,0),'Thigh_R':(-10,0,3),'Leg_R':(22,0,0),'Arm_R':(-12,0,-15),'Arm_L':(-8,0,12)})],
 markers=[('Load',16),('Takeoff',28),('Heel impact',52),('Land',76),('Recover',92)]),
dict(name='Acro_OneHand_Cartwheel_Axe', impact=58, end=104,
 flight=None,
 contacts=[],
 beats=[
  dict(f=1,pose={'Torso':(5,0,8),'Arm_L':(-15,0,30),'Arm_R':(-15,0,-25),'Thigh_L':(-12,0,-8)}),
  dict(f=18,loc=(.05,-.08,-.10),root=(5,22,0),pose={'Torso':(48,0,-20),'Chest':(12,0,-15),'Arm_L':(-105,0,55),'Arm_R':(-65,0,-45),'Thigh_R':(-30,0,25),'Leg_R':(48,0,0)}),
  dict(f=34,loc=(.12,-.20,-.18),root=(42,72,0),pose={'Torso':(88,0,-20),'Chest':(18,0,-15),'Head':(-25,0,15),'Arm_L':(-145,0,28),'Hand_L':(75,0,0),'Arm_R':(-110,0,-55),'Thigh_R':(-35,0,88),'Leg_R':(12,0,0),'Thigh_L':(10,0,-75),'Leg_L':(15,0,0)}),
  dict(f=47,loc=(.20,-.28,-.12),root=(88,118,0),pose={'Torso':(82,0,18),'Chest':(12,0,20),'Arm_L':(-155,0,12),'Hand_L':(80,0,0),'Arm_R':(-125,0,-72),'Thigh_R':(-20,0,105),'Leg_R':(5,0,0),'Thigh_L':(10,0,-105),'Leg_L':(5,0,0)}),
  dict(f=58,loc=(.28,-.36,.05),root=(125,168,0),pose={'Torso':(55,0,18),'Chest':(8,0,20),'Head':(-20,0,-20),'Arm_L':(-130,0,10),'Arm_R':(-95,0,-65),'Thigh_R':(-8,0,20),'Leg_R':(8,0,0),'Thigh_L':(-65,0,-75),'Leg_L':(2,0,0)}),
  dict(f=72,loc=(.34,-.45,.02),root=(58,205,0),pose={'Torso':(40,0,-10),'Arm_L':(-80,0,30),'Arm_R':(-55,0,-35),'Thigh_L':(-42,0,-25),'Leg_L':(45,0,0),'Thigh_R':(-18,0,15),'Leg_R':(30,0,0)}),
  dict(f=88,loc=(.35,-.52,0),root=(10,220,0),pose={'Torso':(18,0,-8),'Arm_L':(-25,0,18),'Arm_R':(-20,0,-20),'Thigh_L':(-18,0,-8),'Leg_L':(30,0,0)}),
  dict(f=104,loc=(.35,-.52,0),root=(0,220,0),pose={'Torso':(4,0,0),'Arm_L':(-8,0,10),'Arm_R':(-8,0,-10),'Thigh_L':(-8,0,-3),'Leg_L':(18,0,0)})],
 markers=[('Entry',18),('One-hand inversion',34),('Axe impact',58),('Land',88),('Recover',104)]),
dict(name='Acro_Butterfly_Twist_Backfist', impact=57, end=100,
 flight=(22,84),
 contacts=[{'bone':'Leg_R','start':1,'end':20}],
 beats=[
  dict(f=1,pose={'Torso':(8,0,-12),'Thigh_L':(-18,0,-10),'Leg_L':(30,0,0),'Arm_R':(-20,0,-40),'Arm_L':(-15,0,35)}),
  dict(f=15,loc=(0,-.08,-.10),root=(0,35,0),pose={'Torso':(42,0,-25),'Chest':(12,0,-15),'Thigh_L':(-45,0,-28),'Leg_L':(70,0,0),'Arm_R':(-65,0,-70),'Arm_L':(-45,0,55)}),
  dict(f=28,loc=(0,-.22,.18),root=(62,100,0),pose={'Torso':(72,0,10),'Chest':(20,0,18),'Head':(-20,0,-15),'Thigh_R':(-35,0,30),'Leg_R':(58,0,0),'Thigh_L':(-28,0,-42),'Leg_L':(45,0,0),'Arm_R':(-90,0,-88),'Arm_L':(-82,0,78)}),
  dict(f=43,loc=(0,-.38,.42),root=(98,205,0),pose={'Torso':(85,0,20),'Chest':(8,0,28),'Head':(-28,0,-20),'Thigh_R':(-20,0,48),'Leg_R':(48,0,0),'Thigh_L':(-15,0,-55),'Leg_L':(38,0,0),'Arm_R':(-78,0,-115),'Arm_L':(-75,0,105)}),
  dict(f=57,loc=(0,-.56,.34),root=(60,315,0),pose={'Torso':(55,0,-18),'Chest':(5,0,-35),'Head':(-15,0,25),'Shoulder_R':(0,0,-20),'Arm_R':(-35,0,-120),'Hand_R':(-70,0,0),'Arm_L':(-72,0,72),'Thigh_R':(-25,0,35),'Leg_R':(42,0,0),'Thigh_L':(-20,0,-35),'Leg_L':(45,0,0)}),
  dict(f=70,loc=(0,-.68,.12),root=(22,390,0),pose={'Torso':(30,0,-18),'Chest':(0,0,-20),'Arm_R':(-18,0,-55),'Hand_R':(-35,0,0),'Arm_L':(-35,0,35),'Thigh_R':(-32,0,12),'Leg_R':(65,0,0),'Thigh_L':(-15,0,-12),'Leg_L':(35,0,0)}),
  dict(f=84,loc=(0,-.74,0),root=(0,420,0),pose={'Torso':(18,0,8),'Arm_R':(-12,0,-25),'Arm_L':(-10,0,20),'Thigh_R':(-20,0,5),'Leg_R':(42,0,0)}),
  dict(f=100,loc=(0,-.73,0),root=(0,420,0),pose={'Torso':(4,0,0),'Arm_R':(-8,0,-12),'Arm_L':(-8,0,10),'Thigh_R':(-8,0,2),'Leg_R':(18,0,0)})],
 markers=[('Load',15),('Takeoff',28),('Twist',43),('Backfist impact',57),('Land',84),('Recover',100)])]

reports=[]
for spec_data in ATTACKS:
    authored=make_action(spec_data['name'],spec_data['beats'],spec_data['markers'])
    bake_clearance(authored,spec_data['flight'])
    plan={'name':spec_data['name'],'bones':[n for n in BODY if n!='Root'],
          'contacts':spec_data['contacts'],'anchor_frames':[spec_data['impact'],spec_data['end']],
          'holds':[], 'settings':{'strength':.42,'frequency_hz':5.5,'damping':.9,
          'centrifugal_gain':.55,'max_offset_degrees':9,'sample_step':.5,
          'max_step_degrees':55}}
    report=ef.refine_motion(rig,plan,PROFILE)
    lift_free_endpoints(rig.animation_data.action,spec_data['contacts'])
    report['authored']=authored.name;report['impact']=spec_data['impact']
    reports.append(report)

scene.frame_start=1;scene.frame_end=ATTACKS[-1]['end'];scene.frame_set(ATTACKS[-1]['impact'])
(BASE/'acrobatic_attacks_report.json').write_text(json.dumps(reports,indent=2),encoding='utf-8')
bpy.ops.wm.save_as_mainfile(filepath=str(BASE/'EpicFight Acrobatic Combat Pack.blend'))
result={'file':bpy.data.filepath,'actions':[r['action'] for r in reports],
        'validation':[r['validation'] for r in reports],'warnings':[r['warnings'] for r in reports]}
