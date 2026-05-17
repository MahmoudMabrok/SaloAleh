const roundEl = document.getElementById("round");
const countEl = document.getElementById("count");
const uidEl = document.getElementById("uid");
const countryAutoEl = document.getElementById("country-auto");
const countryCodeEl = document.getElementById("country-code");
const saveCountryBtn = document.getElementById("save-country");
const countryStatusEl = document.getElementById("country-status");
const qrEl = document.getElementById("qr");
const qrMetaEl = document.getElementById("qr-meta");
const qrRawEl = document.getElementById("qr-raw");
const confirmResetBtn = document.getElementById("confirm-reset");
const refreshQrBtn = document.getElementById("refresh-qr");
const handoffStatusEl = document.getElementById("handoff-status");
const roundsTbody = document.querySelector("#rounds-table tbody");

const POLL_INTERVAL_MS = 4000;
let pollTimer = null;

function renderQr(payload) {
  const qr = qrcode(0, "M");
  qr.addData(payload, "Byte");
  qr.make();
  qrEl.innerHTML = qr.createSvgTag({ scalable: true, margin: 1 });
}

function fmtArabic(n) {
  return n.toLocaleString("ar-EG");
}

function setHandoffStatus(text, kind) {
  handoffStatusEl.textContent = text || "";
  handoffStatusEl.className = "hint" + (kind ? " " + kind : "");
}

async function render() {
  const view = await SaloState.getView();
  roundEl.textContent = view.roundKey;
  countEl.textContent = fmtArabic(view.count);
  uidEl.textContent = view.uid;

  countryAutoEl.checked = view.countryAuto;
  countryCodeEl.value = view.countryCode;
  countryCodeEl.disabled = view.countryAuto;

  const handoff = await SaloState.ensureHandoff();
  const payload = SaloState.buildQrPayload(view, handoff);
  renderQr(payload);
  qrRawEl.textContent = payload;
  qrMetaEl.textContent = `${view.countryCode} · ${view.roundKey} · ${fmtArabic(
    handoff.count,
  )}`;

  if (view.lastApplied && !view.lastApplied.manual) {
    const when = new Date(view.lastApplied.at).toLocaleString("ar-EG");
    setHandoffStatus(
      `تم استلام ${fmtArabic(view.lastApplied.count)} صلاة عبر الجوال بتاريخ ${when}.`,
      "ok",
    );
  } else if (view.pendingHandoff) {
    setHandoffStatus("بانتظار مسح الكود من تطبيق الجوال…", "");
  } else {
    setHandoffStatus("");
  }

  renderRoundsTable();
}

async function renderRoundsTable() {
  const obj = await chrome.storage.local.get(SaloState.STORAGE_KEY);
  const state = obj[SaloState.STORAGE_KEY];
  roundsTbody.innerHTML = "";
  if (!state?.rounds) return;
  const entries = Object.entries(state.rounds).sort((a, b) =>
    a[0] < b[0] ? 1 : -1,
  );
  for (const [round, data] of entries) {
    const tr = document.createElement("tr");
    const tdR = document.createElement("td");
    tdR.textContent = round;
    const tdC = document.createElement("td");
    tdC.textContent = fmtArabic(data.count ?? 0);
    tr.append(tdR, tdC);
    roundsTbody.appendChild(tr);
  }
}

async function pollHandoff() {
  const obj = await chrome.storage.local.get(SaloState.STORAGE_KEY);
  const state = obj[SaloState.STORAGE_KEY];
  const pending = state?.pendingHandoff;
  if (!pending?.nonce) return;
  try {
    const record = await SaloFirebase.readHandoff(pending.nonce);
    if (!record) return;
    const result = await SaloState.applyHandoffRecord(record);
    if (result.applied) {
      setHandoffStatus(
        `تم استلام ${fmtArabic(result.previousCount - result.newCount)} صلاة عبر الجوال.`,
        "ok",
      );
    }
  } catch (e) {
    // Network errors are silent — the background alarm will retry.
  }
}

countryAutoEl.addEventListener("change", () => {
  countryCodeEl.disabled = countryAutoEl.checked;
  if (countryAutoEl.checked) {
    countryCodeEl.value = SaloCountry.detectCountryCode();
  }
});

saveCountryBtn.addEventListener("click", async () => {
  const auto = countryAutoEl.checked;
  const code = countryCodeEl.value.trim().toUpperCase();
  if (!auto && !/^[A-Z]{2,3}$/.test(code)) {
    countryStatusEl.textContent = "أدخل رمز دولة من حرفين أو ثلاثة (مثال: EG)";
    countryStatusEl.className = "hint err";
    return;
  }
  const saved = await SaloState.setCountry(code, auto);
  countryStatusEl.textContent = `تم الحفظ: ${saved}`;
  countryStatusEl.className = "hint ok";
  render();
});

refreshQrBtn.addEventListener("click", async () => {
  await SaloState.refreshHandoff();
  render();
});

confirmResetBtn.addEventListener("click", async () => {
  const view = await SaloState.getView();
  if (view.count === 0) {
    setHandoffStatus("العداد فارغ بالفعل.", "");
    return;
  }
  const ok = confirm(
    `سيتم تصفير ${fmtArabic(view.count)} صلاة لجولة ${view.roundKey}. متابعة؟`,
  );
  if (!ok) return;
  await SaloState.resetCurrentRound();
  render();
});

chrome.storage.onChanged.addListener((changes, area) => {
  if (area === "local" && changes[SaloState.STORAGE_KEY]) {
    render();
  }
});

(function startPolling() {
  pollHandoff();
  pollTimer = setInterval(pollHandoff, POLL_INTERVAL_MS);
  document.addEventListener("visibilitychange", () => {
    if (document.hidden) {
      clearInterval(pollTimer);
      pollTimer = null;
    } else if (!pollTimer) {
      pollHandoff();
      pollTimer = setInterval(pollHandoff, POLL_INTERVAL_MS);
    }
  });
})();

render();
