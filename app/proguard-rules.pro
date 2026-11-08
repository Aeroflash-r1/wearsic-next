# Wearsic Next — ProGuard / R8 rules

# --- JNI bridge: native method holders must never be renamed/removed ----
-keep class com.wearsic.next.engine.RustEngine {
    <methods>;
}
-keep class com.wearsic.next.engine.EngineProbe {
    <methods>;
}
-keepclassmembers class com.wearsic.next.engine.RustEngine {
    native <methods>;
}
-keepclassmembers class com.wearsic.next.engine.EngineProbe {
    native <methods>;
}

# --- kotlinx-serialization models (reflective serializer lookup) ----
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault
-keepclassmembers class com.wearsic.next.engine.** {
    *** Companion;
}
-keepclasseswithmembers class com.wearsic.next.engine.** {
    kotlinx.serialization.KSerializer serializer(...);
}
# Keep generated serializers
-keepnames class com.wearsic.next.engine.**$$serializer { *; }
-keepnames class com.wearsic.next.playback.**$$serializer { *; }

# --- Compose / Wear (handled by AGP default rules, but keep safe) ----
-dontwarn androidx.wear.**
-dontwarn android.media3.**

# Media3 ships its own consumer rules; keep defaults.

# --- Coroutines (usually fine; belt-and-braces) ----
-dontwarn kotlinx.coroutines.**
