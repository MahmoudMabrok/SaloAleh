/**
 * Ad banner design system — copy, palettes, artwork and per-format layouts.
 *
 * Pure string/HTML generation, no I/O. `build-ad-banners.js` feeds the HTML
 * returned by `renderBanner` to headless Chromium to produce the PNG/JPG files.
 *
 * Brand source of truth: `landing.html` (palette) and the app's bundled fonts
 * (Aref Ruqaa for display Arabic, IBM Plex Sans Arabic for everything else).
 */

const PLAY_URL = 'https://play.google.com/store/apps/details?id=tools.mo3ta.salo';

/* ── Palette ──────────────────────────────────────────────────────────── */

const THEMES = {
  ink: {
    bg: 'radial-gradient(115% 130% at 78% 6%, #191d33 0%, #0d1020 44%, #07080f 100%)',
    fg: '#f4e9d5',
    dim: 'rgba(238,222,196,.56)',
    accent: '#e5b65a',
    accentDeep: '#c8953c',
    panel: 'rgba(255,255,255,.045)',
    rim: 'rgba(200,149,60,.26)',
    onAccent: '#10121d',
  },
  emerald: {
    bg: 'radial-gradient(115% 130% at 78% 6%, #23724f 0%, #12432f 46%, #07231a 100%)',
    fg: '#f6ecd8',
    dim: 'rgba(246,236,216,.6)',
    accent: '#f0c979',
    accentDeep: '#d3a24a',
    panel: 'rgba(255,255,255,.07)',
    rim: 'rgba(240,201,121,.3)',
    onAccent: '#0b2b1e',
  },
  ember: {
    bg: 'radial-gradient(115% 130% at 76% 4%, #2c2032 0%, #14101f 48%, #08060d 100%)',
    fg: '#f6e8d6',
    dim: 'rgba(246,232,214,.56)',
    accent: '#f0a952',
    accentDeep: '#cf7f34',
    panel: 'rgba(255,255,255,.05)',
    rim: 'rgba(240,169,82,.26)',
    onAccent: '#160f1c',
  },
  night: {
    bg: 'radial-gradient(100% 120% at 50% 0%, #14172a 0%, #0a0c17 52%, #05060b 100%)',
    fg: '#f4e9d5',
    dim: 'rgba(238,222,196,.5)',
    accent: '#e5b65a',
    accentDeep: '#c8953c',
    panel: 'rgba(255,255,255,.04)',
    rim: 'rgba(200,149,60,.2)',
    onAccent: '#10121d',
  },
};

/* ── Concepts ─────────────────────────────────────────────────────────── */
/**
 * Every claim below is a shipped feature:
 *  - weekly round + global leaderboard  → CLAUDE.md "Firebase RTDB structure"
 *  - Friday ×2                          → string `mohamed_lovers_friday_bonus_label`
 *  - 7-day streak badge                 → `ROUND_STREAK_TARGET = 7`
 *  - medals / rank badges               → "Winner medal badge"
 *  - free, no sign-up                   → no auth, device-uuid identity
 */
