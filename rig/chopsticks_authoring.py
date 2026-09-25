# Authoring tool for Ichiryu's Chopsticks clips: "chopsticks" (summon), "chopsticks_pick" (Pick) and
# "chopsticks_transfer" (Transfer Pick).
#
# Run inside Blender with rig/EpicFight Animation Rig.blend open:
#   exec(open(r"...\rig\chopsticks_authoring.py", encoding="utf-8").read())
# then call preview("chopsticks", 12) etc., and export_all() to write the JSON into the mod's resources.
#
# Headless full export:
#   blender --background "EpicFight Animation Rig.blend" --python chopsticks_authoring.py
#
# Like the other skill clips these are shared by every combat style, so each starts on NEUTRAL (a plain
# standing pose) and ends close to it. The pose conventions live in combat_style_authoring.py.
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

# The right hand thrown up over the head, pinched round an invisible pair of sticks; the left arm hangs
# relaxed (the technique is one-handed) and the body arches back to look up at them.
RAISED = G({
    "root_pos": (0.0, -0.01, 0.74),
    "Torso": (8.0, 0.0, 0.0),
    "Chest": (10.0, 0.0, 0.0),
    "Head": (-4.0, 0.0, 0.0),
    "Thigh_R": (-4.0, 0.0, -7.0),
    "Leg_R": (-8.0, 0.0, 0.0),
    "Thigh_L": (4.0, 0.0, 7.0),
    "Leg_L": (-8.0, 0.0, 0.0),
    "Shoulder_R": (0.0, 4.0, 0.0),
    "Arm_R": (158.0, 0.0, 6.0),
    "Hand_R": (12.0, 0.0, 0.0),
    "Shoulder_L": (0.0, -2.0, 0.0),
    "Arm_L": (14.0, 0.0, -8.0),
    "Hand_L": (34.0, 0.0, 0.0),
})

# The ready stance: the right arm held out level in front at the height of the hovering sticks.
READY = G({
    "root_pos": (0.0, 0.0, 0.73),
    "Torso": (-6.0, 0.0, 0.0),
    "Chest": (-4.0, 0.0, 0.0),
    "Head": (4.0, 0.0, 0.0),
    "Thigh_R": (-8.0, 0.0, -7.0),
    "Leg_R": (-18.0, 0.0, 0.0),
    "Thigh_L": (8.0, 0.0, 7.0),
    "Leg_L": (-18.0, 0.0, 0.0),
    "Shoulder_R": (0.0, 6.0, 0.0),
    "Arm_R": (88.0, 0.0, 14.0),
    "Hand_R": (10.0, 0.0, 0.0),
    "Shoulder_L": (0.0, -2.0, 0.0),
    "Arm_L": (14.0, 0.0, -8.0),
    "Hand_L": (34.0, 0.0, 0.0),
})

# Pick: the right hand drawn back up by the head to wind up the pinch...
PICK_WIND = G({
    "root_pos": (0.0, -0.02, 0.72),
    "Torso": (6.0, 0.0, 0.0),
    "Chest": (6.0, 0.0, 0.0),
    "Head": (0.0, 0.0, 0.0),
    "Thigh_R": (-10.0, 0.0, -7.0),
    "Leg_R": (-24.0, 0.0, 0.0),
    "Thigh_L": (10.0, 0.0, 7.0),
    "Leg_L": (-16.0, 0.0, 0.0),
    "Shoulder_R": (0.0, 4.0, 0.0),
    "Arm_R": (128.0, 0.0, 12.0),
    "Hand_R": (16.0, 0.0, 0.0),
    "Shoulder_L": (0.0, -2.0, 0.0),
    "Arm_L": (14.0, 0.0, -8.0),
    "Hand_L": (34.0, 0.0, 0.0),
})

