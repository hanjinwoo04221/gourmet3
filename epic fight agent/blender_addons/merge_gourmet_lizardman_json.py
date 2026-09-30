import json
from pathlib import Path
project=Path('C:/Users/hanjw/Downloads/gourmet2-main/gourmet2-main')
workspace=Path(__file__).resolve().parent.parent
source=project/'src/main/resources/assets/gourmet2/animations/entity/lizardman.animation.json'
generated=workspace/'sampleRig/gourmet2_lizardman.animation.new.json'
original=json.loads(source.read_text(encoding='utf-8'))
new=json.loads(generated.read_text(encoding='utf-8'))
names=['animation.lizardman.duck_rake_bite','animation.lizardman.hop_tail_claw']
for n in names:
    assert n not in original['animations']
    assert n in new['animations']
    original['animations'][n]=new['animations'][n]
output=workspace/'sampleRig/gourmet2_lizardman.animation.merged.json'
output.write_text(json.dumps(original,indent=1,ensure_ascii=False)+'\n',encoding='utf-8')
assert all(original['animations'][n]==json.loads(source.read_text(encoding='utf-8'))['animations'][n]
           for n in original['animations'] if n not in names)
print('MERGED',len(original['animations']),str(output))
