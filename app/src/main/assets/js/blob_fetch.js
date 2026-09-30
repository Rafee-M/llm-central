// blob_fetch.js — run by the app when the user downloads a "blob:" link in duck.ai (see MainActivity's
// DownloadListener, which calls this function with the link, the file-name header and the file type).
//
// A blob: link points to a file that exists only inside the web page's memory, so Android's normal
// download manager can't fetch it. This script reads the file's bytes in the page, turns them into a
// data: URL (text) and sends them to the app, which saves the file in Downloads.
// It prefers the Blob remembered by blob_download_patch.js (window.blobMap); if that's missing it asks
// the page to fetch the link itself.
//
// Round 5: moved here from a Java string in MainActivity (CLAUDE.md §7: injected JavaScript lives in
// assets/js). It now sends a "blob:" message through window.AppBridge (see web/DuckBridge.java) instead
// of calling Android.processBlob. The app passes the three values in with JSONObject.quote(), so a
// crafted file name can't break out of the string (PROJECT.md P8).
//
// This file is one function expression; the app appends "(url, contentDisposition, mimetype)".

(function(url, contentDisposition, mimetype) {
  function send(dataUrl, type) {
    if (!window.AppBridge) return;
    AppBridge.postMessage('blob:' + JSON.stringify({
      data: dataUrl,
      mime: type || '',
      disposition: contentDisposition || '',
      url: window.location.href
    }));
  }
  var blob = window.blobMap ? window.blobMap.get(url) : null;
  if (blob) {
    var reader = new FileReader();
    reader.onloadend = function() { send(reader.result, blob.type); };
    reader.readAsDataURL(blob);
    return;
  }
  var xhr = new XMLHttpRequest();
  xhr.open('GET', url, true);
  xhr.responseType = 'blob';
  xhr.onload = function() {
    if (this.status == 200) {
      var fileReader = new FileReader();
      fileReader.onloadend = function() { send(fileReader.result, mimetype); };
      fileReader.readAsDataURL(this.response);
    }
  };
  xhr.onerror = function() { console.error('Blob fetch failed: CSP or not found'); };
  xhr.send();
})
