# Keep LSPosed HiddenApiBypass and its native/reflective hooks
-keep class org.lsposed.hiddenapibypass.** { *; }
-dontwarn org.lsposed.hiddenapibypass.**

# Keep Android framework reflection targets used for backdrop blur, status bar, and Bluetooth battery
-dontwarn android.view.ViewRootImpl
-dontwarn com.android.internal.graphics.drawable.BackgroundBlurDrawable
-dontwarn android.app.StatusBarManager

# Keep enum values serialized by name in SharedPreferences and JSON backups
-keepclassmembers enum ca.saboor.larpdot.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Preserve Compose & Kotlin metadata needed by runtime inspection
-keepattributes Signature,InnerClasses,EnclosingMethod
-keepattributes RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations
