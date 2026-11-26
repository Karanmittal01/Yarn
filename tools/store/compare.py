"""Draws the "Yarn vs Google Messages" poster (1080x1920 PNG) for sharing.

From the repository root, with Pillow installed:
    python tools/store/compare.py [output.png]

Every claim about Google Messages is in ROWS with a short note; keep them true and dated
(see FINE_PRINT) when Google's app changes.
"""
import os
import sys

from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
ICON = os.path.join(ROOT, "distribution", "play-store-icon-512.png")
FONTS = "/usr/share/fonts/opentype/inter"
W, H = 1080, 1920
STOPS = [(0.0, (37, 124, 211)), (0.35, (16, 69, 149)), (0.65, (6, 35, 97)), (1.0, (6, 11, 37))]

YES, PART, NO = "yes", "part", "no"
# (feature, Pro, Google Messages, note under Google's mark)
ROWS = [
    ("Smart tabs: Personal, OTP, Transactions, Shopping, Travel, Updates, Offers", True, NO, "Tabs removed"),
    ("Real names and logos of 1,000+ Indian brands, not codes like VM-HDFCBK-S", True, NO, "Sender codes"),
    ("Copy any OTP right from the inbox", False, NO, ""),
    ("Clear thousands of old OTPs and offers in one tap", False, NO, ""),
    ("Automatic clean-up of old OTPs, offers and spam, when you choose", True, PART, "OTPs only, 24 h"),
    ("Choose how each tab notifies you: offers silent, OTPs loud", True, NO, ""),
    ("Translate any message, even Hinglish, on any Android phone", False, PART, "Pixel only"),
    ("AI summaries of long chats and message rewrite", True, PART, "Rewrite only"),
    ("Recycle bin: bring back deleted messages for 30 days or forever", True, PART, "Rolling out"),
    ("App lock with fingerprint or face", False, NO, ""),
    ("Encrypted backup to your own Google Drive, restore any time", True, PART, "At phone setup"),
    ("Spam filter and scam warnings before risky links", True, YES, ""),
    ("Scheduled messages", True, YES, ""),
    ("Accent colours and chat wallpapers", True, YES, ""),
]
FINE_PRINT = (
    "Google Messages features as of November 2026; some vary by phone and country. Google Messages also has RCS chat, "
    "which Android keeps for Google's app. Google Messages is a trademark of Google LLC."
)

INK = (27, 28, 30)
MUTED = (96, 101, 112)
BLUE = (20, 102, 240)
GREEN = (24, 160, 88)
AMBER = (232, 150, 20)
GREY = (176, 180, 190)
LINE = 31  # line height of a feature


def font(weight, size):
    return ImageFont.truetype(os.path.join(FONTS, f"Inter-{weight}.otf"), size)


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
    img = small.resize((W, H), Image.BICUBIC).convert("RGBA")
    for (cx, cy, r, col, a) in [(160, 160, 520, (70, 190, 255), 90), (980, 1500, 560, (130, 90, 255), 70)]:
        g = Image.new("L", (W, H), 0)
        ImageDraw.Draw(g).ellipse((cx - r, cy - r, cx + r, cy + r), fill=a)
        img.alpha_composite(_tint(g.filter(ImageFilter.GaussianBlur(r * 0.45)), col))
    return img


def _tint(alpha, color):
    layer = Image.new("RGBA", alpha.size, color + (0,))
    layer.putalpha(alpha)
    return layer


def wrap(draw, text, fnt, width):
    words, lines, line = text.split(), [], ""
    for w in words:
        trial = (line + " " + w).strip()
        if draw.textlength(trial, font=fnt) <= width:
            line = trial
        else:
            lines.append(line)
            line = w
    lines.append(line)
    return lines


def centred(draw, y, text, fnt, fill):
    draw.text(((W - draw.textlength(text, font=fnt)) / 2, y), text, font=fnt, fill=fill)


def mark(draw, cx, cy, kind, r=19):
    if kind == YES:
        draw.ellipse((cx - r, cy - r, cx + r, cy + r), fill=GREEN)
        draw.line([(cx - 9, cy + 1), (cx - 3, cy + 7), (cx + 9, cy - 6)], fill="white", width=5, joint="curve")
    elif kind == PART:
        draw.ellipse((cx - r, cy - r, cx + r, cy + r), outline=AMBER, width=4)
        draw.pieslice((cx - r + 5, cy - r + 5, cx + r - 5, cy + r - 5), 90, 270, fill=AMBER)
    else:
        draw.ellipse((cx - r, cy - r, cx + r, cy + r), fill=(236, 238, 242))
        d = 7
        draw.line([(cx - d, cy - d), (cx + d, cy + d)], fill=GREY, width=4)
        draw.line([(cx - d, cy + d), (cx + d, cy - d)], fill=GREY, width=4)


