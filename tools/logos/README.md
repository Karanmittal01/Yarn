# Business logos

The app shows a logo beside messages from well-known Indian businesses. The logos ship inside the app
(`app/src/main/assets/logos/<key>.webp`, 144 px, about 2 KB each), so nothing is looked up online and no one
learns who messages you. A sender gets a logo only when its registered SMS sender ID matches a brand in
`SenderNames.kt`; the key is the brand name in lower case with only letters and digits ("HDFC Bank" -> `hdfcbank`).

These scripts rebuild them. They need Python 3 with Pillow and NumPy.

1. `brands.py`: every brand with its website and, for brands new to `SenderNames.kt`, its sender-ID prefixes.
2. `fetch.py`, `fetch_site.py`, `fetch_wiki.py`, `fetch_wiki2.py`, `fetch_commons.py`: download candidate icons
   (the website's icon, the Play Store app icon, the Wikipedia or Wikimedia Commons logo) into `raw/`.
3. `choices.py`: which candidate each brand uses (or none), and small crops for icons with banners.
4. `build.py`: fits each mark inside the circle on its own background colour and writes the WebP files.
5. `gen_kotlin.py`: turns the new prefixes in `brands.py` into `SenderNames.kt` entries and checks that no prefix
   steals another brand's sender IDs.

Look at every logo before shipping it: app icons often show a sub-brand ("YONO", "IRIS") or a "NEW" ribbon.
