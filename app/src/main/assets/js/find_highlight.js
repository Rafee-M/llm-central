// find_highlight.js — colours the matches of the app's find bar (ui/FindBar) with the app's accent colour.
// Chromium's find-in-page (WebView.findAllAsync) draws its own yellow/orange highlights; since Chromium/WebView 144 a
// page can restyle them with the CSS pseudo-elements ::search-text (every match) and ::search-text:current (the
// current one). Older WebViews ignore those rules and keep the default colours.
//
// This file is one function expression; the app appends ("the CSS") using JSONObject.quote() to add or update the
// style sheet, or (null) to remove it when the bar closes. The CSS is built by web/FindInPage.highlightCss from
// #RRGGBB colours only.
//
// Why a "constructed" style sheet (document.adoptedStyleSheets) and not a <style> tag: chat sites can forbid inline
// styles with their Content Security Policy, which blocks a <style> tag but doesn't apply to style sheets made through
// the CSSOM (checked in Chromium: tools/page-checks/find-highlight.mjs). The page's own adopted sheets are kept.
// It returns "applied", "removed" or "unsupported" (no constructed style sheets: matches keep the default colours).

(function(css) {
  var KEY = '__llmCentralFindSheet';
  if (!('adoptedStyleSheets' in document) || typeof CSSStyleSheet !== 'function') return 'unsupported';
  var sheet = window[KEY];
  if (css === null) {
    if (sheet) {
      document.adoptedStyleSheets = document.adoptedStyleSheets.filter(function(s) { return s !== sheet; });
    }
    return 'removed';
  }
  if (!sheet) {
    try {
      sheet = new CSSStyleSheet();
    } catch (e) {
      return 'unsupported';
    }
    window[KEY] = sheet;
  }
  sheet.replaceSync(css);
  if (document.adoptedStyleSheets.indexOf(sheet) < 0) {
    document.adoptedStyleSheets = document.adoptedStyleSheets.concat([sheet]);
  }
  return 'applied';
})
