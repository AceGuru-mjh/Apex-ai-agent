# ProGuard rules

# ============================================================
# PERF-47: R8 minification keep rules
# (release isMinifyEnabled=true + isShrinkResources=true)
# ============================================================

# Hilt generated code
-keep class dagger.hilt.** { *; }
-keep class * extends dagger.hilt.android.HiltAndroidApp { *; }
-keep @dagger.hilt.android.HiltAndroidApp class * { *; }
-keep @dagger.hilt.android.AndroidEntryPoint class * { *; }
-keep @dagger.hilt.android.lifecycle.HiltViewModel class * { *; }
-keepclassmembers class * { @dagger.hilt.android.lifecycle.HiltViewModel <init>(...); }

# JNI — rage-native
-keep class com.apex.rage.nativelib.** { *; }
-keepclassmembers class com.apex.rage.nativelib.** { *; }

# JNI — ai-terminal
-keep class com.ai.assistance.aiterminal.terminal.TerminalJni { *; }
-keep class com.ai.assistance.aiterminal.terminal.RootTerminalManager { *; }
-keep class com.ai.assistance.aiterminal.terminal.TerminalJni$* { *; }

# Room entities + DAOs
-keep class com.apex.agent.database.entity.** { *; }
-keep class com.apex.agent.database.dao.** { *; }
-keep class com.apex.agent.database.AppDatabase { *; }
-keep class com.apex.agent.database.DatabaseRepository { *; }

# kotlinx.serialization
-keep @kotlinx.serialization.Serializable class * { *; }
-keepclassmembers class * {
    @kotlinx.serialization.Serializable <fields>;
}

# AIDL generated stubs
-keep class com.ai.assistance.apex.engine.** { *; }
-keep class com.ai.assistance.aiterminal.terminal.**$Stub { *; }
-keep class com.apex.sdk.bridge.** { *; }

# Compose (R8 handles most, but keep runtime metadata)
-keep class androidx.compose.runtime.** { *; }

# Keep all native method declarations
-keepclasseswithmembernames class * {
    native <methods>;
}
