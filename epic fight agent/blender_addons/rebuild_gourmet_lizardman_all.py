"""Author every Lizardman clip from scratch with the epicfight API.

Design rules that make the clips link smoothly, by construction rather than by
tuning:

  * every one-shot starts AND ends on STANCE, so any transition between a
    one-shot and idle (or two one-shots) begins and ends on the same pose
  * the loops also pass through STANCE-compatible poses at their wrap point
  * poses are given as the direction each part should POINT, and converted to the
    rig's local rotation with the limb axes from lizardman_mixamo.LIMB_AXIS,
    because this rig's bone axes are +Y stubs and do not follow the parts

`author_motion` receives local_quaternions for that reason: passing `directions`
would align each stub's own +Y axis, aiming the parts 90 deg off.
"""
import importlib.util
import json
import math
import sys
from pathlib import Path

import bpy
from mathutils import Quaternion, Vector

ROOT = Path(__file__).resolve().parent.parent
PROJECT = Path('C:/Users/hanjw/Downloads/gourmet2-main/gourmet2-main')
sys.path.insert(0, str(PROJECT / "tools" / "blender"))
import lizardman_anim as LA       # noqa: E402
import lizardman_mixamo as MX     # noqa: E402

spec = importlib.util.spec_from_file_location("ef", ROOT / "blender_addons" / "epicfight_ai_anim.py")
ef = importlib.util.module_from_spec(spec)
spec.loader.exec_module(ef)

U, F, B, L, R = (0, 0, 1), (0, -1, 0), (0, 1, 0), (1, 0, 0), (-1, 0, 0)


def up(*parts):
    """Blend a pose direction from named components so poses stay readable."""
    v = Vector((0.0, 0.0, 0.0))
    for part in parts:
        v += Vector(part if isinstance(part, tuple) else part)
    return v.normalized() if v.length else Vector((0.0, 0.0, 1.0))


# ---------------------------------------------------------------- pose vocabulary
STANCE = {
    "Root": U, "Torso": U, "Chest": U, "Head": U, "Jaw": up(F, (0, 0, -0.1)),
    "Tail1": up(B, (0, 0, -0.18)), "Tail2": up(B, (0, 0, -0.07)), "Tail3": up(B, (0, 0, -0.1)),
    "Shoulder_L": up(L, (0, 0, -0.3)), "Shoulder_R": up(R, (0, 0, -0.3)),
    "Arm_L": up((0, 0, -1)), "Arm_R": up((0, 0, -1)),
    "Hand_L": up((0, 0, -1)), "Hand_R": up((0, 0, -1)),
    "Thigh_L": up((0, 0, -1)), "Thigh_R": up((0, 0, -1)),
    "Leg_L": up((0, 0, -1)), "Leg_R": up((0, 0, -1)),
    "Foot_L": up(F, (0, 0, -0.45)), "Foot_R": up(F, (0, 0, -0.45)),
    "Toe_L_mid": up(F, (0, 0, -0.05)), "Toe_R_mid": up(F, (0, 0, -0.05)),
}

