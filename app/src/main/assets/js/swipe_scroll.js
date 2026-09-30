// swipe_scroll.js — injected into the chat page by the app (loaded via web/Scripts.java).
//
// On click, notices when the user taps duck.ai's "Voice chat" button (window.isVoiceChatActive).
//
// Moved from a Java string in MainActivity (Phase 0.4). Round 5 removed its scroll listener: it only
// reported "page at the top" to a no-op app method left from an unfinished pull-to-refresh (PROJECT.md P12).

(function() {
    if (window.swipeScrollInjected) return;
    window.swipeScrollInjected = true;
    document.addEventListener('click', function(e) {
        var btn = e.target && e.target.closest('button, [role="button"], a');
        if (btn) {
            var text = (btn.innerText || btn.textContent || '').toLowerCase();
            var hasVoiceSvg = btn.querySelector('path[d*="M5.625 0c.345 0 .625.28"]');
            if (hasVoiceSvg || text.includes('voice chat')) {
                window.isVoiceChatActive = true;
            }
        }
    }, true);
})();
