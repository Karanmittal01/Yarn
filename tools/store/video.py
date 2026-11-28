"""Renders Yarn's demo video (1080x1920, 30 fps, about a minute) from the app's own screens.

Render the screens first (made-up people and numbers only):
    ./gradlew :app:testDebugUnitTest --tests '*UiSnapshotTest.video*'
then, from the repository root, with Pillow and NumPy installed and ffmpeg on the PATH:
    python tools/store/voiceover.py <voice_dir>   # narration, see that file
    python tools/store/music.py                   # music, mixed with the narration
    python tools/store/video.py [output.mp4]

The story: a messy-inbox hook, the logo, signing up, then each main feature in the phone with
taps, zooms and captions, the price, and the Play Store end card. Every timing is in TIMELINE.
"""
import math
import os
import subprocess
import sys

import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
SHOTS = os.path.join(ROOT, "app", "build", "ui-snapshots")
ICON = os.path.join(ROOT, "distribution", "play-store-icon-512.png")
FONTS = "/usr/share/fonts/opentype/inter"

W, H, FPS = 1080, 1920, 30
STOPS = [(0.0, (37, 124, 211)), (0.35, (16, 69, 149)), (0.65, (6, 35, 97)), (1.0, (6, 11, 37))]

# The phone: screens are 1233x2673 renders of a 411x891dp phone.
SW = 700
SCALE = SW / 1233
SH = round(2673 * SCALE)
BEZEL = 14
PW, PH = SW + 2 * BEZEL, SH + 2 * BEZEL
PX, PY = (W - PW) // 2, 360
R_SCREEN, R_PHONE = 56, 70
SCRIM_SOLID, SCRIM_END = 300, 360  # while zooming, the phone fades out under the captions


def font(weight, size):
    return ImageFont.truetype(os.path.join(FONTS, f"Inter-{weight}.otf"), size)


def clamp(x, a=0.0, b=1.0):
    return max(a, min(b, x))


def ease(t):
    t = clamp(t)
    return 4 * t * t * t if t < 0.5 else 1 - (-2 * t + 2) ** 3 / 2


def ease_out(t):
    t = clamp(t)
    return 1 - (1 - t) ** 3


def back_out(t):
    t = clamp(t)
    c1, c3 = 1.4, 2.4
    return 1 + c3 * (t - 1) ** 3 + c1 * (t - 1) ** 2


# ---- The story ----------------------------------------------------------------------------------
# Screens in the phone. t0/t1 in seconds; enter: rise (phone comes up), slide (next screen, like
# Android), fade, cut. taps: (seconds into the shot, x, y in screen pixels of the 1233-wide render).
SETUP = ("Set up in a minute", "Make Yarn your SMS app, bring your chats back, done")
TABS = ("Jump to what matters", "OTP · Updates · Offers")
OWN = ("Tabs your way", "Bank & payments, chats you pick, any word")
LOOK = ("Make it yours", "Accent colours and chat wallpapers")

