#import "FirebasexMessagingPlugin.h"
#import "AppDelegate+FirebasexCore.h"
#import "FirebasePluginMessageReceiverManager.h"
#import <Cordova/CDV.h>
@import FirebaseMessaging;
@import UserNotifications;

@implementation FirebasexMessagingPlugin

@synthesize openSettingsCallbackId;
@synthesize notificationCallbackId;
@synthesize tokenRefreshCallbackId;
@synthesize apnsTokenRefreshCallbackId;
@synthesize notificationStack;

static NSString *const LOG_TAG = @"FirebasexMessaging[native]";
static NSInteger const kNotificationStackSize = 32;
static NSString *const FIREBASEX_IOS_FCM_ENABLED = @"FIREBASEX_IOS_FCM_ENABLED";

static FirebasexMessagingPlugin *messagingInstance;
static BOOL registeredForRemoteNotifications = NO;
static BOOL openSettingsEmitted = NO;
static BOOL immediateMessagePayloadDelivery = NO;

+ (FirebasexMessagingPlugin *)instance {
    return messagingInstance;
}

- (void)pluginInitialize {
    NSLog(@"Starting Firebase Messaging plugin");
    messagingInstance = self;

    @try {
        immediateMessagePayloadDelivery = [[[NSBundle mainBundle]
            objectForInfoDictionaryKey:@"FIREBASE_MESSAGING_IMMEDIATE_PAYLOAD_DELIVERY"] boolValue];

        _isFCMEnabled = [[FirebasexCorePlugin sharedInstance]
            getGooglePlistFlagWithDefaultValue:FIREBASEX_IOS_FCM_ENABLED
                                  defaultValue:YES];

        if (!self.isFCMEnabled) {
            [[FirebasexCorePlugin sharedInstance] _logInfo:@"Firebase Cloud Messaging is disabled, see IOS_FCM_ENABLED variable of the plugin"];
        }

        // Set actionable categories if pn-actions.json exist in bundle
        [self setActionableNotifications];

        // Flush pending notifications when app returns to foreground
        [[NSNotificationCenter defaultCenter] addObserver:self
                                                 selector:@selector(onAppDidBecomeActive)
                                                     name:FirebasexAppDidBecomeActive
                                                   object:nil];

        // Check for permission and register for remote notifications if granted
        if (self.isFCMEnabled) {
            [self _hasPermission:^(BOOL result) {
            }];
        }
    } @catch (NSException *exception) {
        [[FirebasexCorePlugin sharedInstance] handlePluginExceptionWithoutContext:exception];
    }
}

- (void)onAppDidBecomeActive {
    [self sendPendingNotifications];
}

// Dynamic actions from pn-actions.json
- (void)setActionableNotifications {
    @try {
        if (!self.isFCMEnabled) {
            return;
        }

        NSString *path = [[NSBundle mainBundle] pathForResource:@"pn-actions" ofType:@"json"];
        NSData *data = [NSData dataWithContentsOfFile:path];
        if (data == nil) return;
        NSDictionary *dict = [NSJSONSerialization JSONObjectWithData:data options:kNilOptions error:nil];

        NSMutableSet *categories = [[NSMutableSet alloc] init];
        NSArray *actionsArray = [dict objectForKey:@"PushNotificationActions"];
        for (NSDictionary *item in actionsArray) {
            NSMutableArray *buttons = [NSMutableArray new];
            NSString *category = [item objectForKey:@"category"];

            NSArray *actions = [item objectForKey:@"actions"];
            for (NSDictionary *action in actions) {
                NSString *actionId = [action objectForKey:@"id"];
                NSString *actionTitle = [action objectForKey:@"title"];
                UNNotificationActionOptions options = UNNotificationActionOptionNone;

                id mode = [action objectForKey:@"foreground"];
                if (mode != nil && (([mode isKindOfClass:[NSString class]] && [mode isEqualToString:@"true"]) || [mode boolValue])) {
                    options |= UNNotificationActionOptionForeground;
                }
                id destructive = [action objectForKey:@"destructive"];
                if (destructive != nil && (([destructive isKindOfClass:[NSString class]] && [destructive isEqualToString:@"true"]) || [destructive boolValue])) {
                    options |= UNNotificationActionOptionDestructive;
                }

                [buttons addObject:[UNNotificationAction actionWithIdentifier:actionId
                                                                        title:NSLocalizedString(actionTitle, nil)
                                                                      options:options]];
            }

            [categories addObject:[UNNotificationCategory categoryWithIdentifier:category
                                                                         actions:buttons
                                                               intentIdentifiers:@[]
                                                                         options:UNNotificationCategoryOptionNone]];
        }

        [[UNUserNotificationCenter currentNotificationCenter] setNotificationCategories:categories];

    } @catch (NSException *exception) {
        [[FirebasexCorePlugin sharedInstance] handlePluginExceptionWithoutContext:exception];
    }
}

