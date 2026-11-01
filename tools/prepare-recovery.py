"""Construct causal restart cases from development recordings, outside the repo."""
import argparse
import json
from pathlib import Path
import numpy as np

p=argparse.ArgumentParser();p.add_argument('--data',default='../benchmark-data');args=p.parse_args()
root=Path(args.data).resolve();paths=Path(root/'development-full-manifest.txt').read_text().splitlines()[:3]
cases=[]
for source in map(Path,paths):
    audio=np.fromfile(source/'audio.f32',dtype='<f4');truth=np.loadtxt(source/'truth.tsv')
    lines=(source/'graph.tsv').read_text().splitlines()
    attacks=[(int(l.split('\t')[0]),l.split('\t')[5]) for l in lines if l.split('\t')[5]]
    patterns=[tuple(x[1] for x in attacks[i:i+8]) for i in range(len(attacks)-7)]
    # Only select passages distinguishable by the next eight notated attacks.
    chosen=0
    for wanted in np.linspace(8,38,25):
        target=truth[np.abs(truth[:,0]-wanted).argmin(),0]
        tick=np.interp(target,truth[:,0],truth[:,1])
        index=min(range(len(attacks)),key=lambda i:abs(attacks[i][0]-tick))
        if index>=len(patterns) or patterns.count(patterns[index])!=1 or abs(target-20)<3:continue
        folder=root/'recovery'/f'{source.name}-{chosen:02d}';folder.mkdir(parents=True,exist_ok=True)
        noise=chosen%2==1
        signal=np.concatenate([audio[:20*44100],np.zeros(44100),audio[int(target*44100):int((target+7)*44100)]])
        if noise:
            rng=np.random.default_rng(7721+chosen)
            level=np.sqrt(np.mean(audio[3*44100:20*44100]**2))/10
            signal=signal+rng.normal(0,level,signal.shape) # 20 dB SNR relative to playing section
        signal.astype('<f4').tofile(folder/'audio.f32')
        (folder/'graph.tsv').write_bytes((source/'graph.tsv').read_bytes())
        (folder/'case.json').write_text(json.dumps(dict(source=str(source),restart=21,target=float(target),noise_snr_db=20 if noise else None),indent=2))
        cases.append(str(folder));chosen+=1
        if chosen>=7:break
cases=cases[:20]
(root/'recovery-manifest.txt').write_text('\n'.join(cases))
all_paths=(root/'all-full-manifest.txt').read_text().splitlines()+cases
(root/'final-manifest.txt').write_text('\n'.join(all_paths))
print('Prepared',len(cases),'restart cases (development audio only)')
