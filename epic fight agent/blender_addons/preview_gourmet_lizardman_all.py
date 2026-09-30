import sys
from pathlib import Path
project=Path('C:/Users/hanjw/Downloads/gourmet2-main/gourmet2-main')
sys.path.insert(0,str(project/'tools/blender'))
import lizardman_preview as pv
out=Path(__file__).resolve().parent.parent/'sampleRig/all_reauthored_preview'
pv.RES=(260,300)
pv.freeze_framing()
images=[]
for clip,frames in {'idle':[0,12,24,36],'walk':[0,9,18,27],
                    'run':[0,5,10,15],'leap':[0,6,10,16],
                    'claw_left':[0,4,7,12],'tail_slam':[0,4,8,13]}.items():
    images+=pv.render_clip(clip,frames,outdir=str(out),azimuth_deg=48,tag='new',samples=8)
print('SHEET',pv.contact_sheet(images,str(out/'overview.png'),cols=4))
