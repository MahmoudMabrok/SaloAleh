const { describe, it } = require('node:test');
const assert = require('node:assert/strict');
const {
  isReplaceableNotes,
  notesFromCompare,
  parseReleaseList,
  planRelease,
  pullRequestFromMessage,
  releaseTag,
  selectPreviousRelease,
} = require('./release-notes');

const REPO = 'MahmoudMabrok/SaloAleh';

// Publish order from the real project: v4.12.1 shipped before the v4.2 line,
// so a semver "previous tag" and the previous release are not the same tag.
const HISTORY = [
  { tagName: 'v4.2.2', publishedAt: '2026-11-08T19:24:24Z', isDraft: false },
  { tagName: 'v4.2.1', publishedAt: '2026-11-06T16:07:45Z', isDraft: false },
  { tagName: 'v4.2.0', publishedAt: '2026-11-06T09:44:05Z', isDraft: false },
  { tagName: 'v4.1.8', publishedAt: '2026-10-31T16:48:30Z', isDraft: false },
  { tagName: 'v4.12.1', publishedAt: '2026-10-23T19:37:17Z', isDraft: false },
  { tagName: 'vytu', publishedAt: '2026-08-10T17:52:38Z', isDraft: false },
];

function release(overrides) {
  return planRelease({
    version: '4.2.3',
    versionCode: '172',
    androidResult: 'success',
    iosResult: '',
    track: 'production',
    repo: REPO,
    releases: HISTORY,
    existing: { exists: false, body: '' },
    compare: { commits: [], total_commits: 0 },
    ...overrides,
  });
}

describe('releaseTag', () => {
  it('prefixes the version name the way the deploy workflow always has', () => {
    assert.equal(releaseTag('4.2.2'), 'v4.2.2');
    assert.equal(releaseTag('4.1.2.1'), 'v4.1.2.1');
  });

  it('rejects a version that cannot be a single git tag', () => {
    assert.throws(() => releaseTag('4.2.2/hotfix'), /Invalid version name/);
    assert.throws(() => releaseTag('4.2.2 hotfix'), /Invalid version name/);
    assert.throws(() => releaseTag(''), /Invalid version name/);
  });
});

describe('selectPreviousRelease', () => {
  it('uses the latest published release when the new tag does not exist yet', () => {
    assert.equal(selectPreviousRelease(HISTORY, 'v4.2.3'), 'v4.2.2');
  });

  it('ignores a higher semver that was published earlier', () => {
    // v4.13.0 is above v4.12.1 by semver, but the release that actually
    // shipped last is v4.2.2.
    assert.equal(selectPreviousRelease(HISTORY, 'v4.13.0'), 'v4.2.2');
  });

  it('ignores the current tag and uses the release published immediately before it', () => {
    assert.equal(selectPreviousRelease(HISTORY, 'v4.2.2'), 'v4.2.1');
    assert.equal(selectPreviousRelease(HISTORY, 'v4.2.0'), 'v4.1.8');
  });

  it('does not treat a later release as the base when re-running an older version', () => {
    assert.equal(selectPreviousRelease(HISTORY, 'v4.1.8'), 'v4.12.1');
  });

  it('skips drafts, missing dates, and junk entries', () => {
    const releases = [
      { tagName: 'v9.9.9', publishedAt: '2026-12-01T00:00:00Z', isDraft: true },
      { tagName: '', publishedAt: '2026-12-01T00:00:00Z', isDraft: false },
      { tagName: 'v4.2.2', publishedAt: null, isDraft: false },
      { tagName: 'v4.2.1', publishedAt: 'not-a-date', isDraft: false },
      { tagName: 'v4.2.0', publishedAt: '2026-11-06T09:44:05Z', isDraft: false },
    ];
    assert.equal(selectPreviousRelease(releases, 'v4.2.3'), 'v4.2.0');
  });

  it('returns null when this is the first published release', () => {
    assert.equal(selectPreviousRelease([], 'v1.0.0'), null);
    assert.equal(selectPreviousRelease(HISTORY, 'vytu'), null);
  });

  it('breaks identical timestamps by tag name so the choice is stable', () => {
    const releases = [
      { tagName: 'v1.0.0', publishedAt: '2026-01-01T00:00:00Z', isDraft: false },
      { tagName: 'v1.0.1', publishedAt: '2026-01-01T00:00:00Z', isDraft: false },
    ];
    assert.equal(selectPreviousRelease(releases, 'v2.0.0'), 'v1.0.1');
  });
});

