import json
from pathlib import Path
from PIL import Image, ImageDraw

base=Path(__file__).resolve().parent.parent
poses=json.loads((base/'capoeira_poses.json').read_text())
fig=Image.new('RGB',(1600,860),'#101923')
draw=ImageDraw.Draw(fig)
draw.text((35,20),'CAPOEIRA / MEIA LUA DE COMPASSO | RIGHT LIMBS: ORANGE',fill='white',font_size=25)
for i,pose in enumerate(poses):
    cx=200+(i%4)*400;cy=405+(i//4)*405
    def project(v):
        x,y,z=v
        return (cx+130*(.9*x+.44*y),cy-155*z+35*(.44*x-.9*y))
    draw.line([project((-1,0,0)),project((1,0,0))],fill='#647080',width=1)
    draw.line([project((0,-1,0)),project((0,1,0))],fill='#647080',width=1)
    for name,(head,tail) in pose['bones'].items():
        color='#ffab57' if name.endswith('_R') else '#70d5ed'
        draw.line([project(head),project(tail)],fill=color,width=5)
        x,y=project(head);draw.ellipse((x-4,y-4,x+4,y+4),fill=color)
    draw.text((cx-60,cy-340),f"FRAME {pose['frame']:03}",fill='white',font_size=22)
fig.save(base/'capoeira_preview.png')
