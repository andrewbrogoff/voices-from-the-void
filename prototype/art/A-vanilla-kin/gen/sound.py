"""tuning_fork_strike: modal synthesis of a struck steel-and-copper fork, then a small-room reverb in sox.

Usage: python3 sound.py <out.ogg> [--f0 440] [--seed 7]
Modes: fundamental (long), the fork's clang mode at ~6.2x (short), a faint 3rd mode (very short),
a whisper of 2nd harmonic, and a 0.35 Hz beat from slightly unequal prongs. The strike is a few ms of
band-limited noise. Variants: change --f0 (pitch) and --seed (strike noise) only; keep the rest.
"""

import argparse
import os
import subprocess
import tempfile
import wave

import numpy as np

SR = 44100
DUR, PAD = 2.3, 0.4  # dry ring, then silence the reverb tail decays into


def strike(rng, n, lo=900.0, hi=5500.0, tau=0.0025, amp=0.22):
    """Band-limited noise burst: the metal-on-wood tap. FFT mask with raised-cosine skirts."""
    noise = rng.standard_normal(n)
    spec = np.fft.rfft(noise)
    f = np.fft.rfftfreq(n, 1 / SR)
    mask = np.clip((f - lo * 0.6) / (lo * 0.4), 0, 1) * np.clip((hi * 1.4 - f) / (hi * 0.4), 0, 1)
    burst = np.fft.irfft(spec * (0.5 - 0.5 * np.cos(np.pi * mask)), n)
    t = np.arange(n) / SR
    env = np.exp(-t / tau) * np.minimum(1, t / 0.0004)
    return amp * burst / np.max(np.abs(burst)) * env


def synth(f0, seed, dur=DUR):
    rng = np.random.default_rng(seed)
    t = np.arange(int(dur * SR)) / SR
    # (frequency ratio, amplitude, decay tau in s)
    modes = [(1.0, 1.00, 0.75), (1.0 + 0.35 / f0, 0.10, 0.75), (2.0, 0.025, 0.30),
             (6.21, 0.24, 0.075), (17.4, 0.035, 0.018)]
    x = sum(a * np.sin(2 * np.pi * f0 * r * t) * np.exp(-t / tau) for r, a, tau in modes if f0 * r < 9500)
    x[: 4096] += strike(rng, 4096)
    x *= np.minimum(1, t / 0.0015)  # 1.5 ms onset so the sine start never clicks
    tail = t > dur - 0.6  # raised-cosine fade over the last 0.6 s to true zero
    x[tail] *= 0.5 + 0.5 * np.cos(np.pi * (t[tail] - (dur - 0.6)) / 0.6)
    x = np.concatenate([x, np.zeros(int(PAD * SR))])  # room for the reverb tail
    return x / np.max(np.abs(x)) * 0.7


def write_wav(path, x, seed):
    rng = np.random.default_rng(seed + 1)
    tpdf = (rng.random(len(x)) - rng.random(len(x)))  # seeded TPDF dither, 1 LSB
    pcm = np.clip(np.round(x * 32767 + tpdf), -32768, 32767).astype("<i2")
    with wave.open(path, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes(pcm.tobytes())


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("out")
    ap.add_argument("--f0", type=float, default=440.0)
    ap.add_argument("--seed", type=int, default=7)
    a = ap.parse_args()
    with tempfile.TemporaryDirectory() as d:
        wav = os.path.join(d, "dry.wav")
        write_wav(wav, synth(a.f0, a.seed), a.seed)
        # small room: short, damped, mostly dry; then a gentle 10 kHz lowpass, settle the tail, normalise
        subprocess.run(["sox", "-R", "-D", wav, "-r", "44100", "-c", "1", "-C", "5", a.out,
                        "reverb", "18", "65", "22", "100", "4", "-8", "channels", "1",
                        "lowpass", "9000", "highpass", "60", "fade", "t", "0", "%.2f" % (DUR + PAD - 0.01), "0.35",
                        "gain", "-n", "-1.5"],
                       check=True)


if __name__ == "__main__":
    main()
