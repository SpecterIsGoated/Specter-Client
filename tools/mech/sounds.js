// Synthesises the Jet Mech sound effects (all original, made from oscillators and noise - no samples).
//
//   node tools/mech/sounds.js <out-dir>
//
// Writes 16-bit mono WAV files; convert them to Ogg Vorbis (Minecraft needs mono for positional sound), e.g.
//   for f in <out-dir>/*.wav; do ffmpeg -y -i "$f" -c:a libvorbis -q:a 5 "${f%.wav}.ogg"; done
const fs = require('fs');
const path = require('path');

const RATE = 44100;
const out = process.argv[2] || '.';
fs.mkdirSync(out, { recursive: true });

// ------------------------------------------------------------------ helpers
let seed = 1234567;
function rnd() {
  seed = (seed * 1664525 + 1013904223) >>> 0;
  return seed / 4294967296;
}
const noise = () => rnd() * 2 - 1;
function buf(seconds) {
  return new Float32Array(Math.round(seconds * RATE));
}
function onePoleLow(x, cutoff) {
  const y = new Float32Array(x.length);
  const a = Math.exp(-2 * Math.PI * cutoff / RATE);
  let s = 0;
  for (let i = 0; i < x.length; i++) { s = (1 - a) * x[i] + a * s; y[i] = s; }
  return y;
}
function onePoleHigh(x, cutoff) {
  const lo = onePoleLow(x, cutoff);
  const y = new Float32Array(x.length);
  for (let i = 0; i < x.length; i++) y[i] = x[i] - lo[i];
  return y;
}
// resonant band pass (state variable filter), cutoff may be a function of time (seconds)
function bandPass(x, cutoff, q) {
  const y = new Float32Array(x.length);
  let low = 0, band = 0;
  for (let i = 0; i < x.length; i++) {
    const fc = typeof cutoff === 'function' ? cutoff(i / RATE) : cutoff;
    const f = 2 * Math.sin(Math.PI * Math.min(fc, RATE / 6) / RATE);
    low += f * band;
    const high = x[i] - low - band / q;
    band += f * high;
    y[i] = band;
  }
  return y;
}
function mix(dst, src, gain = 1, at = 0) {
  const off = Math.round(at * RATE);
  for (let i = 0; i < src.length && i + off < dst.length; i++) if (i + off >= 0) dst[i + off] += src[i] * gain;
  return dst;
}
function env(x, attack, hold, release) {
  const a = attack * RATE, h = hold * RATE, r = release * RATE;
  for (let i = 0; i < x.length; i++) {
    let g;
    if (i < a) g = i / a;
    else if (i < a + h) g = 1;
    else g = Math.max(0, 1 - (i - a - h) / r);
    x[i] *= g;
  }
  return x;
}
function normalize(x, peak = 0.89) {
  let m = 0;
  for (const v of x) m = Math.max(m, Math.abs(v));
  if (m > 0) for (let i = 0; i < x.length; i++) x[i] *= peak / m;
  return x;
}
function softClip(x, drive = 1.5) {
  for (let i = 0; i < x.length; i++) x[i] = Math.tanh(x[i] * drive) / Math.tanh(drive);
  return x;
}
function writeWav(name, x) {
  const data = Buffer.alloc(44 + x.length * 2);
  data.write('RIFF', 0); data.writeUInt32LE(36 + x.length * 2, 4); data.write('WAVE', 8);
  data.write('fmt ', 12); data.writeUInt32LE(16, 16); data.writeUInt16LE(1, 20); data.writeUInt16LE(1, 22);
  data.writeUInt32LE(RATE, 24); data.writeUInt32LE(RATE * 2, 28); data.writeUInt16LE(2, 32); data.writeUInt16LE(16, 34);
  data.write('data', 36); data.writeUInt32LE(x.length * 2, 40);
  for (let i = 0; i < x.length; i++) data.writeInt16LE(Math.max(-32767, Math.min(32767, Math.round(x[i] * 32767))), 44 + i * 2);
  fs.writeFileSync(path.join(out, name + '.wav'), data);
  console.log('wrote', name, (x.length / RATE).toFixed(2) + 's');
}

