"""Render three impact frames for quick visual review."""
import math
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
target = Vector((0.0, 0.0, 1.0))
camera.rotation_euler = (target - camera.location).to_track_quat('-Z', 'Y').to_euler()
camera.data.type = 'ORTHO'
camera.data.ortho_scale = 3.5
scene.render.engine = 'BLENDER_WORKBENCH'
scene.render.image_settings.file_format = 'PNG'
scene.render.resolution_x = 512
scene.render.resolution_y = 512
scene.render.resolution_percentage = 100
scene.render.film_transparent = False
for name, frame in [('test.hook_claw_bite', 15),
                    ('test.tail_pivot_rake', 16),
                    ('test.low_pounce_double_rake', 25)]:
    action = bpy.data.actions[name]
    rig.animation_data.action = action
    if action.slots:
        rig.animation_data.action_slot = action.slots[0]
    scene.frame_set(frame)
    scene.render.filepath = str(root / ('sampleRig/lizardman_timing_' + name.split('.')[-1] + '.png'))
    bpy.ops.render.render(write_still=True)
    print('PREVIEW', scene.render.filepath)
