import java.util.Properties

plugins {
  alias(libs.plugins.android.library)
  alias(libs.plugins.kotlin.android)
  alias(libs.plugins.kotlin.compose)
}

// Camera Kit SDK (Snap) isolated in its own module — the largest safe step
// toward on-demand delivery (Play Feature Delivery / Dynamic Feature Module).
//
// WHY A SEPARATE MODULE: the SDK (~88 MB AAR footprint: ~96 MB of .so files
// across ABIs) is the single biggest contributor to APK size. It cannot be
// moved to the file-based PackManager (native libs + dex must load from the
// APK), so the correct mechanism is a Dynamic Feature Module served by Play.
// This module is the prerequisite: the base app no longer has ANY direct
// Camera Kit import (it depends only on this module's public API), so the
// DFM follow-up is mechanical:
//   1. Change this module's plugin to `com.android.dynamic-feature` +
//      `dist:onDemand="true"` in its manifest.
//   2. Base: replace `implementation(project(":feature:camerakit"))` with
//      `dynamicFeatures = [":feature:camerakit"]` and access LensesScreen
//      via SplitInstallManager + Intent (wrap it in a feature Activity).
//   3. Add the "download to use AR Face / Lens" prompt + progress (Packs UI pattern).
// Until then the SDK stays bundled (behavior identical) — this module only
// isolates it. See the PR description for measured with/without sizes.

android {
  namespace = "com.apexstudio.app.camerakit"
  compileSdk = 36

  defaultConfig {
    minSdk = 26
    consumerProguardFiles("consumer-rules.pro")

    // Snap Camera Kit API token: same source as the app module used to read —
    // SNAP_CAMERA_KIT_TOKEN env var (CI secret) falling back to
    // local.properties. Never hardcoded. This module generates its own
    // BuildConfig; the token lives here now, not in the app module.
    val snapCameraKitToken: String = System.getenv("SNAP_CAMERA_KIT_TOKEN")
      ?: rootProject.file("local.properties").takeIf { it.exists() }?.let { propsFile ->
        Properties().also { props ->
          propsFile.inputStream().use { props.load(it) }
        }.getProperty("SNAP_CAMERA_KIT_TOKEN")
      }
      ?: ""
    val escapedToken = snapCameraKitToken
      .replace("\\", "\\\\")
      .replace("\"", "\\\"")
    buildConfigField("String", "SNAP_CAMERA_KIT_TOKEN", "\"$escapedToken\"")
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
  kotlinOptions {
    jvmTarget = "17"
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
}

dependencies {
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.coil.compose)

  // Snap Camera Kit SDK (AR lenses) — https://github.com/Snapchat/camera-kit-android-sdk
  // Moved here verbatim from the app module; nothing in :app imports these.
  val cameraKitVersion = "1.50.0"
  implementation("com.snap.camerakit:camerakit:$cameraKitVersion")
  implementation("com.snap.camerakit:camerakit-kotlin:$cameraKitVersion")
  implementation("com.snap.camerakit:support-camerax:$cameraKitVersion")
}
