"""Replay the upstream OpenScofo binary on ordered, causal audio windows.

This adapter writes its separate .scofo score; it does not modify MusicXML/MIDI.
Published Python wheel version and GPL license must be recorded with results.
"""
import argparse
import json
import time
from pathlib import Path
import numpy as np
from scipy.signal import resample_poly
from OpenScofo import OpenScofo

p=argparse.ArgumentParser()
p.add_argument('case',type=Path)
p.add_argument('--fft',type=int,default=4096)
p.add_argument('--hop',type=int,default=1024)
p.add_argument('--bpm',type=float)
args=p.parse_args()
graph=[row.split('\t') for row in (args.case/'graph.tsv').read_text().splitlines()]
bpm=args.bpm or float(graph[0][7])
score=[f'BPM {bpm}']
for row in graph:
    pitches=list(map(int,row[6].split(','))) if row[6] else []
    notes=[('C','C#','D','D#','E','F','F#','G','G#','A','A#','B')[n%12]+str(n//12-1) for n in pitches]
    duration=int(row[1])/960
    if len(notes)>1:score.append(f'CHORD ({" ".join(notes)}) {duration}')
    else:score.append(f'NOTE {notes[0]} {duration}' if notes else f'REST {duration}')
path=args.case/'reference.scofo'
path.write_text('\n'.join(score)+'\n')
engine=OpenScofo(48000,args.fft,args.hop)
result=engine.load_score(str(path))
if result is False:raise RuntimeError('OpenScofo refused generated score')
print('API',dir(engine),flush=True)
print('CONFIG',engine.get_sr(),engine.get_fft_size(),engine.get_hop_size(),flush=True)
args.fft=int(engine.get_fft_size());args.hop=int(engine.get_hop_size())
position=getattr(engine,'get_event_index',None) or engine.get_current_score_position
tempo=getattr(engine,'get_live_bpm',None) or engine.get_current_bpm
rows=['audio_seconds\tevent_index\ttick\tbpm\tlevel_db']
active=False
t0=time.perf_counter()
import io
audio=resample_poly(np.fromfile(args.case/'audio.f32',dtype='<f4'),160,147).astype('<f4')
with io.BytesIO(audio.tobytes()) as source:
    frame=np.frombuffer(source.read(args.fft*4),dtype='<f4').copy()
    pos=0
    while len(frame)==args.fft:
        db=20*np.log10(max(1e-9,float(np.sqrt(np.mean(frame**2)))))
        if db>-55:active=True
        if active:engine.process_block(frame.astype(np.float64))
        index=position()
        tick=int(graph[min(max(0,index),len(graph)-1)][0])
        rows.append(f'{(pos+args.fft)/48000:.8f}\t{index}\t{tick}\t{tempo()}\t{db:.5f}')
        tail=np.frombuffer(source.read(args.hop*4),dtype='<f4')
        if len(tail)!=args.hop:break
        frame=np.concatenate((frame[args.hop:],tail));pos+=args.hop
(args.case/'openscofo-trace.tsv').write_text('\n'.join(rows)+'\n')
print(json.dumps(dict(frames=len(rows)-1,compute_seconds=time.perf_counter()-t0,last_index=position())))
