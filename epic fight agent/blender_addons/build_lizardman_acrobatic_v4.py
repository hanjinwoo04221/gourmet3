"""Lizardman v4: a technical, acrobatic human-martial-arts move set with tempo and combo flow.

Same epicfight-API pipeline as build_lizardman_acrobatic_v4.py (analyze_rig / author_motion, forward-kinematic
ground solve, lagged tail overlap), but choreographed differently:

  * upright guarded stance, human gait, hips/torso/chest counter-rotation
  * slow, weighted preparation -> explosive `snap` into the hit -> held impact -> relaxed recovery
  * combo families share CARRY poses so consecutive clips (claw R/L, flurry, rise R/L -> bite, tail spin -> tail
    spin) start where the previous one ended
  * acrobatics: back-flip kick (launcher), front-flip axe kick (spike), somersault pounce (leap),
    corkscrew (jump), side flip (dodge), spinning tail sweep, aerial tail spin -> claw
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

OUT_JSON = ROOT / "sampleRig" / "gourmet2_lizardman_acrobatic_v4.json"
OUT_BLEND = ROOT / "sampleRig" / "Gourmet2_Lizardman_Acrobatic_V4.blend"
OUT_REPORT = ROOT / "sampleRig" / "lizardman_acrobatic_v4_report.json"


def V(f=0.0, l=0.0, u=0.0):
    """Direction: f forward, l toward the character's left, u up."""
    return Vector((l, -f, u)).normalized()


def O(s, f=0.0, o=0.0, u=0.0):
    """Side-relative direction: o is OUTWARD for side s (+1 left, -1 right); negative crosses the body."""
    return V(f, s * o, u)


# --------------------------------------------------------------------------- pose model
STANCE_D = {
    "Root": V(0, 0, 1), "Torso": V(.20, 0, 1), "Chest": V(.22, 0, 1), "Head": V(-.06, 0, 1),
    "Jaw": V(1, 0, -.12),
    "Tail1": V(-1, 0, .05), "Tail2": V(-1, 0, .16), "Tail3": V(-1, 0, .10),
}
for _s, _n in ((1, "L"), (-1, "R")):
    STANCE_D.update({
        "Shoulder_" + _n: O(_s, 0, 1, -.3),
        "Arm_" + _n: O(_s, .55, .20, -.80), "Hand_" + _n: O(_s, 1, .12, -.25),
        "Thigh_" + _n: O(_s, .55, .12, -1), "Leg_" + _n: O(_s, -.65, .08, -1),
        "Foot_" + _n: V(1, 0, -.42), "Toe_" + _n + "_mid": V(1, 0, -.05),
    })

POSES = {}


def pose(name, d=None, rot=None, root=(0, 0, 0), air=False):
    POSES[name] = {"d": dict(d or {}), "rot": dict(rot or {}), "root": tuple(root), "air": air}


def legs(s, hip, knee, ankle=.42, spread=.12, toe=.05):
    n = "L" if s > 0 else "R"
    return {"Thigh_" + n: V(hip, s * spread, -1), "Leg_" + n: V(-knee, s * .08, -1),
            "Foot_" + n: V(1, 0, -ankle), "Toe_" + n + "_mid": V(1, 0, -toe)}


def arm(s, a, h=None, sh=(1.0, -.3)):
    n = "L" if s > 0 else "R"
    out = {"Arm_" + n: O(s, *a), "Shoulder_" + n: O(s, 0, *sh)}
    if h is not None:
        out["Hand_" + n] = O(s, *h)
    return out


def merge(*parts):
    out = {}
    for p in parts:
        out.update(p)
    return out


def mirror_pose(p):
    def swap(name):
        if name.endswith("_L") or "_L_" in name:
            return name.replace("_L", "_R")
        if name.endswith("_R") or "_R_" in name:
            return name.replace("_R", "_L")
        return name
    return {"d": {swap(k): Vector((-v.x, v.y, v.z)) for k, v in p["d"].items()},
            "rot": {swap(k): (v[0], -v[1], -v[2]) for k, v in p["rot"].items()},
            "root": (-p["root"][0], p["root"][1], p["root"][2]), "air": p["air"]}


def pair(name_l, **kw):
    """Define a left pose and register its mirrored right twin."""
    pose(name_l, **kw)
    POSES[name_l[:-2] + "_R"] = mirror_pose(POSES[name_l])


def primaries(p):
    t = dict(STANCE_D)
    t.update(p["d"])
    return t


def slerp_ex(q0, q1, t):
    if q0.dot(q1) < 0:
        q1 = Quaternion((-q1.w, -q1.x, -q1.y, -q1.z))
    d = q0.inverted() @ q1
    axis, angle = d.to_axis_angle()
    return (q0 @ Quaternion(axis, angle * t)).normalized() if angle > 1e-9 else q0.copy()


def blend_direction(v0, v1, w):
    if (v0 - v1).length < 1e-9:
        return v0.copy()
    return slerp_ex(Quaternion(), v0.rotation_difference(v1), w) @ v0


# ---- human martial-arts stance: upright, knees soft, hands up, tail as a low counterweight
STANCE_D.update({
    "Torso": V(.10, 0, 1), "Chest": V(.12, 0, 1), "Head": V(-.10, 0, 1), "Jaw": V(1, 0, -.05),
    "Tail1": V(-1, 0, .02), "Tail2": V(-1, 0, .07), "Tail3": V(-1, 0, .04),
})
for _s, _n in ((1, "L"), (-1, "R")):
    STANCE_D.update({
        "Shoulder_" + _n: O(_s, 0, 1, -.3),
        "Arm_" + _n: O(_s, .30, .28, -.95), "Hand_" + _n: O(_s, .95, -.25, .55),
        "Thigh_" + _n: O(_s, .45, .20, -1), "Leg_" + _n: O(_s, -.55, .05, -1),
        "Foot_" + _n: V(1, 0, -.42), "Toe_" + _n + "_mid": V(1, 0, -.05),
    })


def blend_pose(a, b, t, name=None):
    """A pose partway between two poses (used for combo 'carry' poses)."""
    pa, pb = POSES[a] if isinstance(a, str) else a, POSES[b] if isinstance(b, str) else b
    da, db = primaries(pa), primaries(pb)
    d = {n: blend_direction(Vector(da[n]), Vector(db[n]), t) for n in da}
    rot = {}
    for n in set(pa["rot"]) | set(pb["rot"]):
        x, y = pa["rot"].get(n, (0, 0, 0)), pb["rot"].get(n, (0, 0, 0))
        rot[n] = tuple(x[i] + (y[i] - x[i]) * t for i in range(3))
    root = tuple(pa["root"][i] + (pb["root"][i] - pa["root"][i]) * t for i in range(3))
    out = {"d": d, "rot": rot, "root": root, "air": pa["air"] and pb["air"]}
    if name:
        POSES[name] = out
    return out


def spun(base, name, yaw=0.0, pitch=0.0, roll=0.0):
    """The same pose after a whole-body turn, so a spin can end exactly where it started."""
    p = POSES[base]
    rot = dict(p["rot"])
    x, y, z = rot.get("Root", (0, 0, 0))
    rot["Root"] = (x + pitch, y + roll, z + yaw)
    POSES[name] = {"d": dict(p["d"]), "rot": rot, "root": p["root"], "air": p["air"]}


def guard(s, forearm=(.95, -.25, .55)):
    return arm(s, (.30, .28, -.95), forearm, (1, -.3))


# --------------------------------------------------------------------------- base poses
pose("STANCE", d=merge(legs(1, .45, .55, spread=.20), legs(-1, .45, .55, spread=.20),
                       guard(1), guard(-1)))
pose("SWAY_L", d=merge({"Chest": V(.15, 0, 1), "Head": V(-.12, 0, 1)}, legs(1, .55, .75, spread=.24),
                       legs(-1, .35, .45, spread=.18), guard(1, (.9, -.2, .65)), guard(-1, (.8, -.2, .75))),
     rot={"Root": (0, -3, 5), "Chest": (0, 0, -3), "Head": (0, 2, 5), "Tail1": (0, 0, 8), "Tail2": (0, 0, 8),
          "Tail3": (0, 0, 8)}, root=(.03, .0, -.01))
pose("BOB", d=merge({"Chest": V(.12, 0, 1.05), "Jaw": V(1, 0, -.20)}, legs(1, .38, .40, spread=.19),
                    legs(-1, .38, .40, spread=.19), guard(1, (1, -.2, .5)), guard(-1, (1, -.2, .5))),
     rot={"Tail1": (0, 0, -4), "Tail2": (0, 0, -5), "Tail3": (0, 0, -5)})
POSES["SWAY_R"] = mirror_pose(POSES["SWAY_L"])

# ---- locomotion (guarded, human gait)
pose("WALK_L", d=merge(legs(1, 1.05, .40, ankle=.5), legs(-1, -.55, 1.1, ankle=.7),
                        {"Torso": V(.12, 0, 1), "Chest": V(.14, 0, 1), "Head": V(-.12, 0, 1)},
                        guard(1, (.9, -.2, .6)), guard(-1, (1.0, -.1, .45))),
     rot={"Root": (0, 2, -6), "Torso": (0, 0, 6), "Chest": (0, 0, 5), "Head": (0, 0, -6),
          "Tail1": (0, 0, 8), "Tail2": (0, 0, 9), "Tail3": (0, 0, 10)}, root=(.012, 0, 0))
