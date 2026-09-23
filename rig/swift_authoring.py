# Authoring tool for the "swift" combat style's Epic Fight animation clips.
#
# Run inside Blender with rig/EpicFight Animation Rig.blend open:
#   exec(open(r"...\rig\swift_authoring.py", encoding="utf-8").read())
# then call preview("basic1", 7) etc.
#
# Headless full export:
#   blender --background "EpicFight Animation Rig.blend" --python swift_authoring.py
#
# The pose conventions and the baking/export code live in combat_style_authoring.py.
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
                       "src", "main", "resources", "assets", "gourmet2", "animmodels", "animations", "combat_swift")
PREVIEW_DIR = _HERE


# ---------------------------------------------------------------- poses

STANCE = {
    "root_pos": (0.0, 0.0, 0.72),
    "Torso": (-8.0, -6.0, 0.0),
    "Chest": (-6.0, 4.0, 0.0),
    "Head": (8.0, 4.0, 0.0),
    "Thigh_R": (-12.0, 0.0, -10.0),
    "Leg_R": (-18.0, 0.0, 0.0),
    "Thigh_L": (14.0, 0.0, 10.0),
    "Leg_L": (-12.0, 0.0, 0.0),
    "Shoulder_R": (0.0, -12.0, 0.0),
    "Arm_R": (70.0, 0.0, 28.0),
    "Hand_R": (35.0, 0.0, 0.0),
    "Shoulder_L": (0.0, 12.0, 0.0),
    "Arm_L": (58.0, 0.0, 34.0),
    "Hand_L": (35.0, 0.0, 0.0),
}

GUARD_POSE = {
    "root_pos": (0.0, 0.0, 0.70),
    "Torso": (-12.0, -8.0, 0.0),
    "Chest": (-8.0, 6.0, 0.0),
    "Head": (14.0, 4.0, 0.0),
    "Thigh_R": (-14.0, 0.0, -12.0),
    "Leg_R": (-24.0, 0.0, 0.0),
    "Thigh_L": (18.0, 0.0, 12.0),
    "Leg_L": (-18.0, 0.0, 0.0),
    "Shoulder_R": (0.0, -18.0, 0.0),
    "Arm_R": (95.0, 0.0, 55.0),
    "Hand_R": (55.0, 0.0, 0.0),
    "Shoulder_L": (0.0, 18.0, 0.0),
    "Arm_L": (88.0, 0.0, 60.0),
    "Hand_L": (55.0, 0.0, 0.0),
}

