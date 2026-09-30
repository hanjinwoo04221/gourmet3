"""Draw an orthographic contact sheet from exported Lizardman pose data."""
import json
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parent.parent
data = json.loads((ROOT/'sampleRig/lizardman_feral_pack_v2_validation.json').read_text(encoding='utf-8'))
W, H, M = 1500, 900, 40
img = Image.new('RGB', (W, H), '#10151b')
d = ImageDraw.Draw(img)
font = ImageFont.load_default()
titles = {'feral.pounce_bite':'POUNCE BITE', 'feral.razor_claw_chain':'RAZOR CLAW CHAIN', 'feral.tail_reversal':'TAIL REVERSAL'}
accent = ['#ff9858', '#69d2e7', '#d7a4ff']
skip = {'Tool_L', 'Tool_R'}

for row, (name, poses) in enumerate(data['poses'].items()):
    y0, cell_h = 80 + row*270, 230
    d.text((M, y0-30), titles[name], fill=accent[row], font=font)
    all_pts=[]
    for pose in poses:
        for n,j in pose['joints'].items():
            if n not in skip:
                all_pts += [(j['head'][0]+.6*j['head'][1],j['head'][2]+.1*j['head'][1]),
                            (j['tail'][0]+.6*j['tail'][1],j['tail'][2]+.1*j['tail'][1])]
    xs=[p[0] for p in all_pts]; zs=[p[1] for p in all_pts]
    span=max(max(xs)-min(xs),max(zs)-min(zs),.01); scale=min(210/span,55)
    for col, pose in enumerate(poses):
        cx = 150 + col*285
        pts=pose['joints']
        def proj(v):
            x, z = v[0]+.6*v[1], v[2]+.1*v[1]
            return (cx+x*scale, y0+cell_h-(z-min(zs))*scale)
        for parent, child in pose['edges']:
            if parent in skip or child in skip: continue
            a=proj(pts[parent]['head']); b=proj(pts[child]['head'])
            color = '#f06d52' if 'Tail' in child or child=='Jaw' else '#dce6ed'
            d.line((a,b),fill=color,width=3)
        for n,j in pts.items():
            if n in skip: continue
            a=proj(j['head']); b=proj(j['tail'])
            color = '#f06d52' if 'Tail' in n or n=='Jaw' else accent[row]
            d.line((a,b),fill=color,width=4)
        d.text((cx-28,y0+cell_h+5),f"F{pose['frame']}",fill='#aab7c4',font=font)
    d.line((M,y0+cell_h+28,W-M,y0+cell_h+28),fill='#27313b',width=1)

out=ROOT/'sampleRig/lizardman_feral_pack_v2_preview.png'
img.save(out)
print(out)
