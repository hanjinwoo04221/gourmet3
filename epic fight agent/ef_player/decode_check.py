import sys, json, bpy
sys.path.insert(0, 'C:/Users/hanjw/Downloads/gourmet2-main/gourmet2-main/epic fight agent/ef_player')
import efrig
arm = efrig.rig()
name = sys.argv[sys.argv.index('--') + 1]
clip = json.load(open('C:/Users/hanjw/Downloads/gourmet2-main/gourmet2-main/src/main/resources/assets/gourmet2/animmodels/animations/skill/%s.json' % name))
n = len(clip['animation'][0]['time'])
efrig.setup_render(); efrig.camera('three')
scn = bpy.context.scene
keys = sorted({0, n // 3, 2 * n // 3, n - 1})
for k in keys:
    efrig.apply_matrices(arm, efrig.decode_frame(clip, k)); efrig.draw_stick(arm)
    scn.render.filepath = 'C:/Users/hanjw/Downloads/gourmet2-main/gourmet2-main/epic fight agent/ef_player/dec_%s_%02d.png' % (name, k)
    bpy.ops.render.render(write_still=True)
print('DONE', keys)
