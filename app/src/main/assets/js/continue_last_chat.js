// continue_last_chat.js — injected into the chat page by the app (loaded via web/Scripts.java).
//
// "Continue last chat" setting: opens duck.ai's sidebar and clicks the most recent conversation,
// then reports back ("continued:" message). Gives up after 10 attempts / 12 seconds.
//
// Moved unchanged from a Java string in MainActivity (Phase 0.4); only line breaks were added.
// Round 5: talks to the app through window.AppBridge.postMessage("type:payload") (see web/DuckBridge.java)
// instead of the old window.Android object.

(function() {
  console.log('Continue Last Chat Trigger Started');
  var attempts = 0;
  var sidebarClicked = false;
  function tryClickLastChat() {
    attempts++;
    if (attempts > 10) {
      console.log('Stopping after 10 attempts');
      if (window.AppBridge) AppBridge.postMessage('continued:');
      return true;
    }
    var targetPath = document.querySelector('path[d^="M8.25 3.5C7.56 3.5"], path[d*="M8.25 3.5"]');
    if (targetPath) {
      console.log('Last chat SVG found!');
      var svgEl = targetPath.closest('svg');
      if (svgEl) {
        var parent1 = svgEl.parentElement;
        if (parent1) {
          var parent2 = parent1.parentElement;
          if (parent2) {
            console.log('Clicking parent of parent of SVG');
            parent2.click();
            var parent3 = parent2.parentElement;
            if (parent3) {
              parent3.click();
              var parent4 = parent3.parentElement;
              if (parent4) {
                parent4.click();
              }
            }
            if (window.AppBridge) AppBridge.postMessage('continued:');
            return true;
          }
        }
      }
      var container = targetPath.closest('a, [role="link"], li');
      if (container) {
        console.log('Clicking fallback container');
        container.click();
        if (window.AppBridge) AppBridge.postMessage('continued:');
        return true;
      }
    }
    if (!sidebarClicked) {
      var sidebarPath = document.querySelector('path[d*="M9.41 10.125a.625.625 0 1 1 0 1.25H1.624"]');
      var sidebarBtn = sidebarPath ? sidebarPath.closest('button, [role="button"]') : null;
      if (!sidebarBtn) {
          sidebarBtn = document.querySelector('button[aria-label*="sidebar"], button[aria-label*="Sidebar"]');
      }
      if (sidebarBtn && sidebarBtn.offsetParent !== null) {
        console.log('Opening sidebar...');
        sidebarBtn.click();
        sidebarClicked = true;
      }
    }
    return false;
  }
  if (!tryClickLastChat()) {
    var observer = new MutationObserver(function(mutations, obs) {
      if (tryClickLastChat()) {
        obs.disconnect();
        clearInterval(fallbackInterval);
      }
    });
    observer.observe(document.body, { childList: true, subtree: true });
    var fallbackInterval = setInterval(tryClickLastChat, 1000);
    setTimeout(function() { 
      observer.disconnect(); 
      clearInterval(fallbackInterval); 
      if (window.AppBridge) AppBridge.postMessage('continued:');
    }, 12000);
  }
})();
