"""Run upstream Matchmaker on causal frames, without the application's old aligner.

Use an isolated environment with the pinned upstream checkout installed. Input and
reference are mono float32 LE PCM; the default rate/hop matches Android's 44.1k path.
Reference may be precomputed in full. Performance is read only one FFT window at a
time; the reported timestamp is the END of that window (no hidden look-ahead).
"""
import argparse
import json
import time
from collections import deque
from pathlib import Path
import numpy as np
from matchmaker.features.audio import LogSpectralEnergyProcessor, ChromagramProcessor
from matchmaker.dp.oltw_arzt import OnlineTimeWarpingArztFrame


def main():
    p = argparse.ArgumentParser()
    p.add_argument('reference', type=Path)
    p.add_argument('performance', type=Path)
    p.add_argument('output', type=Path)
    p.add_argument('--rate', type=int, default=44100)
    p.add_argument('--hop', type=int, default=1024)
    p.add_argument('--feature', choices=['lse', 'chroma'], default='lse')
    p.add_argument('--fft',type=int,help='chroma FFT size, as supported by upstream')
    p.add_argument('--gate-db', type=float, default=-55)
    p.add_argument('--golden', type=Path)
    p.add_argument('--other-reference',action='append',default=[],help='PCM_PATH:TEMPO_FACTOR (parallel upstream-style selection)')
    args = p.parse_args()
    cls = LogSpectralEnergyProcessor if args.feature == 'lse' else ChromagramProcessor
    options=dict(sample_rate=args.rate,hop_length=args.hop)
    if args.feature=='chroma' and args.fft:options['n_fft']=args.fft
    proc = cls(**options)
    reference = np.fromfile(args.reference, dtype='<f4')
    ref, _ = proc((reference, 0.0))
    ref = np.ascontiguousarray(ref, dtype=np.float32)
    if args.golden:
        args.golden.mkdir(parents=True, exist_ok=True)
        ref.tofile(args.golden / 'reference-features.f32')
    proc = cls(**options)
    nfft = proc.n_fft
    rate = args.rate / args.hop
    tracker = OnlineTimeWarpingArztFrame(reference_features=ref,
        score_positions=np.arange(len(ref), dtype=np.float32),
        frame_rate=rate, ref_frame_to_beat=np.arange(len(ref), dtype=np.float64),
        distance_func='Manhattan', window_size=10, step_size=3, start_window_size=.1)
    hypotheses=[dict(tracker=tracker,features=ref,factor=1.,costs=deque(maxlen=50))]
    for item in args.other_reference:
        path,factor=item.rsplit(':',1)
        other_proc=cls(**options)
        other,_=other_proc((np.fromfile(path,dtype='<f4'),0.0))
        other=np.ascontiguousarray(other,dtype=np.float32)
        other_tracker=OnlineTimeWarpingArztFrame(reference_features=other,
            score_positions=np.arange(len(other),dtype=np.float32),frame_rate=rate,
            ref_frame_to_beat=np.arange(len(other),dtype=np.float64),distance_func='Manhattan',
            window_size=10,step_size=3,start_window_size=.1)
        hypotheses.append(dict(tracker=other_tracker,features=other,factor=float(factor),costs=deque(maxlen=50)))
    selected=0
    started = False
    frames = []
    positions = []
    golden_positions = []
    rows = ['audio_seconds\treference_seconds\tframe\tlevel_db\tactive\tcost\thypothesis']
    t0 = time.perf_counter()
    with args.performance.open('rb') as source:
        window = np.frombuffer(source.read(nfft * 4), dtype='<f4').copy()
        frame = 0
        while len(window) == nfft:
            feature, _ = proc((window, 0.0))
            feature = np.ascontiguousarray(feature[0], dtype=np.float32)
            db = 20 * np.log10(max(1e-9, float(np.sqrt(np.mean(window ** 2)))))
            active = db >= args.gate_db
            if active:
                started = True
            if started and active:
                for hyp in hypotheses:
                    hyp['tracker'](feature,frame/rate)
                    hframe=hyp['tracker'].window_index
                    hyp['costs'].append(float(np.sum(np.abs(hyp['features'][hframe]-feature))))
                means=[np.mean(h['costs']) for h in hypotheses]
                best=int(np.argmin(means))
                if means[best] < means[selected]*.95:selected=best
                tracker=hypotheses[selected]['tracker']
                if args.golden:
                    frames.append(feature)
                    golden_positions.append(tracker.window_index)
            positions.append(tracker.window_index)
            cost=tracker.global_cost_matrix[tracker.window_index+1,0]/max(1,tracker.input_index+tracker.window_index)
            rows.append(f'{(frame * args.hop + nfft) / args.rate:.8f}\t{tracker.window_index / rate*hypotheses[selected]["factor"]:.8f}\t{tracker.window_index}\t{db:.4f}\t{int(active)}\t{cost:.6f}\t{selected}')
            hop = np.frombuffer(source.read(args.hop * 4), dtype='<f4')
            if len(hop) != args.hop:
                break
            window = np.concatenate((window[args.hop:], hop))
            frame += 1
    args.output.write_text('\n'.join(rows) + '\n', encoding='utf-8')
    if args.golden:
        np.asarray(frames, dtype=np.float32).tofile(args.golden / 'input-features.f32')
        np.asarray(golden_positions, dtype='<i4').tofile(args.golden / 'positions.i32')
        (args.golden / 'config.json').write_text(json.dumps(dict(dim=int(ref.shape[1]),
            reference_frames=len(ref), frames=len(positions), hop=args.hop, rate=args.rate)), encoding='utf-8')
    print(json.dumps(dict(reference_frames=len(ref), frames=len(positions),
        elapsed_seconds=time.perf_counter()-t0, last_reference_seconds=positions[-1]/rate)))


if __name__ == '__main__':
    main()
