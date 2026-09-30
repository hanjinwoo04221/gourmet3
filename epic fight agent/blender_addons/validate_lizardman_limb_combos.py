"""Inspect local limb articulation and support in the Lizardman limb pack."""
import bpy,json,math
from pathlib import Path

root=Path(__file__).resolve().parent.parent
blend_stem=Path(bpy.data.filepath).stem
stem={'Lizardman_Dynamic_Lateral_Test':'lizardman_dynamic_lateral',
      'Lizardman_COM_Centrifugal_Test':'lizardman_com_centrifugal'}.get(
          blend_stem,'lizardman_lateral_shoulder')
plans=json.loads((root/f'sampleRig/{stem}_report.json').read_text(encoding='utf-8'))
rig=bpy.data.objects['Lizardman'];scene=bpy.context.scene
limbs=[n for n in rig.pose.bones.keys() if n.startswith((
    'Shoulder','Arm','Elbow','Hand','Thigh','Knee','Leg','Foot','Toe')) and not n.startswith('Tool')]

def local_q(p):
    return (p.rotation_quaternion if p.rotation_mode=='QUATERNION' else p.rotation_euler.to_quaternion()).normalized()

result={}
for plan in plans:
    a=bpy.data.actions[plan['action']];rig.animation_data.action=a
    if a.slots:rig.animation_data.action_slot=a.slots[0]
    first,end=(int(round(v)) for v in a.frame_range)
    previous=None;active=[];max_step=0.;min_z=float('inf');nonfinite=[]
    lateral={side:[] for side in ('L','R')}
    support={i:[] for i in range(len(plan['contacts']))}
    for i in range((end-first)*2+1):
        frame=first+i*.5;scene.frame_set(math.floor(frame),subframe=frame-math.floor(frame))
        poses={n:local_q(rig.pose.bones[n]).copy() for n in limbs}
        if any(not all(math.isfinite(x) for x in q) for q in poses.values()):nonfinite.append(frame)
        for name in ('Foot_L','Foot_R','Toe_L_mid','Toe_R_mid'):
            min_z=min(min_z,(rig.matrix_world@rig.pose.bones[name].head).z)
        if frame.is_integer():
            chest=rig.pose.bones['Chest']
            for side in ('L','R'):
                hand=rig.pose.bones['Hand_'+side]
                lateral[side].append((chest.matrix.inverted()@hand.tail).x)
        for index,c in enumerate(plan['contacts']):
            if c['start']<=frame<=c['end']:
                support[index].append(tuple(rig.matrix_world@rig.pose.bones[c['bone']].tail))
        if previous:
            angles=[math.degrees(previous[n].rotation_difference(poses[n]).angle) for n in limbs]
            max_step=max(max_step,*angles)
            if frame.is_integer():active.append(sum(angle>.1 for angle in angles))
        previous=poses
    contacts=[]
    for index,c in enumerate(plan['contacts']):
        pts=support[index]
        contacts.append(dict(c,world_tail_drift=max(math.dist(pts[0],p) for p in pts)))
    pose_frames=plan.get('preview_frames') or sorted(set([first,end]+plan['impacts']))
    poses=[]
    for frame in pose_frames:
        scene.frame_set(frame)
        poses.append({'frame':frame,'bones':{n:{
            'head':list(rig.matrix_world@rig.pose.bones[n].head),
            'tail':list(rig.matrix_world@rig.pose.bones[n].tail)}
            for n in [b.name for b in rig.data.bones if b.use_deform and not b.name.startswith('Tool')]}})
    result[plan['action']]={'limb_bones':len(limbs),'mean_active_limb_bones':sum(active)/len(active),
        'minimum_active_limb_bones':min(active),'median_active_limb_bones':sorted(active)[len(active)//2],
        'maximum_half_frame_local_rotation_degrees':max_step,
        'minimum_foot_or_toe_head_z':min_z,'nonfinite_frames':nonfinite,'contacts':contacts,
        'hand_lateral_range':{side:max(xs)-min(xs) for side,xs in lateral.items()},
        'poses':poses}
path=root/f'sampleRig/{stem}_validation.json'
path.write_text(json.dumps(result,indent=2),encoding='utf-8')
print('LIMB_VALIDATION',json.dumps({name:{k:v for k,v in stats.items() if k!='poses'}
                                    for name,stats in result.items()}))
