package org.llmcentral.app.ui;

import android.content.Context;
import android.content.res.ColorStateList;
import android.os.Build;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.webkit.WebView;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.llmcentral.app.R;
import org.llmcentral.app.theme.Scheme;
import org.llmcentral.app.web.FindInPage;

/**
 * The app's own "find in page" bar: one line at the bottom of the screen with a search box, the "3/12" count,
 * previous / next arrows and a close button. Opened by the top bar's search button (MainActivity) for every provider
 * that has no site search of its own ({@link FindInPage#usesSiteSearch}).
 *
 * <p><b>How it searches.</b> It doesn't read the page itself: it asks the WebView, whose {@code findAllAsync} is
 * Chromium's own find-in-page (the same engine as Chrome's). Chromium highlights every match, scrolls to the current
 * one and reports "match n of total" to a {@link WebView.FindListener}. It counts in slices of at most 10 ms while the
 * page is idle (Chromium {@code find_task_controller.cc}, {@code kFindTaskTimeAllotment}), so even thousands of
 * matches don't freeze scrolling; the total grows while it counts. Docs:
 * https://developer.android.com/reference/android/webkit/WebView#findAllAsync(java.lang.String)</p>
 *
 * <p><b>Cost when unused: nothing.</b> MainActivity creates this bar the first time it is opened; until then no view,
 * listener or script exists. While closed, the WebView has no find listener and no highlights.</p>
 *
 * <p>One bar per screen (main screen and assistant drawer each have their own). It searches one tab: MainActivity
 * closes it when another account comes on screen, when the tab is unloaded and when the tab's page process dies.</p>
 */
public final class FindBar {

    private final LinearLayout bar;
    private final ImageView icon;
    private final EditText input;
    private final TextView count;
    private final ImageButton previous;
    private final ImageButton next;
    private final ImageButton close;
    private final InputMethodManager keyboard;
    // The tab being searched while the bar is open; null while closed.
    private WebView target;
    // The tab's address when the search last started: a different one (new page or chat) needs a fresh search,
    // because Chromium's "find next" keeps the old page's count.
    private String searchedUrl;

    private final WebView.FindListener listener = this::onFindResult;

    /**
     * Builds the bar (hidden) and puts it directly under the tab container. Both layouts (activity_main and the
     * drawer's activity_chat) hold the tab container in a vertical LinearLayout, so the bar takes a line of the page's
     * space instead of covering it; and that layout shrinks above the keyboard, so the bar stays visible while typing.
     *
     * @param tabContainer the view that holds the tabs' WebViews
     */
    public FindBar(ViewGroup tabContainer, Scheme scheme) {
        Context context = tabContainer.getContext();
        keyboard = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);

        bar = new LinearLayout(context);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setMinimumHeight(ProviderBadge.dp(context, 56));
        bar.setPadding(ProviderBadge.dp(context, 8), 0, ProviderBadge.dp(context, 4), 0);
        bar.setVisibility(View.GONE);

        // The search box: a rounded "pill" (Material 3 search bar style) with a magnifier in front.
        LinearLayout pill = new LinearLayout(context);
        pill.setOrientation(LinearLayout.HORIZONTAL);
        pill.setGravity(Gravity.CENTER_VERTICAL);
        pill.setPadding(ProviderBadge.dp(context, 12), 0, ProviderBadge.dp(context, 12), 0);
        icon = new ImageView(context);
        icon.setImageResource(R.drawable.ic_search);
        icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        pill.addView(icon, new LinearLayout.LayoutParams(ProviderBadge.dp(context, 20), ProviderBadge.dp(context, 20)));

