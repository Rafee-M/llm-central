// ==UserScript==
// @name         Example: larger text on duck.ai
// @description  Shows how user scripts work: makes duck.ai's text 10 % larger. Edit or delete it as you like.
// @author       LLM Central
// @license      GPL-3.0-only
// @match        *://*.duck.ai/*
// @grant        GM_addStyle
// ==/UserScript==

// GM_addStyle adds CSS (style rules) to the page. 110 % makes the page's base text size 10 % larger;
// sites that size their text in "rem" units follow it.
GM_addStyle('html { font-size: 110% !important; }');
