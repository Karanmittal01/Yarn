"""Downloads candidate icons for every brand: the website's icon (via Google's favicon service) and the
Play Store app icon (via search). Saves raw files to raw/<slug>.web.png and raw/<slug>.play.png plus meta.json."""
import json, os, re, sys, time, urllib.parse, urllib.request
from concurrent.futures import ThreadPoolExecutor
from io import BytesIO
from PIL import Image

sys.path.insert(0, os.path.dirname(__file__))
from brands import EXISTING, NEW

HERE = os.path.dirname(os.path.abspath(__file__))
RAW = os.path.join(HERE, "raw")
os.makedirs(RAW, exist_ok=True)
UA = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130 Safari/537.36"


def slug(name):
    return re.sub(r"[^a-z0-9]", "", name.lower())


def get(url, timeout=25):
    req = urllib.request.Request(url, headers={"User-Agent": UA, "Accept-Language": "en-IN,en;q=0.9"})
    for attempt in range(3):
        try:
            with urllib.request.urlopen(req, timeout=timeout) as r:
                return r.read()
        except urllib.error.HTTPError as e:
            if e.code in (404, 410):
                return None
            time.sleep(1.5 * (attempt + 1))
        except Exception:
            time.sleep(1.5 * (attempt + 1))
    return None


def save_image(data, path):
    try:
        im = Image.open(BytesIO(data))
        im.load()
        im = im.convert("RGBA")
        im.save(path)
        return im.size
    except Exception:
        return None


def words(s):
    return [w for w in re.findall(r"[a-z0-9]+", s.lower()) if w not in {"the", "of", "and", "india", "bank", "app", "by", "ltd", "limited"}]


def web_icon(domain, out):
    host = domain.split("/")[0]
    url = "https://t3.gstatic.com/faviconV2?client=SOCIAL&type=FAVICON&fallback_opts=TYPE,SIZE,URL&url=" + urllib.parse.quote("https://" + domain, safe="") + "&size=256"
    data = get(url)
    size = save_image(data, out) if data else None
    if (not size or size[0] < 64) and host != domain:
        data = get(url.replace(urllib.parse.quote("https://" + domain, safe=""), urllib.parse.quote("https://" + host, safe="")))
        size = save_image(data, out) if data else size
    return size


def play_icon(name, query, out):
    q = urllib.parse.quote(query or name)
    page = get(f"https://play.google.com/store/search?q={q}&c=apps&gl=in&hl=en")
    if not page:
        return None
    ids = list(dict.fromkeys(re.findall(r"/store/apps/details\?id=([A-Za-z0-9._]+)", page.decode("utf-8", "ignore"))))[:3]
    want = set(words(name)) or {name.lower()}
    for app in ids:
        details = get(f"https://play.google.com/store/apps/details?id={app}&gl=in&hl=en")
        if not details:
            continue
        html = details.decode("utf-8", "ignore")
        title = re.search(r'<meta property="og:title" content="([^"]+)"', html)
        image = re.search(r'<meta property="og:image" content="([^"=]+)', html)
        if not title or not image:
            continue
        t = title.group(1).replace(" - Apps on Google Play", "")
        dev = re.search(r'"author":\[\{"@type":"Person","name":"([^"]+)"', html) or re.search(r'href="/store/apps/dev(?:eloper)?\?id=[^"]+"[^>]*><span>([^<]+)</span>', html)
        hay = set(words(t + " " + (dev.group(1) if dev else "") + " " + app.replace(".", " ")))
        if not (want & hay) and name.lower().replace(" ", "") not in (t + app).lower().replace(" ", ""):
            continue
        data = get(image.group(1) + "=s256")
        size = save_image(data, out) if data else None
        if size:
            return {"size": size, "app": app, "title": t, "dev": dev.group(1) if dev else None}
    return None


def work(entry):
    name, domain = entry[0], entry[1]
    query = entry[3] if len(entry) > 3 else None
    s = slug(name)
    meta = {"name": name, "domain": domain}
    web = os.path.join(RAW, s + ".web.png")
    play = os.path.join(RAW, s + ".play.png")
    if not os.path.exists(web):
        meta["web"] = web_icon(domain, web)
    else:
        meta["web"] = Image.open(web).size
    if not os.path.exists(play):
        meta["play"] = play_icon(name, query, play)
    else:
        meta["play"] = {"size": Image.open(play).size}
    return s, meta


if __name__ == "__main__":
    entries = [(n, d) for n, d in EXISTING] + [tuple([e[0], e[1]] + ([e[3]] if len(e) > 3 else [])) for e in NEW]
    seen, todo = set(), []
    for e in entries:
        if slug(e[0]) not in seen:
            seen.add(slug(e[0]))
            todo.append(e)
    path = os.path.join(HERE, "meta.json")
    meta = json.load(open(path)) if os.path.exists(path) else {}
    with ThreadPoolExecutor(8) as pool:
        for i, (s, m) in enumerate(pool.map(work, todo)):
            meta[s] = {**meta.get(s, {}), **m}
            if i % 25 == 0:
                print(i, s, m.get("web"), (m.get("play") or {}).get("size"), flush=True)
    json.dump(meta, open(path, "w"), indent=1)
    print("brands", len(todo))
