// Best-effort country detection from the browser locale, with manual override.
// Mirrors the iOS fallback (NSLocale.currentLocale.countryCode) rather than
// telephony — the browser has no SIM/network ISO.

const UNKNOWN = "NA";

function detectCountryCode() {
  const candidates = [];
  if (navigator.language) candidates.push(navigator.language);
  if (Array.isArray(navigator.languages)) candidates.push(...navigator.languages);

  for (const tag of candidates) {
    const region = parseRegion(tag);
    if (region) return region;
  }
  return UNKNOWN;
}

function parseRegion(tag) {
  if (!tag || typeof tag !== "string") return null;
  // Examples: "en-US", "ar-EG", "ar", "zh-Hant-TW", "en-Latn-US"
  const parts = tag.split("-");
  for (let i = 1; i < parts.length; i++) {
    const p = parts[i];
    if (/^[A-Za-z]{2}$/.test(p)) return p.toUpperCase();
    if (/^[0-9]{3}$/.test(p)) return p; // UN M.49 region code
  }
  return null;
}

self.SaloCountry = { detectCountryCode, UNKNOWN };
