// rtl_cleanup.js — injected into the chat page by the app (loaded via web/Scripts.java).
//
// Undoes rtl_resolver.js when the setting is switched off: stops watching, removes its style and
// the dir="rtl" attributes it added.
//
// Moved unchanged from a Java string in MainActivity (Phase 0.4); only line breaks were added.

(function() {
    window.__aiRtlResolverInjected = false;
    if (window.__aiRtlObserver) {
        window.__aiRtlObserver.disconnect();
        window.__aiRtlObserver = null;
    }
    var style = document.getElementById('ai-rtl-resolver-style');
    if (style) style.remove();
    document.querySelectorAll('[dir="rtl"]').forEach(function(el) {
        el.removeAttribute('dir');
    });
})();
