"""Render six key poses of the human-inspired Lizardman test."""
from pathlib import Path
import bpy
from mathutils import Vector

root = Path(__file__).resolve().parents[1]
scene = bpy.context.scene
rig = bpy.data.objects['Lizardman']
bpy.data.objects['Cube'].hide_render = True
camera = bpy.data.objects['Camera']
scene.camera = camera
camera.location = (3.0, -5.0, 2.35)
camera.rotation_euler = (Vector((0, 0, 1)) - camera.location).to_track_quat('-Z', 'Y').to_euler()
camera.data.type = 'ORTHO'
camera.data.ortho_scale = 3.4
scene.render.engine = 'BLENDER_WORKBENCH'
scene.render.image_settings.file_format = 'PNG'
scene.render.resolution_x = 512
scene.render.resolution_y = 512
scene.render.resolution_percentage = 100
for action_name, frames in [('martial.jab_cross_hook', (9, 18, 27)),
                            ('martial.slip_elbow_knee', (5, 14, 24))]:
    action = bpy.data.actions[action_name]
    rig.animation_data.action = action
    if action.slots:
        rig.animation_data.action_slot = action.slots[0]
    for frame in frames:
        scene.frame_set(frame)
        scene.render.filepath = str(root / f'sampleRig/{action_name.replace(".", "_")}_f{frame:02d}.png')
        bpy.ops.render.render(write_still=True)
        print('PREVIEW', scene.render.filepath)
