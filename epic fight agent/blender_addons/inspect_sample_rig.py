import bpy, json
from pathlib import Path
out=[]
for obj in bpy.data.objects:
    if obj.type!='ARMATURE':continue
    bones=[]
    for pb in obj.pose.bones:
        b=pb.bone
        bones.append({'name':pb.name,'parent':pb.parent.name if pb.parent else None,
          'children':[c.name for c in pb.children],'deform':b.use_deform,'length':b.length,
          'head':list(b.head_local),'tail':list(b.tail_local),'rotation_mode':pb.rotation_mode,
          'constraints':[c.type for c in pb.constraints]})
    out.append({'object':obj.name,'world':[list(r) for r in obj.matrix_world],
      'bones':bones,'active_action':obj.animation_data.action.name if obj.animation_data and obj.animation_data.action else None,
      'nla':[(t.name,t.mute,[(s.name,s.action.name if s.action else None) for s in t.strips]) for t in obj.animation_data.nla_tracks] if obj.animation_data else []})
path=Path(bpy.data.filepath).with_name('lizardman_inventory.json')
path.write_text(json.dumps(out,indent=2),encoding='utf-8')
print('INVENTORY',path)
