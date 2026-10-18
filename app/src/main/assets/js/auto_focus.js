// auto_focus.js — injected into the chat page by the app (loaded via web/Scripts.java).
//
// "Auto-focus keyboard" setting: waits until duck.ai has loaded, presses duck.ai's
// new-chat shortcut (Ctrl+Shift+O), then focuses the message box and asks the app to open the keyboard.
// Does nothing while a voice chat is active.
//
// Moved unchanged from a Java string in MainActivity (Phase 0.4); only line breaks were added.
// Round 5: talks to the app through window.AppBridge.postMessage("type:payload") (see web/DuckBridge.java)
// instead of the old window.Android object.

(function() {
  if (window.isVoiceChatActive) {
    console.log('Voice chat active, suppressing auto focus');
    return;
  }
  // The app injects this script up to three times for one page load (onPageFinished, progress 100, app opened):
  // only the first run of a load does anything, so the new-chat shortcut and the keyboard fire once, not three
  // times (VERIFICATION.md R4-23). The flag means "a run is in progress", not "already ran": it is cleared when the
  // run ends, so opening the app again later still starts a new chat, as the setting promises.
  if (window.__llmcAutoFocusRunning) return;
  window.__llmcAutoFocusRunning = true;
  function finished() { window.__llmcAutoFocusRunning = false; }
  console.log('Auto Focus: waiting for page progress to complete before triggering Ctrl+Shift+O');
  function sendShortcut() {
    if (window.isVoiceChatActive) return;
    console.log('Dispatching Ctrl+Shift+O shortcut');
    var targets = [window, document, document.body, document.documentElement, document.activeElement];
    var events = ['keydown', 'keypress', 'keyup'];
    var configs = [
      { key: 'O', code: 'KeyO', keyCode: 79, which: 79, ctrlKey: true, shiftKey: true, bubbles: true, cancelable: true },
      { key: 'o', code: 'KeyO', keyCode: 79, which: 79, ctrlKey: true, shiftKey: true, bubbles: true, cancelable: true },
      { key: 'O', code: 'KeyO', keyCode: 79, which: 79, metaKey: true, shiftKey: true, bubbles: true, cancelable: true },
      { key: 'o', code: 'KeyO', keyCode: 79, which: 79, metaKey: true, shiftKey: true, bubbles: true, cancelable: true }
    ];
    for (var i = 0; i < targets.length; i++) {
      var t = targets[i];
      if (!t) continue;
      for (var c = 0; c < configs.length; c++) {
        for (var e = 0; e < events.length; e++) {
          try {
            t.dispatchEvent(new KeyboardEvent(events[e], configs[c]));
          } catch (err) {}
        }
      }
    }
    if (window.AppBridge) AppBridge.postMessage('shortcut:');
  }
  function isPageReady() {
    var app = document.getElementById('app');
    var loader = document.getElementById('app-loader');
    var loaderHidden = !loader || loader.classList.contains('loader-hidden') || loader.style.display === 'none' || loader.style.opacity === '0';
    var hasContent = app && app.children && app.children.length > 0;
    return (hasContent && loaderHidden) || document.querySelector('textarea, [contenteditable="true"]') !== null;
  }
  function focusAndOpenKeyboard() {
    if (window.isVoiceChatActive) return;
    var input = document.querySelector('textarea, [contenteditable="true"], input[type="text"]');
    if (input) {
      input.focus();
      input.click();
    }
    if (window.AppBridge) AppBridge.postMessage('keyboard:');
  }
  var attempts = 0;
  function checkAndFire() {
    if (window.isVoiceChatActive) { finished(); return true; }
    attempts++;
    if (isPageReady() || attempts >= 25) {
      if (window.isVoiceChatActive) { finished(); return true; }
      console.log('Page ready (attempt ' + attempts + '), firing Ctrl+Shift+O');
      sendShortcut();
      setTimeout(function() { if (!window.isVoiceChatActive) sendShortcut(); }, 400);
      setTimeout(function() { if (!window.isVoiceChatActive) focusAndOpenKeyboard(); }, 600);
      setTimeout(function() { if (!window.isVoiceChatActive) focusAndOpenKeyboard(); }, 1200);
      setTimeout(finished, 1300); // after the run's last step
      return true;
    }
    return false;
  }
  if (!checkAndFire()) {
    var interval = setInterval(function() {
      if (checkAndFire()) {
        clearInterval(interval);
      }
    }, 400);
  }
})();
