# FCM Push Notifications Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add FCM push notification support so notifications sent from Firebase Console → topic `general` are received on both Android and iOS.

**Architecture:** Android uses `FirebaseMessagingService` to handle foreground messages and subscribes to topic `general` at app start. iOS uses `@UIApplicationDelegateAdaptor` to wire `MessagingDelegate` + APNs registration into the SwiftUI app, subscribing to `general` on FCM token receipt. Background/killed-state notifications are displayed automatically by the OS on both platforms.

**Tech Stack:** `firebase-messaging` Android SDK 24.1.0, `Firebase/Messaging` CocoaPod ~> 11.0, existing `NotificationChannels` for Android foreground notifications.

---

## File Map

| File | Action | Purpose |
|------|--------|---------|
| `gradle/libs.versions.toml` | Modify | Add `firebaseMessaging` version + library entry |
| `app/build.gradle.kts` | Modify | Add `firebase-messaging` to `androidMain.dependencies` |
| `app/src/androidMain/kotlin/tools/mo3ta/salo/notification/SaloFirebaseMessagingService.kt` | Create | FCM service — handles foreground messages + token refresh |
| `app/src/androidMain/AndroidManifest.xml` | Modify | Register FCM service |
| `app/src/androidMain/kotlin/tools/mo3ta/salo/SaloApplication.kt` | Modify | Subscribe to FCM topic `general` on app start |
| `iosApp/Podfile` | Modify | Add `Firebase/Messaging` pod |
| `iosApp/iosApp/iOSApp.swift` | Modify | Add `AppDelegate` with `MessagingDelegate` + APNs wiring |
| `iosApp/iosApp/iosApp.entitlements` | Modify | Add `aps-environment: development` |
| `iosApp/iosApp.xcodeproj/project.pbxproj` | Modify | Add `CODE_SIGN_ENTITLEMENTS` to Debug build config |

---

## Task 1: Add firebase-messaging Android dependency

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`

- [ ] **Step 1: Add version + library entry to libs.versions.toml**

In the `[versions]` section, after `firebaseAppCheck = "18.0.0"`:

```toml
firebaseMessaging = "24.1.0"
```

In the `[libraries]` section, after `firebase-appcheck-debug`:

```toml
firebase-messaging = { group = "com.google.firebase", name = "firebase-messaging", version.ref = "firebaseMessaging" }
```

- [ ] **Step 2: Add dependency to androidMain.dependencies in app/build.gradle.kts**

Inside the `androidMain.dependencies { }` block (already contains `libs.firebase.analytics`), add:

```kotlin
implementation(libs.firebase.messaging)
```

- [ ] **Step 3: Verify dependency resolves**

```bash
cd /Users/appleworld/Documents/SaloAleh
./gradlew :app:dependencies --configuration androidDebugRuntimeClasspath 2>&1 | grep "firebase-messaging" | head -5
```

Expected: line showing `com.google.firebase:firebase-messaging:24.1.0`

- [ ] **Step 4: Commit**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts
git commit -m "chore: add firebase-messaging dependency for Android"
```

---

## Task 2: Create FCM service and register in manifest

**Files:**
- Create: `app/src/androidMain/kotlin/tools/mo3ta/salo/notification/SaloFirebaseMessagingService.kt`
- Modify: `app/src/androidMain/AndroidManifest.xml`

- [ ] **Step 1: Create SaloFirebaseMessagingService.kt**

Create `app/src/androidMain/kotlin/tools/mo3ta/salo/notification/SaloFirebaseMessagingService.kt`:

```kotlin
package tools.mo3ta.salo.notification

import android.app.PendingIntent
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import tools.mo3ta.salo.MainActivity
import tools.mo3ta.salo.R

class SaloFirebaseMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        // Topic subscription handles delivery — no token storage needed
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val title = message.notification?.title ?: return
        val body = message.notification?.body ?: return
        if (!NotificationManagerCompat.from(this).areNotificationsEnabled()) return

        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_CLEAR_TOP },
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )
        NotificationManagerCompat.from(this).notify(
            NotificationChannels.NOTIF_ID_DAILY,
            NotificationCompat.Builder(this, NotificationChannels.CHANNEL_DAILY)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(body)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .build()
        )
    }
}
```

- [ ] **Step 2: Register service in AndroidManifest.xml**

Inside the `<application>` block, after the `<activity>` closing tag, add:

