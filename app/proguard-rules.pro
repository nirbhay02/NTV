# --- JTV ProGuard Rules ---
# IMPORTANT: the app package is com.fenyx.jtv (NOT com.fenyx.jiotv). Using the wrong package here
# previously let R8 strip the @Serializable navigation classes, crashing the release build at launch.

# ── kotlinx.serialization ──────────────────────────────────────────────────────
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

# Keep the serialization runtime
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep @Serializable classes in our package + their generated $$serializer classes.
# The navigation keys (Main/Settings/Login/Player) are serialized by Navigation3 for state saving.
-keep,includedescriptorclasses class com.fenyx.jtv.**$$serializer { *; }
-keepclassmembers class com.fenyx.jtv.** {
    *** Companion;
}
-keepclasseswithmembers class com.fenyx.jtv.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep class com.fenyx.jtv.Main { *; }
-keep class com.fenyx.jtv.Settings { *; }
-keep class com.fenyx.jtv.Login { *; }
-keep class com.fenyx.jtv.Player { *; }

# Data classes used in manual JSON (org.json) parsing — keep names to be safe.
-keep class com.fenyx.jtv.data.Channel { *; }
-keep class com.fenyx.jtv.data.JioApiClient$AuthData { *; }
-keep class com.fenyx.jtv.data.JioApiClient$StreamData { *; }
-keep class com.fenyx.jtv.data.EpgProgram { *; }

# ── NTV AndroidTV (com.jiotv.androidtv) ─────────────────────────────────────────
-keep class com.jiotv.androidtv.** { *; }
-keepclassmembers class com.jiotv.androidtv.** { *; }
-dontwarn com.jiotv.androidtv.**

# Keep Media3 / ExoPlayer components
-keep class androidx.media3.** { *; }
-keepclassmembers class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# Keep Gson models
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
