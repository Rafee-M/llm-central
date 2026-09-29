// clipboard_patch.js — injected into the chat page by the app (loaded via web/Scripts.java).
//
// Makes the site's "Copy" buttons work: navigator.clipboard.writeText is replaced by a call to
// the app (Android.copyToClipboard), because the web clipboard API is unreliable inside WebView.
//
// Moved unchanged from a Java string in MainActivity (Phase 0.4); only line breaks were added.

(function() {
    if (window.clipboardPatchInjected) return;
    window.clipboardPatchInjected = true;
    if (navigator.clipboard) {
        navigator.clipboard.writeText = function(text) {
            return new Promise((resolve, reject) => {
                try {
                    Android.copyToClipboard(text);
                    resolve();
                } catch(e) {
                    reject(e);
                }
            });
        };
    }
})();
