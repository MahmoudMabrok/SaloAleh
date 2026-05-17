const countryEl = document.getElementById("country");
const roundEl = document.getElementById("round");
const countEl = document.getElementById("count");
const bonusEl = document.getElementById("bonus");
const tapBtn = document.getElementById("tap");
const openWindowBtn = document.getElementById("open-window");
const openSettingsBtn = document.getElementById("open-settings");

async function render() {
  const view = await SaloState.getView();
  countryEl.textContent = view.countryCode;
  roundEl.textContent = view.roundKey;
  countEl.textContent = view.count.toLocaleString("ar-EG");
  bonusEl.classList.toggle("hidden", !view.isFridayBonus);
  if (view.qrSessionActive) {
    const secs = Math.ceil(view.msRemaining / 1000);
    tapBtn.disabled = true;
    tapBtn.textContent = `إرسال جارٍ… ${secs} ث`;
  } else {
    tapBtn.disabled = false;
    tapBtn.textContent = "+1 صلاة";
  }
}

tapBtn.addEventListener("click", async () => {
  const next = await SaloState.increment(1);
  countEl.textContent = next.toLocaleString("ar-EG");
});

openWindowBtn.addEventListener("click", () => {
  chrome.runtime.sendMessage({ type: "openFloatingWindow" });
  window.close();
});

openSettingsBtn.addEventListener("click", () => {
  chrome.runtime.openOptionsPage();
  window.close();
});

chrome.storage.onChanged.addListener((changes, area) => {
  if (area === "local" && changes[SaloState.STORAGE_KEY]) render();
});

setInterval(render, 1000);
render();
