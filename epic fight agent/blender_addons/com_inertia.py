"""Pose-driven, distance-dependent angular springs for the Epic Fight rig.

This is an animation model, not a rigid-body simulation. Segment masses are
editable estimates. Root motion is retained; low foot poses suppress leg edits.
"""
import math
import bpy
from mathutils import Vector, Quaternion

MASSES = {'Torso': .25, 'Chest': .25, 'Head': .08,
          'Thigh_R': .10, 'Thigh_L': .10, 'Leg_R': .05, 'Leg_L': .05,
          'Shoulder_R': .015, 'Shoulder_L': .015,
          'Arm_R': .025, 'Arm_L': .025, 'Hand_R': .02, 'Hand_L': .02}


def center_of_mass(obj, masses=None):
    """World-space evaluated segment midpoint estimate, normalized to present bones."""
    masses = MASSES if masses is None else masses
    rig = obj.evaluated_get(bpy.context.evaluated_depsgraph_get())
    segments = [(rig.pose.bones[n], w) for n, w in masses.items()
                if n in rig.pose.bones and w > 0 and math.isfinite(w)]
    total = sum(w for _, w in segments)
    if total <= 0:
        raise ValueError('No positive segment masses on this rig')
    return sum((rig.matrix_world @ ((b.head + b.tail) * .5) * w
                for b, w in segments), Vector()) / total


def set_frame(scene, frame):
    scene.frame_set(math.floor(frame), subframe=frame-math.floor(frame))


