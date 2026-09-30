import bpy
from pathlib import Path
from mathutils import Vector
root=Path(__file__).resolve().parent.parent
bpy.ops.wm.open_mainfile(filepath=str(root/'sampleRig'/'Lizardman_Reference_Timing_Test.blend'))
rig=bpy.data.objects['Lizardman']
for pb in rig.pose.bones:
    if any(x in pb.name.lower() for x in ('arm','hand','elbow','leg','knee','foot','shoulder','thigh')):
        b=pb.bone
        print(pb.name,'parent',pb.parent.name if pb.parent else None,'head',tuple(round(x,3) for x in b.head_local),
              'tail',tuple(round(x,3) for x in b.tail_local),'children',[c.name for c in pb.children],
              'mode',pb.rotation_mode,'deform',b.use_deform)
