# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in the Android SDK tools/proguard directory.

# ---- Retrofit / JSON adapters ----
-keepattributes Signature
-keepattributes RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations,AnnotationDefault

# Retrofit interfaces and HTTP method annotations.
-keep class retrofit2.** { *; }
-keep interface retrofit2.** { *; }
-keepclasseswithmembers interface * {
    @retrofit2.http.* <methods>;
}

# DTOs used by network layer (Gson/Moshi serialization safety).
-keep class com.issaczerubbabel.ledgar.data.remote.** { *; }

# Gson field mapping annotations.
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# Gson's TypeToken reads its type argument from the generic signature of the anonymous subclass
# (object : TypeToken<Map<String, String>>() {}). R8 strips that signature unless the subclass is
# kept, and Gson then throws "TypeToken must be created with a type argument" at runtime. Auto-capture
# uses this for the keyword list, the category mapping and the notification trace.
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken

# Moshi generated adapters and annotated models (safe even if Moshi is introduced).
-keep @com.squareup.moshi.JsonClass class * { *; }

# ---- Room ----
# Keep Room database, entities, DAOs and generated implementation classes.
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }
-keep class *_Impl { *; }
-dontwarn androidx.room.**

# ---- DataStore Preferences ----
# Keep static preference keys to prevent key name/field stripping.
-keepclassmembers class * {
    public static final androidx.datastore.preferences.core.Preferences$Key *;
}

# ---- Hilt ----
-dontwarn dagger.hilt.**
-keep class dagger.hilt.** { *; }