pose("WALK_PASS_L", d=merge(legs(-1, .10, .40), legs(1, .80, 1.7, ankle=.9),
                             {"Torso": V(.10, 0, 1), "Chest": V(.12, 0, 1)}, guard(1), guard(-1)),
     rot={"Root": (0, -2, 0)})
POSES["WALK_R"] = mirror_pose(POSES["WALK_L"])
POSES["WALK_PASS_R"] = mirror_pose(POSES["WALK_PASS_L"])
pose("RUN_L", d=merge(legs(1, 1.9, 1.0, ankle=.5, spread=.25), legs(-1, -1.5, 2.5, ankle=.7, spread=.25),
                       {"Torso": V(.75, 0, 1), "Chest": V(.85, 0, 1), "Head": V(-.55, 0, 1), "Jaw": V(1, 0, -.35),
                        "Tail1": V(-1, 0, .35), "Tail2": V(-1, 0, .30), "Tail3": V(-1, 0, .16)},
                       arm(-1, (1.0, .30, -.15), (1.0, .10, .55)), arm(1, (-1.0, .35, -.45), (-.5, .2, .55))),
     rot={"Root": (12, 6, -12), "Torso": (0, 0, 11), "Chest": (0, 0, 9), "Head": (0, 0, -16),
          "Tail1": (0, 0, 14), "Tail2": (0, 0, 16), "Tail3": (0, 0, 18)}, root=(.02, .03, 0))
pose("RUN_FLY_L", d=merge(legs(-1, 1.8, 1.4, ankle=.8, spread=.3), legs(1, -1.5, .7, ankle=1.1, spread=.3),
                           {"Torso": V(.80, 0, 1), "Chest": V(.90, 0, 1), "Head": V(-.6, 0, 1), "Jaw": V(1, 0, -.5),
                            "Tail1": V(-1, 0, .40), "Tail2": V(-1, 0, .34), "Tail3": V(-1, 0, .18)},
                           arm(1, (1.0, .45, -.10), (1.0, .2, .6)), arm(-1, (-1.05, .35, -.30), (-.6, .2, .7))),
     rot={"Root": (18, 0, 0)}, root=(0, .0, .12), air=True)
POSES["RUN_R"] = mirror_pose(POSES["RUN_L"])
POSES["RUN_FLY_R"] = mirror_pose(POSES["RUN_FLY_L"])

# ---- combo family: strike with the left hand (the right-hand twins are mirrors)
pair("F_L", d=merge(legs(1, .15, 1.0, spread=.22), legs(-1, .85, .35, spread=.24, ankle=.5),
                     {"Torso": V(.14, 0, 1), "Chest": V(.16, 0, 1), "Head": V(-.10, 0, 1)},
                     arm(1, (-.55, .30, -.80), (.75, -.15, .75), (1, -.1)), arm(-1, (.85, .15, -.45), (1, -.15, .15))),
     rot={"Root": (2, 0, 10), "Torso": (0, 0, 6), "Chest": (0, 0, 8), "Head": (0, 0, -22),
          "Tail1": (0, 0, -8), "Tail2": (0, 0, -8), "Tail3": (0, 0, -8)}, root=(0, .04, -.01))
pair("STRIKE_L", d=merge(legs(1, 1.15, .30, spread=.16, ankle=.45), legs(-1, -.65, .55, spread=.26, ankle=.95),
                          {"Torso": V(.24, 0, 1), "Chest": V(.28, 0, 1), "Head": V(-.05, 0, 1), "Jaw": V(1, 0, -.6)},
                          arm(1, (1, -.08, .10), (1, -.05, .05), (1, .2)), arm(-1, (-.15, .35, -.9), (.6, -.25, .85))),
       rot={"Root": (6, 0, -14), "Torso": (0, 0, -8), "Chest": (0, 0, -12), "Head": (0, 0, 32),
            "Tail1": (0, 0, 14), "Tail2": (0, 0, 14), "Tail3": (0, 0, 14)}, root=(0, -.15, -.01))
pair("FOLLOW_L", d=merge(POSES["STRIKE_L"]["d"], arm(1, (1, .12, .20), (1, .2, .10), (1, .3))),
       rot={"Root": (8, 0, -20), "Torso": (0, 0, -10), "Chest": (0, 0, -14), "Head": (0, 0, 40),
            "Tail1": (0, 0, 20), "Tail2": (0, 0, 20), "Tail3": (0, 0, 20)}, root=(0, -.19, -.01))
blend_pose("STANCE", "F_L", .5, "CARRY_L")
blend_pose("STANCE", "F_R", .5, "CARRY_R")
pair("SWING_L", d=blend_pose("F_L", "STRIKE_L", .55)["d"], rot=blend_pose("F_L", "STRIKE_L", .55)["rot"],
     root=(0, -.08, -.01))

# ---- rising technique (dip, then a driving knee + uppercut); the right twin is a mirror
pair("DIP_L", d=merge(legs(1, 1.6, 2.1, spread=.26), legs(-1, 1.4, 2.0, spread=.26),
                       {"Torso": V(.45, 0, 1), "Chest": V(.50, 0, 1), "Head": V(-.30, 0, 1), "Jaw": V(1, 0, -.15),
                        "Tail1": V(-1, 0, .30), "Tail2": V(-1, 0, .24), "Tail3": V(-1, 0, .14)},
                       arm(1, (-.20, .55, -.95), (.55, .45, -.75), (1, -.4)), arm(-1, (.6, .2, -.6), (1, -.2, .35))),
       rot={"Root": (10, 0, -12), "Torso": (0, 0, -6), "Chest": (0, 0, -8), "Head": (0, 0, 14)}, root=(0, .05, -.02))
pair("RISE_HIT_L", d=merge(legs(-1, .05, .10, spread=.14, ankle=.5),
                            {"Thigh_L": V(1.25, .15, .55), "Leg_L": V(-.25, .05, -1), "Foot_L": V(.4, 0, -1),
                             "Toe_L_mid": V(.5, 0, -1)},
                            {"Torso": V(-.05, 0, 1), "Chest": V(-.14, 0, 1), "Head": V(-.45, 0, 1), "Jaw": V(1, 0, -.75),
                             "Tail1": V(-1, 0, .08), "Tail2": V(-1, 0, .04), "Tail3": V(-1, 0, -.02)},
                            arm(1, (.55, -.28, 1.0), (.4, -.28, 1), (1, .35)), arm(-1, (.35, .35, -.9), (.9, -.25, .55))),
       rot={"Root": (-4, 0, -20), "Torso": (0, 0, -8), "Chest": (0, 0, -10), "Head": (0, 0, 22)}, root=(0, -.08, 0))
pair("RISE_OVER_L", d=merge(POSES["RISE_HIT_L"]["d"], {"Thigh_L": V(1.1, .15, .85), "Chest": V(-.30, 0, 1),
                                                          "Head": V(-.60, 0, 1)},
                             arm(1, (.30, -.30, 1.15), (.2, -.30, 1.1), (1, .5))),
       rot={"Root": (-8, 0, -30), "Torso": (0, 0, -10), "Chest": (0, 0, -12), "Head": (0, 0, 30)}, root=(0, -.06, 0))

# ---- bite: a committed head-first lunge with the arms swept back
pose("BITE_COIL", d=merge(legs(1, 1.0, 1.7, spread=.22), legs(-1, .9, 1.6, spread=.22),
                          {"Torso": V(-.05, 0, 1), "Chest": V(-.22, 0, 1), "Head": V(-.65, 0, 1), "Jaw": V(1, 0, -.4),
                           "Tail1": V(-1, 0, .30), "Tail2": V(-1, 0, .24), "Tail3": V(-1, 0, .12)},
                          guard(1, (.9, -.3, .8)), guard(-1, (.9, -.3, .8))),
     rot={"Root": (-6, 0, 0)}, root=(0, .08, -.01))
pose("BITE_LUNGE", d=merge(legs(1, 1.6, .45, spread=.20), legs(-1, -1.0, .35, spread=.16, ankle=1.0),
                           {"Torso": V(.85, 0, 1), "Chest": V(.95, 0, 1), "Head": V(1.15, 0, .30), "Jaw": V(.55, 0, -1),
                            "Tail1": V(-1, 0, .40), "Tail2": V(-1, 0, .32), "Tail3": V(-1, 0, .20)},
                           arm(1, (-.85, .45, -.35), (-.9, .40, -.10)), arm(-1, (-.85, .45, -.35), (-.9, .40, -.10))),
     rot={"Root": (18, 0, 0)}, root=(0, -.26, -.01))
pose("BITE_CLAMP", d=merge(POSES["BITE_LUNGE"]["d"], {"Jaw": V(1, 0, -.02), "Head": V(1.3, 0, .05)}),
     rot={"Root": (22, 0, 0)}, root=(0, -.30, -.02))
pose("BITE_SHAKE", d=POSES["BITE_CLAMP"]["d"], rot={"Root": (22, 0, 0), "Head": (0, 8, 22), "Chest": (0, 0, 6)},
     root=(0, -.28, -.02))

