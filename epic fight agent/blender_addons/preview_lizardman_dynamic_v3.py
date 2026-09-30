import sys, os
from pathlib import Path
project=Path('C:/Users/hanjw/Downloads/gourmet2-main/gourmet2-main')
sys.path.insert(0,str(project/'tools/blender'))
import lizardman_preview as pv
out=Path(__file__).resolve().parent.parent/'sampleRig/dynamic_v3_preview'
pv.RES=(240,290)
pv.freeze_framing()
pv._FRAMING['span']=pv._FRAMING['span']*1.35
pv._FRAMING['center']=pv._FRAMING['center']+pv.Vector((0,-.1,.05))
which=os.environ.get('CLIPS','claw_left,bite,tail_slam,leap').split(',')
plan={'idle':[0,7,14,27],'walk':[0,9,18,27],'run':[0,5,10,15],'leap':[0,6,9,13,16,19],
 'claw_left':[0,3,5,7,9,12],'tail_slam':[0,4,7,9,13],'bite':[0,3,5,6,8,10],'jump':[0,5,8,11,14,16],
 'spike':[0,4,7,9],'launcher':[0,4,7,9],'skill_thrust':[0,3,6,8],'rise_left':[0,4,6,8],
 'flurry_left':[0,2,4,6,8],'guard':[0,3,6],'dodge':[0,3,7],'skill_charge':[0,5,9],
 'duck_rake_bite':[0,4,8,12,17,20,22],'hop_tail_claw':[0,4,7,10,12,18,26],'leap_charge':[0,4,6,9]}
images=[]
for clip in which:
    images+=pv.render_clip(clip,plan[clip],outdir=str(out),azimuth_deg=48,tag='v3',samples=8)
print('SHEET',pv.contact_sheet(images,str(out/(os.environ.get('SHEET','sheet')+'.png')),cols=6))