CLIPS = {
    # --- one-shot actions -------------------------------------------------
    "basic1": (17, [  # right knife-hand chop, quick step-in
        (0, STANCE, "lin"),
        (3, {**STANCE, "root_pos": (0.0, 0.0, 0.705), "Torso": (-4.0, -22.0, 0.0), "Chest": (-2.0, -14.0, 0.0),
             "Arm_R": (98.0, 0.0, 44.0), "Hand_R": (55.0, 0.0, 0.0), "Shoulder_R": (0.0, -20.0, 0.0)}, "snap"),
        (7, {**STANCE, "root_pos": (0.0, 0.06, 0.715), "Torso": (-14.0, 16.0, 0.0), "Chest": (-10.0, 10.0, 0.0),
             "Thigh_L": (22.0, 0.0, 10.0), "Leg_L": (-16.0, 0.0, 0.0),
             "Arm_R": (118.0, 0.0, 48.0), "Hand_R": (10.0, 0.0, 0.0), "Shoulder_R": (0.0, 10.0, 0.0),
             "Arm_L": (40.0, 0.0, 18.0), "Hand_L": (35.0, 0.0, 0.0)}, "snap"),
        (12, {**STANCE, "root_pos": (0.0, 0.07, 0.715), "Torso": (-12.0, 20.0, 0.0),
              "Arm_R": (124.0, 0.0, 50.0), "Hand_R": (8.0, 0.0, 0.0), "Arm_L": (48.0, 0.0, 20.0)}, "settle"),
        (17, STANCE, "smooth"),
    ]),
    "basic2": (17, [  # left palm strike, deeper lunge
        (0, STANCE, "lin"),
        (3, {**STANCE, "root_pos": (0.0, -0.01, 0.70), "Torso": (-6.0, 18.0, 0.0), "Chest": (-4.0, 12.0, 0.0),
             "Arm_L": (30.0, 0.0, -18.0), "Hand_L": (55.0, 0.0, 0.0), "Shoulder_L": (0.0, 22.0, 0.0)}, "snap"),
        (7, {**STANCE, "root_pos": (0.0, 0.08, 0.71), "Torso": (-16.0, -18.0, 0.0), "Chest": (-10.0, -12.0, 0.0),
             "Thigh_L": (26.0, 0.0, 10.0), "Leg_L": (-20.0, 0.0, 0.0), "Thigh_R": (-22.0, 0.0, -12.0), "Leg_R": (-10.0, 0.0, 0.0),
             "Arm_L": (96.0, 0.0, -10.0), "Hand_L": (0.0, 0.0, 0.0), "Shoulder_L": (0.0, -8.0, 0.0),
             "Arm_R": (55.0, 0.0, 30.0), "Hand_R": (40.0, 0.0, 0.0)}, "snap"),
        (12, {**STANCE, "root_pos": (0.0, 0.08, 0.71), "Torso": (-14.0, -20.0, 0.0),
              "Arm_L": (100.0, 0.0, -8.0), "Hand_L": (0.0, 0.0, 0.0), "Arm_R": (60.0, 0.0, 32.0)}, "settle"),
        (17, STANCE, "smooth"),
    ]),
    "basic3": (20, [  # finisher: left chop then heavy right chop (two impacts)
        (0, STANCE, "lin"),
        (2, {**STANCE, "root_pos": (0.0, 0.0, 0.695), "Torso": (-8.0, 22.0, 0.0), "Chest": (-6.0, 14.0, 0.0),
             "Arm_L": (25.0, 0.0, -20.0), "Hand_L": (50.0, 0.0, 0.0), "Arm_R": (85.0, 0.0, 30.0)}, "snap"),
        (6, {**STANCE, "root_pos": (0.0, 0.05, 0.71), "Torso": (-14.0, -16.0, 0.0), "Chest": (-10.0, -10.0, 0.0),
             "Arm_L": (98.0, 0.0, -10.0), "Hand_L": (0.0, 0.0, 0.0), "Arm_R": (60.0, 0.0, 30.0), "Hand_R": (40.0, 0.0, 0.0)}, "snap"),
        (9, {**STANCE, "root_pos": (0.0, 0.04, 0.70), "Torso": (-8.0, 8.0, 0.0),
             "Arm_L": (70.0, 0.0, 12.0), "Arm_R": (70.0, 0.0, 36.0), "Hand_R": (45.0, 0.0, 0.0)}, "lin"),
        (13, {**STANCE, "root_pos": (0.0, 0.10, 0.715), "Torso": (-18.0, -26.0, 0.0), "Chest": (-12.0, -16.0, 0.0),
              "Thigh_L": (28.0, 0.0, 10.0), "Leg_L": (-22.0, 0.0, 0.0),
              "Arm_L": (40.0, 0.0, 16.0), "Hand_L": (35.0, 0.0, 0.0),
              "Arm_R": (122.0, 0.0, 52.0), "Hand_R": (5.0, 0.0, 0.0), "Shoulder_R": (0.0, 12.0, 0.0)}, "snap"),
        (17, {**STANCE, "root_pos": (0.0, 0.10, 0.715), "Torso": (-14.0, -28.0, 0.0),
              "Arm_R": (128.0, 0.0, 54.0), "Hand_R": (5.0, 0.0, 0.0), "Arm_L": (44.0, 0.0, 18.0)}, "settle"),
        (20, STANCE, "smooth"),
    ]),
    "heavy": (24, [  # whipping spinning back-fist with a driving lunge
        (0, STANCE, "lin"),
        (5, {**STANCE, "root_pos": (0.0, -0.02, 0.70), "Torso": (-10.0, -30.0, 0.0), "Chest": (-6.0, -20.0, 0.0),
             "Head": (8.0, 22.0, 0.0),
             "Arm_R": (60.0, 0.0, 45.0), "Hand_R": (50.0, 0.0, 0.0),
             "Arm_L": (45.0, 0.0, 22.0)}, "snap"),
        (10, {**STANCE, "root_pos": (0.0, 0.04, 0.71), "Torso": (-12.0, 28.0, 0.0), "Chest": (-8.0, 18.0, 0.0),
              "Head": (8.0, -18.0, 0.0),
              "Arm_R": (88.0, 0.0, -68.0), "Hand_R": (0.0, 0.0, 0.0), "Shoulder_R": (0.0, -30.0, 0.0),
              "Arm_L": (60.0, 0.0, 40.0)}, "snap"),
        (14, {**STANCE, "root_pos": (0.0, 0.12, 0.715), "Torso": (-16.0, 34.0, 0.0), "Chest": (-10.0, 24.0, 0.0),
              "Thigh_L": (30.0, 0.0, 10.0), "Leg_L": (-24.0, 0.0, 0.0),
              "Arm_R": (100.0, 0.0, -55.0), "Hand_R": (-10.0, 0.0, 0.0),
              "Arm_L": (50.0, 0.0, 30.0)}, "settle"),
        (19, {**STANCE, "root_pos": (0.0, 0.10, 0.71), "Torso": (-12.0, 26.0, 0.0),
              "Arm_R": (85.0, 0.0, -35.0), "Arm_L": (52.0, 0.0, 32.0)}, "smooth"),
        (24, STANCE, "smooth"),
    ]),
    "dodge": (13, [  # quick slip back with a low weave
        (0, STANCE, "lin"),
        (4, {**STANCE, "root_pos": (0.0, -0.10, 0.66), "Torso": (14.0, 6.0, 0.0), "Chest": (10.0, 4.0, 0.0),
             "Head": (-8.0, 0.0, 0.0),
             "Arm_R": (55.0, 0.0, 45.0), "Arm_L": (50.0, 0.0, 48.0), "Hand_R": (55.0, 0.0, 0.0), "Hand_L": (55.0, 0.0, 0.0),
             "Thigh_R": (-6.0, 0.0, -14.0), "Leg_R": (-28.0, 0.0, 0.0)}, "snap"),
        (8, {**STANCE, "root_pos": (0.0, -0.14, 0.63), "Torso": (18.0, 8.0, 0.0), "Chest": (12.0, 6.0, 0.0),
             "Head": (-12.0, 0.0, 0.0),
             "Arm_R": (60.0, 0.0, 50.0), "Arm_L": (55.0, 0.0, 52.0), "Hand_R": (60.0, 0.0, 0.0), "Hand_L": (60.0, 0.0, 0.0),
             "Thigh_R": (-2.0, 0.0, -16.0), "Leg_R": (-34.0, 0.0, 0.0), "Thigh_L": (20.0, 0.0, 12.0), "Leg_L": (-24.0, 0.0, 0.0)}, "settle"),
        (13, STANCE, "smooth"),
    ]),
    "launcher": (21, [  # rising palm strike: sink, then explode upward
        (0, STANCE, "lin"),
        (5, {**STANCE, "root_pos": (0.0, 0.02, 0.60), "Torso": (6.0, -10.0, 0.0),
             "Thigh_R": (-18.0, 0.0, -16.0), "Leg_R": (-48.0, 0.0, 0.0),
             "Thigh_L": (30.0, 0.0, 16.0), "Leg_L": (-44.0, 0.0, 0.0),
             "Arm_R": (30.0, 0.0, 18.0), "Hand_R": (20.0, 0.0, 0.0), "Arm_L": (28.0, 0.0, 20.0), "Hand_L": (20.0, 0.0, 0.0)}, "snap"),
        (9, {**STANCE, "root_pos": (0.0, 0.03, 0.80), "Torso": (-16.0, -4.0, 0.0),
             "Thigh_R": (-6.0, 0.0, -8.0), "Leg_R": (-12.0, 0.0, 0.0),
             "Thigh_L": (-16.0, 0.0, 8.0), "Leg_L": (-4.0, 0.0, 0.0),
             "Arm_R": (168.0, 0.0, 6.0), "Hand_R": (0.0, 0.0, 0.0), "Shoulder_R": (0.0, 6.0, 0.0),
             "Arm_L": (35.0, 0.0, 24.0)}, "snap"),
        (13, {**STANCE, "root_pos": (0.0, 0.03, 0.83), "Torso": (-20.0, 0.0, 0.0),
              "Thigh_R": (-4.0, 0.0, -6.0), "Leg_R": (-8.0, 0.0, 0.0),
              "Thigh_L": (-22.0, 0.0, 8.0), "Leg_L": (-2.0, 0.0, 0.0),
              "Arm_R": (175.0, 0.0, 4.0), "Hand_R": (0.0, 0.0, 0.0),
              "Arm_L": (30.0, 0.0, 26.0)}, "settle"),
        (21, STANCE, "smooth"),
    ]),
    "spike": (18, [  # airborne axe-hand + heel slam straight down
        (0, {**STANCE, "root_pos": (0.0, 0.0, 0.78), "Torso": (-14.0, 0.0, 0.0),
             "Thigh_R": (95.0, 0.0, -6.0), "Leg_R": (-30.0, 0.0, 0.0),
             "Thigh_L": (-12.0, 0.0, 10.0), "Leg_L": (-10.0, 0.0, 0.0),
             "Arm_R": (150.0, 0.0, 20.0), "Arm_L": (140.0, 0.0, -20.0), "Hand_R": (0.0, 0.0, 0.0), "Hand_L": (0.0, 0.0, 0.0)}, "snap"),
        (7, {**STANCE, "root_pos": (0.0, 0.12, 0.60), "Torso": (22.0, -6.0, 0.0), "Chest": (14.0, 0.0, 0.0),
             "Head": (-16.0, 0.0, 0.0),
             "Thigh_R": (18.0, 0.0, -8.0), "Leg_R": (-14.0, 0.0, 0.0),
             "Thigh_L": (24.0, 0.0, 14.0), "Leg_L": (-40.0, 0.0, 0.0),
             "Arm_R": (55.0, 0.0, 30.0), "Arm_L": (50.0, 0.0, -28.0), "Hand_R": (30.0, 0.0, 0.0), "Hand_L": (30.0, 0.0, 0.0)}, "snap"),
        (11, {**STANCE, "root_pos": (0.0, 0.10, 0.56), "Torso": (26.0, -4.0, 0.0),
              "Thigh_R": (26.0, 0.0, -12.0), "Leg_R": (-52.0, 0.0, 0.0),
              "Thigh_L": (32.0, 0.0, 16.0), "Leg_L": (-52.0, 0.0, 0.0),
              "Arm_R": (40.0, 0.0, 35.0), "Arm_L": (38.0, 0.0, -34.0)}, "settle"),
        (18, STANCE, "smooth"),
    ]),
    # --- stances (looping) ------------------------------------------------
    "guard": (14, [  # static tight high guard
        (0, GUARD_POSE, "lin"),
        (14, GUARD_POSE, "lin"),
    ]),
    "guard_release": (6, [
        (0, GUARD_POSE, "smooth"),
        (6, STANCE, "lin"),
    ]),
    "idle_stance": (24, [
        (0, STANCE, "smooth"),
        (12, {**STANCE, "root_pos": (0.0, 0.0, 0.705), "Arm_R": (74.0, 0.0, 26.0), "Arm_L": (62.0, 0.0, 32.0),
              "Torso": (-9.0, -4.0, 0.0)}, "smooth"),
        (24, STANCE, "smooth"),
    ]),
    "walk_stance": (24, [
        (0, STANCE, "smooth"),
        (6, {**STANCE, "root_pos": (0.0, 0.0, 0.71), "Thigh_L": (30.0, 0.0, 10.0), "Leg_L": (-8.0, 0.0, 0.0),
             "Thigh_R": (-18.0, 0.0, -10.0), "Leg_R": (-30.0, 0.0, 0.0),
             "Arm_R": (64.0, 0.0, 30.0), "Arm_L": (52.0, 0.0, 32.0)}, "smooth"),
        (12, {**STANCE, "root_pos": (0.0, 0.0, 0.72)}, "smooth"),
        (18, {**STANCE, "root_pos": (0.0, 0.0, 0.71), "Thigh_R": (-2.0, 0.0, -10.0), "Leg_R": (-14.0, 0.0, 0.0),
              "Thigh_L": (2.0, 0.0, 10.0), "Leg_L": (-26.0, 0.0, 0.0),
              "Arm_R": (76.0, 0.0, 26.0), "Arm_L": (64.0, 0.0, 36.0)}, "smooth"),
        (24, STANCE, "smooth"),
    ]),
    "run_stance": (16, [
        (0, {**STANCE, "root_pos": (0.0, 0.0, 0.68), "Torso": (-22.0, -6.0, 0.0), "Chest": (-14.0, 4.0, 0.0),
             "Thigh_L": (34.0, 0.0, 8.0), "Leg_L": (-30.0, 0.0, 0.0), "Thigh_R": (-28.0, 0.0, -8.0), "Leg_R": (-46.0, 0.0, 0.0),
             "Arm_R": (50.0, 0.0, 30.0), "Arm_L": (85.0, 0.0, 26.0)}, "smooth"),
        (8, {**STANCE, "root_pos": (0.0, 0.0, 0.72), "Torso": (-22.0, -6.0, 0.0), "Chest": (-14.0, 4.0, 0.0),
             "Thigh_L": (-24.0, 0.0, 8.0), "Leg_L": (-18.0, 0.0, 0.0), "Thigh_R": (18.0, 0.0, -8.0), "Leg_R": (-14.0, 0.0, 0.0),
             "Arm_R": (88.0, 0.0, 28.0), "Arm_L": (45.0, 0.0, 28.0)}, "smooth"),
        (16, {**STANCE, "root_pos": (0.0, 0.0, 0.68), "Torso": (-22.0, -6.0, 0.0), "Chest": (-14.0, 4.0, 0.0),
              "Thigh_L": (34.0, 0.0, 8.0), "Leg_L": (-30.0, 0.0, 0.0), "Thigh_R": (-28.0, 0.0, -8.0), "Leg_R": (-46.0, 0.0, 0.0),
              "Arm_R": (50.0, 0.0, 30.0), "Arm_L": (85.0, 0.0, 26.0)}, "smooth"),
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
    pass  # interactive: exec this file, then call preview("basic1", 7) etc.
