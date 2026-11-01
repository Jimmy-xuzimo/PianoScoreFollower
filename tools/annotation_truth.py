"""Resolve annotation notes onto the *played* score, including repeat visits.

Match files may unfold measure numbers whereas MusicXML keeps repeat signs.
Explicit source note IDs take precedence over printed measure labels.
"""
import re
from fractions import Fraction
import xml.etree.ElementTree as ET


def annotation_anchors(xml_path, graphlines, data, clock, units):
    xml = ET.parse(xml_path)
    measures = xml.getroot().find('part').findall('measure')
    names = {m.get('number'): i for i, m in enumerate(measures)}
    barstarts, visits = {}, {}
    for line in graphlines:
        measure, occurrence = int(line[2]), int(line[3])
        barstarts.setdefault(measure, int(line[4]))
        visits.setdefault((measure, occurrence), int(line[0]) - (int(line[4]) - barstarts[measure]))
    ids = {}
    # A separate cursor per part follows MusicXML backup/forward semantics.
    for part in xml.getroot().findall('part'):
        division = 1
        for index, measure in enumerate(part.findall('measure')):
            cursor, chord_at = Fraction(0), Fraction(0)
            for item in measure:
                if item.tag == 'attributes':
                    division = int(item.findtext('divisions', str(division)))
                elif item.tag in ('backup', 'forward'):
                    cursor += Fraction(int(item.findtext('duration')), division) * (-1 if item.tag == 'backup' else 1)
                elif item.tag == 'note':
                    chord = item.find('chord') is not None
                    if not chord:
                        chord_at = cursor
                    if item.get('id') and item.find('pitch') is not None:
                        ids[item.get('id')] = (index, chord_at * 960)
                    if not chord:
                        cursor += Fraction(int(item.findtext('duration', '0')), division)
    pattern = r'snote\(([^,]+),\[[^\]]+\],[^,]+,([^:,]+):(\d+),([^,]+),[^,]+,([\-\d.]+),[^,]+,\[[^\]]*\]\)-note\([^,]+,\d+,(\d+),'
    matches = re.findall(pattern, data)
    numeric = [int(m[1]) for m in matches if m[1].lstrip('-').isdigit()]
    shift = int(measures[0].get('number')) - min(numeric) if numeric else 0
    # Some Vienna XML files omit IDs. Their first-visit match records establish
    # the source note's printed position; later visits refer to that same note.
    # Include deletions here so a missed first-visit note can still be resolved.
    for note_id, measure, beat, offset in re.findall(r'snote\(([^,]+),\[[^\]]+\],[^,]+,([^:,]+):(\d+),([^,]+),', data):
        source_id, dash, visit = note_id.rpartition('-')
        if not dash or visit != '1' or source_id in ids:
            continue
        index = names.get(str(int(measure) + shift)) if measure.lstrip('-').isdigit() else names.get(measure)
        if index is not None and (index, 1) in visits:
            ids[source_id] = (index, ((int(beat)-1)+Fraction(offset)*4)*960)
    anchors = []
    for note_id, measure, beat, offset, _q, time in matches:
        source_id, dash, visit = note_id.rpartition('-')
        explicit = ids.get(note_id) or (ids.get(source_id) if dash and visit.isdigit() else None)
        if explicit:
            index, relative = explicit
            occurrence = int(visit) - 1 if dash and visit.isdigit() else 0
            start = visits.get((index, occurrence))
            if start is None:
                raise ValueError(f'Annotation repeat visit absent from score: {note_id}')
            expected = start + float(relative)
        else:
            index = names.get(str(int(measure) + shift)) if measure.lstrip('-').isdigit() else names.get(measure)
            if index is None or index not in barstarts:
                continue
            expected = barstarts[index] + ((int(beat) - 1) + float(Fraction(offset)) * 4) * 960
        anchors.append((int(time) * clock / units / 1e6 + 3, expected / 960))
    return anchors
