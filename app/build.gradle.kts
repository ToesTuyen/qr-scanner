plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
    id("kotlin-parcelize")
    alias(libs.plugins.kotlin.ksp)
    alias(libs.plugins.hilt.android)
}

val releaseVersionCode = providers.gradleProperty("releaseVersionCode")
    .orNull
    ?.toIntOrNull()
    ?: 1_000_003
val releaseVersionName = providers.gradleProperty("releaseVersionName")
    .orNull
    ?: "1.0.3"
val githubKeystorePath = providers.environmentVariable("ANDROID_KEYSTORE_PATH").orNull
val githubKeystorePassword = providers.environmentVariable("ANDROID_KEYSTORE_PASSWORD").orNull
val githubKeyAlias = providers.environmentVariable("ANDROID_KEY_ALIAS").orNull
val githubKeyPassword = providers.environmentVariable("ANDROID_KEY_PASSWORD").orNull

android {
    namespace = "com.ivistatect.qrscanner"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.ivistatect.qrscanner"
        minSdk = 28
        targetSdk = 36
        versionCode = releaseVersionCode
        versionName = releaseVersionName
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Local release builds use the existing debug key. GitHub Actions replaces it with
            // the same key supplied through repository secrets so updates can install in place.
            signingConfig = if (githubKeystorePath != null) {
                signingConfigs.maybeCreate("githubRelease").apply {
                    storeFile = file(githubKeystorePath)
                    storePassword = githubKeystorePassword
                    keyAlias = githubKeyAlias
                    keyPassword = githubKeyPassword
                }
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions { jvmTarget = "11" }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    androidResources {
        noCompress += listOf("ogg", "mp4", "webp", "jpg", "png", "db", "tflite")
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    // Material Components provides the Theme.Material3 XML theme inherited by the app.
    implementation(libs.material)
    implementation("androidx.print:print:1.0.0")
    implementation(libs.androidx.navigation.compose)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)

    // Hilt backs the app's ViewModel and repository graph.
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    // ── QR Scanner domain deps (each for a concrete feature) ────────────────────────────────
    // Live + gallery decode: MLKit barcode-scanning (bundled models variant; no .tflite copied).
    implementation(libs.mlkit.barcode.scanning)
    // Camera preview + frame analysis.
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    // QR / barcode bitmap generation for the Create flow.
    implementation(libs.zxing.core)
    // History persistence (scanned / created / favorites, search, delete).
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    debugImplementation(libs.androidx.ui.tooling)
}
