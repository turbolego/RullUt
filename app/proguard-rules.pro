# Add project specific ProGuard rules here.
# MapLibre Native rules
-keep class org.maplibre.android.** { *; }
-dontwarn org.maplibre.android.**

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**

# Kotlin serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.turbolego.rullut.**$$serializer { *; }
-keepclassmembers class com.turbolego.rullut.** {
    *** Companion;
}
-keepclasseswithmembers class com.turbolego.rullut.** {
    kotlinx.serialization.KSerializer serializer(...);
}