const CONCEPTS = [
  {
    id: '01-compete',
    theme: 'ink',
    art: 'leaderboard',
    name: 'Compete — global leaderboard',
    ar: {
      eyebrow: 'منافسة أسبوعية',
      headline: 'أرسل الصلاة على النبي ﷺ',
      headlineEm: 'ونافس العالم',
      sub: 'ترتيب عالمي مباشر، وكل جمعة تبدأ جولة جديدة.',
      short: 'نافس العالم في الصلاة على النبي',
      cta: 'حمّل مجانًا',
    },
    en: {
      eyebrow: 'Weekly competition',
      headline: 'Send salawat on the Prophet ﷺ',
      headlineEm: 'and compete with the world',
      sub: 'A live global leaderboard — every Friday a new round begins.',
      short: 'Compete with the world in salawat',
      cta: 'Get it free',
    },
  },
  {
    id: '02-friday',
    theme: 'emerald',
    art: 'friday',
    name: 'Friday — double points',
    ar: {
      eyebrow: 'يوم الجمعة',
      headline: 'صلاتك تُحتسب',
      headlineEm: 'مضاعفة × ٢',
      sub: '«أكثِروا من الصلاة عليَّ يوم الجمعة» — ونقاطك فيه تتضاعف.',
      short: 'يوم الجمعة نقاطك × ٢',
      cta: 'ابدأ الآن',
    },
    en: {
      eyebrow: 'Friday bonus',
      headline: 'Every salawat counts',
      headlineEm: 'double on Friday',
      sub: 'The best day of the week — and the day your score doubles.',
      short: 'Double points every Friday',
      cta: 'Start now',
    },
  },
  {
    id: '03-streak',
    theme: 'ember',
    art: 'streak',
    name: 'Streak — seven days',
    ar: {
      eyebrow: 'سلسلة الأيام',
      headline: 'سبعة أيام متتالية',
      headlineEm: 'تكسب شارة',
      sub: 'صلِّ كل يوم ولو مرة — لا تكسر سلسلتك.',
      short: '٧ أيام متتالية = شارة',
      cta: 'ابدأ سلسلتك',
    },
    en: {
      eyebrow: 'Daily streak',
      headline: 'Seven days in a row',
      headlineEm: 'earns you a badge',
      sub: 'Send salawat every day — never break the chain.',
      short: '7 days in a row earns a badge',
      cta: 'Start your streak',
    },
  },
  {
    id: '04-community',
    theme: 'ink',
    art: 'globe',
    name: 'Community — one ummah',
    ar: {
      eyebrow: 'مجتمع عالمي',
      headline: 'دول تفصلكم',
      headlineEm: 'وصلاة تجمعكم',
      sub: 'انضم إلى محبّين يصلّون على النبي ﷺ من كل مكان.',
      short: 'مجتمع يصلّي على النبي من كل مكان',
      cta: 'انضم إليهم',
    },
    en: {
      eyebrow: 'One community',
      headline: 'Countries apart,',
      headlineEm: 'united in salawat',
      sub: 'Join believers sending blessings on the Prophet ﷺ worldwide.',
      short: 'One community, salawat worldwide',
      cta: 'Join them',
    },
  },
  {
    id: '05-onetap',
    theme: 'night',
    art: 'tap',
    name: 'One tap — minimal',
    ar: {
      eyebrow: 'مجانًا — بدون تسجيل',
      headline: 'لمسة واحدة،',
      headlineEm: 'صلاة واحدة',
      sub: 'افتح التطبيق والمس الشاشة. بسيط بقدر ما هو عظيم.',
      short: 'لمسة واحدة = صلاة واحدة',
      cta: 'حمّل التطبيق',
    },
    en: {
      eyebrow: 'Free — no sign-up',
      headline: 'One tap,',
      headlineEm: 'one salawat',
      sub: 'Open the app and touch the screen. As simple as it is rewarding.',
      short: 'One tap = one salawat',
      cta: 'Download the app',
    },
  },
];

/* ── Formats ──────────────────────────────────────────────────────────── */
/** `scale` renders a family's canonical layout at another exact pixel size. */
const FORMATS = [
  { id: 'feed-1200x628', w: 1200, h: 628, family: 'feed', jpg: true, use: 'Meta / Google Display — landscape feed' },
  { id: 'square-1080x1080', w: 1080, h: 1080, family: 'square', jpg: true, use: 'Meta / Instagram feed — square' },
  { id: 'story-1080x1920', w: 1080, h: 1920, family: 'story', jpg: true, use: 'Stories / Reels — full portrait' },
  { id: 'mrec-300x250', w: 300, h: 250, family: 'box', use: 'AdMob / GDN — medium rectangle' },
  { id: 'lrec-336x280', w: 336, h: 280, family: 'box', scale: 1.12, use: 'GDN — large rectangle' },
  { id: 'leaderboard-728x90', w: 728, h: 90, family: 'strip', use: 'GDN — desktop leaderboard' },
  { id: 'mobile-320x100', w: 320, h: 100, family: 'mobile', use: 'AdMob — large mobile banner' },
  { id: 'sky-160x600', w: 160, h: 600, family: 'tall', use: 'GDN — wide skyscraper' },
];

