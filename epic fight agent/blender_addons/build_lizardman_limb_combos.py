"""Build limb-led Lizardman attacks on the inspected sample rig."""
import bpy, importlib.util, json, math
from pathlib import Path
from mathutils import Quaternion, Vector

ROOT=Path(__file__).resolve().parent.parent
spec=importlib.util.spec_from_file_location('ef',ROOT/'blender_addons/epicfight_ai_anim.py')
ef=importlib.util.module_from_spec(spec);spec.loader.exec_module(ef)
rig=bpy.data.objects['Lizardman'];scene=bpy.context.scene
ef.inspect_motion_rig(rig)
analysis=ef.analyze_rig(rig,overrides={'driver':'Root'})
ef.build_auto_refinement(rig,overrides={'driver':'Root'})
body=[b.name for b in rig.data.bones if b.use_deform and not b.name.startswith('Tool')]

def assign(a):
    rig.animation_data.action=a
    if a.slots:rig.animation_data.action_slot=a.slots[0]

def rotation(p):
    return (p.rotation_quaternion if p.rotation_mode=='QUATERNION' else p.rotation_euler.to_quaternion()).copy().normalized()

# Pose accents are local to this inspected XZY rig. Arm and leg chains drive the
# choreography; elbow/knee deform bones receive matching shape motion.
LEFT_LOAD={'Shoulder_L':-18,'Arm_L':-10,'Elbow_L':-8,'Hand_L':-6,
           'Thigh_R':7,'Knee_R':6,'Leg_R':-5,'Foot_R':3}
LEFT_HIT={'Shoulder_L':17,'Arm_L':13,'Elbow_L':12,'Hand_L':9,
          'Shoulder_R':-9,'Arm_R':-6,'Thigh_L':-8,'Knee_L':-6,'Leg_L':6}
RIGHT_LOAD={'Shoulder_R':18,'Arm_R':11,'Elbow_R':8,'Hand_R':6,
            'Thigh_L':-7,'Knee_L':-6,'Leg_L':5}
RIGHT_HIT={'Shoulder_R':-18,'Arm_R':-14,'Elbow_R':-12,'Hand_R':-8,
           'Shoulder_L':9,'Arm_L':5,'Thigh_R':8,'Knee_R':6,'Leg_R':-6}
KNEE_LOAD={'Thigh_R':-7,'Knee_R':-6,'Leg_R':5,'Foot_R':-3,
           'Shoulder_L':-10,'Arm_L':-5,'Shoulder_R':10,'Arm_R':5}
KNEE_HIT={'Thigh_R':13,'Knee_R':10,'Leg_R':-9,'Foot_R':6,
          'Shoulder_L':13,'Arm_L':9,'Elbow_L':7,'Shoulder_R':-13,'Arm_R':-9,'Elbow_R':-7}

PHRASES=[
 {'name':'lizardman.cross_claw_knee','end':38,'impacts':[13,23,29],
  'keys':[
   ('animation.lizardman.guard',.20,1,'Guard',{}),
   ('animation.lizardman.dodge',.42,5,'Slip and load',LEFT_LOAD),
   ('animation.lizardman.claw_left',.19,9,'Left chamber',LEFT_LOAD),
   ('animation.lizardman.claw_left',.66,13,'Left rake',LEFT_HIT),
   ('animation.lizardman.leap_charge',.50,18,'Knee chamber',KNEE_LOAD),
   ('animation.lizardman.leap',.42,23,'Knee drive',KNEE_HIT),
   ('animation.lizardman.claw_right',.22,26,'Right chamber',RIGHT_LOAD),
   ('animation.lizardman.claw_right',.68,29,'Right rake',RIGHT_HIT),
   ('animation.lizardman.guard',.67,34,'Reset',{}),
   ('animation.lizardman.idle',0,38,'Settle',{})],
  'contacts':[{'bone':'Foot_R','start':1,'end':3},
              {'bone':'Foot_L','start':11,'end':13},
              {'bone':'Foot_L','start':21,'end':23},
              {'bone':'Foot_R','start':29,'end':31}]},
 {'name':'lizardman.low_sweep_double_rake','end':36,'impacts':[14,22,28],
  'keys':[
   ('animation.lizardman.guard',.14,1,'Guard',{}),
   ('animation.lizardman.dodge',.58,5,'Lower stance',RIGHT_LOAD),
   ('animation.lizardman.spike',.22,10,'Sweep chamber',KNEE_LOAD),
   ('animation.lizardman.spike',.70,14,'Low sweep',KNEE_HIT),
   ('animation.lizardman.flurry_right',.24,18,'Right load',RIGHT_LOAD),
   ('animation.lizardman.flurry_right',.70,22,'Right strike',RIGHT_HIT),
   ('animation.lizardman.flurry_left',.25,25,'Left load',LEFT_LOAD),
   ('animation.lizardman.flurry_left',.73,28,'Left strike',LEFT_HIT),
   ('animation.lizardman.guard',.64,33,'Recover',{}),
   ('animation.lizardman.idle',0,36,'Settle',{})],
  'contacts':[{'bone':'Foot_L','start':1,'end':3},
              {'bone':'Foot_L','start':12,'end':14},
              {'bone':'Foot_R','start':20,'end':22},
              {'bone':'Foot_L','start':27,'end':29}]},
]