POSES = {
    "STANCE": {},
    "BREATH": {"Chest": up(U, (0, -0.08, 0)), "Head": up(U, (0, -0.05, 0)),
               "Arm_L": up((0, 0, -1), (0, -0.06, 0)), "Arm_R": up((0, 0, -1), (0, -0.06, 0)),
               "Jaw": up(F, (0, 0, -0.25))},
    "CROUCH": {"Thigh_L": up(F, (0, 0, -0.9)), "Thigh_R": up(F, (0, 0, -0.9)),
               "Leg_L": up(B, (0, 0, -0.9)), "Leg_R": up(B, (0, 0, -0.9)),
               "Foot_L": up(F, (0, 0, -0.6)), "Foot_R": up(F, (0, 0, -0.6)),
               "Torso": up(U, (0, -0.3, 0)), "Chest": up(U, (0, -0.2, 0)),
               "Head": up(U, (0, -0.15, 0)), "Tail1": up(B, (0, 0, 0.4)),
               "Tail2": up(B, (0, 0, 0.3)), "Tail3": up(B, (0, 0, 0.2)),
               "Arm_L": up((0, 0, -1), B, (0, 0, 0.2)), "Arm_R": up((0, 0, -1), B, (0, 0, 0.2))},
    "AIRBORNE": {"Thigh_L": up(F, (0, 0, -0.5)), "Thigh_R": up(F, (0, 0, -0.5)),
                 "Leg_L": up(B, (0, 0, -0.5)), "Leg_R": up(B, (0, 0, -0.5)),
                 "Foot_L": up(F, (0, 0, -0.15)), "Foot_R": up(F, (0, 0, -0.15)),
                 "Torso": up(U, (0, -0.25, 0)), "Chest": up(U, (0, -0.15, 0)),
                 "Head": up(U, (0, -0.1, 0)), "Tail1": up(B, (0, 0, 0.7)),
                 "Tail2": up(B, (0, 0, 0.6)), "Tail3": up(B, (0, 0, 0.5)),
                 "Arm_L": up(L, F, (0, 0, -0.5)), "Arm_R": up(R, F, (0, 0, -0.5))},
    "LAND": {"Thigh_L": up(F, (0, 0, -0.7)), "Thigh_R": up(F, (0, 0, -0.7)),
             "Leg_L": up(B, (0, 0, -0.7)), "Leg_R": up(B, (0, 0, -0.7)),
             "Foot_L": up(F, (0, 0, -0.5)), "Foot_R": up(F, (0, 0, -0.5)),
             "Torso": up(U, (0, -0.4, 0)), "Chest": up(U, (0, -0.3, 0)),
             "Head": up(U, (0, -0.25, 0)), "Tail1": up(B, (0, 0, 0.3)),
             "Tail2": up(B, (0, 0, 0.2)), "Tail3": up(B, (0, 0, 0.1))},
    "WIND_L": {"Arm_L": up(R, (0, -0.4, -0.4)), "Hand_L": up(R, (0, -0.3, -0.4)),
               "Chest": up(U, (0.2, 0, 0)), "Torso": up(U, (0.1, 0, 0)),
               "Head": up(U, (0, -0.2, 0)), "Tail1": up(B, L, (0, 0, -0.1))},
    "RAKE_L": {"Arm_L": up(L, F, (0, 0, 0.2)), "Hand_L": up(L, F, (0, 0, 0.1)),
               "Chest": up(U, (-0.2, -0.15, 0)), "Torso": up(U, (-0.1, -0.15, 0)),
               "Head": up(U, (-0.1, -0.25, 0)), "Tail1": up(B, R, (0, 0, 0.1)),
               "Thigh_L": up(F, (0, 0, -0.95)), "Leg_L": up(B, (0, 0, -0.95))},
    "WIND_R": {"Arm_R": up(L, (0, -0.4, -0.4)), "Hand_R": up(L, (0, -0.3, -0.4)),
               "Chest": up(U, (-0.2, 0, 0)), "Torso": up(U, (-0.1, 0, 0)),
               "Head": up(U, (0, -0.2, 0)), "Tail1": up(B, R, (0, 0, -0.1))},
    "RAKE_R": {"Arm_R": up(R, F, (0, 0, 0.2)), "Hand_R": up(R, F, (0, 0, 0.1)),
               "Chest": up(U, (0.2, -0.15, 0)), "Torso": up(U, (0.1, -0.15, 0)),
               "Head": up(U, (0.1, -0.25, 0)), "Tail1": up(B, L, (0, 0, 0.1)),
               "Thigh_R": up(F, (0, 0, -0.95)), "Leg_R": up(B, (0, 0, -0.95))},
    "HEAD_SNAP": {"Head": up(F, (0, 0, 0.45)), "Chest": up(U, (0, -0.3, 0)),
                  "Torso": up(U, (0, -0.18, 0)), "Jaw": up(F, (0, 0, -0.2)),
                  "Arm_L": up((0, 0, -1), (0, -0.25, 0)), "Arm_R": up((0, 0, -1), (0, -0.25, 0))},
    "BITE": {"Head": up(F, (0, 0, 0.25)), "Jaw": up(F, (0, 0, -0.8)),
             "Chest": up(U, (0, -0.25, 0)), "Torso": up(U, (0, -0.15, 0))},
    "TAIL_WHIP": {"Tail1": up(L, B, (0, 0, 0.15)), "Tail2": up(L, B, (0, 0, 0.1)),
                  "Tail3": up(L, B, (0, 0, 0.05)), "Torso": up(U, (0.25, 0.1, 0)),
                  "Chest": up(U, (0.3, 0, 0)), "Head": up(U, (-0.15, -0.2, 0)),
                  "Arm_L": up(L, (0, 0, -0.4)), "Arm_R": up((0, 0, -1), (0, -0.2, 0))},
    "GUARD": {"Arm_L": up(F, U, (0.3, 0, 0)), "Hand_L": up(F, U),
              "Arm_R": up(F, U, (-0.3, 0, 0)), "Hand_R": up(F, U),
              "Torso": up(U, (0, -0.2, 0)), "Chest": up(U, (0, -0.15, 0)),
              "Head": up(U, (0, -0.15, 0)), "Thigh_L": up(F, (0, 0, -0.85)),
              "Thigh_R": up(F, (0, 0, -0.85))},
    "THRUST": {"Arm_R": up(F, (0, 0, 0.1)), "Hand_R": F, "Torso": up(U, (0, -0.3, 0)),
               "Chest": up(U, (0, -0.22, 0)), "Head": up(U, (0, -0.3, 0)),
               "Thigh_R": up(F, (0, 0, -0.95)), "Leg_R": up(B, (0, 0, -0.95)),
               "Tail1": up(B, (0, 0, 0.1))},
    "RISE_UP": {"Arm_R": up(F, U), "Hand_R": up(F, U), "Torso": up(U, (0, 0.1, 0)),
                "Chest": up(U, (0, 0.05, 0)), "Head": up(U, (0, 0.1, 0)),
                "Tail1": up(B, (0, 0, -0.3)), "Tail2": up(B, (0, 0, -0.2))},
    "SLIP_L": {"Torso": up(U, (0.35, 0, 0)), "Chest": up(U, (0.25, 0, 0)),
               "Head": up(U, (0.15, -0.1, 0)), "Tail1": up(B, R, (0, 0, 0.1)),
               "Thigh_L": up(L, (0, 0, -0.9)), "Leg_L": up((0, 0, -1)),
               "Thigh_R": up(R, (0, 0, -0.6)), "Leg_R": up(B, (0, 0, -0.6))},
    "CHARGE": {"Arm_R": up(B, (0, 0, -0.2)), "Hand_R": up(B, (0, 0, -0.3)),
               "Arm_L": up(B, R, (0, 0, -0.3)), "Hand_L": up(B, R, (0, 0, -0.4)),
               "Torso": up(U, (0, -0.45, 0)), "Chest": up(U, (0, -0.35, 0)),
               "Head": up(U, (0, -0.3, 0)), "Thigh_L": up(F, (0, 0, -0.75)),
               "Thigh_R": up(F, (0, 0, -0.75)), "Leg_L": up(B, (0, 0, -0.75)),
               "Leg_R": up(B, (0, 0, -0.75)), "Tail1": up(B, (0, 0, 0.45)),
               "Tail2": up(B, (0, 0, 0.35)), "Tail3": up(B, (0, 0, 0.25))},
}
# Mirrors so left/right variants stay in step.
for name in ("WIND_L", "RAKE_L", "SLIP_L"):
    mirror = {k: (-v[0], v[1], v[2]) for k, v in POSES[name].items()}
    POSES[name.replace("_L", "_R")] = {
        (k.replace("_L", "_R") if k.endswith("_L") else k.replace("_R", "_L") if k.endswith("_R") else k): v
        for k, v in mirror.items()}