# ...then thrown down and forward with the whole body behind it, the hand closing on the target.
PICK_STAB = G({
    "root_pos": (0.0, 0.05, 0.66),
    "Torso": (-24.0, 0.0, 0.0),
    "Chest": (-16.0, 0.0, 0.0),
    "Head": (12.0, 0.0, 0.0),
    "Thigh_R": (-20.0, 0.0, -7.0),
    "Leg_R": (-42.0, 0.0, 0.0),
    "Thigh_L": (22.0, 0.0, 7.0),
    "Leg_L": (-24.0, 0.0, 0.0),
    "Shoulder_R": (0.0, 6.0, 0.0),
    "Arm_R": (104.0, 0.0, 6.0),
    "Hand_R": (26.0, 0.0, 0.0),
    "Shoulder_L": (0.0, -2.0, 0.0),
    "Arm_L": (14.0, 0.0, -8.0),
    "Hand_L": (34.0, 0.0, 0.0),
})

# Transfer Pick: the right arm high and swung to one side, then all the way across to the other as the sticks carry
# everything gripped to where the caster looks, with the body twisting after them.
SWEEP_A = G({
    "root_pos": (0.0, -0.01, 0.73),
    "Torso": (0.0, 18.0, 4.0),
    "Chest": (2.0, 14.0, 3.0),
    "Head": (0.0, -12.0, 0.0),
    "Thigh_R": (-8.0, 0.0, -9.0),
    "Leg_R": (-16.0, 0.0, 0.0),
    "Thigh_L": (8.0, 0.0, 9.0),
    "Leg_L": (-16.0, 0.0, 0.0),
    "Shoulder_R": (0.0, 4.0, 0.0),
    "Arm_R": (132.0, 0.0, 40.0),
    "Hand_R": (12.0, 0.0, 0.0),
    "Shoulder_L": (0.0, -2.0, 0.0),
    "Arm_L": (14.0, 0.0, -8.0),
    "Hand_L": (34.0, 0.0, 0.0),
})
SWEEP_B = G({
    "root_pos": (0.0, 0.01, 0.72),
    "Torso": (-6.0, -20.0, -4.0),
    "Chest": (-4.0, -16.0, -3.0),
    "Head": (2.0, 14.0, 0.0),
    "Thigh_R": (-12.0, 0.0, -9.0),
    "Leg_R": (-26.0, 0.0, 0.0),
    "Thigh_L": (10.0, 0.0, 9.0),
    "Leg_L": (-20.0, 0.0, 0.0),
    "Shoulder_R": (0.0, 4.0, 0.0),
    "Arm_R": (110.0, 0.0, -14.0),
    "Hand_R": (16.0, 0.0, 0.0),
    "Shoulder_L": (0.0, -2.0, 0.0),
    "Arm_L": (14.0, 0.0, -8.0),
    "Hand_L": (34.0, 0.0, 0.0),
})

def right_arm(arm, hand, torso=(-4.0, 0.0, 0.0), chest=(-3.0, 0.0, 0.0), root=(0.0, 0.0, 0.745), legs=None, head=None):
    """NEUTRAL with only the torso and the right arm changed: the fist techniques are one-handed."""
    spec = dict(NEUTRAL)
    spec.pop("root_pos")
    spec.update({"Torso": torso, "Chest": chest, "Arm_R": arm, "Hand_R": hand})
    if legs:
        spec.update(legs)
    if head:
        spec["Head"] = head
    spec["root_pos"] = root
    return G(spec)


CROUCH = {"Thigh_R": (-12.0, 0.0, -7.0), "Leg_R": (-28.0, 0.0, 0.0),
          "Thigh_L": (12.0, 0.0, 7.0), "Leg_L": (-18.0, 0.0, 0.0)}

# Fist Chopstick: the right hand closes on the stick and is held up in front of the chest.
FIST_HELD = right_arm((72.0, 0.0, 10.0), (96.0, 0.0, 0.0), root=(0.0, 0.0, 0.735))
# The throw: cocked back behind the head, then snapped forward level with the body behind it.
THROW_WIND = right_arm((198.0, 0.0, 14.0), (10.0, 0.0, 0.0), torso=(10.0, 8.0, 0.0), chest=(10.0, 8.0, 0.0),
                       legs=CROUCH, root=(0.0, -0.02, 0.72))
