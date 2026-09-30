package org.llmcentral.app;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

public class AskActivity extends Activity {
    private static final String TAG = "duckAssistAsk";

    private DocConverter.ProgressDialogController progressDialog;

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
                // 1. Fallback for native images (no conversion needed)
                if (type != null && type.startsWith("image/")) {
                    Intent chatIntent = new Intent(this, ChatActivity.class);
                    chatIntent.setAction(Intent.ACTION_SEND);
                    chatIntent.setType(type);
                    chatIntent.putExtra(Intent.EXTRA_STREAM, streamUri);
                    chatIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    chatIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(chatIntent);
                    finish();
                    return;
                }

                // 2. Native PDF check (check if exceeds 15 pages and split if needed)
                String ext = DocConverter.getFileExtension(this, streamUri, type);
                if ("pdf".equalsIgnoreCase(ext) || "application/pdf".equals(type) || (type != null && type.contains("pdf"))) {
                    String filename = DocConverter.getFileName(this, streamUri);
                    progressDialog = DocConverter.showProgressDialog(this, "Checking " + filename + "...", this::finish);
                    DocConverter.getInstance(this).processNativePdf(streamUri, new DocConverter.DocumentProcessingCallback() {
                        @Override
                        public void onStatusUpdate(String status) {
                            if (progressDialog != null) {
                                progressDialog.setMessage(status);
                            }
                        }

                        @Override
                        public void onTextReady(String text) {}

                        @Override
                        public void onPdfReady(List<Uri> pdfUris, String originalName) {
                            if (progressDialog != null) {
                                progressDialog.dismiss();
                            }
                            if (pdfUris == null || pdfUris.isEmpty()) {
                                finish();
                                return;
                            }
                            Intent chatIntent = new Intent(AskActivity.this, ChatActivity.class);
                            if (pdfUris.size() == 1) {
                                chatIntent.setAction(Intent.ACTION_SEND);
                                chatIntent.putExtra(Intent.EXTRA_STREAM, pdfUris.get(0));
                            } else {
                                chatIntent.setAction(Intent.ACTION_SEND_MULTIPLE);
                                chatIntent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, new ArrayList<>(pdfUris));
                            }
                            chatIntent.setType("application/pdf");
                            chatIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                            chatIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            startActivity(chatIntent);
                            finish();
                        }

                        @Override
                        public void onError(String errorMessage) {
                            if (progressDialog != null) {
                                progressDialog.dismiss();
                            }
                            Log.e(TAG, "Native PDF processing failed: " + errorMessage);
                            Toast.makeText(AskActivity.this, "Failed: " + errorMessage, Toast.LENGTH_LONG).show();
                            finish();
                        }
                    });
                    return;
                }

                // 3. Process convertible documents or plain text files
                if (DocConverter.isSupportedDoc(this, streamUri, type) || DocConverter.isPlainTextDoc(this, streamUri, type)) {
                    String filename = DocConverter.getFileName(this, streamUri);
                    progressDialog = DocConverter.showProgressDialog(this, "Processing " + filename + "...", this::finish);
                    DocConverter.getInstance(this).processDocument(streamUri, type, new DocConverter.DocumentProcessingCallback() {
                        @Override
                        public void onStatusUpdate(String status) {
                            if (progressDialog != null) {
                                progressDialog.setMessage(status);
                            }
                        }

                        @Override
                        public void onTextReady(String text) {
                            if (progressDialog != null) {
                                progressDialog.dismiss();
                            }
                            launchChatWithText(text);
                            finish();
                        }

                        @Override
                        public void onPdfReady(List<Uri> pdfUris, String originalName) {
                            if (progressDialog != null) {
                                progressDialog.dismiss();
                            }
                            if (pdfUris == null || pdfUris.isEmpty()) {
                                finish();
                                return;
                            }
                            Intent chatIntent = new Intent(AskActivity.this, ChatActivity.class);
                            if (pdfUris.size() == 1) {
                                chatIntent.setAction(Intent.ACTION_SEND);
                                chatIntent.putExtra(Intent.EXTRA_STREAM, pdfUris.get(0));
                            } else {
                                chatIntent.setAction(Intent.ACTION_SEND_MULTIPLE);
                                chatIntent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, new ArrayList<>(pdfUris));
                            }
                            chatIntent.setType("application/pdf");
                            chatIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                            chatIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            startActivity(chatIntent);
                            finish();
                        }

                        @Override
                        public void onError(String errorMessage) {
                            if (progressDialog != null) {
                                progressDialog.dismiss();
                            }
                            Log.e(TAG, "Document processing failed: " + errorMessage);
                            Toast.makeText(AskActivity.this, "Failed: " + errorMessage, Toast.LENGTH_LONG).show();
                            finish();
                        }
                    });
                    return;
                }
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

    @Override
    protected void onDestroy() {
        if (progressDialog != null) {
            progressDialog.dismiss();
        }
        super.onDestroy();
    }
}