# Locomotion gets its own silhouettes; an attack rake is not a walking contact.
POSES.update({
    "EXHALE": {"Chest": up(U,(0,.05,0)), "Head": up(U,(0,.04,0)),
                "Jaw": up(F,(0,0,-.04)), "Tail1": up(B,(.05,0,-.2))},
    "WALK_L": {"Thigh_L": up(F,(0,0,-2.5)), "Leg_L": up(B,(0,0,-3.0)),
               "Thigh_R": up(B,(0,0,-1.3)), "Leg_R": up(F,(0,0,-1.8)),
               "Arm_R": up(F,(0,0,-.95)), "Arm_L": up(B,(0,0,-.95)),
               "Torso": up(U,(0,-.06,0)), "Tail1": up(B,(-.1,0,-.18))},
    "WALK_R": {"Thigh_R": up(F,(0,0,-2.5)), "Leg_R": up(B,(0,0,-3.0)),
               "Thigh_L": up(B,(0,0,-1.3)), "Leg_L": up(F,(0,0,-1.8)),
               "Arm_L": up(F,(0,0,-.95)), "Arm_R": up(B,(0,0,-.95)),
               "Torso": up(U,(0,-.06,0)), "Tail1": up(B,(.1,0,-.18))},
    "WALK_PASS_L": {"Thigh_L": up(B,(0,0,-2.2)),"Leg_L": up(F,(0,0,-2.5)),
                    "Thigh_R": up(F,(0,0,-1.8)),"Leg_R": up(B,(0,0,-1.8)),
                    "Arm_L": up(F,(0,0,-.96)),"Arm_R": up(B,(0,0,-.96)),
                    "Torso": up(U,(0,-.05,0))},
    "WALK_PASS_R": {"Thigh_R": up(B,(0,0,-2.2)),"Leg_R": up(F,(0,0,-2.5)),
                    "Thigh_L": up(F,(0,0,-1.8)),"Leg_L": up(B,(0,0,-1.8)),
                    "Arm_R": up(F,(0,0,-.96)),"Arm_L": up(B,(0,0,-.96)),
                    "Torso": up(U,(0,-.05,0))},
    "RUN_L": {"Thigh_L": up(F,(0,0,-2.2)), "Leg_L": up(B,(0,0,-2.4)),
              "Thigh_R": up(B,(0,0,-.9)), "Leg_R": up(F,(0,0,-1.2)),
              "Arm_R": up(F,(0,0,-.6)), "Arm_L": up(B,(0,0,-.6)),
              "Torso": up(U,(0,-.28,0)), "Chest": up(U,(0,-.19,0)),
              "Head": up(U,(0,.08,0)), "Tail1": up(B,(0,0,.28))},
    "RUN_R": {"Thigh_R": up(F,(0,0,-2.2)), "Leg_R": up(B,(0,0,-2.4)),
              "Thigh_L": up(B,(0,0,-.9)), "Leg_L": up(F,(0,0,-1.2)),
              "Arm_L": up(F,(0,0,-.6)), "Arm_R": up(B,(0,0,-.6)),
              "Torso": up(U,(0,-.28,0)), "Chest": up(U,(0,-.19,0)),
              "Head": up(U,(0,.08,0)), "Tail1": up(B,(0,0,.28))},
    "RUN_FLIGHT": {"Thigh_L": up(F,(0,0,-.65)), "Thigh_R": up(F,(0,0,-.65)),
                   "Leg_L": up(B,(0,0,-.55)), "Leg_R": up(B,(0,0,-.55)),
                   "Torso": up(U,(0,-.23,0)), "Tail1": up(B,(0,0,.42))},
})

