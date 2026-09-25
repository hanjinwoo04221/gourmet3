# Authoring tool for the nail-gun charge clip.
#
# Run inside Blender with rig/EpicFight Animation Rig.blend open:
#   exec(open(r"...\rig\nail_authoring.py", encoding="utf-8").read())
# then call preview("nail_gun_charge", 20) etc.
#
# Headless full export:
#   blender --background "EpicFight Animation Rig.blend" --python nail_authoring.py
#
# The nail gun is a CHARGE skill now (see SkillEngine), so the engine plays "<skill>_charge" the moment
# the key goes down and the barrage itself only fires on key-up. Without this clip the wind-up had
# nothing to show and every press logged "Skill animation 'nail_gun_charge' is not registered".
#
# Like the leap's clips these are shared by every combat style, so the wind-up starts on NEUTRAL — a
# plain standing pose close to the rig's rest — and settles into a volley loaded at the hip; Epic
# Fight then blends out of it into whatever stance the player is actually in, so the shorter the blend
# the better. 56 frames matches the other charge clips (nail_punch_charge, leg_knife_charge,
# flying_fork_charge, flying_knife_charge).
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

# A plain standing pose: arms down and slightly forward, weight even. Matches the leap clips' NEUTRAL so
# the wind-up blends out of (and the player's stance blends into) the same place whatever the style.
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

# The volley loaded: the body sinks into a forward crouch and the right arm — the muzzle the cast
# effect rides (SkillFx.NAIL_GUN_CAST is anchored to the right hand) — is cocked back with the elbow
# folded, its fist beside the hip, while the left forearm braces forward across the chest. The head
# counter-turns so the aim stays down the barrel.
#
# The angles themselves are not the readable part: the arm and hand bones' rest frames are rotated, so
# 'rx' is not simply 'swing back' here. These were picked by measuring where the elbow and both fists
# actually land (see the joint targets in the session notes): elbow ~(0.27, -0.24, 1.14), right fist
# ~(0.33, -0.04, 0.96) — behind, at hip height, tucked to the side — and left fist ~(0.01, 0.42, 0.96),
# out in front of the body's centre line.
LOAD = G({
    "root_pos": (0.0, -0.01, 0.70),
    # Sunk and turned: back leg loaded under the body, front foot light, torso pitched into the aim.
    "Torso": (-16.0, -9.0, -3.0),
    "Chest": (-10.0, -5.0, -2.0),
    "Head": (7.0, 10.0, 2.0),
    "Thigh_R": (-10.0, 0.0, -7.0),
    "Leg_R": (-32.0, 0.0, 0.0),
    "Thigh_L": (14.0, 0.0, 9.0),
    "Leg_L": (-20.0, 0.0, 0.0),
    # Right arm cocked: shoulder rolled back, forearm folded so the fist comes to the hip.
    "Shoulder_R": (0.0, -14.0, 0.0),
    "Arm_R": (-16.0, 0.0, 10.0),
    "Hand_R": (100.0, 0.0, 0.0),
    # Left forearm braced forward and across, palm turned in.
    "Shoulder_L": (0.0, 12.0, 0.0),
    "Arm_L": (38.0, 0.0, -18.0),
    "Hand_L": (44.0, 0.0, -26.0),
})

# The loading pulse: the same pose sunk a touch deeper with the volley wound a little further, so a
# held key reads as loading rather than as a frozen frame (the leap's wind-up takes the same two steps).
LOAD_DEEP = {**LOAD,
             "root_pos": (0.0, -0.015, 0.668),
             "Torso": (-19.0, -11.0, -3.0),
             "Chest": (-12.0, -6.0, -2.0),
             "Leg_R": (-36.0, 0.0, 0.0),
             "Shoulder_R": (0.0, -17.0, 0.0),
             "Arm_R": (-22.0, 0.0, 12.0),
             "Hand_R": (104.0, 0.0, 0.0),
             "Arm_L": (42.0, 0.0, -19.0)}

CLIPS = {
    # NEUTRAL -> LOAD -> a deeper wind -> LOAD: the volley cocked and waiting on the key.
    "nail_gun_charge": (56, [
        (0, NEUTRAL, "smooth"),
        (10, LOAD, "settle"),
        (34, LOAD_DEEP, "smooth"),
        (56, LOAD, "smooth"),
    ]),
    # The held pose, frozen on exactly the frame the wind-up above settles on. The magazine — and with
    # it the charge — grows with the cell level, so a long charge outlasts the wind-up; the engine
    # re-plays this every few ticks for as long as the key is down (SkillEngine.CHARGE_HOLD_*), and a
    # static clip keeps that replay unseen. Same shape as the leap's coil (leap_hold).
    "nail_gun_hold": (12, [
        (0, LOAD, "lin"),
        (12, LOAD, "lin"),
    ]),
}


def preview(clip, frame=0, tag=None):
    """Render one frame of a clip to <rig>/preview_<tag>.png and return the path."""
    return _fw.preview(CLIPS, clip, frame, tag, PREVIEW_DIR)


def export_all():
    return _fw.export_clips(CLIPS, OUT_DIR)


if __name__ == "__main__":
    previews = [preview("nail_gun_charge", 34, "nail_gun_charge"),
                preview("nail_gun_hold", 6, "nail_gun_hold")]
    print("PREVIEWS=" + json.dumps(previews))
    print("EXPORTED=" + json.dumps(export_all()))
else:
    pass  # interactive: exec this file, then call preview("nail_gun_charge", 20) etc.
