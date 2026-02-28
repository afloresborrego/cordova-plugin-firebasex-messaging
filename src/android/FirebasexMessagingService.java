package org.apache.cordova.firebasex;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Map;
import java.util.Random;

/**
 * FirebasexMessagingService
 *
 * Handles incoming FCM messages and token refreshes.
 * Adapted from the monolithic FirebasePluginMessagingService.
 */
public class FirebasexMessagingService extends FirebaseMessagingService {

    private static final String TAG = "FirebasexMsgService";

    @Override
    public void onNewToken(String token) {
        super.onNewToken(token);
        Log.d(TAG, "onNewToken: " + token);
        FirebasexMessagingPlugin.sendToken(token);
    }

    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {
        Log.d(TAG, "onMessageReceived");

        try {
            if (FirebasePluginMessageReceiverManager.onMessageReceived(remoteMessage)) {
                return;
            }

            Map<String, String> data = remoteMessage.getData();
            RemoteMessage.Notification notification = remoteMessage.getNotification();

            boolean hasData = !data.isEmpty();
            boolean hasNotification = notification != null;
            String messageType;

            String id = null;
            String title = null;
            String body = null;
            String icon = null;
            String sound = null;
            String color = null;
            String channelId = null;
            String image = null;
            String imageType = null;
            boolean foregroundNotification = false;
            boolean visibility_show = true;
            boolean light = true;
            int lightColor = 0;
            String vibrate = null;
            String priority = null;

            if (hasNotification) {
                messageType = "notification";
                id = notification.getTag();
                title = notification.getTitle();
                body = notification.getBody();
                icon = notification.getIcon();
                if (notification.getSound() != null) {
                    sound = notification.getSound();
                }
                color = notification.getColor();
                channelId = notification.getChannelId();
                if (notification.getImageUrl() != null) {
                    image = notification.getImageUrl().toString();
                }
            } else {
                messageType = "data";
            }

            if (hasData) {
                if (data.containsKey("notification_title")) title = data.get("notification_title");
                if (data.containsKey("notification_body")) body = data.get("notification_body");
                if (data.containsKey("notification_android_icon")) icon = data.get("notification_android_icon");
                if (data.containsKey("notification_android_sound")) sound = data.get("notification_android_sound");
                if (data.containsKey("notification_android_color")) color = data.get("notification_android_color");
                if (data.containsKey("notification_android_channel_id")) channelId = data.get("notification_android_channel_id");
                if (data.containsKey("notification_android_image")) image = data.get("notification_android_image");
                if (data.containsKey("notification_android_image_type")) imageType = data.get("notification_android_image_type");
                if (data.containsKey("notification_android_visibility")) {
                    String vis = data.get("notification_android_visibility");
                    if ("secret".equals(vis)) visibility_show = false;
                }
                if (data.containsKey("notification_android_light")) {
                    light = "true".equals(data.get("notification_android_light"));
                }
                if (data.containsKey("notification_android_light_color")) {
                    try {
                        lightColor = Integer.parseInt(data.get("notification_android_light_color"));
                    } catch (NumberFormatException ignored) {}
                }
                if (data.containsKey("notification_android_vibrate")) vibrate = data.get("notification_android_vibrate");
                if (data.containsKey("notification_android_priority")) priority = data.get("notification_android_priority");
                if (data.containsKey("notification_foreground")) {
                    foregroundNotification = "true".equals(data.get("notification_foreground"));
                }
            }

            boolean isEmpty = title == null || title.isEmpty();
            boolean showNotification = (FirebasexMessagingPlugin.inBackground()
                    || !FirebasexMessagingPlugin.hasNotificationsCallback()
                    || foregroundNotification) && (!isEmpty || (body != null && !body.isEmpty()));

            sendMessage(remoteMessage, data, messageType, id, title, body, showNotification,
                    sound, icon, color, channelId, image, imageType,
                    foregroundNotification, visibility_show, light, lightColor, vibrate, priority);

        } catch (Exception e) {
            Log.e(TAG, "Exception in onMessageReceived: " + e.getMessage());
        }
    }

    private void sendMessage(RemoteMessage remoteMessage, Map<String, String> data,
                             String messageType, String id, String title, String body,
                             boolean showNotification, String sound, String icon, String color,
                             String channelId, String image, String imageType,
                             boolean foregroundNotification, boolean visibility_show,
                             boolean light, int lightColor, String vibrate, String priority) {
        Bundle bundle = new Bundle();
        bundle.putString("messageType", messageType);

        for (String key : data.keySet()) {
            bundle.putString(key, data.get(key));
        }

        if (id != null) bundle.putString("id", id);
        if (title != null) bundle.putString("title", title);
        if (body != null) bundle.putString("body", body);
        if (remoteMessage.getMessageId() != null) {
            bundle.putString("google.message_id", remoteMessage.getMessageId());
        }

        if (showNotification) {
            displayNotification(bundle, title, body, sound, icon, color, channelId,
                    image, imageType, visibility_show, light, lightColor, vibrate, priority);
        }

        String tap = null;
        if (FirebasexMessagingPlugin.inBackground()) {
            tap = "background";
        }
        bundle.putString("tap", tap);

        FirebasexMessagingPlugin.sendMessage(bundle, getApplicationContext());
    }

