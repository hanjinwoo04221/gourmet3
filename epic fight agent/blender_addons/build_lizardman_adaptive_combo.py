"""Author two new Lizardman combat phrases from inspected source poses."""
import bpy, importlib.util, json, math
from pathlib import Path
from mathutils import Quaternion, Vector

ROOT = Path(__file__).resolve().parent.parent
spec = importlib.util.spec_from_file_location('ef', ROOT/'blender_addons/epicfight_ai_anim.py')
ef = importlib.util.module_from_spec(spec); spec.loader.exec_module(ef)
rig = bpy.data.objects['Lizardman']; scene = bpy.context.scene
inventory = ef.inspect_motion_rig(rig)
analysis = ef.analyze_rig(rig, overrides={'driver':'Root'})
draft = ef.build_auto_refinement(rig, overrides={'driver':'Root'})
body = [n for n in analysis['usable_bones'] if n not in {'Tool_L','Tool_R'}]

def assign(a):
    rig.animation_data.action=a
    if a.slots: rig.animation_data.action_slot=a.slots[0]

def sample(name, phase, frame, marker=None):
    action=bpy.data.actions[name]; assign(action)
    t=action.frame_range[0]+(action.frame_range[1]-action.frame_range[0])*phase
    scene.frame_set(math.floor(t),subframe=t-math.floor(t))
    pose={}
    for n in body:
        p=rig.pose.bones[n]
        q=p.rotation_quaternion.normalized() if p.rotation_mode=='QUATERNION' else p.rotation_euler.to_quaternion().normalized()
        pose[n]=list(q)
    out={'frame':frame,'local_quaternions':pose}
    if marker:out['marker']=marker
    return out

# Dodge under a blow, rake low, then turn the open flank into a bite.
# The second phrase uses a short leap feint, a grounded tail strike and claw exit.
PHRASES=[
 {'name':'lizardman.duck_rake_bite','end':34,'impacts':[14,23],
  'keys':[
   ('animation.lizardman.guard',.16,1,'Low guard'),
   ('animation.lizardman.dodge',.46,5,'Duck to outside'),
   ('animation.lizardman.dodge',.76,8,'Clear the strike'),
   ('animation.lizardman.claw_left',.20,11,'Load low claw'),
   ('animation.lizardman.claw_left',.66,14,'Low rake'),
   ('animation.lizardman.guard',.38,17,'Recoil'),
   ('animation.lizardman.bite',.24,20,'Neck drive'),
   ('animation.lizardman.bite',.65,23,'Bite'),
   ('animation.lizardman.bite',.88,26,'Release'),
   ('animation.lizardman.guard',.65,30,'Guard return'),
   ('animation.lizardman.idle',0,34,'Settle')]},
 {'name':'lizardman.hop_tail_claw','end':38,'impacts':[21,29],
  'keys':[
   ('animation.lizardman.idle',0,1,'Stalk'),
   ('animation.lizardman.leap_charge',.44,5,'Compression'),
   ('animation.lizardman.leap_charge',.9,8,'Feint launch'),
   ('animation.lizardman.leap',.38,12,'Hop across'),
   ('animation.lizardman.leap',.84,16,'Landing'),
   ('animation.lizardman.tail_slam',.20,18,'Tail coil'),
   ('animation.lizardman.tail_slam',.70,21,'Tail sweep'),
   ('animation.lizardman.tail_slam',.90,24,'Turn through'),
   ('animation.lizardman.claw_right',.25,26,'Claw chamber'),
   ('animation.lizardman.claw_right',.68,29,'Claw impact'),
   ('animation.lizardman.guard',.60,34,'Recover'),
   ('animation.lizardman.idle',0,38,'Settle')]},
]

