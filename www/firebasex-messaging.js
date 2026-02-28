var exec = require('cordova/exec');

var SERVICE = 'FirebasexMessagingPlugin';

// Token management
exports.getToken = function (success, error) {
    exec(success, error, SERVICE, 'getToken', []);
};

exports.getAPNSToken = function (success, error) {
    exec(success, error, SERVICE, 'getAPNSToken', []);
};

exports.onTokenRefresh = function (success, error) {
    exec(success, error, SERVICE, 'onTokenRefresh', []);
};

exports.onApnsTokenReceived = function (success, error) {
    exec(success, error, SERVICE, 'onApnsTokenReceived', []);
};

// Message handling
exports.onMessageReceived = function (success, error) {
    exec(success, error, SERVICE, 'onMessageReceived', []);
};

exports.onOpenSettings = function (success, error) {
    exec(success, error, SERVICE, 'onOpenSettings', []);
};

// Subscriptions
exports.subscribe = function (topic, success, error) {
    exec(success, error, SERVICE, 'subscribe', [topic]);
};

exports.unsubscribe = function (topic, success, error) {
    exec(success, error, SERVICE, 'unsubscribe', [topic]);
};

exports.unregister = function (success, error) {
    exec(success, error, SERVICE, 'unregister', []);
};

// Permissions
exports.hasPermission = function (success, error) {
    exec(success, error, SERVICE, 'hasPermission', []);
};

exports.grantPermission = function (success, error, requestWithProvidesAppNotificationSettings) {
    exec(success, error, SERVICE, 'grantPermission', [requestWithProvidesAppNotificationSettings || false]);
};

exports.hasCriticalPermission = function (success, error) {
    exec(success, error, SERVICE, 'hasCriticalPermission', []);
};

exports.grantCriticalPermission = function (success, error) {
    exec(success, error, SERVICE, 'grantCriticalPermission', []);
};

// Auto-init
exports.isAutoInitEnabled = function (success, error) {
    exec(success, error, SERVICE, 'isAutoInitEnabled', []);
};

exports.setAutoInitEnabled = function (enabled, success, error) {
    exec(success, error, SERVICE, 'setAutoInitEnabled', [enabled]);
};

// Badge
exports.setBadgeNumber = function (number, success, error) {
    exec(success, error, SERVICE, 'setBadgeNumber', [number]);
};

exports.getBadgeNumber = function (success, error) {
    exec(success, error, SERVICE, 'getBadgeNumber', []);
};

// Notification channels (Android)
exports.createChannel = function (options, success, error) {
    exec(success, error, SERVICE, 'createChannel', [options]);
};

exports.setDefaultChannel = function (options, success, error) {
    exec(success, error, SERVICE, 'setDefaultChannel', [options]);
};

exports.deleteChannel = function (channelID, success, error) {
    exec(success, error, SERVICE, 'deleteChannel', [channelID]);
};

exports.listChannels = function (success, error) {
    exec(success, error, SERVICE, 'listChannels', []);
};

// Notification management
exports.clearAllNotifications = function (success, error) {
    exec(success, error, SERVICE, 'clearAllNotifications', []);
};

// Internal callbacks (called from native)
exports._onNotificationReceived = null;
exports._onTokenRefreshCallback = null;
exports._onApnsTokenReceivedCallback = null;
exports._onOpenSettingsCallback = null;
