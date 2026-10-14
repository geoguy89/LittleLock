# LittleLock ProGuard Rules
# Keep Koin-related classes
-keep class org.koin.** { *; }
-keep interface org.koin.** { *; }

# Keep Room-related classes
-keep class com.littlelock.app.data.local.** { *; }
-keep @androidx.room.Entity class *

# Keep data classes
-keepclassmembers class * {
    @androidx.room.* <methods>;
}

# Keep Koin modules
-keep class com.littlelock.app.di.** { *; }

# Keep services
-keep class com.littlelock.app.services.** { *; }
-keep class com.littlelock.app.receivers.** { *; }

# Keep activities
-keep class com.littlelock.app.ui.** { *; }

# OkHttp
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn okhttp3.**
-dontwarn okio.**

# Coroutines
-dontwarn kotlinx.coroutines.**
-keepnames class kotlinx.coroutines.internal.** { *; }
-keepnames class kotlinx.coroutines.** { *; }

# Keep all member classes
-keepclassmembers class * {
    public <init>(...);
}
