"""Build a grounded, one-hand-supported capoeira compass kick on the existing rig.
Run inside Blender with exec(compile(open(path).read(), path, 'exec')).
Analytic two-link solves are baked to FK; no runtime constraints are required.
"""
import bpy
import math
import json
import importlib.util
from pathlib import Path
from mathutils import Vector, Quaternion, Matrix

BASE = Path(bpy.data.filepath).parent
spec = importlib.util.spec_from_file_location('com_model', BASE/'blender_addons/com_inertia.py')
com_model = importlib.util.module_from_spec(spec)
spec.loader.exec_module(com_model)
rig = bpy.data.objects['Armature']
scene = bpy.context.scene
STRONG_ROTATION = globals().get('STRONG_ROTATION', False)
aim_previous={}
pole_previous={}
drift_states={}


def rotational_drift(name, target, com, omega, alpha, amount, limit):
    """Bounded rotating-frame response: outward omega^2*r and Euler lag.
    This artistic spring response is not a free-body dynamics solver.
    """
    radius=target-com
    radius.z=0
    force=radius*(omega*omega)-Vector((0,0,alpha)).cross(radius)
    offset,velocity=drift_states.get(name,(Vector(),Vector()))
    dt=1/(scene.render.fps/scene.render.fps_base)/8
    frequency=2*math.pi*3.2
    for _ in range(8):
        velocity+=(force*amount-offset*frequency**2-velocity*1.3*frequency)*dt
        offset+=velocity*dt
        if offset.length>limit:
            offset=offset.normalized()*limit
            outward=velocity.dot(offset.normalized())
            if outward>0:velocity-=offset.normalized()*outward
    drift_states[name]=(offset,velocity)
    return target+offset


def ease(a,b,t):
    u=max(0.,min(1.,(t-a)/(b-a)))
    return u*u*u*(10-15*u+6*u*u)


def aim(name, end):
    pb=rig.pose.bones[name]
    head=pb.head.copy()
    direction=(end-head).normalized()
    rest=aim_previous.get(name,pb.bone.matrix_local.to_quaternion())
    q=(rest @ Vector((0,1,0))).rotation_difference(direction) @ rest
    aim_previous[name]=q.copy()
    pb.matrix=Matrix.Translation(head) @ q.to_matrix().to_4x4()
    bpy.context.view_layer.update()


def limb(upper,lower,target,pole):
    pb=rig.pose.bones[upper]
    start=pb.head.copy()
    l1=pb.bone.length; l2=rig.pose.bones[lower].bone.length
    delta=target-start
    distance=max(1e-6,delta.length)
    direction=delta/distance
    reach=min(l1+l2-.001,max(abs(l1-l2)+.001,distance))
    along=(l1*l1-l2*l2+reach*reach)/(2*reach)
    normal=pole-direction*pole.dot(direction)
    if normal.length<1e-5: normal=direction.cross(Vector((1,0,0)))
    if upper.startswith('Arm_'):
        up=Vector((0,0,1))
        normal=up-direction*up.dot(direction)
        if normal.length<1e-4:normal=Vector((1,0,0))
    elif upper in pole_previous:
        transported=pole_previous[upper]-direction*pole_previous[upper].dot(direction)
        if transported.length>1e-5:
            if normal.dot(transported)<0:normal.negate()
            normal=transported.normalized().lerp(normal.normalized(),.15)
    pole_previous[upper]=normal.normalized()
    joint=start+direction*along+normal.normalized()*math.sqrt(max(0,l1*l1-along*along))
    aim(upper,joint)
    aim(lower,start+direction*reach)
    return max(0.,distance-reach)


source=rig.animation_data.action
if source: source.use_fake_user=True
source_com={}
if STRONG_ROTATION:
    for f in range(1,161):
        scene.frame_set(f)
        source_com[f]=rig.matrix_world.inverted() @ com_model.center_of_mass(rig)