```xml
<service
    android:name=".notification.SaloFirebaseMessagingService"
    android:exported="false">
    <intent-filter>
        <action android:name="com.google.firebase.MESSAGING_EVENT" />
    </intent-filter>
</service>
```

- [ ] **Step 3: Build debug APK to verify compile**

```bash
cd /Users/appleworld/Documents/SaloAleh
./gradlew :app:assembleDebug 2>&1 | tail -5
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Commit**

```bash
git add app/src/androidMain/kotlin/tools/mo3ta/salo/notification/SaloFirebaseMessagingService.kt \
        app/src/androidMain/AndroidManifest.xml
git commit -m "feat: add FCM service for Android push notifications"
```

---

## Task 3: Subscribe to FCM topic on Android app start

**Files:**
- Modify: `app/src/androidMain/kotlin/tools/mo3ta/salo/SaloApplication.kt`

- [ ] **Step 1: Add topic subscription to SaloApplication.onCreate()**

Replace the full content of `SaloApplication.kt`:

```kotlin
package tools.mo3ta.salo

import android.app.Application
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import com.google.firebase.messaging.FirebaseMessaging

class SaloApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        val appCheck = FirebaseAppCheck.getInstance()
        if (BuildConfig.DEBUG) {
            appCheck.installAppCheckProviderFactory(DebugAppCheckProviderFactory.getInstance())
        } else {
            appCheck.installAppCheckProviderFactory(PlayIntegrityAppCheckProviderFactory.getInstance())
        }
        AndroidAppContext.init(this)
        FirebaseMessaging.getInstance().subscribeToTopic("general")
    }
}
```

- [ ] **Step 2: Build and deploy to emulator**

```bash
cd /Users/appleworld/Documents/SaloAleh
./gradlew :app:assembleDebug 2>&1 | tail -3
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am force-stop tools.mo3ta.salo
adb logcat -c
adb shell am start -n tools.mo3ta.salo/.MainActivity
```

- [ ] **Step 3: Confirm topic subscription in logcat**

```bash
sleep 5 && adb logcat -d 2>/dev/null | grep -i "subscribe\|topic\|general" | head -5
```

Expected: line containing `Successfully subscribed to topic: general` or similar Firebase log.

- [ ] **Step 4: Commit**

```bash
git add app/src/androidMain/kotlin/tools/mo3ta/salo/SaloApplication.kt
git commit -m "feat: subscribe Android app to FCM topic 'general' on start"
```

---

## Task 4: Add Firebase/Messaging pod to iOS

**Files:**
- Modify: `iosApp/Podfile`

- [ ] **Step 1: Add Firebase/Messaging pod**

Replace `iosApp/Podfile` content:

```ruby
platform :ios, '16.0'
use_frameworks!

target 'SaloAleh' do
  pod 'Firebase/Database', '~> 11.0'
  pod 'Firebase/AppCheck', '~> 11.0'
  pod 'Firebase/Messaging', '~> 11.0'
end
```

- [ ] **Step 2: Run pod install**

```bash
cd /Users/appleworld/Documents/SaloAleh/iosApp
pod install 2>&1 | tail -5
```

Expected: `Pod installation complete! There are 3 dependencies from the Podfile`

- [ ] **Step 3: Commit**

```bash
cd /Users/appleworld/Documents/SaloAleh
git add iosApp/Podfile iosApp/Podfile.lock
git commit -m "chore: add Firebase/Messaging pod for iOS push notifications"
```

---

## Task 5: Add APNs entitlement and wire FCM in iOSApp.swift

**Files:**
- Modify: `iosApp/iosApp/iosApp.entitlements`
- Modify: `iosApp/iosApp.xcodeproj/project.pbxproj`
- Modify: `iosApp/iosApp/iOSApp.swift`

- [ ] **Step 1: Add aps-environment to entitlements file**

Replace full content of `iosApp/iosApp/iosApp.entitlements`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
	<key>aps-environment</key>
	<string>development</string>
	<key>com.apple.developer.appattest-environment</key>
	<string>development</string>
</dict>
</plist>
```

- [ ] **Step 2: Add CODE_SIGN_ENTITLEMENTS to Debug build config in project.pbxproj**

In `iosApp/iosApp.xcodeproj/project.pbxproj`, find the Debug build config for the SaloAleh target (section `AA0000000000000000000012 /* Debug */`) and add `CODE_SIGN_ENTITLEMENTS` to its `buildSettings`:

