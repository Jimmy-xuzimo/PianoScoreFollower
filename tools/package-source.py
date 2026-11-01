"""Package repository sources without build state, corpora or local signing files."""
import argparse
import subprocess
import zipfile
from pathlib import Path

p = argparse.ArgumentParser()
p.add_argument('destination')
args = p.parse_args()
repo = Path(__file__).resolve().parent.parent
names = subprocess.check_output(['git','ls-files','--cached','--others','--exclude-standard','-z'],cwd=repo).decode().split('\0')
target = Path(args.destination).resolve()
target.parent.mkdir(parents=True,exist_ok=True)
with zipfile.ZipFile(target,'w',zipfile.ZIP_DEFLATED,compresslevel=6) as archive:
    for name in sorted(set(names)):
        if not name or not (repo/name).is_file():
            continue
        if name.endswith(('.keystore','.jks','.pyc')) or Path(name).name in ('local.properties','keystore.properties'):
            raise RuntimeError('Local-only material must not be tracked: '+name)
        archive.write(repo/name,'PianoScoreFollower/'+name)
print(target.name,target.stat().st_size,'bytes')
