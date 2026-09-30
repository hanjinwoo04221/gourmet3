"""Measure per-frame joint activity in the Lizardman test actions."""
import bpy, json, math
from pathlib import Path
from mathutils import Quaternion

rig=bpy.data.objects['Lizardman']; scene=bpy.context.scene
names=[b.name for b in rig.data.bones if b.use_deform and not b.name.startswith('Tool')]
out={}
for action_name in ('feral.pounce_bite','feral.razor_claw_chain','feral.tail_reversal'):
    action=bpy.data.actions[action_name];rig.animation_data.action=action
    if action.slots:rig.animation_data.action_slot=action.slots[0]
    first,last=map(lambda v:int(round(v)),action.frame_range)
    previous=None;counts=[];by_bone={n:0 for n in names};per_frame={};quiet={}
    for frame in range(first,last+1):
        scene.frame_set(frame)
        current={n:(rig.pose.bones[n].rotation_quaternion.copy() if rig.pose.bones[n].rotation_mode=='QUATERNION'
                    else rig.pose.bones[n].rotation_euler.to_quaternion()) for n in names}
        if previous:
            count=0;quiet_names=[]
            for n in names:
                a,b=previous[n],current[n]
                angle=math.degrees(a.rotation_difference(b).angle)
                if angle>.1:
                    count+=1;by_bone[n]+=1
                else: quiet_names.append(n)
            counts.append(count);per_frame[frame]=count
            if count<23:quiet[frame]=quiet_names
        previous=current
    out[action_name]={'bone_count':len(names),'min_moving':min(counts),'median_moving':sorted(counts)[len(counts)//2],
                      'mean_moving':sum(counts)/len(counts),'per_frame':per_frame,'quiet_low_frames':quiet,'per_bone':by_bone}
print(json.dumps(out,indent=2))
Path(__file__).resolve().parent.parent.joinpath('sampleRig/lizardman_feral_pack_v2_activity.json').write_text(
    json.dumps(out,indent=2),encoding='utf-8')
