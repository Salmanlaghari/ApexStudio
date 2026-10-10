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

# --- Phase 2: R8 Guard additions ---
# Snap Camera Kit SDK (restored PR #127 — reflection/JNI heavy)
-keep class com.snap.camerakit.** { *; }
-keep class com.snap.** { *; }
-dontwarn com.snap.**

# Media3 (bundles own rules, belt-and-braces for Transformer GlEffects)
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# Pack assets (loaded via dynamic asset paths — keep from resource shrinking)
-keepres "assets/packs/*"
-keepres "assets/stickers/*"

# PackLoader data models are @Serializable — covered by kotlinx-serialization
# rules above. GpuColorProfile / GpuFilterConfig are constructed reflectively
# by PackLoader — keep their fields.
-keepclassmembers class com.apexstudio.app.data.filter.GpuColorProfile { *; }
-keepclassmembers class com.apexstudio.app.data.filter.GpuFilterConfig { *; }
-keepclassmembers class com.apexstudio.app.data.packs.PackLoader$* { *; }

# CameraX (used by Camera Kit support lib)
-keep class androidx.camera.** { *; }
-dontwarn androidx.camera.**

# Phase 4: Vosk offline STT (JNA + reflection)
-keep class org.vosk.** { *; }
-keep class com.sun.jna.** { *; }
-dontwarn org.vosk.**
-dontwarn com.sun.jna.**

# Phase 4: Lottie (reflection on model classes)
-keep class com.airbnb.lottie.** { *; }
-dontwarn com.airbnb.lottie.**

# Phase 4: gl-transitions shader assets + Lottie animation assets
-keepres "assets/gl_transitions/*"
-keepres "assets/lottie/*"
