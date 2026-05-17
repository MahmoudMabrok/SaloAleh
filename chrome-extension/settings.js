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

  const snapshot = await SaloState.ensureSubmissionSnapshot();
  const payload = SaloState.buildQrPayload(view, snapshot);
  renderQr(payload);
  qrRawEl.textContent = payload;
  qrMetaEl.textContent = `${view.countryCode} · ${view.roundKey} · ${fmtArabic(
    snapshot.count,
  )}`;

  if (view.lastSubmittedAt) {
    const when = new Date(view.lastSubmittedAt).toLocaleString("ar-EG");
    setHandoffStatus(`آخر إرسال مؤكّد: ${when}.`, "ok");
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

refreshQrBtn.addEventListener("click", render);

confirmResetBtn.addEventListener("click", async () => {
  const view = await SaloState.getView();
  if (view.count === 0) {
    setHandoffStatus("العداد فارغ بالفعل.", "");
    return;
  }
  const snapshot = await SaloState.ensureSubmissionSnapshot();
  const ok = confirm(
    `سيتم خصم ${fmtArabic(snapshot.count)} صلاة (ما يحتويه الكود) من جولة ${
      view.roundKey
    }. تابع فقط بعد أن أرسل الجوال النتيجة.`,
  );
  if (!ok) return;
  const result = await SaloState.applySubmitted();
  if (result.applied) {
    setHandoffStatus(
      `تم خصم ${fmtArabic(result.subtracted)} صلاة. المتبقي للجولة: ${fmtArabic(
        result.newCount,
      )}.`,
      "ok",
    );
  }
  render();
});

chrome.storage.onChanged.addListener((changes, area) => {
  if (area === "local" && changes[SaloState.STORAGE_KEY]) {
    render();
  }
});

render();
