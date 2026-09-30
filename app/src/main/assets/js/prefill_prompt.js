// prefill_prompt.js — types a prompt into a chat site's message box (ChatGPT, Claude, DeepSeek, Z.ai).
// Run by web/PromptFiller.java every half second after a page loads, until it reports success.
// Used for shared text, "Ask AI" and converted documents when the site has no reliable
// "open a chat with this text" address (see provider/PromptPrefill.java). It never presses Send.
//
// This file is one function expression; the app appends ("the prompt") using JSONObject.quote(),
// so the text can't break out of the string. It returns (and evaluateJavascript hands back to Java):
//   "filled" = the text was typed in;
//   "busy"   = the box already has text (e.g. the site filled it from the address, or the user is
//              typing): left alone, nothing to do;
//   "none"   = no message box on this page (yet): try again later.
//
// Only a <textarea> or a rich-text box (contenteditable) counts as a message box, never an <input>:
// sign-in pages use <input> fields for e-mail and password, and the prompt must never land there.

(function(text) {
  function visible(el) {
    var r = el.getBoundingClientRect();
    return r.width > 0 && r.height > 0;
  }
  function textOf(el) {
    return el.tagName === 'TEXTAREA' ? el.value : (el.innerText || '');
  }
  var boxes = Array.prototype.filter.call(
      document.querySelectorAll('textarea, [contenteditable="true"], [contenteditable=""]'),
      function(el) { return visible(el) && !el.disabled && !el.readOnly; });
  if (!boxes.length) return 'none';
  // The sites' own ids first (ChatGPT: #prompt-textarea, DeepSeek/Open WebUI: #chat-input), else the
  // last box on the page (the message box sits at the bottom).
  var box = boxes.filter(function(el) { return el.id === 'prompt-textarea' || el.id === 'chat-input'; })[0]
      || boxes[boxes.length - 1];
  if (textOf(box).trim().length) return 'busy';
  box.focus();
  if (box.tagName === 'TEXTAREA') {
    // Sites built with React keep their own copy of the text. Setting the value through the
    // browser's original setter and announcing an "input" event updates that copy too.
    var setter = Object.getOwnPropertyDescriptor(HTMLTextAreaElement.prototype, 'value').set;
    setter.call(box, text);
    box.dispatchEvent(new Event('input', { bubbles: true }));
  } else {
    // Rich-text editors (ProseMirror on ChatGPT/Claude, and others) handle a "paste" well, keeping
    // line breaks. If the editor ignores it, fall back to the browser's "insert text" command.
    var data = new DataTransfer();
    data.setData('text/plain', text);
    var paste = new ClipboardEvent('paste', { clipboardData: data, bubbles: true, cancelable: true });
    box.dispatchEvent(paste);
    if (!paste.defaultPrevented || !textOf(box).trim().length) {
      document.execCommand('insertText', false, text);
    }
  }
  return textOf(box).trim().length ? 'filled' : 'none';
})
