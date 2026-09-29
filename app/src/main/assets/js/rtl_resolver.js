// rtl_resolver.js — injected into the chat page by the app (loaded via web/Scripts.java).
//
// "RTL resolver" setting: detects right-to-left text (Persian/Arabic script) per paragraph and
// sets the correct text direction, keeping code blocks and formulas left-to-right. Keeps watching the
// page for new messages. Note: loads the Vazirmatn font from Google Fonts (PROJECT.md P9).
//
// Moved unchanged from a Java string in MainActivity (Phase 0.4); only line breaks were added.

(function() {
    if (window.__aiRtlResolverInjected) {
        if (typeof window.__aiRtlFix === 'function') window.__aiRtlFix();
        return;
    }
    window.__aiRtlResolverInjected = true;
    console.log('[ai-rtl-resolver] Initializing DuckAI RTL resolver');
    function injectStyles() {
        if (document.getElementById('ai-rtl-resolver-style')) return;
        var style = document.createElement('style');
        style.id = 'ai-rtl-resolver-style';
        style.textContent = "@import url('https://fonts.googleapis.com/css2?family=Vazirmatn:wght@400;700&display=swap'); " +
            ".rtl, [dir=\"rtl\"], .vazir, .user-message-bubble-color { font-family: 'Vazirmatn', 'Segoe UI', Tahoma, Arial, sans-serif !important; } " +
            "[dir=\"rtl\"] { direction: rtl !important; text-align: right !important; } " +
            "[dir=\"ltr\"] { direction: ltr !important; text-align: left !important; } " +
            "[dir=\"rtl\"] ul, [dir=\"rtl\"] ol { padding-right: 1.5em !important; padding-left: 0 !important; } " +
            "[dir=\"rtl\"] blockquote { border-right: 4px solid #ccc !important; border-left: none !important; padding-right: 12px !important; padding-left: 0 !important; }";
        (document.head || document.documentElement).appendChild(style);
    }
    var PERSIAN_WEIGHT_PERCENTAGE = 30;
    var PERSIAN_SCRIPT_REGEX = /[\u0600-\u06FF\u0750-\u077F\u08A0-\u08FF\uFB50-\uFDFF\uFE70-\uFEFF]/;
    function isEmojiLike(char) {
        try { return /[\p{Emoji}\p{Emoji_Presentation}]/u.test(char); } catch(e) { return false; }
    }
    function isIgnorableChar(char) {
        return /[\d\s\u200E\u200F\u200B#@$%^&*()\-+=_{}[\]\\|:;"'<>,.?/~`!\u00AB\u00BB]/.test(char);
    }
    function isRtlScriptChar(char) {
        return PERSIAN_SCRIPT_REGEX.test(char);
    }
    var graphemeSegmenter;
    function segmentGraphemes(text) {
        if (typeof Intl !== 'undefined' && Intl.Segmenter) {
            graphemeSegmenter = graphemeSegmenter || new Intl.Segmenter('en', { granularity: 'grapheme' });
            return graphemeSegmenter.segment(text);
        }
        return Array.from(text).map(function(ch) { return { segment: ch }; });
    }
    function detectParagraphDirection(text) {
        var trimmed = text.trim();
        if (trimmed.length === 0) return 'ltr';
        var rtlCount = 0;
        var ltrCount = 0;
        var sawMeaningfulChar = false;
        for (var seg of segmentGraphemes(trimmed)) {
            var char = seg.segment;
            if (isEmojiLike(char) || isIgnorableChar(char)) continue;
            var isRtl = isRtlScriptChar(char);
            if (!sawMeaningfulChar) {
                if (isRtl) return 'rtl';
                sawMeaningfulChar = true;
            }
            if (isRtl) rtlCount++;
            else ltrCount++;
        }
        var totalRelevant = rtlCount + ltrCount;
        if (totalRelevant === 0) return 'ltr';
        var rtlPercentage = (rtlCount / totalRelevant) * 100;
        return rtlPercentage > PERSIAN_WEIGHT_PERCENTAGE ? 'rtl' : 'ltr';
    }
    function setElementDirection(element, direction) {
        if (!element || element.getAttribute('dir') === direction) return;
        element.setAttribute('dir', direction);
    }
    var directionCache = new WeakMap();
    function getElementText(element) {
        if (element instanceof HTMLTextAreaElement || element.tagName === 'TEXTAREA' || element.tagName === 'INPUT') {
            return element.value;
        }
        return element.textContent || '';
    }
    var LTR_ONLY_SELECTOR = 'pre, code, .code-block, [id^="heading-"][id*="assistant-message"], .katex, .katex-html';
    var APPLY_DIRECTION_SELECTOR = '[name="user-prompt"], [data-testid="user-message"] p, [id*="assistant-message"]:not([id^="heading-"]) p';
    var TABLES_SELECTOR = 'table';
    function applyDetectedDirection(elements, getText) {
        for (var i = 0; i < elements.length; i++) {
            var element = elements[i];
            var text = getText(element);
            var cached = directionCache.get(element);
            var direction;
            if (cached !== undefined && cached.text === text) {
                direction = cached.direction;
            } else {
                direction = detectParagraphDirection(text);
                directionCache.set(element, { text: text, direction: direction });
            }
            setElementDirection(element, direction);
        }
    }
    function applyTableDirection(tables) {
        for (var t = 0; t < tables.length; t++) {
            var table = tables[t];
            var text = '';
            var cells = table.querySelectorAll('th, td');
            for (var c = 0; c < cells.length; c++) {
                text += getElementText(cells[c]);
            }
            var tableDir = detectParagraphDirection(text);
            if (tableDir === 'rtl') {
                for (var c = 0; c < cells.length; c++) {
                    setElementDirection(cells[c], tableDir);
                    cells[c].style.textAlign = 'right';
                }
            }
        }
    }
    function forceLtrDirection(elements) {
        for (var i = 0; i < elements.length; i++) {
            setElementDirection(elements[i], 'ltr');
        }
    }
    function fixDuckaiDirection() {
        applyDetectedDirection(document.querySelectorAll(APPLY_DIRECTION_SELECTOR), getElementText);
        applyTableDirection(document.querySelectorAll(TABLES_SELECTOR));
        forceLtrDirection(document.querySelectorAll(LTR_ONLY_SELECTOR));
    }
    window.__aiRtlFix = fixDuckaiDirection;
    document.addEventListener('input', function(e) {
        if (e.target && (e.target.matches && e.target.matches('[name="user-prompt"]') || e.target.tagName === 'TEXTAREA')) {
            var dir = detectParagraphDirection(e.target.value);
            setElementDirection(e.target, dir);
        }
    }, true);
    var mutationCallbacks = [fixDuckaiDirection];
    var bodyObserver;
    var pendingFrame;
    function flushMutationCallbacks() {
        pendingFrame = undefined;
        for (var i = 0; i < mutationCallbacks.length; i++) {
            try { mutationCallbacks[i](); } catch (err) { console.error('[ai-rtl-resolver] callback error', err); }
        }
        if (bodyObserver) bodyObserver.takeRecords();
    }
    function scheduleFlush() {
        if (pendingFrame !== undefined) return;
        pendingFrame = requestAnimationFrame(flushMutationCallbacks);
    }
    function observeBodyMutations() {
        var body = document.body || document.documentElement;
        if (!body) return;
        bodyObserver = new MutationObserver(scheduleFlush);
        bodyObserver.observe(body, { childList: true, subtree: true });
        window.__aiRtlObserver = bodyObserver;
    }
    injectStyles();
    fixDuckaiDirection();
    observeBodyMutations();
    if (!bodyObserver && document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', function() {
            injectStyles();
            fixDuckaiDirection();
            observeBodyMutations();
        });
    }
})();