THROW_OUT = right_arm((86.0, 0.0, 4.0), (24.0, 0.0, 0.0), torso=(-22.0, -10.0, 0.0), chest=(-14.0, -8.0, 0.0),
                      legs=CROUCH, root=(0.0, 0.04, 0.68), head=(10.0, -6.0, 0.0))
# The Frenzy: the arm flails around the body in wide loops, one sweep after another.
FLAIL_A = right_arm((150.0, 0.0, 55.0), (30.0, 0.0, 0.0), torso=(-4.0, 16.0, 3.0), chest=(-2.0, 12.0, 2.0),
                    legs=CROUCH, root=(0.0, 0.0, 0.72))
FLAIL_B = right_arm((120.0, 0.0, -35.0), (30.0, 0.0, 0.0), torso=(-8.0, -18.0, -3.0), chest=(-4.0, -14.0, -2.0),
                    legs=CROUCH, root=(0.0, 0.0, 0.72))
FLAIL_C = right_arm((60.0, 0.0, 70.0), (30.0, 0.0, 0.0), torso=(-14.0, 10.0, 0.0), chest=(-8.0, 8.0, 0.0),
                    legs=CROUCH, root=(0.0, 0.0, 0.71))

# Chopstick Stab: sink low with the weight on the back leg, arm cocked up behind the head, then the whole
# body drops into an overhand thrust down the line of the arm.
LOW = {"Thigh_R": (-20.0, 0.0, -8.0), "Leg_R": (-46.0, 0.0, 0.0),
       "Thigh_L": (20.0, 0.0, 8.0), "Leg_L": (-30.0, 0.0, 0.0)}
STAB_SINK = right_arm((176.0, 0.0, 16.0), (8.0, 0.0, 0.0), torso=(12.0, 10.0, 0.0), chest=(10.0, 10.0, 0.0),
                      legs=LOW, root=(0.0, -0.05, 0.62))
STAB_OUT = right_arm((92.0, 0.0, 2.0), (18.0, 0.0, 0.0), torso=(-30.0, -12.0, 0.0), chest=(-18.0, -10.0, 0.0),
                     legs={"Thigh_R": (-26.0, 0.0, -8.0), "Leg_R": (-52.0, 0.0, 0.0),
                           "Thigh_L": (30.0, 0.0, 8.0), "Leg_L": (-24.0, 0.0, 0.0)},
                     root=(0.0, 0.08, 0.6), head=(14.0, -6.0, 0.0))

# Chopstick Flurry: knees bent, the front foot stepped in, the fist cocked at the hip; the jabs alternate
# between fully out and drawn back.
STEP_IN = {"Thigh_R": (34.0, 0.0, -6.0), "Leg_R": (-46.0, 0.0, 0.0),
           "Thigh_L": (-18.0, 0.0, 6.0), "Leg_L": (-30.0, 0.0, 0.0)}
FLURRY_LOAD = right_arm((26.0, 0.0, 8.0), (100.0, 0.0, 0.0), torso=(-14.0, -10.0, 0.0), chest=(-8.0, -8.0, 0.0),
                        legs=STEP_IN, root=(0.0, 0.02, 0.62))
FLURRY_LOAD_DEEP = right_arm((20.0, 0.0, 10.0), (104.0, 0.0, 0.0), torso=(-18.0, -12.0, 0.0), chest=(-10.0, -9.0, 0.0),
                             legs=STEP_IN, root=(0.0, 0.02, 0.60))
FLURRY_JAB = right_arm((92.0, 0.0, 4.0), (12.0, 0.0, 0.0), torso=(-22.0, 6.0, 0.0), chest=(-14.0, 4.0, 0.0),
                       legs=STEP_IN, root=(0.0, 0.05, 0.62), head=(10.0, 0.0, 0.0))