#pragma mark - Plugin API

- (void)getToken:(CDVInvokedUrlCommand *)command {
    [self _getToken:^(NSString *token, NSError *error) {
        [[FirebasexCorePlugin sharedInstance] handleStringResultWithPotentialError:error
                                                                          command:command
                                                                           result:token];
    }];
}

- (void)_getToken:(void (^)(NSString *token, NSError *error))completeBlock {
    @try {
        [[FIRMessaging messaging] tokenWithCompletion:^(NSString *token, NSError *error) {
            @try {
                completeBlock(token, error);
            } @catch (NSException *exception) {
                [[FirebasexCorePlugin sharedInstance] handlePluginExceptionWithoutContext:exception];
            }
        }];
    } @catch (NSException *exception) {
        [[FirebasexCorePlugin sharedInstance] handlePluginExceptionWithoutContext:exception];
    }
}

- (void)getAPNSToken:(CDVInvokedUrlCommand *)command {
    CDVPluginResult *pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK
                                                      messageAsString:[self getAPNSToken]];
    [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
}

- (NSString *)getAPNSToken {
    NSString *hexToken = nil;
    NSData *apnsToken = [FIRMessaging messaging].APNSToken;
    if (apnsToken) {
        hexToken = [self hexadecimalStringFromData:apnsToken];
    }
    return hexToken;
}

- (NSString *)hexadecimalStringFromData:(NSData *)data {
    NSUInteger dataLength = data.length;
    if (dataLength == 0) return nil;

    const unsigned char *dataBuffer = data.bytes;
    NSMutableString *hexString = [NSMutableString stringWithCapacity:(dataLength * 2)];
    for (int i = 0; i < dataLength; ++i) {
        [hexString appendFormat:@"%02x", dataBuffer[i]];
    }
    return [hexString copy];
}

- (void)onTokenRefresh:(CDVInvokedUrlCommand *)command {
    self.tokenRefreshCallbackId = command.callbackId;
    NSString *apnsToken = [self getAPNSToken];
    if (apnsToken != nil) {
        [self _getToken:^(NSString *token, NSError *error) {
            if (error == nil && token != nil) {
                [self sendToken:token];
            }
        }];
    }
}

- (void)onApnsTokenReceived:(CDVInvokedUrlCommand *)command {
    self.apnsTokenRefreshCallbackId = command.callbackId;
    @try {
        NSString *apnsToken = [self getAPNSToken];
        if (apnsToken != nil) {
            [self sendApnsToken:apnsToken];
        }
    } @catch (NSException *exception) {
        [[FirebasexCorePlugin sharedInstance] handlePluginExceptionWithContext:exception:command];
    }
}

- (void)onMessageReceived:(CDVInvokedUrlCommand *)command {
    self.notificationCallbackId = command.callbackId;
    [self sendPendingNotifications];
}

- (void)onOpenSettings:(CDVInvokedUrlCommand *)command {
    @try {
        self.openSettingsCallbackId = command.callbackId;
        if (openSettingsEmitted == YES) {
            [[FirebasexCorePlugin sharedInstance] sendPluginSuccessAndKeepCallback:self.openSettingsCallbackId
                                                                          command:self.commandDelegate];
            openSettingsEmitted = NO;
        }
    } @catch (NSException *exception) {
        [[FirebasexCorePlugin sharedInstance] handlePluginExceptionWithContext:exception:command];
    }
}

#pragma mark - Permissions

