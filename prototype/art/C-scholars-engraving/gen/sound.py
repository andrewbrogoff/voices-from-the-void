"""Tuning Fork strike, Scholar's Engraving: a felt-struck fork stood on a wooden resonator box, in a quiet room.

numpy builds the dry signal; sox adds the room, gentle EQ, the tail fade, loudness and Vorbis.
Variants: change PITCH (one fork per note) or SEED (felt contact noise). Usage: sound.py <out.ogg> [pitch_hz] [seed]
"""

import os
import subprocess
import sys
import tempfile
import wave

import numpy as np

SR = 44100
PITCH = 293.66  # D4: low enough to stay warm, high enough to read as a fork, not a bell
SEED = 7
LENGTH = 2.6


def one_pole_lowpass(x, hz):
    a = np.exp(-2 * np.pi * hz / SR)
    y = np.empty_like(x)
    acc = 0.0
    for i, v in enumerate(x):
        acc = (1 - a) * v + a * acc
        y[i] = acc
    return y


def strike(f0=PITCH, seed=SEED, dur=LENGTH + 0.4):
    rng = np.random.default_rng(seed)
    t = np.arange(int(SR * dur)) / SR
    felt = 0.5 - 0.5 * np.cos(np.pi * np.clip(t / 0.007, 0, 1))  # 7 ms felt attack: no click
    fork = np.sin(2 * np.pi * f0 * t) * np.exp(-t / 1.3) * felt
    clang = 0.035 * np.sin(2 * np.pi * 6.27 * f0 * t + 0.4) * np.exp(-t / 0.045) * felt  # fork's clang mode, muted by felt
    bloom = (1 - np.exp(-t / 0.08)) * np.exp(-t / 0.9)  # the box answers a beat late
    box = 0.6 * np.sin(2 * np.pi * f0 * 1.0006 * t + 1.1) * bloom  # tiny detune: a slow, warm beat
    box += 0.05 * np.sin(2 * np.pi * 2 * f0 * t + 0.2) * bloom * np.exp(-t / 0.35)
    knock = sum(  # the tabletop / box body: short, low, wooden
        a * np.sin(2 * np.pi * f * t + p) * np.exp(-t / tau) * felt
        for f, a, tau, p in [(118, 0.22, 0.09, 0.0), (186, 0.14, 0.06, 0.7), (305, 0.06, 0.035, 1.9)]
    )
    contact = one_pole_lowpass(rng.standard_normal(len(t)) * np.exp(-t / 0.005), 900) * 0.6
    y = fork + clang + box + knock + contact
    return y / np.max(np.abs(y)) * 0.5


def write_wav(path, y):
    with wave.open(path, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes((np.clip(y, -1, 1) * 32767).astype("<i2").tobytes())


def main():
    out = sys.argv[1]
    f0 = float(sys.argv[2]) if len(sys.argv) > 2 else PITCH
    seed = int(sys.argv[3]) if len(sys.argv) > 3 else SEED
    with tempfile.TemporaryDirectory() as tmp:
        dry = os.path.join(tmp, "dry.wav")
        write_wav(dry, strike(f0, seed))
        subprocess.run(
            ["sox", "-R", dry, "-C", "5", out,
             "highpass", "45", "lowpass", "5000",               # nothing shrill survives
             "reverb", "28", "70", "35", "100", "12", "-4",    # small wooden room, damped highs
             "trim", "0", str(LENGTH), "fade", "h", "0", str(LENGTH), "0.9",
             "gain", "-n", "-1.5"],                            # peak -1.5 dBFS before Vorbis
            check=True,
        )


if __name__ == "__main__":
    main()
