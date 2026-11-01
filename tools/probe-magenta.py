"""Causal rolling-window probe for Google's released realtime TFLite model.

Only already available PCM is passed to invoke(). Initial context is zero, not
future audio. Four frames are emitted per 128ms block, leaving two output frames
as right context; uncentred frame-label timestamps and availability are separate.
No neural accuracy claims are inferred from successful execution.
"""
import argparse
import json
import time
import struct
from pathlib import Path
import numpy as np
from scipy.signal import resample_poly
from ai_edge_litert.interpreter import Interpreter

p=argparse.ArgumentParser()
p.add_argument('model',type=Path)
p.add_argument('audio',type=Path)
p.add_argument('output',type=Path)
p.add_argument('--seconds',type=float,default=35)
p.add_argument('--threads',type=int,default=2)
p.add_argument('--resampler',choices=['poly','linear'],default='poly')
p.add_argument('--export-frames',action='store_true')
args=p.parse_args()
model=Interpreter(model_path=str(args.model),num_threads=args.threads)
model.allocate_tensors()
input_info=model.get_input_details()[0]
outputs={v['name']:v['index'] for v in model.get_output_details()}
size=int(input_info['shape'][0])
frames=int(model.get_output_details()[0]['shape'][1])
hop=(size-2048)//(frames-1)
assert hop==512 and frames==32 and size==17920
audio=np.fromfile(args.audio,dtype='<f4')[:int(args.seconds*44100)]
if args.resampler=='poly':audio=resample_poly(audio,160,441).astype(np.float32)
else:
    positions=np.arange(int(np.floor((len(audio)-1)*16000/44100))+1)*44100/16000
    left=np.floor(positions).astype(int);right=np.minimum(left+1,len(audio)-1)
    fraction=(positions-left).astype(np.float32)
    audio=(audio[left]+(audio[right]-audio[left])*fraction).astype(np.float32)
block=hop*4
window=np.zeros(size,dtype=np.float32)
previous=np.zeros(88,dtype=np.float32)
rows=['available_sec\tonset_sec\tpitch\tprobability']
roll=[]
binary=args.output.with_suffix('.frames.bin').open('wb') if args.export_frames else None
if binary:binary.write(b'NPF2')
start=time.perf_counter()
for offset in range(0,len(audio)-block+1,block):
    window[:-block]=window[block:]
    window[-block:]=audio[offset:offset+block]
    model.set_tensor(input_info['index'],window)
    model.invoke()
    logits=model.get_tensor(outputs['onset_logits'])[0]
    frame_logits=model.get_tensor(outputs['frame_logits'])[0]
    available=(offset+block)/16000
    for frame in range(26,30):
        onset=(offset+block-size+frame*hop)/16000
        if onset<0:continue
        # Same onset threshold as the official demo (logit zero).
        attack_pitches=np.flatnonzero((logits[frame]>0)&(previous<=0))
        for pitch in attack_pitches:
            probability=float(1/(1+np.exp(-logits[frame,pitch])))
            rows.append(f'{available:.6f}\t{onset:.6f}\t{pitch+21}\t{probability:.6f}')
        if binary:
            pitches=np.zeros(128,dtype='<f4');attacks=np.zeros(128,dtype='<f4')
            pitches[21:109]=1/(1+np.exp(-frame_logits[frame]))
            attacks[attack_pitches+21]=1/(1+np.exp(-logits[frame,attack_pitches]))
            binary.write(struct.pack('<qq',int(round(onset*1000)),int(round(available*1000))))
            binary.write(pitches.tobytes());binary.write(attacks.tobytes())
        previous=logits[frame].copy()
        roll.append(logits[frame].copy())
args.output.write_text('\n'.join(rows)+'\n')
if binary:binary.close()
np.save(args.output.with_suffix('.npy'),np.asarray(roll))
elapsed=time.perf_counter()-start
print(json.dumps(dict(seconds=len(audio)/16000,compute_seconds=elapsed,
    realtime_factor=elapsed/(len(audio)/16000),notes=len(rows)-1)))
