"""Draw key pose silhouettes for the limb-led test actions."""
import json,sys
from pathlib import Path
from PIL import Image,ImageDraw,ImageFont

ROOT=Path(__file__).resolve().parent.parent
stem=sys.argv[1] if len(sys.argv)>1 else 'lizardman_lateral_shoulder'
data=json.loads((ROOT/f'sampleRig/{stem}_validation.json').read_text(encoding='utf-8'))
W,H=1480,640
img=Image.new('RGB',(W,H),'#10151b');d=ImageDraw.Draw(img);font=ImageFont.load_default()
colors=['#fa9b5f','#83d4e8']
for row,(name,stats) in enumerate(data.items()):
    poses=stats['poses'];y0=60+row*300
    d.text((35,y0-32),name,fill=colors[row],font=font)
    for col,pose in enumerate(poses):
        bones=pose['bones'];cx=130+col*265
        def xy(v):return (cx+(v[0]+.6*v[1])*75,y0+235-(v[2]+.1*v[1])*125)
        for n,points in bones.items():
            parent=None
            if n.startswith('Tail'): parent={'Tail1':'Root','Tail2':'Tail1','Tail3':'Tail2'}[n]
            elif n in ('Torso','Thigh_L','Thigh_R'):parent='Root'
            elif n=='Chest':parent='Torso'
            elif n in ('Head','Shoulder_L','Shoulder_R'):parent='Chest'
            elif n=='Jaw':parent='Head'
            elif n.startswith(('Arm_','Elbow_')):parent='Shoulder_'+n[-1] if n.startswith('Arm_') else 'Arm_'+n[-1]
            elif n.startswith('Hand_'):parent='Arm_'+n[-1]
            elif n.startswith(('Knee_','Leg_')):parent='Thigh_'+n[-1]
            elif n.startswith('Foot_'):parent='Leg_'+n[-1]
            elif n.startswith('Toe_'):parent='Foot_'+n.split('_')[1]
            if parent and parent in bones:d.line((xy(bones[parent]['head']),xy(points['head'])),fill='#46515e',width=2)
            color=colors[row] if n.startswith(('Shoulder','Arm','Elbow','Hand','Thigh','Knee','Leg','Foot','Toe')) else '#e7edf3'
            d.line((xy(points['head']),xy(points['tail'])),fill=color,width=4)
        d.text((cx-22,y0+242),f"F{pose['frame']}",fill='#aab7c4',font=font)
    d.line((35,y0+277,W-35,y0+277),fill='#293540')
out=ROOT/f'sampleRig/{stem}_preview.png'
img.save(out);print(out)