    private void displayNotification(Bundle bundle, String title, String body,
                                     String sound, String icon, String color,
                                     String channelId, String image, String imageType,
                                     boolean visibility_show, boolean light, int lightColor,
                                     String vibrate, String priority) {
        try {
            Context context = getApplicationContext();
            String packageName = context.getPackageName();

            if (channelId == null || !FirebasexMessagingPlugin.channelExists(channelId)) {
                channelId = FirebasexMessagingPlugin.defaultChannelId;
            }

            NotificationCompat.Builder builder = new NotificationCompat.Builder(context, channelId);

            builder.setContentTitle(title);
            builder.setContentText(body);
            builder.setAutoCancel(true);

            // Icon
            int iconResId = 0;
            if (icon != null) {
                iconResId = context.getResources().getIdentifier(icon, "drawable", packageName);
            }
            if (iconResId == 0) {
                iconResId = context.getResources().getIdentifier("notification_icon", "drawable", packageName);
            }
            if (iconResId == 0) {
                iconResId = context.getApplicationInfo().icon;
            }
            builder.setSmallIcon(iconResId);

            // Image
            if (image != null) {
                Bitmap bitmap = getBitmapFromUrl(image);
                if (bitmap != null) {
                    if ("circle".equals(imageType)) {
                        bitmap = getCircleBitmap(bitmap);
                        builder.setLargeIcon(bitmap);
                    } else {
                        builder.setStyle(new NotificationCompat.BigPictureStyle().bigPicture(bitmap));
                    }
                }
            }

            // Sound
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                if (sound != null) {
                    if ("default".equals(sound)) {
                        builder.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION));
                    } else if ("ringtone".equals(sound)) {
                        builder.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE));
                    } else {
                        Uri soundUri = Uri.parse(ContentResolver.SCHEME_ANDROID_RESOURCE + "://" + packageName + "/raw/" + sound);
                        builder.setSound(soundUri);
                    }
                } else {
                    builder.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION));
                }
            }

            // Lights
            if (light) {
                if (lightColor != 0) {
                    builder.setLights(lightColor, 500, 500);
                } else {
                    builder.setDefaults(NotificationCompat.DEFAULT_LIGHTS);
                }
            }

            // Vibration
            if (vibrate != null) {
                try {
                    String[] parts = vibrate.split(",");
                    long[] pattern = new long[parts.length];
                    for (int i = 0; i < parts.length; i++) {
                        pattern[i] = Long.parseLong(parts[i].trim());
                    }
                    builder.setVibrate(pattern);
                } catch (Exception ignored) {
                    builder.setDefaults(NotificationCompat.DEFAULT_VIBRATE);
                }
            }

            // Color
            if (color != null) {
                try {
                    builder.setColor(android.graphics.Color.parseColor(color));
                } catch (Exception ignored) {}
            }

            // Priority
            if (priority != null) {
                switch (priority) {
                    case "max":
                        builder.setPriority(NotificationCompat.PRIORITY_MAX);
                        break;
                    case "high":
                        builder.setPriority(NotificationCompat.PRIORITY_HIGH);
                        break;
                    case "low":
                        builder.setPriority(NotificationCompat.PRIORITY_LOW);
                        break;
                    case "min":
                        builder.setPriority(NotificationCompat.PRIORITY_MIN);
                        break;
                    default:
                        builder.setPriority(NotificationCompat.PRIORITY_DEFAULT);
                        break;
                }
            }

            // Visibility
            if (visibility_show) {
                builder.setVisibility(NotificationCompat.VISIBILITY_PUBLIC);
            } else {
                builder.setVisibility(NotificationCompat.VISIBILITY_SECRET);
            }

            // Click intent
            Intent intent = new Intent(context, OnNotificationReceiverActivity.class);
            intent.putExtras(bundle);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            int requestCode = new Random().nextInt();
            PendingIntent pendingIntent = PendingIntent.getActivity(context, requestCode, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            builder.setContentIntent(pendingIntent);

            int notificationId;
            String bundleId = bundle.getString("id");
            if (bundleId != null) {
                try {
                    notificationId = Integer.parseInt(bundleId);
                } catch (NumberFormatException e) {
                    notificationId = bundleId.hashCode();
                }
            } else {
                notificationId = new Random().nextInt();
            }

            NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            nm.notify(notificationId, builder.build());

        } catch (Exception e) {
            Log.e(TAG, "Exception displaying notification: " + e.getMessage());
        }
    }

    private Bitmap getBitmapFromUrl(String imageUrl) {
        try {
            URL url = new URL(imageUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setDoInput(true);
            connection.connect();
            InputStream input = connection.getInputStream();
            return BitmapFactory.decodeStream(input);
        } catch (Exception e) {
            Log.e(TAG, "Error getting bitmap from URL: " + e.getMessage());
            return null;
        }
    }

    private Bitmap getCircleBitmap(Bitmap bitmap) {
        int size = Math.min(bitmap.getWidth(), bitmap.getHeight());
        Bitmap output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(output);
        Paint paint = new Paint();
        Rect rect = new Rect(0, 0, size, size);

        paint.setAntiAlias(true);
        canvas.drawARGB(0, 0, 0, 0);
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint);
        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
        canvas.drawBitmap(bitmap, rect, rect, paint);
        return output;
    }
}
