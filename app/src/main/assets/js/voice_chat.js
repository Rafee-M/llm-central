// voice_chat.js — injected into the chat page by the app (loaded via web/Scripts.java).
//
// Digital-assistant "start voice chat" feature: opens duck.ai's sidebar if needed and clicks the
// "Voice chat" button (found by its icon shape or its text). Retries for up to 15 seconds while the page
// loads. Relies on duck.ai's current page structure.
//
// Moved unchanged from a Java string in MainActivity (Phase 0.4); only line breaks were added.

(function() {
  window.isVoiceChatActive = true;
  console.log('Voice Chat Trigger Started');
  function tryClick() {
    var sidebarPath = document.querySelector('path[d*="M9.41 10.125a.625.625 0 1 1 0 1.25H1.624"]');
    var sidebarBtn = sidebarPath ? sidebarPath.closest('button, [role="button"]') : null;
    if (!sidebarBtn) {
        sidebarBtn = document.querySelector('button[aria-label*="sidebar"], button[aria-label*="Sidebar"]');
    }
    if (sidebarBtn && sidebarBtn.offsetParent !== null) {
      console.log('Opening sidebar first...');
      sidebarBtn.click();
    }
    var allPaths = document.querySelectorAll('path[d*="M5.625 0c.345 0 .625.28"]');
    for (var i = 0; i < allPaths.length; i++) {
        var btn = allPaths[i].closest('button, [role="button"]');
        if (btn) {
            console.log('Voice Chat found by SVG icon!');
            btn.click();
            return true;
        }
    }
    var elements = document.querySelectorAll('button, [role="button"], div > span, a');
    for (var i = 0; i < elements.length; i++) {
      var text = (elements[i].innerText || elements[i].textContent || '').trim();
      if (text.toLowerCase().includes('voice chat')) {
        console.log('Target found by text: ' + text);
        var clickTarget = elements[i];
        while (clickTarget && clickTarget.tagName !== 'BUTTON' && clickTarget.getAttribute('role') !== 'button' && clickTarget.tagName !== 'A') {
           clickTarget = clickTarget.parentElement;
        }
        if (!clickTarget) clickTarget = elements[i];
        clickTarget.click();
        return true;
      }
    }
    return false;
  }
  if (!tryClick()) {
    console.log('Waiting for buttons...');
    var observer = new MutationObserver(function(mutations, obs) {
      if (tryClick()) {
        obs.disconnect();
        clearInterval(fallbackInterval);
      }
    });
    observer.observe(document.body, { childList: true, subtree: true });
    var fallbackInterval = setInterval(tryClick, 1000);
    setTimeout(function() { 
      observer.disconnect(); 
      clearInterval(fallbackInterval); 
      console.log('Timeout after 15s');
    }, 15000);
  }
})();