def apply_com_inertia(obj, strength=.45, frequency=5.0, damping=.95,
                      max_offset_degrees=9.0, masses=None):
    """Copy active action, bake angular spring response every frame, add COM marker.

    Acceleration = omega^2 * rotation_error - 2*zeta*omega*velocity.
    omega^2 increases as distance to COM decreases, bounded to 0.65..1.8
    times the base stiffness. Quaternion-log errors avoid component springs.
    All source targets are sampled before any output is written.
    """
    import importlib.util
    from pathlib import Path
    spec = importlib.util.spec_from_file_location('efai_helpers',
              Path(__file__).with_name('epicfight_ai_anim.py'))
    helper = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(helper)
    if not 0 <= strength <= 1 or not all(math.isfinite(v) and v > 0
            for v in (frequency, damping, max_offset_degrees)):
        raise ValueError('Invalid inertia settings')
    source = helper._get_action(obj)
    slot = obj.animation_data.action_slot
    scene = bpy.context.scene
    saved_frame = scene.frame_current + scene.frame_subframe
    start, end = map(float, source.frame_range)
    if end <= start:
        raise ValueError('Action requires a nonzero duration')
    frames = sorted({start, end} | set(float(f) for f in
                    range(math.ceil(start), math.floor(end)+1)))
    names = [b.name for b in obj.pose.bones if b.name != 'Root'
             and b.rotation_mode == 'QUATERNION'
             and len(helper._bone_fcurves(obj,b.name,'rotation_quaternion')) == 4]
    if not names:
        raise ValueError('No animated quaternion bones')
    targets = {n: [] for n in names}
    distances = {n: [] for n in names}
    coms, feet = [], {'R': [], 'L': []}
    curves = {n: {f.array_index:f for f in
              helper._bone_fcurves(obj,n,'rotation_quaternion')} for n in names}
    try:
        for frame in frames:
            set_frame(scene, frame)
            com = center_of_mass(obj, masses)
            coms.append(com)
            rig = obj.evaluated_get(bpy.context.evaluated_depsgraph_get())
            for n in names:
                b = rig.pose.bones[n]
                distances[n].append((rig.matrix_world @ ((b.head+b.tail)*.5)-com).length)
                q = Quaternion([curves[n][i].evaluate(frame) for i in range(4)]).normalized()
                if targets[n] and targets[n][-1].dot(q)<0: q.negate()
                targets[n].append(q)
            for side in feet:
                b = rig.pose.bones.get('Leg_'+side)
                feet[side].append((rig.matrix_world @ b.tail).z if b else 0)
        scale = max((obj.matrix_world.to_3x3() @ Vector((0,0,1))).length, 1e-6)
        radius = .45*scale
        fps = scene.render.fps/scene.render.fps_base
        limit = math.radians(max_offset_degrees)
        output, gains = {}, {}
        for n in names:
            q = targets[n][0].copy(); velocity = Vector()
            output[n] = [q.copy()]; gains[n] = []
            for i in range(len(frames)):
                gain = max(.65, min(1.8, (radius*radius+.12*scale*scale)/
                                    (distances[n][i]**2+.12*scale*scale)))
                gains[n].append(gain)
                if not i: continue
                dt = (frames[i]-frames[i-1])/fps
                omega = 2*math.pi*frequency*math.sqrt(gain)
                steps = max(4, math.ceil(dt*omega/.15))
                h = dt/steps
                for j in range(steps):
                    target = targets[n][i-1].slerp(targets[n][i], (j+1)/steps)
                    error = q.conjugated() @ target
                    if error.w < 0: error.negate()
                    axis, angle = error.to_axis_angle()
                    velocity += (axis*angle*omega*omega-velocity*(2*damping*omega))*h
                    turn = velocity.length*h
                    if turn > 1e-10: q = (q @ Quaternion(velocity.normalized(),turn)).normalized()
                target = targets[n][i]
                dot = min(1.,abs(q.dot(target)))
                offset = 2*math.acos(dot)
                blend = strength * min(1.,limit/max(offset,1e-9))
                # Fade corrections to zero at endpoints, preserving authored contacts.
                edge = min((frames[i]-start)/5.,(end-frames[i])/5.,1.)
                blend *= edge*edge*(3-2*edge)
                if n.startswith(('Thigh_', 'Leg_')):
                    side = n[-1]
                    height = feet[side][i]-min(feet[side])
                    t = max(0.,min(1.,(height-.06*scale)/(.12*scale)))
                    blend *= t*t*(3-2*t)
                result = target.slerp(q, blend).normalized()
                if output[n][-1].dot(result)<0: result.negate()
                output[n].append(result)
        target_action = source.copy()
        target_action.name = source.name+'_COM_Inertia'
        source.use_fake_user = target_action.use_fake_user = True
        obj.animation_data.action = target_action
        if slot: obj.animation_data.action_slot = target_action.slots[slot.identifier]
        for n in names:
            for fc in helper._bone_fcurves(obj,n,'rotation_quaternion'):
                fc.keyframe_points.clear()
                for frame,q in zip(frames,output[n]):
                    kp = fc.keyframe_points.insert(frame,q[fc.array_index],options={'FAST'})
                    kp.interpolation = 'LINEAR'
                fc.update()
        marker = bpy.data.objects.new('COM_'+target_action.name,None)
        scene.collection.objects.link(marker)
        marker.empty_display_type='SPHERE'; marker.empty_display_size=.05*scale
        marker.show_in_front=True
        marker['description']='Estimated mass-weighted COM of the corrected pose at each frame'
        rows=[]
        for i,frame in enumerate(frames):
            set_frame(scene,frame)
            com = center_of_mass(obj,masses)
            marker.location=com
            marker.keyframe_insert(data_path='location',frame=frame)
            rows.append({'frame':frame,'com':list(com),'source_com':list(coms[i]),
                         'distance':{n:distances[n][i] for n in names},
                         'stiffness_gain':{n:gains[n][i] for n in names}})
        for fc in helper._iter_fcurves(marker.animation_data.action,marker):
            for kp in fc.keyframe_points: kp.interpolation='LINEAR'
        return {'source':source.name,'action':target_action.name,'marker':marker.name,
                'strength':strength,'frequency_hz':frequency,'damping':damping,
                'max_correction_degrees':max_offset_degrees*strength,
                'mass_weights':MASSES if masses is None else masses,'frames':rows}
    finally:
        set_frame(scene,saved_frame)
