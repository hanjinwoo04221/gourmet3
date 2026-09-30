"""Measure all exported actions after reopening the deliverable blend."""
import bpy,json,math,sys
from pathlib import Path
root=Path(__file__).resolve().parent.parent
project=Path('C:/Users/hanjw/Downloads/gourmet2-main/gourmet2-main')
sys.path.insert(0,str(project/'tools/blender'))
import lizardman_anim as la
plans=json.loads((root/'sampleRig/lizardman_all_refined_report.json').read_text(encoding='utf-8'))
rig=bpy.data.objects['Lizardman'];scn=bpy.context.scene
bones=[b.name for b in rig.data.bones if b.use_deform and not b.name.startswith('Tool')]
report={}
for clip,plan in plans.items():
    action=bpy.data.actions['animation.lizardman.'+clip];la.assign_action(rig,action)
    end=int(round(action.frame_range[1]));samples={};minz=1e9;maxstep=0;maxwhere=None;moving=[];finite=True
    previous=None
    for i in range(end*2+1):
        f=i/2;scn.frame_set(int(f),subframe=f-int(f))
        now={}
        for n in bones:
            p=rig.pose.bones[n];q=p.matrix.to_quaternion().normalized()
            tail=rig.matrix_world@p.tail;head=rig.matrix_world@p.head
            now[n]=(q,tuple(tail),tuple(head))
            finite &= all(math.isfinite(x) for x in (*q,*tail,*head))
            if n.startswith(('Foot','Toe')):minz=min(minz,head.z)
        samples[f]=now
        if previous:
            steps=[min(a,360-a) for a in
                   (math.degrees(previous[n][0].rotation_difference(now[n][0]).angle) for n in bones)]
            if max(steps)>maxstep:
                maxstep=max(steps);maxwhere={'frame':f,'bone':bones[steps.index(max(steps))]}
            if i%2==0:moving.append(sum(x>.05 for x in steps))
        previous=now
    contacts=[]
    for c in plan['contacts']:
        points=[samples[i/2][c['bone']][1] for i in range(2*c['start'],2*c['end']+1)]
        drift=max(math.dist(points[0],p) for p in points)
        contacts.append({**c,'tail_drift':round(drift,6)})
    seam=max(math.degrees(samples[0][n][0].rotation_difference(samples[float(end)][n][0]).angle)
             for n in bones) if action.use_cyclic else None
    root0=rig.pose.bones['Root']
    report[clip]={'frames':end,'loop':bool(action.use_cyclic),'finite':finite,
      'min_foot_toe_head_z':round(minz,6),'min_moving_bones_per_integer_frame':min(moving),
      'median_moving_bones_per_integer_frame':sorted(moving)[len(moving)//2],
      'max_half_frame_rotation_degrees':round(maxstep,3),
      'max_rotation_at':maxwhere,
      'loop_rotation_gap_degrees':round(seam,5) if seam is not None else None,
      'contacts':contacts}
out=root/'sampleRig/lizardman_all_refined_validation.json'
out.write_text(json.dumps(report,indent=2),encoding='utf-8')
for n,v in report.items():
    print('CHECK',n,'frames',v['frames'],'minZ',v['min_foot_toe_head_z'],
          'active',v['min_moving_bones_per_integer_frame'],
          'halfstep',v['max_half_frame_rotation_degrees'],
          'drift',max((c['tail_drift'] for c in v['contacts']),default=0))
