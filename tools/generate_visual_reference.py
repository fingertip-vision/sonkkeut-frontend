"""Run pinned Python core for A-task Kotlin parity cases; no model/camera execution.
Usage: python tools/generate_visual_reference.py app/build/t08-reference
"""
from dataclasses import asdict
import hashlib
import json
from pathlib import Path
import random
import sys

root=Path(sys.argv[1]).resolve()
sys.path.insert(0,str(root))
from sonkkeut_vision.contracts import Point, Box, Element, ScreenSnapshot
from sonkkeut_vision.geometry import Homography, PlaneTracker
from sonkkeut_vision.elements import Detection, process_detections
from sonkkeut_vision.tracking import HandObservation, FingertipTracker
from sonkkeut_vision.verification import ExpectedEffect, PressVerifier, screen_changed

def point(p): return None if p is None else Point(*p)
def box(b): return Box(*b)
def element(e): return Element(e['id'],e['kind'],box(e['box']),e['conf'],e.get('text'),e.get('price'))
def screen(s): return None if s is None else ScreenSnapshot(s['type'],s['keyframe'],tuple(element(e) for e in s['elements']))
def capture(fn):
    try: return fn()
    except (ValueError,RuntimeError): return dict(error=True)

rng=random.Random(1802)
corners=[[100,80],[1180,100],[1130,650],[130,620]]
geometry=[]
for index in range(60):
    dx,dy=rng.uniform(-300,300),rng.uniform(-300,300)
    w,h=rng.uniform(200,2000),rng.uniform(200,2000)
    c=[[dx,dy],[dx+w,dy+rng.uniform(-30,30)],[dx+w+rng.uniform(-30,30),dy+h],[dx+rng.uniform(-30,30),dy+h]]
    samples=[[rng.uniform(-.2,1.2),rng.uniform(-.2,1.2)] for _ in range(4)]+[[0,0],[1,1]]
    transform=Homography.from_corners(tuple(map(point,c)))
    outputs=[]
    for p in samples:
        camera=transform.screen_to_camera(point(p)); projected=transform.camera_to_screen(camera)
        outputs.append(dict(screen=p,camera=asdict(camera),projected=asdict(projected)))
    geometry.append(dict(corners=c,samples=outputs))
for c in [corners,[corners[0],corners[3],corners[2],corners[1]],[[0,0],[1,0],[2,0],[3,0]],[[0,0],[1,1],[1,0],[0,1]],[[0,0]]]:
    geometry.append(dict(corners=c,expected=capture(lambda: dict(valid=Homography.from_corners(tuple(map(point,c))) is not None))))

plane=PlaneTracker(); plane_rows=[]
for now,c,confidence in [(0,corners,.95),(.1,None,1),(.2,corners,.4),(3.1,None,1),(3.2,corners,.95),(3.3,[[0,0],[1,0],[2,0],[3,0]],1),(3.4,corners,2),(3.5,corners,.95),(3.5,corners,.95),(3.4,corners,.95)]:
    def run_plane():
        r=plane.update(None if c is None else tuple(map(point,c)),now,confidence)
        return dict(valid=r.valid,lost=r.lost_for_s,timeout=r.loss_timeout,reacquire=r.requires_reacquisition,has_reason=r.reason is not None)
    plane_rows.append(dict(input=dict(now=now,corners=c,confidence=confidence),expected=capture(run_plane)))

elements=[]
base=[dict(kind='button',box=[.1,.1,.4,.4],conf=.9),dict(kind='button',box=[.11,.11,.41,.41],conf=.8),dict(kind='price',box=[.1,.1,.4,.4],conf=.85),dict(kind='menu',box=[.6,.6,.8,.8],conf=.49)]
for index in range(20):
    detections=base[:] if index==0 else []
    for _ in range(12):
        x,y=rng.uniform(0,.8),rng.uniform(0,.8)
        detections.append(dict(kind=rng.choice(['button','menu','price','tab','back']),box=[x,y,x+.15,y+.15],conf=rng.choice([.49,.5,.7,.9])))
    if index==1: detections=[]
    r=process_detections(tuple(Detection(d['kind'],box(d['box']),d['conf']) for d in detections),index)
    elements.append(dict(input=dict(detections=detections,keyframe=index),expected=[e.to_dict() for e in r.elements],reacquire=r.requires_plane_reacquisition))
# Exact equality at the suppression threshold must keep both detections.
equal=[dict(kind='button',box=[.1,.1,.4,.4],conf=.9)]*2
r=process_detections(tuple(Detection(d['kind'],box(d['box']),d['conf']) for d in equal),30,nms_iou_threshold=1)
elements.append(dict(input=dict(detections=equal,keyframe=30,nms=1),expected=[e.to_dict() for e in r.elements],reacquire=False))

unit=[[0,0],[100,0],[100,100],[0,100]]
def hand(name='h',p=(50,50),conf=.95,distance=None): return dict(id=name,point=p,conf=conf,distance=distance)
tracking=[]
traces=[[(i/10,[hand(p=(20+i*4,30+i*2))],1,unit) for i in range(9)],
    [(0,[hand()],1,unit),(.1,[hand('b')],1,unit),(.2,[hand('b',conf=.5)],1,unit),(.3,[hand('b')],1,None),(.4,[hand('b',p=(110,50))],1,unit),(.5,[],1,unit),(1.4,[],1,unit),(1.5,[hand('b')],2,unit)],
    [(0,[hand('a'),hand('b')],1,unit),(.1,[hand('a',distance=2),hand('b',distance=1)],1,unit),(.2,[hand('a'),hand('b')],1,unit),(.3,[hand('a',distance=1),hand('b',distance=1)],1,unit),(.4,[hand('b'),hand('b')],1,unit)],
    [(0,[hand()],1,unit),(.1,[hand(p=(60,50))],1,unit),(.1,[hand()],1,unit),(.2,[hand()],1,unit),(.5,[hand(p=(80,50))],1,unit),(.6,[hand()],-1,unit)],
    [(0,[hand()],1,unit),(.1,[hand(p=(60,50))],1,[[1,0],[101,0],[101,100],[1,100]]),(.2,[hand(p=(70,50))],2,unit)]]
