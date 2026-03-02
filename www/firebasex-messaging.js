/**
 * @fileoverview Firebase Cloud Messaging (FCM) interface for Cordova.
 * Provides APIs for push notification token management, message handling,
 * topic subscriptions, notification permissions, badge control, and
 * Android notification channels.
 * @module firebasex-messaging
 */
var exec = require('cordova/exec');

/** @private */
var SERVICE = 'FirebasexMessagingPlugin';

/**
 * Normalises a value to a strict boolean.
 * @private
 * @param {*} value - The value to normalise.
 * @returns {boolean}
 */
function ensureBoolean(value) {
    if (value === "true" || value === true) return true;
    if (value === "false" || value === false) return false;
    return !!value;
}

/**
 * Wraps a callback so its argument is normalised to a strict boolean.
 * @private
 * @param {Function} fn - The callback to wrap.
 * @returns {Function} Wrapped callback.
 */
function ensureBooleanFn(fn) {
    return function (value) {
        return fn(ensureBoolean(value));
    };
}

// Token management

/**
 * Retrieves the current FCM registration token.
 * @param {Function} success - Called with the token string.
 * @param {Function} error - Called on failure.
 */
exports.getToken = function (success, error) {
    exec(success, error, SERVICE, 'getToken', []);
};

/**
 * Retrieves the APNs device token (iOS only).
 * On Android this returns null.
 * @param {Function} success - Called with the APNs token string or null.
 * @param {Function} error - Called on failure.
 */
exports.getAPNSToken = function (success, error) {
    exec(success, error, SERVICE, 'getAPNSToken', []);
};

/**
 * Registers a callback invoked whenever the FCM token is refreshed.
 * The callback is kept alive and fires each time a new token is issued.
 * @param {Function} success - Called with the new token string.
 * @param {Function} error - Called on failure.
 */
exports.onTokenRefresh = function (success, error) {
    exec(success, error, SERVICE, 'onTokenRefresh', []);
};

/**
 * Registers a callback invoked when the APNs token is received or refreshed (iOS only).
 * On Android this is a no-op.
 * @param {Function} success - Called with the APNs token string.
 * @param {Function} error - Called on failure.
 */
exports.onApnsTokenReceived = function (success, error) {
    exec(success, error, SERVICE, 'onApnsTokenReceived', []);
};

// Message handling

/**
 * Registers a callback invoked when a push notification or data message is received.
 * The callback is kept alive and fires for every incoming message.
 * The message object includes `messageType` ("notification" or "data"),
 * `tap` ("background" or "foreground" if tapped, absent otherwise), and
 * all payload key/value pairs.
 * @param {Function} success - Called with the message object.
 * @param {Function} error - Called on failure.
 */
exports.onMessageReceived = function (success, error) {
    exec(success, error, SERVICE, 'onMessageReceived', []);
};

/**
 * Registers a callback invoked when the user taps the notification settings
 * action in the system notification settings (iOS 12+ only).
 * Requires `UNAuthorizationOptionProvidesAppNotificationSettings`.
 * @param {Function} success - Called when the user opens notification settings.
 * @param {Function} error - Called on failure.
 */
exports.onOpenSettings = function (success, error) {
    exec(success, error, SERVICE, 'onOpenSettings', []);
};

// Subscriptions

/**
 * Subscribes to an FCM topic to receive topic-targeted messages.
 * @param {string} topic - The topic name to subscribe to.
 * @param {Function} success - Called on successful subscription.
 * @param {Function} error - Called on failure.
 */
exports.subscribe = function (topic, success, error) {
    exec(success, error, SERVICE, 'subscribe', [topic]);
};

/**
 * Unsubscribes from an FCM topic.
 * @param {string} topic - The topic name to unsubscribe from.
 * @param {Function} success - Called on successful unsubscription.
 * @param {Function} error - Called on failure.
 */
exports.unsubscribe = function (topic, success, error) {
    exec(success, error, SERVICE, 'unsubscribe', [topic]);
};

/**
 * Deletes the current FCM token and, if auto-init is enabled, requests a new one.
 * Use this to force a token refresh or to stop receiving messages.
 * @param {Function} success - Called on success.
 * @param {Function} error - Called on failure.
 */
exports.unregister = function (success, error) {
    exec(success, error, SERVICE, 'unregister', []);
};

// Permissions

/**
 * Checks whether the app has notification permission.
 * @param {Function} success - Called with `true` if permission is granted, `false` otherwise.
 * @param {Function} error - Called on failure.
 */
exports.hasPermission = function (success, error) {
    exec(ensureBooleanFn(success), error, SERVICE, 'hasPermission', []);
};

/**
 * Requests notification permission from the user.
 * On Android 13+ this requests the POST_NOTIFICATIONS runtime permission.
 * On iOS this presents the system permission dialog.
 * @param {Function} success - Called with `true` if permission was granted.
 * @param {Function} error - Called on failure (e.g. permission already granted).
 * @param {boolean} [requestWithProvidesAppNotificationSettings=false] - iOS 12+ only:
 *   if `true`, includes `UNAuthorizationOptionProvidesAppNotificationSettings` so
 *   the system shows an in-app settings button in notification settings.
 */
