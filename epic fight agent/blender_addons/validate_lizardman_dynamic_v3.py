"""Measure the dynamic_v3 actions: finiteness, ground clearance, rotation speed, loop seams, stance hand-off."""
import bpy,json,math,sys
from pathlib import Path
root=Path(__file__).resolve().parent.parent
project=Path('C:/Users/hanjw/Downloads/gourmet2-main/gourmet2-main')
sys.path.insert(0,str(project/'tools/blender'))
import lizardman_anim as la
rig=bpy.data.objects['Lizardman'];scn=bpy.context.scene
bones=[b.name for b in rig.data.bones if b.use_deform and not b.name.startswith('Tool')]
report={};stance=None
for action in sorted([a for a in bpy.data.actions if a.name.startswith(la.PREFIX)],key=lambda a:a.name):
    clip=action.name[len(la.PREFIX):];la.assign_action(rig,action)
    end=int(round(action.frame_range[1]));minz=1e9;maxz=-1e9;maxstep=0;where=None;finite=True;prev=None
    first=last=None;heights=[]
    for i in range(end*2+1):
        f=i/2;scn.frame_set(int(f),subframe=f-int(f));now={}
        for n in bones:
            p=rig.pose.bones[n];q=p.matrix.to_quaternion().normalized();h=rig.matrix_world@p.head
            now[n]=q;finite&=all(math.isfinite(x) for x in (*q,*h))
            if n.startswith(('Foot','Toe')):minz=min(minz,h.z)
        heights.append(rig.matrix_world@rig.pose.bones['Head'].head)
        if prev:
            steps=[(lambda a:min(a,360-a))(math.degrees(prev[n].rotation_difference(now[n]).angle)) for n in bones]
            m=max(steps)
            if m>maxstep:maxstep=m;where=(f,bones[steps.index(m)])
        prev=now
        if i==0:first=now
        last=now
    gap=max((lambda a:min(a,360-a))(math.degrees(first[n].rotation_difference(last[n]).angle)) for n in bones)
    if clip=='idle':stance=first
    report[clip]={'frames':end,'loop':bool(action.use_cyclic),'finite':finite,'min_foot_z':round(minz,4),
      'max_half_frame_deg':round(maxstep,1),'at':where,'ends_gap_deg':round(gap,3),
      'head_height_range':round(max(h.z for h in heights)-min(h.z for h in heights),3)}
for clip,r in report.items():
    first=None
(root/'sampleRig/lizardman_dynamic_v3_validation.json').write_text(json.dumps(report,indent=2),encoding='utf-8')
for c,r in report.items():print('CHECK %-14s f=%2d loop=%-5s fin=%s minFootZ=%7.4f maxStep=%5.1f@%s gap=%6.3f headRange=%.2f'%(c,r['frames'],r['loop'],r['finite'],r['min_foot_z'],r['max_half_frame_deg'],r['at'],r['ends_gap_deg'],r['head_height_range']))
