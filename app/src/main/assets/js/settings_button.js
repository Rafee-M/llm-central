// settings_button.js — injected into the chat page by the app (loaded via web/Scripts.java).
//
// Adds the app's own settings button to the duck.ai page: finds duck.ai's settings icon (by its SVG
// shape), clones it, swaps in a wrench icon and makes it call Android.showSettingsDialog().
// Will be replaced by a native top bar in Phase 2 (PROJECT.md R-SW-1).
//
// Moved unchanged from a Java string in MainActivity (Phase 0.4); only line breaks were added.

(function() {
    if (window.duckAssistSettingsButtonInjected) return;
    function injectButton() {
        var webSettingsPath = document.querySelector('path[d^="M5.647 14.153"]');
        var webSettingsBtn = webSettingsPath ? webSettingsPath.closest('button, [role="button"]') : null;
        if (webSettingsBtn && !document.querySelector('.duckassist-native-settings-btn')) {
            console.log('Found web settings button, injecting our button beside it');
            var ourBtn = webSettingsBtn.cloneNode(true);
            ourBtn.classList.add('duckassist-native-settings-btn');
            var svg = ourBtn.querySelector('svg');
            if (svg) {
                svg.innerHTML = '<path fill="currentColor" d="M22.7 19l-9.1-9.1c.9-2.3.4-5-1.5-6.9-2-2-5-2.4-7.4-1.3L9 6 6 9 1.6 4.3C.5 6.7.9 9.8 2.9 11.8c1.9 1.9 4.6 2.4 6.9 1.5l9.1 9.1c.4.4 1 .4 1.4 0l2.3-2.3c.5-.4.5-1.1.1-1.1z"/>';
            }
            ourBtn.style.marginLeft = '8px';
            ourBtn.style.marginRight = '8px';
            ourBtn.addEventListener('click', function(e) {
                e.stopPropagation();
                e.preventDefault();
                if (typeof Android !== 'undefined' && Android.showSettingsDialog) {
                    Android.showSettingsDialog();
                }
            });
            webSettingsBtn.parentNode.insertBefore(ourBtn, webSettingsBtn.nextSibling);
            window.duckAssistSettingsButtonInjected = true;
            return true;
        }
        return false;
    }
    if (!injectButton()) {
        var target = document.body || document.documentElement;
        if (target) {
            var observer = new MutationObserver(function(mutations) {
                if (injectButton()) {
                    observer.disconnect();
                }
            });
            observer.observe(target, { childList: true, subtree: true });
        }
    }
})();
