import sys, os
from pathlib import Path
project=Path('C:/Users/hanjw/Downloads/gourmet2-main/gourmet2-main')
sys.path.insert(0,str(project/'tools/blender'))
import lizardman_preview as pv
out=Path(__file__).resolve().parent.parent/'sampleRig/acrobatic_v4_preview'
pv.RES=(240,290)
pv.freeze_framing()
pv._FRAMING['span']=pv._FRAMING['span']*1.35
pv._FRAMING['center']=pv._FRAMING['center']+pv.Vector((0,-.1,.05))
which=os.environ.get('CLIPS','claw_left,bite,tail_slam,leap').split(',')
plan={'idle':[0,10,21,42],'walk':[0,10,20,30],'run':[0,5,10,15],
 'claw_right':[0,7,10,14,18],'claw_left':[0,7,10,14,18],'flurry_right':[0,4,6,12],'rise_right':[0,7,10,14,18],
 'bite':[0,7,10,14,20],'tail_slam':[0,7,14,17,21,28],'launcher':[0,7,10,12,15,18,22],'spike':[0,4,7,9,12,16],
 'leap_charge':[0,6,9,12],'leap':[0,8,11,15,19,22],'jump':[0,6,9,13,17,19],'dodge':[0,4,7,10,13,15],
 'guard':[0,5,8,11],'skill_charge':[0,9,15,22],'skill_thrust':[0,5,8,10],
 'flash_step':[0,2,5,8,10],
 'wall_hold':[0,10,20,30],'ceiling_hang':[0,12,24,36],'ceiling_move':[0,6,12,18],
 'climb':[0,5,10,15],
 'run_claw_left':[0,3,6,10,12],'run_claw_right':[0,2,5,7,10],'run_bite':[0,4,6,9,12],'dash_spin':[0,4,7,10,14,18,21,26],'rise_punch':[0,4,7,10,13],
 'duck_rake_bite':[0,6,11,16,19,25,29],'hop_tail_claw':[0,10,14,17,20,32,35]}
images=[]
for clip in which:
    images+=pv.render_clip(clip,plan[clip],outdir=str(out),azimuth_deg=48,tag="v4",samples=8)
print('SHEET',pv.contact_sheet(images,str(out/(os.environ.get('SHEET','sheet')+'.png')),cols=6))
