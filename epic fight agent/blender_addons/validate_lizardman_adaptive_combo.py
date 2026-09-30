import bpy,json,math
from pathlib import Path
from mathutils import Quaternion
root=Path(__file__).resolve().parent.parent
report=json.loads((root/'sampleRig/lizardman_adaptive_combo_report.json').read_text(encoding='utf-8'))
rig=bpy.data.objects['Lizardman']; scene=bpy.context.scene
names=[b.name for b in rig.data.bones if b.use_deform and not b.name.startswith('Tool')]
out={}
for item in report:
    action=bpy.data.actions.get(item['action']) or bpy.data.actions['animation.'+item['action']]
    rig.animation_data.action=action
    if action.slots:rig.animation_data.action_slot=action.slots[0]
    end=int(action.frame_range[1]); previous=None; movements=[]; maxstep=0; minz=1e9
    positions={}
    for f in [1+.5*i for i in range((end-1)*2+1)]:
        scene.frame_set(math.floor(f),subframe=f-math.floor(f))
        now={n:(rig.pose.bones[n].matrix.to_quaternion().normalized(),
                tuple((rig.matrix_world@rig.pose.bones[n].tail))) for n in names}
        minz=min(minz,*[(rig.matrix_world@rig.pose.bones[n].head).z for n in names if n.startswith(('Foot','Toe'))])
        positions[f]=now
        if previous:
            steps={n:math.degrees(previous[n][0].rotation_difference(now[n][0]).angle) for n in names}
            maxstep=max(maxstep,max(steps.values()))
            if f.is_integer():movements.append(sum(v>.035 for v in steps.values()))
        previous=now
    contacts=[]
    for c in item['contacts']:
        coords=[positions[float(f)][c['bone']][1] for f in range(c['start'],c['end']+1)]
        drift=max(math.dist(coords[0],p) for p in coords)
        contacts.append({'bone':c['bone'],'start':c['start'],'end':c['end'],'world_tail_drift':drift})
    out[item['action']]={'deform_bones':len(names),'minimum_moving_bones_per_frame':min(movements),
        'median_moving_bones_per_frame':sorted(movements)[len(movements)//2],
        'max_half_frame_rotation_degrees':maxstep,'minimum_foot_or_toe_head_z':minz,
        'contact_windows':contacts,'impact_poses':[
            {'frame':f,'root_tail':positions[float(f)]['Root'][1],
             'head_tail':positions[float(f)]['Head'][1],
             'left_hand_tail':positions[float(f)]['Hand_L'][1],
             'right_hand_tail':positions[float(f)]['Hand_R'][1]}
            for f in item['impacts']]}
(root/'sampleRig/lizardman_adaptive_combo_validation.json').write_text(json.dumps(out,indent=2),encoding='utf-8')
print('VALIDATION',json.dumps(out))
