-keep public class * implements android.os.IInterface
-keepclassmembers class * implements android.os.IInterface {
    public *;
}

-keep class com.ai.assistance.apex.engine.** { *; }
-keep interface com.ai.assistance.apex.engine.** { *; }
-keep class com.ai.assistance.apex.engine.model.** { *; }

-keepclassmembers class * {
    @android.webkit.JavascriptInterface *;
}
# Fix R8 missing class
-dontwarn java.lang.invoke.StringConcatFactory
