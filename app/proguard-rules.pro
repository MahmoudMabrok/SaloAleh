# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

-keep class com.android.vending.billing.** { *; }

# The on-demand Mushaf module calls these. R8 only sees the base module.
-keep class tools.mo3ta.salo.ui.quran.MushafPageBridge { *; }
-keep class tools.mo3ta.salo.data.quran.PageDwellCounter { *; }

# feature-delivery 2.1.0 has no consumer rules. Keep the classes SplitCompat
# loads by reflection, or the release build crashes when the Mushaf opens.
-keep class com.google.android.play.core.** { *; }
-dontwarn com.google.android.play.core.**
