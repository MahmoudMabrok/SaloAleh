const { describe, it } = require('node:test');
const assert = require('node:assert/strict');
const { timeBoxMirror, writeChangedRanks } = require('./firestore-utils');

// timeBoxMirror guards fire-and-forget Firestore dual-writes: a quota-stalled
// commit retries RESOURCE_EXHAUSTED for ~10 minutes, which would block the
// cron's main() from reaching process.exit(0). The box must let the caller
// proceed, and must never reject (mirrors are fire-and-forget).

describe('timeBoxMirror', () => {
  it('resolves promptly when the mirror op hangs (the bug this fixes)', async () => {
    const started = process.hrtime.bigint();
    const neverSettles = new Promise(() => {}); // models a stalled batch.commit()
    await timeBoxMirror(neverSettles, 'hanging-mirror', 50);
    const elapsedMs = Number(process.hrtime.bigint() - started) / 1e6;
    // Without the box this would block for ~600000ms.
    assert.ok(elapsedMs < 1000, `expected fast return, took ${elapsedMs}ms`);
  });

  it('passes through a fast mirror without waiting for the timeout', async () => {
    const started = process.hrtime.bigint();
    await timeBoxMirror(Promise.resolve('ok'), 'fast-mirror', 60000);
    const elapsedMs = Number(process.hrtime.bigint() - started) / 1e6;
    assert.ok(elapsedMs < 1000, `expected immediate return, took ${elapsedMs}ms`);
  });

  it('swallows a rejecting mirror instead of propagating (no cron crash)', async () => {
    await assert.doesNotReject(() =>
      timeBoxMirror(Promise.reject(new Error('quota')), 'failing-mirror', 60000),
    );
  });
});

// Minimal Firestore stub for writeChangedRanks: records committed rank writes and
// the snapshot doc, and can be seeded with a prior snapshot / made to fail a commit.
function makeFakeFirestore({ snapshot = undefined, failCommit = false } = {}) {
  const store = { snapshot, writes: [], commits: 0 };
  const firestore = {
    batch() {
      const ops = [];
      return {
        set(ref, data) {
          ops.push({ uid: ref.__uid, rank: data.rank });
        },
        async commit() {
          if (failCommit) throw new Error('RESOURCE_EXHAUSTED');
          store.commits++;
          for (const op of ops) store.writes.push(op);
        },
      };
    },
  };
  const userCollectionRef = {
    firestore,
    doc: (uid) => ({ __uid: uid }),
  };
  const snapshotDocRef = {
    async get() {
      return { exists: store.snapshot !== undefined, data: () => store.snapshot };
    },
    async set(data) {
      store.snapshot = data;
    },
  };
  return { store, userCollectionRef, snapshotDocRef };
}

const snapOf = (ranks) => ({ ranksJson: JSON.stringify(ranks), count: Object.keys(ranks).length, updatedAt: 0 });

describe('writeChangedRanks', () => {
  it('cold start (no snapshot) writes every rank once and records the snapshot', async () => {
    const { store, userCollectionRef, snapshotDocRef } = makeFakeFirestore();
    await writeChangedRanks(userCollectionRef, snapshotDocRef, { a: 1, b: 2, c: 3 }, 'test');
    assert.equal(store.writes.length, 3);
    assert.equal(store.commits, 1);
    assert.deepEqual(JSON.parse(store.snapshot.ranksJson), { a: 1, b: 2, c: 3 });
  });

  it('an unchanged run writes zero rank docs (only refreshes the snapshot)', async () => {
    const { store, userCollectionRef, snapshotDocRef } = makeFakeFirestore({ snapshot: snapOf({ a: 1, b: 2, c: 3 }) });
    await writeChangedRanks(userCollectionRef, snapshotDocRef, { a: 1, b: 2, c: 3 }, 'test');
    assert.equal(store.writes.length, 0);
    assert.equal(store.commits, 0);
  });

  it('writes exactly the ranks that changed', async () => {
    const { store, userCollectionRef, snapshotDocRef } = makeFakeFirestore({ snapshot: snapOf({ a: 1, b: 2, c: 3 }) });
    await writeChangedRanks(userCollectionRef, snapshotDocRef, { a: 1, b: 5, c: 3 }, 'test');
    assert.deepEqual(store.writes, [{ uid: 'b', rank: 5 }]);
  });

  it('chunks writes above the 500-op batch limit into multiple commits', async () => {
    const { store, userCollectionRef, snapshotDocRef } = makeFakeFirestore();
    const newRanks = {};
    for (let i = 0; i < 1000; i++) newRanks[`u${i}`] = i + 1;
    await writeChangedRanks(userCollectionRef, snapshotDocRef, newRanks, 'test');
    assert.equal(store.writes.length, 1000);
    assert.equal(store.commits, 3); // 490 + 490 + 20
  });

  it('leaves the snapshot unchanged when a commit fails', async () => {
    const prior = snapOf({ a: 1 });
    const { store, userCollectionRef, snapshotDocRef } = makeFakeFirestore({ snapshot: prior, failCommit: true });
    await assert.rejects(() => writeChangedRanks(userCollectionRef, snapshotDocRef, { a: 2 }, 'test'));
    assert.equal(store.snapshot, prior); // not overwritten → next run retries
  });
});
