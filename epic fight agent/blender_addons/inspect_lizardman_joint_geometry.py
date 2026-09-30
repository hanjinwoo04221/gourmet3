import bpy
rig=bpy.data.objects['Lizardman']
for pb in rig.pose.bones:
    b=pb.bone
    print(b.name,'parent=',b.parent.name if b.parent else None,'length=',round(b.length,4),
          'deform=',b.use_deform,'constraints=',[c.type for c in pb.constraints],
          'mode=',pb.rotation_mode)
