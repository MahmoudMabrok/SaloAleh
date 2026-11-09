const { describe, it } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const {
  chooseNextVersionCode,
  collectBundleVersionCodes,
  collectTrackVersionCodes,
  loadServiceAccount,
  queryPlayVersionCodes,
  writeGithubOutput,
} = require('./play-version-code');

describe('chooseNextVersionCode', () => {
  it('uses the highest code across tracks, including internal ahead of production', () => {
    const choice = chooseNextVersionCode({
      trackVersionCodes: collectTrackVersionCodes({
        tracks: [
          { track: 'production', releases: [{ status: 'completed', versionCodes: ['167'] }] },
          { track: 'internal', releases: [{ status: 'completed', versionCodes: ['171'] }] },
        ],
      }),
      bundleVersionCodes: collectBundleVersionCodes({ bundles: [{ versionCode: 167 }] }),
    });
    assert.equal(choice.maxTrackVersionCode, 171);
    assert.equal(choice.latestVersionCode, 171);
    assert.equal(choice.nextVersionCode, 172);
  });

  it('includes halted and draft releases and numeric bundle codes', () => {
    const tracks = collectTrackVersionCodes({
      tracks: [
        {
          track: 'internal',
          releases: [
            { status: 'completed', versionCodes: ['171'] },
            { status: 'halted', versionCodes: [169] },
            { status: 'draft', versionCodes: ['178'] },
          ],
        },
      ],
    });
    const bundles = collectBundleVersionCodes({
      bundles: [{ versionCode: '180' }, { versionCode: null }, {}],
    });
    const choice = chooseNextVersionCode({ trackVersionCodes: tracks, bundleVersionCodes: bundles });
    assert.equal(choice.maxTrackVersionCode, 178);
    assert.equal(choice.maxBundleVersionCode, 180);
    assert.equal(choice.nextVersionCode, 181);
  });

  it('uses an uploaded bundle when it is ahead of every track', () => {
    const choice = chooseNextVersionCode({
      trackVersionCodes: [171],
      bundleVersionCodes: [178],
    });
    assert.equal(choice.latestVersionCode, 178);
    assert.equal(choice.nextVersionCode, 179);
  });

  it('refuses an empty catalog instead of inventing Gradle+1', () => {
    assert.throws(
      () => chooseNextVersionCode({
        trackVersionCodes: collectTrackVersionCodes({ tracks: [{ track: 'production', releases: [] }] }),
        bundleVersionCodes: collectBundleVersionCodes({ bundles: [] }),
      }),
      /Refusing to fall back to the Gradle versionCode/,
    );
  });

  it('ignores tracks or releases that are missing', () => {
    assert.deepEqual(collectTrackVersionCodes(null), []);
    assert.deepEqual(collectTrackVersionCodes({ tracks: [null, { releases: null }] }), []);
    assert.deepEqual(collectBundleVersionCodes(undefined), []);
  });
});

describe('loadServiceAccount', () => {
  it('fails clearly when the secret is missing', () => {
    assert.throws(() => loadServiceAccount(''), /GOOGLE_PLAY_SERVICE_ACCOUNT_JSON is empty/);
    assert.throws(() => loadServiceAccount('not-json'), /not valid JSON/);
  });
});

describe('queryPlayVersionCodes', () => {
  it('reads tracks and bundles, then deletes the edit without committing it', async () => {
    const calls = [];
    const fetchImpl = async (token, method, url) => {
      calls.push({ token, method, url });
      if (method === 'POST') return { id: 'edit-1' };
      if (url.endsWith('/tracks')) {
        return { tracks: [{ track: 'internal', releases: [{ versionCodes: ['171'] }] }] };
      }
      if (url.endsWith('/bundles')) return { bundles: [{ versionCode: 167 }] };
      if (method === 'DELETE') return {};
      throw new Error(`unexpected ${method} ${url}`);
    };
    const result = await queryPlayVersionCodes({
      accessToken: 'token-not-logged',
      packageName: 'tools.mo3ta.salo',
      fetchImpl,
    });
    assert.equal(result.nextVersionCode, 172);
    assert.deepEqual(calls.map((call) => call.method), ['POST', 'GET', 'GET', 'DELETE']);
    assert.equal(calls.some((call) => call.method === 'POST' && call.url.includes('/commit')), false);
    assert.equal(calls.at(-1).url.endsWith('/edits/edit-1'), true);
  });

  it('still deletes the edit when listing tracks fails', async () => {
    const methods = [];
    const fetchImpl = async (_token, method, url) => {
      methods.push(method);
      if (method === 'POST') return { id: 'edit-2' };
      if (url.endsWith('/tracks')) throw new Error('Play tracks failed (403): permission denied');
      if (method === 'DELETE') return {};
      return {};
    };
    await assert.rejects(
      () => queryPlayVersionCodes({
        accessToken: 'token',
        packageName: 'tools.mo3ta.salo',
        fetchImpl,
      }),
      /permission denied/,
    );
    assert.deepEqual(methods, ['POST', 'GET', 'DELETE']);
  });
});

describe('writeGithubOutput', () => {
  it('writes the codes the deploy job reads', () => {
    const file = path.join(os.tmpdir(), `play-version-${process.pid}.txt`);
    writeGithubOutput({
      maxTrackVersionCode: 171,
      maxBundleVersionCode: null,
      latestVersionCode: 171,
      nextVersionCode: 172,
    }, file);
    const text = fs.readFileSync(file, 'utf8');
    assert.match(text, /max_track_version_code=171/);
    assert.match(text, /max_bundle_version_code=none/);
    assert.match(text, /latest_version_code=171/);
    assert.match(text, /next_version_code=172/);
    fs.unlinkSync(file);
  });
});
