package org.apache.cordova.firebasex;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;

/**
 * Activity that handles notification taps on Android S+ (API 31+).
 *
 * <p>Starting with Android S, PendingIntents targeting BroadcastReceivers are restricted.
 * This transparent Activity serves as the notification tap target instead of
 * {@link OnNotificationOpenReceiver}. It extracts the message data from the intent,
 * forwards it to {@link FirebasexMessagingPlugin#sendMessage(Bundle, Context)} for
 * JS delivery, launches the app's main activity, and immediately finishes itself.</p>
 *
 * @see OnNotificationOpenReceiver
 * @see FirebasexMessagingPlugin
 */
public class OnNotificationReceiverActivity extends Activity {

    private static final String TAG = "FirebasePlugin";

    /** Handles notification data when the activity is first created. */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "OnNotificationReceiverActivity.onCreate()");
        handleNotification(this, getIntent());
        finish();
    }

    /** Handles notification data when the activity receives a new intent while already running. */
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        Log.d(TAG, "OnNotificationReceiverActivity.onNewIntent()");
        handleNotification(this, intent);
        finish();
    }

    /**
     * Extracts notification data from the intent, sets the tap source, delivers the
     * message to the plugin, and launches the app's main activity.
     * @param context The application context.
     * @param intent  The intent containing notification extras.
     */
    private static void handleNotification(Context context, Intent intent) {
        try{
            PackageManager pm = context.getPackageManager();

            Intent launchIntent = pm.getLaunchIntentForPackage(context.getPackageName());
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);

            Bundle data = intent.getExtras();
            if(!data.containsKey("messageType")) data.putString("messageType", "notification");
            data.putString("tap", FirebasexMessagingPlugin.inBackground() ? "background" : "foreground");

            Log.d(TAG, "OnNotificationReceiverActivity.handleNotification(): "+data.toString());

            FirebasexMessagingPlugin.sendMessage(data, context);

            launchIntent.putExtras(data);
            context.startActivity(launchIntent);
        }catch (Exception e){
            FirebasexCorePlugin.handleExceptionWithoutContext(e);
        }
    }
}
