// image_zoom_monitor.js — injected into the chat page by the app (loaded via web/Scripts.java).
//
// Watches for duck.ai's full-screen image viewer and tells the app ("zoom:true/false" message through window.AppBridge)
// so the app stops treating pinch gestures as "change text size" while an image is zoomed.
// Note: relies on duck.ai's internal CSS class name, which can change when duck.ai updates.
//
// Moved unchanged from a Java string in MainActivity (Phase 0.4); only line breaks were added.

(function() {
    if (window.imageZoomMonitorInjected) return;
    window.imageZoomMonitorInjected = true;
    var lastState = false;
    var check = function() {
        var el = document.querySelector('.UOuDiDFlbyCQchgZ4909');
        var isActive = false;
        if (el) {
            var style = window.getComputedStyle(el);
            isActive = style.display !== 'none' && style.visibility !== 'hidden';
        }
        if (isActive !== lastState) {
            lastState = isActive;
            if (window.AppBridge) AppBridge.postMessage('zoom:' + isActive);
        }
    };
    var target = document.body || document.documentElement;
    if (target) {
        var observer = new MutationObserver(check);
        observer.observe(target, { childList: true, subtree: true, attributes: true });
    }
})();
