"""Frames UI snapshots into Google Play phone screenshots (1080x1920).

Render the screens first (made-up data only):
    ./gradlew :app:testDebugUnitTest --tests '*UiSnapshotTest.store*'
then run, from the repository root, with Pillow installed:
    python tools/store/frame.py
It writes distribution/store/screenshot-1.png ... screenshot-8.png.
"""
import os
import sys

from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
SHOTS = os.path.join(ROOT, "app", "build", "ui-snapshots")
OUT = os.path.join(ROOT, "distribution", "store")
FONTS = "/usr/share/fonts/opentype/inter"

W, H = 1080, 1920
CARD_X, CARD_Y, CARD_W, RADIUS = 110, 380, 860, 48
# Brand gradient, top-left to bottom-right (same as the feature graphic).
STOPS = [(0.0, (37, 124, 211)), (0.35, (16, 69, 149)), (0.65, (6, 35, 97)), (1.0, (6, 11, 37))]

SCREENS = [
    ("store_1_inbox", "Your inbox, sorted", "Every brand with its logo, every message in its tab"),
    ("store_2_otp", "Codes never get lost", "One-tap copy for every OTP"),
    ("store_11_tabs", "Tabs your way", "Bank, orders, travel or any word, with Yarn Pro"),
    ("store_3_chat", "Chats that understand you", "Translation, smart replies and dual SIM"),
    ("store_4_spam", "Scams stay out", "Fake prizes, KYC and loan scams go to Spam"),
    ("store_7_backup", "Safe in your Google Drive", "Encrypted backup, restore on any phone"),
    ("store_8_private", "Private by design", "Sorting and smart features run on your phone"),
    ("store_10_pro", "Free to use", "Yarn Pro free for 30 days, then \u20b9199 a year"),
]


def font(weight, size):
    return ImageFont.truetype(os.path.join(FONTS, f"Inter-{weight}.otf"), size)


def gradient():
    # Colour depends on a diagonal position, so render a small strip and stretch it.
    line = []
    for i in range(256):
        t = i / 255
        for (t0, c0), (t1, c1) in zip(STOPS, STOPS[1:]):
            if t <= t1:
                f = (t - t0) / (t1 - t0)
                line.append(tuple(round(a + (b - a) * f) for a, b in zip(c0, c1)))
                break
    small = Image.new("RGB", (64, 112))
    px = small.load()
    for y in range(112):
        for x in range(64):
            t = min(1.0, 0.35 * x / 63 + 0.65 * y / 111)
            px[x, y] = line[round(t * 255)]
    return small.resize((W, H), Image.BICUBIC)


def centred(draw, y, text, f, fill):
    w = draw.textlength(text, font=f)
    draw.text(((W - w) / 2, y), text, font=f, fill=fill)


def frame(name, title, subtitle, bg):
    shot = Image.open(os.path.join(SHOTS, name + ".png")).convert("RGB")
    shot = shot.resize((CARD_W, round(shot.height * CARD_W / shot.width)), Image.LANCZOS)
    card_h = H - CARD_Y + RADIUS  # runs off the bottom edge, like a phone held up
    canvas = bg.copy()

    shadow = Image.new("L", (W, H), 0)
    ImageDraw.Draw(shadow).rounded_rectangle((CARD_X, CARD_Y + 14, CARD_X + CARD_W, CARD_Y + card_h), RADIUS, fill=110)
    canvas.paste((2, 8, 30), (0, 0), shadow.filter(ImageFilter.GaussianBlur(28)))

    screen = Image.new("RGB", (CARD_W, card_h), shot.getpixel((CARD_W // 2, shot.height - 2)))
    screen.paste(shot.crop((0, 0, CARD_W, min(shot.height, card_h))), (0, 0))
    mask = Image.new("L", (CARD_W, card_h), 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, CARD_W - 1, card_h - 1), RADIUS, fill=255)
    canvas.paste(screen, (CARD_X, CARD_Y), mask)

    d = ImageDraw.Draw(canvas)
    title_font = font("Bold", 70 if d.textlength(title, font=font("Bold", 70)) <= W - 120 else 62)
    centred(d, 150, title, title_font, (255, 255, 255))
    centred(d, 252, subtitle, font("Regular", 34), (196, 218, 255))
    return canvas


def main():
    missing = [n for n, _, _ in SCREENS if not os.path.exists(os.path.join(SHOTS, n + ".png"))]
    if missing:
        sys.exit("Render the screens first; missing: " + ", ".join(missing))
    bg = gradient()
    for i, (name, title, subtitle) in enumerate(SCREENS, 1):
        path = os.path.join(OUT, f"screenshot-{i}.png")
        frame(name, title, subtitle, bg).save(path, optimize=True)
        print(path)


if __name__ == "__main__":
    main()
