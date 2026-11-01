"""Evaluate Parangonar's existing online tempo/pitch tracker on causal note events."""
import argparse
import json
from pathlib import Path
import numpy as np
from parangonar.dp.t_oltw import SLT_OLTW

p=argparse.ArgumentParser()
p.add_argument('graph',type=Path)
p.add_argument('notes',type=Path)
p.add_argument('output',type=Path)
p.add_argument('--bpm',type=float,default=120)
args=p.parse_args()
features=[]
for line in args.graph.read_text().splitlines():
    row=line.split('\t')
    if row[5]:features.append([int(row[0])/960,set(map(int,row[5].split(',')))])
tracker=SLT_OLTW(reference_features=features,window_size=40,max_run_count=10,
    init_tempo=60/args.bpm,time_weight=2,tempo_factor=.1,directional_weights=np.array([2.,1.,1.]))
rows=['available_sec\ttick\tpitch\tindex\tonset_sec\tseconds_per_beat']
for line in args.notes.read_text().splitlines()[1:]:
    available,onset,pitch,_=line.split('\t')
    index=tracker([[float(onset),int(pitch)]])
    period=float(tracker.global_tempo_matrix[index+1,0])
    rows.append(f'{available}\t{features[index][0]*960:.0f}\t{pitch}\t{index}\t{onset}\t{period}')
args.output.write_text('\n'.join(rows)+'\n')
print(json.dumps(dict(notes=len(rows)-1,last_tick=features[tracker.current_position][0]*960)))