# ---------------------------------------------------------------- clip definitions
# (frame, pose, marker). One-shots open and close on STANCE; loops wrap on STANCE.
CLIPS = {
    "idle": (True, [(0, "STANCE", "settle"), (12, "BREATH", "inhale"),
                    (24, "EXHALE", "exhale"), (36, "BREATH", "inhale"),
                    (48, "STANCE", "settle")]),
    "walk": (True, [(0, "WALK_L", "left contact"), (9, "WALK_PASS_L", "pass"),
                    (18, "WALK_R", "right contact"), (27, "WALK_PASS_R", "pass"),
                    (36, "WALK_L", "left contact")]),
    "run": (True, [(0, "RUN_L", "left contact"), (5, "RUN_FLIGHT", "flight"),
                   (10, "RUN_R", "right contact"), (15, "RUN_FLIGHT", "flight"),
                   (20, "RUN_L", "left contact")]),
    # new attacks: a cross-body rake that ends back on stance
    "claw_left": (False, [(0, "STANCE", "ready"), (4, "WIND_L", "wind"),
                          (7, "RAKE_L", "impact"), (12, "STANCE", "recover")]),
    "claw_right": (False, [(0, "STANCE", "ready"), (4, "WIND_R", "wind"),
                           (7, "RAKE_R", "impact"), (12, "STANCE", "recover")]),
    "flurry_left": (False, [(0, "STANCE", "ready"), (3, "WIND_L", "wind"),
                            (5, "RAKE_L", "impact"), (7, "RAKE_L", "follow"),
                            (10, "STANCE", "recover")]),
    "flurry_right": (False, [(0, "STANCE", "ready"), (3, "WIND_R", "wind"),
                             (5, "RAKE_R", "impact"), (7, "RAKE_R", "follow"),
                             (10, "STANCE", "recover")]),
    "bite": (False, [(0, "STANCE", "ready"), (4, "HEAD_SNAP", "drive"),
                     (6, "BITE", "bite"), (9, "HEAD_SNAP", "release"),
                     (14, "STANCE", "recover")]),
    "tail_slam": (False, [(0, "STANCE", "ready"), (4, "CROUCH", "coil"),
                          (8, "TAIL_WHIP", "impact"), (13, "STANCE", "recover")]),
    "spike": (False, [(0, "STANCE", "ready"), (4, "WIND_R", "wind"),
                      (7, "THRUST", "impact"), (12, "STANCE", "recover")]),
    "guard": (False, [(0, "STANCE", "ready"), (4, "GUARD", "set"),
                      (8, "GUARD", "hold"), (12, "STANCE", "recover")]),
    "dodge": (False, [(0, "STANCE", "ready"), (3, "SLIP_L", "slip"),
                      (7, "SLIP_R", "slip"), (12, "STANCE", "recover")]),
    "launcher": (False, [(0, "STANCE", "ready"), (4, "CROUCH", "load"),
                         (7, "RISE_UP", "launch"), (12, "STANCE", "recover")]),
    "rise_left": (False, [(0, "STANCE", "ready"), (4, "CROUCH", "load"),
                          (7, "RAKE_L", "rise"), (11, "STANCE", "recover")]),
    "rise_right": (False, [(0, "STANCE", "ready"), (4, "CROUCH", "load"),
                           (7, "RAKE_R", "rise"), (11, "STANCE", "recover")]),
    "skill_charge": (False, [(0, "STANCE", "ready"), (5, "CHARGE", "wind"),
                             (9, "CHARGE", "hold"), (14, "STANCE", "recover")]),
    "skill_thrust": (False, [(0, "STANCE", "ready"), (3, "CHARGE", "load"),
                             (6, "THRUST", "impact"), (10, "STANCE", "recover")]),
    "leap_charge": (False, [(0, "STANCE", "ready"), (4, "CROUCH", "load"),
                            (6, "AIRBORNE", "launch"), (9, "LAND", "land"),
                            (13, "STANCE", "recover")]),
    "leap": (False, [(0, "STANCE", "ready"), (6, "CROUCH", "load"),
                     (10, "AIRBORNE", "flight"), (16, "LAND", "land"),
                     (22, "STANCE", "recover")]),
    "jump": (False, [(0, "STANCE", "ready"), (5, "CROUCH", "load"),
                     (9, "AIRBORNE", "flight"), (14, "LAND", "land"),
                     (19, "STANCE", "recover")]),
    "duck_rake_bite": (False, [(0,"STANCE","ready"),(4,"SLIP_L","duck"),
                    (8,"WIND_L","rake load"),(12,"RAKE_L","rake"),
                    (16,"HEAD_SNAP","drive"),(20,"BITE","bite"),
                    (26,"STANCE","recover")]),
    "hop_tail_claw": (False, [(0,"STANCE","ready"),(4,"CROUCH","load"),
                    (8,"AIRBORNE","hop"),(12,"LAND","land"),
                    (17,"TAIL_WHIP","tail impact"),(22,"WIND_R","claw load"),
                    (26,"RAKE_R","claw impact"),(32,"STANCE","recover")]),
}

