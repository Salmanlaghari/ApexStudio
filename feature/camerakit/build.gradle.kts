import java.util.Properties

plugins {
  alias(libs.plugins.android.dynamic.feature)
  alias(libs.plugins.kotlin.android)
  alias(libs.plugins.kotlin.compose)
}

// Play Feature Delivery keeps Camera Kit's dex and native libraries out of the
// lightweight base APK until the user explicitly requests AR lenses.

android {
  namespace = "com.apexstudio.app.camerakit"
  compileSdk = 36

  defaultConfig {
    minSdk = 26

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
    // Kilo SUGGESTIONs (token escaping): escape the token for a Java string
    // literal so the generated BuildConfig field is always valid. The token
    // is RAW here (env vars are raw; Properties.load() already unescapes
    // local.properties values), so backslash-escaping is required and
    // correct — a raw backslash must become "\\" in source. Newlines /
    // carriage returns / tabs are escaped (not stripped) so the value
    // round-trips exactly instead of breaking the literal.
    val escapedToken = snapCameraKitToken
      .replace("\\", "\\\\")
      .replace("\"", "\\\"")
      .replace("\n", "\\n")
      .replace("\r", "\\r")
      .replace("\t", "\\t")
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
  buildTypes {
    getByName("release") {
      proguardFiles("consumer-rules.pro")
    }
  }
}

dependencies {
  implementation(project(":app"))
  implementation(libs.play.feature.delivery)
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
  testImplementation("junit:junit:4.13.2")
}
