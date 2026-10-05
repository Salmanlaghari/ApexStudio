# ApexStudio ProGuard rules
# Targeted rules only — libraries (Media3, Coil, Compose, DataStore, Firebase)
# bundle their own ProGuard rules; blanket -keep defeats R8 shrinking.

# Keep line numbers for crash reports
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# --- kotlinx-serialization ---
# Keep the serialization infrastructure
-keepattributes *Annotation*, InnerClasses, Signature
-dontwarn kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** { *; }
# Keep serializer() methods so R8 doesn't remove them
-keepclasseswithmembernames class * {
    kotlinx.serialization.KSerializer serializer(...);
}
# Keep @Serializable classes but allow R8 to shrink unused members.
# Only the fields actually serialized need keeping — the annotation
# processor generates the serializer which R8 tracks.
-keep @kotlinx.serialization.Serializable class com.apexstudio.app.** {
    <fields>;
}

# --- Native methods (GPUImage, etc.) ---
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
