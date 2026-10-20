plugins {
  alias(libs.plugins.android.library)
}

android {
  namespace = "com.apexstudio.gpuimage.nativefix"
  compileSdk = 36
  ndkVersion = "28.2.13676358"

  defaultConfig {
    minSdk = 26
    ndk {
      abiFilters += listOf("arm64-v8a", "x86_64")
    }
  }

  externalNativeBuild {
    cmake {
      path = file("src/main/cpp/CMakeLists.txt")
      version = "3.22.1"
    }
  }
}