- (void)hasPermission:(CDVInvokedUrlCommand *)command {
    @try {
        [self _hasPermission:^(BOOL enabled) {
            CDVPluginResult *commandResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK
                                                                messageAsBool:enabled];
            [self.commandDelegate sendPluginResult:commandResult callbackId:command.callbackId];
        }];
    } @catch (NSException *exception) {
        [[FirebasexCorePlugin sharedInstance] handlePluginExceptionWithContext:exception:command];
    }
}

- (void)_hasPermission:(void (^)(BOOL result))completeBlock {
    @try {
        [[UNUserNotificationCenter currentNotificationCenter]
            getNotificationSettingsWithCompletionHandler:^(UNNotificationSettings *_Nonnull settings) {
                @try {
                    BOOL enabled = NO;
                    if (settings.alertSetting == UNNotificationSettingEnabled) {
                        enabled = YES;
                        [self registerForRemoteNotifications];
                    }
                    NSLog(@"_hasPermission: %@", enabled ? @"YES" : @"NO");
                    completeBlock(enabled);
                } @catch (NSException *exception) {
                    [[FirebasexCorePlugin sharedInstance] handlePluginExceptionWithoutContext:exception];
                }
            }];
    } @catch (NSException *exception) {
        [[FirebasexCorePlugin sharedInstance] handlePluginExceptionWithoutContext:exception];
    }
}

- (void)grantPermission:(CDVInvokedUrlCommand *)command {
    NSLog(@"grantPermission");
    @try {
        [self _hasPermission:^(BOOL enabled) {
            @try {
                if (enabled) {
                    NSString *message = @"Permission is already granted - call hasPermission() to check before calling grantPermission()";
                    CDVPluginResult *pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR
                                                                      messageAsString:message];
                    [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
                } else {
                    [UNUserNotificationCenter currentNotificationCenter].delegate =
                        (id<UNUserNotificationCenterDelegate> _Nullable)self;
                    BOOL requestWithProvidesAppNotificationSettings = [[command argumentAtIndex:0] boolValue];
                    UNAuthorizationOptions authOptions = UNAuthorizationOptionAlert | UNAuthorizationOptionSound | UNAuthorizationOptionBadge;
                    if (@available(iOS 12.0, *)) {
                        if (requestWithProvidesAppNotificationSettings) {
                            authOptions = authOptions | UNAuthorizationOptionProvidesAppNotificationSettings;
                        }
                    }

                    [[UNUserNotificationCenter currentNotificationCenter]
                        requestAuthorizationWithOptions:authOptions
                                      completionHandler:^(BOOL granted, NSError *_Nullable error) {
                            @try {
                                NSLog(@"requestAuthorizationWithOptions: granted=%@", granted ? @"YES" : @"NO");
                                if (error == nil && granted) {
                                    [UNUserNotificationCenter currentNotificationCenter].delegate = [AppDelegate instance];
                                    [self registerForRemoteNotifications];
                                }
                                [[FirebasexCorePlugin sharedInstance] handleBoolResultWithPotentialError:error
                                                                                                command:command
                                                                                                 result:granted];
                            } @catch (NSException *exception) {
                                [[FirebasexCorePlugin sharedInstance] handlePluginExceptionWithContext:exception:command];
                            }
                        }];
                }
            } @catch (NSException *exception) {
                [[FirebasexCorePlugin sharedInstance] handlePluginExceptionWithContext:exception:command];
            }
        }];
    } @catch (NSException *exception) {
        [[FirebasexCorePlugin sharedInstance] handlePluginExceptionWithContext:exception:command];
    }
}

- (void)hasCriticalPermission:(CDVInvokedUrlCommand *)command {
    @try {
        [self _hasCriticalPermission:^(BOOL enabled) {
            CDVPluginResult *commandResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK
                                                                messageAsBool:enabled];
            [self.commandDelegate sendPluginResult:commandResult callbackId:command.callbackId];
        }];
    } @catch (NSException *exception) {
        [[FirebasexCorePlugin sharedInstance] handlePluginExceptionWithContext:exception:command];
    }
}

