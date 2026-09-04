plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp") version "2.1.0-1.0.29" apply false
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
        vectorDrawables { useSupportLibrary = true }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug { isMinifyEnabled = false }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf(
            "-opt-in=androidx.media3.common.util.UnstableApi"
        )
    }
    buildFeatures { compose = true }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.11.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    // Compose
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.foundation:foundation")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    // Core
    implementation("androidx.core:core-ktx:1.14.0")
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")
    implementation("androidx.navigation:navigation-compose:3.0.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.2")

    // ExoPlayer / Media3
    implementation("androidx.media3:media3-exoplayer:1.5.0")
    implementation("androidx.media3:media3-ui:1.5.0")
    implementation("androidx.media3:media3-common:1.5.0")
    implementation("androidx.media3:media3-transformer:1.5.0")
    implementation("androidx.media3:media3-effect:1.5.0")
    implementation("androidx.media3:media3-session:1.5.0")
    implementation("androidx.media3:media3-exoplayer-dash:1.5.0")
    implementation("androidx.media3:media3-exoplayer-hls:1.5.0")
    implementation("androidx.media3:media3-datasource:1.5.0")
    implementation("androidx.media3:media3-datasource-okhttp:1.5.0")

    // Coil for image loading
    implementation("io.coil-kt:coil-compose:2.9.0")

    // DataStore
    implementation("androidx.datastore:datastore-preferences:1.2.0")

    // Project auto-save: kotlinx-serialization drives the JSON
    // codec that stores each Project (clips, trim, LUT, audio tracks,
    // keyframes) in DataStore<Preferences>. No Room / KSP needed —
    // we keep the schema versioned by hand inside ProjectSerializer.
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    // GPUImage — lookup-table (3D LUT) filter engine for the CapCut-style
    // color presets. Used to bake the .cube LUTs onto frames in real time
    // and during hardware-accelerated export.
    implementation("jp.co.cyberagent.android:gpuimage:2.1.0")
}