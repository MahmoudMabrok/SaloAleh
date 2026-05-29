#!/usr/bin/env python3
"""Pull GA4 analytics for SaloAleh via the Analytics Data API.

Auth: Firebase Admin SDK service account key at $SALO_SA_KEY.
The SA must be granted Viewer on the GA4 property (GA Admin -> Property Access).

Usage:
  python3 scripts/ga4-report.py            # default property 529874204, last 28 days
  python3 scripts/ga4-report.py 280d       # last 280 days
  GA4_PROPERTY=123456789 python3 scripts/ga4-report.py
"""
import os
import sys
import json
import ssl
import urllib.request

try:
    import certifi
    _SSL = ssl.create_default_context(cafile=certifi.where())
except ImportError:
    _SSL = ssl.create_default_context()

KEY = os.environ.get("SALO_SA_KEY", os.path.expanduser("~/.config/salo-analytics/sa-key.json"))
SCOPE = "https://www.googleapis.com/auth/analytics.readonly"
DAYS = sys.argv[1] if len(sys.argv) > 1 else "28d"
DAYS = DAYS.rstrip("d")


def token():
    from google.oauth2 import service_account
    from google.auth.transport.requests import Request
    creds = service_account.Credentials.from_service_account_file(KEY, scopes=[SCOPE])
    creds.refresh(Request())
    return creds.token


def api(url, tok, body=None):
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(url, data=data, method="POST" if data else "GET")
    req.add_header("Authorization", f"Bearer {tok}")
    if data:
        req.add_header("Content-Type", "application/json")
    with urllib.request.urlopen(req, context=_SSL) as r:
        return json.load(r)


def discover_property(tok):
    d = api("https://analyticsadmin.googleapis.com/v1beta/accountSummaries?pageSize=200", tok)
    props = []
    for a in d.get("accountSummaries", []):
        for p in a.get("propertySummaries", []):
            props.append((p.get("displayName"), p.get("property")))
    return props


def report(tok, prop, dimensions, metrics, label, limit=25):
    body = {
        "dateRanges": [{"startDate": f"{DAYS}daysAgo", "endDate": "today"}],
        "dimensions": [{"name": d} for d in dimensions],
        "metrics": [{"name": m} for m in metrics],
        "limit": limit,
        "orderBys": [{"metric": {"metricName": metrics[0]}, "desc": True}],
    }
    d = api(f"https://analyticsdata.googleapis.com/v1beta/{prop}:runReport", tok, body)
    print(f"\n=== {label} (last {DAYS}d) ===")
    rows = d.get("rows", [])
    if not rows:
        print("  (no data)")
        return
    for row in rows:
        dims = " | ".join(v["value"] for v in row.get("dimensionValues", []))
        mets = " ".join(f"{m['value']}" for m in row.get("metricValues", []))
        print(f"  {dims:<45} {mets}")


def main():
    tok = token()
    prop = os.environ.get("GA4_PROPERTY", "529874204")
    prop = f"properties/{prop}"

    report(tok, prop, [], ["activeUsers", "newUsers", "sessions", "screenPageViews", "eventCount"], "TOTALS")
    report(tok, prop, ["eventName"], ["eventCount"], "TOP EVENTS")
    report(tok, prop, ["unifiedScreenName"], ["screenPageViews"], "SCREEN VIEWS")
    report(tok, prop, ["country"], ["activeUsers"], "USERS BY COUNTRY")
    report(tok, prop, ["deviceCategory", "operatingSystem"], ["activeUsers"], "PLATFORM")
    report(tok, prop, ["date"], ["activeUsers", "newUsers"], "DAILY ACTIVE", limit=60)


if __name__ == "__main__":
    main()
