"""Makes the demo video's narration (app/build/yarn-voiceover.wav) with Kokoro, a free voice model that
runs offline. The words and when each is spoken come from the scenes in video.py (`say`), so the voice
always starts with its scene. tools/store/music.py then mixes it with the music.

    pip install kokoro-onnx soundfile
    python tools/store/voiceover.py <model_dir>

model_dir holds kokoro-v1.0.onnx and voices-v1.0.bin from
https://github.com/thewh1teagle/kokoro-onnx/releases (model-files-v1.0). A line that would run into
the next one stops the build: lengthen its scene in video.py (beats) or shorten the words.
"""
import os
import sys
import wave

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import video  # noqa: E402

OUT = os.path.join(video.ROOT, "app", "build", "yarn-voiceover.wav")
RATE = 24000
# An Indian woman narrates, in English and in Hindi: Kokoro's Hindi voice reads English with an Indian
# accent, and British spelling-to-sound rules sit closer to Indian English than American ones.
VOICE = os.environ.get("VOICE", "hf_alpha")
VOICES = {"narrator": (VOICE, os.environ.get("LANG_EN", "en-gb")), "hindi": (VOICE, "hi")}
SPEED = float(os.environ.get("SPEED", "1.0"))
LEVEL = 0.09  # every line's loudness (RMS), so the narrator and the Hindi voice match
GAP = 0.25  # at least this much quiet before the next line


def main():
    from kokoro_onnx import Kokoro

    folder = sys.argv[1] if len(sys.argv) > 1 else "."
    k = Kokoro(os.path.join(folder, "kokoro-v1.0.onnx"), os.path.join(folder, "voices-v1.0.bin"))
    lines = video.narration()
    track = np.zeros(int(video.END * RATE) + RATE, dtype=np.float32)
    problems = []
    for i, (start, words, who) in enumerate(lines):
        voice, lang = VOICES[who]
        clip, sr = k.create(words, voice=voice, speed=SPEED, lang=lang)
        assert sr == RATE, sr
        clip = np.asarray(clip, dtype=np.float32)
        # Trim the model's silence at the ends, then fade in and out over 10 ms.
        loud = np.nonzero(np.abs(clip) > 0.01)[0]
        if len(loud):
            clip = clip[max(0, loud[0] - 120): loud[-1] + 1200]
        fade = min(240, len(clip) // 4)
        clip[:fade] *= np.linspace(0, 1, fade)
        clip[-fade:] *= np.linspace(1, 0, fade)
        clip *= LEVEL / max(1e-6, float(np.sqrt(np.mean(clip ** 2))))
        end = start + len(clip) / RATE
        room = (lines[i + 1][0] if i + 1 < len(lines) else video.END) - GAP
        flag = "" if end <= room else f"  RUNS {end - room:.2f}s INTO THE NEXT LINE"
        if flag:
            problems.append(words)
        print(f"{start:6.2f}-{end:6.2f}s  {who:8s} {words}{flag}")
        at = int(start * RATE)
        track[at: at + len(clip)] += clip[: len(track) - at]
    if problems:
        raise SystemExit(f"{len(problems)} line(s) too long for their scene: give them more beats in video.py")
    track = np.clip(track[: int(video.END * RATE)], -0.99, 0.99)
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with wave.open(OUT, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(RATE)
        w.writeframes((track * 32767).astype(np.int16).tobytes())
    print(OUT)


if __name__ == "__main__":
    main()
