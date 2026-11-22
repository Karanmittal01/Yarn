"""Publishes Yarn's Play Store listing: app name, short and full description, phone screenshots.

The text comes from distribution/store/LISTING.md (the App name, Short description and Full
description blocks) and the images from distribution/store/screenshot-*.png, so that page stays
the one source. Run by .github/workflows/store-listing.yml with the Play service account in
PLAY_JSON. --dry-run checks access and prints what would change without saving anything.
"""
import glob
import hashlib
import json
import os
import re
import sys

from google.oauth2 import service_account
from googleapiclient.discovery import build
from googleapiclient.errors import HttpError
from googleapiclient.http import MediaFileUpload

PACKAGE = "app.yarn.messages"
ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
STORE = os.path.join(ROOT, "distribution", "store")
LIMITS = {"title": 30, "shortDescription": 80, "fullDescription": 4000}


def block(md, label):
    m = re.search(r"\*\*" + re.escape(label) + r"\*\*[^\n]*\n```\n(.*?)\n```", md, re.S)
    if not m:
        sys.exit(f"LISTING.md has no '{label}' block")
    return m.group(1).strip()


def listing_text():
    md = open(os.path.join(STORE, "LISTING.md"), encoding="utf-8").read()
    text = {
        "title": block(md, "App name"),
        "shortDescription": block(md, "Short description"),
        "fullDescription": block(md, "Full description"),
    }
    for key, limit in LIMITS.items():
        if len(text[key]) > limit:
            sys.exit(f"{key} is {len(text[key])} characters; Play allows {limit}")
    return text


def screenshots():
    shots = sorted(glob.glob(os.path.join(STORE, "screenshot-*.png")), key=lambda p: int(re.search(r"(\d+)\.png$", p).group(1)))
    if not 2 <= len(shots) <= 8:
        sys.exit(f"Play needs 2 to 8 phone screenshots; found {len(shots)}")
    return shots


def main():
    dry_run = "--dry-run" in sys.argv
    text, shots = listing_text(), screenshots()
    creds = service_account.Credentials.from_service_account_info(
        json.loads(os.environ["PLAY_JSON"]), scopes=["https://www.googleapis.com/auth/androidpublisher"],
    )
    edits = build("androidpublisher", "v3", credentials=creds, cache_discovery=False).edits()
    edit = edits.insert(packageName=PACKAGE, body={}).execute()["id"]
    try:
        language = edits.details().get(packageName=PACKAGE, editId=edit).execute()["defaultLanguage"]
        current = edits.listings().get(packageName=PACKAGE, editId=edit, language=language).execute()
        print(f"Default listing language: {language}")
        for key in LIMITS:
            same = (current.get(key) or "").strip() == text[key]
            print(f"  {key}: {'unchanged' if same else 'will change'}")
        old = edits.images().list(packageName=PACKAGE, editId=edit, language=language, imageType="phoneScreenshots").execute()
        print(f"  phone screenshots: {len(old.get('images', []))} now, {len(shots)} new")
        same_text = all((current.get(key) or "").strip() == text[key] for key in LIMITS)
        same_shots = [i.get("sha256") for i in old.get("images", [])] == [hashlib.sha256(open(p, "rb").read()).hexdigest() for p in shots]
        if same_text and same_shots:
            # Nothing to change: don't send the listing back to Google for review.
            print("Listing already up to date.")
            edits.delete(packageName=PACKAGE, editId=edit).execute()
            return
        if dry_run:
            print("Dry run: nothing saved.")
            edits.delete(packageName=PACKAGE, editId=edit).execute()
            return

        body = {"language": language, "title": text["title"], "shortDescription": text["shortDescription"], "fullDescription": text["fullDescription"]}
        if current.get("video"):
            body["video"] = current["video"]
        if not same_text:
            edits.listings().update(packageName=PACKAGE, editId=edit, language=language, body=body).execute()
        if not same_shots:
            edits.images().deleteall(packageName=PACKAGE, editId=edit, language=language, imageType="phoneScreenshots").execute()
            for path in shots:
                edits.images().upload(
                    packageName=PACKAGE, editId=edit, language=language, imageType="phoneScreenshots",
                    media_body=MediaFileUpload(path, mimetype="image/png"),
                ).execute()
                print(f"  uploaded {os.path.basename(path)}")
        try:
            edits.commit(packageName=PACKAGE, editId=edit).execute()
            print("Listing saved and sent for review.")
        except HttpError as e:
            if "changesNotSentForReview" not in str(e):
                raise
            # The console wants these sent for review by hand (e.g. managed publishing is on).
            edits.commit(packageName=PACKAGE, editId=edit, changesNotSentForReview=True).execute()
            print("Listing saved. Send it for review in Play Console → Publishing overview.")
    except Exception:
        try:
            edits.delete(packageName=PACKAGE, editId=edit).execute()
        except Exception:
            pass
        raise


if __name__ == "__main__":
    main()