/* ── Artwork ──────────────────────────────────────────────────────────── */

const medalStops = [
  ['#f7d774', '#c8953c'],
  ['#e3e6ee', '#9aa3b5'],
  ['#e0a877', '#a56a3c'],
];

/** Top-3 leaderboard card. */
function artLeaderboard(t, size) {
  const rows = [
    { code: 'EG', tag: 'A3F9C2', score: '3,181', w: 100 },
    { code: 'YE', tag: '7B21D4', score: '2,640', w: 78 },
    { code: 'TD', tag: 'C09E15', score: '2,105', w: 60 },
  ];
  const defs = medalStops
    .map(
      ([a, b], i) =>
        `<linearGradient id="m${i}" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="${a}"/><stop offset="1" stop-color="${b}"/></linearGradient>`,
    )
    .join('');
  const body = rows
    .map((r, i) => {
      const y = 18 + i * 74;
      return `
      <g transform="translate(0 ${y})">
        <rect x="0" y="0" width="380" height="58" rx="16" fill="${t.panel}" stroke="${t.rim}" stroke-width="1"/>
        <circle cx="330" cy="29" r="18" fill="url(#m${i})"/>
        <text x="330" y="36" text-anchor="middle" font-family="Plex" font-weight="600" font-size="18" fill="#1a1408">${i + 1}</text>
        <text x="296" y="35" text-anchor="end" font-family="Plex" font-weight="500" font-size="16" fill="${t.fg}" letter-spacing="1">${r.code} · ${r.tag}</text>
        <rect x="24" y="24" width="${r.w}" height="10" rx="5" fill="${t.accentDeep}" opacity=".5"/>
        <text x="24" y="46" font-family="Plex" font-weight="600" font-size="15" fill="${t.accent}">${r.score}</text>
      </g>`;
    })
    .join('');
  return svg(
    size,
    380,
    250,
    `<defs>${defs}</defs>${body}`,
  );
}

/** Crescent + ×2 over the week, with Friday lit. */
function artFriday(t, size, locale) {
  const two = locale === 'ar' ? '٢' : '2';
  const dots = Array.from({ length: 7 }, (_, i) => {
    const on = i === 4;
    const cx = 62 + i * 43;
    return on
      ? `<circle cx="${cx}" cy="216" r="13" fill="${t.accent}"/><circle cx="${cx}" cy="216" r="21" fill="none" stroke="${t.accent}" stroke-width="1.5" opacity=".45"/>`
      : `<circle cx="${cx}" cy="216" r="7" fill="none" stroke="${t.accent}" stroke-width="1.5" opacity=".4"/>`;
  }).join('');
  return svg(
    size,
    380,
    250,
    `<defs>
       <mask id="cres"><rect width="380" height="250" fill="#fff"/><circle cx="207" cy="98" r="66" fill="#000"/></mask>
       <linearGradient id="cg" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="${t.accent}"/><stop offset="1" stop-color="${t.accentDeep}"/></linearGradient>
     </defs>
     <circle cx="178" cy="98" r="82" fill="url(#cg)" mask="url(#cres)" opacity=".95"/>
     <text x="256" y="122" font-family="Plex" font-weight="600" font-size="72" fill="${t.fg}">×${two}</text>
     <line x1="46" y1="182" x2="334" y2="182" stroke="${t.accent}" stroke-width="1" opacity=".25"/>
     ${dots}`,
  );
}

