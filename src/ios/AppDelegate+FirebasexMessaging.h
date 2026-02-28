#import "AppDelegate.h"
#import "AppDelegate+FirebasexCore.h"

@import UserNotifications;
@import FirebaseMessaging;

/**
 * NSNotification names for messaging events.
 */
extern NSString *const FirebasexFCMTokenReceived;
extern NSString *const FirebasexAPNSTokenReceived;
extern NSString *const FirebasexNotificationReceived;
extern NSString *const FirebasexNotificationTapped;
extern NSString *const FirebasexNotificationSettings;

/**
 * AppDelegate category for Firebase Cloud Messaging.
 * Registers as FIRMessagingDelegate and UNUserNotificationCenterDelegate.
 */
@interface AppDelegate (FirebasexMessaging) <UNUserNotificationCenterDelegate, FIRMessagingDelegate>
@end
