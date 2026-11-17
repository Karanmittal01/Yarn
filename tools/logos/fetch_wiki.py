"""Third candidate: the logo from the brand's English Wikipedia page (rendered to PNG at 512px)."""
import json, os, sys, urllib.parse, urllib.request, time
from concurrent.futures import ThreadPoolExecutor
from io import BytesIO
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
RAW = os.path.join(HERE, "raw")
UA = "YarnLogoBuild/1.0 (one-off build script)"
HINT = {
    "SBI": "State Bank of India", "PNB": "Punjab National Bank", "Union Bank": "Union Bank of India",
    "Central Bank": "Central Bank of India", "Kotak Bank": "Kotak Mahindra Bank", "AU Bank": "AU Small Finance Bank",
    "Bob Card": "Bank of Baroda", "Ujjivan Bank": "Ujjivan Small Finance Bank", "Equitas Bank": "Equitas Small Finance Bank",
    "Fino Bank": "Fino Payments Bank", "J&K Bank": "Jammu & Kashmir Bank", "Max Life": "Axis Max Life Insurance",
    "Vi": "Vodafone Idea", "Jio": "Jio", "Airtel": "Bharti Airtel", "Ola": "Ola Cabs", "Uber": "Uber",
    "UIDAI (Aadhaar)": "Unique Identification Authority of India", "Income Tax Dept": "Income Tax Department",
    "GST": "Goods and Services Tax Network", "NPS": "National Pension System", "LIC": "Life Insurance Corporation",
    "Apollo": "Apollo Hospitals", "Chola": "Cholamandalam Investment and Finance Company", "HP": "HP Inc.",
    "Shell": "Shell plc", "Indane Gas": "Indane", "Bharat Gas": "Bharat Petroleum", "Kia": "Kia India",
    "Trends": "Reliance Trends", "Titan": "Titan Company", "Godrej": "Godrej Group", "Mahindra": "Mahindra & Mahindra",
    "Honda": "Honda Cars India", "Tata Motors": "Tata Motors", "TRAI": "Telecom Regulatory Authority of India",
    "RBI": "Reserve Bank of India", "SEBI": "Securities and Exchange Board of India", "Visa": "Visa Inc.",
    "District": "District by Zomato", "Swiggy Toing": "Swiggy", "Fi": "Fi Money", "Navi": "Navi Technologies",
    "Jupiter": "Jupiter (neobank)", "slice": "Slice (company)", "Uni Cards": "Uni Cards", "CRED": "CRED (company)",
    "Simpl": "Simpl (company)", "Plum": "Plum Goodness", "Nothing": "Nothing (company)", "Campus": "Campus Activewear",
    "Lifestyle": "Lifestyle International", "Spectra": "Spectra (Indian company)", "Kiwi": "Kiwi credit card",
    "Jar": "Jar (app)", "Fibe": "Fibe (company)", "Freo": "Freo (company)", "Dhani": "Dhani Services",
    "aha": "Aha (streaming service)", "apna": "Apna (company)", "Shine": "Shine.com", "Scaler": "Scaler Academy",
    "Box8": "Box8", "Porter": "Porter (company)", "Smartr": "Smartr Logistics", "Ekart": "Ekart",
}


def get(url):
    req = urllib.request.Request(url, headers={"User-Agent": UA})
    for i in range(3):
        try:
            with urllib.request.urlopen(req, timeout=25) as r:
                return r.read()
        except Exception:
            time.sleep(1 + i)
    return None


def work(item):
    slug, name = item
    out = os.path.join(RAW, slug + ".wiki.png")
    if os.path.exists(out):
        return slug, Image.open(out).size
    q = HINT.get(name, name)
    api = ("https://en.wikipedia.org/w/api.php?action=query&format=json&redirects=1&prop=pageimages&piprop=thumbnail"
           "&pithumbsize=512&pilicense=any&generator=search&gsrlimit=1&gsrsearch=" + urllib.parse.quote(q))
    data = get(api)
    if not data:
        return slug, None
    pages = (json.loads(data).get("query") or {}).get("pages") or {}
    for p in pages.values():
        thumb = (p.get("thumbnail") or {}).get("source")
        if not thumb:
            continue
        img = get(thumb)
        try:
            im = Image.open(BytesIO(img)).convert("RGBA")
            im.save(out)
            return slug, im.size
        except Exception:
            return slug, None
    return slug, None


if __name__ == "__main__":
    meta = json.load(open(os.path.join(HERE, "meta.json")))
    with ThreadPoolExecutor(6) as pool:
        res = dict(pool.map(work, [(k, v["name"]) for k, v in meta.items()]))
    for k, v in res.items():
        meta[k]["wiki"] = v
    json.dump(meta, open(os.path.join(HERE, "meta.json"), "w"), indent=1)
    print("wiki", sum(1 for v in res.values() if v), "of", len(res))
