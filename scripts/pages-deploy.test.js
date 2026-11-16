// The app reads ahl-adhikr/latest.json from GitHub Pages, so the snapshot only
// reaches users when "Deploy GitHub Pages" runs after the snapshot workflow.
// Pushes made with the default GITHUB_TOKEN never fire `push` triggers (GitHub
// suppresses them), and the snapshot bot commits without [skip ci] — so the
// `workflow_run` fallback below is the only thing that publishes a fresh
// snapshot. Without it the app serves a stale file: salawat rows fall back to
// the public id instead of the nickname and `countryCode` is missing, so the
// flag cannot render.
const fs = require('fs');
const path = require('path');
const assert = require('node:assert');
const test = require('node:test');

const WORKFLOW_PATH = path.join(__dirname, '..', '.github', 'workflows', 'deploy-pages.yml');
const workflow = fs.readFileSync(WORKFLOW_PATH, 'utf8');

test('deploy-pages republishes after the Ahl Adhikr Snapshot workflow', () => {
  const idx = workflow.indexOf('workflow_run:');
  assert.ok(idx >= 0, 'deploy-pages.yml has no workflow_run trigger');
  // Everything up to the next top-level trigger key — the `workflows:` list of
  // this block is the first thing after the colon.
  const block = workflow.slice(idx, idx + 400);
  assert.match(
    block,
    /workflows:\s*\[[^\]]*"Ahl Adhikr Snapshot"/,
    'workflow_run must list "Ahl Adhikr Snapshot" so the published snapshot stays fresh',
  );
});

test('deploy-pages publishes the snapshot file', () => {
  assert.match(workflow, /ahl-adhikr/);
});
