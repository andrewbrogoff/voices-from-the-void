"""Ink & Brass tuning fork strike. Usage: python3 sound.py <out.ogg> [pitch_ratio] [seed]

Additive synthesis in numpy (dry, deterministic), then sox for space and the Vorbis encode:
  fundamental + a detuned pair an octave up (slow beating shimmer) + a fast-dying clang partial
  + a soft felt strike (filtered noise), into a pre-delayed airy reverb, normalised to -1.5 dBFS.
Variants: pitch_ratio scales every partial (keep within 0.94..1.06); seed only changes the strike noise.
"""

import os
import subprocess
import sys
import tempfile
import wave

import numpy as np

SR = 44100
F0 = 523.25  # C5: bright enough to read as a fork, low enough to stay sweet
DUR = 2.4  # dry length; reverb tail adds the rest
LENGTH = 2.8  # final length, s


def synth(pitch=1.0, seed=1307):
    t = np.arange(int(SR * DUR)) / SR
    f = F0 * pitch
    partials = [  # (freq Hz, amplitude, decay tau s)
        (f, 1.0, 1.1),
        (2 * f * (1 - 0.0006), 0.20, 0.8),  # detuned pair: ~1.25 Hz beat, the shimmer
        (2 * f * (1 + 0.0006), 0.20, 0.8),
        (6.27 * f, 0.10, 0.05),  # the fork's real clang mode: a short "ting", gone in ~0.2 s
    ]
    y = sum(a * np.exp(-t / tau) * np.sin(2 * np.pi * fr * t) for fr, a, tau in partials)
    att = int(0.004 * SR)  # 4 ms raised-cosine onset: a soft strike, no click
    y[:att] *= 0.5 - 0.5 * np.cos(np.linspace(0, np.pi, att))
    rng = np.random.RandomState(seed)
    n = rng.randn(int(0.03 * SR)) * np.exp(-np.arange(int(0.03 * SR)) / (0.006 * SR))
    for _ in range(2):  # two one-pole lowpasses ~1.8 kHz: felt, not metal-on-metal
        a = np.exp(-2 * np.pi * 1800 / SR)
        for i in range(1, len(n)):
            n[i] = (1 - a) * n[i] + a * n[i - 1]
    y[: len(n)] += 0.6 * n / np.abs(n).max()
    rel = int(0.6 * SR)  # dry tail fades to true zero before the reverb takes over
    y[-rel:] *= np.linspace(1, 0, rel) ** 2
    y = np.concatenate([y, np.zeros(SR)])  # 1 s of room for the reverb tail
    return y / np.abs(y).max() * 0.5


def write_wav(path, y):
    with wave.open(path, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes((y * 32767).astype("<i2").tobytes())


def main():
    out = sys.argv[1]
    pitch = float(sys.argv[2]) if len(sys.argv) > 2 else 1.0
    seed = int(sys.argv[3]) if len(sys.argv) > 3 else 1307
    os.makedirs(os.path.dirname(os.path.abspath(out)), exist_ok=True)
    with tempfile.TemporaryDirectory() as tmp:
        dry = os.path.join(tmp, "dry.wav")
        write_wav(dry, synth(pitch, seed))
        subprocess.run(
            ["sox", "-R", dry, "-r", str(SR), "-c", "1", "-C", "5", out,
             "highpass", "60", "lowpass", "8000",
             "reverb", "60", "50", "90", "0", "40", "-4",  # reverberance, HF damp, room, stereo, pre-delay ms, wet dB
             "remix", "-", "lowpass", "9000",
             "trim", "0", str(LENGTH),
             "fade", "q", "0.002", str(LENGTH), "0.9",
             "gain", "-n", "-1.5"],
            check=True)
    print("wrote", out)


if __name__ == "__main__":
    main()
