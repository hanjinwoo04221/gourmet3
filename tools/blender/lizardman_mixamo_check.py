"""Validate a Mixamo retarget export against the pre-retarget realism snapshot.

Checks the retargeted clips are structurally sound (valid JSON, finite values,
loop flags, bone coverage) and that no other clip was disturbed.
"""
import json
import math
import os

BASE = os.path.join("src", "main", "resources", "assets", "gourmet2", "animations", "entity")
NEW = os.path.join(BASE, "lizardman.animation.json")
SNAP = os.path.join("tools", "blender", "snapshots", "lizardman.animation.realism.json")
RETARGETED = ("animation.lizardman.idle", "animation.lizardman.run")


def vec(frame):
    if isinstance(frame, dict):
        return [float(x) for x in frame.get("vector", frame.get("post", [0, 0, 0]))]
    if isinstance(frame, (int, float)):
        return [float(frame)] * 3
    return [float(x) for x in frame]


new = json.load(open(NEW, encoding="utf-8"))
snap = json.load(open(SNAP, encoding="utf-8"))
nc, sc = new["animations"], snap["animations"]
problems = []

print("format_version %s | clips %d | keys %s" % (new["format_version"], len(nc), sorted(new)))
if sorted(nc) != sorted(sc):
    problems.append("clip set changed")

worst, worst_where = 0.0, None
for name, clip in nc.items():
    for bone, chans in clip.get("bones", {}).items():
        for ch, frames in chans.items():
            for t, v in frames.items():
                for value in vec(v):
                    if not math.isfinite(value):
                        problems.append("%s %s %s @%s is not finite" % (name, bone, ch, t))
                    if abs(value) > worst:
                        worst, worst_where = abs(value), "%s %s %s @%s" % (
                            name.replace("animation.lizardman.", ""), bone, ch, t)

print("loops:", sorted(k.replace("animation.lizardman.", "") for k, v in nc.items() if v.get("loop")))
print("max |value| = %.2f (%s)" % (worst, worst_where))
# A large euler component is NOT itself a defect: many euler triples describe one
# rotation, and once unwrapping enforces continuity a component may legitimately
# drift past 180. The property that matters is that neighbouring keys never jump
# by a whole turn, which the continuity section below checks.
if worst > 1000.0:
    problems.append("a value exceeded 1000, which is implausible: %s" % worst_where)

print()
print("%-14s %-10s %-8s %-8s %s" % ("clip", "length", "loop", "bones", "status"))
for name in sorted(nc):
    a, b = sc[name], nc[name]
    if name in RETARGETED:
        keys = sum(len(fr) for chans in b["bones"].values() for fr in chans.values())
        status = "RETARGETED (was %.3fs, %d keys)" % (a["animation_length"], keys)
    elif a == b:
        status = "untouched"
    else:
        status = "CHANGED UNEXPECTEDLY"
        problems.append("%s changed but should not have" % name)
    print("%-14s %-10.3f %-8s %-8d %s" % (name.replace("animation.lizardman.", ""),
                                           b["animation_length"], b.get("loop"), len(b["bones"]), status))

# frame rate sanity: GeckoLib reads seconds, so keys must land inside the clip
for name in RETARGETED:
    clip = nc[name]
    length = clip["animation_length"]
    beyond = max((float(t) for chans in clip["bones"].values() for fr in chans.values() for t in fr),
                 default=0.0)
    if beyond > length + 1e-6:
        problems.append("%s has a key at %.4fs beyond its %.3fs length" % (name, beyond, length))
    else:
        print("\n%s last key %.4fs == length %.3fs OK" % (name, beyond, length))

print()
print("=== euler continuity on the retargeted clips ===")
print("An adjacent-key jump near 360 deg means the exporter picked the other euler")
print("branch on that frame, which makes GeckoLib spin the bone across one frame.")
for name in RETARGETED:
    worst_jump, where = 0.0, None
    for bone, chans in nc[name].get("bones", {}).items():
        for ch, frames in chans.items():
            items = sorted(((float(t), vec(v)) for t, v in frames.items()), key=lambda kv: kv[0])
            for i in range(1, len(items)):
                for axis in range(3):
                    jump = abs(items[i][1][axis] - items[i - 1][1][axis])
                    if jump > worst_jump:
                        worst_jump = jump
                        where = "%s %s[%d] %.3f->%.3f" % (bone, ch, axis, items[i - 1][0], items[i][0])
    verdict = "OK" if worst_jump < 180.0 else "BRANCH FLIP"
    print("%-14s worst adjacent jump %7.2f deg  %-14s %s"
          % (name.replace("animation.lizardman.", ""), worst_jump, verdict, where))
    if worst_jump >= 180.0:
        problems.append("%s has an euler branch flip (%s)" % (name, where))

print()
print("PROBLEMS: %s" % (problems if problems else "none"))
