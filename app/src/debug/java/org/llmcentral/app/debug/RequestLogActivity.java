package org.llmcentral.app.debug;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.util.TypedValue;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.llmcentral.app.R;
import org.llmcentral.app.theme.Scheme;
import org.llmcentral.app.theme.ThemeManager;
import org.llmcentral.app.ui.ProviderBadge;

/**
 * <b>Debug builds only</b> (this file is in {@code app/src/debug/}, which release builds never compile): the
 * "LLM Central requests" screen. It shows what {@link RequestLog} has counted since the app started (or since
 * Clear) and hands it over as text: Copy (clipboard) or Share (any app, e.g. a chat or Files).
 *
 * <p>It has its own launcher icon in debug installs. It runs in the same app process as the chat screens, so it
 * sees the same log; if Android ended the process in between, the log starts empty.</p>
 *
 * <p>Plain framework views, no layout file and no UI library (CLAUDE.md §5); colours from the app's theme.</p>
 */
public final class RequestLogActivity extends Activity {

    /** Lines shown on screen; Copy and Share always hand over everything. */
    private static final int PREVIEW_LINES = 200;

    private TextView summary;
    private TextView preview;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Scheme scheme = ThemeManager.scheme(this);
        int pad = ProviderBadge.dp(this, 16);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);
        root.setBackgroundColor(scheme.get(Scheme.SURFACE));
        root.setFitsSystemWindows(true); // stay clear of the status and navigation bars

        TextView help = text(scheme.get(Scheme.ON_SURFACE_VARIANT), 13);
        help.setText(R.string.request_log_help);
        root.addView(help);

        summary = text(scheme.get(Scheme.ON_SURFACE), 15);
        summary.setPadding(0, pad / 2, 0, pad / 2);
        root.addView(summary);

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.addView(button(R.string.request_log_copy, this::copy));
        buttons.addView(button(R.string.request_log_share, this::share));
        buttons.addView(button(R.string.request_log_clear, this::clear));
        buttons.addView(button(R.string.request_log_refresh, this::refresh));
        root.addView(buttons);

        // The table is wide: it scrolls both ways, in a fixed-width font so the columns are readable.
        preview = text(scheme.get(Scheme.ON_SURFACE), 11);
        preview.setTypeface(Typeface.MONOSPACE);
        preview.setTextIsSelectable(true);
        HorizontalScrollView wide = new HorizontalScrollView(this);
        wide.addView(preview);
        ScrollView tall = new ScrollView(this);
        tall.addView(wide);
        root.addView(tall, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        setContentView(root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh(); // e.g. after using the app in between
    }

    private void refresh() {
        long now = System.currentTimeMillis();
        RequestLog.Summary s = RequestLog.get().summary(now);
        summary.setText(getString(R.string.request_log_summary, s.seconds, s.accounts, s.rows, s.requests, s.dropped));
        String[] lines = export().split("\n");
        StringBuilder shown = new StringBuilder();
        for (int i = 0; i < Math.min(lines.length, PREVIEW_LINES); i++) {
            shown.append(lines[i]).append('\n');
        }
        if (lines.length > PREVIEW_LINES) {
            shown.append(getString(R.string.request_log_more, lines.length - PREVIEW_LINES));
        }
        preview.setText(shown);
    }

    private String export() {
        return RequestLog.get().export(RequestLogHooks.about(this), System.currentTimeMillis());
    }

    private void copy() {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.request_log_title), export()));
        Toast.makeText(this, R.string.request_log_copied, Toast.LENGTH_SHORT).show();
    }

    private void share() {
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("text/plain");
        send.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.request_log_title));
        send.putExtra(Intent.EXTRA_TEXT, export());
        startActivity(Intent.createChooser(send, getString(R.string.request_log_share)));
    }

    private void clear() {
        RequestLog.get().clear(System.currentTimeMillis());
        Toast.makeText(this, R.string.request_log_cleared, Toast.LENGTH_SHORT).show();
        refresh();
    }

    private TextView text(int color, int sizeSp) {
        TextView view = new TextView(this);
        view.setTextColor(color);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp);
        return view;
    }

    private Button button(int label, Runnable action) {
        Button button = new Button(this);
        button.setText(label);
        button.setOnClickListener(v -> action.run());
        return button;
    }
}