/** A flame over the seven-day chain, the last link a badge. */
function artStreak(t, size) {
  const nodes = Array.from({ length: 7 }, (_, i) => {
    const x = 46 + i * 48;
    const last = i === 6;
    const link = i < 6 ? `<line x1="${x + 14}" y1="212" x2="${x + 34}" y2="212" stroke="${t.accent}" stroke-width="2" opacity=".35"/>` : '';
    if (last) {
      return `${link}<circle cx="${x}" cy="212" r="21" fill="${t.accent}"/>
        <path d="M${x} 200l4.6 9.4 10.4 1.5-7.5 7.3 1.8 10.3-9.3-4.9-9.3 4.9 1.8-10.3-7.5-7.3 10.4-1.5z" fill="${t.onAccent}"/>`;
    }
    return `${link}<circle cx="${x}" cy="212" r="14" fill="${t.accentDeep}"/>
      <text x="${x}" y="217" text-anchor="middle" font-family="Plex" font-weight="600" font-size="13" fill="${t.onAccent}">${i + 1}</text>`;
  }).join('');
  return svg(
    size,
    380,
    250,
    `<defs><linearGradient id="fl" x1="0" y1="1" x2="0" y2="0"><stop offset="0" stop-color="${t.accentDeep}"/><stop offset="1" stop-color="${t.accent}"/></linearGradient></defs>
     <path d="M190 18c24 42 56 58 56 92 0 34-25 56-56 56s-56-22-56-56c0-20 12-38 24-52 2 18 12 26 20 22 12-6 4-36 12-62z" fill="url(#fl)" opacity=".95"/>
     <path d="M190 82c12 20 24 30 24 46 0 20-11 32-24 32s-24-12-24-32c0-16 12-26 24-46z" fill="${t.fg}" opacity=".3"/>
     ${nodes}`,
  );
}

/** Globe with meridians and four live country pins. */
function artGlobe(t, size) {
  const line = `stroke="${t.accent}" stroke-width="1.5" opacity=".38" fill="none"`;
  const pins = [
    [128, 82],
    [238, 66],
    [166, 152],
    [256, 136],
  ]
    .map(
      ([x, y]) =>
        `<circle cx="${x}" cy="${y}" r="6.5" fill="${t.accent}"/><circle cx="${x}" cy="${y}" r="14" fill="none" stroke="${t.accent}" stroke-width="1.5" opacity=".5"/>`,
    )
    .join('');
  return svg(
    size,
    380,
    250,
    `<circle cx="190" cy="112" r="98" fill="${t.accent}" opacity=".05"/>
     <circle cx="190" cy="112" r="98" stroke="${t.accent}" stroke-width="1.8" opacity=".55" fill="none"/>
     <ellipse cx="190" cy="112" rx="38" ry="98" ${line}/>
     <ellipse cx="190" cy="112" rx="74" ry="98" ${line}/>
     <line x1="92" y1="112" x2="288" y2="112" ${line}/>
     <path d="M113 62h154M113 162h154" ${line}/>
     <path d="M128 82q56-38 110-16" stroke="${t.accent}" stroke-width="1.5" stroke-dasharray="4 5" opacity=".55" fill="none"/>
     <path d="M166 152q52 6 90-16" stroke="${t.accent}" stroke-width="1.5" stroke-dasharray="4 5" opacity=".55" fill="none"/>
     ${pins}`,
  );
}

/** Tap ripple around a solid centre. */
function artTap(t, size) {
  const rings = [50, 82, 114, 146]
    .map(
      (r, i) =>
        `<circle cx="190" cy="112" r="${r}" fill="none" stroke="${t.accent}" stroke-width="${2.2 - i * 0.4}" opacity="${0.55 - i * 0.12}"/>`,
    )
    .join('');
  return svg(
    size,
    380,
    250,
    `${rings}
     <circle cx="190" cy="112" r="34" fill="${t.accent}"/>
     <circle cx="190" cy="112" r="21" fill="none" stroke="${t.onAccent}" stroke-width="2.5" opacity=".55"/>`,
  );
}

const ART = {
  leaderboard: artLeaderboard,
  friday: artFriday,
  streak: artStreak,
  globe: artGlobe,
  tap: artTap,
};

