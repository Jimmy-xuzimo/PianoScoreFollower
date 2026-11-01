"""Compare Android and offline causal model outputs, aligning clock labels.

NPF1 used centre timestamps; NPF2 uses upstream frame-label timestamps.
This checks numerical parity, not transcription or score-position accuracy.
"""
import argparse,json
from pathlib import Path
import numpy as np

p=argparse.ArgumentParser()
p.add_argument('android',type=Path);p.add_argument('reference',type=Path)
p.add_argument('--output',type=Path)
a=p.parse_args()
dtype=np.dtype([('onset','<i8'),('available','<i8'),('pitches','<f4',(128,)),('attacks','<f4',(128,))])
def read(path):
    raw=path.read_bytes();magic=raw[:4]
    assert magic in (b'NPF1',b'NPF2'),magic
    result=np.frombuffer(raw[4:],dtype=dtype).copy()
    if magic==b'NPF1':result['onset']-=64
    return result[result['onset']>=0]
x=read(a.android);y=read(a.reference)
lookup={(int(row['onset']),int(row['available'])):i for i,row in enumerate(y)}
pairs=[(i,lookup[(int(row['onset']),int(row['available']))]) for i,row in enumerate(x)
       if (int(row['onset']),int(row['available'])) in lookup]
assert pairs,'No matching clocks'
ix,iy=np.asarray(pairs).T
d=np.abs(x['pitches'][ix]-y['pitches'][iy])
report=dict(android_frames=len(x),reference_frames=len(y),matched_frames=len(pairs),
            unmatched_android_frames=len(x)-len(pairs),max_pitch_probability_difference=float(d.max()),
            mean_pitch_probability_difference=float(d.mean()),
            attack_decision_differences=int(np.sum((x['attacks'][ix]>=.5)!=(y['attacks'][iy]>=.5))))
text=json.dumps(report,indent=2);print(text)
if a.output:a.output.write_text(text+'\n')
if report['unmatched_android_frames'] or report['attack_decision_differences']:raise SystemExit(1)
