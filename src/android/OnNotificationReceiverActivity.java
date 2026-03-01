package org.apache.cordova.firebasex;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;

public class OnNotificationReceiverActivity extends Activity {

    private static final String TAG = "FirebasePlugin";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "OnNotificationReceiverActivity.onCreate()");
        handleNotification(this, getIntent());
        finish();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        Log.d(TAG, "OnNotificationReceiverActivity.onNewIntent()");
        handleNotification(this, intent);
        finish();
    }

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