# The music runs at 120 beats a minute (tools/store/music.py) and every scene lasts a whole number of
# beats, so each cut lands on the beat. `say` is the scene's narration (tools/store/voiceover.py),
# spoken from a quarter-second in, or (seconds in, words[, "hindi"]) pieces; taps are seconds in.
BPM = 120
BEAT = 60 / BPM
HOOK_END = 6.0  # a bar line: the phone rises on the downbeat after the logo's
HOOK_SAY = [(0.3, "One messy inbox. Codes, offers, scams."), (4.1, "Meet Yarn.")]
OUTRO_BEATS = 10
OUTRO_SAY = [(0.6, "Get Yarn, free, on Google Play.")]
SCENES = [
    dict(img="video_01_welcome", beats=4, enter="rise", cap=SETUP, taps=[(1.5, 616, 2408)],
         say="Setting up takes a minute. Make Yarn your messaging app, bring back your chats, and you're done."),
    # No permissions page: becoming the default SMS app is the only setup step that asks anything.
    dict(img="video_02_default", beats=4, enter="slide", cap=SETUP, taps=[(1.5, 616, 2408)]),
    dict(img="video_04_backup", beats=3, enter="slide", cap=SETUP, taps=[(1.0, 616, 2408)]),
    dict(img="video_05_you", beats=3, enter="slide", cap=SETUP, taps=[(1.0, 616, 2408)]),
    dict(img="video_06_clean", beats=3, enter="slide", cap=SETUP, taps=[(1.0, 616, 2408)]),
    dict(img="video_07_inbox_welcome", beats=6, enter="fade", cap=("Yarn Pro, free for 30 days", "Every feature, unlocked from day one"),
         zoom=(1.0, 1.1, 616, 640), say="Pro is free for your first thirty days."),
    dict(img="video_08_inbox", beats=8, enter="fade", cap=("Your inbox, sorted", "Every message in its tab, every brand with its logo"),
         zoom=(1.0, 1.06, 616, 1150), taps=[(3.5, 700, 299)], say="Every message, sorted, with real logos."),
    # The tab tour: each scene taps the next tab.
    dict(img="video_09_tab_otp", beats=3, enter="cut", cap=TABS, taps=[(1.0, 770, 299)], say="Jump straight to O T Ps, updates or offers."),
    dict(img="video_10_tab_updates", beats=3, enter="cut", cap=TABS, taps=[(1.0, 1057, 299)]),
    dict(img="video_13_tab_offers", beats=3, enter="cut", cap=TABS, taps=[(1.0, 1050, 299)]),
    dict(img="video_11_tab_bank", beats=6, enter="cut", cap=OWN, taps=[(2.0, 1148, 299)],
         say="Or make tabs of your own, like bank and payments. Each message lives in just one tab."),
    dict(img="video_12_tabs_editor", beats=7, enter="fade", cap=OWN),
    dict(img="video_09_tab_otp", beats=7, enter="fade", cap=("Codes never get lost", "Copy any OTP in one tap"),
         taps=[(1.0, 407, 573)], toast=(1.15, 2.6, "Code copied"), zoom=(1.0, 1.18, 430, 720), say="Copy any code in one tap."),
    dict(img="video_14_chat", beats=10, enter="fade", cap=("Chats that understand you", "Translate Hinglish, smart replies, dual SIM"),
         zoom=(1.0, 1.15, 500, 1000), say="Translate Hinglish, get smart replies, and pick your SIM."),
    dict(img="video_15_spam", beats=8, enter="fade", cap=("Scams stay out", "Fake prizes, KYC and loan scams go to Spam"),
         zoom=(1.0, 1.07, 616, 900), say="Fake prize, K Y C and loan scams go to Spam."),
    dict(img="video_16_look", beats=5, enter="fade", cap=LOOK, taps=[(1.0, 548, 805), (2.0, 1065, 1185)],
         say="Make it yours, with accent colours and chat wallpapers."),
    dict(img="video_17_look_chat", beats=4, enter="fade", cap=LOOK),
    dict(img="video_18_clean", beats=7, enter="fade", cap=("A clean inbox in one tap", "Clear thousands of old OTPs and offers"),
         taps=[(2.0, 616, 2103)], zoom=(1.0, 1.06, 616, 1300), say="Clear thousands of old codes in one tap."),
    dict(img="video_19_backup", beats=7, enter="fade", cap=("Safe in your Google Drive", "Encrypted backup, restore on any phone"),
         taps=[(1.5, 616, 815)], say="Backed up, encrypted, in your Google Drive."),
    dict(img="video_20_private", beats=7, enter="fade", cap=("Private by design", "Runs on your phone. No ads, no tracking."),
         say="All on your phone. No ads. No tracking."),
    dict(img="video_21_hindi", beats=6, enter="fade", cap=("Speaks your language", "In English and Hindi"),
         say=[(0.25, "In English,"), (1.3, "और हिंदी में भी।", "hindi")]),
    dict(img="video_22_pro", beats=10, enter="fade", cap=("Free to use", "Yarn Pro free for 30 days, then ₹199 a year"),
         zoom=(1.0, 1.1, 616, 1250), say="Free to use. Pro is just a hundred and ninety-nine rupees a year."),
]