# ---- spinning tail sweep (a full turn that ends where it began, so tail1 -> tail2 chains)
pose("CROUCH_LOW", d=merge(legs(1, 1.6, 2.2, spread=.30), legs(-1, 1.6, 2.2, spread=.30),
                            {"Torso": V(.40, 0, 1), "Chest": V(.44, 0, 1), "Head": V(-.25, 0, 1),
                             "Tail1": V(-1, 0, .25), "Tail2": V(-1, 0, .20), "Tail3": V(-1, 0, .12)},
                            arm(1, (.45, .65, -.55), (.5, .5, -.4), (1, -.1)), arm(-1, (.45, .65, -.55), (.5, .5, -.4), (1, -.1))),
     rot={"Root": (6, 0, 0)}, root=(0, 0, 0))
blend_pose("STANCE", "CROUCH_LOW", .30, "SPIN_READY")
pose("SPIN_COIL", d=merge(POSES["CROUCH_LOW"]["d"], arm(1, (-.2, .8, -.5), (-.3, .8, -.3)),
                           arm(-1, (.5, .4, -.7), (.6, .2, -.5))),
     rot={"Root": (8, 0, 42), "Torso": (0, 0, 10), "Chest": (0, 0, 10), "Head": (0, 0, -55),
          "Tail1": (0, 0, 10), "Tail2": (0, 0, 10), "Tail3": (0, 0, 10)}, root=(-.02, .04, -.03))
pose("SPIN_MID", d=merge(POSES["CROUCH_LOW"]["d"], {"Tail1": V(-1, 0, .04), "Tail2": V(-1, 0, .02), "Tail3": V(-1, 0, .0)},
                          arm(1, (.1, 1, -.05), (.1, 1, -.1), (1, .1)), arm(-1, (.1, 1, -.05), (.1, 1, -.1), (1, .1))),
     rot={"Root": (6, 5, -150), "Torso": (0, 0, -10), "Chest": (0, 0, -10), "Head": (0, 0, 30),
          "Tail1": (0, 0, -18), "Tail2": (0, 0, -18), "Tail3": (0, 0, -18)}, root=(0, 0, .0))
pose("SPIN_HIT", d=POSES["SPIN_MID"]["d"],
     rot={"Root": (6, 8, -312), "Torso": (0, 0, -8), "Chest": (0, 0, -8), "Head": (0, 0, 20),
          "Tail1": (0, 0, -26), "Tail2": (0, 0, -26), "Tail3": (0, 0, -26)}, root=(0, 0, 0))
spun("SPIN_READY", "SPIN_END", yaw=-360)

# ---- back-flip kick (launcher), front-flip axe kick (spike), somersault pounce (leap), corkscrew (jump), side flip (dodge)
pose("LOAD_LOW", d=merge(legs(1, 1.8, 2.4, spread=.30), legs(-1, 1.8, 2.4, spread=.30),
                          {"Torso": V(.55, 0, 1), "Chest": V(.60, 0, 1), "Head": V(-.35, 0, 1),
                           "Tail1": V(-1, 0, .40), "Tail2": V(-1, 0, .30), "Tail3": V(-1, 0, .18)},
                          arm(1, (-.7, .35, -.7), (-.5, .3, -.9)), arm(-1, (-.7, .35, -.7), (-.5, .3, -.9))),
     rot={"Root": (10, 0, 0)}, root=(0, .06, 0))
pose("FLIP_KICK_A", d=merge(legs(1, .90, .10, ankle=.7), legs(-1, .95, .10, ankle=.7),
                             {"Torso": V(-.05, 0, 1), "Chest": V(-.10, 0, 1), "Head": V(-.3, 0, 1),
                              "Tail1": V(-1, 0, .25), "Tail2": V(-1, 0, .20), "Tail3": V(-1, 0, .12)},
                             arm(1, (.3, .55, .95), (.3, .5, 1)), arm(-1, (.3, .55, .95), (.3, .5, 1))),
     rot={"Root": (-75, 0, 0)}, root=(0, .06, .32), air=True)
pose("FLIP_KICK_B", d=merge(legs(1, .05, .05, ankle=1.2), legs(-1, .05, .05, ankle=1.2),
                             {"Torso": V(-.10, 0, 1), "Chest": V(-.15, 0, 1), "Head": V(-.4, 0, 1), "Jaw": V(1, 0, -.6),
                              "Tail1": V(-1, 0, .05), "Tail2": V(-1, 0, .0), "Tail3": V(-1, 0, -.05)},
                             arm(1, (.4, .8, .4), (.4, .8, .3)), arm(-1, (.4, .8, .4), (.4, .8, .3))),
     rot={"Root": (-150, 0, 0)}, root=(0, .02, .62), air=True)
pose("FLIP_TUCK", d=merge(legs(1, 2.0, 1.9, ankle=.6), legs(-1, 2.0, 1.9, ankle=.6),
                           {"Torso": V(.55, 0, 1), "Chest": V(.6, 0, 1), "Head": V(-.2, 0, 1),
                            "Tail1": V(-1, 0, .4), "Tail2": V(-1, 0, .3), "Tail3": V(-1, 0, .2)},
                           arm(1, (.9, .3, -.4), (.9, .2, -.2)), arm(-1, (.9, .3, -.4), (.9, .2, -.2))),
     rot={"Root": (-250, 0, 0)}, root=(0, 0, .55), air=True)
pose("FLIP_OPEN", d=merge(legs(1, .70, .30, ankle=1.0), legs(-1, .50, .35, ankle=1.0),
                           {"Torso": V(.2, 0, 1), "Chest": V(.2, 0, 1), "Head": V(-.2, 0, 1),
                            "Tail1": V(-1, 0, .2), "Tail2": V(-1, 0, .16), "Tail3": V(-1, 0, .1)},
                           arm(1, (.5, .7, .3), (.6, .6, .2)), arm(-1, (.5, .7, .3), (.6, .6, .2))),
     rot={"Root": (-330, 0, 0)}, root=(0, 0, .22), air=True)
spun("LOAD_LOW", "LAND_BACK", pitch=-360)
spun("STANCE", "STANCE_BACK", pitch=-360)

# front flip (spike): rotation is positive pitch; the axe kick is the leg chopping down at the end
pose("AXE_LOAD", d=POSES["LOAD_LOW"]["d"], rot={"Root": (14, 0, 0)}, root=(0, .05, 0))
pose("AXE_TUCK", d=POSES["FLIP_TUCK"]["d"], rot={"Root": (110, 0, 0)}, root=(0, -.04, .50), air=True)
pose("AXE_OVER", d=merge(legs(1, .2, .1, ankle=1.1), legs(-1, .1, 1.4, ankle=.8),
                          {"Torso": V(.15, 0, 1), "Chest": V(.15, 0, 1), "Head": V(-.3, 0, 1), "Jaw": V(1, 0, -.7),
                           "Tail1": V(-1, 0, .15), "Tail2": V(-1, 0, .1), "Tail3": V(-1, 0, .05)},
                          arm(1, (.5, .6, .8), (.5, .6, .9)), arm(-1, (.5, .6, .8), (.5, .6, .9))),
     rot={"Root": (250, 0, 0)}, root=(0, -.08, .55), air=True)
pose("AXE_HIT", d=merge(POSES["AXE_OVER"]["d"], {"Thigh_R": V(1.0, -.15, -.1), "Leg_R": V(.3, -.05, -1),
                                                    "Foot_R": V(.2, 0, -1)}),
     rot={"Root": (325, 0, 0)}, root=(0, -.10, .34), air=True)
spun("LOAD_LOW", "LAND_FRONT", pitch=360)
spun("STANCE", "STANCE_FRONT", pitch=360)

# somersault pounce (leap)
pose("POUNCE_A", d=POSES["FLIP_OPEN"]["d"], rot={"Root": (55, 0, 0)}, root=(0, -.14, .32), air=True)
pose("POUNCE_B", d=POSES["FLIP_TUCK"]["d"], rot={"Root": (160, 0, 0)}, root=(0, -.30, .56), air=True)
pose("POUNCE_C", d=merge(POSES["FLIP_OPEN"]["d"], {"Jaw": V(1, 0, -.9)}), rot={"Root": (300, 0, 0)},
     root=(0, -.34, .34), air=True)
spun("LOAD_LOW", "LAND_LEAP", pitch=360)
POSES["LAND_LEAP"]["root"] = (0, -.20, 0)

# corkscrew (jump): the turn is about the vertical axis
pose("TWIST_A", d=merge(POSES["FLIP_TUCK"]["d"], arm(1, (.2, .9, .2), (.2, .9, .1)), arm(-1, (.2, .9, .2), (.2, .9, .1))),
     rot={"Root": (10, 0, -70)}, root=(0, 0, .34), air=True)
pose("TWIST_B", d=merge(POSES["FLIP_TUCK"]["d"], {"Tail1": V(-1, .3, .2)}), rot={"Root": (10, 0, -190)},
     root=(0, 0, .52), air=True)
pose("TWIST_C", d=merge(POSES["FLIP_OPEN"]["d"]), rot={"Root": (6, 0, -320)}, root=(0, 0, .26), air=True)
spun("LOAD_LOW", "LAND_TWIST", yaw=-360)
spun("STANCE", "STANCE_TWIST", yaw=-360)