- (void)_hasCriticalPermission:(void (^)(BOOL result))completeBlock {
    @try {
        if (@available(iOS 12.0, *)) {
            [[UNUserNotificationCenter currentNotificationCenter]
                getNotificationSettingsWithCompletionHandler:^(UNNotificationSettings *_Nonnull settings) {
                    @try {
                        BOOL enabled = NO;
                        if (settings.criticalAlertSetting == UNNotificationSettingEnabled) {
                            enabled = YES;
                            [self registerForRemoteNotifications];
                        }
                        NSLog(@"_hasCriticalPermission: %@", enabled ? @"YES" : @"NO");
                        completeBlock(enabled);
                    } @catch (NSException *exception) {
                        [[FirebasexCorePlugin sharedInstance] handlePluginExceptionWithoutContext:exception];
                    }
                }];
        } else {
            completeBlock(NO);
        }
    } @catch (NSException *exception) {
        [[FirebasexCorePlugin sharedInstance] handlePluginExceptionWithoutContext:exception];
    }
}

- (void)grantCriticalPermission:(CDVInvokedUrlCommand *)command {
    NSLog(@"grantCriticalPermission");
    @try {
        if (@available(iOS 12.0, *)) {
            [self _hasCriticalPermission:^(BOOL enabled) {
                @try {
                    if (enabled) {
                        NSString *message = @"Critical permission is already granted - call hasCriticalPermission() to check before calling grantCriticalPermission()";
                        CDVPluginResult *pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR
                                                                          messageAsString:message];
                        [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
                    } else {
                        [UNUserNotificationCenter currentNotificationCenter].delegate =
                            (id<UNUserNotificationCenterDelegate> _Nullable)self;
                        UNAuthorizationOptions authOptions = UNAuthorizationOptionCriticalAlert;

                        [[UNUserNotificationCenter currentNotificationCenter]
                            requestAuthorizationWithOptions:authOptions
                                          completionHandler:^(BOOL granted, NSError *_Nullable error) {
                                @try {
                                    NSLog(@"requestAuthorizationWithOptions: granted=%@", granted ? @"YES" : @"NO");
                                    [[FirebasexCorePlugin sharedInstance] handleBoolResultWithPotentialError:error
                                                                                                    command:command
                                                                                                     result:granted];
                                } @catch (NSException *exception) {
                                    [[FirebasexCorePlugin sharedInstance] handlePluginExceptionWithContext:exception:command];
                                }
                            }];
                    }
                } @catch (NSException *exception) {
                    [[FirebasexCorePlugin sharedInstance] handlePluginExceptionWithContext:exception:command];
                }
            }];
        } else {
            [[FirebasexCorePlugin sharedInstance] handleBoolResultWithPotentialError:nil command:command result:false];
        }
    } @catch (NSException *exception) {
        [[FirebasexCorePlugin sharedInstance] handlePluginExceptionWithContext:exception:command];
    }
}

- (void)registerForRemoteNotifications {
    NSLog(@"registerForRemoteNotifications");
    if (registeredForRemoteNotifications) return;

    [[FirebasexCorePlugin sharedInstance] runOnMainThread:^{
        @try {
            [[UIApplication sharedApplication] registerForRemoteNotifications];
        } @catch (NSException *exception) {
            [[FirebasexCorePlugin sharedInstance] handlePluginExceptionWithoutContext:exception];
        }
        registeredForRemoteNotifications = YES;
    }];
}

#pragma mark - Subscriptions

- (void)subscribe:(CDVInvokedUrlCommand *)command {
    @try {
        NSString *topic = [NSString stringWithFormat:@"%@", [command.arguments objectAtIndex:0]];
        [[FIRMessaging messaging] subscribeToTopic:topic completion:^(NSError *_Nullable error) {
            [[FirebasexCorePlugin sharedInstance] handleEmptyResultWithPotentialError:error command:command];
        }];

        CDVPluginResult *pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK];
        [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
    } @catch (NSException *exception) {
        [[FirebasexCorePlugin sharedInstance] handlePluginExceptionWithContext:exception:command];
    }
}