```
CODE_SIGN_ENTITLEMENTS = iosApp/iosApp.entitlements;
```

Place it directly before `CODE_SIGN_STYLE = Automatic;` in that section.

- [ ] **Step 3: Rewrite iOSApp.swift with AppDelegate + MessagingDelegate**

Replace full content of `iosApp/iosApp/iOSApp.swift`:

```swift
import SwiftUI
import FirebaseCore
import FirebaseAppCheck
import FirebaseMessaging
import UserNotifications

class AppDelegate: NSObject, UIApplicationDelegate, MessagingDelegate {

    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        #if DEBUG
        AppCheck.setAppCheckProviderFactory(AppCheckDebugProviderFactory())
        #else
        AppCheck.setAppCheckProviderFactory(AppAttestProviderFactory())
        #endif
        FirebaseApp.configure()

        Messaging.messaging().delegate = self
        UNUserNotificationCenter.current().delegate = self
        application.registerForRemoteNotifications()
        return true
    }

    func application(
        _ application: UIApplication,
        didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data
    ) {
        Messaging.messaging().apnsToken = deviceToken
    }

    func messaging(_ messaging: Messaging, didReceiveRegistrationToken fcmToken: String?) {
        Messaging.messaging().subscribe(toTopic: "general") { _ in }
    }
}

extension AppDelegate: UNUserNotificationCenterDelegate {
    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        willPresent notification: UNNotification,
        withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void
    ) {
        completionHandler([.banner, .sound])
    }
}

@main
struct iOSApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) var delegate

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
```

Note: `UNUserNotificationCenterDelegate` is added so foreground notifications show as banners (otherwise iOS suppresses them silently).

- [ ] **Step 4: Build via make ios**

```bash
cd /Users/appleworld/Documents/SaloAleh
make ios 2>&1 | grep -E "SUCCEEDED|FAILED|error:" | grep -v warning | tail -5
```

Expected: `** BUILD SUCCEEDED **`

- [ ] **Step 5: Confirm FCM token + topic subscription in simulator log**

```bash
xcrun simctl spawn booted log show --last 1m \
  --predicate 'eventMessage contains "general" OR eventMessage contains "FCM" OR eventMessage contains "registration token"' \
  2>/dev/null | grep -iv "log run" | head -10
```

Expected: line containing `Successfully subscribed to topic: general` or FCM token receipt log.

- [ ] **Step 6: Commit**

```bash
git add iosApp/iosApp/iosApp.entitlements \
        iosApp/iosApp.xcodeproj/project.pbxproj \
        iosApp/iosApp/iOSApp.swift
git commit -m "feat: add FCM push notification support for iOS"
```

---

## Task 6: Manual — Upload APNs key to Firebase Console

This task is manual — no code changes.

- [ ] **Step 1: Get APNs Auth Key from Apple Developer Portal**

Go to [developer.apple.com](https://developer.apple.com) → Certificates, Identifiers & Profiles → Keys → Create a new key with "Apple Push Notifications service (APNs)" checked. Download the `.p8` file. Note the Key ID and your Team ID.

- [ ] **Step 2: Upload to Firebase Console**

Firebase Console → Project Settings → Cloud Messaging → Apple app (`com.mo3ta.saloalaihapp`) → APNs Authentication Key → Upload. Provide the `.p8` file, Key ID, and Team ID.

- [ ] **Step 3: Verify in Firebase Console**

The app entry should show a green checkmark next to APNs Authentication Key.

---

## Task 7: End-to-end test — send notification from Firebase Console

- [ ] **Step 1: Send test notification to Android**

With the Android emulator running the debug build:
- Firebase Console → Cloud Messaging → Create your first campaign → Firebase Notification messages
- Notification title: `تذكير` — Body: `اللهم صلِّ على محمد ﷺ`
- Target: Topic → `general`
- Send test message (use the "Send test message" option with the device's FCM registration token first if available, or send to topic)

Expected: notification appears on emulator.

- [ ] **Step 2: Send test notification to iOS**

With iOS simulator running:
- Repeat the same send from Firebase Console
- Expected: notification appears in simulator notification center (simulator supports push via APNs sandbox since iOS 16 simulator)

- [ ] **Step 3: Final commit if any fixes were needed**

```bash
git add -p
git commit -m "fix: FCM end-to-end notification delivery"
```
