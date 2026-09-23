# Authoring tool for the charged leap's clips: the wind-up, the hold it settles into, and the launch.
#
# Run inside Blender with rig/EpicFight Animation Rig.blend open:
#   exec(open(r"...\rig\leap_authoring.py", encoding="utf-8").read())
# then call preview("leap_charge", 16) etc.
#
# Headless full export:
#   blender --background "EpicFight Animation Rig.blend" --python leap_authoring.py
#
# Unlike the combat styles, these clips are shared by every style, so they live in animations/skill/
# next to the skill casts and are played by LeapEngine through the same path.
# That also means they cannot assume any one style's stance. The wind-up starts and the launch ends on
# NEUTRAL, a plain standing pose close to the rig's rest, so Epic Fight's blend into (and out of)
# whatever stance the player is actually in stays short; the wind-up runs NEUTRAL -> COIL, the hold is a
# frozen COIL, and the launch runs COIL -> flat out -> NEUTRAL.
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

# The loaded stance: legs and waist folded down, the right arm dropped in front of the body with the
# elbow folded across to the left, and the left arm stretched out behind with its elbow bent a little
# downward. Held while the leap key is down, and the pose the launch starts from.
COIL = G({
    "root_pos": (0.0, 0.02, 0.60),
    # Legs and waist folded.
    "Torso": (-22.0, 0.0, 0.0),
    "Chest": (-14.0, 0.0, 0.0),
    "Head": (12.0, 0.0, 0.0),
    "Thigh_R": (-28.0, 0.0, -14.0),
    "Leg_R": (-64.0, 0.0, 0.0),
    "Thigh_L": (32.0, 0.0, 16.0),
    "Leg_L": (-60.0, 0.0, 0.0),
    # Right arm down in front, forearm folded across to the character's left (rz is "toward the left").
    "Shoulder_R": (0.0, 6.0, 0.0),
    "Arm_R": (22.0, 0.0, 26.0),
    "Hand_R": (30.0, 0.0, 48.0),
    # Left arm stretched behind the body, elbow tipped slightly further down.
    "Shoulder_L": (0.0, 12.0, 0.0),
    "Arm_L": (-52.0, 0.0, -10.0),
    "Hand_L": (-22.0, 0.0, 0.0),
})

# The push off the surface, and the two shapes a flight settles into: flat out along the direction of
# travel, and the same thing stood on end for a leap that climbs. Named so the hold clips below can be
# exactly the pose the launch arrives at, with no seam when the one takes over from the other.
PUSH_FLAT = {
    "root_pos": (0.0, 0.04, 0.84),
    "Root": (-26.0, 0.0, 0.0),
    "Torso": (-10.0, 0.0, 0.0), "Chest": (-6.0, 0.0, 0.0), "Head": (18.0, 0.0, 0.0),
    "Thigh_R": (-14.0, 0.0, -8.0), "Leg_R": (-22.0, 0.0, 0.0),
    "Thigh_L": (16.0, 0.0, 8.0), "Leg_L": (-20.0, 0.0, 0.0),
    "Shoulder_R": (0.0, 2.0, 0.0), "Arm_R": (18.0, 0.0, 20.0), "Hand_R": (44.0, 0.0, 40.0),
    "Shoulder_L": (0.0, 10.0, 0.0), "Arm_L": (-30.0, 0.0, -8.0), "Hand_L": (-6.0, 0.0, 0.0),
}

# Flat out along the direction of travel: Root pitched over so the whole body is one straight line, legs
# trailing in that line, and both arms dropped to the waist with the elbows folded.
FLIGHT = {
    "root_pos": (0.0, 0.06, 0.94),
    "Root": (-84.0, 0.0, 0.0),
    "Torso": (5.0, 0.0, 0.0), "Chest": (4.0, 0.0, 0.0), "Head": (36.0, 0.0, 0.0),
    "Thigh_R": (3.0, 0.0, -7.0), "Leg_R": (-6.0, 0.0, 0.0),
    "Thigh_L": (-3.0, 0.0, 7.0), "Leg_L": (-6.0, 0.0, 0.0),
    "Shoulder_R": (0.0, -4.0, 0.0), "Arm_R": (4.0, 0.0, 12.0), "Hand_R": (74.0, 0.0, 16.0),
    "Shoulder_L": (0.0, 4.0, 0.0), "Arm_L": (4.0, 0.0, -12.0), "Hand_L": (74.0, 0.0, -16.0),
}

