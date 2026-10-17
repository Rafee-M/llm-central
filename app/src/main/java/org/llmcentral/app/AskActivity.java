package org.llmcentral.app;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

public class AskActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        handleIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleIntent(intent);
    }

    private void handleIntent(Intent intent) {
        if (intent == null) {
            finish();
            return;
        }

        String action = intent.getAction();
        String type = intent.getType();

        if (Intent.ACTION_SEND.equals(action)) {
            Uri streamUri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
            if (streamUri != null) {
                // A shared file (document, PDF, image, anything): handed to the chat screen exactly as
                // it is, same content and same name. The chat screen offers it when the page's 📎
                // button asks for a file. (Until the round 6 follow-up, documents were converted on
                // the phone and long PDFs were split into 15-page picture copies.)
                // FLAG_GRANT_READ_URI_PERMISSION passes on our permission to read the other app's
                // file: without it, the chat screen couldn't open it once this screen closes.
                Intent chatIntent = new Intent(this, ChatActivity.class);
                chatIntent.setAction(Intent.ACTION_SEND);
                chatIntent.setType(type != null ? type : "*/*");
                chatIntent.putExtra(Intent.EXTRA_STREAM, streamUri);
                chatIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                chatIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(chatIntent);
                finish();
                return;
            }
        }

        if (Intent.ACTION_SEND_MULTIPLE.equals(action)) {
            // Several files at once (R4-12): forwarded exactly like one file. Android copies the list into the
            // intent's ClipData with the read permission when it starts the chat screen (AOSP
            // Intent.migrateExtraStreamToClipData, which handles ACTION_SEND_MULTIPLE too).
            java.util.ArrayList<Uri> streams = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
            if (streams != null && !streams.isEmpty()) {
                Intent chatIntent = new Intent(this, ChatActivity.class);
                chatIntent.setAction(Intent.ACTION_SEND_MULTIPLE);
                chatIntent.setType(type != null ? type : "*/*");
                chatIntent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, streams);
                chatIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                chatIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(chatIntent);
                finish();
                return;
            }
        }

        String sharedText = null;

        if (Intent.ACTION_SEND.equals(action) && "text/plain".equals(type)) {
            sharedText = intent.getStringExtra(Intent.EXTRA_TEXT);
        } else if (Intent.ACTION_PROCESS_TEXT.equals(action)) {
            CharSequence text = intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT);
            if (text != null) {
                sharedText = text.toString();
            }
        }

        if (sharedText != null) {
            launchChatWithText(sharedText);
        }
        finish();
    }

    /**
     * Hands the text to the chat screen as ordinary shared text. The chat screen decides which
     * account gets it (Settings → "Share & Ask AI account", see account/DefaultAccounts) and adds the
     * user's "text added to prompts". Until round 5 this built a duck.ai address here, so shared text
     * could only ever go to duck.ai.
     */
    private void launchChatWithText(String promptText) {
        if (promptText == null || promptText.trim().isEmpty()) return;
        Intent chatIntent = new Intent(this, ChatActivity.class);
        chatIntent.setAction(Intent.ACTION_SEND);
        chatIntent.setType("text/plain");
        chatIntent.putExtra(Intent.EXTRA_TEXT, promptText);
        chatIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(chatIntent);
    }
}
