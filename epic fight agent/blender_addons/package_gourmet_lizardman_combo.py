"""Build the new clips from gourmet2's own rig and stage GeckoLib output."""
import bpy,importlib.util,runpy
from pathlib import Path
project=Path('C:/Users/hanjw/Downloads/gourmet2-main/gourmet2-main')
workspace=Path(__file__).resolve().parent.parent
runpy.run_path(str(workspace/'blender_addons/build_lizardman_adaptive_combo.py'))
for old in ('lizardman.duck_rake_bite','lizardman.hop_tail_claw'):
    bpy.data.actions[old].name='animation.'+old
tool=project/'tools/blender/lizardman_anim.py'
spec=importlib.util.spec_from_file_location('gourmet_lizardman_export',tool)
module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module)
out=workspace/'sampleRig/gourmet2_lizardman.animation.new.json'
print('EXPORTED',module.export_all(str(project),str(out)))
rig=bpy.data.objects['Lizardman'];rig.animation_data.action=bpy.data.actions['animation.lizardman.duck_rake_bite']
if rig.animation_data.action.slots:rig.animation_data.action_slot=rig.animation_data.action.slots[0]
bpy.ops.wm.save_as_mainfile(filepath=str(workspace/'sampleRig/Gourmet2_Lizardman_New_Combos.blend'))