// ------------------------------------------------------------------ building blocks
// electric servo: a buzzy saw-ish tone sweeping between two pitches, with gear chatter
function servo(seconds, f0, f1, gain = 1) {
  const x = buf(seconds);
  let ph = 0;
  for (let i = 0; i < x.length; i++) {
    const t = i / x.length;
    const s = t * t * (3 - 2 * t);
    const f = f0 + (f1 - f0) * s;
    ph += f / RATE;
    const saw = 2 * (ph % 1) - 1;
    const chatter = 0.6 + 0.4 * Math.sign(Math.sin(2 * Math.PI * ph * 0.125));
    x[i] = (saw * 0.5 + Math.sin(2 * Math.PI * ph * 2) * 0.3) * chatter;
  }
  const y = bandPass(x, t => f0 * 2 + (f1 - f0) * 2 * (t / seconds), 1.4);
  env(y, 0.03, seconds - 0.1, 0.07);
  for (let i = 0; i < y.length; i++) y[i] *= gain;
  return y;
}
// metal clunk: low thump + a few inharmonic ringing partials
function clunk(weight = 1, ring = 1) {
  const x = buf(0.6);
  const partials = [[180, 0.5], [437, 0.35], [1013, 0.22], [1789, 0.14], [2617, 0.08]];
  for (let i = 0; i < x.length; i++) {
    const t = i / RATE;
    let v = Math.sin(2 * Math.PI * (55 + 40 * Math.exp(-t * 30)) * t) * Math.exp(-t * 18) * weight;
    for (const [f, a] of partials) v += Math.sin(2 * Math.PI * f * t + f) * a * ring * Math.exp(-t * (9 + f / 300));
    v += noise() * Math.exp(-t * 90) * 0.5;
    x[i] = v;
  }
  return x;
}
// hydraulic / pneumatic hiss
function hiss(seconds, center = 3500, gain = 0.6) {
  const x = buf(seconds);
  for (let i = 0; i < x.length; i++) x[i] = noise();
  const y = bandPass(x, center, 0.8);
  env(y, 0.01, seconds * 0.3, seconds * 0.7);
  for (let i = 0; i < y.length; i++) y[i] *= gain;
  return y;
}
// low rumble (brown-ish noise)
function rumble(seconds, cutoff = 160) {
  const x = buf(seconds);
  for (let i = 0; i < x.length; i++) x[i] = noise();
  return onePoleLow(onePoleLow(x, cutoff), cutoff * 1.5);
}

// ------------------------------------------------------------------ sounds
function transform(reverse) {
  const len = 1.6;
  const x = buf(len);
  const servos = reverse
    ? [[0.0, 0.32, 820, 340], [0.22, 0.36, 960, 420], [0.5, 0.3, 700, 300], [0.78, 0.34, 880, 360], [1.05, 0.3, 620, 260]]
    : [[0.0, 0.32, 300, 760], [0.2, 0.38, 380, 900], [0.48, 0.3, 320, 820], [0.74, 0.34, 420, 1000], [1.0, 0.3, 360, 880]];
  for (const [at, d, a, b] of servos) mix(x, servo(d, a, b, 0.55), 1, at);
  const clunks = reverse ? [0.18, 0.46, 0.74, 1.0, 1.32] : [0.3, 0.6, 0.86, 1.12, 1.36];
  clunks.forEach((at, i) => mix(x, clunk(i === clunks.length - 1 ? 1.4 : 0.7, 0.8), i === clunks.length - 1 ? 1.1 : 0.7, at));
  mix(x, hiss(0.35, 4200, 0.5), 1, reverse ? 0.05 : 0.12);
  mix(x, hiss(0.25, 2600, 0.4), 1, reverse ? 0.9 : 1.2);
  return normalize(softClip(x, 1.4));
}

