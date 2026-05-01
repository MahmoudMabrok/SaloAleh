# SaloAleh

Kotlin Multiplatform app for Android and iOS.

## Requirements

- JDK 17+
- Android SDK (API 24+)
- Xcode 15+ (iOS only)
- CocoaPods (iOS only): `sudo gem install cocoapods`

## Run Android

```bash
# Build debug APK
./gradlew assembleDebug

# Install on connected device / running emulator
adb install app/build/outputs/apk/debug/app-debug.apk

# Or build + install in one step
./gradlew installDebug
```

## Run iOS (Simulator)

```bash
# 1. Build the KMP shared framework
./gradlew :app:linkDebugFrameworkIosSimulatorArm64

# 2. Build the iOS app (uses CocoaPods workspace)
xcodebuild \
  -workspace iosApp/iosApp.xcworkspace \
  -scheme SaloAleh \
  -configuration Debug \
  -destination 'platform=iOS Simulator,name=iPhone 17' \
  build

# 3. Install and launch on the booted simulator
SIMULATOR_ID=$(xcrun simctl list devices booted | grep -m1 iPhone | sed 's/.*(\(.*\)).*/\1/')
APP_PATH=$(find build/ios-dd/Build/Products/Debug-iphonesimulator -name "SaloAleh.app" 2>/dev/null | head -1)
xcrun simctl install "$SIMULATOR_ID" "$APP_PATH"
xcrun simctl launch "$SIMULATOR_ID" tools.mo3ta.salo
```

> **Tip:** To specify a derived data directory (avoids stale builds), add `-derivedDataPath build/ios-dd` to the `xcodebuild` call.

## Project Structure

```
app/                    # KMP shared + Android source
  src/commonMain/       # Shared Kotlin code
  src/androidMain/      # Android-specific code
  src/iosMain/          # iOS-specific code
iosApp/                 # Native iOS Swift app shell
  iosApp.xcworkspace    # Open this in Xcode (not .xcodeproj)
```
