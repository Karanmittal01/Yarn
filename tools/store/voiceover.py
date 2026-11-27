"""Makes the demo video's voiceover (app/build/yarn-voiceover.wav) with Piper, a free voice engine
that runs offline. tools/store/video.py adds it to the video when the file exists.

    pip install piper-tts
    python tools/store/voiceover.py [voice_dir]

voice_dir holds Piper voices from https://huggingface.co/rhasspy/piper-voices (each a .onnx file and
its .onnx.json): the narrator in VOICE and the Hindi line in HINDI_VOICE. Each LINE starts at its
scene's time in video.py; a line that runs into the next one is spoken a little faster.
"""
import os
import sys
import wave

import numpy as np

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
OUT = os.path.join(ROOT, "app", "build", "yarn-voiceover.wav")
END = 68.0  # video.py's END
RATE = 22050

VOICE = os.environ.get("VOICE", "en_GB-cori-high")
SPEAKER = int(os.environ["SPEAKER"]) if os.environ.get("SPEAKER") else None
HINDI_VOICE = "hi_IN-priyamvada-medium"

# (start in seconds, words). Numbers are spelled the way they should be said.
LINES = [
    (0.25, "One messy inbox. Codes, offers, scams."),
    (4.2, "Meet Yarn."),
    (5.6, "Setting up takes a minute. Make Yarn your messaging app, bring back your chats, and you're done."),
    (15.3, "Pro is free for your first thirty days."),
    (18.5, "Every message, sorted, with real logos."),
    (22.0, "Jump straight to O T Ps, transactions, shopping, travel, or offers."),
    (28.6, "Copy any code in one tap."),
    (32.2, "Translate Hinglish, get smart replies, and pick your SIM."),
    (37.2, "Fake prize, K Y C and loan scams go to Spam."),
    (41.2, "Make it yours, with accent colours and chat wallpapers."),
    (46.2, "Clear thousands of old codes in one tap."),
    (49.6, "Backed up, encrypted, in your Google Drive."),
    (52.8, "All on your phone. No ads. No tracking."),
    (55.8, "In English,"),
    (57.0, ("hindi", "और हिंदी में भी।")),
    (58.2, "Free to use. Pro is just a hundred and ninety-nine rupees a year."),
    (63.3, "Get Yarn, free, on Google Play."),
]


def synth(voice, text, speaker=None, length_scale=1.0):
    from piper import PiperVoice, SynthesisConfig

    cfg = SynthesisConfig(speaker_id=speaker, length_scale=length_scale)
    audio = b"".join(chunk.audio_int16_bytes for chunk in voice.synthesize(text, syn_config=cfg))
    return np.frombuffer(audio, dtype=np.int16).astype(np.float32) / 32768


def main():
    from piper import PiperVoice

    folder = sys.argv[1] if len(sys.argv) > 1 else "."
    narrator = PiperVoice.load(os.path.join(folder, VOICE + ".onnx"))
    hindi = PiperVoice.load(os.path.join(folder, HINDI_VOICE + ".onnx"))
    track = np.zeros(int(END * RATE) + RATE, dtype=np.float32)
    for i, (start, words) in enumerate(LINES):
        voice, speaker, text = (hindi, None, words[1]) if isinstance(words, tuple) else (narrator, SPEAKER, words)
        room = (LINES[i + 1][0] if i + 1 < len(LINES) else END) - start - 0.15
        clip = synth(voice, text, speaker)
        if len(clip) / RATE > room:
            # Speak a little faster rather than run into the next scene (never more than 10%).
            scale = max(0.9, room / (len(clip) / RATE))
            clip = synth(voice, text, speaker, scale)
        # Trim the engine's silence at the ends, then fade in and out over 10 ms.
        loud = np.nonzero(np.abs(clip) > 0.01)[0]
        if len(loud):
            clip = clip[max(0, loud[0] - 200): loud[-1] + 2000]
        fade = min(220, len(clip) // 4)
        clip[:fade] *= np.linspace(0, 1, fade)
        clip[-fade:] *= np.linspace(1, 0, fade)
        at = int(start * RATE)
        track[at: at + len(clip)] += clip[: len(track) - at]
        over = len(clip) / RATE > room + 0.15
        print(f"{start:5.1f}s  {len(clip) / RATE:4.1f}s of {room:4.1f}s  {'TOO LONG  ' if over else ''}{text}")
    # Level to -1 dBFS peak.
    track *= 0.89 / max(1e-6, np.max(np.abs(track)))
    with wave.open(OUT, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(RATE)
        w.writeframes((track[: int(END * RATE)] * 32767).astype(np.int16).tobytes())
    print(OUT)


if __name__ == "__main__":
    main()
