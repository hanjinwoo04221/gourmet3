"""Exercise shared COM, centrifugal and all-eligible-joint refinement defaults."""
import bpy,importlib.util,json,math
from pathlib import Path
from mathutils import Quaternion,Vector

ROOT=Path(__file__).resolve().parent.parent
spec=importlib.util.spec_from_file_location('ef',ROOT/'blender_addons/epicfight_ai_anim.py')
ef=importlib.util.module_from_spec(spec);spec.loader.exec_module(ef)
rig=bpy.data.objects['Lizardman'];scene=bpy.context.scene
analysis=ef.analyze_rig(rig,{'driver':'Root'})
ef.inspect_motion_rig(rig);ef.build_auto_refinement(rig,{'driver':'Root'})
body=[b.name for b in rig.data.bones if b.use_deform and not b.name.startswith('Tool')]

def assign(action):
    rig.animation_data.action=action
    if action.slots:rig.animation_data.action_slot=action.slots[0]

def pose(source_name,phase,frame,marker,left=0.,right=0.,accents=None,root_location=None):
    action=bpy.data.actions[source_name];assign(action)
    t=action.frame_range[0]+(action.frame_range[1]-action.frame_range[0])*phase
    scene.frame_set(math.floor(t),subframe=t-math.floor(t))
    values={};accents=accents or {}
    for n in body:
        pb=rig.pose.bones[n]
        q=(pb.rotation_quaternion if pb.rotation_mode=='QUATERNION' else pb.rotation_euler.to_quaternion()).copy().normalized()
        if n in accents:q=(q@Quaternion(Vector((1,0,0)),math.radians(accents[n]))).normalized()
        values[n]=list(q)
    targets={}
    for side,distance in (('L',left),('R',right)):
        if distance:
            targets['Shoulder_'+side]={'effector':'Hand_'+side,
                'direction':[1.,0.,0.],'distance':distance,'max_degrees':35.}
    for side in ('L','R'):
        if 'Arm_'+side in accents:
            reach=.045 if any(word in marker.lower() for word in ('windup','coil','load')) else -.045
            targets['Arm_'+side]={'effector':'Hand_'+side,
                'direction':[0.,1.,0.],'distance':reach,'max_degrees':35.}
    return {'frame':frame,'marker':marker,'local_quaternions':values,
            'translations':{'Root':root_location or (0.,0.,0.)},'lateral_targets':targets}

PHRASES=[
 {'name':'lizardman.inertial_leap_cross_claws','end':39,'impacts':[24,30],
  'preview_frames':[1,13,17,24,39],
  'travel':{1:(0,0,0),5:(-.08,0,0),9:(-.16,0,.07),13:(-.22,0,.20),
            17:(-.25,0,.28),21:(-.20,0,.20),24:(-.14,0,.12),
            27:(-.08,0,.06),30:(0,0,.03),34:(.06,0,0),39:(.06,0,0)},
  'keys':[
   ('guard',.16,1,'Guard',0.,0.,{}),
   ('dodge',.45,5,'Split guard',.08,-.08,{'Elbow_L':-5,'Elbow_R':5}),
   ('leap_charge',.55,9,'Coil',.11,-.11,{'Thigh_L':7,'Thigh_R':7}),
   ('leap',.32,13,'Launch',.12,-.12,{'Leg_L':-7,'Leg_R':-7}),
   ('leap',.68,17,'Cross over',-.05,.05,{'Hand_L':6,'Hand_R':-6}),
   ('flurry_left',.20,21,'Left windup',.12,-.05,{'Arm_L':-8,'Elbow_L':-7}),
   ('flurry_left',.70,24,'Left cross',-.10,-.04,{'Arm_L':11,'Elbow_L':9}),
   ('flurry_right',.22,27,'Right windup',.05,-.12,{'Arm_R':9,'Elbow_R':7}),
   ('flurry_right',.73,30,'Right cross',.04,.11,{'Arm_R':-11,'Elbow_R':-9}),
   ('leap',.91,34,'Land',.08,-.08,{'Thigh_L':-5,'Thigh_R':-5}),
   ('guard',.70,39,'Recover',0.,0.,{})],
  'contacts':[{'bone':'Foot_R','start':1,'end':3},
              {'bone':'Foot_L','start':34,'end':36}]},
 {'name':'lizardman.inertial_spiral_rake_knee','end':37,'impacts':[13,23,29],
  'preview_frames':[1,13,23,29,37],
  'travel':{1:(0,0,0),5:(-.08,0,0),9:(-.12,0,0),13:(-.16,0,0),
            18:(-.20,0,0),23:(-.24,0,.10),26:(-.27,0,.05),
            29:(-.30,0,0),33:(-.27,0,0),37:(-.27,0,0)},
  'keys':[
   ('guard',.14,1,'Guard',0.,0.,{}),
   ('dodge',.64,5,'Evade',.10,-.10,{'Thigh_L':-6,'Thigh_R':6}),
   ('tail_slam',.20,9,'Body coil',.11,-.11,{'Arm_L':-8,'Arm_R':8}),
   ('claw_right',.64,13,'Wide rake',.06,.12,{'Arm_R':-12,'Elbow_R':-9}),
   ('leap_charge',.52,18,'Knee load',.12,-.12,{'Thigh_R':-8,'Knee_R':-6}),
   ('leap',.44,23,'Knee drive',-.08,.08,{'Thigh_R':13,'Leg_R':-9,'Knee_R':10}),
   ('claw_left',.22,26,'Reverse windup',.12,-.04,{'Arm_L':-9,'Elbow_L':-7}),
   ('claw_left',.70,29,'Reverse rake',-.12,-.05,{'Arm_L':12,'Elbow_L':9}),
   ('guard',.60,33,'Guard return',.05,-.05,{}),
   ('idle',0,37,'Settle',0.,0.,{})],
  'contacts':[{'bone':'Foot_L','start':1,'end':3},
              {'bone':'Foot_L','start':21,'end':23},
              {'bone':'Foot_R','start':29,'end':31}]},
]

