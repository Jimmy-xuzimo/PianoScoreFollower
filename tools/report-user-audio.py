"""Describe unannotated user audio; these diagnostics are NOT position accuracy."""
import argparse,bisect,hashlib,json
from pathlib import Path
import numpy as np

p=argparse.ArgumentParser();p.add_argument('case');p.add_argument('--video');p.add_argument('--android');p.add_argument('--output',required=True)
a=p.parse_args();folder=Path(a.case)
trace=np.genfromtxt(folder/'trace.tsv',dtype=str,delimiter='\t')
time=trace[:,0].astype(float)-3;tick=trace[:,1].astype(int);level=trace[:,5].astype(float)
steps=np.diff(tick);back=np.where(steps < -1920)[0]
spans=[];start=0
for i in range(1,len(tick)+1):
    if i==len(tick) or tick[i]!=tick[start]:
        duration=time[i-1]-time[start]
        if duration>=2 and time[start]>=0 and level[start:i].max()>-60:
            spans.append(dict(start_seconds=round(float(time[start]),3),end_seconds=round(float(time[i-1]),3),
                duration_seconds=round(float(duration),3),tick=int(tick[start])))
        start=i
graph=[line.split('\t') for line in (folder/'graph.tsv').read_text().splitlines()]
ticks=[int(row[0]) for row in graph];snapshots=[]
for target in [10,30,60,90,120,150,180,210,228]:
    if target>time[-1]:continue
    i=int(abs(time-target).argmin());g=graph[max(0,bisect.bisect_right(ticks,int(tick[i]))-1)]
    snapshots.append(dict(video_seconds=target,tick=int(tick[i]),estimated_measure=int(g[2])+1,status=str(trace[i,3])))
result=dict(annotation='none; no accuracy claim',analyzed_seconds=float(time[-1]),
    backwards_beyond_two_beats=[dict(video_seconds=round(float(time[i+1]),3),beats=round(float(-steps[i])/960,3)) for i in back],
    fixed_position_spans=sorted(spans,key=lambda r:r['duration_seconds'],reverse=True),snapshots=snapshots,
    source_sha256=hashlib.sha256(Path(a.video).read_bytes()).hexdigest() if a.video else None,
    pipeline='decoded mono44100Hz, 3s silence, actual AudioAnalyzer + OnlineAligner in causal order')
if a.android:
    data=json.loads(Path(a.android).read_text());systems=[r['followedSystem'] for r in data if r['followedSystem']>=0]
    result['android']=dict(samples=len(data),monitored_seconds=data[-1]['wallMs']/1000,
        backward_line_changes=sum(y<x for x,y in zip(systems,systems[1:])),first_tick=data[0]['tick'],last_tick=data[-1]['tick'])
target=Path(a.output);target.parent.mkdir(parents=True,exist_ok=True);target.write_text(json.dumps(result,ensure_ascii=False,indent=2),'utf-8')
print(json.dumps(result,ensure_ascii=False,indent=2))
