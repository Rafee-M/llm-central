// clipboard_patch.js — injected into the chat page by the app (loaded via web/Scripts.java).
//
// Makes the site's "Copy" buttons work: navigator.clipboard.writeText is replaced by a call to
// the app ("copy:" message through window.AppBridge, see web/DuckBridge.java), because the web clipboard API is unreliable inside WebView.
//
// Moved unchanged from a Java string in MainActivity (Phase 0.4); only line breaks were added.

(function() {
    if (window.clipboardPatchInjected) return;
    window.clipboardPatchInjected = true;
    // Only when the app's bridge is there (older WebViews have none): otherwise keep the browser's own.
    if (navigator.clipboard && window.AppBridge) {
        navigator.clipboard.writeText = function(text) {
            return new Promise((resolve, reject) => {
                try {
                    AppBridge.postMessage('copy:' + String(text));
                    resolve();
                } catch(e) {
                    reject(e);
                }
            });
        };
    }
})();
