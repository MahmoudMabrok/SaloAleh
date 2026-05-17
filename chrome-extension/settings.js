const roundEl = document.getElementById("round");
const countEl = document.getElementById("count");
const uidEl = document.getElementById("uid");
const countryAutoEl = document.getElementById("country-auto");
const countryCodeEl = document.getElementById("country-code");
const saveCountryBtn = document.getElementById("save-country");
const countryStatusEl = document.getElementById("country-status");

const idlePane = document.getElementById("idle-pane");
const activePane = document.getElementById("active-pane");
const donePane = document.getElementById("done-pane");
const startBtn = document.getElementById("start-btn");
const cancelBtn = document.getElementById("cancel-btn");
const restartBtn = document.getElementById("restart-btn");
const countdownEl = document.getElementById("countdown");
const doneMsgEl = document.getElementById("done-msg");
const qrEl = document.getElementById("qr");
const qrMetaEl = document.getElementById("qr-meta");
const qrRawEl = document.getElementById("qr-raw");
const rawWrap = document.getElementById("raw-wrap");
const roundsTbody = document.querySelector("#rounds-table tbody");

let tickHandle = null;

function fmtArabic(n) {
  return n.toLocaleString("ar-EG");
}

function renderQr(payload) {
  const qr = qrcode(0, "M");
  qr.addData(payload, "Byte");
  qr.make();
  qrEl.innerHTML = qr.createSvgTag({ scalable: true, margin: 1 });
}

function setPane(name) {
  idlePane.classList.toggle("hidden", name !== "idle");
  activePane.classList.toggle("hidden", name !== "active");
  donePane.classList.toggle("hidden", name !== "done");
  rawWrap.classList.toggle("hidden", name !== "active");
}

function fmtCountdown(ms) {
  const total = Math.max(0, Math.ceil(ms / 1000));
  const m = String(Math.floor(total / 60)).padStart(2, "0");
  const s = String(total % 60).padStart(2, "0");
  return `${m}:${s}`;
}

function stopTick() {
  if (tickHandle) {
    clearInterval(tickHandle);
    tickHandle = null;
  }
}

function startTick(endsAt) {
  stopTick();
  const update = async () => {
    const remaining = endsAt - Date.now();
    countdownEl.textContent = fmtCountdown(remaining);
    countdownEl.classList.toggle("warn", remaining > 0 && remaining < 15000);
    if (remaining <= 0) {
      stopTick();
      // Service worker alarm will have run finishQrSession; storage
      // change will trigger a re-render. As a fallback in case the
      // alarm was suspended, call it here too — finishQrSession is a
      // no-op when there's no session.
      await SaloState.finishQrSession();
    }
  };
  update();
  tickHandle = setInterval(update, 500);
}

async function render() {
  const view = await SaloState.getView();
  roundEl.textContent = view.roundKey;
  countEl.textContent = fmtArabic(view.count);
  uidEl.textContent = view.uid;

  countryAutoEl.checked = view.countryAuto;
  countryCodeEl.value = view.countryCode;
  countryCodeEl.disabled = view.countryAuto;

  if (view.qrSessionActive) {
    const session = view.qrSession;
    const payload = SaloState.buildQrPayload(view, session);
    renderQr(payload);
    qrRawEl.textContent = payload;
    qrMetaEl.textContent = `${view.countryCode} · ${view.roundKey} · ${fmtArabic(
      session.count,
    )}`;
    setPane("active");
    startTick(session.endsAt);
  } else if (view.qrSession && !view.qrSessionActive) {
    // Stale session in storage but past endsAt — finalize.
    await SaloState.finishQrSession();
    return;
  } else if (view.lastSubmittedAt) {
    stopTick();
    const when = new Date(view.lastSubmittedAt).toLocaleString("ar-EG");
    doneMsgEl.textContent = `تم تصفير العداد بعد انتهاء النافذة بتاريخ ${when}.`;
    setPane("done");
  } else {
    stopTick();
    setPane("idle");
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

startBtn.addEventListener("click", async () => {
  await SaloState.startQrSession();
  render();
});

restartBtn.addEventListener("click", async () => {
  await SaloState.startQrSession();
  render();
});

cancelBtn.addEventListener("click", async () => {
  await SaloState.cancelQrSession();
  render();
});

chrome.storage.onChanged.addListener((changes, area) => {
  if (area === "local" && changes[SaloState.STORAGE_KEY]) render();
});

window.addEventListener("beforeunload", stopTick);

render();