# side flip (dodge): a cartwheel-like roll about the forward axis
pose("SIDE_COIL", d=merge(legs(1, 1.4, 2.0, spread=.5), legs(-1, .4, 1.0, spread=.10),
                           {"Torso": V(.3, -.35, 1), "Chest": V(.3, -.35, 1), "Head": V(-.15, .3, 1),
                            "Tail1": V(-1, -.3, .25), "Tail2": V(-1, -.3, .2), "Tail3": V(-1, -.3, .1)},
                           arm(1, (.3, .8, -.6), (.4, .6, -.5)), arm(-1, (.3, .6, -.6), (.4, .5, -.5))),
     rot={"Root": (4, -10, 12)}, root=(.10, .0, -.02))
pose("SIDE_A", d=merge(legs(1, .1, .1, ankle=.9, spread=.55), legs(-1, .1, .1, ankle=.9, spread=.55),
                        {"Torso": V(0, 0, 1), "Chest": V(0, 0, 1),
                         "Tail1": V(-1, 0, .1), "Tail2": V(-1, 0, .06), "Tail3": V(-1, 0, .02)},
                        arm(1, (.1, 1, -.1), (.1, 1, -.1), (1, .3)), arm(-1, (.1, 1, -.1), (.1, 1, -.1), (1, .3))),
     rot={"Root": (0, -70, 0)}, root=(.18, 0, .30), air=True)
pose("SIDE_B", d=POSES["SIDE_A"]["d"], rot={"Root": (0, -180, 0)}, root=(.24, 0, .50), air=True)
pose("SIDE_C", d=merge(POSES["SIDE_A"]["d"], legs(1, .3, .3, ankle=.9, spread=.35), legs(-1, .3, .3, ankle=.9, spread=.35)),
     rot={"Root": (0, -300, 0)}, root=(.10, 0, .24), air=True)
spun("LOAD_LOW", "LAND_SIDE", roll=-360)
spun("STANCE", "STANCE_SIDE", roll=-360)

# ---- leap prep, guard, skills
pose("LEAP_SWING", d=merge(POSES["LOAD_LOW"]["d"], arm(1, (-.9, .3, -.3), (-.9, .3, -.2)), arm(-1, (-.9, .3, -.3), (-.9, .3, -.2))),
     rot={"Root": (16, 0, 0)}, root=(0, .08, 0))
pose("LEAP_SPRING", d=merge(legs(1, .1, .1, ankle=1.2), legs(-1, .1, .1, ankle=1.2),
                             {"Torso": V(.10, 0, 1), "Chest": V(.05, 0, 1), "Head": V(-.3, 0, 1),
                              "Tail1": V(-1, 0, -.1), "Tail2": V(-1, 0, -.2), "Tail3": V(-1, 0, -.25)},
                             arm(1, (.7, .3, .9), (.7, .3, 1)), arm(-1, (.7, .3, .9), (.7, .3, 1))),
     rot={"Root": (0, 0, 0)}, root=(0, -.06, .26), air=True)
pose("GUARD_UP", d=merge(legs(1, .9, 1.4, spread=.28), legs(-1, .9, 1.4, spread=.28),
                          {"Torso": V(.28, 0, 1), "Chest": V(.36, 0, 1), "Head": V(-.20, 0, 1),
                           "Tail1": V(-1, 0, .20), "Tail2": V(-1, 0, .16), "Tail3": V(-1, 0, .08)},
                          arm(1, (.45, -.1, -.45), (.4, -.75, .95), (1, -.2)), arm(-1, (.45, -.1, -.45), (.4, -.75, .95), (1, -.2))),
     rot={"Root": (4, 0, 0)}, root=(0, .04, 0))
pose("GUARD_HIT", d=POSES["GUARD_UP"]["d"], rot={"Root": (-2, 0, 0), "Chest": (-5, 0, 0)}, root=(0, .12, 0))
pose("GATHER", d=merge(legs(1, .9, 1.9, spread=.55), legs(-1, .9, 1.9, spread=.55),
                        {"Torso": V(.05, 0, 1), "Chest": V(-.10, 0, 1), "Head": V(-.25, 0, 1), "Jaw": V(1, 0, -.2),
                         "Tail1": V(-1, 0, .45), "Tail2": V(-1, 0, .30), "Tail3": V(-1, 0, .16)},
                        arm(1, (.75, .3, -.1), (.85, -.7, .45), (1, -.1)), arm(-1, (.75, .3, -.1), (.85, -.7, .45), (1, -.1))),
     rot={"Root": (0, 0, 0)}, root=(0, .02, 0))
pose("GATHER_A", d=POSES["GATHER"]["d"], rot={"Root": (0, 2, 2), "Chest": (0, 0, 3)}, root=(.006, .02, .0))
pose("GATHER_B", d=POSES["GATHER"]["d"], rot={"Root": (0, -2, -2), "Chest": (0, 0, -3)}, root=(-.006, .02, .0))
pose("THRUST_LOAD", d=merge(legs(1, .9, 1.5, spread=.22), legs(-1, -.1, .9, spread=.20),
                             {"Torso": V(.28, 0, 1), "Chest": V(.32, 0, 1), "Head": V(-.15, 0, 1), "Jaw": V(1, 0, -.2),
                              "Tail1": V(-1, 0, .3), "Tail2": V(-1, 0, .24), "Tail3": V(-1, 0, .12)},
                             arm(-1, (-.6, -.05, -.4), (.9, -.05, .7), (1, -.2)), arm(1, (.9, .3, -.3), (1, .2, .25))),
     rot={"Root": (6, 0, 26), "Torso": (0, 0, 8), "Chest": (0, 0, 10), "Head": (0, 0, -34)}, root=(0, .08, -.02))
pose("THRUST_HIT", d=merge(legs(1, 1.5, .25, spread=.20, ankle=.45),
                            {"Thigh_R": V(-1.2, -.1, -.35), "Leg_R": V(-.5, -.05, -1), "Foot_R": V(.5, 0, -1),
                             "Toe_R_mid": V(.5, 0, -1)},
                            {"Torso": V(.55, 0, 1), "Chest": V(.65, 0, 1), "Head": V(-.05, 0, 1), "Jaw": V(1, 0, -.75),
                             "Tail1": V(-1, 0, .45), "Tail2": V(-1, 0, .36), "Tail3": V(-1, 0, .24)},
                            arm(-1, (1, 0, .05), (1, 0, .05), (1, -.1)), arm(1, (-.5, .8, -.4), (-.6, .8, -.2))),
     rot={"Root": (30, 0, -32), "Torso": (0, 0, -6), "Chest": (0, 0, -10), "Head": (0, 0, 40)}, root=(0, -.28, 0))
pose("THRUST_HOLD", d=POSES["THRUST_HIT"]["d"],
     rot={"Root": (32, 0, -36), "Torso": (0, 0, -6), "Chest": (0, 0, -10), "Head": (0, 0, 44)}, root=(0, -.31, .0))

# ---- duck / spinning rake / bite (uses the left-hand rake and the spin-turn family)
pair("DUCK_L", d=merge(legs(1, 1.9, 2.3, spread=.60), legs(-1, .1, .8, spread=.08),
                        {"Torso": V(.65, -.45, 1), "Chest": V(.7, -.35, 1), "Head": V(-.25, .3, 1), "Jaw": V(1, 0, -.1),
                         "Tail1": V(-1, -.5, .2), "Tail2": V(-1, -.6, .15), "Tail3": V(-1, -.7, .1)},
                        arm(1, (.5, .9, -.6), (.6, .5, -.5)), arm(-1, (.6, -.2, -.8), (.8, -.3, .3))),
       rot={"Root": (6, -14, 16), "Head": (0, 6, -14)}, root=(.16, .04, -.02))
pose("SPIN_LOAD", d=POSES["DUCK_L"]["d"], rot={"Root": (10, -6, 60), "Head": (0, 0, -60)}, root=(.10, .06, -.03))
pose("SPIN_RAKE", d=merge(legs(-1, .5, .6, spread=.25), legs(1, .2, .5, spread=.22),
                           {"Torso": V(.1, 0, 1), "Chest": V(.1, 0, 1), "Head": V(-.1, 0, 1), "Jaw": V(1, 0, -.6),
                            "Tail1": V(-1, 0, .1), "Tail2": V(-1, 0, .1), "Tail3": V(-1, 0, .05)},
                           arm(1, (.2, 1, .25), (.2, 1, .3), (1, .4)), arm(-1, (.3, .3, -.85), (.7, -.2, .6))),
     rot={"Root": (-2, 0, 200), "Torso": (0, 0, 10), "Chest": (0, 0, 12), "Head": (0, 0, -30),
          "Tail1": (0, 0, 16), "Tail2": (0, 0, 16), "Tail3": (0, 0, 16)}, root=(0, 0, .05), air=True)
pose("SPIN_RAKE_END", d=POSES["SPIN_RAKE"]["d"],
     rot={"Root": (2, 0, 350), "Torso": (0, 0, 8), "Chest": (0, 0, 10), "Head": (0, 0, -20),
          "Tail1": (0, 0, 20), "Tail2": (0, 0, 20), "Tail3": (0, 0, 20)}, root=(0, -.04, .0))
for _n in ("BITE_COIL", "BITE_LUNGE", "BITE_CLAMP", "BITE_SHAKE"):
    spun(_n, _n + "_360", yaw=360)
spun("STANCE", "STANCE_360", yaw=360)

