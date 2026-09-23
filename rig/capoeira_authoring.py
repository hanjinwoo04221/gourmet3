# Authoring tool for the "capoeira" combat style's Epic Fight animation clips.
#
# Run inside Blender with rig/EpicFight Animation Rig.blend open:
#   exec(open(r"...\rig\capoeira_authoring.py", encoding="utf-8").read())
# then call preview("basic1", 7) etc.
#
# Headless full export:
#   blender --background "EpicFight Animation Rig.blend" --python capoeira_authoring.py
#
# The pose conventions, the grounding helper and the baking/export code live in
# combat_style_authoring.py.
#
# The style: capoeira, bundled the way the combat system wants it — three attack groups, one key
# each, and each clip named after its group:
#   kick  ginga's crescent (meia lua), the spinning armada, and the queixada finisher
#   hand  the short-range form: galopante, cotovelada, asfixiante
#   spin  the rotational finish: meia lua de compasso, chibata, and a full au batido cartwheel
# Everything reads as a fight that refuses to stand still: the neutral pose is ginga's constant
# side-to-side sway, and the dodge is a negativa that drops under the swing. Four clips rotate the
# Root a full 360 (for the cartwheel, flip it) and end exactly where they started, so a spin never
# leaves the player facing a different way than the server thinks.
#
# Kick heights: the ankle bone hangs off the thigh (see ankle_heights), so the foot's height is
# hip_z + 0.7494 * (-cos(rx) * cos(rz)). A kick that should arrive at hip height therefore needs a
# thigh rx near 80-90; smaller values sweep low like a leg sweep. Every pose that is supposed to
# touch the floor goes through G(...) so the supporting foot stays planted.
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
                       "src", "main", "resources", "assets", "gourmet2", "animmodels", "animations", "combat_capoeira")
PREVIEW_DIR = _HERE

G = grounded  # G(pose) plants its lowest foot on the floor; G(pose, feet=("Knee_L",)) plants the left

# Keys are grounded as they are written, but capoeira leans far enough that the frames *between*
# them would still dip a foot through the floor, so clamp every baked frame too.
_fw.FLOOR_CLAMP = True


def mirrored(spec):
    """The same pose reflected left-to-right: the right side does what the left did.

    Each bone frame is world-aligned (X right, and the bone's own Y down or up the bone), so the
    mirror flips the two non-forward angles and swaps the _R/_L bones; root_pos flips its x."""
    out = {}
    for bone, value in spec.items():
        if bone == "root_pos":
            out[bone] = (-value[0], value[1], value[2])
            continue
        side = bone[-2:]
        name = bone
        if side in ("_R", "_L"):
            name = bone[:-2] + ("_L" if side == "_R" else "_R")
        out[name] = (value[0], -value[1], -value[2])
    return out


# ---------------------------------------------------------------- poses

# Ginga: weight on the right leg, left foot forward, right hand up across the chin, left arm
# swinging low and open. The style's neutral pose and the pose every one-shot returns to.
STANCE = G({
    "root_pos": (0.03, 0.0, 0.695),
    "Torso": (-7.0, -16.0, 4.0),
    "Chest": (-5.0, -10.0, 2.0),
    "Head": (10.0, 14.0, -4.0),
    "Thigh_R": (-16.0, 0.0, -16.0),
    "Leg_R": (-32.0, 0.0, 0.0),
    "Thigh_L": (20.0, 0.0, 14.0),
    "Leg_L": (-14.0, 0.0, 0.0),
    "Shoulder_R": (0.0, 8.0, 0.0),
    "Arm_R": (52.0, 0.0, 58.0),
    "Hand_R": (74.0, 0.0, 0.0),
    "Shoulder_L": (0.0, -6.0, 0.0),
    "Arm_L": (34.0, 0.0, 40.0),
    "Hand_L": (18.0, 0.0, 0.0),
})

# The other half of the sway (weight on the left leg, right foot forward).
STANCE_B = mirrored(STANCE)

# Guard: a low coiled cross-arm block, elbows in tight, weight sitting back.
GUARD_POSE = G({
    "root_pos": (0.0, -0.03, 0.655),
    "Torso": (-15.0, -9.0, 2.0),
    "Chest": (-10.0, -6.0, 1.0),
    "Head": (17.0, 10.0, -2.0),
    "Thigh_R": (-20.0, 0.0, -17.0),
    "Leg_R": (-42.0, 0.0, 0.0),
    "Thigh_L": (25.0, 0.0, 15.0),
    "Leg_L": (-27.0, 0.0, 0.0),
    "Shoulder_R": (0.0, 12.0, 0.0),
    "Arm_R": (56.0, 0.0, 62.0),
    "Hand_R": (78.0, 0.0, 0.0),
    "Shoulder_L": (0.0, -9.0, 0.0),
    "Arm_L": (50.0, 0.0, -58.0),
    "Hand_L": (78.0, 0.0, 0.0),
})

