# ─────────────────────────────────────────────────────────
# Waselak Delivery — ProGuard / R8 Rules
# ─────────────────────────────────────────────────────────

# ── Kotlinx Serialization ────────────────────────────────
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep all @Serializable classes and their serializers
-keep,includedescriptorclasses class net.marllex.waselak.**$$serializer { *; }
-keepclassmembers class net.marllex.waselak.** {
    *** Companion;
}
-keepclasseswithmembers class net.marllex.waselak.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# ── Ktor Client ──────────────────────────────────────────
-keep class io.ktor.** { *; }
-dontwarn io.ktor.**
-keep class kotlinx.coroutines.** { *; }
-dontwarn kotlinx.coroutines.**

# ── Koin DI ──────────────────────────────────────────────
-keep class org.koin.** { *; }
-dontwarn org.koin.**
-keepclassmembers class * {
    @org.koin.core.annotation.* <methods>;
}

# ── Coil Image Loading ───────────────────────────────────
-keep class coil3.** { *; }
-dontwarn coil3.**

# ── SQLDelight ───────────────────────────────────────────
-keep class app.cash.sqldelight.** { *; }
-dontwarn app.cash.sqldelight.**

# ── Sentry ───────────────────────────────────────────────
-keep class io.sentry.** { *; }
-dontwarn io.sentry.**

# ── Google Maps & Location ───────────────────────────────
-keep class com.google.android.gms.maps.** { *; }
-keep class com.google.android.gms.location.** { *; }
-dontwarn com.google.android.gms.**

# ── Compose ──────────────────────────────────────────────
-dontwarn androidx.compose.**
-keep class androidx.compose.** { *; }

# ── BuildConfig ──────────────────────────────────────────
-keep class net.marllex.waselak.config.BuildConfig { *; }

# ── Core Model classes (used in serialization) ───────────
-keep class net.marllex.waselak.core.model.** { *; }
-keep class net.marllex.waselak.core.network.dto.** { *; }

# ── Kotlin ───────────────────────────────────────────────
-dontwarn kotlin.**
-keep class kotlin.Metadata { *; }
-keepclassmembers class kotlin.Metadata {
    public <methods>;
}

# ── Enum classes ─────────────────────────────────────────
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ── DataStore ────────────────────────────────────────────
-keep class androidx.datastore.** { *; }
-dontwarn androidx.datastore.**

# ── General Android ──────────────────────────────────────
-keepattributes Signature
-keepattributes Exceptions
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
