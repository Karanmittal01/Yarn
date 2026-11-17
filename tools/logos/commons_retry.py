"""Slow, polite Commons lookups for brands still without a logo: up to 4 candidates each."""
import json, os, sys, time, urllib.parse
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from io import BytesIO
from PIL import Image
import fetch_commons as fc  # guarded below: fetch_commons has no main guard, so only its helpers are used
Q = {
 "unionbank": "Union Bank of India logo", "idbibank": "IDBI Bank logo", "bandhanbank": "Bandhan Bank logo",
 "bankofindia": "Bank of India logo", "ucobank": "UCO Bank logo", "bankofmaharashtra": "Bank of Maharashtra logo",
 "southindianbank": "South Indian Bank logo", "karnatakabank": "Karnataka Bank logo", "ujjivanbank": "Ujjivan Small Finance Bank logo",
 "punjabsindbank": "Punjab & Sind Bank logo", "saraswatbank": "Saraswat Bank logo", "bluedart": "Blue Dart logo",
 "dtdc": "DTDC logo", "bsnl": "BSNL logo", "indanegas": "Indane logo", "bharatgas": "Bharat Petroleum logo",
 "incometaxdept": "Income Tax Department India logo", "manappuram": "Manappuram Finance logo", "ltfinance": "L&T Finance logo",
 "hdbfinancial": "HDB Financial Services logo", "kotaklife": "Kotak Life Insurance logo", "pnbmetlife": "PNB MetLife logo",
 "sbimutualfund": "SBI Mutual Fund logo", "shadowfax": "Shadowfax Technologies logo", "indianrailways": "Indian Railways logo",
 "tatamotors": "Tata Motors logo", "hp": "HP logo 2012", "lenovo": "Lenovo logo 2015", "sony": "Sony logo",
 "whirlpool": "Whirlpool Corporation logo", "haier": "Haier logo", "bluestar": "Blue Star India logo", "lakme": "Lakme logo",
 "kalyanjewellers": "Kalyan Jewellers logo", "dlf": "DLF logo", "lodha": "Lodha Group logo", "cbse": "CBSE logo",
 "mpl": "Mobile Premier League logo", "zupee": "Zupee logo", "jungleerummy": "Junglee Games logo", "subway": "Subway restaurant logo 2016",
 "paisabazaar": "Paisabazaar logo", "5paisa": "5paisa logo", "geojit": "Geojit logo", "herofincorp": "Hero FinCorp logo",
 "plum": "Plum Goodness logo", "campus": "Campus Activewear logo", "trends": "Reliance Trends logo",
}
for slug, q in Q.items():
    if any(os.path.exists(os.path.join(fc.RAW, f"{slug}.c{n}.png")) for n in (1, 2, 3, 4)):
        continue
    d = fc.get("https://commons.wikimedia.org/w/api.php?action=query&format=json&list=search&srnamespace=6&srlimit=4&srsearch=" + urllib.parse.quote(q))
    time.sleep(4)
    titles = [h["title"] for h in (json.loads(d)["query"]["search"] if d else [])]
    if not titles:
        print(slug, "none", flush=True); continue
    d = fc.get("https://commons.wikimedia.org/w/api.php?action=query&format=json&prop=imageinfo&iiprop=url&iiurlwidth=512&titles=" + urllib.parse.quote("|".join(titles)))
    time.sleep(4)
    order = {t: i for i, t in enumerate(titles)}
    for p in sorted(json.loads(d)["query"]["pages"].values() if d else [], key=lambda p: order.get(p["title"], 9)):
        info = (p.get("imageinfo") or [{}])[0]
        url = info.get("thumburl") or info.get("url")
        if not url: continue
        img = fc.get(url); time.sleep(2)
        try:
            im = Image.open(BytesIO(img)).convert("RGBA")
        except Exception:
            continue
        n = order[p["title"]] + 1
        im.save(os.path.join(fc.RAW, f"{slug}.c{n}.png"))
        print(slug, n, p["title"], im.size, flush=True)