# Seconds, from the beats.
TIMELINE = []
_t = HOOK_END
for _s in SCENES:
    TIMELINE.append(dict(_s, t0=_t, t1=_t + _s["beats"] * BEAT))
    _t += _s["beats"] * BEAT
OUTRO_START = _t
END = OUTRO_START + OUTRO_BEATS * BEAT


def narration():
    """Every spoken line as (start in seconds, words, voice), voice being "narrator" or "hindi"."""
    out = [(t, w, "narrator") for t, w in HOOK_SAY]
    for s in TIMELINE:
        say = s.get("say")
        if isinstance(say, str):
            out.append((s["t0"] + 0.25, say, "narrator"))
        elif say:
            out += [(s["t0"] + p[0], p[1], p[2] if len(p) > 2 else "narrator") for p in say]
    out += [(OUTRO_START + t, w, "narrator") for t, w in OUTRO_SAY]
    return out


# ---- Fixed layers -------------------------------------------------------------------------------
def gradient():
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
            px[x, y] = line[round(min(1.0, 0.35 * x / 63 + 0.65 * y / 111) * 255)]
    return small.resize((W, H), Image.BICUBIC).convert("RGBA")


def glow(size, color, alpha):
    g = Image.new("L", (size, size), 0)
    ImageDraw.Draw(g).ellipse((size * 0.2, size * 0.2, size * 0.8, size * 0.8), fill=int(255 * alpha))
    g = g.filter(ImageFilter.GaussianBlur(size * 0.12))
    out = Image.new("RGBA", (size, size), color + (0,))
    out.putalpha(g)
    return out


def rounded_mask(w, h, r):
    m = Image.new("L", (w * 2, h * 2), 0)
    ImageDraw.Draw(m).rounded_rectangle((0, 0, w * 2 - 1, h * 2 - 1), r * 2, fill=255)
    return m.resize((w, h), Image.LANCZOS)


def phone_body():
    pad = 60
    layer = Image.new("RGBA", (PW + 2 * pad, PH + 2 * pad), (0, 0, 0, 0))
    shadow = Image.new("L", layer.size, 0)
    ImageDraw.Draw(shadow).rounded_rectangle((pad, pad + 24, pad + PW, pad + PH + 24), R_PHONE, fill=150)
    layer.paste((2, 6, 24, 255), (0, 0), shadow.filter(ImageFilter.GaussianBlur(30)))
    d = ImageDraw.Draw(layer)
    d.rounded_rectangle((pad, pad, pad + PW - 1, pad + PH - 1), R_PHONE, fill=(14, 16, 22, 255))
    d.rounded_rectangle((pad + 2, pad + 2, pad + PW - 3, pad + PH - 3), R_PHONE - 2, outline=(70, 76, 92, 255), width=2)
    return layer, pad


class Screens:
    def __init__(self):
        self.cache = {}

    def get(self, name):
        if name not in self.cache:
            path = os.path.join(SHOTS, name + ".png")
            if not os.path.exists(path):
                sys.exit(f"Render the screens first; missing {name}.png")
            im = Image.open(path).convert("RGB")
            # Taller renders are cropped to one phone screen.
            im = im.crop((0, 0, im.width, min(im.height, round(im.width * 2673 / 1233))))
            self.cache[name] = im.resize((SW, SH), Image.LANCZOS)
        return self.cache[name]


# ---- Text ---------------------------------------------------------------------------------------
def text_layer(text, fnt, fill, shadow=True, max_width=W - 110):
    while ImageDraw.Draw(Image.new("L", (1, 1))).textlength(text, font=fnt) > max_width and fnt.size > 24:
        fnt = ImageFont.truetype(fnt.path, fnt.size - 2)
    bbox = ImageDraw.Draw(Image.new("L", (1, 1))).textbbox((0, 0), text, font=fnt)
    w, h = bbox[2] - bbox[0] + 40, bbox[3] - bbox[1] + 40
    layer = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    if shadow:
        s = Image.new("L", (w, h), 0)
        ImageDraw.Draw(s).text((20 - bbox[0], 24 - bbox[1]), text, font=fnt, fill=120)
        layer.paste((0, 8, 30, 255), (0, 0), s.filter(ImageFilter.GaussianBlur(8)))
    ImageDraw.Draw(layer).text((20 - bbox[0], 20 - bbox[1]), text, font=fnt, fill=fill)
    return layer


