"""Render selected capture-driven poses for visual review."""
from pathlib import Path
import bpy
from mathutils import Vector

root=Path(__file__).resolve().parents[1]
scene=bpy.context.scene;rig=bpy.data.objects['Lizardman']
bpy.data.objects['Cube'].hide_render=True
camera=bpy.data.objects['Camera'];scene.camera=camera
camera.location=(3,-5,2.35)
camera.rotation_euler=(Vector((0,0,1))-camera.location).to_track_quat('-Z','Y').to_euler()
camera.data.type='ORTHO';camera.data.ortho_scale=3.5
scene.render.engine='BLENDER_WORKBENCH'
scene.render.image_settings.file_format='PNG'
scene.render.resolution_x=512;scene.render.resolution_y=512
scene.render.resolution_percentage=100
for name,frames in [('mocap.boxing_two_strikes',(1,13,22,37)),
                    ('mocap.front_kick_right',(1,8,14,24,31))]:
    action=bpy.data.actions[name]
    rig.animation_data.action=action
    if action.slots:rig.animation_data.action_slot=action.slots[0]
    for frame in frames:
        scene.frame_set(frame)
        scene.render.filepath=str(root/f'sampleRig/{name.replace(".","_")}_f{frame:02d}.png')
        bpy.ops.render.render(write_still=True)
        print('PREVIEW',scene.render.filepath)