- (void)unsubscribe:(CDVInvokedUrlCommand *)command {
    @try {
        NSString *topic = [NSString stringWithFormat:@"%@", [command.arguments objectAtIndex:0]];
        [[FIRMessaging messaging] unsubscribeFromTopic:topic completion:^(NSError *_Nullable error) {
            [[FirebasexCorePlugin sharedInstance] handleEmptyResultWithPotentialError:error command:command];
        }];

        CDVPluginResult *pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK];
        [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
    } @catch (NSException *exception) {
        [[FirebasexCorePlugin sharedInstance] handlePluginExceptionWithContext:exception:command];
    }
}

- (void)unregister:(CDVInvokedUrlCommand *)command {
    @try {
        __block NSError *error = nil;
        [[FIRMessaging messaging] deleteTokenWithCompletion:^(NSError *_Nullable deleteTokenError) {
            if (error == nil && deleteTokenError != nil) error = deleteTokenError;
            if ([FIRMessaging messaging].isAutoInitEnabled) {
                [self _getToken:^(NSString *token, NSError *getError) {
                    if (error == nil && getError != nil) error = getError;
                    [[FirebasexCorePlugin sharedInstance] handleEmptyResultWithPotentialError:error command:command];
                }];
            } else {
                [[FirebasexCorePlugin sharedInstance] handleEmptyResultWithPotentialError:error command:command];
            }
        }];
    } @catch (NSException *exception) {
        [[FirebasexCorePlugin sharedInstance] handlePluginExceptionWithContext:exception:command];
    }
}

#pragma mark - Auto-init

- (void)setAutoInitEnabled:(CDVInvokedUrlCommand *)command {
    @try {
        bool enabled = [[command.arguments objectAtIndex:0] boolValue];
        [[FirebasexCorePlugin sharedInstance] runOnMainThread:^{
            @try {
                [FIRMessaging messaging].autoInitEnabled = enabled;
                CDVPluginResult *pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK];
                [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
            } @catch (NSException *exception) {
                [[FirebasexCorePlugin sharedInstance] handlePluginExceptionWithContext:exception:command];
            }
        }];
    } @catch (NSException *exception) {
        [[FirebasexCorePlugin sharedInstance] handlePluginExceptionWithContext:exception:command];
    }
}

- (void)isAutoInitEnabled:(CDVInvokedUrlCommand *)command {
    @try {
        [[FirebasexCorePlugin sharedInstance] runOnMainThread:^{
            @try {
                bool enabled = [FIRMessaging messaging].isAutoInitEnabled;
                CDVPluginResult *commandResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK
                                                                    messageAsBool:enabled];
                [self.commandDelegate sendPluginResult:commandResult callbackId:command.callbackId];
            } @catch (NSException *exception) {
                [[FirebasexCorePlugin sharedInstance] handlePluginExceptionWithContext:exception:command];
            }
        }];
    } @catch (NSException *exception) {
        [[FirebasexCorePlugin sharedInstance] handlePluginExceptionWithContext:exception:command];
    }
}

#pragma mark - Badge

- (void)setBadgeNumber:(CDVInvokedUrlCommand *)command {
    @try {
        int number = [[command.arguments objectAtIndex:0] intValue];
        [[FirebasexCorePlugin sharedInstance] runOnMainThread:^{
            @try {
                [[UIApplication sharedApplication] setApplicationIconBadgeNumber:number];
                CDVPluginResult *pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK];
                [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
            } @catch (NSException *exception) {
                [[FirebasexCorePlugin sharedInstance] handlePluginExceptionWithContext:exception:command];
            }
        }];
    } @catch (NSException *exception) {
        [[FirebasexCorePlugin sharedInstance] handlePluginExceptionWithContext:exception:command];
    }
}

- (void)getBadgeNumber:(CDVInvokedUrlCommand *)command {
    [[FirebasexCorePlugin sharedInstance] runOnMainThread:^{
        @try {
            long badge = [[UIApplication sharedApplication] applicationIconBadgeNumber];
            CDVPluginResult *pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK
                                                              messageAsDouble:badge];
            [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
        } @catch (NSException *exception) {
            [[FirebasexCorePlugin sharedInstance] handlePluginExceptionWithContext:exception:command];
        }
    }];
}

#pragma mark - Notifications

