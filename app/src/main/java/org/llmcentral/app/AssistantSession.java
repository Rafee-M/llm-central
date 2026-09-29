package org.llmcentral.app;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.service.voice.VoiceInteractionSession;
import android.app.assist.AssistContent;
import android.app.assist.AssistStructure;

public class AssistantSession extends VoiceInteractionSession {

    public AssistantSession(Context context) {
        super(context);
    }

    @Override
    public void onHandleAssist(Bundle data, AssistStructure structure, AssistContent content) {
        super.onHandleAssist(data, structure, content);
        // We could extract text from 'structure' here if we wanted to be fancy.
        // For now, we just launch the activity.
    }

    @Override
    public void onShow(Bundle args, int showFlags) {
        super.onShow(args, showFlags);
        
        Intent intent = new Intent(getContext(), ChatActivity.class);
        intent.setAction(Intent.ACTION_ASSIST);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        
        // startAssistantActivity() only exists on Android 8.0+ (API 26). The app supports Android 7
        // (minSdk 24), where calling it would crash with NoSuchMethodError, so older versions start the
        // activity the ordinary way. FLAG_ACTIVITY_NEW_TASK (set above) is required for that because we
        // are starting it from a service context, not from another activity.
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            startAssistantActivity(intent);
        } else {
            getContext().startActivity(intent);
        }
        finish();
    }
}
