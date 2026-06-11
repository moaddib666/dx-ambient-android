# =============================================================================
# DX Ambient — R8 / ProGuard rules for the release build.
#
# AGP 8 runs R8 in full mode. Library consumer rules (Hilt, Room, Media3,
# Compose, Coil, Navigation) are merged automatically, so the rules below only
# cover what those don't: kotlinx.serialization for our JSON-persisted models.
# =============================================================================

# --- kotlinx.serialization --------------------------------------------------
# Our domain models (com.dx.ambient.domain.model.**) AND the YouTube API
# response models (com.dx.ambient.youtube.**) are @Serializable. R8 would
# otherwise strip the synthetic $$serializer / Companion members and crash at
# runtime when (de)serializing. These rules are package-agnostic so any future
# @Serializable class is covered automatically.
-keepattributes *Annotation*, InnerClasses

-dontnote kotlinx.serialization.**

# Keep the Companion.serializer() accessor on every @Serializable type.
-keepclassmembers @kotlinx.serialization.Serializable class * {
    *** Companion;
    *** INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}

-if @kotlinx.serialization.Serializable class *
-keepclassmembers class <1>$Companion {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep the generated $$serializer classes and their members/descriptors.
-keep,includedescriptorclasses class **$$serializer { *; }

# Enums participating in serialization keep their values()/valueOf().
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# --- Crash readability ------------------------------------------------------
# Keep source file + line numbers so Play Console / GlitchTip stack traces are
# de-obfuscatable via the mapping.txt uploaded with each release.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
