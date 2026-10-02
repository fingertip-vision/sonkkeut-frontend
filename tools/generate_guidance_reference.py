"""Execute pinned Python guidance core; produce Kotlin comparison cases.

Usage: python tools/generate_guidance_reference.py app/build/t08-reference
The reference package must be fetched from ba55be0248c709ab9222d40c100dcb9d61362a50.
No camera/model inference is run.
"""
import hashlib
import json
import math
from pathlib import Path
import random
import sys
from dataclasses import asdict

root = Path(sys.argv[1]).resolve()
sys.path.insert(0, str(root))
from sonkkeut_vision.contracts import Point, Box, Element
from sonkkeut_vision.guidance import GuidanceEngine, direction_for_delta

target = dict(id="t", kind="button", box=[.4, .4, .6, .6], conf=.95)
def sample(t, point=(.5, .5), **overrides):
    return dict(now=t, point=point, raw=point, target=target, keyframe=1, hand="h", confidence=.95, **overrides)
cases = []
cases.append(("dwell-press-dropout-reset", [sample(i/10) for i in range(5)] + [sample(.5, None), sample(.6), dict(sample(.7), reset=True), sample(.8), sample(.9), sample(1.0)]))
cases.append(("raw-outside-and-gap", [dict(sample(0), raw=[.7,.5]), sample(.1), sample(.2), sample(.6), sample(.7), sample(.8), sample(.9)]))
cases.append(("identity-changes", [sample(0), sample(.1), dict(sample(.2), keyframe=2), sample(.3), dict(sample(.4), hand="new"), sample(.5), sample(.6), sample(.7), sample(.8), dict(sample(.9), keyframe=3)]))
cases.append(("invalid-input", [dict(sample(0), target=None), dict(sample(.1), target=dict(target, kind="price")), dict(sample(.2), target=dict(target, conf=.5)), dict(sample(.3), confidence=.5), sample(.4, (-.1,.5)), dict(sample(.5), keyframe=-1), dict(sample(.6), hand=" "), dict(sample(.7), confidence=1.5)]))
cases.append(("timestamp-rejection", [sample(0), sample(.1), sample(.1), sample(.05), sample(.2), sample(.3), sample(.4), sample(.5)]))
cases.append(("increasing-distance-recenter", [sample(i/10, (.39-i*.008,.5)) for i in range(35)]))
cases.append(("near-boundary", [sample(0, (.39,.5)), sample(.1,(.36,.5)), sample(.2,(.35,.5)), sample(.3,(.34,.5))]))
rng = random.Random(802)
cases.append(("seeded-observations", [sample(i/10, (rng.uniform(.1,.9),rng.uniform(.1,.9))) for i in range(160)]))
results = []
for name, steps in cases:
    engine = GuidanceEngine()
    rows = []
    for step in steps:
        if step.get("reset"): engine.reset_attempt()
        value = step["target"]
        element = None if value is None else Element(value["id"],value["kind"],Box(*value["box"]),value["conf"])
        try:
            decision = engine.update(None if step["point"] is None else Point(*step["point"]), element, step["now"],
                raw_point=None if step["raw"] is None else Point(*step["raw"]), finger_confidence=step["confidence"], keyframe_id=step["keyframe"],hand_id=step["hand"])
            expected = dict(asdict(decision), distance_band=decision.distance_band)
        except ValueError:
            expected = dict(error=True)
        rows.append(dict(input=step, expected=expected))
    results.append(dict(name=name, rows=rows))
directions = []
for i in range(32):
    angle = i * math.pi / 16
    dx, dy = math.cos(angle), math.sin(angle)
    directions.append(dict(dx=dx, dy=dy, expected=direction_for_delta(dx,dy)))
directions.append(dict(dx=0,dy=0,expected=None))
document = dict(revision="ba55be0248c709ab9222d40c100dcb9d61362a50", source_sha256=hashlib.sha256((root/"sonkkeut_vision/guidance.py").read_bytes()).hexdigest(), cases=results,directions=directions)
output = Path("app/src/test/resources/guidance-reference.json")
output.parent.mkdir(parents=True,exist_ok=True)
output.write_text(json.dumps(document,ensure_ascii=False,indent=2)+"\n",encoding="utf-8")
print(f"Python executed: {len(results)} traces, {sum(len(c['rows']) for c in results)} observations, {len(directions)} directions; {output}")