CLIPS = {
    # --- one-shot actions -------------------------------------------------
    "kick1": (17, [  # meia lua de frente: the right leg scythes across the front, hip high
        (0, STANCE, "lin"),
        (3, G({**STANCE, "root_pos": (0.07, -0.01, 0.67), "Root": (0.0, 0.0, 8.0),
               "Torso": (-6.0, -30.0, 6.0), "Chest": (-4.0, -18.0, 3.0), "Head": (10.0, 26.0, -6.0),
               "Thigh_R": (-24.0, 0.0, -54.0), "Leg_R": (-30.0, 0.0, 0.0),
               "Thigh_L": (14.0, 0.0, 12.0), "Leg_L": (-26.0, 0.0, 0.0),
               "Arm_R": (44.0, 0.0, 66.0), "Arm_L": (30.0, 0.0, 54.0)}, feet=("Knee_L",)), "snap"),
        (6, G({**STANCE, "root_pos": (0.0, 0.05, 0.70), "Root": (0.0, 0.0, 16.0),
               "Torso": (-10.0, -8.0, -8.0), "Chest": (-6.0, -4.0, -5.0), "Head": (12.0, 12.0, 8.0),
               "Thigh_R": (80.0, 0.0, -22.0), "Leg_R": (-6.0, 0.0, 0.0),
               "Thigh_L": (-6.0, 0.0, 14.0), "Leg_L": (-12.0, 0.0, 0.0),
               "Arm_R": (26.0, 0.0, -52.0), "Hand_R": (26.0, 0.0, 0.0),
               "Arm_L": (58.0, 0.0, 30.0), "Hand_L": (40.0, 0.0, 0.0)}, feet=("Knee_L",)), "snap"),
        (10, G({**STANCE, "root_pos": (-0.02, 0.05, 0.70), "Root": (0.0, 0.0, 20.0),
                "Torso": (-9.0, -2.0, -9.0), "Chest": (-6.0, -1.0, -5.0),
                "Thigh_R": (70.0, 0.0, 55.0), "Leg_R": (-10.0, 0.0, 0.0),
                "Thigh_L": (-8.0, 0.0, 14.0), "Leg_L": (-14.0, 0.0, 0.0),
                "Arm_R": (24.0, 0.0, -66.0), "Arm_L": (52.0, 0.0, 44.0)}, feet=("Knee_L",)), "settle"),
        (13, G({**STANCE, "root_pos": (0.03, 0.0, 0.685), "Root": (0.0, 0.0, 8.0),
                "Torso": (-8.0, -14.0, 0.0),
                "Thigh_R": (6.0, 0.0, -6.0), "Leg_R": (-26.0, 0.0, 0.0)}), "smooth"),
        (17, STANCE, "smooth"),
    ]),
    "kick2": (17, [  # armada: a full turn on the left foot, the left leg whipping out level
        (0, STANCE, "lin"),
        (2, G({**STANCE, "root_pos": (0.02, 0.0, 0.675), "Root": (0.0, -30.0, 8.0),
               "Torso": (-9.0, -34.0, 7.0), "Chest": (-6.0, -20.0, 4.0), "Head": (8.0, 30.0, -6.0),
               "Thigh_L": (-18.0, 0.0, 6.0), "Leg_L": (-38.0, 0.0, 0.0),
               "Thigh_R": (-6.0, 0.0, -20.0), "Leg_R": (-24.0, 0.0, 0.0),
               "Arm_R": (40.0, 0.0, 74.0), "Arm_L": (26.0, 0.0, 30.0)}, feet=("Knee_R",)), "snap"),
        (5, G({**STANCE, "root_pos": (-0.04, 0.02, 0.72), "Root": (0.0, 62.0, -12.0),
               "Torso": (-6.0, 16.0, -10.0), "Chest": (-4.0, 10.0, -6.0), "Head": (10.0, -18.0, 8.0),
               "Thigh_L": (25.0, 0.0, 82.0), "Leg_L": (-8.0, 0.0, 0.0),
               "Thigh_R": (-18.0, 0.0, -10.0), "Leg_R": (-16.0, 0.0, 0.0),
               "Arm_R": (24.0, 0.0, -66.0), "Hand_R": (20.0, 0.0, 0.0),
               "Arm_L": (66.0, 0.0, 46.0), "Hand_L": (30.0, 0.0, 0.0)}, feet=("Knee_R",)), "snap"),
        (8, G({**STANCE, "root_pos": (-0.05, 0.03, 0.735), "Root": (0.0, 180.0, -13.0),
               "Torso": (-6.0, 18.0, -11.0), "Chest": (-4.0, 12.0, -6.0),
               "Thigh_L": (22.0, 0.0, 86.0), "Leg_L": (-4.0, 0.0, 0.0),
               "Thigh_R": (-20.0, 0.0, -10.0), "Leg_R": (-18.0, 0.0, 0.0),
               "Arm_R": (22.0, 0.0, -70.0), "Arm_L": (70.0, 0.0, 44.0)}, feet=("Knee_R",)), "lin"),
        (11, G({**STANCE, "root_pos": (-0.03, 0.02, 0.72), "Root": (0.0, 300.0, -10.0),
                "Torso": (-8.0, 14.0, -8.0), "Chest": (-5.0, 9.0, -5.0),
                "Thigh_L": (30.0, 0.0, 58.0), "Leg_L": (-18.0, 0.0, 0.0),
                "Thigh_R": (-18.0, 0.0, -12.0), "Leg_R": (-24.0, 0.0, 0.0),
                "Arm_R": (26.0, 0.0, -60.0), "Arm_L": (64.0, 0.0, 46.0)}, feet=("Knee_R",)), "settle"),
        (14, G({**STANCE, "root_pos": (0.0, 0.0, 0.70), "Root": (0.0, 360.0, -4.0),
                "Torso": (-8.0, -4.0, 0.0),
                "Thigh_L": (20.0, 0.0, 24.0), "Leg_L": (-30.0, 0.0, 0.0),
                "Thigh_R": (-16.0, 0.0, -18.0), "Leg_R": (-34.0, 0.0, 0.0)}), "smooth"),
        (17, {**STANCE, "Root": (0.0, 360.0, 0.0)}, "smooth"),
    ], 1),  # every frame: the root turns a full 360 through this clip
    "kick3": (20, [  # queixada into a stamp: the right leg arcs from the inside out, then plants
        (0, STANCE, "lin"),
        (3, G({**STANCE, "root_pos": (-0.03, 0.0, 0.675), "Torso": (-6.0, 26.0, -6.0),
               "Chest": (-4.0, 16.0, -4.0), "Head": (10.0, -22.0, 6.0),
               "Thigh_R": (62.0, 0.0, 48.0), "Leg_R": (-52.0, 0.0, 0.0),
               "Thigh_L": (10.0, 0.0, 10.0), "Leg_L": (-24.0, 0.0, 0.0),
               "Arm_R": (34.0, 0.0, -30.0), "Arm_L": (54.0, 0.0, 34.0)}, feet=("Knee_L",)), "snap"),
        (7, G({**STANCE, "root_pos": (0.05, 0.05, 0.72), "Root": (0.0, 0.0, 16.0),
               "Torso": (-11.0, -24.0, -8.0), "Chest": (-7.0, -14.0, -5.0), "Head": (12.0, 24.0, 8.0),
               "Thigh_R": (82.0, 0.0, -40.0), "Leg_R": (-6.0, 0.0, 0.0),
               "Thigh_L": (-10.0, 0.0, 16.0), "Leg_L": (-18.0, 0.0, 0.0),
               "Arm_R": (22.0, 0.0, -68.0), "Hand_R": (16.0, 0.0, 0.0),
               "Arm_L": (60.0, 0.0, 24.0), "Hand_L": (40.0, 0.0, 0.0)}, feet=("Knee_L",)), "snap"),
        (11, G({**STANCE, "root_pos": (0.07, 0.04, 0.71), "Root": (0.0, 0.0, 20.0),
                "Torso": (-10.0, -34.0, -10.0), "Chest": (-6.0, -20.0, -6.0),
                "Thigh_R": (76.0, 0.0, -74.0), "Leg_R": (-10.0, 0.0, 0.0),
                "Thigh_L": (-12.0, 0.0, 16.0), "Leg_L": (-20.0, 0.0, 0.0),
                "Arm_R": (20.0, 0.0, -76.0), "Arm_L": (62.0, 0.0, 30.0)}, feet=("Knee_L",)), "settle"),
        (15, G({**STANCE, "root_pos": (0.02, 0.02, 0.655), "Root": (0.0, 0.0, 8.0),
                "Torso": (-16.0, -18.0, -2.0), "Chest": (-10.0, -10.0, -2.0), "Head": (16.0, 16.0, 2.0),
                "Thigh_R": (-6.0, 0.0, -18.0), "Leg_R": (-46.0, 0.0, 0.0),
                "Thigh_L": (24.0, 0.0, 16.0), "Leg_L": (-30.0, 0.0, 0.0),
                "Arm_R": (48.0, 0.0, -20.0), "Hand_R": (60.0, 0.0, 0.0),
                "Arm_L": (46.0, 0.0, -46.0), "Hand_L": (60.0, 0.0, 0.0)}), "snap"),
        (20, STANCE, "smooth"),
    ]),
    # --- spin: rotational finishers, ending in a full cartwheel ---
    "spin3": (24, [  # au batido: a full cartwheel, the legs scissoring over the top
        (0, STANCE, "lin"),
        (4, G({**STANCE, "root_pos": (0.03, 0.0, 0.62), "Root": (0.0, 0.0, 26.0),
               "Torso": (-10.0, -12.0, 12.0), "Chest": (-6.0, -8.0, 7.0),
               "Thigh_R": (-14.0, 0.0, -20.0), "Leg_R": (-52.0, 0.0, 0.0),
               "Thigh_L": (36.0, 0.0, 18.0), "Leg_L": (-44.0, 0.0, 0.0),
               "Arm_R": (78.0, 0.0, 46.0), "Hand_R": (40.0, 0.0, 0.0),
               "Arm_L": (14.0, 0.0, -58.0), "Hand_L": (30.0, 0.0, 0.0)}), "snap"),
        (9, {**STANCE, "root_pos": (-0.06, 0.06, 1.00), "Root": (0.0, 0.0, 110.0),
             "Torso": (-6.0, 0.0, 16.0), "Chest": (-4.0, 0.0, 10.0),
             "Thigh_L": (74.0, 0.0, 26.0), "Leg_L": (-12.0, 0.0, 0.0),
             "Thigh_R": (-50.0, 0.0, -10.0), "Leg_R": (-26.0, 0.0, 0.0),
             "Arm_R": (140.0, 0.0, 20.0), "Hand_R": (20.0, 0.0, 0.0),
             "Arm_L": (140.0, 0.0, -24.0), "Hand_L": (20.0, 0.0, 0.0)}, "lin"),
        (13, {**STANCE, "root_pos": (-0.07, 0.10, 1.10), "Root": (0.0, 0.0, 196.0),
              "Torso": (4.0, 0.0, 14.0), "Chest": (2.0, 0.0, 9.0),
              "Thigh_L": (34.0, 0.0, 28.0), "Leg_L": (-18.0, 0.0, 0.0),
              "Thigh_R": (66.0, 0.0, -30.0), "Leg_R": (-8.0, 0.0, 0.0),
              "Arm_R": (30.0, 0.0, 60.0), "Arm_L": (30.0, 0.0, -70.0),
              "Hand_R": (70.0, 0.0, 0.0), "Hand_L": (70.0, 0.0, 0.0)}, "snap"),
        (18, G({**STANCE, "root_pos": (-0.02, 0.04, 0.70), "Root": (0.0, 0.0, 322.0),
                "Torso": (-12.0, -8.0, 6.0), "Chest": (-8.0, -5.0, 4.0),
                "Thigh_L": (26.0, 0.0, 18.0), "Leg_L": (-34.0, 0.0, 0.0),
                "Thigh_R": (2.0, 0.0, -22.0), "Leg_R": (-34.0, 0.0, 0.0),
                "Arm_R": (46.0, 0.0, 20.0), "Arm_L": (30.0, 0.0, 10.0)}), "settle"),
        (24, {**STANCE, "Root": (0.0, 0.0, 360.0)}, "smooth"),
    ], 2),
    "spin1": (17, [  # meia lua de compasso: crouch onto a hand and sweep the leg all the way round
        (0, STANCE, "lin"),
        (3, G({**STANCE, "root_pos": (0.06, -0.02, 0.60), "Root": (0.0, -20.0, 10.0),
               "Torso": (6.0, -26.0, 8.0), "Chest": (4.0, -15.0, 5.0), "Head": (-6.0, 22.0, -8.0),
               "Thigh_R": (-18.0, 0.0, -24.0), "Leg_R": (-56.0, 0.0, 0.0),
               "Thigh_L": (24.0, 0.0, 40.0), "Leg_L": (-70.0, 0.0, 0.0),
               "Shoulder_R": (0.0, 6.0, 0.0), "Arm_R": (46.0, 0.0, 58.0), "Hand_R": (60.0, 0.0, 0.0),
               "Shoulder_L": (0.0, -20.0, 0.0), "Arm_L": (-30.0, 0.0, -32.0), "Hand_L": (-30.0, 0.0, 0.0)}, feet=("Knee_L",)), "snap"),
        (7, {**STANCE, "root_pos": (0.02, 0.02, 0.55), "Root": (0.0, 130.0, 16.0),
             "Torso": (10.0, 10.0, 10.0), "Chest": (6.0, 7.0, 6.0), "Head": (-10.0, -8.0, -10.0),
             "Thigh_R": (14.0, 0.0, -80.0), "Leg_R": (-8.0, 0.0, 0.0),
             "Thigh_L": (-22.0, 0.0, 6.0), "Leg_L": (-74.0, 0.0, 0.0),
             "Shoulder_R": (0.0, 10.0, 0.0), "Arm_R": (40.0, 0.0, 54.0), "Hand_R": (50.0, 0.0, 0.0),
             "Shoulder_L": (0.0, -26.0, 0.0), "Arm_L": (-46.0, 0.0, -26.0), "Hand_L": (-40.0, 0.0, 0.0)}, "lin"),
        (11, {**STANCE, "root_pos": (-0.02, 0.04, 0.60), "Root": (0.0, 250.0, 12.0),
              "Torso": (-4.0, -10.0, 4.0), "Chest": (-2.0, -6.0, 3.0),
              "Thigh_R": (24.0, 0.0, -74.0), "Leg_R": (-16.0, 0.0, 0.0),
              "Thigh_L": (-16.0, 0.0, 14.0), "Leg_L": (-60.0, 0.0, 0.0),
              "Arm_R": (34.0, 0.0, 46.0), "Arm_L": (-24.0, 0.0, -14.0)}, "settle"),
        (14, G({**STANCE, "root_pos": (0.02, 0.0, 0.66), "Root": (0.0, 340.0, 4.0),
                "Torso": (-8.0, -14.0, 3.0),
                "Thigh_R": (10.0, 0.0, -22.0), "Leg_R": (-38.0, 0.0, 0.0),
                "Thigh_L": (18.0, 0.0, 14.0), "Leg_L": (-30.0, 0.0, 0.0)}), "smooth"),
        (17, {**STANCE, "Root": (0.0, 360.0, 0.0)}, "smooth"),
    ], 2),
    "spin2": (18, [  # chibata: jump, lay the body out sideways and whip the leg round
        (0, STANCE, "lin"),
        (4, G({**STANCE, "root_pos": (0.04, 0.0, 0.62), "Root": (0.0, -16.0, 22.0),
               "Torso": (-6.0, -18.0, 10.0), "Chest": (-4.0, -10.0, 6.0),
               "Thigh_R": (-16.0, 0.0, -22.0), "Leg_R": (-54.0, 0.0, 0.0),
               "Thigh_L": (28.0, 0.0, 22.0), "Leg_L": (-56.0, 0.0, 0.0),
               "Arm_R": (40.0, 0.0, 60.0), "Hand_R": (56.0, 0.0, 0.0),
               "Arm_L": (-10.0, 0.0, -44.0), "Hand_L": (-20.0, 0.0, 0.0)}), "snap"),
        (8, {**STANCE, "root_pos": (-0.05, 0.05, 1.00), "Root": (0.0, 120.0, 46.0),
             "Torso": (2.0, 6.0, 18.0), "Chest": (1.0, 4.0, 11.0), "Head": (-6.0, 6.0, -12.0),
             "Thigh_R": (26.0, 0.0, -78.0), "Leg_R": (-8.0, 0.0, 0.0),
             "Thigh_L": (-30.0, 0.0, -6.0), "Leg_L": (-30.0, 0.0, 0.0),
             "Arm_R": (74.0, 0.0, 30.0), "Hand_R": (30.0, 0.0, 0.0),
             "Arm_L": (30.0, 0.0, -60.0), "Hand_L": (30.0, 0.0, 0.0)}, "lin"),
        (12, {**STANCE, "root_pos": (-0.03, 0.06, 0.92), "Root": (0.0, 250.0, 30.0),
              "Torso": (-4.0, -8.0, 12.0), "Chest": (-2.0, -5.0, 7.0),
              "Thigh_R": (30.0, 0.0, -64.0), "Leg_R": (-14.0, 0.0, 0.0),
              "Thigh_L": (-22.0, 0.0, -6.0), "Leg_L": (-26.0, 0.0, 0.0),
              "Arm_R": (66.0, 0.0, 34.0), "Arm_L": (26.0, 0.0, -50.0)}, "settle"),
        (15, G({**STANCE, "root_pos": (-0.01, 0.04, 0.68), "Root": (0.0, 340.0, 8.0),
                "Torso": (-10.0, -12.0, 2.0),
                "Thigh_R": (4.0, 0.0, -20.0), "Leg_R": (-40.0, 0.0, 0.0),
                "Thigh_L": (20.0, 0.0, 16.0), "Leg_L": (-32.0, 0.0, 0.0)}), "smooth"),
        (18, {**STANCE, "Root": (0.0, 360.0, 0.0)}, "smooth"),
    ], 2),
    # --- hand: the short-range form, and the fastest of the three ---
    "hand1": (16, [  # galopante: an open palm whipped across the face
        (0, STANCE, "lin"),
        (3, G({**STANCE, "root_pos": (0.05, -0.01, 0.685), "Torso": (-6.0, -30.0, 5.0),
               "Chest": (-4.0, -18.0, 3.0), "Head": (10.0, 26.0, -6.0),
               "Thigh_R": (-18.0, 0.0, -18.0), "Leg_R": (-34.0, 0.0, 0.0),
               "Thigh_L": (16.0, 0.0, 14.0), "Leg_L": (-22.0, 0.0, 0.0),
               "Shoulder_R": (0.0, -24.0, 0.0), "Arm_R": (30.0, 0.0, -30.0), "Hand_R": (6.0, 0.0, 0.0),
               "Shoulder_L": (0.0, -8.0, 0.0), "Arm_L": (54.0, 0.0, 46.0), "Hand_L": (62.0, 0.0, 0.0)}), "snap"),
        (6, G({**STANCE, "root_pos": (0.0, 0.06, 0.70), "Torso": (-9.0, 20.0, -5.0),
               "Chest": (-6.0, 12.0, -3.0), "Head": (11.0, -16.0, 5.0),
               "Thigh_R": (-12.0, 0.0, -18.0), "Leg_R": (-26.0, 0.0, 0.0),
               "Thigh_L": (20.0, 0.0, 14.0), "Leg_L": (-16.0, 0.0, 0.0),
               "Shoulder_R": (0.0, 18.0, 0.0), "Arm_R": (64.0, 0.0, 54.0), "Hand_R": (42.0, 0.0, 0.0),
               "Arm_L": (40.0, 0.0, 18.0), "Hand_L": (30.0, 0.0, 0.0)}), "snap"),
        (10, G({**STANCE, "root_pos": (0.0, 0.05, 0.70), "Torso": (-8.0, 28.0, -7.0),
                "Chest": (-5.0, 17.0, -4.0), "Head": (10.0, -20.0, 6.0),
                "Thigh_R": (-14.0, 0.0, -18.0), "Leg_R": (-30.0, 0.0, 0.0),
                "Thigh_L": (22.0, 0.0, 14.0), "Leg_L": (-18.0, 0.0, 0.0),
                "Shoulder_R": (0.0, 20.0, 0.0), "Arm_R": (56.0, 0.0, 68.0), "Hand_R": (34.0, 0.0, 0.0),
                "Arm_L": (44.0, 0.0, 14.0)}), "settle"),
        (16, STANCE, "smooth"),
    ]),
    "hand2": (16, [  # cotovelada: a short elbow driven through the guard
        (0, STANCE, "lin"),
        (3, G({**STANCE, "root_pos": (-0.04, -0.01, 0.685), "Torso": (-7.0, 18.0, -3.0),
               "Chest": (-4.0, 11.0, -2.0), "Head": (10.0, -14.0, 3.0),
               "Thigh_R": (-14.0, 0.0, -16.0), "Leg_R": (-28.0, 0.0, 0.0),
               "Thigh_L": (18.0, 0.0, 12.0), "Leg_L": (-20.0, 0.0, 0.0),
               "Shoulder_L": (0.0, 10.0, 0.0), "Arm_L": (26.0, 0.0, 58.0), "Hand_L": (104.0, 0.0, 0.0),
               "Arm_R": (54.0, 0.0, 44.0), "Hand_R": (66.0, 0.0, 0.0)}), "snap"),
        (6, G({**STANCE, "root_pos": (-0.02, 0.07, 0.705), "Torso": (-13.0, -24.0, 4.0),
               "Chest": (-8.0, -14.0, 3.0), "Head": (14.0, 20.0, -3.0),
               "Thigh_R": (-16.0, 0.0, -18.0), "Leg_R": (-30.0, 0.0, 0.0),
               "Thigh_L": (26.0, 0.0, 14.0), "Leg_L": (-14.0, 0.0, 0.0),
               "Shoulder_L": (0.0, -18.0, 0.0), "Arm_L": (62.0, 0.0, 12.0), "Hand_L": (118.0, 0.0, 0.0),
               "Arm_R": (52.0, 0.0, 60.0), "Hand_R": (72.0, 0.0, 0.0)}), "snap"),
        (10, G({**STANCE, "root_pos": (0.0, 0.06, 0.705), "Torso": (-12.0, -30.0, 5.0),
                "Chest": (-8.0, -18.0, 3.0), "Head": (13.0, 24.0, -4.0),
                "Thigh_R": (-16.0, 0.0, -18.0), "Leg_R": (-32.0, 0.0, 0.0),
                "Thigh_L": (26.0, 0.0, 14.0), "Leg_L": (-16.0, 0.0, 0.0),
                "Shoulder_L": (0.0, -12.0, 0.0), "Arm_L": (58.0, 0.0, 22.0), "Hand_L": (112.0, 0.0, 0.0),
                "Arm_R": (50.0, 0.0, 58.0)}), "settle"),
        (16, STANCE, "smooth"),
    ]),
    "hand3": (20, [  # asfixiante: sink, then drive the palm straight down the middle
        (0, STANCE, "lin"),
        (4, G({**STANCE, "root_pos": (0.05, -0.02, 0.63), "Root": (0.0, 0.0, 4.0),
               "Torso": (-4.0, -34.0, 6.0), "Chest": (-3.0, -20.0, 4.0), "Head": (8.0, 30.0, -6.0),
               "Thigh_R": (-22.0, 0.0, -26.0), "Leg_R": (-50.0, 0.0, 0.0),
               "Thigh_L": (26.0, 0.0, 26.0), "Leg_L": (-48.0, 0.0, 0.0),
               "Shoulder_R": (0.0, -30.0, 0.0), "Arm_R": (14.0, 0.0, -26.0), "Hand_R": (0.0, 0.0, 0.0),
               "Shoulder_L": (0.0, -6.0, 0.0), "Arm_L": (52.0, 0.0, 50.0), "Hand_L": (70.0, 0.0, 0.0)}), "snap"),
        (9, G({**STANCE, "root_pos": (0.0, 0.09, 0.72), "Root": (0.0, 0.0, 0.0),
               "Torso": (-12.0, 16.0, -4.0), "Chest": (-8.0, 10.0, -2.0), "Head": (14.0, -12.0, 3.0),
               "Thigh_R": (-14.0, 0.0, -18.0), "Leg_R": (-28.0, 0.0, 0.0),
               "Thigh_L": (26.0, 0.0, 14.0), "Leg_L": (-18.0, 0.0, 0.0),
               "Shoulder_R": (0.0, 22.0, 0.0), "Arm_R": (88.0, 0.0, 30.0), "Hand_R": (26.0, 0.0, 0.0),
               "Arm_L": (42.0, 0.0, 30.0), "Hand_L": (34.0, 0.0, 0.0)}), "snap"),
        (13, G({**STANCE, "root_pos": (0.0, 0.10, 0.72), "Root": (0.0, 0.0, 0.0),
                "Torso": (-13.0, 12.0, -3.0), "Chest": (-9.0, 8.0, -2.0),
                "Thigh_R": (-14.0, 0.0, -18.0), "Leg_R": (-28.0, 0.0, 0.0),
                "Thigh_L": (28.0, 0.0, 14.0), "Leg_L": (-20.0, 0.0, 0.0),
                "Shoulder_R": (0.0, 24.0, 0.0), "Arm_R": (92.0, 0.0, 28.0), "Hand_R": (22.0, 0.0, 0.0),
                "Arm_L": (40.0, 0.0, 32.0)}), "settle"),
        (20, STANCE, "smooth"),
    ]),
    "dodge": (13, [  # negativa: drop under the swing, weight back over the trailing hand
        (0, STANCE, "lin"),
        (4, G({**STANCE, "root_pos": (0.02, -0.12, 0.585), "Root": (0.0, 0.0, -6.0),
               "Torso": (12.0, 4.0, 4.0), "Chest": (8.0, 2.0, 3.0), "Head": (-4.0, 0.0, -4.0),
               "Thigh_R": (-30.0, 0.0, -22.0), "Leg_R": (-66.0, 0.0, 0.0),
               "Thigh_L": (34.0, 0.0, 18.0), "Leg_L": (-30.0, 0.0, 0.0),
               "Shoulder_R": (0.0, -16.0, 0.0), "Arm_R": (-24.0, 0.0, 24.0), "Hand_R": (-30.0, 0.0, 0.0),
               "Arm_L": (40.0, 0.0, 40.0), "Hand_L": (50.0, 0.0, 0.0)}), "snap"),
        (8, G({**STANCE, "root_pos": (0.03, -0.19, 0.545), "Root": (0.0, 0.0, -10.0),
               "Torso": (18.0, 6.0, 6.0), "Chest": (12.0, 3.0, 4.0), "Head": (-8.0, 0.0, -6.0),
               "Thigh_R": (-34.0, 0.0, -26.0), "Leg_R": (-78.0, 0.0, 0.0),
               "Thigh_L": (40.0, 0.0, 20.0), "Leg_L": (-24.0, 0.0, 0.0),
               "Shoulder_R": (0.0, -24.0, 0.0), "Arm_R": (-40.0, 0.0, 26.0), "Hand_R": (-40.0, 0.0, 0.0),
               "Arm_L": (36.0, 0.0, 44.0), "Hand_L": (54.0, 0.0, 0.0)}), "settle"),
        (13, STANCE, "smooth"),
    ]),
    "launcher": (21, [  # bencao: sink, then drive a straight kick up through the target
        (0, STANCE, "lin"),
        (5, G({**STANCE, "root_pos": (0.02, 0.0, 0.565), "Root": (0.0, 0.0, 0.0),
               "Torso": (-2.0, -10.0, 4.0), "Chest": (-1.0, -6.0, 2.0),
               "Thigh_R": (-22.0, 0.0, -20.0), "Leg_R": (-58.0, 0.0, 0.0),
               "Thigh_L": (30.0, 0.0, 16.0), "Leg_L": (-52.0, 0.0, 0.0),
               "Arm_R": (34.0, 0.0, 40.0), "Hand_R": (50.0, 0.0, 0.0),
               "Arm_L": (26.0, 0.0, 26.0), "Hand_L": (30.0, 0.0, 0.0)}), "snap"),
        (9, G({**STANCE, "root_pos": (0.0, 0.03, 0.80), "Root": (0.0, 0.0, 0.0),
               "Torso": (10.0, -6.0, 0.0), "Chest": (6.0, -4.0, 0.0), "Head": (-8.0, 0.0, 0.0),
               "Thigh_R": (112.0, 0.0, 2.0), "Leg_R": (-10.0, 0.0, 0.0),
               "Thigh_L": (-10.0, 0.0, 10.0), "Leg_L": (-8.0, 0.0, 0.0),
               "Arm_R": (10.0, 0.0, -46.0), "Hand_R": (10.0, 0.0, 0.0),
               "Arm_L": (46.0, 0.0, 54.0), "Hand_L": (20.0, 0.0, 0.0)}, feet=("Knee_L",)), "snap"),
        (13, G({**STANCE, "root_pos": (0.0, 0.03, 0.835), "Root": (0.0, 0.0, 0.0),
                "Torso": (14.0, -4.0, 0.0), "Chest": (8.0, -2.0, 0.0), "Head": (-10.0, 0.0, 0.0),
                "Thigh_R": (128.0, 0.0, 0.0), "Leg_R": (-6.0, 0.0, 0.0),
                "Thigh_L": (-14.0, 0.0, 10.0), "Leg_L": (-4.0, 0.0, 0.0),
                "Arm_R": (6.0, 0.0, -56.0), "Arm_L": (52.0, 0.0, 52.0)}, feet=("Knee_L",)), "settle"),
        (21, STANCE, "smooth"),
    ]),
    "spike": (18, [  # martelo de negativa: heel up, then hammer it straight down
        (0, {**STANCE, "root_pos": (0.0, -0.02, 0.86), "Root": (0.0, 0.0, 4.0),
             "Torso": (6.0, -12.0, 2.0), "Chest": (4.0, -8.0, 1.0), "Head": (-6.0, 10.0, 0.0),
             "Thigh_R": (152.0, 0.0, -4.0), "Leg_R": (-8.0, 0.0, 0.0),
             "Thigh_L": (-14.0, 0.0, 12.0), "Leg_L": (-14.0, 0.0, 0.0),
             "Arm_R": (24.0, 0.0, -50.0), "Hand_R": (10.0, 0.0, 0.0),
             "Arm_L": (40.0, 0.0, 56.0), "Hand_L": (20.0, 0.0, 0.0)}, "snap"),
        (7, G({**STANCE, "root_pos": (0.0, 0.06, 0.62), "Root": (0.0, 0.0, 0.0),
               "Torso": (-20.0, -4.0, 0.0), "Chest": (-12.0, -2.0, 0.0), "Head": (16.0, 0.0, 0.0),
               "Thigh_R": (-4.0, 0.0, -10.0), "Leg_R": (-6.0, 0.0, 0.0),
               "Thigh_L": (28.0, 0.0, 14.0), "Leg_L": (-40.0, 0.0, 0.0),
               "Arm_R": (34.0, 0.0, 34.0), "Hand_R": (30.0, 0.0, 0.0),
               "Arm_L": (30.0, 0.0, 26.0), "Hand_L": (30.0, 0.0, 0.0)}), "snap"),
        (11, G({**STANCE, "root_pos": (0.02, 0.05, 0.575), "Torso": (-16.0, -8.0, 2.0),
                "Thigh_R": (-10.0, 0.0, -14.0), "Leg_R": (-30.0, 0.0, 0.0),
                "Thigh_L": (30.0, 0.0, 16.0), "Leg_L": (-46.0, 0.0, 0.0),
                "Arm_R": (44.0, 0.0, 30.0), "Arm_L": (36.0, 0.0, 30.0)}), "settle"),
        (18, STANCE, "smooth"),
    ]),
    # --- stances (looping) ------------------------------------------------
    "guard": (14, [  # static tight cross-arm guard
        (0, GUARD_POSE, "lin"),
        (14, GUARD_POSE, "lin"),
    ]),
    "guard_release": (6, [
        (0, GUARD_POSE, "smooth"),
        (6, STANCE, "lin"),
    ]),
    "idle_stance": (24, [  # ginga: the endless sway the whole style is built on
        (0, STANCE, "smooth"),
        (6, G({**STANCE, "root_pos": (0.0, 0.0, 0.685), "Torso": (-8.0, -8.0, 2.0), "Chest": (-6.0, -5.0, 1.0),
               "Thigh_R": (-2.0, 0.0, -18.0), "Leg_R": (-30.0, 0.0, 0.0),
               "Thigh_L": (10.0, 0.0, 16.0), "Leg_L": (-22.0, 0.0, 0.0),
               "Arm_R": (48.0, 0.0, 46.0), "Arm_L": (30.0, 0.0, 44.0)}), "smooth"),
        (12, STANCE_B, "smooth"),
        (18, G({**STANCE_B, "root_pos": (-0.03, 0.0, 0.685), "Torso": (-8.0, 8.0, -2.0), "Chest": (-6.0, 5.0, -1.0),
                "Thigh_L": (-2.0, 0.0, 18.0), "Leg_L": (-30.0, 0.0, 0.0),
                "Thigh_R": (10.0, 0.0, -16.0), "Leg_R": (-22.0, 0.0, 0.0),
                "Arm_L": (48.0, 0.0, -46.0), "Arm_R": (30.0, 0.0, -44.0)}), "smooth"),
        (24, STANCE, "smooth"),
    ]),
    "walk_stance": (24, [  # stepping ginga: the sway keeps travelling
        (0, STANCE, "smooth"),
        (6, G({**STANCE, "root_pos": (0.05, 0.0, 0.70), "Torso": (-9.0, -10.0, 5.0),
               "Thigh_R": (-22.0, 0.0, -20.0), "Leg_R": (-40.0, 0.0, 0.0),
               "Thigh_L": (30.0, 0.0, 14.0), "Leg_L": (-10.0, 0.0, 0.0),
               "Arm_R": (50.0, 0.0, 52.0), "Arm_L": (32.0, 0.0, 46.0)}), "smooth"),
        (12, G({**STANCE, "root_pos": (0.0, 0.0, 0.71), "Torso": (-7.0, -2.0, 0.0), "Chest": (-5.0, -1.0, 0.0),
                "Thigh_R": (-8.0, 0.0, -18.0), "Leg_R": (-32.0, 0.0, 0.0),
                "Thigh_L": (16.0, 0.0, 16.0), "Leg_L": (-20.0, 0.0, 0.0)}), "smooth"),
        (18, G({**STANCE_B, "root_pos": (-0.05, 0.0, 0.70), "Torso": (-9.0, 10.0, -5.0),
                "Thigh_L": (-22.0, 0.0, 20.0), "Leg_L": (-40.0, 0.0, 0.0),
                "Thigh_R": (30.0, 0.0, -14.0), "Leg_R": (-10.0, 0.0, 0.0),
                "Arm_L": (50.0, 0.0, -52.0), "Arm_R": (32.0, 0.0, -46.0)}), "smooth"),
        (24, STANCE, "smooth"),
    ]),
    "run_stance": (16, [  # running ginga: bigger swings, deeper lean
        (0, G({**STANCE, "root_pos": (0.02, 0.0, 0.665), "Torso": (-18.0, -18.0, 6.0), "Chest": (-12.0, -11.0, 3.0),
               "Thigh_R": (-34.0, 0.0, -18.0), "Leg_R": (-58.0, 0.0, 0.0),
               "Thigh_L": (44.0, 0.0, 14.0), "Leg_L": (-14.0, 0.0, 0.0),
               "Arm_R": (54.0, 0.0, 58.0), "Arm_L": (28.0, 0.0, 40.0)}), "smooth"),
        (8, G({**STANCE_B, "root_pos": (-0.02, 0.0, 0.665), "Torso": (-18.0, 18.0, -6.0), "Chest": (-12.0, 11.0, -3.0),
               "Thigh_L": (-34.0, 0.0, 18.0), "Leg_L": (-58.0, 0.0, 0.0),
               "Thigh_R": (44.0, 0.0, -14.0), "Leg_R": (-14.0, 0.0, 0.0),
               "Arm_L": (54.0, 0.0, -58.0), "Arm_R": (28.0, 0.0, -40.0)}), "smooth"),
        (16, G({**STANCE, "root_pos": (0.02, 0.0, 0.665), "Torso": (-18.0, -18.0, 6.0), "Chest": (-12.0, -11.0, 3.0),
                "Thigh_R": (-34.0, 0.0, -18.0), "Leg_R": (-58.0, 0.0, 0.0),
                "Thigh_L": (44.0, 0.0, 14.0), "Leg_L": (-14.0, 0.0, 0.0),
                "Arm_R": (54.0, 0.0, 58.0), "Arm_L": (28.0, 0.0, 40.0)}), "smooth"),
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
