"""Validate the Kotlin writer with mido, an independent SMF parser."""
from pathlib import Path
import mido

file=Path(__file__).resolve().parent.parent/'app/build/roundtrip/recording.mid'
midi=mido.MidiFile(file)
expected=[('note_on',3,60,95,123456),('control_change',3,64,127,234567),
          ('note_off',3,60,12,456789),('control_change',3,64,0,876543)]
actual=[];seconds=0
for event in midi:
    seconds+=event.time
    if event.is_meta:continue
    if event.type in ['note_on','note_off']:
        actual.append((event.type,event.channel,event.note,event.velocity,round(seconds*1e6)))
    elif event.type=='control_change':
        actual.append((event.type,event.channel,event.control,event.value,round(seconds*1e6)))
for item in expected:
    matches=[x for x in actual if x[:4]==item[:4] and abs(x[4]-item[4])<=522]
    assert matches,f'Missing or mistimed event: {item}'
assert len([x for x in actual if x[0]=='note_on'])==1
print('PASS: pitch, velocity, MIDI channel, sustain and source times survive SMF roundtrip (<=1 tick).')
