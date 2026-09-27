"""Correctly-labelled axis audit.

JSON key vector is [fx, fy, fz] = model-space Euler; the exporter maps it as
    Blender X = fx   -> pitch  (limb swings back/forward)
    Blender Z = -fy  -> yaw    (turn left/right, spine twist, tail side sway)
    Blender Y = fz   -> roll   (lateral tilt / weight shift)
so JSON index 0 = pitch, 1 = yaw, 2 = roll.
"""
import collections
import json
import os

P = os.path.join("src", "main", "resources", "assets", "gourmet2", "animations", "entity", "lizardman.animation.json")
clips = json.load(open(P, encoding="utf-8"))["animations"]
AXES = ("pitch", "yaw", "roll")


def vec(frame):
    if isinstance(frame, dict):
        return [float(x) for x in frame.get("vector", frame.get("post", [0, 0, 0]))]
    if isinstance(frame, (int, float)):
        return [float(frame)] * 3
    return [float(x) for x in frame]


print("Per-clip amplitude (deg) per axis, summed as max-min over the clip.")
print("flag = axis is essentially dead (<1.0 deg) but you would expect motion.\n")
print("%-14s %-6s %8s %8s %8s   %s" % ("clip", "loop", "pitch", "yaw", "roll", "bones with dead roll"))
for name in sorted(clips):
    clip = clips[name]
    short = name.replace("animation.lizardman.", "")
    trav = collections.defaultdict(lambda: [0.0, 0.0, 0.0])
    for bone, chans in clip.get("bones", {}).items():
        frames = chans.get("rotation")
        if not frames:
            continue
        vals = [vec(v) for v in frames.values()]
        for a in range(3):
            col = [v[a] for v in vals]
            trav[bone][a] = max(trav[bone][a], max(col) - min(col))
    tot = [max((v[a] for v in trav.values()), default=0.0) for a in range(3)]
    dead_roll = sorted(b for b, v in trav.items() if v[2] < 1.0)
    print("%-14s %-6s %8.1f %8.1f %8.1f   %s" % (short, clip.get("loop"), tot[0], tot[1], tot[2], ",".join(dead_roll[:8])))
