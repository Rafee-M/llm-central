// download_name.js — answers the app: "what name did the page give the data: file it just offered?"
// Run by MainActivity's DownloadListener (ChatGPT, Claude, DeepSeek, Z.ai) with evaluateJavascript, which
// hands the answer back to Java. WebView doesn't pass that name on itself; blob_capture.js noted it when
// the page clicked its download link. Only a click from the last 10 seconds counts; otherwise "".

(function() {
  var d = window.__llmcLastDownload;
  return d && d.href === 'data:' && Date.now() - d.time < 10000 ? d.name : '';
})()
