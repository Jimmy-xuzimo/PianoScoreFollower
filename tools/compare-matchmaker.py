"""Compare an upstream feature on development recordings, preserving old traces.

Run in the isolated Matchmaker environment. Windows manifests are also accepted
from WSL; the engine itself must be installed in the Python running this script.
"""
import argparse
import json
import subprocess
import sys
from pathlib import Path
import numpy as np

p=argparse.ArgumentParser()
p.add_argument('manifest',type=Path)
p.add_argument('output',type=Path)
p.add_argument('--feature',choices=['chroma','lse'],default='chroma')
p.add_argument('--fft',type=int)
args=p.parse_args()
reports=[]
for line in args.manifest.read_text().splitlines():
    if not line.strip():continue
    if sys.platform=='linux' and len(line)>2 and line[1]==':':
        folder=Path('/mnt/'+line[0].lower()+'/'+line[3:].replace('\\','/'))
    else:folder=Path(line)
    trace=folder/f'upstream-{args.feature}-{args.fft or "default"}.tsv'
    command=[sys.executable,str(Path(__file__).with_name('evaluate-matchmaker.py')),
        str(folder/'reference.f32'),str(folder/'audio.f32'),str(trace),'--feature',args.feature]
    if args.fft:command+=['--fft',str(args.fft)]
    subprocess.run(command,check=True)
    a=np.loadtxt(trace,skiprows=1);g=np.loadtxt(folder/'truth.tsv')
    graph=[row.split('\t') for row in (folder/'graph.tsv').read_text().splitlines()]
    ticks=np.asarray([float(r[0]) for r in graph]+[float(graph[-1][0])+float(graph[-1][1])])
    tempi=np.asarray([float(r[7]) for r in graph])
    seconds=np.concatenate(([ticks[0]*60/960/tempi[0]],np.cumsum(np.diff(ticks)*60/960/tempi)))
    t=a[:,0];mask=(t>=g[0,0])&(t<=g[-1,0])
    estimated=np.interp(a[:,1],seconds,ticks)
    error=abs(estimated-np.interp(t,g[:,0],g[:,1]))/960
    r=dict(name=folder.name,seconds=float(mask.sum()*1024/44100),
        accuracy=float(np.mean(error[mask]<=.5)),median=float(np.median(error[mask])),p95=float(np.quantile(error[mask],.95)))
    reports.append(r);print('RESULT',json.dumps(r),flush=True)
    args.output.write_text(json.dumps(reports,indent=2))
print('TOTAL',sum(r['seconds']*r['accuracy'] for r in reports)/sum(r['seconds'] for r in reports))
