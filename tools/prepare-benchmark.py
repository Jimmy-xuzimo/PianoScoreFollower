"""Download selected paired recordings. Data is outside the source tree/APK.
Usage: python tools/prepare-benchmark.py --data ../benchmark-data --split development
Development and held-out scores are disjoint. Never tune on held-out traces.
"""
import argparse, csv, io, json, re, subprocess, urllib.request
from pathlib import Path
import numpy as np
import soundfile as sf
from scipy.signal import resample_poly

REVISION = '7ea5babf039a0c009050646c90ea2bfe925653f6'
BASE = f'https://raw.githubusercontent.com/pymatchmaker/matchmaker-benchmark-data/{REVISION}/'
parser=argparse.ArgumentParser();parser.add_argument('--data',default='../benchmark-data')
parser.add_argument('--split',choices=['development','heldout','verification'],default='development')
parser.add_argument('--seconds',type=int,default=60,help='0 evaluates entire recordings')
args=parser.parse_args();root=Path(args.data).resolve();root.mkdir(parents=True,exist_ok=True)
repo=Path(__file__).resolve().parent.parent
def fetch(path):
    dest=root/path
    if not dest.exists():
        dest.parent.mkdir(parents=True,exist_ok=True)
        for attempt in range(3):
            try:
                data=urllib.request.urlopen(BASE+path,timeout=60).read();dest.write_bytes(data);break
            except Exception:
                if attempt==2: raise
    return dest

manifest=[]
for group in ['asap','batik','vienna']:
    metadata=fetch(f'{group}/metadata-{group}.csv')
    rows=list(csv.DictReader(io.StringIO(metadata.read_text())))
    scores=sorted(set(r['score'] for r in rows))
    # First two distinct works for development, next two for locked evaluation.
    if args.split=='verification':
        def family(score):
            match=re.search(r'(?:bwv_|kv)([0-9]+)',score)
            return match.group(0).replace('bwv_','bwv') if match else score
        blocked={family(s) for s in scores[:4]}
        selected=[s for s in scores if family(s) not in blocked][:2]
        if not selected: print(group,'has no unused score families for verification',flush=True)
    else:
        selected=scores[:2] if args.split=='development' else scores[2:4]
    for score in selected:
        for row in [r for r in rows if r['score']==score][:2]:
            paths={k:fetch(row[k]) for k in ['score','audio','midi','match']}
            name=Path(row['audio']).stem
            output=root/(args.split+('-full' if args.seconds==0 else ''))/name;output.mkdir(parents=True,exist_ok=True)
            subprocess.run(['node',str(repo/'tools/export-graph.cjs'),str(paths['score']),str(output/'graph.tsv')],check=True)
            # libsndfile decodes mp3, no system ffmpeg dependency.
            audio,rate=sf.read(paths['audio'],dtype='float32',always_2d=True)
            audio=audio.mean(axis=1)
            if args.seconds: audio=audio[:rate*args.seconds]
            if rate!=44100:
                from math import gcd
                divisor=gcd(rate,44100);audio=resample_poly(audio,44100//divisor,rate//divisor)
            # Three seconds of room silence gives the live noise estimator its normal pre-roll.
            audio=np.concatenate([np.zeros(3*44100,dtype=np.float32),audio])
            audio.astype('<f4').tofile(output/'audio.f32')
            data=paths['match'].read_text(errors='replace')
            units=int(re.search(r'info\(midiClockUnits,(\d+)\)',data)[1])
            clock=int(re.search(r'info\(midiClockRate,(\d+)\)',data)[1])
            from annotation_truth import annotation_anchors
            graphlines=[line.split('\t') for line in (output/'graph.tsv').read_text().splitlines()]
            anchors=annotation_anchors(paths['score'],graphlines,data,clock,units)
            if not anchors: raise RuntimeError('No ground truth '+name)
            anchors=sorted((t,q*960) for t,q in anchors)
            # Consolidate chord onsets by median, preserving the source's beat axis.
            buckets={}
            for t,q in anchors: buckets.setdefault(q,[]).append(t)
            unique=sorted((float(np.median(ts)),q) for q,ts in buckets.items())
            (output/'truth.tsv').write_text('\n'.join(f'{t:.6f}\t{q:.3f}' for t,q in unique))
            (output/'provenance.json').write_text(json.dumps(dict(row,revision=REVISION,split=args.split,seconds=args.seconds),indent=2))
            manifest.append(str(output))
            print(args.split,name,len(audio)/44100,'seconds',flush=True)
fetch('LICENSE.md');fetch('LICENSING.md')
(root/(args.split+('-full' if args.seconds==0 else '')+'-manifest.txt')).write_text('\n'.join(manifest))