action=bpy.data.actions.new('Capoeira_Rotational_Inertia_R' if STRONG_ROTATION else 'Capoeira_MeiaLuaDeCompasso_R')
action.use_fake_user=True
rig.animation_data.action=action
records=[]; previous={}
free_pos=None;free_velocity=Vector()
foot=Vector((-.12,.04,.015))
hand=Vector((.12,-.18,.025))
for f in range(1,161):
    scene.frame_set(f)
    for pb in rig.pose.bones:
        pb.matrix_basis=Matrix.Identity(4)
    down=ease(18,48,f)*(1-ease(108,148,f))
    spin=ease(43,118,f)
    yaw=math.radians(-20+360*spin)
    fps=scene.render.fps/scene.render.fps_base
    yaw_at=lambda frame:math.radians(-20+360*ease(43,118,frame))
    omega=(yaw_at(f+.5)-yaw_at(f-.5))*fps
    alpha=(yaw_at(f+1)-2*yaw_at(f)+yaw_at(f-1))*fps*fps
    momentum=math.tanh(omega/7.) if STRONG_ROTATION else 0.
    heading=Quaternion((0,0,1),yaw)
    root=rig.pose.bones['Root']
    pelvis=Vector((0,0,.69)).lerp(foot+heading@Vector((.08,.10,.59)),down)
    if STRONG_ROTATION:
        pelvis+=Vector((hand.x-pelvis.x,hand.y-pelvis.y,0))*.12*momentum
    root.matrix=Matrix.Translation(pelvis) @ heading.to_matrix().to_4x4() @ root.bone.matrix_local.to_quaternion().to_matrix().to_4x4()
    bpy.context.view_layer.update()
    lean=math.radians(12+99*down)
    torso_dir=heading @ Vector((0,-math.sin(lean),math.cos(lean)))
    if STRONG_ROTATION:
        torso_dir=Quaternion((0,0,1),-.09*momentum) @ torso_dir
    aim('Torso',rig.pose.bones['Torso'].head+torso_dir)
    chest_dir=torso_dir
    if STRONG_ROTATION:
        chest_dir=Quaternion((0,0,1),-.12*momentum) @ (heading @ Vector((.06*momentum,-math.sin(lean-.06*momentum),math.cos(lean-.06*momentum))))
    aim('Chest',rig.pose.bones['Chest'].head+chest_dir)
    head_dir=heading @ Vector((0,-math.sin(lean-.22),math.cos(lean-.22)))
    if STRONG_ROTATION:
        head_dir=Quaternion((0,0,1),-.23*momentum) @ head_dir
    aim('Head',rig.pose.bones['Head'].head+head_dir)
    # Supporting foot stays fixed; the heel makes a broad trailing arc.
    pole=heading @ Vector((0,-1,.15))
    left_error=limb('Thigh_L','Leg_L',foot,pole)
    lift=ease(42,63,f)*(1-ease(97,124,f))
    hip=rig.pose.bones['Thigh_R'].head.copy()
    kick_dir=heading @ Vector((.20,.94,.27)).normalized()
    swing=hip+kick_dir*.735
    rest_foot=heading @ Vector((.26,.10,.015))
    rest_foot.z=.015
    target=rest_foot.lerp(swing,lift)
    if STRONG_ROTATION:
        target=rotational_drift('kick',target,source_com[f],omega,alpha,.85,.11*lift)
        displacement=target-hip
        if displacement.length>.65:
            reach=.65+.085*math.tanh((displacement.length-.65)/.085)
            target=hip+displacement.normalized()*reach
    right_error=limb('Thigh_R','Leg_R',target,pole)
    hand_error=0.
    for side,sign in [('R',1),('L',-1)]:
        shoulder=rig.pose.bones['Shoulder_'+side]
        # Clavicle motion brings the long game-rig shoulder segments inward.
        offset=heading @ Vector((sign*(.34-.15*down),0,-(.12+.20*down)))
        if STRONG_ROTATION:
            offset=Quaternion((0,0,1),-.10*momentum) @ offset
        if side=='R':
            contact=ease(28,48,f)*(1-ease(103,125,f))
            reach_distance=(hand-shoulder.head).length
            reach_dir=(hand-shoulder.head).normalized()+heading@Vector((.10,0,0))
            if STRONG_ROTATION:
                toward=(hand-shoulder.head).normalized()
                side_axis=heading@Vector((1,0,0))
                side_axis=(side_axis-toward*side_axis.dot(toward)).normalized()
                length=shoulder.bone.length
                shoulder_angle=.42*math.exp(-((reach_distance-length)/.20)**2)
                reach_dir=toward*math.cos(shoulder_angle)+side_axis*math.sin(shoulder_angle)
            offset=offset.normalized().lerp(reach_dir.normalized(),contact)
        aim(shoulder.name,shoulder.head+offset)
        arm=rig.pose.bones['Arm_'+side]
        guard=arm.head+heading @ Vector((sign*.10,-.30,-.20))
        if side=='R':
            contact=ease(28,48,f)*(1-ease(103,125,f))
            target_hand=guard.lerp(hand,contact)
        else:
            target_hand=arm.head+heading @ Vector((-.20,-.24,.12-.12*down))
            com=com_model.center_of_mass(rig)
            radius=(target_hand-com).length
            gain=max(.65,min(1.8,(.45**2+.12)/(radius*radius+.12)))
            omega=2*math.pi*5*math.sqrt(gain)
            if free_pos is None:free_pos=target_hand.copy()
            dt=1/(scene.render.fps/scene.render.fps_base)/6
            for step in range(6):
                free_velocity+=((target_hand-free_pos)*omega*omega-free_velocity*1.9*omega)*dt
                free_pos+=free_velocity*dt
            target_hand=target_hand.lerp(free_pos,.4)
            if STRONG_ROTATION:
                target_hand=rotational_drift('free_arm',target_hand,source_com[f],omega,alpha,1.5,.18)
        error=limb('Arm_'+side,'Hand_'+side,target_hand,heading @ Vector((sign,0,-.2)))
        if side=='R' and 48<=f<=103: hand_error=error
    if STRONG_ROTATION:
        # These short side branches are joint deformation helpers, not extra limbs.
        for helper,child in [('Knee_R','Leg_R'),('Knee_L','Leg_L'),('Elbow_R','Hand_R'),('Elbow_L','Hand_L')]:
            distal=rig.pose.bones[child]
            proximal=distal.parent
            bend=(proximal.tail-proximal.head).angle(distal.tail-distal.head)
            rig.pose.bones[helper].rotation_quaternion=Quaternion((1,0,0),bend*.5)
    for pb in rig.pose.bones:
        q=pb.rotation_quaternion
        if pb.name in previous and previous[pb.name].dot(q)<0:q.negate()
        limit_free_left=STRONG_ROTATION and pb.name in ('Shoulder_L','Arm_L','Hand_L')
        limit_free_right=pb.name in ('Shoulder_R','Arm_R','Hand_R') and not 45<=f<=103
        if pb.name in previous and (limit_free_left or limit_free_right):
            angle=2*math.acos(min(1.,abs(previous[pb.name].dot(q.normalized()))))
            if angle>math.radians(14):
                pb.rotation_quaternion=previous[pb.name].slerp(q,math.radians(14)/angle)
                q=pb.rotation_quaternion
        previous[pb.name]=q.copy()
        pb.keyframe_insert(data_path='rotation_quaternion',frame=f,group=pb.name)
        if pb.name=='Root':pb.keyframe_insert(data_path='location',frame=f,group=pb.name)
    bpy.context.view_layer.update()
    palm=rig.pose.bones['Hand_R']
    if palm.tail.z<.015:
        direction=(palm.tail-palm.head).normalized()
        z=max(-1.,min(1.,(.015-palm.head.z)/palm.bone.length))
        horizontal=Vector((direction.x,direction.y,0)).normalized()*math.sqrt(max(0.,1-z*z))
        direction=Vector((horizontal.x,horizontal.y,z))
        aim('Hand_R',palm.head+direction*palm.bone.length)
        if previous['Hand_R'].dot(palm.rotation_quaternion)<0:palm.rotation_quaternion.negate()
        previous['Hand_R']=palm.rotation_quaternion.copy()
        palm.keyframe_insert(data_path='rotation_quaternion',frame=f,group='Hand_R')
        bpy.context.view_layer.update()
    records.append({'frame':f,'com':list(com_model.center_of_mass(rig)),
                    'foot_error':(rig.pose.bones['Leg_L'].tail-foot).length,
                    'hand_error':(rig.pose.bones['Hand_R'].tail-hand).length if 48<=f<=103 else None,
                    'kick_height':rig.pose.bones['Leg_R'].tail.z,
                    'unreachable':max(left_error,right_error,hand_error),
                    'omega_rad_s':omega,'alpha_rad_s2':alpha,
                    'rotational_offsets':{name:list(state[0]) for name,state in drift_states.items()}})
