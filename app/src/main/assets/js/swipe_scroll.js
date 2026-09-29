// swipe_scroll.js — injected into the chat page by the app (loaded via web/Scripts.java).
//
// Two small listeners on the chat page:
// 1) on scroll, reports whether the page is at the top (Android.setSwipeEnabled, currently a no-op
// left over from an unfinished pull-to-refresh feature, see PROJECT.md P12);
// 2) on click, notices when the user taps duck.ai's "Voice chat" button (window.isVoiceChatActive).
//
// Moved unchanged from a Java string in MainActivity (Phase 0.4); only line breaks were added.

(function() {
    if (window.swipeScrollInjected) return;
    window.swipeScrollInjected = true;
    document.addEventListener('scroll', function(e) {
        if (e.target && e.target.scrollTop !== undefined) {
            var isAtTop = e.target.scrollTop === 0;
            Android.setSwipeEnabled(isAtTop);
        }
    }, true);
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
