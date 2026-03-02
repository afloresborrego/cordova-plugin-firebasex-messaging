/**
 * @file AppDelegate+FirebasexMessaging.h
 * @brief AppDelegate category that integrates Firebase Cloud Messaging with the application lifecycle.
 *
 * Configures UNUserNotificationCenter and FIRMessaging delegates, handles incoming
 * remote notifications, manages foreground notification display, and processes
 * notification tap responses.
 */

#import "AppDelegate.h"
#import "AppDelegate+FirebasexCore.h"

@import UserNotifications;
@import FirebaseMessaging;

/**
 * NSNotification names posted for FCM lifecycle events.
 * These notifications allow other parts of the app to observe messaging events.
 */
/** Posted when an FCM registration token is received or refreshed. */
extern NSString *const FirebasexFCMTokenReceived;
/** Posted when an APNs device token is received. */
extern NSString *const FirebasexAPNSTokenReceived;
/** Posted when a remote notification is received. */
extern NSString *const FirebasexNotificationReceived;
/** Posted when a notification is tapped by the user. */
extern NSString *const FirebasexNotificationTapped;
/** Posted when the user opens notification settings (iOS 12+). */
extern NSString *const FirebasexNotificationSettings;

/**
 * AppDelegate category for Firebase Cloud Messaging.
 *
 * Registers as FIRMessagingDelegate and UNUserNotificationCenterDelegate to handle
 * FCM token events, remote notification delivery, foreground notification display,
 * and notification tap responses including actionable notifications.
 */
@interface AppDelegate (FirebasexMessaging) <UNUserNotificationCenterDelegate, FIRMessagingDelegate>
@end
