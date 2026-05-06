# FCM Push Notifications Design

## Goal

Enable Firebase Cloud Messaging (FCM) so push notifications can be sent manually from Firebase Console to all app users on both Android and iOS.

## Approach

All devices subscribe to a single FCM topic (`general`) on first launch. Notifications are sent from Firebase Console → Cloud Messaging → target topic `general`. Tapping a notification opens the app with default behavior — no deep linking or custom routing.

No commonMain changes. All FCM wiring is platform-specific.

## Android

**Dependencies:**
- Add `firebase-messaging` to `gradle/libs.versions.toml` and `app/build.gradle.kts` (androidMain)

**Service:**
- Create `app/src/androidMain/.../notification/SaloFirebaseMessagingService.kt`
- Extends `FirebaseMessagingService`
- Overrides `onNewToken(token)` — no-op (topic subscription does not require token handling)
- Register in `app/src/androidMain/AndroidManifest.xml` with intent filter `com.google.firebase.MESSAGING_EVENT`

**Topic subscription:**
- Subscribe to topic `general` in `SaloApplication.onCreate()` via `FirebaseMessaging.getInstance().subscribeToTopic("general")`

**Background notifications:**
- FCM SDK displays notifications automatically when app is in background or killed — no extra code needed

**Foreground notifications:**
- Override `onMessageReceived` in `SaloFirebaseMessagingService` to post a local notification using existing `NotificationChannels.CHANNEL_DAILY` channel

## iOS

**Pod:**
- Add `Firebase/Messaging` to `iosApp/Podfile`
- Run `pod install`

**Entitlements:**
- Add `aps-environment` entitlement to `iosApp/iosApp/iosApp.entitlements` (value: `development`)
- Add `CODE_SIGN_ENTITLEMENTS` back to Debug build config in `project.pbxproj` (push works on simulator with sandbox APNs)

**App delegate wiring in `iOSApp.swift`:**
- Register for remote notifications: `UIApplication.shared.registerForRemoteNotifications()`
- Set `Messaging.messaging().delegate = appDelegate`
- Implement `MessagingDelegate.messaging(_:didReceiveRegistrationToken:)` — subscribe to topic `general`
- `UNUserNotificationCenter` delegate not needed (default open-app behavior is sufficient)

**Note:** `iOSApp` struct needs to become a class-based `AppDelegate` pattern (or use `@UIApplicationDelegateAdaptor`) to implement `MessagingDelegate` and `UIApplicationDelegate` methods.

## Firebase Console Manual Step

Before notifications can be delivered to iOS devices, upload the APNs key:
- Firebase Console → Project Settings → Cloud Messaging → Apple app → APNs Authentication Key → upload `.p8` key from Apple Developer Portal

## Sending a Notification

Firebase Console → Cloud Messaging → New campaign → Notification → target: Topic → `general` → Send.

## Out of Scope

- Deep linking / custom screen routing on tap
- Per-user targeting (no token storage)
- Backend server sending (manual console only)
- Notification preferences UI changes
