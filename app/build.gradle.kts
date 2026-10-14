import com.google.gms.googleservices.GoogleServicesPlugin.MissingGoogleServicesStrategy
import java.util.Properties

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.android)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.kotlin.serialization)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.roborazzi)
  alias(libs.plugins.secrets)
  alias(libs.plugins.google.services)
  // Baseline Profile (editor smoothness): registers the
  // generateReleaseBaselineProfile task used by BaselineProfileGenerator.
  alias(libs.plugins.androidx.baselineprofile)
}

// Royalty-free music library (Jamendo API): the free client ID is read from the
// JAMENDO_CLIENT_ID env var first, then from local.properties (same key).
// Get a free key at https://developer.jamendo.com/ — see README "Music library".
// Empty when unset: the app then shows the built-in offline tracks + device import.
fun musicApiKey(name: String): String {
  System.getenv(name)?.takeIf { it.isNotBlank() }?.let { return it }
  val props = Properties()
  val local = rootProject.file("local.properties")
  if (local.exists()) {
    local.inputStream().use { props.load(it) }
  }
  return props.getProperty(name, "")
}

android {
  namespace = "com.apexstudio.app"
  compileSdk = 36

  defaultConfig {
    applicationId = "com.apexstudio.app"
    minSdk = 26
    targetSdk = 36
    versionCode = 1
    versionName = "1.0.0"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    vectorDrawables { useSupportLibrary = true }

    // Royalty-free music library: free Jamendo API client ID (empty = offline mode).
    buildConfigField("String", "JAMENDO_CLIENT_ID", "\"${musicApiKey("JAMENDO_CLIENT_ID")}\"")
  }

  signingConfigs {
    create("release") {
      val keystorePath = System.getenv("KEYSTORE_PATH") ?: "${rootDir}/my-upload-key.jks"
      storeFile = file(keystorePath)
      storePassword = System.getenv("STORE_PASSWORD")
      keyAlias = "upload"
      keyPassword = System.getenv("KEY_PASSWORD")
    }
    create("debugConfig") {
      storeFile = file("${rootDir}/debug.keystore")
      storePassword = "android"
      keyAlias = "androiddebugkey"
      keyPassword = "android"
    }
  }

  buildTypes {
    release {
      isCrunchPngs = true
      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      signingConfig = signingConfigs.getByName("release")
      // PR-C ("Blake" — APK size discipline): ship native libraries for arm64
      // only in release. This drops the x86_64/armeabi-v7a copies of heavy
      // native SDKs (notably Snap Camera Kit's ~96MB of .so files), roughly
      // halving their footprint. arm64-v8a covers effectively all real
      // devices (2017+); debug keeps x86_64 so emulator testing still works.
      ndk {
        abiFilters += listOf("arm64-v8a")
      }
    }
    debug {
      signingConfig = signingConfigs.getByName("debugConfig")
      ndk {
        abiFilters += listOf("arm64-v8a", "x86_64")
      }
    }
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
  testOptions { unitTests { isIncludeAndroidResources = true } }
  dependenciesInfo {
    includeInApk = false
    includeInBundle = true
  }
  packaging {
    resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
  }
}

// Configure the Secrets Gradle Plugin to use .env and .env.example files
// to match the convention used in Web projects.
secrets {
  propertiesFileName = ".env"
  defaultPropertiesFileName = ".env.example"
  ignoreList.add("FIREBASE_APPCHECK_DEBUG_TOKEN")
}

googleServices { missingGoogleServicesStrategy = MissingGoogleServicesStrategy.WARN }

// Some unused dependencies are commented out below instead of being removed.
// This makes it easy to add them back in the future if needed.
dependencies {
  implementation(platform(libs.androidx.compose.bom))
  implementation(platform(libs.firebase.bom))
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.datastore.preferences)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.navigation.compose)
  implementation(libs.coil.compose)

  // Media3 & Video/Audio Processing
  implementation("androidx.media3:media3-exoplayer:1.5.0")
  implementation("androidx.media3:media3-ui:1.5.0")
  implementation("androidx.media3:media3-common:1.5.0")
  implementation("androidx.media3:media3-transformer:1.5.0")
  implementation("androidx.media3:media3-effect:1.5.0")
  implementation("androidx.media3:media3-session:1.5.0")
  implementation("androidx.media3:media3-datasource:1.5.0")

  // Camera Kit SDK lives in :feature:camerakit (see that module's
  // build.gradle.kts) — the app must not import the SDK directly, so the
  // DFM follow-up (dynamic-feature + on-demand download) is mechanical.
  implementation(project(":feature:camerakit"))

  // Serialization & Data
  implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
  implementation("jp.co.cyberagent.android:gpuimage:2.1.0")
  implementation("com.google.mlkit:face-detection:16.1.7")
  // NOTE: ML Kit segmentation-selfie was removed (app-size work): it had zero
  // imports — all person segmentation goes through MediaPipe Tasks Vision
  // (PR-G, BackgroundReplaceEngine) with its model on-demand via PackManager.
  // Keeping the dep would bundle its ~249 KB .tflite model asset for nothing.

  // Phase 4: GitHub open-source integrations (all Apache-2.0/MIT/OFL)
  // Lottie animated stickers/overlays — https://github.com/airbnb/lottie-android
  implementation("com.airbnb.android:lottie:6.7.1")
  implementation("com.airbnb.android:lottie-compose:6.7.1")
  // Ken Burns pan/zoom for photo slideshow — https://github.com/flavioarfaria/KenBurnsView (Apache-2.0)
  implementation("com.flaviofaria:kenburnsview:1.0.7")
  // On-device Whisper speech recognition for auto-captions —
  // https://github.com/microsoft/onnxruntime (MIT). Official Maven
  // artifact, no NDK/JNI of our own. Model (~103MB in 3 file packs)
  // downloads on first use via PackManager, NOT bundled in the APK.
  implementation("com.microsoft.onnxruntime:onnxruntime-android:1.24.3")

  // PR-G: MediaPipe Tasks Vision for per-clip background replace
  // (person segmentation) — https://github.com/google-ai-edge/mediapipe
  // (Apache-2.0). Official Maven artifact (Google Maven). Only the
  // Tasks library is bundled; the segmentation model (~244 KB)
  // downloads on first use via PackManager, NOT in the APK.
  implementation("com.google.mediapipe:tasks-vision:0.10.35")

  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)

  // Baseline Profile: installs the generated baseline-prof.txt into
  // release builds (faster cold start + first editor open). The profile
  // itself is generated on-device (BaselineProfileGenerator) — until it
  // lands in src/main/baseline-prof.txt this dep is a harmless no-op.
  implementation(libs.androidx.profileinstaller)

  // Baseline Profile generator (on-device only; never runs in CI).
  androidTestImplementation(libs.androidx.benchmark.macro.junit4)
  androidTestImplementation(libs.androidx.uiautomator)
  androidTestImplementation(libs.androidx.junit)
  // Explicit: BaselineProfileGenerator imports InstrumentationRegistry
  // from here (do not rely on the transitive copy).
  androidTestImplementation(libs.androidx.test.platform)

  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  testImplementation(libs.roborazzi)
  testImplementation(libs.roborazzi.compose)
  testImplementation(libs.roborazzi.junit.rule)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
}
