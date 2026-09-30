"""Author the player's wall / ceiling cling clips for Epic Fight, from limb directions on the biped rig.

Forward is -Y, up is +Z; 'R' bones sit at +X. Every pose gives the direction each limb points; the matrices are
written the way the Epic Fight loader reads them (see efrig.py, verified against biped.json and shipped clips).
"""
import sys, json, math
sys.path.insert(0, 'C:/Users/hanjw/Downloads/gourmet2-main/gourmet2-main/epic fight agent/ef_player')
import bpy
import efrig
from mathutils import Vector

OUT = 'C:/Users/hanjw/Downloads/gourmet2-main/gourmet2-main/src/main/resources/assets/gourmet2/animmodels/animations/skill/'
PREVIEW = 'C:/Users/hanjw/Downloads/gourmet2-main/gourmet2-main/epic fight agent/ef_player/'
arm = efrig.rig()

def V(x, y, z):
    return Vector((x, y, z))

def mirror(d):
    """Swap R and L (and flip x)."""
    out = {}
    for k, v in d.items():
        nk = k.replace('_R', '_L') if k.endswith('_R') else k.replace('_L', '_R') if k.endswith('_L') else k
        out[nk] = V(-v.x, v.y, v.z)
    return out

def limb(side, arm_dir, hand_dir, thigh, leg):
    s = 'R' if side > 0 else 'L'
    return {'Arm_' + s: arm_dir, 'Hand_' + s: hand_dir, 'Elbow_' + s: arm_dir, 'Thigh_' + s: thigh, 'Leg_' + s: leg,
            'Knee_' + s: thigh, 'Shoulder_' + s: V(side, 0, -.15), 'Tool_' + s: hand_dir}

# ---- wall: facing the wall (at -Y), hands up on it, knees bent, feet pressed to it
def wall(hand_hi=1.0, hand_lo=0.55, knee_hi=1.0, knee_lo=0.0, lean=.12, dz=-0.05):
    d = {'Root': V(0, 0, 1), 'Torso': V(0, lean, 1), 'Chest': V(0, lean, 1), 'Head': V(0, -.20, 1)}
    d.update(limb(+1, V(0.10, -.55, hand_hi), V(0, -.75, hand_hi * .6), V(.25, -.35 - knee_hi * .3, -.75 + knee_hi * .45), V(0, -.10 - knee_hi * .1, -1)))
    d.update(limb(-1, V(-0.10, -.55, hand_lo + .3), V(0, -.75, hand_lo * .6), V(-.25, -.35 - knee_lo * .3, -.75 + knee_lo * .45), V(0, -.10 - knee_lo * .1, -1)))
    return d, (0, 0.03, dz + knee_hi * .02)

# ---- ceiling: hanging by the hands, body loose beneath
def ceiling(reach_r=0.0, reach_l=0.0, swing=0.0, dz=0.05):
    d = {'Root': V(0, swing * .5, 1), 'Torso': V(0, swing * .3, 1), 'Chest': V(0, swing * .2, 1), 'Head': V(0, -.35, 1)}
    d.update(limb(+1, V(0.16, -.10 - reach_r * .5, 1.0), V(0.0, -.65 - reach_r * .5, .75), V(.12, -.10 + swing * .8, -1), V(0, .18 + swing * .5, -1)))
    d.update(limb(-1, V(-0.16, -.10 - reach_l * .5, 1.0), V(0.0, -.65 - reach_l * .5, .75), V(-.12, -.10 + swing * .8, -1), V(0, .18 + swing * .5, -1)))
    return d, (0, 0.0, dz)

def pose(spec):
    d, off = spec
    mats = efrig.pose_by_directions(arm, d, root_offset=off)
    efrig.apply_matrices(arm, mats)
    return efrig.encode_pose(arm)

def clip(name, frames, fps=24):
    """frames: list of (frame, spec)."""
    tracks = {j: {'name': j, 'time': [], 'transform': []} for j in efrig.ORDER}
    for f, spec in frames:
        enc = pose(spec)
        for j in efrig.ORDER:
            tracks[j]['time'].append(round(f / fps, 4)); tracks[j]['transform'].append(enc[j])
    data = {'animation': [tracks[j] for j in efrig.ORDER]}
    open(OUT + name + '.json', 'w').write(json.dumps(data))
    return data

def preview(name, data, picks):
    scn = bpy.context.scene
    efrig.setup_render(); 
    for view in ('front', 'side'):
        efrig.camera(view)
        for k in picks:
            mats = efrig.decode_frame(data, k); efrig.apply_matrices(arm, mats); efrig.draw_stick(arm)
            scn.render.filepath = PREVIEW + 'prev_%s_%s_%02d.png' % (name, view, k)
            bpy.ops.render.render(write_still=True)

WALL_A = wall()
WALL_B = wall(hand_hi=.55, hand_lo=1.0, knee_hi=0.0, knee_lo=1.0)
CEIL_A = ceiling()
CEIL_R = ceiling(reach_r=1.0, reach_l=-.3, swing=.10)
CEIL_L = ceiling(reach_r=-.3, reach_l=1.0, swing=-.10)

made = {}
made['cling_wall'] = clip('cling_wall', [(0, WALL_A), (12, WALL_A)])
made['cling_wall_move'] = clip('cling_wall_move', [(0, WALL_A), (12, WALL_B), (24, WALL_A)])
made['cling_ceiling'] = clip('cling_ceiling', [(0, CEIL_A), (12, CEIL_A)])
made['cling_ceiling_move'] = clip('cling_ceiling_move', [(0, CEIL_R), (12, CEIL_L), (24, CEIL_R)])
for n, data in made.items():
    preview(n, data, [0] if 'move' not in n else [0, 1])
print('MADE', list(made))
