package org.apache.cordova.firebasex;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

/**
 * OnNotificationReceiverActivity
 *
 * Trampoline Activity for handling notification taps on Android 12+.
 * Launches the main activity and forwards notification data.
 */
public class OnNotificationReceiverActivity extends Activity {

    private static final String TAG = "FirebasexNotifReceiver";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            Bundle extras = getIntent().getExtras();
            if (extras != null) {
                extras.putString("messageType", "notification");
                extras.putString("tap", "background");

                // Launch the main activity
                String packageName = getPackageName();
                Intent launchIntent = getPackageManager().getLaunchIntentForPackage(packageName);
                if (launchIntent != null) {
                    launchIntent.putExtras(extras);
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(launchIntent);
                }

                FirebasexMessagingPlugin.sendMessage(extras, getApplicationContext());
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in OnNotificationReceiverActivity: " + e.getMessage());
        }
        finish();
    }
}