function ignite() {
  const len = 1.5;
  const x = buf(len);
  // whoomp: low boom with a pitch drop + noise burst
  const boom = buf(0.9);
  for (let i = 0; i < boom.length; i++) {
    const t = i / RATE;
    boom[i] = Math.sin(2 * Math.PI * (40 + 90 * Math.exp(-t * 9)) * t) * Math.exp(-t * 5);
  }
  mix(x, boom, 1.0);
  const burst = rumble(0.8, 600);
  env(burst, 0.005, 0.05, 0.7);
  mix(x, burst, 2.5);
  // turbine spool-up whine
  const whine = buf(1.3);
  let ph = 0;
  for (let i = 0; i < whine.length; i++) {
    const t = i / RATE;
    const f = 300 + 1500 * (1 - Math.exp(-t * 2.2));
    ph += f / RATE;
    whine[i] = (Math.sin(2 * Math.PI * ph) * 0.5 + Math.sin(2 * Math.PI * ph * 2.01) * 0.25) * Math.min(1, t * 4) * Math.exp(-Math.max(0, t - 0.8) * 4);
  }
  mix(x, whine, 0.35, 0.15);
  const air = rumble(1.4, 2500);
  env(air, 0.1, 0.6, 0.7);
  mix(x, onePoleHigh(air, 400), 1.2, 0.05);
  return normalize(softClip(x, 1.6));
}

function afterburner() {
  const len = 1.7;
  const x = buf(len);
  const roar = rumble(len, 900);
  for (let i = 0; i < roar.length; i++) {
    const t = i / RATE;
    const crackle = rnd() < 0.002 ? noise() * 6 : 0;
    roar[i] = roar[i] * (0.6 + 0.4 * Math.sin(t * 50 + Math.sin(t * 13) * 3)) + crackle * Math.exp(-t * 1.2);
  }
  env(roar, 0.04, 0.7, 0.95);
  mix(x, roar, 3.0);
  const low = rumble(len, 90);
  env(low, 0.02, 0.6, 1.0);
  mix(x, low, 6.0);
  const pop = clunk(1.2, 0.1);
  mix(x, pop, 0.6);
  return normalize(softClip(x, 2.0));
}

// seamless 2 s loop: every tonal component completes a whole number of cycles, the noise is cross-faded
function engineLoop() {
  const len = 2.0;
  const n = Math.round(len * RATE);
  const fade = Math.round(0.25 * RATE);
  const raw = new Float32Array(n + fade);
  for (let i = 0; i < raw.length; i++) raw[i] = noise();
  const roar = onePoleLow(bandPass(raw, 700, 0.6), 2500);
  const hissy = onePoleHigh(onePoleLow(raw, 7000), 2500);
  const x = buf(len);
  for (let i = 0; i < n; i++) {
    let v = roar[i] * 1.6 + hissy[i] * 0.25;
    if (i < fade) {
      const w = i / fade;
      v = v * w + (roar[n + i] * 1.6 + hissy[n + i] * 0.25) * (1 - w);
    }
    x[i] = v;
  }
  const cycles = f => Math.round(f * len) / len; // snap to whole cycles over the loop
  const tones = [[cycles(118), 0.18], [cycles(1240), 0.10], [cycles(2482), 0.05], [cycles(3721), 0.025]];
  for (let i = 0; i < n; i++) {
    const t = i / RATE;
    for (const [f, a] of tones) x[i] += Math.sin(2 * Math.PI * f * t) * a * (1 + 0.15 * Math.sin(2 * Math.PI * cycles(3) * t));
  }
  return normalize(x, 0.75);
}

function step() {
  const x = buf(0.45);
  mix(x, clunk(1.0, 0.55), 0.9);
  mix(x, servo(0.12, 500, 260, 0.25), 1, 0.04);
  return normalize(x, 0.8);
}

function land() {
  const x = buf(0.9);
  const thud = buf(0.7);
  for (let i = 0; i < thud.length; i++) {
    const t = i / RATE;
    thud[i] = Math.sin(2 * Math.PI * (38 + 50 * Math.exp(-t * 20)) * t) * Math.exp(-t * 7);
  }
  mix(x, thud, 1.2);
  mix(x, clunk(1.3, 0.9), 0.8);
  mix(x, hiss(0.4, 3000, 0.45), 1, 0.08);
  return normalize(softClip(x, 1.5));
}

writeWav('transform', transform(false));
writeWav('transform_back', transform(true));
writeWav('ignite', ignite());
writeWav('afterburner', afterburner());
writeWav('engine', engineLoop());
writeWav('step', step());
writeWav('land', land());