LA.import_all(LA.find_project())
rig = bpy.data.objects[LA.ARMATURE]
scene = bpy.context.scene
analysis = ef.analyze_rig(rig)
AXES = {n: Vector(v) for n, v in MX.LIMB_AXIS.items()}

def bake_shortest_rotations(action,keyposes,end):
    """Sample the planned quaternion arc directly; XZY Euler curves can flip."""
    LA.assign_action(rig,action)
    names=sorted(keyposes[0]['local_quaternions'])
    for n in names:
        prop=ef._rotation_prop(rig,n)
        for fc in ef._bone_fcurves(rig,n,prop):fc.keyframe_points.clear()
        p=rig.pose.bones[n];previous=None
        for half in range(end*2+1):
            f=half/2
            j=min(next((i for i in range(len(keyposes)-1)
                        if keyposes[i]['frame']<=f<=keyposes[i+1]['frame']),len(keyposes)-2),
                  len(keyposes)-2)
            left,right=keyposes[j],keyposes[j+1]
            t=(f-left['frame'])/(right['frame']-left['frame'])
            q0=Quaternion(left['local_quaternions'][n]);q1=Quaternion(right['local_quaternions'][n])
            q=q0.slerp(q1,t).normalized()
            if p.rotation_mode=='QUATERNION':p.rotation_quaternion=q
            else:
                previous=q.to_euler(p.rotation_mode,previous) if previous else q.to_euler(p.rotation_mode)
                p.rotation_euler=previous
            p.keyframe_insert(data_path=prop,frame=f,group=n)
        for fc in ef._bone_fcurves(rig,n,prop):
            for kp in fc.keyframe_points:kp.interpolation='LINEAR'


