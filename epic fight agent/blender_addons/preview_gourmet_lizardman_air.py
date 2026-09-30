import sys
from pathlib import Path
project=Path('C:/Users/hanjw/Downloads/gourmet2-main/gourmet2-main')
sys.path.insert(0,str(project/'tools/blender'))
import lizardman_preview as pv
out=Path(__file__).resolve().parent.parent/'sampleRig/all_reauthored_preview'
pv.RES=(300,360)
pv.freeze_framing()
original=pv.setup_camera
def wide(azimuth_deg=35.0,elevation_deg=12.0,margin=1.15):
    return original(azimuth_deg,elevation_deg,1.65)
pv.setup_camera=wide
images=[]
for clip,frames in {'jump':[0,5,9,14,19],'leap':[0,6,10,16,22],
                    'hop_tail_claw':[0,8,12,17,26]}.items():
    images+=pv.render_clip(clip,frames,outdir=str(out),azimuth_deg=55,tag='wide',samples=8)
print('SHEET',pv.contact_sheet(images,str(out/'air_actions.png'),cols=5))
