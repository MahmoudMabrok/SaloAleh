SIMULATOR_ID := 79A4F6A5-F198-4CD3-9AD2-7ED68EFBABF1
BUNDLE_ID    := com.mo3ta.saloalaihapp
WORKSPACE    := iosApp/iosApp.xcworkspace
SCHEME       := SaloAleh
DESTINATION  := platform=iOS Simulator,id=$(SIMULATOR_ID)

# Resolve build dir dynamically each run
BUILD_DIR = $(shell xcodebuild \
	-workspace $(WORKSPACE) \
	-scheme $(SCHEME) \
	-configuration Debug \
	-destination '$(DESTINATION)' \
	-showBuildSettings 2>/dev/null \
	| awk '/CONFIGURATION_BUILD_DIR/{print $$NF}' | head -1)

APP_PATH = $(BUILD_DIR)/SaloAleh.app

.PHONY: ios android

ios:
	./gradlew :app:linkDebugFrameworkIosSimulatorArm64
	rm -rf "$(APP_PATH)"
	xcodebuild \
		-workspace $(WORKSPACE) \
		-scheme $(SCHEME) \
		-configuration Debug \
		-destination '$(DESTINATION)' \
		build
	xcrun simctl install $(SIMULATOR_ID) "$(APP_PATH)"
	xcrun simctl launch $(SIMULATOR_ID) $(BUNDLE_ID)

android:
	./gradlew assembleDebug
	adb install -r app/build/outputs/apk/debug/app-debug.apk
	adb shell am start -n tools.mo3ta.salo/tools.mo3ta.salo.MainActivity