reports=[]
for phrase in PHRASES:
    poses=[sample(*k) for k in phrase['keys']]
    authored=ef.author_motion(rig,poses,analysis,phrase['name']+'.authored')
    # These are deliberate short support windows. The hop itself is airborne.
    if 'hop_' in phrase['name']:
        contacts=[{'bone':'Foot_L','start':1,'end':3},
                  {'bone':'Foot_R','start':20,'end':22},
                  {'bone':'Foot_L','start':28,'end':30}]
    else:
        contacts=[{'bone':'Foot_R','start':1,'end':3},
                  {'bone':'Foot_L','start':13,'end':15},
                  {'bone':'Foot_R','start':22,'end':24}]
    refined=ef.refine_motion_auto(rig, contacts, anchors=phrase['impacts'],
        overrides={'driver':'Root'}, name=phrase['name'], settings={
          'strength':.27,'frequency_hz':7.5,'damping':.92,
          'centrifugal_gain':.24,'max_offset_degrees':5.0,
          'sample_step':.5,'max_step_degrees':85.})
    action=bpy.data.actions[refined['action']]; assign(action)
    # Quiet deform joints follow their parent with low, staggered motion. Foot
    # chains remain source driven during support; attachments are untouched.
    secondary=[n for n in body if n not in {'Root','Foot_L','Foot_R','Thigh_L','Thigh_R','Leg_L','Leg_R'}]
    base={}
    for f in range(1,phrase['end']+1):
        scene.frame_set(f)
        base[f]={}
        for n in secondary:
            p=rig.pose.bones[n]
            base[f][n]=(p.rotation_quaternion if p.rotation_mode=='QUATERNION' else p.rotation_euler.to_quaternion()).copy().normalized()
    for i,n in enumerate(secondary):
        p=rig.pose.bones[n]; prop=ef._rotation_prop(rig,n)
        for fc in ef._bone_fcurves(rig,n,prop):fc.keyframe_points.clear()
        family=n.split('_')[0]
        amp={'Jaw':.45,'Tail1':.55,'Tail2':.7,'Tail3':.8,'Knee':.45,
             'Elbow':.55,'Toe':.35,'Hand':.42,'Head':.32}.get(family,.34)
        delay=.19*len(p.parent_recursive)+.43*(i%3)
        previous_euler=None
        for f in range(1,phrase['end']+1):
            # Delayed overlapping response; taper around impact and endpoints.
            speed=math.degrees(base[max(1,f-1)][n].rotation_difference(base[f][n]).angle)
            response=.45+.55*min(1.,speed/7.)
            taper=min(1.,(f-1)/2.,(phrase['end']-f)/2.)
            if min(abs(f-x) for x in phrase['impacts'])<=1:taper*=.35
            a=math.radians(amp)*response*taper*math.sin(.38*f-delay)
            axis=Vector((1,0,0)) if family in {'Knee','Elbow','Toe'} else Vector((0,0,1))
            q=(base[f][n]@Quaternion(axis,a)).normalized()
            if p.rotation_mode=='QUATERNION':p.rotation_quaternion=q
            else:
                previous_euler=q.to_euler(p.rotation_mode,previous_euler) if previous_euler else q.to_euler(p.rotation_mode)
                p.rotation_euler=previous_euler
            p.keyframe_insert(data_path=prop,frame=f,group=n)
        for fc in ef._bone_fcurves(rig,n,prop):
            for kp in fc.keyframe_points:kp.interpolation='LINEAR'
    # Lock each short support phase at its central pose. Blend the source
    # chain in and out over two frames to avoid a rotation jump.
    for c in contacts:
        foot=rig.pose.bones[c['bone']]
        chain=[p.name for p in reversed(foot.parent_recursive)]+[foot.name]
        lo=max(1,c['start']-2); hi=min(phrase['end'],c['end']+2)
        original={}
        for f in range(lo,hi+1):
            scene.frame_set(f)
            original[f]={}
            for n in chain:
                p=rig.pose.bones[n]
                original[f][n]=(p.rotation_quaternion if p.rotation_mode=='QUATERNION' else p.rotation_euler.to_quaternion()).copy().normalized()
        center=(c['start']+c['end'])//2
        for n in chain:
            p=rig.pose.bones[n]; prop=ef._rotation_prop(rig,n)
            hold=original[center][n]
            previous_euler=None
            for f in range(lo,hi+1):
                if c['start']<=f<=c['end']:q=hold
                elif f==c['start']-1 or f==c['end']+1:q=original[f][n].slerp(hold,.5)
                else:q=original[f][n]
                if p.rotation_mode=='QUATERNION':p.rotation_quaternion=q
                else:
                    previous_euler=q.to_euler(p.rotation_mode,previous_euler) if previous_euler else q.to_euler(p.rotation_mode)
                    p.rotation_euler=previous_euler
                p.keyframe_insert(data_path=prop,frame=f,group=n)
    reports.append({'action':action.name,'authored':authored['action'],'phases':[
        {'frame':k[2],'label':k[3]} for k in phrase['keys'] if k[3]],
        'contacts':contacts,'impacts':phrase['impacts'],
        'refinement':{'validation':refined['validation'],'warnings':refined['warnings']}})

assign(bpy.data.actions[PHRASES[0]['name']]);scene.frame_start=1;scene.frame_end=34;scene.frame_set(14)
out=ROOT/'sampleRig/Lizardman_Adaptive_Combat_Combos.blend'
bpy.ops.wm.save_as_mainfile(filepath=str(out))
(ROOT/'sampleRig/lizardman_adaptive_combo_report.json').write_text(json.dumps(reports,indent=2),encoding='utf-8')
print('ADAPTIVE_COMBOS_OK',str(out))
