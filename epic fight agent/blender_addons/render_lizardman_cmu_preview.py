"""Render playable frame sequences when Blender lacks an FFmpeg encoder."""
import json
from pathlib import Path
import bpy
from mathutils import Vector

root=Path(__file__).resolve().parents[1]
out=root/'sampleRig/lizardman_cmu_playback'
out.mkdir(exist_ok=True)
scene=bpy.context.scene;rig=bpy.data.objects['Lizardman']
bpy.data.objects['Cube'].hide_render=True
camera=bpy.data.objects['Camera'];scene.camera=camera
camera.location=(3,-5,2.35)
camera.rotation_euler=(Vector((0,0,1))-camera.location).to_track_quat('-Z','Y').to_euler()
camera.data.type='ORTHO';camera.data.ortho_scale=3.5
scene.render.engine='BLENDER_WORKBENCH'
scene.render.resolution_x=512;scene.render.resolution_y=512
scene.render.resolution_percentage=100
scene.render.image_settings.file_format='PNG'
clips={}
for name in ('mocap.boxing_two_strikes','mocap.front_kick_right'):
    action=bpy.data.actions[name]
    rig.animation_data.action=action
    if action.slots:rig.animation_data.action_slot=action.slots[0]
    folder=out/name.replace('.','_')
    folder.mkdir(exist_ok=True)
    files=[]
    for frame in range(int(action.frame_range[0]),int(action.frame_range[1])+1):
        scene.frame_set(frame)
        file=folder/f'{frame:03}.png'
        scene.render.filepath=str(file)
        bpy.ops.render.render(write_still=True)
        files.append(str(file.relative_to(out)).replace('\\','/'))
    clips[name]=files
html='''<!doctype html><html lang="ko"><meta charset="utf-8"><title>Lizardman CMU mocap preview</title>
<style>body{background:#10151b;color:#e7edf3;font:16px system-ui;max-width:800px;margin:2rem auto}
img{width:512px;max-width:100%;display:block;background:#28282a}button,select,input{margin:.6rem .4rem .6rem 0}
</style><h1>Lizardman CMU 모션캡처 테스트</h1>
<p>24fps 재생. 타격·차기 구간을 선택해 프레임별로 확인하세요.</p>
<select id="clip"></select><button id="play">일시정지</button><span id="counter"></span>
<input id="scrub" type="range" min="0" value="0" style="display:block;width:512px;max-width:100%">
<img id="frame" alt="Animation frame"><script>
const clips=CLIPS;const names=Object.keys(clips);const clip=document.getElementById('clip');
const img=document.getElementById('frame');const scrub=document.getElementById('scrub');
const counter=document.getElementById('counter');const button=document.getElementById('play');
names.forEach(n=>clip.add(new Option(n,n)));let i=0,playing=true;
function draw(){let a=clips[clip.value];i=(i+a.length)%a.length;img.src=a[i];scrub.max=a.length-1;
scrub.value=i;counter.textContent=`${i+1}/${a.length}`}
clip.onchange=()=>{i=0;draw()};scrub.oninput=()=>{i=+scrub.value;draw()};
button.onclick=()=>{playing=!playing;button.textContent=playing?'일시정지':'재생'};
setInterval(()=>{if(playing){i++;draw()}},1000/24);draw();</script></html>'''
(out/'index.html').write_text(html.replace('CLIPS',json.dumps(clips)),encoding='utf-8')
print('PLAYBACK',out/'index.html')
