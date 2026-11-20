# Add project specific ProGuard rules here.

# Keep generic signatures + annotations so Gson/Retrofit can resolve
# parameterized types (fixes "Class cannot be cast to ParameterizedType"
# on release builds under R8 full mode).
-keepattributes Signature
-keepattributes InnerClasses
-keepattributes EnclosingMethod
-keepattributes *Annotation*, RuntimeVisibleAnnotations, AnnotationDefault

# Retrofit
-keep class retrofit2.** { *; }
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
# R8 full mode: keep type info on Retrofit return / suspend types.
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
# Keep the API service interface with its generic signatures intact.
-keep interface com.yamtrack.app.data.api.YamtrackApi { *; }

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }

# Moshi (reflective Kotlin adapter)
-keep class com.squareup.moshi.** { *; }
-keep @com.squareup.moshi.JsonClass class * { *; }
-keepclassmembers class * {
    @com.squareup.moshi.Json <fields>;
}
-keepclassmembers class kotlin.Metadata { *; }
-keep class kotlin.reflect.jvm.internal.** { *; }

# Data models (keep names + members so reflection-based JSON works)
-keep class com.yamtrack.app.data.model.** { *; }
-keepclassmembers class com.yamtrack.app.data.model.** { *; }
