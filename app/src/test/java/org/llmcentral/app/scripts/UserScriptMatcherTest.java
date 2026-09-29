package org.llmcentral.app.scripts;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * JVM unit tests for {@link UserScriptMatcher}. Run with: ./gradlew test
 * (they run on the computer in seconds; no phone or emulator needed).
 */
public class UserScriptMatcherTest {

    private static final String DUCK_DEFAULT = "*://*.duck.ai/*, *://*.duckduckgo.com/*";

    @Test
    public void wildcardSubdomainAlsoMatchesTheBareDomain() {
        // The old code only matched these because of the "contains duck.ai" shortcut.
        assertTrue(UserScriptMatcher.matchesAny("https://duck.ai/", DUCK_DEFAULT));
        assertTrue(UserScriptMatcher.matchesAny("https://duck.ai/chat?q=hello", DUCK_DEFAULT));
        assertTrue(UserScriptMatcher.matchesAny("https://www.duckduckgo.com/?q=x&ia=chat", DUCK_DEFAULT));
    }

    @Test
    public void scriptForOneProviderDoesNotRunOnAnother() {
        // The actual bug: the old code returned true for any duck.ai URL, whatever the pattern said.
        assertFalse(UserScriptMatcher.matchesAny("https://duck.ai/chat", "https://chatgpt.com/*"));
        assertFalse(UserScriptMatcher.matchesAny("https://chatgpt.com/c/123", DUCK_DEFAULT));
        assertTrue(UserScriptMatcher.matchesAny("https://chatgpt.com/c/123", "https://chatgpt.com/*"));
    }

    @Test
    public void lookalikeDomainsDoNotMatch() {
        assertFalse(UserScriptMatcher.matchesAny("https://evilduck.ai/", DUCK_DEFAULT));
        assertFalse(UserScriptMatcher.matchesAny("https://duck.ai.evil.com/", DUCK_DEFAULT));
        assertFalse(UserScriptMatcher.matchesAny("https://evil.com/?duck.ai", DUCK_DEFAULT));
    }

    @Test
    public void schemeRules() {
        assertTrue(UserScriptMatcher.matchesAny("http://duck.ai/", "*://duck.ai/*"));
        assertFalse(UserScriptMatcher.matchesAny("http://claude.ai/", "https://claude.ai/*"));
        assertFalse(UserScriptMatcher.matchesAny("file:///android_asset/settings.html", "*://*/*"));
    }

    @Test
    public void pathRulesTreatDotsAndQuestionMarksLiterally() {
        assertTrue(UserScriptMatcher.matchesAny("https://claude.ai/new?q=a", "https://claude.ai/new?q=*"));
        assertFalse(UserScriptMatcher.matchesAny("https://claude.ai/newXq=a", "https://claude.ai/new?q=*"));
        assertTrue(UserScriptMatcher.matchesAny("https://claude.ai/chat/1", "https://claude.ai/chat/*"));
        assertFalse(UserScriptMatcher.matchesAny("https://claude.ai/settings", "https://claude.ai/chat/*"));
        // A pattern without a path means "any path on that host".
        assertTrue(UserScriptMatcher.matchesAny("https://claude.ai/anything", "https://claude.ai"));
    }

    @Test
    public void specialAndEmptyPatterns() {
        assertTrue(UserScriptMatcher.matchesAny("https://chat.deepseek.com/", "*"));
        assertTrue(UserScriptMatcher.matchesAny("https://chat.deepseek.com/", "<all_urls>"));
        assertTrue(UserScriptMatcher.matchesAny("https://chat.deepseek.com/", ""));   // kept from duckAssist
        assertTrue(UserScriptMatcher.matchesAny("https://chat.deepseek.com/", null));
        assertFalse(UserScriptMatcher.matchesAny(null, DUCK_DEFAULT));
        assertFalse(UserScriptMatcher.matchesAny("https://duck.ai/", " , "));
    }

    @Test
    public void regexCharactersInPatternsDoNotCrash() {
        // The old code passed these straight into a regex and could throw PatternSyntaxException.
        assertFalse(UserScriptMatcher.matchesAny("https://duck.ai/", "https://duck.ai/(unclosed*"));
        assertTrue(UserScriptMatcher.matchesAny("https://duck.ai/a+b", "https://duck.ai/a+b"));
    }

    @Test
    public void hostComparisonIsCaseInsensitive() {
        assertTrue(UserScriptMatcher.matchesAny("https://Chat.DeepSeek.com/x", "*://*.deepseek.com/*"));
    }
}
