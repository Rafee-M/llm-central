// zoom_viewport.js — injected into the chat pages by the app (web/PageZoom, setting "Pinch zooms the page").
//
// Lets a pinch zoom the whole page freely, like in a browser: the page is magnified as it is, nothing
// re-flows. Most chat sites forbid that in their <meta name="viewport"> ("user-scalable=no" or
// "maximum-scale=1"), and Android's WebView has no "force zoom" switch like Chrome's accessibility
// option (checked in Chromium's AwSettings). So this script keeps the page's own viewport values and
// only lifts the ban: "user-scalable" and "maximum-scale" are replaced by "maximum-scale=5,
// user-scalable=yes". Whenever the page changes its viewport tag, the ban is lifted again.
//
// Runs at document start (before the page's code); that is what counts. Measured in Chromium
// (2026-10-15): once a page has loaded with the ban, changing its viewport tag afterwards no longer
// changes the zoom limits. That's why web/PageZoom reloads the open tabs when the setting changes.
// The run after each page load is only a best-effort fallback for very old WebViews without
// document-start scripts. window.__llmcPageZoom.off() puts the page's own values back.
//
// Desktop site (desktop_viewport.js, window.__llmcDesktopViewport) sets "width=1024", which already
// allows zooming; this script then does nothing, so the two never fight over the tag.

(function() {
    if (window.__llmcPageZoom) { window.__llmcPageZoom.apply(); return; }

    var BAN = /^(user-scalable|maximum-scale)$/i;
    var own = new WeakMap();      // tag → the page's own value (put back by off())
    var written = new WeakMap();  // tag → the value this script last wrote
    var headWatch = null, docWatch = null;

    // The page's value without the ban, plus our two rules. Applying it twice gives the same text.
    function allowZoom(content) {
        var parts = String(content || '').split(/[,;]/).map(function(p) { return p.trim(); })
            .filter(function(p) { return p && !BAN.test(p.split('=')[0].trim()); });
        parts.push('maximum-scale=5', 'user-scalable=yes');
        return parts.join(', ');
    }

    function apply() {
        if (window.__llmcDesktopViewport) return; // desktop site: its wide page already zooms
        var head = document.head;
        if (!head) return;
        var tags = head.querySelectorAll('meta[name="viewport"]');
        for (var i = 0; i < tags.length; i++) {
            var tag = tags[i];
            if (tag.content !== written.get(tag)) own.set(tag, tag.content); // the page set it
            var want = allowZoom(tag.content);
            if (tag.content !== want) {          // only when different: no endless loop
                written.set(tag, want);
                tag.content = want;
            }
        }
    }

    function watchHead() {
        if (!document.head) return false;
        headWatch = new MutationObserver(apply);
        headWatch.observe(document.head,
            { childList: true, subtree: true, attributes: true, attributeFilter: ['content', 'name'] });
        apply();
        return true;
    }

    function off() {
        if (headWatch) headWatch.disconnect();
        if (docWatch) docWatch.disconnect();
        var tags = document.head ? document.head.querySelectorAll('meta[name="viewport"]') : [];
        for (var i = 0; i < tags.length; i++) {
            if (own.has(tags[i])) tags[i].content = own.get(tags[i]);
        }
        delete window.__llmcPageZoom;
    }

    window.__llmcPageZoom = { apply: apply, off: off };
    if (!watchHead()) {
        // At "document start" there may be no <head> yet: wait for it to appear.
        docWatch = new MutationObserver(function() {
            if (watchHead()) docWatch.disconnect();
        });
        docWatch.observe(document.documentElement || document, { childList: true, subtree: true });
    }
})();
