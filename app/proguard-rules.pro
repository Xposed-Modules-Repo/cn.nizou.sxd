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

-keep class cn.nizou.sxd.XposedInit

# --- libxposed API 102 官方规则 ---
-dontwarn io.github.libxposed.annotation.**
-adaptresourcefilecontents META-INF/xposed/java_init.list
-keep,allowoptimization,allowobfuscation public class * extends io.github.libxposed.api.XposedModule {
    public <init>();
}
-keepnames class cn.nizou.sxd.util.XposedHelpers

# --- Compose Material3 混淆规则（宿主注入面板 + 独立 MainActivity 都需要） ---
-dontwarn androidx.compose.**
-keep class androidx.compose.ui.platform.AndroidComposeView { *; }
-keep class androidx.compose.runtime.** { *; }
-keepclassmembers class * {
    @androidx.compose.runtime.Composable <methods>;
}
-keep class cn.nizou.sxd.ui.** { *; }
-keepclassmembers class cn.nizou.sxd.ui.** {
    @androidx.compose.runtime.Composable <methods>;
}