// desktop_viewport.js — "Desktop site" mode (see web/DesktopMode.java): makes the page lay itself out
// for a 1024-pixel-wide screen, like a computer, instead of the phone's ~400 pixels.
//
// Web pages say how wide they want to be drawn with a <meta name="viewport"> tag, usually
// "width=device-width" (= the phone's width). This script replaces that with "width=1024"; WebView
// then shows the whole page zoomed out to fit, and the user can pinch to zoom in.
// Sites built with React/Next.js may put their own tag back while they run, so the <head> is watched
// and the value restored. Only <head> is watched (not the whole page), so streaming chat replies don't
// wake it up.
//
// Runs at "document start" (before the page's own scripts) where WebView supports it, and again after
// each page load; running it twice does nothing extra.

(function() {
  var CONTENT = 'width=1024';
  function apply() {
    var head = document.head;
    if (!head) return;
    var tags = head.querySelectorAll('meta[name="viewport"]');
    if (!tags.length) {
      var tag = document.createElement('meta');
      tag.name = 'viewport';
      tag.content = CONTENT;
      head.appendChild(tag);
      return;
    }
    for (var i = 0; i < tags.length; i++) {
      if (tags[i].content !== CONTENT) tags[i].content = CONTENT; // only when different: no endless loop
    }
  }
  if (window.__llmcDesktopViewport) { apply(); return; }
  window.__llmcDesktopViewport = true;
  function watchHead() {
    if (!document.head) return false;
    new MutationObserver(apply).observe(document.head,
        { childList: true, subtree: true, attributes: true, attributeFilter: ['content', 'name'] });
    apply();
    return true;
  }
  if (!watchHead()) {
    // At "document start" there may be no <head> yet: wait for it to appear.
    var waiter = new MutationObserver(function() {
      if (watchHead()) waiter.disconnect();
    });
    waiter.observe(document.documentElement || document, { childList: true, subtree: true });
  }
})();
