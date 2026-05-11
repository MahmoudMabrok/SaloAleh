# Score Reliability Fixes

## Architecture Overview

```mermaid
flowchart TD
    User([User Tap]) --> VM[ViewModel]
    VM -->|registerLocalTap| Store[SessionStore<br/>pending count]
    VM -->|flushPendingSession| Repo[Repository]
    Repo -->|incrementSession| FB[(Firebase RTDB)]
    FB -->|observeSelfPlayer| VM
    VM -->|applyLeaderboard| UI[UI Score Display]
    Store -.->|sessionClicks| VM

    style Store fill:#ffd,stroke:#aa0
    style FB fill:#ddf,stroke:#00a
    style VM fill:#dfd,stroke:#0a0
```

The two race conditions occur at the boundaries between these components — one between Store and Repository (score lost), and one between Firebase observer and ViewModel state (score doubled).

## Problem

Two race conditions caused unreliable score display:
1. **Score lost** — taps added during a network flush were wiped out
2. **Score doubled** — UI showed remote + local count during the flush-to-ack window

## Root Cause 1: Score Lost (Flush Wipes Concurrent Taps)

`flushPendingSession()` called `clearPendingRound(roundKey)` on success, which deleted ALL pending taps — including ones the user added while the network call was in flight.

**Timeline:**
```
User has 5 pending taps
flushPendingSession() snapshots count=5, starts network call
User taps 3 more → pending=8
Network returns success → clearPendingRound() → pending=0  ← 3 taps LOST
```

**Sequence diagram (before fix):**
```mermaid
sequenceDiagram
    participant User
    participant Store as SessionStore
    participant Repo as Repository
    participant FB as Firebase

    Note over Store: pending = 5
    Repo->>Store: getAllPendingRounds() → {R1: 5}
    Repo->>FB: incrementSession(R1, delta=5)
    User->>Store: incrementPendingClick(R1, 3)
    Note over Store: pending = 8
    FB-->>Repo: Success
    Repo->>Store: clearPendingRound(R1)
    Note over Store: pending = 0 ✗ (3 taps LOST)
```

**Sequence diagram (after fix):**
```mermaid
sequenceDiagram
    participant User
    participant Store as SessionStore
    participant Repo as Repository
    participant FB as Firebase

    Note over Store: pending = 5
    Repo->>Store: getAllPendingRounds() → {R1: 5}
    Repo->>FB: incrementSession(R1, delta=5)
    User->>Store: incrementPendingClick(R1, 3)
    Note over Store: pending = 8
    FB-->>Repo: Success
    Repo->>Store: decrementPendingClick(R1, 5)
    Note over Store: pending = 3 ✓ (new taps preserved)
```

**Fix:** Replace `clearPendingRound()` with `decrementPendingClick(roundKey, count)` which only subtracts the snapshotted amount:
```kotlin
// Before (broken)
result.onSuccess { sessionStore.clearPendingRound(roundKey) }

// After (fixed)
result.onSuccess { sessionStore.decrementPendingClick(roundKey, count) }
```

## Root Cause 2: Score Doubled (Projection Race)

`applyLeaderboard()` computed: `selfProjectedTotal = selfRemoteTotal + sessionClicks`

When Firebase's local cache fires `observeSelfPlayer` (reflecting the flushed increment) BEFORE the ViewModel resets `sessionClicks`, both values are non-zero:

**Timeline:**
```
sessionClicks=5, remote=0 → projected=5 ✓
flush starts, incrementSession fires
Firebase local cache updates → observeSelfPlayer emits totalCount=5
applyLeaderboard() → 5 + 5 = 10 ← DOUBLED
flush completes → sessionClicks=0, remote=5 → projected=5 ✓ (too late)
```

**Sequence diagram (before fix — score doubles):**
```mermaid
sequenceDiagram
    participant UI as UI State
    participant VM as ViewModel
    participant Repo as Repository
    participant FB as Firebase

    Note over UI: sessionClicks=5, remote=0<br/>projected = 0+5 = 5 ✓

    VM->>Repo: flushPendingSession()
    Repo->>FB: incrementSession(delta=5)
    Note over FB: Local cache updates immediately
    FB-->>VM: observeSelfPlayer → totalCount=5
    VM->>VM: applyLeaderboard()
    Note over UI: sessionClicks=5, remote=5<br/>projected = 5+5 = 10 ✗ DOUBLED

    FB-->>Repo: Server ack
    Repo-->>VM: flush complete
    VM->>UI: sessionClicks = 0
    VM->>VM: applyLeaderboard()
    Note over UI: sessionClicks=0, remote=5<br/>projected = 5+0 = 5 ✓ (too late, user saw 10)
```

**Sequence diagram (after fix — inFlightFlush prevents doubling):**
```mermaid
sequenceDiagram
    participant UI as UI State
    participant VM as ViewModel
    participant Repo as Repository
    participant FB as Firebase

    Note over UI: sessionClicks=5, remote=0<br/>projected = 0+5 = 5 ✓

    VM->>VM: inFlightFlush = 5
    VM->>Repo: flushPendingSession()
    Repo->>FB: incrementSession(delta=5)
    Note over FB: Local cache updates immediately
    FB-->>VM: observeSelfPlayer → totalCount=5
    VM->>VM: applyLeaderboard()
    Note over VM: pendingNet = 5 - 5 = 0
    Note over UI: remote=5, pendingNet=0<br/>projected = 5+0 = 5 ✓

    Note over UI: User taps 3 more → sessionClicks=8
    VM->>VM: applyLeaderboard()
    Note over VM: pendingNet = 8 - 5 = 3
    Note over UI: remote=5, pendingNet=3<br/>projected = 5+3 = 8 ✓

    FB-->>Repo: Server ack
    Repo-->>VM: flush complete
    VM->>VM: inFlightFlush = 0
    VM->>UI: sessionClicks = 3
    Note over UI: remote=5, pendingNet=3<br/>projected = 5+3 = 8 ✓
```

**Fix:** Track in-flight flush amount and subtract from projection:
```kotlin
// Snapshot before flush
inFlightFlush = state.value.sessionClicks

// In applyLeaderboard, exclude in-flight taps from projection
val pendingNet = (state.value.sessionClicks - inFlightFlush).coerceAtLeast(0)
val selfProjectedTotal = selfRemoteTotal + pendingNet
```

During flush: `pendingNet = 5 - 5 = 0`, so `projected = 5 + 0 = 5` ✓
New taps during flush: `sessionClicks=8, inFlight=5 → pendingNet=3, projected = 5 + 3 = 8` ✓

## Interface Extraction

Extracted `MohamedLoversFirebaseApi` interface from `MohamedLoversFirebaseClient` to enable unit testing without Firebase. DI uses `bind`:
```kotlin
single { MohamedLoversFirebaseClient(get()) } bind MohamedLoversFirebaseApi::class
```

## Test Coverage

- `MohamedLoversSessionStoreTest` — 10 tests covering increment, decrement, concurrent taps, index cleanup
- `MohamedLoversRepositoryFlushTest` — 7 tests covering flush send, clear on success, preserve on failure, concurrent tap preservation, partial multi-round failure, empty pending, auth failure
- `MohamedLoversViewModelProjectionTest` — 1 test verifying projection doesn't double-count when observeSelfPlayer fires during flush