def main():
    out = sys.argv[1] if len(sys.argv) > 1 else os.path.join(ROOT, "app", "build", "yarn-vs-google-messages.png")
    img = gradient()
    d = ImageDraw.Draw(img)

    # Header
    icon = Image.open(ICON).convert("RGBA").resize((96, 96), Image.LANCZOS)
    mask = Image.new("L", (448, 448), 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, 447, 447), 104, fill=255)
    icon.putalpha(mask.resize((96, 96), Image.LANCZOS))
    img.alpha_composite(icon, ((W - 96) // 2, 52))
    centred(d, 166, "Yarn vs Google Messages", font("Bold", 62), (255, 255, 255))
    centred(d, 248, "The SMS app made for Indian inboxes", font("Medium", 32), (196, 218, 255))

    # The table
    x0, x1, top = 36, W - 36, 316
    col_yarn, col_g = 790, 952
    text_x, text_w = 70, 640
    f_feat, f_note, f_head = font("SemiBold", 25), font("Medium", 18), font("Bold", 26)
    rows = []
    for feat, pro, g, note in ROWS:
        lines = wrap(d, feat, f_feat, text_w - (70 if pro else 0))
        rows.append((lines, pro, g, note, max(70, 22 + LINE * len(lines))))
    height = 86 + sum(r[4] for r in rows) + 12
    card = Image.new("RGBA", (x1 - x0, height), (0, 0, 0, 0))
    cd = ImageDraw.Draw(card)
    cd.rounded_rectangle((0, 0, card.width - 1, height - 1), 36, fill=(255, 255, 255, 255))
    # Yarn's column stands out.
    cd.rounded_rectangle((col_yarn - x0 - 74, 12, col_yarn - x0 + 74, height - 12), 28, fill=(232, 240, 255, 255))
    shadow = Image.new("L", (W, H), 0)
    ImageDraw.Draw(shadow).rounded_rectangle((x0, top + 18, x1, top + height + 18), 36, fill=120)
    img.alpha_composite(_tint(shadow.filter(ImageFilter.GaussianBlur(24)), (2, 6, 24)))
    img.alpha_composite(card, (x0, top))
    d = ImageDraw.Draw(img)

    # Column heads
    hy = top + 30
    d.text((text_x, hy + 6), "What you get", font=font("Bold", 24), fill=MUTED)
    small_icon = icon.resize((34, 34), Image.LANCZOS)
    img.alpha_composite(small_icon, (col_yarn - 52, hy + 2))
    d.text((col_yarn - 12, hy + 6), "Yarn", font=f_head, fill=BLUE)
    for i, word in enumerate(["Google", "Messages"]):
        d.text((col_g - d.textlength(word, font=font("Bold", 21)) / 2, hy - 6 + i * 24), word, font=font("Bold", 21), fill=MUTED)
    y = top + 86
    d.line([(x0 + 24, y - 6), (x1 - 24, y - 6)], fill=(228, 231, 237), width=2)

    for idx, (lines, pro, g, note, h) in enumerate(rows):
        cy = y + h / 2
        ty = cy - LINE * len(lines) / 2 + 2
        for i, line in enumerate(lines):
            d.text((text_x, ty + i * LINE), line, font=f_feat, fill=INK)
        if pro:
            last = lines[-1]
            px = text_x + d.textlength(last, font=f_feat) + 12
            py = ty + (len(lines) - 1) * LINE + 1
            d.rounded_rectangle((px, py, px + 54, py + 26), 8, fill=BLUE)
            d.text((px + 27 - d.textlength("PRO", font=font("Bold", 16)) / 2, py + 4), "PRO", font=font("Bold", 16), fill="white")
        mark(d, col_yarn, cy, YES)
        mark(d, col_g, cy - (10 if note else 0), g)
        if note:
            d.text((col_g - d.textlength(note, font=f_note) / 2, cy + 12), note, font=f_note, fill=MUTED)
        y += h
        if idx < len(rows) - 1:
            d.line([(x0 + 24, y), (x1 - 24, y)], fill=(238, 240, 244), width=2)

    # Score and the way to get it
    yes_g = sum(1 for r in ROWS if r[2] == YES)
    part_g = sum(1 for r in ROWS if r[2] == PART)
    sy = top + height + 36
    centred(d, sy, f"Yarn: all {len(ROWS)}.  Google Messages: {yes_g}, and {part_g} in part.", font("Bold", 36), (255, 255, 255))
    f_cta = font("Bold", 40)
    label = "Free on Google Play"
    bw = d.textlength(label, font=f_cta) + 100
    by = sy + 66
    d.rounded_rectangle(((W - bw) / 2, by, (W + bw) / 2, by + 84), 42, fill=(255, 255, 255))
    centred(d, by + 19, label, f_cta, (16, 69, 149))
    centred(d, by + 102, "Yarn Pro free for 30 days, then ₹199 a year or ₹999 for life", font("Medium", 29), (196, 218, 255))
    f_fine = font("Regular", 19)
    for i, line in enumerate(wrap(d, FINE_PRINT, f_fine, W - 120)):
        centred(d, by + 158 + i * 25, line, f_fine, (150, 170, 210))
    img.convert("RGB").save(out, optimize=True)
    print(out, "content bottom", by + 158 + 25 * len(wrap(d, FINE_PRINT, f_fine, W - 120)))


if __name__ == "__main__":
    main()