for layer in action.layers:
    for strip in layer.strips:
        for bag in strip.channelbags:
            for fc in bag.fcurves:
                for kp in fc.keyframe_points:kp.interpolation='LINEAR'
marker=bpy.data.objects.new('COM_Capoeira_Rotational' if STRONG_ROTATION else 'COM_Capoeira',None)
scene.collection.objects.link(marker)
marker.empty_display_type='SPHERE';marker.empty_display_size=.045;marker.show_in_front=True
for row in records:
    marker.location=row['com'];marker.keyframe_insert(data_path='location',frame=row['frame'])
for ob in bpy.data.objects:
    if ob.name.startswith('COM_') and ob!=marker:ob.hide_set(True)
for name,f in [('Ginga',1),('Lower / load',24),('Hand contact',48),('Heel sweep',76),('Release',104),('Recover',132)]:
    action.pose_markers.new(name).frame=f
scene.frame_start=1;scene.frame_end=160;scene.frame_set(76)
report={'action':action.name,'frames':records,'max_support_foot_error':max(r['foot_error'] for r in records),
        'max_support_hand_error':max(r['hand_error'] or 0 for r in records),
        'max_unreachable':max(r['unreachable'] for r in records)}
(BASE/('capoeira_rotational_validation.json' if STRONG_ROTATION else 'capoeira_validation.json')).write_text(json.dumps(report,indent=2),encoding='utf-8')
result={k:v for k,v in report.items() if k!='frames'}
