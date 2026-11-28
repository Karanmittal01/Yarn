"""Composes the demo video's music and mixes it with the narration into app/build/yarn-soundtrack.wav.

The music is made here, note by note, so it's Yarn's own (no licence to clear). It follows the scenes in
video.py: 120 beats a minute so every cut lands on the beat, a build into the logo landing on the
downbeat, the groove starting as the phone rises, a soft tick on each tap and a chime when a code is
copied, a build into the end card and a last chord. It dips under the narration while it's spoken.

    python tools/store/voiceover.py <model_dir>   # first: the narration
    python tools/store/music.py
"""
import math
import os
import sys
import wave

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import video  # noqa: E402

SR = 48000
OUT = os.path.join(video.ROOT, "app", "build", "yarn-soundtrack.wav")
VOICE = os.path.join(video.ROOT, "app", "build", "yarn-voiceover.wav")
BEAT, END = video.BEAT, video.END
BAR = 4 * BEAT
GROOVE = (video.HOOK_END, video.OUTRO_START)  # drums and bass play between these
LOGO = 4.0  # the logo lands here (video.hook_frame), on a downbeat

# D major, one chord a bar: D, A, Bm, G (I, V, vi, IV). MIDI notes, low to high.
CHORDS = [(50, 54, 57, 62), (45, 52, 57, 61), (47, 54, 59, 62), (43, 50, 55, 59)]
RNG = np.random.default_rng(7)
N = int(END * SR)
L = np.zeros(N, dtype=np.float32)
R = np.zeros(N, dtype=np.float32)
FX = np.zeros(N, dtype=np.float32)  # taps and chimes: not dipped under the voice


def hz(m):
    return 440.0 * 2 ** ((m - 69) / 12)


def add(buf, at, sig, gain=1.0):
    i = int(at * SR)
    if i >= len(buf) or i + len(sig) <= 0:
        return
    if i < 0:
        sig, i = sig[-i:], 0
    n = min(len(sig), len(buf) - i)
    buf[i: i + n] += gain * sig[:n]


def stereo(at, sig, gain, pan=0.0):
    add(L, at, sig, gain * math.cos((pan + 1) * math.pi / 4) * math.sqrt(2))
    add(R, at, sig, gain * math.sin((pan + 1) * math.pi / 4) * math.sqrt(2))


def tt(seconds):
    return np.arange(int(seconds * SR)) / SR


def saw(f, t, harmonics):
    return sum(np.sin(2 * np.pi * f * k * t) / k for k in range(1, harmonics + 1))


def env(t, attack, release, length):
    a = np.clip(t / attack, 0, 1)
    r = np.clip((length - t) / release, 0, 1)
    return a * r


