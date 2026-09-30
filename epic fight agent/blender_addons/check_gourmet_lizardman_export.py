import json,math
from pathlib import Path
project=Path('C:/Users/hanjw/Downloads/gourmet2-main/gourmet2-main')
root=Path(__file__).resolve().parent.parent
old=json.loads((project/'src/main/resources/assets/gourmet2/animations/entity/lizardman.animation.json').read_text(encoding='utf-8'))
new=json.loads((root/'sampleRig/gourmet2_lizardman_all_refined.json').read_text(encoding='utf-8'))
assert set(old['animations'])==set(new['animations'])
def vector(v):
    if isinstance(v,dict):v=v.get('vector',v.get('post'))
    return v
summary={}
for name,clip in new['animations'].items():
    before=old['animations'][name]
    assert clip!=before,name+' unchanged'
    assert clip['loop']==before['loop'],name+' loop flag changed'
    if name not in {'animation.lizardman.duck_rake_bite','animation.lizardman.hop_tail_claw'}:
        assert abs(clip['animation_length']-before['animation_length'])<.0001,name+' duration changed'
    keys=0;gap=0.
    for channels in clip['bones'].values():
        for frames in channels.values():
            entries=sorted((float(t),vector(v)) for t,v in frames.items())
            keys+=len(entries)
            for _,v in entries:
                assert all(math.isfinite(float(x)) for x in v),name+' nonfinite'
            if clip['loop'] and entries:
                gap=max(gap,max(abs(float(a)-float(b)) for a,b in zip(entries[0][1],entries[-1][1])))
    if clip['loop']:assert gap<.01,(name,gap)
    summary[name.replace('animation.lizardman.','')]={'seconds':clip['animation_length'],
                                                       'keys':keys,'loop_gap':round(gap,5)}
out=root/'sampleRig/lizardman_all_export_check.json'
out.write_text(json.dumps(summary,indent=2),encoding='utf-8')
print('EXPORT_CHECK',len(summary),'clips changed; loop seams and duration checked')