# ---- hop -> aerial tail spin -> landing -> right claw
pose("AIR_TAIL_A", d=merge(POSES["FLIP_TUCK"]["d"], {"Tail1": V(-1, 0, .05), "Tail2": V(-1, 0, .02), "Tail3": V(-1, 0, 0)},
                            arm(1, (.2, 1, -.1), (.2, 1, -.1)), arm(-1, (.2, 1, -.1), (.2, 1, -.1))),
     rot={"Root": (10, 0, -140), "Tail1": (0, 0, -14), "Tail2": (0, 0, -14), "Tail3": (0, 0, -14)}, root=(0, 0, .46), air=True)
pose("AIR_TAIL_B", d=POSES["AIR_TAIL_A"]["d"],
     rot={"Root": (6, 0, -290), "Tail1": (0, 0, -24), "Tail2": (0, 0, -24), "Tail3": (0, 0, -24)}, root=(0, 0, .28), air=True)
spun("CROUCH_LOW", "LAND_TAILSPIN", yaw=-360)
spun("STANCE", "STANCE_TS", yaw=-360)
spun("CARRY_R", "CARRY_R_TS", yaw=-360)
spun("F_R", "F_R_TS", yaw=-360)
spun("STRIKE_R", "STRIKE_R_TS", yaw=-360)
spun("FOLLOW_R", "FOLLOW_R_TS", yaw=-360)

# ---- context moves: attacks thrown mid-run, the spinning dash, the rising punch, the one-leg dive stomp
def over(base, d=None, rot=None):
    p = POSES[base] if isinstance(base, str) else base
    r = dict(p["rot"])
    r.update(rot or {})
    dd = dict(p["d"])
    dd.update(d or {})
    return {"d": dd, "rot": r, "root": p["root"], "air": p["air"]}


def pair_p(name_l, p):
    POSES[name_l] = p
    POSES[name_l[:-2] + "_R"] = mirror_pose(p)


UP_W = merge(arm(1, (-.9, .35, -.5), (.5, -.1, .75), (1, -.1)))
UP_S = merge(arm(1, (1, -.05, .15), (1, -.05, .10), (1, .2)))
UP_F = merge(arm(1, (1, .30, .20), (1, .40, .10), (1, .3)))
pair_p("RCW_L", over("RUN_FLY_L", UP_W, {"Torso": (0, 0, 20), "Chest": (0, 0, 12), "Head": (0, 0, -28)}))
pair_p("RCS_L", over(blend_pose("RUN_FLY_L", "RUN_R", .7), UP_S, {"Torso": (0, 0, -18), "Chest": (0, 0, -16), "Head": (0, 0, 30)}))
pair_p("RCF_L", over("RUN_R", UP_F, {"Torso": (0, 0, -22), "Chest": (0, 0, -20), "Head": (0, 0, 36)}))

RB_OPEN = {"Head": V(1.15, 0, .30), "Jaw": V(.55, 0, -1), "Chest": V(1.1, 0, 1), "Torso": V(.95, 0, 1)}
RB_CLAMP = {"Head": V(1.3, 0, .05), "Jaw": V(1, 0, -.02), "Chest": V(1.15, 0, 1), "Torso": V(1.0, 0, 1)}
POSES["RB_OPEN"] = over("RUN_FLY_L", RB_OPEN)
POSES["RB_CLAMP"] = over(blend_pose("RUN_FLY_L", "RUN_R", .7), RB_CLAMP)
POSES["RB_SHAKE"] = over("RUN_R", RB_CLAMP, {"Head": (0, 8, 24)})

POSES["DASH_LOW"] = over("RUN_FLY_L", merge(
    arm(1, (-1, .3, -.3), (-.8, .3, -.1)), arm(-1, (-1, .3, -.3), (-.8, .3, -.1)),
    {"Torso": V(1.0, 0, 1), "Chest": V(1.1, 0, 1), "Head": V(-.3, 0, 1)}), {"Root": (24, 0, 0)})
pose("DASHBASE", d=merge(legs(1, 1.3, 1.5, spread=.32, ankle=.6), legs(-1, .7, 1.7, spread=.32, ankle=.7),
                         {"Torso": V(.55, 0, 1), "Chest": V(.6, 0, 1), "Head": V(-.3, 0, 1), "Jaw": V(1, 0, -.4),
                          "Tail1": V(-1, 0, .04), "Tail2": V(-1, 0, .02), "Tail3": V(-1, 0, 0)},
                         arm(1, (.1, 1, -.05), (.1, 1, -.05), (1, .1)), arm(-1, (.1, 1, -.05), (.1, 1, -.05), (1, .1))),
     rot={"Root": (20, 8, 0), "Tail1": (0, 0, -20), "Tail2": (0, 0, -20), "Tail3": (0, 0, -20),
          "Head": (0, 0, 24)}, root=(0, 0, .0))
for _yaw in (120, 300, 480, 660, 720):
    spun("DASHBASE", "DASH_%d" % _yaw, yaw=-_yaw)
spun("RUN_R", "RUN_R_720", yaw=-720)
spun("RUN_L", "RUN_L_720", yaw=-720)

pose("PUNCH_COIL", d=merge(legs(1, 1.4, 2.0, spread=.24), legs(-1, 1.4, 2.0, spread=.24),
                            {"Torso": V(.45, 0, 1), "Chest": V(.5, 0, 1), "Head": V(-.3, 0, 1),
                             "Tail1": V(-1, 0, .3), "Tail2": V(-1, 0, .24), "Tail3": V(-1, 0, .12)},
                            arm(-1, (-.4, -.05, -.95), (.2, -.05, -1), (1, -.4)), arm(1, (.8, .3, -.4), (1, .2, .3))),
     rot={"Root": (10, 0, -12)}, root=(0, .05, -.02))
pose("PUNCH_UP", d=merge(legs(1, .1, .05, ankle=1.3, spread=.14), legs(-1, .05, .05, ankle=1.3, spread=.14),
                          {"Torso": V(-.08, 0, 1), "Chest": V(-.18, 0, 1), "Head": V(-.55, 0, 1), "Jaw": V(1, 0, -.9),
                           "Tail1": V(-1, 0, -.15), "Tail2": V(-1, 0, -.25), "Tail3": V(-1, 0, -.30)},
                          arm(-1, (.05, -.05, 1.3), (.02, -.05, 1.3), (1, .7)), arm(1, (-.3, .5, -.8), (-.2, .3, -1))),
     rot={"Root": (-6, 0, -14), "Chest": (0, 0, -10)}, root=(0, -.02, .12), air=True)
pose("PUNCH_HOLD", d=POSES["PUNCH_UP"]["d"], rot={"Root": (-8, 0, -16), "Chest": (0, 0, -12)}, root=(0, -.02, .18), air=True)
pose("PUNCH_FALL", d=merge(legs(1, .5, .9, ankle=.9, spread=.2), legs(-1, .5, .9, ankle=.9, spread=.2),
                            {"Torso": V(.10, 0, 1), "Chest": V(.10, 0, 1), "Head": V(-.2, 0, 1),
                             "Tail1": V(-1, 0, .1), "Tail2": V(-1, 0, .05), "Tail3": V(-1, 0, 0)},
                            arm(1, (.3, .8, .3), (.3, .8, .2)), arm(-1, (.3, .8, .3), (.3, .8, .2))),
     root=(0, 0, .08), air=True)

pose("STOMP_RAISE", d=merge(legs(1, .5, 1.3, ankle=.8, spread=.2),
                            {"Thigh_R": V(1.3, -.15, .6), "Leg_R": V(-.3, -.05, -1), "Foot_R": V(.5, 0, -1),
                             "Toe_R_mid": V(.6, 0, -1)},
                            {"Torso": V(-.12, 0, 1), "Chest": V(-.18, 0, 1), "Head": V(-.4, 0, 1), "Jaw": V(1, 0, -.4),
                             "Tail1": V(-1, 0, .25), "Tail2": V(-1, 0, .2), "Tail3": V(-1, 0, .1)},
                            arm(1, (.15, 1, .25), (.15, 1, .2), (1, .5)), arm(-1, (.15, 1, .25), (.15, 1, .2), (1, .5))),
     rot={"Root": (-8, 0, 0)}, root=(0, .0, .14), air=True)
pose("STOMP_HIT", d=merge(legs(1, 1.6, 2.0, ankle=.8, spread=.2),
                          {"Thigh_R": V(.35, -.12, -1), "Leg_R": V(0, -.05, -1), "Foot_R": V(.3, 0, -1),
                           "Toe_R_mid": V(.4, 0, -1)},
                          {"Torso": V(.45, 0, 1), "Chest": V(.5, 0, 1), "Head": V(-.1, 0, 1), "Jaw": V(1, 0, -.8),
                           "Tail1": V(-1, 0, .4), "Tail2": V(-1, 0, .32), "Tail3": V(-1, 0, .2)},
                          arm(1, (-.5, .9, .1), (-.5, .9, 0), (1, .3)), arm(-1, (-.5, .9, .1), (-.5, .9, 0), (1, .3))),
     rot={"Root": (12, 0, 0)}, root=(0, -.06, .04), air=True)

