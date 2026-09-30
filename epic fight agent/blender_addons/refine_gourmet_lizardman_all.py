"""Refine all newly authored actions, retain impact poses and short supports."""
import bpy,importlib.util,json,math,sys
from pathlib import Path
from mathutils import Quaternion,Vector
root=Path(__file__).resolve().parent.parent
project=Path('C:/Users/hanjw/Downloads/gourmet2-main/gourmet2-main')
sys.path.insert(0,str(project/'tools/blender'))
import lizardman_anim as la
spec=importlib.util.spec_from_file_location('ef',root/'blender_addons/epicfight_ai_anim.py')
ef=importlib.util.module_from_spec(spec);spec.loader.exec_module(ef)
rig=bpy.data.objects['Lizardman'];scene=bpy.context.scene
analysis=ef.analyze_rig(rig,overrides={'driver':'Root'})
draft=ef.build_auto_refinement(rig,overrides={'driver':'Root'})
auth=json.loads((root/'sampleRig/lizardman_all_reauthored_report.json').read_text(encoding='utf-8'))

def assign(a):
    la.assign_action(rig,a)

def contacts_for(clip,end,poses):
    if clip=='idle':return [{'bone':foot,'start':0,'end':end} for foot in ('Foot_L','Foot_R')]
    if clip=='walk':return [{'bone':'Foot_L','start':0,'end':3},
                            {'bone':'Foot_R','start':17,'end':20},
                            {'bone':'Foot_L','start':33,'end':36}]
    if clip=='run':return [{'bone':'Foot_L','start':0,'end':2},
                           {'bone':'Foot_R','start':9,'end':11},
                           {'bone':'Foot_L','start':18,'end':20}]
    if clip=='guard':return [{'bone':foot,'start':0,'end':end} for foot in ('Foot_L','Foot_R')]
    out=[{'bone':'Foot_R','start':0,'end':2}]
    # During launch the character deliberately leaves support; settle at landing.
    if clip in {'jump','leap','leap_charge'}:
        land=next((int(m.frame) for m in bpy.data.actions['animation.lizardman.'+clip].pose_markers
                   if m.name=='land'),None)
        if land:out.append({'bone':'Foot_L','start':land,'end':min(end,land+2)})
    elif clip=='hop_tail_claw':out.append({'bone':'Foot_L','start':12,'end':14})
    else:
        impacts=[int(m.frame) for m in bpy.data.actions['animation.lizardman.'+clip].pose_markers
                 if m.name in {'impact','bite','rake','rise','slip','tail impact','claw impact'}]
        for f in impacts:
            if 2<=f<end-1:out.append({'bone':'Foot_R' if clip.endswith(('left','bite')) else 'Foot_L',
                                      'start':f-1,'end':f+1})
    return out

def freeze_support(action,contacts,end):
    assign(action)
    for c in contacts:
        foot=rig.pose.bones[c['bone']]
        chain=[p.name for p in reversed(foot.parent_recursive)]+[foot.name]
        sampled={}
        for half in range(end*2+1):
            f=half/2;scene.frame_set(int(f),subframe=f-int(f))
            sampled[f]={}
            for n in chain:
                p=rig.pose.bones[n]
                sampled[f][n]=(p.rotation_quaternion if p.rotation_mode=='QUATERNION'
                               else p.rotation_euler.to_quaternion()).copy().normalized()
            sampled[f]['Root_location']=rig.pose.bones['Root'].location.copy()
        center=float(c['start'] if c['end']-c['start']>4 or c['start']==0 else
                     c['end'] if c['end']==end else
                     round((c['start']+c['end'])/2*2)/2)
        def influence(f):
            if c['start']<=f<=c['end']:return 1.
            d=c['start']-f if f<c['start'] else f-c['end']
            return max(0.,1.-d)
        for n in chain:
            p=rig.pose.bones[n];prop=ef._rotation_prop(rig,n);prev=None
            for fc in ef._bone_fcurves(rig,n,prop):fc.keyframe_points.clear()
            for f in sorted(sampled):
                q=sampled[f][n].slerp(sampled[center][n],influence(f))
                if p.rotation_mode=='QUATERNION':p.rotation_quaternion=q
                else:
                    prev=q.to_euler(p.rotation_mode,prev) if prev else q.to_euler(p.rotation_mode)
                    p.rotation_euler=prev
                p.keyframe_insert(data_path=prop,frame=f,group=n)
            for fc in ef._bone_fcurves(rig,n,prop):
                for kp in fc.keyframe_points:kp.interpolation='LINEAR'
        rootbone=rig.pose.bones['Root']
        for fc in ef._bone_fcurves(rig,'Root','location'):fc.keyframe_points.clear()
        for f in sorted(sampled):
            v=sampled[f]['Root_location'].lerp(sampled[center]['Root_location'],influence(f))
            rootbone.location=v;rootbone.keyframe_insert(data_path='location',frame=f,group='Root')
        for fc in ef._bone_fcurves(rig,'Root','location'):
            for kp in fc.keyframe_points:kp.interpolation='LINEAR'