PUSH_UP = {
    "root_pos": (0.0, 0.04, 0.88),
    "Torso": (-8.0, 0.0, 0.0), "Chest": (-5.0, 0.0, 0.0), "Head": (12.0, 0.0, 0.0),
    "Thigh_R": (-10.0, 0.0, -6.0), "Leg_R": (-18.0, 0.0, 0.0),
    "Thigh_L": (12.0, 0.0, 6.0), "Leg_L": (-16.0, 0.0, 0.0),
    "Shoulder_R": (0.0, 2.0, 0.0), "Arm_R": (18.0, 0.0, 20.0), "Hand_R": (44.0, 0.0, 40.0),
    "Shoulder_L": (0.0, 10.0, 0.0), "Arm_L": (-30.0, 0.0, -8.0), "Hand_L": (-6.0, 0.0, 0.0),
}

# Straight up: a vertical line, arms dropped to the waist, legs together and stretched out below.
CLIMB = {
    "root_pos": (0.0, 0.05, 1.02),
    "Torso": (2.0, 0.0, 0.0), "Chest": (1.0, 0.0, 0.0), "Head": (6.0, 0.0, 0.0),
    "Thigh_R": (1.0, 0.0, -3.0), "Leg_R": (-5.0, 0.0, 0.0),
    "Thigh_L": (-1.0, 0.0, 3.0), "Leg_L": (-5.0, 0.0, 0.0),
    "Shoulder_R": (0.0, -4.0, 0.0), "Arm_R": (4.0, 0.0, 12.0), "Hand_R": (74.0, 0.0, 16.0),
    "Shoulder_L": (0.0, 4.0, 0.0), "Arm_L": (4.0, 0.0, -12.0), "Hand_L": (74.0, 0.0, -16.0),
}

CLIPS = {
    # --- the wind-up ----------------------------------------------------------------------------------
    "leap_charge": (24, [
        # NEUTRAL -> COIL -> a deeper dip -> COIL: a held key reads as loading, not as a frozen frame.
        (0, NEUTRAL, "smooth"),
        (8, COIL, "settle"),
        # A second, smaller dip so a held key still reads as loading rather than a frozen frame.
        (16, {**COIL, "root_pos": (0.0, 0.03, 0.575), "Torso": (-21.0, 0.0, 0.0),
              "Arm_R": (-48.0, 0.0, 16.0), "Arm_L": (-48.0, 0.0, -16.0)}, "smooth"),
        (24, COIL, "smooth"),
    ]),
    # --- the holds ----------------------------------------------------------------------------------------
    # Frozen poses, exactly the frame the clip they follow ends its action on. A one-shot clip finishes and
    # Epic Fight hands the player back to the idle stance, and a long leap outlasts its launch clip, so the
    # engine re-plays these every few ticks for as long as the phase lasts; static clips keep that unseen.
    "leap_hold": (12, [
        (0, COIL, "lin"),
        (12, COIL, "lin"),
    ]),
    "leap_fly": (12, [
        (0, FLIGHT, "lin"),
        (12, FLIGHT, "lin"),
    ]),
    "leap_up_fly": (12, [
        (0, CLIMB, "lin"),
        (12, CLIMB, "lin"),
    ]),
    # --- the launches -------------------------------------------------------------------------------------
    # Legs snap straight and the arms whip forward: the push off the surface, then flat out.
    "leap": (16, [
        (0, COIL, "lin"),
        (3, PUSH_FLAT, "snap"),
        (6, FLIGHT, "lin"),
        (12, FLIGHT, "lin"),
        # Feet come back under the body to take the landing.
        (16, NEUTRAL, "smooth"),
    ]),
    # --- the launch, climbing ---------------------------------------------------------------------------
    # A steep leap gets this one instead of the flat pose above: laid out along the direction of travel
    # means upright when the travel is upward, or the body would be lying on its side in the air.
    "leap_up": (16, [
        (0, COIL, "lin"),
        (3, PUSH_UP, "snap"),
        (6, CLIMB, "lin"),
        (12, CLIMB, "lin"),
        # Feet down first for the landing.
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
