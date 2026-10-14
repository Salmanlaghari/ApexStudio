# Consumer ProGuard rules for :feature:camerakit (Snap Camera Kit SDK).
# Shipped with the module so R8 keeps the SDK's reflection/JNI-heavy classes
# wherever the module is consumed (base APK now, dynamic feature later).
# Moved verbatim from app/proguard-rules.pro.
-keep class com.snap.camerakit.** { *; }
-keep class com.snap.** { *; }
-dontwarn com.snap.**
