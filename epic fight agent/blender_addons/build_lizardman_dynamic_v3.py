"""Author every Lizardman clip again as high-energy, fully-committed motion.

Uses the epicfight API (`analyze_rig`, `author_motion`) for the action creation and markers, and adds
what makes motion read as *dynamic*:

  * poses are built from part DIRECTIONS (the rig's bone axes are +Y stubs) plus cumulative body
    twist/pitch/roll ("rot"), so hips, torso, chest and head can counter-rotate
  * every attack has anticipation -> snap -> overshoot -> settle, using per-segment easing
    (`snap` accelerates into impact, `back` overshoots, `out` decelerates on recovery)
  * the Root is driven for lunges, dips and hops, and its height is solved from the feet each
    half-frame (forward kinematics), so grounded poses keep their feet on the floor and only
    airborne keys leave it
  * tail / jaw follow with a frame-lag overlap so they whip instead of moving in lockstep
  * one-shots start and end on STANCE == idle frame 0, so controller hand-offs do not pop

Clip names and lengths are unchanged (Java attack timing keeps working).
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

OUT_JSON = ROOT / "sampleRig" / "gourmet2_lizardman_dynamic_v3.json"
OUT_BLEND = ROOT / "sampleRig" / "Gourmet2_Lizardman_Dynamic_V3.blend"
OUT_REPORT = ROOT / "sampleRig" / "lizardman_dynamic_v3_report.json"


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


# ---- core / idle
pose("STANCE")
pose("IN_L", d=merge({"Chest": V(.10, 0, 1.05), "Torso": V(.16, 0, 1), "Head": V(-.10, 0, 1), "Jaw": V(1, 0, -.30)},
                     arm(1, (.50, .26, -.78), (1, .1, -.1), (1, -.10)), arm(-1, (.50, .26, -.78), (1, .1, -.1), (1, -.10))),
     rot={"Root": (0, -3, 3), "Head": (0, 3, 14), "Tail1": (0, 0, 8), "Tail2": (0, 0, 9), "Tail3": (0, 0, 10)},
     root=(.020, .0, .0))
pose("EX_L", d=merge({"Chest": V(.26, 0, 1), "Torso": V(.24, 0, 1), "Head": V(-.02, 0, 1), "Jaw": V(1, 0, -.75),
                      "Tail2": V(-1, 0, .28), "Tail3": V(-1, 0, .25)},
                     arm(1, (.62, .18, -.72), (1, .1, .1), (1, -.4)), arm(-1, (.40, .22, -.86), (.9, .1, -.4))),
     rot={"Root": (2, -2, 6), "Head": (0, 6, 34), "Tail1": (0, 0, -6), "Tail2": (0, 0, -9), "Tail3": (0, 0, -10)},
     root=(.012, .0, -.01))
pose("SETTLE", d=merge({"Chest": V(.16, 0, 1.02), "Jaw": V(1, 0, -.05)}),
     rot={"Head": (0, 0, -6), "Tail1": (0, 0, -8), "Tail2": (0, 0, -7), "Tail3": (0, 0, -6)}, root=(-.006, 0, 0))
POSES["IN_R"] = mirror_pose(POSES["IN_L"])
POSES["EX_R"] = mirror_pose(POSES["EX_L"])
pose("SETTLE_R", d=POSES["SETTLE"]["d"], rot={"Head": (0, 0, 6), "Tail1": (0, 0, 8), "Tail2": (0, 0, 7), "Tail3": (0, 0, 6)},
     root=(.006, 0, 0))

# ---- locomotion
pose("WALK_L", d=merge(legs(1, 1.45, .55), legs(-1, -.80, 1.55, ankle=.55),
                        {"Torso": V(.26, 0, 1), "Chest": V(.28, 0, 1), "Head": V(-.12, 0, 1),
                         "Tail1": V(-1, 0, .10), "Tail2": V(-1, 0, .20), "Tail3": V(-1, 0, .16)},
                        arm(-1, (.95, .12, -.65), (1, .1, -.05)), arm(1, (-.45, .2, -.95), (-.2, .2, -1))),
     rot={"Root": (2, 3, -8), "Torso": (0, 0, 10), "Chest": (0, 0, 8), "Head": (0, 0, -14),
          "Tail1": (0, 0, 10), "Tail2": (0, 0, 12), "Tail3": (0, 0, 14)}, root=(.02, 0, 0))
pose("WALK_PASS_L", d=merge(legs(-1, .15, .35), legs(1, 1.0, 2.1, ankle=.85),
                             {"Torso": V(.24, 0, 1), "Chest": V(.26, 0, 1), "Head": V(-.10, 0, 1),
                              "Tail1": V(-1, 0, .16), "Tail2": V(-1, 0, .26), "Tail3": V(-1, 0, .20)},
                             arm(1, (.30, .2, -.9), (.6, .1, -.6)), arm(-1, (.30, .2, -.9), (.6, .1, -.6))),
     rot={"Root": (0, -4, 0), "Torso": (0, 0, 0), "Chest": (0, 0, 0)}, root=(0, 0, 0))
POSES["WALK_R"] = mirror_pose(POSES["WALK_L"])
POSES["WALK_PASS_R"] = mirror_pose(POSES["WALK_PASS_L"])

pose("RUN_L", d=merge(legs(1, 1.75, .40, ankle=.50), legs(-1, -1.30, 2.30, ankle=.62),
                       {"Torso": V(.55, 0, 1), "Chest": V(.62, 0, 1), "Head": V(-.42, 0, 1), "Jaw": V(1, 0, -.35),
                        "Tail1": V(-1, 0, .34), "Tail2": V(-1, 0, .30), "Tail3": V(-1, 0, .16)},
                       arm(-1, (1.0, .30, -.10), (1, .1, .45)), arm(1, (-.95, .30, -.55), (-.4, .2, -1))),
     rot={"Root": (6, 4, -10), "Torso": (0, 0, 12), "Chest": (0, 0, 10), "Head": (0, 0, -18),
          "Tail1": (0, 0, 12), "Tail2": (0, 0, 14), "Tail3": (0, 0, 16)}, root=(.02, .02, 0))
pose("RUN_FLY_L", d=merge(legs(-1, 1.55, 1.30, ankle=.7), legs(1, -1.30, .55, ankle=1.1),
                           {"Torso": V(.60, 0, 1), "Chest": V(.66, 0, 1), "Head": V(-.46, 0, 1), "Jaw": V(1, 0, -.45),
                            "Tail1": V(-1, 0, .42), "Tail2": V(-1, 0, .34), "Tail3": V(-1, 0, .18)},
                           arm(1, (1.0, .50, -.05), (1, .1, .5)), arm(-1, (-1.0, .40, -.20), (-.6, .2, -.6))),
     rot={"Root": (10, 0, 0), "Torso": (0, 0, 0), "Chest": (0, 0, 0)}, root=(0, .0, .16), air=True)
POSES["RUN_R"] = mirror_pose(POSES["RUN_L"])
POSES["RUN_FLY_R"] = mirror_pose(POSES["RUN_FLY_L"])

# ---- crouch / jump family
CROUCH_LEGS = merge(legs(1, 1.7, 2.2, ankle=.5, spread=.22), legs(-1, 1.7, 2.2, ankle=.5, spread=.22))
pose("CROUCH", d=merge(CROUCH_LEGS, {"Torso": V(.55, 0, 1), "Chest": V(.62, 0, 1), "Head": V(-.30, 0, 1),
                                       "Tail1": V(-1, 0, .42), "Tail2": V(-1, 0, .30), "Tail3": V(-1, 0, .18)},
                        arm(1, (-.75, .40, -.55), (-.3, .2, -1)), arm(-1, (-.75, .40, -.55), (-.3, .2, -1))),
     rot={"Root": (10, 0, 0)}, root=(0, .06, 0))
pose("STRETCH", d=merge(legs(1, -.10, -.05, ankle=1.6, spread=.10), legs(-1, -.10, -.05, ankle=1.6, spread=.10),
                         {"Torso": V(-.10, 0, 1), "Chest": V(-.18, 0, 1), "Head": V(-.42, 0, 1), "Jaw": V(1, 0, -.6),
                          "Tail1": V(-1, 0, -.30), "Tail2": V(-1, 0, -.42), "Tail3": V(-1, 0, -.50)},
                         arm(1, (.25, .55, 1), (.2, .3, 1), (1, .7)), arm(-1, (.25, .55, 1), (.2, .3, 1), (1, .7))),
     rot={"Root": (-4, 0, 0)}, root=(0, -.02, .22), air=True)
pose("TUCK", d=merge(legs(1, 2.1, 1.6, ankle=.45, spread=.3), legs(-1, 2.1, 1.6, ankle=.45, spread=.3),
                      {"Torso": V(.55, 0, 1), "Chest": V(.62, 0, 1), "Head": V(-.3, 0, 1),
                       "Tail1": V(-1, 0, .55), "Tail2": V(-1, 0, .45), "Tail3": V(-1, 0, .3)},
                      arm(1, (.9, .55, .05), (1, .2, .2)), arm(-1, (.9, .55, .05), (1, .2, .2))),
     rot={"Root": (8, 0, 0)}, root=(0, -.05, .44), air=True)
pose("REACH_LAND", d=merge(legs(1, 1.2, .35, ankle=1.3, spread=.2), legs(-1, 1.2, .35, ankle=1.3, spread=.2),
                            {"Torso": V(.30, 0, 1), "Chest": V(.34, 0, 1), "Head": V(-.20, 0, 1), "Jaw": V(1, 0, -.4),
                             "Tail1": V(-1, 0, .3), "Tail2": V(-1, 0, .28), "Tail3": V(-1, 0, .2)},
                            arm(1, (.95, .65, .25), (1, .3, .2)), arm(-1, (.95, .65, .25), (1, .3, .2))),
     rot={"Root": (4, 0, 0)}, root=(0, -.02, .16), air=True)
pose("LAND", d=merge(legs(1, 2.0, 2.6, ankle=.4, spread=.30), legs(-1, 2.0, 2.6, ankle=.4, spread=.30),
                      {"Torso": V(.70, 0, 1), "Chest": V(.75, 0, 1), "Head": V(-.42, 0, 1), "Jaw": V(1, 0, -.5),
                       "Tail1": V(-1, 0, .60), "Tail2": V(-1, 0, .42), "Tail3": V(-1, 0, .3)},
                      arm(1, (.6, .75, -.35), (.7, .5, -.3)), arm(-1, (.6, .75, -.35), (.7, .5, -.3))),
     rot={"Root": (14, 0, 0)}, root=(0, .0, 0))

# ---- pounce (leap)
pose("POUNCE_LAUNCH", d=merge(legs(1, -.55, -.10, ankle=1.4, spread=.16), legs(-1, -.55, -.10, ankle=1.4, spread=.16),
                               {"Torso": V(.15, 0, 1), "Chest": V(.10, 0, 1), "Head": V(-.55, 0, 1), "Jaw": V(1, 0, -.7),
                                "Tail1": V(-1, 0, -.15), "Tail2": V(-1, 0, -.25), "Tail3": V(-1, 0, -.30)},
                               arm(1, (1, .55, .45), (1, .3, .5)), arm(-1, (1, .55, .45), (1, .3, .5))),
     rot={"Root": (32, 0, 0)}, root=(0, -.16, .20), air=True)
pose("POUNCE_APEX", d=merge(legs(1, -.9, .55, ankle=.8, spread=.30), legs(-1, -.9, .55, ankle=.8, spread=.30),
                             {"Torso": V(.05, 0, 1), "Chest": V(.0, 0, 1), "Head": V(-.45, 0, 1), "Jaw": V(1, 0, -.95),
                              "Tail1": V(-1, 0, -.05), "Tail2": V(-1, 0, .05), "Tail3": V(-1, 0, .08)},
                             arm(1, (1, .75, .70), (1, .4, .6)), arm(-1, (1, .75, .70), (1, .4, .6))),
     rot={"Root": (52, 0, 0)}, root=(0, -.42, .46), air=True)
pose("POUNCE_STRIKE", d=merge(legs(1, .25, 1.4, ankle=.9, spread=.35), legs(-1, .25, 1.4, ankle=.9, spread=.35),
                               {"Torso": V(.10, 0, 1), "Chest": V(.10, 0, 1), "Head": V(-.6, 0, 1), "Jaw": V(1, 0, -1),
                                "Tail1": V(-1, 0, .20), "Tail2": V(-1, 0, .28), "Tail3": V(-1, 0, .2)},
                               arm(1, (1, .28, .95), (1, .1, .95)), arm(-1, (1, .28, .95), (1, .1, .95))),
     rot={"Root": (60, 0, 0)}, root=(0, -.6, .24), air=True)

# ---- attacks (left-authored, right mirrored)
pair("WIND_L", d=merge(legs(-1, 1.05, 1.5, ankle=.45, spread=.18), legs(1, .30, .55, ankle=.5, spread=.28),
                        {"Torso": V(.30, 0, 1), "Chest": V(.36, 0, 1), "Head": V(-.20, 0, 1), "Jaw": V(1, 0, -.4),
                         "Tail1": V(-1, 0, .28), "Tail2": V(-1, 0, .22), "Tail3": V(-1, 0, .1)},
                        arm(1, (.35, -1.15, .15), (.5, -1, .25), (0.4, -.4)), arm(-1, (-.30, .4, -.95), (-.1, .3, -1))),
       rot={"Root": (6, 0, -12), "Torso": (0, 0, -12), "Chest": (0, 0, -18), "Head": (0, 0, 18),
            "Tail1": (0, 0, 10), "Tail2": (0, 0, 10), "Tail3": (0, 0, 8)}, root=(-.03, .08, -.01))
pair("RAKE_L", d=merge(legs(1, 1.45, .50, ankle=.42, spread=.30), legs(-1, -.75, .60, ankle=1.0, spread=.10),
                        {"Torso": V(.42, 0, 1), "Chest": V(.50, 0, 1), "Head": V(-.28, 0, 1), "Jaw": V(1, 0, -.85),
                         "Tail1": V(-1, 0, .10), "Tail2": V(-1, 0, .10), "Tail3": V(-1, 0, .04)},
                        arm(1, (.95, .90, .05), (1, .55, .05), (1, .4)), arm(-1, (-.55, .6, -.85), (-.2, .4, -1))),
       rot={"Root": (10, -2, 26), "Torso": (0, 0, 16), "Chest": (0, 0, 20), "Head": (0, 0, -28),
            "Tail1": (0, 0, -14), "Tail2": (0, 0, -14), "Tail3": (0, 0, -12)}, root=(.02, -.18, -.02))
pair("RAKE_OVER_L", d=merge(POSES["RAKE_L"]["d"], arm(1, (.35, 1.1, .55), (.4, 1, .6), (1, .5)),
                             {"Torso": V(.40, 0, 1), "Chest": V(.46, 0, 1)}),
       rot={"Root": (10, -4, 34), "Torso": (0, 0, 18), "Chest": (0, 0, 24), "Head": (0, 0, -34),
            "Tail1": (0, 0, -22), "Tail2": (0, 0, -22), "Tail3": (0, 0, -20)}, root=(.03, -.14, -.02))
pair("RAKE_MID_L", d=merge(POSES["RAKE_L"]["d"], arm(1, (1, .1, .15), (1, 0, .2), (1, .1))),
       rot={"Root": (8, 0, 6), "Torso": (0, 0, 6), "Chest": (0, 0, 8), "Head": (0, 0, -8)}, root=(0, -.10, -.02))

# head / bite family
pose("HEAD_RECOIL", d=merge({"Head": V(-.75, 0, 1), "Jaw": V(1, 0, -.45), "Chest": V(-.32, 0, 1), "Torso": V(-.05, 0, 1),
                              "Tail1": V(-1, 0, -.05), "Tail2": V(-1, 0, .02), "Tail3": V(-1, 0, .1)},
                             legs(1, .9, 1.4, spread=.2), legs(-1, .9, 1.4, spread=.2),
                             arm(1, (.30, .3, -.95), (.2, .2, -1)), arm(-1, (.30, .3, -.95), (.2, .2, -1))),
     rot={"Root": (-8, 0, 0)}, root=(0, .10, .0))
pose("BITE_OPEN", d=merge({"Head": V(1.15, 0, .35), "Jaw": V(.55, 0, -1), "Chest": V(.85, 0, 1), "Torso": V(.85, 0, 1),
                            "Tail1": V(-1, 0, .55), "Tail2": V(-1, 0, .40), "Tail3": V(-1, 0, .25)},
                           legs(1, 1.5, .7, spread=.25), legs(-1, .6, 1.5, ankle=.9, spread=.15),
                           arm(1, (.95, .5, -.1), (1, .4, .1)), arm(-1, (.95, .5, -.1), (1, .4, .1))),
     rot={"Root": (20, 0, 0)}, root=(0, -.22, -.02))
pose("BITE_CLAMP", d=merge(POSES["BITE_OPEN"]["d"], {"Jaw": V(1, 0, -.02), "Head": V(1.3, 0, .05)}),
     rot={"Root": (24, 0, 0)}, root=(0, -.28, -.04))
pose("BITE_SHAKE_L", d=POSES["BITE_CLAMP"]["d"], rot={"Root": (24, 0, 0), "Head": (0, 14, 42), "Chest": (0, 0, 10)},
     root=(.02, -.24, -.03))
pose("BITE_SHAKE_R", d=POSES["BITE_CLAMP"]["d"], rot={"Root": (24, 0, 0), "Head": (0, -14, -42), "Chest": (0, 0, -10)},
     root=(-.02, -.24, -.03))

# tail slam
pose("TAIL_COIL", d=merge(legs(1, 1.5, 2.0, spread=.30), legs(-1, 1.5, 2.0, spread=.30),
                           {"Torso": V(.45, 0, 1), "Chest": V(.50, 0, 1), "Head": V(-.28, 0, 1), "Jaw": V(1, 0, -.5),
                            "Tail1": V(-1, 0, .60), "Tail2": V(-1, 0, .50), "Tail3": V(-1, 0, .35)},
                           arm(1, (.6, .5, -.5), (.6, .3, -.5)), arm(-1, (.6, .5, -.5), (.6, .3, -.5))),
     rot={"Root": (6, 0, 34), "Torso": (0, 0, 14), "Chest": (0, 0, 12), "Head": (0, 0, -42),
          "Tail1": (0, 0, 12), "Tail2": (0, 0, 14), "Tail3": (0, 0, 12)}, root=(-.03, .05, 0))
pose("TAIL_SWEEP", d=merge(legs(1, 1.3, 1.3, spread=.35, ankle=.6), legs(-1, .9, 1.5, spread=.25, ankle=.7),
                            {"Torso": V(.55, 0, 1), "Chest": V(.6, 0, 1), "Head": V(-.3, 0, 1), "Jaw": V(1, 0, -.6),
                             "Tail1": V(-1, 0, -.02), "Tail2": V(-1, 0, -.04), "Tail3": V(-1, 0, -.06)},
                            arm(1, (.55, .9, -.1), (.6, .8, 0)), arm(-1, (.55, .4, -.6), (.6, .3, -.7))),
     rot={"Root": (8, 0, -64), "Torso": (0, 0, -20), "Chest": (0, 0, -24), "Head": (0, 0, 60),
          "Tail1": (0, 0, -22), "Tail2": (0, 0, -24), "Tail3": (0, 0, -26)}, root=(.03, -.02, .05))
pose("TAIL_FOLLOW", d=POSES["TAIL_SWEEP"]["d"],
     rot={"Root": (8, 0, -78), "Torso": (0, 0, -24), "Chest": (0, 0, -28), "Head": (0, 0, 72),
          "Tail1": (0, 0, -30), "Tail2": (0, 0, -34), "Tail3": (0, 0, -38)}, root=(.04, -.03, .02))

# guard / dodge
pose("GUARD", d=merge(legs(1, 1.5, 2.0, spread=.30), legs(-1, 1.5, 2.0, spread=.30),
                       {"Torso": V(.65, 0, 1), "Chest": V(.70, 0, 1), "Head": V(-.42, 0, 1), "Jaw": V(1, 0, -.02),
                        "Tail1": V(-1, 0, .45), "Tail2": V(-1, 0, .30), "Tail3": V(-1, 0, .18)},
                       arm(1, (1, -.2, .55), (.3, -.5, 1), (1, -.1)), arm(-1, (1, -.2, .55), (.3, -.5, 1), (1, -.1))),
     rot={"Root": (10, 0, 0)}, root=(0, .05, 0))
pose("GUARD_HIT", d=POSES["GUARD"]["d"], rot={"Root": (4, 0, 0), "Chest": (-6, 0, 0)}, root=(0, .13, .0))
pair("SLIP_L", d=merge(legs(1, 1.9, 2.2, spread=.6, ankle=.5), legs(-1, .2, .9, spread=.05, ankle=.8),
                        {"Torso": V(.55, -.4, 1), "Chest": V(.6, -.3, 1), "Head": V(-.3, .25, 1), "Jaw": V(1, 0, -.4),
                         "Tail1": V(-1, -.6, .3), "Tail2": V(-1, -.7, .2), "Tail3": V(-1, -.8, .1)},
                        arm(1, (.4, .9, -.6), (.5, .6, -.5)), arm(-1, (.5, -.3, -.9), (.6, -.2, -.5))),
       rot={"Root": (6, -14, 16), "Head": (0, 6, -12)}, root=(.22, .02, 0))

# spike / thrust / rise / launcher
pose("SPIKE_WIND", d=merge(legs(1, .2, .6), legs(-1, .8, 1.3, ankle=.6),
                            {"Torso": V(-.2, 0, 1), "Chest": V(-.45, 0, 1), "Head": V(-.7, 0, 1), "Jaw": V(1, 0, -.6),
                             "Tail1": V(-1, 0, .25), "Tail2": V(-1, 0, .2), "Tail3": V(-1, 0, .1)},
                            arm(-1, (-.55, -.1, 1), (-.4, -.1, 1), (1, .7)), arm(1, (.4, .6, -.6), (.6, .4, -.5))),
     rot={"Root": (-10, 0, 8), "Torso": (0, 0, -8), "Chest": (0, 0, -10)}, root=(0, .10, .08))
pose("SPIKE_HIT", d=merge(legs(-1, 1.5, .8), legs(1, -.4, 1.4, ankle=.9),
                           {"Torso": V(1.0, 0, 1), "Chest": V(1.1, 0, 1), "Head": V(.35, 0, 1), "Jaw": V(1, 0, -.9),
                            "Tail1": V(-1, 0, .60), "Tail2": V(-1, 0, .45), "Tail3": V(-1, 0, .3)},
                           arm(-1, (.55, -.05, -1), (.8, -.05, -.7), (1, -.2)), arm(1, (-.4, .7, -.5), (-.2, .5, -.6))),
     rot={"Root": (26, 0, -16), "Torso": (0, 0, -10), "Chest": (0, 0, -12)}, root=(0, -.20, -.06))
pose("SPIKE_OVER", d=POSES["SPIKE_HIT"]["d"], rot={"Root": (32, 0, -20), "Torso": (0, 0, -12), "Chest": (0, 0, -14)},
     root=(0, -.24, -.10))

pose("THRUST_LOAD", d=merge(legs(1, 1.6, 1.9, spread=.2), legs(-1, -.6, 1.6, ankle=.9, spread=.2),
                             {"Torso": V(.5, 0, 1), "Chest": V(.55, 0, 1), "Head": V(-.3, 0, 1), "Jaw": V(1, 0, -.3),
                              "Tail1": V(-1, 0, .5), "Tail2": V(-1, 0, .35), "Tail3": V(-1, 0, .2)},
                             arm(-1, (-.95, -.05, -.05), (-.9, -.05, .1), (1, -.2)), arm(1, (.9, .4, -.5), (1, .3, -.3))),
     rot={"Root": (8, 0, 30), "Torso": (0, 0, 10), "Chest": (0, 0, 12), "Head": (0, 0, -36)}, root=(0, .12, -.02))
pose("THRUST_HIT", d=merge(legs(1, 2.1, 1.1, ankle=.6, spread=.3), legs(-1, -1.3, .3, ankle=1.5, spread=.15),
                            {"Torso": V(.7, 0, 1), "Chest": V(.75, 0, 1), "Head": V(-.1, 0, 1), "Jaw": V(1, 0, -.8),
                             "Tail1": V(-1, 0, .3), "Tail2": V(-1, 0, .28), "Tail3": V(-1, 0, .2)},
                            arm(-1, (1, -.05, .05), (1, 0, .05), (1, -.1)), arm(1, (-.4, .7, -.7), (-.2, .5, -.8))),
     rot={"Root": (14, 0, -34), "Torso": (0, 0, -10), "Chest": (0, 0, -14), "Head": (0, 0, 40)},
     root=(0, -.26, -.02))
pose("THRUST_OVER", d=POSES["THRUST_HIT"]["d"],
     rot={"Root": (16, 0, -40), "Torso": (0, 0, -12), "Chest": (0, 0, -16), "Head": (0, 0, 46)}, root=(0, -.32, -.03))

pair("RISE_LOAD_L", d=merge(legs(1, 1.7, 2.3, spread=.35), legs(-1, 1.7, 2.3, spread=.35),
                             {"Torso": V(.7, 0, 1), "Chest": V(.75, 0, 1), "Head": V(-.4, 0, 1), "Jaw": V(1, 0, -.3),
                              "Tail1": V(-1, 0, .5), "Tail2": V(-1, 0, .4), "Tail3": V(-1, 0, .3)},
                             arm(1, (-.2, 1.0, -.95), (0, .8, -1), (1, -.4)), arm(-1, (.7, .3, -.8), (1, .2, -.4))),
       rot={"Root": (12, 0, 20), "Torso": (0, 0, 8), "Chest": (0, 0, 8)}, root=(.03, .07, -.03))
pair("RISE_HIT_L", d=merge(legs(1, 1.0, .5, spread=.25), legs(-1, -.4, .3, ankle=1.5, spread=.15),
                            {"Torso": V(-.05, 0, 1), "Chest": V(-.15, 0, 1), "Head": V(-.55, 0, 1), "Jaw": V(1, 0, -.85),
                             "Tail1": V(-1, 0, .1), "Tail2": V(-1, 0, .05), "Tail3": V(-1, 0, -.05)},
                            arm(1, (.6, -.65, 1.05), (.4, -.6, 1), (1, .3)), arm(-1, (-.4, .5, -.8), (-.2, .3, -1))),
       rot={"Root": (-8, 6, -30), "Torso": (0, 0, -10), "Chest": (0, 0, -12), "Head": (0, 0, 20)}, root=(-.03, -.12, .10))
pair("RISE_OVER_L", d=merge(POSES["RISE_HIT_L"]["d"], arm(1, (.2, -.45, 1.15), (0, -.4, 1.1))),
       rot={"Root": (-10, 6, -38), "Torso": (0, 0, -12), "Chest": (0, 0, -14), "Head": (0, 0, 26)}, root=(-.04, -.10, .16))

pose("LAUNCH_LOAD", d=merge(legs(1, 1.8, 2.4, spread=.3), legs(-1, 1.8, 2.4, spread=.3),
                             {"Torso": V(.75, 0, 1), "Chest": V(.8, 0, 1), "Head": V(-.45, 0, 1), "Jaw": V(1, 0, -.3),
                              "Tail1": V(-1, 0, .6), "Tail2": V(-1, 0, .45), "Tail3": V(-1, 0, .3)},
                             arm(-1, (-.3, -.1, -1), (-.2, -.1, -1), (1, -.5)), arm(1, (.6, .5, -.6), (.8, .3, -.4))),
     rot={"Root": (14, 0, -8)}, root=(0, .08, -.04))
pose("LAUNCH_HIT", d=merge(legs(1, .25, .15, ankle=.9, spread=.15), legs(-1, -.2, .2, ankle=1.3, spread=.15),
                            {"Torso": V(-.3, 0, 1), "Chest": V(-.4, 0, 1), "Head": V(-.7, 0, 1), "Jaw": V(1, 0, -.9),
                             "Tail1": V(-1, 0, -.1), "Tail2": V(-1, 0, -.2), "Tail3": V(-1, 0, -.3)},
                            arm(-1, (.7, -.15, 1.2), (.5, -.1, 1), (1, .5)), arm(1, (.3, .5, -.8), (.2, .3, -1))),
     rot={"Root": (-14, 0, 10), "Head": (0, 0, -8)}, root=(0, -.10, .20), air=True)
pose("LAUNCH_OVER", d=POSES["LAUNCH_HIT"]["d"], rot={"Root": (-20, 0, 14), "Head": (0, 0, -10)},
     root=(0, -.10, .26), air=True)

# charge (skill)
pose("CHARGE", d=merge(legs(1, 1.8, 2.5, spread=.35), legs(-1, 1.8, 2.5, spread=.35),
                        {"Torso": V(.85, 0, 1), "Chest": V(.9, 0, 1), "Head": V(-.45, 0, 1), "Jaw": V(1, 0, -.55),
                         "Tail1": V(-1, 0, .65), "Tail2": V(-1, 0, .5), "Tail3": V(-1, 0, .32)},
                        arm(1, (-.75, .55, -.45), (-.6, .4, -.8)), arm(-1, (-.75, .55, -.45), (-.6, .4, -.8))),
     rot={"Root": (16, 0, 0)}, root=(0, .12, -.03))
pose("CHARGE_TREMOR_A", d=POSES["CHARGE"]["d"], rot={"Root": (18, 2, 3), "Chest": (0, 0, 4), "Head": (0, 3, -6)},
     root=(.008, .13, -.032))
pose("CHARGE_TREMOR_B", d=POSES["CHARGE"]["d"], rot={"Root": (18, -2, -3), "Chest": (0, 0, -4), "Head": (0, -3, 6)},
     root=(-.008, .13, -.032))

# ------------------------------------------------------------------------ clip definitions
# (frame, pose, ease into this key, marker). ease: lin in out io snap back hold
CLIPS = {
    "idle": (True, 48, [(0, "STANCE", "io", "settle"), (7, "IN_L", "io", "inhale"), (14, "EX_L", "snap", "glance"),
                        (20, "SETTLE", "out", "exhale"), (27, "IN_R", "io", "inhale"), (34, "EX_R", "snap", "glance"),
                        (41, "SETTLE_R", "out", "exhale"), (48, "STANCE", "io", "settle")]),
    "walk": (True, 36, [(0, "WALK_L", "io", "left contact"), (9, "WALK_PASS_L", "io", "pass"),
                        (18, "WALK_R", "io", "right contact"), (27, "WALK_PASS_R", "io", "pass"),
                        (36, "WALK_L", "io", "left contact")]),
    "run": (True, 20, [(0, "RUN_L", "io", "left contact"), (5, "RUN_FLY_L", "out", "flight"),
                       (10, "RUN_R", "in", "right contact"), (15, "RUN_FLY_R", "out", "flight"),
                       (20, "RUN_L", "in", "left contact")]),
    "claw_left": (False, 12, [(0, "STANCE", "io", "ready"), (3, "WIND_L", "out", "wind"),
                              (5, "RAKE_MID_L", "snap", "swing"), (7, "RAKE_L", "snap", "impact"),
                              (9, "RAKE_OVER_L", "out", "follow"), (12, "STANCE", "io", "recover")]),
    "claw_right": (False, 12, [(0, "STANCE", "io", "ready"), (3, "WIND_R", "out", "wind"),
                               (5, "RAKE_MID_R", "snap", "swing"), (7, "RAKE_R", "snap", "impact"),
                               (9, "RAKE_OVER_R", "out", "follow"), (12, "STANCE", "io", "recover")]),
    "flurry_left": (False, 10, [(0, "STANCE", "io", "ready"), (2, "WIND_L", "out", "wind"),
                                (4, "RAKE_L", "snap", "impact"), (6, "WIND_R", "snap", "cross"),
                                (8, "RAKE_R", "snap", "follow"), (10, "STANCE", "out", "recover")]),
    "flurry_right": (False, 10, [(0, "STANCE", "io", "ready"), (2, "WIND_R", "out", "wind"),
                                 (4, "RAKE_R", "snap", "impact"), (6, "WIND_L", "snap", "cross"),
                                 (8, "RAKE_L", "snap", "follow"), (10, "STANCE", "out", "recover")]),
    "bite": (False, 14, [(0, "STANCE", "io", "ready"), (3, "HEAD_RECOIL", "out", "coil"),
                         (5, "BITE_OPEN", "snap", "lunge"), (6, "BITE_CLAMP", "snap", "bite"),
                         (8, "BITE_SHAKE_L", "snap", "shake"), (10, "BITE_SHAKE_R", "snap", "shake"),
                         (14, "STANCE", "out", "recover")]),
    "tail_slam": (False, 13, [(0, "STANCE", "io", "ready"), (4, "TAIL_COIL", "out", "coil"),
                              (7, "TAIL_SWEEP", "snap", "impact"), (9, "TAIL_FOLLOW", "out", "follow"),
                              (13, "STANCE", "io", "recover")]),
    "spike": (False, 12, [(0, "STANCE", "io", "ready"), (4, "SPIKE_WIND", "out", "wind"),
                          (7, "SPIKE_HIT", "snap", "impact"), (9, "SPIKE_OVER", "out", "follow"),
                          (12, "STANCE", "io", "recover")]),
    "guard": (False, 12, [(0, "STANCE", "io", "ready"), (3, "GUARD", "snap", "set"),
                          (6, "GUARD_HIT", "snap", "hold"), (8, "GUARD", "out", "hold"),
                          (12, "STANCE", "io", "recover")]),
    "dodge": (False, 12, [(0, "STANCE", "io", "ready"), (3, "SLIP_L", "snap", "slip"),
                          (7, "SLIP_R", "snap", "slip"), (12, "STANCE", "out", "recover")]),
    "launcher": (False, 12, [(0, "STANCE", "io", "ready"), (4, "LAUNCH_LOAD", "out", "load"),
                             (7, "LAUNCH_HIT", "snap", "launch"), (9, "LAUNCH_OVER", "out", "follow"),
                             (12, "STANCE", "io", "recover")]),
    "rise_left": (False, 11, [(0, "STANCE", "io", "ready"), (4, "RISE_LOAD_L", "out", "load"),
                              (6, "RISE_HIT_L", "snap", "rise"), (8, "RISE_OVER_L", "out", "follow"),
                              (11, "STANCE", "io", "recover")]),
    "rise_right": (False, 11, [(0, "STANCE", "io", "ready"), (4, "RISE_LOAD_R", "out", "load"),
                               (6, "RISE_HIT_R", "snap", "rise"), (8, "RISE_OVER_R", "out", "follow"),
                               (11, "STANCE", "io", "recover")]),
    "skill_charge": (False, 14, [(0, "STANCE", "io", "ready"), (5, "CHARGE", "out", "wind"),
                                 (7, "CHARGE_TREMOR_A", "lin", "hold"), (9, "CHARGE_TREMOR_B", "lin", "hold"),
                                 (11, "CHARGE_TREMOR_A", "lin", "hold"), (14, "STANCE", "snap", "recover")]),
    "skill_thrust": (False, 10, [(0, "STANCE", "io", "ready"), (3, "THRUST_LOAD", "out", "load"),
                                 (6, "THRUST_HIT", "snap", "impact"), (8, "THRUST_OVER", "out", "hold"),
                                 (10, "STANCE", "io", "recover")]),
    "leap_charge": (False, 13, [(0, "STANCE", "io", "ready"), (4, "CROUCH", "out", "load"),
                                (6, "STRETCH", "snap", "launch"), (9, "LAND", "in", "land"),
                                (13, "STANCE", "out", "recover")]),
    "leap": (False, 22, [(0, "STANCE", "io", "ready"), (6, "CROUCH", "out", "load"),
                         (9, "POUNCE_LAUNCH", "snap", "launch"), (13, "POUNCE_APEX", "out", "flight"),
                         (16, "POUNCE_STRIKE", "in", "strike"), (19, "LAND", "snap", "land"),
                         (22, "STANCE", "out", "recover")]),
    "jump": (False, 19, [(0, "STANCE", "io", "ready"), (5, "CROUCH", "out", "load"),
                         (8, "STRETCH", "snap", "launch"), (11, "TUCK", "out", "flight"),
                         (14, "REACH_LAND", "in", "descend"), (16, "LAND", "snap", "land"),
                         (19, "STANCE", "out", "recover")]),
    "duck_rake_bite": (False, 26, [(0, "STANCE", "io", "ready"), (4, "SLIP_L", "snap", "duck"),
                                   (8, "WIND_L", "out", "rake load"), (10, "RAKE_MID_L", "snap", "swing"),
                                   (12, "RAKE_L", "snap", "rake"), (14, "RAKE_OVER_L", "out", "follow"),
                                   (17, "HEAD_RECOIL", "snap", "drive"), (19, "BITE_OPEN", "snap", "lunge"),
                                   (20, "BITE_CLAMP", "snap", "bite"), (22, "BITE_SHAKE_L", "snap", "shake"),
                                   (26, "STANCE", "out", "recover")]),
    "hop_tail_claw": (False, 32, [(0, "STANCE", "io", "ready"), (4, "CROUCH", "out", "load"),
                                  (7, "STRETCH", "snap", "hop"), (10, "TUCK", "out", "flight"),
                                  (12, "LAND", "snap", "land"), (15, "TAIL_COIL", "out", "tail wind"),
                                  (18, "TAIL_SWEEP", "snap", "tail impact"), (20, "TAIL_FOLLOW", "out", "follow"),
                                  (23, "WIND_R", "io", "claw load"), (26, "RAKE_R", "snap", "claw impact"),
                                  (29, "RAKE_OVER_R", "out", "follow"), (32, "STANCE", "io", "recover")]),
}
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
        t["Hand_" + side] = (a * .62 + h * .38).normalized()
        t["Elbow_" + side] = (a * .80 + h * .20).normalized()
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
        report = ef.author_motion(rig, keyposes, analysis, name="%s.dyn" % (LA.PREFIX + clip))
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
