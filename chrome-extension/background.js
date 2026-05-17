// Service worker:
//  - opens the popup as a detached "floating" window
//  - fires chrome.alarms when a QR session expires, so the local
//    counter resets even if the user closed the settings page

importScripts("lib/round.js", "lib/country.js", "lib/state.js");

const ALARM_NAME = "saloAlehQrExpiry";

async function rescheduleAlarm() {
  const obj = await chrome.storage.local.get(SaloState.STORAGE_KEY);
  const session = obj[SaloState.STORAGE_KEY]?.qrSession;
  if (session?.endsAt && session.endsAt > Date.now()) {
    chrome.alarms.create(ALARM_NAME, { when: session.endsAt });
  } else {
    chrome.alarms.clear(ALARM_NAME);
    if (session?.endsAt && session.endsAt <= Date.now()) {
      await SaloState.finishQrSession();
    }
  }
}

chrome.runtime.onMessage.addListener((message, _sender, sendResponse) => {
  if (message?.type === "openFloatingWindow") {
    chrome.windows.create({
      url: chrome.runtime.getURL("popup.html"),
      type: "popup",
      width: 300,
      height: 380,
      focused: true,
    });
    sendResponse({ ok: true });
    return true;
  }
  return false;
});

chrome.alarms.onAlarm.addListener(async (alarm) => {
  if (alarm.name === ALARM_NAME) {
    await SaloState.finishQrSession();
  }
});

chrome.storage.onChanged.addListener((changes, area) => {
  if (area === "local" && changes[SaloState.STORAGE_KEY]) {
    rescheduleAlarm();
  }
});

chrome.runtime.onInstalled.addListener(rescheduleAlarm);
chrome.runtime.onStartup.addListener(rescheduleAlarm);
