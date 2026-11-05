# R8 rules for the release build.

# kotlinx.serialization ships its own consumer rules; keep the generated serializer of our LRCLIB response DTO.
-keepattributes *Annotation*, InnerClasses
-keep,includedescriptorclasses class io.github.adit0132.rockscreen.data.network.**$$serializer { *; }
-keepclassmembers class io.github.adit0132.rockscreen.data.network.** {
    *** Companion;
}
-keepclasseswithmembers class io.github.adit0132.rockscreen.data.network.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Ktor references an optional SLF4J binder that doesn't exist on Android.
-dontwarn org.slf4j.impl.StaticLoggerBinder
