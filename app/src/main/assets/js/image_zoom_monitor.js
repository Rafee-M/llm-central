// image_zoom_monitor.js — injected into the chat page by the app (loaded via web/Scripts.java).
//
// Watches for duck.ai's full-screen image viewer and tells the app ("zoom:true/false" message through window.AppBridge)
// so the app stops treating pinch gestures as "change text size" while an image is zoomed.
// Note: relies on duck.ai's internal CSS class name, which can change when duck.ai updates.
//
// Speed (2026-10-15): the first version searched the WHOLE page (querySelector) on every change
// the page made. While an answer streams in, the page changes for every word, and the search takes
// longer the longer the chat is, so duck.ai got slower the longer it was used (measured in Chromium:
// 0.1 ms per word with 100 messages, 2.2 ms with 3,000). Now it only looks at what changed: new
// elements (is the viewer among them?) and changes on the viewer itself or around it. Same answers
// to the app as before (checked side by side in Chromium, CHANGES.md 2026-10-15).

(function() {
    if (window.imageZoomMonitorInjected) return;
    window.imageZoomMonitorInjected = true;
    var CLASS = 'UOuDiDFlbyCQchgZ4909';
    var lastState = false;
    var viewer = document.getElementsByClassName(CLASS)[0] || null; // one search, at start only

    // Is the viewer element on screen? (the same rule as before: shown and not hidden)
    var check = function() {
        if (viewer && (!viewer.isConnected || !viewer.classList.contains(CLASS))) {
            viewer = document.getElementsByClassName(CLASS)[0] || null; // gone or renamed: another one?
        }
        var isActive = false;
        if (viewer) {
            var style = window.getComputedStyle(viewer);
            isActive = style.display !== 'none' && style.visibility !== 'hidden';
        }
        if (isActive !== lastState) {
            lastState = isActive;
            if (window.AppBridge) AppBridge.postMessage('zoom:' + isActive);
        }
    };

    // Could this change have shown, hidden, added or removed the viewer? Only looks at the changed
    // elements (a streamed word is one small element), never at the whole page.
    var matters = function(record) {
        if (record.type === 'attributes') {
            var target = record.target;
            if (target.classList && target.classList.contains(CLASS)) {
                if (!viewer || !viewer.isConnected) viewer = target; // just became the viewer
                return true;
            }
            // A change on the viewer, around it (a parent can hide what's inside) or next to it (CSS can
            // style an element by its neighbours): the changed element's parent holds the viewer.
            return viewer !== null && target.parentNode !== null && target.parentNode.contains(viewer);
        }
        var i, node;
        for (i = 0; i < record.addedNodes.length; i++) {
            node = record.addedNodes[i];
            if (node.nodeType !== 1) continue; // text: can't be the viewer
            if (node.classList.contains(CLASS)) {
                if (!viewer || !viewer.isConnected) viewer = node;
                return true;
            }
            var inside = node.getElementsByClassName(CLASS)[0];
            if (inside) {
                if (!viewer || !viewer.isConnected) viewer = inside;
                return true;
            }
        }
        if (viewer !== null) {
            // Elements added or removed next to the viewer or around it (CSS neighbours, see above).
            if (record.target.contains(viewer)) return true;
            for (i = 0; i < record.removedNodes.length; i++) {
                node = record.removedNodes[i];
                if (node === viewer || (node.nodeType === 1 && node.contains(viewer))) return true;
            }
        }
        return false;
    };

    var target = document.body || document.documentElement;
    if (target) {
        var observer = new MutationObserver(function(records) {
            for (var i = 0; i < records.length; i++) {
                if (matters(records[i])) {
                    check();
                    return;
                }
            }
        });
        observer.observe(target, { childList: true, subtree: true, attributes: true });
    }
    if (viewer) check(); // already open when the script starts
})();
