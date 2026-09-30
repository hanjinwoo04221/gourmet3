import json
from pathlib import Path
from PIL import Image, ImageDraw

base=Path(__file__).resolve().parent.parent
poses=json.loads((base/'acrobatic_poses.json').read_text())
image=Image.new('RGB',(1500,980),'#0d1722');draw=ImageDraw.Draw(image)
draw.text((35,20),'EPIC FIGHT / ACROBATIC ATTACK PACK',fill='white',font_size=28)
for i,pose in enumerate(poses):
    row=i//3;col=i%3;cx=250+col*500;cy=335+row*305
    def project(v):
        x,y,z=v;return (cx+145*(.86*x+.50*y),cy-150*z+38*(.50*x-.86*y))
    draw.line([project((-1,0,0)),project((1,0,0))],fill='#536477',width=1)
    draw.line([project((0,-1,0)),project((0,1,0))],fill='#536477',width=1)
    for name,(head,tail) in pose['bones'].items():
        color='#ffad5c' if name.endswith('_R') else '#66d7ef'
        draw.line([project(head),project(tail)],fill=color,width=5)
    label=pose['action'].replace('Acro_','').replace('_',' ')
    draw.text((cx-210,cy-255),f'{label}  /  F{pose["frame"]}',fill='white',font_size=19)
image.save(base/'acrobatic_attacks_preview.png')