# ---- wall climbing (loop): hand over hand, feet pressed to the wall
pair_p("CLIMB_L", {"d": merge(
    arm(1, (.5, .15, 1.1), (.5, .10, 1.1), (1, .5)), arm(-1, (.6, .25, -.15), (.7, -.15, .55), (1, -.1)),
    {"Thigh_L": V(1.3, .30, .35), "Leg_L": V(-.2, .10, -1), "Foot_L": V(.5, 0, -.8), "Toe_L_mid": V(.7, 0, -.5)},
    {"Thigh_R": V(.5, -.25, -1), "Leg_R": V(-.5, -.05, -1), "Foot_R": V(1, 0, -.4), "Toe_R_mid": V(1, 0, -.05)},
    {"Torso": V(.15, 0, 1), "Chest": V(.2, 0, 1), "Head": V(-.55, 0, 1), "Jaw": V(1, 0, -.2),
     "Tail1": V(-1, 0, -.35), "Tail2": V(-1, 0, -.5), "Tail3": V(-1, 0, -.6)}),
    "rot": {"Root": (0, -4, 6), "Chest": (0, 0, -5)}, "root": (0, 0, 0), "air": True})

# ---- flash step: a low, stretched slide (the entity itself does the travelling)
POSES["FLASH_LEAN"] = over("RUN_L", POSES["DASH_LOW"]["d"], {"Root": (26, 0, 0), "Tail1": (0, 0, 0)})
POSES["FLASH_SLIDE"] = over("FLASH_LEAN", {"Tail1": V(-1, 0, .15), "Tail2": V(-1, 0, .1), "Tail3": V(-1, 0, .05),
                                          "Head": V(-.1, 0, 1)}, {"Root": (32, 0, 0)})
POSES["FLASH_LEAN"]["root"] = (0, .05, 0)
POSES["FLASH_SLIDE"]["root"] = (0, -.10, 0)

# ---- hanging on a wall / from a ceiling: holds and the hand-over-hand crawl
_WALL_LEGS = merge({"Thigh_L": V(1.2, .30, .35), "Leg_L": V(-.2, .10, -1), "Foot_L": V(.5, 0, -.8), "Toe_L_mid": V(.7, 0, -.5)},
                   {"Thigh_R": V(1.0, -.30, .20), "Leg_R": V(-.25, -.10, -1), "Foot_R": V(.5, 0, -.8), "Toe_R_mid": V(.7, 0, -.5)})
_WALL_BODY = {"Torso": V(.10, 0, 1), "Chest": V(.14, 0, 1), "Head": V(-.5, 0, 1), "Jaw": V(1, 0, -.15),
              "Tail1": V(-1, 0, -.4), "Tail2": V(-1, 0, -.55), "Tail3": V(-1, 0, -.65)}
pose("WALL_HOLD_A", d=merge(_WALL_LEGS, _WALL_BODY, arm(1, (.5, .30, 1.1), (.5, .25, 1.1), (1, .5)),
                            arm(-1, (.5, .30, .85), (.5, .25, .85), (1, .3))),
     rot={"Root": (0, 0, 0)}, root=(0, 0, 0), air=True)
pose("WALL_HOLD_B", d=merge(_WALL_LEGS, _WALL_BODY, {"Chest": V(.20, 0, 1), "Head": V(-.4, .1, 1)},
                            arm(1, (.5, .30, 1.0), (.5, .25, 1.0), (1, .45)), arm(-1, (.5, .30, .95), (.5, .25, .95), (1, .4))),
     rot={"Root": (0, -2, 2), "Head": (0, 0, 8), "Tail1": (0, 0, 8), "Tail2": (0, 0, 8), "Tail3": (0, 0, 8)},
     root=(0, 0, 0), air=True)

_CEIL_BODY = {"Torso": V(0, 0, 1), "Chest": V(-.05, 0, 1), "Head": V(-.35, 0, 1), "Jaw": V(1, 0, -.1),
              "Tail1": V(-1, 0, -.7), "Tail2": V(-1, 0, -.8), "Tail3": V(-1, 0, -.9)}
_CEIL_LEGS = merge(legs(1, .35, .5, ankle=.8, spread=.15), legs(-1, .35, .5, ankle=.8, spread=.15))
pose("CEIL_HANG_A", d=merge(_CEIL_LEGS, _CEIL_BODY, arm(1, (.15, .30, 1.35), (.10, .30, 1.4), (1, .6)),
                            arm(-1, (.15, .30, 1.35), (.10, .30, 1.4), (1, .6))),
     rot={"Root": (-5, 0, 0), "Tail1": (0, 0, 0)}, root=(0, 0, 0), air=True)
pose("CEIL_HANG_B", d=merge(_CEIL_LEGS, _CEIL_BODY, arm(1, (.15, .30, 1.35), (.10, .30, 1.4), (1, .6)),
                            arm(-1, (.15, .30, 1.35), (.10, .30, 1.4), (1, .6)), {"Head": V(-.3, .1, 1)}),
     rot={"Root": (6, 0, 0), "Head": (0, 0, 6), "Tail1": (0, 0, 6), "Tail2": (0, 0, 6), "Tail3": (0, 0, 6)},
     root=(0, 0, 0), air=True)
pair_p("CEILM_L", {"d": merge(legs(1, .9, .9, ankle=.8, spread=.15), legs(-1, -.2, .5, ankle=.8, spread=.15), _CEIL_BODY,
                              arm(1, (.75, .20, 1.1), (.7, .2, 1.15), (1, .6)), arm(-1, (-.1, .25, 1.3), (-.2, .25, 1.35), (1, .6))),
                   "rot": {"Root": (10, -3, 6), "Chest": (0, 0, -6), "Tail1": (0, 0, -10), "Tail2": (0, 0, -10),
                           "Tail3": (0, 0, -10)}, "root": (0, 0, 0), "air": True})