function svg(size, vw, vh, inner) {
  const h = Math.round((size * vh) / vw);
  return `<svg class="art-svg" width="${size}" height="${h}" viewBox="0 0 ${vw} ${vh}" xmlns="http://www.w3.org/2000/svg" fill="none">${inner}</svg>`;
}

/* ── Layout ───────────────────────────────────────────────────────────── */

const esc = (s) => String(s).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');

function lockup(assets, c, l, px) {
  const name = l === 'ar' ? 'صلوا عليه' : 'SaloAleh';
  return `<div class="lockup"><img class="logo" src="${assets.logo}" style="width:${px}px;height:${px}px"><span class="brand">${esc(name)}</span></div>`;
}

function storeLine(l) {
  return `<span class="store">${l === 'ar' ? 'متوفر على Google Play' : 'Get it on Google Play'}</span>`;
}

function head(c, l, cls) {
  const t = c[l];
  return `<h1 class="${cls}">${esc(t.headline)}<br><em>${esc(t.headlineEm)}</em></h1>`;
}

const LAYOUTS = {
  feed: (c, l, a) => `
    <div class="stage feed">
      <div class="copy">
        ${lockup(a, c, l, 46)}
        <p class="eyebrow">${esc(c[l].eyebrow)}</p>
        ${head(c, l, 'headline')}
        <p class="sub">${esc(c[l].sub)}</p>
        <div class="cta-row">
          <span class="cta">${esc(c[l].cta)}</span>
          ${storeLine(l)}
        </div>
      </div>
      <div class="art">${ART[c.art](a.theme, 380, l)}</div>
    </div>`,

  square: (c, l, a) => `
    <div class="stage square">
      ${lockup(a, c, l, 76)}
      <div class="art">${ART[c.art](a.theme, 560, l)}</div>
      <div class="copy">
        <p class="eyebrow">${esc(c[l].eyebrow)}</p>
        ${head(c, l, 'headline')}
        <p class="sub">${esc(c[l].sub)}</p>
      </div>
      <div class="cta-row">
        <span class="cta">${esc(c[l].cta)}</span>
        ${storeLine(l)}
      </div>
    </div>`,

  story: (c, l, a) => `
    <div class="stage story">
      ${lockup(a, c, l, 92)}
      <div class="art">${ART[c.art](a.theme, 620, l)}</div>
      <div class="copy">
        <p class="eyebrow">${esc(c[l].eyebrow)}</p>
        ${head(c, l, 'headline')}
        <p class="sub">${esc(c[l].sub)}</p>
      </div>
      <div class="cta-row">
        <span class="cta">${esc(c[l].cta)}</span>
        ${storeLine(l)}
      </div>
    </div>`,

  box: (c, l, a) => `
    <div class="stage box">
      ${lockup(a, c, l, 26)}
      <p class="headline">${esc(c[l].short)}</p>
      <div class="art">${ART[c.art](a.theme, 168, l)}</div>
      <span class="cta">${esc(c[l].cta)}</span>
    </div>`,

  strip: (c, l, a) => `
    <div class="stage strip">
      ${lockup(a, c, l, 52)}
      <span class="rule"></span>
      <div class="copy">
        <p class="headline">${esc(c[l].short)}</p>
        <p class="sub">${esc(c[l].sub)}</p>
      </div>
      <span class="cta">${esc(c[l].cta)}</span>
    </div>`,

  mobile: (c, l, a) => `
    <div class="stage mobile">
      <img class="logo" src="${a.logo}">
      <div class="copy">
        <p class="headline">${esc(c[l].short)}</p>
        <p class="brand-line">${l === 'ar' ? 'صلوا عليه — Google Play' : 'SaloAleh — Google Play'}</p>
      </div>
      <span class="cta">${esc(c[l].cta)}</span>
    </div>`,

  tall: (c, l, a) => `
    <div class="stage tall">
      ${lockup(a, c, l, 44)}
      <p class="headline">${esc(c[l].short)}</p>
      <div class="art">${ART[c.art](a.theme, 124, l)}</div>
      <div class="foot">
        <span class="cta">${esc(c[l].cta)}</span>
        ${storeLine(l)}
      </div>
    </div>`,
};

