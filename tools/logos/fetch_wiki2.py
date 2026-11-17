"""Logo from the Wikipedia infobox's logo= field, rendered at 512px."""
import json, os, re, sys, urllib.parse
from concurrent.futures import ThreadPoolExecutor
from io import BytesIO
from PIL import Image
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from fetch_wiki import get, HINT, RAW, HERE

def title_for(q):
    d = get("https://en.wikipedia.org/w/api.php?action=query&format=json&list=search&srlimit=1&srsearch=" + urllib.parse.quote(q))
    try:
        return json.loads(d)["query"]["search"][0]["title"]
    except Exception:
        return None

def work(item):
    slug, name = item
    out = os.path.join(RAW, slug + ".wiki.png")
    if os.path.exists(out):
        return slug, Image.open(out).size
    t = title_for(HINT.get(name, name))
    if not t:
        return slug, None
    d = get("https://en.wikipedia.org/w/api.php?action=parse&format=json&redirects=1&prop=wikitext&section=0&page=" + urllib.parse.quote(t))
    try:
        wt = json.loads(d)["parse"]["wikitext"]["*"]
    except Exception:
        return slug, None
    m = re.search(r"\|\s*(?:logo|image_logo|logo_file)\s*=\s*(?:\[\[)?(?:File:|Image:)?\s*([^|\]\n{}]+?\.(?:svg|png|jpg|jpeg|gif|webp))", wt, re.I)
    if not m:
        return slug, None
    f = m.group(1).strip()
    d = get("https://en.wikipedia.org/w/api.php?action=query&format=json&prop=imageinfo&iiprop=url&iiurlwidth=512&titles=" + urllib.parse.quote("File:" + f))
    try:
        info = next(iter(json.loads(d)["query"]["pages"].values()))["imageinfo"][0]
        url = info.get("thumburl") or info["url"]
        im = Image.open(BytesIO(get(url))).convert("RGBA")
        im.save(out)
        return slug, im.size
    except Exception:
        return slug, None

if __name__ == "__main__":
    meta = json.load(open(os.path.join(HERE, "meta.json")))
    todo = [(k, v["name"]) for k, v in meta.items() if not v.get("wiki")]
    with ThreadPoolExecutor(6) as pool:
        res = dict(pool.map(work, todo))
    for k, v in res.items():
        meta[k]["wiki"] = v
    json.dump(meta, open(os.path.join(HERE, "meta.json"), "w"), indent=1)
    print("wiki now", sum(1 for v in meta.values() if v.get("wiki")), "of", len(meta))