def add_secondary(action,clip,end,anchors):
    """Low-frequency overlap on creature anatomy; support ancestors stay fixed."""
    assign(action)
    names=[n for n in analysis['usable_bones'] if n.startswith((
        'Torso','Chest','Head','Jaw','Tail','Shoulder','Arm','Elbow','Hand','Toe'))]
    base={}
    for f in range(end+1):
        scene.frame_set(f);base[f]={}
        for n in names:
            p=rig.pose.bones[n]
            base[f][n]=(p.rotation_quaternion if p.rotation_mode=='QUATERNION'
                        else p.rotation_euler.to_quaternion()).copy().normalized()
    loop=clip in {'idle','walk','run'}
    for i,n in enumerate(names):
        p=rig.pose.bones[n];prop=ef._rotation_prop(rig,n)
        for fc in ef._bone_fcurves(rig,n,prop):fc.keyframe_points.clear()
        family=next((key for key in ('Tail','Shoulder','Elbow','Toe','Jaw','Head','Hand')
                     if n.startswith(key)),n)
        amp={'Tail':.65,'Shoulder':.38,'Elbow':.5,'Toe':.28,'Jaw':.38,
             'Head':.32,'Hand':.42,'Torso':.22,'Chest':.28,'Arm':.3}.get(family,.25)
        if clip=='walk':amp*=2.5
        elif clip=='idle':amp*=2.2
        phase=.31*len(p.parent_recursive)+.57*(i%4)+(.6 if '_L' in n else -.6 if '_R' in n else 0.)
        previous=None
        for f in range(end+1):
            if loop:
                cycles=2 if clip in {'idle','run'} else 1
                angle=math.radians(amp)*math.sin(2*math.pi*cycles*f/end-phase)
            else:
                envelope=math.sin(math.pi*f/end)**2
                if any(abs(f-a)<.5 for a in anchors):envelope=0.
                angle=math.radians(amp)*envelope*math.sin(.43*f-phase)
            axis=Vector((1,0,0)) if family in {'Elbow','Toe','Jaw'} else Vector((0,0,1))
            q=(base[f][n]@Quaternion(axis,angle)).normalized()
            if p.rotation_mode=='QUATERNION':p.rotation_quaternion=q
            else:
                previous=q.to_euler(p.rotation_mode,previous) if previous else q.to_euler(p.rotation_mode)
                p.rotation_euler=previous
            p.keyframe_insert(data_path=prop,frame=f,group=n)
        for fc in ef._bone_fcurves(rig,n,prop):
            for kp in fc.keyframe_points:kp.interpolation='LINEAR'

results={}
for clip in auth:
    action=bpy.data.actions['animation.lizardman.'+clip];assign(action)
    end=int(round(action.frame_range[1]))
    contacts=contacts_for(clip,end,auth[clip]['poses'])
    anchors=sorted({int(m.frame) for m in action.pose_markers
                    if m.name not in {'ready','recover','settle','pass','flight','hold'}})
    # Selected bones are actual deform joints; attachment sockets are excluded.
    scope=[n for n in analysis['usable_bones'] if n not in {'Root','Tool_L','Tool_R'}]
    settings={'strength':.19,'frequency_hz':6.0,'damping':.94,
              'centrifugal_gain':.16,'max_offset_degrees':3.0,
              'sample_step':.5,'max_step_degrees':175.0}
    entry={'contacts':contacts,'anchors':anchors,'selected_bones':scope}
    try:
        prepared=ef.build_auto_refinement(rig,overrides={'driver':'Root'},anchors=anchors,
            contacts=contacts,settings=settings,name='refined.lizardman.'+clip)
        prepared['plan']['bones']=scope
        report=ef.refine_motion(rig,prepared['plan'],prepared['profile'])
        refined=bpy.data.actions[report['action']]
        action.name='authored.lizardman.'+clip
        refined.name='animation.lizardman.'+clip
        refined['gecko_loop']=bool(auth[clip]['loop'])
        refined.use_cyclic=bool(auth[clip]['loop'])
        assign(refined)
        freeze_support(refined,contacts,end)
        add_secondary(refined,clip,end,anchors)
        entry.update({'status':'refined','api_validation':report['validation'],
                      'warnings':report['warnings']})
    except Exception as exc:
        freeze_support(action,contacts,end)
        add_secondary(action,clip,end,anchors)
        entry.update({'status':'authored_only','error':str(exc)})
    results[clip]=entry
    print('REFINE',clip,entry['status'])

assign(bpy.data.actions['animation.lizardman.idle'])
scene.frame_start=0;scene.frame_end=48;scene.frame_set(0)
out=root/'sampleRig/Gourmet2_Lizardman_All_Refined.blend'
bpy.ops.wm.save_as_mainfile(filepath=str(out))
(root/'sampleRig/lizardman_all_refined_report.json').write_text(json.dumps(results,indent=2),encoding='utf-8')
print('EXPORT',la.export_all(str(project),str(root/'sampleRig/gourmet2_lizardman_all_refined.json')))
