// Reads the highest Android versionCode already on Google Play and prints the
// next code to upload. Play is the source of truth: a Gradle versionCode that
// is behind internal (or any other track) must not be incremented on its own.
//
// The edit opened here is deleted and never committed, so this script does not
// publish, upload, or change a track.

const crypto = require('node:crypto');

const PLAY_SCOPE = 'https://www.googleapis.com/auth/androidpublisher';
const TOKEN_URL = 'https://oauth2.googleapis.com/token';
const PLAY_API = 'https://androidpublisher.googleapis.com/androidpublisher/v3/applications';

function asVersionCode(value) {
  if (typeof value === 'number' && Number.isInteger(value) && value >= 0) return value;
  if (typeof value === 'string' && /^[0-9]+$/.test(value)) return Number(value);
  return null;
}

function collectTrackVersionCodes(tracksResponse) {
  const codes = [];
  const tracks = tracksResponse && Array.isArray(tracksResponse.tracks) ? tracksResponse.tracks : [];
  for (const track of tracks) {
    const releases = track && Array.isArray(track.releases) ? track.releases : [];
    for (const release of releases) {
      const versionCodes = release && Array.isArray(release.versionCodes) ? release.versionCodes : [];
      for (const value of versionCodes) {
        const code = asVersionCode(value);
        if (code != null) codes.push(code);
      }
    }
  }
  return codes;
}

function collectBundleVersionCodes(bundlesResponse) {
  const bundles = bundlesResponse && Array.isArray(bundlesResponse.bundles) ? bundlesResponse.bundles : [];
  const codes = [];
  for (const bundle of bundles) {
    const code = asVersionCode(bundle && bundle.versionCode);
    if (code != null) codes.push(code);
  }
  return codes;
}

function maxOrNull(codes) {
  if (!codes.length) return null;
  return Math.max(...codes);
}

/**
 * Next upload code is one higher than the highest versionCode Play already
 * has, whether that code is on a track (production, internal, alpha, beta, or
 * any other) or only on an uploaded bundle. An empty catalog is an error:
 * callers must not fall back to the Gradle file.
 */
function chooseNextVersionCode({ trackVersionCodes, bundleVersionCodes }) {
  const tracks = (trackVersionCodes || []).filter((code) => asVersionCode(code) != null).map(Number);
  const bundles = (bundleVersionCodes || []).filter((code) => asVersionCode(code) != null).map(Number);
  const maxTrackVersionCode = maxOrNull(tracks);
  const maxBundleVersionCode = maxOrNull(bundles);
  const present = [maxTrackVersionCode, maxBundleVersionCode].filter((code) => code != null);
  if (!present.length) {
    throw new Error(
      'Google Play returned no versionCode on any track or uploaded bundle. ' +
        'Refusing to fall back to the Gradle versionCode.',
    );
  }
  const latestVersionCode = Math.max(...present);
  return {
    maxTrackVersionCode,
    maxBundleVersionCode,
    latestVersionCode,
    nextVersionCode: latestVersionCode + 1,
  };
}

function signServiceAccountJwt(serviceAccount) {
  const now = Math.floor(Date.now() / 1000);
  const header = Buffer.from(JSON.stringify({ alg: 'RS256', typ: 'JWT' })).toString('base64url');
  const payload = Buffer.from(JSON.stringify({
    iss: serviceAccount.client_email,
    scope: PLAY_SCOPE,
    aud: TOKEN_URL,
    iat: now - 60,
    exp: now + 3600,
  })).toString('base64url');
  const signer = crypto.createSign('RSA-SHA256');
  signer.update(`${header}.${payload}`);
  signer.end();
  const signature = signer.sign(serviceAccount.private_key).toString('base64url');
  return `${header}.${payload}.${signature}`;
}

function loadServiceAccount(raw) {
  if (!raw || !raw.trim()) {
    throw new Error(
      'GOOGLE_PLAY_SERVICE_ACCOUNT_JSON is empty. Refusing to guess versionCode from Gradle.',
    );
  }
  let parsed;
  try {
    parsed = JSON.parse(raw);
  } catch (error) {
    throw new Error(`GOOGLE_PLAY_SERVICE_ACCOUNT_JSON is not valid JSON (${error.message}).`);
  }
  if (!parsed.client_email || !parsed.private_key) {
    throw new Error('GOOGLE_PLAY_SERVICE_ACCOUNT_JSON is missing client_email or private_key.');
  }
  return parsed;
}

