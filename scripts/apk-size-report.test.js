const { describe, it } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const {
  COMMENT_MARKER,
  buildReport,
  deltaPhrase,
  formatBytes,
  main,
} = require('./apk-size-report');

describe('formatBytes', () => {
  it('uses binary units', () => {
    assert.equal(formatBytes(0), '0 B');
    assert.equal(formatBytes(512), '512 B');
    assert.equal(formatBytes(1024), '1.0 KiB');
    assert.equal(formatBytes(1536), '1.5 KiB');
    assert.equal(formatBytes(1048576), '1.0 MiB');
    assert.equal(formatBytes(100204092), '95.6 MiB');
  });
});

describe('deltaPhrase', () => {
  it('says grew, shrank, or unchanged', () => {
    assert.equal(deltaPhrase(1000, 1000), 'unchanged');
    assert.equal(deltaPhrase(1000, 2024), 'grew by 1.0 KiB (+102.4%)');
    assert.equal(deltaPhrase(4096, 2048), 'shrank by 2.0 KiB (-50.0%)');
  });

  it('does not treat a missing APK as zero bytes', () => {
    assert.equal(deltaPhrase(null, 1024), 'only on head, 1.0 KiB');
    assert.equal(deltaPhrase(2048, null), 'only on base, 2.0 KiB');
  });
});

describe('buildReport', () => {
  const rows = [
    { name: 'app-debug.apk', beforeBytes: 1000, afterBytes: 2024 },
    { name: 'mushaf-debug.apk', beforeBytes: 5000, afterBytes: 5000 },
  ];

  it('writes one comparison comment that names the debug APK', () => {
    const report = buildReport({
      mode: 'pr',
      baseLabel: '`main` @ `abc`',
      headLabel: '`feature` @ `def`',
      rows,
    });
    assert.match(report.comment, new RegExp(`^${COMMENT_MARKER}`));
    assert.match(report.comment, /assembleDebug/);
    assert.match(report.comment, /grew by 1.0 KiB \(\+102\.4%\)/);
    assert.match(report.comment, /unchanged/);
    assert.match(report.comment, /not a release AAB/);
    assert.match(report.summary, /grew by 1.0 KiB/);
  });

  it('records a single artifact and no comment when there is no pull request', () => {
    const report = buildReport({
      mode: 'dispatch',
      headLabel: '`main` @ `abc`',
      rows: [{ name: 'app-debug.apk', beforeBytes: null, afterBytes: 4096 }],
    });
    assert.equal(report.comment, null);
    assert.match(report.summary, /No pull request/);
    assert.match(report.summary, /4.0 KiB/);
    assert.doesNotMatch(report.summary, /grew|shrank|unchanged/);
  });
});

describe('main', () => {
  it('measures the files it is given and appends the job summary', () => {
    const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'apk-size-'));
    const base = path.join(dir, 'base.apk');
    const head = path.join(dir, 'head.apk');
    const summary = path.join(dir, 'summary.md');
    const comment = path.join(dir, 'comment.md');
    fs.writeFileSync(base, Buffer.alloc(1000));
    fs.writeFileSync(head, Buffer.alloc(2024));
    main([
      '--mode', 'pr',
      '--base', base,
      '--head', head,
      '--base-label', '`main` @ `abc`',
      '--head-label', '`feature` @ `def`',
      '--summary', summary,
      '--comment', comment,
    ], {});
    assert.match(fs.readFileSync(summary, 'utf8'), /grew by 1.0 KiB/);
    assert.match(fs.readFileSync(comment, 'utf8'), new RegExp(COMMENT_MARKER));
  });
});