# ------------------------------------------------------------------------ clip definitions
# (frame, pose, ease into this key, marker). Slow, weighted preparation (`out`/`io`), an explosive `snap`
# into the hit, a held impact pose, then a long relaxed recovery. Combo members share carry poses.
CLIPS = {
    "idle": (True, 64, [(0, "STANCE", "io", "settle"), (10, "SWAY_L", "io", "shift"), (21, "BOB", "io", "bob"),
                        (32, "STANCE", "io", "settle"), (42, "SWAY_R", "io", "shift"), (53, "BOB", "io", "bob"),
                        (64, "STANCE", "io", "settle")]),
    "walk": (True, 40, [(0, "WALK_L", "io", "left contact"), (10, "WALK_PASS_L", "io", "pass"),
                        (20, "WALK_R", "io", "right contact"), (30, "WALK_PASS_R", "io", "pass"),
                        (40, "WALK_L", "io", "left contact")]),
    "run": (True, 20, [(0, "RUN_L", "io", "left contact"), (5, "RUN_FLY_L", "out", "flight"),
                       (10, "RUN_R", "in", "right contact"), (15, "RUN_FLY_R", "out", "flight"),
                       (20, "RUN_L", "in", "left contact")]),
    # claws: coil slowly, burst into the hit, hold it, then flow into the other hand's chamber
    "claw_right": (False, 18, [(0, "CARRY_R", "io", "ready"), (7, "F_R", "io", "wind"),
                               (9, "SWING_R", "snap", "swing"), (10, "STRIKE_R", "snap", "impact"),
                               (12, "STRIKE_R", "lin", "hold"), (14, "FOLLOW_R", "out", "follow"),
                               (18, "CARRY_L", "io", "flow")]),
    "claw_left": (False, 18, [(0, "CARRY_L", "io", "ready"), (7, "F_L", "io", "wind"),
                              (9, "SWING_L", "snap", "swing"), (10, "STRIKE_L", "snap", "impact"),
                              (12, "STRIKE_L", "lin", "hold"), (14, "FOLLOW_L", "out", "follow"),
                              (18, "CARRY_R", "io", "flow")]),
    "flurry_right": (False, 12, [(0, "CARRY_R", "io", "ready"), (4, "F_R", "out", "wind"),
                                 (6, "STRIKE_R", "snap", "impact"), (7, "STRIKE_R", "lin", "hold"),
                                 (12, "CARRY_L", "io", "flow")]),
    "flurry_left": (False, 12, [(0, "CARRY_L", "io", "ready"), (4, "F_L", "out", "wind"),
                                (6, "STRIKE_L", "snap", "impact"), (7, "STRIKE_L", "lin", "hold"),
                                (12, "CARRY_R", "io", "flow")]),
    "rise_right": (False, 18, [(0, "CARRY_R", "io", "ready"), (7, "DIP_R", "io", "load"),
                               (10, "RISE_HIT_R", "snap", "rise"), (12, "RISE_HIT_R", "lin", "hold"),
                               (14, "RISE_OVER_R", "out", "follow"), (18, "CARRY_L", "io", "flow")]),
    "rise_left": (False, 18, [(0, "CARRY_L", "io", "ready"), (7, "DIP_L", "io", "load"),
                              (10, "RISE_HIT_L", "snap", "rise"), (12, "RISE_HIT_L", "lin", "hold"),
                              (14, "RISE_OVER_L", "out", "follow"), (18, "CARRY_R", "io", "flow")]),
    "bite": (False, 20, [(0, "CARRY_R", "io", "ready"), (7, "BITE_COIL", "io", "coil"),
                         (10, "BITE_LUNGE", "snap", "lunge"), (11, "BITE_CLAMP", "snap", "bite"),
                         (14, "BITE_SHAKE", "out", "shake"), (16, "BITE_CLAMP", "io", "hold"),
                         (20, "STANCE", "io", "recover")]),
    "tail_slam": (False, 28, [(0, "SPIN_READY", "io", "ready"), (7, "SPIN_COIL", "io", "coil"),
                              (14, "SPIN_MID", "in", "spin"), (17, "SPIN_HIT", "snap", "impact"),
                              (21, "SPIN_END", "out", "follow"), (28, "SPIN_END", "io", "settle")]),
    "launcher": (False, 22, [(0, "STANCE", "io", "ready"), (7, "LOAD_LOW", "io", "load"),
                             (10, "FLIP_KICK_A", "snap", "kick"), (12, "FLIP_KICK_B", "lin", "launch"),
                             (15, "FLIP_TUCK", "out", "flip"), (18, "FLIP_OPEN", "in", "open"),
                             (20, "LAND_BACK", "snap", "land"), (22, "STANCE_BACK", "out", "recover")]),
    "climb": (True, 20, [(0, "CLIMB_L", "io", "reach"), (10, "CLIMB_R", "io", "reach"), (20, "CLIMB_L", "io", "reach")]),
    "flash_step": (False, 10, [(0, "STANCE", "io", "ready"), (2, "FLASH_LEAN", "snap", "burst"),
                               (5, "FLASH_SLIDE", "lin", "slide"), (8, "LAND_LOW", "out", "skid"),
                               (10, "STANCE", "io", "recover")]),
    "wall_hold": (True, 40, [(0, "WALL_HOLD_A", "io", "hold"), (20, "WALL_HOLD_B", "io", "breathe"), (40, "WALL_HOLD_A", "io", "hold")]),
    "ceiling_hang": (True, 48, [(0, "CEIL_HANG_A", "io", "hang"), (24, "CEIL_HANG_B", "io", "sway"), (48, "CEIL_HANG_A", "io", "hang")]),
    "ceiling_move": (True, 24, [(0, "CEILM_L", "io", "reach"), (12, "CEILM_R", "io", "reach"), (24, "CEILM_L", "io", "reach")]),
    "run_claw_left": (False, 14, [(0, "RUN_L", "io", "ready"), (3, "RCW_L", "out", "wind"),
                                  (6, "RCS_L", "snap", "impact"), (8, "RCS_L", "lin", "hold"),
                                  (10, "RCF_L", "out", "follow"), (12, "RUN_FLY_R", "io", "flow"),
                                  (14, "RUN_L", "io", "run")]),
    "run_claw_right": (False, 10, [(0, "RUN_R", "io", "ready"), (2, "RCW_R", "out", "wind"),
                                   (5, "RCS_R", "snap", "impact"), (7, "RCS_R", "lin", "hold"),
                                   (10, "RUN_L", "io", "run")]),
    "run_bite": (False, 14, [(0, "RUN_L", "io", "ready"), (4, "RB_OPEN", "out", "lunge"),
                             (6, "RB_CLAMP", "snap", "bite"), (9, "RB_SHAKE", "out", "shake"),
                             (12, "RUN_FLY_R", "io", "flow"), (14, "RUN_L", "io", "run")]),
    "dash_spin": (False, 26, [(0, "RUN_L", "io", "ready"), (4, "DASH_LOW", "out", "coil"),
                              (7, "DASH_120", "snap", "spin"), (10, "DASH_300", "lin", "impact"),
                              (14, "DASH_480", "lin", "impact"), (18, "DASH_660", "lin", "impact"),
                              (21, "DASH_720", "lin", "follow"), (23, "RUN_R_720", "out", "flow"),
                              (26, "RUN_L_720", "io", "run")]),
    "rise_punch": (False, 16, [(0, "STANCE", "io", "ready"), (4, "PUNCH_COIL", "out", "load"),
                               (7, "PUNCH_UP", "snap", "impact"), (10, "PUNCH_HOLD", "lin", "hold"),
                               (13, "PUNCH_FALL", "out", "follow"), (16, "STANCE", "io", "recover")]),
    "spike": (False, 16, [(0, "STANCE", "io", "ready"), (4, "STOMP_RAISE", "out", "load"),
                          (7, "STOMP_HIT", "snap", "impact"), (9, "STOMP_HIT", "lin", "hold"),
                          (12, "LAND_LOW", "in", "land"), (16, "STANCE", "out", "recover")]),
    "leap_charge": (False, 16, [(0, "STANCE", "io", "ready"), (6, "LEAP_SWING", "io", "load"),
                                (9, "LEAP_SPRING", "snap", "launch"), (12, "LAND_LOW", "in", "land"),
                                (16, "STANCE", "out", "recover")]),
    "leap": (False, 30, [(0, "STANCE", "io", "ready"), (8, "LOAD_LOW", "io", "load"),
                         (11, "POUNCE_A", "snap", "launch"), (15, "POUNCE_B", "out", "flip"),
                         (19, "POUNCE_C", "lin", "strike"), (22, "LAND_LEAP", "snap", "land"),
                         (30, "STANCE_FRONT", "out", "recover")]),
    "jump": (False, 24, [(0, "STANCE", "io", "ready"), (6, "LOAD_LOW", "io", "load"),
                         (9, "TWIST_A", "snap", "launch"), (13, "TWIST_B", "out", "twist"),
                         (17, "TWIST_C", "in", "open"), (19, "LAND_TWIST", "snap", "land"),
                         (24, "STANCE_TWIST", "out", "recover")]),
    "dodge": (False, 18, [(0, "STANCE", "io", "ready"), (4, "SIDE_COIL", "out", "coil"),
                          (7, "SIDE_A", "snap", "spring"), (10, "SIDE_B", "lin", "invert"),
                          (13, "SIDE_C", "lin", "open"), (15, "LAND_SIDE", "snap", "land"),
                          (18, "STANCE_SIDE", "out", "recover")]),
    "guard": (False, 16, [(0, "STANCE", "io", "ready"), (5, "GUARD_UP", "out", "set"),
                          (8, "GUARD_HIT", "snap", "hold"), (11, "GUARD_UP", "out", "hold"),
                          (16, "STANCE", "io", "recover")]),
    "skill_charge": (False, 22, [(0, "STANCE", "io", "ready"), (9, "GATHER", "io", "wind"),
                                 (12, "GATHER_A", "lin", "hold"), (15, "GATHER_B", "lin", "hold"),
                                 (18, "GATHER_A", "lin", "hold"), (22, "STANCE", "snap", "recover")]),
    "skill_thrust": (False, 14, [(0, "STANCE", "io", "ready"), (5, "THRUST_LOAD", "io", "load"),
                                 (8, "THRUST_HIT", "snap", "impact"), (10, "THRUST_HOLD", "lin", "hold"),
                                 (14, "STANCE", "io", "recover")]),
    "duck_rake_bite": (False, 38, [(0, "STANCE", "io", "ready"), (6, "DUCK_L", "out", "duck"),
                                   (11, "SPIN_LOAD", "io", "load"), (16, "SPIN_RAKE", "snap", "rake"),
                                   (19, "SPIN_RAKE_END", "out", "follow"), (22, "BITE_COIL_360", "io", "drive"),
                                   (25, "BITE_LUNGE_360", "snap", "lunge"), (26, "BITE_CLAMP_360", "snap", "bite"),
                                   (29, "BITE_SHAKE_360", "out", "shake"), (38, "STANCE_360", "io", "recover")]),
    "hop_tail_claw": (False, 44, [(0, "STANCE", "io", "ready"), (7, "LOAD_LOW", "io", "load"),
                                  (10, "LEAP_SPRING", "snap", "hop"), (14, "AIR_TAIL_A", "out", "spin"),
                                  (17, "AIR_TAIL_B", "in", "tail impact"), (20, "LAND_TAILSPIN", "snap", "land"),
                                  (24, "CARRY_R_TS", "io", "recover"), (29, "F_R_TS", "io", "claw load"),
                                  (32, "STRIKE_R_TS", "snap", "claw impact"), (35, "FOLLOW_R_TS", "out", "follow"),
                                  (44, "STANCE_TS", "io", "recover")]),
}
# leap_charge lands from a hop: give it its own low landing pose
POSES["LAND_LOW"] = POSES["LOAD_LOW"]


MISSING = [k for k, (_, _, ks) in CLIPS.items() for _, p, _, _ in ks if p not in POSES]
assert not MISSING, MISSING

# lag (frames) of relative motion, so trailing parts whip after the body
OVERLAP = {"Tail1": 1.0, "Tail2": 2.0, "Tail3": 3.0, "Jaw": 1.0, "Hand_L": .5, "Hand_R": .5}

EASE = {
    "lin": lambda t: t,
    "io": lambda t: t * t * (3 - 2 * t),
    "in": lambda t: t ** 2.0,
    "out": lambda t: 1 - (1 - t) ** 2.4,
    "snap": lambda t: t ** 3.4,
    "back": lambda t: 1 + 2.70158 * (t - 1) ** 3 + 1.70158 * (t - 1) ** 2,
    "hold": lambda t: 0.0,
}

# ------------------------------------------------------------------------ rig math
LA.import_all(LA.find_project())
rig = bpy.data.objects[LA.ARMATURE]
scene = bpy.context.scene
analysis = ef.analyze_rig(rig, overrides={"driver": "Root"})
AXES = {n: Vector(v).normalized() for n, v in MX.LIMB_AXIS.items()}
PARENT = {b.name: (b.parent.name if b.parent else None) for b in rig.data.bones}
REST_HEAD = {b.name: b.head_local.copy() for b in rig.data.bones}
ORDER = sorted(PARENT, key=lambda n: len(rig.pose.bones[n].parent_recursive))
FOOT_POINTS = ("Foot_L", "Foot_R", "Toe_L_mid", "Toe_R_mid")


