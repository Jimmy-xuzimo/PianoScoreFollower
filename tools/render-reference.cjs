// Render the exact imported score with the APK's alphaTab and piano soundfont.
// No browser, audio device or external synthesis dependency is required.
const fs = require('node:fs');
const at = require('../app/src/main/assets/alphatab/alphaTab.min.js');
const [scorePath, outputPath, rate = '44100', tempoFactor = '1'] = process.argv.slice(2);
if (!scorePath || !outputPath) throw new Error('usage: node render-reference.cjs score.musicxml reference.f32 [rate]');
const score = at.importer.ScoreLoader.loadScoreFromBytes(fs.readFileSync(scorePath));
const midi = new at.midi.MidiFile();
const generator = new at.midi.MidiFileGenerator(score, new at.Settings(), new at.midi.AlphaSynthMidiFileHandler(midi));
generator.generate();
for(const track of midi.tracks) for(const event of track.events)
    if(event instanceof at.midi.TempoChangeEvent)event.beatsPerMinute*=Number(tempoFactor);
const options = new at.synth.AudioExportOptions();
options.sampleRate = Number(rate);
options.soundFonts = [fs.readFileSync(require('node:path').join(__dirname, '../app/src/main/assets/alphatab/soundfont/piano.sf2'))];
const renderer = at.synth.AlphaSynth.prototype.exportAudio.call(null, options, midi, [], new Map());
const fd = fs.openSync(outputPath, 'w');
let samples = 0;
try {
    for (let chunk; (chunk = renderer.render(1000));) {
        const mono = new Float32Array(chunk.samples.length / 2);
        for (let i = 0; i < mono.length; i++) mono[i] = (chunk.samples[2 * i] + chunk.samples[2 * i + 1]) * .5;
        fs.writeSync(fd, Buffer.from(mono.buffer)); samples += mono.length;
    }
} finally { fs.closeSync(fd); }
const mapping = generator.tickLookup.masterBars.map(bar => ({
    start: bar.start, end: bar.end, time: bar.startTime, duration: bar.duration,
    tempos: bar.tempoChanges.map(t => ({ tick: t.tick, tempo: t.tempo, time: t.time }))
}));
fs.writeFileSync(outputPath + '.json', JSON.stringify({ sampleRate: options.sampleRate, tempoFactor:Number(tempoFactor), samples, bars: mapping }, null, 2));
console.log(JSON.stringify({ samples, seconds: samples / options.sampleRate }));
