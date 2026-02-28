package org.apache.cordova.firebasex;

import android.app.Activity;
import android.app.NotificationManager;
import android.app.NotificationChannel;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.google.firebase.messaging.FirebaseMessaging;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * FirebasexMessagingPlugin
 *
 * Cordova plugin for Firebase Cloud Messaging.
 * Handles FCM token management, notification permissions, subscriptions,
 * notification channels, and message delivery.
 */
public class FirebasexMessagingPlugin extends CordovaPlugin {

    protected static FirebasexMessagingPlugin instance = null;
    protected static final String TAG = "FirebasexMessaging";
    protected static final String JS_GLOBAL_NAMESPACE = "FirebasexMessaging.";

    protected static final String POST_NOTIFICATIONS = "POST_NOTIFICATIONS";
    protected static final int POST_NOTIFICATIONS_PERMISSION_REQUEST_ID = 1;

    private static boolean immediateMessagePayloadDelivery = false;
    private static ArrayList<Bundle> notificationStack = null;
    private static CallbackContext notificationCallbackContext;
    private static CallbackContext tokenRefreshCallbackContext;
    private static CallbackContext postNotificationPermissionRequestCallbackContext;

    private static NotificationChannel defaultNotificationChannel = null;
    public static String defaultChannelId = null;
    public static String defaultChannelName = null;

    private BroadcastReceiver lifecycleReceiver;

    /**
     * Returns the singleton instance.
     */
    public static FirebasexMessagingPlugin getInstance() {
        return instance;
    }

