SIMULATOR_ID := 79A4F6A5-F198-4CD3-9AD2-7ED68EFBABF1
BUNDLE_ID    := com.mo3ta.saloalaihapp
DERIVED_DATA := $(HOME)/Library/Developer/Xcode/DerivedData/iosApp-gotjmnfluldsuwfqumdprqjzjxrt/Build/Products/Debug-iphonesimulator/SaloAleh.app

.PHONY: ios android

ios:
	./gradlew :app:linkDebugFrameworkIosSimulatorArm64
	xcodebuild \
		-workspace iosApp/iosApp.xcworkspace \
		-scheme SaloAleh \
		-configuration Debug \
		-destination 'platform=iOS Simulator,id=$(SIMULATOR_ID)' \
		build
	xcrun simctl install $(SIMULATOR_ID) "$(DERIVED_DATA)"
	xcrun simctl launch $(SIMULATOR_ID) $(BUNDLE_ID)

android:
	./gradlew assembleDebug
	adb install -r app/build/outputs/apk/debug/app-debug.apk
	adb shell am start -n tools.mo3ta.salo/tools.mo3ta.salo.MainActivity
