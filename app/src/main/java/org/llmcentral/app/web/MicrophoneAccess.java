package org.llmcentral.app.web;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.webkit.PermissionRequest;
import android.widget.Toast;

import org.llmcentral.app.R;
import org.llmcentral.app.provider.ProviderSpec;

import java.util.Arrays;

/**
 * The microphone for voice chat, asked for <b>when a chat page wants it</b>, not when the app starts.
 *
 * <p><b>Two permissions are involved:</b></p>
 * <ol>
 *   <li>Android's permission for the <i>app</i> ({@code RECORD_AUDIO}): the system dialog "Allow LLM
 *       Central to record audio?". Docs:
 *       https://developer.android.com/training/permissions/requesting</li>
 *   <li>WebView's question for the <i>page</i> ({@code WebChromeClient.onPermissionRequest}): "this page
 *       wants the microphone". The app answers with {@code grant} or {@code deny}.</li>
 * </ol>
 * <p>Until 2026-10-01 the app asked for (1) at every start until allowed, before anyone used voice.
 * Now (1) is asked only when a page asks for (2) and the app doesn't have it yet. The page's request
 * is kept until the user answers the system dialog, then granted or denied.</p>
 *
 * <p>Only the provider's own pages may get the microphone, never embedded content from other sites
 * (rule unchanged). {@link #decide} is plain Java (MicrophoneAccessTest).</p>
 */
public final class MicrophoneAccess {

    /** Request code for the system dialog (onRequestPermissionsResult). */
    public static final int REQUEST_CODE = 125;

    /** What to do with a page's request. */
    enum Decision { GRANT, ASK_ANDROID, DENY }

    private final Activity activity;
    // The page request waiting for the user's answer in the system dialog (at most one).
    private PermissionRequest pending;

    public MicrophoneAccess(Activity activity) {
        this.activity = activity;
    }

    /**
     * @param providersPage    the request comes from the provider's own site
     * @param wantsMicrophone  the page asks for audio capture
     * @param appHasPermission Android already allowed the app to record audio
     */
    static Decision decide(boolean providersPage, boolean wantsMicrophone, boolean appHasPermission) {
        if (!providersPage || !wantsMicrophone) {
            return Decision.DENY;
        }
        return appHasPermission ? Decision.GRANT : Decision.ASK_ANDROID;
    }

    /** Call from {@code WebChromeClient.onPermissionRequest}, on the main thread. */
    public void onPageRequest(PermissionRequest request, ProviderSpec provider) {
        Uri origin = request.getOrigin();
        boolean providersPage = origin != null && provider.ownsHost(origin.getHost());
        boolean wantsMicrophone = Arrays.asList(request.getResources())
                .contains(PermissionRequest.RESOURCE_AUDIO_CAPTURE);
        boolean appHasPermission = activity.checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED;
        switch (decide(providersPage, wantsMicrophone, appHasPermission)) {
            case GRANT:
                request.grant(new String[]{PermissionRequest.RESOURCE_AUDIO_CAPTURE});
                break;
            case ASK_ANDROID:
                if (pending != null) {
                    pending.deny(); // an older request that was never answered
                }
                pending = request;
                activity.requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_CODE);
                break;
            default:
                request.deny();
        }
    }

    /** Call from {@code WebChromeClient.onPermissionRequestCanceled}: the page no longer waits. */
    public void onPageRequestCanceled(PermissionRequest request) {
        if (request == pending) {
            pending = null;
        }
    }

    /** Call from {@code onRequestPermissionsResult} for {@link #REQUEST_CODE}. */
    public void onAndroidResult(int[] grantResults) {
        PermissionRequest request = pending;
        pending = null;
        if (request == null) {
            return;
        }
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            request.grant(new String[]{PermissionRequest.RESOURCE_AUDIO_CAPTURE});
        } else {
            request.deny();
            // Also shown when the user once chose "Don't allow" for good: Android then answers
            // without a dialog, and this is the only hint where to change it.
            Toast.makeText(activity, R.string.microphone_denied, Toast.LENGTH_LONG).show();
        }
    }
}
