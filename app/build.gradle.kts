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

    // Snap Camera Kit API token: read from the SNAP_CAMERA_KIT_TOKEN environment
    // variable (injected in CI from the GitHub Actions secret of the same name),
    // falling back to local.properties for local builds. Never hardcode the token.
    val snapCameraKitToken: String = System.getenv("SNAP_CAMERA_KIT_TOKEN")
      ?: rootProject.file("local.properties").takeIf { it.exists() }?.let { propsFile ->
        Properties().also { props ->
          propsFile.inputStream().use { props.load(it) }
        }.getProperty("SNAP_CAMERA_KIT_TOKEN")
      }
      ?: ""
    // Escape every character that is special inside a Java string literal so the
    // token survives verbatim into the generated BuildConfig field.
    // (In Java source only backslash and the quote need escaping; '$' is literal.)
    val escapedToken = snapCameraKitToken
      .replace("\\", "\\\\")
      .replace("\"", "\\\"")
    buildConfigField("String", "SNAP_CAMERA_KIT_TOKEN", "\"$escapedToken\"")
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
    }
    debug { signingConfig = signingConfigs.getByName("debugConfig") }
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

  // Snap Camera Kit SDK (AR lenses) — https://github.com/Snapchat/camera-kit-android-sdk
  val cameraKitVersion = "1.50.0"
  implementation("com.snap.camerakit:camerakit:$cameraKitVersion")
  implementation("com.snap.camerakit:camerakit-kotlin:$cameraKitVersion")
  implementation("com.snap.camerakit:support-camerax:$cameraKitVersion")

  // Serialization & Data
  implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
  implementation("jp.co.cyberagent.android:gpuimage:2.1.0")
  implementation("com.google.mlkit:face-detection:16.1.7")

  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)

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
