# GPUImage native 16KB alignment

GPUImage remains the upstream Maven artifact `jp.co.cyberagent.android:gpuimage:2.1.0` for its Java API, resources, and filters. Its AAR includes 4KB-aligned `libyuv-decoder.so` binaries. The app's dedicated `gpuImageAar` artifact view runs a Gradle transform that removes **only** `jni/<abi>/libyuv-decoder.so`; it fails if the expected entry is missing. The app then includes `:gpuimage-native`, which rebuilds the same library name and JNI entry points from the unmodified upstream C source. No other AAR entries are patched or excluded.

## Source and license

The replacement source is `gpuimage-native/src/main/cpp/yuv-decoder.c`, byte-for-byte from [GPUImage release 2.1.0, commit `25c40697abdaf051c12c337bcdda69c4ba774b10`](https://github.com/cats-oss/android-gpuimage/tree/25c40697abdaf051c12c337bcdda69c4ba774b10/library/src/main/cpp). The upstream release README identifies the project as Apache-2.0; the canonical license text is included at [`licenses/GPUImage-Apache-2.0.txt`](licenses/GPUImage-Apache-2.0.txt). The upstream JNI class loads `yuv-decoder` and declares the native methods retained by the rebuilt library.

## Build

Install Android NDK `28.2.13676358` and CMake `3.22.1`, then run `./gradlew :gpuimage-native:assembleRelease`. The module builds `arm64-v8a` and `x86_64`; app variant ABI filters still control what is packaged. CMake passes both `-Wl,-z,max-page-size=16384` and `-Wl,-z,common-page-size=16384` to the native linker. The focused transform test is in `buildSrc/src/test/java/com/apexstudio/buildlogic/StripGpuImageDecoderTransformTest.java`.
