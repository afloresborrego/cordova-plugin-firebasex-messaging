/**
 * Notification channel configuration options (Android only).
 */
interface IChannelOptions {
    id: string;
    name?: string;
    description?: string;
    importance?: number;
    light?: boolean;
    lightColor?: number;
    visibility?: number;
    badge?: boolean;
    sound?: string;
    vibration?: boolean | number[];
    usage?: number;
    streamType?: number;
}

interface FirebasexMessagingPlugin {
    // Token management
    getToken(success: (token: string) => void, error: (err: string) => void): void;
    getAPNSToken(success: (token: string) => void, error: (err: string) => void): void;
    onTokenRefresh(success: (token: string) => void, error: (err: string) => void): void;
    onApnsTokenReceived(success: (token: string) => void, error: (err: string) => void): void;

    // Message handling
    onMessageReceived(success: (message: object) => void, error: (err: string) => void): void;
    onOpenSettings(success: () => void, error: (err: string) => void): void;

    // Subscriptions
    subscribe(topic: string, success: () => void, error: (err: string) => void): void;
    unsubscribe(topic: string, success: () => void, error: (err: string) => void): void;
    unregister(success: () => void, error: (err: string) => void): void;

    // Permissions
    hasPermission(success: (hasPermission: boolean) => void, error: (err: string) => void): void;
    grantPermission(success: (granted: boolean) => void, error: (err: string) => void, requestWithProvidesAppNotificationSettings?: boolean): void;
    hasCriticalPermission(success: (hasPermission: boolean) => void, error: (err: string) => void): void;
    grantCriticalPermission(success: (granted: boolean) => void, error: (err: string) => void): void;

    // Auto-init
    isAutoInitEnabled(success: (enabled: boolean) => void, error: (err: string) => void): void;
    setAutoInitEnabled(enabled: boolean, success: () => void, error: (err: string) => void): void;

    // Badge
    setBadgeNumber(number: number, success: () => void, error: (err: string) => void): void;
    getBadgeNumber(success: (number: number) => void, error: (err: string) => void): void;

    // Notification channels (Android)
    createChannel(options: IChannelOptions, success: () => void, error: (err: string) => void): void;
    setDefaultChannel(options: IChannelOptions, success: () => void, error: (err: string) => void): void;
    deleteChannel(channelID: string, success: () => void, error: (err: string) => void): void;
    listChannels(success: (channels: IChannelOptions[]) => void, error: (err: string) => void): void;

    // Notification management
    clearAllNotifications(success: () => void, error: (err: string) => void): void;
}

declare var FirebasexMessaging: FirebasexMessagingPlugin;
