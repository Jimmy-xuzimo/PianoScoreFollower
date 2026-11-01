"""Evaluate the published causal RTT checkpoint with persistent streaming state.

No Lightning/training dependencies; upstream model, weights and license stay in
the supplied checkout. Inference gets 100ms of new audio at a time. Causal conv
history and unidirectional GRU hidden states are retained, not reset each block.
The asymmetric STFT's declared 10ms lookahead is accounted for in available_sec.
This is a research probe, not an Android implementation.
"""
import argparse
import json
import sys
import time
import types
from pathlib import Path
import numpy as np
from scipy.signal import resample_poly
import torch
import torch.nn.functional as F


def make_streaming(model, causal_type):
    for module in model.modules():
        if isinstance(module, causal_type) and module.kernel_size[1] > 1:
            module.stream_cache = None
            def conv(self, x):
                n = self.kernel_size[1]-1
                if self.stream_cache is None:
                    self.stream_cache = x.new_zeros((*x.shape[:-1],n))
                history = torch.cat((self.stream_cache,x),dim=-1)
                self.stream_cache = history[...,-n:].detach()
                return F.conv2d(history,self.weight,self.bias,self.stride,
                    (self.padding[0],0),self.dilation,self.groups)
            module.forward=types.MethodType(conv,module)
        elif isinstance(module,torch.nn.GRU):
            module.stream_hidden=None
            module.original_forward=module.forward
            def gru(self,x,hx=None):
                y,h=self.original_forward(x,self.stream_hidden if hx is None else hx)
                self.stream_hidden=h.detach()
                return y,h
            module.forward=types.MethodType(gru,module)
    frontend=model.logmel_extractor
    frontend.stream_cache=None
    def mel(self,x):
        n=self.spectrogram.win_length-self.fft_delay
        if self.stream_cache is None:
            self.stream_cache=x.new_zeros((x.shape[0],n))
        samples=torch.cat((self.stream_cache,x),dim=-1)
        self.stream_cache=samples[...,-n:].detach()
        return torch.log1p(self.log_multiplier*self.mel_scale(self.spectrogram(samples)))
    frontend.forward=types.MethodType(mel,frontend)


def main():
    p=argparse.ArgumentParser()
    p.add_argument('upstream',type=Path)
    p.add_argument('audio',type=Path)
    p.add_argument('output',type=Path)
    p.add_argument('--seconds',type=float,default=30)
    p.add_argument('--rate',type=int,default=44100)
    p.add_argument('--threads',type=int,default=2)
    p.add_argument('--parity',action='store_true')
    args=p.parse_args()
    torch.set_num_threads(args.threads)
    torch.backends.mkldnn.enabled=False
    sys.path.insert(0,str(args.upstream/'src'))
    from models import CustomAMT,CausalConv2d
    checkpoint=torch.load(args.upstream/'ckpts/CustomAMT.ckpt',map_location='cpu',weights_only=True)
    state={k.removeprefix('model.'):v for k,v in checkpoint['state_dict'].items()}
    model=CustomAMT().eval()
    model.load_state_dict(state,strict=True)
    audio=np.fromfile(args.audio,dtype='<f4')[:int(args.seconds*args.rate)]
    # Resampling is preparatory here. The network only receives past 100ms blocks.
    # A deployed resampler also needs its own separately measured filter latency.
    from math import gcd
    d=gcd(16000,args.rate)
    audio=resample_poly(audio,16000//d,args.rate//d).astype(np.float32)
    count=len(audio)//1600
    audio=audio[:count*1600]
    expected=None
    if args.parity:
        with torch.inference_mode():
            expected=model(torch.from_numpy(audio[None,:]))
    make_streaming(model,CausalConv2d)
    outputs={}
    start=time.perf_counter()
    with torch.inference_mode():
        for i in range(count):
            # Preserve upstream inference's floating waveform convention. The
            # checkpoint's BatchNorm statistics include its internal /32768.
            out=model(torch.from_numpy(audio[i*1600:(i+1)*1600][None,:]))
            for k,v in out.items():outputs.setdefault(k,[]).append(v[0].numpy())
    elapsed=time.perf_counter()-start
    outputs={k:np.concatenate(v) for k,v in outputs.items()}
    if expected is not None:
        errors={k:float(np.max(np.abs(v-expected[k][0,:len(v)].detach().numpy()))) for k,v in outputs.items()}
        print('STREAM_PARITY',json.dumps(errors),flush=True)
        for k,v in outputs.items():
            e=expected[k][0,:len(v)].detach().numpy()
            delta=np.max(abs(v-e),axis=1)
            probability_error=abs(1/(1+np.exp(-v))-1/(1+np.exp(-e)))
            print('PARITY_DETAIL',k,json.dumps(dict(first_bad=int(np.argmax(delta>.002)),maximum_at=int(np.argmax(delta)),mean=float(np.mean(abs(v-e))),probability_max=float(probability_error.max()),probability_mean=float(probability_error.mean()),decisions=float(np.mean((v>=0)!=(e>=0))))),flush=True)
        # GEMM/GRU accumulation order differs between one long batch and small
        # streaming batches. Keep probability and onset-decision checks explicit.
        e=expected['onset_output'][0,:len(outputs['onset_output'])].detach().numpy()
        v=outputs['onset_output']
        if np.mean((v>=0)!=(e>=0))>.001:
            raise RuntimeError('Streaming onset decisions differ from original by more than 0.1%')
    probabilities={k:1/(1+np.exp(-v)) for k,v in outputs.items()}
    np.savez_compressed(args.output,**probabilities)
    onset=probabilities['onset_output']
    previous=np.vstack((np.zeros((1,88)),onset[:-1]))
    frame,pitch=np.where((onset>=.5)&(previous<.5))
    rows=['available_sec\tonset_sec\tpitch\tprobability']
    for i,n in zip(frame,pitch):
        rows.append(f'{(i+1)/100:.5f}\t{i/100:.5f}\t{n+21}\t{onset[i,n]:.6f}')
    args.output.with_suffix('.notes.tsv').write_text('\n'.join(rows)+'\n')
    print(json.dumps(dict(seconds=count*.1,compute_seconds=elapsed,realtime_factor=elapsed/(count*.1),notes=len(frame))),flush=True)


if __name__=='__main__':main()
