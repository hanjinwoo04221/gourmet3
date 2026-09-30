"""Summarize timing diversity in selected checked-in Epic Fight clips."""
import collections
import json
from pathlib import Path

root = Path(__file__).resolve().parents[1] / 'epicfight-1.21.1/epicfight-1.21.1/src/main/resources/assets/epicfight/animmodels/animations'
clips = ('enderman/jump_kick', 'enderman/knee', 'enderman/rush_kick',
         'enderman/walk', 'biped/skill/roll_forward', 'biped/skill/battojutsu')
for name in clips:
    tracks = json.loads((root / (name + '.json')).read_text(encoding='utf-8'))['animation']
    counts = [len(track['time']) for track in tracks]
    shared = collections.Counter(round(time, 4) for track in tracks for time in track['time'])
    duration = max(max(track['time']) for track in tracks if track['time'])
    print(name, 'seconds=', duration, 'tracks=', len(tracks),
          'keys min/max=', min(counts), max(counts), 'unique times=', len(shared),
          'most shared=', shared.most_common(5))
