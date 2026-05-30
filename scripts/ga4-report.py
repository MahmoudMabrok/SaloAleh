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
SUMMARY_FILE = os.environ.get("GITHUB_STEP_SUMMARY")

APP_FILTER = {
    "filter": {
        "fieldName": "streamName",
        "stringFilter": {"matchType": "EXACT", "value": "Salo"},
    }
}

md_lines = []


def out(text=""):
    print(text)
    if SUMMARY_FILE:
        md_lines.append(text)


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
    try:
        with urllib.request.urlopen(req, context=_SSL) as r:
            return json.load(r)
    except urllib.error.HTTPError as e:
        print(f"HTTP {e.code}: {e.read().decode()}", file=sys.stderr)
        raise


def report(tok, prop, dimensions, metrics, label, limit=25):
    body = {
        "dateRanges": [{"startDate": f"{DAYS}daysAgo", "endDate": "today"}],
        "dimensions": [{"name": d} for d in dimensions],
        "metrics": [{"name": m} for m in metrics],
        "dimensionFilter": APP_FILTER,
        "limit": limit,
        "orderBys": [{"metric": {"metricName": metrics[0]}, "desc": True}],
    }
    d = api(f"https://analyticsdata.googleapis.com/v1beta/{prop}:runReport", tok, body)
    rows = d.get("rows", [])

    out(f"\n### {label} (last {DAYS}d)")
    out()
    dim_header = " | ".join(dimensions) if dimensions else ""
    cols = [dim_header] + metrics if dim_header else metrics
    out("| " + " | ".join(cols) + " |")
    out("| " + " | ".join("---:" if i > 0 or not dim_header else "---" for i, _ in enumerate(cols)) + " |")

    if not rows:
        out("| " + " | ".join(["*(no data)*"] * len(cols)) + " |")
        return
    for row in rows:
        dim_vals = " | ".join(v["value"] or "—" for v in row.get("dimensionValues", []))
        met_vals = [m["value"] for m in row.get("metricValues", [])]
        cells = [dim_vals] + met_vals if dim_header else met_vals
        out("| " + " | ".join(cells) + " |")


def screen_views(tok, prop):
    body = {
        "dateRanges": [{"startDate": f"{DAYS}daysAgo", "endDate": "today"}],
        "dimensions": [{"name": "eventName"}],
        "metrics": [{"name": "eventCount"}],
        "dimensionFilter": {
            "andGroup": {
                "expressions": [
                    APP_FILTER,
                    {"filter": {"fieldName": "eventName", "stringFilter": {"matchType": "BEGINS_WITH", "value": "salo_screen_view_"}}},
                ]
            }
        },
        "limit": 25,
        "orderBys": [{"metric": {"metricName": "eventCount"}, "desc": True}],
    }
    d = api(f"https://analyticsdata.googleapis.com/v1beta/{prop}:runReport", tok, body)
    rows = d.get("rows", [])

    out(f"\n### SCREEN VIEWS (last {DAYS}d)")
    out()
    out("| screen | views |")
    out("| --- | ---: |")
    if not rows:
        out("| *(no data)* | |")
        return
    for row in rows:
        name = row["dimensionValues"][0]["value"].replace("salo_screen_view_", "")
        count = row["metricValues"][0]["value"]
        out(f"| {name} | {count} |")


def main():
    tok = token()
    prop = os.environ.get("GA4_PROPERTY", "529874204")
    prop = f"properties/{prop}"

    out(f"# SaloAleh GA4 Report")

    report(tok, prop, [], ["activeUsers", "newUsers", "sessions", "screenPageViews", "eventCount"], "TOTALS")
    report(tok, prop, ["eventName"], ["eventCount"], "TOP EVENTS")
    screen_views(tok, prop)
    report(tok, prop, ["country"], ["activeUsers"], "USERS BY COUNTRY")
    report(tok, prop, ["deviceCategory", "operatingSystem"], ["activeUsers"], "PLATFORM")
    report(tok, prop, ["date"], ["activeUsers", "newUsers"], "DAILY ACTIVE", limit=60)

    if SUMMARY_FILE:
        with open(SUMMARY_FILE, "a") as f:
            f.write("\n".join(md_lines) + "\n")


if __name__ == "__main__":
    main()
