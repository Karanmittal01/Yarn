"""Site's own icons: <link rel=icon/apple-touch-icon>, the web manifest, and common default paths."""
import json, os, re, sys, urllib.parse
from concurrent.futures import ThreadPoolExecutor
from io import BytesIO
from PIL import Image
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from fetch import get, RAW, HERE

def load(data):
    try:
        im = Image.open(BytesIO(data))
        if im.format == "ICO":
            sizes = sorted(im.info.get("sizes", [im.size]), key=lambda s: s[0])
            im.size = sizes[-1]
        im.load()
        return im.convert("RGBA")
    except Exception:
        return None

def work(item):
    slug, domain = item
    out = os.path.join(RAW, slug + ".site.png")
    if os.path.exists(out):
        return slug, Image.open(out).size
    base = "https://" + (domain if "." in domain.split("/")[0] else domain)
    host = "https://" + domain.split("/")[0]
    urls = []
    html = get(base + ("/" if "/" not in domain else ""), timeout=20)
    if html:
        h = html.decode("utf-8", "ignore")
        for tag in re.findall(r"<link[^>]+>", h, re.I):
            rel = re.search(r'rel=["\']([^"\']+)', tag, re.I)
            href = re.search(r'href=["\']([^"\']+)', tag, re.I)
            if not rel or not href:
                continue
            r = rel.group(1).lower()
            if "icon" in r:
                urls.append(urllib.parse.urljoin(base + "/", href.group(1)))
            elif "manifest" in r:
                mu = urllib.parse.urljoin(base + "/", href.group(1))
                md = get(mu, timeout=15)
                try:
                    for ic in json.loads(md).get("icons", []):
                        urls.append(urllib.parse.urljoin(mu, ic["src"]))
                except Exception:
                    pass
    urls += [host + p for p in ("/apple-touch-icon.png", "/apple-touch-icon-precomposed.png", "/android-chrome-512x512.png", "/android-chrome-192x192.png", "/favicon-196x196.png", "/favicon.ico")]
    best = None
    for u in dict.fromkeys(urls):
        if u.lower().endswith(".svg"):
            continue
        d = get(u, timeout=15)
        im = load(d) if d else None
        if im and (best is None or min(im.size) > min(best.size)):
            best = im
        if best and min(best.size) >= 180:
            break
    if best:
        best.save(out)
        return slug, best.size
    return slug, None

if __name__ == "__main__":
    meta = json.load(open(os.path.join(HERE, "meta.json")))
    todo = [(k, v["domain"]) for k, v in meta.items() if not (v.get("web") and v["web"][0] >= 128)]
    with ThreadPoolExecutor(16) as pool:
        res = dict(pool.map(work, todo))
    for k, v in res.items():
        meta[k]["site"] = v
    json.dump(meta, open(os.path.join(HERE, "meta.json"), "w"), indent=1)
    good = sum(1 for v in res.values() if v and min(v) >= 96)
    print("tried", len(todo), "site icons >=96:", good)