def put(frame, layer, cx, cy, alpha=1.0, scale=1.0, rotate=0.0):
    if alpha <= 0.01:
        return
    if scale != 1.0:
        layer = layer.resize((max(1, round(layer.width * scale)), max(1, round(layer.height * scale))), Image.BICUBIC)
    if rotate:
        layer = layer.rotate(rotate, resample=Image.BICUBIC, expand=True)
    if alpha < 1.0:
        a = layer.getchannel("A").point(lambda v: int(v * alpha))
        layer = layer.copy()
        layer.putalpha(a)
    frame.alpha_composite(layer, (round(cx - layer.width / 2), round(cy - layer.height / 2)))


def pill(text, fnt, bg, fg, pad_x=34, pad_y=18):
    probe = ImageDraw.Draw(Image.new("L", (1, 1)))
    bbox = probe.textbbox((0, 0), text, font=fnt)
    w, h = bbox[2] - bbox[0] + 2 * pad_x, bbox[3] - bbox[1] + 2 * pad_y
    layer = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    d.rounded_rectangle((0, 0, w - 1, h - 1), h // 2, fill=bg)
    d.text((pad_x - bbox[0], pad_y - bbox[1]), text, font=fnt, fill=fg)
    return layer


# ---- Scenes -------------------------------------------------------------------------------------
class Video:
    def __init__(self):
        self.bg = gradient()
        self.glows = [glow(1100, (70, 190, 255), 0.35), glow(1000, (130, 90, 255), 0.30)]
        self.body, self.body_pad = phone_body()
        self.mask = rounded_mask(SW, SH, R_SCREEN)
        self.screens = Screens()
        self.captions = {}
        icon = Image.open(ICON).convert("RGBA")
        self.icon = icon.resize((280, 280), Image.LANCZOS)
        self.icon.putalpha(Image.composite(self.icon.getchannel("A"), Image.new("L", (280, 280), 0), rounded_mask(280, 280, 64)))
        self.icon_small = self.icon.resize((200, 200), Image.LANCZOS)
        f = font
        self.hook = {
            "inbox": text_layer("Your SMS inbox:", f("Bold", 88), (255, 255, 255)),
            "mess": text_layer("All in one messy list.", f("SemiBold", 58), (196, 218, 255)),
            "pills": [
                (pill("OTPs", f("SemiBold", 46), (255, 255, 255, 40), (255, 255, 255)), 300, 800, -6, 0.9),
                (pill("Bank alerts", f("SemiBold", 46), (255, 255, 255, 40), (255, 255, 255)), 720, 860, 5, 1.1),
                (pill("Offers", f("SemiBold", 46), (255, 255, 255, 40), (255, 255, 255)), 380, 980, 4, 1.3),
                (pill("Deliveries", f("SemiBold", 46), (255, 255, 255, 40), (255, 255, 255)), 760, 1040, -5, 1.5),
                (pill("Scams", f("Bold", 46), (242, 69, 61, 230), (255, 255, 255)), 300, 1150, -3, 1.7),
                (pill("Bills", f("SemiBold", 46), (255, 255, 255, 40), (255, 255, 255)), 700, 1200, 7, 1.9),
            ],
            "yarn": text_layer("Yarn", f("Bold", 120), (255, 255, 255)),
            "tag": text_layer("Your SMS, sorted by on-device AI", f("Medium", 44), (196, 218, 255)),
        }
        self.outro = {
            "name": text_layer("Yarn - AI Messaging", f("Bold", 76), (255, 255, 255)),
            "line": text_layer("Made for Indian inboxes", f("Medium", 42), (196, 218, 255)),
            "cta": pill("Free on Google Play", f("Bold", 50), (255, 255, 255, 255), (16, 69, 149), pad_x=54, pad_y=30),
            "pro": text_layer("Yarn Pro free for 30 days  ·  then ₹199/year or ₹999 for life", f("Medium", 34), (196, 218, 255)),
        }
        self.toast_font = f("Medium", 30)
        # Above the captions' baseline the background is solid, then it fades out over the phone.
        ramp = np.clip((SCRIM_END - np.arange(SCRIM_END)) / (SCRIM_END - SCRIM_SOLID), 0, 1) * 255
        self.scrim = Image.fromarray(np.repeat(ramp.astype(np.uint8)[:, None], W, axis=1), "L")

    def caption(self, cap):
        if cap not in self.captions:
            title, sub = cap
            self.captions[cap] = (text_layer(title, font("Bold", 72), (255, 255, 255)), text_layer(sub, font("Medium", 38), (196, 218, 255)))
        return self.captions[cap]

    def background(self, t):
        frame = self.bg.copy()
        a, b = self.glows
        frame.alpha_composite(a, (round(-260 + 120 * math.sin(t * 0.35)), round(-200 + 160 * math.cos(t * 0.27))))
        frame.alpha_composite(b, (round(420 + 140 * math.cos(t * 0.3)), round(1100 + 180 * math.sin(t * 0.22))))
        return frame

    # The screen inside the phone at time t, with taps and toasts drawn on it.
    def screen(self, t):
        idx = max(i for i, s in enumerate(TIMELINE) if t >= s["t0"] or i == 0)
        shot = TIMELINE[idx]
        cur = self.screens.get(shot["img"]).copy()
        local = t - shot["t0"]
        prev = TIMELINE[idx - 1] if idx > 0 else None
        if prev and shot["enter"] in ("slide", "fade", "cut"):
            dur = {"slide": 0.38, "fade": 0.4, "cut": 0.14}[shot["enter"]]
            p = local / dur
            if p < 1:
                old = self.screens.get(prev["img"]).copy()
                self.draw_effects(old, prev, t - prev["t0"])
                if shot["enter"] == "slide":
                    e = ease(p)
                    canvas = Image.new("RGB", (SW, SH), (0, 0, 0))
                    dim = Image.blend(old, Image.new("RGB", old.size, (0, 0, 0)), 0.25 * e)
                    canvas.paste(dim, (round(-SW * 0.28 * e), 0))
                    self.draw_effects(cur, shot, local)
                    canvas.paste(cur, (round(SW * (1 - e)), 0))
                    return canvas
                self.draw_effects(cur, shot, local)
                return Image.blend(old, cur, ease(p))
        self.draw_effects(cur, shot, local)
        return cur

    def draw_effects(self, img, shot, local):
        d = ImageDraw.Draw(img, "RGBA")
        for (tt, x, y) in shot.get("taps", []):
            dt = local - tt
            sx, sy = x * SCALE, y * SCALE
            if -0.25 < dt < 0.5:
                # The finger lands, then a ripple spreads from it.
                a = clamp((dt + 0.25) / 0.15) * clamp((0.5 - dt) / 0.2)
                r = 24
                d.ellipse((sx - r, sy - r, sx + r, sy + r), fill=(255, 255, 255, int(150 * a)), outline=(20, 40, 90, int(90 * a)), width=2)
            if 0 <= dt < 0.55:
                p = dt / 0.55
                r = 20 + 70 * ease_out(p)
                d.ellipse((sx - r, sy - r, sx + r, sy + r), fill=(120, 150, 220, int(70 * (1 - p))))
        toast = shot.get("toast")
        if toast:
            t0, t1, text = toast
            a = clamp((local - t0) / 0.2) * clamp((t1 - local) / 0.25)
            if a > 0:
                probe = d.textbbox((0, 0), text, font=self.toast_font)
                w, h = probe[2] - probe[0] + 64, probe[3] - probe[1] + 40
                x0, y0 = (SW - w) / 2, SH - 190 - 14 * (1 - a)
                d.rounded_rectangle((x0, y0, x0 + w, y0 + h), h / 2, fill=(48, 49, 52, int(235 * a)))
                d.text((x0 + 32 - probe[0], y0 + 20 - probe[1]), text, font=self.toast_font, fill=(255, 255, 255, int(255 * a)))

    def phone(self, frame, t, dy=0.0, alpha=1.0):
        screen = self.screen(t)
        layer = self.body.copy()
        layer.paste(screen, (self.body_pad + BEZEL, self.body_pad + BEZEL), self.mask)
        if alpha < 1.0:
            layer.putalpha(layer.getchannel("A").point(lambda v: int(v * alpha)))
        frame.alpha_composite(layer, (PX - self.body_pad, round(PY - self.body_pad + dy)))

    def zoomed(self, frame, t):
        """Pushes in on the shot's focus point; the captions' backdrop is laid back over the phone's top."""
        zoomed = self._zoom(frame, t)
        if zoomed is frame:
            return frame
        clean = self.background(t)
        zoomed.paste(clean.crop((0, 0, W, SCRIM_END)), (0, 0), self.scrim)
        return zoomed

    def _zoom(self, frame, t):
        for s in TIMELINE:
            if s["t0"] <= t < s["t1"] and "zoom" in s:
                z0, z1, fx, fy = s["zoom"]
                p = ease((t - s["t0"]) / (s["t1"] - s["t0"]))
                z = z0 + (z1 - z0) * p
                # A little zoom on the incoming fade, back out at the end of the shot.
                z = 1 + (z - 1) * clamp((s["t1"] - t) / 0.45)
                if z <= 1.001:
                    return frame
                # The focus point is in the screen render's pixels, like the taps.
                fxs, fys = PX + BEZEL + fx * SCALE, PY + BEZEL + fy * SCALE
                cw, ch = W / z, H / z
                x0 = clamp(fxs - cw / 2, 0, W - cw)
                y0 = clamp(fys - ch / 2, 0, H - ch)
                return frame.crop((round(x0), round(y0), round(x0 + cw), round(y0 + ch))).resize((W, H), Image.BICUBIC)
        return frame

    def captions_on(self, frame, t):
        active = [s for s in TIMELINE if s["t0"] <= t < s["t1"]]
        if not active:
            return
        s = active[0]
        cap = s["cap"]
        # When the caption started (consecutive shots can share one) and when it ends.
        i = TIMELINE.index(s)
        start = s["t0"]
        while i > 0 and TIMELINE[i - 1]["cap"] == cap:
            i -= 1
            start = TIMELINE[i]["t0"]
        j = TIMELINE.index(s)
        end = s["t1"]
        while j + 1 < len(TIMELINE) and TIMELINE[j + 1]["cap"] == cap:
            j += 1
            end = TIMELINE[j]["t1"]
        title, sub = self.caption(cap)
        a_in = ease_out((t - start) / 0.45)
        a_out = clamp((end - t) / 0.3)
        a = a_in * a_out
        put(frame, title, W / 2, 165 + 24 * (1 - a_in), a)
        a2 = ease_out((t - start - 0.12) / 0.45) * a_out
        put(frame, sub, W / 2, 258 + 24 * (1 - a2), a2)

    def hook_frame(self, frame, t):
        h = self.hook
        out = clamp((3.5 - t) / 0.35)  # the mess clears
        put(frame, h["inbox"], W / 2, 600 - 30 * (1 - ease_out((t - 0.2) / 0.5)), ease_out((t - 0.2) / 0.5) * out)
        for layer, x, y, rot, at in h["pills"]:
            p = (t - at) / 0.35
            if p > 0:
                drift = (t - at) * 14
                put(frame, layer, x + drift * (1 if x > 540 else -1), y - drift * 0.5, clamp(p) * out, scale=0.55 + 0.45 * back_out(p), rotate=rot)
        put(frame, h["mess"], W / 2, 1400, ease_out((t - 2.3) / 0.45) * out)
        # The logo arrives.
        p = (t - 3.75) / 0.5
        if p > 0:
            leave = clamp((HOOK_END + 0.05 - t) / 0.35)
            put(frame, self.icon, W / 2, 780, clamp(p * 2) * leave, scale=0.4 + 0.6 * back_out(p))
            put(frame, h["yarn"], W / 2, 1030, ease_out((t - 4.05) / 0.4) * leave)
            put(frame, h["tag"], W / 2, 1150, ease_out((t - 4.3) / 0.4) * leave)

    def outro_frame(self, frame, t):
        o = self.outro
        lt = t - OUTRO_START
        put(frame, self.icon, W / 2, 720, clamp(lt / 0.3 - 1), scale=0.4 + 0.6 * back_out((lt - 0.3) / 0.5))
        put(frame, o["name"], W / 2, 960, ease_out((lt - 0.6) / 0.45))
        put(frame, o["line"], W / 2, 1060, ease_out((lt - 0.85) / 0.45))
        put(frame, o["cta"], W / 2, 1230, ease_out((lt - 1.2) / 0.4), scale=0.85 + 0.15 * back_out((lt - 1.2) / 0.5))
        put(frame, o["pro"], W / 2, 1380, ease_out((lt - 1.6) / 0.45))

    def frame(self, t):
        frame = self.background(t)
        if t < HOOK_END:
            self.hook_frame(frame, t)
            return frame
        first = TIMELINE[0]["t0"]
        if t < OUTRO_START + 0.6:
            rise = 1 - ease_out((t - first) / 0.7)
            leave = ease((t - OUTRO_START) / 0.6) if t >= OUTRO_START else 0.0
            self.phone(frame, min(t, TIMELINE[-1]["t1"] - 0.001), dy=rise * 1300 + leave * 1400, alpha=1 - leave * 0.6)
            frame = self.zoomed(frame, t)
            if t < OUTRO_START:
                self.captions_on(frame, t)
        if t >= OUTRO_START:
            self.outro_frame(frame, t)
        # Fade in from and out to the brand's darkest blue.
        fade = max(clamp(1 - t / 0.35), clamp((t - (END - 0.5)) / 0.5))
        if fade > 0:
            frame = Image.blend(frame, Image.new("RGBA", (W, H), (6, 11, 37, 255)), fade)
        return frame


def main():
    out = sys.argv[1] if len(sys.argv) > 1 else os.path.join(ROOT, "app", "build", "yarn-demo.mp4")
    only = os.environ.get("FRAMES")  # e.g. FRAMES=6,16,30 writes stills instead, for checking
    v = Video()
    if only:
        for s in only.split(","):
            v.frame(float(s)).convert("RGB").save(out.replace(".mp4", f"-{s}.png"))
        return
    # Sound: the soundtrack from tools/store/music.py (music and voice, mixed), else the voiceover
    # alone, else silence.
    track = os.path.join(ROOT, "app", "build", "yarn-soundtrack.wav")
    voice = os.path.join(ROOT, "app", "build", "yarn-voiceover.wav")
    if os.path.exists(track):
        audio, af = ["-i", track], "aresample=48000"
    elif os.path.exists(voice):
        audio, af = ["-i", voice], "aresample=48000,pan=stereo|c0=c0|c1=c0"
    else:
        audio, af = ["-f", "lavfi", "-i", "anullsrc=channel_layout=stereo:sample_rate=48000"], "anull"
    cmd = [
        "ffmpeg", "-y", "-loglevel", "error",
        "-f", "rawvideo", "-pix_fmt", "rgb24", "-s", f"{W}x{H}", "-r", str(FPS), "-i", "-",
        *audio, "-af", af,
        "-shortest", "-c:v", "libx264", "-preset", "slow", "-crf", "20", "-pix_fmt", "yuv420p",
        "-c:a", "aac", "-b:a", "128k", "-movflags", "+faststart", out,
    ]
    proc = subprocess.Popen(cmd, stdin=subprocess.PIPE)
    total = int(END * FPS)
    for i in range(total):
        proc.stdin.write(v.frame(i / FPS).convert("RGB").tobytes())
        if i % 150 == 0:
            print(f"{i}/{total}", flush=True)
    proc.stdin.close()
    proc.wait()
    print(out)


if __name__ == "__main__":
    main()
