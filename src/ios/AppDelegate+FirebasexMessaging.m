#import "AppDelegate+FirebasexMessaging.h"
#import "FirebasexMessagingPlugin.h"
#import "FirebasexCorePlugin.h"
#import <objc/runtime.h>

@import UserNotifications;
@import FirebaseMessaging;

NSString *const FirebasexFCMTokenReceived = @"FirebasexFCMTokenReceived";
NSString *const FirebasexAPNSTokenReceived = @"FirebasexAPNSTokenReceived";
NSString *const FirebasexNotificationReceived = @"FirebasexNotificationReceived";
NSString *const FirebasexNotificationTapped = @"FirebasexNotificationTapped";
NSString *const FirebasexNotificationSettings = @"FirebasexNotificationSettings";

static __weak id<UNUserNotificationCenterDelegate> _prevUserNotificationCenterDelegate = nil;
static NSDictionary *mutableUserInfo;

@implementation AppDelegate (FirebasexMessaging)

+ (void)load {
    // Observe core's FirebasexAppDidFinishLaunching to register delegates
    [[NSNotificationCenter defaultCenter] addObserverForName:FirebasexAppDidFinishLaunching
                                                      object:nil
                                                       queue:nil
                                                  usingBlock:^(NSNotification *notification) {
        AppDelegate *self = [AppDelegate instance];
        [self firebasexMessagingSetup];
    }];
}

- (void)firebasexMessagingSetup {
    @try {
        if ([self firebasexMessagingIsFCMEnabled]) {
            _prevUserNotificationCenterDelegate = [UNUserNotificationCenter currentNotificationCenter].delegate;
            [UNUserNotificationCenter currentNotificationCenter].delegate = self;
            [FIRMessaging messaging].delegate = self;
        } else {
            [[FIRMessaging messaging] setAutoInitEnabled:NO];
        }
    } @catch (NSException *exception) {
        [[FirebasexCorePlugin sharedInstance] handlePluginExceptionWithoutContext:exception];
    }
}

- (BOOL)firebasexMessagingIsFCMEnabled {
    return [FirebasexMessagingPlugin instance] != nil ? [FirebasexMessagingPlugin instance].isFCMEnabled : YES;
}

#pragma mark - FIRMessagingDelegate

- (void)messaging:(FIRMessaging *)messaging didReceiveRegistrationToken:(NSString *)fcmToken {
    @try {
        [[FirebasexCorePlugin sharedInstance] _logMessage:[NSString stringWithFormat:@"didReceiveRegistrationToken: %@", fcmToken]];
        [[FirebasexMessagingPlugin instance] sendToken:fcmToken];
    } @catch (NSException *exception) {
        [[FirebasexCorePlugin sharedInstance] handlePluginExceptionWithoutContext:exception];
    }
}

- (void)application:(UIApplication *)application didRegisterForRemoteNotificationsWithDeviceToken:(NSData *)deviceToken {
    if (![self firebasexMessagingIsFCMEnabled]) return;

    [FIRMessaging messaging].APNSToken = deviceToken;
    [[FirebasexCorePlugin sharedInstance] _logMessage:[NSString stringWithFormat:@"didRegisterForRemoteNotificationsWithDeviceToken: %@", deviceToken]];
    [[FirebasexMessagingPlugin instance] sendApnsToken:[[FirebasexMessagingPlugin instance] getAPNSToken]];
}

- (void)application:(UIApplication *)application didFailToRegisterForRemoteNotificationsWithError:(NSError *)error {
    if (![self firebasexMessagingIsFCMEnabled]) return;
    [[FirebasexCorePlugin sharedInstance] _logError:[NSString stringWithFormat:@"didFailToRegisterForRemoteNotificationsWithError: %@", error.description]];
}

#pragma mark - Remote Notifications

