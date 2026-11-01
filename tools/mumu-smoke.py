"""Small adb-driven UI helper. Requires the debug APK and a running MuMu device."""
import argparse,json,re,subprocess,time,xml.etree.ElementTree as ET
from pathlib import Path

parser=argparse.ArgumentParser();parser.add_argument('--adb',required=True);parser.add_argument('--serial',default='127.0.0.1:16416')
parser.add_argument('command',choices=['dump','tap','debug','screenshot']);parser.add_argument('argument',nargs='?',default='')
parser.add_argument('--value',default='');args=parser.parse_args()
adb=[args.adb,'-s',args.serial]
def call(*parts): return subprocess.check_output(adb+list(parts)).decode('utf-8',errors='replace').strip()
def dump():
    name='/data/local/tmp/piano-ui-'+str(time.time_ns())+'.xml'
    subprocess.run(adb+['shell','uiautomator','dump',name],capture_output=True)
    result=subprocess.run(adb+['shell','cat',name],capture_output=True)
    if result.returncode:raise SystemExit('UI is updating continuously; pause playback before requesting an idle UI dump.')
    result=ET.fromstring(result.stdout)
    call('shell','rm',name)
    return result
def native_nodes(root):
    yield root
    if root.get('class')!='android.webkit.WebView':
        for child in root:yield from native_nodes(child)
if args.command=='dump':
    print(json.dumps([(n.get('text') or n.get('content-desc'),n.get('bounds'),n.get('enabled')) for n in native_nodes(dump()) if n.get('text') or n.get('content-desc')],ensure_ascii=False))
elif args.command=='tap':
    nodes=list(dump().iter('node'))
    node=next((n for n in nodes if n.get('content-desc')==args.argument),None)
    if node is None:node=next((n for n in nodes if n.get('text')==args.argument),None)
    if node is None: raise SystemExit('UI element not found: '+args.argument)
    x1,y1,x2,y2=map(int,re.findall(r'\d+',node.get('bounds')))
    print(call('shell','input','tap',str((x1+x2)//2),str((y1+y2)//2)))
elif args.command=='debug':
    print(call('shell','am','broadcast','-n','com.pianoscorefollower.app.debug/com.pianoscorefollower.app.DebugReceiver',
        '-a','com.pianoscorefollower.DEBUG','--es','command',args.argument,*(['--es','argument',args.value] if args.value else [])))
elif args.command=='screenshot':
    call('shell','screencap','-p','/sdcard/piano-ui.png')
    subprocess.run(adb+['pull','/sdcard/piano-ui.png',args.argument],check=True)
