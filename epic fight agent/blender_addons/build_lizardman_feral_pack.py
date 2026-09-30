"""Create fast predatory attacks on the sample Lizardman without modifying its source."""
import bpy,math,json,importlib.util
from mathutils import Quaternion,Vector
from pathlib import Path

ROOT=Path(__file__).resolve().parent.parent
spec=importlib.util.spec_from_file_location('ef',ROOT/'blender_addons/epicfight_ai_anim.py')
ef=importlib.util.module_from_spec(spec);spec.loader.exec_module(ef)
rig=bpy.data.objects['Lizardman'];scene=bpy.context.scene
analysis=ef.analyze_rig(rig)
body=[n for n in analysis['usable_bones'] if n not in analysis['exclude']]

def assign(action):
    rig.animation_data.action=action
    if action.slots:rig.animation_data.action_slot=action.slots[0]

def source_pose(action_name,phase,target_frame,marker=None):
    action=bpy.data.actions[action_name];assign(action)
    frame=action.frame_range[0]+(action.frame_range[1]-action.frame_range[0])*phase
    scene.frame_set(math.floor(frame),subframe=frame-math.floor(frame))
    values={}
    for n in body:
        pb=rig.pose.bones[n]
        if pb.rotation_mode=='QUATERNION':q=pb.rotation_quaternion.normalized()
        elif pb.rotation_mode=='AXIS_ANGLE':
            a,x,y,z=pb.rotation_axis_angle;q=Quaternion((x,y,z),a)
        else:q=pb.rotation_euler.to_quaternion()
        values[n]=list(q)
    result={'frame':target_frame,'local_quaternions':values}
    if marker:result['marker']=marker
    return result

PACK=[
 {'name':'feral.pounce_bite','impact':24,'end':38,'keys':[
  ('animation.lizardman.idle',0,1,'Stalk'),
  ('animation.lizardman.leap_charge',.35,5,'Compress'),
  ('animation.lizardman.leap_charge',1,9,'Launch'),
  ('animation.lizardman.leap',.28,14,None),
  ('animation.lizardman.leap',.62,19,'Pounce'),
  ('animation.lizardman.bite',.28,22,None),
  ('animation.lizardman.bite',.58,24,'Bite impact'),
  ('animation.lizardman.bite',.85,28,None),
  ('animation.lizardman.leap',.92,33,'Land'),
  ('animation.lizardman.idle',0,38,'Recover')]},
 {'name':'feral.razor_claw_chain','impact':19,'end':36,'keys':[
  ('animation.lizardman.guard',.1,1,'Guard'),
  ('animation.lizardman.dodge',.58,5,'Slip'),
  ('animation.lizardman.claw_right',.18,8,None),
  ('animation.lizardman.claw_right',.58,11,'Right claw'),
  ('animation.lizardman.claw_left',.18,14,None),
  ('animation.lizardman.claw_left',.60,17,'Left claw'),
  ('animation.lizardman.flurry_right',.38,20,None),
  ('animation.lizardman.flurry_right',.72,22,'Rake'),
  ('animation.lizardman.flurry_left',.38,25,None),
  ('animation.lizardman.flurry_left',.72,27,'Finisher'),
  ('animation.lizardman.guard',.75,32,None),
  ('animation.lizardman.idle',0,36,'Recover')]},
 {'name':'feral.tail_reversal','impact':18,'end':36,'keys':[
  ('animation.lizardman.idle',0,1,'Bait'),
  ('animation.lizardman.dodge',.35,5,'Evade'),
  ('animation.lizardman.dodge',.82,8,None),
  ('animation.lizardman.tail_slam',.12,10,'Coil'),
  ('animation.lizardman.tail_slam',.42,14,None),
  ('animation.lizardman.tail_slam',.68,18,'Tail impact'),
  ('animation.lizardman.tail_slam',.88,22,None),
  ('animation.lizardman.spike',.35,25,None),
  ('animation.lizardman.spike',.66,28,'Follow-up'),
  ('animation.lizardman.guard',.65,32,None),
  ('animation.lizardman.idle',0,36,'Recover')]},
]