def chord_at(t):
    return CHORDS[int(t // BAR) % len(CHORDS)]


def level(t):
    """How full the music is at [t]: quiet under the hook's start, full in the groove."""
    if t < LOGO:
        return 0.35 + 0.65 * (t / LOGO) ** 2
    return 1.0


# ---- Pad: soft chords, a bar each, overlapping a little ------------------------------------------
for b in range(int(END // BAR) + 1):
    start = b * BAR
    if start >= END:
        break
    last = start >= video.OUTRO_START  # the end card holds one chord to the end
    length = (END - start) if last else BAR + 0.5
    t = tt(length)
    notes = (50, 54, 57, 62) if last else chord_at(start)
    bright = 3 if start < LOGO else 5
    for i, m in enumerate(notes):
        for detune, pan in ((-0.004, -0.6), (0.004, 0.6)):
            sig = saw(hz(m + 12) * (1 + detune), t, bright) * env(t, 0.45, 0.6, length)
            stereo(start, sig.astype(np.float32), 0.022 * level(start), pan * (0.4 + 0.15 * i))

# ---- Arpeggio: eighth notes over the chord, from the logo on -------------------------------------
step = BEAT / 2
pattern = (0, 1, 2, 3, 2, 1, 2, 3)
s = LOGO
k = 0
while s < video.OUTRO_START + 2 * BAR:
    notes = (50, 54, 57, 62) if s >= video.OUTRO_START else chord_at(s)
    m = notes[pattern[k % len(pattern)]] + 24
    t = tt(0.45)
    pluck = (np.sin(2 * np.pi * hz(m) * t) + 0.35 * np.sin(2 * np.pi * hz(m) * 3 * t) / 3) * np.exp(-t / 0.16)
    fade = 1.0 if s < video.OUTRO_START else max(0.0, 1 - (s - video.OUTRO_START) / (2 * BAR))
    stereo(s, pluck.astype(np.float32), 0.045 * fade, -0.5 if k % 2 == 0 else 0.5)
    s += step
    k += 1

# ---- Groove: kick on every beat, clap on 2 and 4, hats on the off-beats, bass ---------------------
kt = tt(0.35)
kick = np.sin(2 * np.pi * np.cumsum(48 + 70 * np.exp(-kt / 0.03)) / SR) * np.exp(-kt / 0.13)
kick[:120] += np.linspace(0.6, 0, 120) * RNG.standard_normal(120)
ct = tt(0.25)
noise = RNG.standard_normal(len(ct))
clap_body = (noise - np.convolve(noise, np.ones(6) / 6, mode="same")) * np.exp(-ct / 0.07)
clap = clap_body.copy()
for d in (0.009, 0.018):
    clap[int(d * SR):] += 0.7 * clap_body[: len(clap) - int(d * SR)]
ht = tt(0.08)
hn = RNG.standard_normal(len(ht))
hat = (hn - np.convolve(hn, np.ones(3) / 3, mode="same")) * np.exp(-ht / 0.022)

beat_t = GROOVE[0]
i = 0
while beat_t < GROOVE[1] - 1e-6:
    stereo(beat_t, kick.astype(np.float32), 0.30)
    if i % 2 == 1:
        stereo(beat_t, clap.astype(np.float32), 0.07, 0.1)
    stereo(beat_t + BEAT / 2, hat.astype(np.float32), 0.035, 0.35)
    # Bass: the chord's root on 1, the & of 2 and 3, with a pickup on the & of 4.
    pos = i % 4
    if pos in (0, 1, 2, 3):
        root = chord_at(beat_t)[0] - 12
        for off, dur in ({0: ((0, 0.4),), 1: ((0.5, 0.25),), 2: ((0, 0.4),), 3: ((0.5, 0.2),)})[pos]:
            t = tt(dur)
            f = hz(root)
            tone = np.sin(2 * np.pi * f * t) + 0.25 * np.sin(4 * np.pi * f * t)
            stereo(beat_t + off * BEAT, (np.tanh(1.5 * tone) * env(t, 0.005, 0.08, dur)).astype(np.float32), 0.11)
    beat_t += BEAT
    i += 1

# ---- Builds into the logo and into the end card, and the hits when they land -------------------
for hit in (LOGO, video.OUTRO_START):
    t = tt(2 * BEAT * 2)
    n = RNG.standard_normal(len(t))
    swell = (n - np.convolve(n, np.ones(4) / 4, mode="same")) * (t / t[-1]) ** 2.5
    stereo(hit - len(t) / SR, swell.astype(np.float32), 0.05)
    bt = tt(2.2)
    boom = np.sin(2 * np.pi * np.cumsum(38 + 30 * np.exp(-bt / 0.05)) / SR) * np.exp(-bt / 0.55)
    stereo(hit, boom.astype(np.float32), 0.32)
    wash = RNG.standard_normal(len(bt))
    wash = np.convolve(wash, np.ones(8) / 8, mode="same") * np.exp(-bt / 0.7)
    stereo(hit, wash.astype(np.float32), 0.05)

# ---- The screen: a soft tick on each tap, a chime when a code is copied, air on each fade --------
tick_t = tt(0.06)
tick = (np.sin(2 * np.pi * 1700 * tick_t) + 0.3 * np.sin(2 * np.pi * 3400 * tick_t)) * np.exp(-tick_t / 0.011)
for sc in video.TIMELINE:
    for tap in sc.get("taps", []):
        add(FX, sc["t0"] + tap[0], tick.astype(np.float32), 0.09)
    if "toast" in sc:
        at = sc["t0"] + sc["toast"][0]
        for j, m in enumerate((81, 88)):
            ct2 = tt(0.6)
            add(FX, at + 0.08 * j, (np.sin(2 * np.pi * hz(m) * ct2) * np.exp(-ct2 / 0.22)).astype(np.float32), 0.05)
    if sc["enter"] == "fade":
        wt = tt(0.5)
        wn = RNG.standard_normal(len(wt))
        whoosh = np.convolve(wn, np.ones(12) / 12, mode="same") * np.sin(np.pi * wt / wt[-1]) ** 2
        stereo(sc["t0"] - 0.25, whoosh.astype(np.float32), 0.025)


def voice_track():
    if not os.path.exists(VOICE):
        print("no narration yet (tools/store/voiceover.py): music only")
        return np.zeros(N, dtype=np.float32)
    with wave.open(VOICE) as w:
        rate = w.getframerate()
        v = np.frombuffer(w.readframes(w.getnframes()), dtype=np.int16).astype(np.float32) / 32768
    x = np.arange(N) / SR
    return np.interp(x, np.arange(len(v)) / rate, v, right=0).astype(np.float32)


def main():
    voice = voice_track()
    # Dip the music under the voice: follow its loudness (fast in, slow out), up to about -9 dB.
    hop = SR // 200
    frames = np.abs(voice[: len(voice) // hop * hop]).reshape(-1, hop).max(axis=1)
    envl = np.zeros_like(frames)
    e = 0.0
    for j, f in enumerate(frames):
        e = f if f > e else e * 0.985  # ~0.33 s to recover
        envl[j] = e
    duck = 1 - 0.65 * np.clip(envl / 0.12, 0, 1)
    duck = np.convolve(duck, np.ones(6) / 6, mode="same")  # no clicks
    gain = np.interp(np.arange(N), np.arange(len(duck)) * hop, duck).astype(np.float32)
    music_l, music_r = L * gain, R * gain
    mix_l = music_l + voice + FX
    mix_r = music_r + voice + FX
    # Fade in and out with the picture.
    ramp = np.ones(N, dtype=np.float32)
    ramp[: int(0.3 * SR)] = np.linspace(0, 1, int(0.3 * SR))
    tail = int(1.5 * SR)
    ramp[-tail:] = np.linspace(1, 0, tail) ** 1.5
    mix = np.stack([mix_l, mix_r], axis=1) * ramp[:, None]
    # Gentle limiting, then -1 dBFS peak.
    mix = np.tanh(1.1 * mix) / math.tanh(1.1)
    mix *= 0.89 / max(1e-6, float(np.max(np.abs(mix))))
    rms = lambda a: 20 * math.log10(max(1e-9, float(np.sqrt(np.mean(a ** 2)))))
    speaking = np.abs(voice) > 0.02
    print(f"music alone {rms(L[~speaking]):.1f} dB, under the voice {rms(music_l[speaking]):.1f} dB, voice {rms(voice[speaking]):.1f} dB")
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with wave.open(OUT, "wb") as w:
        w.setnchannels(2)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes((mix * 32767).astype(np.int16).tobytes())
    print(OUT, f"{END:.1f}s")


if __name__ == "__main__":
    main()
