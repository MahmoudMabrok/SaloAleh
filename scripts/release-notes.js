// Builds the GitHub Release body for the deploy workflow.
//
// The previous release is the latest published GitHub Release other than the
// tag being written, or — when that tag already exists — the latest release
// published strictly earlier. Publish time is the order releases actually
// shipped. A semver sort picks the wrong base in this repo: v4.12.1 shipped
// before v4.2.0, and stray tags (vtest3, vytu, …) are not releases.
//
// Notes that were edited by hand are left alone. Empty notes, the old
// status-only body, and a body this script still recognizes as its own are
// safe to replace.

const { execFileSync } = require('node:child_process');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');

const AUTO_MARKER_LINE = '<!-- saloaleh-release-notes:auto -->';

const STATUS_LINE = [
  /^## v\S+$/,
  /^- \*\*Version name:\*\* \S+$/,
  /^- \*\*Version code:\*\* \d+$/,
  /^- ✅ Android published to Google Play \([^()\n]+\)$/,
  /^- ❌ Android deploy failed$/,
  /^- ✅ iOS published to App Store Connect$/,
  /^- ❌ iOS deploy failed$/,
  /^- ℹ️ iOS: \S+$/,
];

const CHANGELOG_LINE = [
  /^<!-- saloaleh-release-notes:auto -->$/,
  /^## What's Changed$/,
  /^\* .+ in https:\/\/github\.com\/[A-Za-z0-9_.-]+\/[A-Za-z0-9_.-]+\/(pull\/\d+|commit\/[0-9a-f]{7,40})$/,
  /^\*\*Full Changelog\*\*: https:\/\/github\.com\/\S+$/,
  /^_No previous release tag to compare against\._$/,
  /^_No commits in this range\._$/,
  /^_The commit list could not be loaded\._$/,
  /^_Showing \d+ of \d+ commits\. The full changelog lists the rest\._$/,
];

function releaseTag(version) {
  if (typeof version !== 'string' || !/^[0-9A-Za-z][0-9A-Za-z._+-]*$/.test(version)) {
    throw new Error(`Invalid version name '${version}'`);
  }
  return `v${version}`;
}

function assertRepo(repo) {
  if (typeof repo !== 'string' || !/^[A-Za-z0-9_.-]+\/[A-Za-z0-9_.-]+$/.test(repo)) {
    throw new Error(`Unexpected repository '${repo}'`);
  }
}

function selectPreviousRelease(releases, currentTag) {
  const published = (releases || [])
    .filter((release) => {
      if (!release || release.isDraft) return false;
      if (typeof release.tagName !== 'string' || release.tagName.length === 0) return false;
      return Number.isFinite(Date.parse(release.publishedAt));
    })
    .sort((a, b) => {
      const byDate = Date.parse(b.publishedAt) - Date.parse(a.publishedAt);
      if (byDate !== 0) return byDate;
      if (a.tagName === b.tagName) return 0;
      return a.tagName < b.tagName ? 1 : -1;
    });

  const current = published.find((release) => release.tagName === currentTag);
  const earlier = published.filter((release) => {
    if (release.tagName === currentTag) return false;
    if (!current) return true;
    return Date.parse(release.publishedAt) < Date.parse(current.publishedAt);
  });
  return earlier.length > 0 ? earlier[0].tagName : null;
}

function isAutoLine(line) {
  return STATUS_LINE.some((pattern) => pattern.test(line))
    || CHANGELOG_LINE.some((pattern) => pattern.test(line));
}

function isReplaceableNotes(body) {
  if (body == null) return true;
  const lines = String(body)
    .split(/\r?\n/)
    .map((line) => line.trim())
    .filter((line) => line.length > 0);
  if (lines.length === 0) return true;
  return lines.every(isAutoLine);
}

