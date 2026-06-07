# Keep kotlinx.serialization for the JSON-persisted domain models.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class **$$serializer { *; }
-keep,includedescriptorclasses class com.dx.ambient.**$$serializer { *; }
-keepclassmembers class com.dx.ambient.domain.model.** {
    *** Companion;
}
-keepclasseswithmembers class com.dx.ambient.domain.model.** {
    kotlinx.serialization.KSerializer serializer(...);
}