- (void)clearAllNotifications:(CDVInvokedUrlCommand *)command {
    [[FirebasexCorePlugin sharedInstance] runOnMainThread:^{
        @try {
            [[UIApplication sharedApplication] setApplicationIconBadgeNumber:1];
            [[UIApplication sharedApplication] setApplicationIconBadgeNumber:0];
            CDVPluginResult *pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK];
            [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
        } @catch (NSException *exception) {
            [[FirebasexCorePlugin sharedInstance] handlePluginExceptionWithContext:exception:command];
        }
    }];
}

#pragma mark - Channels (iOS stubs)

- (void)createChannel:(CDVInvokedUrlCommand *)command {
    CDVPluginResult *pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK];
    [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
}

- (void)setDefaultChannel:(CDVInvokedUrlCommand *)command {
    CDVPluginResult *pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK];
    [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
}

- (void)deleteChannel:(CDVInvokedUrlCommand *)command {
    CDVPluginResult *pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK];
    [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
}

- (void)listChannels:(CDVInvokedUrlCommand *)command {
    CDVPluginResult *pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK
                                                      messageAsArray:@[]];
    [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
}

#pragma mark - Message Delivery Internal

- (void)sendPendingNotifications {
    if (self.notificationCallbackId != nil && self.notificationStack != nil && [self.notificationStack count]) {
        @try {
            for (NSDictionary *userInfo in self.notificationStack) {
                [self sendNotification:userInfo];
            }
            [self.notificationStack removeAllObjects];
        } @catch (NSException *exception) {
            [[FirebasexCorePlugin sharedInstance] handlePluginExceptionWithoutContext:exception];
        }
    }
}

- (void)sendNotification:(NSDictionary *)userInfo {
    @try {
        if ([FirebasePluginMessageReceiverManager sendNotification:userInfo]) {
            [[FirebasexCorePlugin sharedInstance] _logMessage:@"Message handled by custom receiver"];
            return;
        }
        if (self.notificationCallbackId != nil &&
            ([[AppDelegate instance].applicationInBackground isEqual:@(NO)] || immediateMessagePayloadDelivery)) {
            [[FirebasexCorePlugin sharedInstance] sendPluginDictionaryResultAndKeepCallback:userInfo
                                                                                   command:self.commandDelegate
                                                                                callbackId:self.notificationCallbackId];
        } else {
            if (!self.notificationStack) {
                self.notificationStack = [[NSMutableArray alloc] init];
            }
            [self.notificationStack addObject:userInfo];

            if ([self.notificationStack count] >= kNotificationStackSize) {
                [self.notificationStack removeLastObject];
            }
        }
    } @catch (NSException *exception) {
        [[FirebasexCorePlugin sharedInstance] handlePluginExceptionWithoutContext:exception];
    }
}

- (void)sendToken:(NSString *)token {
    @try {
        if (self.tokenRefreshCallbackId != nil) {
            [[FirebasexCorePlugin sharedInstance] sendPluginStringResultAndKeepCallback:token
                                                                               command:self.commandDelegate
                                                                            callbackId:self.tokenRefreshCallbackId];
        }
    } @catch (NSException *exception) {
        [[FirebasexCorePlugin sharedInstance] handlePluginExceptionWithoutContext:exception];
    }
}

- (void)sendApnsToken:(NSString *)token {
    @try {
        if (self.apnsTokenRefreshCallbackId != nil) {
            [[FirebasexCorePlugin sharedInstance] sendPluginStringResultAndKeepCallback:token
                                                                               command:self.commandDelegate
                                                                            callbackId:self.apnsTokenRefreshCallbackId];
        }
    } @catch (NSException *exception) {
        [[FirebasexCorePlugin sharedInstance] handlePluginExceptionWithoutContext:exception];
    }
}

- (void)sendOpenNotificationSettings {
    @try {
        if (self.openSettingsCallbackId != nil) {
            [[FirebasexCorePlugin sharedInstance] sendPluginSuccessAndKeepCallback:self.openSettingsCallbackId
                                                                          command:self.commandDelegate];
        } else if (openSettingsEmitted != YES) {
            openSettingsEmitted = YES;
        }
    } @catch (NSException *exception) {
        [[FirebasexCorePlugin sharedInstance] handlePluginExceptionWithoutContext:exception];
    }
}

@end
