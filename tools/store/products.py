"""Creates Yarn's products in Play Console: the yearly subscription and the testers' lifetime unlock.

Safe to run again: whatever already exists is left as it is (an inactive base plan is activated).
Run by .github/workflows/play-products.yml with the Play service account in PLAY_JSON.
--dry-run only checks access and prints what it would create.
"""
import json
import os
import sys

from google.oauth2 import service_account
from googleapiclient.discovery import build
from googleapiclient.errors import HttpError

PACKAGE = "app.yarn.messages"
LANGUAGE = "en-GB"
SUBSCRIPTION, BASE_PLAN, LIFETIME = "yarn", "yearly", "yarn_lifetime"
YEARLY_INR, LIFETIME_INR = 199, 999


def inr(rupees):
    return {"currencyCode": "INR", "units": str(rupees), "nanos": 0}


def missing(call):
    """Runs a get; None when the product doesn't exist yet."""
    try:
        return call()
    except HttpError as e:
        if e.resp.status == 404:
            return None
        raise


def convert(api, rupees):
    """Play's own conversion of an Indian price into every region's currency."""
    conv = api.monetization().convertRegionPrices(packageName=PACKAGE, body={"price": inr(rupees)}).execute()
    prices = {code: r["price"] for code, r in conv.get("convertedRegionPrices", {}).items()}
    prices["IN"] = inr(rupees)
    return prices, conv.get("convertedOtherRegionsPrice"), conv["regionVersion"]["version"]


def subscription(api, dry):
    subs = api.monetization().subscriptions()
    current = missing(lambda: subs.get(packageName=PACKAGE, productId=SUBSCRIPTION).execute())
    if current is None:
        prices, other, version = convert(api, YEARLY_INR)
        plan = {
            "basePlanId": BASE_PLAN,
            "autoRenewingBasePlanType": {
                "billingPeriodDuration": "P1Y",
                "resubscribeState": "RESUBSCRIBE_STATE_ACTIVE",
                "legacyCompatible": True,
            },
            "regionalConfigs": [
                {"regionCode": code, "newSubscriberAvailability": True, "price": price} for code, price in sorted(prices.items())
            ],
        }
        if other:
            plan["otherRegionsConfig"] = {"usdPrice": other["usdPrice"], "eurPrice": other["eurPrice"], "newSubscriberAvailability": True}
        body = {
            "packageName": PACKAGE,
            "productId": SUBSCRIPTION,
            "listings": [{
                "languageCode": LANGUAGE,
                "title": "Yarn",
                "description": "Everything in Yarn, renewed every year. Cancel anytime in Google Play.",
                "benefits": [
                    "Every message sorted into its tab",
                    "Logos for 1,000+ Indian businesses",
                    "Scam and spam protection",
                    "Smart replies and translation",
                ],
            }],
            "basePlans": [plan],
        }
        print(f"Subscription '{SUBSCRIPTION}': will create base plan '{BASE_PLAN}', yearly, ₹{YEARLY_INR} in India, {len(prices)} regions priced by Play")
        if dry:
            return
        current = subs.create(packageName=PACKAGE, productId=SUBSCRIPTION, regionsVersion_version=version, body=body).execute()
        print(f"Subscription '{SUBSCRIPTION}' created.")
    else:
        print(f"Subscription '{SUBSCRIPTION}' already exists.")

    plans = {p["basePlanId"]: p for p in current.get("basePlans", [])}
    plan = plans.get(BASE_PLAN)
    if plan is None:
        sys.exit(f"Subscription '{SUBSCRIPTION}' has no '{BASE_PLAN}' base plan; add it in Play Console.")
    print(f"Base plan '{BASE_PLAN}': {plan.get('state', 'unknown')}")
    if plan.get("state") != "ACTIVE" and not dry:
        subs.basePlans().activate(packageName=PACKAGE, productId=SUBSCRIPTION, basePlanId=BASE_PLAN, body={}).execute()
        print(f"Base plan '{BASE_PLAN}' activated.")


def lifetime(api, dry):
    title, description = "Yarn for life", "Yarn forever, for the people who helped build it."
    otp = api.monetization().onetimeproducts()
    current = missing(lambda: otp.get(packageName=PACKAGE, productId=LIFETIME).execute())
    if current is None:
        prices, other, version = convert(api, LIFETIME_INR)
        option = {
            "purchaseOptionId": "lifetime",
            "buyOption": {"legacyCompatible": True},
            "regionalPricingAndAvailabilityConfigs": [
                {"regionCode": code, "price": price, "availability": "AVAILABLE"} for code, price in sorted(prices.items())
            ],
        }
        if other:
            option["newRegionsConfig"] = {"usdPrice": other["usdPrice"], "eurPrice": other["eurPrice"], "availability": "AVAILABLE"}
        body = {
            "packageName": PACKAGE,
            "productId": LIFETIME,
            "listings": [{"languageCode": LANGUAGE, "title": title, "description": description}],
            "purchaseOptions": [option],
        }
        print(f"One-time product '{LIFETIME}': will create at ₹{LIFETIME_INR} (only given through promo codes)")
        if dry:
            return
        current = otp.patch(
            packageName=PACKAGE, productId=LIFETIME, body=body, allowMissing=True,
            updateMask="listings,purchaseOptions", regionsVersion_version=version,
        ).execute()
        print(f"One-time product '{LIFETIME}' created.")
    else:
        print(f"One-time product '{LIFETIME}' already exists.")

    for option in current.get("purchaseOptions", []):
        state = option.get("state", "unknown")
        print(f"Purchase option '{option.get('purchaseOptionId')}': {state}")
        if state != "ACTIVE" and not dry:
            otp.purchaseOptions().batchUpdateStates(
                packageName=PACKAGE, productId=LIFETIME,
                body={"requests": [{"activatePurchaseOptionRequest": {
                    "packageName": PACKAGE, "productId": LIFETIME, "purchaseOptionId": option["purchaseOptionId"],
                }}]},
            ).execute()
            print(f"Purchase option '{option['purchaseOptionId']}' activated.")


def main():
    dry = "--dry-run" in sys.argv
    creds = service_account.Credentials.from_service_account_info(
        json.loads(os.environ["PLAY_JSON"]), scopes=["https://www.googleapis.com/auth/androidpublisher"],
    )
    # The live API description: one-time products are newer than the client's bundled copy.
    api = build("androidpublisher", "v3", credentials=creds, cache_discovery=False, static_discovery=False)
    failed = False
    for step in (subscription, lifetime):
        try:
            step(api, dry)
        except HttpError as e:
            failed = True
            print(f"{step.__name__} failed: HTTP {e.resp.status}: {e.content.decode('utf-8', 'replace')[:1500]}")
    if dry:
        print("Dry run: nothing created.")
    if failed:
        sys.exit(1)


if __name__ == "__main__":
    main()
