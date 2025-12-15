# Eppo Kotlin SDK - Consumer ProGuard Rules

# Keep public API
-keep class cloud.eppo.kotlin.** { public *; }
-keep interface cloud.eppo.kotlin.** { *; }

# Keep sdk-common-jvm types used in public API
-keep class cloud.eppo.logging.** { *; }
-keep class cloud.eppo.ufc.dto.VariationType { *; }
-keep class cloud.eppo.api.Attributes { *; }

# Keep kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keep,includedescriptorclasses class cloud.eppo.kotlin.**$$serializer { *; }
-keepclassmembers class cloud.eppo.kotlin.** {
    *** Companion;
}
-keepclasseswithmembers class cloud.eppo.kotlin.** {
    kotlinx.serialization.KSerializer serializer(...);
}

-keepclassmembers @kotlinx.serialization.Serializable class ** {
    <fields>;
    <init>(...);
}