/* ── CSS ──────────────────────────────────────────────────────────────── */

function css(t, l, family) {
  // Headlines run in IBM Plex Sans Arabic: at a glance-length ad impression
  // legibility beats calligraphy. Aref Ruqaa is kept for the accent line on the
  // large formats only, where it reads clearly and carries the app's character.
  const calligraphic = l === 'ar' && ['feed', 'square', 'story'].includes(family);
  return `
  *,*::before,*::after{box-sizing:border-box;margin:0;padding:0}
  html,body{background:${t.bg};overflow:hidden}
  body{font-family:Plex,sans-serif;color:${t.fg};-webkit-font-smoothing:antialiased;text-rendering:geometricPrecision}
  .stage{position:relative;width:100vw;height:100vh;background:${t.bg}}
  .stage::before{content:"";position:absolute;inset:0;background:url("${pattern(t)}") repeat;background-size:120px 120px;opacity:.5;pointer-events:none}
  .stage::after{content:"";position:absolute;inset:0;border:1px solid ${t.rim};pointer-events:none}
  .lockup{display:flex;align-items:center;gap:.55em}
  .logo{border-radius:22%;object-fit:cover;clip-path:inset(10.6% round 24%);transform:scale(1.26)}
  .brand{font-weight:600;color:${t.fg};letter-spacing:.01em;white-space:nowrap}
  .eyebrow{color:${t.accentDeep};font-weight:600;letter-spacing:.04em}
  .headline{font-family:Plex;font-weight:600;line-height:1.4;color:${t.fg};letter-spacing:0}
  .headline em{font-style:normal;color:${t.accent}${calligraphic ? ';font-family:Ruqaa;font-weight:700;line-height:1.7' : ''}}
  .sub{color:${t.dim};font-weight:300;line-height:1.65}
  .cta{display:inline-block;background:linear-gradient(140deg,${t.accent},${t.accentDeep});color:${t.onAccent};font-weight:600;border-radius:999px;white-space:nowrap;box-shadow:0 6px 22px rgba(0,0,0,.28)}
  .store{color:${t.dim};font-weight:300}
  .cta-row{display:flex;align-items:center}
  /* The artwork carries Latin codes and digits; the page's RTL direction would
     otherwise reorder them and flip every text-anchor inside the SVG. */
  .art-svg{display:block;direction:ltr}
  ${FAMILY_CSS[family](t)}`;
}