def lock_contacts(action,contacts):
    assign(action)
    for c in contacts:
        pb=rig.pose.bones[c['bone']]
        names=[x.name for x in reversed(pb.parent_recursive)]+[pb.name]
        center=(c['start']+c['end'])//2;scene.frame_set(center)
        held={n:rig.pose.bones[n].rotation_euler.to_quaternion().copy() for n in names}
        root_location=rig.pose.bones['Root'].location.copy()
        for f in range(c['start'],c['end']+1):
            for n,q in held.items():
                p=rig.pose.bones[n];p.rotation_euler=q.to_euler(p.rotation_mode,p.rotation_euler)
                p.keyframe_insert(data_path='rotation_euler',frame=f,group=n)
            root=rig.pose.bones['Root'];root.location=root_location
            root.keyframe_insert(data_path='location',frame=f,group='Root')

reports=[]
for phrase in PHRASES:
    keyposes=[pose('animation.lizardman.'+k[0],*k[1:],
                   root_location=phrase['travel'][k[2]]) for k in phrase['keys']]
    authored=ef.author_motion(rig,keyposes,analysis,phrase['name']+'.authored')
    lock_contacts(bpy.data.actions[authored['action']],phrase['contacts'])
    refined=ef.refine_motion_auto(rig,phrase['contacts'],anchors=phrase['impacts'],
      overrides={'driver':'Root'},name=phrase['name'],settings={
        'frequency_hz':8.,'damping':.9,'max_offset_degrees':8.,
        'sample_step':.5,'max_step_degrees':85.})
    reports.append({'action':refined['action'],'authored':authored['action'],
      'lateral_results':authored['lateral_results'],'contacts':phrase['contacts'],
      'impacts':phrase['impacts'],'preview_frames':phrase['preview_frames'],
      'refinement':refined['validation'],'settings':refined['settings'],
      'joint_coverage':refined['joint_coverage'],
      'modified_bones':refined['modified_bones'],
      'com_start':refined['com_trajectory'][0],
      'com_end':refined['com_trajectory'][-1],
      'rules_version':refined['rules_version'],
      'warnings':refined['warnings']})

assign(bpy.data.actions[PHRASES[0]['name']]);scene.frame_start=1;scene.frame_end=39;scene.frame_set(24)
path=ROOT/'sampleRig/Lizardman_COM_Centrifugal_Test.blend'
bpy.ops.wm.save_as_mainfile(filepath=str(path))
(ROOT/'sampleRig/lizardman_com_centrifugal_report.json').write_text(json.dumps(reports,indent=2),encoding='utf-8')
print('COM_CENTRIFUGAL_OK',str(path))
