// Service worker: handles opening a detached "floating" window of the popup.

chrome.runtime.onMessage.addListener((message, _sender, sendResponse) => {
  if (message?.type === "openFloatingWindow") {
    chrome.windows.create({
      url: chrome.runtime.getURL("popup.html"),
      type: "popup",
      width: 300,
      height: 360,
      focused: true,
    });
    sendResponse({ ok: true });
    return true;
  }
  return false;
});