def local_quats(pose_name):
    """Model-space part targets -> parent-relative local rotations."""
    target = dict(STANCE)
    target.update(POSES[pose_name])
    for side in ('L','R'):
        # Keep wrists and deform elbows aligned with the upper limb through
        # cross-body strikes; opposite vectors otherwise flip a nub by 180°.
        arm=Vector(target['Arm_'+side]);hand=Vector(target['Hand_'+side])
        target['Hand_'+side]=up(arm*.68,hand*.32)
        target['Elbow_'+side]=up(arm*.84,hand*.16)
        target['Knee_'+side]=up(target['Thigh_'+side],target['Leg_'+side])
        for toe in ('in','out'):
            fan=.10 if toe=='in' else -.10
            if side=='R':fan=-fan
            target['Toe_'+side+'_'+toe]=up(target['Toe_'+side+'_mid'],(fan,0,0))
    desired = {}
    for bone, direction in target.items():
        rest = AXES.get(bone)
        if rest is None:
            continue
        desired[bone] = rest.rotation_difference(Vector(direction).normalized())
    out={}
    for bone in sorted(desired,key=lambda n:len(rig.pose.bones[n].parent_recursive)):
        parent=rig.pose.bones[bone].parent
        q=desired[parent.name].inverted()@desired[bone] if parent and parent.name in desired else desired[bone]
        out[bone]=list(q.normalized())
    return out


results, authored = {}, {}
for clip, (loop, keys) in CLIPS.items():
    keyposes=[]
    for i,(frame,pose,marker) in enumerate(keys):
        position=[0.,0.,0.]
        if clip=='idle':position[2]=0.
        elif clip=='walk':position=[[-.025,0.,0.],[0.,0.,.025],[.025,0.,0.],
                                   [0.,0.,.025],[-.025,0.,0.]][i]
        elif clip=='run':position=[[-.025,0.,-.055],[0.,0.,.10],[.025,0.,-.055],
                                  [0.,0.,.10],[-.025,0.,-.055]][i]
        elif clip in {'jump','leap','leap_charge'}:
            profile={'jump':[0.,-.07,.34,-.04,0.],
                     'leap':[0.,-.07,.38,-.02,0.],
                     'leap_charge':[0.,-.08,.19,-.04,0.]}[clip]
            position[2]=profile[i]
            if clip=='leap':position[1]=[0.,0.,-.12,-.22,0.][i]
        elif clip=='hop_tail_claw':position[2]=[0.,-.07,.25,-.04,0.,0.,0.,0.][i]
        keyposes.append({'frame':frame,'local_quaternions':local_quats(pose),
                         'translations':{'Root':position},'marker':marker})
    try:
        report = ef.author_motion(rig, keyposes, analysis, name="%s.new" % (LA.PREFIX + clip))
        action = bpy.data.actions[report["action"]]
        action.use_frame_range = True
        action.frame_start = 0
        action.frame_end = float(keys[-1][0])
        action.use_cyclic = loop
        action["gecko_loop"] = loop
        bake_shortest_rotations(action,keyposes,int(keys[-1][0]))
        authored[clip] = action
        results[clip] = {"status": "authored", "loop": loop, "frames": keys[-1][0],
                         "poses": [k[1] for k in keys]}
    except Exception as exc:
        results[clip] = {"status": "FAILED", "error": "%s: %s" % (type(exc).__name__, exc)}

for clip, action in authored.items():
    shipped = LA.PREFIX + clip
    old = bpy.data.actions.get(shipped)
    if old is not None and old is not action:
        bpy.data.actions.remove(old)
    action.name = shipped
    action.use_fake_user = True

out_blend = ROOT / "sampleRig" / "Gourmet2_Lizardman_All_Reauthored.blend"
bpy.ops.wm.save_as_mainfile(filepath=str(out_blend))
(ROOT / "sampleRig" / "lizardman_all_reauthored_report.json").write_text(
    json.dumps(results, indent=2), encoding="utf-8")
print('EXPORT',LA.export_all(str(PROJECT),str(ROOT/'sampleRig/gourmet2_lizardman_all_reauthored.json')))
for clip in sorted(results):
    r = results[clip]
    print("%-13s %s" % (clip, r["status"] if r["status"] != "authored"
                        else "authored %2df loop=%-5s %s" % (r["frames"], r["loop"], " -> ".join(r["poses"]))))
print("authored %d/%d -> %s" % (len(authored), len(CLIPS), out_blend.name))
