"""Compare the exported lizardman.animation.json against its .bak (the original).

Checks that nothing was lost (clips, lengths, loop flags, easing) and reports
what the realism pass actually added.
"""
import collections
import json
import os

BASE = os.path.join("src", "main", "resources", "assets", "gourmet2", "animations", "entity")
NEW = os.path.join(BASE, "lizardman.animation.json")
OLD = NEW + ".bak"


def load(path):
    with open(path, encoding="utf-8") as f:
        return json.load(f)


def vec(frame):
    if isinstance(frame, dict):
        return [float(x) for x in frame.get("vector", frame.get("post", [0, 0, 0]))], frame.get("easing")
    if isinstance(frame, (int, float)):
        return [float(frame)] * 3, None
    return [float(x) for x in frame], None


old, new = load(OLD), load(NEW)
oc, nc = old["animations"], new["animations"]
problems = []

print("format_version  old=%s new=%s" % (old.get("format_version"), new.get("format_version")))
print("top-level keys  old=%s new=%s" % (sorted(old), sorted(new)))
print("clip count      old=%d new=%d" % (len(oc), len(nc)))
if sorted(oc) != sorted(nc):
    problems.append("clip set changed: %s" % (set(oc) ^ set(nc)))

print()
print("%-14s %7s %7s %6s %6s %8s %8s %9s" % ("clip", "len_old", "len_new", "loop", "chans", "roll_old", "roll_new", "keys"))
total_old = total_new = 0
for name in sorted(nc):
    a, b = oc[name], nc[name]
    if abs(a["animation_length"] - b["animation_length"]) > 1e-6:
        problems.append("%s: length %s -> %s" % (name, a["animation_length"], b["animation_length"]))
    if a.get("loop") != b.get("loop"):
        problems.append("%s: loop flag %s -> %s" % (name, a.get("loop"), b.get("loop")))

    def roll_span(clip):
        best = 0.0
        for bone, chans in clip.get("bones", {}).items():
            frames = chans.get("rotation")
            if not frames:
                continue
            col = [vec(v)[0][2] for v in frames.values()]
            best = max(best, max(col) - min(col))
        return best

    def count(clip):
        return sum(len(fr) for bones in clip.get("bones", {}).values() for fr in bones.values())

    def chans(clip):
        return sum(1 for bones in clip.get("bones", {}).values() for _ in bones)

    short = name.replace("animation.lizardman.", "")
    total_old += count(a)
    total_new += count(b)
    print("%-14s %7.2f %7.2f %6s %6s %8.1f %8.1f %9d" % (
        short, a["animation_length"], b["animation_length"], b.get("loop"),
        "%d>%d" % (chans(a), chans(b)), roll_span(a), roll_span(b), count(b) - count(a)))

    # bones/channels present before must still be present
    for bone, c in a.get("bones", {}).items():
        if bone not in b.get("bones", {}):
            problems.append("%s: lost bone %s" % (short, bone))
            continue
        for ch in c:
            if ch not in b["bones"][bone]:
                problems.append("%s: lost channel %s.%s" % (short, bone, ch))

print()
print("keys total      old=%d new=%d (+%.1f%%)" % (total_old, total_new, 100.0 * (total_new - total_old) / total_old))

# easing must survive on the one-shot clips
def easing_hist(clip):
    h = collections.Counter()
    for bones in clip.get("bones", {}).values():
        for chans in bones.values():
            for v in chans.values():
                h[vec(v)[1]] += 1
    return h

lost_easing = []
for name in sorted(nc):
    ho, hn = easing_hist(oc[name]), easing_hist(nc[name])
    for key, n in ho.items():
        if key and hn.get(key, 0) < n:
            lost_easing.append((name.replace("animation.lizardman.", ""), key, n, hn.get(key, 0)))
if lost_easing:
    problems.append("easing reduced: %s" % lost_easing[:5])

# nothing may have blown up by the ~57x of a degrees/radians slip
worst = 0.0
for name, clip in nc.items():
    for bones in clip.get("bones", {}).values():
        for chans in bones.values():
            for v in chans.values():
                worst = max(worst, max(abs(x) for x in vec(v)[0]))
print("max |channel value| in export: %.2f (deg for rotation / px for position)" % worst)

print()
if problems:
    print("PROBLEMS (%d):" % len(problems))
    for p in problems[:20]:
        print("  -", p)
else:
    print("OK: clip set, lengths, loop flags, bones, channels and easing all preserved.")