- (void)application:(UIApplication *)application
    didReceiveRemoteNotification:(NSDictionary *)userInfo
          fetchCompletionHandler:(void (^)(UIBackgroundFetchResult))completionHandler {

    if (![self firebasexMessagingIsFCMEnabled]) return;

    @try {
        [[FIRMessaging messaging] appDidReceiveMessage:userInfo];
        NSMutableDictionary *mutableInfo = [userInfo mutableCopy];
        NSDictionary *aps = [mutableInfo objectForKey:@"aps"];
        bool isContentAvailable = [self firebasexMessagingIsContentAvailable:userInfo];

        if ([aps objectForKey:@"alert"] != nil) {
            [mutableInfo setValue:@"notification" forKey:@"messageType"];
            NSString *tap;
            if ([self.applicationInBackground isEqual:[NSNumber numberWithBool:YES]] && !isContentAvailable) {
                tap = @"background";
            }
            [mutableInfo setValue:tap forKey:@"tap"];
        } else {
            [mutableInfo setValue:@"data" forKey:@"messageType"];
        }

        [[FirebasexCorePlugin sharedInstance] _logMessage:[NSString stringWithFormat:@"didReceiveRemoteNotification: %@", mutableInfo]];

        completionHandler(UIBackgroundFetchResultNewData);

        if ([self.applicationInBackground isEqual:[NSNumber numberWithBool:YES]] && isContentAvailable) {
            [[FirebasexCorePlugin sharedInstance] _logError:@"didReceiveRemoteNotification: omitting foreground notification as content-available:1 so system notification will be shown"];
        } else {
            [self firebasexMessagingProcessMessageForForegroundNotification:mutableInfo];
        }

        if ([self.applicationInBackground isEqual:[NSNumber numberWithBool:YES]] || !isContentAvailable) {
            [[FirebasexMessagingPlugin instance] sendNotification:mutableInfo];
        }
    } @catch (NSException *exception) {
        [[FirebasexCorePlugin sharedInstance] handlePluginExceptionWithoutContext:exception];
    }
}

- (void)firebasexMessagingProcessMessageForForegroundNotification:(NSDictionary *)messageData {
    bool showForegroundNotification = [messageData objectForKey:@"notification_foreground"];
    if (!showForegroundNotification) return;

    NSString *title = nil;
    NSString *body = nil;
    NSString *sound = nil;
    NSNumber *badge = nil;

    NSDictionary *aps = [messageData objectForKey:@"aps"];
    if ([aps objectForKey:@"alert"] != nil) {
        NSDictionary *alert = [aps objectForKey:@"alert"];
        if ([alert objectForKey:@"title"] != nil) title = [alert objectForKey:@"title"];
        if ([alert objectForKey:@"body"] != nil) body = [alert objectForKey:@"body"];
        if ([aps objectForKey:@"sound"] != nil) sound = [aps objectForKey:@"sound"];
        if ([aps objectForKey:@"badge"] != nil) badge = [aps objectForKey:@"badge"];
    }

    if ([messageData objectForKey:@"notification_title"] != nil) title = [messageData objectForKey:@"notification_title"];
    if ([messageData objectForKey:@"notification_body"] != nil) body = [messageData objectForKey:@"notification_body"];
    if ([messageData objectForKey:@"notification_ios_sound"] != nil) sound = [messageData objectForKey:@"notification_ios_sound"];
    if ([messageData objectForKey:@"notification_ios_badge"] != nil) badge = [messageData objectForKey:@"notification_ios_badge"];

    if (title == nil || body == nil) return;

    [[UNUserNotificationCenter currentNotificationCenter]
        getNotificationSettingsWithCompletionHandler:^(UNNotificationSettings *_Nonnull settings) {
            @try {
                if (settings.alertSetting == UNNotificationSettingEnabled) {
                    UNMutableNotificationContent *objNotificationContent = [[UNMutableNotificationContent alloc] init];
                    objNotificationContent.title = [NSString localizedUserNotificationStringForKey:title arguments:nil];
                    objNotificationContent.body = [NSString localizedUserNotificationStringForKey:body arguments:nil];

                    NSDictionary *alert = [[NSDictionary alloc] initWithObjectsAndKeys:title, @"title", body, @"body", nil];
                    NSMutableDictionary *aps = [[NSMutableDictionary alloc] initWithObjectsAndKeys:alert, @"alert", nil];

                    if (![sound isKindOfClass:[NSString class]] || [sound isEqualToString:@"default"]) {
                        objNotificationContent.sound = [UNNotificationSound defaultSound];
                        [aps setValue:sound forKey:@"sound"];
                    } else if (sound != nil) {
                        objNotificationContent.sound = [UNNotificationSound soundNamed:sound];
                        [aps setValue:sound forKey:@"sound"];
                    }

                    if (badge != nil) {
                        [aps setValue:badge forKey:@"badge"];
                    }

                    NSString *messageType = @"data";
                    if ([messageData objectForKey:@"messageType"] != nil) {
                        messageType = [messageData objectForKey:@"messageType"];
                    }

                    NSDictionary *userInfo = [[NSDictionary alloc]
                        initWithObjectsAndKeys:@"true", @"notification_foreground",
                                               messageType, @"messageType",
                                               aps, @"aps", nil];
                    objNotificationContent.userInfo = userInfo;

                    UNTimeIntervalNotificationTrigger *trigger =
                        [UNTimeIntervalNotificationTrigger triggerWithTimeInterval:0.1f repeats:NO];
                    UNNotificationRequest *request = [UNNotificationRequest
                        requestWithIdentifier:@"local_notification"
                                      content:objNotificationContent
                                      trigger:trigger];
                    [[UNUserNotificationCenter currentNotificationCenter]
                        addNotificationRequest:request
                         withCompletionHandler:^(NSError *_Nullable error) {
                            if (!error) {
                                [[FirebasexCorePlugin sharedInstance] _logMessage:@"Local Notification succeeded"];
                            } else {
                                [[FirebasexCorePlugin sharedInstance] _logError:[NSString stringWithFormat:@"Local Notification failed: %@", error.description]];
                            }
                        }];
                } else {
                    [[FirebasexCorePlugin sharedInstance] _logError:@"processMessageForForegroundNotification: cannot show notification as permission denied"];
                }
            } @catch (NSException *exception) {
                [[FirebasexCorePlugin sharedInstance] handlePluginExceptionWithoutContext:exception];
            }
        }];
}

