# kotlinx.serialization: keep generated serializers for our @Serializable models.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers @kotlinx.serialization.Serializable class io.github.perruzz.stacco.** {
    *** Companion;
    *** INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclasseswithmembers class io.github.perruzz.stacco.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class io.github.perruzz.stacco.**$$serializer { *; }

# Glance instantiates widget actions by class name: keep their no-arg constructor
# (R8 full mode would otherwise strip it and the widget buttons would do nothing).
-keep class * implements androidx.glance.appwidget.action.ActionCallback {
    <init>();
}
