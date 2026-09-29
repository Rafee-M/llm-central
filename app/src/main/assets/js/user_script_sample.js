// ==UserScript==
// @name         Duck.ai Ultra Clean Unified
// @namespace    http://tampermonkey.net/
// @version      8.1
// @description  Unified, clean split-screen with centered items and coordinated floating buttons.
// @author       iJahangard (https://github.com/iJahangard)
// @match        *://*.duck.ai/*
// @match        *://*.duckduckgo.com/*
// @run-at       document-idle
// @grant        GM_addStyle
// ==/UserScript==

(function() {
    'use strict';

    GM_addStyle(`
        /* Main Container */
        .duck-main-box {
            position: relative !important;
            width: 100% !important;
            max-width: 100% !important;
            padding: 0 46px 0 0 !important;
            margin-bottom: -6px !important;
            box-sizing: border-box !important;
        }

        .duck-ta-row {
            width: 100% !important;
            margin: 0 !important;
            padding: 0 !important;
            display: block !important;
        }

        textarea[name="user-prompt"] {
            width: 100% !important;
            min-height: 40px !important;
            max-height: 160px !important;
            border-radius: 18px !important;
            padding: 10px 15px !important;
            margin: 0 !important;
            box-sizing: border-box !important;
            line-height: 1.4 !important;
        }

        /* Floating buttons */
        .duck-fab-btn, .duck-fab-4 {
            position: absolute !important;
            right: 0px !important;
            width: 38px !important;
            height: 38px !important;
            border-radius: 50% !important;
            display: flex !important;
            justify-content: center !important;
            align-items: center !important;
            z-index: 10 !important;
            transition: transform 0.2s ease, filter 0.2s ease !important;
            box-sizing: border-box !important;
        }

        .duck-fab-btn {
            background-color: var(--dynamic-send-bg, #3b82f6) !important;
            color: var(--dynamic-send-color, #ffffff) !important;
        }
        .duck-fab-btn svg { color: #ffffff !important; }
        .duck-fab-btn:hover, .duck-send-btn:hover, .duck-stop-btn:hover {
            transform: scale(0.9) !important;
            filter: brightness(1.2) !important;
        }

        .duck-fab-4 { bottom: 160px !important; padding: 0 !important; justify-content: right !important; }
        .duck-fab-1 { bottom: 120px !important; }
        .duck-fab-2 { bottom: 80px !important; }
        .duck-fab-3 { bottom: 40px !important; }

        .duck-send-btn, .duck-stop-btn {
            position: absolute !important;
            right: 0px !important;
            bottom: -12px !important;
            width: 40px !important;
            height: 40px !important;
            border-radius: 50% !important;
            display: flex !important;
            justify-content: center !important;
            align-items: center !important;
            z-index: 10 !important;
            transition: transform 0.2s ease, filter 0.2s ease !important;
            box-sizing: border-box !important;
            margin: 0 !important;
        }

        .duck-stop-btn { background-color: #E60023 !important; }
        .duck-stop-btn svg { color: #ffffff !important; }

        .duck-send-btn { background-color: #DE5833 !important; }
        .duck-send-btn svg { color: #ffffff !important; }

        [data-testid="duckai-top-toolbar"],
        [data-testid="duckai-top-toolbar"] > div {
            height: 36px !important;
            min-height: 38px !important;
            max-height: 38px !important;
            box-sizing: border-box !important;
            align-items: right !important;
        }

        .TmiyHFYeTH6BRfiFO5eV, .WylRI_oUZA16s0qnjS1p {
            width: 100% !important;
            display: flex !important;
            flex-direction: row !important;
            flex-wrap: nowrap !important;
            align-items: center !important;
            justify-content: center !important;
            text-align: center !important;
            gap: 8px !important;
        }

        .TmiyHFYeTH6BRfiFO5eV > .pjSWjOTlZyJAUFYWwJd7,
        .WylRI_oUZA16s0qnjS1p > .pjSWjOTlZyJAUFYWwJd7,
        .WylRI_oUZA16s0qnjS1p .PLoPNq8ZrjT3PBQhCQ4Q,
        .WylRI_oUZA16s0qnjS1p .sVQf5w9mw0rZOdrXmKsG,
        .WylRI_oUZA16s0qnjS1p [data-testid="feedback-prompt"] {
            background-color: transparent !important;
            width: auto !important;
            margin: 0 !important;
            display: flex !important;
            flex: 0 0 auto !important;
            align-items: center !important;
            justify-content: center !important;
        }

        .TmiyHFYeTH6BRfiFO5eV button, .WylRI_oUZA16s0qnjS1p button {
            float: none !important;
            display: inline-flex !important;
            align-items: center !important;
            justify-content: center !important;
            margin: 0 !important;
        }

        .WylRI_oUZA16s0qnjS1p button i, .WylRI_oUZA16s0qnjS1p button svg {
            display: block !important;
            margin: 0 !important;
            flex-shrink: 0 !important;
        }

        .WylRI_oUZA16s0qnjS1p .sVQf5w9mw0rZOdrXmKsG > .pjSWjOTlZyJAUFYWwJd7 {
            margin: 0 4px !important;
        }

        .DRAot9rDvc5ggNSzK6hY {
            width: 80% !important;
            display: flex !important;
            margin-top: 20px !important;
            align-items: center !important;
            justify-content: center !important;
            text-align: center !important;
        }

        .DRAot9rDvc5ggNSzK6hY > .BB8BdTLAPMS9cAdduzJV {
            background-color: transparent !important;
            display: flex !important;
            flex-direction: row !important;
            flex-wrap: nowrap !important;
            align-items: center !important;
            justify-content: center !important;
            gap: 8px !important;
            width: auto !important;
            margin: 0 auto !important;
        }

        .DRAot9rDvc5ggNSzK6hY button {
            height: 40px !important;
            margin-right: 46px !important;
            display: inline-flex !important;
            flex-direction: row !important;
            align-items: center !important;
            justify-content: center !important;
            flex: 0 0 auto !important;
            white-space: nowrap !important;
            float: none !important;
        }

        .DRAot9rDvc5ggNSzK6hY button svg {
            display: inline-block !important;
            width: 16px !important;
            height: 16px !important;
            margin-right: 8px !important;
            vertical-align: middle !important;
        }
    `);

    function getCommonAncestor(node1, node2) {
        let parents = [];
        let current = node1;
        while (current) {
            parents.push(current);
            current = current.parentElement;
        }
        current = node2;
        while (current) {
            if (parents.includes(current)) return current;
            current = current.parentElement;
        }
        return null;
    }

    function syncSendButtonStyles() {
        const sendBtn = document.querySelector('button[aria-label="Send"]');
        if (sendBtn) {
            const computedStyle = window.getComputedStyle(sendBtn);
            const bgColor = computedStyle.backgroundColor;
            if (bgColor !== 'rgba(0, 0, 0, 0)' && bgColor !== 'transparent') {
                document.documentElement.style.setProperty('--dynamic-send-bg', bgColor);
                const rgb = bgColor.match(/\d+/g);
                if (rgb && rgb.length >= 3) {
                    const r = parseInt(rgb[0]);
                    const g = parseInt(rgb[1]);
                    const b = parseInt(rgb[2]);
                    const yiq = ((r * 299) + (g * 587) + (b * 114)) / 1000;
                    const textColor = yiq >= 128 ? '#000000' : '#ffffff';
                    document.documentElement.style.setProperty('--dynamic-send-color', textColor);
                }
            }
        }
    }

    function optimizeLayout() {
        const textarea = document.querySelector('textarea[name="user-prompt"]');
        const sendBtn = document.querySelector('button[aria-label="Send"]');
        const stopBtn = document.querySelector('button[aria-label="Stop generating"]');
        if (textarea && (sendBtn || stopBtn)) {
            const activeActionBtn = sendBtn || stopBtn;
            const mainBox = getCommonAncestor(textarea, activeActionBtn);
            if (mainBox) {
                mainBox.classList.add('duck-main-box');
                Array.from(mainBox.children).forEach(child => {
                    if (child.contains(textarea)) child.classList.add('duck-ta-row');
                    if (child.contains(activeActionBtn)) child.classList.add('duck-tb-row');
                });
            }
            const mapWrapper = (selector, classNames) => {
                const btn = document.querySelector(selector);
                if (btn && btn.parentElement) {
                    classNames.split(' ').forEach(cls => btn.parentElement.classList.add(cls));
                }
            };
            mapWrapper('button[data-testid="duckai-attach-button"]', 'duck-fab-btn duck-fab-1');
            mapWrapper('button[data-testid="duckai-tools-button"]', 'duck-fab-btn duck-fab-2');
            mapWrapper('button[data-testid="duckai-reasoning-button"]', 'duck-fab-btn duck-fab-3');
            mapWrapper('button[data-testid="model-picker-button"]', 'duck-fab-4');
            mapWrapper('button[aria-label="Send"]', 'duck-send-btn');
            mapWrapper('button[aria-label="Stop generating"]', 'duck-stop-btn');
            syncSendButtonStyles();
        }
    }

    function setupAutoGrow() {
        const textarea = document.querySelector('textarea[name="user-prompt"]');
        if (textarea && !textarea.dataset.autogrow) {
            textarea.dataset.autogrow = "true";
            const resize = () => {
                textarea.style.height = '48px';
                textarea.style.height = Math.min(textarea.scrollHeight, 150) + 'px';
            };
            textarea.addEventListener('input', resize);
            resize();
        }
    }

    const observer = new MutationObserver(() => {
        optimizeLayout();
        setupAutoGrow();
    });
    if (document.body) {
        observer.observe(document.body, { childList: true, subtree: true });
    }
})();
