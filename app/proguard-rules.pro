# ApexStudio ProGuard rules
# Targeted rules only — most libraries bundle their own ProGuard rules.

# Keep line numbers for crash reports
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# --- kotlinx-serialization (official R8 full-mode rules) ---
-keepattributes *Annotation*, InnerClasses, Signature
-dontwarn kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** { *; }
# Keep serializer entry points: Companion objects and generated $serializer classes
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1>$<2> {
    kotlinx.serialization.KSerializer serializer(...);
}

# --- GPUImage (pre-2020 AAR, JNI-backed — R8 cannot verify JNI name binding) ---
# GPUImageFilter hierarchy is instantiated directly and bound via JNI by class/method name
-keep class jp.co.cyberagent.android.gpuimage.** { *; }
-dontwarn jp.co.cyberagent.android.gpuimage.**

# --- Native methods ---
-keepclasseswithmembernames class * {
    native <methods>;
}

# --- Enums (used in serialization) ---
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# --- Parcelable ---
-keep class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}

# --- LUT assets (loaded via dynamic paths — keep from resource shrinking) ---
-keepres "assets/luts/*"
