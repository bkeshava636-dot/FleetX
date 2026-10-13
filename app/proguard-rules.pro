# Proguard rules for College Bus Tracker

# OSMDroid
-keep class org.osmdroid.** { *; }
-dontwarn org.osmdroid.**

# Firebase & Play Services
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
-dontwarn com.google.firebase.**
-dontwarn com.google.android.gms.**

# Keep data models and pass models
-keep class com.college.bustracker.data.model.** { *; }
-keep class com.college.bustracker.pass.** { *; }
-keep class androidx.camera.** { *; }
-dontwarn androidx.camera.**

# Strip debug and verbose logs in release builds
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
}

# Remove Kotlin null-check intrinsics in release (reduces APK ~50-100KB)
-assumenosideeffects class kotlin.jvm.internal.Intrinsics {
    static void checkNotNullParameter(...);
    static void checkNotNullExpressionValue(...);
    static void checkReturnedValueIsNotNull(...);
    static void checkFieldIsNotNull(...);
    static void checkParameterIsNotNull(...);
}


