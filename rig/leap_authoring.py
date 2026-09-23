# Authoring tool for the charged leap's two clips: the wind-up and the launch.
#
# Run inside Blender with rig/EpicFight Animation Rig.blend open:
#   exec(open(r"...\rig\leap_authoring.py", encoding="utf-8").read())
# then call preview("leap_charge", 16) etc.
#
# Headless full export:
#   blender --background "EpicFight Animation Rig.blend" --python leap_authoring.py
#
# Unlike the combat styles, these two clips are shared by every style, so they live in
# animations/skill/ next to the skill casts and are played by LeapEngine through the same path.
# That also means they cannot assume any one style's stance: both start and end on NEUTRAL, a plain
# standing pose close to the rig's rest, so Epic Fight's blend into (and out of) whatever stance the
# player is actually in stays short.
#
# The pose conventions, the grounding helper and the baking/export code live in
# combat_style_authoring.py.
import os
import sys

try:
    _HERE = os.path.dirname(os.path.abspath(__file__))
except NameError:  # exec'd inside Blender's interactive console
    _HERE = r"C:\Users\hanjw\Downloads\gourmet2-main\gourmet2-main\rig"
if _HERE not in sys.path:
    sys.path.insert(0, _HERE)

from combat_style_authoring import *  # noqa: F401,F403
import combat_style_authoring as _fw

OUT_DIR = os.path.join(os.path.dirname(_HERE),
                       "src", "main", "resources", "assets", "gourmet2", "animmodels", "animations", "skill")
PREVIEW_DIR = _HERE

G = grounded
_fw.FLOOR_CLAMP = True

# A plain standing pose: arms down and slightly forward, weight even. The leap clips start and end here.
NEUTRAL = G({
    "root_pos": (0.0, 0.0, 0.745),
    "Torso": (-4.0, 0.0, 0.0),
    "Chest": (-3.0, 0.0, 0.0),
    "Head": (6.0, 0.0, 0.0),
    "Thigh_R": (-6.0, 0.0, -6.0),
    "Leg_R": (-12.0, 0.0, 0.0),
    "Thigh_L": (6.0, 0.0, 6.0),
    "Leg_L": (-12.0, 0.0, 0.0),
    "Shoulder_R": (0.0, 2.0, 0.0),
    "Arm_R": (14.0, 0.0, 8.0),
    "Hand_R": (34.0, 0.0, 0.0),
    "Shoulder_L": (0.0, -2.0, 0.0),
    "Arm_L": (14.0, 0.0, -8.0),
    "Hand_L": (34.0, 0.0, 0.0),
})

# Sprinter's crouch: sunk, folded forward, both arms swept back behind the hips. Held while the leap
# key is down, and the pose the launch starts from.
COIL = G({
    "root_pos": (0.0, 0.02, 0.60),
    "Torso": (-18.0, 0.0, 0.0),
    "Chest": (-12.0, 0.0, 0.0),
    "Head": (18.0, 0.0, 0.0),
    "Thigh_R": (-26.0, 0.0, -14.0),
    "Leg_R": (-62.0, 0.0, 0.0),
    "Thigh_L": (30.0, 0.0, 16.0),
    "Leg_L": (-58.0, 0.0, 0.0),
    "Shoulder_R": (0.0, -18.0, 0.0),
    "Arm_R": (-42.0, 0.0, 16.0),
    "Hand_R": (20.0, 0.0, 0.0),
    "Shoulder_L": (0.0, 18.0, 0.0),
    "Arm_L": (-42.0, 0.0, -16.0),
    "Hand_L": (20.0, 0.0, 0.0),
})

CLIPS = {
    # --- the wind-up, held for as long as the key is down -------------------------------------------------
    "leap_charge": (24, [
        (0, NEUTRAL, "smooth"),
        (8, COIL, "settle"),
        # A second, smaller dip so a held key still reads as loading rather than a frozen frame.
        (16, {**COIL, "root_pos": (0.0, 0.03, 0.575), "Torso": (-21.0, 0.0, 0.0),
              "Arm_R": (-48.0, 0.0, 16.0), "Arm_L": (-48.0, 0.0, -16.0)}, "smooth"),
        (24, COIL, "smooth"),
    ]),
    # --- the launch ---------------------------------------------------------------------------------------
    "leap": (16, [
        (0, COIL, "lin"),
        # Legs snap straight and the arms whip forward: the push off the surface.
        (3, {
            "root_pos": (0.0, 0.03, 0.86),
            "Torso": (-14.0, 0.0, 0.0), "Chest": (-9.0, 0.0, 0.0), "Head": (14.0, 0.0, 0.0),
            "Thigh_R": (-10.0, 0.0, -8.0), "Leg_R": (-6.0, 0.0, 0.0),
            "Thigh_L": (10.0, 0.0, 8.0), "Leg_L": (-6.0, 0.0, 0.0),
            "Shoulder_R": (0.0, 16.0, 0.0), "Arm_R": (40.0, 0.0, 30.0), "Hand_R": (20.0, 0.0, 0.0),
            "Shoulder_L": (0.0, -16.0, 0.0), "Arm_L": (40.0, 0.0, -30.0), "Hand_L": (20.0, 0.0, 0.0),
        }, "snap"),
        # Airborne: the body stretched out with the legs trailing, which is what the dash looks like.
        (8, {
            "root_pos": (0.0, 0.06, 0.98),
            "Torso": (-20.0, 0.0, 0.0), "Chest": (-12.0, 0.0, 0.0), "Head": (22.0, 0.0, 0.0),
            "Thigh_R": (-28.0, 0.0, -10.0), "Leg_R": (-26.0, 0.0, 0.0),
            "Thigh_L": (26.0, 0.0, 10.0), "Leg_L": (-14.0, 0.0, 0.0),
            "Shoulder_R": (0.0, 20.0, 0.0), "Arm_R": (78.0, 0.0, 26.0), "Hand_R": (10.0, 0.0, 0.0),
            "Shoulder_L": (0.0, -20.0, 0.0), "Arm_L": (78.0, 0.0, -26.0), "Hand_L": (10.0, 0.0, 0.0),
        }, "lin"),
        # Legs come under the body to take the landing.
        (12, {
            "root_pos": (0.0, 0.05, 0.78),
            "Torso": (-10.0, 0.0, 0.0), "Chest": (-6.0, 0.0, 0.0), "Head": (12.0, 0.0, 0.0),
            "Thigh_R": (-14.0, 0.0, -12.0), "Leg_R": (-30.0, 0.0, 0.0),
            "Thigh_L": (18.0, 0.0, 10.0), "Leg_L": (-26.0, 0.0, 0.0),
            "Arm_R": (36.0, 0.0, 24.0), "Hand_R": (40.0, 0.0, 0.0),
            "Arm_L": (36.0, 0.0, -24.0), "Hand_L": (40.0, 0.0, 0.0),
        }, "settle"),
        (16, NEUTRAL, "smooth"),
    ]),
}


def preview(clip, frame=0, tag=None):
    """Render one frame of a clip to <rig>/preview_<tag>.png and return the path."""
    return _fw.preview(CLIPS, clip, frame, tag, PREVIEW_DIR)


def export_all():
    return _fw.export_clips(CLIPS, OUT_DIR)


if __name__ == "__main__":
    print("EXPORTED=" + json.dumps(export_all()))
else:
    pass  # interactive: exec this file, then call preview("leap_charge", 16) etc.
