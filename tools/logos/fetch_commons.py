import json, os, sys, time, urllib.parse, urllib.request
from io import BytesIO
from PIL import Image
RAW = os.path.join(os.path.dirname(os.path.abspath(__file__)), "raw")
UA = "YarnLogoBuild/1.0 (one-off build script)"
def get(url):
    for i in range(4):
        try:
            with urllib.request.urlopen(urllib.request.Request(url, headers={"User-Agent": UA}), timeout=30) as r:
                return r.read()
        except urllib.error.HTTPError as e:
            if e.code == 429:
                time.sleep(int(e.headers.get("retry-after", "20")) + 2); continue
            return None
        except Exception:
            time.sleep(3)
    return None
Q = {"sbi": "State Bank of India logo", "yesbank": "Yes Bank logo", "axisbank": "Axis Bank logo", "hdfcbank": "HDFC Bank logo",
     "kotakbank": "Kotak Mahindra Bank logo", "unionbank": "Union Bank of India logo", "idbibank": "IDBI Bank logo",
     "bandhanbank": "Bandhan Bank logo", "bankofindia": "Bank of India logo", "southindianbank": "South Indian Bank logo",
     "karnatakabank": "Karnataka Bank logo", "ujjivanbank": "Ujjivan logo", "punjabsindbank": "Punjab Sind Bank logo",
     "bluedart": "Blue Dart logo", "dtdc": "DTDC logo", "bsnl": "BSNL logo", "indanegas": "Indane logo",
     "bajajfinserv": "Bajaj Finserv logo", "manappuram": "Manappuram logo", "ltfinance": "L&T Finance logo"}
for slug, q in Q.items():
    d = get("https://commons.wikimedia.org/w/api.php?action=query&format=json&list=search&srnamespace=6&srlimit=3&srsearch=" + urllib.parse.quote(q))
    time.sleep(2)
    titles = [h["title"] for h in (json.loads(d)["query"]["search"] if d else [])]
    if not titles:
        print(slug, "none"); continue
    d = get("https://commons.wikimedia.org/w/api.php?action=query&format=json&prop=imageinfo&iiprop=url&iiurlwidth=512&titles=" + urllib.parse.quote("|".join(titles)))
    time.sleep(2)
    pages = json.loads(d)["query"]["pages"].values() if d else []
    order = {t: i for i, t in enumerate(titles)}
    for p in sorted(pages, key=lambda p: order.get(p["title"], 9)):
        info = (p.get("imageinfo") or [{}])[0]
        url = info.get("thumburl") or info.get("url")
        if not url: continue
        img = get(url); time.sleep(1)
        try:
            im = Image.open(BytesIO(img)).convert("RGBA")
        except Exception:
            continue
        n = order[p["title"]] + 1
        im.save(os.path.join(RAW, f"{slug}.c{n}.png"))
        print(slug, n, p["title"], im.size, flush=True)
