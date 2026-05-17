# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in [sdk]/tools/proguard/proguard-android-optimize.txt

# ---------------------------------------------------------------------------
# Hilt — generated DI graph
# ---------------------------------------------------------------------------
-keep class dagger.hilt.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }

# ---------------------------------------------------------------------------
# Room
# ---------------------------------------------------------------------------
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# ---------------------------------------------------------------------------
# Compose
# ---------------------------------------------------------------------------
-keep class androidx.compose.runtime.** { *; }

# ---------------------------------------------------------------------------
# JNA — Vosk talks to libvosk.so entirely through JNA, which binds native
# functions and Structure fields by reflection. R8 must not rename or strip
# any of it, or 語音辨識 crashes at runtime in the release build only.
# JNA also references java.awt.* which does not exist on Android — suppress.
# ---------------------------------------------------------------------------
-keep class com.sun.jna.** { *; }
-keep interface com.sun.jna.** { *; }
-keepclassmembers class * extends com.sun.jna.** { *; }
-dontwarn com.sun.jna.**
-dontwarn java.awt.**

# ---------------------------------------------------------------------------
# Vosk — Java API wrapping the JNA-bound native model.
# ---------------------------------------------------------------------------
-keep class org.vosk.** { *; }
-dontwarn org.vosk.**

# ---------------------------------------------------------------------------
# OpenCC4j — simplified→traditional conversion. Loads dictionary resources by
# classpath name; keep the package whole to be safe.
# ---------------------------------------------------------------------------
-keep class com.github.houbb.** { *; }
-dontwarn com.github.houbb.**

# ---------------------------------------------------------------------------
# kotlinx.serialization — scam_catalog.json / catalog-update / app-update are
# parsed into @Serializable classes. R8 would otherwise strip the generated
# $serializer and Companion members, breaking 詐騙資料庫 parsing in release.
# ---------------------------------------------------------------------------
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**

# Keep every @Serializable model class and its generated serializer intact.
-keep @kotlinx.serialization.Serializable class ** { *; }
-keep class **$$serializer { *; }

# Keep the Companion + serializer() lookup path the runtime relies on.
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}
-if @kotlinx.serialization.Serializable class ** {
    static **$* *;
}
-keepclassmembers class <2>$<3> {
    kotlinx.serialization.KSerializer serializer(...);
}