function buildStatusSection({ version, versionCode, androidResult, iosResult, track }) {
  releaseTag(version);
  const lines = [
    AUTO_MARKER_LINE,
    `## v${version}`,
    `- **Version name:** ${version}`,
  ];
  const code = versionCode == null ? '' : String(versionCode).trim();
  if (code.length > 0) {
    if (!/^[0-9]+$/.test(code)) throw new Error(`Invalid version code '${versionCode}'`);
    lines.push(`- **Version code:** ${code}`);
  }
  if (androidResult === 'success') {
    const safeTrack = String(track || '').trim();
    if (!/^[A-Za-z0-9_.-]+$/.test(safeTrack)) throw new Error(`Invalid Play track '${track}'`);
    lines.push(`- ✅ Android published to Google Play (${safeTrack})`);
  } else {
    lines.push('- ❌ Android deploy failed');
  }
  if (iosResult === 'success') {
    lines.push('- ✅ iOS published to App Store Connect');
  } else {
    const status = String(iosResult || '').trim() || 'skipped';
    if (!/^[A-Za-z0-9_.-]+$/.test(status)) throw new Error(`Invalid iOS result '${iosResult}'`);
    lines.push(`- ℹ️ iOS: ${status}`);
  }
  return lines.join('\n');
}

function subjectOf(message) {
  const first = String(message || '').split(/\r?\n/)[0] || '';
  return first.trim();
}

