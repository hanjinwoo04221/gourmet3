"""Epic Fight <-> Blender pose helpers for the biped rig in 'EpicFight Animation Rig.blend'.

File matrix convention (see JsonAssetLoader.getTransformSheet): a joint's entry is the row-major 4x4 of its pose
matrix relative to its parent, in Blender armature space; the Root entry is its armature-space matrix. The loader
multiplies by the inverse rest-local transform, so the rest pose exports as identity motion.
"""
import json, math
import bpy
from mathutils import Matrix, Vector, Quaternion, Euler

ORDER = ['Root','Thigh_R','Leg_R','Knee_R','Thigh_L','Leg_L','Knee_L','Torso','Chest','Head','Shoulder_R','Arm_R',
         'Hand_R','Tool_R','Elbow_R','Shoulder_L','Arm_L','Hand_L','Tool_L','Elbow_L']

def rig():
    return [o for o in bpy.data.objects if o.type == 'ARMATURE'][0]

def mat_from_flat(v):
    return Matrix([v[0:4], v[4:8], v[8:12], v[12:16]])

def apply_matrices(arm, mats):
    """mats: joint name -> armature-space pose matrix. Applied parent-first."""
    for name in ORDER:
        if name in mats:
            arm.pose.bones[name].matrix = mats[name]
        bpy.context.view_layer.update()

def decode_frame(clip, i):
    """JSON clip -> armature-space pose matrices at key index i."""
    tracks = {t['name']: t for t in clip['animation']}
    out = {}
    for name in ORDER:
        if name not in tracks:
            continue
        tr = tracks[name]
        m = mat_from_flat(tr['transform'][min(i, len(tr['transform']) - 1)])
        parent = arm_parent(name)
        out[name] = m if parent is None else out[parent] @ m
    return out

_PARENT = {}
def arm_parent(name):
    if not _PARENT:
        for b in rig().data.bones:
            _PARENT[b.name] = b.parent.name if b.parent else None
    return _PARENT[name]

def encode_pose(arm):
    """Current pose -> joint name -> flat row-major matrix (as stored in the file)."""
    pm = {b.name: b.matrix.copy() for b in arm.pose.bones}
    out = {}
    for name in ORDER:
        p = arm_parent(name)
        m = pm[name] if p is None else pm[p].inverted() @ pm[name]
        out[name] = [round(x, 6) for row in m for x in row]
    return out


def _stick_setup():
    coll = bpy.context.scene.collection
    cur = bpy.data.curves.get('stick') or bpy.data.curves.new('stick', 'CURVE')
    cur.dimensions = '3D'; cur.bevel_depth = 0.022; cur.bevel_resolution = 2
    obj = bpy.data.objects.get('stick')
    if obj is None:
        obj = bpy.data.objects.new('stick', cur); coll.objects.link(obj)
    return cur

def draw_stick(arm):
    """Draw the current pose as lines between joint heads (Workbench-renderable stick figure)."""
    cur = _stick_setup()
    cur.splines.clear()
    lines = [('Root', 'Torso'), ('Torso', 'Chest'), ('Chest', 'Head'), ('Root', 'Thigh_R'), ('Thigh_R', 'Leg_R'),
             ('Root', 'Thigh_L'), ('Thigh_L', 'Leg_L'), ('Chest', 'Arm_R'), ('Arm_R', 'Hand_R'), ('Hand_R', 'Tool_R'),
             ('Chest', 'Arm_L'), ('Arm_L', 'Hand_L'), ('Hand_L', 'Tool_L')]
    tails = {'Leg_R': 'Tool_R', 'Leg_L': 'Tool_L'}
    P = {b.name: (arm.matrix_world @ b.head).copy() for b in arm.pose.bones}
    for a, b in lines:
        sp = cur.splines.new('POLY'); sp.points.add(1)
        for pt, v in zip(sp.points, (P[a], P[b])):
            pt.co = (v.x, v.y, v.z, 1)
    # feet: shin end = Leg head + (Leg tail direction)
    for leg in ('Leg_R', 'Leg_L'):
        pb = arm.pose.bones[leg]
        sp = cur.splines.new('POLY'); sp.points.add(1)
        for pt, v in zip(sp.points, (arm.matrix_world @ pb.head, arm.matrix_world @ pb.tail)):
            pt.co = (v.x, v.y, v.z, 1)
    # head marker
    sp = cur.splines.new('POLY'); sp.points.add(1)
    h = P['Head']
    for pt, v in zip(sp.points, (h, h + Vector((0, 0, 0.16)))):
        pt.co = (v.x, v.y, v.z, 1)

def setup_render(w=320, h=420):
    scn = bpy.context.scene
    scn.render.engine = 'BLENDER_WORKBENCH'
    scn.render.resolution_x, scn.render.resolution_y = w, h
    scn.render.resolution_percentage = 100
    scn.display.shading.light = 'FLAT'; scn.display.shading.color_type = 'SINGLE'
    scn.display.shading.single_color = (0.95, 0.85, 0.3)
    scn.world = scn.world or bpy.data.worlds.new('w')
    scn.world.color = (0.12, 0.13, 0.16)
    for o in bpy.data.objects:
        if o.type in ('MESH', 'ARMATURE'): o.hide_render = True

def camera(view='front'):
    scn = bpy.context.scene
    cam = bpy.data.objects.get('cam') or bpy.data.objects.new('cam', bpy.data.cameras.new('cam'))
    if cam.name not in scn.collection.objects: scn.collection.objects.link(cam)
    scn.camera = cam
    pos = {'front': (0.0, -3.4, 1.0), 'side': (3.4, 0.0, 1.0), 'three': (2.4, -2.6, 1.2)}[view]
    cam.location = pos
    cam.data.type = 'ORTHO'; cam.data.ortho_scale = 2.4
    cam.rotation_euler = (Vector((0, 0, 0.9)) - Vector(pos)).to_track_quat('-Z', 'Y').to_euler()


# ------------------------------------------------------------------ authoring by limb direction (armature space)
def rest_axes(arm):
    """Per bone: rest head, rest 3x3 orientation, and the unit direction of its Y axis (armature space)."""
    out = {}
    for b in arm.data.bones:
        m3 = b.matrix_local.to_3x3()
        out[b.name] = (b.head_local.copy(), m3.copy(), (m3 @ Vector((0, 1, 0))).normalized(), b.parent.name if b.parent else None)
    return out

def pose_by_directions(arm, dirs, root_offset=(0, 0, 0), twists=None):
    """dirs: bone -> desired armature-space direction of that bone's Y axis (missing bones keep their rest direction).
    Returns joint -> armature-space pose matrix, and applies it to the armature."""
    twists = twists or {}
    rest = rest_axes(arm)
    heads, mats = {}, {}
    from mathutils import Matrix
    for name in ORDER:
        head0, m3, axis, parent = rest[name]
        target = Vector(dirs[name]).normalized() if name in dirs else axis
        d = axis.rotation_difference(target).to_matrix()
        if name in twists:
            d = Matrix.Rotation(math.radians(twists[name]), 3, target) @ d
        if parent is None:
            head = head0 + Vector(root_offset)
        else:
            phead0, _, _, _ = rest[parent]
            head = heads[parent] + mats[parent].to_3x3() @ (head0 - phead0) if False else heads[parent] + Dp[parent] @ (head0 - phead0)
        heads[name] = head
        Dp_local = d
        Dp[name] = Dp_local
        m = (d @ m3).to_4x4(); m.translation = head
        mats[name] = m
    return mats

Dp = {}
