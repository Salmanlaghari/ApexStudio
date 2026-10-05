# ApexStudio ProGuard rules

# Keep line numbers for crash reports
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# --- kotlinx-serialization ---
# Keep generated serializers
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *; }
-keepclasseswithmembernames class * {
    kotlinx.serialization.KSerializer serializer(...);
}
# Keep serializable data classes (Project, MediaClip, etc.)
-keep @kotlinx.serialization.Serializable class com.apexstudio.app.** { *; }
-keepclassmembers @kotlinx.serialization.Serializable class com.apexstudio.app.** { *; }

# --- AndroidX Media3 (ExoPlayer, Transformer) ---
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**
-keepclassmembers class androidx.media3.** { *; }

# --- Coil (image loading) ---
-keep class coil.** { *; }
-dontwarn coil.**

# --- DataStore ---
-keep class androidx.datastore.** { *; }

# --- Compose ---
-dontwarn androidx.compose.**
-keep class androidx.compose.** { *; }

# --- Firebase (if used) ---
-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**

# --- GPUImage ---
-keep class jp.co.cyberagent.android.gpuimage.** { *; }
-dontwarn jp.co.cyberagent.android.gpuimage.**

# Keep native methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep enums
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Keep Parcelable
-keep class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}