function pullRequestFromMessage(message) {
  const lines = String(message || '').split(/\r?\n/);
  const subject = (lines[0] || '').trim();
  const merge = subject.match(/^Merge pull request #(\d+)\b/);
  if (merge) {
    const rest = lines.slice(1).map((line) => line.trim()).filter((line) => line.length > 0);
    const title = (rest[0] || `Pull request #${merge[1]}`).replace(/\s+/g, ' ').trim();
    return { pr: merge[1], title };
  }
  // Squash merges end in (#123). Keep the last marker when an earlier
  // parenthetical is part of the title, and still recognize it when a few
  // stray characters were left after the marker.
  const matches = [...subject.matchAll(/\(#(\d+)\)/g)];
  if (matches.length > 0) {
    const last = matches[matches.length - 1];
    const title = `${subject.slice(0, last.index)}${subject.slice(last.index + last[0].length)}`
      .replace(/\s+/g, ' ')
      .trim();
    return { pr: last[1], title: title || `Pull request #${last[1]}` };
  }
  return null;
}

function validLogin(login) {
  if (typeof login !== 'string') return '';
  const trimmed = login.trim();
  if (!/^[A-Za-z0-9][A-Za-z0-9-]*(\[bot\])?$/.test(trimmed)) return '';
  return trimmed;
}

function compareUrl(repo, previousTag, tag) {
  return `https://github.com/${repo}/compare/${encodeURIComponent(previousTag)}...${encodeURIComponent(tag)}`;
}

function changelogBullet({ title, login, url }) {
  const safeTitle = String(title || '').replace(/\s+/g, ' ').trim();
  const who = login ? ` by @${login}` : '';
  return `* ${safeTitle}${who} in ${url}`;
}

function notesFromCompare({ commits, totalCommits, total_commits, repo, previousTag, tag }) {
  assertRepo(repo);
  const rows = Array.isArray(commits) ? commits : [];
  const seenPr = new Set();
  const seenSha = new Set();
  const bullets = [];

  for (const commit of rows) {
    if (!commit) continue;
    const sha = String(commit.sha || '').toLowerCase();
    if (!/^[0-9a-f]{7,40}$/.test(sha) || seenSha.has(sha)) continue;
    seenSha.add(sha);
    const login = validLogin(commit.login);
    const pull = pullRequestFromMessage(commit.message);
    if (pull) {
      if (seenPr.has(pull.pr)) continue;
      seenPr.add(pull.pr);
      bullets.push(changelogBullet({
        title: pull.title,
        login,
        url: `https://github.com/${repo}/pull/${pull.pr}`,
      }));
      continue;
    }
    const title = subjectOf(commit.message).replace(/\s+/g, ' ').trim() || `Commit ${sha.slice(0, 7)}`;
    bullets.push(changelogBullet({
      title,
      login,
      url: `https://github.com/${repo}/commit/${sha}`,
    }));
  }

  const lines = ["## What's Changed"];
  if (bullets.length === 0) {
    lines.push('_No commits in this range._');
  } else {
    lines.push(...bullets);
  }

  const total = Number.isFinite(totalCommits) ? totalCommits : Number(total_commits);
  if (Number.isFinite(total) && rows.length > 0 && total > rows.length) {
    lines.push(`_Showing ${rows.length} of ${total} commits. The full changelog lists the rest._`);
  }
  lines.push('');
  lines.push(`**Full Changelog**: ${compareUrl(repo, previousTag, tag)}`);
  return lines.join('\n');
}

function noPreviousReleaseChangelog() {
  return "## What's Changed\n_No previous release tag to compare against._";
}

function unavailableChangelog(repo, previousTag, tag) {
  return [
    "## What's Changed",
    '_The commit list could not be loaded._',
    '',
    `**Full Changelog**: ${compareUrl(repo, previousTag, tag)}`,
  ].join('\n');
}

function composeReleaseNotes({ status, changelog }) {
  return `${status.trim()}\n\n${changelog.trim()}\n`;
}

function planRelease({
  version,
  versionCode,
  androidResult,
  iosResult,
  track,
  repo,
  releases,
  existing,
  compare,
  compareError,
}) {
  assertRepo(repo);
  const tag = releaseTag(version);
  const previous = selectPreviousRelease(releases, tag);
  const exists = Boolean(existing && existing.exists);
  if (exists && !isReplaceableNotes(existing.body)) {
    return { action: 'keep', tag, previous, notes: null };
  }
  let changelog;
  if (!previous) {
    changelog = noPreviousReleaseChangelog();
  } else if (compareError) {
    changelog = unavailableChangelog(repo, previous, tag);
  } else {
    changelog = notesFromCompare({
      commits: compare && compare.commits,
      totalCommits: compare && (compare.totalCommits ?? compare.total_commits),
      repo,
      previousTag: previous,
      tag,
    });
  }
  const notes = composeReleaseNotes({
    status: buildStatusSection({ version, versionCode, androidResult, iosResult, track }),
    changelog,
  });
  if (!isReplaceableNotes(notes)) {
    throw new Error('Generated release notes do not match the auto-notes allowlist.');
  }
  return { action: exists ? 'update' : 'create', tag, previous, notes };
}

function parseReleaseList(ndjson) {
  const releases = [];
  for (const line of String(ndjson || '').split(/\r?\n/)) {
    const trimmed = line.trim();
    if (!trimmed) continue;
    const row = JSON.parse(trimmed);
    releases.push({
      tagName: typeof row.tagName === 'string' ? row.tagName : '',
      publishedAt: row.publishedAt || null,
      isDraft: Boolean(row.isDraft),
    });
  }
  return releases;
}

function errorText(err) {
  const stderr = err && err.stderr ? err.stderr.toString() : '';
  const stdout = err && err.stdout ? err.stdout.toString() : '';
  return `${stderr}\n${stdout}`.trim();
}

function runGh(args) {
  try {
    return execFileSync('gh', args, {
      encoding: 'utf8',
      maxBuffer: 32 * 1024 * 1024,
      env: process.env,
    });
  } catch (err) {
    const detail = errorText(err) || (err && err.message) || 'unknown error';
    const wrapped = new Error(`gh ${args[0]} failed: ${detail}`);
    wrapped.stderr = detail;
    throw wrapped;
  }
}

function fetchReleaseList(repo) {
  const raw = runGh([
    'api',
    '--paginate',
    `repos/${repo}/releases?per_page=100`,
    '--jq',
    '.[] | {tagName:.tag_name, publishedAt:.published_at, isDraft:.draft}',
  ]);
  return parseReleaseList(raw);
}

function fetchCompare(repo, base, head) {
  const raw = runGh([
    'api',
    `repos/${repo}/compare/${encodeURIComponent(base)}...${encodeURIComponent(head)}`,
    '--jq',
    '{total_commits:.total_commits, commits:[.commits[]? | {sha:.sha, login:(.author.login // ""), message:(.commit.message // "")}]}',
  ]);
  return JSON.parse(raw);
}

function readExistingRelease(tag) {
  try {
    const raw = runGh(['release', 'view', tag, '--json', 'body', '--jq', '.body']);
    const body = raw.replace(/\n$/, '');
    return { exists: true, body: body === 'null' ? '' : body };
  } catch (err) {
    if (/release not found/i.test(`${err.stderr || ''}\n${err.message || ''}`)) {
      return { exists: false, body: '' };
    }
    throw err;
  }
}

function runGhVisible(args) {
  try {
    execFileSync('gh', args, { stdio: 'inherit', env: process.env });
  } catch (err) {
    const detail = errorText(err) || (err && err.message) || 'unknown error';
    throw new Error(`gh ${args[0]} failed: ${detail}`);
  }
}

function writeRelease({ tag, notes, exists, target }) {
  const notesFile = path.join(process.env.RUNNER_TEMP || os.tmpdir(), `release-notes-${tag}.md`);
  fs.writeFileSync(notesFile, notes.endsWith('\n') ? notes : `${notes}\n`);
  if (exists) {
    runGhVisible(['release', 'edit', tag, '--notes-file', notesFile]);
    return;
  }
  const args = ['release', 'create', tag, '--title', tag, '--notes-file', notesFile];
  if (target) args.push('--target', target);
  runGhVisible(args);
}

function main() {
  const dryRun = process.argv.includes('--dry-run');
  const version = process.env.RELEASE_VERSION || '';
  if (!version) {
    throw new Error('RELEASE_VERSION is required (the version name, e.g. 4.2.3).');
  }
  const repo = process.env.RELEASE_REPO || process.env.GITHUB_REPOSITORY || '';
  assertRepo(repo);
  const tag = releaseTag(version);
  const releases = fetchReleaseList(repo);
  const existing = readExistingRelease(tag);
  const previous = selectPreviousRelease(releases, tag);
  console.log(previous
    ? `Previous release tag: ${previous}`
    : 'No previous release tag (first release).');

  let compare = null;
  let compareError = false;
  if (previous && !(existing.exists && !isReplaceableNotes(existing.body))) {
    const head = existing.exists
      ? tag
      : (process.env.RELEASE_TARGET || process.env.GITHUB_REF_NAME || 'HEAD');
    console.log(`Changelog range: ${previous}...${head}`);
    try {
      compare = fetchCompare(repo, previous, head);
    } catch (err) {
      // Still publish the release. A missed commit list can be regenerated;
      // a failed release step cannot be retried without another Play upload.
      compareError = true;
      console.error(err && err.message ? err.message : err);
    }
  }

  const plan = planRelease({
    version,
    versionCode: process.env.RELEASE_VERSION_CODE || '',
    androidResult: process.env.RELEASE_ANDROID_RESULT || '',
    iosResult: process.env.RELEASE_IOS_RESULT || '',
    track: process.env.RELEASE_TRACK || '',
    repo,
    releases,
    existing,
    compare,
    compareError,
  });

  if (plan.action === 'keep') {
    console.log(`Release ${tag} already has notes that were not generated by this workflow; leaving them unchanged.`);
    return;
  }
  if (dryRun) {
    console.log(plan.action === 'update' ? `Would update ${tag}` : `Would create ${tag}`);
    console.log('-----');
    process.stdout.write(plan.notes);
    return;
  }
  writeRelease({
    tag,
    notes: plan.notes,
    exists: existing.exists,
    target: process.env.RELEASE_TARGET || process.env.GITHUB_REF_NAME || '',
  });
  console.log(plan.action === 'update' ? `Updated ${tag}` : `Created ${tag}`);
}

module.exports = {
  AUTO_MARKER_LINE,
  releaseTag,
  selectPreviousRelease,
  isReplaceableNotes,
  buildStatusSection,
  pullRequestFromMessage,
  notesFromCompare,
  composeReleaseNotes,
  planRelease,
  parseReleaseList,
};

if (require.main === module) {
  try {
    main();
  } catch (err) {
    console.error(err && err.message ? err.message : err);
    process.exit(1);
  }
}
