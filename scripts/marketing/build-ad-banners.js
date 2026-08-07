#!/usr/bin/env node
/**
 * Renders the ad banner kit to `docs/marketing/ad-banners/`.
 *
 *   node scripts/marketing/build-ad-banners.js              # Arabic (default)
 *   node scripts/marketing/build-ad-banners.js --locale=en
 *   node scripts/marketing/build-ad-banners.js --locale=ar,en
 *   node scripts/marketing/build-ad-banners.js --only=02-friday --format=feed-1200x628
 *
 * Everything is self-contained: the app's own fonts and launcher icon are
 * inlined as data URIs, so the page renders identically anywhere headless
 * Chromium is available and needs no network.
 */

const fs = require('fs');
const path = require('path');
const { CONCEPTS, FORMATS, renderBanner } = require('./banner-design');
const { Chrome } = require('./chrome-shot');

const ROOT = path.resolve(__dirname, '../..');
const OUT = path.join(ROOT, 'docs/marketing/ad-banners');
const FONT_DIR = path.join(ROOT, 'app/src/commonMain/composeResources/font');
const LOGO = path.join(ROOT, 'app/src/main/res/playstore-icon.png');

/* ── args ─────────────────────────────────────────────────────────────── */

const argv = Object.fromEntries(
  process.argv.slice(2).map((a) => {
    const [k, v] = a.replace(/^--/, '').split('=');
    return [k, v ?? 'true'];
  }),
);
const locales = (argv.locale || 'ar').split(',').map((s) => s.trim());
const conceptFilter = argv.only ? argv.only.split(',') : null;
const formatFilter = argv.format ? argv.format.split(',') : null;

/* ── binaries ─────────────────────────────────────────────────────────── */

function findChrome() {
  const candidates = [
    process.env.CHROME_PATH,
    '/opt/pw-browsers/chromium-1194/chrome-linux/chrome',
    ...glob('/opt/pw-browsers', /^chromium-\d+$/).map((d) => path.join(d, 'chrome-linux/chrome')),
    '/usr/bin/chromium',
    '/usr/bin/google-chrome',
  ].filter(Boolean);
  const hit = candidates.find((p) => fs.existsSync(p));
  if (!hit) throw new Error('No Chromium found. Set CHROME_PATH to a Chrome/Chromium binary.');
  return hit;
}

function glob(dir, re) {
  if (!fs.existsSync(dir)) return [];
  return fs
    .readdirSync(dir)
    .filter((n) => re.test(n))
    .map((n) => path.join(dir, n));
}

/* ── assets ───────────────────────────────────────────────────────────── */

function dataUri(file, mime) {
  return `data:${mime};base64,${fs.readFileSync(file).toString('base64')}`;
}

function buildFontFace() {
  const faces = [
    ['Plex', 'ibm_plex_sans_arabic_light.ttf', 300],
    ['Plex', 'ibm_plex_sans_arabic_regular.ttf', 400],
    ['Plex', 'ibm_plex_sans_arabic_medium.ttf', 500],
    ['Plex', 'ibm_plex_sans_arabic_semibold.ttf', 600],
    ['Ruqaa', 'aref_ruqaa_bold.ttf', 700],
  ];
  return faces
    .map(
      ([family, file, weight]) =>
        `@font-face{font-family:${family};font-weight:${weight};font-display:block;src:url("${dataUri(
          path.join(FONT_DIR, file),
          'font/ttf',
        )}") format("truetype")}`,
    )
    .join('\n');
}

/* ── render ───────────────────────────────────────────────────────────── */

const assets = { fontFace: buildFontFace(), logo: dataUri(LOGO, 'image/png') };
const kb = (f) => `${Math.round(fs.statSync(f).size / 1024)}KB`;

/** Google Display rejects image ads above this; social feeds are far looser. */
const GDN_MAX_KB = 150;

async function main() {
  const chrome = new Chrome(findChrome());
  await chrome.start();

  const manifest = [];
  const oversized = [];
  let count = 0;

  try {
    for (const locale of locales) {
      for (const concept of CONCEPTS) {
        if (conceptFilter && !conceptFilter.includes(concept.id)) continue;
        const dir = path.join(OUT, locale, concept.id);
        fs.mkdirSync(dir, { recursive: true });

        for (const format of FORMATS) {
          if (formatFilter && !formatFilter.includes(format.id)) continue;
          const scale = format.scale || 1;
          const html = renderBanner({ concept, format, locale, assets });
          const metrics = {
            width: Math.round(format.w / scale),
            height: Math.round(format.h / scale),
            deviceScaleFactor: scale,
          };

          // Social formats ship as JPEG — a lossless twin would add ~18MB to the
          // repo for an image nobody uploads. Re-run the build for a PNG.
          const files = [];
          if (format.jpg) {
            const jpg = path.join(dir, `${format.id}.jpg`);
            fs.writeFileSync(jpg, await chrome.shoot(html, { ...metrics, format: 'jpeg', quality: 88 }));
            files.push(jpg);
          } else {
            const png = path.join(dir, `${format.id}.png`);
            fs.writeFileSync(png, await chrome.shoot(html, { ...metrics, format: 'png' }));
            files.push(png);
            if (fs.statSync(png).size > GDN_MAX_KB * 1024) oversized.push(path.relative(OUT, png));
          }

          count += files.length;
          manifest.push({
            locale,
            concept: concept.id,
            format: format.id,
            size: `${format.w}x${format.h}`,
            use: format.use,
            files: files.map((f) => ({ path: path.relative(OUT, f), bytes: fs.statSync(f).size })),
          });
          console.log(`  ${locale}/${concept.id}/${format.id}  ${files.map(kb).join(' · ')}`);
        }
      }
    }
  } finally {
    await chrome.stop();
  }

  fs.writeFileSync(path.join(OUT, 'manifest.json'), `${JSON.stringify(manifest, null, 2)}\n`);
  console.log(`\n${count} files -> ${path.relative(ROOT, OUT)}`);
  if (oversized.length) {
    console.warn(`\nOver the ${GDN_MAX_KB}KB display-network limit:\n  ${oversized.join('\n  ')}`);
  }
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});
