"""Turns raw icons into round-friendly squares: the mark is centred and scaled to sit inside the circle
the app clips it to, on its own background colour. Also draws review sheets."""
import json, os, sys
import numpy as np
from PIL import Image, ImageDraw, ImageFont

HERE = os.path.dirname(os.path.abspath(__file__))
RAW = os.path.join(HERE, "raw")
S = 144  # output size in px: sharp at 46dp on 3x screens


def border(a, w=3):
    return np.concatenate([a[:w].reshape(-1, a.shape[-1]), a[-w:].reshape(-1, a.shape[-1]),
                           a[:, :w].reshape(-1, a.shape[-1]), a[:, -w:].reshape(-1, a.shape[-1])])


def prepare(im, ops):
    """Small fixes before fitting: ("crop", x0, y0, x1, y1) in fractions, ("fill", x0, y0, x1, y1, rgb) paints a box,
    ("outside", cx, cy, r, rgb) paints everything outside a circle."""
    for op in ops or []:
        w, h = im.size
        if op[0] == "crop":
            im = im.crop((int(op[1] * w), int(op[2] * h), int(op[3] * w), int(op[4] * h)))
        elif op[0] == "fill":
            ImageDraw.Draw(im).rectangle((op[1] * w, op[2] * h, op[3] * w, op[4] * h), fill=tuple(op[5]) + (255,))
        elif op[0] == "pad":
            m = int(op[1] * max(w, h))
            bigger = Image.new("RGBA", (w + 2 * m, h + 2 * m), tuple(op[2]) + (255,))
            bigger.paste(im, (m, m))
            im = bigger
        elif op[0] == "recolor":
            # ("recolor", rgb_from, tolerance, rgb_to, keep_cx, keep_cy, keep_r): swap a colour except inside a circle.
            a = np.asarray(im).copy()
            yy, xx = np.mgrid[0:h, 0:w]
            far = ((xx - op[4] * w) ** 2 + (yy - op[5] * h) ** 2) > (op[6] * w) ** 2
            near = np.abs(a[..., :3].astype(int) - np.array(op[1])).sum(axis=2) < op[2]
            a[far & near, :3] = op[3]
            im = Image.fromarray(a)
        elif op[0] == "brighten":
            # Undo a dark overlay (a page's pop-up scrim) captured with the logo.
            a = np.asarray(im).astype(np.float32)
            a[..., :3] = np.clip(a[..., :3] * op[1], 0, 255)
            im = Image.fromarray(a.astype(np.uint8))
        elif op[0] == "outside":
            cx, cy, r = op[1] * w, op[2] * h, op[3] * w
            m = Image.new("L", im.size, 255)
            ImageDraw.Draw(m).ellipse((cx - r, cy - r, cx + r, cy + r), fill=0)
            im.paste(Image.new("RGBA", im.size, tuple(op[4]) + (255,)), (0, 0), m)
    return im


def fit(path, size=S, inner=0.40, cap=0.70, override_bg=None, zoom=1.0, ops=None):
    im = prepare(Image.open(path).convert("RGBA"), ops)
    if im.width != im.height:
        side = max(im.size)
        corner = im.getpixel((0, 0))
        sq = Image.new("RGBA", (side, side), corner if corner[3] == 255 else (0, 0, 0, 0))
        sq.paste(im, ((side - im.width) // 2, (side - im.height) // 2))
        im = sq
    work = im.resize((512, 512), Image.LANCZOS)
    a = np.asarray(work).astype(np.int32)
    rgb, alpha = a[..., :3], a[..., 3]
    edge = border(a)
    transparent_edge = (edge[:, 3] < 40).mean() > 0.5
    if transparent_edge:
        mask = alpha > 40
        if not mask.any():
            return None
        vis = rgb[mask]
        lum = (0.2126 * vis[:, 0] + 0.7152 * vis[:, 1] + 0.0722 * vis[:, 2]) / 255
        bg = (32, 33, 36) if lum.mean() > 0.82 else (255, 255, 255)
        mode = "transparent"
    else:
        med = np.median(edge[:, :3], axis=0)
        dist = np.abs(edge[:, :3] - med).sum(axis=1)
        if (dist < 36).mean() < 0.85:
            # A full-bleed picture (gradient, photo): use it as it is.
            out = work.convert("RGB").resize((size, size), Image.LANCZOS)
            if zoom != 1.0:
                out = zoomed(out, zoom)
            return out, "fullbleed"
        bg = tuple(int(x) for x in med)
        mask = (np.abs(rgb - np.array(bg)).sum(axis=2) > 48) & (alpha > 40)
        if not mask.any():
            return None
        mode = "solid"
    if override_bg:
        bg = override_bg
    ys, xs = np.where(mask)
    x0, x1, y0, y1 = xs.min(), xs.max() + 1, ys.min(), ys.max() + 1
    w, h = x1 - x0, y1 - y0
    half_diag = ((w / 2) ** 2 + (h / 2) ** 2) ** 0.5
    scale = min(inner * size / half_diag, cap * size / max(w, h)) * zoom
    crop = work.crop((x0, y0, x1, y1))
    cw, ch = max(1, round(w * scale)), max(1, round(h * scale))
    crop = crop.resize((cw, ch), Image.LANCZOS)
    canvas = Image.new("RGBA", (size, size), bg + (255,))
    canvas.alpha_composite(crop, ((size - cw) // 2, (size - ch) // 2))
    return canvas.convert("RGB"), mode


def zoomed(img, z):
    w = img.width
    c = int(w / z)
    o = (w - c) // 2
    return img.crop((o, o, o + c, o + c)).resize((w, w), Image.LANCZOS)


def circle(img, d):
    img = img.resize((d, d), Image.LANCZOS)
    m = Image.new("L", (d * 4, d * 4), 0)
    ImageDraw.Draw(m).ellipse((0, 0, d * 4 - 1, d * 4 - 1), fill=255)
    m = m.resize((d, d), Image.LANCZOS)
    out = Image.new("RGBA", (d, d), (0, 0, 0, 0))
    out.paste(img, (0, 0), m)
    return out


def sheet(cells, out, cols=8, d=92, pair=True, bg=(40, 42, 46)):
    """cells: list of (label, [img or None, ...])"""
    per = 2 if pair else 1
    cw = d * per + 8 * (per - 1) + 16
    chh = d + 30
    rows = (len(cells) + cols - 1) // cols
    W, H = cols * cw, rows * chh
    page = Image.new("RGB", (W, H), bg)
    dr = ImageDraw.Draw(page)
    try:
        font = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf", 12)
    except Exception:
        font = ImageFont.load_default()
    for i, (label, imgs) in enumerate(cells):
        x, y = (i % cols) * cw + 8, (i // cols) * chh + 4
        for j, im in enumerate(imgs[:per]):
            if im is None:
                dr.ellipse((x + j * (d + 8), y, x + j * (d + 8) + d, y + d), outline=(120, 120, 120))
            else:
                page.paste(circle(im, d), (x + j * (d + 8), y), circle(im, d))
        dr.text((x, y + d + 4), label[:22], fill=(235, 235, 235), font=font)
    page.save(out)
