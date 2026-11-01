"""Compare cursor discontinuities on forward recordings, separately from accuracy.
Constructed restart cases are excluded: their backwards movement is intentional.
"""
import argparse,json
from pathlib import Path
import numpy as np

p=argparse.ArgumentParser();p.add_argument('manifest');p.add_argument('--before',required=True);p.add_argument('--output',required=True);a=p.parse_args()
rows=[]
def metrics(file):
    data=np.loadtxt(file,usecols=(0,1));steps=np.diff(data[:,1]);dt=np.diff(data[:,0])
    return dict(backward_steps=int((steps<0).sum()),backward_over_half_beat=int((steps < -480).sum()),
        forward_over_eight_beats=int((steps>7680).sum()))
for line in Path(a.manifest).read_text().splitlines():
    folder=Path(line)
    if (folder/'case.json').exists():continue
    old=Path(a.before)/(folder.parent.name+'--'+folder.name+'.tsv')
    rows.append(dict(name=folder.name,before=metrics(old),after=metrics(folder/'trace.tsv')))
summary={phase:{k:sum(r[phase][k] for r in rows) for k in rows[0][phase]} for phase in ['before','after']}
Path(a.output).write_text(json.dumps(dict(summary=summary,recordings=rows),indent=2))
print(json.dumps(summary,indent=2))