def sample_pose(source_name,phase,frame,marker,accents):
    source=bpy.data.actions[source_name];assign(source)
    t=source.frame_range[0]+(source.frame_range[1]-source.frame_range[0])*phase
    scene.frame_set(math.floor(t),subframe=t-math.floor(t))
    values={}
    for n in body:
        q=rotation(rig.pose.bones[n])
        if n in accents:
            # On this rig local Y moves the hands sideways; X bends the other limb segments.
            axis=Vector((0,1,0)) if n.startswith('Shoulder') else Vector((1,0,0))
            q=(q@Quaternion(axis,math.radians(accents[n]))).normalized()
        values[n]=list(q)
    return {'frame':frame,'local_quaternions':values,'marker':marker}

def lock_short_contacts(action,contacts,end):
    assign(action)
    for c in contacts:
        bone=rig.pose.bones[c['bone']]
        chain=[p.name for p in reversed(bone.parent_recursive)]+[bone.name]
        center=(c['start']+c['end'])//2
        scene.frame_set(center)
        held={n:rotation(rig.pose.bones[n]) for n in chain}
        for f in range(c['start'],c['end']+1):
            for n,q in held.items():
                p=rig.pose.bones[n];prop=ef._rotation_prop(rig,n)
                if p.rotation_mode=='QUATERNION':p.rotation_quaternion=q
                else:p.rotation_euler=q.to_euler(p.rotation_mode,p.rotation_euler)
                p.keyframe_insert(data_path=prop,frame=f,group=n)

def add_limb_followthrough(action,contacts,impacts,end):
    """Add small chain-timed rotations while keeping support ancestors fixed."""
    assign(action)
    limbs=[n for n in body if n.startswith((
        'Shoulder','Arm','Elbow','Hand','Thigh','Knee','Leg','Foot','Toe'))]
    frames=[1+i*.5 for i in range((end-1)*2+1)]
    source={}
    for f in frames:
        scene.frame_set(math.floor(f),subframe=f-math.floor(f))
        source[f]={n:rotation(rig.pose.bones[n]) for n in limbs}
    amplitudes={'Shoulder':2.0,'Arm':.8,'Elbow':1.5,'Hand':1.,
                'Thigh':.9,'Knee':1.4,'Leg':.8,'Foot':.55,'Toe':.85}
    support_chains=[]
    for c in contacts:
        p=rig.pose.bones[c['bone']]
        support_chains.append((c,{x.name for x in p.parent_recursive}|{p.name}))
    for n in limbs:
        p=rig.pose.bones[n];prop=ef._rotation_prop(rig,n)
        family=next(k for k in amplitudes if n.startswith(k))
        side=.67 if '_L' in n else -.67
        digit=(-.6 if n.endswith('_out') else .6 if n.endswith('_in') else 0.)
        depth=len(p.parent_recursive)
        old_euler=None
        for index,f in enumerate(frames):
            if index:
                source_speed=math.degrees(source[frames[index-1]][n].rotation_difference(source[f][n]).angle)*2
            else:source_speed=0.
            response=.55+.45*min(1.,source_speed/6.)
            beat=min(impacts,key=lambda impact:abs(f-impact))
            phase=.48*(f-beat)+side+digit-.21*depth
            taper=1.
            for c,chain in support_chains:
                if n in chain:
                    if c['start']<=f<=c['end']:taper=0.
                    elif c['start']-1.5<f<c['start']:
                        taper=min(taper,(c['start']-f)/1.5)
                    elif c['end']<f<c['end']+1.5:
                        taper=min(taper,(f-c['end'])/1.5)
            # Contact and impact poses stay dominant; secondary turns remain below 1.5 degrees.
            if any(abs(f-impact)<.5 for impact in impacts):taper*=.45
            angle=math.radians(amplitudes[family])*response*taper*math.sin(phase)
            axis=Vector((0,1,0)) if family=='Shoulder' else Vector((1,0,0))
            q=(source[f][n]@Quaternion(axis,angle)).normalized()
            if p.rotation_mode=='QUATERNION':p.rotation_quaternion=q
            else:
                old_euler=q.to_euler(p.rotation_mode,old_euler) if old_euler else q.to_euler(p.rotation_mode)
                p.rotation_euler=old_euler
            p.keyframe_insert(data_path=prop,frame=f,group=n)
    return limbs

reports=[]
for phrase in PHRASES:
    poses=[sample_pose(*key) for key in phrase['keys']]
    authored=ef.author_motion(rig,poses,analysis,phrase['name']+'.authored')
    lock_short_contacts(bpy.data.actions[authored['action']],phrase['contacts'],phrase['end'])
    refined=ef.refine_motion_auto(rig,phrase['contacts'],anchors=phrase['impacts'],
       overrides={'driver':'Root'},name=phrase['name'],settings={
       'strength':.27,'frequency_hz':7.5,'damping':.92,'centrifugal_gain':.25,
       'max_offset_degrees':5.,'sample_step':.5,'max_step_degrees':85.})
    emphasized=add_limb_followthrough(bpy.data.actions[refined['action']],
        phrase['contacts'],phrase['impacts'],phrase['end'])
    reports.append({'action':refined['action'],'authored':authored['action'],
      'impacts':phrase['impacts'],'contacts':phrase['contacts'],
      'limb_accents':[{ 'frame':k[2],'bones':list(k[4])} for k in phrase['keys'] if k[4]],
      'followthrough_bones':emphasized,
      'refinement':refined['validation'],'warnings':refined['warnings']})

assign(bpy.data.actions[PHRASES[0]['name']]);scene.frame_start=1;scene.frame_end=38;scene.frame_set(23)
path=ROOT/'sampleRig/Lizardman_Lateral_Shoulder_Pack.blend'
bpy.ops.wm.save_as_mainfile(filepath=str(path))
(ROOT/'sampleRig/lizardman_lateral_shoulder_report.json').write_text(json.dumps(reports,indent=2),encoding='utf-8')
print('LIMB_PACK_OK',str(path))
