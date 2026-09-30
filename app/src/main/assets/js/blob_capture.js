// blob_capture.js — lets the app save files that ChatGPT, Claude, DeepSeek or Z.ai create inside the page
// (a generated CSV, an exported chat…). Injected after each page load on those providers (see
// MainActivity's onPageFinished and web/AppBridge's downloads-only bridge).
//
// How a page offers such a file: it wraps the bytes in a Blob, asks the browser for a temporary address
// with URL.createObjectURL(blob) ("blob:https://chatgpt.com/…"), clicks a hidden link to it, and often
// cancels the address at once with URL.revokeObjectURL(). The app only hears about the download a moment
// later, when the address may already be gone.
// So this script remembers each Blob by its address (window.blobMap, which blob_fetch.js reads). When the
// page cancels an address, the address stops working right away as the page expects, but the Blob stays
// in the map for 60 seconds, then it's forgotten, so memory is only held briefly.
// duck.ai uses its own blob_download_patch.js instead (its offline-chat backup reads the map).

(function() {
  if (window.__llmcBlobCapture) return;
  window.__llmcBlobCapture = true;
  window.blobMap = window.blobMap || new Map();
  var create = URL.createObjectURL;
  var revoke = URL.revokeObjectURL;
  URL.createObjectURL = function(obj) {
    var url = create.apply(URL, arguments);
    if (obj instanceof Blob) window.blobMap.set(url, obj);
    return url;
  };
  URL.revokeObjectURL = function(url) {
    revoke.apply(URL, arguments);
    setTimeout(function() { window.blobMap.delete(url); }, 60000);
  };
})();