describe('pullRequestFromMessage', () => {
  it('reads a squash-merge subject and keeps an earlier parenthetical', () => {
    const pull = pullRequestFromMessage(
      'Revert idempotent flush token (#212): weekly total falls behind daily count (#227)\n\nbody',
    );
    assert.equal(pull.pr, '227');
    assert.equal(pull.title, 'Revert idempotent flush token (#212): weekly total falls behind daily count');
  });

  it('reads a merge commit and uses the title under the subject', () => {
    const pull = pullRequestFromMessage('Merge pull request #226 from MahmoudMabrok/rules\n\nDeploy database rules\n');
    assert.equal(pull.pr, '226');
    assert.equal(pull.title, 'Deploy database rules');
  });

  it('returns null for a direct commit', () => {
    assert.equal(pullRequestFromMessage('chore: bump version to 4.2.2 [skip ci]\n'), null);
  });

  it('still reads the pull number when stray characters follow it', () => {
    const pull = pullRequestFromMessage(
      'Add required schemaVersion field to gate main competition writes (#136)خخ\n\nbody',
    );
    assert.equal(pull.pr, '136');
    assert.equal(pull.title, 'Add required schemaVersion field to gate main competition writes خخ');
  });
});

describe('notesFromCompare', () => {
  const commits = [
    {
      sha: 'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa',
      login: 'MahmoudMabrok',
      message: 'chore: round results 2026-11-06 [skip ci]',
    },
    {
      sha: 'bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb',
      login: 'MahmoudMabrok',
      message: 'Allow server-owned rank in Mohamed Lovers player transaction writes (#225)',
    },
    {
      sha: 'cccccccccccccccccccccccccccccccccccccccc',
      login: 'MahmoudMabrok',
      message: 'Allow server-owned rank in Mohamed Lovers player transaction writes (#225)',
    },
    {
      sha: 'dddddddddddddddddddddddddddddddddddddddd',
      login: 'not a login',
      message: 'chore: update landing page stats [skip ci]',
    },
  ];

  it('links each merged pull once and links other commits', () => {
    const notes = notesFromCompare({
      commits,
      total_commits: 4,
      repo: REPO,
      previousTag: 'v4.2.1',
      tag: 'v4.2.2',
    });
    assert.match(notes, /## What's Changed/);
    assert.match(
      notes,
      /\* Allow server-owned rank in Mohamed Lovers player transaction writes by @MahmoudMabrok in https:\/\/github\.com\/MahmoudMabrok\/SaloAleh\/pull\/225/,
    );
    assert.equal(notes.match(/\/pull\/225/g).length, 1);
    assert.match(
      notes,
      /\* chore: round results 2026-11-06 \[skip ci\] by @MahmoudMabrok in https:\/\/github\.com\/MahmoudMabrok\/SaloAleh\/commit\/aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa/,
    );
    assert.match(notes, /\* chore: update landing page stats \[skip ci\] in https:\/\/github\.com\/MahmoudMabrok\/SaloAleh\/commit\/dddddddddddddddddddddddddddddddddddddddd/);
    assert.match(
      notes,
      /\*\*Full Changelog\*\*: https:\/\/github\.com\/MahmoudMabrok\/SaloAleh\/compare\/v4\.2\.1\.\.\.v4\.2\.2/,
    );
    assert.doesNotMatch(notes, /Showing/);
  });

  it('says when the compare response was truncated', () => {
    const notes = notesFromCompare({
      commits: commits.slice(0, 1),
      total_commits: 400,
      repo: REPO,
      previousTag: 'v4.2.1',
      tag: 'v4.2.2',
    });
    assert.match(notes, /_Showing 1 of 400 commits\. The full changelog lists the rest\._/);
  });

  it('stays readable when the range is empty', () => {
    const notes = notesFromCompare({
      commits: [],
      total_commits: 0,
      repo: REPO,
      previousTag: 'v4.2.1',
      tag: 'v4.2.2',
    });
    assert.match(notes, /_No commits in this range\._/);
    assert.match(notes, /compare\/v4\.2\.1\.\.\.v4\.2\.2/);
  });
});

describe('isReplaceableNotes', () => {
  const legacy = [
    '## v4.2.1',
    '- **Version name:** 4.2.1',
    '- **Version code:** 167',
    '- ✅ Android published to Google Play (production)',
    '- ℹ️ iOS: skipped',
  ].join('\n');

  const legacyIosFailure = [
    '## v4.1.2',
    '- **Version name:** 4.1.2',
    '- **Version code:** 158',
    '- ✅ Android published to Google Play (internal)',
    '- ❌ iOS deploy failed',
  ].join('\n');

  it('treats empty and the historical status-only bodies as generated', () => {
    assert.equal(isReplaceableNotes(null), true);
    assert.equal(isReplaceableNotes('  \n'), true);
    assert.equal(isReplaceableNotes(legacy), true);
    assert.equal(isReplaceableNotes(`${legacy}\n`), true);
    assert.equal(isReplaceableNotes(legacyIosFailure), true);
  });

  it('keeps a body a person has written on', () => {
    assert.equal(isReplaceableNotes(`${legacy}\n\nShips the heart index.`), false);
    assert.equal(isReplaceableNotes('Hotfix for the login crash.'), false);
    assert.equal(isReplaceableNotes('## v4.2.1\n\nSee the forum post.'), false);
  });
});

describe('planRelease', () => {
  it('creates notes from the previous release, with pull and commit links', () => {
    const plan = release({
      compare: {
        total_commits: 2,
        commits: [
          {
            sha: 'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa',
            login: 'MahmoudMabrok',
            message: 'Disable automatic runs of the Flush Instrumented Test workflow. (#238)',
          },
          {
            sha: 'bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb',
            login: 'github-actions[bot]',
            message: 'chore: bump version to 4.2.2 [skip ci]',
          },
        ],
      },
    });
    assert.equal(plan.action, 'create');
    assert.equal(plan.tag, 'v4.2.3');
    assert.equal(plan.previous, 'v4.2.2');
    assert.match(plan.notes, /<!-- saloaleh-release-notes:auto -->/);
    assert.match(plan.notes, /## v4\.2\.3/);
    assert.match(plan.notes, /- \*\*Version code:\*\* 172/);
    assert.match(plan.notes, /- ✅ Android published to Google Play \(production\)/);
    assert.match(plan.notes, /- ℹ️ iOS: skipped/);
    assert.match(plan.notes, /\/pull\/238/);
    assert.match(plan.notes, /by @github-actions\[bot\]/);
    assert.match(plan.notes, /compare\/v4\.2\.2\.\.\.v4\.2\.3/);
    assert.equal(isReplaceableNotes(plan.notes), true);
  });

  it('refreshes an existing status-only release against the prior tag', () => {
    const plan = release({
      version: '4.2.2',
      versionCode: '171',
      track: 'internal',
      existing: {
        exists: true,
        body: [
          '## v4.2.2',
          '- **Version name:** 4.2.2',
          '- **Version code:** 171',
          '- ✅ Android published to Google Play (internal)',
          '- ℹ️ iOS: skipped',
        ].join('\n'),
      },
      compare: { commits: [], total_commits: 0 },
    });
    assert.equal(plan.action, 'update');
    assert.equal(plan.previous, 'v4.2.1');
    assert.match(plan.notes, /compare\/v4\.2\.1\.\.\.v4\.2\.2/);
    assert.equal(isReplaceableNotes(plan.notes), true);
  });

  it('does not replace notes someone edited', () => {
    const plan = release({
      version: '4.2.2',
      existing: { exists: true, body: '## v4.2.2\n\nWritten by hand.\n' },
      compare: null,
    });
    assert.equal(plan.action, 'keep');
    assert.equal(plan.notes, null);
    assert.equal(plan.previous, 'v4.2.1');
  });

  it('keeps the compare link when the commit list could not be loaded', () => {
    const plan = release({ compare: null, compareError: true });
    assert.equal(plan.action, 'create');
    assert.match(plan.notes, /_The commit list could not be loaded\._/);
    assert.match(plan.notes, /compare\/v4\.2\.2\.\.\.v4\.2\.3/);
    assert.doesNotMatch(plan.notes, /_No commits in this range\._/);
    assert.equal(isReplaceableNotes(plan.notes), true);
  });

  it('describes a first release without a compare link', () => {
    const plan = release({
      version: '1.0.0',
      releases: [],
      compare: null,
    });
    assert.equal(plan.action, 'create');
    assert.equal(plan.previous, null);
    assert.match(plan.notes, /_No previous release tag to compare against\._/);
    assert.doesNotMatch(plan.notes, /Full Changelog/);
    assert.equal(isReplaceableNotes(plan.notes), true);
  });

  it('keeps an Android failure and a successful iOS publish in the status block', () => {
    const plan = release({
      androidResult: 'failure',
      iosResult: 'success',
      versionCode: '',
      releases: [],
    });
    assert.match(plan.notes, /- ❌ Android deploy failed/);
    assert.match(plan.notes, /- ✅ iOS published to App Store Connect/);
    assert.doesNotMatch(plan.notes, /Version code/);
    assert.equal(isReplaceableNotes(plan.notes), true);
  });
});

describe('parseReleaseList', () => {
  it('reads one release per line and skips blanks', () => {
    const parsed = parseReleaseList(
      '\n{"tagName":"v4.2.2","publishedAt":"2026-11-08T19:24:24Z","isDraft":false}\n'
      + '{"tagName":"v4.2.1","publishedAt":"2026-11-06T16:07:45Z","isDraft":true}\n',
    );
    assert.deepEqual(parsed, [
      { tagName: 'v4.2.2', publishedAt: '2026-11-08T19:24:24Z', isDraft: false },
      { tagName: 'v4.2.1', publishedAt: '2026-11-06T16:07:45Z', isDraft: true },
    ]);
  });
});
