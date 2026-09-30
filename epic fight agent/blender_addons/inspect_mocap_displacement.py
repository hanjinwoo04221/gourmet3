"""Compare captured hand/foot displacement against the current retarget."""
from pathlib import Path
import bpy
from mathutils import Matrix,Vector

root=Path(__file__).resolve().parents[1]
rig=bpy.data.objects['Lizardman'];scene=bpy.context.scene
for name,file,first,impact,target,source in [
    ('mocap.boxing_two_strikes','14_01.bvh',1531,1591,'Hand_R','RightHand'),
    ('mocap.front_kick_right','135_04.bvh',311,376,'Foot_R','RightFoot')]:
    action=bpy.data.actions[name];rig.animation_data.action=action
    if action.slots:rig.animation_data.action_slot=action.slots[0]
    scene.frame_set(1)
    first_target=rig.pose.bones[target].tail.copy()
    scene.frame_set(1+(impact-first)//5)
    impact_target=rig.pose.bones[target].tail.copy()
    print('TARGET',name,target,list(first_target),list(impact_target),
          'displacement',list(impact_target-first_target))
    bpy.ops.import_anim.bvh(filepath=str(root/'sampleRig/mocap_reference'/file),rotate_mode='QUATERNION')
    actor=bpy.context.object
    actor_action=actor.animation_data.action
    actor.animation_data.action=actor_action
    scene.frame_set(first)
    first_source=actor.pose.bones[source].head.copy()
    shoulders=actor.pose.bones['RightArm'].head-actor.pose.bones['LeftArm'].head
    source_width=shoulders.length
    scene.frame_set(impact)
    impact_source=actor.pose.bones[source].head.copy()
    print('SOURCE',name,source,'displacement',list(impact_source-first_source),
          'source_width',source_width)
    bpy.data.objects.remove(actor,do_unlink=True)
