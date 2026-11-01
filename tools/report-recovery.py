"""A restart is recovered after 0.3 s of continuous correct, confident following."""
import argparse,json
from pathlib import Path
import numpy as np
p=argparse.ArgumentParser();p.add_argument('manifest');p.add_argument('--require-target',action='store_true');p.add_argument('--trace',default='trace.tsv');args=p.parse_args();rows=[]
for folder in map(Path,Path(args.manifest).read_text().splitlines()):
    case=json.loads((folder/'case.json').read_text());truth=np.loadtxt(Path(case['source'])/'truth.tsv')
    trace=np.genfromtxt(folder/args.trace,dtype=str,delimiter='\t');time=trace[:,0].astype(float)
    expected=np.interp(time-case['restart']+case['target'],truth[:,0],truth[:,1])
    good=(abs(trace[:,1].astype(float)-expected)<=480)&(trace[:,2].astype(float)>=.5)&(trace[:,3]=='FOLLOWING')&(time>=case['restart'])
    latency=None;count=0
    for i,ok in enumerate(good):
        count=count+1 if ok else 0
        if count>=13:
            latency=float(time[i-12]-case['restart']);break
    rows.append(dict(name=folder.name,latency_seconds=latency,within_three_seconds=latency is not None and latency<=3,noise_snr_db=case['noise_snr_db']))
summary=dict(cases=rows,count=len(rows),within_three_seconds=sum(r['within_three_seconds'] for r in rows)/len(rows))
summary['passed']=summary['within_three_seconds']>=.95
latencies=[r['latency_seconds'] for r in rows]
summary['p95_seconds']=float(np.quantile(latencies,.95)) if all(v is not None for v in latencies) else None
report_base=Path(args.manifest) if args.trace=='trace.tsv' else Path(args.manifest).with_name(Path(args.manifest).stem+'-'+Path(args.trace).stem)
report_base.with_suffix('.report.json').write_text(json.dumps(summary,indent=2))
print(json.dumps(summary,indent=2))

if args.require_target and not summary['passed']:raise SystemExit(1)