        input = new EditText(context);
        input.setBackground(null); // no underline: the pill is the box
        input.setSingleLine(true);
        input.setHint(R.string.find_in_page);
        input.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        // No suggestions/autocorrect: a search word must stay exactly as typed. NO_EXTRACT_UI keeps the page visible
        // in landscape (otherwise the keyboard opens a full-screen text box over everything).
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        input.setImeOptions(EditorInfo.IME_ACTION_SEARCH | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        input.setPadding(ProviderBadge.dp(context, 8), 0, 0, 0);
        input.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                search(s.toString()); // Chromium cancels the previous search itself (FindHelper::FindAllAsync)
            }
        });
        // The keyboard's search key = next match. A hardware keyboard's Enter = next, Shift+Enter = previous (the
        // same keys as in Chrome). A hardware Enter arrives twice (down and up): act on "down", swallow both.
        input.setOnEditorActionListener((v, actionId, event) -> {
            if (event == null) {
                step(true);
            } else if (event.getAction() == KeyEvent.ACTION_DOWN) {
                step(!event.isShiftPressed());
            }
            return true;
        });
        input.setOnKeyListener((v, keyCode, event) -> {
            if (keyCode == KeyEvent.KEYCODE_ESCAPE && event.getAction() == KeyEvent.ACTION_UP) {
                close();
                return true;
            }
            return false;
        });
        pill.addView(input, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        LinearLayout.LayoutParams pillSize = new LinearLayout.LayoutParams(0, ProviderBadge.dp(context, 40), 1f);
        bar.addView(pill, pillSize);

        // "3/12": a polite live region, so TalkBack reads the new count without moving focus.
        count = new TextView(context);
        count.setGravity(Gravity.CENTER);
        count.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        count.setMinWidth(ProviderBadge.dp(context, 48));
        count.setPadding(ProviderBadge.dp(context, 8), 0, ProviderBadge.dp(context, 4), 0);
        count.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        bar.addView(count, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        previous = button(context, R.drawable.ic_keyboard_arrow_up, R.string.find_previous, v -> step(false));
        next = button(context, R.drawable.ic_keyboard_arrow_down, R.string.find_next, v -> step(true));
        close = button(context, R.drawable.ic_close, R.string.find_close, v -> close());
        int size = ProviderBadge.dp(context, 48);
        bar.addView(previous, new LinearLayout.LayoutParams(size, size));
        bar.addView(next, new LinearLayout.LayoutParams(size, size));
        bar.addView(close, new LinearLayout.LayoutParams(size, size));
        setArrowsEnabled(false);
        restyle(scheme);

        ViewGroup column = (ViewGroup) tabContainer.getParent();
        column.addView(bar, column.indexOfChild(tabContainer) + 1,
                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    /** @return true while the bar is on screen. */
    public boolean isOpen() {
        return bar.getVisibility() == View.VISIBLE;
    }

    /**
     * Shows the bar for this tab and puts the cursor in the box (keyboard open). Opening it again (or for another
     * tab) searches the box's text again, so the count also covers text the page added since.
     */
    public void open(WebView tab) {
        if (target != tab) {
            detach();
            target = tab;
            tab.setFindListener(listener);
        }
        bar.setVisibility(View.VISIBLE);
        input.requestFocus();
        input.selectAll(); // typing replaces the last search word, like Chrome
        // The keyboard can only open once the box is focused and laid out: wait for the next frame.
        input.post(() -> {
            if (isOpen()) {
                keyboard.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT);
            }
        });
        search(input.getText().toString());
    }

    /** Hides the bar and removes the highlights. The search word stays for next time. */
    public void close() {
        detach();
        if (isOpen()) {
            keyboard.hideSoftInputFromWindow(input.getWindowToken(), 0);
            bar.setVisibility(View.GONE);
        }
    }

    /** Closes the bar if it searches this tab (the tab is being unloaded or replaced). */
    public void closeIfSearching(WebView tab) {
        if (target != null && target == tab) {
            close();
        }
    }

    /** Closes the bar if it searches any tab other than this one (another account came on screen). */
    public void closeUnlessSearching(WebView tab) {
        if (target != null && target != tab) {
            close();
        }
    }

    /** Colours the bar with the current theme (also called when the theme changes). */
    public void restyle(Scheme scheme) {
        Context context = bar.getContext();
        bar.setBackgroundColor(scheme.get(Scheme.SURFACE));
        ((View) input.getParent()).setBackground(NativeTheme.pill(scheme.get(Scheme.SURFACE_CONTAINER_HIGH),
                ProviderBadge.dp(context, 20)));
        icon.setImageTintList(ColorStateList.valueOf(scheme.get(Scheme.ON_SURFACE_VARIANT)));
        input.setTextColor(scheme.get(Scheme.ON_SURFACE));
        input.setHintTextColor(scheme.get(Scheme.ON_SURFACE_VARIANT));
        input.setHighlightColor(scheme.get(Scheme.PRIMARY_CONTAINER)); // selected text in the box
        count.setTextColor(scheme.get(Scheme.ON_SURFACE_VARIANT));
        ColorStateList iconColour = ColorStateList.valueOf(scheme.get(Scheme.ON_SURFACE_VARIANT));
        previous.setImageTintList(iconColour);
        next.setImageTintList(iconColour);
        close.setImageTintList(iconColour);
    }

    /** Stops searching the tab: no listener, no highlights. */
    private void detach() {
        if (target != null) {
            target.clearMatches();
            target.setFindListener(null);
            target = null;
        }
        searchedUrl = null;
        count.setText("");
        setArrowsEnabled(false);
    }

    private void search(String text) {
        if (target == null) {
            return;
        }
        if (text.isEmpty()) {
            target.clearMatches();
            count.setText("");
            setArrowsEnabled(false);
            return;
        }
        searchedUrl = target.getUrl();
        target.findAllAsync(text);
    }

    /** Moves to the next (forward) or previous match; on a new page, searches it first. */
    private void step(boolean forward) {
        if (target == null || input.getText().length() == 0) {
            return;
        }
        String url = target.getUrl();
        if (url == null ? searchedUrl != null : !url.equals(searchedUrl)) {
            search(input.getText().toString()); // e.g. a new chat opened since: count it again
            return;
        }
        target.findNext(forward);
    }

    /** Chromium reports the count on the UI thread, several times while counting and once more when done. */
    private void onFindResult(int activeMatchOrdinal, int numberOfMatches, boolean isDoneCounting) {
        if (target == null) {
            return; // a late report after the bar was closed
        }
        count.setText(FindInPage.countText(activeMatchOrdinal, numberOfMatches, isDoneCounting));
        setArrowsEnabled(numberOfMatches > 0);
    }

    private void setArrowsEnabled(boolean enabled) {
        for (ImageButton arrow : new ImageButton[] { previous, next }) {
            arrow.setEnabled(enabled);
            arrow.setAlpha(enabled ? 1f : 0.38f); // Material 3's disabled opacity
        }
    }

    /** A 48 × 48dp icon button (Android's minimum touch size) with a round ripple; a long press shows its name. */
    private static ImageButton button(Context context, int iconRes, int labelRes, View.OnClickListener action) {
        ImageButton button = new ImageButton(context);
        button.setImageResource(iconRes);
        button.setScaleType(ImageView.ScaleType.CENTER);
        TypedValue ripple = new TypedValue();
        context.getTheme().resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, ripple, true);
        button.setBackgroundResource(ripple.resourceId);
        String label = context.getString(labelRes);
        button.setContentDescription(label);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            button.setTooltipText(label);
        }
        button.setOnClickListener(action);
        return button;
    }
}
