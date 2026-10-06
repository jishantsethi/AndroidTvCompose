# Media3 keeps its renderers/extractors reflectively in a couple of places.
-dontwarn androidx.media3.**
-keep class androidx.media3.exoplayer.** { *; }

# kotlinx.serialization generated serializers.
-keepclassmembers class ** {
    *** Companion;
}
-keepclasseswithmembers class ** {
    kotlinx.serialization.KSerializer serializer(...);
}