exports.grantPermission = function (success, error, requestWithProvidesAppNotificationSettings) {
    exec(ensureBooleanFn(success), error, SERVICE, 'grantPermission', [ensureBoolean(requestWithProvidesAppNotificationSettings)]);
};

/**
 * Checks whether the app has critical alert permission (iOS 12+ only).
 * On Android this always returns `false`.
 * @param {Function} success - Called with `true` if critical alert permission is granted.
 * @param {Function} error - Called on failure.
 */
exports.hasCriticalPermission = function (success, error) {
    exec(ensureBooleanFn(success), error, SERVICE, 'hasCriticalPermission', []);
};

/**
 * Requests critical alert permission (iOS 12+ only).
 * Critical alerts bypass Do Not Disturb and the ringer switch.
 * Requires a special Apple entitlement.
 * On Android this is a no-op and returns `false`.
 * @param {Function} success - Called with `true` if permission was granted.
 * @param {Function} error - Called on failure.
 */
exports.grantCriticalPermission = function (success, error) {
    exec(ensureBooleanFn(success), error, SERVICE, 'grantCriticalPermission', []);
};

// Auto-init

/**
 * Checks whether FCM auto-initialization is enabled.
 * When enabled, the SDK automatically generates an FCM token on app startup.
 * @param {Function} success - Called with `true` if auto-init is enabled.
 * @param {Function} error - Called on failure.
 */
exports.isAutoInitEnabled = function (success, error) {
    exec(success, error, SERVICE, 'isAutoInitEnabled', []);
};

/**
 * Enables or disables FCM auto-initialization.
 * When disabled, no FCM token is generated until explicitly requested.
 * @param {boolean} enabled - `true` to enable, `false` to disable.
 * @param {Function} success - Called on success.
 * @param {Function} error - Called on failure.
 */
exports.setAutoInitEnabled = function (enabled, success, error) {
    exec(success, error, SERVICE, 'setAutoInitEnabled', [!!enabled]);
};

// Badge

/**
 * Sets the app icon badge number (iOS only).
 * On Android this is a no-op.
 * @param {number} number - The badge number to display.
 * @param {Function} success - Called on success.
 * @param {Function} error - Called on failure.
 */
exports.setBadgeNumber = function (number, success, error) {
    exec(success, error, SERVICE, 'setBadgeNumber', [number]);
};

/**
 * Gets the current app icon badge number (iOS only).
 * On Android this returns 0.
 * @param {Function} success - Called with the current badge number.
 * @param {Function} error - Called on failure.
 */
exports.getBadgeNumber = function (success, error) {
    exec(success, error, SERVICE, 'getBadgeNumber', []);
};

// Notification channels (Android)

/**
 * Creates or updates a notification channel (Android 8+ only).
 * On iOS this is a no-op.
 * @param {Object} options - Channel configuration with properties:
 *   `id` (string, required), `name` (string, required),
 *   `importance` (string: "none"|"min"|"low"|"default"|"high"|"max"),
 *   `description` (string), `sound` (string), `vibration` (boolean|number[]),
 *   `light` (boolean), `lightColor` (string), `badge` (boolean),
 *   `visibility` (number: -1|0|1), `usage` (number), `streamType` (number).
 * @param {Function} success - Called on success.
 * @param {Function} error - Called on failure.
 */
exports.createChannel = function (options, success, error) {
    exec(success, error, SERVICE, 'createChannel', [options]);
};

/**
 * Sets the default notification channel for FCM notifications (Android 8+ only).
 * On iOS this is a no-op.
 * @param {Object} options - Channel configuration (same as {@link createChannel}).
 * @param {Function} success - Called on success.
 * @param {Function} error - Called on failure.
 */
exports.setDefaultChannel = function (options, success, error) {
    exec(success, error, SERVICE, 'setDefaultChannel', [options]);
};

/**
 * Deletes a notification channel by its ID (Android 8+ only).
 * On iOS this is a no-op.
 * @param {string} channelID - The channel ID to delete.
 * @param {Function} success - Called on success.
 * @param {Function} error - Called on failure.
 */
exports.deleteChannel = function (channelID, success, error) {
    exec(success, error, SERVICE, 'deleteChannel', [channelID]);
};

/**
 * Lists all notification channels (Android 8+ only).
 * On iOS the success callback receives an empty array.
 * @param {Function} success - Called with an array of channel objects.
 * @param {Function} error - Called on failure.
 */
exports.listChannels = function (success, error) {
    exec(success, error, SERVICE, 'listChannels', []);
};

// Notification management

/**
 * Removes all delivered notifications from the notification centre/shade.
 * @param {Function} success - Called on success.
 * @param {Function} error - Called on failure.
 */
exports.clearAllNotifications = function (success, error) {
    exec(success, error, SERVICE, 'clearAllNotifications', []);
};

// Internal callbacks (called from native)
/** @private Called by native code when a notification is received. */
exports._onNotificationReceived = null;
/** @private Called by native code when the FCM token is refreshed. */
exports._onTokenRefreshCallback = null;
/** @private Called by native code when the APNs token is received. */
exports._onApnsTokenReceivedCallback = null;
/** @private Called by native code when the user opens notification settings. */
exports._onOpenSettingsCallback = null;
