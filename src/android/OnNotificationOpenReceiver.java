package org.apache.cordova.firebasex;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

/**
 * OnNotificationOpenReceiver
 *
 * BroadcastReceiver that handles notification tap events.
 */
public class OnNotificationOpenReceiver extends BroadcastReceiver {

    private static final String TAG = "FirebasexNotifOpen";

    @Override
    public void onReceive(Context context, Intent intent) {
        try {
            Bundle data = intent.getExtras();
            if (data != null) {
                data.putString("messageType", "notification");
                data.putString("tap", "background");
                FirebasexMessagingPlugin.sendMessage(data, context);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error handling notification open: " + e.getMessage());
        }
    }
}