reports=[]
def add_dense_joint_followthrough(action, impact, end):
    """Animate quiet deform joints with phase-linked, bounded follow-through."""
    assign(action)
    names=[b.name for b in rig.data.bones if b.use_deform and b.name not in
           {'Root','Foot_L','Foot_R','Leg_L','Leg_R','Thigh_L','Thigh_R','Tool_L','Tool_R'}]
    base={}; energy={}
    previous={}
    for frame in range(1,end+1):
        scene.frame_set(frame)
        base[frame]={}
        for n in names:
            pb=rig.pose.bones[n]
            q=pb.rotation_quaternion.normalized() if pb.rotation_mode=='QUATERNION' else pb.rotation_euler.to_quaternion().normalized()
            base[frame][n]=q.copy()
        if previous:
            energy[frame]={n:math.degrees(previous[n].rotation_difference(base[frame][n]).angle) for n in names}
        previous=base[frame]
    energy[1]=energy[2].copy()
    # A shared attack beat produces subtle motion even during source interpolation eases.
    secondary=names
    amplitudes={'Shoulder':1.1,'Elbow':1.1,'Knee':1.05,'Toe':1.05,'Jaw':.65,
                'Tail':.65,'Torso':.42,'Chest':.42,'Head':.48,'Arm':.38,'Hand':.42}
    for n in secondary:
        pb=rig.pose.bones[n];prop=ef._rotation_prop(rig,n)
        for fc in ef._bone_fcurves(rig,n,prop):fc.keyframe_points.clear()
        previous_euler=None
        parent=pb.parent.name if pb.parent else n
        for frame in range(1,end+1):
            parent_motion=energy[frame].get(parent,energy[frame].get('Torso',0.))
            response=min(1.,parent_motion/7.)
            family=next((key for key in amplitudes if n.startswith(key)),None)
            amplitude=amplitudes.get(family,.55)
            # The wave follows the strike beat, with a small alternating phase on each side.
            side=.75 if '_L' in n else -.75 if '_R' in n else 0.
            digit=(-.7 if n.endswith('_out') else .7 if n.endswith('_in') else 0.)
            phase=.51*(frame-impact)+side+digit-(len(pb.parent_recursive)*.19)
            envelope=1.
            angle=math.radians(amplitude)*(.65+.35*response)*math.sin(phase)*envelope
            axis=Vector((1.,0.,0.)) if family in {'Elbow','Knee','Toe'} else Vector((0.,0.,1.))
            q=(base[frame][n] @ Quaternion(axis,angle)).normalized()
            if pb.rotation_mode=='QUATERNION':pb.rotation_quaternion=q
            else:
                previous_euler=q.to_euler(pb.rotation_mode,previous_euler) if previous_euler else q.to_euler(pb.rotation_mode)
                pb.rotation_euler=previous_euler
            pb.keyframe_insert(data_path=prop,frame=frame,group=n)
        for fc in ef._bone_fcurves(rig,n,prop):
            for kp in fc.keyframe_points:kp.interpolation='LINEAR'
    return secondary

for item in PACK:
    poses=[source_pose(*key) for key in item['keys']]
    authored=ef.author_motion(rig,poses,analysis,item['name']+'.authored')
    contacts=[{'bone':'Foot_R','start':1,'end':item['end']},
              {'bone':'Foot_L','start':1,'end':item['end']}]
    report=ef.refine_motion_auto(rig,contacts,anchors=[item['impact']],
      overrides={'driver':analysis['driver']},name=item['name'],settings={
      'strength':.30,'frequency_hz':8.,'damping':.88,'centrifugal_gain':.38,
      'max_offset_degrees':7.,'sample_step':.4,'max_step_degrees':85.})
    report['dense_followthrough_bones']=add_dense_joint_followthrough(
        bpy.data.actions[report['action']],item['impact'],item['end'])
    report['authored_action']=authored['action'];report['impact_frame']=item['impact']
    reports.append(report)

assign(bpy.data.actions[PACK[0]['name']]);scene.frame_start=1;scene.frame_end=PACK[0]['end'];scene.frame_set(PACK[0]['impact'])
out=ROOT/'sampleRig/Lizardman_Feral_Combat_Pack_v2.blend'
bpy.ops.wm.save_as_mainfile(filepath=str(out))
summary=[]
for report in reports:
    summary.append({'action':report['action'],'source':report['authored_action'],
      'impact_frame':report['impact_frame'],'modified_bones':report['modified_bones'],
      'protected_bones':report['protected_bones'],'validation':report['validation'],
      'dense_followthrough_bones':report['dense_followthrough_bones'],
      'warnings':report['warnings']})
(ROOT/'sampleRig/lizardman_feral_pack_v2_report.json').write_text(json.dumps(summary,indent=2),encoding='utf-8')
print('PASS FERAL PACK',json.dumps(summary))
