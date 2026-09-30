"""Create a non-destructive validation copy from sampleRig/lizardman.blend."""
import bpy,json,math,importlib.util
from pathlib import Path

root=Path(__file__).resolve().parent.parent
spec=importlib.util.spec_from_file_location('ef',root/'blender_addons/epicfight_ai_anim.py')
ef=importlib.util.module_from_spec(spec);spec.loader.exec_module(ef)
rig=next(o for o in bpy.data.objects if o.type=='ARMATURE')
source=bpy.data.actions['animation.lizardman.tail_slam']
rig.animation_data.action=source
if source.slots:rig.animation_data.action_slot=source.slots[0]
analysis=ef.analyze_rig(rig)
suggestions=ef.detect_contact_windows(rig,analysis)
# This validation accepts only high-confidence hand/foot suggestions.
accepted=[c for c in suggestions if c['confidence']>=.7]
for c in accepted:c.pop('confidence',None)
report=ef.refine_motion_auto(rig,accepted,overrides={'driver':analysis['driver']},
    settings={'strength':.25,'centrifugal_gain':.25,'sample_step':.5,
              'max_offset_degrees':6,'max_step_degrees':60},
    name='animation.lizardman.tail_slam.generalized')
assert report['validation']['contact_error']<1e-4
assert all(math.isfinite(x) for _,point in report['com_trajectory'] for x in point)
out=root/'sampleRig/lizardman_generalized_test.blend'
bpy.ops.wm.save_as_mainfile(filepath=str(out))
summary={'source':source.name,'output_action':report['action'],'driver':analysis['driver'],
 'usable_bones':len(analysis['usable_bones']),'animated_bones':len(analysis['animated_bones']),
 'roles':analysis['roles'],'excluded':analysis['exclude'],'accepted_contacts':accepted,
 'validation':report['validation'],'warnings':report['warnings'],'file':str(out)}
(root/'sampleRig/lizardman_generalization_report.json').write_text(json.dumps(summary,indent=2),encoding='utf-8')
print('PASS OUTPUT',json.dumps(summary))
