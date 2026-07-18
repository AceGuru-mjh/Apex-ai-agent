# rage-jni ProGuard rules — keep the JNI bridge API surface.
-keep class com.apex.rage.native.RageNative { *; }
-keep class com.apex.rage.native.NativeCallbacks { *; }
-keep class com.apex.rage.native.RageNativeBridge { *; }
-keep class com.apex.rage.native.** { *; }

# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
