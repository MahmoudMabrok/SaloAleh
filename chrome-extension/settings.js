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
const roundsTbody = document.querySelector("#rounds-table tbody");

function renderQr(payload) {
  // typeNumber=0 lets the library pick the smallest version that fits.
  const qr = qrcode(0, "M");
  qr.addData(payload, "Byte");
  qr.make();
  qrEl.innerHTML = qr.createSvgTag({ scalable: true, margin: 1 });
}

async function render() {
  const view = await SaloState.getView();
  roundEl.textContent = view.roundKey;
  countEl.textContent = view.count.toLocaleString("ar-EG");
  uidEl.textContent = view.uid;

  countryAutoEl.checked = view.countryAuto;
  countryCodeEl.value = view.countryCode;
  countryCodeEl.disabled = view.countryAuto;

  const payload = SaloState.buildQrPayload(view);
  renderQr(payload);
  qrRawEl.textContent = payload;
  qrMetaEl.textContent = `${view.countryCode} · ${view.roundKey} · ${view.count.toLocaleString(
    "ar-EG",
  )}`;

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
    tdC.textContent = (data.count ?? 0).toLocaleString("ar-EG");
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
    countryStatusEl.textContent = "العداد فارغ بالفعل.";
    countryStatusEl.className = "hint";
    return;
  }
  const ok = confirm(
    `سيتم تصفير ${view.count.toLocaleString("ar-EG")} صلاة لجولة ${view.roundKey}. متابعة؟`,
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

render();
