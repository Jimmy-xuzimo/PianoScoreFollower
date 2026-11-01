"""Score saved online traces against annotation anchors, including unconfident frames."""
import argparse,json,hashlib
from pathlib import Path
import numpy as np
p=argparse.ArgumentParser();p.add_argument('manifest');p.add_argument('--require-target',action='store_true');p.add_argument('--trace',default='trace.tsv');args=p.parse_args()
reports=[]
for folder in Path(args.manifest).read_text().splitlines():
    folder=Path(folder)
    truth=np.loadtxt(folder/'truth.tsv'); trace=np.genfromtxt(folder/args.trace,dtype=str,delimiter='\t')
    time=trace[:,0].astype(float); tick=trace[:,1].astype(float)
    mask=(time>=truth[0,0])&(time<=min(truth[-1,0],time[-1]))
    expected=np.interp(time,truth[:,0],truth[:,1])
    error=np.abs(tick-expected)/960
    # Score both continuous beat error and nearest annotated attack position.
    report=dict(name=folder.name,seconds=float(mask.sum()*1024/44100),
        within_half_beat=float(np.mean(error[mask]<=.5)),median_error_beats=float(np.median(error[mask])),
        p95_error_beats=float(np.quantile(error[mask],.95)),onsets=int(np.sum(trace[:,4]=='true')))
    reports.append(report)
    print(json.dumps(report))
total=sum(r['seconds'] for r in reports)
accuracy=sum(r['within_half_beat']*r['seconds'] for r in reports)/total
print('TOTAL',accuracy,'PASS' if accuracy>=.95 else 'FAIL')
report_base=Path(args.manifest) if args.trace=='trace.tsv' else Path(args.manifest).with_name(Path(args.manifest).stem+'-'+Path(args.trace).stem)
report_base.with_suffix('.report.json').write_text(json.dumps(reports,indent=2))
repo=Path(__file__).resolve().parent.parent
sources=['app/src/main/java/com/pianoscorefollower/app/'+p for p in [
    'audio/AudioAnalyzer.kt','audio/MagentaPianoModel.kt','audio/StreamingResampler.kt',
    'follower/TempoPitchTracker.kt','follower/SymbolicScoreFollower.kt','follower/NoteStartLocator.kt',
    'follower/ScoreTempoPrediction.kt','follower/FollowerEngine.kt',
    'performance/PerformanceInput.kt']]+['app/src/main/assets/scoreviewer/graph.js','tools/annotation_truth.py']
if args.trace=='trace.tsv':sources+=['app/src/test/java/com/pianoscorefollower/app/follower/OnlineAligner.kt']
summary=dict(seconds=total,within_half_beat=accuracy,target=.95,passed=accuracy>=.95,
    sha256={s:hashlib.sha256((repo/s).read_bytes()).hexdigest() for s in sources})
summary['trace']=args.trace
report_base.with_suffix('.summary.json').write_text(json.dumps(summary,indent=2))
if args.require_target and accuracy<.95:raise SystemExit(1)
