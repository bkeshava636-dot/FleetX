# Proguard rules for College Bus Tracker

# OSMDroid
-keep class org.osmdroid.** { *; }
-dontwarn org.osmdroid.**

# Firebase & Play Services
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
-dontwarn com.google.firebase.**
-dontwarn com.google.android.gms.**
-keep class com.google.android.gms.maps.** { *; }
-dontwarn com.google.android.gms.maps.**
-keep class com.google.maps.android.** { *; }
-dontwarn com.google.maps.android.**

# Keep data models and pass models
-keep class com.college.bustracker.data.model.** { *; }
-keep class com.college.bustracker.pass.** { *; }
-keep class androidx.camera.** { *; }
-dontwarn androidx.camera.**

# ViewModel constructors preservation
-keep class * extends androidx.lifecycle.ViewModel {
    <init>(...);
}
-keep class * extends androidx.lifecycle.AndroidViewModel {
    <init>(...);
}

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


