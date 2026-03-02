package org.apache.cordova.firebasex;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;

/**
 * BroadcastReceiver that handles notification taps on pre-Android S devices.
 *
 * <p>When a user taps a system notification, this receiver extracts the message
 * data from the intent extras, forwards it to
 * {@link FirebasexMessagingPlugin#sendMessage(Bundle, Context)} for JS delivery,
 * and launches the app's main activity with the notification data attached.</p>
 *
 * <p>On Android S+ (API 31+), {@link OnNotificationReceiverActivity} is used instead
 * due to PendingIntent restrictions that require an Activity rather than a BroadcastReceiver.</p>
 *
 * @see OnNotificationReceiverActivity
 * @see FirebasexMessagingPlugin
 */
public class OnNotificationOpenReceiver extends BroadcastReceiver {

    private static final String TAG = "FirebasePlugin";

    /**
     * Handles the notification tap broadcast.
     * Extracts message data, sets the tap source ("background" or "foreground"),
     * delivers the message to the plugin, and launches the main activity.
     */
    @Override
    public void onReceive(Context context, Intent intent) {
        try{
            PackageManager pm = context.getPackageManager();

            Intent launchIntent = pm.getLaunchIntentForPackage(context.getPackageName());
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);

            Bundle data = intent.getExtras();
            if(!data.containsKey("messageType")) data.putString("messageType", "notification");
            data.putString("tap", FirebasexMessagingPlugin.inBackground() ? "background" : "foreground");

            Log.d(TAG, "OnNotificationOpenReceiver.onReceive(): "+data.toString());

            FirebasexMessagingPlugin.sendMessage(data, context);

            launchIntent.putExtras(data);
            context.startActivity(launchIntent);
        }catch (Exception e){
            FirebasexCorePlugin.handleExceptionWithoutContext(e);
        }
    }
}
