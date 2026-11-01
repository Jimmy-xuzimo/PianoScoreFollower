"""Monitor a debug-only decoded audio replay, labelled explicitly as injection."""
import argparse
import json
import subprocess
import time
from pathlib import Path

p=argparse.ArgumentParser()
p.add_argument('--adb',required=True)
p.add_argument('--serial',default='127.0.0.1:16416')
p.add_argument('--file',required=True)
p.add_argument('--seconds',type=float,default=80)
p.add_argument('--output',type=Path,required=True)
args=p.parse_args()
adb=[args.adb,'-s',args.serial]
def command(name,value=None):
    parts=['shell','am','broadcast','-n','com.pianoscorefollower.app.debug/com.pianoscorefollower.app.DebugReceiver',
        '-a','com.pianoscorefollower.DEBUG','--es','command',name]
    if value is not None:parts+=['--es','argument',value]
    subprocess.run(adb+parts,check=True,stdout=subprocess.DEVNULL)
command('replayAudio',args.file)
start=time.monotonic();rows=[]
while time.monotonic()-start<args.seconds:
    command('dump')
    raw=subprocess.check_output(adb+['shell','cat','/sdcard/Android/data/com.pianoscorefollower.app.debug/files/debug-audio.json'])
    row=json.loads(raw);row['monitor_seconds']=time.monotonic()-start;rows.append(row)
    args.output.write_text(json.dumps(rows,indent=2),encoding='utf-8')
    if not row['following'] and len(rows)>2:break
    time.sleep(.5)
for name in ['debug-neural-trace.tsv','debug-model-frames.bin']:
    subprocess.run(adb+['pull','/sdcard/Android/data/com.pianoscorefollower.app.debug/files/'+name,str(args.output.with_name(name))],check=True)
print(json.dumps(dict(samples=len(rows),last=rows[-1]),ensure_ascii=False))