    @Override
    protected void pluginInitialize() {
        instance = this;
        try {
            Log.d(TAG, "Starting Firebase Messaging plugin");

            Context applicationContext = cordova.getActivity().getApplicationContext();
            Activity cordovaActivity = cordova.getActivity();

            immediateMessagePayloadDelivery = "true".equals(FirebasexCorePlugin.getInstance()
                    .getPluginVariableFromConfigXml("FIREBASE_MESSAGING_IMMEDIATE_PAYLOAD_DELIVERY"));

            // Check for notification from app launch
            Bundle extras = cordovaActivity.getIntent().getExtras();
            if (extras != null && extras.size() > 1) {
                if (notificationStack == null) {
                    notificationStack = new ArrayList<Bundle>();
                }
                if (extras.containsKey("google.message_id")) {
                    extras.putString("messageType", "notification");
                    extras.putString("tap", "background");
                    notificationStack.add(extras);
                    Log.d(TAG, "Notification message found on init: " + extras.toString());
                }
            }

            // Initialize default notification channel
            defaultChannelId = FirebasexCorePlugin.getInstance().getStringResource("default_notification_channel_id");
            defaultChannelName = FirebasexCorePlugin.getInstance().getStringResource("default_notification_channel_name");
            createDefaultChannel();

            // Register for lifecycle events from core
            lifecycleReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    String action = intent.getAction();
                    if ("FirebasexAppResumed".equals(action)) {
                        sendPendingNotifications();
                    }
                }
            };
            FirebasexEventBus.register(applicationContext, "FirebasexAppResumed", lifecycleReceiver);

        } catch (Exception e) {
            FirebasexCorePlugin.getInstance().handleExceptionWithoutContext(e);
        }
    }

    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
        try {
            switch (action) {
                case "getToken":
                    getToken(callbackContext);
                    return true;
                case "onMessageReceived":
                    onMessageReceived(callbackContext);
                    return true;
                case "onTokenRefresh":
                    onTokenRefresh(callbackContext);
                    return true;
                case "subscribe":
                    subscribe(callbackContext, args.getString(0));
                    return true;
                case "unsubscribe":
                    unsubscribe(callbackContext, args.getString(0));
                    return true;
                case "unregister":
                    unregister(callbackContext);
                    return true;
                case "hasPermission":
                    hasPermission(callbackContext);
                    return true;
                case "grantPermission":
                    grantPermission(callbackContext);
                    return true;
                case "isAutoInitEnabled":
                    isAutoInitEnabled(callbackContext);
                    return true;
                case "setAutoInitEnabled":
                    setAutoInitEnabled(callbackContext, args.getBoolean(0));
                    return true;
                case "createChannel":
                    createChannel(callbackContext, args.getJSONObject(0));
                    return true;
                case "setDefaultChannel":
                    setDefaultChannel(callbackContext, args.getJSONObject(0));
                    return true;
                case "deleteChannel":
                    deleteChannel(callbackContext, args.getString(0));
                    return true;
                case "listChannels":
                    listChannels(callbackContext);
                    return true;
                case "clearAllNotifications":
                    clearAllNotifications(callbackContext);
                    return true;
                case "setBadgeNumber":
                    // No-op on Android
                    callbackContext.success();
                    return true;
                case "getBadgeNumber":
                    // No-op on Android
                    callbackContext.success(0);
                    return true;
                case "hasCriticalPermission":
                    // No-op on Android
                    callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.OK, false));
                    return true;
                case "grantCriticalPermission":
                    // No-op on Android
                    callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.OK, false));
                    return true;
                case "getAPNSToken":
                    // No-op on Android
                    callbackContext.success("");
                    return true;
                case "onApnsTokenReceived":
                    // No-op on Android
                    callbackContext.success();
                    return true;
                case "onOpenSettings":
                    // No-op on Android
                    callbackContext.success();
                    return true;
                default:
                    return false;
            }
        } catch (Exception e) {
            FirebasexCorePlugin.getInstance().handleExceptionWithContext(e, callbackContext);
            return false;
        }
    }

    @Override
    public void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        try {
            Bundle data = intent.getExtras();
            if (data != null && data.containsKey("google.message_id")) {
                data.putString("messageType", "notification");
                data.putString("tap", "background");
                sendMessage(data, cordova.getActivity().getApplicationContext());
            }
        } catch (Exception e) {
            FirebasexCorePlugin.getInstance().handleExceptionWithoutContext(e);
        }
    }

    @Override
    public void onDestroy() {
        if (lifecycleReceiver != null) {
            FirebasexEventBus.unregister(cordova.getActivity().getApplicationContext(), lifecycleReceiver);
        }
        super.onDestroy();
    }

    // ---- Token Management ----

    private void getToken(final CallbackContext callbackContext) {
        cordova.getThreadPool().execute(new Runnable() {
            public void run() {
                try {
                    FirebaseMessaging.getInstance().getToken()
                            .addOnCompleteListener(new com.google.android.gms.tasks.OnCompleteListener<String>() {
                                @Override
                                public void onComplete(com.google.android.gms.tasks.Task<String> task) {
                                    try {
                                        if (task.isSuccessful()) {
                                            callbackContext.success(task.getResult());
                                        } else {
                                            callbackContext.error(task.getException().getMessage());
                                        }
                                    } catch (Exception e) {
                                        FirebasexCorePlugin.getInstance().handleExceptionWithContext(e, callbackContext);
                                    }
                                }
                            });
                } catch (Exception e) {
                    FirebasexCorePlugin.getInstance().handleExceptionWithContext(e, callbackContext);
                }
            }
        });
    }

    private void onTokenRefresh(final CallbackContext callbackContext) {
        tokenRefreshCallbackContext = callbackContext;
    }

    private void onMessageReceived(final CallbackContext callbackContext) {
        notificationCallbackContext = callbackContext;
        sendPendingNotifications();
    }

    // ---- Subscriptions ----

    private void subscribe(final CallbackContext callbackContext, final String topic) {
        cordova.getThreadPool().execute(new Runnable() {
            public void run() {
                try {
                    FirebaseMessaging.getInstance().subscribeToTopic(topic);
                    callbackContext.success();
                } catch (Exception e) {
                    FirebasexCorePlugin.getInstance().handleExceptionWithContext(e, callbackContext);
                }
            }
        });
    }

    private void unsubscribe(final CallbackContext callbackContext, final String topic) {
        cordova.getThreadPool().execute(new Runnable() {
            public void run() {
                try {
                    FirebaseMessaging.getInstance().unsubscribeFromTopic(topic);
                    callbackContext.success();
                } catch (Exception e) {
                    FirebasexCorePlugin.getInstance().handleExceptionWithContext(e, callbackContext);
                }
            }
        });
    }

    private void unregister(final CallbackContext callbackContext) {
        cordova.getThreadPool().execute(new Runnable() {
            public void run() {
                try {
                    FirebaseMessaging.getInstance().deleteToken();
                    boolean isAutoInitEnabled = FirebaseMessaging.getInstance().isAutoInitEnabled();
                    if (isAutoInitEnabled) {
                        FirebaseMessaging.getInstance().getToken();
                    }
                    callbackContext.success();
                } catch (Exception e) {
                    FirebasexCorePlugin.getInstance().handleExceptionWithContext(e, callbackContext);
                }
            }
        });
    }

    // ---- Permissions ----

    private void hasPermission(final CallbackContext callbackContext) {
        cordova.getThreadPool().execute(new Runnable() {
            public void run() {
                try {
                    boolean hasPermission;
                    Context ctx = cordova.getActivity().getApplicationContext();
                    if (Build.VERSION.SDK_INT >= 33) {
                        hasPermission = ctx.checkSelfPermission("android.permission.POST_NOTIFICATIONS")
                                == android.content.pm.PackageManager.PERMISSION_GRANTED;
                    } else {
                        hasPermission = androidx.core.app.NotificationManagerCompat.from(ctx).areNotificationsEnabled();
                    }
                    callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.OK, hasPermission));
                } catch (Exception e) {
                    FirebasexCorePlugin.getInstance().handleExceptionWithContext(e, callbackContext);
                }
            }
        });
    }

    private void grantPermission(final CallbackContext callbackContext) {
        cordova.getThreadPool().execute(new Runnable() {
            public void run() {
                try {
                    if (Build.VERSION.SDK_INT >= 33) {
                        postNotificationPermissionRequestCallbackContext = callbackContext;
                        cordova.requestPermission(FirebasexMessagingPlugin.this, POST_NOTIFICATIONS_PERMISSION_REQUEST_ID,
                                "android.permission.POST_NOTIFICATIONS");
                    } else {
                        callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.OK, true));
                    }
                } catch (Exception e) {
                    FirebasexCorePlugin.getInstance().handleExceptionWithContext(e, callbackContext);
                }
            }
        });
    }

    @Override
    public void onRequestPermissionResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == POST_NOTIFICATIONS_PERMISSION_REQUEST_ID && postNotificationPermissionRequestCallbackContext != null) {
            boolean granted = grantResults.length > 0 && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED;
            postNotificationPermissionRequestCallbackContext.sendPluginResult(
                    new PluginResult(PluginResult.Status.OK, granted));
            postNotificationPermissionRequestCallbackContext = null;
        }
    }

    // ---- Auto-init ----

    private void isAutoInitEnabled(final CallbackContext callbackContext) {
        cordova.getThreadPool().execute(new Runnable() {
            public void run() {
                try {
                    boolean enabled = FirebaseMessaging.getInstance().isAutoInitEnabled();
                    callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.OK, enabled));
                } catch (Exception e) {
                    FirebasexCorePlugin.getInstance().handleExceptionWithContext(e, callbackContext);
                }
            }
        });
    }

    private void setAutoInitEnabled(final CallbackContext callbackContext, final boolean enabled) {
        cordova.getThreadPool().execute(new Runnable() {
            public void run() {
                try {
                    FirebaseMessaging.getInstance().setAutoInitEnabled(enabled);
                    callbackContext.success();
                } catch (Exception e) {
                    FirebasexCorePlugin.getInstance().handleExceptionWithContext(e, callbackContext);
                }
            }
        });
    }

    // ---- Notification Channels ----

    public void createChannel(final CallbackContext callbackContext, final JSONObject options) {
        cordova.getThreadPool().execute(new Runnable() {
            public void run() {
                try {
                    createChannel(options);
                    callbackContext.success();
                } catch (Exception e) {
                    FirebasexCorePlugin.getInstance().handleExceptionWithContext(e, callbackContext);
                }
            }
        });
    }

    protected static NotificationChannel createChannel(final JSONObject options) throws JSONException {
        NotificationChannel channel = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            String id = options.getString("id");
            Log.i(TAG, "Creating channel id=" + id);

            if (channelExists(id)) {
                deleteChannel(id);
            }

            Context applicationContext = FirebasexCorePlugin.getInstance().getApplicationContext();
            NotificationManager nm = (NotificationManager) applicationContext.getSystemService(Context.NOTIFICATION_SERVICE);
            String packageName = FirebasexCorePlugin.getInstance().getCordovaActivity().getPackageName();

            String name = options.optString("name", "");
            int importance = options.optInt("importance", NotificationManager.IMPORTANCE_HIGH);

            channel = new NotificationChannel(id, name, importance);

            String description = options.optString("description", "");
            channel.setDescription(description);

            boolean light = options.optBoolean("light", true);
            channel.enableLights(light);

            int lightColor = options.optInt("lightColor", -1);
            if (lightColor != -1) {
                channel.setLightColor(lightColor);
            }

            int visibility = options.optInt("visibility", NotificationCompat.VISIBILITY_PUBLIC);
            channel.setLockscreenVisibility(visibility);

            boolean badge = options.optBoolean("badge", true);
            channel.setShowBadge(badge);

            int usage = options.optInt("usage", AudioAttributes.USAGE_NOTIFICATION_RINGTONE);
            int streamType = options.optInt("streamType", -1);

            String sound = options.optString("sound", "default");
            AudioAttributes.Builder audioAttributesBuilder = new AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setUsage(usage);

            if (streamType != -1) {
                audioAttributesBuilder.setLegacyStreamType(streamType);
            }

            AudioAttributes audioAttributes = audioAttributesBuilder.build();
            if ("ringtone".equals(sound)) {
                channel.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE), audioAttributes);
            } else if (!sound.contentEquals("false")) {
                if (!sound.contentEquals("default")) {
                    Uri soundUri = Uri.parse(ContentResolver.SCHEME_ANDROID_RESOURCE + "://" + packageName + "/raw/" + sound);
                    channel.setSound(soundUri, audioAttributes);
                } else {
                    channel.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION), audioAttributes);
                }
            } else {
                channel.setSound(null, null);
            }

            JSONArray pattern = options.optJSONArray("vibration");
            if (pattern != null) {
                int patternLength = pattern.length();
                long[] patternArray = new long[patternLength];
                for (int i = 0; i < patternLength; i++) {
                    patternArray[i] = pattern.optLong(i);
                }
                channel.enableVibration(true);
                channel.setVibrationPattern(patternArray);
            } else {
                boolean vibrate = options.optBoolean("vibration", true);
                channel.enableVibration(vibrate);
            }

            nm.createNotificationChannel(channel);
        }
        return channel;
    }

    protected static void createDefaultChannel() throws JSONException {
        JSONObject options = new JSONObject();
        options.put("id", defaultChannelId);
        options.put("name", defaultChannelName);
        createDefaultChannel(options);
    }

    protected static void createDefaultChannel(final JSONObject options) throws JSONException {
        defaultNotificationChannel = createChannel(options);
    }

    public void setDefaultChannel(final CallbackContext callbackContext, final JSONObject options) {
        cordova.getThreadPool().execute(new Runnable() {
            public void run() {
                try {
                    deleteChannel(defaultChannelId);

                    String id = options.optString("id", null);
                    if (id != null) {
                        defaultChannelId = id;
                    }

                    String name = options.optString("name", null);
                    if (name != null) {
                        defaultChannelName = name;
                    }
                    createDefaultChannel(options);
                    callbackContext.success();
                } catch (Exception e) {
                    FirebasexCorePlugin.getInstance().handleExceptionWithContext(e, callbackContext);
                }
            }
        });
    }

    public void deleteChannel(final CallbackContext callbackContext, final String channelID) {
        cordova.getThreadPool().execute(new Runnable() {
            public void run() {
                try {
                    deleteChannel(channelID);
                    callbackContext.success();
                } catch (Exception e) {
                    FirebasexCorePlugin.getInstance().handleExceptionWithContext(e, callbackContext);
                }
            }
        });
    }

    protected static void deleteChannel(final String channelID) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Context applicationContext = FirebasexCorePlugin.getInstance().getApplicationContext();
            NotificationManager nm = (NotificationManager) applicationContext.getSystemService(Context.NOTIFICATION_SERVICE);
            nm.deleteNotificationChannel(channelID);
        }
    }

    public void listChannels(final CallbackContext callbackContext) {
        cordova.getThreadPool().execute(new Runnable() {
            public void run() {
                try {
                    List<NotificationChannel> notificationChannels = listChannels();
                    JSONArray channels = new JSONArray();
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        for (NotificationChannel notificationChannel : notificationChannels) {
                            JSONObject channel = new JSONObject();
                            channel.put("id", notificationChannel.getId());
                            channel.put("name", notificationChannel.getName());
                            channels.put(channel);
                        }
                    }
                    callbackContext.success(channels);
                } catch (Exception e) {
                    FirebasexCorePlugin.getInstance().handleExceptionWithContext(e, callbackContext);
                }
            }
        });
    }

    public static List<NotificationChannel> listChannels() {
        List<NotificationChannel> notificationChannels = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Context applicationContext = FirebasexCorePlugin.getInstance().getApplicationContext();
            NotificationManager nm = (NotificationManager) applicationContext.getSystemService(Context.NOTIFICATION_SERVICE);
            notificationChannels = nm.getNotificationChannels();
        }
        return notificationChannels;
    }

    public static boolean channelExists(String channelId) {
        boolean exists = false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            List<NotificationChannel> notificationChannels = listChannels();
            if (notificationChannels != null) {
                for (NotificationChannel notificationChannel : notificationChannels) {
                    if (notificationChannel.getId().equals(channelId)) {
                        exists = true;
                    }
                }
            }
        }
        return exists;
    }

    // ---- Notifications ----

    public void clearAllNotifications(final CallbackContext callbackContext) {
        cordova.getThreadPool().execute(new Runnable() {
            public void run() {
                try {
                    Context applicationContext = FirebasexCorePlugin.getInstance().getApplicationContext();
                    NotificationManager nm = (NotificationManager) applicationContext.getSystemService(Context.NOTIFICATION_SERVICE);
                    nm.cancelAll();
                    callbackContext.success();
                } catch (Exception e) {
                    FirebasexCorePlugin.getInstance().handleExceptionWithContext(e, callbackContext);
                }
            }
        });
    }

    // ---- Static message delivery (called from FirebasexMessagingService) ----

    public static boolean inBackground() {
        return FirebasexCorePlugin.isApplicationInBackground();
    }

    public static boolean hasNotificationsCallback() {
        return notificationCallbackContext != null;
    }

    public static boolean isImmediateMessagePayloadDelivery() {
        return immediateMessagePayloadDelivery;
    }

    /**
     * Called from the messaging service or notification receivers to deliver a message
     * to the JS callback.
     */
    public static void sendMessage(Bundle bundle, Context context) {
        try {
            if (!FirebasePluginMessageReceiverManager.sendMessage(bundle)) {
                if (notificationCallbackContext != null && (!inBackground() || immediateMessagePayloadDelivery)) {
                    JSONObject json = new JSONObject();
                    for (String key : bundle.keySet()) {
                        Object value = bundle.get(key);
                        json.put(key, value);
                    }
                    PluginResult pluginResult = new PluginResult(PluginResult.Status.OK, json);
                    pluginResult.setKeepCallback(true);
                    notificationCallbackContext.sendPluginResult(pluginResult);
                } else {
                    if (notificationStack == null) {
                        notificationStack = new ArrayList<Bundle>();
                    }
                    notificationStack.add(bundle);
                }
            }
        } catch (Exception e) {
            FirebasexCorePlugin.getInstance().handleExceptionWithoutContext(e);
        }
    }

    /**
     * Called from the messaging service when a new FCM token is received.
     */
    public static void sendToken(String token) {
        try {
            if (tokenRefreshCallbackContext != null) {
                PluginResult pluginResult = new PluginResult(PluginResult.Status.OK, token);
                pluginResult.setKeepCallback(true);
                tokenRefreshCallbackContext.sendPluginResult(pluginResult);
            }
        } catch (Exception e) {
            FirebasexCorePlugin.getInstance().handleExceptionWithoutContext(e);
        }
    }

    /**
     * Sends any queued notifications to the JS layer.
     */
    public void sendPendingNotifications() {
        if (notificationCallbackContext != null && notificationStack != null) {
            for (Bundle bundle : notificationStack) {
                sendMessage(bundle, cordova.getActivity().getApplicationContext());
            }
            notificationStack.clear();
        }
    }
}