#pragma mark - UNUserNotificationCenterDelegate

- (void)userNotificationCenter:(UNUserNotificationCenter *)center
    openSettingsForNotification:(UNNotification *)notification {
    @try {
        [[FirebasexMessagingPlugin instance] sendOpenNotificationSettings];
    } @catch (NSException *exception) {
        [[FirebasexCorePlugin sharedInstance] handlePluginExceptionWithoutContext:exception];
    }
}

- (void)userNotificationCenter:(UNUserNotificationCenter *)center
       willPresentNotification:(UNNotification *)notification
         withCompletionHandler:(void (^)(UNNotificationPresentationOptions options))completionHandler {
    @try {
        if (![notification.request.trigger isKindOfClass:UNPushNotificationTrigger.class] &&
            ![notification.request.trigger isKindOfClass:UNTimeIntervalNotificationTrigger.class]) {
            if (_prevUserNotificationCenterDelegate) {
                [_prevUserNotificationCenterDelegate userNotificationCenter:center
                                                    willPresentNotification:notification
                                                      withCompletionHandler:completionHandler];
                return;
            } else {
                [[FirebasexCorePlugin sharedInstance] _logError:@"willPresentNotification: aborting as not a supported UNNotificationTrigger"];
                return;
            }
        }

        [[FIRMessaging messaging] appDidReceiveMessage:notification.request.content.userInfo];
        NSMutableDictionary *mutableInfo = [notification.request.content.userInfo mutableCopy];

        NSString *messageType = [mutableInfo objectForKey:@"messageType"];
        if (![messageType isEqualToString:@"data"]) {
            [mutableInfo setValue:@"notification" forKey:@"messageType"];
        }

        [[FirebasexCorePlugin sharedInstance] _logMessage:[NSString stringWithFormat:@"willPresentNotification: %@", mutableInfo]];

        NSDictionary *aps = [mutableInfo objectForKey:@"aps"];
        bool showForegroundNotification = [mutableInfo objectForKey:@"notification_foreground"];
        bool hasAlert = [aps objectForKey:@"alert"] != nil;
        bool hasBadge = [aps objectForKey:@"badge"] != nil;
        bool hasSound = [aps objectForKey:@"sound"] != nil;
        bool isContentAvailable = [self firebasexMessagingIsContentAvailable:mutableInfo];

        if (showForegroundNotification && isContentAvailable) {
            [[FirebasexCorePlugin sharedInstance] _logMessage:[NSString stringWithFormat:@"willPresentNotification: foreground notification alert=%@, badge=%@, sound=%@",
                hasAlert ? @"YES" : @"NO", hasBadge ? @"YES" : @"NO", hasSound ? @"YES" : @"NO"]];

            UNNotificationPresentationOptions options = 0;
            if (hasAlert) options |= UNNotificationPresentationOptionAlert;
            if (hasBadge) options |= UNNotificationPresentationOptionBadge;
            if (hasSound) options |= UNNotificationPresentationOptionSound;
            if (options > 0) completionHandler(options);
        } else {
            [[FirebasexCorePlugin sharedInstance] _logMessage:@"willPresentNotification: foreground notification not set"];
        }

        if (![messageType isEqualToString:@"data"]) {
            [[FirebasexMessagingPlugin instance] sendNotification:mutableInfo];
        }
    } @catch (NSException *exception) {
        [[FirebasexCorePlugin sharedInstance] handlePluginExceptionWithoutContext:exception];
    }
}

