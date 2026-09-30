"""Render key beats of the native-action timing test for visual inspection."""
from pathlib import Path
import bpy
from mathutils import Vector

root = Path(__file__).resolve().parent.parent
bpy.ops.wm.open_mainfile(filepath=str(root / 'sampleRig' / 'Lizardman_Reference_Timing_Test.blend'))
scene = bpy.context.scene
rig = bpy.data.objects['Lizardman']
if 'Cube' in bpy.data.objects:
    bpy.data.objects['Cube'].hide_render = True
camera = bpy.data.objects['Camera']
scene.camera = camera
camera.location = (3.0, -5.0, 2.35)
camera.rotation_euler = (Vector((0, 0, 1)) - camera.location).to_track_quat('-Z', 'Y').to_euler()
camera.data.type = 'ORTHO'
camera.data.ortho_scale = 3.5
scene.render.engine = 'BLENDER_WORKBENCH'
scene.render.image_settings.file_format = 'PNG'
scene.render.resolution_x = 400
scene.render.resolution_y = 400
scene.render.resolution_percentage = 100
for action_name, label, frames in [
    ('Lizardman_Claw_Balanced_Articulated', 'claw_bal', [1, 8, 12, 17, 23, 29]),
    ('Lizardman_Tail_Balanced_Articulated', 'tail_bal', [1, 8, 12, 20, 28, 37]),
]:
    action = bpy.data.actions[action_name]
    rig.animation_data.action = action
    if action.slots:
        rig.animation_data.action_slot = action.slots[0]
    for frame in frames:
        scene.frame_set(frame)
        scene.render.filepath = str(root / 'sampleRig' / f'ref_timing_{label}_{frame:02d}.png')
        bpy.ops.render.render(write_still=True)
