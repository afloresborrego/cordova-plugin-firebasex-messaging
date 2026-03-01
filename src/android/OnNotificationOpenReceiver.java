package org.apache.cordova.firebasex;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;

public class OnNotificationOpenReceiver extends BroadcastReceiver {

    private static final String TAG = "FirebasePlugin";

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
