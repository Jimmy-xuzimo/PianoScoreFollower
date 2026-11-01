"""Run released realtime Magenta + Parangonar on a fixed recording manifest.
Uses score tempo, never performance-derived estimated_bpm metadata.
"""
import argparse
import subprocess
import sys
from pathlib import Path

p=argparse.ArgumentParser()
p.add_argument('manifest',type=Path)
p.add_argument('model',type=Path)
p.add_argument('--resampler',choices=['poly','linear'],default='linear')
p.add_argument('--threads',type=int,default=2)
p.add_argument('--export-frames',action='store_true')
args=p.parse_args()
for line in args.manifest.read_text().splitlines():
    if not line.strip():continue
    folder=Path('/mnt/'+line[0].lower()+'/'+line[3:].replace('\\','/')) if sys.platform=='linux' and line[1:2]==':' else Path(line)
    prefix='magenta-linear' if args.resampler=='linear' else 'magenta'
    notes=folder/(prefix+'.notes.tsv')
    command=[sys.executable,str(Path(__file__).with_name('probe-magenta.py')),
        str(args.model),str(folder/'audio.f32'),str(notes),'--seconds','10000',
        '--resampler',args.resampler,'--threads',str(args.threads)]
    if args.export_frames:command+=['--export-frames']
    subprocess.run(command,check=True)
    bpm=(folder/'graph.tsv').read_text().splitlines()[0].split('\t')[7]
    subprocess.run([sys.executable,str(Path(__file__).with_name('evaluate-symbolic.py')),
        str(folder/'graph.tsv'),str(notes),str(folder/(prefix+'-symbolic.tsv')),'--bpm',bpm],check=True)
    print('COMPLETED',folder.name,flush=True)
