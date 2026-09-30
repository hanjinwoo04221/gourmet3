"""Measure hand spread relative to the chest in Lizardman combat actions."""
import bpy,json,math
from pathlib import Path

rig=bpy.data.objects['Lizardman'];scene=bpy.context.scene
out={}
for action_name in ('lizardman.cross_claw_knee','lizardman.low_sweep_double_rake'):
    a=bpy.data.actions[action_name];rig.animation_data.action=a
    if a.slots:rig.animation_data.action_slot=a.slots[0]
    first,end=map(lambda x:int(round(x)),a.frame_range)
    result={side:{'lateral':[],'shoulder_y_degrees':[]} for side in ('L','R')}
    for frame in range(first,end+1):
        scene.frame_set(frame)
        chest=rig.pose.bones['Chest']
        for side in ('L','R'):
            hand=rig.pose.bones['Hand_'+side]
            shoulder=rig.pose.bones['Shoulder_'+side]
            local=chest.matrix.inverted()@hand.tail
            result[side]['lateral'].append([frame,local.x])
            result[side]['shoulder_y_degrees'].append([frame,math.degrees(shoulder.rotation_euler.y)])
    out[action_name]={}
    for side,values in result.items():
        xs=[v for _,v in values['lateral']]
        ys=[v for _,v in values['shoulder_y_degrees']]
        out[action_name][side]={'hand_lateral_range':max(xs)-min(xs),
            'shoulder_y_range_degrees':max(ys)-min(ys),'hand_lateral_min':min(xs),
            'hand_lateral_max':max(xs)}
path=Path(bpy.data.filepath)
report=path.with_name(path.stem+'_lateral_metrics.json')
report.write_text(json.dumps(out,indent=2),encoding='utf-8')
print('LATERAL_METRICS',json.dumps(out))
