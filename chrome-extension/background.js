// Service worker:
//  - opens the popup as a detached "floating" window
//  - polls Firebase for pending handoff confirmations and auto-resets

importScripts(
  "lib/round.js",
  "lib/country.js",
  "lib/state.js",
  "lib/firebase.js",
);

const ALARM_NAME = "saloAlehHandoffPoll";
const POLL_PERIOD_MIN = 1; // chrome.alarms minimum is 1 minute in production

async function rescheduleAlarm() {
  const obj = await chrome.storage.local.get(SaloState.STORAGE_KEY);
  const pending = obj[SaloState.STORAGE_KEY]?.pendingHandoff;
  if (pending?.nonce) {
    const existing = await chrome.alarms.get(ALARM_NAME);
    if (!existing) {
      chrome.alarms.create(ALARM_NAME, {
        delayInMinutes: POLL_PERIOD_MIN,
        periodInMinutes: POLL_PERIOD_MIN,
      });
    }
  } else {
    chrome.alarms.clear(ALARM_NAME);
  }
}

async function pollOnce() {
  const obj = await chrome.storage.local.get(SaloState.STORAGE_KEY);
  const pending = obj[SaloState.STORAGE_KEY]?.pendingHandoff;
  if (!pending?.nonce) {
    chrome.alarms.clear(ALARM_NAME);
    return;
  }
  try {
    const record = await SaloFirebase.readHandoff(pending.nonce);
    if (record) {
      await SaloState.applyHandoffRecord(record);
      chrome.alarms.clear(ALARM_NAME);
    }
  } catch (_e) {
    // Try again on the next tick.
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
  if (message?.type === "pollHandoffNow") {
    pollOnce().then(() => sendResponse({ ok: true }));
    return true;
  }
  return false;
});

chrome.alarms.onAlarm.addListener((alarm) => {
  if (alarm.name === ALARM_NAME) pollOnce();
});

chrome.storage.onChanged.addListener((changes, area) => {
  if (area === "local" && changes[SaloState.STORAGE_KEY]) {
    rescheduleAlarm();
  }
});

chrome.runtime.onInstalled.addListener(rescheduleAlarm);
chrome.runtime.onStartup.addListener(rescheduleAlarm);
