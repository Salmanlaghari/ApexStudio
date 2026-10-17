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
-keepclassmembers class <1>$Companion {
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

# NOTE (app-size): no keep rule needed for assets/ - AGP shrinkResources
# only shrinks res/ resources, never assets/. (R8 rejects both forms of
# this option as unknown, which broke the release build.)

# --- Phase 2: R8 Guard additions ---
# (Snap Camera Kit keep rules moved to :feature:camerakit/consumer-rules.pro,
# shipped with that module.)

# Media3 (bundles own rules, belt-and-braces for Transformer GlEffects)
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# Pack/sticker assets: loaded via dynamic asset paths; assets/ are never
# shrunk, so no keep rule is needed (see note above).

# PackLoader data models are @Serializable — covered by kotlinx-serialization
# rules above. GpuColorProfile / GpuFilterConfig are constructed reflectively
# by PackLoader — keep their fields.
-keepclassmembers class com.apexstudio.app.data.filter.GpuColorProfile { *; }
-keepclassmembers class com.apexstudio.app.data.filter.GpuFilterConfig { *; }
-keepclassmembers class com.apexstudio.app.data.packs.PackLoader$* { *; }

# CameraX (used by Camera Kit support lib)
-keep class androidx.camera.** { *; }
-dontwarn androidx.camera.**

# AI auto-captions: onnxruntime-android (JNI; direct API calls, no reflection,
# but keep the package so R8 can't strip classes the native layer needs).
-keep class ai.onnxruntime.** { *; }
-dontwarn ai.onnxruntime.**

# PR-G: MediaPipe Tasks (JNI + reflection-heavy task graph; release
# builds have crashed without this keep — the native layer resolves
# task classes by name).
-keep class com.google.mediapipe.** { *; }
-dontwarn com.google.mediapipe.**

# Phase 4: Lottie (reflection on model classes)
-keep class com.airbnb.lottie.** { *; }
-dontwarn com.airbnb.lottie.**

# Phase 4: gl-transitions shader assets + Lottie animation assets

# --- FFmpegKit (PR #156) ---
-keep class com.arthenica.ffmpegkit.** { *; }
-dontwarn com.arthenica.ffmpegkit.**
-dontwarn com.arthenica.smartexception.**

# --- GStreamer & JNA (Hybrid Engine PR #2) ---
-keep class org.freedesktop.gstreamer.** { *; }
-dontwarn org.freedesktop.gstreamer.**
-keep class com.sun.jna.** { *; }
-dontwarn com.sun.jna.**
