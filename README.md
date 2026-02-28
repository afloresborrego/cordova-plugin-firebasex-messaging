# cordova-plugin-firebasex-messaging

Cordova plugin for Firebase Cloud Messaging (FCM). Provides push notification support including token management, topic subscriptions, notification permissions, and notification channels (Android).

## Dependencies

- `cordova-plugin-firebasex-core` (required)

## Installation

```bash
cordova plugin add cordova-plugin-firebasex-messaging
```

## Preferences

| Preference | Default | Description |
|---|---|---|
| `FIREBASE_FCM_AUTOINIT_ENABLED` | `true` | Enable/disable FCM auto-initialization |
| `FIREBASE_MESSAGING_IMMEDIATE_PAYLOAD_DELIVERY` | `false` | Deliver message payloads immediately even when app is in background |
| `IOS_FCM_ENABLED` | `true` | Enable/disable FCM on iOS |
| `IOS_ENABLE_CRITICAL_ALERTS_ENABLED` | `false` | Enable critical alerts support on iOS |

## API

### Token Management

```javascript
// Get the current FCM registration token
FirebasexMessaging.getToken(function(token) {
    console.log("FCM token: " + token);
}, function(error) {
    console.error(error);
});

// Get the APNS token (iOS only)
FirebasexMessaging.getAPNSToken(function(token) {
    console.log("APNS token: " + token);
}, function(error) {
    console.error(error);
});

// Listen for token refresh events
FirebasexMessaging.onTokenRefresh(function(token) {
    console.log("New FCM token: " + token);
}, function(error) {
    console.error(error);
});
```

### Message Handling

```javascript
// Listen for incoming messages
FirebasexMessaging.onMessageReceived(function(message) {
    console.log("Message received: " + JSON.stringify(message));
}, function(error) {
    console.error(error);
});
```

### Topic Subscriptions

```javascript
FirebasexMessaging.subscribe("news", success, error);
FirebasexMessaging.unsubscribe("news", success, error);
```

### Permissions

```javascript
FirebasexMessaging.hasPermission(function(hasPermission) {
    if (!hasPermission) {
        FirebasexMessaging.grantPermission(function(granted) {
            console.log("Permission granted: " + granted);
        }, function(error) {
            console.error(error);
        });
    }
}, function(error) {
    console.error(error);
});
```

### Notification Channels (Android)

```javascript
FirebasexMessaging.createChannel({
    id: "my_channel",
    name: "My Channel",
    description: "Channel description",
    importance: 4, // IMPORTANCE_HIGH
    sound: "default",
    vibration: true,
    light: true
}, success, error);

FirebasexMessaging.listChannels(function(channels) {
    console.log(JSON.stringify(channels));
}, error);

FirebasexMessaging.deleteChannel("my_channel", success, error);
```

### Badge Management (iOS)

```javascript
FirebasexMessaging.setBadgeNumber(5, success, error);
FirebasexMessaging.getBadgeNumber(function(number) {
    console.log("Badge: " + number);
}, error);
```

### Other

```javascript
// Clear all notifications
FirebasexMessaging.clearAllNotifications(success, error);

// Unregister (delete FCM token)
FirebasexMessaging.unregister(success, error);

// Auto-init control
FirebasexMessaging.isAutoInitEnabled(function(enabled) { ... }, error);
FirebasexMessaging.setAutoInitEnabled(true, success, error);
```
