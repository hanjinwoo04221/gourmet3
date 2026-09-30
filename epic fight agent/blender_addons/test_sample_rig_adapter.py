"""Run against a .blend path passed after --, without saving the source file."""
import bpy,sys,math,importlib.util
from pathlib import Path

root=Path(__file__).resolve().parent.parent
spec=importlib.util.spec_from_file_location('ef',root/'blender_addons/epicfight_ai_anim.py')
ef=importlib.util.module_from_spec(spec);spec.loader.exec_module(ef)
rig=next(o for o in bpy.data.objects if o.type=='ARMATURE')
analysis=ef.analyze_rig(rig)
assert analysis['driver'] in rig.pose.bones
assert {'tail','head','foot','hand'} <= {r for r,v in analysis['roles'].items() if v}
assert all(n in rig.pose.bones for n in analysis['masses'])
assert abs(sum(analysis['masses'].values())-1)<1e-6
assert {'Knee_R','Knee_L','Elbow_R','Elbow_L'} <= set(analysis['exclude'])
assert {'Tool_R','Tool_L'} <= set(analysis['exclude'])
suggestions=ef.detect_contact_windows(rig,analysis)
prepared=ef.build_auto_refinement(rig,contacts=[],anchors=[])
assert not prepared['requires_contact_review']
assert prepared['plan']['bones']
source=rig.animation_data.action;slot=rig.animation_data.action_slot;before=len(bpy.data.actions)
report=ef.refine_motion_auto(rig,[],settings={'strength':.12,'centrifugal_gain':.1,
                             'sample_step':1.,'max_step_degrees':120})
assert report['source']==source.name and report['modified_bones']
assert len(bpy.data.actions)==before+1
assert report['validation']['max_sample_rotation_degrees']<=120
assert all(math.isfinite(x) for _,v in report['com_trajectory'] for x in v)
rig.animation_data.action=source;rig.animation_data.action_slot=source.slots[slot.identifier]
try:ef.refine_motion_auto(rig,None)
except ValueError:pass
else:raise AssertionError('Unreviewed contacts accepted')
print('PASS SAMPLE',rig.name,'driver',analysis['driver'],'usable',len(analysis['usable_bones']),
      'animated',len(analysis['animated_bones']),'suggested_contacts',len(suggestions),
      'modified',len(report['modified_bones']))
