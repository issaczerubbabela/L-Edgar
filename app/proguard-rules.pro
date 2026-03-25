# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in the Android SDK tools/proguard directory.

# Retrofit
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.squareup.retrofit2.** { *; }
-keep interface com.squareup.retrofit2.** { *; }
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}

# Gson
-keep class com.google.gson.** { *; }
-keep class * implements com.google.gson.TypeAdapterFactory
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# Room
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.**

# Hilt
-dontwarn dagger.hilt.**
-keep class dagger.hilt.** { *; }

# Data classes (for Gson serialization)
-keep class com.sheetsync.data.** { *; }