const FAMILY_CSS = {
  feed: (t) => `
    .feed{display:flex;align-items:center}
    .feed .copy{flex:1 1 0;padding:0 72px}
    .feed .art{flex:0 0 470px;display:flex;align-items:center;justify-content:center;padding-inline-end:56px}
    .feed .lockup{margin-bottom:30px}
    .feed .brand{font-size:20px}
    .feed .eyebrow{font-size:18px;margin-bottom:12px}
    .feed .headline{font-size:48px}
    .feed .sub{font-size:21px;margin-top:18px;max-width:500px}
    .feed .cta-row{margin-top:34px;gap:22px}
    .feed .cta{font-size:22px;padding:16px 40px}
    .feed .store{font-size:15px}`,

  square: (t) => `
    .square{display:flex;flex-direction:column;align-items:center;justify-content:space-between;padding:88px 84px 78px;text-align:center}
    .square .art{display:flex;justify-content:center}
    .square .brand{font-size:34px}
    .square .eyebrow{font-size:26px;margin-bottom:20px}
    .square .headline{font-size:70px}
    .square .sub{font-size:28px;margin-top:26px}
    .square .cta-row{flex-direction:column;gap:20px}
    .square .cta{font-size:30px;padding:22px 62px}
    .square .store{font-size:20px}`,

  story: (t) => `
    .story{display:flex;flex-direction:column;align-items:center;justify-content:space-between;padding:170px 84px 190px;text-align:center}
    .story .art{display:flex;justify-content:center}
    .story .brand{font-size:40px}
    .story .eyebrow{font-size:28px;margin-bottom:22px}
    .story .headline{font-size:80px}
    .story .sub{font-size:31px;margin-top:30px}
    .story .cta-row{flex-direction:column;gap:24px}
    .story .cta{font-size:34px;padding:26px 76px}
    .story .store{font-size:22px}`,

  box: (t) => `
    .box{display:flex;flex-direction:column;align-items:center;justify-content:space-between;padding:16px 18px 18px;text-align:center;overflow:hidden}
    .box .brand{font-size:12.5px}
    .box .headline{font-size:19px;line-height:1.35;margin-top:10px}
    .box .art{display:flex;justify-content:center;margin:6px 0 4px}
    .box .cta{font-size:13px;padding:9px 24px}`,

  strip: (t) => `
    .strip{display:flex;align-items:center;gap:20px;padding:0 22px}
    .strip .brand{font-size:17px}
    .strip .rule{width:1px;height:46px;background:${t.rim};flex:0 0 auto}
    .strip .copy{flex:1 1 auto;min-width:0}
    .strip .headline{font-size:22px;line-height:1.25;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
    .strip .sub{font-size:13px;margin-top:3px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
    .strip .cta{font-size:14.5px;padding:10px 26px;flex:0 0 auto}`,

  mobile: (t) => `
    .mobile{display:flex;align-items:center;gap:12px;padding:0 14px}
    .mobile .logo{width:46px;height:46px;flex:0 0 auto;border-radius:22%}
    .mobile .copy{flex:1 1 auto;min-width:0}
    .mobile .headline{font-size:16px;line-height:1.25;font-weight:600;font-family:Plex}
    .mobile .brand-line{font-size:11px;color:${t.dim};margin-top:4px;font-weight:300}
    .mobile .cta{font-size:12.5px;padding:9px 18px;flex:0 0 auto}`,

  tall: (t) => `
    .tall{display:flex;flex-direction:column;align-items:center;justify-content:space-around;padding:28px 14px 24px;text-align:center}
    .tall .lockup{flex-direction:column;gap:10px}
    .tall .brand{font-size:15px}
    .tall .headline{font-size:21px;line-height:1.42}
    .tall .art{display:flex;justify-content:center}
    .tall .foot{display:flex;flex-direction:column;align-items:center;gap:10px;width:100%}
    .tall .cta{font-size:14px;padding:11px 18px;width:100%}
    .tall .store{font-size:10.5px}`,
};

/** Low-contrast eight-point star tile — the app's geometric motif. */
function pattern(t) {
  const stroke = encodeURIComponent(t.accent);
  const s = `<svg xmlns="http://www.w3.org/2000/svg" width="120" height="120"><g fill="none" stroke="%23${stroke.slice(3)}" stroke-width="1" opacity="0.05"><path d="M60 8 82 30 104 30 104 52 126 74 104 96 104 118 82 118 60 140 38 118 16 118 16 96 -6 74 16 52 16 30 38 30Z"/><path d="M0 60 30 30 60 60 30 90Z"/><path d="M120 60 90 30 60 60 90 90Z"/></g></svg>`;
  return `data:image/svg+xml,${s.replace(/#/g, '%23').replace(/"/g, "'")}`;
}

/* ── Page ─────────────────────────────────────────────────────────────── */

function renderBanner({ concept, format, locale, assets }) {
  const theme = THEMES[concept.theme];
  const a = { ...assets, theme };
  return `<!doctype html>
<html lang="${locale}" dir="${locale === 'ar' ? 'rtl' : 'ltr'}">
<head><meta charset="utf-8">
<style>
${assets.fontFace}
${css(theme, locale, format.family)}
</style></head>
<body>${LAYOUTS[format.family](concept, locale, a)}</body></html>`;
}

module.exports = { CONCEPTS, FORMATS, THEMES, PLAY_URL, renderBanner };
