"""Subframe and support checks for saved CMU-retargeted Lizardman actions."""
import json
import math
from pathlib import Path
import bpy

root=Path(__file__).resolve().parents[1]
items=json.loads((root/'sampleRig/lizardman_cmu_mocap_report.json').read_text())
rig=bpy.data.objects['Lizardman'];scene=bpy.context.scene
names=[b.name for b in rig.data.bones if b.use_deform and not b.name.startswith('Tool')]
out={}
for item in items:
    action=bpy.data.actions[item['action']]
    rig.animation_data.action=action
    if action.slots:rig.animation_data.action_slot=action.slots[0]
    start,end=[int(x) for x in action.frame_range]
    previous=None;maximum=0;worst=None;moving=[];contacts={}
    effector='Hand_R' if item['action']=='mocap.boxing_two_strikes' else 'Foot_R'
    impact=13 if effector=='Hand_R' else 14
    scene.frame_set(1)
    start_point=(rig.matrix_world@rig.pose.bones[effector].tail).copy()
    scene.frame_set(impact)
    impact_point=(rig.matrix_world@rig.pose.bones[effector].tail).copy()
    effector_displacement=(impact_point-start_point).length
    for half in range(2*start,2*end+1):
        frame=half*.5
        scene.frame_set(math.floor(frame),subframe=frame-math.floor(frame))
        quats={name:rig.pose.bones[name].matrix.to_quaternion().normalized().copy()
               for name in names}
        if previous:
            steps={name:math.degrees(2*math.acos(min(1,abs(previous[name].dot(quats[name])))))
                   for name in names}
            name=max(steps,key=steps.get)
            if steps[name]>maximum:maximum=steps[name];worst=[frame,name]
            moving.append(sum(value>.1 for value in steps.values()))
        previous=quats
        for contact in item['contacts']:
            if contact['start']<=frame<=contact['end']:
                key=f"{contact['bone']}:{contact['start']}-{contact['end']}"
                foot=rig.pose.bones[contact['bone']]
                contacts.setdefault(key,[]).append(tuple(rig.matrix_world@foot.tail))
    drift={key:max(math.dist(points[0],point) for point in points)
           for key,points in contacts.items()}
    assert maximum<85
    assert item['validation']['contact_error']<1e-4
    assert effector_displacement>(.2 if effector=='Hand_R' else .4), 'Mocap effectors barely moved'
    out[item['action']]={'max_half_frame_rotation_degrees':maximum,
                         'worst_step':worst,
                         'median_moving_joints':sorted(moving)[len(moving)//2],
                         'contact_world_tail_drift':drift,
                         'mapped_bones':len(item['mapped_bones'])}
    out[item['action']]['effector_displacement']={effector:effector_displacement}
(root/'sampleRig/lizardman_cmu_mocap_validation.json').write_text(json.dumps(out,indent=2))
print('CMU_RETARGET_VALIDATION',json.dumps(out))