FLURRY_JAB_HIGH = right_arm((104.0, 0.0, 12.0), (12.0, 0.0, 0.0), torso=(-22.0, 10.0, 0.0), chest=(-14.0, 8.0, 0.0),
                            legs=STEP_IN, root=(0.0, 0.05, 0.62), head=(10.0, 0.0, 0.0))
FLURRY_PULL = right_arm((40.0, 0.0, 8.0), (96.0, 0.0, 0.0), torso=(-16.0, -6.0, 0.0), chest=(-10.0, -4.0, 0.0),
                        legs=STEP_IN, root=(0.0, 0.03, 0.62))

# Single Chopstick: the right arm thrown straight up to conjure the stick, then chopped down hard with the body
# folding over it as it lands.
SLAM_UP = right_arm((176.0, 0.0, 6.0), (6.0, 0.0, 0.0), torso=(8.0, 0.0, 0.0), chest=(10.0, 0.0, 0.0),
                    legs={"Thigh_R": (-6.0, 0.0, -8.0), "Leg_R": (-10.0, 0.0, 0.0),
                          "Thigh_L": (6.0, 0.0, 8.0), "Leg_L": (-10.0, 0.0, 0.0)}, root=(0.0, -0.01, 0.75))
SLAM_DOWN = right_arm((84.0, 0.0, 4.0), (30.0, 0.0, 0.0), torso=(-34.0, 0.0, 0.0), chest=(-22.0, 0.0, 0.0),
                      legs={"Thigh_R": (-24.0, 0.0, -8.0), "Leg_R": (-50.0, 0.0, 0.0),
                            "Thigh_L": (26.0, 0.0, 8.0), "Leg_L": (-26.0, 0.0, 0.0)},
                      root=(0.0, 0.08, 0.62), head=(16.0, 0.0, 0.0))

# Asura Chopsticks: the arm punched straight ahead again and again, alternating high and low, the body leaning
# into it.
ASURA_A = right_arm((94.0, 0.0, 6.0), (14.0, 0.0, 0.0), torso=(-20.0, -6.0, 0.0), chest=(-12.0, -4.0, 0.0),
                    legs=CROUCH, root=(0.0, 0.03, 0.70))
ASURA_B = right_arm((112.0, 0.0, 10.0), (14.0, 0.0, 0.0), torso=(-16.0, 6.0, 0.0), chest=(-10.0, 4.0, 0.0),
                    legs=CROUCH, root=(0.0, 0.03, 0.70))
ASURA_PULL = right_arm((52.0, 0.0, 8.0), (60.0, 0.0, 0.0), torso=(-12.0, 0.0, 0.0), chest=(-8.0, 0.0, 0.0),
                       legs=CROUCH, root=(0.0, 0.01, 0.71))