function packageNameFromEnv(env) {
  const packageName = (env.PLAY_PACKAGE_NAME || 'tools.mo3ta.salo').trim();
  if (!/^[A-Za-z0-9_.]+$/.test(packageName)) {
    throw new Error(`Refusing Play package name "${packageName}".`);
  }
  return packageName;
}

async function playFetch(accessToken, method, url, body) {
  const response = await fetch(url, {
    method,
    headers: {
      Authorization: `Bearer ${accessToken}`,
      Accept: 'application/json',
      ...(body ? { 'Content-Type': 'application/json' } : {}),
    },
    body: body ? JSON.stringify(body) : undefined,
    signal: AbortSignal.timeout(30000),
  });
  const text = await response.text();
  let json = null;
  if (text) {
    try {
      json = JSON.parse(text);
    } catch {
      json = null;
    }
  }
  if (!response.ok) {
    const detail = (json && json.error && (json.error.message || json.error.status)) || text.slice(0, 400);
    throw new Error(`Google Play API ${method} ${url} failed (${response.status}): ${detail}`);
  }
  return json || {};
}

async function queryPlayVersionCodes({ accessToken, packageName, fetchImpl }) {
  const call = fetchImpl || playFetch;
  const root = `${PLAY_API}/${encodeURIComponent(packageName)}/edits`;
  const edit = await call(accessToken, 'POST', root, {});
  const editId = edit && edit.id;
  if (!editId) {
    throw new Error('Google Play did not return an edit id. Refusing to fall back to the Gradle versionCode.');
  }
  try {
    const tracks = await call(accessToken, 'GET', `${root}/${encodeURIComponent(editId)}/tracks`);
    const bundles = await call(accessToken, 'GET', `${root}/${encodeURIComponent(editId)}/bundles`);
    return chooseNextVersionCode({
      trackVersionCodes: collectTrackVersionCodes(tracks),
      bundleVersionCodes: collectBundleVersionCodes(bundles),
    });
  } finally {
    await call(accessToken, 'DELETE', `${root}/${encodeURIComponent(editId)}`).catch((error) => {
      console.error(`Warning: could not delete the read-only Play edit ${editId}: ${error.message}`);
    });
  }
}

function writeGithubOutput(result, outputPath) {
  if (!outputPath) return;
  const fs = require('node:fs');
  const lines = [
    `max_track_version_code=${result.maxTrackVersionCode == null ? 'none' : result.maxTrackVersionCode}`,
    `max_bundle_version_code=${result.maxBundleVersionCode == null ? 'none' : result.maxBundleVersionCode}`,
    `latest_version_code=${result.latestVersionCode}`,
    `next_version_code=${result.nextVersionCode}`,
    '',
  ];
  fs.appendFileSync(outputPath, lines.join('\n'));
}

async function main(env = process.env) {
  const serviceAccount = loadServiceAccount(env.GOOGLE_PLAY_SERVICE_ACCOUNT_JSON);
  const packageName = packageNameFromEnv(env);
  const assertion = signServiceAccountJwt(serviceAccount);
  const tokenResponse = await fetch(TOKEN_URL, {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams({
      grant_type: 'urn:ietf:params:oauth:grant-type:jwt-bearer',
      assertion,
    }),
    signal: AbortSignal.timeout(30000),
  });
  const tokenJson = await tokenResponse.json().catch(() => ({}));
  if (!tokenResponse.ok || !tokenJson.access_token) {
    const detail = [tokenJson.error, tokenJson.error_description].filter(Boolean).join(': ') || 'no access_token';
    throw new Error(
      `Google Play authentication failed (${tokenResponse.status}): ${detail}. ` +
        'Refusing to fall back to the Gradle versionCode.',
    );
  }
  const result = await queryPlayVersionCodes({
    accessToken: tokenJson.access_token,
    packageName,
  });
  writeGithubOutput(result, env.GITHUB_OUTPUT);
  console.error(
    `Play highest versionCode is ${result.latestVersionCode} ` +
      `(tracks: ${result.maxTrackVersionCode == null ? 'none' : result.maxTrackVersionCode}, ` +
      `bundles: ${result.maxBundleVersionCode == null ? 'none' : result.maxBundleVersionCode}). ` +
      `Next versionCode is ${result.nextVersionCode}.`,
  );
  return result;
}

module.exports = {
  asVersionCode,
  collectTrackVersionCodes,
  collectBundleVersionCodes,
  chooseNextVersionCode,
  loadServiceAccount,
  packageNameFromEnv,
  queryPlayVersionCodes,
  writeGithubOutput,
};

if (require.main === module) {
  main().catch((error) => {
    console.error(error.message);
    process.exit(1);
  });
}
