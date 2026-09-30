"""Checks for Epic Fight 1.21.1 animation *pose* JSON files.

This validates the resource shape before loading. It cannot certify that a
matrix matches an armature, that contacts work, or that combat phases align.
"""

import argparse
import json
import math
from pathlib import Path


def validate_clip(data, joint_names=None):
    """Return errors and warnings for one Epic Fight animation resource.

    ``joint_names`` is an optional iterable from the target armature, used to
    catch tracks that the Java loader would otherwise silently skip.
    """
    errors, warnings = [], []
    if not isinstance(data, dict):
        return {"errors": ["root must be a JSON object"], "warnings": []}
    fmt = data.get("format", "MATRIX")
    if fmt not in ("MATRIX", "ATTRIBUTES"):
        errors.append("format must be MATRIX or ATTRIBUTES")
    tracks = data.get("animation")
    if not isinstance(tracks, list) or not tracks:
        errors.append("animation must be a nonempty array")
        return {"errors": errors, "warnings": warnings}
    known = set(joint_names) if joint_names is not None else None
    seen = set()
    for index, track in enumerate(tracks):
        where = f"animation[{index}]"
        if not isinstance(track, dict):
            errors.append(f"{where} must be an object")
            continue
        name = track.get("name")
        if not isinstance(name, str) or not name:
            errors.append(f"{where}.name must be a nonempty string")
        else:
            if name in seen:
                errors.append(f"{where}.name duplicates {name!r}")
            seen.add(name)
            if known is not None and name not in known and name != "Coord":
                warnings.append(f"{where}: {name!r} is absent from target armature")
        times, transforms = track.get("time"), track.get("transform")
        if not isinstance(times, list) or not isinstance(transforms, list):
            errors.append(f"{where} requires time and transform arrays")
            continue
        if not times or len(times) != len(transforms):
            errors.append(f"{where}: time and transform must have equal nonzero lengths")
        previous = -1.0
        for key, (time, transform) in enumerate(zip(times, transforms)):
            at = f"{where}[{key}]"
            if not _finite_number(time) or time < 0 or time <= previous:
                errors.append(f"{at}: times must be finite, nonnegative, strictly increasing seconds")
            else:
                previous = time
            if fmt == "MATRIX":
                if not _numeric_vector(transform, 16):
                    errors.append(f"{at}: MATRIX transform must have 16 finite numbers")
                elif any(abs(a - b) > 1e-4 for a, b in zip(transform[12:], (0, 0, 0, 1))):
                    warnings.append(f"{at}: matrix last row is not the usual affine [0,0,0,1]")
            elif fmt == "ATTRIBUTES":
                if not isinstance(transform, dict) or any(
                    not _numeric_vector(transform.get(field), size)
                    for field, size in (("loc", 3), ("rot", 4), ("sca", 3))
                ):
                    errors.append(f"{at}: ATTRIBUTES requires finite loc[3], rot[4], sca[3]")
    if tracks and isinstance(tracks[0], dict) and tracks[0].get("name") not in ("Root", "Coord"):
        warnings.append("first track is not Root or Coord; loader applies root correction to the first recognized joint")
    return {"errors": errors, "warnings": warnings}


def _finite_number(value):
    return isinstance(value, (int, float)) and not isinstance(value, bool) and math.isfinite(value)


def _numeric_vector(value, size):
    return isinstance(value, list) and len(value) == size and all(_finite_number(x) for x in value)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("files", nargs="+", type=Path)
    arguments = parser.parse_args()
    failed = False
    for path in arguments.files:
        try:
            report = validate_clip(json.loads(path.read_text(encoding="utf-8")))
        except (OSError, UnicodeError, json.JSONDecodeError) as exc:
            report = {"errors": [str(exc)], "warnings": []}
        print(f"{path}: {len(report['errors'])} errors, {len(report['warnings'])} warnings")
        for kind in ("errors", "warnings"):
            for message in report[kind]:
                print(f"  {kind[:-1]}: {message}")
        failed |= bool(report["errors"])
    return int(failed)


if __name__ == "__main__":
    raise SystemExit(main())