- (void)userNotificationCenter:(UNUserNotificationCenter *)center
    didReceiveNotificationResponse:(UNNotificationResponse *)response
             withCompletionHandler:(void (^)(void))completionHandler {
    @try {
        if (![response.notification.request.trigger isKindOfClass:UNPushNotificationTrigger.class] &&
            ![response.notification.request.trigger isKindOfClass:UNTimeIntervalNotificationTrigger.class]) {
            if (_prevUserNotificationCenterDelegate) {
                [_prevUserNotificationCenterDelegate userNotificationCenter:center
                                                didReceiveNotificationResponse:response
                                                         withCompletionHandler:completionHandler];
                return;
            } else {
                [[FirebasexCorePlugin sharedInstance] _logMessage:@"didReceiveNotificationResponse: aborting as not a supported UNNotificationTrigger"];
                return;
            }
        }

        [[FIRMessaging messaging] appDidReceiveMessage:response.notification.request.content.userInfo];
        NSMutableDictionary *mutableInfo = [response.notification.request.content.userInfo mutableCopy];

        NSString *tap;
        if ([self.applicationInBackground isEqual:[NSNumber numberWithBool:YES]]) {
            tap = @"background";
        } else {
            tap = @"foreground";
        }
        [mutableInfo setValue:tap forKey:@"tap"];
        if ([mutableInfo objectForKey:@"messageType"] == nil) {
            [mutableInfo setValue:@"notification" forKey:@"messageType"];
        }

        // Dynamic Actions
        if (response.actionIdentifier && ![response.actionIdentifier isEqual:UNNotificationDefaultActionIdentifier]) {
            [mutableInfo setValue:response.actionIdentifier forKey:@"action"];
        }

        [[FirebasexCorePlugin sharedInstance] _logInfo:[NSString stringWithFormat:@"didReceiveNotificationResponse: %@", mutableInfo]];
        [[FirebasexMessagingPlugin instance] sendNotification:mutableInfo];
        completionHandler();
    } @catch (NSException *exception) {
        [[FirebasexCorePlugin sharedInstance] handlePluginExceptionWithoutContext:exception];
    }
}

#pragma mark - Helpers

- (bool)firebasexMessagingIsContentAvailable:(NSDictionary *)userInfo {
    if (userInfo == nil) return false;
    NSDictionary *aps = [userInfo objectForKey:@"aps"];
    if (aps == nil) return false;

    return ([aps objectForKey:@"'content-available'"] != nil &&
            [[aps objectForKey:@"'content-available'"] isEqualToNumber:[NSNumber numberWithInt:1]]) ||
           ([aps objectForKey:@"content-available"] != nil &&
            [[aps objectForKey:@"content-available"] isEqualToNumber:[NSNumber numberWithInt:1]]);
}

@end