def primaries(p):
    t = dict(STANCE_D)
    t.update(p["d"])
    return t


def derive(t):
    """Add the deform helper bones that follow their limb (wrist blend, elbow, knee, toe fan)."""
    t = dict(t)
    for side in ("L", "R"):
        a, h = Vector(t["Arm_" + side]), Vector(t["Hand_" + side])
        t["Hand_" + side] = (a * .15 + h * .85).normalized()
        t["Elbow_" + side] = (a * .50 + h * .50).normalized()
        t["Knee_" + side] = (Vector(t["Thigh_" + side]) + Vector(t["Leg_" + side])).normalized()
        for toe in ("in", "out"):
            fan = .10 if toe == "in" else -.10
            if side == "R":
                fan = -fan
            t["Toe_%s_%s" % (side, toe)] = (Vector(t["Toe_%s_mid" % side]) + Vector((fan, 0, 0))).normalized()
    return t


def absolute_rotations(targets, rots):
    """Armature-space rotation per bone: cumulative body twist composed with the direction swing."""
    targets = derive(targets)
    cum, absolute = {}, {}
    for name in ORDER:
        parent = PARENT[name]
        rx, ry, rz = rots.get(name, (0.0, 0.0, 0.0))
        extra = (Quaternion((0, 0, 1), math.radians(rz)) @ Quaternion((0, 1, 0), math.radians(ry))
                 @ Quaternion((1, 0, 0), math.radians(rx)))
        cum[name] = (cum[parent] if parent in cum else Quaternion()) @ extra
        if name in targets and name in AXES:
            absolute[name] = (cum[name] @ AXES[name].rotation_difference(targets[name])).normalized()
    return absolute


def slerp_ex(q0, q1, t):
    if q0.dot(q1) < 0:
        q1 = Quaternion((-q1.w, -q1.x, -q1.y, -q1.z))
    d = q0.inverted() @ q1
    axis, angle = d.to_axis_angle()
    return (q0 @ Quaternion(axis, angle * t)).normalized() if angle > 1e-9 else q0.copy()


def blend_direction(v0, v1, w):
    """Spherical blend of two directions; w may leave 0..1 for overshoot."""
    if (v0 - v1).length < 1e-9:
        return v0.copy()
    return slerp_ex(Quaternion(), v0.rotation_difference(v1), w) @ v0


PRIMARY_CACHE = {}


def pose_params(name):
    if name not in PRIMARY_CACHE:
        p = POSES[name]
        PRIMARY_CACHE[name] = (primaries(p), p["rot"])
    return PRIMARY_CACHE[name]


def ground_lift(absolute):
    """Root height that puts the lowest sole point back on its rest height."""
    heads = {}
    for name in ORDER:
        if name not in FOOT_POINTS and not name.startswith(("Root", "Thigh", "Leg")):
            continue
        parent = PARENT[name]
        if parent is None:
            heads[name] = REST_HEAD[name].copy()
        elif parent in heads:
            heads[name] = heads[parent] + absolute[parent] @ (REST_HEAD[name] - REST_HEAD[parent])
    return -min(heads[n].z - REST_HEAD[n].z for n in FOOT_POINTS)


def sample(clip_keys, length, f, loop):
    """Absolute rotations, Root position at (fractional) frame f.

    Directions blend spherically and body twist blends in degrees, so a sweep past 180 degrees
    stays one continuous swing instead of taking the short way round.
    """
    f = min(max(f, 0.0), float(length))
    j = 0
    for i in range(len(clip_keys) - 1):
        if clip_keys[i][0] <= f <= clip_keys[i + 1][0]:
            j = i
            break
    (f0, n0, _, _), (f1, n1, ease, _) = clip_keys[j], clip_keys[j + 1]
    t = 0.0 if f1 == f0 else (f - f0) / (f1 - f0)
    w = EASE[ease](t)
    (d0, r0), (d1, r1) = pose_params(n0), pose_params(n1)
    targets = {n: blend_direction(Vector(d0[n]), Vector(d1[n]), w) for n in d0}
    rots = {}
    for n in set(r0) | set(r1):
        a = r0.get(n, (0.0, 0.0, 0.0))
        b = r1.get(n, (0.0, 0.0, 0.0))
        rots[n] = tuple(a[i] + (b[i] - a[i]) * w for i in range(3))
    absolute = absolute_rotations(targets, rots)
    p0, p1 = POSES[n0], POSES[n1]
    root = [p0["root"][i] + (p1["root"][i] - p0["root"][i]) * w for i in range(3)]
    ground = (0.0 if p0["air"] else 1.0) * (1 - w) + (0.0 if p1["air"] else 1.0) * w
    # grounded keys: the feet decide the height; airborne keys: the authored height does
    root[2] = ground * ground_lift(absolute) + (1 - ground) * root[2]
    return absolute, tuple(root)


def local_rotations(absolute):
    out = {}
    for name in ORDER:
        if name not in absolute:
            continue
        parent = PARENT[name]
        q = absolute[parent].inverted() @ absolute[name] if parent in absolute else absolute[name]
        out[name] = q.normalized()
    return out


def frame_data(keys, length, loop, f):
    absolute, root = sample(keys, length, f, loop)
    local = local_rotations(absolute)
    for name, lag in OVERLAP.items():
        if name not in local:
            continue
        if not loop:  # fade the lag out so the clip still ends exactly on STANCE
            edge = min(max((length - f) / (lag + 2.0), 0.0), 1.0)
            lag *= edge * edge * (3 - 2 * edge)
        g = f - lag
        if loop:
            g %= length
        lag_abs, _ = sample(keys, length, max(g, 0.0), loop)
        local[name] = local_rotations(lag_abs)[name]
    return local, root


results, authored = {}, {}
for clip, (loop, length, keys) in CLIPS.items():
    keyposes = []
    for f in range(length + 1):
        local, root = frame_data(keys, length, loop, float(f))
        marker = next((m for kf, _, _, m in keys if kf == f), None)
        kp = {"frame": f, "local_quaternions": {n: list(q) for n, q in local.items()},
              "translations": {"Root": list(root)}}
        if marker:
            kp["marker"] = marker
        keyposes.append(kp)
    try:
        report = ef.author_motion(rig, keyposes, analysis, name="%s.acro" % (LA.PREFIX + clip))
        action = bpy.data.actions[report["action"]]
        action.use_frame_range = True
        action.frame_start = 0
        action.frame_end = float(length)
        action.use_cyclic = loop
        action["gecko_loop"] = loop
        LA.assign_action(rig, action)
        names = sorted(keyposes[0]["local_quaternions"])
        previous = {}
        for n in names:
            prop = ef._rotation_prop(rig, n)
            for fc in ef._bone_fcurves(rig, n, prop):
                fc.keyframe_points.clear()
        for fc in ef._bone_fcurves(rig, "Root", "location"):
            fc.keyframe_points.clear()
        for half in range(length * 2 + 1):
            f = half / 2.0
            local, root = frame_data(keys, length, loop, f)
            for n in names:
                p = rig.pose.bones[n]
                prop = ef._rotation_prop(rig, n)
                q = local[n]
                if p.rotation_mode == "QUATERNION":
                    last = previous.get(n)
                    if last is not None and last.dot(q) < 0:
                        q = Quaternion((-q.w, -q.x, -q.y, -q.z))
                    previous[n] = q.copy()
                    p.rotation_quaternion = q
                else:
                    e = q.to_euler(p.rotation_mode, previous[n]) if n in previous else q.to_euler(p.rotation_mode)
                    previous[n] = e
                    p.rotation_euler = e
                p.keyframe_insert(data_path=prop, frame=f, group=n)
            rig.pose.bones["Root"].location = root
            rig.pose.bones["Root"].keyframe_insert(data_path="location", frame=f, group="Root")
        for n in names:
            for fc in ef._bone_fcurves(rig, n, ef._rotation_prop(rig, n)):
                for kp in fc.keyframe_points:
                    kp.interpolation = "LINEAR"
        for fc in ef._bone_fcurves(rig, "Root", "location"):
            for kp in fc.keyframe_points:
                kp.interpolation = "LINEAR"
        authored[clip] = action
        results[clip] = {"status": "authored", "loop": loop, "frames": length,
                         "poses": [k[1] for k in keys]}
    except Exception as exc:
        import traceback
        traceback.print_exc()
        results[clip] = {"status": "FAILED", "error": "%s: %s" % (type(exc).__name__, exc)}

for clip, action in authored.items():
    shipped = LA.PREFIX + clip
    old = bpy.data.actions.get(shipped)
    if old is not None and old is not action:
        bpy.data.actions.remove(old)
    action.name = shipped
    action.use_fake_user = True

bpy.ops.wm.save_as_mainfile(filepath=str(OUT_BLEND))
OUT_REPORT.write_text(json.dumps(results, indent=2), encoding="utf-8")
print("EXPORT", LA.export_all(str(PROJECT), str(OUT_JSON)))
for clip in sorted(results):
    r = results[clip]
    print("%-14s %s" % (clip, r["status"] if r["status"] != "authored" else
                        "authored %2df loop=%-5s" % (r["frames"], r["loop"])))
print("authored %d/%d" % (len(authored), len(CLIPS)))
