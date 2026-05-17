// Firebase RTDB REST helpers. No auth — the only path we touch
// (`mohamed_lovers/handoffs/<nonce>`) is gated by per-key rules.

const RTDB_BASE = "https://kamapp-3b3ac-default-rtdb.firebaseio.com";
const HANDOFF_PATH = "mohamed_lovers/handoffs";

async function readHandoff(nonce) {
  if (!nonce || !/^[a-f0-9]{16,64}$/i.test(nonce)) {
    throw new Error("invalid nonce");
  }
  const url = `${RTDB_BASE}/${HANDOFF_PATH}/${encodeURIComponent(nonce)}.json`;
  const resp = await fetch(url, { method: "GET", cache: "no-store" });
  if (!resp.ok) {
    throw new Error(`handoff fetch ${resp.status}`);
  }
  const body = await resp.json();
  return body; // null when not yet written
}

self.SaloFirebase = { readHandoff, RTDB_BASE };
