// Formats the debug-APK size comparison posted by .github/workflows/build.yml.
// Sizes are file lengths in bytes. The workflow measures app/build outputs of
// `./gradlew :app:assembleDebug` on the base branch and the PR head.

const fs = require('node:fs');

const COMMENT_MARKER = '<!-- android-debug-apk-size -->';

function formatBytes(bytes) {
  if (!Number.isInteger(bytes) || bytes < 0) {
    throw new Error(`Size must be a non-negative integer byte count, got ${bytes}.`);
  }
  if (bytes < 1024) return `${bytes} B`;
  const units = ['KiB', 'MiB', 'GiB'];
  let value = bytes / 1024;
  let unit = 0;
  while (value >= 1024 && unit < units.length - 1) {
    value /= 1024;
    unit += 1;
  }
  return `${value.toFixed(1)} ${units[unit]}`;
}

function formatExact(bytes) {
  return `${bytes.toLocaleString('en-US')} bytes`;
}

function formatSize(bytes) {
  if (bytes == null) return 'not produced';
  return `${formatBytes(bytes)} (${formatExact(bytes)})`;
}

function deltaPhrase(before, after) {
  if (before == null && after == null) return 'absent';
  if (before == null) return `only on head, ${formatBytes(after)}`;
  if (after == null) return `only on base, ${formatBytes(before)}`;
  const delta = after - before;
  if (delta === 0) return 'unchanged';
  const percent = before === 0 ? null : (delta / before) * 100;
  const percentText = percent == null ? '' : ` (${delta > 0 ? '+' : ''}${percent.toFixed(1)}%)`;
  const direction = delta > 0 ? 'grew' : 'shrank';
  return `${direction} by ${formatBytes(Math.abs(delta))}${percentText}`;
}

function fileBytes(filePath) {
  if (!filePath) return null;
  if (!fs.existsSync(filePath)) return null;
  return fs.statSync(filePath).size;
}

function buildReport({ mode, baseLabel, headLabel, rows }) {
  const compared = mode === 'pr';
  const lines = [];
  if (compared) {
    lines.push('## Android debug APK size');
    lines.push('');
    lines.push(
      'Compared the **debug APK** from `./gradlew :app:assembleDebug` on the pull request base and head. ' +
        'That is the artifact this workflow already builds. It is not a release AAB and it is not the Play Console install size. ' +
        'Debug APKs are not minified and contain every ABI. The on-demand `mushaf_assets` pack is not inside these APKs.',
    );
    lines.push('');
    lines.push(`| Artifact | Base (${baseLabel}) | Head (${headLabel}) | Change |`);
    lines.push('| --- | --- | --- | --- |');
    for (const row of rows) {
      if (row.beforeBytes == null && row.afterBytes == null) continue;
      lines.push(
        `| \`${row.name}\` | ${formatSize(row.beforeBytes)} | ${formatSize(row.afterBytes)} | ${deltaPhrase(row.beforeBytes, row.afterBytes)} |`,
      );
    }
    lines.push('');
    lines.push(`Base: ${baseLabel}`);
    lines.push(`Head: ${headLabel}`);
  } else {
    lines.push('## Android debug APK size');
    lines.push('');
    lines.push(
      'No pull request on this run, so there is no base-versus-head comparison and no pull request comment. ' +
        'The size below is the debug APK from `./gradlew :app:assembleDebug` at the dispatched ref. ' +
        'It is not a release AAB and it is not the Play Console install size.',
    );
    lines.push('');
    lines.push(`| Artifact | ${headLabel} |`);
    lines.push('| --- | --- |');
    for (const row of rows) {
      if (row.afterBytes == null) continue;
      lines.push(`| \`${row.name}\` | ${formatSize(row.afterBytes)} |`);
    }
  }
  lines.push('');
  const summary = lines.join('\n');
  if (!compared) return { summary, comment: null };
  return { summary, comment: `${COMMENT_MARKER}\n${summary}` };
}

function parseArgs(argv) {
  const args = {};
  for (let i = 0; i < argv.length; i += 1) {
    const token = argv[i];
    if (!token.startsWith('--')) continue;
    const key = token.slice(2);
    const next = argv[i + 1];
    if (next == null || next.startsWith('--')) {
      args[key] = true;
    } else {
      args[key] = next;
      i += 1;
    }
  }
  return args;
}

function rowsFromArgs(args) {
  const rows = [
    { name: 'app-debug.apk', beforeBytes: fileBytes(args.base), afterBytes: fileBytes(args.head) },
  ];
  if (args['base-feature'] || args['head-feature']) {
    rows.push({
      name: 'mushaf-debug.apk',
      beforeBytes: fileBytes(args['base-feature']),
      afterBytes: fileBytes(args['head-feature']),
    });
  }
  return rows;
}

function main(argv = process.argv.slice(2), env = process.env) {
  const args = parseArgs(argv);
  const mode = args.mode === 'pr' ? 'pr' : 'dispatch';
  const rows = rowsFromArgs(args);
  if (rows[0].afterBytes == null) {
    throw new Error(`Head debug APK not found at ${args.head || '(missing --head)'}.`);
  }
  if (mode === 'pr' && rows[0].beforeBytes == null) {
    throw new Error(`Base debug APK not found at ${args.base || '(missing --base)'}.`);
  }
  const report = buildReport({
    mode,
    baseLabel: args['base-label'] || 'base',
    headLabel: args['head-label'] || 'head',
    rows,
  });
  const summaryPath = args.summary || env.GITHUB_STEP_SUMMARY;
  if (summaryPath) fs.appendFileSync(summaryPath, `${report.summary}\n`);
  if (args.comment) {
    if (!report.comment) throw new Error('Refusing to write a pull request comment when there is no comparison.');
    fs.writeFileSync(args.comment, report.comment);
  }
  return report;
}

module.exports = {
  COMMENT_MARKER,
  buildReport,
  deltaPhrase,
  formatBytes,
  formatSize,
  main,
};

if (require.main === module) {
  try {
    main();
  } catch (error) {
    console.error(error.message);
    process.exit(1);
  }
}
