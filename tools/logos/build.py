"""Writes the chosen logos into the app as small WebP files, plus a final review sheet."""
import json, os, shutil, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from process import fit, sheet, RAW, S
from choices import C, TUNE

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "app", "src", "main", "assets", "logos")
m = json.load(open(os.path.join(os.path.dirname(os.path.abspath(__file__)), "meta.json")))
shutil.rmtree(OUT, ignore_errors=True)
os.makedirs(OUT)
total, cells = 0, []
for slug, src in sorted(C.items()):
    if not src:
        continue
    path = os.path.join(RAW, f"{slug}.{src}.png")
    if not os.path.exists(path):
        print("missing", slug, src)
        continue
    r = fit(path, **TUNE.get(slug, {}))
    if not r:
        print("unusable", slug, src)
        continue
    img = r[0]
    dest = os.path.join(OUT, slug + ".webp")
    img.save(dest, "WEBP", quality=86, method=6)
    total += os.path.getsize(dest)
    cells.append((m.get(slug, {}).get("name", slug), [img]))
sheet(cells, os.path.join(os.path.dirname(os.path.abspath(__file__)), "sheets", "final.png"), cols=12, d=64, pair=False)
for i in range(0, len(cells), 120):
    sheet(cells[i:i + 120], os.path.join(os.path.dirname(os.path.abspath(__file__)), "sheets", f"final_{i // 120 + 1}.png"), cols=12, d=64, pair=False)
print("logos", len(cells), "bytes", total, "avg", total // max(1, len(cells)))
