#import <Cordova/CDV.h>
#import "FirebasexCorePlugin.h"

@import UserNotifications;

/**
 * @interface FirebasexMessagingPlugin
 * @abstract Cordova plugin for Firebase Cloud Messaging on iOS.
 */
@interface FirebasexMessagingPlugin : CDVPlugin

+ (FirebasexMessagingPlugin *)instance;

- (void)sendPendingNotifications;
- (void)sendNotification:(NSDictionary *)userInfo;
- (void)sendToken:(NSString *)token;
- (void)sendApnsToken:(NSString *)token;
- (void)sendOpenNotificationSettings;

// Plugin API
- (void)getToken:(CDVInvokedUrlCommand *)command;
- (void)getAPNSToken:(CDVInvokedUrlCommand *)command;
- (void)onTokenRefresh:(CDVInvokedUrlCommand *)command;
- (void)onApnsTokenReceived:(CDVInvokedUrlCommand *)command;
- (void)onMessageReceived:(CDVInvokedUrlCommand *)command;
- (void)onOpenSettings:(CDVInvokedUrlCommand *)command;
- (void)subscribe:(CDVInvokedUrlCommand *)command;
- (void)unsubscribe:(CDVInvokedUrlCommand *)command;
- (void)unregister:(CDVInvokedUrlCommand *)command;
- (void)hasPermission:(CDVInvokedUrlCommand *)command;
- (void)grantPermission:(CDVInvokedUrlCommand *)command;
- (void)hasCriticalPermission:(CDVInvokedUrlCommand *)command;
- (void)grantCriticalPermission:(CDVInvokedUrlCommand *)command;
- (void)isAutoInitEnabled:(CDVInvokedUrlCommand *)command;
- (void)setAutoInitEnabled:(CDVInvokedUrlCommand *)command;
- (void)setBadgeNumber:(CDVInvokedUrlCommand *)command;
- (void)getBadgeNumber:(CDVInvokedUrlCommand *)command;
- (void)clearAllNotifications:(CDVInvokedUrlCommand *)command;
- (void)createChannel:(CDVInvokedUrlCommand *)command;
- (void)setDefaultChannel:(CDVInvokedUrlCommand *)command;
- (void)deleteChannel:(CDVInvokedUrlCommand *)command;
- (void)listChannels:(CDVInvokedUrlCommand *)command;

@property(nonatomic, readonly) BOOL isFCMEnabled;
@property(nonatomic, copy) NSString *notificationCallbackId;
@property(nonatomic, copy) NSString *openSettingsCallbackId;
@property(nonatomic, copy) NSString *tokenRefreshCallbackId;
@property(nonatomic, copy) NSString *apnsTokenRefreshCallbackId;
@property(nonatomic, retain) NSMutableArray *notificationStack;

@end
