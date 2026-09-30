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
//
// It also notes the NAME the page gives the file. Pages name a download with <a download="report.csv">,
// but WebView doesn't tell the app that name (only the address and type; verified in Chromium's
// AwDownloadManagerDelegate), so without this the app had to guess and got the chat's address.
// Pages start the download with link.click(), link.dispatchEvent(click) or a real tap on the link;
// all three are noticed. window.__llmcDownloadNames maps address → name (blob_fetch.js reads it) and
// window.__llmcLastDownload is the newest one (download_name.js reads it for data: links).

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
    setTimeout(function() {
      window.blobMap.delete(url);
      window.__llmcDownloadNames.delete(url);
    }, 60000);
  };

  window.__llmcDownloadNames = window.__llmcDownloadNames || new Map();
  function remember(link) {
    try {
      if (!link || !link.hasAttribute || !link.hasAttribute('download') || !link.href) return;
      var name = link.getAttribute('download') || '';
      // data: addresses can be huge, so only the newest one is kept (not in the map).
      if (link.href.indexOf('data:') !== 0) window.__llmcDownloadNames.set(link.href, name);
      window.__llmcLastDownload = { href: link.href.indexOf('data:') === 0 ? 'data:' : link.href, name: name, time: Date.now() };
    } catch (e) {}
  }
  var click = HTMLAnchorElement.prototype.click;
  HTMLAnchorElement.prototype.click = function() {
    remember(this);
    return click.apply(this, arguments);
  };
  var dispatch = HTMLAnchorElement.prototype.dispatchEvent;
  HTMLAnchorElement.prototype.dispatchEvent = function(event) {
    if (event && event.type === 'click') remember(this);
    return dispatch.apply(this, arguments);
  };
  document.addEventListener('click', function(e) {
    var link = e.target && e.target.closest ? e.target.closest('a[download]') : null;
    if (link) remember(link);
  }, true);
})();
