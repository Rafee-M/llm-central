// Runs ONE of the user's own scripts (Settings → Data & tools → User scripts) in the page.
// This file is a function expression: the app (scripts/UserScripts.invocation) calls it as
//   <this file>("script id", function (GM_addStyle, unsafeWindow) { { …the script's code… } })
// after a page finished loading, for each switched-on script whose sites match the page.
//
// Once per page: WebView reports a page as "finished" again after a jump to a #section (and after
// a repeated navigation), so without the mark below a script would run twice in the same page and
// add its styles, buttons or listeners a second time. A new page starts with no marks.
//
// The marks live in an object without a prototype, so a key like "toString" isn't mistaken for a mark.
// The page itself can see (and could block) them: it can interfere with anything injected into it anyway.
//
// GM_addStyle and unsafeWindow are the two Tampermonkey helpers we offer. GM_addStyle is also put
// on window, for older scripts that call window.GM_addStyle.
(function (id, body) {
    var ran = window.__llmcUserScriptsRun || (window.__llmcUserScriptsRun = Object.create(null));
    if (Object.prototype.hasOwnProperty.call(ran, id)) return;
    ran[id] = true;
    function GM_addStyle(css) {
        var style = document.createElement('style');
        style.textContent = css;
        (document.head || document.documentElement).appendChild(style);
        return style;
    }
    if (typeof window.GM_addStyle !== 'function') window.GM_addStyle = GM_addStyle;
    try {
        body(GM_addStyle, window);
    } catch (err) {
        // A mistake in one script must not stop the page or the other scripts.
        console.error('[LLMCentral] user script "' + id + '" failed:', err);
    }
})
