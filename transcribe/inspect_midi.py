"""Summarise a MIDI file: tempo, tracks, note ranges, and per-track note counts."""
import struct
import sys


def read_varlen(data, offset):
    value = 0
    while True:
        byte = data[offset]
        offset += 1
        value = (value << 7) | (byte & 0x7F)
        if not byte & 0x80:
            return value, offset


def parse(path):
    data = open(path, "rb").read()
    offset = 14
    tracks = []
    while offset < len(data) and data[offset:offset + 4] == b"MTrk":
        length = struct.unpack(">I", data[offset + 4:offset + 8])[0]
        tracks.append(data[offset + 8:offset + 8 + length])
        offset += 8 + length

    results = []
    for index, track in enumerate(tracks):
        offset = 0
        tick = 0
        running = 0
        name = None
        tempos = []
        time_sigs = []
        key_sigs = []
        notes = []
        active = {}
        program = None

        while offset < len(track):
            delta, offset = read_varlen(track, offset)
            tick += delta
            status = track[offset]
            if status & 0x80:
                offset += 1
                running = status
            else:
                status = running

            kind = status & 0xF0
            channel = status & 0x0F

            if status == 0xFF:
                meta = track[offset]
                offset += 1
                length, offset = read_varlen(track, offset)
                payload = track[offset:offset + length]
                offset += length
                if meta == 0x03:
                    name = payload.decode("latin-1", "replace")
                elif meta == 0x51:
                    micros = int.from_bytes(payload, "big")
                    tempos.append((tick, round(60_000_000 / micros)))
                elif meta == 0x58:
                    time_sigs.append((tick, payload[0], 1 << payload[1]))
                elif meta == 0x59:
                    key_sigs.append((tick, payload[0], payload[1]))
            elif status in (0xF0, 0xF7):
                length, offset = read_varlen(track, offset)
                offset += length
            elif kind in (0x80, 0x90, 0xA0, 0xB0, 0xE0):
                a = track[offset]
                b = track[offset + 1]
                offset += 2
                if kind == 0x90 and b > 0:
                    active.setdefault((channel, a), []).append(tick)
                elif kind == 0x80 or (kind == 0x90 and b == 0):
                    starts = active.get((channel, a))
                    if starts:
                        start = starts.pop(0)
                        notes.append((start, tick - start, a, channel))
            elif kind in (0xC0, 0xD0):
                value = track[offset]
                offset += 1
                if kind == 0xC0:
                    program = value

        results.append({
            "index": index,
            "name": name,
            "program": program,
            "tempos": tempos,
            "timeSigs": time_sigs,
            "keySigs": key_sigs,
            "notes": notes,
        })
    return results


def main(path):
    for track in parse(path):
        notes = track["notes"]
        print(f"--- track {track['index']} name={track['name']!r} program={track['program']}")
        print(f"    tempos={track['tempos'][:4]} timeSigs={track['timeSigs'][:3]} keySigs={track['keySigs'][:3]}")
        print(f"    notes={len(notes)}")
        if notes:
            pitches = [n[2] for n in notes]
            channels = sorted({n[3] for n in notes})
            last = max(n[0] + n[1] for n in notes)
            print(f"    pitch range {min(pitches)}..{max(pitches)} channels={channels}")
            print(f"    last event tick={last}  (~{last / 480:.1f} quarters)")
            # Simultaneous attacks per channel hint at how chords will be grouped.
            onsets = {}
            for start, _, _, channel in notes:
                onsets[(start, channel)] = onsets.get((start, channel), 0) + 1
            sizes = sorted(onsets.values(), reverse=True)
            print(f"    max notes sharing one onset={sizes[0] if sizes else 0}")


if __name__ == "__main__":
    main(sys.argv[1])