"""Measure shoulder-axis effect on hand position for this specific rig."""
import bpy,math,json
from mathutils import Quaternion,Vector
rig=bpy.data.objects['Lizardman'];scene=bpy.context.scene
a=bpy.data.actions['animation.lizardman.guard'];rig.animation_data.action=a
if a.slots:rig.animation_data.action_slot=a.slots[0]
scene.frame_set(1)
out={}
for side in ('L','R'):
    shoulder=rig.pose.bones['Shoulder_'+side]
    hand=rig.pose.bones['Hand_'+side]
    base=shoulder.rotation_euler.copy()
    bpy.context.view_layer.update()
    origin=(rig.matrix_world@hand.tail).copy()
    samples={}
    for name,axis in [('X',(1,0,0)),('Y',(0,1,0)),('Z',(0,0,1))]:
        q=base.to_quaternion()@Quaternion(Vector(axis),math.radians(15))
        shoulder.rotation_euler=q.to_euler(shoulder.rotation_mode,base)
        bpy.context.view_layer.update()
        point=rig.matrix_world@hand.tail
        samples[name]=list(point-origin)
    shoulder.rotation_euler=base
    out[side]=samples
print('SHOULDER_AXIS_PROBE',json.dumps(out))