CLIPS = {
    # Summon: the arm thrown up over the head and held there while the stick hangs waiting.
    "chopstick_single": (20, [
        (0, NEUTRAL, "smooth"),
        (8, SLAM_UP, "settle"),
        (20, SLAM_UP, "smooth"),
    ]),
    # Drop: from the raised arm, chopped down as the stick lands (it comes down over 6 ticks, about 7 frames).
    "chopstick_single_slam": (24, [
        (0, SLAM_UP, "snap"),
        (7, SLAM_DOWN, "lin"),
        (13, SLAM_DOWN, "smooth"),
        (24, NEUTRAL, "smooth"),
    ]),
    "chopstick_asura": (24, [
        (0, ASURA_PULL, "snap"),
        (3, ASURA_A, "smooth"),
        (6, ASURA_PULL, "snap"),
        (9, ASURA_B, "smooth"),
        (12, ASURA_PULL, "snap"),
        (15, ASURA_A, "smooth"),
        (18, ASURA_PULL, "snap"),
        (21, ASURA_B, "smooth"),
        (24, ASURA_PULL, "smooth"),
    ], 1),
    "chopstick_stab": (26, [
        (0, NEUTRAL, "smooth"),
        (6, STAB_SINK, "snap"),
        (10, STAB_OUT, "lin"),
        (17, STAB_OUT, "smooth"),
        (26, NEUTRAL, "smooth"),
    ]),
    "chopstick_flurry_charge": (56, [
        (0, NEUTRAL, "smooth"),
        (10, FLURRY_LOAD, "settle"),
        (34, FLURRY_LOAD_DEEP, "smooth"),
        (56, FLURRY_LOAD, "smooth"),
    ]),
    "chopstick_flurry_hold": (12, [
        (0, FLURRY_LOAD, "lin"),
        (12, FLURRY_LOAD, "lin"),
    ]),
    "chopstick_flurry": (24, [
        (0, FLURRY_LOAD, "snap"),
        (2, FLURRY_JAB, "smooth"),
        (4, FLURRY_PULL, "snap"),
        (6, FLURRY_JAB_HIGH, "smooth"),
        (8, FLURRY_PULL, "snap"),
        (10, FLURRY_JAB, "smooth"),
        (12, FLURRY_PULL, "snap"),
        (14, FLURRY_JAB_HIGH, "smooth"),
        (16, FLURRY_PULL, "snap"),
        (18, FLURRY_JAB, "smooth"),
        (20, FLURRY_PULL, "snap"),
        (22, FLURRY_JAB_HIGH, "smooth"),
        (24, FLURRY_PULL, "smooth"),
    ], 1),
    "chopstick_fist": (24, [
        (0, NEUTRAL, "settle"),
        (14, FIST_HELD, "smooth"),
        (24, FIST_HELD, "smooth"),
    ]),
    "chopstick_throw": (20, [
        (0, FIST_HELD, "smooth"),
        (7, THROW_WIND, "snap"),
        (11, THROW_OUT, "lin"),
        (15, THROW_OUT, "smooth"),
        (20, NEUTRAL, "smooth"),
    ]),
    "chopstick_frenzy": (24, [
        (0, FLAIL_A, "smooth"),
        (6, FLAIL_B, "smooth"),
        (12, FLAIL_C, "smooth"),
        (18, FLAIL_B, "smooth"),
        (24, FLAIL_A, "smooth"),
    ]),
    # Arms thrown up to conjure the sticks, then lowered into the ready stance.
    "chopsticks": (36, [
        (0, NEUTRAL, "settle"),
        (12, RAISED, "lin"),
        (20, RAISED, "smooth"),
        (36, READY, "smooth"),
    ]),
    # Draw back, stab down, hold the grip, ease out.
    "chopsticks_pick": (24, [
        (0, READY, "smooth"),
        (7, PICK_WIND, "snap"),
        (12, PICK_STAB, "lin"),
        (18, PICK_STAB, "smooth"),
        (24, NEUTRAL, "smooth"),
    ]),
    # Raise, sweep across, follow through.
    "chopsticks_transfer": (44, [
        (0, READY, "smooth"),
        (10, SWEEP_A, "lin"),
        (16, SWEEP_A, "smooth"),
        (32, SWEEP_B, "lin"),
        (44, NEUTRAL, "smooth"),
    ]),
}


def preview(clip, frame=0, tag=None):
    """Render one frame of a clip to <rig>/preview_<tag>.png and return the path."""
    return _fw.preview(CLIPS, clip, frame, tag, PREVIEW_DIR)


def export_all():
    return _fw.export_clips(CLIPS, OUT_DIR)


if __name__ == "__main__":
    previews = [preview("chopsticks", 14, "chopsticks_raised"),
                preview("chopsticks_pick", 13, "chopsticks_pick_stab"),
                preview("chopsticks_transfer", 14, "chopsticks_transfer_a")]
    print("PREVIEWS=" + json.dumps(previews))
    print("EXPORTED=" + json.dumps(export_all()))
else:
    pass  # interactive: exec this file, then call preview("chopsticks", 14) etc.