for trace in traces:
    tracker=FingertipTracker(); rows=[]
    for now,hands,keyframe,c in trace:
        def run_tracking():
            hs=tuple(HandObservation(h['id'],tuple(point(h['point']) for _ in range(21)),h['conf'],h['distance']) for h in hands)
            result=tracker.update(hs,None if c is None else Homography.from_corners(tuple(map(point,c))),now,keyframe_id=keyframe)
            return dict(asdict(result),valid=result.valid)
        rows.append(dict(input=dict(now=now,hands=hands,keyframe=keyframe,corners=c),expected=capture(run_tracking)))
    tracking.append(rows)

def snapshot(kind='menu',keyframe=1,text='coffee',conf=.95): return dict(type=kind,keyframe=keyframe,elements=[dict(id=f'e{keyframe}',kind='button',box=[.2,.2,.4,.4],conf=conf,text=text)])
def observation(after=None,now=.5,captured=.4,**kw): return dict(after=after,now=now,captured=captured,cart=None,cart_keyframe=None,returned=False,**kw)
before=snapshot(); expected=dict(screen_type='option')
verification=[]
flows=[
    [observation(snapshot('option',2)),observation(snapshot('menu',3),.7,.6)],
    [observation(snapshot('cart',2))],
    [observation(snapshot('menu',2)),observation(snapshot('menu',3),1.5,1.5)],
    [observation(snapshot('menu',2),1.6,1.6)],
    [observation(snapshot('option',2),1.6,1.6)],
    [observation(snapshot('option',2),1.6,1.4)],
    [observation(snapshot('menu',2),1.6,1.4)],
    [observation(snapshot('option',2),captured=None),observation(snapshot('option',3),1.5,None)],
    [observation(None),observation(None,1.5,1.5)],
    [observation(snapshot('option',2),captured=-.1),observation(snapshot('option',3),.6,.7)],
    [observation(snapshot('option',1)),observation(snapshot('option',2),.6,.5)],
    [observation(snapshot('other',2)),observation(snapshot('option',3,conf=.5),1.5,1.5)],
    [dict(observation(snapshot('menu',2)),returned=True)],
    [observation(snapshot('menu',2)),observation(snapshot('menu',3),.4,.3)],
    [observation(snapshot('menu',2,text='changed')),observation(snapshot('menu',3),1.6,1.6)],
]
for flow in flows:
    verifier=PressVerifier(); verifier.begin(screen(before),ExpectedEffect(**expected),0)
    rows=[]
    for obs in flow:
        rows.append(dict(input=obs,expected=capture(lambda: verifier.observe(screen(obs['after']),obs['now'],captured_at=obs['captured'],returned_to_start=obs['returned']).to_dict())))
    verification.append(dict(before=before,effect=expected,started=0,rows=rows))
cart_before=snapshot('cart'); cart_effect=dict(cart_item_key='coffee|hot',before_quantity=0,quantity_delta=2)
for quantities,bound,now,captured in [(None,None,.5,.4),({'other':2},2,.5,.4),({'coffee|hot':2},None,.5,.4),({'coffee|hot':2},2,.5,.4),({'coffee|hot':1},2,.5,.4),({'coffee|hot':0},2,.5,.4),({'coffee|hot':0},2,1.5,1.5),({'coffee|hot':0},2,1.6,1.6),({'coffee|hot':2},2,1.6,1.6)]:
    verifier=PressVerifier(); verifier.begin(screen(cart_before),ExpectedEffect(**cart_effect),0)
    obs=dict(observation(snapshot('cart',2),now,captured),cart=quantities,cart_keyframe=bound)
    result=verifier.observe(screen(obs['after']),now,captured_at=captured,cart_quantities=quantities,cart_keyframe_id=bound).to_dict()
    verification.append(dict(before=cart_before,effect=cart_effect,started=0,rows=[dict(input=obs,expected=result)]))
changes=[]
for after in [snapshot('menu',2),snapshot('menu',2,text='tea'),snapshot('option',2),dict(snapshot('menu',2),elements=[]),snapshot('menu',2,conf=.8)]:
    changes.append(dict(before=before,after=after,expected=screen_changed(screen(before),screen(after))))

data=dict(revision='ba55be0248c709ab9222d40c100dcb9d61362a50',sources={name:hashlib.sha256((root/'sonkkeut_vision'/name).read_bytes()).hexdigest() for name in ['contracts.py','geometry.py','elements.py','tracking.py','verification.py']},geometry=geometry,plane=plane_rows,elements=elements,tracking=tracking,verification=verification,changes=changes)
output=Path('app/src/test/resources/visual-reference.json'); output.parent.mkdir(parents=True,exist_ok=True)
output.write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
print('Python executed:',dict(geometry=len(geometry),plane=len(plane_rows),elements=len(elements),tracking=sum(map(len,tracking)),verification=sum(len(f['rows']) for f in verification),changes=len(changes)))
